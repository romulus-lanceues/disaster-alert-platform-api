CREATE TABLE locations (
    id UUID PRIMARY KEY,

    user_id UUID NOT NULL,

    name VARCHAR(100) NOT NULL,

    address VARCHAR(255),

    geographic_area_id UUID NOT NULL,

    latitude DOUBLE PRECISION NOT NULL,

    longitude DOUBLE PRECISION NOT NULL,

    location GEOGRAPHY(POINT, 4326) NOT NULL,

    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_location_user
        FOREIGN KEY (user_id)
        REFERENCES users(id)
        ON DELETE CASCADE,

    CONSTRAINT fk_location_geographic_area
        FOREIGN KEY (geographic_area_id)
        REFERENCES geographic_areas(id)
);