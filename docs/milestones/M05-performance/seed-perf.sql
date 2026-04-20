-- M05 Performance baseline seed.
--
-- Назначение: подготовить реалистичный dataset для EXPLAIN ANALYZE
-- hot queries до/после добавления composite indexes (Группа 1, D1/D2/D4).
--
-- Запуск:
--   schedule_db   → section 1
--   academic_db   → section 2
--   Mongo attendance (late_checkin_requests) — отдельно через seed-perf.js,
--   SQL здесь не применим.
--
-- Idempotent: использует ON CONFLICT DO NOTHING + DELETE перед reseed'ом
-- для группы seed-id >= 900000. Rows, созданные приложением, не трогаются.
--
-- Объём:
--   schedule: 20 групп × 30 schedule_items × ~20 недель = ~12 000 lessons.
--   academic: 20 групп + 500 students + 4 семестра, 30 TSG-assignments ×
--             4 семестра = 120 rows, ~2000 homeworks.
--
-- Очистка после M05 измерений:
--   schedule_db:  DELETE FROM lessons WHERE schedule_item_id IN
--                   (SELECT id FROM schedule_items WHERE group_id >= 900000);
--                 DELETE FROM schedule_items WHERE group_id >= 900000;
--   academic_db:  DELETE FROM homeworks WHERE group_id >= 900000;
--                 DELETE FROM teacher_subject_groups WHERE group_id >= 900000;
--                 DELETE FROM users WHERE id >= 900000;
--                 DELETE FROM groups WHERE id >= 900000;

\echo '=== M05 seed started ==='

-- ============================================================
-- SECTION 1: schedule_db (PostgreSQL — run on schedule_db)
-- ============================================================

-- 1.1 Очистка существующих seed-данных (idempotent reseed).
DELETE FROM lessons
 WHERE schedule_item_id IN (
    SELECT id FROM schedule_items WHERE group_id >= 900000
 );
DELETE FROM schedule_items WHERE group_id >= 900000;
DELETE FROM schedule_one_off_lessons WHERE group_id >= 900000;

-- 1.2 schedule_items — 20 групп (id 900001..900020), 30 уроков в неделю
-- каждая группа, 1 активный семестр id=1.
-- Уроки распределены пн-пт (day_of_week 1..5), 1-6 lesson_number,
-- week_type всегда 'all' для простоты (чтобы не возиться с parity).

-- NB: V3 drop column schedule_items.teacher_id — teacher уже не
-- хранится в schedule. Связь teacher↔group_subject живёт в academic
-- teacher_subject_groups. Не вставляем teacher_id.
INSERT INTO schedule_items (
    id, group_id, subject_id, semester_id,
    day_of_week, lesson_number, start_time, end_time,
    week_type, room, is_active, created_at
)
SELECT
    900000 + (g.group_offset * 30 + s.slot_offset) AS id,
    900000 + g.group_offset               AS group_id,
    1 + (s.slot_offset % 15)              AS subject_id,
    1                                     AS semester_id,
    1 + (s.slot_offset % 5)               AS day_of_week,
    1 + (s.slot_offset % 6)               AS lesson_number,
    TIME '09:00' + ((s.slot_offset % 6) * INTERVAL '1 hour 30 minutes') AS start_time,
    TIME '09:00' + ((s.slot_offset % 6) * INTERVAL '1 hour 30 minutes')
        + INTERVAL '1 hour 30 minutes'    AS end_time,
    'all'::week_type                      AS week_type,
    'A-' || (100 + (s.slot_offset % 20)) AS room,
    TRUE                                  AS is_active,
    NOW() - INTERVAL '200 days'           AS created_at
FROM generate_series(1, 20) AS g(group_offset),
     generate_series(1, 30) AS s(slot_offset)
ON CONFLICT (id) DO NOTHING;

-- 1.3 lessons — 20 недель × каждый schedule_item (один конкретный
-- экземпляр на неделю). 600 schedule_items × 20 недель = 12 000 lessons.
-- Статусы распределены: 40% planned (будущие), 40% closed (прошлые),
-- 15% active (текущая неделя), 5% cancelled.

INSERT INTO lessons (
    schedule_item_id, date, status, is_geo_blocked,
    cancel_reason, created_at, closed_at,
    is_blocked_by_headman
)
SELECT
    si.id AS schedule_item_id,
    (CURRENT_DATE - INTERVAL '70 days')
        + ((w.week_offset - 1) * INTERVAL '7 days')
        + ((si.day_of_week - 1) * INTERVAL '1 day') AS date,
    CASE
        WHEN w.week_offset <= 8 THEN 'closed'::lesson_status
        WHEN w.week_offset = 9 THEN 'active'::lesson_status
        WHEN w.week_offset = 10 AND (si.id % 20) = 0 THEN 'cancelled'::lesson_status
        ELSE 'planned'::lesson_status
    END AS status,
    FALSE,
    NULL,
    NOW() - INTERVAL '100 days',
    CASE
        WHEN w.week_offset <= 8 THEN NOW() - INTERVAL '60 days'
        ELSE NULL
    END AS closed_at,
    FALSE
FROM schedule_items si,
     generate_series(1, 20) AS w(week_offset)
WHERE si.group_id >= 900000
ON CONFLICT (schedule_item_id, date) DO NOTHING;

-- 1.4 ANALYZE — обновить статистику для planner'а.
ANALYZE lessons;
ANALYZE schedule_items;

\echo '=== schedule seed complete ==='
-- Ожидаемая статистика (схема-wide):
-- schedule_items:  600 seed + существующие ~10
-- lessons:         12000 seed + существующие ~N

-- ============================================================
-- SECTION 2: academic_db (PostgreSQL — run on academic_db)
-- ============================================================

-- 2.1 Reseed — очистка seed-данных в правильном FK-порядке.
-- Children первыми: homework_completions → homeworks → TSG →
-- subjects (FK на groups) → student_group_history → attendance_thresholds
-- → headman_assistants → password_reset_tokens → users → groups.
DELETE FROM homework_completions WHERE homework_id IN
    (SELECT id FROM homeworks WHERE group_id >= 900000);
DELETE FROM homeworks WHERE group_id >= 900000;
DELETE FROM teacher_subject_groups WHERE group_id >= 900000;
DELETE FROM subjects WHERE group_id >= 900000;
DELETE FROM student_group_history WHERE user_id >= 900000;
DELETE FROM attendance_thresholds WHERE group_id >= 900000;
DELETE FROM headman_assistants WHERE group_id >= 900000;
DELETE FROM password_reset_tokens WHERE user_id >= 900000;
DELETE FROM users WHERE id >= 900000;
DELETE FROM groups WHERE id >= 900000;

-- 2.2 Semesters — 4 (2 активных прошлых + 1 текущий + 1 будущий).
-- Предполагаем что из production уже есть id=1 (активный) и опционально
-- другие. Вставляем только если id >= 900001.

-- NB: V10__semesters_no_overlap добавил EXCLUDE constraint на
-- daterange(date_from, date_to). Production уже имеет «Spring 2026» id=1.
-- Поэтому берём 3 исторических семестра, не пересекающихся с prod id=1.
-- Для EXPLAIN по (group_id, semester_id) этого достаточно — selectivity
-- semester колонки даёт 1/4 (включая prod id=1).
INSERT INTO semesters (id, name, date_from, date_to, is_active, created_at)
VALUES
    (900001, 'M05 perf seed semester 2024/25 fall',
     DATE '2024-09-01', DATE '2025-01-31', FALSE, NOW()),
    (900002, 'M05 perf seed semester 2025 spring',
     DATE '2025-02-01', DATE '2025-06-30', FALSE, NOW()),
    (900003, 'M05 perf seed semester 2025/26 fall',
     DATE '2025-09-01', DATE '2026-01-31', FALSE, NOW())
ON CONFLICT (id) DO NOTHING;

-- 2.3 Groups (20 штук). V8 дропнул code, name теперь UNIQUE VARCHAR(32).
-- Формат имени — любой, V8 не добавил CHECK-constraint (pattern
-- валидируется в сервисном слое через GroupNameValidator).
INSERT INTO groups (id, name, is_active, created_at)
SELECT
    900000 + g.group_offset       AS id,
    'М05-G' || g.group_offset     AS name,
    TRUE,
    NOW() - INTERVAL '200 days'
FROM generate_series(1, 20) AS g(group_offset)
ON CONFLICT (id) DO NOTHING;

-- 2.4 Subjects — V12 добавил subjects.group_id NOT NULL, поэтому
-- каждой группе свой набор subjects. 20 групп × 15 subjects = 300 rows.
-- id = 900000 + group_offset * 100 + subj_offset, чтобы TSG ссылался
-- на нужный subject для своей group.
-- (DELETE уже в секции 2.1)

INSERT INTO subjects (id, name, type, group_id, created_at)
SELECT
    900000 + (g.group_offset * 100 + s.subj_offset) AS id,
    'M05 subj g' || g.group_offset || ' #' || s.subj_offset AS name,
    'lecture'::subject_type,
    900000 + g.group_offset                         AS group_id,
    NOW()
FROM generate_series(1, 20) AS g(group_offset),
     generate_series(1, 15) AS s(subj_offset)
ON CONFLICT (id) DO NOTHING;

-- 2.5 Users: 20 teachers (id 900101..900120) + 500 students
-- (id 900501..901000). 25 students per group.

-- Teachers
INSERT INTO users (
    id, login, last_name, first_name, role, status,
    created_at, updated_at
)
SELECT
    900100 + t.t_offset,
    'teacher' || (900100 + t.t_offset),
    'M05Teacher' || t.t_offset,
    'Имя' || t.t_offset,
    'teacher'::user_role,
    'active'::account_status,
    NOW() - INTERVAL '200 days',
    NOW() - INTERVAL '200 days'
FROM generate_series(1, 20) AS t(t_offset)
ON CONFLICT (id) DO NOTHING;

-- Students
INSERT INTO users (
    id, login, last_name, first_name, role, status,
    is_headman, group_id, created_at, updated_at
)
SELECT
    900500 + s.s_offset,
    'student' || (900500 + s.s_offset),
    'M05Student' || s.s_offset,
    'Студент' || s.s_offset,
    'student'::user_role,
    'active'::account_status,
    (s.s_offset % 25) = 0,                            -- 1 староста на 25
    900000 + (1 + ((s.s_offset - 1) / 25)),           -- 25 on group
    NOW() - INTERVAL '200 days',
    NOW() - INTERVAL '200 days'
FROM generate_series(1, 500) AS s(s_offset)
ON CONFLICT (id) DO NOTHING;

-- 2.6 TSG assignments: каждая группа × 30 slots × 3 семестра = 1800 rows.
-- (Был 4 семестра, но prod id=1 «Spring 2026» перекрывает наш 4-й —
-- см. semester section.)

INSERT INTO teacher_subject_groups (
    id, teacher_id, subject_id, group_id, semester_id, assigned_at
)
SELECT
    900000 + (g.group_offset * 120 + sem.sem_offset * 30 + slot.slot_offset) AS id,
    900100 + (1 + (slot.slot_offset % 20))                     AS teacher_id,
    -- subject принадлежит своей группе: id = 900000 + group*100 + subj_offset
    900000 + (g.group_offset * 100 + (1 + (slot.slot_offset % 15))) AS subject_id,
    900000 + g.group_offset                                    AS group_id,
    900000 + sem.sem_offset                                    AS semester_id,
    NOW() - INTERVAL '200 days'
FROM generate_series(1, 20) AS g(group_offset),
     generate_series(1, 3)  AS sem(sem_offset),
     generate_series(1, 30) AS slot(slot_offset)
ON CONFLICT (id) DO NOTHING;

-- 2.7 Homeworks — 20 групп × 15 subjects × 3 семестра × 2 домашки = 1800 rows.
-- Заполняет оба composite (group_id, subject_id) и
-- (group_id, semester_id) запросы.

-- V13: lesson_id дропнут, добавлены lesson_date/lesson_number NOT NULL.
INSERT INTO homeworks (
    id, group_id, subject_id, semester_id,
    lesson_date, lesson_number,
    title, description, link, published_by, created_at, updated_at
)
SELECT
    900000 + (g.group_offset * 120 + (sem.sem_offset * 30 + subj.subj_offset * 2 + hw.hw_offset))
                                                AS id,
    900000 + g.group_offset                     AS group_id,
    900000 + (g.group_offset * 100 + subj.subj_offset) AS subject_id,
    900000 + sem.sem_offset                     AS semester_id,
    -- lesson_date: распределяем по 60 дням вокруг создания
    (CURRENT_DATE - INTERVAL '60 days' +
        ((hw.hw_offset * 30 + subj.subj_offset) * INTERVAL '1 day'))::date
                                                AS lesson_date,
    (1 + (subj.subj_offset % 6))                AS lesson_number,
    'M05 HW g' || g.group_offset || ' s' || sem.sem_offset
        || ' subj' || subj.subj_offset || ' n' || hw.hw_offset,
    'Тестовое описание для EXPLAIN baseline',
    NULL,
    900100 + (1 + (subj.subj_offset % 20)),
    NOW() - INTERVAL '180 days',
    NOW() - INTERVAL '180 days'
FROM generate_series(1, 20) AS g(group_offset),
     generate_series(1, 3)  AS sem(sem_offset),
     generate_series(1, 15) AS subj(subj_offset),
     generate_series(1, 2)  AS hw(hw_offset)
ON CONFLICT (id) DO NOTHING;

-- 2.8 Статистика.
ANALYZE users;
ANALYZE groups;
ANALYZE teacher_subject_groups;
ANALYZE homeworks;

\echo '=== academic seed complete ==='
-- Ожидаемая статистика:
--   groups:                   20 seed
--   users:                   520 seed
--   teacher_subject_groups: 2400 seed
--   homeworks:              2400 seed
--   semesters:                 4 seed
