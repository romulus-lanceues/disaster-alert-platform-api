ALTER TABLE geographic_areas
DROP CONSTRAINT chk_geographic_area_type;

ALTER TABLE geographic_areas
ADD CONSTRAINT chk_geographic_area_type
CHECK (
    type IN (
        'REGION',
        'PROVINCE',
        'SPECIAL_AREA',
        'CITY',
        'MUNICIPALITY',
        'SUB_MUNICIPALITY',
        'BARANGAY'
        )
);

ALTER TABLE geographic_areas
    ALTER COLUMN id SET DEFAULT gen_random_uuid(),
    ALTER COLUMN psgc_code TYPE VARCHAR(10),
    ADD CONSTRAINT chk_psgc_code CHECK (psgc_code ~ '^[0-9]{10}$');

CREATE INDEX idx_geographic_areas_parent ON geographic_areas(parent_id, name);