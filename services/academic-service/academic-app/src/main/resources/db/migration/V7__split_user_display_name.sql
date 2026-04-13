-- V7__split_user_display_name.sql
-- Replace users.display_name with three separate columns: last_name, first_name, middle_name.
-- middle_name is optional (отчество может отсутствовать).

ALTER TABLE users
    ADD COLUMN last_name   VARCHAR(128),
    ADD COLUMN first_name  VARCHAR(128),
    ADD COLUMN middle_name VARCHAR(128);

-- Backfill: положим всё display_name во last_name, чтобы NOT NULL не сломал на существующих строках.
-- В проде данных пока нет, так что split на части не нужен.
UPDATE users
SET last_name  = COALESCE(display_name, login),
    first_name = COALESCE(display_name, login);

ALTER TABLE users
    ALTER COLUMN last_name  SET NOT NULL,
    ALTER COLUMN first_name SET NOT NULL;

ALTER TABLE users DROP COLUMN display_name;

CREATE INDEX idx_users_last_name ON users(last_name);
