package com.example.everme.lab.service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/** 서버가 실제 제공한 자료만 출처로 인정하고 조회 여부와 인용 여부를 구분한다. */
final class LabAnswerEvidence {
  private static final java.util.List<String> TYPES =
      java.util.List.of(
          "CHAT_CONTEXT", "SUMMARY", "SESSION_RAG", "MEMORY", "PROFILE", "VIEWER_RAG");
  private final ObjectMapper json;
  private final Map<String, ObjectNode> sources = new LinkedHashMap<>();
  private final Set<String> checked = new java.util.HashSet<>();

  LabAnswerEvidence(ObjectMapper json) {
    this.json = json;
  }

  void checked(String type) {
    checked.add(type);
  }

  void add(String type, String id, JsonNode details) {
    checked(type);
    ObjectNode source =
        details.isObject() ? ((ObjectNode) details).deepCopy() : json.createObjectNode();
    source.put("id", id).put("type", type);
    sources.put(id, source);
  }

  // ##### 인용 ID·단계를 검증하고 화면용 출처 스냅샷을 만드는 메소드 #####
  ObjectNode describe(JsonNode answer) {
    String stage = answer.path("stage").asText();
    Set<String> used = new java.util.LinkedHashSet<>();
    Set<String> types = new java.util.HashSet<>();
    for (JsonNode id : answer.path("evidenceIds")) {
      ObjectNode source = sources.get(id.asText());
      if (source == null) throw LabException.invalid("답변이 제공되지 않은 출처를 참조했습니다.");
      used.add(id.asText());
      types.add(source.path("type").asText());
    }
    boolean valid =
        switch (stage) {
          case "CHAT_CONTEXT" -> types.equals(Set.of("CHAT_CONTEXT"));
          case "SUMMARY" ->
              types.contains("SUMMARY") && Set.of("CHAT_CONTEXT", "SUMMARY").containsAll(types);
          case "VIEWER_RAG" ->
              types.contains("VIEWER_RAG")
                  && Set.of("CHAT_CONTEXT", "SUMMARY", "VIEWER_RAG").containsAll(types);
          case "SESSION_RAG", "CONTEXT" ->
              types.contains("SESSION_RAG")
                  && !types.contains("MEMORY")
                  && !types.contains("PROFILE");
          case "MEMORY" -> types.contains("MEMORY") && !types.contains("PROFILE");
          case "PROFILE" -> types.contains("PROFILE") && answer.path("inferred").asBoolean();
          case "INSUFFICIENT" -> true;
          default -> false;
        };
    if (!valid) throw LabException.invalid("답변 단계와 실제 인용 출처가 일치하지 않습니다.");
    LabValidation.text(answer.path("reason").asText(), 2000, "답변 판단 근거");
    ObjectNode result = json.createObjectNode();
    var cited = result.putArray("sources");
    used.forEach(id -> cited.add(sources.get(id)));
    var routes = result.putArray("routes");
    for (String type : TYPES) {
      long count =
          sources.values().stream().filter(s -> type.equals(s.path("type").asText())).count();
      long citedCount =
          used.stream().filter(id -> type.equals(sources.get(id).path("type").asText())).count();
      routes
          .addObject()
          .put("type", type)
          .put("availableCount", count)
          .put("citedCount", citedCount)
          .put(
              "status",
              citedCount > 0
                  ? "USED"
                  : !checked.contains(type) ? "NOT_CHECKED" : count > 0 ? "PROVIDED" : "EMPTY");
    }
    return result;
  }
}
