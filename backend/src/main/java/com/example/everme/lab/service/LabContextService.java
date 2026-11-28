package com.example.everme.lab.service;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.example.everme.lab.dao.LabDao;
import com.example.everme.lab.prompt.LabPrompts;
import com.fasterxml.jackson.databind.ObjectMapper;

/** 최근 20쌍 문맥을 JDBC에 보존하고 20쌍 단위 요약을 최근 5개로 제한한다. */
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
  public Map<String, Object> prepare(Long session) {
    refresh(session);
    Map<String, Object> input = LabJson.object();
    List<Object> summaries = LabJson.childList(input, "summaries");
    dao.mapper()
        .summaries(session)
        .forEach(
            row -> {
              Map<String, Object> item = LabJson.object();
              item.put("id", "summary:" + row.get("id"));
              item.put("sessionId", session.toString());
              item.put("startMessageId", row.get("start_message_id").toString());
              item.put("endMessageId", row.get("end_message_id").toString());
              item.put("segment", ((Number) row.get("segment_no")).intValue());
              item.put("summary", row.get("content").toString());
              summaries.add(item);
            });
    List<Object> recent = LabJson.childList(input, "recent");
    List<Message> messages = memory.get(session.toString());
    List<Map<String, Object>> pairs = dao.mapper().sessionPairs(session);
    int first = pairs.size() - messages.size() / 2;
    for (int index = 0; index < messages.size(); index++) {
      Message message = messages.get(index);
      Object pairId = pairs.get(first + index / 2).get("id");
      Map<String, Object> item = LabJson.object();
      item.put("id", "context:" + pairId + ":" + message.getMessageType().getValue());
      item.put("messageId", pairId.toString());
      item.put("sessionId", session.toString());
      item.put("role", message.getMessageType().getValue());
      item.put("content", message.getText());
      recent.add(item);
    }
    return input;
  }

  // ##### 저장 원문으로 JDBC 문맥을 동기화하는 메소드 #####
  public void recorded(Long session) {
    refresh(session);
  }

  private void refresh(Long session) {
    List<Map<String, Object>> pairs = dao.mapper().sessionPairs(session);
    int count = pairs.size(), blocks = Math.max(0, (count - 20) / 20);
    // 이전 정책으로 너무 일찍 생성된 요약은 원문 보존 상태에서 다시 구성한다.
    tx.executeWithoutResult(
        status -> {
          dao.mapper().lockSession(session);
          int eligible = Math.max(0, (dao.mapper().sessionPairs(session).size() - 20) / 20);
          dao.mapper().discardPrematureSummaries(session, eligible);
        });
    List<Map<String, Object>> summaries = dao.mapper().summaries(session);
    int latest =
        summaries.isEmpty() ? 0 : ((Number) summaries.getLast().get("segment_no")).intValue();
    for (int segment = latest + 1; segment <= blocks; segment++) {
      int number = segment;
      List<Map<String, Object>> selected = pairs.subList((segment - 1) * 20, segment * 20);
      Map<String, Object> request = LabJson.object();
      request.put(
          "turns",
          LabJson.convert(
              json,
              selected.stream()
                  .map(
                      p -> Map.of("user", p.get("user_text"), "assistant", p.get("assistant_text")))
                  .toList()));
      LabAi.Result result =
          ai.structured(
              "summary",
              LabPrompts.CONTEXT_SUMMARY,
              request,
              schemas.object(Map.of("summary", schemas.string())));
      String summary = LabJson.text(result.data().get("summary"));
      LabValidation.text(summary, 1000, "대화 요약");
      tx.executeWithoutResult(
          status -> {
            Long owner = dao.mapper().sessionOwner(session);
            Map<String, Object> user = dao.mapper().lockUser(owner);
            dao.mapper().lockSession(session);
            if (dao.mapper().sessionPairs(session).size() != count)
              throw LabException.conflict("요약 중 대화가 변경되었습니다. 다시 시도해 주세요.");
            List<Map<String, Object>> now = dao.mapper().summaries(session);
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
            write(session);
            Object parsedRun = dao.parse(user.get("run_state"));
            if (LabJson.isMap(parsedRun)) {
              Map<String, Object> run = LabJson.map(parsedRun);
              if (session.toString().equals(LabJson.text(run.get("batchId")))
                  && Set.of("running", "stopping")
                      .contains(LabJson.text(run.get("status")))) {
                Map<String, Object> metrics = LabJson.object();
                metrics.put("inputTokens", result.inputTokens());
                metrics.put("outputTokens", result.outputTokens());
                metrics.put("cachedTokens", result.cachedTokens());
                metrics.put("elapsedMs", result.elapsedMs());
                LabPipeline.addMetrics(run, metrics);
                dao.mapper().runState(owner, LabJson.write(json, run));
              }
            }
          });
    }
    tx.executeWithoutResult(
        status -> {
          dao.mapper().lockSession(session);
          write(session);
        });
  }

  private void write(Long session) {
    dao.mapper().preserveContext(session);
    dao.mapper().slideContext(session);
    dao.mapper().deleteSummarizedContext(session);
  }
}
