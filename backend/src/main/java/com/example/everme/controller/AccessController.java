package com.example.everme.controller;

import com.example.everme.dto.EndpointRequests.*;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

// 정보이용자 관리
@RestController
@RequestMapping(value = "/api/me", produces = "application/json")
public class AccessController {

    // 정보이용자 목록 조회
    // FR-071, 074, 075
    @GetMapping("/members")
    public ResponseEntity<?> members(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return null;
    }

    // 개별 최대 접근 등급 변경
    // FR-074, 075, 077-05
    @PatchMapping("/members/{memberId}/access")
    public ResponseEntity<?> changeAccess(@PathVariable("memberId") UUID memberId, @RequestBody AccessRequest request) {
        return null;
    }

    /** FR-076: 이용 권한 철회. */
    @DeleteMapping("/members/{memberId}")
    public ResponseEntity<?> revoke(@PathVariable("memberId") UUID memberId) {
        return null;
    }

    /** FR-074-01: 그룹 조회. */
    @GetMapping("/groups")
    public ResponseEntity<?> groups(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return null;
    }

    /** FR-074-01: 그룹 생성. */
    @PostMapping("/groups")
    public ResponseEntity<?> createGroup(@RequestBody GroupRequest request) {
        return null;
    }

    /** FR-074-01: 그룹 이름 변경. */
    @PatchMapping("/groups/{groupId}")
    public ResponseEntity<?> renameGroup(@PathVariable("groupId") UUID groupId, @RequestBody GroupRequest request) {
        return null;
    }

    /** FR-074-01: 그룹 해제. */
    @DeleteMapping("/groups/{groupId}")
    public ResponseEntity<?> deleteGroup(@PathVariable("groupId") UUID groupId) {
        return null;
    }

    /** FR-074-01: 그룹 이동 또는 해제. */
    @PutMapping("/members/{memberId}/group")
    public ResponseEntity<?> moveMember(@PathVariable("memberId") UUID memberId, @RequestBody MembershipRequest request) {
        return null;
    }

    /** FR-074-01: 그룹 구성원 등급 일괄 변경. */
    @PatchMapping("/groups/{groupId}/access")
    public ResponseEntity<?> groupAccess(@PathVariable("groupId") UUID groupId, @RequestBody AccessRequest request) {
        return null;
    }

    /** FR-078: 클론 접근·이용 이력. */
    @GetMapping("/clone/access-history")
    public ResponseEntity<?> accessHistory(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return null;
    }
}

