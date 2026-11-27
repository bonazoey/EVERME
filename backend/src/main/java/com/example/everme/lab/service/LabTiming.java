package com.example.everme.lab.service;

import java.util.UUID;

import org.slf4j.MDC;

import lombok.extern.slf4j.Slf4j;

/** Safe phase timing: never records prompts, response bodies, topics, or credentials. */
@Slf4j
final class LabTiming implements AutoCloseable {

  // ##### 필드 #####
  private final String operation;
  private final String requestId;
  private final String previousId;
  private final long start = System.nanoTime();
  private long last = start;
  private boolean successful;

  // ##### 생성자 #####
  LabTiming(String operation) {
    this.operation = operation;
    previousId = MDC.get("labRequestId");
    requestId = previousId == null ? UUID.randomUUID().toString() : previousId;
    MDC.put("labRequestId", requestId);
    log.info(
        "lab_timing requestId={} runId={} segment={} operation={} event=start",
        requestId,
        MDC.get("labRunId"),
        MDC.get("labSegment"),
        operation);
  }

  void mark(String phase) {
    long now = System.nanoTime();
    log.info(
        "lab_timing requestId={} runId={} segment={} operation={} phase={} elapsedMs={} totalMs={}",
        requestId,
        MDC.get("labRunId"),
        MDC.get("labSegment"),
        operation,
        phase,
        (now - last) / 1_000_000,
        (now - start) / 1_000_000);
    last = now;
  }

  void success() {
    successful = true;
  }

  @Override
  public void close() {
    log.info(
        "lab_timing requestId={} operation={} event=end outcome={} totalMs={}",
        requestId,
        operation,
        successful ? "success" : "failure",
        (System.nanoTime() - start) / 1_000_000);
    if (previousId == null) {
      MDC.remove("labRequestId");
    } else {
      MDC.put("labRequestId", previousId);
    }
  }
}
