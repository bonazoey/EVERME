package com.example.everme.lab.service;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import java.util.*;

/**
 * Strict Responses JSON schemas. All object keys required; uncertainty is
 * explicit null.
 */
public class LabSchemas {
    private final ObjectMapper json;

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

    public JsonNode object(Map<String, JsonNode> props) {
        return json.valueToTree(Map.of("type", "object", "properties", props, "required",
                new ArrayList<>(props.keySet()), "additionalProperties", false));
    }

    public JsonNode enumeration(Collection<String> values) {
        return json.valueToTree(Map.of("type", "string", "enum", values));
    }

    public JsonNode persona(LabCatalog catalog) {
        var scores = new LinkedHashMap<String, JsonNode>();
        catalog.codes().forEach(c -> scores.put(c, number()));
        return object(Map.of("biography", string(), "scores", object(scores), "facts", array(string())));
    }

    public JsonNode turns() {
        return object(Map.of("turns", array(object(Map.of("user", string(), "assistant", string())))));
    }

    public JsonNode analysis(LabCatalog catalog) {
        var element = object(Map.of("code", enumeration(catalog.codes()), "score", number(), "strength", integer(1, 3),
                "sourceIds", array(string()), "reason", string()));
        var memory = object(Map.of("kind", enumeration(List.of("FACT", "EXPERIENCE", "CONDITIONAL_PREFERENCE")),
                "content", string(), "sourceIds", array(string()), "occurredAt", nullable(string())));
        return object(Map.of("observations", array(element), "memories", array(memory)));
    }

    public JsonNode answer() {
        return object(Map.of("answer", string(), "stage",
                enumeration(List.of("CONTEXT", "MEMORY", "PROFILE", "INSUFFICIENT")), "inferred",
                json.valueToTree(Map.of("type", "boolean")), "evidenceIds", array(string())));
    }

    public JsonNode tool(String name, String description, Map<String, JsonNode> props) {
        return json.valueToTree(Map.of("type", "function", "name", name, "description", description, "strict", true,
                "parameters", object(props)));
    }
}
