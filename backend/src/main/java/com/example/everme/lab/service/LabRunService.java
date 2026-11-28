package com.example.everme.lab.service;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import jakarta.annotation.PreDestroy;

/** 자동 실행 체크포인트를 runs에 보존하고 서버 재시작 후 같은 구간에서 복구한다. */
@Service
@Profile("clone-lab")
public class LabRunService {
  // ##### 필드 #####
  private final LabService lab;
  private final LabPipeline pipeline;
  private final Set<Long> active = ConcurrentHashMap.newKeySet();
  private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

  // ##### 생성자 #####
  public LabRunService(LabService lab, LabPipeline pipeline) {
    this.lab = lab;
    this.pipeline = pipeline;
  }

  // ##### 서버 재시작 후 실행 상태를 복구하는 메소드 #####
  public void recoverInterrupted() {
    for (Long user : lab.dao.mapper().interruptedUsers()) {
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
  public JsonNode state(Long user) {
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
  public synchronized JsonNode start(Long user, String topic, boolean resume) {
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
            state.put("runId", lab.dao.mapper().nextId("runs", "run_id").toString());
            state.put("topic", topic);
            state.put("targetPairs", 1000);
            state.put("mode", LabPipeline.MODE);
            state.set("config", lab.config());
            state.put("generationPhase", "waiting");
            state.put("analysisPhase", "waiting");
            state.put("generatedPairs", 0);
            state.put("analyzedPairs", 0);
            state.put("savedPairs", 0);
            state.put("wallElapsedMs", 0);
            state.put("llmElapsedMs", 0);
            state.put("cachedTokens", 0);
            state.put("retryCount", 0);
            Long batch = lab.dao.mapper().nextId("sessions", "session_id");
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
  public JsonNode stop(Long user) {
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
  private void execute(Long user) {
    try {
      pipeline.execute(user);
    } finally {
      active.remove(user);
    }
  }

  @PreDestroy
  public void close() {
    executor.shutdownNow();
  }
}
