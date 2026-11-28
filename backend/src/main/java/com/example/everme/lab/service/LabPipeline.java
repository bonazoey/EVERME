package com.example.everme.lab.service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.MDC;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import com.example.everme.lab.prompt.LabPrompts;
import com.example.everme.lab.service.LabAi.Result;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/** One ordered producer and one ordered consumer; all durable checkpoints live in existing JSON. */
@Service
@Profile("clone-lab")
public class LabPipeline {
  // ##### 필드 #####
  static final String MODE = "single-session-pipeline-v1";
  private final LabService lab;
  private final LabCatalog catalog;
  private final LabSchemas schemas;
  private final LabValidation validation;
  private final LabAi ai;

  // ##### 생성자 #####
  public LabPipeline(
      LabService lab, LabCatalog catalog, LabSchemas schemas, LabValidation validation, LabAi ai) {
    this.lab = lab;
    this.catalog = catalog;
    this.schemas = schemas;
    this.validation = validation;
    this.ai = ai;
  }

  static void addMetrics(ObjectNode state, JsonNode metrics) {
    for (String name : List.of("inputTokens", "outputTokens", "cachedTokens")) {
      state.put(name, state.path(name).asLong() + metrics.path(name).asLong());
    }
    state.put(
        "llmElapsedMs", state.path("llmElapsedMs").asLong() + metrics.path("elapsedMs").asLong());
    state.put("elapsedMs", state.path("llmElapsedMs").asLong());
    state.put("heartbeatAt", System.currentTimeMillis());
  }

  // ##### 자동 실행 상태를 조회하는 메소드 #####
  private ObjectNode state(UUID user) {
    return (ObjectNode) lab.dao.parse(lab.dao.requireUser(user).get("run_state"));
  }

  private boolean running(UUID user, AtomicReference<String> error) {
    return error.get() == null && "running".equals(state(user).path("status").asText());
  }

  // ##### 대화 배치를 조회하는 메소드 #####
  private UUID batch(ObjectNode state) {
    return UUID.fromString(state.path("batchId").asText());
  }

  private ObjectNode details(UUID user, UUID batch) {
    return (ObjectNode) lab.dao.parse(lab.dao.requireBatch(user, batch).get("analysis"));
  }

  private ObjectNode segment(ObjectNode details, int number) {
    return (ObjectNode) details.path("segments").get(number - 1);
  }

  // ##### 생성과 분석 작업을 병행 실행하는 메소드 #####
  public void execute(UUID user) {
    AtomicReference<String> error = new AtomicReference<String>();
    Object signal = new Object();
    try (ExecutorService workers = Executors.newVirtualThreadPerTaskExecutor()) {
      Future<?> producer = workers.submit(() -> worker(user, error, signal, true));
      Future<?> consumer = workers.submit(() -> worker(user, error, signal, false));
      try {
        producer.get();
        consumer.get();
      } catch (InterruptedException e) {
        workers.shutdownNow();
        Thread.currentThread().interrupt();
        error.compareAndSet(null, "Pipeline interrupted");
      } catch (ExecutionException e) {
        error.compareAndSet(null, "Pipeline worker failed");
      }
    }
    lab.tx.executeWithoutResult(
        s -> {
          Map<String, Object> row = lab.dao.mapper().lockUser(user);
          ObjectNode run = (ObjectNode) lab.dao.parse(row.get("run_state"));
          run.put(
              "wallElapsedMs",
              run.path("wallElapsedMs").asLong()
                  + Math.max(
                      0,
                      System.currentTimeMillis()
                          - run.path("activeSince").asLong(System.currentTimeMillis())));
          run.remove("activeSince");
          run.put(
              "status",
              error.get() != null
                  ? "failed"
                  : run.path("savedPairs").asInt() >= 1000 ? "completed" : "paused");
          if (error.get() == null) {
            run.putNull("error");
          } else {
            run.put("error", error.get());
          }
          lab.dao.mapper().runState(user, run.toString());
        });
  }

  private void worker(UUID user, AtomicReference<String> error, Object signal, boolean producer) {
    MDC.put("labRunId", state(user).path("runId").asText());
    try (LabCallContext context =
        new LabCallContext(() -> running(user, error), () -> recordRetry(user))) {
      while (running(user, error)) {
        ObjectNode run = state(user);
        UUID batch = batch(run);
        boolean worked = false;
        if (producer) {
          if (run.path("generatedPairs").asInt() >= 1000) {
            return;
          }
          if (reserve(user, error)) {
            MDC.put(
                "labSegment",
                Integer.toString(
                    state(user).path("generationReservation").path("from").asInt() / 10 + 1));
            lab.appendAutomatic(user, batch);
            worked = true;
          }
        } else {
          if (run.path("savedPairs").asInt() >= 1000) {
            return;
          }
          int number = run.path("savedPairs").asInt() / 10 + 1;
          JsonNode segments = details(user, batch).path("segments");
          if (segments.size() >= number) {
            JsonNode selected = segments.get(number - 1);
            String phase =
                "generated".equals(selected.path("status").asText()) ? "analyzing" : "saving";
            if (admit(user, phase, error)) {
              MDC.put("labSegment", Integer.toString(number));
              if ("analyzing".equals(phase)) {
                analyzeSegment(user, batch, number);
              } else {
                saveSegment(user, batch, number);
              }
              worked = true;
            }
          }
        }
        synchronized (signal) {
          signal.notifyAll();
          if (!worked) {
            signal.wait(100);
          }
        }
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      error.compareAndSet(null, "Pipeline interrupted");
    } catch (Exception e) {
      if ("running".equals(state(user).path("status").asText())) {
        error.compareAndSet(
            null,
            e instanceof LabException ? e.getMessage() : "자동 실행 단계가 실패했습니다. 같은 구간에서 재개할 수 있습니다.");
      }
    } finally {
      MDC.remove("labRunId");
      MDC.remove("labSegment");
      synchronized (signal) {
        signal.notifyAll();
      }
    }
  }

  private boolean reserve(UUID user, AtomicReference<String> error) {
    return Boolean.TRUE.equals(
        lab.tx.execute(
            s -> {
              Map<String, Object> row = lab.dao.mapper().lockUser(user);
              ObjectNode run = (ObjectNode) lab.dao.parse(row.get("run_state"));
              int generated = run.path("generatedPairs").asInt();
              if (error.get() != null
                  || !"running".equals(run.path("status").asText())
                  || generated >= 1000
                  || generated - run.path("savedPairs").asInt() + 10 > 20) {
                return false;
              }
              ObjectNode reservation = run.putObject("generationReservation");
              reservation.put("from", generated + 1);
              reservation.put("to", generated + 10);
              run.put("generationPhase", "generating");
              run.put("heartbeatAt", System.currentTimeMillis());
              lab.dao.mapper().runState(user, run.toString());
              return true;
            }));
  }

  private boolean admit(UUID user, String phase, AtomicReference<String> error) {
    return Boolean.TRUE.equals(
        lab.tx.execute(
            s -> {
              Map<String, Object> row = lab.dao.mapper().lockUser(user);
              ObjectNode run = (ObjectNode) lab.dao.parse(row.get("run_state"));
              if (error.get() != null || !"running".equals(run.path("status").asText())) {
                return false;
              }
              run.put("analysisPhase", phase);
              run.put("heartbeatAt", System.currentTimeMillis());
              lab.dao.mapper().runState(user, run.toString());
              return true;
            }));
  }

  private void recordRetry(UUID user) {
    lab.tx.executeWithoutResult(
        s -> {
          Map<String, Object> row = lab.dao.mapper().lockUser(user);
          ObjectNode run = (ObjectNode) lab.dao.parse(row.get("run_state"));
          run.put("retryCount", run.path("retryCount").asInt() + 1);
          lab.dao.mapper().runState(user, run.toString());
        });
  }

  private ArrayNode fresh(UUID user, UUID batch, JsonNode selected) {
    HashSet<String> ids = new HashSet<String>();
    for (JsonNode id : selected.path("sourceIds")) {
      ids.add(id.asText());
    }
    Set<String> seen = lab.duplicateTexts(user);
    ArrayNode result = lab.json.createArrayNode();
    for (Map<String, Object> turn : lab.dao.mapper().turns(batch)) {
      if (ids.contains(turn.get("id").toString())
          && seen.add(LabService.normalized(turn.get("user_text").toString()))) {
        result
            .addObject()
            .put("id", turn.get("id").toString())
            .put("text", turn.get("user_text").toString());
      }
    }
    return result;
  }

  // ##### 고정된 10쌍 구간을 분석하는 메소드 #####
  void analyzeSegment(UUID user, UUID batch, int number) {
    try (LabTiming timing = new LabTiming("pipeline.analyze")) {
      ObjectNode selected = segment(details(user, batch), number).deepCopy();
      if (!"generated".equals(selected.path("status").asText())) {
        timing.success();
        return;
      }
      ArrayNode sources = fresh(user, batch, selected);
      ArrayNode summaries = lab.json.createArrayNode();
      List<Map<String, Object>> turns = lab.dao.mapper().turns(batch);
      for (int prior = Math.max(1, number - 5); prior < number; prior++) {
        ObjectNode old = segment(details(user, batch), prior);
        if (!old.path("summary").isTextual()) {
          ObjectNode request = lab.object();
          ArrayNode pairs = request.putArray("turns");
          final JsonNode priorIds = old.path("sourceIds");
          for (Map<String, Object> turn :
              turns.stream()
                  .filter(
                      t -> {
                        for (JsonNode source : priorIds)
                          if (source.asText().equals(t.get("id").toString())) return true;
                        return false;
                      })
                  .toList()) {
            pairs
                .addObject()
                .put("user", turn.get("user_text").toString())
                .put("assistant", turn.get("assistant_text").toString());
          }
          Result result =
              ai.structured(
                  "summary",
                  LabPrompts.SEGMENT_SUMMARY,
                  request,
                  schemas.object(Map.of("summary", schemas.string())));
          LabValidation.text(result.data().path("summary").asText(), 1000, "대화 요약");
          final int index = prior;
          lab.tx.executeWithoutResult(
              s -> {
                Map<String, Object> row = lab.dao.mapper().lockUser(user);
                ObjectNode latest = details(user, batch);
                ObjectNode item = segment(latest, index);
                if (!item.has("summary")) {
                  item.set("summary", result.data().path("summary"));
                  item.set("summaryMetrics", lab.metrics(result));
                  lab.dao.mapper().pipelineDetails(batch, latest.toString(), "generated");
                  ObjectNode run = (ObjectNode) lab.dao.parse(row.get("run_state"));
                  addMetrics(run, lab.metrics(result));
                  lab.dao.mapper().runState(user, run.toString());
                }
              });
          old = segment(details(user, batch), prior);
        }
        ObjectNode entry = summaries.addObject();
        entry.set("from", old.path("from"));
        entry.set("to", old.path("to"));
        entry.set("summary", old.path("summary"));
      }
      timing.mark("prepare_and_cache_summaries");
      ObjectNode input = lab.object();
      input.set("catalog", catalog.analysisDocument());
      input.set("sources", sources);
      input.set("summaries", summaries);
      input.put("maxMemories", 40);
      input.put("maxReasonLength", 1000);
      LabAi.Result result =
          sources.isEmpty()
              ? null
              : ai.structured("analysis", LabPrompts.ANALYSIS, input, schemas.analysis(catalog));
      ObjectNode analysis = result == null ? lab.object() : ((ObjectNode) result.data()).deepCopy();
      if (result == null) {
        analysis.putArray("observations");
        analysis.putArray("memories");
      } else {
        analysis.set("metrics", lab.metrics(result));
      }
      analysis.set("summaries", summaries);
      validation.analysis(analysis, lab.sourceIds(sources));
      timing.mark("analyze_and_validate");
      lab.tx.executeWithoutResult(
          s -> {
            Map<String, Object> row = lab.dao.mapper().lockUser(user);
            ObjectNode latest = details(user, batch);
            ObjectNode current = segment(latest, number);
            if (!"generated".equals(current.path("status").asText())) {
              return;
            }
            if (!current.path("sourceIds").equals(selected.path("sourceIds"))) {
              throw LabException.conflict("분석 대상이 변경되었습니다.");
            }
            current.set("analysis", analysis);
            current.put("status", "analyzed");
            for (JsonNode id : current.path("sourceIds")) {
              lab.dao.mapper().contextStatus(UUID.fromString(id.asText()), "analyzed");
            }
            lab.dao.mapper().pipelineDetails(batch, latest.toString(), "generated");
            ObjectNode run = (ObjectNode) lab.dao.parse(row.get("run_state"));
            run.put("analyzedPairs", current.path("to").asInt());
            run.put("analysisPhase", "ready_to_save");
            if (result != null) {
              addMetrics(run, lab.metrics(result));
            }
            lab.dao.mapper().runState(user, run.toString());
          });
      timing.mark("persist_draft");
      timing.success();
    }
  }

  // ##### 구간 분석 결과를 원자적으로 저장하는 메소드 #####
  void saveSegment(UUID user, UUID batch, int number) {
    try (LabTiming timing = new LabTiming("pipeline.save")) {
      ObjectNode selected = segment(details(user, batch), number).deepCopy();
      if ("saved".equals(selected.path("status").asText())) {
        timing.success();
        return;
      }
      if (!"analyzed".equals(selected.path("status").asText())) {
        throw LabException.conflict("먼저 구간을 분석해 주세요.");
      }
      JsonNode analysis = selected.path("analysis");
      validation.analysis(analysis, lab.sourceIds(fresh(user, batch, selected)));
      JsonNode memories = analysis.path("memories");
      ArrayList<String> texts = new ArrayList<String>();
      for (JsonNode m : memories) {
        texts.add(m.path("content").asText());
      }
      List<float[]> vectors = ai.embed(texts);
      timing.mark("embed_memories");
      lab.tx.executeWithoutResult(
          s -> {
            Map<String, Object> row = lab.dao.mapper().lockUser(user);
            ObjectNode run = (ObjectNode) lab.dao.parse(row.get("run_state"));
            ObjectNode latest = details(user, batch);
            ObjectNode current = segment(latest, number);
            if ("saved".equals(current.path("status").asText())) {
              return;
            }
            if (run.path("savedPairs").asInt() != selected.path("from").asInt() - 1
                || !current.path("analysis").equals(analysis)) {
              throw LabException.conflict("구간 저장 순서 또는 초안이 변경되었습니다.");
            }
            validation.analysis(analysis, lab.sourceIds(fresh(user, batch, selected)));
            ObjectNode old = lab.dao.profile(user);
            ObjectNode merged = validation.merge(old, analysis.path("observations"));
            lab.dao.updateProfile(user, merged);
            int index = 0;
            List<Map<String, Object>> currentTurns = lab.dao.mapper().turns(batch);
            for (JsonNode m : memories) {
              lab.memory.memory(
                  user, batch, "segment:" + number, index, m, vectors.get(index), currentTurns);
              index++;
            }
            java.util.Set<String> completedIds = new java.util.HashSet<>();
            selected.path("sourceIds").forEach(id -> completedIds.add(id.asText()));
            lab.memory.complete(latest, currentTurns, completedIds);
            ArrayNode changes = current.putArray("changes");
            ObjectNode caps = current.putObject("maxWeights");
            for (JsonNode observation : analysis.path("observations")) {
              String code = observation.path("code").asText();
              caps.put(code, catalog.maxWeight(code));
              ObjectNode change = changes.addObject();
              change.put("code", code);
              change.set("previous", old.path(code).path("score"));
              change.set("observed", observation.path("score"));
              change.set("stored", merged.path(code).path("score"));
              if (old.path(code).path("score").isNumber()) {
                change.put(
                    "delta",
                    merged
                        .path(code)
                        .path("score")
                        .decimalValue()
                        .subtract(old.path(code).path("score").decimalValue()));
              } else {
                change.putNull("delta");
              }
            }
            current.put("updatePolicyVersion", catalog.policyVersion());
            current.put("status", "saved");
            long version = ((Number) row.get("profile_version")).longValue();
            current.put("profileVersion", version + 1);
            LinkedHashSet<String> ids = new LinkedHashSet<String>();
            for (JsonNode id : latest.path("processedSourceIds")) {
              ids.add(id.asText());
            }
            for (JsonNode id : current.path("sourceIds")) {
              ids.add(id.asText());
            }
            latest.set("processedSourceIds", lab.json.valueToTree(ids));
            latest.set("observations", analysis.path("observations"));
            latest.set("memories", analysis.path("memories"));
            latest.set("changes", changes);
            latest.put("updatePolicyVersion", catalog.policyVersion());
            latest.set("maxWeights", caps);
            if (lab.dao.mapper().incrementVersion(user, version) != 1) {
              throw LabException.conflict("프로필 저장 충돌");
            }
            run.put("savedPairs", current.path("to").asInt());
            run.put("completedPairs", current.path("to").asInt());
            run.put("completedBatches", number);
            run.put("analysisPhase", number == 100 ? "completed" : "waiting");
            run.put("heartbeatAt", System.currentTimeMillis());
            lab.dao
                .mapper()
                .pipelineDetails(batch, latest.toString(), number == 100 ? "saved" : "generated");
            lab.dao.mapper().runState(user, run.toString());
            lab.memory.retain(user, user, "owner");
          });
      timing.mark("merge_and_commit");
      timing.success();
    }
  }
}
