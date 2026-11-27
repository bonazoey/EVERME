package com.example.everme.lab.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.ai.openai.*;
import static org.junit.jupiter.api.Assertions.*;

class LabAiConfigurationTests {
    @Test void labWiresQualifiedSpringAiModelsWithoutMakingRequests(){
        new ApplicationContextRunner().withUserConfiguration(LabAiConfiguration.class,OpenAiLabClient.class)
                .withBean(ObjectMapper.class,ObjectMapper::new)
                .withPropertyValues("spring.profiles.active=clone-lab","lab.api-key=","lab.api-base=http://127.0.0.1:1/v1","lab.generation-model=gpt-6.1-sol","lab.analysis-model=gpt-6.1-sol","lab.answer-model=gpt-6.1-terra","lab.embedding-model=text-embedding-3-small")
                .run(context->{
                    assertNull(context.getStartupFailure());assertInstanceOf(OpenAiChatModel.class,context.getBean("labChatModel"));
                    assertInstanceOf(OpenAiEmbeddingModel.class,context.getBean("labEmbeddingModel"));assertInstanceOf(LabResponsesChatModel.class,context.getBean("labResponsesChatModel"));
                    assertEquals(1,context.getBeansOfType(LabAi.class).size());assertEquals("spring-ai",context.getBean(LabAi.class).models().path("framework").asText());
                    assertFalse(context.getBean(LabAi.class).models().path("keyConfigured").asBoolean());
                });
    }
}
