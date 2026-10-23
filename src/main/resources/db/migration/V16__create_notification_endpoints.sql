CREATE TABLE notification_endpoints (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    user_id UUID NOT NULL,

    channel VARCHAR(30) NOT NULL,

    destination TEXT NOT NULL,

    destination_hint VARCHAR(100) NOT NULL,

    enabled BOOLEAN NOT NULL DEFAULT TRUE,

    verified_at TIMESTAMPTZ,

    disabled_reason VARCHAR(100),

    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_notification_endpoints_user
        FOREIGN KEY (user_id)
        REFERENCES users(id)
        ON DELETE CASCADE,

    CONSTRAINT chk_notification_endpoints_channel
        CHECK (channel IN ('EMAIL', 'DISCORD', 'TELEGRAM')),

    CONSTRAINT uk_notification_endpoints_user_channel
        UNIQUE (user_id, channel)
);
