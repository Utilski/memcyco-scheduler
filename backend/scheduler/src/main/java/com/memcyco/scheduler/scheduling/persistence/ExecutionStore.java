package com.memcyco.scheduler.scheduling.persistence;

import com.memcyco.scheduler.scheduling.domain.Scheduling;
import com.memcyco.scheduler.scheduling.domain.TaskType;
import com.memcyco.scheduler.scheduling.execution.ExecutionRetryPolicy;
import com.memcyco.scheduler.scheduling.execution.ExecutionStatus;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** SQL state shared by dispatch, the durable outbox, and consumers. No broker calls here. */
@Repository
public class ExecutionStore {
  private final JdbcTemplate jdbc;

  public ExecutionStore(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public record Execution(
      UUID id,
      UUID schedulingId,
      long generation,
      Instant scheduledAt,
      TaskType taskType,
      String payload,
      ExecutionStatus status,
      int attempts,
      Instant publishAfter,
      boolean published) {
    public boolean isReady(Instant now) {
      return status == ExecutionStatus.PENDING && !publishAfter.isAfter(now);
    }
  }

  public void requestSync(UUID schedulingId, Instant now) {
    jdbc.update("DELETE FROM scheduling_sync WHERE scheduling_id = ?", schedulingId.toString());
    jdbc.update(
        "INSERT INTO scheduling_sync (scheduling_id, token, requested_at) VALUES (?, ?, ?)",
        schedulingId.toString(),
        UUID.randomUUID().toString(),
        Timestamp.from(now));
  }

  public List<UUID> pendingSync() {
    return jdbc.query(
        "SELECT scheduling_id FROM scheduling_sync ORDER BY requested_at LIMIT 100",
        (row, index) -> UUID.fromString(row.getString(1)));
  }

  public String lockSync(UUID id) {
    List<String> tokens =
        jdbc.query(
            "SELECT token FROM scheduling_sync WHERE scheduling_id = ? FOR UPDATE",
            (row, index) -> row.getString(1),
            id.toString());
    return tokens.isEmpty() ? null : tokens.getFirst();
  }

  public void completeSync(UUID id, String token) {
    jdbc.update(
        "DELETE FROM scheduling_sync WHERE scheduling_id = ? AND token = ?", id.toString(), token);
  }

  public boolean hasOutstanding(UUID id) {
    Integer count =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM task_executions WHERE scheduling_id = ? AND status = 'PENDING'",
            Integer.class,
            id.toString());
    return count != null && count > 0;
  }

  public void cancelPending(UUID id, Instant now) {
    jdbc.update(
        "UPDATE task_executions SET status = 'CANCELED', completed_at = ? WHERE scheduling_id = ?"
            + " AND status = 'PENDING'",
        Timestamp.from(now),
        id.toString());
  }

  public boolean occurrenceExists(UUID id, long generation, Instant scheduledAt) {
    return jdbc.queryForObject(
            "SELECT COUNT(*) FROM task_executions WHERE scheduling_id = ? AND generation = ? AND"
                + " scheduled_at = ?",
            Integer.class,
            id.toString(),
            generation,
            Timestamp.from(scheduledAt))
        > 0;
  }

  public void insert(Scheduling scheduling, Instant scheduledAt, String payload, Instant now) {
    jdbc.update(
        "INSERT INTO task_executions (id, scheduling_id, generation, scheduled_at, task_type,"
            + " payload, status, publish_after) VALUES (?, ?, ?, ?, ?, ?, 'PENDING', ?)",
        UUID.randomUUID().toString(),
        scheduling.getId().toString(),
        scheduling.getGeneration(),
        Timestamp.from(scheduledAt),
        scheduling.getTaskType().name(),
        payload,
        Timestamp.from(now));
  }

  public List<UUID> publishable(Instant now) {
    return jdbc.query(
        "SELECT id FROM task_executions WHERE status = 'PENDING' AND published = FALSE AND"
            + " publish_after <= ? ORDER BY publish_after LIMIT 100",
        (row, index) -> UUID.fromString(row.getString(1)),
        Timestamp.from(now));
  }

  public Execution lockExecution(UUID id) {
    List<Execution> results =
        jdbc.query(
            "SELECT * FROM task_executions WHERE id = ? FOR UPDATE",
            (row, index) ->
                new Execution(
                    UUID.fromString(row.getString("id")),
                    UUID.fromString(row.getString("scheduling_id")),
                    row.getLong("generation"),
                    row.getTimestamp("scheduled_at").toInstant(),
                    TaskType.valueOf(row.getString("task_type")),
                    row.getString("payload"),
                    ExecutionStatus.valueOf(row.getString("status")),
                    row.getInt("attempts"),
                    row.getTimestamp("publish_after").toInstant(),
                    row.getBoolean("published")),
            id.toString());
    return results.isEmpty() ? null : results.getFirst();
  }

  public UUID schedulingId(UUID executionId) {
    List<String> ids =
        jdbc.query(
            "SELECT scheduling_id FROM task_executions WHERE id = ?",
            (row, index) -> row.getString(1),
            executionId.toString());
    return ids.isEmpty() ? null : UUID.fromString(ids.getFirst());
  }

  public void markPublished(UUID id) {
    jdbc.update("UPDATE task_executions SET published = TRUE WHERE id = ?", id.toString());
  }

  public void succeed(UUID id, int attempts, Instant now) {
    jdbc.update(
        "UPDATE task_executions SET status = 'SUCCEEDED', attempts = ?, completed_at = ?,"
            + " failure_detail = NULL WHERE id = ?",
        attempts,
        Timestamp.from(now),
        id.toString());
  }

  public void fail(UUID id, int attempts, String detail, Instant now) {
    if (ExecutionRetryPolicy.isExhausted(attempts)) {
      jdbc.update(
          "UPDATE task_executions SET status = 'FAILED', attempts = ?, failure_detail = ?,"
              + " completed_at = ? WHERE id = ?",
          attempts,
          detail,
          Timestamp.from(now),
          id.toString());
    } else {
      jdbc.update(
          "UPDATE task_executions SET attempts = ?, failure_detail = ?, published = FALSE,"
              + " publish_after = ? WHERE id = ?",
          attempts,
          detail,
          Timestamp.from(now.plus(ExecutionRetryPolicy.delayAfter(attempts))),
          id.toString());
    }
  }
}
