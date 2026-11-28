package com.example.everme.lab.service;

import java.util.Map;

import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;

import com.fasterxml.jackson.databind.ObjectMapper;

/** Human-readable formatting only; never changes the prompt sent to the model. */
final class LabAiLogFormatter {
  // ##### 필드 #####
  private final ObjectMapper json;

  // ##### 생성자 #####
  LabAiLogFormatter(ObjectMapper json) {
    this.json = json;
  }

  // ##### 모델 요청을 처리하는 메소드 #####
  String request(ChatClientRequest request) {
    StringBuilder text = new StringBuilder("\n=== LLM REQUEST ===\n");
    section(text, "CONTEXT", pretty(request.context()));
    for (Message message : request.prompt().getInstructions()) {
      section(text, message.getMessageType().name(), content(message.getText()));
    }
    ChatOptions options = request.prompt().getOptions();
    if (options instanceof LabResponsesOptions responses) {
      section(text, "RESPONSES INPUT", pretty(responses.input()));
      Map<String, Object> settings = LabJson.object();
      settings.put("model", responses.model());
      settings.put("reasoningEffort", responses.effort());
      settings.put("schema", responses.schema());
      settings.put("tools", responses.tools());
      section(text, "MODEL OPTIONS", pretty(settings));
    } else {
      section(text, "MODEL OPTIONS", pretty(options));
    }
    return text.append("=== END LLM REQUEST ===").toString();
  }

  // ##### 모델 응답을 처리하는 메소드 #####
  String response(ChatResponse response) {
    StringBuilder text = new StringBuilder("\n=== LLM RESPONSE ===\n");
    if (response == null) {
      return text.append("(empty)\n=== END LLM RESPONSE ===").toString();
    }
    for (Generation result : response.getResults()) {
      section(text, "ASSISTANT", content(result.getOutput().getText()));
      if (result.getOutput().hasToolCalls()) {
        section(text, "TOOL CALLS", pretty(result.getOutput().getToolCalls()));
      }
    }
    section(text, "METADATA", pretty(response.getMetadata()));
    return text.append("=== END LLM RESPONSE ===").toString();
  }

  private void section(StringBuilder text, String title, String value) {
    text.append("\n--- ").append(title).append(" ---\n").append(value).append('\n');
  }

  private String content(String value) {
    if (value == null) {
      return "";
    }
    int catalog = value.indexOf("\nCATALOG\n");
    if (catalog >= 0) {
      return value.substring(0, catalog) + "\nCATALOG\n" + content(value.substring(catalog + 9));
    }
    try {
      Object node = json.readValue(value, Object.class);
      if (LabJson.isMap(node) || LabJson.isList(node)) {
        return pretty(node);
      }
    } catch (Exception ignored) {
    }
    return value;
  }

  private String pretty(Object value) {
    try {
      return json.writerWithDefaultPrettyPrinter().writeValueAsString(value).replace("\r\n", "\n");
    } catch (Exception ignored) {
      return "(unable to format log value)";
    }
  }
}
