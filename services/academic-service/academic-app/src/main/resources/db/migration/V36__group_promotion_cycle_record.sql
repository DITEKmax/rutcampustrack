CREATE TABLE group_promotion_cycle_record
(
    id                BIGSERIAL PRIMARY KEY,
    cycle_semester_id BIGINT       NOT NULL,
    group_id          BIGINT       NOT NULL,
    action            VARCHAR(12)  NOT NULL,
    from_name         VARCHAR(32)  NOT NULL,
    to_name           VARCHAR(32),
    student_count     BIGINT       NOT NULL,
    processed_at      TIMESTAMPTZ  NOT NULL,

    CONSTRAINT group_promotion_cycle_action_chk
        CHECK (action IN ('PROMOTE', 'ARCHIVE')),
    CONSTRAINT group_promotion_cycle_student_count_chk
        CHECK (student_count >= 0),
    CONSTRAINT group_promotion_cycle_semester_group_uq
        UNIQUE (cycle_semester_id, group_id)
);
