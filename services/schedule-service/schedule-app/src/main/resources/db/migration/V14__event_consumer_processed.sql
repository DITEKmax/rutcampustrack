-- M13 G8 — consumer-side dedup по event_id (M02 CRITICAL #2).
-- См. academic V18 для подробностей.

CREATE TABLE event_consumer_processed
(
    consumer_id  VARCHAR(64) NOT NULL,
    event_id     UUID        NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    PRIMARY KEY (consumer_id, event_id)
);

CREATE INDEX idx_ecp_cleanup
    ON event_consumer_processed (processed_at);
