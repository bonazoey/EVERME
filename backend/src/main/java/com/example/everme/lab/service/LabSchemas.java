package com.example.everme.lab.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Strict Responses JSON schemas. All object keys required; uncertainty is explicit null. */
public class LabSchemas {
  // ##### 필드 #####
  private final ObjectMapper json;

  // ##### 생성자 #####
  public LabSchemas(ObjectMapper json) {
    this.json = json;
  }

  public JsonNode string() {
    return json.valueToTree(Map.of("type", "string"));
  }

  public JsonNode number() {
    return json.valueToTree(Map.of("type", "number", "minimum", 0, "maximum", 100));
  }

  public JsonNode integer(int min, int max) {
    return json.valueToTree(Map.of("type", "integer", "minimum", min, "maximum", max));
  }

  public JsonNode nullable(JsonNode node) {
    return json.valueToTree(Map.of("anyOf", List.of(node, Map.of("type", "null"))));
  }

  public JsonNode array(JsonNode node) {
    return json.valueToTree(Map.of("type", "array", "items", node));
  }

  public JsonNode array(JsonNode node, int minItems, int maxItems) {
    return json.valueToTree(
        Map.of("type", "array", "items", node, "minItems", minItems, "maxItems", maxItems));
  }

  public JsonNode object(Map<String, JsonNode> props) {
    return json.valueToTree(
        Map.of(
            "type",
            "object",
            "properties",
            props,
            "required",
            new ArrayList<>(props.keySet()),
            "additionalProperties",
            false));
  }

  public JsonNode enumeration(Collection<String> values) {
    return json.valueToTree(Map.of("type", "string", "enum", values));
  }

  public JsonNode persona(LabCatalog catalog) {
    LinkedHashMap<String, JsonNode> scores = new LinkedHashMap<String, JsonNode>();
    catalog.codes().forEach(c -> scores.put(c, number()));
    return object(
        Map.of("biography", string(), "scores", object(scores), "facts", array(string())));
  }

  public JsonNode turns() {
    return object(Map.of("turns", array(object(Map.of("user", string(), "assistant", string())))));
  }

  public JsonNode analysis(LabCatalog catalog) {
    JsonNode element =
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
    JsonNode memory =
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
  public JsonNode answer() {
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
            json.valueToTree(Map.of("type", "boolean")),
            "evidenceIds",
            array(string())));
  }

  public JsonNode tool(String name, String description, Map<String, JsonNode> props) {
    return json.valueToTree(
        Map.of(
            "type",
            "function",
            "name",
            name,
            "description",
            description,
            "strict",
            true,
            "parameters",
            object(props)));
  }
}
