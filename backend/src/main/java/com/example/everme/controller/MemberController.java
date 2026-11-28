package com.example.everme.controller;

import com.example.everme.dto.*;
import com.example.everme.entity.Member;
import com.example.everme.service.JwtService;
import com.example.everme.service.MemberService;

import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(value = "/api/member", produces = "application/json")
@Slf4j
public class MemberController {

    private final MemberService memberService;
    private final JwtService jwtService;

    public MemberController(MemberService memberService, JwtService jwtService) {
        this.memberService = memberService;
        this.jwtService = jwtService;
    }

    /** FR-001,008: 회원가입. */
    @PostMapping("/signup")
    public ResponseEntity<Map<String, Object>> signup(@RequestBody SignupRequest request) {
        Member member = new Member();
        member.setEmail(request.getEmail());
        member.setPassword(request.getPassword());
        member.setBirthDate(request.getBirthDate());
        member.setName(request.getName());
        member.setGender(request.getGender());
        member.setNationality(request.getNationality());
        member.setPhone(request.getPhone());

        try {
            memberService.signup(member);
        } catch (DuplicateKeyException exception) {
            return ResponseEntity.status(409)
                    .body(Map.<String, Object>of("message", "이미 가입된 이메일입니다. 로그인해 주세요."));
        }

        Member dbMember = memberService.getMember(member.getEmail());

        String token = jwtService.createToken(dbMember);  
        Map<String, Object> map = new HashMap<>();
        map.put("token", token);
        return ResponseEntity.status(201).body(map);
    }

    /** FR-002: 로그인. */
    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@RequestBody LoginRequest request) {
        Member member = new Member();
        member.setEmail(request.getEmail());
        member.setPassword(request.getPassword());
        String result = memberService.login(member);

        if (!"success".equals(result)) {
            return ResponseEntity.status(401)
                    .body(Map.<String, Object>of("message", "이메일 또는 비밀번호가 올바르지 않습니다."));
        }
        String token = jwtService.createToken(member);
        return ResponseEntity.ok(Map.<String, Object>of("token", token));
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
    public ResponseEntity<?> socialCallback(@PathVariable("provider") String provider,
            @RequestParam("code") String code, @RequestParam("state") String state) {
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
