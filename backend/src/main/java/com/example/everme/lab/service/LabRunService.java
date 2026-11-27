package com.example.everme.lab.service;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import jakarta.annotation.PreDestroy;

/** One server process; checkpoints live on the user, not in an extra jobs table. */
@Service
@Profile("clone-lab")
public class LabRunService {
  // ##### 필드 #####
  private final LabService lab;
  private final LabPipeline pipeline;
  private final Set<UUID> active = ConcurrentHashMap.newKeySet();
  private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

  // ##### 생성자 #####
  public LabRunService(LabService lab, LabPipeline pipeline) {
    this.lab = lab;
    this.pipeline = pipeline;
  }

  // ##### 서버 재시작 후 실행 상태를 복구하는 메소드 #####
  public void recoverInterrupted() {
    for (UUID user : lab.dao.mapper().interruptedUsers()) {
      lab.tx.executeWithoutResult(
          s -> {
            Map<String, Object> row = lab.dao.mapper().lockUser(user);
            ObjectNode state = (ObjectNode) lab.dao.parse(row.get("run_state"));
            if (LabPipeline.MODE.equals(state.path("mode").asText()) && state.has("activeSince")) {
              state.put(
                  "wallElapsedMs",
                  state.path("wallElapsedMs").asLong()
                      + Math.max(
                          0,
                          state.path("heartbeatAt").asLong() - state.path("activeSince").asLong()));
              state.remove("activeSince");
            }
            state.put("status", "paused");
            state.put("error", "서버가 재시작되었습니다. 재개하면 현재 배치부터 이어집니다.");
            lab.dao.mapper().runState(user, state.toString());
          });
    }
  }

  // ##### 자동 실행 상태를 조회하는 메소드 #####
  public JsonNode state(UUID user) {
    ObjectNode state = (ObjectNode) lab.dao.parse(lab.dao.requireUser(user).get("run_state"));
    if (LabPipeline.MODE.equals(state.path("mode").asText()) && state.has("activeSince")) {
      state.put(
          "wallElapsedMs",
          state.path("wallElapsedMs").asLong()
              + Math.max(0, System.currentTimeMillis() - state.path("activeSince").asLong()));
    }
    return state;
  }

  // ##### 실행 또는 대화를 시작하는 메소드 #####
  public synchronized JsonNode start(UUID user, String topic, boolean resume) {
    if (active.contains(user)) {
      throw LabException.conflict("이미 자동 실행 중입니다.");
    }
    lab.tx.executeWithoutResult(
        s -> {
          Map<String, Object> row = lab.dao.mapper().lockUser(user);
          if (row == null) {
            throw new LabException(404, "테스트 사용자를 찾을 수 없습니다.");
          }
          JsonNode old = lab.dao.parse(row.get("run_state"));
          ObjectNode state;
          if (Set.of("running", "stopping").contains(old.path("status").asText())) {
            throw LabException.conflict("이미 자동 실행 중입니다.");
          }
          if (resume) {
            if (!Set.of("paused", "failed").contains(old.path("status").asText())) {
              throw LabException.conflict("재개할 실행이 없습니다.");
            }
            state = (ObjectNode) old.deepCopy();
          } else {
            if (Set.of("paused", "failed").contains(old.path("status").asText())) {
              throw LabException.conflict("이전 실행을 먼저 재개해 주세요. 새 실험은 다른 테스트 사용자를 만드세요.");
            }
            LabValidation.text(topic, 1000, "주제");
            state = lab.object();
            state.put("runId", UUID.randomUUID().toString());
            state.put("topic", topic);
            state.put("targetPairs", 1000);
            state.put("mode", LabPipeline.MODE);
            state.put("generationPhase", "waiting");
            state.put("analysisPhase", "waiting");
            state.put("generatedPairs", 0);
            state.put("analyzedPairs", 0);
            state.put("savedPairs", 0);
            state.put("wallElapsedMs", 0);
            state.put("llmElapsedMs", 0);
            state.put("cachedTokens", 0);
            state.put("retryCount", 0);
            UUID batch = UUID.randomUUID();
            state.put("batchId", batch.toString());
            lab.dao.mapper().createBatch(batch, user, topic, lab.dao.mapper().nextSequence(user));
            ObjectNode details = lab.object();
            details.putArray("segments");
            details.putArray("processedSourceIds");
            lab.dao.mapper().pipelineDetails(batch, details.toString(), "generated");
            state.put("completedBatches", 0);
            state.put("inputTokens", 0);
            state.put("outputTokens", 0);
            state.put("elapsedMs", 0);
          }
          if (LabPipeline.MODE.equals(state.path("mode").asText())) {
            state.put("activeSince", System.currentTimeMillis());
            state.put("heartbeatAt", System.currentTimeMillis());
          }
          state.put("status", "running");
          state.putNull("error");
          lab.dao.mapper().runState(user, state.toString());
        });
    active.add(user);
    executor.submit(() -> execute(user));
    return state(user);
  }

  // ##### 현재 작업 후 중단을 요청하는 메소드 #####
  public JsonNode stop(UUID user) {
    lab.tx.executeWithoutResult(
        s -> {
          Map<String, Object> row = lab.dao.mapper().lockUser(user);
          if (row == null) {
            throw new LabException(404, "사용자를 찾을 수 없습니다.");
          }
          ObjectNode state = (ObjectNode) lab.dao.parse(row.get("run_state"));
          if ("running".equals(state.path("status").asText())) {
            state.put("status", "stopping");
          }
          lab.dao.mapper().runState(user, state.toString());
        });
    return state(user);
  }

  // ##### 생성과 분석 작업을 병행 실행하는 메소드 #####
  private void execute(UUID user) {
    if (LabPipeline.MODE.equals(state(user).path("mode").asText())) {
      try {
        pipeline.execute(user);
      } finally {
        active.remove(user);
      }
      return;
    }
    if (!"single-session-v1".equals(state(user).path("mode").asText())) {
      executeLegacy(user);
      return;
    }
    try {
      while (!Thread.currentThread().isInterrupted()) {
        JsonNode state = state(user);
        if ("stopping".equals(state.path("status").asText())) {
          finish(user, "paused", null);
          return;
        }
        UUID batch =
            state.path("batchId").isTextual()
                ? UUID.fromString(state.path("batchId").asText())
                : null;
        if (state.path("generatedPairs").asInt() < 1000) {
          if (batch == null) {
            lab.generate(user, state.path("topic").asText(), true);
          } else {
            lab.appendAutomatic(user, batch);
          }
          continue;
        }
        JsonNode current = lab.batch(user, batch);
        if ("generated".equals(current.path("status").asText())) {
          checkpoint(user, "analyzing", null, null);
          lab.analyze(user, batch, true);
          continue;
        }
        if (!state.path("analysisCounted").asBoolean()) {
          checkpoint(user, "saving", "analysisCounted", current.path("analysis").path("metrics"));
          continue;
        }
        if ("analyzed".equals(current.path("status").asText())) {
          lab.save(user, batch, true);
          continue;
        }
        if ("saved".equals(current.path("status").asText())) {
          checkpoint(
              user, "completed", "mergeCounted", current.path("analysis").path("mergeMetrics"));
          finish(user, "completed", null);
          return;
        }
        throw LabException.conflict("자동 실행 대화 상태를 확인해 주세요.");
      }
      finish(user, "paused", "실행이 중단되었습니다.");
    } catch (Exception e) {
      finish(
          user,
          "failed",
          e instanceof LabException ? e.getMessage() : "자동 실행에 실패했습니다. 재개하면 같은 대화에서 이어집니다.");
    } finally {
      active.remove(user);
    }
  }

  private void checkpoint(UUID user, String phase, String counted, JsonNode metrics) {
    lab.tx.executeWithoutResult(
        s -> {
          Map<String, Object> row = lab.dao.mapper().lockUser(user);
          ObjectNode state = (ObjectNode) lab.dao.parse(row.get("run_state"));
          state.put("phase", phase);
          if (counted != null && !state.path(counted).asBoolean()) {
            for (String metric : List.of("inputTokens", "outputTokens", "elapsedMs")) {
              state.put(metric, state.path(metric).asLong() + metrics.path(metric).asLong());
            }
            state.put(counted, true);
          }
          lab.dao.mapper().runState(user, state.toString());
        });
  }

  private void executeLegacy(UUID user) {
    try {
      while (!Thread.currentThread().isInterrupted()) {
        JsonNode state = state(user);
        if ("stopping".equals(state.path("status").asText())) {
          finish(user, "paused", null);
          return;
        }
        if (state.path("completedBatches").asInt() >= 100) {
          finish(user, "completed", null);
          return;
        }
        UUID batch =
            state.path("batchId").isTextual()
                ? UUID.fromString(state.path("batchId").asText())
                : null;
        if (batch == null) {
          batch =
              UUID.fromString(
                  lab.generate(user, state.path("topic").asText(), true).path("id").asText());
        }
        JsonNode current = lab.batch(user, batch);
        if ("generated".equals(current.path("status").asText())) {
          current = lab.analyze(user, batch, true);
        }
        JsonNode saved =
            "saved".equals(current.path("status").asText()) ? null : lab.save(user, batch, true);
        JsonNode analyzed = current;
        JsonNode done = saved;
        lab.tx.executeWithoutResult(
            s -> {
              Map<String, Object> row = lab.dao.mapper().lockUser(user);
              ObjectNode checkpoint = (ObjectNode) lab.dao.parse(row.get("run_state"));
              checkpoint.put("completedBatches", checkpoint.path("completedBatches").asInt() + 1);
              checkpoint.put("completedPairs", checkpoint.path("completedBatches").asInt() * 10);
              for (String metric : List.of("inputTokens", "outputTokens", "elapsedMs")) {
                long total =
                    checkpoint.path(metric).asLong()
                        + checkpoint.path("generationMetrics").path(metric).asLong()
                        + analyzed.path("analysis").path("metrics").path(metric).asLong()
                        + (done == null
                            ? analyzed.path("analysis").path("mergeMetrics").path(metric).asLong()
                            : done.path("metrics").path(metric).asLong());
                checkpoint.put(metric, total);
              }
              checkpoint.remove(List.of("batchId", "generationMetrics"));
              lab.dao.mapper().runState(user, checkpoint.toString());
            });
      }
      finish(user, "paused", "실행이 중단되었습니다.");
    } catch (Exception e) {
      finish(
          user,
          "failed",
          e instanceof LabException ? e.getMessage() : "자동 실행에 실패했습니다. 서버 로그와 설정을 확인한 후 재개해 주세요.");
    } finally {
      active.remove(user);
    }
  }

  private void finish(UUID user, String status, String error) {
    lab.tx.executeWithoutResult(
        s -> {
          Map<String, Object> row = lab.dao.mapper().lockUser(user);
          ObjectNode state = (ObjectNode) lab.dao.parse(row.get("run_state"));
          state.put("status", status);
          if (error == null) {
            state.putNull("error");
          } else {
            state.put("error", error);
          }
          lab.dao.mapper().runState(user, state.toString());
        });
  }

  @PreDestroy
  public void close() {
    executor.shutdownNow();
  }
}
