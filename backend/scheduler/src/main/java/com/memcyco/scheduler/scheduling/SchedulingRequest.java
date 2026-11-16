package com.memcyco.scheduler.scheduling;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public record SchedulingRequest(
    String name,
    String description,
    TaskType taskType,
    Map<String, Object> payload,
    ScheduleType scheduleType,
    String timeZone,
    LocalDateTime startsAt,
    Map<String, Object> scheduleConfig,
    Instant endsAt,
    Long maxOccurrences,
    boolean enabled) {

  static final Set<String> WRITABLE_FIELDS =
      Set.of(
          "name",
          "description",
          "taskType",
          "payload",
          "scheduleType",
          "timeZone",
          "startsAt",
          "scheduleConfig",
          "endsAt",
          "maxOccurrences",
          "enabled");

  static SchedulingRequest parse(Map<String, Object> input) {
    validateWritableFields(input);
    Object enabled = input.getOrDefault("enabled", true);
    if (!(enabled instanceof Boolean)) {
      throw new IllegalArgumentException("enabled must be a boolean");
    }
    return new SchedulingRequest(
        readText(input, "name", true),
        readText(input, "description", false),
        TaskType.valueOf(readText(input, "taskType", true)),
        readObject(input, "payload"),
        ScheduleType.valueOf(readText(input, "scheduleType", true)),
        readText(input, "timeZone", true),
        LocalDateTime.parse(readText(input, "startsAt", true)),
        readObject(input, "scheduleConfig"),
        input.get("endsAt") == null ? null : Instant.parse(readText(input, "endsAt", true)),
        input.get("maxOccurrences") == null
            ? null
            : readPositiveInteger(input.get("maxOccurrences"), "maxOccurrences"),
        (Boolean) enabled);
  }

  static void validateWritableFields(Map<String, Object> input) {
    if (input == null) {
      throw new IllegalArgumentException("Request must be a JSON object");
    }
    for (String field : input.keySet()) {
      if (!WRITABLE_FIELDS.contains(field)) {
        throw new IllegalArgumentException("Unknown or read-only field: " + field);
      }
    }
  }

  static String readText(Map<String, Object> input, String fieldName, boolean required) {
    Object value = input.get(fieldName);
    if (value == null && !required) {
      return null;
    }
    if (!(value instanceof String text) || (required && text.isBlank())) {
      throw new IllegalArgumentException(
          fieldName + " must be " + (required ? "a nonblank" : "a") + " string");
    }
    return (String) value;
  }

  @SuppressWarnings("unchecked")
  static Map<String, Object> readObject(Map<String, Object> input, String fieldName) {
    if (!(input.get(fieldName) instanceof Map<?, ?>)) {
      throw new IllegalArgumentException(fieldName + " must be a JSON object");
    }
    return new LinkedHashMap<>((Map<String, Object>) input.get(fieldName));
  }

  static long readPositiveInteger(Object value, String field) {
    if (!(value instanceof Number)) {
      throw new IllegalArgumentException(field + " must be a positive integer");
    }
    try {
      long result = new BigDecimal(value.toString()).longValueExact();
      if (result <= 0) {
        throw new ArithmeticException();
      }
      return result;
    } catch (ArithmeticException | NumberFormatException exception) {
      throw new IllegalArgumentException(field + " must be a positive integer");
    }
  }

  Map<String, Object> toMap() {
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("name", name);
    result.put("description", description);
    result.put("taskType", taskType.name());
    result.put("payload", payload);
    result.put("scheduleType", scheduleType.name());
    result.put("timeZone", timeZone);
    result.put("startsAt", startsAt.toString());
    result.put("scheduleConfig", scheduleConfig);
    result.put("endsAt", endsAt == null ? null : endsAt.toString());
    result.put("maxOccurrences", maxOccurrences);
    result.put("enabled", enabled);
    return result;
  }

  static SchedulingRequest from(Scheduling scheduling) {
    return new SchedulingRequest(
        scheduling.getName(),
        scheduling.getDescription(),
        scheduling.getTaskType(),
        scheduling.getPayload(),
        scheduling.getScheduleType(),
        scheduling.getTimeZone(),
        scheduling.getStartsAt(),
        scheduling.getScheduleConfig(),
        scheduling.getEndsAt(),
        scheduling.getMaxOccurrences(),
        scheduling.isEnabled());
  }
}
