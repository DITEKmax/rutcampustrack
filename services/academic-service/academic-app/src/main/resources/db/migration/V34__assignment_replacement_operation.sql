-- S3 teacher replacement: durable Academic operation and replacement-only close.
-- Existing assignment identity remains immutable; only the effective end and
-- lifecycle state may change through the exact durable operation.

ALTER TABLE assignments
    ADD COLUMN lifecycle_state VARCHAR(16) NOT NULL DEFAULT 'ACTIVE';

ALTER TABLE assignments
    ADD CONSTRAINT assignments_lifecycle_state_chk
        CHECK (lifecycle_state IN ('ACTIVE', 'PREPARED'));

    ALTER TABLE assignments
    DROP CONSTRAINT IF EXISTS assignments_validity_chk,
    ADD CONSTRAINT assignments_validity_chk
        CHECK (valid_until_exclusive IS NULL OR valid_until_exclusive >= valid_from);

CREATE INDEX idx_assignments_lifecycle_group
    ON assignments (group_id, semester_id, lifecycle_state, valid_from);

CREATE TABLE assignment_replacement_operations
(
    operation_id              UUID PRIMARY KEY,
    actor_id                  BIGINT       NOT NULL,
    request_key               UUID         NOT NULL,
    payload_hash              BYTEA        NOT NULL,
    source_assignment_id      BIGINT       NOT NULL,
    target_assignment_id      BIGINT       NOT NULL,
    effective_from            DATE         NOT NULL,
    source_valid_until        DATE,
    target_valid_until        DATE,
    state                     VARCHAR(16)  NOT NULL,
    schedule_receipt_state    VARCHAR(16),
    schedule_moved_count      BIGINT       NOT NULL DEFAULT 0,
    schedule_skipped_count    BIGINT       NOT NULL DEFAULT 0,
    created_at                TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at                TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT assignment_replacement_operation_actor_chk CHECK (actor_id > 0),
    CONSTRAINT assignment_replacement_operation_hash_chk CHECK (octet_length(payload_hash) = 32),
    CONSTRAINT assignment_replacement_operation_source_chk CHECK (source_assignment_id > 0),
    CONSTRAINT assignment_replacement_operation_target_chk CHECK (target_assignment_id > 0),
    CONSTRAINT assignment_replacement_operation_state_chk
        CHECK (state IN ('PREPARED', 'APPLIED', 'COMMITTED')),
    CONSTRAINT assignment_replacement_operation_source_target_chk
        CHECK (source_assignment_id <> target_assignment_id),
    CONSTRAINT assignment_replacement_operation_actor_key_uq
        UNIQUE (actor_id, request_key)
);

CREATE INDEX idx_assignment_replacement_source
    ON assignment_replacement_operations (source_assignment_id, state, effective_from);

CREATE OR REPLACE FUNCTION protect_assignment_replacement_operation()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'assignment replacement operations are immutable history';
    END IF;

    IF NEW.operation_id IS DISTINCT FROM OLD.operation_id
       OR NEW.actor_id IS DISTINCT FROM OLD.actor_id
       OR NEW.request_key IS DISTINCT FROM OLD.request_key
       OR NEW.payload_hash IS DISTINCT FROM OLD.payload_hash
       OR NEW.source_assignment_id IS DISTINCT FROM OLD.source_assignment_id
       OR NEW.target_assignment_id IS DISTINCT FROM OLD.target_assignment_id
       OR NEW.effective_from IS DISTINCT FROM OLD.effective_from
       OR NEW.source_valid_until IS DISTINCT FROM OLD.source_valid_until
       OR NEW.target_valid_until IS DISTINCT FROM OLD.target_valid_until
       OR NEW.created_at IS DISTINCT FROM OLD.created_at THEN
        RAISE EXCEPTION 'assignment replacement operation identity is immutable';
    END IF;

    IF NEW.state IS DISTINCT FROM OLD.state THEN
        IF NOT ((OLD.state = 'PREPARED' AND NEW.state = 'APPLIED')
             OR (OLD.state = 'APPLIED' AND NEW.state = 'COMMITTED')) THEN
            RAISE EXCEPTION 'invalid assignment replacement operation transition';
        END IF;
        IF OLD.state = 'PREPARED'
           AND (NEW.schedule_receipt_state IS DISTINCT FROM 'APPLIED'
                OR NEW.schedule_moved_count <> 0
                OR NEW.schedule_skipped_count <> 0) THEN
            RAISE EXCEPTION 'Academic APPLIED barrier must have an empty Schedule receipt';
        END IF;
        IF OLD.state = 'APPLIED'
           AND (OLD.schedule_receipt_state IS DISTINCT FROM 'APPLIED'
                OR NEW.schedule_receipt_state IS DISTINCT FROM 'COMMITTED') THEN
            RAISE EXCEPTION 'Academic commit must follow its exact applied barrier receipt';
        END IF;
    ELSIF NEW.schedule_receipt_state IS DISTINCT FROM OLD.schedule_receipt_state
       OR NEW.schedule_moved_count IS DISTINCT FROM OLD.schedule_moved_count
       OR NEW.schedule_skipped_count IS DISTINCT FROM OLD.schedule_skipped_count THEN
        RAISE EXCEPTION 'Schedule receipt can change only with an operation transition';
    END IF;

    IF NEW.schedule_moved_count < OLD.schedule_moved_count
       OR NEW.schedule_skipped_count < OLD.schedule_skipped_count THEN
        RAISE EXCEPTION 'Schedule receipt counts cannot move backwards';
    END IF;
    RETURN NEW;
END
$$;

CREATE OR REPLACE FUNCTION validate_assignment_replacement_operation_insert()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    IF NEW.state <> 'PREPARED'
       OR NEW.schedule_receipt_state IS NOT NULL
       OR NEW.schedule_moved_count <> 0
       OR NEW.schedule_skipped_count <> 0 THEN
        RAISE EXCEPTION 'replacement operations must start in PREPARED without a Schedule receipt';
    END IF;
    RETURN NEW;
END
$$;

CREATE TRIGGER assignment_replacement_operation_insert_guard_trg
    BEFORE INSERT ON assignment_replacement_operations
    FOR EACH ROW EXECUTE FUNCTION validate_assignment_replacement_operation_insert();

CREATE TRIGGER assignment_replacement_operation_guard_trg
    BEFORE UPDATE OR DELETE ON assignment_replacement_operations
    FOR EACH ROW EXECUTE FUNCTION protect_assignment_replacement_operation();

CREATE OR REPLACE FUNCTION validate_assignment_insert_interval()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    IF NEW.valid_until_exclusive IS NOT NULL
       AND NEW.valid_until_exclusive <= NEW.valid_from THEN
        RAISE EXCEPTION 'new assignments require a positive effective interval';
    END IF;
    RETURN NEW;
END
$$;

CREATE TRIGGER assignments_insert_interval_guard_trg
    BEFORE INSERT ON assignments
    FOR EACH ROW EXECUTE FUNCTION validate_assignment_insert_interval();

CREATE OR REPLACE FUNCTION protect_assignment_identity()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
DECLARE
    operation_id_value UUID;
    operation_source  BIGINT;
    operation_target  BIGINT;
    operation_date    DATE;
    operation_state   VARCHAR(16);
    operation_found   BOOLEAN := FALSE;
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'assignments are closed, never deleted';
    END IF;

    IF NEW.id IS DISTINCT FROM OLD.id
       OR NEW.teacher_id IS DISTINCT FROM OLD.teacher_id
       OR NEW.subject_id IS DISTINCT FROM OLD.subject_id
       OR NEW.group_id IS DISTINCT FROM OLD.group_id
       OR NEW.semester_id IS DISTINCT FROM OLD.semester_id
       OR NEW.lesson_type IS DISTINCT FROM OLD.lesson_type
       OR NEW.valid_from IS DISTINCT FROM OLD.valid_from
       OR NEW.created_at IS DISTINCT FROM OLD.created_at THEN
        RAISE EXCEPTION 'assignment identity is immutable';
    END IF;

    IF NEW.valid_until_exclusive IS NOT NULL
       AND NEW.valid_until_exclusive < NEW.valid_from THEN
        RAISE EXCEPTION 'assignment close must not precede valid_from';
    END IF;

    operation_id_value := NULLIF(current_setting('rutcampustrack.assignment_replacement_operation_id', true), '')::UUID;
    IF operation_id_value IS NOT NULL THEN
        SELECT source_assignment_id, target_assignment_id, effective_from, state
          INTO operation_source, operation_target, operation_date, operation_state
          FROM assignment_replacement_operations
         WHERE operation_id = operation_id_value
         FOR UPDATE;
        operation_found := FOUND;
        IF NOT operation_found THEN
            RAISE EXCEPTION 'assignment replacement operation does not exist';
        END IF;
    END IF;

    IF NEW.valid_until_exclusive IS DISTINCT FROM OLD.valid_until_exclusive THEN
        IF (operation_id_value IS NOT NULL
            AND operation_found
            AND operation_source = OLD.id
            AND operation_date = NEW.valid_until_exclusive
            AND operation_state = 'APPLIED'
            AND (OLD.valid_until_exclusive IS NULL
                 OR NEW.valid_until_exclusive < OLD.valid_until_exclusive)) IS NOT TRUE THEN
            RAISE EXCEPTION 'assignment end can only narrow to the exact APPLIED replacement date';
        END IF;
    END IF;

    IF NEW.lifecycle_state IS DISTINCT FROM OLD.lifecycle_state
       AND (operation_id_value IS NOT NULL
            AND operation_found
            AND operation_target = OLD.id
            AND operation_state = 'APPLIED'
            AND OLD.lifecycle_state = 'PREPARED'
            AND NEW.lifecycle_state = 'ACTIVE') IS NOT TRUE THEN
        RAISE EXCEPTION 'only PREPARED-to-ACTIVE is allowed for the exact APPLIED replacement target';
    END IF;

    RETURN NEW;
END
$$;

DROP TRIGGER IF EXISTS assignments_identity_guard_trg ON assignments;
CREATE TRIGGER assignments_identity_guard_trg
    BEFORE UPDATE OR DELETE ON assignments
    FOR EACH ROW
    EXECUTE FUNCTION protect_assignment_identity();
