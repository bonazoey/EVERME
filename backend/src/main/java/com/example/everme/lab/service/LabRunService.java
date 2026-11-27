package com.example.everme.lab.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.annotation.PreDestroy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import java.util.*;
import java.util.concurrent.*;

/**
 * 1000쌍 자동 실행의 시작·중단·재개와 서버 재시작 복구.
 *
 * <p>실행 상태(체크포인트)는 별도 작업 테이블 없이 사용자 행의 run_state JSON에 둔다. 서버 프로세스는
 * 하나만 띄운다는 전제다. 실제 처리 방식은 run_state.mode로 고른다.
 *
 * <ul>
 *   <li>{@value LabPipeline#MODE}: 현재 방식. {@link LabPipeline}이 생성·분석·저장을 병행한다.
 *   <li>single-session-v1: 이전 방식. 1000쌍을 모두 생성한 뒤 한 번에 분석·저장한다({@link #executeSingleSession}).
 *   <li>그 외(mode 없음): 최초 방식. 10쌍 배치를 100번 생성·분석·저장한다({@link #executeLegacy}).
 * </ul>
 */
@Service
@Profile("clone-lab")
public class LabRunService {
    private static final int TARGET_PAIRS = 1000;

    private final LabService lab;
    private final LabPipeline pipeline;
    private final Set<UUID> active = ConcurrentHashMap.newKeySet();
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    public LabRunService(LabService lab, LabPipeline pipeline) {
        this.lab = lab;
        this.pipeline = pipeline;
    }

    private static boolean isPipeline(JsonNode state) {
        return LabPipeline.MODE.equals(state.path("mode").asText());
    }

    /** 서버 시작 시: 실행 중이던 상태를 paused로 바꾸고, 마지막 체크포인트까지의 활성 시간만 합산한다. */
    public void recoverInterrupted() {
        for (var user : lab.dao.mapper().interruptedUsers())
            lab.tx.executeWithoutResult(
                    s -> {
                        var row = lab.dao.mapper().lockUser(user);
                        var state = (ObjectNode) lab.dao.parse(row.get("run_state"));
                        if (isPipeline(state) && state.has("activeSince")) {
                            long active =
                                    Math.max(
                                            0,
                                            state.path("heartbeatAt").asLong()
                                                    - state.path("activeSince").asLong());
                            state.put("wallElapsedMs", state.path("wallElapsedMs").asLong() + active);
                            state.remove("activeSince");
                        }
                        state.put("status", "paused");
                        state.put("error", "서버가 재시작되었습니다. 재개하면 현재 배치부터 이어집니다.");
                        lab.dao.mapper().runState(user, state.toString());
                    });
    }

    /** 현재 실행 상태. 실행 중이면 지금까지의 활성 시간을 더해 보여준다(DB에는 쓰지 않음). */
    public JsonNode state(UUID user) {
        var state = (ObjectNode) lab.dao.parse(lab.dao.requireUser(user).get("run_state"));
        if (isPipeline(state) && state.has("activeSince")) {
            long active = Math.max(0, System.currentTimeMillis() - state.path("activeSince").asLong());
            state.put("wallElapsedMs", state.path("wallElapsedMs").asLong() + active);
        }
        return state;
    }

    /** 새 실행을 시작하거나(resume=false), paused/failed 실행을 재개한다(resume=true). */
    public synchronized JsonNode start(UUID user, String topic, boolean resume) {
        if (active.contains(user)) throw LabException.conflict("이미 자동 실행 중입니다.");
        lab.tx.executeWithoutResult(
                s -> {
                    var row = lab.dao.mapper().lockUser(user);
                    if (row == null) throw new LabException(404, "테스트 사용자를 찾을 수 없습니다.");
                    var old = lab.dao.parse(row.get("run_state"));
                    String status = old.path("status").asText();
                    if (Set.of("running", "stopping").contains(status))
                        throw LabException.conflict("이미 자동 실행 중입니다.");

                    ObjectNode state;
                    if (resume) {
                        if (!Set.of("paused", "failed").contains(status))
                            throw LabException.conflict("재개할 실행이 없습니다.");
                        state = (ObjectNode) old.deepCopy();
                    } else {
                        if (Set.of("paused", "failed").contains(status))
                            throw LabException.conflict("이전 실행을 먼저 재개해 주세요. 새 실험은 다른 테스트 사용자를 만드세요.");
                        LabValidation.text(topic, 1000, "주제");
                        state = newPipelineRun(user, topic);
                    }

                    if (isPipeline(state)) {
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

    /** 새 파이프라인 실행: 빈 배치 하나를 만들고 진행 카운터를 0으로 둔 run_state를 반환한다. */
    private ObjectNode newPipelineRun(UUID user, String topic) {
        var state = lab.object();
        state.put("runId", UUID.randomUUID().toString());
        state.put("topic", topic);
        state.put("targetPairs", TARGET_PAIRS);
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
        var details = lab.object();
        details.putArray("segments");
        details.putArray("processedSourceIds");
        lab.dao.mapper().pipelineDetails(batch, details.toString(), "generated");

        state.put("completedBatches", 0);
        state.put("inputTokens", 0);
        state.put("outputTokens", 0);
        state.put("elapsedMs", 0);
        return state;
    }

    /** 실행 중이면 stopping으로 표시한다. 진행 중인 단계만 마치고 멈춘다. */
    public JsonNode stop(UUID user) {
        lab.tx.executeWithoutResult(
                s -> {
                    var row = lab.dao.mapper().lockUser(user);
                    if (row == null) throw new LabException(404, "사용자를 찾을 수 없습니다.");
                    var state = (ObjectNode) lab.dao.parse(row.get("run_state"));
                    if ("running".equals(state.path("status").asText())) state.put("status", "stopping");
                    lab.dao.mapper().runState(user, state.toString());
                });
        return state(user);
    }

    private void execute(UUID user) {
        var state = state(user);
        if (isPipeline(state)) {
            try {
                pipeline.execute(user);
            } finally {
                active.remove(user);
            }
            return;
        }
        if ("single-session-v1".equals(state.path("mode").asText())) executeSingleSession(user);
        else executeLegacy(user);
    }

    /** 이전 방식(single-session-v1): 1000쌍을 모두 생성한 뒤 한 번에 분석·저장한다. */
    private void executeSingleSession(UUID user) {
        try {
            while (!Thread.currentThread().isInterrupted()) {
                var state = state(user);
                if ("stopping".equals(state.path("status").asText())) {
                    finish(user, "paused", null);
                    return;
                }
                UUID batch =
                        state.path("batchId").isTextual()
                                ? UUID.fromString(state.path("batchId").asText())
                                : null;

                if (state.path("generatedPairs").asInt() < TARGET_PAIRS) {
                    if (batch == null) lab.generate(user, state.path("topic").asText(), true);
                    else lab.appendAutomatic(user, batch);
                    continue;
                }
                var current = lab.batch(user, batch);
                String status = current.path("status").asText();
                if ("generated".equals(status)) {
                    checkpoint(user, "analyzing", null, null);
                    lab.analyze(user, batch, true);
                    continue;
                }
                if (!state.path("analysisCounted").asBoolean()) {
                    checkpoint(user, "saving", "analysisCounted", current.path("analysis").path("metrics"));
                    continue;
                }
                if ("analyzed".equals(status)) {
                    lab.save(user, batch, true);
                    continue;
                }
                if ("saved".equals(status)) {
                    checkpoint(user, "completed", "mergeCounted", current.path("analysis").path("mergeMetrics"));
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
                    e instanceof LabException
                            ? e.getMessage()
                            : "자동 실행에 실패했습니다. 재개하면 같은 대화에서 이어집니다.");
        } finally {
            active.remove(user);
        }
    }

    /** 진행 단계를 기록하고, counted 플래그가 처음 켜질 때만 사용량을 한 번 더한다(중복 집계 방지). */
    private void checkpoint(UUID user, String phase, String counted, JsonNode metrics) {
        lab.tx.executeWithoutResult(
                s -> {
                    var row = lab.dao.mapper().lockUser(user);
                    var state = (ObjectNode) lab.dao.parse(row.get("run_state"));
                    state.put("phase", phase);
                    if (counted != null && !state.path(counted).asBoolean()) {
                        for (String metric : List.of("inputTokens", "outputTokens", "elapsedMs"))
                            state.put(metric, state.path(metric).asLong() + metrics.path(metric).asLong());
                        state.put(counted, true);
                    }
                    lab.dao.mapper().runState(user, state.toString());
                });
    }

    /** 최초 방식: 10쌍 배치를 생성 → 분석 → 저장하는 과정을 100번 반복한다. */
    private void executeLegacy(UUID user) {
        try {
            while (!Thread.currentThread().isInterrupted()) {
                var state = state(user);
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
                if (batch == null)
                    batch =
                            UUID.fromString(
                                    lab.generate(user, state.path("topic").asText(), true)
                                            .path("id")
                                            .asText());
                var current = lab.batch(user, batch);
                if ("generated".equals(current.path("status").asText()))
                    current = lab.analyze(user, batch, true);
                JsonNode saved =
                        "saved".equals(current.path("status").asText()) ? null : lab.save(user, batch, true);
                completeLegacyBatch(user, current, saved);
            }
            finish(user, "paused", "실행이 중단되었습니다.");
        } catch (Exception e) {
            finish(
                    user,
                    "failed",
                    e instanceof LabException
                            ? e.getMessage()
                            : "자동 실행에 실패했습니다. 서버 로그와 설정을 확인한 후 재개해 주세요.");
        } finally {
            active.remove(user);
        }
    }

    /** 최초 방식의 배치 1개 완료 체크포인트: 완료 수를 올리고 생성·분석·병합 사용량을 더한다. */
    private void completeLegacyBatch(UUID user, JsonNode analyzed, JsonNode saved) {
        lab.tx.executeWithoutResult(
                s -> {
                    var row = lab.dao.mapper().lockUser(user);
                    var checkpoint = (ObjectNode) lab.dao.parse(row.get("run_state"));
                    checkpoint.put("completedBatches", checkpoint.path("completedBatches").asInt() + 1);
                    checkpoint.put("completedPairs", checkpoint.path("completedBatches").asInt() * 10);
                    for (var metric : List.of("inputTokens", "outputTokens", "elapsedMs")) {
                        long merge =
                                saved == null
                                        ? analyzed.path("analysis").path("mergeMetrics").path(metric).asLong()
                                        : saved.path("metrics").path(metric).asLong();
                        long total =
                                checkpoint.path(metric).asLong()
                                        + checkpoint.path("generationMetrics").path(metric).asLong()
                                        + analyzed.path("analysis").path("metrics").path(metric).asLong()
                                        + merge;
                        checkpoint.put(metric, total);
                    }
                    checkpoint.remove(List.of("batchId", "generationMetrics"));
                    lab.dao.mapper().runState(user, checkpoint.toString());
                });
    }

    private void finish(UUID user, String status, String error) {
        lab.tx.executeWithoutResult(
                s -> {
                    var row = lab.dao.mapper().lockUser(user);
                    var state = (ObjectNode) lab.dao.parse(row.get("run_state"));
                    state.put("status", status);
                    if (error == null) state.putNull("error");
                    else state.put("error", error);
                    lab.dao.mapper().runState(user, state.toString());
                });
    }

    @PreDestroy
    public void close() {
        executor.shutdownNow();
    }
}
