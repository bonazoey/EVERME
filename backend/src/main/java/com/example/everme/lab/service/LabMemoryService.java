package com.example.everme.lab.service;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import com.example.everme.lab.dao.LabDao;
import com.fasterxml.jackson.databind.ObjectMapper;

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

  public Map<String, Object> metadata(Long owner, Long actor, Long session, String type) {
    Map<String, Object> meta = LabJson.object();
    meta.put("owner_id", owner.toString());
    meta.put("viewer_id", actor.toString());
    meta.put("session_id", session.toString());
    meta.put("conversation_type", type);
    return meta;
  }

  // ##### 임베딩이 준비된 대화와 질문·응답 시각을 저장하는 메소드 #####
  public void turn(
      Long id,
      Long owner,
      Long actor,
      Long session,
      String type,
      int ordinal,
      String user,
      String assistant,
      float[] vector,
      Instant received,
      Instant answered,
      boolean synthetic,
      Object response) {
    Map<String, Object> selected = dao.mapper().lockSession(session);
    if (selected == null
        || !owner.equals(((Number) selected.get("owner_id")).longValue())
        || !actor.equals(((Number) selected.get("viewer_id")).longValue())
        || !type.equals(selected.get("conversation_type").toString())) {
      throw LabException.conflict("대화방의 소유자·참여자·종류가 일치하지 않습니다.");
    }
    Map<String, Object> meta = metadata(owner, actor, session, type);
    meta.put("message_id", id.toString());
    meta.put("ordinal", ordinal);
    meta.put("user_text", user);
    meta.put("assistant_text", assistant);
    meta.put("user_received_at", received.toString());
    meta.put("assistant_received_at", answered.toString());
    meta.put("synthetic", synthetic);
    if (synthetic) {
      meta.put("time_semantics", "generation_request_response_shared_by_pairs");
    }
    if (response != null) {
      meta.put("response", response);
    }
    dao.mapper()
        .context(
            id,
            content(user, assistant),
            vector == null ? null : LabDao.vector(vector),
            LabJson.write(json, meta),
            "owner".equals(type) ? "pending" : "saved");
  }

  // ##### 영구 원문 ID로 중복 반영을 검사할 근거를 보존하는 메소드 #####
  public void complete(
      Map<String, Object> details, List<Map<String, Object>> turns, Set<String> ids) {
    List<Object> evidence = LabJson.childList(details, "processedEvidence");
    List<Object> processed = LabJson.childList(details, "processedSourceIds");
    Set<String> known = new HashSet<>();
    processed.forEach(id -> known.add(LabJson.text(id)));
    for (Map<String, Object> turn : turns) {
      String id = turn.get("id").toString();
      if (ids.contains(id)) {
        if (known.add(id)) {
          processed.add(id);
          Map<String, Object> item = LabJson.object();
          item.put("id", id);
          item.put("user_text", turn.get("user_text").toString());
          evidence.add(item);
        }
        dao.mapper().contextStatus(Long.valueOf(id), "saved");
      }
    }
  }

  /** 호출자는 사용자 잠금 안의 짧은 트랜잭션을 유지한다. 외부 모델 호출은 하지 않는다. */
  public void retain(Long owner, Long actor, String type) {
    if (!"owner".equals(type)) return;
    for (Map<String, Object> row : dao.mapper().overflow(owner, actor, type)) {
      Long id = ((Number) row.get("id")).longValue();
      if (!dao.mapper().archiveReady(id)) continue;
      dao.mapper().archive(id);
      dao.mapper().contextStatus(id, "archived");
    }
  }
}
