package com.example.everme.lab.service;

import java.util.List;
import java.util.Map;

import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.document.Document;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.method.MethodToolCallback;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import com.example.everme.lab.dao.LabDao;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Tool callbacks capture the server-selected owner; tool input cannot change that owner. */
@Component
@Profile("clone-lab")
public class LabCloneTools {
  // ##### 필드 #####
  // 도구 스키마·인자·결과는 단순 JSON이라 Spring 설정 없이 고정 ObjectMapper로 변환한다.
  private static final ObjectMapper TOOL_JSON = new ObjectMapper();
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

  public ToolCallback memory(Long owner) {
    return callback(
        owner,
        schemas.tool(
            "searchLongTermMemory",
            "현재 컨텍스트의 근거가 부족할 때 소유자 장기 기억 검색",
            Map.of("query", schemas.string())),
        String.class);
  }

  // ##### 현재 정보이용자의 대화방 이력 검색 도구를 구성하는 메소드 #####
  public ToolCallback viewerHistory(Long owner, Long viewer, Long session) {
    return callback(
        owner,
        schemas.tool(
            "searchViewerHistory",
            "현재 대화 문맥과 요약에 없는 정보이용자 본인의 이전 대화를 현재 대화방에서 검색",
            Map.of("query", schemas.string())),
        String.class,
        Map.of("ownerId", owner, "viewerId", viewer, "sessionId", session));
  }

  @Tool(description = "정보이용자 본인의 현재 대화방 이력을 검색합니다. 소유자 사실 근거가 아닙니다.")
  public List<Object> searchViewerHistory(
      @ToolParam(description = "이전 대화 검색어") String query, ToolContext toolContext) {
    LabValidation.text(query, 1000, "검색어");
    Long owner = owner(toolContext);
    if (!(toolContext.getContext().get("viewerId") instanceof Long viewer)
        || !(toolContext.getContext().get("sessionId") instanceof Long session)) {
      throw LabException.invalid("정보이용자 검색 범위가 필요합니다.");
    }
    List<Object> items = LabJson.array();
    // 과거 viewer 이력의 NULL 임베딩은 검색 시 현재 범위의 원문으로만 보완한다.
    while (true) {
      List<Map<String, Object>> missing = lab.dao.mapper().viewerMissingEmbeddings(owner, viewer, session);
      if (missing.isEmpty()) break;
      List<float[]> vectors = lab.embedViewerHistory(missing);
      for (int i = 0; i < missing.size(); i++) {
        lab.dao
            .mapper()
            .embedding(
                ((Number) missing.get(i).get("id")).longValue(),
                LabDao.vector(vectors.get(i)));
      }
    }
    for (Document document : retrieval.viewerHistory(owner, viewer, session, query)) {
      Map<String, Object> item = LabJson.object();
      item.put("id", "viewer:" + document.getId());
      item.put("messageId", document.getId());
      item.put("sessionId", session.toString());
      item.put("content", document.getText());
      item.put("scope", "VIEWER_CONVERSATION");
      if (document.getScore() != null) item.put("similarity", document.getScore());
      items.add(item);
    }
    return items;
  }

  // ##### 소유자의 장기 기억을 검색하는 도구 메소드 #####
  @Tool(description = "현재 컨텍스트의 근거가 부족할 때 소유자 장기 기억을 검색합니다.")
  public List<Object> searchLongTermMemory(
      @ToolParam(description = "질문과 관련된 기억 검색어") String query, ToolContext toolContext) {
    Long owner = owner(toolContext);
    LabValidation.text(query, 1000, "검색어");
    List<Object> items = LabJson.array();
    for (Document document : retrieval.memories(owner, query)) {
      Map<String, Object> meta = LabJson.map(LabJson.convert(lab.json, document.getMetadata()));
      Map<String, Object> item = LabJson.object();
      item.put("id", "memory:" + document.getId());
      item.put("kind", LabJson.text(meta.get("kind")));
      item.put(
          "content",
          LabJson.text(meta.get("record_type")).equals("conversation_archive")
              ? LabJson.text(meta.get("user_text"))
              : document.getText());
      item.put("sourceIds", meta.get("source_ids"));
      item.put("sourceExcerpts", meta.get("source_excerpts"));
      item.put("occurredAt", meta.get("occurred_at"));
      item.put("sessionId", LabJson.text(meta.get("session_id")));
      item.put("scope", "OWNER_FACT");
      if (document.getScore() != null) item.put("similarity", document.getScore());
      items.add(item);
    }
    return items;
  }

  // ##### 분석 프로필을 조회하는 메소드 #####
  public ToolCallback profile(Long owner) {
    return callback(
        owner,
        schemas.tool(
            "getAnalysisProfile",
            "기억으로 판단 불가능한 성향·선택을 추정할 때 관련 요소 조회",
            Map.of("codes", schemas.array(schemas.enumeration(catalog.codes()), 1, 20))),
        List.class);
  }

  // ##### 소유자의 분석 프로필을 조회하는 도구 메소드 #####
  @Tool(description = "기억으로 판단할 수 없는 성향과 선택을 추정하기 위해 관련 분석 요소를 조회합니다.")
  public Map<String, Object> getAnalysisProfile(
      @ToolParam(description = "조회할 분석 요소 코드 목록, 1~20개") List<String> codes,
      ToolContext toolContext) {
    Long owner = owner(toolContext);
    if (codes == null || codes.isEmpty() || codes.size() > 20) {
      throw LabException.invalid("관련 프로필 요소는 1~20개만 조회할 수 있습니다.");
    }
    Map<String, Object> profile = LabJson.map(lab.profile(owner).get("elements"));
    Map<String, Object> points = LabJson.object();
    for (String code : codes) {
      Map<String, Object> definition = catalog.element(code);
      Object point = profile.get(code);
      if (LabJson.isNumber(LabJson.path(point, "score"))) {
        Map<String, Object> detail = LabJson.object();
        detail.put("definition", definition);
        detail.put("value", point);
        points.put(code, detail);
      }
    }
    return points;
  }

  public Map<String, Object> descriptor(ToolCallback callback) {
    ToolDefinition definition = callback.getToolDefinition();
    Map<String, Object> descriptor = LabJson.object();
    descriptor.put("type", "function");
    descriptor.put("name", definition.name());
    descriptor.put("description", definition.description());
    descriptor.put("strict", true);
    try {
      descriptor.put("parameters", TOOL_JSON.readValue(definition.inputSchema(), Map.class));
      return descriptor;
    } catch (Exception failure) {
      throw new IllegalStateException("Invalid tool schema", failure);
    }
  }

  // ##### 서버가 선택한 소유자와 도구 콜백을 연결하는 메소드 #####
  private ToolCallback callback(Long owner, Map<String, Object> schema, Class<?> argumentType) {
    return callback(owner, schema, argumentType, Map.of("ownerId", owner));
  }

  private ToolCallback callback(
      Long owner, Map<String, Object> schema, Class<?> argumentType, Map<String, Object> scope) {
    ToolDefinition definition =
        ToolDefinition.builder()
            .name(LabJson.text(schema.get("name")))
            .description(LabJson.text(schema.get("description")))
            .inputSchema(LabJson.write(TOOL_JSON, schema.get("parameters")))
            .build();
    MethodToolCallback delegate;
    try {
      delegate =
          MethodToolCallback.builder()
              .toolDefinition(definition)
              .toolObject(this)
              .toolMethod(
                  LabCloneTools.class.getMethod(definition.name(), argumentType, ToolContext.class))
              // 도구 결과(Map·List)는 JSON 문자열로 모델에 돌려준다. toString()은 JSON이 아니다.
              .toolCallResultConverter((result, type) -> LabJson.write(TOOL_JSON, result))
              .build();
    } catch (NoSuchMethodException failure) {
      throw new IllegalStateException("등록된 도구 메소드를 찾을 수 없습니다.", failure);
    }
    ToolContext toolContext = new ToolContext(scope);
    Map<String, Object> properties = LabJson.map(LabJson.path(schema, "parameters", "properties"));
    return new ToolCallback() {
      public ToolDefinition getToolDefinition() {
        return definition;
      }

      public String call(String input) {
        Object args;
        try {
          args = TOOL_JSON.readValue(input, Object.class);
        } catch (Exception failure) {
          throw LabException.invalid("도구 인자 JSON 오류입니다.");
        }
        if (!LabJson.isMap(args)) {
          throw LabException.invalid("도구 인자는 객체여야 합니다.");
        }
        for (String field : LabJson.map(args).keySet()) {
          if (!properties.containsKey(field)) {
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
  private Long owner(ToolContext toolContext) {
    if (toolContext == null || !(toolContext.getContext().get("ownerId") instanceof Long ownerId)) {
      throw LabException.invalid("도구 실행에 소유자 정보가 필요합니다.");
    }
    return ownerId;
  }
}
