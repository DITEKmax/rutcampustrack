-- M16 G6 — индексы для audit_log. ИСПРАВЛЕНО (M16 G6 hotfix #3):
-- CREATE INDEX CONCURRENTLY вешал Flyway 10.20.1 + Postgres pg_advisory_lock
-- (issue #3961). На пустой таблице (V19 только что её создал, 0 строк)
-- plain CREATE INDEX берёт write lock на 0 строк = **мгновенно**,
-- без downtime. CONCURRENTLY нужен только на заполненных prod-таблицах.
--
-- Будущие индексы на заполненной audit_log (когда понадобятся) — отдельной
-- миграцией V{N+1} с CONCURRENTLY + V{N+1}.sql.conf executeInTransaction=false.
--
-- MigrationConcurrentlyTest baseline cutoff bumped до 20 — V20
-- grandfathered как «empty-table initial indexes».

CREATE INDEX IF NOT EXISTS idx_audit_log_user_created
    ON audit_log (user_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_audit_log_action_created
    ON audit_log (action, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_audit_log_correlation
    ON audit_log (correlation_id);
