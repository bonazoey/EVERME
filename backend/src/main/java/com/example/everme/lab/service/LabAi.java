package com.example.everme.lab.service;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;

public interface LabAi {
    record Result(JsonNode data, String model, long inputTokens, long outputTokens, long elapsedMs,long cachedTokens,int retries) {
        public Result(JsonNode data,String model,long inputTokens,long outputTokens,long elapsedMs){this(data,model,inputTokens,outputTokens,elapsedMs,0,0);}
    }
    Result structured(String stage, String instructions, JsonNode input, JsonNode schema);
    JsonNode respond(String instructions, JsonNode input, JsonNode schema, List<JsonNode> tools);
    List<float[]> embed(List<String> texts);
    JsonNode models();
}
