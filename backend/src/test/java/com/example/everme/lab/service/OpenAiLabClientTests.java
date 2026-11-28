package com.example.everme.lab.service;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;

class OpenAiLabClientTests {
  private final ObjectMapper json = new ObjectMapper();
  private HttpServer server;
  private OpenAiLabClient client;
  private final List<JsonNode> requests = new ArrayList<>();
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
          requests.add(json.readTree(exchange.getRequestBody()));
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
        new OpenAiLabClient(
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

  @Test
  void providerErrorsLogDetailsAndRequestIdWithoutLeakingKey() {
    var logger =
        (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(LabAiTransport.class);
    var appender =
        new ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>();
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
    var transport =
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
              .map(ch.qos.logback.classic.spi.ILoggingEvent::getFormattedMessage)
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

  private JsonNode schema() {
    return new LabSchemas(json)
        .object(Map.of("ok", json.createObjectNode().put("type", "boolean")));
  }

  private ObjectNode input(String text) {
    var input = json.createObjectNode();
    input.set("catalog", json.createObjectNode().put("definition", "fixed reference"));
    input.put("text", text);
    return input;
  }

  @Test
  void boundedRetriesKeepPayloadAndReportTokens() {
    statuses = new int[] {429, 500, 502, 503, 200};
    var result = client.structured("generation", "fixed rules", input("first"), schema());
    assertEquals(5, calls.get());
    assertEquals(4, result.retries());
    assertEquals(6, result.cachedTokens());
    for (var request : requests) assertEquals(requests.getFirst(), request);
    assertEquals("low", requests.getFirst().path("reasoning_effort").asText());
  }

  @Test
  void fixedPrefixPrecedesVariableInputAndEffortCanBeOmitted() {
    client.structured("analysis", "fixed rules", input("first"), schema());
    client.structured("analysis", "fixed rules", input("second"), schema());
    var a = requests.get(0);
    var b = requests.get(1);
    assertEquals(a.path("messages").get(0), b.path("messages").get(0));
    assertEquals("system", a.path("messages").get(0).path("role").asText());
    assertTrue(a.path("messages").get(0).path("content").asText().contains("fixed reference"));
    assertFalse(a.path("messages").get(1).path("content").asText().contains("fixed reference"));
    assertEquals("medium", a.path("reasoning_effort").asText());
    client.configureEfforts("", "", "", "");
    client.structured("generation", "rules", input("third"), schema());
    assertFalse(requests.get(2).has("reasoning_effort"));
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
    try (var context = new LabCallContext(() -> false)) {
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
        java.time.ZonedDateTime.now(java.time.ZoneOffset.UTC)
            .plusSeconds(5)
            .format(java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME);
    assertTrue(OpenAiLabClient.retryAfter(future) > 3000);
  }

  @Test
  void transientConnectionsAndTimeoutsAreRetryableButTlsIsNot() {
    assertTrue(OpenAiLabClient.retryableConnection(new java.net.ConnectException()));
    assertTrue(
        OpenAiLabClient.retryableConnection(new java.net.http.HttpTimeoutException("timeout")));
    assertFalse(
        OpenAiLabClient.retryableConnection(
            new javax.net.ssl.SSLHandshakeException("invalid certificate")));
  }

  @Test
  void nativeChatUsesStrictSchemaWithoutSamplingDefaults() {
    var result = client.structured("summary", "rules", input("test"), schema());
    var request = requests.getFirst();
    assertTrue(result.data().path("ok").asBoolean());
    assertEquals("gpt-6.1-sol", request.path("model").asText());
    assertEquals("json_schema", request.path("response_format").path("type").asText());
    assertTrue(request.path("response_format").path("json_schema").path("strict").asBoolean());
    assertEquals(schema(), request.path("response_format").path("json_schema").path("schema"));
    assertFalse(request.path("store").asBoolean());
    assertFalse(request.has("temperature"));
    assertFalse(request.has("top_p"));
    assertFalse(request.has("tools"));
  }

  @Test
  void responsesAdapterPreservesReasoningAndFunctionCallReplay() throws Exception {
    var wire = new ArrayList<JsonNode>();
    server.createContext(
        "/v1/responses",
        exchange -> {
          wire.add(json.readTree(exchange.getRequestBody()));
          byte[] bytes = responsesSuccess.getBytes(StandardCharsets.UTF_8);
          exchange.getResponseHeaders().set("Content-Type", "application/json");
          exchange.sendResponseHeaders(200, bytes.length);
          exchange.getResponseBody().write(bytes);
          exchange.close();
        });
    var raw = json.createObjectNode().put("status", "completed");
    var output = raw.putArray("output");
    output
        .addObject()
        .put("type", "reasoning")
        .put("id", "rs-1")
        .put("encrypted_content", "opaque-state")
        .putArray("summary");
    output
        .addObject()
        .put("type", "function_call")
        .put("call_id", "call-1")
        .put("name", "searchLongTermMemory")
        .put("arguments", "{\"query\":\"running\"}");
    responsesSuccess = raw.toString();
    var input = json.createArrayNode();
    input.addObject().put("role", "user").put("content", "question");
    var tools =
        List.of(
            new LabSchemas(json)
                .tool(
                    "searchLongTermMemory",
                    "search",
                    Map.of("query", new LabSchemas(json).string())));
    var response = client.respond("rules", input, schema(), tools);
    assertEquals(raw, response);
    for (var item : response.path("output")) input.add(item);
    input
        .addObject()
        .put("type", "function_call_output")
        .put("call_id", "call-1")
        .put("output", "[]");
    responsesSuccess =
        "{\"status\":\"completed\",\"output\":[{\"content\":[{\"type\":\"output_text\",\"text\":\"{\\\"ok\\\":true}\"}]}]}";
    assertTrue(
        OpenAiLabClient.output(json, client.respond("rules", input, schema(), List.of()))
            .path("ok")
            .asBoolean());
    assertEquals(input, wire.get(1).path("input"));
    assertEquals(
        "opaque-state", wire.get(1).path("input").get(1).path("encrypted_content").asText());
    assertFalse(wire.getFirst().has("reasoning"));
    assertFalse(wire.getFirst().path("parallel_tool_calls").asBoolean());
    assertEquals(tools.getFirst(), wire.getFirst().path("tools").get(0));
  }

  @Test
  void springEmbeddingModelUses1536DimensionsAndValidatesResults() {
    var wire = new ArrayList<JsonNode>();
    var embeddingResponse = json.createObjectNode().put("model", "text-embedding-3-small");
    var data = embeddingResponse.putArray("data");
    for (int index = 0; index < 2; index++) {
      var item = data.addObject().put("index", index);
      var vector = item.putArray("embedding");
      for (int n = 0; n < 1536; n++) vector.add(index + 0.5);
    }
    server.createContext(
        "/v1/embeddings",
        exchange -> {
          wire.add(json.readTree(exchange.getRequestBody()));
          byte[] bytes = embeddingResponse.toString().getBytes(StandardCharsets.UTF_8);
          exchange.getResponseHeaders().set("Content-Type", "application/json");
          exchange.sendResponseHeaders(200, bytes.length);
          exchange.getResponseBody().write(bytes);
          exchange.close();
        });
    var vectors = client.embed(List.of("one", "two"));
    assertEquals(2, vectors.size());
    assertEquals(1536, vectors.getFirst().length);
    assertEquals(0.5f, vectors.get(0)[0]);
    assertEquals(1.5f, vectors.get(1)[0]);
    assertEquals(1536, wire.getFirst().path("dimensions").asInt());
    assertEquals("text-embedding-3-small", wire.getFirst().path("model").asText());
    ((ArrayNode) data.get(0).path("embedding")).remove(0);
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
    var logger =
        (ch.qos.logback.classic.Logger)
            org.slf4j.LoggerFactory.getLogger(
                org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor.class);
    var previous = logger.getLevel();
    var events =
        new ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>();
    events.start();
    logger.addAppender(events);
    logger.setLevel(ch.qos.logback.classic.Level.DEBUG);
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
      var input = json.createArrayNode();
      input.addObject().put("role", "user").put("content", "advisor-test");
      client.respond("advisor rules", input, schema(), List.of());
      var messages =
          events.list.stream()
              .map(ch.qos.logback.classic.spi.ILoggingEvent::getFormattedMessage)
              .toList();
      assertEquals(2, messages.stream().filter(message -> message.startsWith("request:")).count());
      assertEquals(2, messages.stream().filter(message -> message.startsWith("response:")).count());
      assertTrue(
          messages.stream().anyMatch(message -> message.contains("\"labStage\" : \"generation\"")));
      assertTrue(
          messages.stream().anyMatch(message -> message.contains("\"labStage\" : \"answer\"")));
      var request =
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
