package com.example.everme.lab.service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
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

  static void addMetrics(Map<String, Object> state, Map<String, Object> metrics) {
    for (String name : List.of("inputTokens", "outputTokens", "cachedTokens")) {
      state.put(name, LabJson.longValue(state.get(name)) + LabJson.longValue(metrics.get(name)));
    }
    state.put(
        "llmElapsedMs",
        LabJson.longValue(state.get("llmElapsedMs")) + LabJson.longValue(metrics.get("elapsedMs")));
    state.put("elapsedMs", LabJson.longValue(state.get("llmElapsedMs")));
    state.put("heartbeatAt", System.currentTimeMillis());
  }

  // ##### 자동 실행 상태를 조회하는 메소드 #####
  private Map<String, Object> state(Long user) {
    return lab.dao.parseMap(lab.dao.requireUser(user).get("run_state"));
  }

  private boolean running(Long user, AtomicReference<String> error) {
    return error.get() == null && "running".equals(LabJson.text(state(user).get("status")));
  }

  // ##### 대화 배치를 조회하는 메소드 #####
  private Long batch(Map<String, Object> state) {
    return Long.valueOf(LabJson.text(state.get("batchId")));
  }

  private Map<String, Object> details(Long user, Long batch) {
    return lab.dao.parseMap(lab.dao.requireBatch(user, batch).get("analysis"));
  }

  // n번째 구간(1부터). 없으면 null.
  private Map<String, Object> segment(Map<String, Object> details, int number) {
    List<Object> segments = LabJson.list(details.get("segments"));
    if (number < 1 || number > segments.size()) {
      return null;
    }
    return LabJson.map(segments.get(number - 1));
  }

  // ##### 생성과 분석 작업을 병행 실행하는 메소드 #####
  public void execute(Long user) {
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
          Map<String, Object> run = lab.dao.parseMap(row.get("run_state"));
          long now = System.currentTimeMillis();
          run.put(
              "wallElapsedMs",
              LabJson.longValue(run.get("wallElapsedMs"))
                  + Math.max(0, now - LabJson.longValue(run.get("activeSince"), now)));
          run.remove("activeSince");
          run.put(
              "status",
              error.get() != null
                  ? "failed"
                  : LabJson.integer(run.get("savedPairs")) >= 1000 ? "completed" : "paused");
          run.put("error", error.get());
          lab.dao.mapper().runState(user, LabJson.write(lab.json, run));
          if ("completed".equals(LabJson.text(run.get("status")))) {
            Map<String, Object> snapshot = lab.object();
            snapshot.put("profileVersion", ((Number) row.get("profile_version")).longValue());
            snapshot.put("elements", lab.dao.profile(user));
            lab.dao.mapper().runResult(user, LabJson.write(lab.json, snapshot));
          }
        });
  }

  private void worker(Long user, AtomicReference<String> error, Object signal, boolean producer) {
    MDC.put("labRunId", LabJson.text(state(user).get("runId")));
    try (LabCallContext context =
        new LabCallContext(() -> running(user, error), () -> recordRetry(user))) {
      while (running(user, error)) {
        Map<String, Object> run = state(user);
        Long batch = batch(run);
        boolean worked = false;
        if (producer) {
          if (LabJson.integer(run.get("generatedPairs")) >= 1000) {
            return;
          }
          if (reserve(user, error)) {
            MDC.put(
                "labSegment",
                Integer.toString(
                    LabJson.integer(LabJson.path(state(user), "generationReservation", "from"))
                            / 10
                        + 1));
            lab.appendAutomatic(user, batch);
            worked = true;
          }
        } else {
          if (LabJson.integer(run.get("savedPairs")) >= 1000) {
            return;
          }
          int number = LabJson.integer(run.get("savedPairs")) / 10 + 1;
          List<Object> segments = LabJson.list(details(user, batch).get("segments"));
          if (segments.size() >= number) {
            Object selected = segments.get(number - 1);
            String phase =
                "generated".equals(LabJson.text(LabJson.path(selected, "status")))
                    ? "analyzing"
                    : "saving";
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
      if ("running".equals(LabJson.text(state(user).get("status")))) {
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

  private boolean reserve(Long user, AtomicReference<String> error) {
    return Boolean.TRUE.equals(
        lab.tx.execute(
            s -> {
              Map<String, Object> row = lab.dao.mapper().lockUser(user);
              Map<String, Object> run = lab.dao.parseMap(row.get("run_state"));
              int generated = LabJson.integer(run.get("generatedPairs"));
              if (error.get() != null
                  || !"running".equals(LabJson.text(run.get("status")))
                  || generated >= 1000
                  || generated - LabJson.integer(run.get("savedPairs")) + 10 > 20) {
                return false;
              }
              Map<String, Object> reservation = LabJson.object();
              reservation.put("from", generated + 1);
              reservation.put("to", generated + 10);
              run.put("generationReservation", reservation);
              run.put("generationPhase", "generating");
              run.put("heartbeatAt", System.currentTimeMillis());
              lab.dao.mapper().runState(user, LabJson.write(lab.json, run));
              return true;
            }));
  }

  private boolean admit(Long user, String phase, AtomicReference<String> error) {
    return Boolean.TRUE.equals(
        lab.tx.execute(
            s -> {
              Map<String, Object> row = lab.dao.mapper().lockUser(user);
              Map<String, Object> run = lab.dao.parseMap(row.get("run_state"));
              if (error.get() != null || !"running".equals(LabJson.text(run.get("status")))) {
                return false;
              }
              run.put("analysisPhase", phase);
              run.put("heartbeatAt", System.currentTimeMillis());
              lab.dao.mapper().runState(user, LabJson.write(lab.json, run));
              return true;
            }));
  }

  private void recordRetry(Long user) {
    lab.tx.executeWithoutResult(
        s -> {
          Map<String, Object> row = lab.dao.mapper().lockUser(user);
          Map<String, Object> run = lab.dao.parseMap(row.get("run_state"));
          run.put("retryCount", LabJson.integer(run.get("retryCount")) + 1);
          lab.dao.mapper().runState(user, LabJson.write(lab.json, run));
        });
  }

  private List<Object> fresh(Long user, Long batch, Map<String, Object> selected) {
    HashSet<String> ids = new HashSet<String>();
    for (Object id : LabJson.list(selected.get("sourceIds"))) {
      ids.add(LabJson.text(id));
    }
    Set<String> seen = lab.duplicateTexts(user);
    List<Object> result = LabJson.array();
    for (Map<String, Object> turn : lab.dao.mapper().turns(batch)) {
      if (ids.contains(turn.get("id").toString())
          && seen.add(LabService.normalized(turn.get("user_text").toString()))) {
        Map<String, Object> item = LabJson.object();
        item.put("id", turn.get("id").toString());
        item.put("text", turn.get("user_text").toString());
        result.add(item);
      }
    }
    return result;
  }

  // ##### 고정된 10쌍 구간을 분석하는 메소드 #####
  void analyzeSegment(Long user, Long batch, int number) {
    try (LabTiming timing = new LabTiming("pipeline.analyze")) {
      Map<String, Object> selected = LabJson.copy(segment(details(user, batch), number));
      if (!"generated".equals(LabJson.text(selected.get("status")))) {
        timing.success();
        return;
      }
      List<Object> sources = fresh(user, batch, selected);
      List<Object> summaries = LabJson.array();
      List<Map<String, Object>> turns = lab.dao.mapper().turns(batch);
      for (int prior = Math.max(1, number - 5); prior < number; prior++) {
        Map<String, Object> old = segment(details(user, batch), prior);
        if (!LabJson.isText(old.get("summary"))) {
          Map<String, Object> request = lab.object();
          List<Object> pairs = LabJson.childList(request, "turns");
          final Set<String> priorIds = new HashSet<String>();
          for (Object source : LabJson.list(old.get("sourceIds"))) {
            priorIds.add(LabJson.text(source));
          }
          for (Map<String, Object> turn :
              turns.stream().filter(t -> priorIds.contains(t.get("id").toString())).toList()) {
            Map<String, Object> pair = LabJson.object();
            pair.put("user", turn.get("user_text").toString());
            pair.put("assistant", turn.get("assistant_text").toString());
            pairs.add(pair);
          }
          Result result =
              ai.structured(
                  "summary",
                  LabPrompts.SEGMENT_SUMMARY,
                  request,
                  schemas.object(Map.of("summary", schemas.string())));
          LabValidation.text(LabJson.text(result.data().get("summary")), 1000, "대화 요약");
          final int index = prior;
          lab.tx.executeWithoutResult(
              s -> {
                Map<String, Object> row = lab.dao.mapper().lockUser(user);
                Map<String, Object> latest = details(user, batch);
                Map<String, Object> item = segment(latest, index);
                if (!item.containsKey("summary")) {
                  item.put("summary", result.data().get("summary"));
                  item.put("summaryMetrics", lab.metrics(result));
                  lab.dao
                      .mapper()
                      .pipelineDetails(batch, LabJson.write(lab.json, latest), "generated");
                  Map<String, Object> run = lab.dao.parseMap(row.get("run_state"));
                  addMetrics(run, lab.metrics(result));
                  lab.dao.mapper().runState(user, LabJson.write(lab.json, run));
                }
              });
          old = segment(details(user, batch), prior);
        }
        Map<String, Object> entry = LabJson.object();
        entry.put("from", old.get("from"));
        entry.put("to", old.get("to"));
        entry.put("summary", old.get("summary"));
        summaries.add(entry);
      }
      timing.mark("prepare_and_cache_summaries");
      Map<String, Object> input = lab.object();
      input.put("catalog", catalog.analysisDocument());
      input.put("sources", sources);
      input.put("summaries", summaries);
      input.put("maxMemories", 40);
      input.put("maxReasonLength", 1000);
      LabAi.Result result =
          sources.isEmpty()
              ? null
              : ai.structured("analysis", LabPrompts.ANALYSIS, input, schemas.analysis(catalog));
      Map<String, Object> analysis = result == null ? lab.object() : LabJson.copy(result.data());
      if (result == null) {
        analysis.put("observations", LabJson.array());
        analysis.put("memories", LabJson.array());
      } else {
        analysis.put("metrics", lab.metrics(result));
      }
      analysis.put("summaries", summaries);
      validation.analysis(analysis, lab.sourceIds(sources));
      timing.mark("analyze_and_validate");
      lab.tx.executeWithoutResult(
          s -> {
            Map<String, Object> row = lab.dao.mapper().lockUser(user);
            Map<String, Object> latest = details(user, batch);
            Map<String, Object> current = segment(latest, number);
            if (!"generated".equals(LabJson.text(current.get("status")))) {
              return;
            }
            if (!Objects.equals(current.get("sourceIds"), selected.get("sourceIds"))) {
              throw LabException.conflict("분석 대상이 변경되었습니다.");
            }
            current.put("analysis", analysis);
            current.put("status", "analyzed");
            for (Object id : LabJson.list(current.get("sourceIds"))) {
              lab.dao.mapper().contextStatus(Long.valueOf(LabJson.text(id)), "analyzed");
            }
            lab.dao.mapper().pipelineDetails(batch, LabJson.write(lab.json, latest), "generated");
            Map<String, Object> run = lab.dao.parseMap(row.get("run_state"));
            run.put("analyzedPairs", LabJson.integer(current.get("to")));
            run.put("analysisPhase", "ready_to_save");
            if (result != null) {
              addMetrics(run, lab.metrics(result));
            }
            lab.dao.mapper().runState(user, LabJson.write(lab.json, run));
          });
      timing.mark("persist_draft");
      timing.success();
    }
  }

  // ##### 구간 분석 결과를 원자적으로 저장하는 메소드 #####
  void saveSegment(Long user, Long batch, int number) {
    try (LabTiming timing = new LabTiming("pipeline.save")) {
      lab.repairEmbeddings(user);
      Map<String, Object> selected = LabJson.copy(segment(details(user, batch), number));
      if ("saved".equals(LabJson.text(selected.get("status")))) {
        timing.success();
        return;
      }
      if (!"analyzed".equals(LabJson.text(selected.get("status")))) {
        throw LabException.conflict("먼저 구간을 분석해 주세요.");
      }
      Map<String, Object> analysis = LabJson.map(selected.get("analysis"));
      List<Object> observations = LabJson.list(analysis.get("observations"));
      validation.analysis(analysis, lab.sourceIds(fresh(user, batch, selected)));
      lab.tx.executeWithoutResult(
          s -> {
            Map<String, Object> row = lab.dao.mapper().lockUser(user);
            Map<String, Object> run = lab.dao.parseMap(row.get("run_state"));
            Map<String, Object> latest = details(user, batch);
            Map<String, Object> current = segment(latest, number);
            if ("saved".equals(LabJson.text(current.get("status")))) {
              return;
            }
            if (LabJson.integer(run.get("savedPairs")) != LabJson.integer(selected.get("from")) - 1
                || !Objects.equals(current.get("analysis"), analysis)) {
              throw LabException.conflict("구간 저장 순서 또는 초안이 변경되었습니다.");
            }
            validation.analysis(analysis, lab.sourceIds(fresh(user, batch, selected)));
            Map<String, Object> old = lab.dao.profile(user);
            Map<String, Object> merged = validation.merge(old, observations);
            lab.dao.updateProfile(user, merged);
            List<Map<String, Object>> currentTurns = lab.dao.mapper().turns(batch);
            Set<String> completedIds = new HashSet<>();
            LabJson.list(selected.get("sourceIds")).forEach(id -> completedIds.add(LabJson.text(id)));
            lab.memory.complete(latest, currentTurns, completedIds);
            List<Object> changes = new ArrayList<Object>();
            current.put("changes", changes);
            Map<String, Object> caps = LabJson.object();
            current.put("maxWeights", caps);
            for (Object observation : observations) {
              String code = LabJson.text(LabJson.path(observation, "code"));
              caps.put(code, catalog.maxWeight(code));
              Object previousScore = LabJson.path(old, code, "score");
              Object storedScore = LabJson.path(merged, code, "score");
              Map<String, Object> change = LabJson.object();
              change.put("code", code);
              change.put("previous", previousScore);
              change.put("observed", LabJson.path(observation, "score"));
              change.put("stored", storedScore);
              if (LabJson.isNumber(previousScore)) {
                change.put(
                    "delta",
                    LabJson.decimal(storedScore).subtract(LabJson.decimal(previousScore)));
              } else {
                change.put("delta", null);
              }
              changes.add(change);
            }
            current.put("updatePolicyVersion", catalog.policyVersion());
            current.put("status", "saved");
            long version = ((Number) row.get("profile_version")).longValue();
            current.put("profileVersion", version + 1);
            LinkedHashSet<String> ids = new LinkedHashSet<String>();
            for (Object id : LabJson.list(latest.get("processedSourceIds"))) {
              ids.add(LabJson.text(id));
            }
            for (Object id : LabJson.list(current.get("sourceIds"))) {
              ids.add(LabJson.text(id));
            }
            latest.put("processedSourceIds", new ArrayList<Object>(ids));
            latest.put("observations", analysis.get("observations"));
            latest.put("memories", analysis.get("memories"));
            latest.put("changes", changes);
            latest.put("updatePolicyVersion", catalog.policyVersion());
            latest.put("maxWeights", caps);
            if (lab.dao.mapper().incrementVersion(user, version) != 1) {
              throw LabException.conflict("프로필 저장 충돌");
            }
            run.put("savedPairs", LabJson.integer(current.get("to")));
            run.put("completedPairs", LabJson.integer(current.get("to")));
            run.put("completedBatches", number);
            run.put("analysisPhase", number == 100 ? "completed" : "waiting");
            run.put("heartbeatAt", System.currentTimeMillis());
            lab.dao
                .mapper()
                .pipelineDetails(
                    batch, LabJson.write(lab.json, latest), number == 100 ? "saved" : "generated");
            lab.dao.mapper().runState(user, LabJson.write(lab.json, run));
            lab.memory.retain(user, user, "owner");
          });
      timing.mark("merge_and_commit");
      timing.success();
    }
  }
}
