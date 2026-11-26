package com.example.everme.controller;

import com.example.everme.dto.EndpointRequests.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** Auth 기능별 엔드포인트 골격. 업무 로직과 계층 주입은 후속 구현한다. */
@RestController
@RequestMapping(value = "/api/auth", produces = "application/json")
public class AuthController {

    /** FR-001,008: 회원가입. */
    @PostMapping("/signup")
    public ResponseEntity<?> signup(@RequestBody SignupRequest request) {
        return null;
    }

    /** FR-002: 로그인. */
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request) {
        return null;
    }

    /** FR-003: 로그아웃. */
    @PostMapping("/logout")
    public ResponseEntity<?> logout() {
        return null;
    }

    /** FR-004: 소셜 인증 시작. */
    @GetMapping("/oauth/{provider}")
    public ResponseEntity<?> socialLogin(@PathVariable("provider") String provider) {
        return null;
    }

    /** FR-004: 소셜 인증 콜백. */
    @GetMapping("/oauth/{provider}/callback")
    public ResponseEntity<?> socialCallback(@PathVariable("provider") String provider, @RequestParam("code") String code, @RequestParam("state") String state) {
        return null;
    }

    /** FR-005: 재설정 이메일 요청. */
    @PostMapping("/password-reset-requests")
    public ResponseEntity<?> requestPasswordReset(@RequestBody PasswordResetRequest request) {
        return null;
    }

    /** FR-005: 본인 인증 토큰으로 비밀번호 재설정. */
    @PostMapping("/password-resets")
    public ResponseEntity<?> resetPassword(@RequestBody PasswordChangeRequest request) {
        return null;
    }
}

