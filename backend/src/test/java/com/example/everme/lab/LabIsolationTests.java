package com.example.everme.lab;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import static org.junit.jupiter.api.Assertions.*;

class LabIsolationTests {
    @Test void pagesAreNotBeansOutsideLabProfile(){
        new ApplicationContextRunner().withUserConfiguration(LabPageController.class,LabController.class,LabConfiguration.class,LabRunService.class).run(context->{assertFalse(context.containsBean("labPageController"));assertFalse(context.containsBean("labController"));assertFalse(context.containsBean("labConfiguration"));assertFalse(context.containsBean("labRunService"));});
    }
}
