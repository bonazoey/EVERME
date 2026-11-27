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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/** Spring AI 1.1 has no native Responses model. Preserve reasoning/tool items in this adapter. */
public final class LabResponsesChatModel implements ChatModel {
  // ##### 필드 #####
  public static final String RAW_RESPONSE = "lab.responses.raw";
  private final ObjectMapper json;
  private final LabAiTransport transport;

  // ##### 생성자 #####
  public LabResponsesChatModel(ObjectMapper json, LabAiTransport transport) {
    this.json = json;
    this.transport = transport;
  }

  @Override
  public ChatResponse call(Prompt prompt) {
    if (!(prompt.getOptions() instanceof LabResponsesOptions options)) {
      throw new IllegalArgumentException("Responses options required");
    }
    ObjectNode body = json.createObjectNode();
    body.put("model", options.getModel());
    body.put("store", false);
    if (!options.effort().isBlank()) {
      body.putObject("reasoning").put("effort", options.effort());
    }
    body.put("instructions", prompt.getSystemMessage().getText());
    body.set("input", options.input());
    body.set(
        "text",
        json.valueToTree(
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
                    options.schema()))));
    body.set("tools", json.valueToTree(options.tools()));
    body.put("parallel_tool_calls", false);
    JsonNode raw = transport.request("/responses", body);
    if (raw == null || !"completed".equals(raw.path("status").asText())) {
      throw LabException.invalid("모델 응답이 완료되지 않았습니다.");
    }
    ArrayList<ToolCall> calls = new ArrayList<AssistantMessage.ToolCall>();
    StringBuilder text = new StringBuilder();
    for (JsonNode item : raw.path("output")) {
      if ("function_call".equals(item.path("type").asText())) {
        calls.add(
            new AssistantMessage.ToolCall(
                item.path("call_id").asText(),
                "function",
                item.path("name").asText(),
                item.path("arguments").asText()));
      }
      for (JsonNode content : item.path("content")) {
        if ("refusal".equals(content.path("type").asText())) {
          throw LabException.invalid("모델이 요청을 거절했습니다.");
        }
        if ("output_text".equals(content.path("type").asText())) {
          text.append(content.path("text").asText());
        }
      }
    }
    JsonNode usage = raw.path("usage");
    ChatResponseMetadata metadata =
        ChatResponseMetadata.builder()
            .model(options.getModel())
            .id(raw.path("id").asText())
            .usage(
                new DefaultUsage(
                    usage.path("input_tokens").asInt(),
                    usage.path("output_tokens").asInt(),
                    null,
                    usage))
            .keyValue(RAW_RESPONSE, raw.deepCopy())
            .build();
    return new ChatResponse(
        List.of(
            new Generation(
                AssistantMessage.builder().content(text.toString()).toolCalls(calls).build())),
        metadata);
  }
}
