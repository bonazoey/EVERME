package com.example.everme.lab.service;

import java.util.function.BooleanSupplier;

/** Lets a pipeline stop retry waits without cancelling an HTTP request already in flight. */
final class LabCallContext implements AutoCloseable {
  // ##### 필드 #####
  private static final ThreadLocal<BooleanSupplier> CURRENT = new ThreadLocal<>();
  private static final ThreadLocal<Runnable> RETRY = new ThreadLocal<>();
  private final BooleanSupplier previous = CURRENT.get();
  private final Runnable previousRetry = RETRY.get();

  // ##### 생성자 #####
  LabCallContext(BooleanSupplier running) {
    this(running, () -> {});
  }

  LabCallContext(BooleanSupplier running, Runnable retry) {
    CURRENT.set(running);
    RETRY.set(retry);
  }

  static boolean retryAllowed() {
    return CURRENT.get() == null || CURRENT.get().getAsBoolean();
  }

  static void retrying() {
    if (RETRY.get() != null) {
      RETRY.get().run();
    }
  }

  @Override
  public void close() {
    if (previous == null) {
      CURRENT.remove();
    } else {
      CURRENT.set(previous);
    }
    if (previousRetry == null) {
      RETRY.remove();
    } else {
      RETRY.set(previousRetry);
    }
  }
}
