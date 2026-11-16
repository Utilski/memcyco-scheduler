CREATE INDEX idx_schedulings_created ON schedulings (created_at, id);
CREATE INDEX idx_schedulings_enabled_created ON schedulings (enabled, created_at, id);
