-- Short-term statistics table (5-minute aggregates)
CREATE TABLE statistics_short_term (
    start_ts    TIMESTAMPTZ NOT NULL,
    entity_id   VARCHAR(255) NOT NULL REFERENCES entities(entity_id),
    mean        DOUBLE PRECISION,
    min         DOUBLE PRECISION,
    max         DOUBLE PRECISION,
    last        DOUBLE PRECISION,
    sum         DOUBLE PRECISION,
    count       INTEGER,
    PRIMARY KEY (start_ts, entity_id)
);

-- Convert to hypertable with 1-day chunks
SELECT create_hypertable('statistics_short_term', by_range('start_ts', INTERVAL '1 day'));

-- Index for per-entity queries
CREATE INDEX idx_stats_short_entity_time ON statistics_short_term (entity_id, start_ts DESC);

COMMENT ON TABLE statistics_short_term IS '5-minute aggregated statistics, kept for 14 days';
