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
import com.example.everme.lab.service.LabJson;
import com.fasterxml.jackson.databind.ObjectMapper;

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

  // DB JSON 문자열을 Map·List·값으로 바꾼다. null은 null 그대로 (응답에 JSON null로 나간다).
  public Object parse(Object value) {
    if (value == null) {
      return null;
    }
    return LabJson.read(json, value.toString());
  }

  // DB JSON 문자열을 수정 가능한 Map으로 바꾼다. null이거나 객체가 아니면 빈 Map.
  public Map<String, Object> parseMap(Object value) {
    return LabJson.map(parse(value));
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
  public Map<String, Object> profile(Long user) {
    Map<String, Object> profile = LabJson.object();
    for (Entry<String, List<String>> d : catalog.domains().entrySet()) {
      Map<String, Object> row = parseMap(mapper.profile(d.getKey(), user));
      for (String code : d.getValue()) {
        Map<String, Object> point = LabJson.object();
        point.put("score", row.get(catalog.column(code)));
        Object meta = LabJson.path(row, "metadata", code);
        point.put("weight", (int) LabJson.longValue(LabJson.path(meta, "weight"), 0));
        Object sourceIds = LabJson.path(meta, "sourceIds");
        point.put("sourceIds", LabJson.isList(sourceIds) ? sourceIds : LabJson.array());
        point.put("reason", LabJson.text(LabJson.path(meta, "reason"), ""));
        profile.put(code, point);
      }
    }
    return profile;
  }

  public void updateProfile(Long user, Map<String, Object> profile) {
    for (Entry<String, List<String>> d : catalog.domains().entrySet()) {
      LinkedHashMap<String, Object> scores = new LinkedHashMap<String, Object>();
      Map<String, Object> metadata = LabJson.object();
      for (String code : d.getValue()) {
        Object point = profile.get(code);
        Object score = LabJson.path(point, "score");
        scores.put(catalog.column(code), LabJson.isNumber(score) ? LabJson.decimal(score) : null);
        metadata.put(code, point);
      }
      mapper.profileUpdate(d.getKey(), user, scores, LabJson.write(json, metadata));
    }
  }

  public static String vector(float[] vector) {
    return Arrays.toString(vector);
  }
}
