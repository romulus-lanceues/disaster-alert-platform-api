ALTER TABLE geographic_areas
ADD CONSTRAINT chk_geographic_area_type
CHECK (
    type IN (
        'REGION',
        'PROVINCE',
        'MUNICIPALITY',
        'BARANGAY'
    )
);

ALTER TABLE disaster_events
ADD CONSTRAINT chk_disaster_event_type
CHECK (
    disaster_type IN (
        'EARTHQUAKE',
        'TYPHOON'
    )
);

ALTER TABLE alerts
ADD CONSTRAINT chk_alert_status
CHECK (
    status IN (
        'PENDING',
        'PROCESSED',
        'CANCELLED'
    )
);

ALTER TABLE notifications
ADD CONSTRAINT chk_notification_channel
CHECK (
    channel IN (
        'EMAIL',
        'DISCORD',
        'TELEGRAM'
    )
);

ALTER TABLE notifications
ADD CONSTRAINT chk_notification_status
CHECK (
    status IN (
        'PENDING',
        'PROCESSING',
        'SENT',
        'FAILED',
        'CANCELLED'
    )
);

ALTER TABLE users
ADD CONSTRAINT chk_user_status
CHECK (
    status IN (
        'ACTIVE',
        'DISABLED'
    )
);