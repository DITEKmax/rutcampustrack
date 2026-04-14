-- V12__subjects_group_id.sql
-- Phase 60-01 / D-01: предмет принадлежит группе. Добавляем subjects.group_id NOT NULL
-- с FK на groups(id). Тестовые данные удаляются, т.к. в проде таблицы subjects и
-- teacher_subject_groups пока не использовались и ссылочных данных в других модулях нет.
BEGIN;

-- Шаг 1: очистка зависимых данных (FK teacher_subject_groups.subject_id → subjects.id)
DELETE FROM teacher_subject_groups;
DELETE FROM subjects;

-- Шаг 2: добавить колонку group_id NOT NULL + FK на groups(id)
ALTER TABLE subjects
    ADD COLUMN group_id BIGINT NOT NULL;

ALTER TABLE subjects
    ADD CONSTRAINT fk_subjects_group
        FOREIGN KEY (group_id) REFERENCES groups(id);

CREATE INDEX idx_subjects_group ON subjects(group_id);

COMMIT;
