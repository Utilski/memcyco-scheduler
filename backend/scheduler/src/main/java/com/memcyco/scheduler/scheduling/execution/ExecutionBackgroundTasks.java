package com.memcyco.scheduler.scheduling.execution;

import com.memcyco.scheduler.scheduling.persistence.ExecutionStore;
import com.memcyco.scheduler.scheduling.quartz.QuartzScheduleSynchronizer;
import java.time.Clock;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ExecutionBackgroundTasks {
  private static final Logger LOGGER = LoggerFactory.getLogger(ExecutionBackgroundTasks.class);
  private final ExecutionStore executionStore;
  private final QuartzScheduleSynchronizer synchronizer;
  private final ExecutionPublisher publisher;
  private final Clock clock;
  private final boolean schedulerEnabled;

  public ExecutionBackgroundTasks(
      ExecutionStore executionStore,
      QuartzScheduleSynchronizer synchronizer,
      ExecutionPublisher publisher,
      Clock clock,
      @Value("${app.execution.scheduler-enabled:true}") boolean schedulerEnabled) {
    this.executionStore = executionStore;
    this.synchronizer = synchronizer;
    this.publisher = publisher;
    this.clock = clock;
    this.schedulerEnabled = schedulerEnabled;
  }

  @Scheduled(fixedDelayString = "${app.execution.poll-delay-ms:1000}")
  public void synchronize() {
    if (!schedulerEnabled) {
      return;
    }
    for (UUID id : executionStore.pendingSync()) {
      try {
        synchronizer.synchronize(id);
      } catch (Exception exception) {
        LOGGER.warn("Quartz synchronization will retry for scheduling={}", id, exception);
      }
    }
  }

  @Scheduled(fixedDelayString = "${app.execution.poll-delay-ms:1000}")
  public void publish() {
    if (!schedulerEnabled) {
      return;
    }
    for (UUID id : executionStore.publishable(clock.instant())) {
      try {
        publisher.publish(id);
      } catch (InterruptedException exception) {
        Thread.currentThread().interrupt();
        return;
      } catch (Exception exception) {
        LOGGER.warn("Publication will retry for execution={}", id, exception);
        break;
      }
    }
  }
}
