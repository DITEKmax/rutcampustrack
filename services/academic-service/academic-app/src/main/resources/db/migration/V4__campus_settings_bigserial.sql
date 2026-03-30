-- V4__campus_settings_bigserial.sql
-- Fix campus_settings.id from SERIAL (INT4) to BIGSERIAL (INT8) to match CLAUDE.md convention
-- (BIGSERIAL / Long in Java). V1 incorrectly used SERIAL.
-- Converts the column type and re-creates the sequence as bigint.
ALTER TABLE campus_settings ALTER COLUMN id TYPE BIGINT;
ALTER SEQUENCE campus_settings_id_seq AS BIGINT;
