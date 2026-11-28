package com.example.everme.lab.dao;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import com.example.everme.lab.service.LabCatalog;
import com.example.everme.lab.service.LabException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

@Repository
@Profile("clone-lab")
public class LabDao {
  // ##### 필드 #####
  private final LabMapper mapper;
  private final ObjectMapper json;
  private final LabCatalog catalog;

  // ##### 생성자 #####
  public LabDao(LabMapper mapper, ObjectMapper json, LabCatalog catalog) {
    this.mapper = mapper;
    this.json = json;
    this.catalog = catalog;
  }

  public LabMapper mapper() {
    return mapper;
  }

  public JsonNode parse(Object value) {
    if (value == null) {
      return json.nullNode();
    }
    try {
      return json.readTree(value.toString());
    } catch (Exception e) {
      throw new IllegalStateException("Invalid lab JSON", e);
    }
  }

  public Map<String, Object> requireUser(Long user) {
    Map<String, Object> row = mapper.user(user);
    if (row == null) {
      throw new LabException(404, "테스트 사용자를 찾을 수 없습니다.");
    }
    return row;
  }

  public Map<String, Object> requireBatch(Long user, Long batch) {
    Map<String, Object> row = mapper.batch(user, batch);
    if (row == null) {
      throw new LabException(404, "대화 배치를 찾을 수 없습니다.");
    }
    return row;
  }

  // ##### 분석 프로필을 조회하는 메소드 #####
  public ObjectNode profile(Long user) {
    ObjectNode profile = json.createObjectNode();
    for (Entry<String, List<String>> d : catalog.domains().entrySet()) {
      JsonNode row = parse(mapper.profile(d.getKey(), user));
      for (String code : d.getValue()) {
        ObjectNode point = json.createObjectNode();
        point.set("score", row.path(catalog.column(code)));
        JsonNode meta = row.path("metadata").path(code);
        point.put("weight", meta.path("weight").asInt(0));
        point.set(
            "sourceIds",
            meta.path("sourceIds").isArray() ? meta.path("sourceIds") : json.createArrayNode());
        point.put("reason", meta.path("reason").asText(""));
        profile.set(code, point);
      }
    }
    return profile;
  }

  public void updateProfile(Long user, JsonNode profile) {
    for (Entry<String, List<String>> d : catalog.domains().entrySet()) {
      LinkedHashMap<String, Object> scores = new LinkedHashMap<String, Object>();
      ObjectNode metadata = json.createObjectNode();
      for (String code : d.getValue()) {
        JsonNode point = profile.path(code);
        scores.put(
            catalog.column(code),
            point.path("score").isNumber() ? point.path("score").decimalValue() : null);
        metadata.set(code, point);
      }
      mapper.profileUpdate(d.getKey(), user, scores, metadata.toString());
    }
  }

  public static String vector(float[] vector) {
    return Arrays.toString(vector);
  }
}
