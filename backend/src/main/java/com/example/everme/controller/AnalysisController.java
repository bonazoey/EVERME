package com.example.everme.controller;

import com.example.everme.dto.*;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** Analysis 기능별 엔드포인트 골격. 업무 로직과 계층 주입은 후속 구현한다. */
@RestController
@RequestMapping(value = "/api/me/analysis", produces = "application/json")
public class AnalysisController {

    /** FR-030~043: 분석 정보 목록. */
    @GetMapping("")
    public ResponseEntity<?> analysis(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return null;
    }

    /** FR-039,042,043: 분석 정보 상세·출처·변경 이력. */
    @GetMapping("/{informationId}")
    public ResponseEntity<?> analysisDetail(@PathVariable("informationId") UUID informationId) {
        return null;
    }
}
