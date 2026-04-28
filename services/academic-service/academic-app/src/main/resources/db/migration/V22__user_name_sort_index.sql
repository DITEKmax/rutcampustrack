-- Composite index supporting ORDER BY last_name, first_name, middle_name with
-- ICU collation (V21). Partial — archived users excluded since
-- @SQLRestriction("status <> 'archived'") on User entity already filters them.
--
-- CONCURRENTLY НЕ используется по той же причине, что и в V20 (audit_log):
-- Flyway 10.20.1 (Spring Boot 3.4.1 BOM) имеет регрессию issue #3961 —
-- CREATE INDEX CONCURRENTLY вешает pg_advisory_lock и Spring context никогда
-- не запускается, что валит CI/E2E (контейнер unhealthy через 3 минуты).
--
-- Это ПРИЕМЛЕМО: в актуальном prod (v0.0.0-alpha.16, ~5-30 пользователей на
-- группу × ~10 групп = ≤300 строк в users) plain CREATE INDEX берёт write
-- lock на ~миллисекунды. Когда users перерастёт ~10K строк и блокировка
-- начнёт быть заметной — выпустить V{N}__reindex_users_concurrently.sql
-- после апгрейда Flyway до версии без issue #3961 (≥10.21).
--
-- См. M16 G6 hotfix #3 (2026-04-27) — там тот же подход применён к V20.

CREATE INDEX IF NOT EXISTS idx_users_lastname_icu
    ON users (
        last_name COLLATE "ru_icu",
        first_name COLLATE "ru_icu",
        middle_name COLLATE "ru_icu"
    )
    WHERE status <> 'archived';
