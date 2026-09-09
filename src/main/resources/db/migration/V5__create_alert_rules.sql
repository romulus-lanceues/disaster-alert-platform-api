CREATE TABLE alert_rules (
    id UUID PRIMARY KEY,

    location_id UUID NOT NULL,

    disaster_type VARCHAR(30) NOT NULL,

    enabled BOOLEAN NOT NULL DEFAULT TRUE,

    minimum_magnitude DOUBLE PRECISION,

    radius_km DOUBLE PRECISION,

    minimum_severity VARCHAR(30),

    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_alert_rule_location
        FOREIGN KEY (location_id)
        REFERENCES locations(id)
        ON DELETE CASCADE
);