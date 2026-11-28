package com.example.everme.lab.service;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.ai.tool.ToolCallback;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import com.example.everme.lab.prompt.LabPrompts;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

@Service
@Profile("clone-lab")
public class LabCloneService {
  // ##### 필드 #####
  private final LabService lab;
  private final LabAi ai;
  private final LabCatalog catalog;
  private final LabSchemas schemas;
  private final LabCloneTools cloneTools;
  private final LabRetrievalService retrieval;

  // ##### 생성자 #####
  public LabCloneService(
      LabService lab,
      LabAi ai,
      LabCatalog catalog,
      LabSchemas schemas,
      LabCloneTools cloneTools,
      LabRetrievalService retrieval) {
    this.lab = lab;
    this.ai = ai;
    this.catalog = catalog;
    this.schemas = schemas;
    this.cloneTools = cloneTools;
    this.retrieval = retrieval;
  }

  // ##### 이용자별 클론 대화 세션을 시작하는 메소드 #####
  public JsonNode start(Long owner, Long batch) {
    return start(owner, owner, batch);
  }

  public JsonNode start(Long owner, Long actor, Long batch) {
    lab.dao.requireUser(owner);
    lab.dao.requireUser(actor);
    if (batch != null) lab.dao.requireBatch(owner, batch);
    Long session = lab.dao.mapper().nextId("sessions", "session_id");
    ObjectNode details = lab.object().put("version", 0);
    if (batch != null) details.put("batchId", batch.toString());
    lab.tx.executeWithoutResult(
        status -> {
          lab.dao.mapper().lockUser(owner);
          lab.dao.mapper().viewerSession(session, owner, actor, details.toString());
        });
    return history(owner, actor);
  }

  // ##### 현재 이용자의 대화만 조회하는 메소드 #####
  public JsonNode history(Long owner) {
    return history(owner, owner);
  }

  public JsonNode history(Long owner, Long actor) {
    lab.dao.requireUser(owner);
    lab.dao.requireUser(actor);
    Map<String, Object> selected = lab.dao.mapper().currentViewer(owner, actor);
    ObjectNode result = lab.object();
    if (selected == null) return result;
    Long session = (Long) selected.get("session_id");
    JsonNode details = lab.dao.parse(selected.get("analysis"));
    result
        .put("sessionId", session.toString())
        .put("actorUserId", actor.toString())
        .put("version", details.path("version").asLong());
    result.set("batchId", details.path("batchId"));
    ArrayNode messages = result.putArray("messages");
    for (Map<String, Object> turn : lab.dao.mapper().viewerTurns(owner, actor, session)) {
      JsonNode metadata = lab.dao.parse(turn.get("metadata"));
      ObjectNode message =
          messages.addObject().put("question", metadata.path("user_text").asText());
      message.set("response", metadata.path("response"));
    }
    return result;
  }

  public JsonNode answer(Long user, Long sessionId, String question) {
    return answer(user, user, sessionId, question);
  }

  // ##### 최근 문맥 검색 후 도구 호출로 클론 답변을 만드는 메소드 #####
  public JsonNode answer(Long user, Long actor, Long sessionId, String question) {
    java.time.Instant receivedAt = java.time.Instant.now();
    LabValidation.text(question, 4000, "질문");
    Map<String, Object> row = lab.dao.requireUser(user);
    ObjectNode context = (ObjectNode) history(user, actor);
    if (!sessionId.toString().equals(context.path("sessionId").asText()))
      throw LabException.conflict("대화가 변경되었습니다. 현재 대화를 다시 불러오세요.");
    long version = context.path("version").asLong();
    ArrayNode input = lab.json.createArrayNode();
    ArrayNode sources = lab.json.createArrayNode();
    HashSet<String> allowed = new HashSet<String>();
    Long sourceSession =
        context.path("batchId").isTextual() ? Long.valueOf(context.path("batchId").asText()) : null;
    for (org.springframework.ai.document.Document document :
        retrieval.context(user, null, question)) {
      sources
          .addObject()
          .put("id", document.getId())
          .put("userText", String.valueOf(document.getMetadata().get("user_text")));
      allowed.add(document.getId());
    }
    ObjectNode request = lab.object();
    request.put("cloneName", row.get("name").toString());
    request.set("ownerSources", sources);
    request.setAll(lab.contexts.prepare(sessionId));
    request.put("question", question);
    input.add(lab.json.valueToTree(Map.of("role", "user", "content", request.toString())));
    ArrayNode trace = lab.json.createArrayNode();
    HashSet<String> memoryIds = new HashSet<String>();
    HashSet<String> profileIds = new HashSet<String>();
    boolean memoryUsed = false, profileUsed = false;
    long inTokens = 0, outTokens = 0, start = System.nanoTime();
    JsonNode finalAnswer = null;
    for (int step = 0; step < 6; step++) {
      LinkedHashMap<String, ToolCallback> callbacks =
          new LinkedHashMap<String, org.springframework.ai.tool.ToolCallback>();
      if (!memoryUsed) {
        callbacks.put("searchLongTermMemory", cloneTools.memory(user));
      } else if (!profileUsed) {
        callbacks.put("getAnalysisProfile", cloneTools.profile(user));
      }
      List<JsonNode> tools = callbacks.values().stream().map(cloneTools::descriptor).toList();
      JsonNode response = ai.respond(LabPrompts.CLONE_ANSWER, input, schemas.answer(), tools);
      if (!"completed".equals(response.path("status").asText())) {
        throw LabException.invalid("도구 응답이 완료되지 않았습니다.");
      }
      inTokens += response.path("usage").path("input_tokens").asLong();
      outTokens += response.path("usage").path("output_tokens").asLong();
      boolean called = false;
      for (JsonNode output : response.path("output")) {
        if ("function_call".equals(output.path("type").asText())) {
          called = true;
        }
      }
      if (!called) {
        JsonNode candidate = OpenAiLabClient.output(lab.json, response);
        if ("INSUFFICIENT".equals(candidate.path("stage").asText()) && !memoryUsed) {
          input.add(
              lab.json.valueToTree(
                  Map.of("role", "user", "content", LabPrompts.REQUIRE_MEMORY_TOOL)));
          continue;
        }
        finalAnswer = candidate;
        break;
      }
      // Preserve all output items, including reasoning items, when continuing a Responses tool
      // turn.
      for (JsonNode output : response.path("output")) {
        input.add(output);
      }
      for (JsonNode output : response.path("output")) {
        if ("function_call".equals(output.path("type").asText())) {
          String tool = output.path("name").asText();
          JsonNode args;
          try {
            args = lab.json.readTree(output.path("arguments").asText());
          } catch (Exception e) {
            throw LabException.invalid("도구 인자 JSON 오류입니다.");
          }
          ToolCallback callback = callbacks.get(tool);
          if (callback == null) {
            throw LabException.invalid("현재 검색 단계에서 허용되지 않은 도구입니다.");
          }
          if (("searchLongTermMemory".equals(tool) && memoryUsed)
              || ("getAnalysisProfile".equals(tool) && (!memoryUsed || profileUsed))) {
            throw LabException.invalid("이미 실행했거나 순서가 잘못된 도구입니다.");
          }
          JsonNode data;
          try {
            data = lab.json.readTree(callback.call(args.toString()));
          } catch (LabException failure) {
            throw failure;
          } catch (Exception failure) {
            throw LabException.invalid("도구 결과 JSON 오류입니다.");
          }
          if ("searchLongTermMemory".equals(tool)) {
            for (JsonNode item : data) {
              String id = item.path("id").asText();
              allowed.add(id);
              memoryIds.add(id);
            }
            memoryUsed = true;
          } else {
            data.fieldNames()
                .forEachRemaining(
                    code -> {
                      allowed.add(code);
                      profileIds.add(code);
                    });
            profileUsed = true;
          }
          trace.add(lab.json.valueToTree(Map.of("tool", tool, "arguments", args, "result", data)));
          input.add(
              lab.json.valueToTree(
                  Map.of(
                      "type",
                      "function_call_output",
                      "call_id",
                      output.path("call_id").asText(),
                      "output",
                      data.toString())));
        }
      }
    }
    if (finalAnswer == null) {
      throw LabException.invalid("도구 호출 한도를 초과했습니다.");
    }
    String stage = finalAnswer.path("stage").asText();
    if (("MEMORY".equals(stage) && !memoryUsed)
        || ("PROFILE".equals(stage) && (!profileUsed || !finalAnswer.path("inferred").asBoolean()))
        || ("INSUFFICIENT".equals(stage) && !memoryUsed)) {
      throw LabException.invalid("답변의 근거 단계가 올바르지 않습니다.");
    }
    if (!finalAnswer.path("evidenceIds").isArray()
        || (!"INSUFFICIENT".equals(stage) && finalAnswer.path("evidenceIds").isEmpty())) {
      throw LabException.invalid("답변 근거가 없습니다.");
    }
    for (JsonNode id : finalAnswer.path("evidenceIds")) {
      if (!allowed.contains(id.asText())) {
        throw LabException.invalid("답변이 제공되지 않은 근거를 참조했습니다.");
      }
    }
    boolean citesMemory = false, citesProfile = false;
    for (JsonNode id : finalAnswer.path("evidenceIds")) {
      citesMemory |= memoryIds.contains(id.asText());
      citesProfile |= profileIds.contains(id.asText());
    }
    if (("CONTEXT".equals(stage) && (citesMemory || citesProfile))
        || ("MEMORY".equals(stage) && (!citesMemory || citesProfile))
        || ("PROFILE".equals(stage) && !citesProfile)) {
      throw LabException.invalid("답변 단계와 실제 인용 근거가 일치하지 않습니다.");
    }
    if (finalAnswer.path("answer").asText().isBlank()) {
      throw LabException.invalid("빈 답변입니다.");
    }
    ObjectNode result = (ObjectNode) finalAnswer.deepCopy();
    result.set("trace", trace);
    result.set(
        "metrics",
        lab.json.valueToTree(
            Map.of(
                "inputTokens",
                inTokens,
                "outputTokens",
                outTokens,
                "elapsedMs",
                (System.nanoTime() - start) / 1_000_000)));
    java.time.Instant answeredAt = java.time.Instant.now();
    float[] vector = null; // 정보이용자 발언은 임베딩·소유자 기억으로 축적하지 않는다.
    lab.tx.executeWithoutResult(
        transaction -> {
          lab.dao.mapper().lockUser(user);
          JsonNode now = history(user, actor);
          if (!sessionId.toString().equals(now.path("sessionId").asText())
              || now.path("version").asLong() != version)
            throw LabException.conflict("대화가 변경되었습니다. 다시 질문해 주세요.");
          Map<String, Object> selected = lab.dao.mapper().currentViewer(user, actor);
          ObjectNode next = (ObjectNode) lab.dao.parse(selected.get("analysis"));
          next.put("version", version + 1);
          lab.memory.turn(
              lab.dao.mapper().nextId("session_messages", "id"),
              user,
              actor,
              sessionId,
              "viewer",
              (int) version + 1,
              question,
              result.path("answer").asText(),
              vector,
              receivedAt,
              answeredAt,
              false,
              result);
          lab.dao.mapper().analysis(sessionId, next.toString());
          lab.memory.retain(user, actor, "viewer");
        });
    lab.contexts.recorded(sessionId);
    return result;
  }
}
