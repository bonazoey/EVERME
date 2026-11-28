package com.example.everme.lab.service;

import java.time.Instant;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import com.example.everme.lab.prompt.LabPrompts;

@Service
@Profile("clone-lab")
public class LabCloneService {
  // ##### 필드 #####
  private static final Logger log =
      LoggerFactory.getLogger(LabCloneService.class);
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
  public Map<String, Object> start(Long owner, Long batch) {
    return start(owner, owner, batch);
  }

  public Map<String, Object> start(Long owner, Long actor, Long batch) {
    lab.dao.requireUser(owner);
    lab.dao.requireUser(actor);
    if (batch != null) lab.dao.requireBatch(owner, batch);
    Long session = lab.dao.mapper().nextId("sessions", "session_id");
    Map<String, Object> details = LabJson.object();
    details.put("version", 0);
    if (batch != null) details.put("batchId", batch.toString());
    lab.tx.executeWithoutResult(
        status -> {
          lab.dao.mapper().lockUser(owner);
          lab.dao.mapper().viewerSession(session, owner, actor, LabJson.write(lab.json, details));
        });
    return history(owner, actor);
  }

  // ##### 현재 이용자의 대화만 조회하는 메소드 #####
  public Map<String, Object> history(Long owner) {
    return history(owner, owner);
  }

  public Map<String, Object> history(Long owner, Long actor) {
    lab.dao.requireUser(owner);
    lab.dao.requireUser(actor);
    Map<String, Object> selected = lab.dao.mapper().currentViewer(owner, actor);
    Map<String, Object> result = LabJson.object();
    if (selected == null) return result;
    Long session = (Long) selected.get("session_id");
    Map<String, Object> details = lab.dao.parseMap(selected.get("analysis"));
    result.put("sessionId", session.toString());
    result.put("actorUserId", actor.toString());
    result.put("version", LabJson.longValue(details.get("version")));
    result.put("batchId", details.get("batchId"));
    List<Object> messages = LabJson.childList(result, "messages");
    for (Map<String, Object> turn : lab.dao.mapper().viewerTurns(owner, actor, session)) {
      Map<String, Object> metadata = lab.dao.parseMap(turn.get("metadata"));
      Map<String, Object> message = LabJson.object();
      message.put("question", LabJson.text(metadata.get("user_text")));
      message.put("response", metadata.get("response"));
      messages.add(message);
    }
    return result;
  }

  public Map<String, Object> answer(Long user, Long sessionId, String question) {
    return answer(user, user, sessionId, question);
  }

  // ##### 최근 문맥 검색 후 도구 호출로 클론 답변을 만드는 메소드 #####
  public Map<String, Object> answer(Long user, Long actor, Long sessionId, String question) {
    Instant receivedAt = Instant.now();
    LabValidation.text(question, 4000, "질문");
    Map<String, Object> row = lab.dao.requireUser(user);
    Map<String, Object> context = history(user, actor);
    if (!sessionId.toString().equals(LabJson.text(context.get("sessionId"))))
      throw LabException.conflict("대화가 변경되었습니다. 현재 대화를 다시 불러오세요.");
    long version = LabJson.longValue(context.get("version"));
    List<Object> input = LabJson.array();
    List<Object> sources = LabJson.array();
    HashSet<String> allowed = new HashSet<String>();
    LabAnswerEvidence evidence = new LabAnswerEvidence();
    evidence.checked("SESSION_RAG");
    for (Document document :
        retrieval.context(user, null, question)) {
      Map<String, Object> source = LabJson.object();
      source.put("id", document.getId());
      source.put("userText", String.valueOf(document.getMetadata().get("user_text")));
      sources.add(source);
      allowed.add(document.getId());
      Map<String, Object> detail = LabJson.object();
      detail.put("content", String.valueOf(document.getMetadata().get("user_text")));
      detail.put("sessionId", String.valueOf(document.getMetadata().get("session_id")));
      detail.put("messageId", document.getId());
      detail.put("scope", "OWNER_FACT");
      if (document.getScore() != null) detail.put("similarity", document.getScore());
      evidence.add("SESSION_RAG", document.getId(), detail);
    }
    Map<String, Object> request = LabJson.object();
    request.put("cloneName", row.get("name").toString());
    request.put("ownerSources", sources);
    request.putAll(lab.contexts.prepare(sessionId));
    for (String field : List.of("recent", "summaries")) {
      String type = "recent".equals(field) ? "CHAT_CONTEXT" : "SUMMARY";
      evidence.checked(type);
      for (Object source : LabJson.list(request.get(field))) {
        String id = LabJson.text(LabJson.path(source, "id"));
        Map<String, Object> detail = LabJson.copy(LabJson.map(source));
        detail.put("scope", "VIEWER_CONVERSATION");
        evidence.add(type, id, detail);
        allowed.add(id);
      }
    }
    request.put("question", question);
    input.add(Map.of("role", "user", "content", LabJson.write(lab.json, request)));
    List<Object> trace = LabJson.array();
    HashSet<String> memoryIds = new HashSet<String>();
    HashSet<String> profileIds = new HashSet<String>();
    boolean memoryUsed = false, profileUsed = false, viewerUsed = false;
    long inTokens = 0, outTokens = 0, start = System.nanoTime();
    Map<String, Object> finalAnswer = null;
    for (int step = 0; step < 6; step++) {
      LinkedHashMap<String, ToolCallback> callbacks =
          new LinkedHashMap<String, ToolCallback>();
      if (!viewerUsed)
        callbacks.put("searchViewerHistory", cloneTools.viewerHistory(user, actor, sessionId));
      if (!memoryUsed) {
        callbacks.put("searchLongTermMemory", cloneTools.memory(user));
      } else if (!profileUsed) {
        callbacks.put("getAnalysisProfile", cloneTools.profile(user));
      }
      List<Map<String, Object>> tools =
          callbacks.values().stream().map(cloneTools::descriptor).toList();
      Map<String, Object> response =
          ai.respond(LabPrompts.CLONE_ANSWER, input, schemas.answer(), tools);
      if (!"completed".equals(LabJson.text(response.get("status")))) {
        throw LabException.invalid("도구 응답이 완료되지 않았습니다.");
      }
      inTokens += LabJson.longValue(LabJson.path(response, "usage", "input_tokens"));
      outTokens += LabJson.longValue(LabJson.path(response, "usage", "output_tokens"));
      List<Object> outputs = LabJson.list(response.get("output"));
      boolean called = false;
      for (Object output : outputs) {
        if ("function_call".equals(LabJson.text(LabJson.path(output, "type")))) {
          called = true;
        }
      }
      if (!called) {
        Map<String, Object> candidate = OpenAiLabClient.output(lab.json, response);
        if ("INSUFFICIENT".equals(LabJson.text(candidate.get("stage"))) && !memoryUsed) {
          input.add(Map.of("role", "user", "content", LabPrompts.REQUIRE_MEMORY_TOOL));
          continue;
        }
        finalAnswer = candidate;
        break;
      }
      // Preserve all output items, including reasoning items, when continuing a Responses tool
      // turn.
      for (Object output : outputs) {
        input.add(output);
      }
      for (Object outputValue : outputs) {
        Map<String, Object> output = LabJson.map(outputValue);
        if ("function_call".equals(LabJson.text(output.get("type")))) {
          String tool = LabJson.text(output.get("name"));
          Object args;
          try {
            args = lab.json.readValue(LabJson.text(output.get("arguments")), Object.class);
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
          Object data;
          try {
            data = lab.json.readValue(callback.call(LabJson.write(lab.json, args)), Object.class);
          } catch (LabException failure) {
            throw failure;
          } catch (Exception failure) {
            throw LabException.invalid("도구 결과 JSON 오류입니다.");
          }
          if ("searchViewerHistory".equals(tool)) {
            evidence.checked("VIEWER_RAG");
            for (Object item : LabJson.list(data)) {
              String id = LabJson.text(LabJson.path(item, "id"));
              allowed.add(id);
              evidence.add("VIEWER_RAG", id, item);
            }
            viewerUsed = true;
          } else if ("searchLongTermMemory".equals(tool)) {
            evidence.checked("MEMORY");
            for (Object item : LabJson.list(data)) {
              String id = LabJson.text(LabJson.path(item, "id"));
              allowed.add(id);
              memoryIds.add(id);
              evidence.add("MEMORY", id, item);
            }
            memoryUsed = true;
          } else {
            evidence.checked("PROFILE");
            Map<String, Object> profileData = LabJson.map(data);
            for (String code : profileData.keySet()) {
              allowed.add(code);
              profileIds.add(code);
              evidence.add("PROFILE", code, profileData.get(code));
            }
            profileUsed = true;
          }
          Map<String, Object> traced = LabJson.object();
          traced.put("tool", tool);
          traced.put("arguments", args);
          traced.put("result", data);
          trace.add(traced);
          input.add(
              Map.of(
                  "type",
                  "function_call_output",
                  "call_id",
                  LabJson.text(output.get("call_id")),
                  "output",
                  LabJson.write(lab.json, data)));
        }
      }
    }
    if (finalAnswer == null) {
      throw LabException.invalid("도구 호출 한도를 초과했습니다.");
    }
    String stage = LabJson.text(finalAnswer.get("stage"));
    List<Object> evidenceIds = LabJson.list(finalAnswer.get("evidenceIds"));
    if (("MEMORY".equals(stage) && !memoryUsed)
        || ("PROFILE".equals(stage) && (!profileUsed || !LabJson.bool(finalAnswer.get("inferred"))))
        || ("INSUFFICIENT".equals(stage) && !memoryUsed)) {
      throw LabException.invalid("답변의 근거 단계가 올바르지 않습니다.");
    }
    if (!LabJson.isList(finalAnswer.get("evidenceIds"))
        || (!"INSUFFICIENT".equals(stage) && evidenceIds.isEmpty())) {
      throw LabException.invalid("답변 근거가 없습니다.");
    }
    for (Object id : evidenceIds) {
      if (!allowed.contains(LabJson.text(id))) {
        throw LabException.invalid("답변이 제공되지 않은 근거를 참조했습니다.");
      }
    }
    boolean citesMemory = false, citesProfile = false;
    for (Object id : evidenceIds) {
      citesMemory |= memoryIds.contains(LabJson.text(id));
      citesProfile |= profileIds.contains(LabJson.text(id));
    }
    if (("CONTEXT".equals(stage) && (citesMemory || citesProfile))
        || ("MEMORY".equals(stage) && (!citesMemory || citesProfile))
        || ("PROFILE".equals(stage) && !citesProfile)) {
      throw LabException.invalid("답변 단계와 실제 인용 근거가 일치하지 않습니다.");
    }
    if (LabJson.text(finalAnswer.get("answer")).isBlank()) {
      throw LabException.invalid("빈 답변입니다.");
    }
    Map<String, Object> result = LabJson.copy(finalAnswer);
    result.put("provenance", evidence.describe(finalAnswer));
    result.put("trace", trace);
    Map<String, Object> metrics = LabJson.object();
    metrics.put("inputTokens", inTokens);
    metrics.put("outputTokens", outTokens);
    metrics.put("elapsedMs", (System.nanoTime() - start) / 1_000_000);
    result.put("metrics", metrics);
    String answerText = LabJson.text(result.get("answer"));
    Instant answeredAt = Instant.now();
    // 정보이용자 이력은 현재 대화방 검색에만 쓰고 소유자 장기기억에는 넣지 않는다.
    float[] prepared = null;
    try {
      prepared =
          ai.embed(List.of(LabMemoryService.content(question, answerText)))
              .getFirst();
    } catch (LabException failure) {
      log.warn(
          "lab_viewer_embedding_deferred sessionId={} status={}", sessionId, failure.getMessage());
    }
    float[] vector = prepared;
    lab.tx.executeWithoutResult(
        transaction -> {
          lab.dao.mapper().lockUser(user);
          Map<String, Object> now = history(user, actor);
          if (!sessionId.toString().equals(LabJson.text(now.get("sessionId")))
              || LabJson.longValue(now.get("version")) != version)
            throw LabException.conflict("대화가 변경되었습니다. 다시 질문해 주세요.");
          Map<String, Object> selected = lab.dao.mapper().currentViewer(user, actor);
          Map<String, Object> next = lab.dao.parseMap(selected.get("analysis"));
          next.put("version", version + 1);
          lab.memory.turn(
              lab.dao.mapper().nextId("session_messages", "id"),
              user,
              actor,
              sessionId,
              "viewer",
              (int) version + 1,
              question,
              answerText,
              vector,
              receivedAt,
              answeredAt,
              false,
              result);
          lab.dao.mapper().analysis(sessionId, LabJson.write(lab.json, next));
          lab.memory.retain(user, actor, "viewer");
        });
    lab.contexts.recorded(sessionId);
    return result;
  }
}
