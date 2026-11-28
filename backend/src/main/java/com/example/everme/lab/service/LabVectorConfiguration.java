package com.example.everme.lab.service;

import java.util.ArrayList;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;

/** 최근 문맥과 장기 기억을 서로 다른 PgVectorStore로 구성한다. DDL은 수동 스키마를 사용한다. */
@Configuration(proxyBeanMethods = false)
@Profile("clone-lab")
public class LabVectorConfiguration {
  @Bean("labContextStore")
  PgVectorStore contextStore(JdbcTemplate jdbc, LabAi ai) {
    return store(jdbc, ai, "context");
  }

  @Bean("labMemoryStore")
  PgVectorStore memoryStore(JdbcTemplate jdbc, LabAi ai) {
    return store(jdbc, ai, "vector_store");
  }

  private PgVectorStore store(JdbcTemplate jdbc, LabAi ai, String table) {
    EmbeddingModel embeddings =
        new EmbeddingModel() {
          @Override
          public EmbeddingResponse call(EmbeddingRequest request) {
            var vectors = ai.embed(request.getInstructions());
            var results = new ArrayList<Embedding>();
            for (int i = 0; i < vectors.size(); i++) results.add(new Embedding(vectors.get(i), i));
            return new EmbeddingResponse(results);
          }

          @Override
          public float[] embed(Document document) {
            return ai.embed(java.util.List.of(document.getText())).getFirst();
          }

          @Override
          public int dimensions() {
            return 1536;
          }
        };
    return PgVectorStore.builder(jdbc, embeddings)
        .schemaName("clone_lab")
        .vectorTableName(table)
        .dimensions(1536)
        .initializeSchema(false)
        .vectorTableValidationsEnabled(false)
        .build();
  }
}
