package com.memcyco.scheduler.scheduling;

import java.time.*;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Component;

@Component
public class NextRunCalculator {

  public Instant next(SchedulingRequest r, Instant now) {
    if (!r.enabled()) return null;
    now = now.truncatedTo(ChronoUnit.MICROS);
    ZoneId zone = ZoneId.of(r.timeZone());
    Instant result =
        switch (r.scheduleType()) {
          case ONE_TIME -> {
            Instant start = resolve(r.startsAt(), zone);
            yield start == null ? null : (start.isBefore(now) ? now : start);
          }
          case CRON -> cron(r, now, zone);
          case RECURRING -> recurring(r, now, zone);
        };
    if (result != null
        && (result.atZone(ZoneOffset.UTC).getYear() < 1000
            || result.atZone(ZoneOffset.UTC).getYear() > 9999)) return null;
    return result != null && r.endsAt() != null && result.isAfter(r.endsAt()) ? null : result;
  }

  private Instant cron(SchedulingRequest r, Instant now, ZoneId zone) {
    CronExpression expression = CronExpression.parse((String) r.scheduleConfig().get("expression"));
    LocalDateTime lower = now.atZone(zone).toLocalDateTime();
    if (lower.isBefore(r.startsAt())) lower = r.startsAt();
    LocalDateTime candidate = expression.next(lower.minusNanos(1));
    while (candidate != null) {
      Instant instant = resolve(candidate, zone);
      if (instant != null && !instant.isBefore(now)) return instant;
      candidate = expression.next(candidate);
    }
    return null;
  }

  private Instant recurring(SchedulingRequest r, Instant now, ZoneId zone) {
    SchedulingRules.Frequency f =
        SchedulingRules.Frequency.valueOf((String) r.scheduleConfig().get("frequency"));
    long interval = ((Number) r.scheduleConfig().get("interval")).longValue();
    LocalDateTime start = r.startsAt();
    LocalDateTime localNow = now.atZone(zone).toLocalDateTime();
    if (f == SchedulingRules.Frequency.MINUTELY || f == SchedulingRules.Frequency.HOURLY) {
      Instant anchor = resolve(start, zone);
      if (anchor == null) return null;
      long seconds =
          Math.multiplyExact(interval, f == SchedulingRules.Frequency.MINUTELY ? 60L : 3600L);
      long elapsed = Math.max(0, Duration.between(anchor, now).getSeconds());
      long step = elapsed / seconds;
      Instant candidate = anchor.plusSeconds(Math.multiplyExact(step, seconds));
      if (candidate.isBefore(now)) candidate = candidate.plusSeconds(seconds);
      return candidate;
    }
    long distance =
        switch (f) {
          case DAILY -> ChronoUnit.DAYS.between(start.toLocalDate(), localNow.toLocalDate());
          case WEEKLY -> ChronoUnit.WEEKS.between(monday(start), monday(localNow));
          case MONTHLY ->
              ChronoUnit.MONTHS.between(YearMonth.from(start), YearMonth.from(localNow));
          case YEARLY -> (long) localNow.getYear() - start.getYear();
          default -> throw new IllegalArgumentException("Unsupported frequency");
        };
    long step = Math.max(0, distance / interval - 1);
    // Gregorian dates repeat every 400 years (4800 months). This also bounds impossible rules.
    for (int attempt = 0; attempt < 4802; attempt++, step++) {
      long offset = Math.multiplyExact(step, interval);
      try {
        if (f == SchedulingRules.Frequency.WEEKLY) {
          LocalDate week = monday(start).plusWeeks(offset);
          List<?> weekdays = (List<?>) r.scheduleConfig().get("weekdays");
          Instant earliest = null;
          for (Object day : weekdays) {
            LocalDateTime date =
                week.plusDays(DayOfWeek.valueOf((String) day).getValue() - 1)
                    .atTime(start.toLocalTime());
            Instant value = eligible(date, start, now, zone);
            if (value != null && (earliest == null || value.isBefore(earliest))) earliest = value;
          }
          if (earliest != null) return earliest;
        } else {
          LocalDateTime date =
              switch (f) {
                case DAILY -> start.plusDays(offset);
                case MONTHLY ->
                    YearMonth.from(start)
                        .plusMonths(offset)
                        .atDay(start.getDayOfMonth())
                        .atTime(start.toLocalTime());
                case YEARLY ->
                    LocalDate.of(
                            Math.toIntExact(start.getYear() + offset),
                            start.getMonth(),
                            start.getDayOfMonth())
                        .atTime(start.toLocalTime());
                default -> throw new IllegalArgumentException("Unsupported frequency");
              };
          Instant value = eligible(date, start, now, zone);
          if (value != null) return value;
        }
      } catch (DateTimeException ex) {
        // Invalid calendar dates (e.g. February 31) are skipped, never clamped.
      }
    }
    return null;
  }

  private static LocalDate monday(LocalDateTime date) {
    return date.toLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
  }

  private static Instant eligible(
      LocalDateTime candidate, LocalDateTime start, Instant now, ZoneId zone) {
    if (candidate.isBefore(start)) return null;
    Instant value = resolve(candidate, zone);
    return value != null && !value.isBefore(now) ? value : null;
  }

  static Instant resolve(LocalDateTime local, ZoneId zone) {
    List<ZoneOffset> offsets = zone.getRules().getValidOffsets(local);
    return offsets.isEmpty() ? null : local.toInstant(offsets.getFirst());
  }
}
