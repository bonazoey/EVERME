package com.example.everme.lab.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("clone-lab")
public class LabValidation {
  // ##### 필드 #####
  private final LabCatalog catalog;

  // ##### 생성자 #####
  public LabValidation(LabCatalog catalog) {
    this.catalog = catalog;
  }

  public static void text(String text, int max, String name) {
    if (text == null || text.isBlank() || text.length() > max) {
      throw LabException.bad(name + "을 확인해 주세요.");
    }
  }

  public static BigDecimal score(Object value) {
    if (!LabJson.isNumber(value)
        || LabJson.decimal(value).compareTo(BigDecimal.ZERO) < 0
        || LabJson.decimal(value).compareTo(new BigDecimal("100")) > 0
        || LabJson.decimal(value).stripTrailingZeros().scale() > 2) {
      throw LabException.invalid("점수는 소수 둘째 자리 이내의 0~100이어야 합니다.");
    }
    return LabJson.decimal(value);
  }

  private static void sources(Object ids, Set<String> allowed) {
    if (!LabJson.isList(ids) || LabJson.list(ids).isEmpty()) {
      throw LabException.invalid("근거 발언이 필요합니다.");
    }
    HashSet<String> used = new HashSet<String>();
    for (Object id : LabJson.list(ids)) {
      if (!LabJson.isText(id) || !allowed.contains(id.toString()) || !used.add(id.toString())) {
        throw LabException.invalid("유효하지 않거나 중복된 사용자 근거입니다.");
      }
    }
  }

  public void analysis(Map<String, Object> data, Set<String> allowed) {
    analysis(data, allowed, 40);
  }

  public void analysis(Map<String, Object> data, Set<String> allowed, int maxMemories) {
    if (!LabJson.isList(data.get("observations")) || !LabJson.isList(data.get("memories"))) {
      throw LabException.invalid("분석 응답 형식 오류입니다.");
    }
    List<Object> observations = LabJson.list(data.get("observations"));
    List<Object> memories = LabJson.list(data.get("memories"));
    if (observations.size() > 140 || memories.size() > maxMemories) {
      throw LabException.invalid("분석 결과가 너무 많습니다.");
    }
    HashSet<String> seen = new HashSet<String>();
    for (Object pointValue : observations) {
      Map<String, Object> point = LabJson.map(pointValue);
      String code = LabJson.text(point.get("code"));
      catalog.element(code);
      if (!seen.add(code)) {
        throw LabException.invalid("분석 요소가 중복되었습니다.");
      }
      score(point.get("score"));
      Object weight = point.get("strength");
      if (!LabJson.isIntegral(weight) || LabJson.integer(weight) < 1 || LabJson.integer(weight) > 3) {
        throw LabException.invalid("근거 강도 오류입니다.");
      }
      sources(point.get("sourceIds"), allowed);
      String reason = LabJson.text(point.get("reason"));
      if (reason.isBlank() || reason.length() > 2000) {
        throw LabException.invalid("분석 근거 설명 오류입니다.");
      }
    }
    for (Object memoryValue : memories) {
      Map<String, Object> memory = LabJson.map(memoryValue);
      if (!Set.of(
              "FACT",
              "EXPERIENCE",
              "PREFERENCE",
              "CONDITIONAL_PREFERENCE",
              "VALUE",
              "HABIT",
              "GOAL",
              "RELATIONSHIP")
          .contains(LabJson.text(memory.get("kind")))) {
        throw LabException.invalid("기억 유형 오류입니다.");
      }
      sources(memory.get("sourceIds"), allowed);
      String content = LabJson.text(memory.get("content"));
      if (content.isBlank() || content.length() > 3000) {
        throw LabException.invalid("기억 내용 오류입니다.");
      }
    }
  }

  /** Deterministic server-side accumulation, with per-element caps. */
  public Map<String, Object> merge(Map<String, Object> old, List<Object> observations) {
    Map<String, Object> next = LabJson.copy(old);
    HashSet<String> seen = new HashSet<String>();
    for (Object observationValue : observations) {
      Map<String, Object> observation = LabJson.map(observationValue);
      String code = LabJson.text(observation.get("code"));
      int cap = catalog.maxWeight(code);
      if (!seen.add(code)) {
        throw LabException.invalid("Duplicate merge element");
      }
      Map<String, Object> previous = LabJson.map(old.get(code));
      // 기존 점수가 명시적으로 null이면 "미관찰". score 키 자체가 없으면 기존처럼 검증 오류가 난다.
      boolean unobserved = previous.containsKey("score") && previous.get("score") == null;
      int weight = unobserved ? 0 : Math.min(LabJson.integer(previous.get("weight")), cap);
      int addition = LabJson.integer(observation.get("strength"));
      if (addition < 1 || addition > 3) {
        throw LabException.invalid("Invalid evidence strength");
      }
      int total = Math.addExact(weight, addition);
      BigDecimal observed = score(observation.get("score"));
      BigDecimal numerator =
          unobserved
              ? observed.multiply(BigDecimal.valueOf(addition))
              : score(previous.get("score"))
                  .multiply(BigDecimal.valueOf(weight))
                  .add(observed.multiply(BigDecimal.valueOf(addition)));
      Map<String, Object> point = LabJson.child(next, code);
      point.put("score", numerator.divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP));
      point.put("weight", Math.min(total, cap));
      point.put("sourceIds", LabJson.copy(observation.get("sourceIds")));
      point.put("reason", LabJson.text(observation.get("reason")));
    }
    return next;
  }
}
