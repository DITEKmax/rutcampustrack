-- The current placement applies prospectively. Original physical snapshots remain intact.
ALTER TABLE schedule_items ADD COLUMN placement_changed_at TIMESTAMPTZ;

-- An already-generated, dated assignment can still belong to an inactive
-- predecessor after a future-effective teacher replacement has committed.
CREATE FUNCTION constructor_has_matching_active_replacement_successor(source_id BIGINT, admitted_family BIGINT[])
RETURNS BOOLEAN LANGUAGE sql STABLE AS $$
    WITH RECURSIVE successors(id) AS (
        SELECT source_id
        UNION
        SELECT link.target_schedule_item_id FROM successors
          JOIN schedule_assignment_replacement_templates link ON link.source_schedule_item_id = successors.id
          JOIN schedule_assignment_replacement_operations operation ON operation.operation_id = link.operation_id
         WHERE link.source_was_active AND operation.state = 'COMMITTED'
           AND link.target_schedule_item_id = ANY(admitted_family)
    )
    SELECT EXISTS (
        SELECT 1 FROM successors
          JOIN schedule_items sibling ON sibling.id = successors.id
          JOIN schedule_items source ON source.id = source_id
         WHERE sibling.id <> source.id AND sibling.is_active
           AND (sibling.group_id, sibling.subject_id, sibling.semester_id, sibling.day_of_week,
                sibling.lesson_number, sibling.start_time, sibling.end_time, sibling.room, sibling.week_type)
                IS NOT DISTINCT FROM
               (source.group_id, source.subject_id, source.semester_id, source.day_of_week,
                source.lesson_number, source.start_time, source.end_time, source.room, source.week_type))
$$;

-- Keep the V35 natural-unassignment branch, the direct transfer guards and the legacy
-- room-only branch. Admit a new placement only under the active constructor write.
DO $migration$
DECLARE
    definition TEXT;
    original TEXT := $original$
              NEW.target_date <> source_row.date OR NEW.target_lesson_number <> source_row.lesson_number
              OR NEW.target_start_time <> source_row.start_time OR NEW.target_end_time <> source_row.end_time
              OR NEW.target_room IS NOT DISTINCT FROM source_row.room_snapshot
              OR (source_row.date + source_row.start_time) AT TIME ZONE 'Europe/Moscow' <= NEW.created_at
$original$;
    replacement TEXT := $replacement$
              (source_row.date + source_row.start_time) AT TIME ZONE 'Europe/Moscow' <= NEW.created_at
              OR NOT (
                  (NEW.target_date = source_row.date AND NEW.target_lesson_number = source_row.lesson_number
                   AND NEW.target_start_time = source_row.start_time AND NEW.target_end_time = source_row.end_time
                   AND NEW.target_room IS DISTINCT FROM source_row.room_snapshot)
                  OR (
                    operation_row.template_operation_id IS NOT DISTINCT FROM
                        NULLIF(current_setting('rutcampustrack.template_placement_operation_id', true), '')::uuid
                    AND (NEW.target_date + NEW.target_start_time) AT TIME ZONE 'Europe/Moscow' > NEW.created_at
                    AND date_trunc('week', NEW.target_date::timestamp) = date_trunc('week', source_row.date::timestamp)
                    AND (NEW.target_date, NEW.target_lesson_number, NEW.target_start_time, NEW.target_end_time)
                        IS DISTINCT FROM (source_row.date, source_row.lesson_number, source_row.start_time, source_row.end_time)
                    AND EXISTS (
                        SELECT 1 FROM schedule_items item
                          JOIN constructor_semester_materializations write
                            ON item.id = ANY(write.family_ids)
                         WHERE item.id = source_row.schedule_item_id AND item.id = occurrence_row.schedule_item_id
                           AND (item.is_active OR (occurrence_row.assignment_id IS NOT NULL
                                AND constructor_has_matching_active_replacement_successor(item.id, write.family_ids)))
                           AND item.placement_changed_at <= NEW.created_at
                           AND item.group_id = occurrence_row.group_id AND item.subject_id = occurrence_row.subject_id
                           AND item.semester_id = occurrence_row.semester_id
                           AND (occurrence_row.assignment_id IS NULL OR item.assignment_id = occurrence_row.assignment_id)
                           AND item.day_of_week = extract(isodow FROM NEW.target_date)::integer
                           AND item.lesson_number = NEW.target_lesson_number
                           AND item.start_time = NEW.target_start_time AND item.end_time = NEW.target_end_time
                           AND item.room IS NOT DISTINCT FROM NEW.target_room
                           AND (item.week_type = 'all'
                                OR item.week_type = 'odd' AND extract(week FROM NEW.target_date)::integer % 2 = 0
                                OR item.week_type = 'even' AND extract(week FROM NEW.target_date)::integer % 2 = 1)
                           AND write.operation_id =
                               NULLIF(current_setting('rutcampustrack.constructor_materialization_operation_id', true), '')::uuid
                           AND write.source_kind = 'USER' AND write.request_kind = 'BACKFILL' AND write.state = 'GENERATING'
                           AND write.actor_id = NEW.actor_id AND write.group_id = item.group_id
                           AND write.subject_id = item.subject_id AND write.semester_id = item.semester_id
                           AND NEW.target_date >= write.semester_from AND NEW.target_date < write.semester_until_exclusive)
                    AND NOT EXISTS (
                        SELECT 1 FROM lesson_transfer_operations manual
                         WHERE manual.occurrence_id = occurrence_row.id AND manual.template_operation_id IS NULL)))
$replacement$;
BEGIN
    SELECT pg_get_functiondef('validate_lesson_transfer_authority_insert()'::regprocedure) INTO definition;
    definition := replace(definition, E'\r\n', E'\n');
    original := trim(both E'\n' from replace(original, E'\r\n', E'\n'));
    replacement := trim(both E'\n' from replace(replacement, E'\r\n', E'\n'));
    IF strpos(definition, original) = 0 THEN
        RAISE EXCEPTION 'recurring placement transfer authority anchor changed';
    END IF;
    EXECUTE replace(definition, original, replacement);
END
$migration$;
