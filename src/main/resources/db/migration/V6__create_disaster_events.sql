CREATE TABLE disaster_events (
    id UUID PRIMARY KEY,

    source VARCHAR(50) NOT NULL,

    external_id VARCHAR(255) NOT NULL,

    disaster_type VARCHAR(30) NOT NULL,

    occurred_at TIMESTAMPTZ NOT NULL,

    latitude DOUBLE PRECISION,

    longitude DOUBLE PRECISION,

    location GEOGRAPHY(POINT, 4326),

    magnitude DOUBLE PRECISION,

    depth_km DOUBLE PRECISION,

    severity VARCHAR(30),

    raw_payload JSONB,

    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uk_disaster_source_external
        UNIQUE (source, external_id)
);