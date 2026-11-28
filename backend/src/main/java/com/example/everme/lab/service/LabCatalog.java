package com.example.everme.lab.service;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.core.io.ClassPathResource;

import com.fasterxml.jackson.databind.ObjectMapper;

// Spring Bean은 LabConfiguration.labCatalog가 load(json)로 만든다.
public class LabCatalog {

  // ##### 필드 #####
  private final Map<String, Object> document;
  private final Map<String, Map<String, Object>> elements = new LinkedHashMap<>();
  private final Map<String, List<String>> domains = new LinkedHashMap<>();

  // ##### 생성자 #####
  // 카탈로그 구조를 검증하고 code → 요소, 영역 → 코드 목록 색인을 만든다. 테스트도 이 생성자를 쓴다.
  LabCatalog(Map<String, Object> source) {
    document = LabJson.copy(source);
    if (!"element-cap-v1".equals(LabJson.text(document.get("updatePolicyVersion")))) {
      throw new IllegalStateException("Invalid update policy version");
    }
    for (Object domainValue : LabJson.list(document.get("domains"))) {
      Map<String, Object> domain = LabJson.map(domainValue);
      ArrayList<String> codes = new ArrayList<String>();
      for (Object elementValue : LabJson.list(domain.get("elements"))) {
        Map<String, Object> element = LabJson.map(elementValue);
        String code = LabJson.text(element.get("code"));
        Map<String, Object> policy = LabJson.map(element.get("updatePolicy"));
        if (!code.equals(
                LabJson.text(domain.get("code")) + "." + LabJson.text(element.get("column")))
            || !LabJson.isIntegral(policy.get("maxWeight"))
            || !Set.of(10, 20, 40).contains(LabJson.integer(policy.get("maxWeight")))
            || LabJson.text(policy.get("rationale")).isBlank()) {
          throw new IllegalStateException("Invalid update policy: " + code);
        }
        if (elements.put(code, element) != null) {
          throw new IllegalStateException("Duplicate element");
        }
        codes.add(code);
      }
      if (codes.size() != 20) {
        throw new IllegalStateException("20 elements required per domain");
      }
      if (domains.put(LabJson.text(domain.get("code")), List.copyOf(codes)) != null) {
        throw new IllegalStateException("Duplicate domain");
      }
    }
    if (domains.size() != 7 || elements.size() != 140) {
      throw new IllegalStateException("Invalid catalog");
    }
  }

  // ##### classpath clone-lab/catalog.json을 읽어 카탈로그를 만드는 메소드 #####
  @SuppressWarnings("unchecked")
  public static LabCatalog load(ObjectMapper json) throws IOException {
    try (InputStream in = new ClassPathResource("clone-lab/catalog.json").getInputStream()) {
      return new LabCatalog(json.readValue(in, Map.class));
    }
  }

  public Map<String, Object> document() {
    return LabJson.copy(document);
  }

  public Map<String, Object> analysisDocument() {
    Map<String, Object> result = LabJson.object();
    result.put("anchorRule", "각 요소의 anchorRef가 가리키는 anchorDefinitions의 점수 해석을 적용한다.");
    Map<String, Object> anchorDefinitions = LabJson.child(result, "anchorDefinitions");
    Map<Object, String> anchorRefs = new LinkedHashMap<>();
    result.put(
        "defaultBoundary",
        "요소 이름에 직접 해당하는 근거만 사용한다. 영역의 exclude를 이 점수로 대신 측정하지 않는다. 요소에 boundary 또는 counterExample이"
            + " 없으면 영역의 공통 규칙을 적용한다.");
    List<Object> list = LabJson.childList(result, "domains");
    for (Object domainValue : LabJson.list(document.get("domains"))) {
      Map<String, Object> domain = LabJson.map(domainValue);
      Map<String, Object> target = LabJson.object();
      list.add(target);
      for (String field : List.of("code", "label", "definition", "exclude")) {
        target.put(field, LabJson.copy(domain.get(field)));
      }
      List<Object> domainElements = LabJson.list(domain.get("elements"));
      Map<String, Object> first = LabJson.map(domainElements.isEmpty() ? null : domainElements.get(0));
      String commonCounter =
          LabJson.text(first.get("counterExample")).replace(LabJson.text(first.get("label")), "{요소}");
      target.put("counterExample", commonCounter);
      List<Object> items = LabJson.childList(target, "elements");
      for (Object elementValue : domainElements) {
        Map<String, Object> element = LabJson.map(elementValue);
        Map<String, Object> item = LabJson.object();
        items.add(item);
        for (String field : List.of("code", "label", "positiveExample")) {
          item.put(field, LabJson.copy(element.get(field)));
        }
        Object anchors = element.get("anchors");
        String anchorRef = anchorRefs.get(anchors);
        if (anchorRef == null) {
          anchorRef = "scale_" + (anchorRefs.size() + 1);
          anchorRefs.put(anchors, anchorRef);
          anchorDefinitions.put(anchorRef, LabJson.copy(anchors));
        }
        item.put("anchorRef", anchorRef);
        String label = LabJson.text(element.get("label"));
        if (!LabJson.text(element.get("boundary")).startsWith(label + "에 직접 해당하는 근거만")) {
          item.put("boundary", LabJson.copy(element.get("boundary")));
        }
        if (!LabJson.text(element.get("counterExample"))
            .replace(label, "{요소}")
            .equals(commonCounter)) {
          item.put("counterExample", LabJson.copy(element.get("counterExample")));
        }
      }
    }
    return result;
  }

  public Map<String, List<String>> domains() {
    return Collections.unmodifiableMap(domains);
  }

  public Set<String> codes() {
    return Collections.unmodifiableSet(elements.keySet());
  }

  public Map<String, Object> element(String code) {
    if (!elements.containsKey(code)) {
      throw LabException.bad("알 수 없는 분석 요소입니다.");
    }
    return LabJson.copy(elements.get(code));
  }

  public String column(String code) {
    return LabJson.text(element(code).get("column"));
  }

  public int maxWeight(String code) {
    return LabJson.integer(LabJson.path(element(code), "updatePolicy", "maxWeight"));
  }

  public String policyVersion() {
    return LabJson.text(document.get("updatePolicyVersion"));
  }

  public void requireDomain(String code) {
    if (!domains.containsKey(code)) {
      throw LabException.bad("알 수 없는 분석 영역입니다.");
    }
  }
}
