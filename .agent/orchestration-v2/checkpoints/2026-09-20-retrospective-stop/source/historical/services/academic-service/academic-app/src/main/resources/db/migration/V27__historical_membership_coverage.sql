-- Managed historical membership coverage.  Existing groups are intentionally
-- left unmarked; an empty legacy roster is not proof of completeness.
CREATE TABLE group_history_coverage (
    group_id       BIGINT PRIMARY KEY REFERENCES groups(id) ON DELETE RESTRICT,
    coverage_from  DATE NOT NULL,
    writer_version VARCHAR(32) NOT NULL,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT group_history_coverage_range_chk CHECK (coverage_from IS NOT NULL),
    CONSTRAINT group_history_coverage_writer_chk CHECK (writer_version <> '')
);

CREATE INDEX idx_sgh_group_user_joined
    ON student_group_history (group_id, user_id, joined_at, id);
