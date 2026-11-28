package com.example.everme.lab.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.example.everme.lab.dao.LabDao;
import com.example.everme.lab.prompt.LabPrompts;
import com.example.everme.lab.service.LabAi.Result;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

@Service
@Profile("clone-lab")
public class LabService {
  // ##### 필드 #####
  final LabDao dao;
  final ObjectMapper json;
  final TransactionTemplate tx;
  private final LabCatalog catalog;
  private final LabSchemas schemas;
  private final LabAi ai;
  private final LabValidation validation;
  final LabMemoryService memory;

  // ##### 생성자 #####
  public LabService(
      LabDao dao,
      ObjectMapper json,
      TransactionTemplate tx,
      LabCatalog catalog,
      LabSchemas schemas,
      LabAi ai,
      LabValidation validation,
      LabMemoryService memory) {
    this.dao = dao;
    this.json = json;
    this.tx = tx;
    this.catalog = catalog;
    this.schemas = schemas;
    this.ai = ai;
    this.validation = validation;
    this.memory = memory;
  }

  ObjectNode object() {
    return json.createObjectNode();
  }

  private JsonNode tree(Object value) {
    return json.valueToTree(value);
  }

  // ##### 분석 요소 카탈로그를 조회하는 메소드 #####
  public JsonNode catalog() {
    return catalog.document();
  }

  // ##### 모델 설정을 조회하는 메소드 #####
  public JsonNode config() {
    return ai.models();
  }

  // ##### 테스트 사용자 목록을 조회하는 메소드 #####
  public JsonNode users() {
    return tree(dao.mapper().users());
  }

  // ##### 분석 프로필을 조회하는 메소드 #####
  public JsonNode profile(UUID user) {
    return tx.execute(
        s -> {
          Map<String, Object> row = dao.mapper().lockUser(user);
          if (row == null) {
            throw new LabException(404, "테스트 사용자를 찾을 수 없습니다.");
          }
          ObjectNode r = object();
          r.put("userId", user.toString());
          r.put("name", row.get("name").toString());
          r.put("version", ((Number) row.get("profile_version")).longValue());
          r.set("elements", dao.profile(user));
          return r;
        });
  }

  // ##### 테스트 사용자를 생성하는 메소드 #####
  public JsonNode createUser(String name, String seed) {
    LabValidation.text(name, 80, "이름");
    LabValidation.text(seed, 3000, "인물 설명");
    ObjectNode input = object();
    input.put("seed", seed);
    input.set("catalog", catalog.analysisDocument());
    Result result =
        ai.structured("generation", LabPrompts.PERSONA, input, schemas.persona(catalog));
    JsonNode persona = result.data();
    if (persona.path("scores").size() != 140 || persona.path("biography").asText().isBlank()) {
      throw LabException.invalid("가상 인물 설정 오류입니다.");
    }
    for (String code : catalog.codes()) {
      LabValidation.score(persona.path("scores").path(code));
    }
    UUID id = UUID.randomUUID();
    tx.executeWithoutResult(
        s -> {
          dao.mapper().createUser(id, name, persona.toString());
          for (String domain : catalog.domains().keySet()) {
            dao.mapper().initialize(domain, id);
          }
        });
    ObjectNode r = object();
    r.put("id", id.toString());
    r.put("name", name);
    r.set("metrics", metrics(result));
    return r;
  }

  ObjectNode metrics(LabAi.Result result) {
    ObjectNode r = object();
    r.put("model", result.model());
    r.put("inputTokens", result.inputTokens());
    r.put("outputTokens", result.outputTokens());
    r.put("elapsedMs", result.elapsedMs());
    r.put("cachedTokens", result.cachedTokens());
    r.put("retries", result.retries());
    return r;
  }

  void manualAllowed(Map<String, Object> row, boolean automatic) {
    JsonNode state = dao.parse(row.get("run_state"));
    if (!automatic
        && (Set.of("running", "stopping").contains(state.path("status").asText())
            || ("single-session-pipeline-v1".equals(state.path("mode").asText())
                && Set.of("paused", "failed").contains(state.path("status").asText())))) {
      throw LabException.conflict("미완료 자동 실행이 있어 수동 변경할 수 없습니다. 먼저 재개해 완료해 주세요.");
    }
  }

  // ##### 테스트 대화 10쌍을 생성하는 메소드 #####
  public JsonNode generate(UUID user, String topic, boolean automatic) {
    return generate(user, topic, automatic, null);
  }

  // ##### 같은 주제의 대화를 추가하는 메소드 #####
  public JsonNode append(UUID user, UUID batch) {
    return generate(user, dao.requireBatch(user, batch).get("topic").toString(), false, batch);
  }

  // ##### 자동 실행 대화를 추가하는 메소드 #####
  JsonNode appendAutomatic(UUID user, UUID batch) {
    return generate(user, dao.requireBatch(user, batch).get("topic").toString(), true, batch);
  }

  // ##### 테스트 대화 10쌍을 생성하는 메소드 #####
  private JsonNode generate(UUID user, String topic, boolean automatic, UUID existing) {
    try (LabTiming timing = new LabTiming("dialogue.generate")) {
      java.time.Instant receivedAt = java.time.Instant.now();
      topic = topic == null ? "" : topic.strip();
      if (topic.length() > 1000) {
        throw LabException.bad("주제를 확인해 주세요.");
      }
      Map<String, Object> row = dao.requireUser(user);
      manualAllowed(row, automatic);
      Map<String, Object> target = existing == null ? null : dao.requireBatch(user, existing);
      List<Map<String, Object>> original =
          existing == null ? List.<Map<String, Object>>of() : dao.mapper().turns(existing);
      // Generating test dialogue is not an analysis task: omit all 140 definitions
      // and target scores.
      ObjectNode input = object();
      input.put("topic", topic);
      input.put("continuation", existing != null);
      ArrayNode recent = json.createArrayNode();
      if (existing != null) {
        for (Map<String, Object> t : dao.mapper().recent(user)) {
          recent.add(
              tree(Map.of("user", t.get("user_text"), "assistant", t.get("assistant_text"))));
        }
      }
      input.set("recent", recent);
      timing.mark("load_db_and_prepare_input");
      Result result = ai.structured("generation", LabPrompts.GENERATION, input, schemas.turns());
      timing.mark("generate_llm");
      JsonNode turns = result.data().path("turns");
      if (!turns.isArray() || turns.size() != 10) {
        throw LabException.invalid("정확히 10쌍의 대화가 필요합니다.");
      }
      for (JsonNode t : turns) {
        LabValidation.text(t.path("user").asText(), 8000, "사용자 발언");
        LabValidation.text(t.path("assistant").asText(), 8000, "AI 응답");
      }
      timing.mark("validate_turns");
      UUID batch =
          storeTurns(
              user,
              topic,
              automatic,
              existing,
              target,
              original,
              turns,
              result,
              receivedAt,
              java.time.Instant.now(),
              true);
      timing.mark("store_turns_including_topic");
      ObjectNode r = (ObjectNode) batch(user, batch);
      r.set("metrics", metrics(result));
      timing.mark("read_response_from_db");
      timing.success();
      return r;
    }
  }

  // ##### 사용자 직접 입력을 처리하는 메소드 #####
  public JsonNode message(UUID user, UUID existing, String requestedTopic, String content) {
    try (LabTiming timing = new LabTiming("dialogue.message")) {
      java.time.Instant receivedAt = java.time.Instant.now();
      LabValidation.text(content, 8000, "메시지");
      manualAllowed(dao.requireUser(user), false);
      Map<String, Object> target = existing == null ? null : dao.requireBatch(user, existing);
      String topic =
          target != null
              ? target.get("topic").toString()
              : requestedTopic == null ? "" : requestedTopic.strip();
      if (topic.length() > 1000) {
        throw LabException.bad("주제를 확인해 주세요.");
      }
      List<Map<String, Object>> original =
          existing == null ? List.<Map<String, Object>>of() : dao.mapper().turns(existing);
      ObjectNode input = object();
      input.put("topic", topic);
      input.put("content", content);
      ArrayNode recent = input.putArray("recent");
      for (Map<String, Object> t : dao.mapper().recent(user)) {
        recent.add(tree(Map.of("user", t.get("user_text"), "assistant", t.get("assistant_text"))));
      }
      timing.mark("load_db_and_prepare_input");
      Result result =
          ai.structured(
              "answer",
              LabPrompts.OWNER_REPLY,
              input,
              schemas.object(Map.of("assistant", schemas.string())));
      timing.mark("answer_llm");
      String reply = result.data().path("assistant").asText();
      LabValidation.text(reply, 8000, "AI 응답");
      ArrayNode turns = json.createArrayNode();
      turns.addObject().put("user", content).put("assistant", reply);
      timing.mark("validate_turns");
      UUID batch =
          storeTurns(
              user,
              topic,
              false,
              existing,
              target,
              original,
              turns,
              result,
              receivedAt,
              java.time.Instant.now(),
              false);
      timing.mark("store_turns_including_topic");
      ObjectNode response = (ObjectNode) batch(user, batch);
      response.set("metrics", metrics(result));
      timing.mark("read_response_from_db");
      timing.success();
      return response;
    }
  }

  private UUID storeTurns(
      UUID user,
      String topic,
      boolean automatic,
      UUID existing,
      Map<String, Object> target,
      List<Map<String, Object>> original,
      JsonNode turns,
      LabAi.Result result,
      java.time.Instant receivedAt,
      java.time.Instant answeredAt,
      boolean synthetic) {
    try (LabTiming timing = new LabTiming("dialogue.store")) {
      String resolvedTopic = topic;
      if (topic.isBlank() && original.size() + turns.size() >= 10) {
        ObjectNode input = object();
        ArrayNode first = input.putArray("turns");
        for (Map<String, Object> turn : original) {
          if (first.size() == 10) {
            break;
          }
          first.add(
              tree(Map.of("user", turn.get("user_text"), "assistant", turn.get("assistant_text"))));
        }
        for (JsonNode turn : turns) {
          if (first.size() == 10) {
            break;
          }
          first.add(turn);
        }
        Result inferred =
            ai.structured(
                "summary",
                LabPrompts.TOPIC,
                input,
                schemas.object(Map.of("topic", schemas.string())));
        resolvedTopic = inferred.data().path("topic").asText().strip();
        LabValidation.text(resolvedTopic, 80, "자동 확정 주제");
      }
      timing.mark("infer_topic_if_required");
      List<float[]> turnVectors =
          ai.embed(
              java.util.stream.StreamSupport.stream(turns.spliterator(), false)
                  .map(
                      t ->
                          LabMemoryService.content(
                              t.path("user").asText(), t.path("assistant").asText()))
                  .toList());
      if (turnVectors.size() != turns.size()) throw LabException.invalid("대화 임베딩 개수가 다릅니다.");
      final String storedTopic = resolvedTopic;
      UUID batch = existing == null ? UUID.randomUUID() : existing;
      tx.executeWithoutResult(
          s -> {
            Map<String, Object> current = dao.mapper().lockUser(user);
            manualAllowed(current, automatic);
            int n = 0;
            ObjectNode run = (ObjectNode) dao.parse(current.get("run_state"));
            boolean pipeline = automatic && LabPipeline.MODE.equals(run.path("mode").asText());
            if (existing == null) {
              dao.mapper().createBatch(batch, user, storedTopic, dao.mapper().nextSequence(user));
            } else {
              Map<String, Object> now = dao.requireBatch(user, batch);
              List<Map<String, Object>> existingTurns = dao.mapper().turns(batch);
              if (!Objects.equals(now.get("turn_count"), target.get("turn_count"))
                  || (!pipeline
                      && (!Objects.equals(now.get("status"), target.get("status"))
                          || !Objects.equals(now.get("analysis"), target.get("analysis"))))) {
                throw LabException.conflict("대화 또는 분석이 변경되었습니다. 다시 추가해 주세요.");
              }
              ObjectNode checkpoint = object();
              JsonNode previousDetails = dao.parse(now.get("analysis"));
              for (String field : List.of("processedEvidence", "turnCount"))
                if (previousDetails.has(field)) checkpoint.set(field, previousDetails.path(field));
              ArrayNode processed = checkpoint.putArray("processedSourceIds");
              if ("saved".equals(now.get("status"))) {
                for (Map<String, Object> t : existingTurns) {
                  processed.add(t.get("id").toString());
                }
              } else {
                JsonNode ids = dao.parse(now.get("analysis")).path("processedSourceIds");
                if (ids.isArray()) {
                  checkpoint.set("processedSourceIds", ids);
                }
              }
              JsonNode cachedSummaries = dao.parse(now.get("analysis")).path("summaryCache");
              if (cachedSummaries.isObject()) {
                checkpoint.set("summaryCache", cachedSummaries);
              }
              if (!pipeline) {
                dao.mapper().appended(batch, checkpoint.toString());
                dao.mapper().resetDraft(batch);
              }
              n = ((Number) now.get("turn_count")).intValue();
              if (!storedTopic.equals(topic)) {
                dao.mapper().topic(batch, storedTopic);
              }
            }
            ArrayNode createdIds = json.createArrayNode();
            int from = n + 1;
            int vectorIndex = 0;
            for (JsonNode turn : turns) {
              UUID id = UUID.randomUUID();
              memory.turn(
                  id,
                  user,
                  user,
                  batch,
                  "owner",
                  ++n,
                  turn.path("user").asText(),
                  turn.path("assistant").asText(),
                  turnVectors.get(vectorIndex++),
                  receivedAt,
                  answeredAt,
                  synthetic,
                  null);
              createdIds.add(id.toString());
            }
            ObjectNode counted =
                (ObjectNode) dao.parse(dao.requireBatch(user, batch).get("analysis"));
            counted.put("turnCount", n);
            dao.mapper().pipelineDetails(batch, counted.toString(), "generated");
            memory.retain(user, user, "owner");
            if (pipeline) {
              if (run.path("generationReservation").path("from").asInt() != from
                  || !batch.toString().equals(run.path("batchId").asText())) {
                throw LabException.conflict("Generation reservation changed");
              }
              ObjectNode details =
                  (ObjectNode) dao.parse(dao.requireBatch(user, batch).get("analysis"));
              ObjectNode segment = details.withArray("segments").addObject();
              segment.put("number", from / 10 + 1);
              segment.put("from", from);
              segment.put("to", n);
              segment.set("sourceIds", createdIds);
              segment.put("status", "generated");
              segment.set("generationMetrics", metrics(result));
              dao.mapper().pipelineDetails(batch, details.toString(), "generated");
              run.put("generatedPairs", n);
              run.put("generationPhase", n >= 1000 ? "completed" : "waiting");
              run.remove("generationReservation");
              LabPipeline.addMetrics(run, metrics(result));
              dao.mapper().runState(user, run.toString());
            }
            if (automatic && !pipeline) {
              ObjectNode state = (ObjectNode) dao.parse(current.get("run_state"));
              state.put("batchId", batch.toString());
              if ("single-session-v1".equals(state.path("mode").asText())) {
                state.put("generatedPairs", n);
                state.put("completedPairs", n);
                state.put("completedBatches", n / 10);
                for (String metric : List.of("inputTokens", "outputTokens", "elapsedMs")) {
                  state.put(
                      metric, state.path(metric).asLong() + metrics(result).path(metric).asLong());
                }
              } else {
                state.set("generationMetrics", metrics(result));
              }
              dao.mapper().runState(user, state.toString());
            }
          });
      timing.mark("db_transaction");
      timing.success();
      return batch;
    }
  }

  // ##### 대화 배치 목록을 조회하는 메소드 #####
  public JsonNode batches(UUID user) {
    dao.requireUser(user);
    ArrayNode rows = json.createArrayNode();
    for (Map<String, Object> b : dao.mapper().batches(user)) {
      ObjectNode item = (ObjectNode) tree(b);
      item.remove("analysis");
      rows.add(item);
    }
    return rows;
  }

  // ##### 대화 배치를 조회하는 메소드 #####
  public JsonNode batch(UUID user, UUID batch) {
    Map<String, Object> row = dao.requireBatch(user, batch);
    ObjectNode r = (ObjectNode) tree(row);
    r.set("analysis", dao.parse(row.get("analysis")));
    r.set("turns", tree(dao.mapper().turns(batch)));
    return r;
  }

  static String normalized(String text) {
    return text.strip().replaceAll("\\s+", " ");
  }

  Set<String> duplicateTexts(UUID user) {
    HashSet<String> set = new HashSet<String>();
    for (Map<String, Object> row : dao.mapper().savedSources(user)) {
      set.add(normalized(row.get("user_text").toString()));
    }
    return set;
  }

  private ArrayNode freshSources(UUID user, UUID batch) {
    Set<String> seen = duplicateTexts(user);
    ArrayNode source = json.createArrayNode();
    for (Map<String, Object> t : dao.mapper().turns(batch)) {
      String text = t.get("user_text").toString();
      if (("pending".equals(t.get("status").toString())
              || "analyzed".equals(t.get("status").toString()))
          && seen.add(normalized(text))) {
        source.add(tree(Map.of("id", t.get("id").toString(), "text", text)));
      }
    }
    return source;
  }

  Set<String> sourceIds(JsonNode sources) {
    HashSet<String> set = new HashSet<String>();
    for (JsonNode s : sources) {
      set.add(s.path("id").asText());
    }
    return set;
  }

  // ##### 대화 분석을 실행하는 메소드 #####
  public JsonNode analyze(UUID user, UUID batch, boolean automatic) {
    try (LabTiming timing = new LabTiming("analysis.batch")) {
      manualAllowed(dao.requireUser(user), automatic);
      Map<String, Object> row = dao.requireBatch(user, batch);
      if ("saved".equals(row.get("status"))) {
        JsonNode response = batch(user, batch);
        timing.mark("already_saved");
        timing.success();
        return response;
      }
      int turnCount = ((Number) row.get("turn_count")).intValue();
      ArrayNode sources = freshSources(user, batch);
      timing.mark("load_sources");
      ObjectNode analysis = analyzeWindows(user, batch, sources);
      timing.mark("summaries_and_analysis_llm");
      validation.analysis(
          analysis, sourceIds(sources), Math.max(40, ((sources.size() + 9) / 10) * 40));
      timing.mark("validate_analysis");
      JsonNode processed = dao.parse(row.get("analysis")).path("processedSourceIds");
      if (processed.isArray()) {
        ((ObjectNode) analysis).set("processedSourceIds", processed);
      }
      analysis.put("turnCount", turnCount);
      JsonNode evidence = dao.parse(row.get("analysis")).path("processedEvidence");
      if (evidence.isArray()) analysis.set("processedEvidence", evidence);
      tx.executeWithoutResult(
          s -> {
            manualAllowed(dao.mapper().lockUser(user), automatic);
            if (((Number) dao.requireBatch(user, batch).get("turn_count")).intValue()
                != turnCount) {
              throw LabException.conflict("분석 중 대화가 추가되었습니다. 다시 분석해 주세요.");
            }
            if (dao.mapper().analysis(batch, analysis.toString()) != 1) {
              throw LabException.conflict("이미 저장된 배치입니다.");
            }
            dao.mapper().analyzed(batch);
          });
      timing.mark("save_analysis_draft");
      JsonNode response = batch(user, batch);
      timing.mark("read_response_from_db");
      timing.success();
      return response;
    }
  }

  private ObjectNode analyzeWindows(UUID user, UUID batch, ArrayNode sources) {
    ArrayList<Map<String, Object>> history = new ArrayList<>(dao.mapper().turns(batch));
    ObjectNode output = object();
    ArrayNode observations = output.putArray("observations");
    ArrayNode memories = output.putArray("memories");
    ArrayNode windows = output.putArray("windows");
    LinkedHashMap<String, List<JsonNode>> grouped = new LinkedHashMap<String, List<JsonNode>>();
    HashMap<Integer, JsonNode> summaries = new HashMap<Integer, JsonNode>();
    JsonNode cached = dao.parse(dao.requireBatch(user, batch).get("analysis")).path("summaryCache");
    if (cached.isObject()) {
      cached
          .fields()
          .forEachRemaining(
              entry -> summaries.put(Integer.parseInt(entry.getKey()), entry.getValue()));
    }
    long inputTokens = 0, outputTokens = 0, elapsed = 0, cachedTokens = 0;
    int retries = 0;
    for (int offset = 0; offset < sources.size(); offset += 10) {
      ArrayNode current = json.createArrayNode();
      for (int i = offset; i < Math.min(offset + 10, sources.size()); i++) {
        current.add(sources.get(i));
      }
      String first = current.get(0).path("id").asText();
      int before = 0;
      while (before < history.size() && !history.get(before).get("id").toString().equals(first)) {
        before++;
      }
      int complete = (((Number) history.get(before).get("ordinal")).intValue() - 1) / 10;
      ArrayNode context = json.createArrayNode();
      for (int block = Math.max(0, complete - 5); block < complete; block++) {
        if (!summaries.containsKey(block)) {
          ObjectNode request = object();
          ArrayNode turns = request.putArray("turns");
          final int blockStart = block * 10;
          for (Map<String, Object> t :
              history.stream()
                  .filter(
                      t ->
                          ((Number) t.get("ordinal")).intValue() > blockStart
                              && ((Number) t.get("ordinal")).intValue() <= blockStart + 10)
                  .toList()) {
            turns.add(
                tree(Map.of("user", t.get("user_text"), "assistant", t.get("assistant_text"))));
          }
          Result summary =
              ai.structured(
                  "summary",
                  LabPrompts.HISTORY_SUMMARY,
                  request,
                  schemas.object(Map.of("summary", schemas.string())));
          LabValidation.text(summary.data().path("summary").asText(), 1000, "대화 요약");
          summaries.put(block, summary.data());
          inputTokens += summary.inputTokens();
          outputTokens += summary.outputTokens();
          elapsed += summary.elapsedMs();
          cachedTokens += summary.cachedTokens();
          retries += summary.retries();
        }
        ObjectNode entry = context.addObject();
        entry.put("from", block * 10 + 1);
        entry.put("to", block * 10 + 10);
        entry.put("summary", summaries.get(block).path("summary").asText());
      }
      ObjectNode request = object();
      request.put("maxMemories", 40);
      request.put("maxReasonLength", 1000);
      request.set("sources", current);
      request.set("summaries", context);
      request.set("catalog", catalog.analysisDocument());
      Result result =
          ai.structured("analysis", LabPrompts.ANALYSIS, request, schemas.analysis(catalog));
      validation.analysis(result.data(), sourceIds(current));
      inputTokens += result.inputTokens();
      outputTokens += result.outputTokens();
      elapsed += result.elapsedMs();
      cachedTokens += result.cachedTokens();
      retries += result.retries();
      for (JsonNode observation : result.data().path("observations")) {
        grouped
            .computeIfAbsent(observation.path("code").asText(), k -> new ArrayList<>())
            .add(observation);
      }
      for (JsonNode memory : result.data().path("memories")) {
        memories.add(memory);
      }
      ObjectNode window = windows.addObject();
      window.set("sourceIds", tree(sourceIds(current)));
      window.set("summaries", context);
      window.set("observations", result.data().path("observations"));
    }
    for (List<JsonNode> group : grouped.values()) {
      ObjectNode combined = (ObjectNode) group.getFirst().deepCopy();
      LinkedHashSet<String> ids = new LinkedHashSet<String>();
      java.math.BigDecimal sum = java.math.BigDecimal.ZERO;
      int weight = 0;
      ArrayList<String> reasons = new ArrayList<String>();
      for (JsonNode item : group) {
        int strength = item.path("strength").asInt();
        weight += strength;
        sum =
            sum.add(
                item.path("score").decimalValue().multiply(java.math.BigDecimal.valueOf(strength)));
        for (JsonNode id : item.path("sourceIds")) {
          ids.add(id.asText());
        }
        reasons.add(item.path("reason").asText());
      }
      String explanation = String.join(" / ", new LinkedHashSet<>(reasons));
      if (explanation.length() > 2000) {
        explanation = explanation.substring(0, 1950) + "… 전체 근거 설명은 창별 분석을 참조하세요.";
      }
      combined.put(
          "score",
          sum.divide(java.math.BigDecimal.valueOf(weight), 2, java.math.RoundingMode.HALF_UP));
      combined.put("strength", Math.min(3, weight));
      combined.set("sourceIds", tree(ids));
      combined.put("reason", explanation);
      observations.add(combined);
    }
    ObjectNode cache = output.putObject("summaryCache");
    summaries.forEach((key, value) -> cache.set(key.toString(), value));
    ObjectNode metrics = output.putObject("metrics");
    metrics.put("inputTokens", inputTokens);
    metrics.put("outputTokens", outputTokens);
    metrics.put("elapsedMs", elapsed);
    metrics.put("cachedTokens", cachedTokens);
    metrics.put("retries", retries);
    return output;
  }

  // ##### 분석 결과를 저장하는 메소드 #####
  public JsonNode save(UUID user, UUID batch, boolean automatic) {
    try (LabTiming timing = new LabTiming("profile.save")) {
      Map<String, Object> row = dao.requireUser(user);
      manualAllowed(row, automatic);
      Map<String, Object> b = dao.requireBatch(user, batch);
      if ("saved".equals(b.get("status"))) {
        JsonNode response = profile(user);
        timing.mark("already_saved");
        timing.success();
        return response;
      }
      if (!"analyzed".equals(b.get("status"))) {
        throw LabException.conflict("먼저 분석해 주세요.");
      }
      JsonNode analysis = dao.parse(b.get("analysis"));
      ArrayNode sources = freshSources(user, batch);
      validation.analysis(
          analysis, sourceIds(sources), Math.max(40, ((sources.size() + 9) / 10) * 40));
      // Snapshot profile + version together. External calls must never run while
      // holding a DB lock.
      JsonNode snapshot = profile(user);
      long version = snapshot.path("version").asLong();
      ObjectNode old = (ObjectNode) snapshot.path("elements");
      ObjectNode input = object();
      input.set("previous", old);
      input.set("observations", analysis.path("observations"));
      ObjectNode caps = input.putObject("maxWeights");
      for (JsonNode observation : analysis.path("observations")) {
        String code = observation.path("code").asText();
        caps.put(code, catalog.maxWeight(code));
      }
      input.put("updatePolicyVersion", catalog.policyVersion());
      timing.mark("load_profile_validate_and_prepare_merge");
      ObjectNode merged = validation.merge(old, analysis.path("observations"));
      timing.mark("merge_server");
      JsonNode memories = analysis.path("memories");
      ArrayList<String> texts = new ArrayList<String>();
      for (JsonNode m : memories) {
        texts.add(m.path("content").asText());
      }
      ArrayList<float[]> vectors = new ArrayList<float[]>();
      for (int offset = 0; offset < texts.size(); offset += 256) {
        vectors.addAll(ai.embed(texts.subList(offset, Math.min(offset + 256, texts.size()))));
      }
      timing.mark("embed_memories");
      tx.executeWithoutResult(
          s -> {
            Map<String, Object> current = dao.mapper().lockUser(user);
            manualAllowed(current, automatic);
            Map<String, Object> currentBatch = dao.requireBatch(user, batch);
            if ("saved".equals(currentBatch.get("status"))) {
              return;
            }
            if (((Number) current.get("profile_version")).longValue() != version) {
              throw LabException.conflict("프로필이 변경되었습니다. 저장을 다시 실행해 주세요.");
            }
            if (!Objects.equals(currentBatch.get("analysis"), b.get("analysis"))) {
              throw LabException.conflict("분석 초안이 변경되었습니다. 결과를 확인한 뒤 저장해 주세요.");
            }
            validation.analysis(
                analysis,
                sourceIds(freshSources(user, batch)),
                Math.max(40, ((sources.size() + 9) / 10) * 40));
            dao.updateProfile(user, merged);
            int i = 0;
            List<Map<String, Object>> currentTurns = dao.mapper().turns(batch);
            for (JsonNode m : memories) {
              memory.memory(user, batch, "manual:" + version, i, m, vectors.get(i), currentTurns);
              i++;
            }
            ObjectNode persisted = (ObjectNode) analysis.deepCopy();
            persisted.put("updatePolicyVersion", catalog.policyVersion());
            persisted.set("maxWeights", input.path("maxWeights"));
            ArrayNode changes = persisted.putArray("changes");
            for (JsonNode observation : analysis.path("observations")) {
              String code = observation.path("code").asText();
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
            memory.complete(
                persisted,
                currentTurns,
                currentTurns.stream()
                    .filter(t -> !"saved".equals(t.get("status").toString()))
                    .map(t -> t.get("id").toString())
                    .collect(java.util.stream.Collectors.toSet()));
            if (dao.mapper().saveDetails(batch, persisted.toString()) != 1
                || dao.mapper().incrementVersion(user, version) != 1) {
              throw LabException.conflict("저장 충돌입니다.");
            }
            memory.retain(user, user, "owner");
          });
      timing.mark("db_transaction");
      ObjectNode response = (ObjectNode) profile(user);
      timing.mark("read_response_from_db");
      timing.success();
      return response;
    }
  }

  static String fingerprint(String text) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  // ##### 실험 결과를 내보내는 메소드 #####
  public JsonNode export(UUID user) {
    ObjectNode r = object();
    r.set("profile", profile(user));
    r.set("catalog", catalog.document());
    ArrayNode all = json.createArrayNode();
    for (Map<String, Object> b : dao.mapper().batches(user)) {
      all.add(batch(user, (UUID) b.get("id")));
    }
    r.set("batches", all);
    r.set("memories", tree(dao.mapper().memories(user)));
    r.set("run", dao.parse(dao.requireUser(user).get("run_state")));
    // Ground truth is evaluator-only and is never returned by normal profile/chat
    // APIs.
    JsonNode truth = dao.parse(dao.requireUser(user).get("persona")).path("scores");
    ArrayNode errors = json.createArrayNode();
    double sum = 0;
    int count = 0;
    for (String code : catalog.codes()) {
      JsonNode point = r.path("profile").path("elements").path(code);
      if (point.path("score").isNumber()) {
        double error = Math.abs(point.path("score").asDouble() - truth.path(code).asDouble());
        sum += error;
        count++;
        errors.add(tree(Map.of("code", code, "absoluteError", error)));
      }
    }
    ObjectNode evaluation = object();
    evaluation.put("observedElements", count);
    evaluation.put("totalElements", 140);
    if (count > 0) {
      evaluation.put("meanAbsoluteError", sum / count);
    } else {
      evaluation.putNull("meanAbsoluteError");
    }
    evaluation.set("errors", errors);
    evaluation.put("note", "합성 인물의 관찰된 요소만 비교한 실험 지표입니다. 실제 사람 재현 정확도를 의미하지 않습니다.");
    r.set("evaluation", evaluation);
    return r;
  }
}
