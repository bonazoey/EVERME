package com.example.everme.lab.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Strict Responses JSON schemas. All object keys required; uncertainty is explicit null. */
public class LabSchemas {
  // 스키마는 순수 Map·List로 만든다. 직렬화는 LLM 호출 시 ObjectMapper가 맡는다.
  public LabSchemas() {}

  public Map<String, Object> string() {
    return Map.of("type", "string");
  }

  public Map<String, Object> number() {
    return Map.of("type", "number", "minimum", 0, "maximum", 100);
  }

  public Map<String, Object> integer(int min, int max) {
    return Map.of("type", "integer", "minimum", min, "maximum", max);
  }

  public Map<String, Object> nullable(Map<String, Object> node) {
    return Map.of("anyOf", List.of(node, Map.of("type", "null")));
  }

  public Map<String, Object> array(Map<String, Object> node) {
    return Map.of("type", "array", "items", node);
  }

  public Map<String, Object> array(Map<String, Object> node, int minItems, int maxItems) {
    return Map.of("type", "array", "items", node, "minItems", minItems, "maxItems", maxItems);
  }

  public Map<String, Object> object(Map<String, Map<String, Object>> props) {
    return Map.of(
        "type",
        "object",
        "properties",
        props,
        "required",
        new ArrayList<>(props.keySet()),
        "additionalProperties",
        false);
  }

  public Map<String, Object> enumeration(Collection<String> values) {
    return Map.of("type", "string", "enum", List.copyOf(values));
  }

  public Map<String, Object> persona(LabCatalog catalog) {
    LinkedHashMap<String, Map<String, Object>> scores =
        new LinkedHashMap<String, Map<String, Object>>();
    catalog.codes().forEach(c -> scores.put(c, number()));
    return object(
        Map.of("biography", string(), "scores", object(scores), "facts", array(string())));
  }

  public Map<String, Object> turns() {
    return object(Map.of("turns", array(object(Map.of("user", string(), "assistant", string())))));
  }

  public Map<String, Object> analysis(LabCatalog catalog) {
    Map<String, Object> element =
        object(
            Map.of(
                "code",
                enumeration(catalog.codes()),
                "score",
                number(),
                "strength",
                integer(1, 3),
                "sourceIds",
                array(string()),
                "reason",
                string()));
    Map<String, Object> memory =
        object(
            Map.of(
                "kind",
                enumeration(
                    List.of(
                        "FACT",
                        "EXPERIENCE",
                        "PREFERENCE",
                        "CONDITIONAL_PREFERENCE",
                        "VALUE",
                        "HABIT",
                        "GOAL",
                        "RELATIONSHIP")),
                "content",
                string(),
                "sourceIds",
                array(string()),
                "occurredAt",
                nullable(string())));
    return object(Map.of("observations", array(element), "memories", array(memory)));
  }

  // ##### 정보이용자 질문에 답변하는 메소드 #####
  public Map<String, Object> answer() {
    return object(
        Map.of(
            "answer",
            string(),
            "stage",
            enumeration(
                List.of(
                    "CHAT_CONTEXT",
                    "VIEWER_RAG",
                    "SUMMARY",
                    "SESSION_RAG",
                    "CONTEXT",
                    "MEMORY",
                    "PROFILE",
                    "INSUFFICIENT")),
            "reason",
            string(),
            "inferred",
            Map.of("type", "boolean"),
            "evidenceIds",
            array(string())));
  }

  public Map<String, Object> tool(
      String name, String description, Map<String, Map<String, Object>> props) {
    return Map.of(
        "type",
        "function",
        "name",
        name,
        "description",
        description,
        "strict",
        true,
        "parameters",
        object(props));
  }
}
