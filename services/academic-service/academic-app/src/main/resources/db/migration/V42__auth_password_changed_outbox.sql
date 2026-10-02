-- Auth owns these intents; Academic owns the shared database migration chain.
-- Pending rows have no expiry: broker outages must not discard committed intent.
CREATE TABLE auth_outbox
(
    id          BIGSERIAL PRIMARY KEY,
    event_type  VARCHAR(128) NOT NULL,
    payload     JSONB NOT NULL,
    status      VARCHAR(16) NOT NULL DEFAULT 'pending',
    retry_count INTEGER NOT NULL DEFAULT 0,
    last_error  TEXT,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    sent_at     TIMESTAMPTZ,
    CONSTRAINT auth_outbox_status_chk CHECK (status IN ('pending', 'sent', 'failed'))
);

CREATE UNIQUE INDEX idx_auth_outbox_event_id ON auth_outbox ((payload ->> 'event_id'));
CREATE INDEX idx_auth_outbox_pending ON auth_outbox (created_at) WHERE status = 'pending';
CREATE INDEX idx_auth_outbox_sent_cleanup ON auth_outbox (sent_at) WHERE status = 'sent';
