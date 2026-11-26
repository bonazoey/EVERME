package com.example.everme.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** ConsentDocument 기능별 엔드포인트 골격. 업무 로직과 계층 주입은 후속 구현한다. */
@RestController
@RequestMapping(value = "/api/consent-documents", produces = "application/json")
public class ConsentDocumentController {

    /** FR-008: 약관·동의 문서 및 버전 조회. */
    @GetMapping("")
    public ResponseEntity<?> documents() {
        return null;
    }
}

