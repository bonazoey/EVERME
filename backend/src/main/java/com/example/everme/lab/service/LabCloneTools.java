package com.example.everme.lab.service;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.method.MethodToolCallback;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/** Tool callbacks capture the server-selected owner; tool input cannot change that owner. */
@Component
@Profile("clone-lab")
public class LabCloneTools {
  // ##### 필드 #####
  private final LabService lab;
  private final LabCatalog catalog;
  private final LabSchemas schemas;
  private final LabRetrievalService retrieval;

  // ##### 생성자 #####
  public LabCloneTools(
      LabService lab, LabCatalog catalog, LabSchemas schemas, LabRetrievalService retrieval) {
    this.lab = lab;
    this.catalog = catalog;
    this.schemas = schemas;
    this.retrieval = retrieval;
  }

  public ToolCallback memory(UUID owner) {
    return callback(
        owner,
        schemas.tool(
            "searchLongTermMemory",
            "현재 컨텍스트의 근거가 부족할 때 소유자 장기 기억 검색",
            Map.of("query", schemas.string())),
        String.class);
  }

  // ##### 소유자의 장기 기억을 검색하는 도구 메소드 #####
  @Tool(description = "현재 컨텍스트의 근거가 부족할 때 소유자 장기 기억을 검색합니다.")
  public JsonNode searchLongTermMemory(
      @ToolParam(description = "질문과 관련된 기억 검색어") String query, ToolContext toolContext) {
    UUID owner = owner(toolContext);
    LabValidation.text(query, 1000, "검색어");
    ArrayNode items = lab.json.createArrayNode();
    for (org.springframework.ai.document.Document document : retrieval.memories(owner, query)) {
      JsonNode meta = lab.json.valueToTree(document.getMetadata());
      ObjectNode item =
          items.addObject().put("id", document.getId()).put("kind", meta.path("kind").asText());
      item.put(
          "content",
          meta.path("record_type").asText().equals("context_archive")
              ? meta.path("user_text").asText()
              : document.getText());
      item.set("sourceIds", meta.path("source_ids"));
      item.set("sourceExcerpts", meta.path("source_excerpts"));
      item.set("occurredAt", meta.path("occurred_at"));
    }
    return items;
  }

  // ##### 분석 프로필을 조회하는 메소드 #####
  public ToolCallback profile(UUID owner) {
    return callback(
        owner,
        schemas.tool(
            "getAnalysisProfile",
            "기억으로 판단 불가능한 성향·선택을 추정할 때 관련 요소 조회",
            Map.of("codes", schemas.array(schemas.enumeration(catalog.codes())))),
        List.class);
  }

  // ##### 소유자의 분석 프로필을 조회하는 도구 메소드 #####
  @Tool(description = "기억으로 판단할 수 없는 성향과 선택을 추정하기 위해 관련 분석 요소를 조회합니다.")
  public JsonNode getAnalysisProfile(
      @ToolParam(description = "조회할 분석 요소 코드 목록, 1~20개") List<String> codes,
      ToolContext toolContext) {
    UUID owner = owner(toolContext);
    if (codes == null || codes.isEmpty() || codes.size() > 20) {
      throw LabException.invalid("관련 프로필 요소는 1~20개만 조회할 수 있습니다.");
    }
    JsonNode profile = lab.profile(owner).path("elements");
    ObjectNode points = lab.object();
    for (String code : codes) {
      JsonNode definition = catalog.element(code);
      JsonNode point = profile.path(code);
      if (point.path("score").isNumber()) {
        ObjectNode detail = lab.object();
        detail.set("definition", definition);
        detail.set("value", point);
        points.set(code, detail);
      }
    }
    return points;
  }

  public JsonNode descriptor(ToolCallback callback) {
    ToolDefinition definition = callback.getToolDefinition();
    ObjectNode descriptor = lab.object();
    descriptor.put("type", "function");
    descriptor.put("name", definition.name());
    descriptor.put("description", definition.description());
    descriptor.put("strict", true);
    try {
      descriptor.set("parameters", lab.json.readTree(definition.inputSchema()));
      return descriptor;
    } catch (Exception failure) {
      throw new IllegalStateException("Invalid tool schema", failure);
    }
  }

  // ##### 서버가 선택한 소유자와 도구 콜백을 연결하는 메소드 #####
  private ToolCallback callback(UUID owner, JsonNode schema, Class<?> argumentType) {
    ToolDefinition definition =
        ToolDefinition.builder()
            .name(schema.path("name").asText())
            .description(schema.path("description").asText())
            .inputSchema(schema.path("parameters").toString())
            .build();
    MethodToolCallback delegate;
    try {
      delegate =
          MethodToolCallback.builder()
              .toolDefinition(definition)
              .toolObject(this)
              .toolMethod(
                  LabCloneTools.class.getMethod(definition.name(), argumentType, ToolContext.class))
              .toolCallResultConverter((result, type) -> result.toString())
              .build();
    } catch (NoSuchMethodException failure) {
      throw new IllegalStateException("등록된 도구 메소드를 찾을 수 없습니다.", failure);
    }
    ToolContext toolContext = new ToolContext(Map.of("ownerId", owner));
    return new ToolCallback() {
      public ToolDefinition getToolDefinition() {
        return definition;
      }

      public String call(String input) {
        JsonNode args;
        try {
          args = lab.json.readTree(input);
        } catch (Exception failure) {
          throw LabException.invalid("도구 인자 JSON 오류입니다.");
        }
        if (args == null || !args.isObject()) {
          throw LabException.invalid("도구 인자는 객체여야 합니다.");
        }
        Iterator<String> fields = args.fieldNames();
        while (fields.hasNext()) {
          if (!schema.path("parameters").path("properties").has(fields.next())) {
            throw LabException.invalid("허용되지 않은 도구 인자입니다.");
          }
        }
        try {
          return delegate.call(input, toolContext);
        } catch (RuntimeException failure) {
          for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof LabException labException) {
              throw labException;
            }
          }
          LabException wrapped = LabException.invalid("도구 실행에 실패했습니다.");
          wrapped.initCause(failure);
          throw wrapped;
        }
      }
    };
  }

  // ##### 서버 도구 컨텍스트의 소유자를 확인하는 메소드 #####
  private UUID owner(ToolContext toolContext) {
    if (toolContext == null || !(toolContext.getContext().get("ownerId") instanceof UUID ownerId)) {
      throw LabException.invalid("도구 실행에 소유자 정보가 필요합니다.");
    }
    return ownerId;
  }
}
