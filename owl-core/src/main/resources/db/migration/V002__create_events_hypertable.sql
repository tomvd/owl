-- Enable TimescaleDB extension
CREATE EXTENSION IF NOT EXISTS timescaledb;

-- Raw events table (stores individual sensor readings)
CREATE TABLE events (
    timestamp   TIMESTAMPTZ NOT NULL,
    entity_id   VARCHAR(255) NOT NULL REFERENCES entities(entity_id),
    state       DOUBLE PRECISION,
    attributes  JSONB,
    PRIMARY KEY (timestamp, entity_id)
);

-- Convert to hypertable with 1-day chunks
SELECT create_hypertable('events', by_range('timestamp', INTERVAL '1 day'));

-- Index for per-entity queries (most common access pattern)
CREATE INDEX idx_events_entity_time ON events (entity_id, timestamp DESC);

COMMENT ON TABLE events IS 'Raw sensor events, stored as TimescaleDB hypertable';
COMMENT ON COLUMN events.state IS 'Numeric value of the measurement';
COMMENT ON COLUMN events.attributes IS 'Optional JSON metadata (e.g., source_type: archive)';
