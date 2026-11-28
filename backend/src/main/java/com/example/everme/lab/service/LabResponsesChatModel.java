package com.example.everme.lab.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.ai.chat.messages.AssistantMessage.ToolCall;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

import com.fasterxml.jackson.databind.ObjectMapper;

/** Spring AI 1.1 has no native Responses model. Preserve reasoning/tool items in this adapter. */
public final class LabResponsesChatModel implements ChatModel {
  // ##### 필드 #####
  public static final String RAW_RESPONSE = "lab.responses.raw";
  private final LabAiTransport transport;

  // ##### 생성자 #####
  // 요청 본문은 Map으로 만들어 RestClient의 Jackson 변환기가 직렬화하므로 ObjectMapper를 따로 쓰지 않는다.
  public LabResponsesChatModel(ObjectMapper json, LabAiTransport transport) {
    this.transport = transport;
  }

  @Override
  public ChatResponse call(Prompt prompt) {
    if (!(prompt.getOptions() instanceof LabResponsesOptions options)) {
      throw new IllegalArgumentException("Responses options required");
    }
    Map<String, Object> body = LabJson.object();
    body.put("model", options.getModel());
    body.put("store", false);
    if (!options.effort().isBlank()) {
      LabJson.child(body, "reasoning").put("effort", options.effort());
    }
    body.put("instructions", prompt.getSystemMessage().getText());
    body.put("input", options.input());
    body.put(
        "text",
        Map.of(
            "format",
            Map.of(
                "type",
                "json_schema",
                "name",
                "clone_lab",
                "strict",
                true,
                "schema",
                options.schema())));
    body.put("tools", options.tools());
    body.put("parallel_tool_calls", false);
    Map<String, Object> raw = transport.request("/responses", body);
    if (raw == null || !"completed".equals(LabJson.text(raw.get("status")))) {
      throw LabException.invalid("모델 응답이 완료되지 않았습니다.");
    }
    ArrayList<ToolCall> calls = new ArrayList<AssistantMessage.ToolCall>();
    StringBuilder text = new StringBuilder();
    for (Object itemValue : LabJson.list(raw.get("output"))) {
      Map<String, Object> item = LabJson.map(itemValue);
      if ("function_call".equals(LabJson.text(item.get("type")))) {
        calls.add(
            new AssistantMessage.ToolCall(
                LabJson.text(item.get("call_id")),
                "function",
                LabJson.text(item.get("name")),
                LabJson.text(item.get("arguments"))));
      }
      for (Object content : LabJson.list(item.get("content"))) {
        if ("refusal".equals(LabJson.text(LabJson.path(content, "type")))) {
          throw LabException.invalid("모델이 요청을 거절했습니다.");
        }
        if ("output_text".equals(LabJson.text(LabJson.path(content, "type")))) {
          text.append(LabJson.text(LabJson.path(content, "text")));
        }
      }
    }
    Map<String, Object> usage = LabJson.map(raw.get("usage"));
    ChatResponseMetadata metadata =
        ChatResponseMetadata.builder()
            .model(options.getModel())
            .id(LabJson.text(raw.get("id")))
            .usage(
                new DefaultUsage(
                    LabJson.integer(usage.get("input_tokens")),
                    LabJson.integer(usage.get("output_tokens")),
                    null,
                    usage))
            .keyValue(RAW_RESPONSE, LabJson.copy(raw))
            .build();
    return new ChatResponse(
        List.of(
            new Generation(
                AssistantMessage.builder().content(text.toString()).toolCalls(calls).build())),
        metadata);
  }
}
