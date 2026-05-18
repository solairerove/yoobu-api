CREATE TABLE notification_outbox (
    id          BIGSERIAL PRIMARY KEY,
    tenant_id   BIGINT                   NOT NULL,
    event_type  VARCHAR(50)              NOT NULL,
    payload     JSONB                    NOT NULL,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    processed_at TIMESTAMP WITH TIME ZONE
);

CREATE INDEX notification_outbox_pending_idx
    ON notification_outbox (created_at)
    WHERE processed_at IS NULL;
