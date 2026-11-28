package com.example.everme.lab.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import java.math.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("clone-lab")
@TestPropertySource(properties = "lab.initialize-schema=true")
@EnabledIfEnvironmentVariable(named = "LAB_TEST_DATABASE_URL", matches = ".+")
class LabIntegrationTests {
  @DynamicPropertySource
  static void database(DynamicPropertyRegistry r) {
    r.add("spring.datasource.url", () -> System.getenv("LAB_TEST_DATABASE_URL"));
    r.add("spring.datasource.username", () -> "everme_lab");
    r.add("spring.datasource.password", () -> "everme_lab");
  }

  @Autowired LabService lab;
  @Autowired LabCloneService clone;
  @Autowired LabRunService runs;
  @Autowired LabPipeline pipeline;
  @Autowired ObjectMapper json;
  @Autowired LabCatalog catalog;
  @Autowired JdbcTemplate jdbc;
  @Autowired MockMvc mvc;
  @MockitoBean OpenAiLabClient ai;

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
    when(ai.structured(anyString(), anyString(), any(JsonNode.class), any(JsonNode.class)))
        .thenAnswer(
            call -> {
              String stage = call.getArgument(0);
              JsonNode input = call.getArgument(2);
              ObjectNode data = json.createObjectNode();
              switch (stage) {
                case "generation" -> {
                  if (input.has("seed")) {
                    data.put("biography", "고정 테스트 인물");
                    data.putArray("facts").add("매주 달린다");
                    var scores = data.putObject("scores");
                    catalog.codes().forEach(c -> scores.put(c, 70));
                  } else {
                    var turns = data.putArray("turns");
                    String topic = input.path("topic").asText();
                    if (input.path("continuation").asBoolean() || topic.startsWith("자동 테스트"))
                      topic += " 추가 이야기 " + UUID.randomUUID();
                    for (int i = 0; i < 10; i++)
                      turns
                          .addObject()
                          .put("user", topic + " 나는 매주 " + i + "일 달린다")
                          .put("assistant", "어떤 기분인가요?");
                  }
                }
                case "summary" -> {
                  JsonNode schema = call.getArgument(3);
                  if (schema.path("properties").has("topic")) data.put("topic", "운동과 일상 경험");
                  else data.put("summary", "The user described prior exercise experiences.");
                }
                case "analysis" -> {
                  var source = input.path("sources").get(0);
                  data.putArray("observations")
                      .addObject()
                      .put("code", "habits.exercise")
                      .put("score", 80)
                      .put("strength", 2)
                      .put("reason", "사용자가 직접 반복 운동을 설명했다")
                      .putArray("sourceIds")
                      .add(source.path("id").asText());
                  data.putArray("memories")
                      .addObject()
                      .put("kind", "FACT")
                      .put("content", "이 사용자는 달리기를 한다")
                      .putNull("occurredAt")
                      .putArray("sourceIds")
                      .add(source.path("id").asText());
                }
                case "answer" -> data.put("assistant", "그런 일이 있었군요. 그때는 어떤 느낌이었나요?");
                default -> throw new IllegalArgumentException(stage);
              }
              return new LabAi.Result(data, "test-model", 10, 10, 1);
            });
  }

  void fixtureTurn(UUID user, UUID batch, int ordinal, String userText, String assistant) {
    var vector = new float[1536];
    vector[0] = 1;
    lab.memory.turn(
        UUID.randomUUID(),
        user,
        user,
        batch,
        "owner",
        ordinal,
        userText,
        assistant,
        vector,
        java.time.Instant.now(),
        java.time.Instant.now(),
        false,
        null);
    var details = (ObjectNode) lab.dao.parse(lab.dao.requireBatch(user, batch).get("analysis"));
    details.put("turnCount", ordinal);
    lab.dao.mapper().analysis(batch, details.toString());
  }

  UUID user() {
    return UUID.fromString(lab.createUser("통합 테스트", "운동하는 사람").path("id").asText());
  }

  UUID generated(UUID user, String topic) {
    return UUID.fromString(lab.generate(user, topic, false).path("id").asText());
  }

  @Test
  void recentHundredRetentionPreservesEvidenceAndTimestampsAcrossSessions() {
    UUID user = user(), batch = generated(user, "retention " + UUID.randomUUID());
    for (int i = 0; i < 10; i++) lab.append(user, batch);
    assertEquals(110, lab.dao.mapper().turns(batch).size()); // 미처리 원문은 100쌍을 넘어도 보존한다.
    var first = lab.dao.mapper().turns(batch).getFirst();
    String firstId = first.get("id").toString();
    var meta = lab.dao.parse(first.get("metadata"));
    assertTrue(
        java.time.Instant.parse(meta.path("user_received_at").asText())
                .compareTo(java.time.Instant.parse(meta.path("assistant_received_at").asText()))
            <= 0);
    assertTrue(meta.path("synthetic").asBoolean());
    lab.analyze(user, batch, false);
    lab.save(user, batch, false);
    assertEquals(100, lab.dao.mapper().turns(batch).size());
    assertEquals(100, lab.dao.mapper().recent(user).size());
    assertEquals(
        10,
        jdbc.queryForObject(
            "SELECT count(*) FROM clone_lab.vector_store WHERE metadata->>'clone_owner_id'=? AND"
                + " metadata->>'record_type'='context_archive'",
            Integer.class,
            user.toString()));
    assertEquals(
        1,
        jdbc.queryForObject(
            "SELECT count(*) FROM clone_lab.vector_store WHERE metadata->>'extraction_key'=? AND"
                + " jsonb_array_length(metadata->'source_excerpts')=1",
            Integer.class,
            "context:" + firstId));
    assertEquals(110, lab.batch(user, batch).path("analysis").path("processedEvidence").size());
    UUID next = generated(user, "new topic");
    var profile = lab.profile(user).path("elements").path("habits.exercise").deepCopy();
    lab.analyze(user, next, false);
    lab.save(user, next, false);
    assertEquals(90, lab.dao.mapper().turns(batch).size());
    assertEquals(100, lab.dao.mapper().recent(user).size());
    assertTrue(
        lab.profile(user).path("elements").path("habits.exercise").path("weight").asInt()
            >= profile.path("weight").asInt());
    var inputs = org.mockito.ArgumentCaptor.forClass(JsonNode.class);
    lab.message(user, next, null, "현재 이야기");
    verify(ai).structured(eq("answer"), anyString(), inputs.capture(), any(JsonNode.class));
    assertEquals(100, inputs.getValue().path("recent").size());
  }

  @Test
  void archiveFailureKeepsRawContextAndRollsBackProfile() {
    UUID user = user(), batch = generated(user, "archive rollback");
    for (int i = 0; i < 10; i++) lab.append(user, batch);
    lab.analyze(user, batch, false);
    String name = "archive_failure_" + user.toString().replace("-", "");
    jdbc.execute(
        "ALTER TABLE clone_lab.vector_store ADD CONSTRAINT "
            + name
            + " CHECK(metadata->>'clone_owner_id'<>'"
            + user
            + "' OR metadata->>'record_type' IS DISTINCT FROM 'context_archive')");
    try {
      assertThrows(Exception.class, () -> lab.save(user, batch, false));
      assertEquals(110, lab.dao.mapper().turns(batch).size());
      assertEquals(0, lab.profile(user).path("version").asInt());
      assertTrue(lab.dao.mapper().memories(user).isEmpty());
    } finally {
      jdbc.execute("ALTER TABLE clone_lab.vector_store DROP CONSTRAINT " + name);
    }
    lab.save(user, batch, false);
    assertEquals(100, lab.dao.mapper().turns(batch).size());
  }

  @Test
  void viewerSessionsSeparateActorsAndNeverUpdateOwnerProfile() {
    UUID owner = user(),
        actor = user(),
        other = user(),
        batch = generated(owner, "private owner source");
    lab.analyze(owner, batch, false);
    lab.save(owner, batch, false);
    var session = clone.start(owner, actor, null);
    var otherSession = clone.start(owner, other, null);
    String source = lab.batch(owner, batch).path("turns").get(0).path("id").asText();
    when(ai.respond(anyString(), any(JsonNode.class), any(JsonNode.class), anyList()))
        .thenReturn(response("CONTEXT", false, List.of(source)));
    clone.answer(owner, actor, UUID.fromString(session.path("sessionId").asText()), "이용자만의 질문");
    assertEquals(1, clone.history(owner, actor).path("messages").size());
    assertEquals(0, clone.history(owner, other).path("messages").size());
    assertThrows(
        LabException.class,
        () ->
            clone.answer(
                owner, other, UUID.fromString(session.path("sessionId").asText()), "다른 이용자 세션"));
    assertNotEquals(session.path("sessionId"), otherSession.path("sessionId"));
    assertEquals(1, lab.profile(owner).path("version").asInt());
    assertEquals(10, lab.dao.mapper().recent(owner).size());
    assertEquals(
        1,
        jdbc.queryForObject(
            "SELECT count(*) FROM clone_lab.context WHERE conversation_type='viewer' AND"
                + " metadata->>'actor_user_id'=?",
            Integer.class,
            actor.toString()));
  }

  @Test
  void ownerAndActorFiltersApplyToBothVectorStores() {
    UUID owner = user(), other = user(), batch = generated(owner, "owner vector source");
    lab.analyze(owner, batch, false);
    lab.save(owner, batch, false);
    UUID otherBatch = generated(other, "other vector source");
    lab.analyze(other, otherBatch, false);
    lab.save(other, otherBatch, false);
    var retrieval = org.springframework.test.util.ReflectionTestUtils.getField(clone, "retrieval");
    for (var document : ((LabRetrievalService) retrieval).context(owner, null, "source"))
      assertEquals(owner.toString(), document.getMetadata().get("clone_owner_id"));
    var memories = ((LabRetrievalService) retrieval).memories(owner, "source");
    assertFalse(memories.isEmpty());
    for (var document : memories) {
      assertEquals(owner.toString(), document.getMetadata().get("clone_owner_id"));
      assertEquals("owner", document.getMetadata().get("conversation_type"));
    }
  }

  @Test
  void mergeUsesServerPolicyAndAuditsCapsWithoutInflatingExistingWeight() {
    UUID user = user();
    var prior = (ObjectNode) lab.profile(user).path("elements");
    prior.withObject("/habits.exercise").put("score", 40).put("weight", 200);
    prior.withObject("/values.family").put("score", 40).put("weight", 20);
    lab.tx.executeWithoutResult(s -> lab.dao.updateProfile(user, prior));
    UUID batch = generated(user, "상한 검증");
    lab.analyze(user, batch, false);
    lab.save(user, batch, false);
    var profile = lab.profile(user).path("elements");
    assertEquals(46.67, profile.path("habits.exercise").path("score").asDouble());
    assertEquals(10, profile.path("habits.exercise").path("weight").asInt());
    assertEquals(20, profile.path("values.family").path("weight").asInt());
    var analysis = lab.batch(user, batch).path("analysis");
    assertEquals("element-cap-v1", analysis.path("updatePolicyVersion").asText());
    assertEquals(10, analysis.path("maxWeights").path("habits.exercise").asInt());
    assertEquals(1, analysis.path("maxWeights").size());
    verify(ai, never())
        .structured(eq("merge"), anyString(), any(JsonNode.class), any(JsonNode.class));
  }

  @Test
  void mergeNoLongerCallsLegacyModel() {
    UUID user = user(), batch = generated(user, "server merge");
    lab.analyze(user, batch, false);
    when(ai.structured(eq("merge"), anyString(), any(JsonNode.class), any(JsonNode.class)))
        .thenThrow(new AssertionError("Merge LLM must not be called"));
    lab.save(user, batch, false);
    assertEquals(1, lab.profile(user).path("version").asInt());
    verify(ai, never())
        .structured(eq("merge"), anyString(), any(JsonNode.class), any(JsonNode.class));
  }

  @Test
  void blankTopicIsInferredOnceAtTenPairsAndUsesOnlyFirstTen() {
    UUID user = user();
    var first = lab.message(user, null, null, "첫 번째 이야기");
    UUID batch = UUID.fromString(first.path("id").asText());
    for (int i = 1; i < 9; i++) lab.message(user, batch, null, "이야기 " + i);
    assertEquals("", lab.batch(user, batch).path("topic").asText());
    verify(ai, never())
        .structured(eq("summary"), anyString(), any(JsonNode.class), any(JsonNode.class));
    var tenth = lab.message(user, batch, null, "열 번째 이야기");
    assertEquals("운동과 일상 경험", tenth.path("topic").asText());
    lab.message(user, batch, null, "열한 번째 다른 이야기");
    var inputs = org.mockito.ArgumentCaptor.forClass(JsonNode.class);
    verify(ai).structured(eq("summary"), anyString(), inputs.capture(), any(JsonNode.class));
    assertEquals(10, inputs.getValue().path("turns").size());
    assertEquals("첫 번째 이야기", inputs.getValue().path("turns").get(0).path("user").asText());
    assertFalse(inputs.getValue().has("persona"));
    assertEquals(0, lab.profile(user).path("version").asInt());
    assertEquals("generated", lab.batch(user, batch).path("status").asText());
  }

  @Test
  void blankGenerationInfersTopicButExplicitTopicIsPreserved() {
    UUID user = user(), batch = generated(user, "");
    assertEquals("운동과 일상 경험", lab.batch(user, batch).path("topic").asText());
    UUID explicit = generated(user, "명시한 주제");
    assertEquals("명시한 주제", lab.batch(user, explicit).path("topic").asText());
    verify(ai).structured(eq("summary"), anyString(), any(JsonNode.class), any(JsonNode.class));
  }

  @Test
  void topicInferenceFailureDoesNotPartiallyAppendTenthPair() {
    UUID user = user();
    UUID batch = UUID.fromString(lab.message(user, null, null, "시작").path("id").asText());
    for (int i = 1; i < 9; i++) lab.message(user, batch, null, "이야기 " + i);
    doThrow(new LabException(502, "unavailable"))
        .when(ai)
        .structured(eq("summary"), anyString(), any(JsonNode.class), any(JsonNode.class));
    assertThrows(LabException.class, () -> lab.message(user, batch, null, "열 번째"));
    assertEquals(9, lab.batch(user, batch).path("turns").size());
    assertEquals("", lab.batch(user, batch).path("topic").asText());
  }

  @Test
  void previousConversationIsNotIncludedInCurrentSummaries() {
    UUID user = user(), previous = UUID.randomUUID();
    lab.tx.executeWithoutResult(
        status -> {
          lab.dao.mapper().createBatch(previous, user, "previous conversation", 1);
          for (int i = 0; i < 33; i++)
            fixtureTurn(user, previous, i + 1, "previous experience " + i, "previous conversation");
        });
    UUID current = generated(user, "previous conversation");
    var result = lab.analyze(user, current, false);
    assertEquals(0, result.path("analysis").path("windows").get(0).path("summaries").size());
    var requests = org.mockito.ArgumentCaptor.forClass(JsonNode.class);
    verify(ai).structured(eq("analysis"), anyString(), requests.capture(), any(JsonNode.class));
    assertEquals(10, requests.getValue().path("sources").size());
    assertEquals(0, requests.getValue().path("summaries").size());
    assertFalse(requests.getValue().has("persona"));
  }

  @Test
  void summariesAreLimitedToFiveAndAllFortyThreePairsAreAnalyzed() {
    UUID user = user(), previous = UUID.randomUUID();
    lab.tx.executeWithoutResult(
        status -> {
          lab.dao.mapper().createBatch(previous, user, "previous conversation", 1);
          for (int i = 0; i < 70; i++)
            fixtureTurn(user, previous, i + 1, "older experience " + i, "previous conversation");
        });
    UUID current = generated(user, "previous conversation");
    lab.append(user, current);
    lab.append(user, current);
    lab.append(user, current);
    for (int i = 0; i < 3; i++) lab.message(user, current, null, "typed experience " + i);
    var result = lab.analyze(user, current, false);
    var windows = result.path("analysis").path("windows");
    assertEquals(5, windows.size());
    int count = 0;
    int windowIndex = 0;
    for (var window : windows) {
      assertEquals(Math.min(5, windowIndex++), window.path("summaries").size());
      count += window.path("sourceIds").size();
    }
    assertEquals(43, count);
    lab.save(user, current, false);
    assertEquals(1, lab.profile(user).path("version").asInt());
  }

  @Test
  void testDialogueGenerationNeverUsesGroundTruth() {
    UUID user = user();
    generated(user, "여행 준비");
    var inputs = org.mockito.ArgumentCaptor.forClass(JsonNode.class);
    var prompts = org.mockito.ArgumentCaptor.forClass(String.class);
    verify(ai, times(2))
        .structured(eq("generation"), prompts.capture(), inputs.capture(), any(JsonNode.class));
    var request = inputs.getAllValues().getLast();
    assertEquals("여행 준비", request.path("topic").asText());
    assertFalse(request.has("catalog"));
    assertFalse(request.has("persona"));
    assertFalse(request.has("biography"));
    assertFalse(request.has("facts"));
    String prompt = prompts.getAllValues().getLast();
    assertTrue(prompt.contains("사용자가 대화를 주도"));
    assertTrue(prompt.contains("공감·경청·맞장구"));
    assertTrue(prompt.contains("새 주제로 전환"));
    generated(user, "최근 대화 참고");
    var third = org.mockito.ArgumentCaptor.forClass(JsonNode.class);
    verify(ai, times(3))
        .structured(eq("generation"), anyString(), third.capture(), any(JsonNode.class));
    assertEquals(0, third.getAllValues().getLast().path("recent").size());
  }

  @Test
  void appendKeepsOriginalTurnsAndClearsUnsavedAnalysis() {
    UUID user = user(), batch = generated(user, "추가 대화");
    var original = lab.batch(user, batch).path("turns").deepCopy();
    lab.analyze(user, batch, false);
    var result = lab.append(user, batch);
    assertEquals(batch.toString(), result.path("id").asText());
    assertEquals(20, result.path("turns").size());
    assertEquals("generated", result.path("status").asText());
    assertFalse(result.path("analysis").has("observations"));
    assertEquals(0, lab.profile(user).path("version").asInt());
    for (int i = 0; i < 10; i++) assertEquals(original.get(i), result.path("turns").get(i));
    assertEquals(20, result.path("turns").get(19).path("ordinal").asInt());
    lab.analyze(user, batch, false);
    lab.save(user, batch, false);
    assertEquals(1, lab.profile(user).path("version").asInt());
  }

  @Test
  void appendAfterSaveDoesNotApplyExistingEvidenceTwice() {
    UUID user = user(), batch = generated(user, "저장 후 추가");
    lab.analyze(user, batch, false);
    lab.save(user, batch, false);
    lab.append(user, batch);
    assertEquals(1, lab.profile(user).path("version").asInt());
    assertEquals(
        2, lab.profile(user).path("elements").path("habits.exercise").path("weight").asInt());
    lab.analyze(user, batch, false);
    var inputs = org.mockito.ArgumentCaptor.forClass(JsonNode.class);
    verify(ai, times(2))
        .structured(eq("analysis"), anyString(), inputs.capture(), any(JsonNode.class));
    assertEquals(10, inputs.getAllValues().getLast().path("sources").size());
    lab.save(user, batch, false);
    assertEquals(
        4, lab.profile(user).path("elements").path("habits.exercise").path("weight").asInt());
    assertEquals(2, lab.profile(user).path("version").asInt());
    assertEquals(30, lab.append(user, batch).path("turns").size());
  }

  @Test
  void appendFailureLeavesOriginalBatchUntouched() {
    UUID user = user(), other = user(), batch = generated(user, "실패 검증");
    var original = lab.batch(user, batch);
    assertThrows(LabException.class, () -> lab.append(other, batch));
    doThrow(new LabException(502, "generation failed"))
        .when(ai)
        .structured(eq("generation"), anyString(), any(JsonNode.class), any(JsonNode.class));
    assertThrows(LabException.class, () -> lab.append(user, batch));
    assertEquals(original, lab.batch(user, batch));
  }

  @Test
  void typedMessageStartsConversationAndIsAnAnalysisSource() {
    UUID user = user();
    String content = "오늘 저녁에는 친구와 함께 운동했어요.\n기분이 좋았어요.";
    var result = lab.message(user, null, null, content);
    UUID batch = UUID.fromString(result.path("id").asText());
    assertEquals(1, result.path("turns").size());
    assertEquals(content, result.path("turns").get(0).path("user_text").asText());
    assertEquals("", result.path("topic").asText());
    assertFalse(result.path("turns").get(0).path("assistant_text").asText().isBlank());
    assertEquals(0, lab.profile(user).path("version").asInt());
    var requests = org.mockito.ArgumentCaptor.forClass(JsonNode.class);
    verify(ai).structured(eq("answer"), anyString(), requests.capture(), any(JsonNode.class));
    assertEquals(content, requests.getValue().path("content").asText());
    assertFalse(requests.getValue().has("persona"));
    assertFalse(requests.getValue().has("catalog"));
    lab.analyze(user, batch, false);
    var analysisInputs = org.mockito.ArgumentCaptor.forClass(JsonNode.class);
    verify(ai)
        .structured(eq("analysis"), anyString(), analysisInputs.capture(), any(JsonNode.class));
    assertEquals(content, analysisInputs.getValue().path("sources").get(0).path("text").asText());
  }

  @Test
  void typedMessageAfterSaveAddsOnePairWithoutReapplyingOldEvidence() {
    UUID user = user(), batch = generated(user, "직접 대화 이어가기");
    lab.analyze(user, batch, false);
    lab.save(user, batch, false);
    var result = lab.message(user, batch, "다른 입력 주제", "오늘은 실제로 5km를 달렸어요.");
    assertEquals(11, result.path("turns").size());
    assertEquals(batch.toString(), result.path("id").asText());
    assertEquals("직접 대화 이어가기", result.path("topic").asText());
    assertEquals("generated", result.path("status").asText());
    assertEquals(1, lab.profile(user).path("version").asInt());
    lab.analyze(user, batch, false);
    var inputs = org.mockito.ArgumentCaptor.forClass(JsonNode.class);
    verify(ai, times(2))
        .structured(eq("analysis"), anyString(), inputs.capture(), any(JsonNode.class));
    assertEquals(1, inputs.getAllValues().getLast().path("sources").size());
    lab.save(user, batch, false);
    assertEquals(
        4, lab.profile(user).path("elements").path("habits.exercise").path("weight").asInt());
  }

  @Test
  void failedOrInvalidTypedMessageDoesNotChangeConversation() {
    UUID user = user(), other = user(), batch = generated(user, "메시지 실패");
    var original = lab.batch(user, batch);
    assertThrows(LabException.class, () -> lab.message(user, batch, null, "   "));
    assertThrows(LabException.class, () -> lab.message(other, batch, null, "다른 사용자"));
    doThrow(new LabException(502, "AI unavailable"))
        .when(ai)
        .structured(eq("answer"), anyString(), any(JsonNode.class), any(JsonNode.class));
    assertThrows(LabException.class, () -> lab.message(user, batch, null, "이 메시지는 실패합니다."));
    assertEquals(original, lab.batch(user, batch));
  }

  @Test
  void generationAnalysisAndSaveAreSeparateAndIdempotent() {
    UUID user = user(), batch = generated(user, "운동 " + UUID.randomUUID());
    assertTrue(lab.profile(user).path("elements").path("habits.exercise").path("score").isNull());
    lab.analyze(user, batch, false);
    assertEquals(0, lab.profile(user).path("version").asInt());
    lab.save(user, batch, false);
    assertEquals(
        80, lab.profile(user).path("elements").path("habits.exercise").path("score").asInt());
    lab.save(user, batch, false);
    assertEquals(1, lab.profile(user).path("version").asInt());
    assertEquals(1, lab.dao.mapper().memories(user).size());
    assertTrue(lab.profile(user).path("elements").path("values.family").path("score").isNull());
    UUID duplicate = generated(user, lab.batch(user, batch).path("topic").asText());
    lab.analyze(user, duplicate, false);
    lab.save(user, duplicate, false);
    assertEquals(
        2, lab.profile(user).path("elements").path("habits.exercise").path("weight").asInt());
  }

  @Test
  void invalidMergeAndEmbeddingFailureNeverPartiallySave() {
    UUID user = user(), batch = generated(user, "rollback " + UUID.randomUUID());
    lab.analyze(user, batch, false);
    when(ai.embed(anyList())).thenThrow(new LabException(502, "embedding failed"));
    assertThrows(LabException.class, () -> lab.save(user, batch, false));
    assertEquals(0, lab.profile(user).path("version").asInt());
    assertEquals("analyzed", lab.batch(user, batch).path("status").asText());
    assertTrue(lab.dao.mapper().memories(user).isEmpty());
  }

  @Test
  void userIsolationAndCurrentContextAnswer() {
    UUID owner = user(), other = user(), batch = generated(owner, "context " + UUID.randomUUID());
    lab.analyze(owner, batch, false);
    lab.save(owner, batch, false);
    assertThrows(LabException.class, () -> lab.batch(other, batch));
    assertThrows(LabException.class, () -> clone.start(other, batch));
    var session = clone.start(owner, batch);
    String source = lab.batch(owner, batch).path("turns").get(0).path("id").asText();
    when(ai.respond(anyString(), any(JsonNode.class), any(JsonNode.class), anyList()))
        .thenReturn(response("CONTEXT", false, List.of(source)));
    var answer = clone.answer(owner, UUID.fromString(session.path("sessionId").asText()), "운동하나요?");
    assertEquals("CONTEXT", answer.path("stage").asText());
    assertEquals(1, clone.history(owner).path("messages").size());
    var next = clone.start(owner, null);
    assertNotEquals(session.path("sessionId").asText(), next.path("sessionId").asText());
    assertThrows(
        LabException.class,
        () -> clone.answer(owner, UUID.fromString(session.path("sessionId").asText()), "이전 대화?"));
  }

  JsonNode response(String stage, boolean inferred, List<String> ids) {
    var r = json.createObjectNode();
    r.put("status", "completed");
    var content =
        r.putArray("output")
            .addObject()
            .put("type", "message")
            .putArray("content")
            .addObject()
            .put("type", "output_text");
    var answer = json.createObjectNode();
    answer.put("answer", "근거에 따른 테스트 답변");
    answer.put("stage", stage);
    answer.put("inferred", inferred);
    answer.set("evidenceIds", json.valueToTree(ids));
    content.put("text", answer.toString());
    return r;
  }

  JsonNode call(String name, JsonNode args) {
    var r = json.createObjectNode();
    r.put("status", "completed");
    r.putArray("output")
        .addObject()
        .put("type", "function_call")
        .put("name", name)
        .put("call_id", UUID.randomUUID().toString())
        .put("arguments", args.toString());
    return r;
  }

  @Test
  void memoryThenProfileToolsAndInsufficientRoute() {
    UUID user = user(), batch = generated(user, "tools " + UUID.randomUUID());
    lab.analyze(user, batch, false);
    lab.save(user, batch, false);
    var session = clone.start(user, null);
    var query = json.createObjectNode().put("query", "운동");
    var codes = json.createObjectNode();
    codes.putArray("codes").add("habits.exercise");
    when(ai.respond(anyString(), any(JsonNode.class), any(JsonNode.class), anyList()))
        .thenReturn(
            call("searchLongTermMemory", query),
            call("getAnalysisProfile", codes),
            response("PROFILE", true, List.of("habits.exercise")));
    var answer =
        clone.answer(user, UUID.fromString(session.path("sessionId").asText()), "오늘도 운동할까요?");
    assertEquals(2, answer.path("trace").size());
    assertTrue(answer.path("inferred").asBoolean());
    session = clone.start(user, null);
    String memory = lab.dao.mapper().memories(user).getFirst().get("id").toString();
    when(ai.respond(anyString(), any(JsonNode.class), any(JsonNode.class), anyList()))
        .thenReturn(
            call("searchLongTermMemory", query), response("MEMORY", false, List.of(memory)));
    assertEquals(
        "MEMORY",
        clone
            .answer(user, UUID.fromString(session.path("sessionId").asText()), "무슨 운동?")
            .path("stage")
            .asText());
    session = clone.start(user, null);
    when(ai.respond(anyString(), any(JsonNode.class), any(JsonNode.class), anyList()))
        .thenReturn(
            call("searchLongTermMemory", query), response("INSUFFICIENT", false, List.of()));
    assertEquals(
        "INSUFFICIENT",
        clone
            .answer(user, UUID.fromString(session.path("sessionId").asText()), "생일은?")
            .path("stage")
            .asText());
  }

  @Test
  void profileToolCannotBeCalledBeforeMemory() {
    UUID user = user();
    var session = clone.start(user, null);
    var args = json.createObjectNode();
    args.putArray("codes").add("habits.exercise");
    when(ai.respond(anyString(), any(JsonNode.class), any(JsonNode.class), anyList()))
        .thenReturn(call("getAnalysisProfile", args));
    assertThrows(
        LabException.class,
        () -> clone.answer(user, UUID.fromString(session.path("sessionId").asText()), "질문"));
  }

  @Test
  void toolCallbacksRejectDuplicateExecutionAndOwnerArguments() {
    UUID user = user();
    var session = clone.start(user, null);
    var query = json.createObjectNode().put("query", "운동");
    var duplicate = (ObjectNode) call("searchLongTermMemory", query);
    ((ArrayNode) duplicate.path("output"))
        .add(call("searchLongTermMemory", query).path("output").get(0));
    when(ai.respond(anyString(), any(JsonNode.class), any(JsonNode.class), anyList()))
        .thenReturn(duplicate);
    UUID sessionId = UUID.fromString(session.path("sessionId").asText());
    assertThrows(LabException.class, () -> clone.answer(user, sessionId, "질문"));
    verify(ai, times(2)).embed(anyList());
    assertTrue(clone.history(user).path("messages").isEmpty());
    clearInvocations(ai);
    query.put("ownerId", UUID.randomUUID().toString());
    when(ai.respond(anyString(), any(JsonNode.class), any(JsonNode.class), anyList()))
        .thenReturn(call("searchLongTermMemory", query));
    assertThrows(LabException.class, () -> clone.answer(user, sessionId, "질문"));
    verify(ai, times(1)).embed(anyList());
  }

  @Test
  void databaseChecksAndSchemaAreRealPostgres() {
    assertEquals(
        11,
        jdbc.queryForObject(
            "SELECT count(*) FROM information_schema.tables WHERE table_schema='clone_lab'",
            Integer.class));
    UUID user = user();
    assertThrows(
        Exception.class,
        () ->
            jdbc.update(
                "UPDATE clone_lab.profile_relations SET social_orientation=101 WHERE user_id=?",
                user));
  }

  @Test
  void schemaInitializationIsIdempotent() {
    Integer before = jdbc.queryForObject("SELECT count(*) FROM pg_constraint WHERE connamespace='clone_lab'::regnamespace", Integer.class);
    var populator = new org.springframework.jdbc.datasource.init.ResourceDatabasePopulator(
        new org.springframework.core.io.ClassPathResource("clone-lab/schema.sql"));
    populator.setSeparator(org.springframework.jdbc.datasource.init.ScriptUtils.EOF_STATEMENT_SEPARATOR);
    populator.execute(jdbc.getDataSource());
    assertEquals(before, jdbc.queryForObject("SELECT count(*) FROM pg_constraint WHERE connamespace='clone_lab'::regnamespace", Integer.class));
  }

  @Test
  void realHttpViewsCatalogAndValidation() throws Exception {
    mvc.perform(get("/lab"))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith("text/html"));
    mvc.perform(get("/lab/viewer")).andExpect(status().isOk());
    mvc.perform(get("/lab/assets/schema.sql")).andExpect(status().isNotFound());
    mvc.perform(get("/api/lab/catalog")).andExpect(jsonPath("$.domains.length()").value(7));
    UUID user = user();
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
    UUID user = user(), batch = generated(user, "embedding failure");
    lab.analyze(user, batch, false);
    when(ai.embed(anyList())).thenThrow(new LabException(502, "temporary failure"));
    assertThrows(LabException.class, () -> lab.save(user, batch, false));
    assertEquals(0, lab.profile(user).path("version").asInt());
    assertTrue(lab.dao.mapper().memories(user).isEmpty());
  }

  @Test
  void databaseFailureRollsBackAllSevenProfilesAndBatch() {
    UUID user = user(), batch = generated(user, "DB rollback " + UUID.randomUUID());
    lab.analyze(user, batch, false);
    String constraint = "lab_test_" + user.toString().replace("-", "");
    jdbc.execute(
        "ALTER TABLE clone_lab.vector_store ADD CONSTRAINT "
            + constraint
            + " CHECK((metadata->>'clone_owner_id')::uuid <> '"
            + user
            + "'::uuid)");
    try {
      assertThrows(Exception.class, () -> lab.save(user, batch, false));
      assertEquals(0, lab.profile(user).path("version").asInt());
      assertEquals("analyzed", lab.batch(user, batch).path("status").asText());
      assertTrue(lab.profile(user).path("elements").path("habits.exercise").path("score").isNull());
    } finally {
      jdbc.execute("ALTER TABLE clone_lab.vector_store DROP CONSTRAINT " + constraint);
    }
  }

  @Test
  void simultaneousSavesApplyTheBatchOnlyOnce() throws Exception {
    UUID user = user(), batch = generated(user, "concurrent " + UUID.randomUUID());
    lab.analyze(user, batch, false);
    var ready = new java.util.concurrent.CountDownLatch(2);
    when(ai.embed(anyList()))
        .thenAnswer(
            call -> {
              ready.countDown();
              assertTrue(ready.await(10, java.util.concurrent.TimeUnit.SECONDS));
              List<?> list = call.getArgument(0);
              return list.stream()
                  .map(
                      x -> {
                        var v = new float[1536];
                        v[0] = 1;
                        return v;
                      })
                  .toList();
            });
    try (var executor = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
      var a = executor.submit(() -> lab.save(user, batch, false));
      var b = executor.submit(() -> lab.save(user, batch, false));
      assertEquals(1, a.get(15, java.util.concurrent.TimeUnit.SECONDS).path("version").asInt());
      assertEquals(1, b.get(15, java.util.concurrent.TimeUnit.SECONDS).path("version").asInt());
    }
    assertEquals(1, lab.dao.mapper().memories(user).size());
    assertEquals(
        2, lab.profile(user).path("elements").path("habits.exercise").path("weight").asInt());
  }

  @Test
  void pipelineGeneratesOneConversationAndCommitsOneHundredOrderedSegments() throws Exception {
    UUID user = user();
    var delegate =
        mockingDetails(ai).getStubbings().stream()
            .filter(s -> s.getInvocation().getMethod().getName().equals("structured"))
            .findFirst()
            .orElseThrow();
    var generationLive = new java.util.concurrent.atomic.AtomicInteger();
    var analysisLive = new java.util.concurrent.atomic.AtomicInteger();
    var generationMax = new java.util.concurrent.atomic.AtomicInteger();
    var analysisMax = new java.util.concurrent.atomic.AtomicInteger();
    var backlogMax = new java.util.concurrent.atomic.AtomicInteger();
    var generatedCalls = new java.util.concurrent.atomic.AtomicInteger();
    var firstAnalysisCount = new java.util.concurrent.atomic.AtomicInteger(-1);
    var overlap = new java.util.concurrent.atomic.AtomicBoolean();
    var together = new java.util.concurrent.CountDownLatch(2);
    when(ai.structured(anyString(), anyString(), any(JsonNode.class), any(JsonNode.class)))
        .thenAnswer(
            call -> {
              String stage = call.getArgument(0);
              boolean generation = stage.equals("generation"), analysis = stage.equals("analysis");
              if (generation)
                generationMax.accumulateAndGet(generationLive.incrementAndGet(), Math::max);
              if (analysis) analysisMax.accumulateAndGet(analysisLive.incrementAndGet(), Math::max);
              try {
                var current = runs.state(user);
                if (generation) {
                  int reservation = current.path("generationReservation").path("to").asInt();
                  backlogMax.accumulateAndGet(
                      reservation - current.path("savedPairs").asInt(), Math::max);
                  if (generatedCalls.incrementAndGet() == 2) {
                    together.countDown();
                    if (!together.await(5, java.util.concurrent.TimeUnit.SECONDS))
                      throw new LabException(502, "overlap timeout");
                  }
                }
                if (analysis
                    && firstAnalysisCount.compareAndSet(
                        -1, current.path("generatedPairs").asInt())) {
                  together.countDown();
                  if (!together.await(5, java.util.concurrent.TimeUnit.SECONDS))
                    throw new LabException(502, "overlap timeout");
                }
                if (generationLive.get() > 0 && analysisLive.get() > 0) overlap.set(true);
                return delegate.answer(call);
              } finally {
                if (generation) generationLive.decrementAndGet();
                if (analysis) analysisLive.decrementAndGet();
              }
            });
    runs.start(user, "pipeline " + UUID.randomUUID(), false);
    JsonNode state = awaitRun(user, 60);
    assertEquals("completed", state.path("status").asText(), state.toString());
    assertEquals(LabPipeline.MODE, state.path("mode").asText());
    assertEquals(100, state.path("completedBatches").asInt());
    assertEquals(1000, state.path("generatedPairs").asInt());
    assertEquals(1000, state.path("analyzedPairs").asInt());
    assertEquals(1000, state.path("savedPairs").asInt());
    assertEquals(100, lab.profile(user).path("version").asInt());
    assertEquals(1, lab.dao.mapper().batches(user).size());
    var batch = lab.batch(user, UUID.fromString(state.path("batchId").asText()));
    assertEquals(100, batch.path("turns").size());
    assertEquals(1000, batch.path("turn_count").asInt());
    assertEquals(
        900,
        jdbc.queryForObject(
            "SELECT count(*) FROM clone_lab.vector_store WHERE metadata->>'clone_owner_id'=? AND"
                + " metadata->>'record_type'='context_archive'",
            Integer.class,
            user.toString()));
    assertEquals(100, batch.path("analysis").path("segments").size());
    int index = 0;
    for (var segment : batch.path("analysis").path("segments")) {
      assertEquals("saved", segment.path("status").asText());
      assertEquals(++index, segment.path("profileVersion").asInt());
      assertEquals(10, segment.path("sourceIds").size());
      assertTrue(segment.path("analysis").path("summaries").size() <= 5);
    }
    for (String code : catalog.codes())
      assertTrue(
          lab.profile(user).path("elements").path(code).path("weight").asInt()
              <= catalog.maxWeight(code));
    assertEquals(
        10, lab.profile(user).path("elements").path("habits.exercise").path("weight").asInt());
    verify(ai, never())
        .structured(eq("merge"), anyString(), any(JsonNode.class), any(JsonNode.class));
    verify(ai, times(100))
        .structured(eq("analysis"), anyString(), any(JsonNode.class), any(JsonNode.class));
    verify(ai, times(99))
        .structured(eq("summary"), anyString(), any(JsonNode.class), any(JsonNode.class));
    assertTrue(overlap.get());
    assertEquals(1, generationMax.get());
    assertEquals(1, analysisMax.get());
    assertTrue(backlogMax.get() <= 20);
    assertTrue(firstAnalysisCount.get() < 1000);
  }

  JsonNode awaitRun(UUID user, int seconds) throws Exception {
    long deadline = System.nanoTime() + java.time.Duration.ofSeconds(seconds).toNanos();
    JsonNode state;
    do {
      Thread.sleep(20);
      state = runs.state(user);
    } while (Set.of("running", "stopping").contains(state.path("status").asText())
        && System.nanoTime() < deadline);
    return state;
  }

  @Test
  void stopPreservesCurrentGenerationAnalysisOrSaveAndRecoveryKeepsCheckpoints() throws Exception {
    for (String phase : List.of("generation", "analysis", "embedding")) {
      fixture();
      UUID user = user();
      var delegate =
          mockingDetails(ai).getStubbings().stream()
              .filter(s -> s.getInvocation().getMethod().getName().equals("structured"))
              .findFirst()
              .orElseThrow();
      var entered = new java.util.concurrent.CountDownLatch(1);
      var release = new java.util.concurrent.CountDownLatch(1);
      var blocked = new java.util.concurrent.atomic.AtomicBoolean();
      if (phase.equals("embedding"))
        when(ai.embed(anyList()))
            .thenAnswer(
                call -> {
                  List<?> texts = call.getArgument(0);
                  if (!texts.isEmpty()
                      && !texts.getFirst().toString().startsWith("USER:")
                      && blocked.compareAndSet(false, true)) {
                    entered.countDown();
                    if (!release.await(5, java.util.concurrent.TimeUnit.SECONDS))
                      throw new LabException(502, "timeout");
                  }
                  return texts.stream()
                      .map(
                          x -> {
                            var v = new float[1536];
                            v[0] = 1;
                            return v;
                          })
                      .toList();
                });
      else
        when(ai.structured(anyString(), anyString(), any(JsonNode.class), any(JsonNode.class)))
            .thenAnswer(
                call -> {
                  if (phase.equals(call.getArgument(0)) && blocked.compareAndSet(false, true)) {
                    entered.countDown();
                    if (!release.await(5, java.util.concurrent.TimeUnit.SECONDS))
                      throw new LabException(502, "timeout");
                  }
                  return delegate.answer(call);
                });
      runs.start(user, "stop " + phase, false);
      assertTrue(entered.await(5, java.util.concurrent.TimeUnit.SECONDS));
      runs.stop(user);
      release.countDown();
      var paused = awaitRun(user, 15);
      assertEquals("paused", paused.path("status").asText(), paused.toString());
      assertTrue(paused.path("generatedPairs").asInt() <= 20);
      assertEquals(phase.equals("embedding") ? 10 : 0, paused.path("savedPairs").asInt());
      assertEquals(phase.equals("generation") ? 0 : 10, paused.path("analyzedPairs").asInt());
      UUID batch = UUID.fromString(paused.path("batchId").asText());
      var before = lab.batch(user, batch).deepCopy();
      assertThrows(LabException.class, () -> lab.analyze(user, batch, false));
      lab.tx.executeWithoutResult(
          s -> {
            var row = lab.dao.mapper().lockUser(user);
            var run = (ObjectNode) lab.dao.parse(row.get("run_state"));
            run.put("status", "running");
            run.put("activeSince", System.currentTimeMillis() - 5000);
            run.put("heartbeatAt", System.currentTimeMillis() - 2000);
            var reservation = run.putObject("generationReservation");
            reservation.put("from", run.path("generatedPairs").asInt() + 1);
            reservation.put("to", run.path("generatedPairs").asInt() + 10);
            lab.dao.mapper().runState(user, run.toString());
          });
      runs.recoverInterrupted();
      var recovered = runs.state(user);
      assertEquals("paused", recovered.path("status").asText());
      assertFalse(recovered.has("activeSince"));
      assertTrue(recovered.path("wallElapsedMs").asLong() >= 3000);
      assertTrue(recovered.has("generationReservation"));
      assertEquals(before, lab.batch(user, batch));
    }
  }

  @Test
  void duplicateSegmentEvidenceIsNotReappliedAndConcurrentSaveIsIdempotent() throws Exception {
    UUID user = user();
    var delegate =
        mockingDetails(ai).getStubbings().stream()
            .filter(s -> s.getInvocation().getMethod().getName().equals("structured"))
            .findFirst()
            .orElseThrow();
    var first = new java.util.concurrent.atomic.AtomicReference<LabAi.Result>();
    when(ai.structured(anyString(), anyString(), any(JsonNode.class), any(JsonNode.class)))
        .thenAnswer(
            call -> {
              String stage = call.getArgument(0);
              if (stage.equals("generation")) {
                if (first.get() == null) first.set((LabAi.Result) delegate.answer(call));
                return first.get();
              }
              if (stage.equals("summary")) runs.stop(user);
              return delegate.answer(call);
            });
    runs.start(user, "duplicate", false);
    var paused = awaitRun(user, 15);
    assertEquals("paused", paused.path("status").asText(), paused.toString());
    assertEquals(10, paused.path("savedPairs").asInt());
    UUID batch = UUID.fromString(paused.path("batchId").asText());
    assertTrue(
        lab.batch(user, batch)
            .path("analysis")
            .path("segments")
            .get(1)
            .path("analysis")
            .path("observations")
            .isEmpty());
    var ready = new java.util.concurrent.CountDownLatch(2);
    when(ai.embed(anyList()))
        .thenAnswer(
            call -> {
              ready.countDown();
              if (!ready.await(5, java.util.concurrent.TimeUnit.SECONDS))
                throw new LabException(502, "timeout");
              return List.of();
            });
    try (var executor = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
      var a = executor.submit(() -> pipeline.saveSegment(user, batch, 2));
      var b = executor.submit(() -> pipeline.saveSegment(user, batch, 2));
      a.get(10, java.util.concurrent.TimeUnit.SECONDS);
      b.get(10, java.util.concurrent.TimeUnit.SECONDS);
    }
    assertEquals(2, lab.profile(user).path("version").asInt());
    assertEquals(
        2, lab.profile(user).path("elements").path("habits.exercise").path("weight").asInt());
    assertEquals(20, lab.batch(user, batch).path("analysis").path("processedSourceIds").size());
    verify(ai, times(1))
        .structured(eq("analysis"), anyString(), any(JsonNode.class), any(JsonNode.class));
  }

  @Test
  void pipelineFailureResumesDraftAndPreservesGeneratedTurns() throws Exception {
    UUID user = user();
    when(ai.embed(anyList()))
        .thenAnswer(
            call -> {
              List<?> texts = call.getArgument(0);
              if (texts.stream().allMatch(x -> x.toString().startsWith("USER:")))
                return texts.stream()
                    .map(
                        x -> {
                          var v = new float[1536];
                          v[0] = 1;
                          return v;
                        })
                    .toList();
              throw new LabException(502, "temporary failure");
            });
    runs.start(user, "resume " + UUID.randomUUID(), false);
    var stopped = awaitRun(user, 15);
    assertEquals("failed", stopped.path("status").asText(), stopped.toString());
    UUID batch = UUID.fromString(stopped.path("batchId").asText());
    var before = lab.batch(user, batch).path("turns").deepCopy();
    assertTrue(before.size() <= 20);
    assertEquals(0, lab.profile(user).path("version").asInt());
    assertEquals(
        "analyzed",
        lab.batch(user, batch).path("analysis").path("segments").get(0).path("status").asText());
    assertThrows(LabException.class, () -> lab.append(user, batch));
    fixture();
    runs.start(user, null, true);
    var completed = awaitRun(user, 60);
    assertEquals("completed", completed.path("status").asText(), completed.toString());
    assertEquals(batch.toString(), completed.path("batchId").asText());
    var after = lab.batch(user, batch).path("turns");
    for (var prior : before) {
      String id = prior.path("id").asText();
      assertEquals(
          1,
          jdbc.queryForObject(
              "SELECT count(*) FROM clone_lab.vector_store WHERE metadata->>'extraction_key'=?",
              Integer.class,
              "context:" + id));
    }
    assertEquals(100, after.size());
    assertEquals(100, lab.profile(user).path("version").asInt());
    verify(ai, times((1000 - before.size()) / 10))
        .structured(eq("generation"), anyString(), any(JsonNode.class), any(JsonNode.class));
    verify(ai, times(99))
        .structured(eq("analysis"), anyString(), any(JsonNode.class), any(JsonNode.class));
  }

  @Test
  void previouslySavedSingleSessionRunResumesWithoutChangingProfile() throws Exception {
    UUID user = user(), batch = generated(user, "legacy");
    lab.tx.executeWithoutResult(
        s -> {
          for (int n = 11; n <= 1000; n++)
            fixtureTurn(user, batch, n, "legacy evidence " + n, "assistant");
        });
    lab.analyze(user, batch, false);
    lab.save(user, batch, false);
    long version = lab.profile(user).path("version").asLong();
    lab.tx.executeWithoutResult(
        s -> {
          var state = lab.object();
          state.put("mode", "single-session-v1");
          state.put("status", "paused");
          state.put("runId", UUID.randomUUID().toString());
          state.put("batchId", batch.toString());
          state.put("generatedPairs", 1000);
          state.put("analysisCounted", true);
          state.put("topic", "legacy");
          lab.dao.mapper().runState(user, state.toString());
        });
    fixture();
    runs.start(user, null, true);
    assertEquals("completed", awaitRun(user, 10).path("status").asText());
    assertEquals(version, lab.profile(user).path("version").asLong());
    assertEquals(1, lab.dao.mapper().batches(user).size());
    verify(ai, never())
        .structured(anyString(), anyString(), any(JsonNode.class), any(JsonNode.class));
  }
}
