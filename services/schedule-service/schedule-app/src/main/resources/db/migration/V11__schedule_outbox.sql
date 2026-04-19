-- M02 Группа 3 — Reliable eventing outbox для schedule-service.
-- Listener'ы записывают события сюда В ТОЙ ЖЕ @Transactional что и доменная
-- операция. OutboxPublisherJob (@Scheduled + @SchedulerLock) читает pending
-- rows, шлёт в Rabbit, помечает sent. OutboxCleanupJob (cron 3am) удаляет
-- sent rows старше 7д (NEW-7 retention).
--
-- Почему отдельная таблица per service (не shared DB):
--   academic_db и schedule_db — разные PostgreSQL БД, схемы изолированы.
--   shared-outbox модуль (NEW-6) реюзает логику через JpaOutboxStorage,
--   но owner'ом таблицы остаётся service.

CREATE TABLE schedule_outbox
(
    id          BIGSERIAL PRIMARY KEY,
    event_type  VARCHAR(128) NOT NULL,
    payload     JSONB        NOT NULL,
    status      VARCHAR(16)  NOT NULL DEFAULT 'pending',
    retry_count INTEGER      NOT NULL DEFAULT 0,
    last_error  TEXT,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    sent_at     TIMESTAMPTZ,

    CONSTRAINT schedule_outbox_status_chk
        CHECK (status IN ('pending', 'sent', 'failed'))
);

-- FIFO-порядок чтения publisher'ом + фильтр по status.
-- Partial index на pending — основной workload (sent строки скоро удалятся).
CREATE INDEX idx_schedule_outbox_pending
    ON schedule_outbox (created_at)
    WHERE status = 'pending';

-- Cleanup job фильтрует по status='sent' AND sent_at < now()-7d.
CREATE INDEX idx_schedule_outbox_sent_cleanup
    ON schedule_outbox (sent_at)
    WHERE status = 'sent';
