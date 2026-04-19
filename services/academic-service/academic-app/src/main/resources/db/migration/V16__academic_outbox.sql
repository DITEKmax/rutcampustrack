-- M02 Группа 3 — Reliable eventing outbox для academic-service.
-- Listener'ы записывают события сюда В ТОЙ ЖЕ @Transactional что и доменная
-- операция. OutboxPublisherJob (@Scheduled + @SchedulerLock) читает pending
-- rows, шлёт в Rabbit, помечает sent. OutboxCleanupJob (cron 3am) удаляет
-- sent rows старше 7д (NEW-7 retention).

CREATE TABLE academic_outbox
(
    id          BIGSERIAL PRIMARY KEY,
    event_type  VARCHAR(128) NOT NULL,
    payload     JSONB        NOT NULL,
    status      VARCHAR(16)  NOT NULL DEFAULT 'pending',
    retry_count INTEGER      NOT NULL DEFAULT 0,
    last_error  TEXT,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    sent_at     TIMESTAMPTZ,

    CONSTRAINT academic_outbox_status_chk
        CHECK (status IN ('pending', 'sent', 'failed'))
);

CREATE INDEX idx_academic_outbox_pending
    ON academic_outbox (created_at)
    WHERE status = 'pending';

CREATE INDEX idx_academic_outbox_sent_cleanup
    ON academic_outbox (sent_at)
    WHERE status = 'sent';
