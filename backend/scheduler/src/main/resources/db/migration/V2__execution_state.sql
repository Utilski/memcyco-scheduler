ALTER TABLE schedulings ADD COLUMN generation BIGINT NOT NULL DEFAULT 0;
CREATE TABLE scheduling_sync (
    scheduling_id VARCHAR(36) NOT NULL PRIMARY KEY,
    token VARCHAR(36) NOT NULL,
    requested_at DATETIME(6) NOT NULL,
    INDEX idx_sync_requested (requested_at)
);
CREATE TABLE task_executions (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    scheduling_id VARCHAR(36) NOT NULL,
    generation BIGINT NOT NULL,
    scheduled_at DATETIME(6) NOT NULL,
    task_type VARCHAR(32) NOT NULL,
    payload LONGTEXT NOT NULL,
    status VARCHAR(16) NOT NULL,
    attempts INT NOT NULL DEFAULT 0,
    publish_after DATETIME(6) NOT NULL,
    published BOOLEAN NOT NULL DEFAULT FALSE,
    completed_at DATETIME(6),
    failure_detail LONGTEXT,
    UNIQUE KEY uq_execution_occurrence (scheduling_id, generation, scheduled_at),
    INDEX idx_execution_outbox (status, published, publish_after),
    INDEX idx_execution_schedule (scheduling_id, status)
);
