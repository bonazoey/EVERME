package com.example.everme.lab;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;

/** Deliberately separate from the production Spring AI chat configuration. */
@Component
@Profile("clone-lab")
public class OpenAiLabClient implements LabAi {
    private final ObjectMapper json;
    private final String key, base, embedding;
    private final Map<String,String> models;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();
    public OpenAiLabClient(ObjectMapper json, @Value("${lab.api-key}") String key,
            @Value("${lab.api-base}") String base, @Value("${lab.generation-model}") String generation,
            @Value("${lab.analysis-model}") String analysis, @Value("${lab.merge-model}") String merge,
            @Value("${lab.answer-model}") String answer, @Value("${lab.embedding-model}") String embedding) {
        this.json=json; this.key=key; this.base=base.replaceAll("/+$",""); this.embedding=embedding;
        models=Map.of("generation",generation,"analysis",analysis,"merge",merge,"answer",answer);
    }
    private JsonNode request(String route, JsonNode body) {
        if (key.isBlank()) throw new LabException(503,"OPENAI_API_KEY를 설정해 주세요.");
        try {
            var builder=HttpRequest.newBuilder(URI.create(base+route)).timeout(Duration.ofMinutes(5))
                    .header("Authorization","Bearer "+key).header("Content-Type","application/json");
            var req=body==null?builder.GET().build():builder.POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build();
            var response=http.send(req,HttpResponse.BodyHandlers.ofString());
            if (response.statusCode()<200 || response.statusCode()>=300)
                throw new LabException(response.statusCode()==429?429:502,"OpenAI 요청 실패 (HTTP "+response.statusCode()+"). 설정과 모델 접근 권한을 확인해 주세요.");
            return json.readTree(response.body());
        } catch (LabException e) { throw e; }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new LabException(503,"요청이 중단되었습니다."); }
        catch (Exception e) { throw new LabException(502,"OpenAI 연결 또는 응답 해석에 실패했습니다."); }
    }
    private ObjectNode body(String stage,String instructions,JsonNode input,JsonNode schema) {
        var body=json.createObjectNode();body.put("model",models.get(stage));body.put("store",false);
        body.put("instructions",instructions);body.set("input",input.isArray()?input:json.valueToTree(List.of(Map.of("role","user","content",input.toString()))));
        body.set("text",json.valueToTree(Map.of("format",Map.of("type","json_schema","name","clone_lab","strict",true,"schema",schema))));
        return body;
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
        long start=System.nanoTime();var response=request("/responses",body(stage,instructions,input,schema));
        return new Result(output(json,response),models.get(stage),response.path("usage").path("input_tokens").asLong(),response.path("usage").path("output_tokens").asLong(),(System.nanoTime()-start)/1_000_000);
    }
    @Override public JsonNode respond(String instructions,JsonNode input,JsonNode schema,List<JsonNode> tools) {
        var body=body("answer",instructions,input,schema);body.set("tools",json.valueToTree(tools));body.put("parallel_tool_calls",false);
        return request("/responses",body);
    }
    @Override public List<float[]> embed(List<String> texts) {
        if(texts.isEmpty())return List.of();
        var result=request("/embeddings",json.valueToTree(Map.of("model",embedding,"input",texts,"dimensions",1536)));
        var vectors=new ArrayList<float[]>();
        for(var item:result.path("data")){var arr=item.path("embedding");if(arr.size()!=1536)throw LabException.invalid("임베딩 차원이 맞지 않습니다.");var v=new float[1536];for(int i=0;i<v.length;i++){v[i]=(float)arr.get(i).asDouble();if(!Float.isFinite(v[i]))throw LabException.invalid("잘못된 임베딩 값입니다.");}vectors.add(v);}
        if(vectors.size()!=texts.size())throw LabException.invalid("임베딩 개수가 맞지 않습니다.");return vectors;
    }
    @Override public JsonNode models() {
        var result=json.createObjectNode();
        for(var entry:models.entrySet())result.put(entry.getKey(),entry.getValue());result.put("embedding",embedding);result.put("keyConfigured",!key.isBlank());return result;
    }
    public JsonNode checkModels() {
        var result=json.createArrayNode();var names=new LinkedHashSet<>(models.values());names.add(embedding);
        for(var name:names){request("/models/"+name,null);result.add(name);}return result;
    }
}
