package com.memcyco.scheduler.scheduling;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class NextRunCalculatorTests {

  private final NextRunCalculator calculator = new NextRunCalculator();

  private SchedulingRequest rule(String start, String zone, String frequency, int interval) {
    return request(
        start,
        zone,
        ScheduleType.RECURRING,
        Map.of("frequency", frequency, "interval", interval),
        null,
        true);
  }

  private SchedulingRequest request(
      String start,
      String zone,
      ScheduleType type,
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

  private void expect(String expected, SchedulingRequest request, String now) {
    assertEquals(
        expected == null ? null : Instant.parse(expected),
        calculator.next(request, Instant.parse(now)));
  }

  @Test
  void oneTimeIsInclusiveAndOverdueRunsOnceNow() {
    SchedulingRequest r =
        request("2026-01-01T09:00", "UTC", ScheduleType.ONE_TIME, Map.of(), null, true);
    expect("2026-01-01T09:00:00Z", r, "2026-01-01T09:00:00Z");
    expect("2026-01-02T00:00:00Z", r, "2026-01-02T00:00:00Z");
    expect(
        null,
        request("2026-01-01T09:00", "UTC", ScheduleType.ONE_TIME, Map.of(), null, false),
        "2026-01-01T00:00:00Z");
  }

  @Test
  void elapsedIntervalsRoundUpAndStayAnchored() {
    expect(
        "2026-01-01T09:30:00Z",
        rule("2026-01-01T09:00", "UTC", "MINUTELY", 15),
        "2026-01-01T09:15:00.000001Z");
    expect(
        "2026-01-01T13:00:00Z",
        rule("2026-01-01T09:00", "UTC", "HOURLY", 2),
        "2026-01-01T12:00:00Z");
    expect(
        "2026-01-05T09:00:00Z",
        rule("2026-01-01T09:00", "UTC", "DAILY", 2),
        "2026-01-04T12:00:00Z");
  }

  @Test
  void weeklyUsesMondayAnchoredWeeksAndChronologicalWeekdays() {
    SchedulingRequest r =
        request(
            "2026-01-07T09:00",
            "UTC",
            ScheduleType.RECURRING,
            Map.of("frequency", "WEEKLY", "interval", 2, "weekdays", List.of("FRIDAY", "MONDAY")),
            null,
            true);
    expect("2026-01-09T09:00:00Z", r, "2026-01-01T00:00:00Z");
    expect("2026-01-19T09:00:00Z", r, "2026-01-10T00:00:00Z");
  }

  @Test
  void monthlySkipsInvalidDatesAndYearlySkipsNonLeapYears() {
    expect(
        "2026-03-31T09:00:00Z",
        rule("2026-01-31T09:00", "UTC", "MONTHLY", 1),
        "2026-02-01T00:00:00Z");
    expect(
        "2028-02-29T09:00:00Z",
        rule("2024-02-29T09:00", "UTC", "YEARLY", 1),
        "2025-01-01T00:00:00Z");
  }

  @Test
  void dailySkipsDstGapAndRunsOverlapOnlyAtEarlierOffset() {
    expect(
        "2026-03-09T06:30:00Z",
        rule("2026-03-07T02:30", "America/New_York", "DAILY", 1),
        "2026-03-08T00:00:00Z");
    SchedulingRequest overlap = rule("2026-10-31T01:30", "America/New_York", "DAILY", 1);
    expect("2026-11-01T05:30:00Z", overlap, "2026-11-01T00:00:00Z");
    expect("2026-11-02T06:30:00Z", overlap, "2026-11-01T06:00:00Z");
  }

  @Test
  void hourlyIsElapsedAcrossDstAndDailyKeepsLocalTime() {
    expect(
        "2026-03-08T07:30:00Z",
        rule("2026-03-08T01:30", "America/New_York", "HOURLY", 1),
        "2026-03-08T06:31:00Z");
    expect(
        "2026-03-08T13:00:00Z",
        rule("2026-03-07T09:00", "America/New_York", "DAILY", 1),
        "2026-03-08T00:00:00Z");
  }

  @Test
  void cronIsInclusiveAndUsesTheConfiguredZone() {
    SchedulingRequest r =
        request(
            "2026-01-01T00:00",
            "Asia/Jerusalem",
            ScheduleType.CRON,
            Map.of("expression", "0 0 9 * * *"),
            null,
            true);
    expect("2026-01-01T07:00:00Z", r, "2026-01-01T07:00:00Z");
    expect("2026-01-02T07:00:00Z", r, "2026-01-01T07:00:00.000001Z");
  }

  @Test
  void cronSkipsGapsAndRepeatedLocalTimes() {
    SchedulingRequest gap =
        request(
            "2026-03-07T00:00",
            "America/New_York",
            ScheduleType.CRON,
            Map.of("expression", "0 30 2 * * *"),
            null,
            true);
    expect("2026-03-09T06:30:00Z", gap, "2026-03-08T00:00:00Z");
    SchedulingRequest overlap =
        request(
            "2026-10-31T00:00",
            "America/New_York",
            ScheduleType.CRON,
            Map.of("expression", "0 30 1 * * *"),
            null,
            true);
    expect("2026-11-02T06:30:00Z", overlap, "2026-11-01T06:00:00Z");
  }

  @Test
  void endDateIsInclusiveAndImpossibleCronHasNoNextRun() {
    SchedulingRequest end =
        request(
            "2026-01-01T09:00",
            "UTC",
            ScheduleType.CRON,
            Map.of("expression", "0 0 9 * * *"),
            Instant.parse("2026-01-01T09:00:00Z"),
            true);
    expect("2026-01-01T09:00:00Z", end, "2026-01-01T09:00:00Z");
    expect(null, end, "2026-01-01T09:00:01Z");
    expect(
        null,
        request(
            "2026-01-01T00:00",
            "UTC",
            ScheduleType.CRON,
            Map.of("expression", "0 0 0 31 2 *"),
            null,
            true),
        "2026-01-01T00:00:00Z");
  }
}
