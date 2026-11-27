package com.example.everme.lab.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.ai.chat.prompt.ChatOptions;
import java.util.List;

/** Request-local Responses options; never stores a viewer's conversation on a singleton. */
public record LabResponsesOptions(String model,String effort,JsonNode input,JsonNode schema,List<JsonNode> tools) implements ChatOptions {
    public LabResponsesOptions {input=input.deepCopy();schema=schema.deepCopy();tools=tools.stream().<JsonNode>map(JsonNode::deepCopy).toList();}
    public String getModel(){return model;}
    public Double getFrequencyPenalty(){return null;}
    public Integer getMaxTokens(){return null;}
    public Double getPresencePenalty(){return null;}
    public List<String> getStopSequences(){return null;}
    public Double getTemperature(){return null;}
    public Integer getTopK(){return null;}
    public Double getTopP(){return null;}
    @SuppressWarnings("unchecked") public <T extends ChatOptions> T copy(){return (T)new LabResponsesOptions(model,effort,input,schema,tools);}
}
