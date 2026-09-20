\set ON_ERROR_STOP on

-- The runner resolves every id from the owned Academic fixture and passes
-- decimal values with psql -v.  Refuse an unbound or non-decimal invocation;
-- no repository ids are valid defaults for this V17 seed.
\if :{?student_id}
\else
  \echo 'student_id is required'
  \quit 3
\endif
\if :{?group_id}
\else
  \echo 'group_id is required'
  \quit 3
\endif
\if :{?semester_id}
\else
  \echo 'semester_id is required'
  \quit 3
\endif
\if :{?subject_id}
\else
  \echo 'subject_id is required'
  \quit 3
\endif
\if :{?homework_id}
\else
  \echo 'homework_id is required'
  \quit 3
\endif
\if :{?teacher_id}
\else
  \echo 'teacher_id is required'
  \quit 3
\endif
\if :{?assignment_id}
\else
  \echo 'assignment_id is required'
  \quit 3
\endif
\if :{?binding_id}
\else
  \echo 'binding_id is required'
  \quit 3
\endif

SELECT CASE WHEN :'student_id' ~ '^[1-9][0-9]*$' THEN :'student_id'::bigint END AS student_id \gset input_
SELECT CASE WHEN :'group_id' ~ '^[1-9][0-9]*$' THEN :'group_id'::bigint END AS group_id \gset input_
SELECT CASE WHEN :'semester_id' ~ '^[1-9][0-9]*$' THEN :'semester_id'::bigint END AS semester_id \gset input_
SELECT CASE WHEN :'subject_id' ~ '^[1-9][0-9]*$' THEN :'subject_id'::bigint END AS subject_id \gset input_
SELECT CASE WHEN :'homework_id' ~ '^[1-9][0-9]*$' THEN :'homework_id'::bigint END AS homework_id \gset input_
SELECT CASE WHEN :'teacher_id' ~ '^[1-9][0-9]*$' THEN :'teacher_id'::bigint END AS teacher_id \gset input_
SELECT CASE WHEN :'assignment_id' ~ '^[1-9][0-9]*$' THEN :'assignment_id'::bigint END AS assignment_id \gset input_
SELECT CASE WHEN :'binding_id' ~ '^[1-9][0-9]*$' THEN :'binding_id'::bigint END AS binding_id \gset input_

\if :{?input_student_id}
\else
  \echo 'all fixture ids must be positive decimal values'
  \quit 3
\endif
\if :{?input_group_id}
\else
  \echo 'all fixture ids must be positive decimal values'
  \quit 3
\endif
\if :{?input_semester_id}
\else
  \echo 'all fixture ids must be positive decimal values'
  \quit 3
\endif
\if :{?input_subject_id}
\else
  \echo 'all fixture ids must be positive decimal values'
  \quit 3
\endif
\if :{?input_homework_id}
\else
  \echo 'all fixture ids must be positive decimal values'
  \quit 3
\endif
\if :{?input_teacher_id}
\else
  \echo 'all fixture ids must be positive decimal values'
  \quit 3
\endif
\if :{?input_assignment_id}
\else
  \echo 'all fixture ids must be positive decimal values'
  \quit 3
\endif
\if :{?input_binding_id}
\else
  \echo 'all fixture ids must be positive decimal values'
  \quit 3
\endif

BEGIN;

SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '30s';

SELECT (
    to_regclass('public.lesson_occurrences') IS NOT NULL
    AND to_regclass('public.lesson_homework_bindings') IS NOT NULL
) AS v17_ready \gset preflight_
\if :preflight_v17_ready
\else
  \echo 'Schedule V17 occurrence and binding tables are required'
  ROLLBACK
  \quit 3
\endif

SELECT NOT EXISTS (
    SELECT 1 FROM schedule_items WHERE assignment_id = :input_assignment_id
) AS assignment_free \gset preflight_
\if :preflight_assignment_free
\else
  \echo 'assignment_id already belongs to another fixture'
  ROLLBACK
  \quit 3
\endif

SELECT NOT EXISTS (
    SELECT 1 FROM lessons WHERE date = DATE '2026-09-21' AND group_id = :input_group_id
) AS group_date_free \gset preflight_
\if :preflight_group_date_free
\else
  \echo 'the owned schedule database already contains a lesson for this group/date'
  ROLLBACK
  \quit 3
\endif

-- V17 keeps assignment provenance on the weekly slot and an immutable
-- occurrence snapshot on each physical lesson.  Lesson generation uses the
-- service's ISO convention (1=Monday..7=Sunday) for the current date.
INSERT INTO schedule_items (
    assignment_id,
    group_id,
    subject_id,
    semester_id,
    day_of_week,
    lesson_number,
    start_time,
    end_time,
    week_type,
    room,
    is_active
)
VALUES (
    :input_assignment_id,
    :input_group_id,
    :input_subject_id,
    :input_semester_id,
    EXTRACT(ISODOW FROM DATE '2026-09-21')::smallint,
    1,
    TIME '09:00',
    TIME '10:30',
    'all',
    'V17-A',
    TRUE
)
RETURNING id AS schedule_item_id \gset item_one_

INSERT INTO schedule_items (
    assignment_id,
    group_id,
    subject_id,
    semester_id,
    day_of_week,
    lesson_number,
    start_time,
    end_time,
    week_type,
    room,
    is_active
)
VALUES (
    :input_assignment_id,
    :input_group_id,
    :input_subject_id,
    :input_semester_id,
    EXTRACT(ISODOW FROM DATE '2026-09-21')::smallint,
    2,
    TIME '11:00',
    TIME '12:30',
    'all',
    'V17-B',
    TRUE
)
RETURNING id AS schedule_item_id \gset item_two_

INSERT INTO lesson_occurrences (
    schedule_item_id,
    occurrence_date,
    assignment_id,
    group_id,
    subject_id,
    semester_id,
    assigned_teacher_id,
    lesson_type,
    generation,
    revision
)
VALUES (
    :item_one_schedule_item_id,
    DATE '2026-09-21',
    :input_assignment_id,
    :input_group_id,
    :input_subject_id,
    :input_semester_id,
    :input_teacher_id,
    'lecture',
    1,
    1
)
RETURNING id AS occurrence_id \gset occurrence_one_

INSERT INTO lesson_occurrences (
    schedule_item_id,
    occurrence_date,
    assignment_id,
    group_id,
    subject_id,
    semester_id,
    assigned_teacher_id,
    lesson_type,
    generation,
    revision
)
VALUES (
    :item_two_schedule_item_id,
    DATE '2026-09-21',
    :input_assignment_id,
    :input_group_id,
    :input_subject_id,
    :input_semester_id,
    :input_teacher_id,
    'lecture',
    1,
    1
)
RETURNING id AS occurrence_id \gset occurrence_two_

INSERT INTO lessons (
    schedule_item_id,
    occurrence_id,
    assignment_id,
    group_id,
    subject_id,
    semester_id,
    assigned_teacher_id,
    lesson_type,
    lesson_number,
    start_time,
    end_time,
    room_snapshot,
    generation,
    revision,
    date,
    status
)
VALUES (
    :item_one_schedule_item_id,
    :occurrence_one_occurrence_id,
    :input_assignment_id,
    :input_group_id,
    :input_subject_id,
    :input_semester_id,
    :input_teacher_id,
    'lecture',
    1,
    TIME '09:00',
    TIME '10:30',
    'V17-A',
    1,
    1,
    DATE '2026-09-21',
    'planned'
)
RETURNING id AS lesson_id \gset lesson_one_

INSERT INTO lessons (
    schedule_item_id,
    occurrence_id,
    assignment_id,
    group_id,
    subject_id,
    semester_id,
    assigned_teacher_id,
    lesson_type,
    lesson_number,
    start_time,
    end_time,
    room_snapshot,
    generation,
    revision,
    date,
    status
)
VALUES (
    :item_two_schedule_item_id,
    :occurrence_two_occurrence_id,
    :input_assignment_id,
    :input_group_id,
    :input_subject_id,
    :input_semester_id,
    :input_teacher_id,
    'lecture',
    2,
    TIME '11:00',
    TIME '12:30',
    'V17-B',
    1,
    1,
    DATE '2026-09-21',
    'planned'
)
RETURNING id AS lesson_id \gset lesson_two_

-- current_lesson_id is deferred in V17 because the immutable occurrence and
-- physical lesson are created in one transaction.  The binding is likewise
-- an opaque cross-service reference: the homework id is validated by
-- Academic, while the schedule database stores its durable provenance.
UPDATE lesson_occurrences
SET current_lesson_id = :lesson_one_lesson_id
WHERE id = :occurrence_one_occurrence_id;

UPDATE lesson_occurrences
SET current_lesson_id = :lesson_two_lesson_id
WHERE id = :occurrence_two_occurrence_id;

INSERT INTO lesson_homework_bindings (
    binding_id,
    occurrence_id,
    current_lesson_id,
    homework_id,
    actor_id,
    request_key,
    payload_hash,
    state,
    revision
)
VALUES (
    :input_binding_id,
    :occurrence_one_occurrence_id,
    :lesson_one_lesson_id,
    :input_homework_id,
    :input_teacher_id,
    gen_random_uuid(),
    decode(repeat('ab', 32), 'hex'),
    'ACTIVE',
    1
)
RETURNING binding_id AS binding_id \gset binding_

SELECT setval(
    pg_get_serial_sequence('lesson_homework_bindings', 'binding_id'),
    (SELECT MAX(binding_id) FROM lesson_homework_bindings),
    TRUE
);

SELECT (
    (SELECT COUNT(*) FROM schedule_items WHERE assignment_id = :input_assignment_id) = 2
    AND (SELECT COUNT(*) FROM lessons WHERE assignment_id = :input_assignment_id AND date = DATE '2026-09-21') = 2
    AND (SELECT COUNT(*) FROM lesson_occurrences WHERE assignment_id = :input_assignment_id AND occurrence_date = DATE '2026-09-21') = 2
    AND (SELECT COUNT(*) FROM lesson_homework_bindings WHERE homework_id = :input_homework_id AND binding_id = :input_binding_id AND state = 'ACTIVE') = 1
) AS cardinality_ok \gset final_
\if :final_cardinality_ok
\else
  \echo 'V17 browser fixture cardinality is invalid'
  ROLLBACK
  \quit 3
\endif

COMMIT;

SELECT
    :input_assignment_id AS assignment_id,
    :item_one_schedule_item_id AS first_schedule_item_id,
    :item_two_schedule_item_id AS second_schedule_item_id,
    :lesson_one_lesson_id AS first_lesson_id,
    :lesson_two_lesson_id AS second_lesson_id,
    :binding_binding_id AS binding_id;
