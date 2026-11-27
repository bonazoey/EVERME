package com.example.everme.lab.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.model.ChatResponse;

/** Human-readable formatting only; never changes the prompt sent to the model. */
final class LabAiLogFormatter {
    private final ObjectMapper json;
    LabAiLogFormatter(ObjectMapper json){this.json=json;}

    String request(ChatClientRequest request) {
        var text=new StringBuilder("\n=== LLM REQUEST ===\n");
        section(text,"CONTEXT",pretty(request.context()));
        for(var message:request.prompt().getInstructions())section(text,message.getMessageType().name(),content(message.getText()));
        var options=request.prompt().getOptions();
        if(options instanceof LabResponsesOptions responses){
            section(text,"RESPONSES INPUT",pretty(responses.input()));
            var settings=json.createObjectNode().put("model",responses.model()).put("reasoningEffort",responses.effort());
            settings.set("schema",responses.schema());settings.set("tools",json.valueToTree(responses.tools()));
            section(text,"MODEL OPTIONS",pretty(settings));
        }else section(text,"MODEL OPTIONS",pretty(options));
        return text.append("=== END LLM REQUEST ===").toString();
    }
    String response(ChatResponse response) {
        var text=new StringBuilder("\n=== LLM RESPONSE ===\n");
        if(response==null)return text.append("(empty)\n=== END LLM RESPONSE ===").toString();
        for(var result:response.getResults()){
            section(text,"ASSISTANT",content(result.getOutput().getText()));
            if(result.getOutput().hasToolCalls())section(text,"TOOL CALLS",pretty(result.getOutput().getToolCalls()));
        }
        section(text,"METADATA",pretty(response.getMetadata()));
        return text.append("=== END LLM RESPONSE ===").toString();
    }
    private void section(StringBuilder text,String title,String value){text.append("\n--- ").append(title).append(" ---\n").append(value).append('\n');}
    private String content(String value) {
        if(value==null)return "";
        int catalog=value.indexOf("\nCATALOG\n");
        if(catalog>=0)return value.substring(0,catalog)+"\nCATALOG\n"+content(value.substring(catalog+9));
        try{var node=json.readTree(value);if(node!=null&&node.isContainerNode())return pretty(node);}catch(Exception ignored){}
        return value;
    }
    private String pretty(Object value){try{return json.writerWithDefaultPrettyPrinter().writeValueAsString(value).replace("\r\n","\n");}catch(Exception ignored){return "(unable to format log value)";}}
}
