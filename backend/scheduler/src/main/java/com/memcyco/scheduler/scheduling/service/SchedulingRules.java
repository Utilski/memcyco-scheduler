package com.memcyco.scheduler.scheduling.service;

import com.memcyco.scheduler.scheduling.api.SchedulingRequest;
import com.memcyco.scheduler.scheduling.domain.ScheduleType;
import jakarta.validation.Validator;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import java.time.ZoneId;
import java.util.Map;
import java.util.Set;
import org.quartz.CronExpression;
import org.springframework.stereotype.Component;

@Component
public class SchedulingRules {

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
    if (request.startsAt().getNano() % 1000000 != 0
        || (request.endsAt() != null && request.endsAt().getNano() % 1000000 != 0)) {
      rejectInvalidRequest("Dates support at most millisecond precision");
    }
    if (request.startsAt().getYear() < 1000
        || request.startsAt().getYear() > 9999
        || (request.endsAt() != null
            && (request.endsAt().atZone(java.time.ZoneOffset.UTC).getYear() < 1000
                || request.endsAt().atZone(java.time.ZoneOffset.UTC).getYear() > 9999))) {
      rejectInvalidRequest("Dates must be within MySQL's supported years 1000 through 9999");
    }
    ZoneId timeZone = ZoneId.of(request.timeZone());
    // Local starts resolve using zone rules; subsequent DST handling belongs to Quartz.
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
        if (!CronExpression.isValidExpression(expression)) {
          rejectInvalidRequest("expression must be a valid Quartz cron expression");
        }
      }
      case SIMPLE_INTERVAL, CALENDAR_INTERVAL -> {
        validateAllowedFields(scheduleConfig, Set.of("interval", "unit"));
        long interval =
            SchedulingRequest.readPositiveInteger(scheduleConfig.get("interval"), "interval");
        if (interval > Integer.MAX_VALUE) {
          rejectInvalidRequest("interval must not exceed 2147483647");
        }
        String unit = SchedulingRequest.readText(scheduleConfig, "unit", true);
        Set<String> allowedUnits =
            request.scheduleType() == ScheduleType.SIMPLE_INTERVAL
                ? Set.of("SECONDS", "MINUTES", "HOURS")
                : Set.of("DAY", "WEEK", "MONTH", "YEAR");
        if (!allowedUnits.contains(unit)) {
          rejectInvalidRequest("Unsupported interval unit: " + unit);
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
