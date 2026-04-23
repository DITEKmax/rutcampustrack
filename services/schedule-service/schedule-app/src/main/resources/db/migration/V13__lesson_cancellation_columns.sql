-- M09 Группа 5 (02 P2-11/5, NEW-118) — full snapshot `lesson.cancelled` event.
--
-- До этой миграции entity fixed `cancel_reason VARCHAR(512)`, но не фиксирует
-- КТО и КОГДА отменил. Downstream consumers (attendance, notification-web)
-- вынуждены были делать second-look gRPC к academic/schedule для аудита —
-- лишняя нагрузка. Event со full snapshot устраняет read-amplification и
-- даёт audit trail в самом сообщении.
--
-- Nullable columns: существующие cancelled-строки не знают кем и когда
-- они были отменены — backfill данных нет, оставляем NULL. Новые
-- cancel-операции заполняют оба поля (LessonService.cancelLesson).

ALTER TABLE lessons
    ADD COLUMN cancelled_by BIGINT,
    ADD COLUMN cancelled_at TIMESTAMPTZ;

COMMENT ON COLUMN lessons.cancelled_by IS 'user_id старосты/админа, отменившего пару (NULL для legacy до M09 G5)';
COMMENT ON COLUMN lessons.cancelled_at IS 'момент отмены UTC (NULL для legacy до M09 G5)';
