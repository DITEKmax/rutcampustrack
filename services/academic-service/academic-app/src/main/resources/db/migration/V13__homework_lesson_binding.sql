-- Phase 61 / D-01, D-02, D-15: привязка ДЗ к конкретной паре через natural key.
-- Колонка lesson_id в V1 была зарезервирована, но так и не использовалась кодом.
-- Переходим на (group_id, lesson_date, lesson_number) — как attendance-service (Phase 60 D-15).
-- Данных в homeworks/homework_completions нет — безопасный TRUNCATE.

TRUNCATE homeworks, homework_completions RESTART IDENTITY CASCADE;

-- Сброс старой привязки по surrogate id
DROP INDEX IF EXISTS idx_hw_lesson;
ALTER TABLE homeworks DROP COLUMN IF EXISTS lesson_id;

-- Новая привязка: дата + номер пары (NOT NULL по D-01)
ALTER TABLE homeworks
    ADD COLUMN lesson_date   DATE NOT NULL,
    ADD COLUMN lesson_number INT  NOT NULL;

-- Индекс для недельных выборок ДЗ по группе (D-15)
CREATE INDEX idx_homeworks_group_date ON homeworks(group_id, lesson_date);
