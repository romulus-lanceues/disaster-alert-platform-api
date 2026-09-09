CREATE TABLE notifications (
    id UUID PRIMARY KEY,

    alert_id UUID NOT NULL,

    channel VARCHAR(30) NOT NULL,

    destination VARCHAR(255) NOT NULL,

    status VARCHAR(30) NOT NULL DEFAULT 'PENDING',

    attempt_count INTEGER NOT NULL DEFAULT 0,

    sent_at TIMESTAMPTZ,

    failure_reason TEXT,

    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_notification_alert
        FOREIGN KEY (alert_id)
        REFERENCES alerts(id)
        ON DELETE CASCADE,

    CONSTRAINT uk_notification_alert_channel
        UNIQUE (alert_id, channel)
);