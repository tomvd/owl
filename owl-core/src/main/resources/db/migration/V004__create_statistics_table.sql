-- Long-term statistics table (hourly aggregates, kept forever)
CREATE TABLE statistics (
    start_ts    TIMESTAMPTZ NOT NULL,
    entity_id   VARCHAR(255) NOT NULL REFERENCES entities(entity_id),
    mean        DOUBLE PRECISION,
    min         DOUBLE PRECISION,
    max         DOUBLE PRECISION,
    last        DOUBLE PRECISION,
    sum         DOUBLE PRECISION,
    count       INTEGER,
    state       DOUBLE PRECISION,
    PRIMARY KEY (start_ts, entity_id)
);

-- Index for per-entity queries
CREATE INDEX idx_stats_entity_time ON statistics (entity_id, start_ts DESC);

COMMENT ON TABLE statistics IS 'Hourly aggregated statistics, kept forever';
COMMENT ON COLUMN statistics.state IS 'For monotonic/cumulative values (e.g., total rain)';
