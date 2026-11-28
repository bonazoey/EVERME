package com.example.everme.lab.service;

import java.util.List;
import java.util.Map;

/**
 * 서비스가 의존하는 LLM 기능 인터페이스. 구현은 OpenAiLabClient, 테스트는 가짜 구현을 쓴다.
 *
 * <p>structured: JSON 스키마 응답, respond: 도구 호출 응답(원본), embed: 임베딩, models: 설정 조회.
 * JSON은 모두 Map(객체)·List(배열)로 주고받는다.
 */
public interface LabAi {
  // 구조화 호출 한 번의 결과와 사용량 (LabService.metrics로 응답에 포함)
  record Result(
      Map<String, Object> data,
      String model,
      long inputTokens,
      long outputTokens,
      long elapsedMs,
      long cachedTokens,
      int retries) {
    public Result(
        Map<String, Object> data,
        String model,
        long inputTokens,
        long outputTokens,
        long elapsedMs) {
      this(data, model, inputTokens, outputTokens, elapsedMs, 0, 0);
    }
  }

  Result structured(
      String stage, String instructions, Map<String, Object> input, Map<String, Object> schema);

  Map<String, Object> respond(
      String instructions,
      List<Object> input,
      Map<String, Object> schema,
      List<Map<String, Object>> tools);

  List<float[]> embed(List<String> texts);

  Map<String, Object> models();
}
