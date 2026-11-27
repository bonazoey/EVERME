package com.example.everme.controller;

import com.example.everme.dto.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** Clone 기능별 엔드포인트 골격. 업무 로직과 계층 주입은 후속 구현한다. */
@RestController
@RequestMapping(value = "/api", produces = "application/json")
public class CloneController {

    /** FR-048,055: 클론 요약 프로필·충분성·최근 갱신. */
    @GetMapping("/me/clone")
    public ResponseEntity<?> myClone() {
        return null;
    }

    /** FR-079: 이용 가능한 클론. */
    @GetMapping("/viewer/clones")
    public ResponseEntity<?> availableClones(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return null;
    }
}

