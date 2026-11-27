package com.example.everme.controller;

import com.example.everme.dto.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/** Account 기능별 엔드포인트 골격. 업무 로직과 계층 주입은 후속 구현한다. */
@RestController
@RequestMapping(value = "/api/me", produces = "application/json")
public class AccountController {

    /** FR-006: 내 프로필. */
    @GetMapping("")
    public ResponseEntity<?> profile() {
        return null;
    }

    /** FR-006: 프로필 변경. */
    @PatchMapping("")
    public ResponseEntity<?> updateProfile(@RequestBody ProfileRequest request) {
        return null;
    }

    /** FR-006: 프로필 이미지 업로드. */
    @PostMapping(value = "/profile-image", consumes = "multipart/form-data")
    public ResponseEntity<?> uploadProfileImage(@RequestParam("file") MultipartFile file) {
        return null;
    }

    /** FR-007; NFR-013: 회원탈퇴 및 논리 비활성화. 동의 철회는 탈퇴 시에만 처리한다. */
    @DeleteMapping("")
    public ResponseEntity<?> withdraw() {
        return null;
    }

    /** FR-019: 대화 설정. */
    @GetMapping("/conversation-settings")
    public ResponseEntity<?> settings() {
        return null;
    }

    /** FR-019: 음성 종류 변경. */
    @PatchMapping("/conversation-settings")
    public ResponseEntity<?> updateSettings(@RequestBody SettingsRequest request) {
        return null;
    }
}

