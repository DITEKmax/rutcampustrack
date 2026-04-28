-- Composite index supporting ORDER BY last_name, first_name, middle_name with
-- ICU collation (V21). Partial — archived users excluded since
-- @SQLRestriction("status <> 'archived'") on User entity already filters them.
--
-- CONCURRENTLY required because users is a live prod table. The companion
-- V22__user_name_sort_index.sql.conf sets executeInTransaction=false so
-- Flyway runs this without wrapping it in a transaction (CONCURRENTLY cannot
-- run inside one). The earlier `-- ##` marker style was insufficient on
-- Flyway 10.20.1 (issue #3961, see V20 hotfix comment).
-- See MigrationConcurrentlyTest + CLAUDE.md "База данных".

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_users_lastname_icu
    ON users (
        last_name COLLATE "ru_icu",
        first_name COLLATE "ru_icu",
        middle_name COLLATE "ru_icu"
    )
    WHERE status <> 'archived';
