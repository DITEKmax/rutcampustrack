ALTER TABLE homeworks
    ADD COLUMN due_reminder_sent_at TIMESTAMPTZ NULL;

CREATE TABLE homework_weekly_digest_runs (
    id          BIGSERIAL PRIMARY KEY,
    group_id    BIGINT NOT NULL,
    semester_id BIGINT NOT NULL,
    week_start  DATE NOT NULL,
    sent_at     TIMESTAMPTZ NOT NULL,
    UNIQUE (group_id, week_start)
);
