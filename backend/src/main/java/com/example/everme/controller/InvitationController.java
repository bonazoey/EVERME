package com.example.everme.controller;

import com.example.everme.dto.EndpointRequests.*;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** Invitation 기능별 엔드포인트 골격. 업무 로직과 계층 주입은 후속 구현한다. */
@RestController
@RequestMapping(value = "/api", produces = "application/json")
public class InvitationController {

    /** FR-071,072: 초대를 통한 정보이용자 등록. */
    @PostMapping("/me/invitations")
    public ResponseEntity<?> invite(@RequestBody InvitationRequest request) {
        return null;
    }

    /** FR-073: 받은 초대 목록. */
    @GetMapping("/viewer/invitations")
    public ResponseEntity<?> invitations(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return null;
    }

    /** FR-073: 초대 수락 또는 거절. */
    @PatchMapping("/viewer/invitations/{invitationId}")
    public ResponseEntity<?> respond(@PathVariable("invitationId") UUID invitationId, @RequestBody InvitationResponseRequest request) {
        return null;
    }
}

