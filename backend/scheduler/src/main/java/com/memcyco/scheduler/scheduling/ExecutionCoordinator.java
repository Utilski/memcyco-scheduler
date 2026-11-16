package com.memcyco.scheduler.scheduling;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Service
public class ExecutionCoordinator {
  private static final Logger LOGGER = LoggerFactory.getLogger(ExecutionCoordinator.class);
  private static final TypeReference<Map<String, Object>> PAYLOAD_TYPE = new TypeReference<>() {};

  private final SchedulingRepository schedulingRepository;
  private final ExecutionStore executionStore;
  private final ObjectMapper objectMapper;
  private final TaskHandlers taskHandlers;
  private final Clock clock;

  public ExecutionCoordinator(
      SchedulingRepository schedulingRepository,
      ExecutionStore executionStore,
      ObjectMapper objectMapper,
      TaskHandlers taskHandlers,
      Clock clock) {
    this.schedulingRepository = schedulingRepository;
    this.executionStore = executionStore;
    this.objectMapper = objectMapper;
    this.taskHandlers = taskHandlers;
    this.clock = clock;
  }

  @Transactional
  public void dispatch(UUID id, long generation, Instant scheduledAt, Instant nextFireAt) {
    Scheduling scheduling = schedulingRepository.findForUpdate(id).orElse(null);
    if (scheduling == null
        || !scheduling.isEnabled()
        || scheduling.getGeneration() != generation
        || scheduling.isCompleted()
        || executionStore.hasOutstanding(id)) {
      return;
    }
    if (scheduling.getEndsAt() != null && scheduledAt.isAfter(scheduling.getEndsAt())) {
      return;
    }
    if (executionStore.occurrenceExists(id, generation, scheduledAt)) {
      return;
    }
    executionStore.insert(
        scheduling,
        scheduledAt,
        objectMapper.writeValueAsString(scheduling.getPayload()),
        clock.instant());
    scheduling.setDispatchedCount(scheduling.getDispatchedCount() + 1);
    scheduling.setNextRunAt(scheduling.isCompleted() ? null : nextFireAt);
    scheduling.setUpdatedAt(clock.instant());
    executionStore.requestSync(id, clock.instant());
  }

  @Transactional
  public void execute(UUID executionId) {
    UUID schedulingId = executionStore.schedulingId(executionId);
    if (schedulingId == null) {
      return;
    }
    // Edits and workers always lock schedule before execution.
    Scheduling scheduling = schedulingRepository.findForUpdate(schedulingId).orElse(null);
    ExecutionStore.Execution execution = executionStore.lockExecution(executionId);
    if (execution == null || !execution.isReady(clock.instant())) {
      return;
    }
    if (scheduling == null
        || !scheduling.isEnabled()
        || scheduling.getGeneration() != execution.generation()) {
      executionStore.cancelPending(schedulingId, clock.instant());
      return;
    }
    int attempts = execution.attempts() + 1;
    try {
      taskHandlers.execute(execution, objectMapper.readValue(execution.payload(), PAYLOAD_TYPE));
    } catch (RuntimeException exception) {
      recordFailure(scheduling, execution, attempts, exception);
      return;
    }
    executionStore.succeed(executionId, attempts, clock.instant());
    executionStore.requestSync(schedulingId, clock.instant());
  }

  private void recordFailure(
      Scheduling scheduling,
      ExecutionStore.Execution execution,
      int attempts,
      RuntimeException exception) {
    LOGGER.warn("Handler failed for execution={} attempt={}", execution.id(), attempts, exception);
    executionStore.fail(execution.id(), attempts, exception.toString(), clock.instant());
    if (ExecutionRetryPolicy.isExhausted(attempts)) {
      scheduling.setEnabled(false);
      scheduling.setNextRunAt(null);
      scheduling.setUpdatedAt(clock.instant());
      executionStore.requestSync(scheduling.getId(), clock.instant());
    }
  }
}
