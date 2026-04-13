-- V1__baseline.sql
-- Academic Service — начальная схема

CREATE TYPE user_role AS ENUM ('admin', 'teacher', 'student');
CREATE TYPE account_status AS ENUM ('active', 'expelled', 'suspended', 'archived');
CREATE TYPE subject_type AS ENUM ('lecture', 'practice', 'lab');

-- Groups (создаётся первой, т.к. users ссылается на неё)
CREATE TABLE groups (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(128) NOT NULL,
    code        VARCHAR(32) UNIQUE,
    is_active   BOOLEAN NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Users
CREATE TABLE users (
    id                  BIGSERIAL PRIMARY KEY,
    login               VARCHAR(32) NOT NULL UNIQUE,
    password_hash       VARCHAR(255),
    last_name           VARCHAR(128) NOT NULL,
    first_name          VARCHAR(128) NOT NULL,
    middle_name         VARCHAR(128),
    email               VARCHAR(255) UNIQUE,
    phone               VARCHAR(20),
    telegram_id         BIGINT UNIQUE,
    telegram_username   VARCHAR(64),
    employee_number     VARCHAR(32) UNIQUE,
    role                user_role NOT NULL DEFAULT 'student',
    status              account_status NOT NULL DEFAULT 'active',
    is_headman          BOOLEAN NOT NULL DEFAULT FALSE,
    group_id            BIGINT REFERENCES groups(id) ON DELETE SET NULL,
    initial_password    VARCHAR(128),
    password_changed    BOOLEAN NOT NULL DEFAULT FALSE,
    avatar_id           VARCHAR(32),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_users_role ON users(role);
CREATE INDEX idx_users_group ON users(group_id);
CREATE INDEX idx_users_telegram ON users(telegram_id);
CREATE INDEX idx_users_employee_number ON users(employee_number);

-- Student group history
CREATE TABLE student_group_history (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT NOT NULL REFERENCES users(id),
    group_id    BIGINT NOT NULL REFERENCES groups(id),
    joined_at   DATE NOT NULL,
    left_at     DATE,
    reason      VARCHAR(255),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_sgh_user ON student_group_history(user_id);

-- Password reset tokens
CREATE TABLE password_reset_tokens (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash  VARCHAR(255) NOT NULL UNIQUE,
    expires_at  TIMESTAMPTZ NOT NULL,
    used_at     TIMESTAMPTZ,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Semesters
CREATE TABLE semesters (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(128) NOT NULL,
    date_from   DATE NOT NULL,
    date_to     DATE NOT NULL,
    is_active   BOOLEAN NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT only_one_active_semester
        EXCLUDE USING btree (is_active WITH =) WHERE (is_active = TRUE)
);

-- Subjects
CREATE TABLE subjects (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(255) NOT NULL,
    type        subject_type NOT NULL DEFAULT 'lecture',
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Teacher-Subject-Group assignments
CREATE TABLE teacher_subject_groups (
    id          BIGSERIAL PRIMARY KEY,
    teacher_id  BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    subject_id  BIGINT NOT NULL REFERENCES subjects(id) ON DELETE CASCADE,
    group_id    BIGINT NOT NULL REFERENCES groups(id) ON DELETE CASCADE,
    semester_id BIGINT NOT NULL REFERENCES semesters(id) ON DELETE CASCADE,
    assigned_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (teacher_id, subject_id, group_id, semester_id)
);

CREATE INDEX idx_tsg_teacher ON teacher_subject_groups(teacher_id);
CREATE INDEX idx_tsg_group ON teacher_subject_groups(group_id);
CREATE INDEX idx_tsg_semester ON teacher_subject_groups(semester_id);

-- Headman assistants
CREATE TABLE headman_assistants (
    id              BIGSERIAL PRIMARY KEY,
    group_id        BIGINT NOT NULL REFERENCES groups(id) ON DELETE CASCADE,
    student_id      BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    permissions     VARCHAR(64)[] NOT NULL,
    assigned_by     BIGINT NOT NULL REFERENCES users(id),
    is_active       BOOLEAN NOT NULL DEFAULT TRUE,
    assigned_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    revoked_at      TIMESTAMPTZ,
    UNIQUE (group_id, student_id)
);

-- Campus settings
CREATE TABLE campus_settings (
    id          SERIAL PRIMARY KEY,
    name        VARCHAR(128) NOT NULL DEFAULT 'Главный кампус',
    lat         DOUBLE PRECISION NOT NULL,
    lng         DOUBLE PRECISION NOT NULL,
    radius_m    INTEGER NOT NULL DEFAULT 200,
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Attendance thresholds (red zone)
CREATE TABLE attendance_thresholds (
    id              BIGSERIAL PRIMARY KEY,
    group_id        BIGINT REFERENCES groups(id) ON DELETE CASCADE,
    subject_id      BIGINT REFERENCES subjects(id) ON DELETE CASCADE,
    threshold_pct   INTEGER NOT NULL CHECK (threshold_pct BETWEEN 0 AND 100),
    set_by          BIGINT NOT NULL REFERENCES users(id),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (group_id, subject_id)
);

-- Homeworks
CREATE TABLE homeworks (
    id              BIGSERIAL PRIMARY KEY,
    group_id        BIGINT NOT NULL REFERENCES groups(id) ON DELETE CASCADE,
    subject_id      BIGINT NOT NULL REFERENCES subjects(id) ON DELETE CASCADE,
    semester_id     BIGINT NOT NULL REFERENCES semesters(id) ON DELETE CASCADE,
    lesson_id       BIGINT,
    title           VARCHAR(500) NOT NULL,
    description     TEXT,
    link            VARCHAR(1000),
    published_by    BIGINT NOT NULL REFERENCES users(id),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_hw_group_subject ON homeworks(group_id, subject_id);
CREATE INDEX idx_hw_lesson ON homeworks(lesson_id);

-- Homework completions (personal tracker)
CREATE TABLE homework_completions (
    id              BIGSERIAL PRIMARY KEY,
    homework_id     BIGINT NOT NULL REFERENCES homeworks(id) ON DELETE CASCADE,
    student_id      BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    completed_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (homework_id, student_id)
);
