-- M05 Группа 1 (P2-10/1, DECISION D1).
--
-- Composite partial index для hot query
-- `LessonRepository.findByScheduleItemIdInAndDateBetweenAndStatusIn`
-- (через LessonService.getLessonsForGroup — week-journal студента
-- и преподавателя).
--
-- Шаблон запроса:
--   SELECT l.* FROM lessons l
--    WHERE l.schedule_item_id IN (<itemIds of group/semester>)
--      AND l.date BETWEEN :from AND :to
--      AND l.status IN ('planned','active','closed');
--
-- До M05: planner использовал idx_lessons_date (weekly range), Hash
-- Join по schedule_item_id. При 100k+ lessons/семестр прогрессивно
-- медленнее.
--
-- Почему partial `WHERE status != 'cancelled'`:
--   cancelled lessons в week-journal не нужны (отображаются отдельно
--   как «отменена»). Partial-index сокращает размер структуры ~5%
--   (cancelled ~5% от общего объёма в seed) и держит hot rows вместе.
--
-- EXPLAIN baseline — см. docs/performance-indexes.md, Q1.
-- Деталей по rationale — DECISIONS.md D1.

CREATE INDEX IF NOT EXISTS idx_lessons_item_date
    ON lessons (schedule_item_id, date)
    WHERE status != 'cancelled';
