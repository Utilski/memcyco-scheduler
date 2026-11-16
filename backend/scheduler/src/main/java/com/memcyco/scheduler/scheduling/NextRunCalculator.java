package com.memcyco.scheduler.scheduling;

import java.time.DateTimeException;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Component;

@Component
public class NextRunCalculator {

  // One Gregorian cycle plus boundary candidates bounds searches for impossible dates.
  private static final int MAX_CALENDAR_SEARCH_ATTEMPTS = 4802;

  public Instant next(SchedulingRequest request, Instant now) {
    if (!request.enabled()) {
      return null;
    }
    now = now.truncatedTo(ChronoUnit.MICROS);
    ZoneId timeZone = ZoneId.of(request.timeZone());
    Instant nextRunAt =
        switch (request.scheduleType()) {
          case ONE_TIME -> nextOneTimeOccurrence(request, now, timeZone);
          case CRON -> nextCronOccurrence(request, now, timeZone);
          case RECURRING -> nextRecurringOccurrence(request, now, timeZone);
        };
    if (nextRunAt != null
        && (nextRunAt.atZone(ZoneOffset.UTC).getYear() < 1000
            || nextRunAt.atZone(ZoneOffset.UTC).getYear() > 9999)) {
      return null;
    }
    if (nextRunAt != null && request.endsAt() != null && nextRunAt.isAfter(request.endsAt())) {
      return null;
    }
    return nextRunAt;
  }

  private Instant nextOneTimeOccurrence(SchedulingRequest request, Instant now, ZoneId timeZone) {
    Instant startsAt = resolve(request.startsAt(), timeZone);
    if (startsAt == null) {
      return null;
    }
    return startsAt.isBefore(now) ? now : startsAt;
  }

  private Instant nextCronOccurrence(SchedulingRequest request, Instant now, ZoneId timeZone) {
    CronExpression expression =
        CronExpression.parse((String) request.scheduleConfig().get("expression"));
    LocalDateTime earliestLocalTime = now.atZone(timeZone).toLocalDateTime();
    if (earliestLocalTime.isBefore(request.startsAt())) {
      earliestLocalTime = request.startsAt();
    }
    LocalDateTime candidate = expression.next(earliestLocalTime.minusNanos(1));
    while (candidate != null) {
      Instant instant = resolve(candidate, timeZone);
      if (instant != null && !instant.isBefore(now)) {
        return instant;
      }
      candidate = expression.next(candidate);
    }
    return null;
  }

  private Instant nextRecurringOccurrence(SchedulingRequest request, Instant now, ZoneId timeZone) {
    SchedulingRules.Frequency frequency =
        SchedulingRules.Frequency.valueOf((String) request.scheduleConfig().get("frequency"));
    long interval = ((Number) request.scheduleConfig().get("interval")).longValue();
    LocalDateTime startsAt = request.startsAt();
    if (frequency == SchedulingRules.Frequency.MINUTELY
        || frequency == SchedulingRules.Frequency.HOURLY) {
      return nextElapsedTimeOccurrence(startsAt, now, timeZone, frequency, interval);
    }
    return nextCalendarOccurrence(request, now, timeZone, frequency, interval);
  }

  private Instant nextElapsedTimeOccurrence(
      LocalDateTime startsAt,
      Instant now,
      ZoneId timeZone,
      SchedulingRules.Frequency frequency,
      long interval) {
    Instant anchor = resolve(startsAt, timeZone);
    if (anchor == null) {
      return null;
    }
    long intervalSeconds =
        Math.multiplyExact(interval, frequency == SchedulingRules.Frequency.MINUTELY ? 60L : 3600L);
    long elapsedSeconds = Math.max(0, Duration.between(anchor, now).getSeconds());
    long occurrenceIndex = elapsedSeconds / intervalSeconds;
    Instant candidate = anchor.plusSeconds(Math.multiplyExact(occurrenceIndex, intervalSeconds));
    if (candidate.isBefore(now)) {
      candidate = candidate.plusSeconds(intervalSeconds);
    }
    return candidate;
  }

  private Instant nextCalendarOccurrence(
      SchedulingRequest request,
      Instant now,
      ZoneId timeZone,
      SchedulingRules.Frequency frequency,
      long interval) {
    LocalDateTime startsAt = request.startsAt();
    LocalDateTime localNow = now.atZone(timeZone).toLocalDateTime();
    long elapsedCalendarUnits =
        switch (frequency) {
          case DAILY -> ChronoUnit.DAYS.between(startsAt.toLocalDate(), localNow.toLocalDate());
          case WEEKLY -> ChronoUnit.WEEKS.between(startOfWeek(startsAt), startOfWeek(localNow));
          case MONTHLY ->
              ChronoUnit.MONTHS.between(YearMonth.from(startsAt), YearMonth.from(localNow));
          case YEARLY -> (long) localNow.getYear() - startsAt.getYear();
          default -> throw new IllegalArgumentException("Unsupported frequency");
        };
    long occurrenceIndex = Math.max(0, elapsedCalendarUnits / interval - 1);
    // Gregorian dates repeat every 400 years (4800 months). This also bounds impossible rules.
    for (int attempt = 0; attempt < MAX_CALENDAR_SEARCH_ATTEMPTS; attempt++, occurrenceIndex++) {
      long calendarOffset = Math.multiplyExact(occurrenceIndex, interval);
      try {
        if (frequency == SchedulingRules.Frequency.WEEKLY) {
          Instant weeklyOccurrence =
              nextWeeklyOccurrence(request, startsAt, now, timeZone, calendarOffset);
          if (weeklyOccurrence != null) {
            return weeklyOccurrence;
          }
        } else {
          LocalDateTime candidateDateTime =
              switch (frequency) {
                case DAILY -> startsAt.plusDays(calendarOffset);
                case MONTHLY ->
                    YearMonth.from(startsAt)
                        .plusMonths(calendarOffset)
                        .atDay(startsAt.getDayOfMonth())
                        .atTime(startsAt.toLocalTime());
                case YEARLY ->
                    LocalDate.of(
                            Math.toIntExact(startsAt.getYear() + calendarOffset),
                            startsAt.getMonth(),
                            startsAt.getDayOfMonth())
                        .atTime(startsAt.toLocalTime());
                default -> throw new IllegalArgumentException("Unsupported frequency");
              };
          Instant candidateInstant =
              resolveEligibleOccurrence(candidateDateTime, startsAt, now, timeZone);
          if (candidateInstant != null) {
            return candidateInstant;
          }
        }
      } catch (DateTimeException exception) {
        // Invalid calendar dates (e.g. February 31) are skipped, never clamped.
      }
    }
    return null;
  }

  private Instant nextWeeklyOccurrence(
      SchedulingRequest request,
      LocalDateTime startsAt,
      Instant now,
      ZoneId timeZone,
      long calendarOffset) {
    LocalDate weekStart = startOfWeek(startsAt).plusWeeks(calendarOffset);
    List<?> weekdays = (List<?>) request.scheduleConfig().get("weekdays");
    Instant earliest = null;
    for (Object day : weekdays) {
      LocalDateTime candidateDateTime =
          weekStart
              .plusDays(DayOfWeek.valueOf((String) day).getValue() - 1)
              .atTime(startsAt.toLocalTime());
      Instant candidateInstant =
          resolveEligibleOccurrence(candidateDateTime, startsAt, now, timeZone);
      if (candidateInstant != null && (earliest == null || candidateInstant.isBefore(earliest))) {
        earliest = candidateInstant;
      }
    }
    return earliest;
  }

  private static LocalDate startOfWeek(LocalDateTime candidateDateTime) {
    return candidateDateTime.toLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
  }

  private static Instant resolveEligibleOccurrence(
      LocalDateTime candidate, LocalDateTime startsAt, Instant now, ZoneId timeZone) {
    if (candidate.isBefore(startsAt)) {
      return null;
    }
    Instant candidateInstant = resolve(candidate, timeZone);
    return candidateInstant != null && !candidateInstant.isBefore(now) ? candidateInstant : null;
  }

  static Instant resolve(LocalDateTime localDateTime, ZoneId timeZone) {
    List<ZoneOffset> validOffsets = timeZone.getRules().getValidOffsets(localDateTime);
    return validOffsets.isEmpty() ? null : localDateTime.toInstant(validOffsets.getFirst());
  }
}
