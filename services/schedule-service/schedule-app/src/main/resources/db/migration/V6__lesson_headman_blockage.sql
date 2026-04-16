-- V6__lesson_headman_blockage.sql
-- v9.0: Headman hard-lock for geo-checkin (отличается от is_geo_blocked — то флаг преподавателя).
-- Когда is_blocked_by_headman=true, студенты НЕ могут отметиться по геолокации
-- на этой паре; староста проставляет посещаемость вручную через PWA/Web bulk-mark.

ALTER TABLE lessons
    ADD COLUMN is_blocked_by_headman BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN blocked_by_user_id    BIGINT,
    ADD COLUMN blocked_at            TIMESTAMPTZ;