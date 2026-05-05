-- Idempotency marker for the near-end attendance reminder.
-- LessonReminderJob publishes lesson.reminder phase=near_end when an ACTIVE
-- lesson reaches end_time - 5 minutes and marks this column in the same
-- transaction as the outbox write.
ALTER TABLE lessons
    ADD COLUMN reminder_near_end_sent_at TIMESTAMPTZ;
