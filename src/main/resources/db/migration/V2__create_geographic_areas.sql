CREATE TABLE geographic_areas (
    id UUID PRIMARY KEY,

    psgc_code VARCHAR(20) NOT NULL UNIQUE,

    name VARCHAR(150) NOT NULL,

    type VARCHAR(30) NOT NULL,

    parent_id UUID,

    active BOOLEAN NOT NULL DEFAULT TRUE,

    CONSTRAINT fk_geographic_area_parent
        FOREIGN KEY (parent_id)
        REFERENCES geographic_areas(id)
);