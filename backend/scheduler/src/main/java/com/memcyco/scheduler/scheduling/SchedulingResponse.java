package com.memcyco.scheduler.scheduling;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

public record SchedulingResponse(
    UUID id,
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
    boolean enabled,
    Instant nextRunAt,
    long dispatchedCount,
    Instant createdAt,
    Instant updatedAt,
    Long version) {

  static SchedulingResponse from(Scheduling scheduling) {
    return new SchedulingResponse(
        scheduling.getId(),
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
        scheduling.isEnabled(),
        scheduling.getNextRunAt(),
        scheduling.getDispatchedCount(),
        scheduling.getCreatedAt(),
        scheduling.getUpdatedAt(),
        scheduling.getVersion());
  }
}
