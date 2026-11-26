package com.example.everme.controller;

import java.util.Arrays;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** DB·AI 없이 기능별 경로 등록과 null 골격을 검증한다. */
class EndpointContractTests {
    private MockMvc mvc;
    private Object[] controllers;

    @BeforeEach
    void setUp() {
        controllers = new Object[]{
                new AuthController(), new AccountController(), new OwnerConversationController(),
                new ViewerConversationController(), new CloneController(), new AnalysisController(),
                new AccessController(), new InvitationController(), new ConsentDocumentController()};
        mvc = MockMvcBuilders.standaloneSetup(controllers).build();
    }

    @Test
    void conversationsUseSeparateControllers() throws Exception {
        mvc.perform(post("/api/owner/conversations"))
                .andExpect(handler().handlerType(OwnerConversationController.class));
        mvc.perform(post("/api/viewer/conversations").contentType("application/json")
                        .content("{\"cloneId\":\"00000000-0000-0000-0000-000000000001\"}"))
                .andExpect(handler().handlerType(ViewerConversationController.class));
        mvc.perform(get("/api/conversations")).andExpect(status().isNotFound());
    }

    @Test
    void featuresHaveTheirOwnControllers() throws Exception {
        mvc.perform(get("/api/me/analysis")).andExpect(handler().handlerType(AnalysisController.class));
        mvc.perform(get("/api/owner/conversations/topics"))
                .andExpect(handler().handlerType(OwnerConversationController.class));
        mvc.perform(get("/api/me/clone/access-history"))
                .andExpect(handler().handlerType(AccessController.class));
        mvc.perform(post("/api/me/invitations").contentType("application/json").content("{}"))
                .andExpect(handler().handlerType(InvitationController.class));
    }

    @Test
    void unsupportedOperationsHaveNoMapping() throws Exception {
        mvc.perform(get("/api/me/consents")).andExpect(status().isNotFound());
        mvc.perform(patch("/api/me/analysis/00000000-0000-0000-0000-000000000001")
                        .contentType("application/json").content("{}"))
                .andExpect(status().isMethodNotAllowed());
        mvc.perform(patch("/api/viewer/conversations/00000000-0000-0000-0000-000000000001/messages/00000000-0000-0000-0000-000000000002")
                        .contentType("application/json").content("{}"))
                .andExpect(status().isMethodNotAllowed());
    }

    @Test
    void all58EndpointsReturnNull() throws Exception {
        var mapping = mvc.getDispatcherServlet().getWebApplicationContext()
                .getBean(RequestMappingHandlerMapping.class);
        assertEquals(58, mapping.getHandlerMethods().size());
        for (Object controller : controllers) {
            for (var method : controller.getClass().getDeclaredMethods()) {
                Object[] args = Arrays.stream(method.getParameterTypes())
                        .map(type -> type == int.class ? 0 : null).toArray();
                assertNull(method.invoke(controller, args), method.toString());
            }
        }
    }
}
