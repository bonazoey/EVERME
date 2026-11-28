package com.example.everme.lab.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

class LabAnswerEvidenceTests {
  private final ObjectMapper json = new ObjectMapper();

  private ObjectNode answer(String stage, String... ids) {
    var answer =
        json.createObjectNode()
            .put("stage", stage)
            .put("reason", "인용한 자료로 확인했습니다.")
            .put("inferred", "PROFILE".equals(stage));
    var evidence = answer.putArray("evidenceIds");
    for (String id : ids) evidence.add(id);
    return answer;
  }

  @Test
  void distinguishesProvidedFromCitedAndNotChecked() {
    var evidence = new LabAnswerEvidence(json);
    evidence.add("CHAT_CONTEXT", "context:1:user", json.createObjectNode().put("content", "직전 질문"));
    evidence.checked("SUMMARY");
    evidence.add("SESSION_RAG", "10", json.createObjectNode().put("content", "소유자 원문"));
    var result = evidence.describe(answer("SESSION_RAG", "10"));
    assertEquals(1, result.path("sources").size());
    assertEquals("PROVIDED", result.path("routes").get(0).path("status").asText());
    assertEquals("EMPTY", result.path("routes").get(1).path("status").asText());
    assertEquals("USED", result.path("routes").get(2).path("status").asText());
    assertEquals("NOT_CHECKED", result.path("routes").get(3).path("status").asText());
  }

  @Test
  void supportsEachSourceAndPreservesSnapshot() {
    for (String type :
        java.util.List.of(
            "CHAT_CONTEXT", "SUMMARY", "SESSION_RAG", "MEMORY", "PROFILE", "VIEWER_RAG")) {
      var evidence = new LabAnswerEvidence(json);
      var detail = json.createObjectNode().put("content", "원본");
      evidence.add(type, "id", detail);
      detail.put("content", "변경");
      assertEquals(
          "원본",
          evidence.describe(answer(type, "id")).path("sources").get(0).path("content").asText());
    }
  }

  @Test
  void rejectsInventedCitationOrMislabelledViewerContext() {
    var evidence = new LabAnswerEvidence(json);
    evidence.add("CHAT_CONTEXT", "context:1:user", json.createObjectNode());
    assertThrows(LabException.class, () -> evidence.describe(answer("CHAT_CONTEXT", "unknown")));
    assertThrows(
        LabException.class, () -> evidence.describe(answer("SESSION_RAG", "context:1:user")));
    assertThrows(LabException.class, () -> evidence.describe(answer("SUMMARY", "context:1:user")));
    evidence.add("VIEWER_RAG", "viewer:1", json.createObjectNode());
    assertThrows(LabException.class, () -> evidence.describe(answer("SESSION_RAG", "viewer:1")));
    assertThrows(LabException.class, () -> evidence.describe(answer("MEMORY", "viewer:1")));
  }

  @Test
  void requiresJudgmentSummaryAndMarksInsufficientWithoutInventingSource() {
    var evidence = new LabAnswerEvidence(json);
    var input = answer("INSUFFICIENT");
    assertEquals(0, evidence.describe(input).path("sources").size());
    input.put("reason", "");
    assertThrows(LabException.class, () -> evidence.describe(input));
  }
}
