package com.example.everme.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import com.example.everme.controller.MemberController;
import com.example.everme.dao.MemberDao;
import com.example.everme.dto.LoginRequest;
import com.example.everme.entity.Member;

class MemberLoginTest {
    @Test
    void onlyAnActiveMemberWithTheCorrectPasswordCanLogin() {
        MemberDao dao = mock(MemberDao.class);
        MemberService service = new MemberService(dao);
        Member request = new Member();
        request.setEmail("member@example.com");
        request.setPassword("password123");

        assertEquals("wrongPassword", service.login(request));

        Member stored = new Member();
        stored.setPassword(new BCryptPasswordEncoder().encode("password123"));
        when(dao.selectById(request.getEmail())).thenReturn(stored);
        assertEquals("success", service.login(request));

        request.setPassword("incorrect");
        assertEquals("wrongPassword", service.login(request));

        request.setPassword("password123");
        stored.setActive(false);
        assertEquals("wrongPassword", service.login(request));
    }

    @Test
    void rejectedLoginReturns401WithoutIssuingAToken() {
        MemberService service = mock(MemberService.class);
        JwtService jwt = mock(JwtService.class);
        when(service.login(any(Member.class))).thenReturn("wrongPassword");

        var response = new MemberController(service, jwt).login(new LoginRequest());

        assertEquals(401, response.getStatusCode().value());
        verifyNoInteractions(jwt);
    }

    @Test
    void successfulLoginReturnsATokenForTheAuthenticatedEmail() {
        MemberService service = mock(MemberService.class);
        JwtService jwt = mock(JwtService.class);
        when(service.login(any(Member.class))).thenReturn("success");
        when(jwt.createToken(any(Member.class))).thenReturn("test-token");
        LoginRequest request = new LoginRequest();
        request.setEmail("member@example.com");
        request.setPassword("password123");

        var response = new MemberController(service, jwt).login(request);

        assertEquals(200, response.getStatusCode().value());
        assertEquals("test-token", response.getBody().get("token"));
        verify(jwt).createToken(argThat(member -> request.getEmail().equals(member.getEmail())));
    }
}
