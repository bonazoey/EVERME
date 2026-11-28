package com.example.everme.lab.service;

import java.util.List;
import java.util.Map;

import org.springframework.ai.chat.prompt.ChatOptions;

/** Request-local Responses options; never stores a viewer's conversation on a singleton. */
public record LabResponsesOptions(
    // ##### 필드 #####
    String model,
    String effort,
    List<Object> input,
    Map<String, Object> schema,
    List<Map<String, Object>> tools)
    implements ChatOptions {
  // ##### 생성자 #####
  public LabResponsesOptions {
    input = LabJson.copy(input);
    schema = LabJson.copy(schema);
    tools = tools.stream().<Map<String, Object>>map(LabJson::copy).toList();
  }

  public String getModel() {
    return model;
  }

  public Double getFrequencyPenalty() {
    return null;
  }

  public Integer getMaxTokens() {
    return null;
  }

  public Double getPresencePenalty() {
    return null;
  }

  public List<String> getStopSequences() {
    return null;
  }

  public Double getTemperature() {
    return null;
  }

  public Integer getTopK() {
    return null;
  }

  public Double getTopP() {
    return null;
  }

  @SuppressWarnings("unchecked")
  public <T extends ChatOptions> T copy() {
    return (T) new LabResponsesOptions(model, effort, input, schema, tools);
  }
}
