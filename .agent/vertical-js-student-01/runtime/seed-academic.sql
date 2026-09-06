BEGIN;

UPDATE semesters SET is_active = FALSE WHERE is_active = TRUE;
UPDATE semesters
SET name = 'JS-STUDENT-01 runtime',
    date_from = CURRENT_DATE - 30,
    date_to = CURRENT_DATE + 30,
    is_active = TRUE
WHERE id = 1;

UPDATE users
SET is_headman = FALSE,
    group_id = 1,
    status = 'active'
WHERE login = 'student';

INSERT INTO subjects (name, type, group_id)
SELECT 'Runtime геопроверка', 'lecture', 1
WHERE NOT EXISTS (SELECT 1 FROM subjects WHERE id = 1);

COMMIT;

SELECT id AS student_id, group_id, is_headman
FROM users
WHERE login = 'student';
SELECT id AS semester_id, date_from, date_to, is_active
FROM semesters
WHERE id = 1;
SELECT id AS subject_id, name
FROM subjects
WHERE id = 1;
