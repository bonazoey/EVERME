package com.example.everme.lab.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class LabAnswerEvidenceTests {
  private Map<String, Object> answer(String stage, String... ids) {
    Map<String, Object> answer = LabJson.object();
    answer.put("stage", stage);
    answer.put("reason", "인용한 자료로 확인했습니다.");
    answer.put("inferred", "PROFILE".equals(stage));
    answer.put("evidenceIds", new ArrayList<Object>(List.of((Object[]) ids)));
    return answer;
  }

  private static Map<String, Object> content(String text) {
    Map<String, Object> detail = LabJson.object();
    detail.put("content", text);
    return detail;
  }

  private static String routeStatus(Map<String, Object> result, int index) {
    return LabJson.text(LabJson.path(LabJson.list(result.get("routes")).get(index), "status"));
  }

  @Test
  void distinguishesProvidedFromCitedAndNotChecked() {
    LabAnswerEvidence evidence = new LabAnswerEvidence();
    evidence.add("CHAT_CONTEXT", "context:1:user", content("직전 질문"));
    evidence.checked("SUMMARY");
    evidence.add("SESSION_RAG", "10", content("소유자 원문"));
    Map<String, Object> result = evidence.describe(answer("SESSION_RAG", "10"));
    assertEquals(1, LabJson.list(result.get("sources")).size());
    assertEquals("PROVIDED", routeStatus(result, 0));
    assertEquals("EMPTY", routeStatus(result, 1));
    assertEquals("USED", routeStatus(result, 2));
    assertEquals("NOT_CHECKED", routeStatus(result, 3));
  }

  @Test
  void supportsEachSourceAndPreservesSnapshot() {
    for (String type :
        List.of(
            "CHAT_CONTEXT", "SUMMARY", "SESSION_RAG", "MEMORY", "PROFILE", "VIEWER_RAG")) {
      LabAnswerEvidence evidence = new LabAnswerEvidence();
      Map<String, Object> detail = content("원본");
      evidence.add(type, "id", detail);
      detail.put("content", "변경");
      Object first = LabJson.list(evidence.describe(answer(type, "id")).get("sources")).get(0);
      assertEquals("원본", LabJson.text(LabJson.path(first, "content")));
    }
  }

  @Test
  void rejectsInventedCitationOrMislabelledViewerContext() {
    LabAnswerEvidence evidence = new LabAnswerEvidence();
    evidence.add("CHAT_CONTEXT", "context:1:user", LabJson.object());
    assertThrows(LabException.class, () -> evidence.describe(answer("CHAT_CONTEXT", "unknown")));
    assertThrows(
        LabException.class, () -> evidence.describe(answer("SESSION_RAG", "context:1:user")));
    assertThrows(LabException.class, () -> evidence.describe(answer("SUMMARY", "context:1:user")));
    evidence.add("VIEWER_RAG", "viewer:1", LabJson.object());
    assertThrows(LabException.class, () -> evidence.describe(answer("SESSION_RAG", "viewer:1")));
    assertThrows(LabException.class, () -> evidence.describe(answer("MEMORY", "viewer:1")));
  }

  @Test
  void requiresJudgmentSummaryAndMarksInsufficientWithoutInventingSource() {
    LabAnswerEvidence evidence = new LabAnswerEvidence();
    Map<String, Object> input = answer("INSUFFICIENT");
    assertEquals(0, LabJson.list(evidence.describe(input).get("sources")).size());
    input.put("reason", "");
    assertThrows(LabException.class, () -> evidence.describe(input));
  }
}
