CREATE TABLE refresh_token_session (
    id          UUID PRIMARY KEY,
    user_id     UUID NOT NULL,
    family_id   UUID NOT NULL,
    token_hash  VARCHAR(64) NOT NULL UNIQUE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at TIMESTAMPTZ NOT NULL,
    revoked_at  TIMESTAMPTZ,
    replaced_by_id UUID UNIQUE ,


    CONSTRAINT fk_refresh_token_user_id
        FOREIGN KEY (user_id)
        REFERENCES users(id)
        ON DELETE CASCADE,

    CONSTRAINT fk_refresh_token_replaced_by_id
        FOREIGN KEY (replaced_by_id)
        REFERENCES refresh_token_session(id)
        ON DELETE SET NULL
);

CREATE INDEX idx_rts_user   ON refresh_token_session(user_id);
CREATE INDEX idx_rts_family ON refresh_token_session(family_id);

