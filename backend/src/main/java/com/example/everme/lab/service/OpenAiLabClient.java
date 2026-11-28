package com.example.everme.lab.service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map.Entry;
import java.util.Map;
import java.util.Set;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.ResponseFormat;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;

/** Spring AI facade. Services retain the lab contract, models own provider serialization. */
@Component
@Profile("clone-lab")
public class OpenAiLabClient implements LabAi {
  // ##### 필드 #####
  private final ObjectMapper json;
  private final String key;
  private final String embedding;
  private final Map<String, String> models;
  private Map<String, String> efforts;
  private final LabAiTransport transport;
  private final ChatClient chatClient;
  private final ChatClient cloneChatClient;
  private final EmbeddingModel embeddingModel;

  // ##### 생성자 #####
  // Spring이 쓰는 유일한 생성자. 단계별 reasoning effort도 여기서 받아 configureEfforts로 검증한다.
  public OpenAiLabClient(
      ObjectMapper json,
      @Value("${lab.api-key}") String key,
      @Value("${lab.generation-model}") String generation,
      @Value("${lab.analysis-model}") String analysis,
      @Value("${lab.answer-model}") String answer,
      @Value("${lab.embedding-model}") String embedding,
      @Value("${lab.generation-reasoning-effort:low}") String generationEffort,
      @Value("${lab.analysis-reasoning-effort:medium}") String analysisEffort,
      @Value("${lab.summary-reasoning-effort:medium}") String summaryEffort,
      @Value("${lab.answer-reasoning-effort:}") String answerEffort,
      LabAiTransport transport,
      @Qualifier("labChatModel") ChatModel model,
      @Qualifier("labResponsesChatModel") ChatModel responseModel,
      @Qualifier("labEmbeddingModel") EmbeddingModel embeddingModel) {
    configureEfforts(generationEffort, analysisEffort, summaryEffort, answerEffort);
    this.json = json;
    this.key = key;
    this.embedding = embedding;
    this.transport = transport;
    this.embeddingModel = embeddingModel;
    LabAiLogFormatter formatter = new LabAiLogFormatter(json);
    SimpleLoggerAdvisor logger =
        SimpleLoggerAdvisor.builder()
            .requestToString(formatter::request)
            .responseToString(formatter::response)
            .build();
    this.chatClient = ChatClient.builder(model).defaultAdvisors(logger).build();
    this.cloneChatClient = ChatClient.builder(responseModel).defaultAdvisors(logger).build();
    this.models =
        Map.of(
            "generation", generation, "summary", analysis, "analysis", analysis, "answer", answer);
  }

  // ##### 테스트용 클라이언트를 만드는 메소드 #####
  // 가짜 HTTP 서버 주소(base)로 Spring과 같은 모델 구성을 직접 만든다. effort는 설정 기본값을 쓴다.
  public static OpenAiLabClient standalone(
      ObjectMapper json,
      String key,
      String base,
      String generation,
      String analysis,
      String answer,
      String embedding) {
    LabAiTransport transport = new LabAiTransport(json, key, base);
    return new OpenAiLabClient(
        json,
        key,
        generation,
        analysis,
        answer,
        embedding,
        "low",
        "medium",
        "medium",
        "",
        transport,
        LabAiConfiguration.chatModel(LabAiConfiguration.api(transport, key, base)),
        new LabResponsesChatModel(json, transport),
        LabAiConfiguration.embeddingModel(
            LabAiConfiguration.api(transport, key, base), embedding));
  }

  // ##### 단계별 reasoning effort를 검증해 설정하는 메소드 #####
  // 빈 문자열이면 reasoning 파라미터를 보내지 않는다. 테스트에서 값을 바꿀 때도 쓴다.
  void configureEfforts(String generation, String analysis, String summary, String answer) {
    efforts =
        Map.of(
            "generation",
            generation.strip(),
            "analysis",
            analysis.strip(),
            "summary",
            summary.strip(),
            "answer",
            answer.strip());
    for (String effort : efforts.values()) {
      if (!Set.of("", "none", "minimal", "low", "medium", "high", "xhigh", "max")
          .contains(effort)) {
        throw new IllegalArgumentException("Invalid reasoning effort");
      }
    }
  }

  public static Map<String, Object> output(ObjectMapper json, Map<String, Object> response) {
    if (!"completed".equals(LabJson.text(response.get("status")))) {
      throw LabException.invalid("모델이 결과 생성을 완료하지 못했습니다.");
    }
    for (Object item : LabJson.list(response.get("output"))) {
      for (Object content : LabJson.list(LabJson.path(item, "content"))) {
        if ("refusal".equals(LabJson.text(LabJson.path(content, "type")))) {
          throw LabException.invalid("모델이 요청을 거절했습니다.");
        }
        if ("output_text".equals(LabJson.text(LabJson.path(content, "type")))) {
          try {
            return LabJson.map(
                json.readValue(LabJson.text(LabJson.path(content, "text")), Object.class));
          } catch (Exception e) {
            throw LabException.invalid("모델 JSON 응답이 올바르지 않습니다.");
          }
        }
      }
    }
    throw LabException.invalid("모델의 분석 결과가 없습니다.");
  }

  // ##### JSON 형식으로 LLM을 호출하는 메소드 #####
  @Override
  public Result structured(
      String stage, String instructions, Map<String, Object> input, Map<String, Object> schema) {
    transport.begin();
    long start = System.nanoTime();
    try {
      Map<String, Object> dynamic = LabJson.copy(input);
      String fixed = instructions;
      if (dynamic.containsKey("catalog")) {
        // 고정 카탈로그는 system 메시지 끝으로 옮겨 가변 입력과 분리한다.
        fixed += "\nCATALOG\n" + LabJson.write(json, dynamic.get("catalog"));
        dynamic.remove("catalog");
      }
      final String variable = LabJson.write(json, dynamic);
      final int fixedCharacters = fixed.length();
      String effort = efforts.getOrDefault(stage, "");
      ResponseFormat format =
          ResponseFormat.builder()
              .type(ResponseFormat.Type.JSON_SCHEMA)
              .jsonSchema(
                  ResponseFormat.JsonSchema.builder()
                      .name("clone_lab")
                      .strict(true)
                      .schema(LabJson.write(json, schema))
                      .build())
              .build();
      OpenAiChatOptions options =
          OpenAiChatOptions.builder()
              .model(models.get(stage))
              .store(false)
              .responseFormat(format)
              .reasoningEffort(effort.isBlank() ? null : effort)
              .build();
      ChatResponse response =
          chatClient
              .prompt()
              .system(fixed)
              .user(variable)
              .options(options)
              .advisors(
                  advisor ->
                      advisor
                          .param("labStage", stage)
                          .param("fixedCharacters", fixedCharacters)
                          .param("variableCharacters", variable.length()))
              .call()
              .chatResponse();
      if (response == null
          || response.getResult() == null
          || !"STOP".equalsIgnoreCase(response.getResult().getMetadata().getFinishReason())) {
        throw LabException.invalid("모델 응답이 완료되지 않았습니다.");
      }
      Object parsed = json.readValue(response.getResult().getOutput().getText(), Object.class);
      if (!LabJson.isMap(parsed)) {
        throw LabException.invalid("모델 JSON 결과가 없습니다.");
      }
      Map<String, Object> data = LabJson.map(parsed);
      Usage usage = response.getMetadata().getUsage();
      Object nativeUsage = LabJson.convert(json, usage.getNativeUsage());
      long cached =
          LabJson.longValue(LabJson.path(nativeUsage, "prompt_tokens_details", "cached_tokens"));
      return new Result(
          data,
          models.get(stage),
          usage.getPromptTokens(),
          usage.getCompletionTokens(),
          (System.nanoTime() - start) / 1_000_000,
          cached,
          transport.retries());
    } catch (LabException failure) {
      throw failure;
    } catch (Exception failure) {
      throw providerFailure(failure);
    } finally {
      transport.end();
    }
  }

  // ##### 도구 호출을 포함한 클론 답변 메소드 #####
  @Override
  public Map<String, Object> respond(
      String instructions,
      List<Object> input,
      Map<String, Object> schema,
      List<Map<String, Object>> tools) {
    transport.begin();
    try {
      LabResponsesOptions options =
          new LabResponsesOptions(
              models.get("answer"), efforts.getOrDefault("answer", ""), input, schema, tools);
      ChatResponse response =
          cloneChatClient
              .prompt()
              .system(instructions)
              .options(options)
              .advisors(advisor -> advisor.param("labStage", "answer"))
              .call()
              .chatResponse();
      if (response == null) {
        throw LabException.invalid("클론 응답이 없습니다.");
      }
      Object raw = response.getMetadata().get(LabResponsesChatModel.RAW_RESPONSE);
      return LabJson.map(raw);
    } catch (LabException failure) {
      throw failure;
    } catch (Exception failure) {
      throw providerFailure(failure);
    } finally {
      transport.end();
    }
  }

  // ##### 텍스트 임베딩을 생성하는 메소드 #####
  @Override
  public List<float[]> embed(List<String> texts) {
    if (texts.isEmpty()) {
      return List.of();
    }
    transport.begin();
    try {
      List<float[]> vectors = embeddingModel.embed(texts);
      if (vectors.size() != texts.size()) {
        throw LabException.invalid("임베딩 개수가 맞지 않습니다.");
      }
      for (float[] vector : vectors) {
        if (vector.length != 1536) {
          throw LabException.invalid("임베딩 차원이 맞지 않습니다.");
        }
        for (float value : vector) {
          if (!Float.isFinite(value)) {
            throw LabException.invalid("잘못된 임베딩 값입니다.");
          }
        }
      }
      return vectors;
    } catch (LabException failure) {
      throw failure;
    } catch (Exception failure) {
      throw providerFailure(failure);
    } finally {
      transport.end();
    }
  }

  private LabException providerFailure(Exception failure) {
    for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
      if (cause instanceof LabException lab) {
        return lab;
      }
    }
    return new LabException(502, "Spring AI 모델 요청 또는 응답 해석에 실패했습니다.");
  }

  // ##### 사용 중인 모델 설정을 반환하는 메소드 #####
  @Override
  public Map<String, Object> models() {
    Map<String, Object> result = LabJson.object();
    for (Entry<String, String> entry : models.entrySet()) {
      result.put(entry.getKey(), entry.getValue());
    }
    result.put("reasoningEfforts", new LinkedHashMap<String, Object>(efforts));
    result.put("embedding", embedding);
    result.put("keyConfigured", !key.isBlank());
    result.put("framework", "spring-ai");
    return result;
  }

  // ##### 모델 접근 권한을 확인하는 메소드 #####
  public List<String> checkModels() {
    transport.begin();
    try {
      List<String> result = new ArrayList<String>();
      LinkedHashSet<String> names = new LinkedHashSet<>(models.values());
      names.add(embedding);
      for (String name : names) {
        transport.request("/models/" + name, null);
        result.add(name);
      }
      return result;
    } catch (LabException failure) {
      throw failure;
    } catch (Exception failure) {
      throw providerFailure(failure);
    } finally {
      transport.end();
    }
  }

  static boolean retryableConnection(IOException failure) {
    return LabAiTransport.retryableConnection(failure);
  }

  static long retryAfter(String value) {
    return LabAiTransport.retryAfter(value);
  }
}
