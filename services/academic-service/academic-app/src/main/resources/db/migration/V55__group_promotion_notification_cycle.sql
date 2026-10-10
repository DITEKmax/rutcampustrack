-- One administrative summary for the first durably applied automatic batch.
-- This marker survives outbox retention; it is not a claim that all groups in
-- the cycle succeeded. Later eligible batches still write their group facts.
CREATE TABLE group_promotion_notification_cycle (
    cycle_semester_id BIGINT PRIMARY KEY REFERENCES semesters(id) ON DELETE CASCADE,
    promoted BIGINT NOT NULL CHECK (promoted >= 0),
    graduated BIGINT NOT NULL CHECK (graduated >= 0),
    skipped BIGINT NOT NULL CHECK (skipped >= 0),
    completed_at TIMESTAMPTZ NOT NULL
);
