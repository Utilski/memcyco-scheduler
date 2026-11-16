package com.memcyco.scheduler.scheduling;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.TimeZone;
import java.util.UUID;
import org.quartz.CalendarIntervalScheduleBuilder;
import org.quartz.CronScheduleBuilder;
import org.quartz.DateBuilder.IntervalUnit;
import org.quartz.JobKey;
import org.quartz.ScheduleBuilder;
import org.quartz.SimpleScheduleBuilder;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.quartz.TriggerKey;
import org.springframework.stereotype.Component;

/** Converts the API contract to native Quartz triggers; contains no recurrence arithmetic. */
@Component
public class QuartzTriggerFactory {
  public static final String GROUP = "schedulings";
  private static final Instant LAST_SUPPORTED_INSTANT = Instant.parse("9999-12-31T23:59:59.999Z");

  public JobKey jobKey(UUID id) {
    return new JobKey(id.toString(), GROUP);
  }

  public TriggerKey triggerKey(UUID id) {
    return new TriggerKey(id.toString(), GROUP);
  }

  public Trigger create(UUID id, SchedulingRequest request, Instant now) {
    ZoneId timeZone = ZoneId.of(request.timeZone());
    Instant startsAt = request.startsAt().atZone(timeZone).toInstant();
    Instant endsAt = request.endsAt() == null ? LAST_SUPPORTED_INSTANT : request.endsAt();
    TriggerBuilder<?> builder =
        TriggerBuilder.newTrigger()
            .withIdentity(triggerKey(id))
            .forJob(jobKey(id))
            .startAt(Date.from(startsAt))
            .endAt(Date.from(endsAt))
            .withSchedule(scheduleBuilder(request, timeZone));
    Date first = firstOccurrence(request.scheduleType(), builder.build(), startsAt, now);
    if (first == null
        || first.toInstant().isAfter(endsAt)
        || first.toInstant().atZone(ZoneOffset.UTC).getYear() < 1000) {
      return null;
    }
    // Reuse the builder: cloning a CalendarIntervalTrigger drops its DST flags in Quartz.
    return builder.startAt(first).build();
  }

  public Instant firstFireTime(SchedulingRequest request, Instant now) {
    if (!request.enabled()) {
      return null;
    }
    Trigger trigger = create(new UUID(0, 0), request, now);
    return trigger == null ? null : trigger.getStartTime().toInstant();
  }

  private ScheduleBuilder<? extends Trigger> scheduleBuilder(
      SchedulingRequest request, ZoneId zone) {
    return switch (request.scheduleType()) {
      case ONE_TIME ->
          SimpleScheduleBuilder.simpleSchedule()
              .withRepeatCount(0)
              .withMisfireHandlingInstructionFireNow();
      case SIMPLE_INTERVAL -> simpleInterval(request);
      case CALENDAR_INTERVAL -> calendarInterval(request, zone);
      case CRON ->
          CronScheduleBuilder.cronSchedule((String) request.scheduleConfig().get("expression"))
              .inTimeZone(TimeZone.getTimeZone(zone))
              .withMisfireHandlingInstructionFireAndProceed();
    };
  }

  private SimpleScheduleBuilder simpleInterval(SchedulingRequest request) {
    long secondsPerUnit =
        switch ((String) request.scheduleConfig().get("unit")) {
          case "SECONDS" -> 1;
          case "MINUTES" -> 60;
          case "HOURS" -> 3600;
          default -> throw new IllegalArgumentException("Unsupported simple interval unit");
        };
    long interval = ((Number) request.scheduleConfig().get("interval")).longValue();
    return SimpleScheduleBuilder.simpleSchedule()
        .withIntervalInMilliseconds(Math.multiplyExact(interval, secondsPerUnit * 1000))
        .repeatForever()
        .withMisfireHandlingInstructionNowWithExistingCount();
  }

  private CalendarIntervalScheduleBuilder calendarInterval(SchedulingRequest request, ZoneId zone) {
    return CalendarIntervalScheduleBuilder.calendarIntervalSchedule()
        .withInterval(
            ((Number) request.scheduleConfig().get("interval")).intValue(),
            IntervalUnit.valueOf((String) request.scheduleConfig().get("unit")))
        .inTimeZone(TimeZone.getTimeZone(zone))
        .preserveHourOfDayAcrossDaylightSavings(true)
        .skipDayIfHourDoesNotExist(true)
        .withMisfireHandlingInstructionFireAndProceed();
  }

  private Date firstOccurrence(ScheduleType type, Trigger trigger, Instant startsAt, Instant now) {
    if (type == ScheduleType.ONE_TIME) {
      return Date.from(startsAt.isBefore(now) ? now : startsAt);
    }
    return trigger.getFireTimeAfter(Date.from(now.minusMillis(1)));
  }
}
