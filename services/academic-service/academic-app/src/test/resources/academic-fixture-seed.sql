-- Minimal canonical V2/V24/V31/V51/V52 fixtures, replayed only in a fresh owned test database.
INSERT INTO groups (name, is_active, alphabetic_code, numeric_code, current_course,
                    training_duration_years, duration_status)
VALUES ('ИВТ-211', TRUE, 'ИВТ', '211', 2, 4, 'KNOWN');
INSERT INTO semesters (name, date_from, date_to, is_active, semester_type, academic_year)
VALUES ('Spring 2026', '2026-02-01', '2026-06-30', TRUE, 'SPRING', 2025);
INSERT INTO users (login, password_hash, last_name, first_name, middle_name, role, status, is_headman, group_id)
VALUES
('admin', '$2a$10$A9r8miSBxjlpjxFB/z0jIerCCSOrLQP6N.sXrjBAw9l7iy4vmRFpi', 'Администратов', 'Админ', 'Системович', 'admin', 'active', FALSE, NULL),
('teacher', '$2a$10$A9r8miSBxjlpjxFB/z0jIerCCSOrLQP6N.sXrjBAw9l7iy4vmRFpi', 'Преподавателев', 'Учитель', 'Знаниевич', 'teacher', 'active', FALSE, NULL),
('student', '$2a$10$A9r8miSBxjlpjxFB/z0jIerCCSOrLQP6N.sXrjBAw9l7iy4vmRFpi', 'Студентов', 'Студент', 'Староста', 'student', 'active', TRUE, 1);
INSERT INTO student_group_history (user_id, group_id, joined_at, reason)
VALUES (3, 1, '2026-02-01', 'initial-enrollment');
UPDATE users SET employee_number='T00001' WHERE login='teacher';
INSERT INTO admin_access_profiles (user_id, assigned, mode, permissions, version)
VALUES (1, TRUE, 'FULL', '{}', 1);
INSERT INTO user_role_grants (user_id, role, status, group_id, created_at, updated_at)
SELECT id, role::text, status::text, group_id, NOW(), NOW() FROM users;
INSERT INTO user_role_grants (user_id, role, status, group_id, created_at, updated_at)
VALUES (3, 'headman', 'active', 1, NOW(), NOW());
INSERT INTO campus_settings (name, lat, lng, radius_m)
VALUES ('Main Campus', 55.788204, 37.606762, 200);
INSERT INTO campus_map_activity_coverage (singleton) VALUES (TRUE);
ALTER SEQUENCE student_login_seq RESTART WITH 1;
ALTER SEQUENCE teacher_login_seq RESTART WITH 1;
