package com.memcyco.scheduler.scheduling;

import jakarta.validation.Validator;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import java.time.DayOfWeek;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Component;

@Component
public class SchedulingRules {

  enum Frequency {
    MINUTELY,
    HOURLY,
    DAILY,
    WEEKLY,
    MONTHLY,
    YEARLY
  }

  private final Validator validator;

  public SchedulingRules(Validator validator) {
    this.validator = validator;
  }

  public void validate(SchedulingRequest r) {
    if (r.name().length() > 255) fail("name must not exceed 255 characters");
    if (r.timeZone().length() > 128 || !ZoneId.getAvailableZoneIds().contains(r.timeZone()))
      fail("timeZone must be an IANA time zone");
    if (r.startsAt().getNano() % 1000 != 0
        || (r.endsAt() != null && r.endsAt().getNano() % 1000 != 0))
      fail("Dates support at most microsecond precision");
    if (r.startsAt().getYear() < 1000
        || r.startsAt().getYear() > 9999
        || (r.endsAt() != null
            && (r.endsAt().atZone(java.time.ZoneOffset.UTC).getYear() < 1000
                || r.endsAt().atZone(java.time.ZoneOffset.UTC).getYear() > 9999)))
      fail("Dates must be within MySQL's supported years 1000 through 9999");
    ZoneId zone = ZoneId.of(r.timeZone());
    // atZone shifts a nonexistent start forward only for checking the end bound.
    // Actual occurrences in a DST gap are skipped by the calculator.
    if (r.endsAt() != null && r.endsAt().isBefore(r.startsAt().atZone(zone).toInstant()))
      fail("endsAt must not be before startsAt");
    validatePayload(r);
    Map<String, Object> config = r.scheduleConfig();
    switch (r.scheduleType()) {
      case ONE_TIME -> {
        allowed(config, Set.of());
        if (r.maxOccurrences() != null)
          fail("maxOccurrences applies only to recurring and cron schedules");
      }
      case CRON -> {
        allowed(config, Set.of("expression"));
        String expression = SchedulingRequest.text(config, "expression", true);
        if (expression.trim().split("\\s+").length != 6)
          fail("Cron expressions must have six fields, including seconds");
        CronExpression.parse(expression);
      }
      case RECURRING -> {
        Frequency frequency = Frequency.valueOf(SchedulingRequest.text(config, "frequency", true));
        allowed(
            config,
            frequency == Frequency.WEEKLY
                ? Set.of("frequency", "interval", "weekdays")
                : Set.of("frequency", "interval"));
        long interval = SchedulingRequest.positiveInteger(config.get("interval"), "interval");
        if (interval > Integer.MAX_VALUE) fail("interval must not exceed 2147483647");
        if (frequency == Frequency.WEEKLY) {
          if (!(config.get("weekdays") instanceof List<?> days) || days.isEmpty())
            fail("Weekly schedules require a nonempty weekdays array");
          List<?> days = (List<?>) config.get("weekdays");
          Set<DayOfWeek> unique = new HashSet<>();
          for (Object day : days) {
            if (!(day instanceof String)) fail("weekdays must contain day names");
            if (!unique.add(DayOfWeek.valueOf((String) day))) fail("weekdays must be unique");
          }
        }
      }
    }
  }

  private void validatePayload(SchedulingRequest r) {
    Map<String, Object> p = r.payload();
    switch (r.taskType()) {
      case LOG -> {
        allowed(p, Set.of("message"));
        SchedulingRequest.text(p, "message", true);
      }
      case DUMMY_EMAIL -> {
        allowed(p, Set.of("to", "subject", "body"));
        String to = SchedulingRequest.text(p, "to", true);
        if (to.contains(",")
            || to.contains(";")
            || !validator.validate(new EmailAddress(to)).isEmpty())
          fail("to must be a single valid email address");
        SchedulingRequest.text(p, "subject", true);
        SchedulingRequest.text(p, "body", true);
      }
      case DB_QUERY -> {
        allowed(p, Set.of("query", "parameters"));
        SchedulingRequest.text(p, "query", true);
        if (p.containsKey("parameters")) SchedulingRequest.object(p, "parameters");
      }
    }
  }

  private static void allowed(Map<String, Object> values, Set<String> allowed) {
    for (String key : values.keySet()) {
      if (!allowed.contains(key)) fail("Unexpected configuration or payload field: " + key);
    }
  }

  private static void fail(String message) {
    throw new IllegalArgumentException(message);
  }

  private record EmailAddress(@NotBlank @Email String value) {}
}
