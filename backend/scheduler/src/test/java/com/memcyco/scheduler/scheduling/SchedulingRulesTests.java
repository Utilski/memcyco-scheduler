package com.memcyco.scheduler.scheduling;

import static org.junit.jupiter.api.Assertions.assertThrows;

import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

class SchedulingRulesTests {

  private static final ValidatorFactory FACTORY = Validation.buildDefaultValidatorFactory();
  private final SchedulingRules rules = new SchedulingRules(FACTORY.getValidator());

  @AfterAll
  static void closeValidator() {
    FACTORY.close();
  }

  static Map<String, Object> valid() {
    return new LinkedHashMap<>(
        Map.of(
            "name",
            "Example",
            "taskType",
            "LOG",
            "payload",
            Map.of("message", "Hello"),
            "scheduleType",
            "ONE_TIME",
            "timeZone",
            "UTC",
            "startsAt",
            "2026-01-01T09:00:00",
            "scheduleConfig",
            Map.of()));
  }

  private void validate(Map<String, Object> input) {
    rules.validate(SchedulingRequest.parse(input));
  }

  @Test
  void acceptsAllPayloadsIncludingDummySqlWithoutParsingOrExecution() {
    Map<String, Object> input = valid();
    validate(input);
    input.put("taskType", "DUMMY_EMAIL");
    input.put("payload", Map.of("to", "a@example.com", "subject", "Hello", "body", "Example"));
    validate(input);
    input.put("taskType", "DB_QUERY");
    input.put(
        "payload", Map.of("query", "This is dummy text, not SQL", "parameters", Map.of("id", 5)));
    validate(input);
  }

  @Test
  void rejectsUnknownAndServerManagedFieldsAndInvalidTypes() {
    for (String key :
        List.of("id", "version", "nextRunAt", "dispatchedCount", "createdAt", "unknown")) {
      Map<String, Object> input = valid();
      input.put(key, 1);
      assertThrows(IllegalArgumentException.class, () -> validate(input));
    }
    Map<String, Object> input = valid();
    input.put("enabled", null);
    assertThrows(IllegalArgumentException.class, () -> validate(input));
    input.put("enabled", "true");
    assertThrows(IllegalArgumentException.class, () -> validate(input));
  }

  @Test
  void validatesPayloadShapeAndUnknownTaskTypes() {
    Map<String, Object> input = valid();
    for (Map<String, Object> payload :
        List.of(
            Map.<String, Object>of(),
            Map.<String, Object>of("message", " "),
            Map.<String, Object>of("message", "OK", "extra", 1))) {
      input.put("payload", payload);
      assertThrows(IllegalArgumentException.class, () -> validate(input));
    }
    input.put("taskType", "UNKNOWN");
    assertThrows(IllegalArgumentException.class, () -> validate(input));
    input.put("taskType", "DUMMY_EMAIL");
    input.put("payload", Map.of("to", "not-an-email", "subject", "hello", "body", "body"));
    assertThrows(IllegalArgumentException.class, () -> validate(input));
    input.put("taskType", "DB_QUERY");
    input.put("payload", Map.of("query", "dummy", "parameters", "wrong"));
    assertThrows(IllegalArgumentException.class, () -> validate(input));
  }

  @Test
  void validatesWeeklyRulesAndIntervalAndCount() {
    Map<String, Object> input = valid();
    input.put("scheduleType", "RECURRING");
    for (Object interval : List.of(0, -1, 1.5, "2", 2147483648L)) {
      input.put("scheduleConfig", Map.of("frequency", "DAILY", "interval", interval));
      assertThrows(IllegalArgumentException.class, () -> validate(input));
    }
    for (List<String> days :
        List.of(List.<String>of(), List.of("MONDAY", "MONDAY"), List.of("NO_DAY"))) {
      input.put("scheduleConfig", Map.of("frequency", "WEEKLY", "interval", 1, "weekdays", days));
      assertThrows(IllegalArgumentException.class, () -> validate(input));
    }
    input.put(
        "scheduleConfig",
        Map.of("frequency", "WEEKLY", "interval", 2, "weekdays", List.of("MONDAY")));
    input.put("maxOccurrences", 3);
    validate(input);
    input.put("maxOccurrences", 0);
    assertThrows(IllegalArgumentException.class, () -> validate(input));
  }

  @Test
  void validatesTimeBoundsTimeZonesCronAndIrrelevantFields() {
    Map<String, Object> input = valid();
    input.put("timeZone", "Not/AZone");
    assertThrows(IllegalArgumentException.class, () -> validate(input));
    input.put("timeZone", "UTC");
    input.put("endsAt", "2025-01-01T00:00:00Z");
    assertThrows(IllegalArgumentException.class, () -> validate(input));
    input.remove("endsAt");
    input.put("maxOccurrences", 2);
    assertThrows(IllegalArgumentException.class, () -> validate(input));
    input.remove("maxOccurrences");
    input.put("scheduleType", "CRON");
    for (String expression :
        List.of("* * * * *", "@daily", "invalid cron expression six fields here")) {
      input.put("scheduleConfig", Map.of("expression", expression));
      assertThrows(IllegalArgumentException.class, () -> validate(input));
    }
    input.put("scheduleConfig", Map.of("expression", "0 0 9 * * *"));
    validate(input);
    input.put("scheduleConfig", Map.of("expression", "0 0 9 * * *", "weekdays", List.of("MONDAY")));
    assertThrows(IllegalArgumentException.class, () -> validate(input));
  }
}
