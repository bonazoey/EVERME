package com.example.everme.lab.service;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 서버가 실제 제공한 자료만 출처로 인정하고 조회 여부와 인용 여부를 구분한다. */
final class LabAnswerEvidence {
  private static final List<String> TYPES =
      List.of(
          "CHAT_CONTEXT", "SUMMARY", "SESSION_RAG", "MEMORY", "PROFILE", "VIEWER_RAG");
  private final Map<String, Map<String, Object>> sources = new LinkedHashMap<>();
  private final Set<String> checked = new HashSet<>();

  LabAnswerEvidence() {}

  void checked(String type) {
    checked.add(type);
  }

  void add(String type, String id, Object details) {
    checked(type);
    Map<String, Object> source = LabJson.isMap(details) ? LabJson.copy(LabJson.map(details)) : LabJson.object();
    source.put("id", id);
    source.put("type", type);
    sources.put(id, source);
  }

  // ##### 인용 ID·단계를 검증하고 화면용 출처 스냅샷을 만드는 메소드 #####
  Map<String, Object> describe(Map<String, Object> answer) {
    String stage = LabJson.text(answer.get("stage"));
    Set<String> used = new LinkedHashSet<>();
    Set<String> types = new HashSet<>();
    for (Object idValue : LabJson.list(answer.get("evidenceIds"))) {
      String id = LabJson.text(idValue);
      Map<String, Object> source = sources.get(id);
      if (source == null) throw LabException.invalid("답변이 제공되지 않은 출처를 참조했습니다.");
      used.add(id);
      types.add(LabJson.text(source.get("type")));
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
          case "PROFILE" -> types.contains("PROFILE") && LabJson.bool(answer.get("inferred"));
          case "INSUFFICIENT" -> true;
          default -> false;
        };
    if (!valid) throw LabException.invalid("답변 단계와 실제 인용 출처가 일치하지 않습니다.");
    LabValidation.text(LabJson.text(answer.get("reason")), 2000, "답변 판단 근거");
    Map<String, Object> result = LabJson.object();
    List<Object> cited = LabJson.childList(result, "sources");
    used.forEach(id -> cited.add(sources.get(id)));
    List<Object> routes = LabJson.childList(result, "routes");
    for (String type : TYPES) {
      long count =
          sources.values().stream().filter(s -> type.equals(LabJson.text(s.get("type")))).count();
      long citedCount =
          used.stream()
              .filter(id -> type.equals(LabJson.text(sources.get(id).get("type"))))
              .count();
      Map<String, Object> route = LabJson.object();
      route.put("type", type);
      route.put("availableCount", count);
      route.put("citedCount", citedCount);
      route.put(
          "status",
          citedCount > 0
              ? "USED"
              : !checked.contains(type) ? "NOT_CHECKED" : count > 0 ? "PROVIDED" : "EMPTY");
      routes.add(route);
    }
    return result;
  }
}
