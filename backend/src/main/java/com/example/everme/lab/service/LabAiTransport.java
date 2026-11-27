package com.example.everme.lab.service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Set;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.databind.JsonNode;
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
  public JsonNode request(String route, JsonNode body) {
    return body == null
        ? client.get().uri(route).retrieve().body(JsonNode.class)
        : client
            .post()
            .uri(route)
            .contentType(MediaType.APPLICATION_JSON)
            .body(body)
            .retrieve()
            .body(JsonNode.class);
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

  private boolean quotaError(byte[] body) {
    try {
      return Set.of("insufficient_quota", "billing_hard_limit_reached", "billing_not_active")
          .contains(json.readTree(body).path("error").path("code").asText());
    } catch (IOException ignored) {
      return false;
    }
  }

  static boolean retryableConnection(IOException failure) {
    return !(failure instanceof javax.net.ssl.SSLException);
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
            java.time.ZonedDateTime.parse(
                        value, java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME)
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
            : (1000L << attempt) + java.util.concurrent.ThreadLocalRandom.current().nextLong(251);
    log.info(
        "lab_retry runId={} segment={} route={} attempt={} waitMs={}",
        org.slf4j.MDC.get("labRunId"),
        org.slf4j.MDC.get("labSegment"),
        route,
        attempt + 1,
        wait);
    long end = System.nanoTime() + java.util.concurrent.TimeUnit.MILLISECONDS.toNanos(wait);
    try {
      while (System.nanoTime() < end) {
        checkRetry();
        Thread.sleep(
            Math.min(
                100,
                Math.max(
                    1,
                    java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(end - System.nanoTime()))));
      }
      checkRetry();
      LabCallContext.retrying();
      retries.set(retries.get() + 1);
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      throw new java.io.InterruptedIOException("Retry interrupted");
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
