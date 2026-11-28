package com.example.everme.lab.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.example.everme.lab.dao.LabDao;
import com.example.everme.lab.prompt.LabPrompts;
import com.example.everme.lab.service.LabAi.Result;
import com.fasterxml.jackson.databind.ObjectMapper;

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
  final LabContextService contexts;

  // ##### 생성자 #####
  public LabService(
      LabDao dao,
      ObjectMapper json,
      TransactionTemplate tx,
      LabCatalog catalog,
      LabSchemas schemas,
      LabAi ai,
      LabValidation validation,
      LabMemoryService memory,
      LabContextService contexts) {
    this.dao = dao;
    this.json = json;
    this.tx = tx;
    this.catalog = catalog;
    this.schemas = schemas;
    this.ai = ai;
    this.validation = validation;
    this.memory = memory;
    this.contexts = contexts;
  }

  Map<String, Object> object() {
    return LabJson.object();
  }

  // DB 행(Map·List·날짜 등)을 응답용 Map·List 구조로 바꾼다.
  private Object tree(Object value) {
    return LabJson.convert(json, value);
  }

  private static Map<String, Object> pair(Object user, Object assistant) {
    Map<String, Object> turn = LabJson.object();
    turn.put("user", user);
    turn.put("assistant", assistant);
    return turn;
  }

  // ##### 정보이용자 원문 쌍을 현재 대화방 검색용으로 임베딩하는 메소드 #####
  List<float[]> embedViewerHistory(List<Map<String, Object>> pairs) {
    return ai.embed(
        pairs.stream()
            .map(
                pair ->
                    LabMemoryService.content(
                        pair.get("user_text").toString(), pair.get("assistant_text").toString()))
            .toList());
  }

  // ##### 분석 요소 카탈로그를 조회하는 메소드 #####
  public Map<String, Object> catalog() {
    return catalog.document();
  }

  // ##### 모델 설정을 조회하는 메소드 #####
  public Map<String, Object> config() {
    return ai.models();
  }

  // ##### 테스트 사용자 목록을 조회하는 메소드 #####
  public List<Object> users() {
    return LabJson.list(tree(dao.mapper().users()));
  }

  // ##### 분석 프로필을 조회하는 메소드 #####
  public Map<String, Object> profile(Long user) {
    return tx.execute(
        s -> {
          Map<String, Object> row = dao.mapper().lockUser(user);
          if (row == null) {
            throw new LabException(404, "테스트 사용자를 찾을 수 없습니다.");
          }
          Map<String, Object> r = object();
          r.put("userId", user.toString());
          r.put("name", row.get("name").toString());
          r.put("version", ((Number) row.get("profile_version")).longValue());
          r.put("elements", dao.profile(user));
          return r;
        });
  }

  // ##### 테스트 사용자를 생성하는 메소드 #####
  public Map<String, Object> createUser(String name, String seed) {
    LabValidation.text(name, 80, "이름");
    LabValidation.text(seed, 3000, "인물 설명");
    Map<String, Object> input = object();
    input.put("seed", seed);
    input.put("catalog", catalog.analysisDocument());
    Result result =
        ai.structured("generation", LabPrompts.PERSONA, input, schemas.persona(catalog));
    Map<String, Object> persona = result.data();
    Map<String, Object> scores = LabJson.map(persona.get("scores"));
    if (scores.size() != 140 || LabJson.text(persona.get("biography")).isBlank()) {
      throw LabException.invalid("가상 인물 설정 오류입니다.");
    }
    for (String code : catalog.codes()) {
      LabValidation.score(scores.get(code));
    }
    Long id = dao.mapper().nextId("users", "id");
    tx.executeWithoutResult(
        s -> {
          dao.mapper().createUser(id, name, LabJson.write(json, persona));
          for (String domain : catalog.domains().keySet()) {
            dao.mapper().initialize(domain, id);
          }
        });
    Map<String, Object> r = object();
    r.put("id", id.toString());
    r.put("name", name);
    r.put("metrics", metrics(result));
    return r;
  }

  Map<String, Object> metrics(LabAi.Result result) {
    Map<String, Object> r = object();
    r.put("model", result.model());
    r.put("inputTokens", result.inputTokens());
    r.put("outputTokens", result.outputTokens());
    r.put("elapsedMs", result.elapsedMs());
    r.put("cachedTokens", result.cachedTokens());
    r.put("retries", result.retries());
    return r;
  }

  void manualAllowed(Map<String, Object> row, boolean automatic) {
    Map<String, Object> state = dao.parseMap(row.get("run_state"));
    String status = LabJson.text(state.get("status"));
    if (!automatic
        && (Set.of("running", "stopping").contains(status)
            || ("single-session-pipeline-v1".equals(LabJson.text(state.get("mode")))
                && Set.of("paused", "failed").contains(status)))) {
      throw LabException.conflict("미완료 자동 실행이 있어 수동 변경할 수 없습니다. 먼저 재개해 완료해 주세요.");
    }
  }

  // ##### 테스트 대화 10쌍을 생성하는 메소드 #####
  public Map<String, Object> generate(Long user, String topic, boolean automatic) {
    return generate(user, topic, automatic, null);
  }

  // ##### 같은 주제의 대화를 추가하는 메소드 #####
  public Map<String, Object> append(Long user, Long batch) {
    return generate(user, dao.requireBatch(user, batch).get("topic").toString(), false, batch);
  }

  // ##### 자동 실행 대화를 추가하는 메소드 #####
  Map<String, Object> appendAutomatic(Long user, Long batch) {
    return generate(user, dao.requireBatch(user, batch).get("topic").toString(), true, batch);
  }

  // ##### 테스트 대화 10쌍을 생성하는 메소드 #####
  private Map<String, Object> generate(
      Long user, String topic, boolean automatic, Long existing) {
    try (LabTiming timing = new LabTiming("dialogue.generate")) {
      Instant receivedAt = Instant.now();
      topic = topic == null ? "" : topic.strip();
      if (topic.length() > 1000) {
        throw LabException.bad("주제를 확인해 주세요.");
      }
      Map<String, Object> row = dao.requireUser(user);
      manualAllowed(row, automatic);
      Map<String, Object> target = existing == null ? null : dao.requireBatch(user, existing);
      List<Map<String, Object>> original =
          existing == null ? List.<Map<String, Object>>of() : dao.mapper().turns(existing);
      // 인물 설정은 합성 대화 생성에만 전달하며 분석·대변인 답변에는 전달하지 않는다.
      Map<String, Object> input = object();
      input.put("topic", topic);
      input.put("persona", dao.parse(row.get("persona")));
      input.put("continuation", existing != null);
      if (existing != null) input.putAll(contexts.prepare(existing));
      timing.mark("load_db_and_prepare_input");
      Result result = ai.structured("generation", LabPrompts.GENERATION, input, schemas.turns());
      timing.mark("generate_llm");
      Object turnsValue = result.data().get("turns");
      List<Object> turns = LabJson.list(turnsValue);
      if (!LabJson.isList(turnsValue) || turns.size() != 10) {
        throw LabException.invalid("정확히 10쌍의 대화가 필요합니다.");
      }
      for (Object t : turns) {
        LabValidation.text(LabJson.text(LabJson.path(t, "user")), 8000, "사용자 발언");
        LabValidation.text(LabJson.text(LabJson.path(t, "assistant")), 8000, "AI 응답");
      }
      timing.mark("validate_turns");
      Long batch =
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
              Instant.now(),
              true);
      timing.mark("store_turns_including_topic");
      Map<String, Object> r = batch(user, batch);
      r.put("metrics", metrics(result));
      timing.mark("read_response_from_db");
      timing.success();
      return r;
    }
  }

  // ##### 사용자 직접 입력을 처리하는 메소드 #####
  public Map<String, Object> message(
      Long user, Long existing, String requestedTopic, String content) {
    try (LabTiming timing = new LabTiming("dialogue.message")) {
      Instant receivedAt = Instant.now();
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
      Map<String, Object> input = object();
      input.put("topic", topic);
      input.put("content", content);
      if (existing != null) input.putAll(contexts.prepare(existing));
      timing.mark("load_db_and_prepare_input");
      Result result =
          ai.structured(
              "answer",
              LabPrompts.OWNER_REPLY,
              input,
              schemas.object(Map.of("assistant", schemas.string())));
      timing.mark("answer_llm");
      String reply = LabJson.text(result.data().get("assistant"));
      LabValidation.text(reply, 8000, "AI 응답");
      List<Object> turns = LabJson.array();
      turns.add(pair(content, reply));
      timing.mark("validate_turns");
      Long batch =
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
              Instant.now(),
              false);
      timing.mark("store_turns_including_topic");
      Map<String, Object> response = batch(user, batch);
      response.put("metrics", metrics(result));
      timing.mark("read_response_from_db");
      timing.success();
      return response;
    }
  }

  private Long storeTurns(
      Long user,
      String topic,
      boolean automatic,
      Long existing,
      Map<String, Object> target,
      List<Map<String, Object>> original,
      List<Object> turns,
      LabAi.Result result,
      Instant receivedAt,
      Instant answeredAt,
      boolean synthetic) {
    try (LabTiming timing = new LabTiming("dialogue.store")) {
      String resolvedTopic = topic;
      if (topic.isBlank() && original.size() + turns.size() >= 10) {
        Map<String, Object> input = object();
        List<Object> first = LabJson.childList(input, "turns");
        for (Map<String, Object> turn : original) {
          if (first.size() == 10) {
            break;
          }
          first.add(pair(turn.get("user_text"), turn.get("assistant_text")));
        }
        for (Object turn : turns) {
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
        resolvedTopic = LabJson.text(inferred.data().get("topic")).strip();
        LabValidation.text(resolvedTopic, 80, "자동 확정 주제");
      }
      timing.mark("infer_topic_if_required");
      List<float[]> preparedVectors;
      try {
        preparedVectors =
            ai.embed(turns.stream().map(t -> LabJson.text(LabJson.path(t, "user"))).toList());
      } catch (LabException failure) {
        LoggerFactory.getLogger(LabService.class)
            .warn(
                "lab_embedding_deferred ownerId={} pairCount={} reason={}",
                user,
                turns.size(),
                failure.getMessage());
        preparedVectors = new ArrayList<>(Collections.nCopies(turns.size(), null));
      }
      final List<float[]> turnVectors = preparedVectors;
      if (turnVectors.size() != turns.size()) throw LabException.invalid("대화 임베딩 개수가 다릅니다.");
      final String storedTopic = resolvedTopic;
      Long batch = existing == null ? dao.mapper().nextId("sessions", "session_id") : existing;
      tx.executeWithoutResult(
          s -> {
            Map<String, Object> current = dao.mapper().lockUser(user);
            manualAllowed(current, automatic);
            int n = 0;
            Map<String, Object> run = dao.parseMap(current.get("run_state"));
            boolean pipeline = automatic && LabPipeline.MODE.equals(LabJson.text(run.get("mode")));
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
              Map<String, Object> checkpoint = object();
              Map<String, Object> previousDetails = dao.parseMap(now.get("analysis"));
              for (String field : List.of("processedEvidence", "turnCount"))
                if (previousDetails.containsKey(field))
                  checkpoint.put(field, previousDetails.get(field));
              List<Object> processed = LabJson.childList(checkpoint, "processedSourceIds");
              if ("saved".equals(now.get("status"))) {
                for (Map<String, Object> t : existingTurns) {
                  processed.add(t.get("id").toString());
                }
              } else {
                Object ids = previousDetails.get("processedSourceIds");
                if (LabJson.isList(ids)) {
                  checkpoint.put("processedSourceIds", ids);
                }
              }
              Object cachedSummaries = previousDetails.get("summaryCache");
              if (LabJson.isMap(cachedSummaries)) {
                checkpoint.put("summaryCache", cachedSummaries);
              }
              if (!pipeline) {
                dao.mapper().appended(batch, LabJson.write(json, checkpoint));
                dao.mapper().resetDraft(batch);
              }
              n = ((Number) now.get("turn_count")).intValue();
              if (!storedTopic.equals(topic)) {
                dao.mapper().topic(batch, storedTopic);
              }
            }
            List<Object> createdIds = LabJson.array();
            int from = n + 1;
            int vectorIndex = 0;
            for (Object turn : turns) {
              Long id = dao.mapper().nextId("session_messages", "id");
              memory.turn(
                  id,
                  user,
                  user,
                  batch,
                  "owner",
                  ++n,
                  LabJson.text(LabJson.path(turn, "user")),
                  LabJson.text(LabJson.path(turn, "assistant")),
                  turnVectors.get(vectorIndex++),
                  receivedAt,
                  answeredAt,
                  synthetic,
                  null);
              createdIds.add(id.toString());
            }
            Map<String, Object> counted =
                dao.parseMap(dao.requireBatch(user, batch).get("analysis"));
            counted.put("turnCount", n);
            dao.mapper().pipelineDetails(batch, LabJson.write(json, counted), "generated");
            memory.retain(user, user, "owner");
            if (pipeline) {
              if (LabJson.integer(LabJson.path(run, "generationReservation", "from")) != from
                  || !batch.toString().equals(LabJson.text(run.get("batchId")))) {
                throw LabException.conflict("Generation reservation changed");
              }
              Map<String, Object> details =
                  dao.parseMap(dao.requireBatch(user, batch).get("analysis"));
              Map<String, Object> segment = LabJson.object();
              LabJson.childList(details, "segments").add(segment);
              segment.put("number", from / 10 + 1);
              segment.put("from", from);
              segment.put("to", n);
              segment.put("sourceIds", createdIds);
              segment.put("status", "generated");
              segment.put("generationMetrics", metrics(result));
              dao.mapper().pipelineDetails(batch, LabJson.write(json, details), "generated");
              run.put("generatedPairs", n);
              run.put("generationPhase", n >= 1000 ? "completed" : "waiting");
              run.remove("generationReservation");
              LabPipeline.addMetrics(run, metrics(result));
              dao.mapper().runState(user, LabJson.write(json, run));
            }
            if (automatic && !pipeline) {
              Map<String, Object> state = dao.parseMap(current.get("run_state"));
              state.put("batchId", batch.toString());
              if ("single-session-v1".equals(LabJson.text(state.get("mode")))) {
                state.put("generatedPairs", n);
                state.put("completedPairs", n);
                state.put("completedBatches", n / 10);
                Map<String, Object> used = metrics(result);
                for (String metric : List.of("inputTokens", "outputTokens", "elapsedMs")) {
                  state.put(
                      metric,
                      LabJson.longValue(state.get(metric)) + LabJson.longValue(used.get(metric)));
                }
              } else {
                state.put("generationMetrics", metrics(result));
              }
              dao.mapper().runState(user, LabJson.write(json, state));
            }
          });
      timing.mark("db_transaction");
      contexts.recorded(batch);
      timing.success();
      return batch;
    }
  }

  // ##### 대화 배치 목록을 조회하는 메소드 #####
  public List<Object> batches(Long user) {
    dao.requireUser(user);
    List<Object> rows = LabJson.array();
    for (Map<String, Object> b : dao.mapper().batches(user)) {
      Map<String, Object> item = LabJson.map(tree(b));
      item.remove("analysis");
      rows.add(item);
    }
    return rows;
  }

  // ##### 대화 배치를 조회하는 메소드 #####
  public Map<String, Object> batch(Long user, Long batch) {
    Map<String, Object> row = dao.requireBatch(user, batch);
    Map<String, Object> r = LabJson.map(tree(row));
    r.put("analysis", dao.parse(row.get("analysis")));
    r.put("turns", tree(dao.mapper().turns(batch)));
    return r;
  }

  static String normalized(String text) {
    return text.strip().replaceAll("\\s+", " ");
  }

  Set<String> duplicateTexts(Long user) {
    HashSet<String> set = new HashSet<String>();
    for (Map<String, Object> row : dao.mapper().savedSources(user)) {
      set.add(normalized(row.get("user_text").toString()));
    }
    return set;
  }

  private List<Object> freshSources(Long user, Long batch) {
    Set<String> seen = duplicateTexts(user);
    List<Object> source = LabJson.array();
    for (Map<String, Object> t : dao.mapper().turns(batch)) {
      String text = t.get("user_text").toString();
      if (("pending".equals(t.get("status").toString())
              || "analyzed".equals(t.get("status").toString()))
          && seen.add(normalized(text))) {
        Map<String, Object> item = LabJson.object();
        item.put("id", t.get("id").toString());
        item.put("text", text);
        source.add(item);
      }
    }
    return source;
  }

  Set<String> sourceIds(List<Object> sources) {
    HashSet<String> set = new HashSet<String>();
    for (Object s : sources) {
      set.add(LabJson.text(LabJson.path(s, "id")));
    }
    return set;
  }

  // ##### 대화 분석을 실행하는 메소드 #####
  public Map<String, Object> analyze(Long user, Long batch, boolean automatic) {
    try (LabTiming timing = new LabTiming("analysis.batch")) {
      manualAllowed(dao.requireUser(user), automatic);
      Map<String, Object> row = dao.requireBatch(user, batch);
      if ("saved".equals(row.get("status"))) {
        Map<String, Object> response = batch(user, batch);
        timing.mark("already_saved");
        timing.success();
        return response;
      }
      int turnCount = ((Number) row.get("turn_count")).intValue();
      List<Object> sources = freshSources(user, batch);
      timing.mark("load_sources");
      Map<String, Object> analysis = analyzeWindows(user, batch, sources);
      timing.mark("summaries_and_analysis_llm");
      validation.analysis(
          analysis, sourceIds(sources), Math.max(40, ((sources.size() + 9) / 10) * 40));
      timing.mark("validate_analysis");
      Map<String, Object> previous = dao.parseMap(row.get("analysis"));
      Object processed = previous.get("processedSourceIds");
      if (LabJson.isList(processed)) {
        analysis.put("processedSourceIds", processed);
      }
      analysis.put("turnCount", turnCount);
      Object evidence = previous.get("processedEvidence");
      if (LabJson.isList(evidence)) analysis.put("processedEvidence", evidence);
      tx.executeWithoutResult(
          s -> {
            manualAllowed(dao.mapper().lockUser(user), automatic);
            if (((Number) dao.requireBatch(user, batch).get("turn_count")).intValue()
                != turnCount) {
              throw LabException.conflict("분석 중 대화가 추가되었습니다. 다시 분석해 주세요.");
            }
            if (dao.mapper().analysis(batch, LabJson.write(json, analysis)) != 1) {
              throw LabException.conflict("이미 저장된 배치입니다.");
            }
            dao.mapper().analyzed(batch);
          });
      timing.mark("save_analysis_draft");
      Map<String, Object> response = batch(user, batch);
      timing.mark("read_response_from_db");
      timing.success();
      return response;
    }
  }

  // ##### 임베딩 실패로 보류된 원문을 외부 호출 후 짧은 트랜잭션으로 보완하는 메소드 #####
  void repairEmbeddings(Long user) {
    List<Map<String, Object>> missing = dao.mapper().missingEmbeddings(user);
    for (int offset = 0; offset < missing.size(); offset += 100) {
      List<Map<String, Object>> group = missing.subList(offset, Math.min(offset + 100, missing.size()));
      List<float[]> vectors = ai.embed(group.stream().map(row -> row.get("user_text").toString()).toList());
      if (vectors.size() != group.size()) throw LabException.invalid("대화 임베딩 개수가 다릅니다.");
      tx.executeWithoutResult(
          status -> {
            dao.mapper().lockUser(user);
            for (int i = 0; i < group.size(); i++)
              dao.mapper()
                  .embedding(
                      ((Number) group.get(i).get("id")).longValue(), LabDao.vector(vectors.get(i)));
            memory.retain(user, user, "owner");
          });
    }
  }

  private Map<String, Object> analyzeWindows(Long user, Long batch, List<Object> sources) {
    ArrayList<Map<String, Object>> history = new ArrayList<>(dao.mapper().turns(batch));
    Map<String, Object> output = object();
    List<Object> observations = LabJson.childList(output, "observations");
    List<Object> memories = LabJson.childList(output, "memories");
    List<Object> windows = LabJson.childList(output, "windows");
    LinkedHashMap<String, List<Map<String, Object>>> grouped =
        new LinkedHashMap<String, List<Map<String, Object>>>();
    HashMap<Integer, Object> summaries = new HashMap<Integer, Object>();
    Object cached =
        dao.parseMap(dao.requireBatch(user, batch).get("analysis")).get("summaryCache");
    if (LabJson.isMap(cached)) {
      LabJson.map(cached).forEach((key, value) -> summaries.put(Integer.parseInt(key), value));
    }
    long inputTokens = 0, outputTokens = 0, elapsed = 0, cachedTokens = 0;
    int retries = 0;
    for (int offset = 0; offset < sources.size(); offset += 10) {
      List<Object> current = LabJson.array();
      for (int i = offset; i < Math.min(offset + 10, sources.size()); i++) {
        current.add(sources.get(i));
      }
      String first = LabJson.text(LabJson.path(current.get(0), "id"));
      int before = 0;
      while (before < history.size() && !history.get(before).get("id").toString().equals(first)) {
        before++;
      }
      int complete = (((Number) history.get(before).get("ordinal")).intValue() - 1) / 10;
      List<Object> context = LabJson.array();
      for (int block = Math.max(0, complete - 5); block < complete; block++) {
        if (!summaries.containsKey(block)) {
          Map<String, Object> request = object();
          List<Object> turns = LabJson.childList(request, "turns");
          final int blockStart = block * 10;
          for (Map<String, Object> t :
              history.stream()
                  .filter(
                      t ->
                          ((Number) t.get("ordinal")).intValue() > blockStart
                              && ((Number) t.get("ordinal")).intValue() <= blockStart + 10)
                  .toList()) {
            turns.add(pair(t.get("user_text"), t.get("assistant_text")));
          }
          Result summary =
              ai.structured(
                  "summary",
                  LabPrompts.HISTORY_SUMMARY,
                  request,
                  schemas.object(Map.of("summary", schemas.string())));
          LabValidation.text(LabJson.text(summary.data().get("summary")), 1000, "대화 요약");
          summaries.put(block, summary.data());
          inputTokens += summary.inputTokens();
          outputTokens += summary.outputTokens();
          elapsed += summary.elapsedMs();
          cachedTokens += summary.cachedTokens();
          retries += summary.retries();
        }
        Map<String, Object> entry = LabJson.object();
        entry.put("from", block * 10 + 1);
        entry.put("to", block * 10 + 10);
        entry.put("summary", LabJson.text(LabJson.path(summaries.get(block), "summary")));
        context.add(entry);
      }
      Map<String, Object> request = object();
      request.put("maxMemories", 40);
      request.put("maxReasonLength", 1000);
      request.put("sources", current);
      request.put("summaries", context);
      request.put("catalog", catalog.analysisDocument());
      Result result =
          ai.structured("analysis", LabPrompts.ANALYSIS, request, schemas.analysis(catalog));
      validation.analysis(result.data(), sourceIds(current));
      inputTokens += result.inputTokens();
      outputTokens += result.outputTokens();
      elapsed += result.elapsedMs();
      cachedTokens += result.cachedTokens();
      retries += result.retries();
      for (Object observation : LabJson.list(result.data().get("observations"))) {
        grouped
            .computeIfAbsent(
                LabJson.text(LabJson.path(observation, "code")), k -> new ArrayList<>())
            .add(LabJson.map(observation));
      }
      for (Object memory : LabJson.list(result.data().get("memories"))) {
        memories.add(memory);
      }
      Map<String, Object> window = LabJson.object();
      window.put("sourceIds", new ArrayList<Object>(sourceIds(current)));
      window.put("summaries", context);
      window.put("observations", result.data().get("observations"));
      windows.add(window);
    }
    for (List<Map<String, Object>> group : grouped.values()) {
      Map<String, Object> combined = LabJson.copy(group.getFirst());
      LinkedHashSet<String> ids = new LinkedHashSet<String>();
      BigDecimal sum = BigDecimal.ZERO;
      int weight = 0;
      ArrayList<String> reasons = new ArrayList<String>();
      for (Map<String, Object> item : group) {
        int strength = LabJson.integer(item.get("strength"));
        weight += strength;
        sum =
            sum.add(
                LabJson.decimal(item.get("score"))
                    .multiply(BigDecimal.valueOf(strength)));
        for (Object id : LabJson.list(item.get("sourceIds"))) {
          ids.add(LabJson.text(id));
        }
        reasons.add(LabJson.text(item.get("reason")));
      }
      String explanation = String.join(" / ", new LinkedHashSet<>(reasons));
      if (explanation.length() > 2000) {
        explanation = explanation.substring(0, 1950) + "… 전체 근거 설명은 창별 분석을 참조하세요.";
      }
      combined.put(
          "score",
          sum.divide(BigDecimal.valueOf(weight), 2, RoundingMode.HALF_UP));
      combined.put("strength", Math.min(3, weight));
      combined.put("sourceIds", new ArrayList<Object>(ids));
      combined.put("reason", explanation);
      observations.add(combined);
    }
    Map<String, Object> cache = LabJson.child(output, "summaryCache");
    summaries.forEach((key, value) -> cache.put(key.toString(), value));
    Map<String, Object> metrics = LabJson.child(output, "metrics");
    metrics.put("inputTokens", inputTokens);
    metrics.put("outputTokens", outputTokens);
    metrics.put("elapsedMs", elapsed);
    metrics.put("cachedTokens", cachedTokens);
    metrics.put("retries", retries);
    return output;
  }

  // ##### 분석 결과를 저장하는 메소드 #####
  public Map<String, Object> save(Long user, Long batch, boolean automatic) {
    try (LabTiming timing = new LabTiming("profile.save")) {
      Map<String, Object> row = dao.requireUser(user);
      manualAllowed(row, automatic);
      repairEmbeddings(user);
      Map<String, Object> b = dao.requireBatch(user, batch);
      if ("saved".equals(b.get("status"))) {
        Map<String, Object> response = profile(user);
        timing.mark("already_saved");
        timing.success();
        return response;
      }
      if (!"analyzed".equals(b.get("status"))) {
        throw LabException.conflict("먼저 분석해 주세요.");
      }
      Map<String, Object> analysis = dao.parseMap(b.get("analysis"));
      List<Object> observations = LabJson.list(analysis.get("observations"));
      List<Object> sources = freshSources(user, batch);
      validation.analysis(
          analysis, sourceIds(sources), Math.max(40, ((sources.size() + 9) / 10) * 40));
      // Snapshot profile + version together. External calls must never run while
      // holding a DB lock.
      Map<String, Object> snapshot = profile(user);
      long version = LabJson.longValue(snapshot.get("version"));
      Map<String, Object> old = LabJson.map(snapshot.get("elements"));
      Map<String, Object> input = object();
      input.put("previous", old);
      input.put("observations", observations);
      Map<String, Object> caps = LabJson.child(input, "maxWeights");
      for (Object observation : observations) {
        String code = LabJson.text(LabJson.path(observation, "code"));
        caps.put(code, catalog.maxWeight(code));
      }
      input.put("updatePolicyVersion", catalog.policyVersion());
      timing.mark("load_profile_validate_and_prepare_merge");
      Map<String, Object> merged = validation.merge(old, observations);
      timing.mark("merge_server");
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
            List<Map<String, Object>> currentTurns = dao.mapper().turns(batch);
            Map<String, Object> persisted = LabJson.copy(analysis);
            persisted.put("updatePolicyVersion", catalog.policyVersion());
            persisted.put("maxWeights", input.get("maxWeights"));
            List<Object> changes = LabJson.childList(persisted, "changes");
            for (Object observation : observations) {
              String code = LabJson.text(LabJson.path(observation, "code"));
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
            memory.complete(
                persisted,
                currentTurns,
                currentTurns.stream()
                    .filter(t -> Set.of("pending", "analyzed").contains(t.get("status").toString()))
                    .map(t -> t.get("id").toString())
                    .collect(Collectors.toSet()));
            if (dao.mapper().saveDetails(batch, LabJson.write(json, persisted)) != 1
                || dao.mapper().incrementVersion(user, version) != 1) {
              throw LabException.conflict("저장 충돌입니다.");
            }
            memory.retain(user, user, "owner");
          });
      timing.mark("db_transaction");
      Map<String, Object> response = profile(user);
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
  public Map<String, Object> export(Long user) {
    Map<String, Object> r = object();
    Map<String, Object> profile = profile(user);
    r.put("profile", profile);
    r.put("catalog", catalog.document());
    List<Object> all = LabJson.array();
    for (Map<String, Object> b : dao.mapper().batches(user)) {
      all.add(batch(user, (Long) b.get("id")));
    }
    r.put("batches", all);
    r.put("memories", tree(dao.mapper().memories(user)));
    r.put("run", dao.parse(dao.requireUser(user).get("run_state")));
    // Ground truth is evaluator-only and is never returned by normal profile/chat
    // APIs.
    Object truth = LabJson.path(dao.parse(dao.requireUser(user).get("persona")), "scores");
    List<Object> errors = LabJson.array();
    double sum = 0;
    int count = 0;
    for (String code : catalog.codes()) {
      Object score = LabJson.path(profile, "elements", code, "score");
      if (LabJson.isNumber(score)) {
        double error =
            Math.abs(
                LabJson.decimal(score).doubleValue()
                    - LabJson.decimal(LabJson.path(truth, code)).doubleValue());
        sum += error;
        count++;
        Map<String, Object> item = LabJson.object();
        item.put("code", code);
        item.put("absoluteError", error);
        errors.add(item);
      }
    }
    Map<String, Object> evaluation = object();
    evaluation.put("observedElements", count);
    evaluation.put("totalElements", 140);
    if (count > 0) {
      evaluation.put("meanAbsoluteError", sum / count);
    } else {
      evaluation.put("meanAbsoluteError", null);
    }
    evaluation.put("errors", errors);
    evaluation.put("note", "합성 인물의 관찰된 요소만 비교한 실험 지표입니다. 실제 사람 재현 정확도를 의미하지 않습니다.");
    r.put("evaluation", evaluation);
    return r;
  }
}
