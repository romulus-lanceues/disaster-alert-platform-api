ALTER TABLE disaster_events
    ADD COLUMN alert_matched_at TIMESTAMPTZ;

-- Backfill existing events so historical events are not treated as unmatched
UPDATE disaster_events
SET alert_matched_at = created_at
WHERE alert_matched_at IS NULL;

-- Partial index for fast claiming of pending unmatched disaster events
CREATE INDEX idx_disaster_events_unmatched
    ON disaster_events (source_updated_at)
    WHERE alert_matched_at IS NULL;
