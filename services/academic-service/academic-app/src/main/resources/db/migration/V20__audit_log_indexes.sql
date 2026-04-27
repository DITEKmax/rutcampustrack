-- ##
-- M16 G6 — индексы для audit_log. Отдельная миграция (vs V19__audit_log.sql)
-- потому что CONCURRENTLY не работает в transaction; `-- ##` отключает
-- Flyway transaction wrapper (см. CLAUDE.md DB rules + MigrationConcurrentlyTest).
--
-- На пустой таблице CONCURRENTLY бесполезен (lock на пустой таблице
-- мгновенный), но используем для консистентности с CLAUDE.md правилом
-- и чтобы будущие индексы (если добавим) не блокировали растущую таблицу.

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_audit_log_user_created
    ON audit_log (user_id, created_at DESC);

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_audit_log_action_created
    ON audit_log (action, created_at DESC);

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_audit_log_correlation
    ON audit_log (correlation_id);
