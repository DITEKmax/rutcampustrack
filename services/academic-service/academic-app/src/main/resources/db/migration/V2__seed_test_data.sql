-- V2__seed_test_data.sql
-- Test data for development and integration testing
-- Password for all test users: "password"
-- BCrypt hash (cost 10) generated with BCryptPasswordEncoder.encode("password")

-- Test group
INSERT INTO groups (name, code, is_active)
VALUES ('ИВТ-211', NULL, true);

-- Test semester
INSERT INTO semesters (name, date_from, date_to, is_active)
VALUES ('Spring 2026', '2026-02-01', '2026-06-30', true);

-- Test users (admin, teacher, student)
-- BCrypt hash of 'password' with cost 10
INSERT INTO users (login, password_hash, last_name, first_name, middle_name, role, status, is_headman, group_id)
VALUES
    ('admin',   '$2a$10$A9r8miSBxjlpjxFB/z0jIerCCSOrLQP6N.sXrjBAw9l7iy4vmRFpi', 'Администратов', 'Админ',    'Системович',   'admin',   'active', false, NULL),
    ('teacher', '$2a$10$A9r8miSBxjlpjxFB/z0jIerCCSOrLQP6N.sXrjBAw9l7iy4vmRFpi', 'Преподавателев', 'Учитель',  'Знаниевич',    'teacher', 'active', false, NULL),
    ('student', '$2a$10$A9r8miSBxjlpjxFB/z0jIerCCSOrLQP6N.sXrjBAw9l7iy4vmRFpi', 'Студентов',     'Студент',  'Староста',     'student', 'active', true,  1);

-- Employee number for teacher
UPDATE users SET employee_number = 'T00001' WHERE login = 'teacher';

-- Campus settings for geofence testing
-- Coordinates: RUT MIIT main campus (ул. Образцова 9, Москва)
INSERT INTO campus_settings (name, lat, lng, radius_m)
VALUES ('Main Campus', 55.788204, 37.606762, 200);
