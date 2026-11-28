package com.example.everme.lab.service;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import com.example.everme.lab.dao.LabDao;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/** 원문·장기 기억의 metadata, 출처 보존과 최근 100쌍 보관 규칙을 관리한다. */
@Service
@Profile("clone-lab")
public class LabMemoryService {
  // ##### 필드 #####
  private final LabDao dao;
  private final ObjectMapper json;

  // ##### 생성자 #####
  public LabMemoryService(LabDao dao, ObjectMapper json) {
    this.dao = dao;
    this.json = json;
  }

  public static String content(String user, String assistant) {
    return "USER: " + user + "\nASSISTANT: " + assistant;
  }

  public ObjectNode metadata(UUID owner, UUID actor, UUID session, String type) {
    return json.createObjectNode()
        .put("clone_owner_id", owner.toString())
        .put("actor_user_id", actor.toString())
        .put("session_id", session.toString())
        .put("conversation_type", type);
  }

  // ##### 임베딩이 준비된 대화와 질문·응답 시각을 저장하는 메소드 #####
  public void turn(
      UUID id,
      UUID owner,
      UUID actor,
      UUID session,
      String type,
      int ordinal,
      String user,
      String assistant,
      float[] vector,
      Instant received,
      Instant answered,
      boolean synthetic,
      JsonNode response) {
    ObjectNode meta = metadata(owner, actor, session, type);
    meta.put("ordinal", ordinal)
        .put("user_text", user)
        .put("assistant_text", assistant)
        .put("user_received_at", received.toString())
        .put("assistant_received_at", answered.toString())
        .put("synthetic", synthetic);
    if (synthetic) {
      meta.put("time_semantics", "generation_request_response_shared_by_pairs");
    }
    if (response != null) {
      meta.set("response", response);
    }
    dao.mapper()
        .context(
            id,
            content(user, assistant),
            LabDao.vector(vector),
            meta.toString(),
            "owner".equals(type) ? "pending" : "saved");
  }

  // ##### 고정 기억 ID와 사용자 원문 발췌를 함께 저장하는 메소드 #####
  public void memory(
      UUID owner,
      UUID session,
      String operation,
      int index,
      JsonNode memory,
      float[] vector,
      List<Map<String, Object>> sources) {
    ObjectNode meta = metadata(owner, owner, session, "owner");
    String extraction = operation + ":" + index;
    meta.put("kind", memory.path("kind").asText()).put("extraction_key", extraction);
    meta.set("source_ids", memory.path("sourceIds"));
    // Spring AI Document는 최상위 metadata의 null 값을 허용하지 않는다.
    if (memory.path("occurredAt").isTextual()) {
      meta.set("occurred_at", memory.path("occurredAt"));
    }
    ArrayNode quotes = meta.putArray("source_excerpts");
    for (JsonNode id : memory.path("sourceIds")) {
      Map<String, Object> source =
          sources.stream()
              .filter(t -> t.get("id").toString().equals(id.asText()))
              .findFirst()
              .orElseThrow(() -> LabException.conflict("기억의 원문 출처가 변경되었습니다."));
      quotes
          .addObject()
          .put("source_id", id.asText())
          .put("user_text", source.get("user_text").toString());
    }
    UUID id = UUID.nameUUIDFromBytes((session + ":" + extraction).getBytes(StandardCharsets.UTF_8));
    dao.mapper()
        .document(id, memory.path("content").asText(), LabDao.vector(vector), meta.toString());
  }

  // ##### 원문 삭제 후에도 중복 반영을 검사할 근거를 보존하는 메소드 #####
  public void complete(ObjectNode details, List<Map<String, Object>> turns, Set<String> ids) {
    ArrayNode evidence = details.withArray("processedEvidence");
    ArrayNode processed = details.withArray("processedSourceIds");
    Set<String> known = new HashSet<>();
    processed.forEach(id -> known.add(id.asText()));
    for (Map<String, Object> turn : turns) {
      String id = turn.get("id").toString();
      if (ids.contains(id)) {
        if (known.add(id)) {
          processed.add(id);
          evidence.addObject().put("id", id).put("user_text", turn.get("user_text").toString());
        }
        dao.mapper().contextStatus(UUID.fromString(id), "saved");
      }
    }
  }

  /** 호출자는 사용자 잠금 안의 짧은 트랜잭션을 유지한다. 외부 모델 호출은 하지 않는다. */
  public void retain(UUID owner, UUID actor, String type) {
    for (Map<String, Object> row : dao.mapper().overflow(owner, actor, type)) {
      UUID id = (UUID) row.get("id");
      JsonNode state = dao.parse(row.get("metadata"));
      // pending/analyzed 원문은 처리 실패·지연 시에도 삭제하지 않는다.
      // 저장 상태는 DB에서 다시 확인하므로 분석되지 않은 대화를 이관하지 않는다.
      if (!dao.mapper().archiveReady(id)) {
        continue;
      }
      ObjectNode meta = (ObjectNode) state.deepCopy();
      meta.put("kind", "EXPERIENCE")
          .put("record_type", "context_archive")
          .put("extraction_key", "context:" + id);
      meta.putArray("source_ids").add(id.toString());
      meta.putArray("source_excerpts")
          .addObject()
          .put("source_id", id.toString())
          .put("user_text", state.path("user_text").asText());
      UUID archiveId = UUID.nameUUIDFromBytes(("context:" + id).getBytes(StandardCharsets.UTF_8));
      dao.mapper()
          .document(
              archiveId,
              row.get("content").toString(),
              row.get("embedding").toString(),
              meta.toString());
      dao.mapper().contextStatus(id, "archived");
      if (dao.mapper().deleteArchived(id) != 1) {
        throw LabException.conflict("원문 이관 상태가 변경되었습니다.");
      }
    }
  }
}
