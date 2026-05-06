CREATE TABLE templates (
    id          VARCHAR(128) PRIMARY KEY,
    name        VARCHAR(255) NOT NULL,
    channel     VARCHAR(32)  NOT NULL,
    subject     VARCHAR(255),
    body        TEXT         NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE TABLE notifications (
    id            UUID PRIMARY KEY,
    recipient_id  UUID         NOT NULL,
    channel       VARCHAR(32)  NOT NULL,
    template_id   VARCHAR(128) NOT NULL REFERENCES templates(id),
    variables     JSONB        NOT NULL DEFAULT '{}'::jsonb,
    status        VARCHAR(32)  NOT NULL,
    attempts      INT          NOT NULL DEFAULT 0,
    max_attempts  INT          NOT NULL DEFAULT 5,
    last_error    TEXT,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    delivered_at  TIMESTAMPTZ
);

CREATE INDEX idx_notifications_status ON notifications(status);
CREATE INDEX idx_notifications_recipient ON notifications(recipient_id);
