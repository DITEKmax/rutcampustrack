-- Поле для idempotent публикации midpoint-напоминаний (NOTIF unification).
-- LessonReminderJob раз в минуту проходит по ACTIVE lessons, у которых
-- now() ≥ midpoint(start, end) и reminder_midpoint_sent_at IS NULL,
-- публикует lesson.reminder и проставляет reminder_midpoint_sent_at = now()
-- одной транзакцией.
--
-- nullable, без default — instant ALTER в PG12+; не блокирует таблицу.
ALTER TABLE lessons
    ADD COLUMN reminder_midpoint_sent_at TIMESTAMPTZ;
