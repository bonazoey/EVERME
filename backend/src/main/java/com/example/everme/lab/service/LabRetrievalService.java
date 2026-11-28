package com.example.everme.lab.service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import com.example.everme.lab.dao.LabDao;

/** 검색 범위는 LLM 인자가 아니라 서버가 선택한 소유자·참여자로 고정한다. */
@Service
@Profile("clone-lab")
public class LabRetrievalService {
  // ##### 필드 #####
  private final PgVectorStore context;
  private final PgVectorStore memory;
  private final LabDao dao;

  // ##### 생성자 #####
  public LabRetrievalService(
      @Qualifier("labContextStore") PgVectorStore context,
      @Qualifier("labMemoryStore") PgVectorStore memory,
      LabDao dao) {
    this.context = context;
    this.memory = memory;
    this.dao = dao;
  }

  // ##### 소유자의 최근 대화를 벡터 검색하는 메소드 #####
  public List<Document> context(Long owner, Long session, String question) {
    // 보관 원문은 이력에 남기되 최근 100쌍만 벡터 검색 대상으로 제한한다.
    String candidates =
        dao.mapper().recent(owner).stream()
            .map(row -> "'" + Long.valueOf(row.get("id").toString()) + "'")
            .collect(Collectors.joining(","));
    if (candidates.isEmpty()) return List.of();
    String filter = scope(owner, owner, "owner");
    filter += " && message_id in [" + candidates + "]";
    if (session != null) filter += " && session_id == '" + session + "'";
    return context.similaritySearch(
        SearchRequest.builder()
            .query(question)
            .topK(8)
            .similarityThreshold(0.35)
            .filterExpression(filter)
            .build());
  }

  // ##### 현재 정보이용자와 대화방 범위를 검증하고 이전 대화를 검색하는 메소드 #####
  public List<Document> viewerHistory(Long owner, Long viewer, Long session, String question) {
    Map<String, Object> selected = dao.mapper().lockFreeSession(session);
    if (selected == null
        || !owner.equals(((Number) selected.get("owner_id")).longValue())
        || !viewer.equals(((Number) selected.get("viewer_id")).longValue())
        || !"viewer".equals(selected.get("conversation_type").toString())) {
      throw LabException.invalid("정보이용자 대화방 접근 범위가 올바르지 않습니다.");
    }
    return context.similaritySearch(
        SearchRequest.builder()
            .query(question)
            .topK(8)
            .similarityThreshold(0.35)
            .filterExpression(
                scope(owner, viewer, "viewer") + " && session_id == '" + session + "'")
            .build());
  }

  // ##### 소유자의 장기 기억을 벡터 검색하는 메소드 #####
  public List<Document> memories(Long owner, String question) {
    return memory.similaritySearch(
        SearchRequest.builder()
            .query(question)
            .topK(8)
            .similarityThreshold(0.35)
            .filterExpression(scope(owner, owner, "owner"))
            .build());
  }

  private String scope(Long owner, Long actor, String type) {
    return "owner_id == '"
        + owner
        + "' && viewer_id == '"
        + actor
        + "' && conversation_type == '"
        + type
        + "'";
  }
}
