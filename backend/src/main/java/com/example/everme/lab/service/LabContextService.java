package com.example.everme.lab.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.example.everme.lab.dao.LabDao;
import com.example.everme.lab.prompt.LabPrompts;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/** 고정 20쌍 문맥을 JDBC에 보존하고 이전 구간 요약을 최근 5개로 제한한다. */
@Service
@Profile("clone-lab")
public class LabContextService {
  // ##### 필드 #####
  private final LabDao dao;
  private final ChatMemory memory;
  private final LabAi ai;
  private final LabSchemas schemas;
  private final ObjectMapper json;
  private final TransactionTemplate tx;

  // ##### 생성자 #####
  public LabContextService(
      LabDao dao,
      ChatMemory memory,
      LabAi ai,
      LabSchemas schemas,
      ObjectMapper json,
      TransactionTemplate tx) {
    this.dao = dao;
    this.memory = memory;
    this.ai = ai;
    this.schemas = schemas;
    this.json = json;
    this.tx = tx;
  }

  // ##### 다음 질문 전 완료 블록을 요약하고 최근 문맥을 읽는 메소드 #####
  public ObjectNode prepare(Long session) {
    refresh(session, true);
    ObjectNode input = json.createObjectNode();
    var summaries = input.putArray("summaries");
    dao.mapper()
        .summaries(session)
        .forEach(
            row ->
                summaries
                    .addObject()
                    .put("segment", ((Number) row.get("segment_no")).intValue())
                    .put("summary", row.get("content").toString()));
    var recent = input.putArray("recent");
    for (Message message : memory.get(session.toString()))
      recent
          .addObject()
          .put("role", message.getMessageType().getValue())
          .put("content", message.getText());
    return input;
  }

  // ##### 저장 원문으로 JDBC 문맥을 동기화하는 메소드 #####
  public void recorded(Long session) {
    refresh(session, false);
  }

  private void refresh(Long session, boolean before) {
    List<Map<String, Object>> pairs = dao.mapper().sessionPairs(session);
    int count = pairs.size(), blocks = Math.max(0, (count - (before ? 0 : 1)) / 20);
    var summaries = dao.mapper().summaries(session);
    int latest =
        summaries.isEmpty() ? 0 : ((Number) summaries.getLast().get("segment_no")).intValue();
    for (int segment = latest + 1; segment <= blocks; segment++) {
      int number = segment;
      var selected = pairs.subList((segment - 1) * 20, segment * 20);
      ObjectNode request = json.createObjectNode();
      request.set(
          "turns",
          json.valueToTree(
              selected.stream()
                  .map(
                      p -> Map.of("user", p.get("user_text"), "assistant", p.get("assistant_text")))
                  .toList()));
      var result =
          ai.structured(
              "summary",
              LabPrompts.CONTEXT_SUMMARY,
              request,
              schemas.object(Map.of("summary", schemas.string())));
      String summary = result.data().path("summary").asText();
      LabValidation.text(summary, 1000, "대화 요약");
      tx.executeWithoutResult(
          status -> {
            Long owner = dao.mapper().sessionOwner(session);
            var user = dao.mapper().lockUser(owner);
            dao.mapper().lockSession(session);
            if (dao.mapper().sessionPairs(session).size() != count)
              throw LabException.conflict("요약 중 대화가 변경되었습니다. 다시 시도해 주세요.");
            var now = dao.mapper().summaries(session);
            int high = now.isEmpty() ? 0 : ((Number) now.getLast().get("segment_no")).intValue();
            if (high >= number) return;
            dao.mapper()
                .summary(
                    session,
                    number,
                    ((Number) selected.getFirst().get("id")).longValue(),
                    ((Number) selected.getLast().get("id")).longValue(),
                    summary);
            dao.mapper().trimSummaries(session);
            write(session, pairs, number * 20);
            var run = dao.parse(user.get("run_state"));
            if (run.isObject()
                && session.toString().equals(run.path("batchId").asText())
                && java.util.Set.of("running", "stopping").contains(run.path("status").asText())) {
              ObjectNode metrics =
                  json.createObjectNode()
                      .put("inputTokens", result.inputTokens())
                      .put("outputTokens", result.outputTokens())
                      .put("cachedTokens", result.cachedTokens())
                      .put("elapsedMs", result.elapsedMs());
              LabPipeline.addMetrics((ObjectNode) run, metrics);
              dao.mapper().runState(owner, run.toString());
            }
          });
    }
    tx.executeWithoutResult(
        status -> {
          dao.mapper().lockSession(session);
          var fresh = dao.mapper().sessionPairs(session);
          var now = dao.mapper().summaries(session);
          int high = now.isEmpty() ? 0 : ((Number) now.getLast().get("segment_no")).intValue();
          write(session, fresh, high * 20);
        });
  }

  private void write(Long session, List<Map<String, Object>> pairs, int start) {
    List<Message> messages = new ArrayList<>();
    for (int i = start; i < pairs.size(); i++) {
      messages.add(new UserMessage(pairs.get(i).get("user_text").toString()));
      messages.add(new AssistantMessage(pairs.get(i).get("assistant_text").toString()));
    }
    memory.clear(session.toString());
    if (!messages.isEmpty()) memory.add(session.toString(), messages);
  }
}
