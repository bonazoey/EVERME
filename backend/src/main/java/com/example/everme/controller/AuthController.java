package com.example.everme.controller;

import com.example.everme.dto.*;
import com.example.everme.service.AuthService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(value = "/api/auth", produces = "application/json")
@Slf4j 
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService){
        this.authService = authService;
    }

    /** FR-001,008: 회원가입. */
    @PostMapping("/signup")
    public ResponseEntity<?> signup(@RequestBody SignupRequest request) {
        // int result = authService.signup();
        
        // 회원가입 실패
        // if(result < 0 ){
            
        // }

        return null;
    }

    /** FR-002: 로그인. */
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request) {
        
        // int result = authService.login(request);

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

