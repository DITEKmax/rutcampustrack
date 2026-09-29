ALTER TABLE semesters
    ADD COLUMN is_archived BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN archive_transition VARCHAR(10) NOT NULL DEFAULT 'NONE',
    ADD COLUMN state_version BIGINT NOT NULL DEFAULT 0,
    ADD CONSTRAINT semesters_archive_transition_valid
        CHECK (archive_transition IN ('NONE', 'ARCHIVING', 'RESTORING')),
    ADD CONSTRAINT semesters_archive_state_consistent
        CHECK (
            (is_archived AND archive_transition IN ('NONE', 'RESTORING'))
            OR (NOT is_archived AND archive_transition IN ('NONE', 'ARCHIVING'))
        ),
    ADD CONSTRAINT semesters_state_version_nonnegative
        CHECK (state_version >= 0),
    ADD CONSTRAINT semesters_write_blocked_inactive
        CHECK (NOT is_active OR (NOT is_archived AND archive_transition = 'NONE'));
