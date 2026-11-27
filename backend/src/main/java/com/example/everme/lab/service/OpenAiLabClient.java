package com.example.everme.lab.service;

import com.fasterxml.jackson.databind.*;
import org.springframework.beans.factory.annotation.*;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.messages.*;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.ResponseFormat;
import java.util.*;

/** Spring AI facade. Services retain the lab contract, models own provider serialization. */
@Component
@Profile("clone-lab")
public class OpenAiLabClient implements LabAi {
    private final ObjectMapper json;
    private final String key,embedding;
    private final Map<String,String> models;
    private Map<String,String> efforts=Map.of("generation","low","analysis","medium","summary","medium","answer","");
    private final LabAiTransport transport;
    private final ChatClient chat,answers;
    private final EmbeddingModel embeddings;

    @Autowired
    public OpenAiLabClient(ObjectMapper json,@Value("${lab.api-key}") String key,
            @Value("${lab.generation-model}") String generation,@Value("${lab.analysis-model}") String analysis,
            @Value("${lab.answer-model}") String answer,@Value("${lab.embedding-model}") String embedding,
            LabAiTransport transport,@Qualifier("labChatModel") ChatModel model,
            @Qualifier("labResponsesChatModel") ChatModel responseModel,@Qualifier("labEmbeddingModel") EmbeddingModel embeddings) {
        this.json=json;this.key=key;this.embedding=embedding;this.transport=transport;this.embeddings=embeddings;
        var formatter=new LabAiLogFormatter(json);
        var logger=SimpleLoggerAdvisor.builder().requestToString(formatter::request).responseToString(formatter::response).build();
        this.chat=ChatClient.builder(model).defaultAdvisors(logger).build();
        this.answers=ChatClient.builder(responseModel).defaultAdvisors(logger).build();
        this.models=Map.of("generation",generation,"summary",analysis,"analysis",analysis,"answer",answer);
    }
    // Standalone construction for the local fake-HTTP contract tests, using the same models as Spring.
    public OpenAiLabClient(ObjectMapper json,String key,String base,String generation,String analysis,String answer,String embedding) {
        this(json,key,generation,analysis,answer,embedding,new LabAiTransport(json,key,base));
    }
    private OpenAiLabClient(ObjectMapper json,String key,String generation,String analysis,String answer,String embedding,LabAiTransport transport) {
        this(json,key,generation,analysis,answer,embedding,transport,
            LabAiConfiguration.chatModel(LabAiConfiguration.api(transport,key,baseUrl(transport))),
            new LabResponsesChatModel(json,transport),
            LabAiConfiguration.embeddingModel(LabAiConfiguration.api(transport,key,baseUrl(transport)),embedding));
    }
    private static String baseUrl(LabAiTransport transport){return transport.baseUrl();}
    @Autowired
    void configureEfforts(@Value("${lab.generation-reasoning-effort:low}") String generation,@Value("${lab.analysis-reasoning-effort:medium}") String analysis,@Value("${lab.summary-reasoning-effort:medium}") String summary,@Value("${lab.answer-reasoning-effort:}") String answer){
        efforts=Map.of("generation",generation.strip(),"analysis",analysis.strip(),"summary",summary.strip(),"answer",answer.strip());
        for(String effort:efforts.values())if(!Set.of("","none","minimal","low","medium","high","xhigh","max").contains(effort))throw new IllegalArgumentException("Invalid reasoning effort");
    }
    public static JsonNode output(ObjectMapper json, JsonNode response) {
        if (!"completed".equals(response.path("status").asText())) throw LabException.invalid("모델이 결과 생성을 완료하지 못했습니다.");
        for(var item:response.path("output")) for(var content:item.path("content")) {
            if("refusal".equals(content.path("type").asText())) throw LabException.invalid("모델이 요청을 거절했습니다.");
            if("output_text".equals(content.path("type").asText())) try {return json.readTree(content.path("text").asText());}
            catch(Exception e){throw LabException.invalid("모델 JSON 응답이 올바르지 않습니다.");}
        }
        throw LabException.invalid("모델의 분석 결과가 없습니다.");
    }

    @Override public Result structured(String stage,String instructions,JsonNode input,JsonNode schema) {
        transport.begin();long start=System.nanoTime();
        try {
            var dynamic=input.deepCopy();String fixed=instructions;
            if(dynamic.isObject()&&dynamic.has("catalog")){fixed+="\nCATALOG\n"+dynamic.path("catalog");((com.fasterxml.jackson.databind.node.ObjectNode)dynamic).remove("catalog");}
            final int fixedCharacters=fixed.length();
            String effort=efforts.getOrDefault(stage,"");
            var format=ResponseFormat.builder().type(ResponseFormat.Type.JSON_SCHEMA)
                    .jsonSchema(ResponseFormat.JsonSchema.builder().name("clone_lab").strict(true).schema(schema.toString()).build()).build();
            var options=OpenAiChatOptions.builder().model(models.get(stage)).store(false).responseFormat(format).reasoningEffort(effort.isBlank()?null:effort).build();
            var response=chat.prompt(new Prompt(List.of(new SystemMessage(fixed),new UserMessage(dynamic.toString())),options))
                    .advisors(advisor->advisor.param("labStage",stage).param("fixedCharacters",fixedCharacters).param("variableCharacters",dynamic.toString().length())).call().chatResponse();
            if(response==null||response.getResult()==null||!"STOP".equalsIgnoreCase(response.getResult().getMetadata().getFinishReason()))throw LabException.invalid("모델 응답이 완료되지 않았습니다.");
            var data=json.readTree(response.getResult().getOutput().getText());
            if(data==null)throw LabException.invalid("모델 JSON 결과가 없습니다.");
            var usage=response.getMetadata().getUsage();
            var nativeUsage=json.valueToTree(usage.getNativeUsage());
            long cached=nativeUsage.path("prompt_tokens_details").path("cached_tokens").asLong();
            return new Result(data,models.get(stage),usage.getPromptTokens(),usage.getCompletionTokens(),(System.nanoTime()-start)/1_000_000,cached,transport.retries());
        }catch(LabException failure){throw failure;}catch(Exception failure){throw providerFailure(failure);}finally{transport.end();}
    }
    @Override public JsonNode respond(String instructions,JsonNode input,JsonNode schema,List<JsonNode> tools) {
        transport.begin();
        try {
            var options=new LabResponsesOptions(models.get("answer"),efforts.getOrDefault("answer",""),input,schema,tools);
            var response=answers.prompt(new Prompt(List.of(new SystemMessage(instructions)),options)).advisors(advisor->advisor.param("labStage","answer")).call().chatResponse();
            if(response==null)throw LabException.invalid("클론 응답이 없습니다.");
            return response.getMetadata().get(LabResponsesChatModel.RAW_RESPONSE);
        }catch(LabException failure){throw failure;}catch(Exception failure){throw providerFailure(failure);}finally{transport.end();}
    }
    @Override public List<float[]> embed(List<String> texts) {
        if(texts.isEmpty())return List.of();transport.begin();
        try {
            var vectors=embeddings.embed(texts);
            if(vectors.size()!=texts.size())throw LabException.invalid("임베딩 개수가 맞지 않습니다.");
            for(var vector:vectors){if(vector.length!=1536)throw LabException.invalid("임베딩 차원이 맞지 않습니다.");for(float value:vector)if(!Float.isFinite(value))throw LabException.invalid("잘못된 임베딩 값입니다.");}
            return vectors;
        }catch(LabException failure){throw failure;}catch(Exception failure){throw providerFailure(failure);}finally{transport.end();}
    }
    private LabException providerFailure(Exception failure) {
        for(Throwable cause=failure;cause!=null;cause=cause.getCause())if(cause instanceof LabException lab)return lab;
        return new LabException(502,"Spring AI 모델 요청 또는 응답 해석에 실패했습니다.");
    }
    @Override public JsonNode models() {
        var result=json.createObjectNode();for(var entry:models.entrySet())result.put(entry.getKey(),entry.getValue());
        result.set("reasoningEfforts",json.valueToTree(efforts));result.put("embedding",embedding);result.put("keyConfigured",!key.isBlank());result.put("framework","spring-ai");return result;
    }
    public JsonNode checkModels() {
        transport.begin();try{var result=json.createArrayNode();var names=new LinkedHashSet<>(models.values());names.add(embedding);for(var name:names){transport.request("/models/"+name,null);result.add(name);}return result;}
        catch(LabException failure){throw failure;}catch(Exception failure){throw providerFailure(failure);}finally{transport.end();}
    }
    static boolean retryableConnection(java.io.IOException failure){return LabAiTransport.retryableConnection(failure);}
    static long retryAfter(String value){return LabAiTransport.retryAfter(value);}
}
