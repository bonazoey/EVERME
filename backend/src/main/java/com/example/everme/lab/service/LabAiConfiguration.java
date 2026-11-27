package com.example.everme.lab.service;

import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.retry.support.RetryTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;

/** Lab-specific models; production chat configuration is not injected into the experiment. */
@Configuration(proxyBeanMethods = false)
@Profile("clone-lab")
public class LabAiConfiguration {
  @Bean
  LabAiTransport labAiTransport(
      ObjectMapper json,
      @Value("${lab.api-key}") String key,
      @Value("${lab.api-base}") String base) {
    return new LabAiTransport(json, key, base);
  }

  @Bean("labOpenAiApi")
  OpenAiApi labOpenAiApi(
      LabAiTransport transport,
      @Value("${lab.api-key}") String key,
      @Value("${lab.api-base}") String base) {
    return api(transport, key, base);
  }

  static OpenAiApi api(LabAiTransport transport, String key, String base) {
    return OpenAiApi.builder()
        .apiKey(key.isBlank() ? "lab-not-configured" : key)
        .baseUrl(base.replaceAll("/+$", ""))
        .completionsPath("/chat/completions")
        .embeddingsPath("/embeddings")
        .restClientBuilder(transport.builder())
        .build();
  }

  @Bean("labChatModel")
  OpenAiChatModel labChatModel(@Qualifier("labOpenAiApi") OpenAiApi api) {
    return chatModel(api);
  }

  static OpenAiChatModel chatModel(OpenAiApi api) {
    return OpenAiChatModel.builder()
        .openAiApi(api)
        .defaultOptions(OpenAiChatOptions.builder().temperature(null).store(false).build())
        .retryTemplate(RetryTemplate.builder().maxAttempts(1).build())
        .build();
  }

  @Bean("labEmbeddingModel")
  OpenAiEmbeddingModel labEmbeddingModel(
      @Qualifier("labOpenAiApi") OpenAiApi api, @Value("${lab.embedding-model}") String model) {
    return embeddingModel(api, model);
  }

  static OpenAiEmbeddingModel embeddingModel(OpenAiApi api, String model) {
    return new OpenAiEmbeddingModel(
        api,
        MetadataMode.NONE,
        OpenAiEmbeddingOptions.builder().model(model).dimensions(1536).build(),
        RetryTemplate.builder().maxAttempts(1).build());
  }

  @Bean("labResponsesChatModel")
  LabResponsesChatModel labResponsesChatModel(ObjectMapper json, LabAiTransport transport) {
    return new LabResponsesChatModel(json, transport);
  }
}
