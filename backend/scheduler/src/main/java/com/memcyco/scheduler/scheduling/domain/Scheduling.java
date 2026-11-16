package com.memcyco.scheduler.scheduling.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "schedulings")
@Getter
@Setter
public class Scheduling {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @JdbcTypeCode(SqlTypes.BINARY)
  @Column(name = "id", nullable = false, columnDefinition = "BINARY(16)")
  private UUID id;

  @Column(name = "name", nullable = false, length = 255)
  private String name;

  @Column(name = "description", columnDefinition = "TEXT")
  private String description;

  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.VARCHAR)
  @Column(name = "task_type", nullable = false, length = 32)
  private TaskType taskType;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "payload", nullable = false, columnDefinition = "JSON")
  private Map<String, Object> payload;

  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.VARCHAR)
  @Column(name = "schedule_type", nullable = false, length = 32)
  private ScheduleType scheduleType;

  @Column(name = "time_zone", nullable = false, length = 128)
  private String timeZone;

  @JdbcTypeCode(SqlTypes.LOCAL_DATE_TIME)
  @Column(name = "starts_at", nullable = false, columnDefinition = "DATETIME(6)")
  private LocalDateTime startsAt;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "schedule_config", nullable = false, columnDefinition = "JSON")
  private Map<String, Object> scheduleConfig;

  @JdbcTypeCode(SqlTypes.TIMESTAMP)
  @Column(name = "ends_at", columnDefinition = "DATETIME(6)")
  private Instant endsAt;

  @Column(name = "max_occurrences")
  private Long maxOccurrences;

  @Column(name = "enabled", nullable = false)
  private boolean enabled;

  @JdbcTypeCode(SqlTypes.TIMESTAMP)
  @Column(name = "next_run_at", columnDefinition = "DATETIME(6)")
  private Instant nextRunAt;

  @Column(name = "dispatched_count", nullable = false)
  private long dispatchedCount;

  @JdbcTypeCode(SqlTypes.TIMESTAMP)
  @Column(name = "created_at", nullable = false, columnDefinition = "DATETIME(6)")
  private Instant createdAt;

  @JdbcTypeCode(SqlTypes.TIMESTAMP)
  @Column(name = "updated_at", nullable = false, columnDefinition = "DATETIME(6)")
  private Instant updatedAt;

  @Column(name = "generation", nullable = false)
  private long generation;

  @Version
  @Column(name = "version", nullable = false)
  private Long version;

  /** Completion is independent of enabled state; toggling enabled does not reset progress. */
  public boolean isCompleted() {
    return (scheduleType == ScheduleType.ONE_TIME && dispatchedCount > 0)
        || (maxOccurrences != null && dispatchedCount >= maxOccurrences);
  }
}
