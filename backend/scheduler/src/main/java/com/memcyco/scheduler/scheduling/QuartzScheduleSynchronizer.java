package com.memcyco.scheduler.scheduling;

import java.time.Clock;
import java.util.UUID;
import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.Trigger;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Applies durable scheduling intents to Quartz without mixing in task execution. */
@Service
public class QuartzScheduleSynchronizer {
  private final SchedulingRepository schedulingRepository;
  private final ExecutionStore executionStore;
  private final QuartzTriggerFactory triggerFactory;
  private final ObjectProvider<Scheduler> schedulerProvider;
  private final Clock clock;

  public QuartzScheduleSynchronizer(
      SchedulingRepository schedulingRepository,
      ExecutionStore executionStore,
      QuartzTriggerFactory triggerFactory,
      ObjectProvider<Scheduler> schedulerProvider,
      Clock clock) {
    this.schedulingRepository = schedulingRepository;
    this.executionStore = executionStore;
    this.triggerFactory = triggerFactory;
    this.schedulerProvider = schedulerProvider;
    this.clock = clock;
  }

  @Transactional(rollbackFor = Exception.class)
  public void synchronize(UUID schedulingId) throws SchedulerException {
    Scheduling scheduling = schedulingRepository.findForUpdate(schedulingId).orElse(null);
    String token = executionStore.lockSync(schedulingId);
    if (token == null) {
      return;
    }
    Scheduler scheduler = schedulerProvider.getObject();
    if (scheduling == null || !scheduling.isEnabled() || scheduling.isCompleted()) {
      removeJob(scheduler, schedulingId, scheduling);
    } else {
      registerCurrentGeneration(scheduler, scheduling);
      updateTriggerState(scheduler, scheduling);
    }
    executionStore.completeSync(schedulingId, token);
  }

  private void removeJob(Scheduler scheduler, UUID id, Scheduling scheduling)
      throws SchedulerException {
    scheduler.deleteJob(triggerFactory.jobKey(id));
    if (scheduling != null) {
      scheduling.setNextRunAt(null);
    }
  }

  private void registerCurrentGeneration(Scheduler scheduler, Scheduling scheduling)
      throws SchedulerException {
    UUID id = scheduling.getId();
    JobDetail existing = scheduler.getJobDetail(triggerFactory.jobKey(id));
    if (existing != null
        && existing.getJobDataMap().getLong(QuartzExecutionJob.GENERATION)
            == scheduling.getGeneration()) {
      return;
    }
    scheduler.deleteJob(triggerFactory.jobKey(id));
    Trigger trigger =
        triggerFactory.create(id, SchedulingRequest.from(scheduling), clock.instant());
    if (trigger != null) {
      scheduler.scheduleJob(jobDetail(scheduling), trigger);
    }
  }

  private JobDetail jobDetail(Scheduling scheduling) {
    return JobBuilder.newJob(QuartzExecutionJob.class)
        .withIdentity(triggerFactory.jobKey(scheduling.getId()))
        .usingJobData(QuartzExecutionJob.GENERATION, scheduling.getGeneration())
        .usingJobData(QuartzExecutionJob.SCHEDULING_ID, scheduling.getId().toString())
        .storeDurably()
        .requestRecovery()
        .build();
  }

  private void updateTriggerState(Scheduler scheduler, Scheduling scheduling)
      throws SchedulerException {
    UUID id = scheduling.getId();
    if (executionStore.hasOutstanding(id)) {
      scheduler.pauseJob(triggerFactory.jobKey(id));
    } else {
      scheduler.resumeJob(triggerFactory.jobKey(id));
    }
    Trigger trigger = scheduler.getTrigger(triggerFactory.triggerKey(id));
    scheduling.setNextRunAt(
        trigger == null || trigger.getNextFireTime() == null
            ? null
            : trigger.getNextFireTime().toInstant());
  }
}
