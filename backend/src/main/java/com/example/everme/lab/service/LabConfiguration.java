package com.example.everme.lab.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import javax.sql.DataSource;

@Configuration
@Profile("clone-lab")
public class LabConfiguration {
    @Bean LabSchemas labSchemas(ObjectMapper json){return new LabSchemas(json);}
    @Bean ApplicationRunner initializeLab(DataSource dataSource,LabRunService runs,@Value("${lab.initialize-schema:false}") boolean initialize){
        return args->{
            if(initialize)new ResourceDatabasePopulator(new ClassPathResource("clone-lab/schema.sql")).execute(dataSource);
            runs.recoverInterrupted();
        };
    }
}
