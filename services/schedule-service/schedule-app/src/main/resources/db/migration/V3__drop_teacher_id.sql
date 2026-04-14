-- V3__drop_teacher_id.sql
-- D-16: teacher_id удаляется из schedule_items. Препод видит журнал через
-- JOIN ScheduleItem × TeacherSubjectGroup WHERE TSG.teacher_id = :me
-- (не через привязку teacher_id на слоте).
ALTER TABLE schedule_items DROP COLUMN IF EXISTS teacher_id;
