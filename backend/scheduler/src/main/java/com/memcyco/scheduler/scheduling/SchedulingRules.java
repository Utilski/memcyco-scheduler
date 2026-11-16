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

  public void validate(SchedulingRequest request) {
    validateNameAndTimeBounds(request);
    validatePayload(request);
    validateScheduleConfig(request);
  }

  private void validateNameAndTimeBounds(SchedulingRequest request) {
    if (request.name().length() > 255) {
      rejectInvalidRequest("name must not exceed 255 characters");
    }
    if (request.timeZone().length() > 128
        || !ZoneId.getAvailableZoneIds().contains(request.timeZone())) {
      rejectInvalidRequest("timeZone must be an IANA time zone");
    }
    if (request.startsAt().getNano() % 1000 != 0
        || (request.endsAt() != null && request.endsAt().getNano() % 1000 != 0)) {
      rejectInvalidRequest("Dates support at most microsecond precision");
    }
    if (request.startsAt().getYear() < 1000
        || request.startsAt().getYear() > 9999
        || (request.endsAt() != null
            && (request.endsAt().atZone(java.time.ZoneOffset.UTC).getYear() < 1000
                || request.endsAt().atZone(java.time.ZoneOffset.UTC).getYear() > 9999))) {
      rejectInvalidRequest("Dates must be within MySQL's supported years 1000 through 9999");
    }
    ZoneId timeZone = ZoneId.of(request.timeZone());
    // atZone shifts a nonexistent start forward only for checking the end bound.
    // Actual occurrences in a DST gap are skipped by the calculator.
    if (request.endsAt() != null
        && request.endsAt().isBefore(request.startsAt().atZone(timeZone).toInstant())) {
      rejectInvalidRequest("endsAt must not be before startsAt");
    }
  }

  private void validateScheduleConfig(SchedulingRequest request) {
    Map<String, Object> scheduleConfig = request.scheduleConfig();
    switch (request.scheduleType()) {
      case ONE_TIME -> {
        validateAllowedFields(scheduleConfig, Set.of());
        if (request.maxOccurrences() != null) {
          rejectInvalidRequest("maxOccurrences applies only to recurring and cron schedules");
        }
      }
      case CRON -> {
        validateAllowedFields(scheduleConfig, Set.of("expression"));
        String expression = SchedulingRequest.readText(scheduleConfig, "expression", true);
        if (expression.trim().split("\\s+").length != 6) {
          rejectInvalidRequest("Cron expressions must have six fields, including seconds");
        }
        CronExpression.parse(expression);
      }
      case RECURRING -> {
        validateRecurringConfig(scheduleConfig);
      }
    }
  }

  private void validateRecurringConfig(Map<String, Object> scheduleConfig) {
    Frequency frequency =
        Frequency.valueOf(SchedulingRequest.readText(scheduleConfig, "frequency", true));
    validateAllowedFields(
        scheduleConfig,
        frequency == Frequency.WEEKLY
            ? Set.of("frequency", "interval", "weekdays")
            : Set.of("frequency", "interval"));
    long interval =
        SchedulingRequest.readPositiveInteger(scheduleConfig.get("interval"), "interval");
    if (interval > Integer.MAX_VALUE) {
      rejectInvalidRequest("interval must not exceed 2147483647");
    }
    if (frequency == Frequency.WEEKLY) {
      if (!(scheduleConfig.get("weekdays") instanceof List<?> weekdays) || weekdays.isEmpty()) {
        rejectInvalidRequest("Weekly schedules require a nonempty weekdays array");
      }
      List<?> weekdays = (List<?>) scheduleConfig.get("weekdays");
      Set<DayOfWeek> uniqueWeekdays = new HashSet<>();
      for (Object day : weekdays) {
        if (!(day instanceof String)) {
          rejectInvalidRequest("weekdays must contain day names");
        }
        if (!uniqueWeekdays.add(DayOfWeek.valueOf((String) day))) {
          rejectInvalidRequest("weekdays must be unique");
        }
      }
    }
  }

  private void validatePayload(SchedulingRequest request) {
    Map<String, Object> payload = request.payload();
    switch (request.taskType()) {
      case LOG -> {
        validateAllowedFields(payload, Set.of("message"));
        SchedulingRequest.readText(payload, "message", true);
      }
      case DUMMY_EMAIL -> {
        validateAllowedFields(payload, Set.of("to", "subject", "body"));
        String recipient = SchedulingRequest.readText(payload, "to", true);
        if (recipient.contains(",")
            || recipient.contains(";")
            || !validator.validate(new EmailAddress(recipient)).isEmpty()) {
          rejectInvalidRequest("to must be a single valid email address");
        }
        SchedulingRequest.readText(payload, "subject", true);
        SchedulingRequest.readText(payload, "body", true);
      }
      case DB_QUERY -> {
        validateAllowedFields(payload, Set.of("query", "parameters"));
        SchedulingRequest.readText(payload, "query", true);
        if (payload.containsKey("parameters")) {
          SchedulingRequest.readObject(payload, "parameters");
        }
      }
    }
  }

  private static void validateAllowedFields(Map<String, Object> values, Set<String> allowedFields) {
    for (String key : values.keySet()) {
      if (!allowedFields.contains(key)) {
        rejectInvalidRequest("Unexpected configuration or payload field: " + key);
      }
    }
  }

  private static void rejectInvalidRequest(String message) {
    throw new IllegalArgumentException(message);
  }

  private record EmailAddress(@NotBlank @Email String value) {}
}
