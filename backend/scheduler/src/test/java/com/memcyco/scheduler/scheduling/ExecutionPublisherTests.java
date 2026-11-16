package com.memcyco.scheduler.scheduling;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

class ExecutionPublisherTests {
  @Test
  void interruptedConfirmationPreservesInterruptAndLeavesOutboxUnpublished() {
    Instant now = Instant.parse("2026-01-01T00:00:00Z");
    UUID executionId = UUID.randomUUID();
    ExecutionStore executionStore = mock(ExecutionStore.class);
    when(executionStore.lockExecution(executionId))
        .thenReturn(
            new ExecutionStore.Execution(
                executionId,
                UUID.randomUUID(),
                0,
                now,
                TaskType.LOG,
                "{}",
                ExecutionStatus.PENDING,
                0,
                now,
                false));
    ExecutionPublisher publisher =
        new ExecutionPublisher(
            executionStore, mock(RabbitTemplate.class), Clock.fixed(now, ZoneOffset.UTC));
    try {
      Thread.currentThread().interrupt();
      assertThrows(InterruptedException.class, () -> publisher.publish(executionId));
      assertTrue(Thread.currentThread().isInterrupted());
      verify(executionStore, never()).markPublished(executionId);
    } finally {
      Thread.interrupted(); // Do not leak interrupt state into the next JUnit test.
    }
  }
}
