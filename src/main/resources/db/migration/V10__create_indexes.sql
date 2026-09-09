CREATE INDEX idx_locations_location
    ON locations
    USING GIST (location);

CREATE INDEX idx_disaster_events_location
    ON disaster_events
    USING GIST (location);

CREATE INDEX idx_disaster_events_occurred_at
    ON disaster_events (occurred_at);

CREATE INDEX idx_alerts_disaster_event
    ON alerts (disaster_event_id);

CREATE INDEX idx_alerts_alert_rule
    ON alerts (alert_rule_id);

CREATE INDEX idx_notifications_status
    ON notifications (status);

CREATE INDEX idx_notifications_created_at
    ON notifications (created_at);