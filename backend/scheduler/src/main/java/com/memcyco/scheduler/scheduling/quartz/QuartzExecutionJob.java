package com.memcyco.scheduler.scheduling.quartz;

import com.memcyco.scheduler.scheduling.execution.ExecutionCoordinator;
import java.time.Instant;
import java.util.UUID;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.Scheduler;
import org.springframework.scheduling.quartz.QuartzJobBean;

@DisallowConcurrentExecution
public class QuartzExecutionJob extends QuartzJobBean {
  public static final String SCHEDULING_ID = "schedulingId";
  public static final String GENERATION = "generation";
  private final ExecutionCoordinator coordinator;

  public QuartzExecutionJob(ExecutionCoordinator coordinator) {
    this.coordinator = coordinator;
  }

  @Override
  protected void executeInternal(JobExecutionContext context) throws JobExecutionException {
    try {
      coordinator.dispatch(
          UUID.fromString(context.getMergedJobDataMap().getString(SCHEDULING_ID)),
          context.getMergedJobDataMap().getLong(GENERATION),
          scheduledFireTime(context),
          context.getNextFireTime() == null ? null : context.getNextFireTime().toInstant());
    } catch (RuntimeException exception) {
      // Refire the same occurrence after infrastructure failure; never advance its count.
      try {
        Thread.sleep(1000);
      } catch (InterruptedException interrupted) {
        Thread.currentThread().interrupt();
      }
      throw new JobExecutionException(exception, true);
    }
  }

  private Instant scheduledFireTime(JobExecutionContext context) {
    if (!context.isRecovering()) {
      return context.getScheduledFireTime().toInstant();
    }
    String originalTime =
        context
            .getMergedJobDataMap()
            .getString(Scheduler.FAILED_JOB_ORIGINAL_TRIGGER_SCHEDULED_FIRETIME_IN_MILLISECONDS);
    return Instant.ofEpochMilli(Long.parseLong(originalTime));
  }
}
