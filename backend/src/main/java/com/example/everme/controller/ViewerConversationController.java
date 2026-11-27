package com.example.everme.controller;

import com.example.everme.dto.*;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/** ViewerConversation 기능별 엔드포인트 골격. 업무 로직과 계층 주입은 후속 구현한다. */
@RestController
@RequestMapping(value = "/api/viewer/conversations", produces = "application/json")
public class ViewerConversationController {

    /** FR-070: 본인 대변인 대화 조회. 현재 클론 권한도 재검증 예정. */
    @GetMapping("")
    public ResponseEntity<?> list(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size, @RequestParam(required = false) UUID cloneId) {
        return null;
    }

    /** FR-058,070: 대상 cloneId로 대변인 세션 생성. 현재 권한 검사 후 kind=spokesperson 저장 예정. */
    @PostMapping("")
    public ResponseEntity<?> create(@RequestBody ViewerConversationRequest request) {
        return null;
    }

    /** FR-070: 본인 대변인 대화 조회. 현재 클론 권한도 재검증 예정. */
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

    /** FR-058,060~068,070: 대변인 질문 저장·RAG·응답 생성. 권한 검색·노출 검사·근거 저장은 후속 서비스 내부 작업. */
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

    /** FR-027; NFR-013: 메시지 논리 비활성화. */
    @DeleteMapping("/{conversationId}/messages/{messageId}")
    public ResponseEntity<?> deleteMessage(@PathVariable("conversationId") UUID conversationId, @PathVariable("messageId") UUID messageId) {
        return null;
    }

    /** FR-029,070: 대변인 세션 요약. 현재 권한으로 조회 범위를 검사 예정. */
    @GetMapping("/{conversationId}/summary")
    public ResponseEntity<?> summary(@PathVariable("conversationId") UUID conversationId) {
        return null;
    }

    /** FR-064,069; NFR-023: 응답 지식 버전·출처 조회. 원문은 현재 권한 검사와 마스킹 후 제공 예정. */
    @GetMapping("/{conversationId}/messages/{messageId}/evidence")
    public ResponseEntity<?> evidence(@PathVariable("conversationId") UUID conversationId, @PathVariable("messageId") UUID messageId) {
        return null;
    }

    /** FR-059: 대변인 질문 음성 인식 요청. */
    @PostMapping(value = "/{conversationId}/speech-recognitions", consumes = "multipart/form-data")
    public ResponseEntity<?> recognize(@PathVariable("conversationId") UUID conversationId, @RequestParam("file") MultipartFile file) {
        return null;
    }

    /** FR-059: 현재 권한 내 검증된 대변인 답변 음성 조회. */
    @GetMapping("/{conversationId}/messages/{messageId}/audio")
    public ResponseEntity<?> audio(@PathVariable("conversationId") UUID conversationId, @PathVariable("messageId") UUID messageId) {
        return null;
    }
}
