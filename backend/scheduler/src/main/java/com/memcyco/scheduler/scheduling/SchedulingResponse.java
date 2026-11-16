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

  static SchedulingResponse from(Scheduling s) {
    return new SchedulingResponse(
        s.getId(),
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
        s.isEnabled(),
        s.getNextRunAt(),
        s.getDispatchedCount(),
        s.getCreatedAt(),
        s.getUpdatedAt(),
        s.getVersion());
  }
}
