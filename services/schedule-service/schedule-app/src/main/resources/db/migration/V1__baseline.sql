-- V1__baseline.sql
-- Schedule Service — начальная схема

CREATE TYPE week_type AS ENUM ('all', 'odd', 'even');
CREATE TYPE lesson_status AS ENUM ('planned', 'active', 'closed', 'cancelled');

-- Schedule items (weekly template)
CREATE TABLE schedule_items (
    id              BIGSERIAL PRIMARY KEY,
    group_id        BIGINT NOT NULL,
    subject_id      BIGINT NOT NULL,
    teacher_id      BIGINT NOT NULL,
    semester_id     BIGINT NOT NULL,
    day_of_week     SMALLINT NOT NULL CHECK (day_of_week BETWEEN 0 AND 5),
    lesson_number   SMALLINT NOT NULL CHECK (lesson_number BETWEEN 1 AND 8),
    start_time      TIME NOT NULL,
    end_time        TIME NOT NULL,
    week_type       week_type NOT NULL DEFAULT 'all',
    room            VARCHAR(64),
    is_active       BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (group_id, day_of_week, lesson_number, week_type, semester_id)
);

CREATE INDEX idx_si_group_semester ON schedule_items(group_id, semester_id);

-- Lessons (concrete instances on specific dates)
CREATE TABLE lessons (
    id                  BIGSERIAL PRIMARY KEY,
    schedule_item_id    BIGINT NOT NULL REFERENCES schedule_items(id) ON DELETE CASCADE,
    date                DATE NOT NULL,
    status              lesson_status NOT NULL DEFAULT 'planned',
    is_geo_blocked      BOOLEAN NOT NULL DEFAULT FALSE,
    cancel_reason       VARCHAR(512),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    closed_at           TIMESTAMPTZ,
    UNIQUE (schedule_item_id, date)
);

CREATE INDEX idx_lessons_date ON lessons(date);
CREATE INDEX idx_lessons_status ON lessons(status) WHERE status IN ('planned', 'active');
