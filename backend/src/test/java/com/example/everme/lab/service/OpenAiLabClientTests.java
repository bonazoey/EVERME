package com.example.everme.lab.service;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.*;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.*;
import java.net.ConnectException;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import javax.net.ssl.SSLHandshakeException;
import org.junit.jupiter.api.*;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

class OpenAiLabClientTests {
  private final ObjectMapper json = new ObjectMapper();
  private HttpServer server;
  private OpenAiLabClient client;
  private final List<Map<String, Object>> requests = new ArrayList<>();
  private final AtomicInteger calls = new AtomicInteger();
  private int[] statuses = {200};
  private String failure = "{\"error\":{\"code\":\"rate_limit_exceeded\"}}";
  private String retryAfter = "0.001";
  private String responsesSuccess =
      "{\"status\":\"completed\",\"usage\":{\"input_tokens\":10,\"output_tokens\":4,\"input_tokens_details\":{\"cached_tokens\":6}},\"output\":[{\"content\":[{\"type\":\"output_text\",\"text\":\"{\\\"ok\\\":true}\"}]}]}";
  private String success =
      "{\"id\":\"chat-test\",\"model\":\"gpt-6.1-sol\",\"usage\":{\"prompt_tokens\":10,\"completion_tokens\":4,\"total_tokens\":14,\"prompt_tokens_details\":{\"cached_tokens\":6}},\"choices\":[{\"index\":0,\"finish_reason\":\"stop\",\"message\":{\"role\":\"assistant\",\"content\":\"{\\\"ok\\\":true}\"}}]}";

  @BeforeEach
  void setup() throws Exception {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/v1/chat/completions",
        exchange -> {
          requests.add(body(exchange));
          int i = calls.getAndIncrement();
          int status = statuses[Math.min(i, statuses.length - 1)];
          byte[] bytes = (status == 200 ? success : failure).getBytes(StandardCharsets.UTF_8);
          exchange.getResponseHeaders().set("Content-Type", "application/json");
          exchange.getResponseHeaders().set("Retry-After", retryAfter);
          exchange.sendResponseHeaders(status, bytes.length);
          exchange.getResponseBody().write(bytes);
          exchange.close();
        });
    server.start();
    client =
        OpenAiLabClient.standalone(
            json,
            "test-key",
            "http://127.0.0.1:" + server.getAddress().getPort() + "/v1",
            "gpt-6.1-sol",
            "gpt-6.1-sol",
            "gpt-6.1-terra",
            "text-embedding-3-small");
  }

  @AfterEach
  void close() {
    server.stop(0);
  }

  // 가짜 서버가 받은 요청 본문을 Map으로 읽는다.
  private Map<String, Object> body(HttpExchange exchange) throws IOException {
    return LabJson.map(json.readValue(exchange.getRequestBody(), Object.class));
  }

  private static Map<String, Object> message(Map<String, Object> request, int index) {
    return LabJson.map(LabJson.list(request.get("messages")).get(index));
  }

  @Test
  void providerErrorsLogDetailsAndRequestIdWithoutLeakingKey() {
    Logger logger =
        (Logger) LoggerFactory.getLogger(LabAiTransport.class);
    ListAppender<ILoggingEvent> appender =
        new ListAppender<ILoggingEvent>();
    appender.start();
    logger.addAppender(appender);
    server.createContext(
        "/v1/error-log",
        exchange -> {
          byte[] bytes = failure.getBytes(StandardCharsets.UTF_8);
          exchange.getResponseHeaders().set("x-request-id", "req-provider-test");
          exchange.sendResponseHeaders(400, bytes.length);
          exchange.getResponseBody().write(bytes);
          exchange.close();
        });
    LabAiTransport transport =
        new LabAiTransport(
            json, "test-key", "http://127.0.0.1:" + server.getAddress().getPort() + "/v1");
    try {
      failure =
          "{\"error\":{\"code\":\"rate_limit_exceeded\",\"type\":\"tokens\",\"message\":\"Limit"
              + " hit\\n"
              + "key=test-key\"}}";
      assertThrows(LabException.class, () -> transport.request("/error-log", null));
      String logged =
          appender.list.stream()
              .map(ILoggingEvent::getFormattedMessage)
              .filter(m -> m.startsWith("lab_http_error"))
              .findFirst()
              .orElseThrow();
      assertTrue(logged.contains("providerRequestId=req-provider-test"));
      assertTrue(logged.contains("errorCode=rate_limit_exceeded"));
      assertTrue(logged.contains("errorType=tokens"));
      assertTrue(logged.contains("errorMessage=Limit hit key=[REDACTED]"));
      assertFalse(logged.contains("test-key"));
      assertFalse(logged.contains("\n"));
      failure = "not JSON";
      assertThrows(LabException.class, () -> transport.request("/error-log", null));
      assertTrue(
          appender.list.stream()
              .anyMatch(e -> e.getFormattedMessage().contains("errorMessage=unavailable")));
    } finally {
      logger.detachAppender(appender);
      appender.stop();
    }
  }

  private Map<String, Object> schema() {
    return new LabSchemas().object(Map.of("ok", Map.of("type", "boolean")));
  }

  private Map<String, Object> input(String text) {
    Map<String, Object> input = LabJson.object();
    input.put("catalog", Map.of("definition", "fixed reference"));
    input.put("text", text);
    return input;
  }

  @Test
  void boundedRetriesKeepPayloadAndReportTokens() {
    statuses = new int[] {429, 500, 502, 503, 200};
    LabAi.Result result = client.structured("generation", "fixed rules", input("first"), schema());
    assertEquals(5, calls.get());
    assertEquals(4, result.retries());
    assertEquals(6, result.cachedTokens());
    for (Map<String, Object> request : requests) assertEquals(requests.getFirst(), request);
    assertEquals("low", LabJson.text(requests.getFirst().get("reasoning_effort")));
  }

  @Test
  void fixedPrefixPrecedesVariableInputAndEffortCanBeOmitted() {
    client.structured("analysis", "fixed rules", input("first"), schema());
    client.structured("analysis", "fixed rules", input("second"), schema());
    Map<String, Object> a = requests.get(0);
    Map<String, Object> b = requests.get(1);
    assertEquals(message(a, 0), message(b, 0));
    assertEquals("system", LabJson.text(message(a, 0).get("role")));
    assertTrue(LabJson.text(message(a, 0).get("content")).contains("fixed reference"));
    assertFalse(LabJson.text(message(a, 1).get("content")).contains("fixed reference"));
    assertEquals("medium", LabJson.text(a.get("reasoning_effort")));
    client.configureEfforts("", "", "", "");
    client.structured("generation", "rules", input("third"), schema());
    assertFalse(requests.get(2).containsKey("reasoning_effort"));
  }

  @Test
  void permanentHttpErrorsDoNotRetry() {
    for (int status : List.of(400, 401, 403)) {
      statuses = new int[] {status};
      int before = calls.get();
      assertThrows(
          LabException.class,
          () -> client.structured("analysis", "rules", input("test"), schema()));
      assertEquals(before + 1, calls.get());
    }
    statuses = new int[] {429};
    for (String error :
        List.of(
            "{\"code\":\"insufficient_quota\"}",
            "{\"code\":\"credit_balance_exhausted\",\"type\":\"insufficient_quota\"}",
            "{\"code\":\"new_billing_error\",\"type\":\"insufficient_quota\"}")) {
      failure = "{\"error\":" + error + "}";
      int before = calls.get();
      assertThrows(
          LabException.class,
          () -> client.structured("analysis", "rules", input("test"), schema()));
      assertEquals(before + 1, calls.get());
    }
  }

  @Test
  void invalidJsonAndInvalidStructuredOutputDoNotRetry() {
    success = "not JSON";
    assertThrows(
        LabException.class, () -> client.structured("analysis", "rules", input("test"), schema()));
    assertEquals(1, calls.get());
    success =
        "{\"choices\":[{\"index\":0,\"finish_reason\":\"stop\",\"message\":{\"role\":\"assistant\",\"content\":\"invalid\"}}]}";
    assertThrows(
        LabException.class, () -> client.structured("analysis", "rules", input("test"), schema()));
    assertEquals(2, calls.get());
  }

  @Test
  void retriesStopAtFourAndCancellationStopsWait() {
    statuses = new int[] {503};
    assertThrows(
        LabException.class, () -> client.structured("analysis", "rules", input("test"), schema()));
    assertEquals(5, calls.get());
    retryAfter = "60";
    long start = System.nanoTime();
    try (LabCallContext context = new LabCallContext(() -> false)) {
      assertThrows(
          LabException.class,
          () -> client.structured("analysis", "rules", input("test"), schema()));
    }
    assertTrue(System.nanoTime() - start < 1_000_000_000);
    assertEquals(6, calls.get());
  }

  @Test
  void retryAfterAcceptsSecondsAndDates() {
    assertEquals(2500, OpenAiLabClient.retryAfter("2.5"));
    assertEquals(0, OpenAiLabClient.retryAfter("invalid"));
    String future =
        ZonedDateTime.now(ZoneOffset.UTC)
            .plusSeconds(5)
            .format(DateTimeFormatter.RFC_1123_DATE_TIME);
    assertTrue(OpenAiLabClient.retryAfter(future) > 3000);
  }

  @Test
  void transientConnectionsAndTimeoutsAreRetryableButTlsIsNot() {
    assertTrue(OpenAiLabClient.retryableConnection(new ConnectException()));
    assertTrue(
        OpenAiLabClient.retryableConnection(new HttpTimeoutException("timeout")));
    assertFalse(
        OpenAiLabClient.retryableConnection(
            new SSLHandshakeException("invalid certificate")));
  }

  @Test
  void nativeChatUsesStrictSchemaWithoutSamplingDefaults() {
    LabAi.Result result = client.structured("summary", "rules", input("test"), schema());
    Map<String, Object> request = requests.getFirst();
    assertTrue(LabJson.bool(result.data().get("ok")));
    assertEquals("gpt-6.1-sol", LabJson.text(request.get("model")));
    assertEquals("json_schema", LabJson.text(LabJson.path(request, "response_format", "type")));
    assertTrue(
        LabJson.bool(LabJson.path(request, "response_format", "json_schema", "strict")));
    assertEquals(schema(), LabJson.path(request, "response_format", "json_schema", "schema"));
    assertFalse(LabJson.bool(request.get("store")));
    assertFalse(request.containsKey("temperature"));
    assertFalse(request.containsKey("top_p"));
    assertFalse(request.containsKey("tools"));
  }

  @Test
  void responsesAdapterPreservesReasoningAndFunctionCallReplay() throws Exception {
    List<Map<String, Object>> wire = new ArrayList<Map<String, Object>>();
    server.createContext(
        "/v1/responses",
        exchange -> {
          wire.add(body(exchange));
          byte[] bytes = responsesSuccess.getBytes(StandardCharsets.UTF_8);
          exchange.getResponseHeaders().set("Content-Type", "application/json");
          exchange.sendResponseHeaders(200, bytes.length);
          exchange.getResponseBody().write(bytes);
          exchange.close();
        });
    Map<String, Object> raw = LabJson.object();
    raw.put("status", "completed");
    List<Object> output = LabJson.childList(raw, "output");
    Map<String, Object> reasoning = LabJson.object();
    reasoning.put("type", "reasoning");
    reasoning.put("id", "rs-1");
    reasoning.put("encrypted_content", "opaque-state");
    reasoning.put("summary", LabJson.array());
    output.add(reasoning);
    Map<String, Object> call = LabJson.object();
    call.put("type", "function_call");
    call.put("call_id", "call-1");
    call.put("name", "searchLongTermMemory");
    call.put("arguments", "{\"query\":\"running\"}");
    output.add(call);
    responsesSuccess = json.writeValueAsString(raw);
    List<Object> input = LabJson.array();
    input.add(Map.of("role", "user", "content", "question"));
    List<Map<String, Object>> tools =
        List.of(
            new LabSchemas()
                .tool("searchLongTermMemory", "search", Map.of("query", new LabSchemas().string())));
    Map<String, Object> response = client.respond("rules", input, schema(), tools);
    assertEquals(raw, response);
    for (Object item : LabJson.list(response.get("output"))) input.add(item);
    input.add(Map.of("type", "function_call_output", "call_id", "call-1", "output", "[]"));
    responsesSuccess =
        "{\"status\":\"completed\",\"output\":[{\"content\":[{\"type\":\"output_text\",\"text\":\"{\\\"ok\\\":true}\"}]}]}";
    assertTrue(
        LabJson.bool(
            OpenAiLabClient.output(json, client.respond("rules", input, schema(), List.of()))
                .get("ok")));
    assertEquals(input, wire.get(1).get("input"));
    assertEquals(
        "opaque-state",
        LabJson.text(
            LabJson.path(LabJson.list(wire.get(1).get("input")).get(1), "encrypted_content")));
    assertFalse(wire.getFirst().containsKey("reasoning"));
    assertFalse(LabJson.bool(wire.getFirst().get("parallel_tool_calls")));
    assertEquals(tools.getFirst(), LabJson.list(wire.getFirst().get("tools")).get(0));
  }

  @Test
  void springEmbeddingModelUses1536DimensionsAndValidatesResults() {
    List<Map<String, Object>> wire = new ArrayList<Map<String, Object>>();
    Map<String, Object> embeddingResponse = LabJson.object();
    embeddingResponse.put("model", "text-embedding-3-small");
    List<Object> data = LabJson.childList(embeddingResponse, "data");
    for (int index = 0; index < 2; index++) {
      Map<String, Object> item = LabJson.object();
      item.put("index", index);
      List<Object> vector = LabJson.childList(item, "embedding");
      for (int n = 0; n < 1536; n++) vector.add(index + 0.5);
      data.add(item);
    }
    server.createContext(
        "/v1/embeddings",
        exchange -> {
          wire.add(body(exchange));
          byte[] bytes = json.writeValueAsBytes(embeddingResponse);
          exchange.getResponseHeaders().set("Content-Type", "application/json");
          exchange.sendResponseHeaders(200, bytes.length);
          exchange.getResponseBody().write(bytes);
          exchange.close();
        });
    List<float[]> vectors = client.embed(List.of("one", "two"));
    assertEquals(2, vectors.size());
    assertEquals(1536, vectors.getFirst().length);
    assertEquals(0.5f, vectors.get(0)[0]);
    assertEquals(1.5f, vectors.get(1)[0]);
    assertEquals(1536, LabJson.integer(wire.getFirst().get("dimensions")));
    assertEquals("text-embedding-3-small", LabJson.text(wire.getFirst().get("model")));
    LabJson.list(LabJson.path(data.get(0), "embedding")).remove(0);
    assertThrows(LabException.class, () -> client.embed(List.of("one", "two")));
    assertEquals(2, wire.size());
    assertTrue(client.embed(List.of()).isEmpty());
    assertEquals(2, wire.size());
  }

  @Test
  void refusalAndTruncationDoNotRetry() {
    success =
        "{\"choices\":[{\"index\":0,\"finish_reason\":\"length\",\"message\":{\"role\":\"assistant\",\"content\":\"{}\"}}]}";
    assertThrows(
        LabException.class, () -> client.structured("analysis", "rules", input("test"), schema()));
    assertEquals(1, calls.get());
    success =
        "{\"choices\":[{\"index\":0,\"finish_reason\":\"stop\",\"message\":{\"role\":\"assistant\",\"refusal\":\"no\"}}]}";
    assertThrows(
        LabException.class, () -> client.structured("analysis", "rules", input("test"), schema()));
    assertEquals(2, calls.get());
  }

  @Test
  void simpleLoggerAdvisorLogsBothChatClients() {
    Logger logger =
        (Logger)
            LoggerFactory.getLogger(
                SimpleLoggerAdvisor.class);
    Level previous = logger.getLevel();
    ListAppender<ILoggingEvent> events =
        new ListAppender<ILoggingEvent>();
    events.start();
    logger.addAppender(events);
    logger.setLevel(Level.DEBUG);
    server.createContext(
        "/v1/responses",
        exchange -> {
          byte[] bytes = responsesSuccess.getBytes(StandardCharsets.UTF_8);
          exchange.getResponseHeaders().set("Content-Type", "application/json");
          exchange.sendResponseHeaders(200, bytes.length);
          exchange.getResponseBody().write(bytes);
          exchange.close();
        });
    try {
      client.structured("generation", "advisor rules", input("advisor-test"), schema());
      List<Object> input = LabJson.array();
      input.add(Map.of("role", "user", "content", "advisor-test"));
      client.respond("advisor rules", input, schema(), List.of());
      List<String> messages =
          events.list.stream()
              .map(ILoggingEvent::getFormattedMessage)
              .toList();
      assertEquals(2, messages.stream().filter(message -> message.startsWith("request:")).count());
      assertEquals(2, messages.stream().filter(message -> message.startsWith("response:")).count());
      assertTrue(
          messages.stream().anyMatch(message -> message.contains("\"labStage\" : \"generation\"")));
      assertTrue(
          messages.stream().anyMatch(message -> message.contains("\"labStage\" : \"answer\"")));
      String request =
          messages.stream()
              .filter(message -> message.startsWith("request:"))
              .findFirst()
              .orElseThrow();
      assertTrue(request.contains("\n--- SYSTEM ---\nadvisor rules"));
      assertTrue(request.contains("\n--- USER ---\n{\n"));
      assertTrue(request.contains("\"text\" : \"advisor-test\""));
      assertTrue(request.contains("\n--- MODEL OPTIONS ---\n{\n"));
      assertFalse(request.contains("ChatClientRequest["));
      assertTrue(
          messages.stream()
              .filter(message -> message.startsWith("response:"))
              .allMatch(message -> message.contains("\n--- ASSISTANT ---\n{\n  \"ok\" : true\n}")));
      assertTrue(messages.stream().anyMatch(message -> message.contains("advisor-test")));
      assertTrue(messages.stream().noneMatch(message -> message.contains("test-key")));
    } finally {
      logger.detachAppender(events);
      events.stop();
      logger.setLevel(previous);
    }
  }
}
