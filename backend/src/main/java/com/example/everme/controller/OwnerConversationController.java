package com.example.everme.controller;

import com.example.everme.dto.EndpointRequests.*;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/** OwnerConversation 기능별 엔드포인트 골격. 업무 로직과 계층 주입은 후속 구현한다. */
@RestController
@RequestMapping(value = "/api/owner/conversations", produces = "application/json")
public class OwnerConversationController {

    /** FR-023: 본인 사용자 대화 조회. */
    @GetMapping("")
    public ResponseEntity<?> list(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return null;
    }

    /** FR-022: 본인 대화 생성. 서버가 kind=personal과 인증 참여자를 결정한다. */
    @PostMapping("")
    public ResponseEntity<?> create() {
        return null;
    }

    /** FR-023: 본인 사용자 대화 조회. */
    @GetMapping("/{conversationId}")
    public ResponseEntity<?> detail(@PathVariable("conversationId") UUID conversationId) {
        return null;
    }

    /** FR-022; 화면: 대화 제목 변경. */
    @PatchMapping("/{conversationId}")
    public ResponseEntity<?> rename(@PathVariable("conversationId") UUID conversationId, @RequestBody TitleRequest request) {
        return null;
    }

    /** FR-027; NFR-013: 세션 논리 비활성화. */
    @DeleteMapping("/{conversationId}")
    public ResponseEntity<?> deactivate(@PathVariable("conversationId") UUID conversationId) {
        return null;
    }

    /** FR-009,014~016,021: 사용자 메시지 저장·AI 대화. 정보 추출·ETL 예약은 후속 서비스 내부 작업. */
    @PostMapping("/{conversationId}/messages")
    public ResponseEntity<?> send(@PathVariable("conversationId") UUID conversationId, @RequestBody MessageRequest request) {
        return null;
    }

    /** FR-013: 응답 스트림 구독 (향후 SSE). */
    @GetMapping("/{conversationId}/messages/{messageId}/events")
    public ResponseEntity<?> events(@PathVariable("conversationId") UUID conversationId, @PathVariable("messageId") UUID messageId) {
        return null;
    }

    /** FR-018: 응답 생성·음성 출력 중단. */
    @PostMapping("/{conversationId}/messages/{messageId}/stop")
    public ResponseEntity<?> stop(@PathVariable("conversationId") UUID conversationId, @PathVariable("messageId") UUID messageId) {
        return null;
    }

    /** FR-026: 자신의 원문 발언 정정. */
    @PatchMapping("/{conversationId}/messages/{messageId}")
    public ResponseEntity<?> correct(@PathVariable("conversationId") UUID conversationId, @PathVariable("messageId") UUID messageId, @RequestBody MessageRequest request) {
        return null;
    }

    /** FR-027; NFR-013: 메시지 논리 비활성화. */
    @DeleteMapping("/{conversationId}/messages/{messageId}")
    public ResponseEntity<?> deleteMessage(@PathVariable("conversationId") UUID conversationId, @PathVariable("messageId") UUID messageId) {
        return null;
    }

    /** FR-029: 세션 요약. */
    @GetMapping("/{conversationId}/summary")
    public ResponseEntity<?> summary(@PathVariable("conversationId") UUID conversationId) {
        return null;
    }

    /** FR-010,012,020: 사용자 음성 인식 요청. */
    @PostMapping(value = "/{conversationId}/speech-recognitions", consumes = "multipart/form-data")
    public ResponseEntity<?> recognize(@PathVariable("conversationId") UUID conversationId, @RequestParam("file") MultipartFile file) {
        return null;
    }

    /** FR-011: 사용자 대화 AI 응답 음성 조회. */
    @GetMapping("/{conversationId}/messages/{messageId}/audio")
    public ResponseEntity<?> audio(@PathVariable("conversationId") UUID conversationId, @PathVariable("messageId") UUID messageId) {
        return null;
    }

    /** FR-017: 대화 주제 추천. */
    @GetMapping("/topics")
    public ResponseEntity<?> topics() {
        return null;
    }
}
