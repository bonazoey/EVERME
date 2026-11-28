package com.example.everme.lab.service;

import java.io.IOException;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import com.fasterxml.jackson.databind.ObjectMapper;

@Configuration
@Profile("clone-lab")
public class LabConfiguration {
  @Bean
  LabSchemas labSchemas() {
    return new LabSchemas();
  }

  @Bean
  LabCatalog labCatalog(ObjectMapper json) throws IOException {
    return LabCatalog.load(json);
  }

  @Bean
  ApplicationRunner initializeLab(
      DataSource dataSource,
      LabRunService runs,
      @Value("${lab.initialize-schema:false}") boolean initialize) {
    return args -> {
      if (initialize) {
        ResourceDatabasePopulator populator =
            new ResourceDatabasePopulator(new ClassPathResource("clone-lab/schema.sql"));
        populator.setSqlScriptEncoding("UTF-8");
        // PostgreSQL DO 블록 내부의 세미콜론을 문장 구분자로 해석하지 않는다.
        populator.setSeparator(ScriptUtils.EOF_STATEMENT_SEPARATOR);
        populator.execute(dataSource);
      }
      runs.recoverInterrupted();
    };
  }
}
