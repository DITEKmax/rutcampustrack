-- Control-plane replay identities deliberately have no business-row FK or TTL.
-- A completed semester/subject deletion must retain the exact ACK identity.
CREATE TABLE academic_initial_membership_backfills (
    event_id UUID PRIMARY KEY,
    history_id BIGINT NOT NULL UNIQUE CHECK (history_id > 0),
    student_id BIGINT NOT NULL CHECK (student_id > 0),
    group_id BIGINT NOT NULL CHECK (group_id > 0),
    semester_id BIGINT NOT NULL CHECK (semester_id > 0),
    joined_on DATE NOT NULL,
    semester_ends_on DATE NOT NULL CHECK (semester_ends_on >= joined_on),
    payload_hash BYTEA NOT NULL CHECK (octet_length(payload_hash) = 32),
    state VARCHAR(7) NOT NULL CHECK (state IN ('PENDING', 'APPLIED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    applied_at TIMESTAMPTZ,
    CONSTRAINT initial_membership_backfill_applied_state CHECK (
        (state = 'PENDING' AND applied_at IS NULL) OR
        (state = 'APPLIED' AND applied_at IS NOT NULL)
    )
);
CREATE INDEX academic_initial_membership_backfills_pending
    ON academic_initial_membership_backfills (semester_id) WHERE state = 'PENDING';
