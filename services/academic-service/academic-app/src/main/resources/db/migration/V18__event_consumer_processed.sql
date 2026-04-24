-- M13 G8 — consumer-side dedup по event_id (M02 CRITICAL #2).
-- RabbitMQ at-least-once → один и тот же event может прийти повторно.
-- Перед обработкой consumer пишет (consumer_id, event_id) сюда; UNIQUE
-- constraint на пару даёт атомарный insert-or-fail в JpaIdempotencyStore.
-- Cleanup-job раз в сутки удаляет записи старше 7 дней (Rabbit retention).

CREATE TABLE event_consumer_processed
(
    consumer_id  VARCHAR(64) NOT NULL,
    event_id     UUID        NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    PRIMARY KEY (consumer_id, event_id)
);

CREATE INDEX idx_ecp_cleanup
    ON event_consumer_processed (processed_at);
