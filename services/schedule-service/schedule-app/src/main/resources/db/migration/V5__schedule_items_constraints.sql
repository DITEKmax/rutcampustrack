-- V5__schedule_items_constraints.sql
--
-- Two related fixes for schedule_items:
--
-- 1) day_of_week CHECK was BETWEEN 0 AND 5. Historic code persisted Mon=0
--    (see comment in OneOffLessonControllerIT), but the v9 API contract
--    accepts dayOfWeek 1..7 (Mon..Sun). Saturday (6) and Sunday (7) failed
--    with a 500. Widen the constraint to 0..7 so both legacy 0=Mon rows and
--    new 1..7 contract values are accepted (no data migration needed).
--
-- 2) The original UNIQUE (group_id, day_of_week, lesson_number, week_type,
--    semester_id) included soft-deleted (is_active=false) rows. After a
--    headman deleted a slot and tried to recreate one with a different
--    week_type, PostgreSQL rejected it because the historical row still
--    occupied the unique key. Switch to a PARTIAL UNIQUE INDEX scoped to
--    is_active=true so the constraint only prevents real duplicates.
--    This also makes the «ODD + EVEN in the same cell» combination work
--    after any soft-delete history.

ALTER TABLE schedule_items
    DROP CONSTRAINT IF EXISTS schedule_items_day_of_week_check;

ALTER TABLE schedule_items
    ADD CONSTRAINT schedule_items_day_of_week_check
    CHECK (day_of_week BETWEEN 0 AND 7);

ALTER TABLE schedule_items
    DROP CONSTRAINT IF EXISTS schedule_items_group_id_day_of_week_lesson_number_week_type_se_key;
ALTER TABLE schedule_items
    DROP CONSTRAINT IF EXISTS schedule_items_group_id_day_of_week_lesson_number_week_type_key;

-- Some PostgreSQL versions auto-name the constraint differently; drop both
-- by enumerating the conventional name + a defensive fallback.
DO $$
DECLARE
    cname text;
BEGIN
    SELECT conname INTO cname
    FROM pg_constraint
    WHERE conrelid = 'schedule_items'::regclass
      AND contype = 'u'
      AND pg_get_constraintdef(oid) LIKE '%group_id, day_of_week, lesson_number, week_type, semester_id%';
    IF cname IS NOT NULL THEN
        EXECUTE format('ALTER TABLE schedule_items DROP CONSTRAINT %I', cname);
    END IF;
END $$;

CREATE UNIQUE INDEX IF NOT EXISTS uq_schedule_items_active_slot
    ON schedule_items (group_id, day_of_week, lesson_number, week_type, semester_id)
    WHERE is_active = true;
