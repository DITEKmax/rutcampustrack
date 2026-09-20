BEGIN;
SET LOCAL TIME ZONE 'Europe/Moscow';

INSERT INTO schedule_items (
    group_id, subject_id, semester_id, day_of_week, lesson_number,
    start_time, end_time, week_type, room, is_active
)
VALUES
    (1, 1, 1, 0, 1,
     TIME '00:00:00',
     TIME '23:59:59',
     'all', 'РТ-01', TRUE),
    (1, 1, 1, 0, 2,
     TIME '00:00:00',
     TIME '23:59:59',
     'all', 'РТ-02', TRUE);

INSERT INTO lessons (schedule_item_id, date, status, is_geo_blocked)
SELECT id, CURRENT_DATE, 'active', FALSE
FROM schedule_items
WHERE group_id = 1
  AND semester_id = 1
  AND lesson_number IN (1, 2);

COMMIT;

SELECT l.id AS lesson_id,
       si.lesson_number,
       l.date,
       si.start_time,
       si.end_time,
       l.status
FROM lessons l
JOIN schedule_items si ON si.id = l.schedule_item_id
WHERE si.group_id = 1
  AND si.semester_id = 1
ORDER BY si.lesson_number;
