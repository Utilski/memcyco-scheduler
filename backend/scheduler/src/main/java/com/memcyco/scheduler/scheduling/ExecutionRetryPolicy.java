package com.memcyco.scheduler.scheduling;

import java.time.Duration;

/** One retry policy shared by persisted attempt state and schedule failure handling. */
final class ExecutionRetryPolicy {
  private static final int MAX_ATTEMPTS = 3;

  private ExecutionRetryPolicy() {}

  static boolean isExhausted(int attempts) {
    return attempts >= MAX_ATTEMPTS;
  }

  static Duration delayAfter(int failedAttempts) {
    return switch (failedAttempts) {
      case 1 -> Duration.ofSeconds(5);
      case 2 -> Duration.ofSeconds(30);
      default -> throw new IllegalArgumentException("No retry delay for attempt " + failedAttempts);
    };
  }
}
