-- Academic's bounded participant receipt, immutable history and pending-publication marker.
CREATE TABLE lesson_transfer_receipts
(
    operation_id      UUID        NOT NULL,
    batch_index       INTEGER     NOT NULL,
    batch_count       INTEGER     NOT NULL,
    batch_size        SMALLINT    NOT NULL,
    operation_hash    BYTEA       NOT NULL,
    batch_hash        BYTEA       NOT NULL,
    source_lesson_id  BIGINT      NOT NULL,
    target_lesson_id  BIGINT      NOT NULL,
    result            VARCHAR(16) NOT NULL,
    error_code        VARCHAR(40),
    received_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    PRIMARY KEY (operation_id, batch_index),
    CONSTRAINT lesson_transfer_receipt_batch_bounds_chk
        CHECK (batch_count BETWEEN 1 AND 2147483647
               AND batch_index >= 0 AND batch_index < batch_count),
    CONSTRAINT lesson_transfer_receipt_batch_size_chk CHECK (batch_size BETWEEN 0 AND 64),
    CONSTRAINT lesson_transfer_receipt_hash_chk
        CHECK (octet_length(operation_hash) = 32 AND octet_length(batch_hash) = 32),
    CONSTRAINT lesson_transfer_receipt_ids_chk
        CHECK (source_lesson_id > 0 AND target_lesson_id > 0 AND source_lesson_id <> target_lesson_id),
    CONSTRAINT lesson_transfer_receipt_result_chk CHECK (result IN ('APPLIED', 'ERROR')),
    CONSTRAINT lesson_transfer_receipt_error_chk CHECK (
        (result = 'ERROR' AND error_code IN (
            'TARGET_DATA_CONFLICT', 'SOURCE_STATE_CONFLICT', 'SCOPE_MISMATCH',
            'INVALID_SNAPSHOT', 'DEPENDENCY_UNAVAILABLE', 'ARCHIVED_SEMESTER'))
        OR (result = 'APPLIED' AND error_code IS NULL)),
    CONSTRAINT lesson_transfer_receipt_empty_batch_chk CHECK (
        batch_size > 0 OR (batch_count = 1 AND batch_index = 0))
);

CREATE TABLE homework_binding_transfer_markers
(
    binding_id             BIGINT      PRIMARY KEY,
    actor_id               BIGINT      NOT NULL,
    request_key            UUID        NOT NULL,
    binding_payload_hash   BYTEA       NOT NULL,
    homework_id            BIGINT,
    operation_id           UUID        NOT NULL,
    operation_hash         BYTEA       NOT NULL,
    occurrence_id          BIGINT      NOT NULL,
    group_id               BIGINT      NOT NULL,
    subject_id             BIGINT      NOT NULL,
    semester_id            BIGINT      NOT NULL,
    source_lesson_id       BIGINT      NOT NULL,
    target_lesson_id       BIGINT      NOT NULL,
    source_date            DATE        NOT NULL,
    source_lesson_number   INTEGER     NOT NULL,
    target_date            DATE        NOT NULL,
    target_lesson_number   INTEGER     NOT NULL,
    state                  VARCHAR(16) NOT NULL,
    updated_at             TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT homework_transfer_marker_ids_chk CHECK (
        binding_id > 0 AND actor_id > 0 AND occurrence_id > 0
        AND group_id > 0 AND subject_id > 0 AND semester_id > 0
        AND source_lesson_id > 0 AND target_lesson_id > 0
        AND source_lesson_id <> target_lesson_id),
    CONSTRAINT homework_transfer_marker_hash_chk CHECK (
        octet_length(binding_payload_hash) = 32 AND octet_length(operation_hash) = 32),
    CONSTRAINT homework_transfer_marker_slot_chk CHECK (
        source_lesson_number BETWEEN 1 AND 8 AND target_lesson_number BETWEEN 1 AND 8),
    CONSTRAINT homework_transfer_marker_state_chk CHECK (state IN ('PENDING', 'APPLIED')),
    CONSTRAINT homework_transfer_marker_homework_chk CHECK (
        (state = 'PENDING' AND homework_id IS NULL)
        OR (state = 'APPLIED' AND homework_id IS NOT NULL))
);

CREATE INDEX idx_homework_transfer_marker_operation
    ON homework_binding_transfer_markers (operation_id, binding_id);

CREATE TABLE homework_binding_transfer_history
(
    operation_id           UUID        NOT NULL,
    binding_id             BIGINT      NOT NULL,
    operation_hash         BYTEA       NOT NULL,
    binding_payload_hash   BYTEA       NOT NULL,
    binding_revision       BIGINT      NOT NULL,
    actor_id               BIGINT      NOT NULL,
    request_key            UUID        NOT NULL,
    homework_id            BIGINT,
    occurrence_id          BIGINT      NOT NULL,
    group_id               BIGINT      NOT NULL,
    subject_id             BIGINT      NOT NULL,
    semester_id            BIGINT      NOT NULL,
    source_lesson_id       BIGINT      NOT NULL,
    target_lesson_id       BIGINT      NOT NULL,
    source_date            DATE        NOT NULL,
    source_lesson_number   INTEGER     NOT NULL,
    target_date            DATE        NOT NULL,
    target_lesson_number   INTEGER     NOT NULL,
    result_state           VARCHAR(24) NOT NULL,
    applied_at             TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    PRIMARY KEY (operation_id, binding_id),
    CONSTRAINT homework_transfer_history_ids_chk CHECK (
        binding_id > 0 AND actor_id > 0 AND binding_revision > 0 AND occurrence_id > 0
        AND group_id > 0 AND subject_id > 0 AND semester_id > 0
        AND source_lesson_id > 0 AND target_lesson_id > 0
        AND source_lesson_id <> target_lesson_id),
    CONSTRAINT homework_transfer_history_hash_chk CHECK (
        octet_length(operation_hash) = 32 AND octet_length(binding_payload_hash) = 32),
    CONSTRAINT homework_transfer_history_state_chk
        CHECK (result_state IN ('MOVED', 'PENDING_PUBLICATION'))
);

CREATE OR REPLACE FUNCTION reject_academic_lesson_transfer_history_mutation()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'lesson transfer receipts and binding history are append-only';
END
$$;

CREATE TRIGGER lesson_transfer_receipt_append_only_trg
    BEFORE UPDATE OR DELETE ON lesson_transfer_receipts
    FOR EACH ROW EXECUTE FUNCTION reject_academic_lesson_transfer_history_mutation();
CREATE TRIGGER homework_transfer_history_append_only_trg
    BEFORE UPDATE OR DELETE ON homework_binding_transfer_history
    FOR EACH ROW EXECUTE FUNCTION reject_academic_lesson_transfer_history_mutation();
