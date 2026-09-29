-- JS-HEADMAN-21: retain immutable physical lesson generations while making
-- the cross-service transfer an observable durable operation.
ALTER TYPE lesson_status ADD VALUE IF NOT EXISTS 'transferred';

CREATE TABLE lesson_transfer_operations
(
    operation_id                    UUID PRIMARY KEY,
    actor_id                         BIGINT      NOT NULL,
    request_key                      UUID        NOT NULL,
    occurrence_id                    BIGINT      NOT NULL
        REFERENCES lesson_occurrences (id) ON DELETE RESTRICT,
    source_lesson_id                 BIGINT      NOT NULL,
    target_lesson_id                 BIGINT      NOT NULL,
    request_hash                     BYTEA       NOT NULL,
    operation_hash                   BYTEA       NOT NULL,
    expected_occurrence_revision     BIGINT      NOT NULL,
    result_occurrence_revision       BIGINT      NOT NULL,
    source_generation                BIGINT      NOT NULL,
    target_generation                BIGINT      NOT NULL,
    batch_count                      INTEGER     NOT NULL,
    source_snapshot                  JSONB       NOT NULL,
    target_snapshot                  JSONB       NOT NULL,
    state                            VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    error_code                       VARCHAR(40),
    created_at                       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at                       TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT lesson_transfer_actor_chk CHECK (actor_id > 0),
    CONSTRAINT lesson_transfer_source_fk FOREIGN KEY (source_lesson_id, occurrence_id)
        REFERENCES lessons (id, occurrence_id) ON DELETE RESTRICT,
    CONSTRAINT lesson_transfer_target_fk FOREIGN KEY (target_lesson_id, occurrence_id)
        REFERENCES lessons (id, occurrence_id) ON DELETE RESTRICT DEFERRABLE INITIALLY DEFERRED,
    CONSTRAINT lesson_transfer_hash_chk CHECK (
        octet_length(request_hash) = 32 AND octet_length(operation_hash) = 32),
    CONSTRAINT lesson_transfer_ids_chk CHECK (
        source_lesson_id > 0 AND target_lesson_id > 0 AND source_lesson_id <> target_lesson_id),
    CONSTRAINT lesson_transfer_revision_chk CHECK (
        expected_occurrence_revision > 0
        AND result_occurrence_revision = expected_occurrence_revision + 1
        AND source_generation > 0 AND target_generation = source_generation + 1),
    CONSTRAINT lesson_transfer_batch_count_chk CHECK (batch_count > 0),
    CONSTRAINT lesson_transfer_state_chk CHECK (state IN ('PENDING', 'COMPLETED', 'ERROR')),
    CONSTRAINT lesson_transfer_error_chk CHECK (
        (state = 'ERROR' AND error_code IN (
            'TARGET_DATA_CONFLICT', 'SOURCE_STATE_CONFLICT', 'SCOPE_MISMATCH',
            'INVALID_SNAPSHOT', 'DEPENDENCY_UNAVAILABLE', 'ARCHIVED_SEMESTER'))
        OR (state <> 'ERROR' AND error_code IS NULL)),
    CONSTRAINT lesson_transfer_actor_key_uq UNIQUE (actor_id, request_key),
    CONSTRAINT lesson_transfer_operation_occurrence_uq UNIQUE (operation_id, occurrence_id)
);

CREATE INDEX idx_lesson_transfer_pending
    ON lesson_transfer_operations (updated_at, created_at)
    WHERE state = 'PENDING';
CREATE INDEX idx_lesson_transfer_source ON lesson_transfer_operations (source_lesson_id, created_at DESC);
CREATE INDEX idx_lesson_transfer_target ON lesson_transfer_operations (target_lesson_id, created_at DESC);

ALTER TABLE schedule_transfer_replay
    ADD CONSTRAINT schedule_transfer_replay_operation_fk
        FOREIGN KEY (actor_id, request_key)
        REFERENCES lesson_transfer_operations (actor_id, request_key) ON DELETE RESTRICT
        DEFERRABLE INITIALLY DEFERRED;

CREATE TABLE lesson_transfer_binding_batches
(
    operation_id UUID    NOT NULL REFERENCES lesson_transfer_operations (operation_id) ON DELETE RESTRICT,
    batch_index  INTEGER NOT NULL,
    batch_hash   BYTEA   NOT NULL,
    payload      JSONB   NOT NULL,
    binding_count SMALLINT NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (operation_id, batch_index),
    CONSTRAINT lesson_transfer_batch_index_chk CHECK (batch_index >= 0),
    CONSTRAINT lesson_transfer_batch_hash_chk CHECK (octet_length(batch_hash) = 32),
    CONSTRAINT lesson_transfer_batch_size_chk CHECK (binding_count BETWEEN 0 AND 64)
);

CREATE TABLE lesson_transfer_authorities
(
    operation_id                    UUID PRIMARY KEY,
    occurrence_id                   BIGINT      NOT NULL,
    source_lesson_id                BIGINT      NOT NULL,
    target_lesson_id                BIGINT      NOT NULL,
    actor_id                        BIGINT      NOT NULL,
    operation_hash                  BYTEA       NOT NULL,
    expected_occurrence_revision    BIGINT      NOT NULL,
    expected_generation             BIGINT      NOT NULL,
    expected_lesson_revision        BIGINT      NOT NULL,
    target_date                     DATE        NOT NULL,
    target_lesson_number            SMALLINT    NOT NULL,
    target_start_time               TIME        NOT NULL,
    target_end_time                 TIME        NOT NULL,
    target_room                     VARCHAR(64),
    created_at                      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT lesson_transfer_authority_operation_fk
        FOREIGN KEY (operation_id, occurrence_id)
        REFERENCES lesson_transfer_operations (operation_id, occurrence_id) ON DELETE RESTRICT,
    CONSTRAINT lesson_transfer_authority_source_fk
        FOREIGN KEY (source_lesson_id, occurrence_id)
        REFERENCES lessons (id, occurrence_id) ON DELETE RESTRICT,
    CONSTRAINT lesson_transfer_authority_target_fk
        FOREIGN KEY (target_lesson_id, occurrence_id)
        REFERENCES lessons (id, occurrence_id) ON DELETE RESTRICT DEFERRABLE INITIALLY DEFERRED,
    CONSTRAINT lesson_transfer_authority_ids_chk CHECK (actor_id > 0 AND source_lesson_id <> target_lesson_id),
    CONSTRAINT lesson_transfer_authority_hash_chk CHECK (octet_length(operation_hash) = 32),
    CONSTRAINT lesson_transfer_authority_revision_chk CHECK (
        expected_occurrence_revision > 0 AND expected_generation > 0 AND expected_lesson_revision > 0),
    CONSTRAINT lesson_transfer_authority_slot_chk CHECK (
        target_lesson_number BETWEEN 1 AND 8 AND target_end_time > target_start_time)
);

CREATE TABLE lesson_transfer_participant_receipts
(
    operation_id UUID        NOT NULL REFERENCES lesson_transfer_operations (operation_id) ON DELETE RESTRICT,
    participant  VARCHAR(16) NOT NULL,
    batch_index  INTEGER     NOT NULL,
    result       VARCHAR(16) NOT NULL,
    error_code   VARCHAR(40),
    payload_hash BYTEA       NOT NULL,
    source_lesson_id BIGINT  NOT NULL,
    target_lesson_id BIGINT  NOT NULL,
    received_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (operation_id, participant, batch_index),
    CONSTRAINT lesson_transfer_receipt_participant_chk
        CHECK (participant IN ('ATTENDANCE', 'ACADEMIC')),
    CONSTRAINT lesson_transfer_receipt_batch_chk CHECK (
        (participant = 'ATTENDANCE' AND batch_index = -1)
        OR (participant = 'ACADEMIC' AND batch_index >= 0)),
    CONSTRAINT lesson_transfer_receipt_result_chk CHECK (result IN ('APPLIED', 'ERROR')),
    CONSTRAINT lesson_transfer_receipt_error_chk CHECK (
        (result = 'ERROR' AND error_code IN (
            'TARGET_DATA_CONFLICT', 'SOURCE_STATE_CONFLICT', 'SCOPE_MISMATCH',
            'INVALID_SNAPSHOT', 'DEPENDENCY_UNAVAILABLE', 'ARCHIVED_SEMESTER'))
        OR (result = 'APPLIED' AND error_code IS NULL)),
    CONSTRAINT lesson_transfer_receipt_hash_chk CHECK (octet_length(payload_hash) = 32),
    CONSTRAINT lesson_transfer_receipt_target_chk CHECK (source_lesson_id <> target_lesson_id)
);

ALTER TABLE lesson_lifecycle_entries
    ADD COLUMN transfer_operation_id UUID,
    ADD CONSTRAINT lesson_lifecycle_transfer_fk FOREIGN KEY (transfer_operation_id)
        REFERENCES lesson_transfer_authorities (operation_id) ON DELETE RESTRICT,
    ADD CONSTRAINT lesson_lifecycle_transfer_action_chk
        CHECK (transfer_operation_id IS NULL OR action = 'TRANSFERRED');
CREATE UNIQUE INDEX uq_lesson_lifecycle_transfer_operation
    ON lesson_lifecycle_entries (transfer_operation_id)
    WHERE transfer_operation_id IS NOT NULL;

CREATE OR REPLACE FUNCTION reject_lesson_transfer_immutable_update()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.operation_id IS DISTINCT FROM OLD.operation_id
       OR NEW.actor_id IS DISTINCT FROM OLD.actor_id
       OR NEW.request_key IS DISTINCT FROM OLD.request_key
       OR NEW.occurrence_id IS DISTINCT FROM OLD.occurrence_id
       OR NEW.source_lesson_id IS DISTINCT FROM OLD.source_lesson_id
       OR NEW.target_lesson_id IS DISTINCT FROM OLD.target_lesson_id
       OR NEW.request_hash IS DISTINCT FROM OLD.request_hash
       OR NEW.operation_hash IS DISTINCT FROM OLD.operation_hash
       OR NEW.expected_occurrence_revision IS DISTINCT FROM OLD.expected_occurrence_revision
       OR NEW.result_occurrence_revision IS DISTINCT FROM OLD.result_occurrence_revision
       OR NEW.source_generation IS DISTINCT FROM OLD.source_generation
       OR NEW.target_generation IS DISTINCT FROM OLD.target_generation
       OR NEW.batch_count IS DISTINCT FROM OLD.batch_count
       OR NEW.source_snapshot IS DISTINCT FROM OLD.source_snapshot
       OR NEW.target_snapshot IS DISTINCT FROM OLD.target_snapshot
       OR NEW.created_at IS DISTINCT FROM OLD.created_at
       OR OLD.state <> 'PENDING'
       OR NEW.state NOT IN ('COMPLETED', 'ERROR') THEN
        RAISE EXCEPTION 'lesson transfer identity is immutable and terminal states cannot change';
    END IF;
    IF NEW.state = 'ERROR' AND NEW.error_code IS NULL THEN
        RAISE EXCEPTION 'lesson transfer ERROR requires an allowlisted error code';
    END IF;
    NEW.updated_at := NOW();
    RETURN NEW;
END
$$;
CREATE TRIGGER lesson_transfer_operation_immutable_trg
    BEFORE UPDATE ON lesson_transfer_operations
    FOR EACH ROW EXECUTE FUNCTION reject_lesson_transfer_immutable_update();

CREATE OR REPLACE FUNCTION reject_lesson_transfer_append_mutation()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'lesson transfer batches, authorities, and receipts are append-only';
END
$$;
CREATE TRIGGER lesson_transfer_batches_append_only_trg
    BEFORE UPDATE OR DELETE ON lesson_transfer_binding_batches
    FOR EACH ROW EXECUTE FUNCTION reject_lesson_transfer_append_mutation();
CREATE TRIGGER lesson_transfer_authorities_append_only_trg
    BEFORE UPDATE OR DELETE ON lesson_transfer_authorities
    FOR EACH ROW EXECUTE FUNCTION reject_lesson_transfer_append_mutation();
CREATE TRIGGER lesson_transfer_receipts_append_only_trg
    BEFORE UPDATE OR DELETE ON lesson_transfer_participant_receipts
    FOR EACH ROW EXECUTE FUNCTION reject_lesson_transfer_append_mutation();

CREATE OR REPLACE FUNCTION validate_lesson_transfer_authority_insert()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
DECLARE
    configured_operation UUID;
    operation_row lesson_transfer_operations%ROWTYPE;
    occurrence_row lesson_occurrences%ROWTYPE;
    source_row lessons%ROWTYPE;
    fence_row schedule_assignment_fences%ROWTYPE;
BEGIN
    configured_operation := NULLIF(current_setting('rutcampustrack.lesson_transfer_operation_id', true), '')::UUID;
    IF configured_operation IS NULL OR configured_operation <> NEW.operation_id THEN
        RAISE EXCEPTION 'lesson transfer requires its exact transaction operation id';
    END IF;
    SELECT * INTO operation_row FROM lesson_transfer_operations
     WHERE operation_id = NEW.operation_id AND occurrence_id = NEW.occurrence_id FOR UPDATE;
    SELECT * INTO occurrence_row FROM lesson_occurrences WHERE id = NEW.occurrence_id FOR UPDATE;
    SELECT * INTO source_row FROM lessons
     WHERE id = NEW.source_lesson_id AND occurrence_id = NEW.occurrence_id FOR UPDATE;
    IF operation_row.operation_id IS NULL OR occurrence_row.id IS NULL OR source_row.id IS NULL
       OR operation_row.operation_hash IS DISTINCT FROM NEW.operation_hash
       OR operation_row.state <> 'PENDING'
       OR operation_row.actor_id <> NEW.actor_id
       OR operation_row.source_lesson_id <> NEW.source_lesson_id
       OR operation_row.target_lesson_id <> NEW.target_lesson_id
       OR operation_row.expected_occurrence_revision <> NEW.expected_occurrence_revision
       OR operation_row.source_generation <> NEW.expected_generation
       OR occurrence_row.current_lesson_id <> NEW.source_lesson_id
       OR occurrence_row.revision <> NEW.expected_occurrence_revision
       OR occurrence_row.generation <> NEW.expected_generation
       OR occurrence_row.occurrence_date <> source_row.date
       OR source_row.status::text <> 'planned'
       OR source_row.revision <> NEW.expected_lesson_revision
       OR source_row.generation <> NEW.expected_generation
       OR source_row.id = NEW.target_lesson_id
       OR NEW.target_date <= CURRENT_DATE THEN
        RAISE EXCEPTION 'lesson transfer source is not the exact future planned generation';
    END IF;
    SELECT * INTO fence_row FROM schedule_assignment_fences
     WHERE assignment_id = occurrence_row.assignment_id FOR UPDATE;
    IF fence_row.assignment_id IS NULL OR NEW.target_date < fence_row.valid_from
       OR NEW.target_date >= fence_row.creation_cap_until_exclusive
       OR fence_row.group_id <> occurrence_row.group_id
       OR fence_row.subject_id <> occurrence_row.subject_id
       OR fence_row.semester_id <> occurrence_row.semester_id
       OR fence_row.assigned_teacher_id <> occurrence_row.assigned_teacher_id
       OR fence_row.lesson_type <> occurrence_row.lesson_type THEN
        RAISE EXCEPTION 'lesson transfer target is outside the current assignment and semester scope';
    END IF;
    RETURN NEW;
END
$$;
CREATE TRIGGER lesson_transfer_authority_validate_trg
    BEFORE INSERT ON lesson_transfer_authorities
    FOR EACH ROW EXECUTE FUNCTION validate_lesson_transfer_authority_insert();

CREATE OR REPLACE FUNCTION protect_occurrence_identity()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
DECLARE
    operation_id_value UUID;
    operation_row schedule_assignment_replacement_operations%ROWTYPE;
    ledger_row schedule_assignment_rebind_ledger%ROWTYPE;
    restore_operation_id_value UUID;
    restore_row lesson_restore_authorities%ROWTYPE;
    transfer_operation_id_value UUID;
    transfer_row lesson_transfer_authorities%ROWTYPE;
BEGIN
    transfer_operation_id_value := NULLIF(current_setting(
        'rutcampustrack.lesson_transfer_operation_id', true), '')::UUID;
    IF transfer_operation_id_value IS NOT NULL
       AND (NEW.current_lesson_id IS DISTINCT FROM OLD.current_lesson_id
            OR NEW.occurrence_date IS DISTINCT FROM OLD.occurrence_date
            OR NEW.generation IS DISTINCT FROM OLD.generation) THEN
        SELECT * INTO transfer_row FROM lesson_transfer_authorities
         WHERE operation_id = transfer_operation_id_value AND occurrence_id = OLD.id FOR UPDATE;
        IF transfer_row.operation_id IS NULL
           OR OLD.current_lesson_id IS DISTINCT FROM transfer_row.source_lesson_id
           OR OLD.occurrence_date IS DISTINCT FROM (
               SELECT date FROM lessons WHERE id = transfer_row.source_lesson_id)
           OR OLD.revision <> transfer_row.expected_occurrence_revision
           OR OLD.generation <> transfer_row.expected_generation
           OR NEW.current_lesson_id IS DISTINCT FROM transfer_row.target_lesson_id
           OR NEW.occurrence_date IS DISTINCT FROM transfer_row.target_date
           OR NEW.one_off_lesson_id IS DISTINCT FROM OLD.one_off_lesson_id
           OR NEW.schedule_item_id IS DISTINCT FROM OLD.schedule_item_id
           OR NEW.assignment_id <> OLD.assignment_id
           OR NEW.group_id <> OLD.group_id
           OR NEW.subject_id <> OLD.subject_id
           OR NEW.semester_id <> OLD.semester_id
           OR NEW.assigned_teacher_id <> OLD.assigned_teacher_id
           OR NEW.lesson_type <> OLD.lesson_type
           OR NEW.generation <> transfer_row.expected_generation + 1
           OR NEW.revision <> transfer_row.expected_occurrence_revision + 1 THEN
            RAISE EXCEPTION 'occurrence update is outside the exact lesson transfer authority';
        END IF;
        RETURN NEW;
    END IF;

    IF NEW.one_off_lesson_id IS DISTINCT FROM OLD.one_off_lesson_id
       OR NEW.occurrence_date IS DISTINCT FROM OLD.occurrence_date
       OR NEW.group_id IS DISTINCT FROM OLD.group_id
       OR NEW.subject_id IS DISTINCT FROM OLD.subject_id
       OR NEW.semester_id IS DISTINCT FROM OLD.semester_id
       OR NEW.lesson_type IS DISTINCT FROM OLD.lesson_type
       OR NEW.generation < OLD.generation
       OR NEW.revision < OLD.revision THEN
        RAISE EXCEPTION 'lesson occurrence identity, current pointer, or ordering is immutable';
    END IF;
    IF OLD.current_lesson_id IS NULL AND NEW.current_lesson_id IS NOT NULL
       AND OLD.schedule_item_id IS NOT NULL AND OLD.one_off_lesson_id IS NULL
       AND NEW.schedule_item_id IS NOT DISTINCT FROM OLD.schedule_item_id
       AND NEW.assignment_id = OLD.assignment_id
       AND NEW.assigned_teacher_id = OLD.assigned_teacher_id
       AND NEW.generation = 1 AND NEW.revision = 1 THEN
        IF NOT EXISTS (SELECT 1 FROM lessons lesson
             WHERE lesson.id = NEW.current_lesson_id AND lesson.occurrence_id = OLD.id
               AND lesson.schedule_item_id = OLD.schedule_item_id
               AND lesson.assignment_id = OLD.assignment_id AND lesson.group_id = OLD.group_id
               AND lesson.subject_id = OLD.subject_id AND lesson.semester_id = OLD.semester_id
               AND lesson.assigned_teacher_id = OLD.assigned_teacher_id
               AND lesson.lesson_type = OLD.lesson_type AND lesson.date = OLD.occurrence_date
               AND lesson.generation = 1 AND lesson.revision = 1) THEN
            RAISE EXCEPTION 'initial occurrence pointer must reference its exact generation-1 lesson';
        END IF;
        RETURN NEW;
    END IF;
    restore_operation_id_value := NULLIF(current_setting(
        'rutcampustrack.lesson_restore_operation_id', true), '')::UUID;
    IF NEW.current_lesson_id IS DISTINCT FROM OLD.current_lesson_id
       OR NEW.schedule_item_id IS DISTINCT FROM OLD.schedule_item_id
       OR NEW.assignment_id IS DISTINCT FROM OLD.assignment_id
       OR NEW.assigned_teacher_id IS DISTINCT FROM OLD.assigned_teacher_id
       OR NEW.generation IS DISTINCT FROM OLD.generation THEN
        IF restore_operation_id_value IS NOT NULL THEN
            SELECT * INTO restore_row FROM lesson_restore_authorities
             WHERE operation_id = restore_operation_id_value AND occurrence_id = OLD.id FOR UPDATE;
            IF NOT FOUND OR OLD.current_lesson_id IS DISTINCT FROM restore_row.source_lesson_id
               OR OLD.schedule_item_id IS DISTINCT FROM restore_row.source_schedule_item_id
               OR OLD.assignment_id <> restore_row.source_assignment_id
               OR OLD.assigned_teacher_id <> restore_row.source_teacher_id
               OR OLD.revision <> restore_row.expected_occurrence_revision
               OR OLD.generation <> restore_row.expected_generation
               OR NEW.current_lesson_id IS DISTINCT FROM restore_row.target_lesson_id
               OR NEW.schedule_item_id IS DISTINCT FROM restore_row.target_schedule_item_id
               OR NEW.assignment_id <> restore_row.target_assignment_id
               OR NEW.assigned_teacher_id <> restore_row.target_teacher_id
               OR NEW.generation <> restore_row.target_generation
               OR NEW.revision <> restore_row.expected_occurrence_revision + 1 THEN
                RAISE EXCEPTION 'occurrence update is outside the exact lesson restore authority';
            END IF;
            RETURN NEW;
        END IF;
        IF NEW.current_lesson_id IS DISTINCT FROM OLD.current_lesson_id THEN
            RAISE EXCEPTION 'occurrence current lesson pointer changes require an exact lifecycle authority';
        END IF;
    END IF;
    IF NEW.schedule_item_id IS DISTINCT FROM OLD.schedule_item_id
       OR NEW.assignment_id IS DISTINCT FROM OLD.assignment_id
       OR NEW.assigned_teacher_id IS DISTINCT FROM OLD.assigned_teacher_id THEN
        operation_id_value := NULLIF(current_setting(
            'rutcampustrack.schedule_assignment_rebind_operation_id', true), '')::UUID;
        IF operation_id_value IS NULL THEN
            RAISE EXCEPTION 'occurrence provenance changes require an exact replacement ledger row';
        END IF;
        SELECT * INTO operation_row FROM schedule_assignment_replacement_operations
         WHERE operation_id = operation_id_value FOR UPDATE;
        SELECT * INTO ledger_row FROM schedule_assignment_rebind_ledger
         WHERE operation_id = operation_id_value AND occurrence_id = OLD.id AND result = 'MOVED' FOR UPDATE;
        IF NOT FOUND OR operation_row.state <> 'APPLIED'
           OR NEW.current_lesson_id IS DISTINCT FROM OLD.current_lesson_id
           OR OLD.assignment_id <> operation_row.source_assignment_id
           OR NEW.assignment_id <> operation_row.target_assignment_id
           OR OLD.assigned_teacher_id <> operation_row.source_teacher_id
           OR NEW.assigned_teacher_id <> operation_row.target_teacher_id
           OR NEW.schedule_item_id <> ledger_row.target_schedule_item_id
           OR OLD.schedule_item_id <> ledger_row.source_schedule_item_id
           OR OLD.occurrence_date < operation_row.effective_from
           OR ledger_row.occurrence_date <> OLD.occurrence_date
           OR ledger_row.expected_occurrence_revision <> OLD.revision
           OR NEW.revision <> OLD.revision + 1
           OR ledger_row.after_occurrence_revision <> NEW.revision
           OR ledger_row.before_assignment_id <> OLD.assignment_id
           OR ledger_row.after_assignment_id <> NEW.assignment_id
           OR ledger_row.before_teacher_id <> OLD.assigned_teacher_id
           OR ledger_row.after_teacher_id <> NEW.assigned_teacher_id THEN
            RAISE EXCEPTION 'occurrence update is outside the exact replacement ledger entry';
        END IF;
    END IF;
    RETURN NEW;
END
$$;

CREATE OR REPLACE FUNCTION validate_restored_physical_lesson_insert()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
DECLARE
    operation_id_value UUID;
    restore_row lesson_restore_authorities%ROWTYPE;
    transfer_row lesson_transfer_authorities%ROWTYPE;
BEGIN
    IF NEW.generation <= 1 THEN RETURN NEW; END IF;
    operation_id_value := NULLIF(current_setting(
        'rutcampustrack.lesson_transfer_operation_id', true), '')::UUID;
    IF operation_id_value IS NOT NULL THEN
        SELECT * INTO transfer_row FROM lesson_transfer_authorities
         WHERE operation_id = operation_id_value AND occurrence_id = NEW.occurrence_id
           AND target_lesson_id = NEW.id FOR UPDATE;
    IF transfer_row.operation_id IS NULL OR NEW.generation <> transfer_row.expected_generation + 1
           OR NEW.revision <> 1 OR NEW.date <> transfer_row.target_date
           OR NEW.lesson_number <> transfer_row.target_lesson_number
           OR NEW.start_time <> transfer_row.target_start_time
           OR NEW.end_time <> transfer_row.target_end_time
           OR NEW.room_snapshot IS DISTINCT FROM transfer_row.target_room
           OR NEW.status::text <> 'planned' THEN
            RAISE EXCEPTION 'new physical lesson differs from exact transfer authority';
        END IF;
        RETURN NEW;
    END IF;
    operation_id_value := NULLIF(current_setting(
        'rutcampustrack.lesson_restore_operation_id', true), '')::UUID;
    IF operation_id_value IS NULL THEN
        RAISE EXCEPTION 'new physical lesson generation requires an exact restore or transfer authority';
    END IF;
    SELECT * INTO restore_row FROM lesson_restore_authorities
     WHERE operation_id = operation_id_value AND occurrence_id = NEW.occurrence_id
       AND target_lesson_id = NEW.id FOR UPDATE;
    IF restore_row.operation_id IS NULL OR NEW.generation <> restore_row.target_generation OR NEW.revision <> 1
       OR NEW.schedule_item_id <> restore_row.target_schedule_item_id
       OR NEW.assignment_id <> restore_row.target_assignment_id
       OR NEW.assigned_teacher_id <> restore_row.target_teacher_id
       OR NEW.status::text <> 'planned'
       OR NEW.date <> (SELECT occurrence_date FROM lesson_occurrences WHERE id = NEW.occurrence_id) THEN
        RAISE EXCEPTION 'new physical lesson differs from exact restore authority';
    END IF;
    RETURN NEW;
END
$$;

CREATE OR REPLACE FUNCTION validate_lesson_transfer_lifecycle_entry()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
DECLARE
    transfer_row lesson_transfer_authorities%ROWTYPE;
    occurrence_row lesson_occurrences%ROWTYPE;
    source_row lessons%ROWTYPE;
    target_row lessons%ROWTYPE;
BEGIN
    IF NEW.transfer_operation_id IS NULL THEN RETURN NEW; END IF;
    SELECT * INTO transfer_row FROM lesson_transfer_authorities
     WHERE operation_id = NEW.transfer_operation_id AND occurrence_id = NEW.occurrence_id;
    SELECT * INTO occurrence_row FROM lesson_occurrences WHERE id = NEW.occurrence_id;
    SELECT * INTO source_row FROM lessons WHERE id = transfer_row.source_lesson_id;
    SELECT * INTO target_row FROM lessons WHERE id = transfer_row.target_lesson_id;
    IF transfer_row.operation_id IS NULL OR occurrence_row.id IS NULL
       OR source_row.id IS NULL OR target_row.id IS NULL
       OR NEW.action <> 'TRANSFERRED'
       OR NEW.lesson_id <> transfer_row.source_lesson_id
       OR NEW.target_lesson_id <> transfer_row.target_lesson_id
       OR NEW.revision <> transfer_row.expected_occurrence_revision + 1
       OR NEW.generation <> transfer_row.expected_generation + 1
       OR NEW.actor_id <> transfer_row.actor_id
       OR NEW.reason IS NOT NULL
       OR source_row.status::text <> 'transferred'
       OR target_row.status::text <> 'planned'
       OR occurrence_row.current_lesson_id <> transfer_row.target_lesson_id
       OR occurrence_row.occurrence_date <> transfer_row.target_date
       OR occurrence_row.revision <> NEW.revision
       OR occurrence_row.generation <> NEW.generation THEN
        RAISE EXCEPTION 'TRANSFERRED history is outside its exact current-generation authority';
    END IF;
    RETURN NEW;
END
$$;
CREATE TRIGGER lesson_lifecycle_transfer_authority_guard_trg
    BEFORE INSERT ON lesson_lifecycle_entries
    FOR EACH ROW EXECUTE FUNCTION validate_lesson_transfer_lifecycle_entry();

CREATE OR REPLACE FUNCTION require_completed_lesson_transfer_authority()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
DECLARE
    operation_row lesson_transfer_operations%ROWTYPE;
    occurrence_row lesson_occurrences%ROWTYPE;
    source_row lessons%ROWTYPE;
    target_row lessons%ROWTYPE;
BEGIN
    SELECT * INTO operation_row FROM lesson_transfer_operations WHERE operation_id = NEW.operation_id;
    SELECT * INTO occurrence_row FROM lesson_occurrences WHERE id = NEW.occurrence_id;
    SELECT * INTO source_row FROM lessons WHERE id = NEW.source_lesson_id;
    SELECT * INTO target_row FROM lessons WHERE id = NEW.target_lesson_id;
    IF operation_row.operation_id IS NULL OR occurrence_row.id IS NULL
       OR source_row.id IS NULL OR target_row.id IS NULL
       OR operation_row.operation_hash IS DISTINCT FROM NEW.operation_hash
       OR occurrence_row.current_lesson_id <> NEW.target_lesson_id
       OR occurrence_row.occurrence_date <> NEW.target_date
       OR occurrence_row.revision <> NEW.expected_occurrence_revision + 1
       OR occurrence_row.generation <> NEW.expected_generation + 1
       OR source_row.status::text <> 'transferred'
       OR target_row.status::text <> 'planned'
       OR target_row.generation <> NEW.expected_generation + 1
       OR target_row.date <> NEW.target_date
       OR NOT EXISTS (SELECT 1 FROM lesson_lifecycle_entries entry
            WHERE entry.transfer_operation_id = NEW.operation_id
              AND entry.occurrence_id = NEW.occurrence_id
              AND entry.revision = NEW.expected_occurrence_revision + 1
              AND entry.action = 'TRANSFERRED'
              AND entry.lesson_id = NEW.source_lesson_id
              AND entry.target_lesson_id = NEW.target_lesson_id
              AND entry.actor_id = NEW.actor_id)
       OR NOT EXISTS (SELECT 1 FROM schedule_transfer_replay replay
            WHERE replay.actor_id = operation_row.actor_id
              AND replay.request_key = operation_row.request_key
              AND replay.occurrence_id = NEW.occurrence_id
              AND replay.source_lesson_id = NEW.source_lesson_id
              AND replay.target_lesson_id = NEW.target_lesson_id
              AND replay.accepted_revision = NEW.expected_occurrence_revision + 1)
       OR (SELECT COUNT(*) FROM lesson_transfer_binding_batches batch
            WHERE batch.operation_id = NEW.operation_id) <> operation_row.batch_count THEN
        RAISE EXCEPTION 'lesson transfer authority has no exact pointer, snapshot, history, replay, or batch set';
    END IF;
    RETURN NULL;
END
$$;
CREATE CONSTRAINT TRIGGER lesson_transfer_authority_completion_guard_trg
    AFTER INSERT ON lesson_transfer_authorities
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW EXECUTE FUNCTION require_completed_lesson_transfer_authority();

CREATE OR REPLACE FUNCTION protect_lesson_homework_binding_transfer()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
DECLARE
    operation_id_value UUID;
    transfer_row lesson_transfer_authorities%ROWTYPE;
    binding_snapshot JSONB;
BEGIN
    IF NEW.current_lesson_id IS NOT DISTINCT FROM OLD.current_lesson_id THEN RETURN NEW; END IF;
    operation_id_value := NULLIF(current_setting(
        'rutcampustrack.lesson_transfer_operation_id', true), '')::UUID;
    SELECT * INTO transfer_row FROM lesson_transfer_authorities
     WHERE operation_id = operation_id_value AND occurrence_id = OLD.occurrence_id FOR UPDATE;
    IF operation_id_value IS NULL OR NOT FOUND
       OR OLD.current_lesson_id <> transfer_row.source_lesson_id
       OR NEW.current_lesson_id <> transfer_row.target_lesson_id
       OR OLD.state NOT IN ('PENDING', 'ACTIVE') OR NEW.state <> OLD.state
       OR NEW.revision <> OLD.revision + 1
       OR NEW.homework_id IS DISTINCT FROM OLD.homework_id
       OR NEW.actor_id IS DISTINCT FROM OLD.actor_id
       OR NEW.request_key IS DISTINCT FROM OLD.request_key
       OR NEW.payload_hash IS DISTINCT FROM OLD.payload_hash THEN
        RAISE EXCEPTION 'homework binding transfer requires its exact active transfer authority';
    END IF;
    SELECT binding.snapshot INTO binding_snapshot
      FROM lesson_transfer_binding_batches batch
      CROSS JOIN LATERAL jsonb_array_elements(batch.payload -> 'bindings') AS binding(snapshot)
     WHERE batch.operation_id = operation_id_value
       AND binding.snapshot ->> 'binding_id' = OLD.binding_id::TEXT
       AND binding.snapshot ->> 'actor_id' = OLD.actor_id::TEXT
       AND binding.snapshot ->> 'request_key' = OLD.request_key::TEXT
       AND binding.snapshot ->> 'homework_id' IS NOT DISTINCT FROM OLD.homework_id::TEXT
       AND binding.snapshot ->> 'payload_hash' = encode(OLD.payload_hash, 'hex')
       AND binding.snapshot ->> 'state' = OLD.state
       AND binding.snapshot ->> 'revision' = OLD.revision::TEXT;
    IF binding_snapshot IS NULL THEN
        RAISE EXCEPTION 'homework binding transfer is absent from the immutable operation batch';
    END IF;
    RETURN NEW;
END
$$;
CREATE TRIGGER lesson_homework_binding_transfer_guard_trg
    BEFORE UPDATE ON lesson_homework_bindings
    FOR EACH ROW EXECUTE FUNCTION protect_lesson_homework_binding_transfer();

CREATE OR REPLACE FUNCTION protect_incomplete_lesson_transfer_status()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    IF OLD.status::text = 'transferred' AND NEW.status::text <> 'transferred' THEN
        RAISE EXCEPTION 'a transferred physical lesson remains immutable history';
    END IF;
    IF NEW.status IS DISTINCT FROM OLD.status
       AND EXISTS (SELECT 1 FROM lesson_transfer_operations operation
                    WHERE operation.target_lesson_id = OLD.id
                      AND operation.state <> 'COMPLETED') THEN
        RAISE EXCEPTION 'lesson status cannot change while its transfer is unresolved';
    END IF;
    RETURN NEW;
END
$$;
CREATE TRIGGER lessons_transfer_status_guard_trg
    BEFORE UPDATE OF status ON lessons
    FOR EACH ROW EXECUTE FUNCTION protect_incomplete_lesson_transfer_status();
