CREATE TABLE alerts (
    id UUID PRIMARY KEY,

    disaster_event_id UUID NOT NULL,

    alert_rule_id UUID NOT NULL,

    status VARCHAR(30) NOT NULL DEFAULT 'PENDING',

    triggered_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_alert_disaster_event
        FOREIGN KEY (disaster_event_id)
        REFERENCES disaster_events(id)
        ON DELETE CASCADE,

    CONSTRAINT fk_alert_rule
        FOREIGN KEY (alert_rule_id)
        REFERENCES alert_rules(id)
        ON DELETE CASCADE,

    CONSTRAINT uk_alert_event_rule
        UNIQUE (disaster_event_id, alert_rule_id)
);