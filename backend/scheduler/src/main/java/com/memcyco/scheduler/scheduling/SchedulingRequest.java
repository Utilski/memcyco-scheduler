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

  static final Set<String> FIELDS =
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
    checkFields(input);
    Object enabled = input.getOrDefault("enabled", true);
    if (!(enabled instanceof Boolean))
      throw new IllegalArgumentException("enabled must be a boolean");
    return new SchedulingRequest(
        text(input, "name", true),
        text(input, "description", false),
        TaskType.valueOf(text(input, "taskType", true)),
        object(input, "payload"),
        ScheduleType.valueOf(text(input, "scheduleType", true)),
        text(input, "timeZone", true),
        LocalDateTime.parse(text(input, "startsAt", true)),
        object(input, "scheduleConfig"),
        input.get("endsAt") == null ? null : Instant.parse(text(input, "endsAt", true)),
        input.get("maxOccurrences") == null
            ? null
            : positiveInteger(input.get("maxOccurrences"), "maxOccurrences"),
        (Boolean) enabled);
  }

  static void checkFields(Map<String, Object> input) {
    if (input == null) throw new IllegalArgumentException("Request must be a JSON object");
    for (String field : input.keySet()) {
      if (!FIELDS.contains(field))
        throw new IllegalArgumentException("Unknown or read-only field: " + field);
    }
  }

  static String text(Map<String, Object> input, String key, boolean required) {
    Object value = input.get(key);
    if (value == null && !required) return null;
    if (!(value instanceof String text) || (required && text.isBlank()))
      throw new IllegalArgumentException(
          key + " must be " + (required ? "a nonblank" : "a") + " string");
    return (String) value;
  }

  @SuppressWarnings("unchecked")
  static Map<String, Object> object(Map<String, Object> input, String key) {
    if (!(input.get(key) instanceof Map<?, ?>))
      throw new IllegalArgumentException(key + " must be a JSON object");
    return new LinkedHashMap<>((Map<String, Object>) input.get(key));
  }

  static long positiveInteger(Object value, String field) {
    if (!(value instanceof Number))
      throw new IllegalArgumentException(field + " must be a positive integer");
    try {
      long result = new BigDecimal(value.toString()).longValueExact();
      if (result <= 0) throw new ArithmeticException();
      return result;
    } catch (ArithmeticException | NumberFormatException ex) {
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

  static SchedulingRequest from(Scheduling s) {
    return new SchedulingRequest(
        s.getName(),
        s.getDescription(),
        s.getTaskType(),
        s.getPayload(),
        s.getScheduleType(),
        s.getTimeZone(),
        s.getStartsAt(),
        s.getScheduleConfig(),
        s.getEndsAt(),
        s.getMaxOccurrences(),
        s.isEnabled());
  }
}
