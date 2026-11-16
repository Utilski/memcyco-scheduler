package com.memcyco.scheduler.scheduling;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.quartz.CalendarIntervalTrigger;
import org.quartz.CronTrigger;
import org.quartz.SimpleTrigger;
import org.quartz.Trigger;

class QuartzTriggerFactoryTests {
  private final QuartzTriggerFactory factory = new QuartzTriggerFactory();
  private final Instant now = Instant.parse("2026-01-01T00:00:00Z");

  private SchedulingRequest request(
      ScheduleType type,
      String start,
      String zone,
      Map<String, Object> config,
      Instant end,
      boolean enabled) {
    return new SchedulingRequest(
        "Test",
        null,
        TaskType.LOG,
        Map.of("message", "hello"),
        type,
        zone,
        LocalDateTime.parse(start),
        config,
        end,
        null,
        enabled);
  }

  private Trigger trigger(SchedulingRequest request) {
    return factory.create(UUID.randomUUID(), request, now);
  }

  @Test
  void oneTimeIsInclusiveOverdueAndDisabled() {
    SchedulingRequest request =
        request(ScheduleType.ONE_TIME, "2025-01-01T09:00", "UTC", Map.of(), null, true);
    SimpleTrigger trigger = assertInstanceOf(SimpleTrigger.class, trigger(request));
    assertEquals(now, trigger.getStartTime().toInstant());
    assertEquals(0, trigger.getRepeatCount());
    assertNull(
        factory.firstFireTime(
            request(ScheduleType.ONE_TIME, "2026-01-01T09:00", "UTC", Map.of(), null, false), now));
  }

  @Test
  void elapsedIntervalsUseNativeSimpleTriggersAndStayAnchored() {
    SimpleTrigger trigger =
        assertInstanceOf(
            SimpleTrigger.class,
            trigger(
                request(
                    ScheduleType.SIMPLE_INTERVAL,
                    "2025-12-31T23:58",
                    "UTC",
                    Map.of("interval", 5, "unit", "MINUTES"),
                    null,
                    true)));
    assertEquals(Instant.parse("2026-01-01T00:03:00Z"), trigger.getStartTime().toInstant());
    assertEquals(300000, trigger.getRepeatInterval());
  }

  @Test
  void calendarIntervalsPreserveHourAndSkipDstGap() {
    CalendarIntervalTrigger trigger =
        assertInstanceOf(
            CalendarIntervalTrigger.class,
            trigger(
                request(
                    ScheduleType.CALENDAR_INTERVAL,
                    "2026-03-07T02:30",
                    "America/New_York",
                    Map.of("interval", 1, "unit", "DAY"),
                    null,
                    true)));
    assertTrue(trigger.isPreserveHourOfDayAcrossDaylightSavings());
    assertTrue(trigger.isSkipDayIfHourDoesNotExist());
    assertEquals(
        Instant.parse("2026-03-09T06:30:00Z"),
        trigger.getFireTimeAfter(Date.from(Instant.parse("2026-03-08T00:00:00Z"))).toInstant());
  }

  @Test
  void monthAndYearRecurrencesAreNativeQuartzCalendarTriggers() {
    for (String unit : new String[] {"MONTH", "YEAR", "WEEK"}) {
      CalendarIntervalTrigger trigger =
          assertInstanceOf(
              CalendarIntervalTrigger.class,
              trigger(
                  request(
                      ScheduleType.CALENDAR_INTERVAL,
                      "2026-01-31T09:00",
                      "UTC",
                      Map.of("interval", 2, "unit", unit),
                      null,
                      true)));
      assertEquals(unit, trigger.getRepeatIntervalUnit().name());
      assertEquals(2, trigger.getRepeatInterval());
    }
  }

  @Test
  void quartzCronAcceptsYearFieldAndUsesTimeZone() {
    CronTrigger trigger =
        assertInstanceOf(
            CronTrigger.class,
            trigger(
                request(
                    ScheduleType.CRON,
                    "2026-01-01T00:00",
                    "Asia/Jerusalem",
                    Map.of("expression", "0 0 9 ? * MON-FRI 2026"),
                    null,
                    true)));
    assertEquals(Instant.parse("2026-01-01T07:00:00Z"), trigger.getStartTime().toInstant());
    assertEquals("Asia/Jerusalem", trigger.getTimeZone().getID());
  }

  @Test
  void endBoundsAndImpossibleRulesHaveNoNextRun() {
    assertNull(
        trigger(
            request(
                ScheduleType.CRON,
                "2026-01-01T00:00",
                "UTC",
                Map.of("expression", "0 0 9 * * ?"),
                now,
                true)));
    assertNull(
        trigger(
            request(
                ScheduleType.CRON,
                "2026-01-01T00:00",
                "UTC",
                Map.of("expression", "0 0 0 31 FEB ?"),
                null,
                true)));
  }
}
