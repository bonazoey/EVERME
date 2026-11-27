package com.example.everme.lab.service;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import com.fasterxml.jackson.databind.ObjectMapper;

@Configuration
@Profile("clone-lab")
public class LabConfiguration {
  @Bean
  LabSchemas labSchemas(ObjectMapper json) {
    return new LabSchemas(json);
  }

  @Bean
  ApplicationRunner initializeLab(
      DataSource dataSource,
      LabRunService runs,
      @Value("${lab.initialize-schema:false}") boolean initialize) {
    return args -> {
      if (initialize) {
        new ResourceDatabasePopulator(new ClassPathResource("clone-lab/schema.sql"))
            .execute(dataSource);
      }
      runs.recoverInterrupted();
    };
  }
}
