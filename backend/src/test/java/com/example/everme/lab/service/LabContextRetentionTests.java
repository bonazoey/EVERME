package com.example.everme.lab.service;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.example.everme.lab.dao.LabDao;
import com.example.everme.lab.dao.LabMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;

class LabContextRetentionTests {
  private final LabMapper mapper = mock(LabMapper.class);
  private final ObjectMapper json = new ObjectMapper();
  private final LabDao dao = new LabDao(mapper, json, null);
  private final LabMemoryService service = new LabMemoryService(dao, json);
  private final Long owner =
      ThreadLocalRandom.current().nextLong(1, Long.MAX_VALUE);
  private final Long id =
      ThreadLocalRandom.current().nextLong(1, Long.MAX_VALUE);

  private void overflow(String type, boolean ready) {
    when(mapper.overflow(owner, owner, type))
        .thenReturn(
            List.of(
                Map.of(
                    "id",
                    id,
                    "content",
                    "대화 원문",
                    "embedding",
                    "[1]",
                    "metadata",
                    "{\"user_text\":\"사용자 발언\"}")));
    when(mapper.archiveReady(id)).thenReturn(ready);
  }

  @Test
  void ownerPreservesContextAfterLongTermStorage() {
    overflow("owner", true);
    service.retain(owner, owner, "owner");
    InOrder order = inOrder(mapper);
    order.verify(mapper).overflow(owner, owner, "owner");
    order.verify(mapper).archiveReady(id);
    order.verify(mapper).archive(id);
    order.verify(mapper).contextStatus(id, "archived");
    verifyNoMoreInteractions(mapper);
  }

  @Test
  void viewerOnlyChangesStatus() {

    service.retain(owner, owner, "viewer");
    verifyNoMoreInteractions(mapper);
  }

  @Test
  void unfinishedContextIsNotArchived() {
    overflow("owner", false);
    service.retain(owner, owner, "owner");
    verify(mapper, never()).contextStatus(any(), any());
    verify(mapper, never()).archive(id);
  }

  @Test
  void failedLongTermStorageDoesNotMarkArchived() {
    overflow("owner", true);
    doThrow(new IllegalStateException("storage unavailable")).when(mapper).archive(id);
    assertThrows(IllegalStateException.class, () -> service.retain(owner, owner, "owner"));
    verify(mapper, never()).contextStatus(any(), any());
  }

  @Test
  void retrievalUsesOnlyActiveCandidatesAndAcceptsNativeFilterSyntax() {
    PgVectorStore context = mock(PgVectorStore.class);
    PgVectorStore memory = mock(PgVectorStore.class);
    LabRetrievalService retrieval = new LabRetrievalService(context, memory, dao);
    when(mapper.recent(owner)).thenReturn(List.of(Map.of("id", id)));
    when(context.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());
    retrieval.context(
        owner, ThreadLocalRandom.current().nextLong(1, Long.MAX_VALUE), "질문");
    verify(context).similaritySearch(any(SearchRequest.class));
    when(mapper.recent(owner)).thenReturn(List.of());
    retrieval.context(owner, null, "질문");
    verifyNoMoreInteractions(context);
  }
}
