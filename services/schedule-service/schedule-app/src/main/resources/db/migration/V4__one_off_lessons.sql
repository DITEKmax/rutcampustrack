-- V4__one_off_lessons.sql
-- Phase 60-03: разовые пары (HEADMAN вставляет разовую пару на конкретную дату).
-- D-04: таблица schedule_one_off_lessons без teacher_id (см. D-16).
-- D-21: UNIQUE(group_id, date, lesson_number) — двойной клик → 409 Conflict.
-- D-23: semester_id NOT NULL — определяется автоматически по date.
BEGIN;

CREATE TABLE schedule_one_off_lessons (
    id              BIGSERIAL PRIMARY KEY,
    group_id        BIGINT NOT NULL,
    subject_id      BIGINT NOT NULL,
    semester_id     BIGINT NOT NULL,
    date            DATE NOT NULL,
    lesson_number   SMALLINT NOT NULL CHECK (lesson_number BETWEEN 1 AND 8),
    classroom       VARCHAR(64),
    created_by      BIGINT NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_one_off_slot UNIQUE (group_id, date, lesson_number)
);

CREATE INDEX idx_one_off_group_date ON schedule_one_off_lessons(group_id, date);

COMMIT;
