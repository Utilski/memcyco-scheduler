package com.memcyco.scheduler.scheduling.execution;

import java.time.Duration;

/** One retry policy shared by persisted attempt state and schedule failure handling. */
public final class ExecutionRetryPolicy {
  private static final int MAX_ATTEMPTS = 3;

  private ExecutionRetryPolicy() {}

  public static boolean isExhausted(int attempts) {
    return attempts >= MAX_ATTEMPTS;
  }

  public static Duration delayAfter(int failedAttempts) {
    return switch (failedAttempts) {
      case 1 -> Duration.ofSeconds(5);
      case 2 -> Duration.ofSeconds(30);
      default -> throw new IllegalArgumentException("No retry delay for attempt " + failedAttempts);
    };
  }
}
