-- Set DB default gen_random_uuid() for notifications.id
ALTER TABLE notifications
    ALTER COLUMN id SET DEFAULT gen_random_uuid();

-- Add delivery lifecycle columns and endpoint reference to notifications
ALTER TABLE notifications
    ADD COLUMN endpoint_id UUID,
    ADD COLUMN next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN locked_until TIMESTAMPTZ,
    ADD COLUMN updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN provider_message_id VARCHAR(100),
    ADD CONSTRAINT fk_notifications_endpoint
        FOREIGN KEY (endpoint_id)
        REFERENCES notification_endpoints(id)
        ON DELETE SET NULL;

-- Replace broad status index with high-selectivity partial indexes
DROP INDEX idx_notifications_status;

CREATE INDEX idx_notifications_pending_claim
    ON notifications (next_attempt_at)
    WHERE status = 'PENDING';

CREATE INDEX idx_notifications_processing_lease
    ON notifications (locked_until)
    WHERE status = 'PROCESSING';

CREATE INDEX idx_notifications_pending_endpoint
    ON notifications (endpoint_id)
    WHERE status = 'PENDING';

-- Fan-out sweep partial index for pending alerts
CREATE INDEX idx_alerts_pending_sweep
    ON alerts (triggered_at)
    WHERE status = 'PENDING';
