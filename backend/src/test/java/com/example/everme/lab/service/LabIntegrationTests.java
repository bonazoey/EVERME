package com.example.everme.lab.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.example.everme.lab.prompt.LabPrompts;
import com.fasterxml.jackson.databind.*;
import java.math.*;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mockito.ArgumentCaptor;
import org.mockito.stubbing.Stubbing;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.memory.repository.jdbc.JdbcChatMemoryRepository;
import org.springframework.ai.document.Document;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("clone-lab")
@TestPropertySource(properties = "lab.initialize-schema=true")
@EnabledIfEnvironmentVariable(named = "LAB_TEST_DATABASE_URL", matches = ".+")
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class LabIntegrationTests {
  @DynamicPropertySource
  static void database(DynamicPropertyRegistry r) {
    r.add("spring.datasource.url", () -> System.getenv("LAB_TEST_DATABASE_URL"));
    r.add("spring.datasource.username", () -> "everme_lab");
    r.add("spring.datasource.password", () -> "everme_lab");
  }

  // ##### 필드 #####
  private final LabService lab;
  private final LabCloneService clone;
  private final LabRunService runs;
  private final LabPipeline pipeline;
  private final LabCloneTools cloneTools;
  private final ObjectMapper json;
  private final LabCatalog catalog;
  private final JdbcTemplate jdbc;
  private final MockMvc mvc;
  // MockitoBean은 필드 선언으로만 등록할 수 있어 생성자 주입 대상에서 제외한다.
  @MockitoBean OpenAiLabClient ai;

  // ##### 생성자 #####
  // @TestConstructor(autowireMode = ALL)로 Spring이 생성자 파라미터를 Bean으로 채운다.
  LabIntegrationTests(
      LabService lab,
      LabCloneService clone,
      LabRunService runs,
      LabPipeline pipeline,
      LabCloneTools cloneTools,
      ObjectMapper json,
      LabCatalog catalog,
      JdbcTemplate jdbc,
      MockMvc mvc) {
    this.lab = lab;
    this.clone = clone;
    this.runs = runs;
    this.pipeline = pipeline;
    this.cloneTools = cloneTools;
    this.json = json;
    this.catalog = catalog;
    this.jdbc = jdbc;
    this.mvc = mvc;
  }

  @BeforeEach
  void fixture() {
    reset(ai);
    when(ai.embed(anyList()))
        .thenAnswer(
            call -> {
              List<?> list = call.getArgument(0);
              return list.stream()
                  .map(
                      x -> {
                        float[] v = new float[1536];
                        v[0] = 1;
                        return v;
                      })
                  .toList();
            });
    when(ai.structured(anyString(), anyString(), anyMap(), anyMap()))
        .thenAnswer(
            call -> {
              String stage = call.getArgument(0);
              JsonView input = view(call.getArgument(2));
              Map<String, Object> data = LabJson.object();
              switch (stage) {
                case "generation" -> {
                  if (input.has("seed")) {
                    data.put("biography", "고정 테스트 인물");
                    data.put("facts", new ArrayList<Object>(List.of("매주 달린다")));
                    Map<String, Object> scores = LabJson.child(data, "scores");
                    catalog.codes().forEach(c -> scores.put(c, 70));
                  } else {
                    List<Object> turns = LabJson.childList(data, "turns");
                    String topic = input.path("topic").asText();
                    if (input.path("continuation").asBoolean() || topic.startsWith("자동 테스트"))
                      topic +=
                          " 추가 이야기 "
                              + ThreadLocalRandom.current()
                                  .nextLong(1, Long.MAX_VALUE);
                    for (int i = 0; i < 10; i++)
                      turns.add(
                          map("user", topic + " 나는 매주 " + i + "일 달린다", "assistant", "어떤 기분인가요?"));
                  }
                }
                case "summary" -> {
                  JsonView schema = view(call.getArgument(3));
                  if (schema.path("properties").has("topic")) data.put("topic", "운동과 일상 경험");
                  else data.put("summary", "The user described prior exercise experiences.");
                }
                case "analysis" -> {
                  String source = input.path("sources").get(0).path("id").asText();
                  data.put(
                      "observations",
                      new ArrayList<Object>(
                          List.of(
                              map(
                                  "code", "habits.exercise",
                                  "score", 80,
                                  "strength", 2,
                                  "reason", "사용자가 직접 반복 운동을 설명했다",
                                  "sourceIds", new ArrayList<Object>(List.of(source))))));
                  Map<String, Object> memory =
                      map(
                          "kind", "FACT",
                          "content", "이 사용자는 달리기를 한다",
                          "sourceIds", new ArrayList<Object>(List.of(source)));
                  memory.put("occurredAt", null);
                  data.put("memories", new ArrayList<Object>(List.of(memory)));
                }
                case "answer" -> data.put("assistant", "그런 일이 있었군요. 그때는 어떤 느낌이었나요?");
                default -> throw new IllegalArgumentException(stage);
              }
              return new LabAi.Result(data, "test-model", 10, 10, 1);
            });
  }

  void fixtureTurn(Long user, Long batch, int ordinal, String userText, String assistant) {
    float[] vector = new float[1536];
    vector[0] = 1;
    lab.memory.turn(
        ThreadLocalRandom.current().nextLong(1, Long.MAX_VALUE),
        user,
        user,
        batch,
        "owner",
        ordinal,
        userText,
        assistant,
        vector,
        Instant.now(),
        Instant.now(),
        false,
        null);
    Map<String, Object> details = lab.dao.parseMap(lab.dao.requireBatch(user, batch).get("analysis"));
    details.put("turnCount", ordinal);
    lab.dao.mapper().analysis(batch, LabJson.write(json, details));
  }

  // ----- 테스트 도우미: 결과 Map·List를 JsonNode처럼 읽기 전용으로 탐색한다 -----
  static JsonView view(Object value) {
    return new JsonView(value, false);
  }

  // 키·값을 번갈아 받아 순서 있는 수정 가능 Map을 만든다.
  static Map<String, Object> map(Object... pairs) {
    Map<String, Object> result = LabJson.object();
    for (int i = 0; i < pairs.length; i += 2) result.put(pairs[i].toString(), pairs[i + 1]);
    return result;
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  static ArgumentCaptor<Map<String, Object>> mapCaptor() {
    return (ArgumentCaptor) ArgumentCaptor.forClass(Map.class);
  }

  static final class JsonView implements Iterable<JsonView> {
    final Object value;
    final boolean missing;

    JsonView(Object value, boolean missing) {
      this.value = value;
      this.missing = missing;
    }

    JsonView path(String key) {
      if (value instanceof Map<?, ?> map && map.containsKey(key)) return view(map.get(key));
      return new JsonView(null, true);
    }

    JsonView get(int index) {
      if (value instanceof List<?> list && index >= 0 && index < list.size())
        return view(list.get(index));
      return new JsonView(null, true);
    }

    int size() {
      if (value instanceof Map<?, ?> map) return map.size();
      if (value instanceof List<?> list) return list.size();
      return 0;
    }

    boolean isEmpty() {
      return size() == 0;
    }

    boolean has(String key) {
      return value instanceof Map<?, ?> map && map.containsKey(key);
    }

    boolean isNull() {
      return !missing && value == null;
    }

    String asText() {
      return LabJson.text(value);
    }

    int asInt() {
      return LabJson.integer(value);
    }

    long asLong() {
      return LabJson.longValue(value);
    }

    boolean asBoolean() {
      return LabJson.bool(value);
    }

    double asDouble() {
      return LabJson.decimal(value).doubleValue();
    }

    JsonView deepCopy() {
      return new JsonView(LabJson.copy(value), missing);
    }

    Object raw() {
      return value;
    }

    @SuppressWarnings("unchecked")
    Map<String, Object> map() {
      return LabJson.map(value);
    }

    @Override
    public Iterator<JsonView> iterator() {
      return LabJson.list(value).stream().map(LabIntegrationTests::view).iterator();
    }

    @Override
    public boolean equals(Object other) {
      return other instanceof JsonView view && Objects.equals(value, view.value);
    }

    @Override
    public int hashCode() {
      return Objects.hashCode(value);
    }

    @Override
    public String toString() {
      return String.valueOf(value);
    }
  }

  @Test
  void slidingContextKeepsTwentyPairsAndLogicallyRemovesOnlyTheOldestPair() {
    Long user = user(), session = generated(user, "sliding context");
    for (int i = 11; i <= 20; i++) fixtureTurn(user, session, i, "user " + i, "reply " + i);
    lab.contexts.recorded(session);
    List<Map<String, Object>> before =
        jdbc.queryForList(
            "SELECT id FROM clone_lab.context WHERE conversation_id=? AND is_active ORDER BY id",
            session.toString());
    fixtureTurn(user, session, 21, "user 21", "reply 21");
    lab.contexts.recorded(session);
    JsonView input = view(lab.contexts.prepare(session));
    assertEquals(40, input.path("recent").size());
    assertEquals(0, input.path("summaries").size());
    assertEquals(
        42,
        jdbc.queryForObject(
            "SELECT count(*) FROM clone_lab.context WHERE conversation_id=?",
            Integer.class,
            session.toString()));
    assertEquals(
        2,
        jdbc.queryForObject(
            "SELECT count(*) FROM clone_lab.context WHERE conversation_id=? AND NOT is_active",
            Integer.class,
            session.toString()));
    assertEquals(
        38,
        jdbc
            .queryForList(
                "SELECT id FROM clone_lab.context WHERE conversation_id=? AND is_active ORDER BY"
                    + " id",
                session.toString())
            .stream()
            .filter(before::contains)
            .count());
    for (int i = 22; i <= 41; i++) fixtureTurn(user, session, i, "user " + i, "reply " + i);
    lab.contexts.recorded(session);
    input = view(lab.contexts.prepare(session));
    assertEquals(40, input.path("recent").size());
    assertEquals("user 22", input.path("recent").get(0).path("content").asText());
    assertEquals(1, input.path("summaries").size());
    assertEquals(41, lab.dao.mapper().turns(session).size());
    assertEquals(
        42,
        jdbc.queryForObject(
            "SELECT count(*) FROM clone_lab.context WHERE conversation_id=?",
            Integer.class,
            session.toString()));
    view(lab.contexts.prepare(session));
    assertEquals(
        42,
        jdbc.queryForObject(
            "SELECT count(*) FROM clone_lab.context WHERE conversation_id=?",
            Integer.class,
            session.toString()));
  }

  @Test
  void contextWindowAndFiveSummariesKeepPermanentHistoryAndSurviveRepositoryRecreation() {
    Long user = user(), batch = generated(user, "summary blocks");
    lab.tx.executeWithoutResult(
        t -> {
          for (int i = 11; i <= 121; i++) fixtureTurn(user, batch, i, "사용자 원문 " + i, "공감 " + i);
        });
    lab.contexts.recorded(batch);
    assertEquals(121, lab.dao.mapper().turns(batch).size());
    List<Map<String, Object>> summaries = lab.dao.mapper().summaries(batch);
    assertEquals(5, summaries.size());
    assertEquals(1, ((Number) summaries.getFirst().get("segment_no")).intValue());
    assertEquals(5, ((Number) summaries.getLast().get("segment_no")).intValue());
    JsonView input = view(lab.contexts.prepare(batch));
    assertEquals(5, input.path("summaries").size());
    assertEquals(40, input.path("recent").size());
    assertEquals("사용자 원문 102", input.path("recent").get(0).path("content").asText());
    assertEquals(
        40,
        jdbc.queryForObject(
            "SELECT count(*) FROM clone_lab.context WHERE conversation_id=? AND is_active",
            Integer.class,
            batch.toString()));
    JdbcChatMemoryRepository repository =
        new LabChatMemoryConfiguration()
            .labChatMemoryRepository(
                jdbc,
                new DataSourceTransactionManager(
                    jdbc.getDataSource()));
    MessageWindowChatMemory recreated =
        MessageWindowChatMemory.builder()
            .chatMemoryRepository(repository)
            .maxMessages(40)
            .build();
    assertEquals(40, recreated.get(batch.toString()).size());
    assertEquals("사용자 원문 102", recreated.get(batch.toString()).getFirst().getText());
  }

  @Test
  void sixthSummaryFailureKeepsExistingFiveAndCurrentContextUntilRetry() {
    Long user = user(), batch = generated(user, "summary retry");
    lab.tx.executeWithoutResult(
        t -> {
          for (int i = 11; i <= 139; i++) fixtureTurn(user, batch, i, "원문 " + i, "응답 " + i);
        });
    lab.contexts.recorded(batch);
    assertEquals(5, lab.dao.mapper().summaries(batch).size());
    List<Map<String, Object>> before =
        jdbc.queryForList(
            "SELECT content,type,timestamp FROM clone_lab.context WHERE conversation_id=? ORDER BY"
                + " timestamp",
            batch.toString());
    when(ai.structured(
            eq("summary"),
            eq(LabPrompts.CONTEXT_SUMMARY),
            anyMap(),
            anyMap()))
        .thenThrow(new LabException(502, "summary unavailable"));
    fixtureTurn(user, batch, 140, "user 140", "reply 140");
    assertThrows(LabException.class, () -> view(lab.contexts.prepare(batch)));
    assertEquals(5, lab.dao.mapper().summaries(batch).size());
    assertEquals(
        1, ((Number) lab.dao.mapper().summaries(batch).getFirst().get("segment_no")).intValue());
    assertEquals(
        before,
        jdbc.queryForList(
            "SELECT content,type,timestamp FROM clone_lab.context WHERE conversation_id=? ORDER BY"
                + " timestamp",
            batch.toString()));
    fixture();
    JsonView next = view(lab.contexts.prepare(batch));
    assertEquals(5, next.path("summaries").size());
    assertEquals(40, next.path("recent").size());
    assertEquals(
        2, ((Number) lab.dao.mapper().summaries(batch).getFirst().get("segment_no")).intValue());
    assertEquals(140, lab.dao.mapper().turns(batch).size());
  }

  @Test
  void malformedScopeCannotWritePermanentHistory() {
    Long owner = user(), viewer = user(), batch = generated(owner, "scope");
    float[] vector = new float[1536];
    assertThrows(
        LabException.class,
        () ->
            lab.tx.executeWithoutResult(
                t ->
                    lab.memory.turn(
                        lab.dao.mapper().nextId("session_messages", "id"),
                        owner,
                        viewer,
                        batch,
                        "owner",
                        11,
                        "wrong actor",
                        "response",
                        vector,
                        Instant.now(),
                        Instant.now(),
                        false,
                        null)));
    assertEquals(10, lab.dao.mapper().turns(batch).size());
  }

  Long user() {
    return Long.valueOf(view(lab.createUser("통합 테스트", "운동하는 사람")).path("id").asText());
  }

  Long generated(Long user, String topic) {
    return Long.valueOf(view(lab.generate(user, topic, false)).path("id").asText());
  }

  @Test
  void recentHundredRetentionPreservesEvidenceAndTimestampsAcrossSessions() {
    Long user = user(),
        batch =
            generated(
                user,
                "retention "
                    + ThreadLocalRandom.current().nextLong(1, Long.MAX_VALUE));
    for (int i = 0; i < 10; i++) view(lab.append(user, batch));
    assertEquals(110, lab.dao.mapper().turns(batch).size()); // 미처리 원문은 100쌍을 넘어도 보존한다.
    Map<String, Object> first = lab.dao.mapper().turns(batch).getFirst();
    String firstId = first.get("id").toString();
    JsonView meta = view(lab.dao.parse(first.get("metadata")));
    assertTrue(
        Instant.parse(meta.path("user_received_at").asText())
                .compareTo(Instant.parse(meta.path("assistant_received_at").asText()))
            <= 0);
    assertTrue(meta.path("synthetic").asBoolean());
    view(lab.analyze(user, batch, false));
    lab.save(user, batch, false);
    assertEquals(110, lab.dao.mapper().turns(batch).size());
    assertEquals(100, lab.dao.mapper().recent(user).size());
    assertEquals(
        10,
        jdbc.queryForObject(
            "SELECT count(*) FROM clone_lab.vector_store WHERE metadata->>'owner_id'=? AND"
                + " metadata->>'record_type'='conversation_archive'",
            Integer.class,
            user.toString()));
    assertEquals(
        1,
        jdbc.queryForObject(
            "SELECT count(*) FROM clone_lab.vector_store WHERE source_session_record_id=?::bigint"
                + " AND jsonb_array_length(metadata->'source_excerpts')=1",
            Integer.class,
            firstId));
    assertEquals(110, view(lab.batch(user, batch)).path("analysis").path("processedEvidence").size());
    Long next = generated(user, "new topic");
    JsonView profile = view(lab.profile(user)).path("elements").path("habits.exercise").deepCopy();
    view(lab.analyze(user, next, false));
    lab.save(user, next, false);
    assertEquals(110, lab.dao.mapper().turns(batch).size());
    assertEquals(100, lab.dao.mapper().recent(user).size());
    assertTrue(
        view(lab.profile(user)).path("elements").path("habits.exercise").path("weight").asInt()
            >= profile.path("weight").asInt());
    ArgumentCaptor<Map<String, Object>> inputs = mapCaptor();
    view(lab.message(user, next, null, "현재 이야기"));
    verify(ai).structured(eq("answer"), anyString(), inputs.capture(), anyMap());
    assertEquals(20, view(inputs.getValue()).path("recent").size());
  }

  @Test
  void archiveFailureKeepsRawContextAndRollsBackProfile() {
    Long user = user(), batch = generated(user, "archive rollback");
    for (int i = 0; i < 10; i++) view(lab.append(user, batch));
    view(lab.analyze(user, batch, false));
    String name = "archive_failure_" + user.toString().replace("-", "");
    jdbc.execute(
        "ALTER TABLE clone_lab.vector_store ADD CONSTRAINT "
            + name
            + " CHECK(metadata->>'owner_id'<>'"
            + user
            + "' OR metadata->>'record_type' IS DISTINCT FROM 'conversation_archive')");
    try {
      assertThrows(Exception.class, () -> lab.save(user, batch, false));
      assertEquals(110, lab.dao.mapper().turns(batch).size());
      assertEquals(0, view(lab.profile(user)).path("version").asInt());
      assertTrue(lab.dao.mapper().memories(user).isEmpty());
    } finally {
      jdbc.execute("ALTER TABLE clone_lab.vector_store DROP CONSTRAINT " + name);
    }
    lab.save(user, batch, false);
    assertEquals(110, lab.dao.mapper().turns(batch).size());
  }

  @Test
  void viewerSessionsSeparateActorsAndNeverUpdateOwnerProfile() {
    Long owner = user(),
        actor = user(),
        other = user(),
        batch = generated(owner, "private owner source");
    view(lab.analyze(owner, batch, false));
    lab.save(owner, batch, false);
    JsonView session = view(clone.start(owner, actor, null));
    JsonView otherSession = view(clone.start(owner, other, null));
    when(ai.respond(anyString(), anyList(), anyMap(), anyList()))
        .thenAnswer(
            call ->
                response(
                    "CONTEXT",
                    false,
                    List.of(
                        view(LabJson.read(
                                lab.json,
                                view(call.getArgument(1)).get(0).path("content").asText()))
                            .path("ownerSources")
                            .get(0)
                            .path("id")
                            .asText())));
    view(clone.answer(owner, actor, Long.valueOf(session.path("sessionId").asText()), "이용자만의 질문"));
    assertEquals(1, view(clone.history(owner, actor)).path("messages").size());
    assertEquals(0, view(clone.history(owner, other)).path("messages").size());
    assertThrows(
        LabException.class,
        () ->
            view(clone.answer(
                owner, other, Long.valueOf(session.path("sessionId").asText()), "다른 이용자 세션")));
    assertNotEquals(session.path("sessionId"), otherSession.path("sessionId"));
    assertEquals(1, view(lab.profile(owner)).path("version").asInt());
    assertEquals(10, lab.dao.mapper().recent(owner).size());
    assertEquals(
        1,
        jdbc.queryForObject(
            "SELECT count(*) FROM clone_lab.session_messages WHERE"
                + " metadata->>'conversation_type'='viewer' AND metadata->>'viewer_id'=?",
            Integer.class,
            actor.toString()));
  }

  @Test
  void ownerAndActorFiltersApplyToBothVectorStores() {
    Long owner = user(), other = user(), batch = generated(owner, "owner vector source");
    view(lab.analyze(owner, batch, false));
    lab.save(owner, batch, false);
    Long otherBatch = generated(other, "other vector source");
    view(lab.analyze(other, otherBatch, false));
    lab.save(other, otherBatch, false);
    Object retrieval = ReflectionTestUtils.getField(clone, "retrieval");
    for (Document document : ((LabRetrievalService) retrieval).context(owner, null, "source"))
      assertEquals(owner.toString(), document.getMetadata().get("owner_id"));
    List<Document> memories = ((LabRetrievalService) retrieval).memories(owner, "source");
    assertTrue(memories.isEmpty()); // 최근 100쌍 이내이므로 장기 복사 전이다.
    for (Document document : memories) {
      assertEquals(owner.toString(), document.getMetadata().get("owner_id"));
      assertEquals("owner", document.getMetadata().get("conversation_type"));
    }
  }

  @Test
  void mergeUsesServerPolicyAndAuditsCapsWithoutInflatingExistingWeight() {
    Long user = user();
    Map<String, Object> prior = view(lab.profile(user)).path("elements").map();
    LabJson.child(prior, "habits.exercise").putAll(map("score", 40, "weight", 200));
    LabJson.child(prior, "values.family").putAll(map("score", 40, "weight", 20));
    lab.tx.executeWithoutResult(s -> lab.dao.updateProfile(user, prior));
    Long batch = generated(user, "상한 검증");
    view(lab.analyze(user, batch, false));
    lab.save(user, batch, false);
    JsonView profile = view(lab.profile(user)).path("elements");
    assertEquals(46.67, profile.path("habits.exercise").path("score").asDouble());
    assertEquals(10, profile.path("habits.exercise").path("weight").asInt());
    assertEquals(20, profile.path("values.family").path("weight").asInt());
    JsonView analysis = view(lab.batch(user, batch)).path("analysis");
    assertEquals("element-cap-v1", analysis.path("updatePolicyVersion").asText());
    assertEquals(10, analysis.path("maxWeights").path("habits.exercise").asInt());
    assertEquals(1, analysis.path("maxWeights").size());
    verify(ai, never())
        .structured(eq("merge"), anyString(), anyMap(), anyMap());
  }

  @Test
  void mergeNoLongerCallsLegacyModel() {
    Long user = user(), batch = generated(user, "server merge");
    view(lab.analyze(user, batch, false));
    when(ai.structured(eq("merge"), anyString(), anyMap(), anyMap()))
        .thenThrow(new AssertionError("Merge LLM must not be called"));
    lab.save(user, batch, false);
    assertEquals(1, view(lab.profile(user)).path("version").asInt());
    verify(ai, never())
        .structured(eq("merge"), anyString(), anyMap(), anyMap());
  }

  @Test
  void blankTopicIsInferredOnceAtTenPairsAndUsesOnlyFirstTen() {
    Long user = user();
    JsonView first = view(lab.message(user, null, null, "첫 번째 이야기"));
    Long batch = Long.valueOf(first.path("id").asText());
    for (int i = 1; i < 9; i++) view(lab.message(user, batch, null, "이야기 " + i));
    assertEquals("", view(lab.batch(user, batch)).path("topic").asText());
    verify(ai, never())
        .structured(eq("summary"), anyString(), anyMap(), anyMap());
    JsonView tenth = view(lab.message(user, batch, null, "열 번째 이야기"));
    assertEquals("운동과 일상 경험", tenth.path("topic").asText());
    view(lab.message(user, batch, null, "열한 번째 다른 이야기"));
    ArgumentCaptor<Map<String, Object>> inputs = mapCaptor();
    verify(ai).structured(eq("summary"), anyString(), inputs.capture(), anyMap());
    assertEquals(10, view(inputs.getValue()).path("turns").size());
    assertEquals("첫 번째 이야기", view(inputs.getValue()).path("turns").get(0).path("user").asText());
    assertFalse(view(inputs.getValue()).has("persona"));
    assertEquals(0, view(lab.profile(user)).path("version").asInt());
    assertEquals("generated", view(lab.batch(user, batch)).path("status").asText());
  }

  @Test
  void blankGenerationInfersTopicButExplicitTopicIsPreserved() {
    Long user = user(), batch = generated(user, "");
    assertEquals("운동과 일상 경험", view(lab.batch(user, batch)).path("topic").asText());
    Long explicit = generated(user, "명시한 주제");
    assertEquals("명시한 주제", view(lab.batch(user, explicit)).path("topic").asText());
    verify(ai).structured(eq("summary"), anyString(), anyMap(), anyMap());
  }

  @Test
  void topicInferenceFailureDoesNotPartiallyAppendTenthPair() {
    Long user = user();
    Long batch = Long.valueOf(view(lab.message(user, null, null, "시작")).path("id").asText());
    for (int i = 1; i < 9; i++) view(lab.message(user, batch, null, "이야기 " + i));
    doThrow(new LabException(502, "unavailable"))
        .when(ai)
        .structured(eq("summary"), anyString(), anyMap(), anyMap());
    assertThrows(LabException.class, () -> view(lab.message(user, batch, null, "열 번째")));
    assertEquals(9, view(lab.batch(user, batch)).path("turns").size());
    assertEquals("", view(lab.batch(user, batch)).path("topic").asText());
  }

  @Test
  void previousConversationIsNotIncludedInCurrentSummaries() {
    Long user = user(),
        previous = ThreadLocalRandom.current().nextLong(1, Long.MAX_VALUE);
    lab.tx.executeWithoutResult(
        status -> {
          lab.dao.mapper().createBatch(previous, user, "previous conversation", 1);
          for (int i = 0; i < 33; i++)
            fixtureTurn(user, previous, i + 1, "previous experience " + i, "previous conversation");
        });
    Long current = generated(user, "previous conversation");
    JsonView result = view(lab.analyze(user, current, false));
    assertEquals(0, result.path("analysis").path("windows").get(0).path("summaries").size());
    ArgumentCaptor<Map<String, Object>> requests = mapCaptor();
    verify(ai).structured(eq("analysis"), anyString(), requests.capture(), anyMap());
    assertEquals(10, view(requests.getValue()).path("sources").size());
    assertEquals(0, view(requests.getValue()).path("summaries").size());
    assertFalse(view(requests.getValue()).has("persona"));
  }

  @Test
  void summariesAreLimitedToFiveAndAllFortyThreePairsAreAnalyzed() {
    Long user = user(),
        previous = ThreadLocalRandom.current().nextLong(1, Long.MAX_VALUE);
    lab.tx.executeWithoutResult(
        status -> {
          lab.dao.mapper().createBatch(previous, user, "previous conversation", 1);
          for (int i = 0; i < 70; i++)
            fixtureTurn(user, previous, i + 1, "older experience " + i, "previous conversation");
        });
    Long current = generated(user, "previous conversation");
    view(lab.append(user, current));
    view(lab.append(user, current));
    view(lab.append(user, current));
    for (int i = 0; i < 3; i++) view(lab.message(user, current, null, "typed experience " + i));
    JsonView result = view(lab.analyze(user, current, false));
    JsonView windows = result.path("analysis").path("windows");
    assertEquals(5, windows.size());
    int count = 0;
    int windowIndex = 0;
    for (JsonView window : windows) {
      assertEquals(Math.min(5, windowIndex++), window.path("summaries").size());
      count += window.path("sourceIds").size();
    }
    assertEquals(43, count);
    lab.save(user, current, false);
    assertEquals(1, view(lab.profile(user)).path("version").asInt());
  }

  @Test
  void testDialogueGenerationUsesSelectedPersona() {
    Long user = user();
    generated(user, "여행 준비");
    ArgumentCaptor<Map<String, Object>> inputs = mapCaptor();
    ArgumentCaptor<String> prompts = ArgumentCaptor.forClass(String.class);
    verify(ai, times(2))
        .structured(eq("generation"), prompts.capture(), inputs.capture(), anyMap());
    JsonView request = view(inputs.getAllValues().getLast());
    assertEquals("여행 준비", request.path("topic").asText());
    assertFalse(request.has("catalog"));
    assertEquals(view(lab.dao.parse(lab.dao.requireUser(user).get("persona"))), request.path("persona"));
    assertFalse(request.has("biography"));
    assertFalse(request.has("facts"));
    String prompt = prompts.getAllValues().getLast();
    assertTrue(prompt.contains("사용자가 대화를 주도"));
    assertTrue(prompt.contains("공감·경청·맞장구"));
    assertTrue(prompt.contains("새 주제로 전환"));
    Long batch = generated(user, "최근 대화 참고");
    ArgumentCaptor<Map<String, Object>> third = mapCaptor();
    verify(ai, times(3))
        .structured(eq("generation"), anyString(), third.capture(), anyMap());
    assertEquals(0, view(third.getAllValues().getLast()).path("recent").size());
    view(lab.append(user, batch));
    ArgumentCaptor<Map<String, Object>> continued = mapCaptor();
    verify(ai, times(4))
        .structured(eq("generation"), anyString(), continued.capture(), anyMap());
    for (Map<String, Object> captured : continued.getAllValues().subList(3, 4)) {
      JsonView input = view(captured);
      assertEquals(request.path("persona"), input.path("persona"));
      assertTrue(input.path("continuation").asBoolean());
    }
  }

  @Test
  void appendKeepsOriginalTurnsAndClearsUnsavedAnalysis() {
    Long user = user(), batch = generated(user, "추가 대화");
    JsonView original = view(lab.batch(user, batch)).path("turns").deepCopy();
    view(lab.analyze(user, batch, false));
    JsonView result = view(lab.append(user, batch));
    assertEquals(batch.toString(), result.path("id").asText());
    assertEquals(20, result.path("turns").size());
    assertEquals("generated", result.path("status").asText());
    assertFalse(result.path("analysis").has("observations"));
    assertEquals(0, view(lab.profile(user)).path("version").asInt());
    for (int i = 0; i < 10; i++) assertEquals(original.get(i), result.path("turns").get(i));
    assertEquals(20, result.path("turns").get(19).path("ordinal").asInt());
    view(lab.analyze(user, batch, false));
    lab.save(user, batch, false);
    assertEquals(1, view(lab.profile(user)).path("version").asInt());
  }

  @Test
  void appendAfterSaveDoesNotApplyExistingEvidenceTwice() {
    Long user = user(), batch = generated(user, "저장 후 추가");
    view(lab.analyze(user, batch, false));
    lab.save(user, batch, false);
    view(lab.append(user, batch));
    assertEquals(1, view(lab.profile(user)).path("version").asInt());
    assertEquals(
        2, view(lab.profile(user)).path("elements").path("habits.exercise").path("weight").asInt());
    view(lab.analyze(user, batch, false));
    ArgumentCaptor<Map<String, Object>> inputs = mapCaptor();
    verify(ai, times(2))
        .structured(eq("analysis"), anyString(), inputs.capture(), anyMap());
    assertEquals(10, view(inputs.getAllValues().getLast()).path("sources").size());
    lab.save(user, batch, false);
    assertEquals(
        4, view(lab.profile(user)).path("elements").path("habits.exercise").path("weight").asInt());
    assertEquals(2, view(lab.profile(user)).path("version").asInt());
    assertEquals(30, view(lab.append(user, batch)).path("turns").size());
  }

  @Test
  void appendFailureLeavesOriginalBatchUntouched() {
    Long user = user(), other = user(), batch = generated(user, "실패 검증");
    JsonView original = view(lab.batch(user, batch));
    assertThrows(LabException.class, () -> view(lab.append(other, batch)));
    doThrow(new LabException(502, "generation failed"))
        .when(ai)
        .structured(eq("generation"), anyString(), anyMap(), anyMap());
    assertThrows(LabException.class, () -> view(lab.append(user, batch)));
    assertEquals(original, view(lab.batch(user, batch)));
  }

  @Test
  void typedMessageStartsConversationAndIsAnAnalysisSource() {
    Long user = user();
    String content = "오늘 저녁에는 친구와 함께 운동했어요.\n기분이 좋았어요.";
    JsonView result = view(lab.message(user, null, null, content));
    Long batch = Long.valueOf(result.path("id").asText());
    assertEquals(1, result.path("turns").size());
    assertEquals(content, result.path("turns").get(0).path("user_text").asText());
    assertEquals("", result.path("topic").asText());
    assertFalse(result.path("turns").get(0).path("assistant_text").asText().isBlank());
    assertEquals(0, view(lab.profile(user)).path("version").asInt());
    ArgumentCaptor<Map<String, Object>> requests = mapCaptor();
    verify(ai).structured(eq("answer"), anyString(), requests.capture(), anyMap());
    assertEquals(content, view(requests.getValue()).path("content").asText());
    assertFalse(view(requests.getValue()).has("persona"));
    assertFalse(view(requests.getValue()).has("catalog"));
    view(lab.analyze(user, batch, false));
    ArgumentCaptor<Map<String, Object>> analysisInputs = mapCaptor();
    verify(ai)
        .structured(eq("analysis"), anyString(), analysisInputs.capture(), anyMap());
    assertEquals(content, view(analysisInputs.getValue()).path("sources").get(0).path("text").asText());
  }

  @Test
  void typedMessageAfterSaveAddsOnePairWithoutReapplyingOldEvidence() {
    Long user = user(), batch = generated(user, "직접 대화 이어가기");
    view(lab.analyze(user, batch, false));
    lab.save(user, batch, false);
    JsonView result = view(lab.message(user, batch, "다른 입력 주제", "오늘은 실제로 5km를 달렸어요."));
    assertEquals(11, result.path("turns").size());
    assertEquals(batch.toString(), result.path("id").asText());
    assertEquals("직접 대화 이어가기", result.path("topic").asText());
    assertEquals("generated", result.path("status").asText());
    assertEquals(1, view(lab.profile(user)).path("version").asInt());
    view(lab.analyze(user, batch, false));
    ArgumentCaptor<Map<String, Object>> inputs = mapCaptor();
    verify(ai, times(2))
        .structured(eq("analysis"), anyString(), inputs.capture(), anyMap());
    assertEquals(1, view(inputs.getAllValues().getLast()).path("sources").size());
    lab.save(user, batch, false);
    assertEquals(
        4, view(lab.profile(user)).path("elements").path("habits.exercise").path("weight").asInt());
  }

  @Test
  void failedOrInvalidTypedMessageDoesNotChangeConversation() {
    Long user = user(), other = user(), batch = generated(user, "메시지 실패");
    JsonView original = view(lab.batch(user, batch));
    assertThrows(LabException.class, () -> view(lab.message(user, batch, null, "   ")));
    assertThrows(LabException.class, () -> view(lab.message(other, batch, null, "다른 사용자")));
    doThrow(new LabException(502, "AI unavailable"))
        .when(ai)
        .structured(eq("answer"), anyString(), anyMap(), anyMap());
    assertThrows(LabException.class, () -> view(lab.message(user, batch, null, "이 메시지는 실패합니다.")));
    assertEquals(original, view(lab.batch(user, batch)));
  }

  @Test
  void generationAnalysisAndSaveAreSeparateAndIdempotent() {
    Long user = user(),
        batch =
            generated(
                user,
                "운동 "
                    + ThreadLocalRandom.current().nextLong(1, Long.MAX_VALUE));
    assertTrue(view(lab.profile(user)).path("elements").path("habits.exercise").path("score").isNull());
    view(lab.analyze(user, batch, false));
    assertEquals(0, view(lab.profile(user)).path("version").asInt());
    lab.save(user, batch, false);
    assertEquals(
        80, view(lab.profile(user)).path("elements").path("habits.exercise").path("score").asInt());
    lab.save(user, batch, false);
    assertEquals(1, view(lab.profile(user)).path("version").asInt());
    assertEquals(0, lab.dao.mapper().memories(user).size());
    assertTrue(view(lab.profile(user)).path("elements").path("values.family").path("score").isNull());
    Long duplicate = generated(user, view(lab.batch(user, batch)).path("topic").asText());
    view(lab.analyze(user, duplicate, false));
    lab.save(user, duplicate, false);
    assertEquals(
        2, view(lab.profile(user)).path("elements").path("habits.exercise").path("weight").asInt());
  }

  @Test
  void saveUsesOnlyServerMergeAndPreparedEmbeddings() {
    Long user = user(), batch = generated(user, "server merge");
    view(lab.analyze(user, batch, false));
    when(ai.embed(anyList())).thenThrow(new LabException(502, "unused embedding"));
    lab.save(user, batch, false);
    assertEquals(1, view(lab.profile(user)).path("version").asInt());
    assertTrue(lab.dao.mapper().memories(user).isEmpty());
    verify(ai, never())
        .structured(eq("merge"), anyString(), anyMap(), anyMap());
  }

  @Test
  void userIsolationAndCurrentContextAnswer() {
    Long owner = user(),
        other = user(),
        batch =
            generated(
                owner,
                "context "
                    + ThreadLocalRandom.current().nextLong(1, Long.MAX_VALUE));
    view(lab.analyze(owner, batch, false));
    lab.save(owner, batch, false);
    assertThrows(LabException.class, () -> view(lab.batch(other, batch)));
    assertThrows(LabException.class, () -> view(clone.start(other, batch)));
    JsonView session = view(clone.start(owner, batch));
    String source = view(lab.batch(owner, batch)).path("turns").get(0).path("id").asText();
    when(ai.respond(anyString(), anyList(), anyMap(), anyList()))
        .thenReturn(response("CONTEXT", false, List.of(source)));
    JsonView answer = view(clone.answer(owner, Long.valueOf(session.path("sessionId").asText()), "운동하나요?"));
    assertEquals("CONTEXT", answer.path("stage").asText());
    assertEquals(1, view(clone.history(owner)).path("messages").size());
    JsonView next = view(clone.start(owner, null));
    assertNotEquals(session.path("sessionId").asText(), next.path("sessionId").asText());
    assertThrows(
        LabException.class,
        () -> view(clone.answer(owner, Long.valueOf(session.path("sessionId").asText()), "이전 대화?")));
  }

  Map<String, Object> response(String stage, boolean inferred, List<String> ids) {
    Map<String, Object> answer =
        map(
            "answer", "근거에 따른 테스트 답변",
            "reason", "인용된 자료의 사용자 발언과 요청한 내용을 비교했습니다.",
            "stage", stage,
            "inferred", inferred,
            "evidenceIds", new ArrayList<Object>(ids));
    Map<String, Object> content =
        map("type", "output_text", "text", LabJson.write(json, answer));
    Map<String, Object> message =
        map("type", "message", "content", new ArrayList<Object>(List.of(content)));
    return map("status", "completed", "output", new ArrayList<Object>(List.of(message)));
  }

  @Test
  void viewerHistoryRagUsesOnlyCurrentActorAndSessionAndNeverOwnerMemory() {
    Long owner = user(), actor = user(), other = user();
    Long session = Long.valueOf(view(clone.start(owner, actor, null)).path("sessionId").asText());
    Long otherSession = Long.valueOf(view(clone.start(owner, other, null)).path("sessionId").asText());
    for (Long target : List.of(session, otherSession)) {
      Long viewer = target.equals(session) ? actor : other;
      lab.tx.executeWithoutResult(
          t ->
              lab.memory.turn(
                  lab.dao.mapper().nextId("session_messages", "id"),
                  owner,
                  viewer,
                  target,
                  "viewer",
                  1,
                  "my earlier question",
                  "previous reply",
                  null,
                  Instant.now(),
                  Instant.now(),
                  false,
                  null));
      lab.dao.mapper().analysis(target, LabJson.write(json, map("version", 1)));
    }
    JsonView items;
    try {
      items =
          view(
              LabJson.read(
                  json,
                  cloneTools
                      .viewerHistory(owner, actor, session)
                      .call("{\"query\":\"earlier question\"}")));
    } catch (Exception failure) {
      throw new AssertionError(failure);
    }
    assertEquals(1, items.size());
    assertEquals(session.toString(), items.get(0).path("sessionId").asText());
    assertEquals("VIEWER_CONVERSATION", items.get(0).path("scope").asText());
    assertTrue(lab.dao.mapper().memories(owner).isEmpty());
    assertThrows(
        LabException.class,
        () ->
            cloneTools
                .viewerHistory(owner, other, session)
                .call("{\"query\":\"earlier question\"}"));
    String id = items.get(0).path("id").asText();
    when(ai.respond(anyString(), anyList(), anyMap(), anyList()))
        .thenReturn(
            call("searchViewerHistory", map("query", "earlier question")),
            response("VIEWER_RAG", false, List.of(id)));
    JsonView result = view(clone.answer(owner, actor, session, "what did I ask earlier?"));
    assertEquals(
        "VIEWER_RAG", result.path("provenance").path("sources").get(0).path("type").asText());
    assertEquals(
        2,
        jdbc.queryForObject(
            "SELECT count(*) FROM clone_lab.session_messages WHERE session_id=? AND embedding IS"
                + " NOT NULL",
            Integer.class,
            session));
    assertTrue(lab.dao.mapper().recent(owner).isEmpty());
  }

  @Test
  void viewerContextCitationsAreStoredWithVerifiedSourceAndReason() throws Exception {
    Long owner = user();
    Long session = Long.valueOf(view(clone.start(owner, null)).path("sessionId").asText());
    when(ai.respond(anyString(), anyList(), anyMap(), anyList()))
        .thenReturn(
            call("searchLongTermMemory", map("query", "대화")),
            response("INSUFFICIENT", false, List.of()));
    view(clone.answer(owner, session, "내 질문을 기억해 줘"));
    String id = view(lab.contexts.prepare(session)).path("recent").get(0).path("id").asText();
    when(ai.respond(anyString(), anyList(), anyMap(), anyList()))
        .thenReturn(response("CHAT_CONTEXT", false, List.of(id)));
    JsonView answer = view(clone.answer(owner, session, "방금 내가 뭐라고 했어?"));
    JsonView source = answer.path("provenance").path("sources").get(0);
    assertEquals("CHAT_CONTEXT", source.path("type").asText());
    assertEquals("VIEWER_CONVERSATION", source.path("scope").asText());
    assertEquals("내 질문을 기억해 줘", source.path("content").asText());
    assertEquals(
        "NOT_CHECKED", answer.path("provenance").path("routes").get(3).path("status").asText());
    assertEquals(
        view(LabJson.read(json, LabJson.write(json, answer.raw()))),
        view(clone.history(owner)).path("messages").get(1).path("response"));
    assertFalse(answer.path("reason").asText().isBlank());
  }

  @Test
  void viewerSummaryCanBeCitedOnlyAsConversationContext() {
    Long owner = user();
    Long session = Long.valueOf(view(clone.start(owner, null)).path("sessionId").asText());
    lab.tx.executeWithoutResult(
        t -> {
          for (int i = 1; i <= 40; i++)
            lab.memory.turn(
                lab.dao.mapper().nextId("session_messages", "id"),
                owner,
                owner,
                session,
                "viewer",
                i,
                "질문 " + i,
                "응답 " + i,
                null,
                Instant.now(),
                Instant.now(),
                false,
                null);
          lab.dao.mapper().analysis(session, LabJson.write(json, map("version", 40)));
        });
    String id = view(lab.contexts.prepare(session)).path("summaries").get(0).path("id").asText();
    when(ai.respond(anyString(), anyList(), anyMap(), anyList()))
        .thenReturn(response("SUMMARY", false, List.of(id)));
    JsonView answer = view(clone.answer(owner, session, "앞에서 어떤 이야기를 했지?"));
    JsonView source = answer.path("provenance").path("sources").get(0);
    assertEquals("SUMMARY", source.path("type").asText());
    assertEquals(session.toString(), source.path("sessionId").asText());
    assertFalse(source.path("startMessageId").asText().isBlank());
    assertEquals("VIEWER_CONVERSATION", source.path("scope").asText());
    assertTrue(lab.dao.mapper().memories(owner).isEmpty());
  }

  Map<String, Object> call(String name, Map<String, Object> args) {
    Map<String, Object> output =
        map(
            "type", "function_call",
            "name", name,
            "call_id",
                Long.toString(
                    ThreadLocalRandom.current().nextLong(1, Long.MAX_VALUE)),
            "arguments", LabJson.write(json, args));
    return map("status", "completed", "output", new ArrayList<Object>(List.of(output)));
  }

  @Test
  void memoryThenProfileToolsAndInsufficientRoute() {
    Long user = user(),
        batch =
            generated(
                user,
                "tools "
                    + ThreadLocalRandom.current().nextLong(1, Long.MAX_VALUE));
    view(lab.analyze(user, batch, false));
    lab.save(user, batch, false);
    for (int i = 0; i < 10; i++) view(lab.append(user, batch));
    view(lab.analyze(user, batch, false));
    lab.save(user, batch, false);
    JsonView session = view(clone.start(user, null));
    Map<String, Object> query = map("query", "운동");
    Map<String, Object> codes = map("codes", new ArrayList<Object>(List.of("habits.exercise")));
    when(ai.respond(anyString(), anyList(), anyMap(), anyList()))
        .thenReturn(
            call("searchLongTermMemory", query),
            call("getAnalysisProfile", codes),
            response("PROFILE", true, List.of("habits.exercise")));
    JsonView answer = view(clone.answer(user, Long.valueOf(session.path("sessionId").asText()), "오늘도 운동할까요?"));
    assertEquals(2, answer.path("trace").size());
    assertTrue(answer.path("inferred").asBoolean());
    session = view(clone.start(user, null));
    String memory = "memory:" + lab.dao.mapper().memories(user).getFirst().get("id").toString();
    when(ai.respond(anyString(), anyList(), anyMap(), anyList()))
        .thenReturn(
            call("searchLongTermMemory", query), response("MEMORY", false, List.of(memory)));
    assertEquals(
        "MEMORY",
        view(clone.answer(user, Long.valueOf(session.path("sessionId").asText()), "무슨 운동?"))
            .path("stage")
            .asText());
    session = view(clone.start(user, null));
    when(ai.respond(anyString(), anyList(), anyMap(), anyList()))
        .thenReturn(
            call("searchLongTermMemory", query), response("INSUFFICIENT", false, List.of()));
    assertEquals(
        "INSUFFICIENT",
        view(clone.answer(user, Long.valueOf(session.path("sessionId").asText()), "생일은?"))
            .path("stage")
            .asText());
  }

  @Test
  void profileToolCannotBeCalledBeforeMemory() {
    Long user = user();
    JsonView session = view(clone.start(user, null));
    Map<String, Object> args = map("codes", new ArrayList<Object>(List.of("habits.exercise")));
    when(ai.respond(anyString(), anyList(), anyMap(), anyList()))
        .thenReturn(call("getAnalysisProfile", args));
    assertThrows(
        LabException.class,
        () -> view(clone.answer(user, Long.valueOf(session.path("sessionId").asText()), "질문")));
  }

  @Test
  void toolCallbacksRejectDuplicateExecutionAndOwnerArguments() {
    Long user = user();
    JsonView session = view(clone.start(user, null));
    Map<String, Object> query = map("query", "운동");
    Map<String, Object> duplicate = call("searchLongTermMemory", query);
    LabJson.list(duplicate.get("output"))
        .add(LabJson.list(call("searchLongTermMemory", query).get("output")).get(0));
    when(ai.respond(anyString(), anyList(), anyMap(), anyList()))
        .thenReturn(duplicate);
    Long sessionId = Long.valueOf(session.path("sessionId").asText());
    assertThrows(LabException.class, () -> view(clone.answer(user, sessionId, "질문")));
    verify(ai, times(1)).embed(anyList());
    assertTrue(view(clone.history(user)).path("messages").isEmpty());
    clearInvocations(ai);
    query.put(
        "ownerId",
        Long.toString(
            ThreadLocalRandom.current().nextLong(1, Long.MAX_VALUE)));
    when(ai.respond(anyString(), anyList(), anyMap(), anyList()))
        .thenReturn(call("searchLongTermMemory", query));
    assertThrows(LabException.class, () -> view(clone.answer(user, sessionId, "질문")));
    verify(ai, never()).embed(anyList());
  }

  @Test
  void databaseChecksAndSchemaAreRealPostgres() {
    assertEquals(
        14,
        jdbc.queryForObject(
            "SELECT count(*) FROM information_schema.tables WHERE table_schema='clone_lab'",
            Integer.class));
    Long user = user();
    assertThrows(
        Exception.class,
        () ->
            jdbc.update(
                "UPDATE clone_lab.profile_relations SET social_orientation=101 WHERE user_id=?",
                user));
  }

  @Test
  void schemaInitializationIsIdempotent() {
    Integer before =
        jdbc.queryForObject(
            "SELECT count(*) FROM pg_constraint WHERE connamespace='clone_lab'::regnamespace",
            Integer.class);
    ResourceDatabasePopulator populator =
        new ResourceDatabasePopulator(
            new ClassPathResource("clone-lab/schema.sql"));
    populator.setSeparator(
        ScriptUtils.EOF_STATEMENT_SEPARATOR);
    populator.execute(jdbc.getDataSource());
    assertEquals(
        before,
        jdbc.queryForObject(
            "SELECT count(*) FROM pg_constraint WHERE connamespace='clone_lab'::regnamespace",
            Integer.class));
  }

  @Test
  void realHttpViewsCatalogAndValidation() throws Exception {
    mvc.perform(get("/lab"))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith("text/html"));
    mvc.perform(get("/lab/viewer")).andExpect(status().isOk());
    mvc.perform(get("/lab/assets/schema.sql")).andExpect(status().isNotFound());
    mvc.perform(get("/api/lab/catalog")).andExpect(jsonPath("$.domains.length()").value(7));
    Long user = user();
    mvc.perform(get("/api/lab/users/" + user + "/runs/current")).andExpect(status().isOk());
    mvc.perform(get("/api/lab/users/" + user + "/labRunService/current"))
        .andExpect(status().isOk());
    mvc.perform(
            post("/api/lab/users")
                .contentType("application/json")
                .content("{\"name\":\"\",\"seed\":\"\"}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void failedEmbeddingDoesNotWriteAnyProfileOrMemory() {
    Long user = user();
    when(ai.embed(anyList())).thenThrow(new LabException(502, "temporary failure"));
    Long batch = Long.valueOf(view(lab.generate(user, "embedding failure", false)).path("id").asText());
    assertEquals(0, view(lab.profile(user)).path("version").asInt());
    assertEquals(10, lab.dao.mapper().turns(batch).size());
    assertEquals(10, lab.dao.mapper().missingEmbeddings(user).size());
    view(lab.analyze(user, batch, false));
    assertThrows(LabException.class, () -> lab.save(user, batch, false));
    assertEquals(0, view(lab.profile(user)).path("version").asInt());
    fixture();
    lab.save(user, batch, false);
    assertTrue(lab.dao.mapper().missingEmbeddings(user).isEmpty());
    assertEquals(1, view(lab.profile(user)).path("version").asInt());
  }

  @Test
  void databaseFailureRollsBackAllSevenProfilesAndBatch() {
    Long user = user(), batch = generated(user, "DB rollback");
    view(lab.analyze(user, batch, false));
    String constraint = "lab_test_" + user;
    jdbc.execute(
        "ALTER TABLE clone_lab.profile_habits ADD CONSTRAINT "
            + constraint
            + " CHECK(user_id <> "
            + user
            + " OR exercise IS NULL)");
    try {
      assertThrows(Exception.class, () -> lab.save(user, batch, false));
      assertEquals(0, view(lab.profile(user)).path("version").asInt());
      assertEquals("analyzed", view(lab.batch(user, batch)).path("status").asText());
      assertTrue(view(lab.profile(user)).path("elements").path("habits.exercise").path("score").isNull());
    } finally {
      jdbc.execute("ALTER TABLE clone_lab.profile_habits DROP CONSTRAINT " + constraint);
    }
  }

  @Test
  void simultaneousSavesApplyTheBatchOnlyOnce() throws Exception {
    Long user = user(),
        batch =
            generated(
                user,
                "concurrent "
                    + ThreadLocalRandom.current().nextLong(1, Long.MAX_VALUE));
    view(lab.analyze(user, batch, false));
    CountDownLatch ready = new CountDownLatch(2);
    when(ai.embed(anyList()))
        .thenAnswer(
            call -> {
              ready.countDown();
              assertTrue(ready.await(10, TimeUnit.SECONDS));
              List<?> list = call.getArgument(0);
              return list.stream()
                  .map(
                      x -> {
                        float[] v = new float[1536];
                        v[0] = 1;
                        return v;
                      })
                  .toList();
            });
    try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
      Future<Map<String, Object>> a = executor.submit(() -> lab.save(user, batch, false));
      Future<Map<String, Object>> b = executor.submit(() -> lab.save(user, batch, false));
      assertEquals(1, view(a.get(15, TimeUnit.SECONDS)).path("version").asInt());
      assertEquals(1, view(b.get(15, TimeUnit.SECONDS)).path("version").asInt());
    }
    assertEquals(0, lab.dao.mapper().memories(user).size());
    assertEquals(
        2, view(lab.profile(user)).path("elements").path("habits.exercise").path("weight").asInt());
  }

  @Test
  void pipelineGeneratesOneConversationAndCommitsOneHundredOrderedSegments() throws Exception {
    Long user = user();
    Stubbing delegate =
        mockingDetails(ai).getStubbings().stream()
            .filter(s -> s.getInvocation().getMethod().getName().equals("structured"))
            .findFirst()
            .orElseThrow();
    AtomicInteger generationLive = new AtomicInteger();
    AtomicInteger analysisLive = new AtomicInteger();
    AtomicInteger generationMax = new AtomicInteger();
    AtomicInteger analysisMax = new AtomicInteger();
    AtomicInteger backlogMax = new AtomicInteger();
    AtomicInteger generatedCalls = new AtomicInteger();
    AtomicInteger firstAnalysisCount = new AtomicInteger(-1);
    AtomicBoolean overlap = new AtomicBoolean();
    CountDownLatch together = new CountDownLatch(2);
    doAnswer(
            call -> {
              String stage = call.getArgument(0);
              boolean generation = stage.equals("generation"), analysis = stage.equals("analysis");
              if (generation)
                generationMax.accumulateAndGet(generationLive.incrementAndGet(), Math::max);
              if (analysis) analysisMax.accumulateAndGet(analysisLive.incrementAndGet(), Math::max);
              try {
                JsonView current = view(runs.state(user));
                if (generation) {
                  int reservation = current.path("generationReservation").path("to").asInt();
                  backlogMax.accumulateAndGet(
                      reservation - current.path("savedPairs").asInt(), Math::max);
                  if (generatedCalls.incrementAndGet() == 2) {
                    together.countDown();
                    if (!together.await(5, TimeUnit.SECONDS))
                      throw new LabException(502, "overlap timeout");
                  }
                }
                if (analysis
                    && firstAnalysisCount.compareAndSet(
                        -1, current.path("generatedPairs").asInt())) {
                  together.countDown();
                  if (!together.await(5, TimeUnit.SECONDS))
                    throw new LabException(502, "overlap timeout");
                }
                if (generationLive.get() > 0 && analysisLive.get() > 0) overlap.set(true);
                return delegate.answer(call);
              } finally {
                if (generation) generationLive.decrementAndGet();
                if (analysis) analysisLive.decrementAndGet();
              }
            })
        .when(ai)
        .structured(anyString(), anyString(), anyMap(), anyMap());
    runs.start(
        user,
        "pipeline " + ThreadLocalRandom.current().nextLong(1, Long.MAX_VALUE),
        false);
    JsonView state = awaitRun(user, 180);
    assertEquals("completed", state.path("status").asText(), state.toString());
    assertEquals(LabPipeline.MODE, state.path("mode").asText());
    assertEquals(100, state.path("completedBatches").asInt());
    assertEquals(1000, state.path("generatedPairs").asInt());
    assertEquals(1000, state.path("analyzedPairs").asInt());
    assertEquals(1000, state.path("savedPairs").asInt());
    assertEquals(100, view(lab.profile(user)).path("version").asInt());
    assertEquals(1, lab.dao.mapper().batches(user).size());
    JsonView batch = view(lab.batch(user, Long.valueOf(state.path("batchId").asText())));
    assertEquals(1000, batch.path("turns").size());
    assertEquals(1000, batch.path("turn_count").asInt());
    assertEquals(
        900,
        jdbc.queryForObject(
            "SELECT count(*) FROM clone_lab.vector_store WHERE metadata->>'owner_id'=? AND"
                + " metadata->>'record_type'='conversation_archive'",
            Integer.class,
            user.toString()));
    assertEquals(100, batch.path("analysis").path("segments").size());
    int index = 0;
    for (JsonView segment : batch.path("analysis").path("segments")) {
      assertEquals("saved", segment.path("status").asText());
      assertEquals(++index, segment.path("profileVersion").asInt());
      assertEquals(10, segment.path("sourceIds").size());
      assertTrue(segment.path("analysis").path("summaries").size() <= 5);
    }
    for (String code : catalog.codes())
      assertTrue(
          view(lab.profile(user)).path("elements").path(code).path("weight").asInt()
              <= catalog.maxWeight(code));
    assertEquals(
        10, view(lab.profile(user)).path("elements").path("habits.exercise").path("weight").asInt());
    verify(ai, never())
        .structured(eq("merge"), anyString(), anyMap(), anyMap());
    verify(ai, times(100))
        .structured(eq("analysis"), anyString(), anyMap(), anyMap());
    verify(ai, times(148))
        .structured(eq("summary"), anyString(), anyMap(), anyMap());
    assertTrue(overlap.get());
    assertEquals(1, generationMax.get());
    assertEquals(1, analysisMax.get());
    assertTrue(backlogMax.get() <= 20);
    assertTrue(firstAnalysisCount.get() < 1000);
  }

  JsonView awaitRun(Long user, int seconds) throws Exception {
    long deadline = System.nanoTime() + Duration.ofSeconds(seconds).toNanos();
    JsonView state;
    do {
      Thread.sleep(20);
      state = view(runs.state(user));
    } while (Set.of("running", "stopping").contains(state.path("status").asText())
        && System.nanoTime() < deadline);
    return state;
  }

  @Test
  void stopPreservesCurrentGenerationAnalysisOrSaveAndRecoveryKeepsCheckpoints() throws Exception {
    for (String phase : List.of("generation", "analysis", "embedding")) {
      fixture();
      Long user = user();
      Stubbing delegate =
          mockingDetails(ai).getStubbings().stream()
              .filter(s -> s.getInvocation().getMethod().getName().equals("structured"))
              .findFirst()
              .orElseThrow();
      CountDownLatch entered = new CountDownLatch(1);
      CountDownLatch release = new CountDownLatch(1);
      AtomicBoolean blocked = new AtomicBoolean();
      if (phase.equals("embedding"))
        when(ai.embed(anyList()))
            .thenAnswer(
                call -> {
                  List<?> texts = call.getArgument(0);
                  if (!texts.isEmpty() && blocked.compareAndSet(false, true)) {

                    entered.countDown();
                    if (!release.await(5, TimeUnit.SECONDS))
                      throw new LabException(502, "timeout");
                  }
                  return texts.stream()
                      .map(
                          x -> {
                            float[] v = new float[1536];
                            v[0] = 1;
                            return v;
                          })
                      .toList();
                });
      else
        doAnswer(
                call -> {
                  if (phase.equals(call.getArgument(0)) && blocked.compareAndSet(false, true)) {
                    entered.countDown();
                    if (!release.await(5, TimeUnit.SECONDS))
                      throw new LabException(502, "timeout");
                  }
                  return delegate.answer(call);
                })
        .when(ai)
        .structured(anyString(), anyString(), anyMap(), anyMap());
      runs.start(user, "stop " + phase, false);
      assertTrue(entered.await(5, TimeUnit.SECONDS));
      runs.stop(user);
      release.countDown();
      JsonView paused = awaitRun(user, 15);
      assertEquals("paused", paused.path("status").asText(), paused.toString());
      assertTrue(paused.path("generatedPairs").asInt() <= 20);
      assertEquals(0, paused.path("savedPairs").asInt());
      assertEquals(phase.equals("analysis") ? 10 : 0, paused.path("analyzedPairs").asInt());
      Long batch = Long.valueOf(paused.path("batchId").asText());
      JsonView before = view(lab.batch(user, batch)).deepCopy();
      assertThrows(LabException.class, () -> view(lab.analyze(user, batch, false)));
      lab.tx.executeWithoutResult(
          s -> {
            Map<String, Object> row = lab.dao.mapper().lockUser(user);
            Map<String, Object> run = lab.dao.parseMap(row.get("run_state"));
            run.put("status", "running");
            run.put("activeSince", System.currentTimeMillis() - 5000);
            run.put("heartbeatAt", System.currentTimeMillis() - 2000);
            int generatedPairs = LabJson.integer(run.get("generatedPairs"));
            run.put(
                "generationReservation",
                map("from", generatedPairs + 1, "to", generatedPairs + 10));
            lab.dao.mapper().runState(user, LabJson.write(json, run));
          });
      runs.recoverInterrupted();
      JsonView recovered = view(runs.state(user));
      assertEquals("paused", recovered.path("status").asText());
      assertFalse(recovered.has("activeSince"));
      assertTrue(recovered.path("wallElapsedMs").asLong() >= 3000);
      assertTrue(recovered.has("generationReservation"));
      assertEquals(before, view(lab.batch(user, batch)));
    }
  }

  @Test
  void duplicateSegmentEvidenceIsNotReappliedAndConcurrentSaveIsIdempotent() throws Exception {
    Long user = user();
    Stubbing delegate =
        mockingDetails(ai).getStubbings().stream()
            .filter(s -> s.getInvocation().getMethod().getName().equals("structured"))
            .findFirst()
            .orElseThrow();
    AtomicReference<LabAi.Result> first = new AtomicReference<LabAi.Result>();
    doAnswer(
            call -> {
              String stage = call.getArgument(0);
              if (stage.equals("generation")) {
                if (first.get() == null) first.set((LabAi.Result) delegate.answer(call));
                return first.get();
              }
              if (stage.equals("summary")) runs.stop(user);
              return delegate.answer(call);
            })
        .when(ai)
        .structured(anyString(), anyString(), anyMap(), anyMap());
    runs.start(user, "duplicate", false);
    JsonView paused = awaitRun(user, 15);
    assertEquals("paused", paused.path("status").asText(), paused.toString());
    assertEquals(10, paused.path("savedPairs").asInt());
    Long batch = Long.valueOf(paused.path("batchId").asText());
    assertTrue(
        view(lab.batch(user, batch))
            .path("analysis")
            .path("segments")
            .get(1)
            .path("analysis")
            .path("observations")
            .isEmpty());
    CountDownLatch ready = new CountDownLatch(2);
    when(ai.embed(anyList()))
        .thenAnswer(
            call -> {
              ready.countDown();
              if (!ready.await(5, TimeUnit.SECONDS))
                throw new LabException(502, "timeout");
              return List.of();
            });
    try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
      Future<?> a = executor.submit(() -> pipeline.saveSegment(user, batch, 2));
      Future<?> b = executor.submit(() -> pipeline.saveSegment(user, batch, 2));
      a.get(10, TimeUnit.SECONDS);
      b.get(10, TimeUnit.SECONDS);
    }
    assertEquals(2, view(lab.profile(user)).path("version").asInt());
    assertEquals(
        2, view(lab.profile(user)).path("elements").path("habits.exercise").path("weight").asInt());
    assertEquals(20, view(lab.batch(user, batch)).path("analysis").path("processedSourceIds").size());
    verify(ai, times(1))
        .structured(eq("analysis"), anyString(), anyMap(), anyMap());
  }

  @Test
  void pipelineFailureResumesDraftAndPreservesGeneratedTurns() throws Exception {
    Long user = user();
    Stubbing original =
        mockingDetails(ai).getStubbings().stream()
            .filter(x -> x.getInvocation().getMethod().getName().equals("structured"))
            .findFirst()
            .orElseThrow();
    doAnswer(
            call -> {
              if ("analysis".equals(call.getArgument(0)))
                throw new LabException(502, "temporary analysis failure");
              return original.answer(call);
            })
        .when(ai)
        .structured(anyString(), anyString(), anyMap(), anyMap());
    runs.start(user, "resume", false);
    JsonView stopped = awaitRun(user, 20);
    assertEquals("failed", stopped.path("status").asText(), stopped.toString());
    Long batch = Long.valueOf(stopped.path("batchId").asText());
    JsonView before = view(lab.batch(user, batch)).path("turns").deepCopy();
    assertTrue(before.size() > 0 && before.size() <= 20);
    assertThrows(LabException.class, () -> view(lab.append(user, batch)));
    fixture();
    runs.start(user, null, true);
    JsonView completed = awaitRun(user, 180);
    assertEquals("completed", completed.path("status").asText(), completed.toString());
    JsonView after = view(lab.batch(user, batch)).path("turns");
    assertEquals(1000, after.size());
    for (int i = 0; i < before.size(); i++)
      assertEquals(before.get(i).path("id"), after.get(i).path("id"));
    assertEquals(100, view(lab.profile(user)).path("version").asInt());
  }

  @Test
  void persistedRunRecoveryUsesColumnsAndKeepsProfileUnchanged() throws Exception {
    Long user = user(), batch = generated(user, "checkpoint");
    lab.tx.executeWithoutResult(
        t -> {
          Map<String, Object> state =
              map(
                  "runId", lab.dao.mapper().nextId("runs", "run_id").toString(),
                  "batchId", batch.toString(),
                  "mode", LabPipeline.MODE,
                  "topic", "checkpoint",
                  "status", "running",
                  "generatedPairs", 10,
                  "activeSince", System.currentTimeMillis() - 5000,
                  "heartbeatAt", System.currentTimeMillis() - 2000);
          lab.dao.mapper().runState(user, LabJson.write(json, state));
        });
    runs.recoverInterrupted();
    JsonView state = view(runs.state(user));
    assertEquals("paused", state.path("status").asText());
    assertEquals(10, state.path("generatedPairs").asInt());
    assertTrue(state.path("wallElapsedMs").asLong() >= 3000);
    assertFalse(state.has("activeSince"));
    assertEquals(0, view(lab.profile(user)).path("version").asInt());
  }
}
