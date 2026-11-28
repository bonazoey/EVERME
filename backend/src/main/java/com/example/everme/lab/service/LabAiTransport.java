package com.example.everme.lab.service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.net.http.HttpClient;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLException;

import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;

/** Shared, interruptible HTTP retry policy for Spring AI and Responses compatibility. */
@Slf4j
public final class LabAiTransport {

  // ##### 필드 #####
  private final ObjectMapper json;
  private final String key;
  private final String base;
  private final RestClient client;
  private final ThreadLocal<Integer> retries = ThreadLocal.withInitial(() -> 0);

  // ##### 생성자 #####
  public LabAiTransport(ObjectMapper json, String key, String base) {
    this.json = json;
    this.key = key;
    this.base = base;
    client =
        builder()
            .baseUrl(base.replaceAll("/+$", ""))
            .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + key)
            .build();
  }

  public RestClient.Builder builder() {
    HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();
    JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(http);
    factory.setReadTimeout(Duration.ofMinutes(5));
    return RestClient.builder()
        .requestFactory(factory)
        .requestInterceptor(this::exchange)
        .defaultStatusHandler(
            HttpStatusCode::isError,
            (request, response) -> {
              throw new LabException(
                  response.getStatusCode().value() == 429 ? 429 : 502,
                  "OpenAI 요청 실패 (HTTP "
                      + response.getStatusCode().value()
                      + "). 설정과 모델 접근 권한을 확인해 주세요.");
            });
  }

  // ##### 모델 요청을 처리하는 메소드 #####
  // 요청 본문 Map은 RestClient의 Jackson 변환기가 직렬화하고, 응답 JSON 객체는 Map으로 받는다.
  @SuppressWarnings("unchecked")
  public Map<String, Object> request(String route, Map<String, Object> body) {
    return body == null
        ? client.get().uri(route).retrieve().body(Map.class)
        : client
            .post()
            .uri(route)
            .contentType(MediaType.APPLICATION_JSON)
            .body(body)
            .retrieve()
            .body(Map.class);
  }

  public void begin() {
    retries.set(0);
  }

  public String baseUrl() {
    return base;
  }

  public int retries() {
    return retries.get();
  }

  public void end() {
    retries.remove();
  }

  private ClientHttpResponse exchange(
      HttpRequest request, byte[] body, ClientHttpRequestExecution execution) throws IOException {
    if (key.isBlank()) {
      throw new LabException(503, "OPENAI_API_KEY를 설정해 주세요.");
    }
    try (LabTiming timing = new LabTiming("http" + request.getURI().getPath())) {
      for (int attempt = 0; ; attempt++) {
        BufferedResponse response;
        try (ClientHttpResponse raw = execution.execute(request, body)) {
          response =
              new BufferedResponse(
                  raw.getStatusCode(),
                  raw.getStatusText(),
                  raw.getHeaders(),
                  raw.getBody().readAllBytes());
        } catch (IOException failure) {
          if (!retryableConnection(failure) || attempt >= 4) {
            throw failure;
          }
          waitRetry(attempt, 0, request.getURI().getPath());
          continue;
        }
        int status = response.status.value();
        if (status >= 400) {
          logProviderError(request, response, attempt + 1);
        }
        boolean retry =
            status >= 500 && status <= 599 || status == 429 && !quotaError(response.bytes);
        if (retry && attempt < 4) {
          waitRetry(
              attempt,
              retryAfter(response.headers.getFirst("Retry-After")),
              request.getURI().getPath());
          continue;
        }
        log.info(
            "lab_http route={} status={} retries={}",
            request.getURI().getPath(),
            status,
            retries());
        if (status >= 200 && status < 300) {
          timing.success();
        }
        return response;
      }
    }
  }

  // ##### 재시도 전 OpenAI 오류 원인과 요청 식별자를 기록하는 메소드 #####
  private void logProviderError(HttpRequest request, BufferedResponse response, int attempt) {
    Object error = null;
    try {
      error = LabJson.path(json.readValue(response.bytes, Object.class), "error");
    } catch (IOException ignored) {
      // JSON이 아닌 오류 응답은 본문 대신 상태 코드와 요청 ID로 추적한다.
    }
    log.warn(
        "lab_http_error requestId={} runId={} segment={} route={} status={} attempt={}"
            + " providerRequestId={} errorCode={} errorType={} errorMessage={}",
        MDC.get("labRequestId"),
        MDC.get("labRunId"),
        MDC.get("labSegment"),
        request.getURI().getPath(),
        response.status.value(),
        attempt,
        logValue(response.headers.getFirst("x-request-id")),
        logValue(LabJson.text(LabJson.path(error, "code"), "unavailable")),
        logValue(LabJson.text(LabJson.path(error, "type"), "unavailable")),
        logValue(LabJson.text(LabJson.path(error, "message"), "unavailable")));
  }

  private String logValue(String value) {
    if (value == null) {
      return "unavailable";
    }
    String safe = key.isBlank() ? value : value.replace(key, "[REDACTED]");
    safe = safe.replaceAll("[\\p{Cntrl}\\u2028\\u2029]", " ");
    return safe.substring(0, Math.min(safe.length(), 2000));
  }

  private boolean quotaError(byte[] body) {
    try {
      Object error = LabJson.path(json.readValue(body, Object.class), "error");
      return "insufficient_quota".equals(LabJson.text(LabJson.path(error, "type")))
          || Set.of(
                  "insufficient_quota",
                  "credit_balance_exhausted",
                  "organization_usage_limit_exceeded",
                  "billing_hard_limit_reached",
                  "billing_not_active")
              .contains(LabJson.text(LabJson.path(error, "code")));
    } catch (IOException ignored) {
      return false;
    }
  }

  static boolean retryableConnection(IOException failure) {
    return !(failure instanceof SSLException);
  }

  static long retryAfter(String value) {
    if (value == null) {
      return 0;
    }
    try {
      return Math.max(0, (long) (Double.parseDouble(value) * 1000));
    } catch (Exception ignored) {
      try {
        return Math.max(
            0,
            ZonedDateTime.parse(
                        value, DateTimeFormatter.RFC_1123_DATE_TIME)
                    .toInstant()
                    .toEpochMilli()
                - System.currentTimeMillis());
      } catch (Exception invalid) {
        return 0;
      }
    }
  }

  private void waitRetry(int attempt, long specified, String route) throws IOException {
    long wait =
        specified > 0
            ? specified
            : (1000L << attempt) + ThreadLocalRandom.current().nextLong(251);
    log.info(
        "lab_retry runId={} segment={} route={} attempt={} waitMs={}",
        MDC.get("labRunId"),
        MDC.get("labSegment"),
        route,
        attempt + 1,
        wait);
    long end = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(wait);
    try {
      while (System.nanoTime() < end) {
        checkRetry();
        Thread.sleep(
            Math.min(
                100,
                Math.max(
                    1,
                    TimeUnit.NANOSECONDS.toMillis(end - System.nanoTime()))));
      }
      checkRetry();
      LabCallContext.retrying();
      retries.set(retries.get() + 1);
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      throw new InterruptedIOException("Retry interrupted");
    }
  }

  private void checkRetry() {
    if (!LabCallContext.retryAllowed()) {
      throw new LabException(503, "재시도 대기가 중단됐습니다.");
    }
  }

  private record BufferedResponse(
      HttpStatusCode status, String text, HttpHeaders headers, byte[] bytes)
      implements ClientHttpResponse {
    public HttpStatusCode getStatusCode() {
      return status;
    }

    public String getStatusText() {
      return text;
    }

    public HttpHeaders getHeaders() {
      return headers;
    }

    public InputStream getBody() {
      return new ByteArrayInputStream(bytes);
    }

    public void close() {}
  }
}
