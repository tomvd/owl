-- Entity registry table
-- Stores metadata about each sensor/measurement entity

CREATE TABLE entities (
    entity_id           VARCHAR(255) PRIMARY KEY,
    friendly_name       VARCHAR(255),
    source              VARCHAR(50) NOT NULL,
    unit_of_measurement VARCHAR(20),
    device_class        VARCHAR(50),
    state_class         VARCHAR(20) DEFAULT 'measurement',
    aggregation_method  VARCHAR(20) DEFAULT 'mean',
    created_at          TIMESTAMPTZ DEFAULT NOW()
);

COMMENT ON TABLE entities IS 'Registry of all sensor entities with their metadata';
COMMENT ON COLUMN entities.entity_id IS 'Unique identifier following pattern sensor.<source>_<measurement>';
COMMENT ON COLUMN entities.aggregation_method IS 'How to aggregate: mean, min, max, sum, last';
