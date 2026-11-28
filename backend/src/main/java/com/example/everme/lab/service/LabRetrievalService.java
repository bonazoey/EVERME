package com.example.everme.lab.service;

import java.util.List;
import java.util.UUID;

import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/** 검색 범위는 LLM 인자가 아니라 서버가 선택한 소유자·참여자로 고정한다. */
@Service
@Profile("clone-lab")
public class LabRetrievalService {
  // ##### 필드 #####
  private final PgVectorStore context;
  private final PgVectorStore memory;

  // ##### 생성자 #####
  public LabRetrievalService(
      @Qualifier("labContextStore") PgVectorStore context,
      @Qualifier("labMemoryStore") PgVectorStore memory) {
    this.context = context;
    this.memory = memory;
  }

  // ##### 소유자의 최근 대화를 벡터 검색하는 메소드 #####
  public List<Document> context(UUID owner, UUID session, String question) {
    String filter = scope(owner, owner, "owner");
    if (session != null) filter += " && session_id == '" + session + "'";
    return context.similaritySearch(
        SearchRequest.builder()
            .query(question)
            .topK(8)
            .similarityThreshold(0.35)
            .filterExpression(filter)
            .build());
  }

  // ##### 소유자의 장기 기억을 벡터 검색하는 메소드 #####
  public List<Document> memories(UUID owner, String question) {
    return memory.similaritySearch(
        SearchRequest.builder()
            .query(question)
            .topK(8)
            .similarityThreshold(0.35)
            .filterExpression(scope(owner, owner, "owner"))
            .build());
  }

  private String scope(UUID owner, UUID actor, String type) {
    return "clone_owner_id == '"
        + owner
        + "' && actor_user_id == '"
        + actor
        + "' && conversation_type == '"
        + type
        + "'";
  }
}
