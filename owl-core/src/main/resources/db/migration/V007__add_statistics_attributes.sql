-- Add attributes JSONB field to statistics tables for non-numeric data
-- (icons, vector components for wind direction, lightning bucket counts, etc.)

ALTER TABLE statistics_short_term ADD COLUMN attributes JSONB;
ALTER TABLE statistics ADD COLUMN attributes JSONB;

COMMENT ON COLUMN statistics_short_term.attributes IS 'Additional attributes (icons, vectors, etc.)';
COMMENT ON COLUMN statistics.attributes IS 'Additional attributes (icons, vectors, etc.)';
