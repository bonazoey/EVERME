package com.example.everme.lab.service;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Clone Lab의 JSON 값 도우미. JSON 객체는 Map, 배열은 List, 나머지는 String·Number·Boolean·null로 다룬다.
 *
 * <p>JsonNode의 path(...).asText()처럼 "없거나 형이 다르면 기본값"으로 읽는 동작을 같은 의미로 제공한다.
 * 문자열 변환은 반드시 write(...)를 쓴다. Map.toString()은 JSON이 아니다.
 */
public final class LabJson {
  private LabJson() {}

  // ##### 새 객체·배열을 만드는 메소드 #####
  public static Map<String, Object> object() {
    return new LinkedHashMap<String, Object>();
  }

  public static List<Object> array() {
    return new ArrayList<Object>();
  }

  // ##### 값을 객체·배열로 보는 메소드 #####
  // 객체가 아니면 새 빈 Map을 돌려준다(읽기 전용으로 쓸 때). 부모에 붙여 써야 하면 child()를 쓴다.
  @SuppressWarnings("unchecked")
  public static Map<String, Object> map(Object value) {
    return value instanceof Map ? (Map<String, Object>) value : new LinkedHashMap<String, Object>();
  }

  @SuppressWarnings("unchecked")
  public static List<Object> list(Object value) {
    return value instanceof List ? (List<Object>) value : new ArrayList<Object>();
  }

  // ##### 중첩 경로의 값을 읽는 메소드 #####
  // 중간에 객체가 아니거나 키가 없으면 null.
  public static Object path(Object node, String... keys) {
    Object current = node;
    for (String key : keys) {
      if (!(current instanceof Map<?, ?> map)) {
        return null;
      }
      current = map.get(key);
    }
    return current;
  }

  // ##### 부모 객체의 자식 객체·배열을 꺼내거나 새로 붙이는 메소드 #####
  // JsonNode의 withObject / putObject(없을 때) / withArray와 같은 용도.
  public static Map<String, Object> child(Map<String, Object> parent, String key) {
    Object value = parent.get(key);
    if (value instanceof Map) {
      return map(value);
    }
    Map<String, Object> created = object();
    parent.put(key, created);
    return created;
  }

  public static List<Object> childList(Map<String, Object> parent, String key) {
    Object value = parent.get(key);
    if (value instanceof List) {
      return list(value);
    }
    List<Object> created = array();
    parent.put(key, created);
    return created;
  }

  // ##### 형 검사 메소드 #####
  public static boolean isMap(Object value) {
    return value instanceof Map;
  }

  public static boolean isList(Object value) {
    return value instanceof List;
  }

  public static boolean isNumber(Object value) {
    return value instanceof Number;
  }

  public static boolean isText(Object value) {
    return value instanceof String;
  }

  public static boolean isIntegral(Object value) {
    return value instanceof Integer
        || value instanceof Long
        || value instanceof Short
        || value instanceof Byte
        || value instanceof BigInteger;
  }

  // ##### 값을 기본 타입으로 읽는 메소드 (asText / asInt / asLong / asBoolean 대용) #####
  public static String text(Object value) {
    return text(value, "");
  }

  public static String text(Object value, String fallback) {
    if (value == null || value instanceof Map || value instanceof List) {
      return fallback;
    }
    return value.toString();
  }

  public static int integer(Object value) {
    return (int) longValue(value, 0);
  }

  public static long longValue(Object value) {
    return longValue(value, 0);
  }

  public static long longValue(Object value, long fallback) {
    if (value instanceof Number number) {
      return number.longValue();
    }
    if (value instanceof Boolean flag) {
      return flag ? 1 : 0;
    }
    if (value instanceof String text) {
      try {
        return Long.parseLong(text.strip());
      } catch (NumberFormatException ignored) {
        try {
          return (long) Double.parseDouble(text.strip());
        } catch (NumberFormatException alsoIgnored) {
          return fallback;
        }
      }
    }
    return fallback;
  }

  public static boolean bool(Object value) {
    if (value instanceof Boolean flag) {
      return flag;
    }
    if (value instanceof Number number) {
      return number.doubleValue() != 0;
    }
    return value instanceof String text && "true".equalsIgnoreCase(text.strip());
  }

  // 숫자를 BigDecimal로. 실수는 BigDecimal.valueOf(double)로 바꿔 JsonNode.decimalValue()와 같은 값을 낸다.
  public static BigDecimal decimal(Object value) {
    if (value instanceof BigDecimal decimal) {
      return decimal;
    }
    if (value instanceof BigInteger integer) {
      return new BigDecimal(integer);
    }
    if (value instanceof Double || value instanceof Float) {
      return BigDecimal.valueOf(((Number) value).doubleValue());
    }
    if (value instanceof Number number) {
      return BigDecimal.valueOf(number.longValue());
    }
    return BigDecimal.ZERO;
  }

  // ##### 깊은 복사 메소드 #####
  // Map·List만 새로 만들고 문자열·숫자는 불변이라 그대로 둔다.
  @SuppressWarnings("unchecked")
  public static <T> T copy(T value) {
    if (value instanceof Map<?, ?> map) {
      Map<String, Object> result = object();
      map.forEach((key, item) -> result.put(String.valueOf(key), copy(item)));
      return (T) result;
    }
    if (value instanceof List<?> items) {
      List<Object> result = array();
      items.forEach(item -> result.add(copy(item)));
      return (T) result;
    }
    return value;
  }

  // ##### JSON 문자열과 변환하는 메소드 #####
  public static String write(ObjectMapper json, Object value) {
    try {
      return json.writeValueAsString(value);
    } catch (Exception failure) {
      throw new IllegalStateException("Invalid lab JSON", failure);
    }
  }

  // 결과는 Map·List·String·Number·Boolean 또는 null.
  public static Object read(ObjectMapper json, String text) {
    try {
      return json.readValue(text, Object.class);
    } catch (Exception failure) {
      throw new IllegalStateException("Invalid lab JSON", failure);
    }
  }

  // 객체가 아니면 빈 Map.
  public static Map<String, Object> readMap(ObjectMapper json, String text) {
    return map(read(json, text));
  }

  // DB 행·DTO 등 임의 객체를 Map/List 구조로 바꾼다 (JsonNode.valueToTree 대용).
  public static Object convert(ObjectMapper json, Object value) {
    return json.convertValue(value, Object.class);
  }
}
