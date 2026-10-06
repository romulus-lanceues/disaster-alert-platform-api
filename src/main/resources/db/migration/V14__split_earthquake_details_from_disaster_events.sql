ALTER TABLE disaster_events
    ADD COLUMN status               VARCHAR(20),
    ADD COLUMN source_updated_at    TIMESTAMPTZ NOT NULL,
    ADD COLUMN place                TEXT;


ALTER TABLE disaster_events
    ADD CONSTRAINT uk_disaster_id_type UNIQUE (id, disaster_type);

CREATE TABLE earthquake(
    disaster_event_id   UUID PRIMARY KEY,
    disaster_type       VARCHAR(30) NOT NULL DEFAULT 'EARTHQUAKE',
    latitude            DOUBLE PRECISION NOT NULL,
    longitude           DOUBLE PRECISION NOT NULL,
    location            GEOGRAPHY(POINT, 4326) NOT NULL,
    magnitude           DOUBLE PRECISION,
    depth_km            DOUBLE PRECISION,

    CONSTRAINT chk_eq_type CHECK (disaster_type = 'EARTHQUAKE'),
    CONSTRAINT fk_eq_event FOREIGN KEY (disaster_event_id, disaster_type)
        REFERENCES disaster_events (id, disaster_type)  ON DELETE CASCADE
);

CREATE INDEX idx_eq_location ON earthquake USING GIST(location);
CREATE INDEX idx_events_type_time ON disaster_events (disaster_type, occurred_at DESC);

ALTER TABLE disaster_events
DROP COLUMN latitude,
    DROP COLUMN longitude,
    DROP COLUMN location,
    DROP COLUMN magnitude,
    DROP COLUMN depth_km;