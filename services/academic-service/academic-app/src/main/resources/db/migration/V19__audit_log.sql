-- M16 G6 — audit log table для @AdminAction aspect.
--
-- Таблица фиксирует ADMIN-действия (archive user, create group, etc.) с
-- attribution: кто, когда, какой ресурс. До v1 не пишем before/after diff
-- (deep cloning + JSON diff = отдельная задача), но добавили JSONB extras
-- для будущего расширения без миграции схемы.
--
-- Хранится в academic_db потому что academic — единственный сервис с
-- ADMIN-actions (М01 архитектура). Если в future schedule/attendance
-- понадобятся свои @AdminAction, они предоставят свой AuditLogStorage
-- impl с собственной таблицей в своей БД (см. DECISIONS § D3 SPI pattern).
--
-- Indexes — отдельная миграция V20 с CONCURRENTLY (см. CLAUDE.md).

CREATE TABLE IF NOT EXISTS audit_log (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT,
    action          VARCHAR(64) NOT NULL,
    target_type     VARCHAR(64),
    target_id       BIGINT,
    correlation_id  VARCHAR(64),
    extras          JSONB,
    succeeded       BOOLEAN NOT NULL DEFAULT TRUE,
    error_message   VARCHAR(512),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE audit_log IS
    'M16 G6 — ADMIN-action audit. user_id NULL допустим (system-action / unauthenticated path). '
    'Retention: TBD (пока без cleanup job, при appearance disk pressure добавим). '
    'See docs/operations/runbooks/audit-log.md.';
