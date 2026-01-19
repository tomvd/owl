-- Enable compression on events table for chunks older than 7 days
ALTER TABLE events SET (
    timescaledb.compress,
    timescaledb.compress_segmentby = 'entity_id'
);

SELECT add_compression_policy('events', INTERVAL '7 days');

-- Retention policy: drop raw events older than 14 days
SELECT add_retention_policy('events', INTERVAL '14 days');

-- Retention policy: drop short-term stats older than 14 days
SELECT add_retention_policy('statistics_short_term', INTERVAL '14 days');

-- Note: statistics table has no retention policy (kept forever)
