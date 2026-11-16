package com.memcyco.scheduler.scheduling.quartz;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.memcyco.scheduler.scheduling.execution.ExecutionCoordinator;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;
import org.quartz.Scheduler;

class QuartzExecutionJobTests {
  @Test
  void recoveredJobRetainsOriginalOccurrenceIdentity() throws Exception {
    UUID schedulingId = UUID.randomUUID();
    Instant originalFireTime = Instant.parse("2026-01-01T09:00:00Z");
    JobDataMap data = new JobDataMap();
    data.put("schedulingId", schedulingId.toString());
    data.put("generation", 4L);
    data.put(
        Scheduler.FAILED_JOB_ORIGINAL_TRIGGER_SCHEDULED_FIRETIME_IN_MILLISECONDS,
        Long.toString(originalFireTime.toEpochMilli()));
    JobExecutionContext context = mock(JobExecutionContext.class);
    when(context.getMergedJobDataMap()).thenReturn(data);
    when(context.isRecovering()).thenReturn(true);
    ExecutionCoordinator coordinator = mock(ExecutionCoordinator.class);
    QuartzExecutionJob job = new QuartzExecutionJob(coordinator);
    job.executeInternal(context);
    verify(coordinator).dispatch(schedulingId, 4L, originalFireTime, null);
  }
}
