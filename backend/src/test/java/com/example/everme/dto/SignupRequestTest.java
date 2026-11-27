package com.example.everme.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SignupRequestTest {
    @Test
    void deserializesSignupAndDoesNotLogPassword() throws Exception {
        String json = """
                {"name":"Kim","email":"kim@example.com","password":"secret123",
                 "consents":[{"consentType":"TERMS","documentVersion":"1","agreed":true}]}
                """;

        SignupRequest request = new ObjectMapper().readValue(json, SignupRequest.class);

        assertEquals("Kim", request.getName());
        assertEquals("secret123", request.getPassword());
        assertEquals("TERMS", request.getConsents().get(0).getConsentType());
        assertTrue(request.getConsents().get(0).getAgreed());
        assertFalse(request.toString().contains("secret123"));
    }
}
