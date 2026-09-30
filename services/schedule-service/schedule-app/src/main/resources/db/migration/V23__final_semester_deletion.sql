CREATE EXTENSION IF NOT EXISTS pgcrypto WITH SCHEMA public;

ALTER TABLE schedule_semester_archive_barriers
    DROP CONSTRAINT schedule_semester_archive_barrier_state_valid,
    ADD COLUMN expected_participant_digest VARCHAR(64),
    ADD COLUMN participant_digest VARCHAR(64),
    ADD COLUMN schedule_templates_count BIGINT,
    ADD COLUMN one_off_lessons_count BIGINT,
    ADD COLUMN lessons_count BIGINT,
    ADD CONSTRAINT schedule_semester_archive_barrier_state_valid
        CHECK (participant_state IN ('PENDING', 'READY', 'PREPARED_RESTORE', 'RELEASED',
                                     'DELETE_PREPARING', 'DELETE_SEALED', 'DELETED')),
    ADD CONSTRAINT schedule_semester_delete_expected_digest_chk
        CHECK (expected_participant_digest IS NULL OR expected_participant_digest ~ '^[0-9a-f]{64}$'),
    ADD CONSTRAINT schedule_semester_delete_receipt_digest_chk
        CHECK (participant_digest IS NULL OR participant_digest ~ '^[0-9a-f]{64}$'),
    ADD CONSTRAINT schedule_semester_delete_receipt_counts_chk
        CHECK ((participant_digest IS NULL AND schedule_templates_count IS NULL
                                  AND one_off_lessons_count IS NULL AND lessons_count IS NULL)
            OR (participant_digest IS NOT NULL AND schedule_templates_count IS NOT NULL
                                  AND one_off_lessons_count IS NOT NULL AND lessons_count IS NOT NULL
                                  AND schedule_templates_count >= 0
                                  AND one_off_lessons_count >= 0 AND lessons_count >= 0)),
    ADD CONSTRAINT schedule_semester_delete_expected_required_chk
        CHECK (participant_state NOT IN ('DELETE_PREPARING', 'DELETE_SEALED', 'DELETED')
            OR expected_participant_digest IS NOT NULL),
    ADD CONSTRAINT schedule_semester_delete_sealed_receipt_chk
        CHECK (participant_state NOT IN ('DELETE_SEALED', 'DELETED')
            OR (participant_digest IS NOT NULL AND schedule_templates_count IS NOT NULL
                AND one_off_lessons_count IS NOT NULL AND lessons_count IS NOT NULL));

-- The physical lesson and its occurrence form a cycle. RESTRICT is immediate
-- even on a DEFERRABLE FK; NO ACTION retains the same committed-row integrity
-- while allowing the exact guarded delete transaction to remove both sides.
ALTER TABLE lesson_occurrences
    DROP CONSTRAINT lesson_occurrences_current_lesson_fk,
    ADD CONSTRAINT lesson_occurrences_current_lesson_fk
        FOREIGN KEY (current_lesson_id, id)
        REFERENCES lessons (id, occurrence_id) ON DELETE NO ACTION
        DEFERRABLE INITIALLY DEFERRED;

CREATE OR REPLACE FUNCTION schedule_semester_delete_authorized(target_semester_id BIGINT)
RETURNS BOOLEAN LANGUAGE SQL STABLE AS $$
    SELECT target_semester_id > 0
       AND nullif(current_setting('rutcampustrack.schedule_delete_operation_id', TRUE), '')::UUID
            = barrier.operation_id
       AND nullif(current_setting('rutcampustrack.schedule_delete_state_version', TRUE), '')::BIGINT
            = barrier.state_version
       AND nullif(current_setting('rutcampustrack.schedule_delete_participant_digest', TRUE), '')
            = barrier.expected_participant_digest
       AND nullif(current_setting('rutcampustrack.schedule_delete_participant_digest', TRUE), '')
            = barrier.participant_digest
       AND barrier.semester_id = target_semester_id
       AND barrier.participant_state = 'DELETE_SEALED'
      FROM schedule_semester_archive_barriers barrier
     WHERE barrier.semester_id = target_semester_id
$$;

CREATE OR REPLACE FUNCTION guard_schedule_semester_deletion_write()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
DECLARE
    old_row JSONB;
    new_row JSONB;
    old_semester_id BIGINT;
    new_semester_id BIGINT;
    affected_semester_id BIGINT;
    barrier_operation_id UUID;
    barrier_state_version BIGINT;
    barrier_state VARCHAR(20);
    deleting BOOLEAN;
    deletion_authorized BOOLEAN;
    binding_confirmation_authorized BOOLEAN;
    binding_cancellation_authorized BOOLEAN;
    neutralize_binding BOOLEAN;
    detach_one_off BOOLEAN;
    context_operation_id UUID;
    context_state_version BIGINT;
    context_binding_id BIGINT;
    context_occurrence_id BIGINT;
    context_actor_id BIGINT;
    context_request_key UUID;
    context_payload_hash TEXT;
    context_revision BIGINT;
    confirmation_revision BIGINT;
BEGIN
    IF TG_OP <> 'INSERT' THEN old_row := to_jsonb(OLD); END IF;
    IF TG_OP <> 'DELETE' THEN new_row := to_jsonb(NEW); END IF;

    IF TG_TABLE_NAME IN ('lesson_homework_bindings', 'lesson_lifecycle_entries') THEN
        IF old_row IS NOT NULL THEN
            SELECT semester_id INTO old_semester_id FROM lesson_occurrences
             WHERE id = (old_row ->> 'occurrence_id')::BIGINT;
        END IF;
        IF new_row IS NOT NULL THEN
            SELECT semester_id INTO new_semester_id FROM lesson_occurrences
             WHERE id = (new_row ->> 'occurrence_id')::BIGINT;
        END IF;
    ELSE
        old_semester_id := nullif(old_row ->> 'semester_id', '')::BIGINT;
        new_semester_id := nullif(new_row ->> 'semester_id', '')::BIGINT;
    END IF;

    context_operation_id := nullif(current_setting('rutcampustrack.archive_cancel_operation_id', TRUE), '')::UUID;
    context_state_version := nullif(current_setting('rutcampustrack.archive_cancel_state_version', TRUE), '')::BIGINT;
    context_binding_id := nullif(current_setting('rutcampustrack.archive_cancel_binding_id', TRUE), '')::BIGINT;
    context_occurrence_id := nullif(current_setting('rutcampustrack.archive_cancel_occurrence_id', TRUE), '')::BIGINT;
    context_actor_id := nullif(current_setting('rutcampustrack.archive_cancel_actor_id', TRUE), '')::BIGINT;
    context_request_key := nullif(current_setting('rutcampustrack.archive_cancel_request_key', TRUE), '')::UUID;
    context_payload_hash := nullif(current_setting('rutcampustrack.archive_cancel_payload_hash', TRUE), '');
    context_revision := nullif(current_setting('rutcampustrack.archive_cancel_revision', TRUE), '')::BIGINT;
    confirmation_revision := nullif(current_setting(
        'rutcampustrack.schedule_delete_binding_revision', TRUE), '')::BIGINT;

    IF old_semester_id IS NULL AND new_semester_id IS NULL THEN
        RAISE EXCEPTION 'semester deletion fence cannot resolve the affected semester';
    END IF;

    FOR affected_semester_id IN
        SELECT DISTINCT candidate
          FROM unnest(ARRAY[old_semester_id, new_semester_id]) AS candidate
         WHERE candidate IS NOT NULL
         ORDER BY candidate
    LOOP
        PERFORM pg_advisory_xact_lock(5452097, (affected_semester_id % 2147483647)::INTEGER);
        SELECT operation_id, state_version, participant_state
          INTO barrier_operation_id, barrier_state_version, barrier_state
          FROM schedule_semester_archive_barriers
         WHERE semester_id = affected_semester_id;

        deleting := barrier_state IN ('DELETE_PREPARING', 'DELETE_SEALED', 'DELETED');
        IF barrier_state IS NULL OR NOT deleting THEN CONTINUE; END IF;

        deletion_authorized := coalesce(TG_OP = 'DELETE'
            AND barrier_state = 'DELETE_SEALED'
            AND schedule_semester_delete_authorized(affected_semester_id), FALSE);

        binding_confirmation_authorized := FALSE;
        IF TG_TABLE_NAME = 'lesson_homework_bindings' AND TG_OP = 'UPDATE'
           AND barrier_state = 'DELETE_PREPARING' THEN
            binding_confirmation_authorized := coalesce(
                nullif(current_setting('rutcampustrack.schedule_delete_binding_operation_id', TRUE), '')::UUID
                    = barrier_operation_id
                AND nullif(current_setting('rutcampustrack.schedule_delete_binding_state_version', TRUE), '')::BIGINT
                    = barrier_state_version
                AND nullif(current_setting('rutcampustrack.schedule_delete_binding_id', TRUE), '')::BIGINT
                    = OLD.binding_id
                AND OLD.binding_id = NEW.binding_id
                AND OLD.occurrence_id = NEW.occurrence_id
                AND OLD.current_lesson_id = NEW.current_lesson_id
                AND OLD.actor_id = NEW.actor_id
                AND OLD.request_key = NEW.request_key
                AND OLD.payload_hash = NEW.payload_hash
                AND OLD.created_at = NEW.created_at
                AND OLD.homework_id IS NULL AND NEW.homework_id IS NOT NULL
                AND OLD.state = 'PENDING' AND NEW.state = 'ACTIVE'
                AND OLD.revision = confirmation_revision
                AND NEW.revision = OLD.revision + 1
                AND old_semester_id = affected_semester_id AND new_semester_id = affected_semester_id, FALSE);
        END IF;

        binding_cancellation_authorized := FALSE;
        IF TG_TABLE_NAME = 'lesson_homework_bindings' AND TG_OP = 'UPDATE'
           AND barrier_state = 'DELETE_PREPARING' THEN
            binding_cancellation_authorized := coalesce(
                context_operation_id = barrier_operation_id
                AND context_state_version = barrier_state_version
                AND context_binding_id = OLD.binding_id AND context_binding_id = NEW.binding_id
                AND context_occurrence_id = OLD.occurrence_id AND context_occurrence_id = NEW.occurrence_id
                AND context_actor_id = OLD.actor_id AND context_actor_id = NEW.actor_id
                AND context_request_key = OLD.request_key AND context_request_key = NEW.request_key
                AND context_payload_hash = encode(OLD.payload_hash, 'hex')
                AND OLD.payload_hash = NEW.payload_hash
                AND OLD.created_at = NEW.created_at
                AND OLD.state = 'PENDING' AND NEW.state = 'ARCHIVED'
                AND OLD.homework_id IS NULL AND NEW.homework_id IS NULL
                AND OLD.revision = context_revision AND NEW.revision = context_revision + 1
                AND old_semester_id = affected_semester_id AND new_semester_id = affected_semester_id, FALSE);
        END IF;

        neutralize_binding := FALSE;
        IF TG_TABLE_NAME = 'lesson_homework_bindings' AND TG_OP = 'UPDATE' THEN
            neutralize_binding := coalesce(barrier_state = 'DELETE_SEALED'
                AND schedule_semester_delete_authorized(affected_semester_id)
                AND OLD.state IN ('PENDING', 'ACTIVE', 'ARCHIVED')
                AND (OLD.state <> 'ARCHIVED' OR OLD.homework_id IS NOT NULL)
                AND NEW.state = 'ARCHIVED' AND NEW.homework_id IS NULL
                AND NEW.binding_id = OLD.binding_id
                AND NEW.occurrence_id = OLD.occurrence_id
                AND NEW.current_lesson_id = OLD.current_lesson_id
                AND NEW.actor_id = OLD.actor_id
                AND NEW.request_key = OLD.request_key
                AND NEW.payload_hash = OLD.payload_hash
                AND NEW.created_at = OLD.created_at
                AND NEW.revision = OLD.revision + 1
                AND old_semester_id = affected_semester_id AND new_semester_id = affected_semester_id, FALSE);
        END IF;

        detach_one_off := FALSE;
        IF TG_TABLE_NAME = 'schedule_one_off_lessons' AND TG_OP = 'UPDATE' THEN
            detach_one_off := coalesce(barrier_state = 'DELETE_SEALED'
                AND schedule_semester_delete_authorized(affected_semester_id)
                AND OLD.physical_lesson_id IS NOT NULL AND NEW.physical_lesson_id IS NULL
                AND (to_jsonb(NEW) - 'physical_lesson_id') IS NOT DISTINCT FROM
                    (to_jsonb(OLD) - 'physical_lesson_id'), FALSE);
        END IF;

        IF NOT deletion_authorized AND NOT binding_confirmation_authorized
           AND NOT binding_cancellation_authorized AND NOT neutralize_binding AND NOT detach_one_off THEN
            RAISE EXCEPTION 'Schedule semester % is fenced by final deletion', affected_semester_id
                USING ERRCODE = '55000';
        END IF;
    END LOOP;

    IF TG_OP = 'DELETE' THEN RETURN OLD; END IF;
    RETURN NEW;
END
$$;

CREATE TRIGGER a_schedule_semester_deletion_fence
    BEFORE INSERT OR UPDATE OR DELETE ON schedule_items
    FOR EACH ROW EXECUTE FUNCTION guard_schedule_semester_deletion_write();
CREATE TRIGGER a_schedule_semester_deletion_fence
    BEFORE INSERT OR UPDATE OR DELETE ON schedule_one_off_lessons
    FOR EACH ROW EXECUTE FUNCTION guard_schedule_semester_deletion_write();
CREATE TRIGGER a_schedule_semester_deletion_fence
    BEFORE INSERT OR UPDATE OR DELETE ON lesson_occurrences
    FOR EACH ROW EXECUTE FUNCTION guard_schedule_semester_deletion_write();
CREATE TRIGGER a_schedule_semester_deletion_fence
    BEFORE INSERT OR UPDATE OR DELETE ON lessons
    FOR EACH ROW EXECUTE FUNCTION guard_schedule_semester_deletion_write();
CREATE TRIGGER a_schedule_semester_deletion_fence
    BEFORE INSERT OR UPDATE OR DELETE ON lesson_lifecycle_entries
    FOR EACH ROW EXECUTE FUNCTION guard_schedule_semester_deletion_write();
CREATE TRIGGER a_schedule_semester_deletion_fence
    BEFORE INSERT OR UPDATE OR DELETE ON lesson_homework_bindings
    FOR EACH ROW EXECUTE FUNCTION guard_schedule_semester_deletion_write();

CREATE OR REPLACE FUNCTION validate_lesson_physical_snapshot()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
DECLARE
    occurrence_record lesson_occurrences%ROWTYPE;
    operation_id_value UUID;
    operation_row schedule_assignment_replacement_operations%ROWTYPE;
    ledger_row schedule_assignment_rebind_ledger%ROWTYPE;
    identity_changed BOOLEAN;
BEGIN
    IF TG_OP = 'DELETE' THEN
        IF schedule_semester_delete_authorized(OLD.semester_id) THEN RETURN OLD; END IF;
        RAISE EXCEPTION 'physical lessons are retained; use a lifecycle state';
    END IF;
    SELECT * INTO occurrence_record FROM lesson_occurrences WHERE id = NEW.occurrence_id;
    IF NOT FOUND THEN RAISE EXCEPTION 'lesson occurrence % does not exist', NEW.occurrence_id; END IF;

    identity_changed := TG_OP = 'UPDATE' AND (
        NEW.schedule_item_id IS DISTINCT FROM OLD.schedule_item_id
        OR NEW.assignment_id IS DISTINCT FROM OLD.assignment_id
        OR NEW.assigned_teacher_id IS DISTINCT FROM OLD.assigned_teacher_id);
    IF identity_changed THEN
        operation_id_value := NULLIF(current_setting(
            'rutcampustrack.schedule_assignment_rebind_operation_id', true), '')::UUID;
        IF operation_id_value IS NULL THEN
            RAISE EXCEPTION 'physical lesson provenance changes require an exact replacement ledger row';
        END IF;
        SELECT * INTO operation_row FROM schedule_assignment_replacement_operations
         WHERE operation_id = operation_id_value FOR UPDATE;
        SELECT * INTO ledger_row FROM schedule_assignment_rebind_ledger
         WHERE operation_id = operation_id_value AND occurrence_id = OLD.occurrence_id
           AND lesson_id = OLD.id AND result = 'MOVED' FOR UPDATE;
        IF NOT FOUND OR operation_row.state <> 'APPLIED'
           OR OLD.status::text <> 'planned' OR NEW.status IS DISTINCT FROM OLD.status
           OR OLD.date < operation_row.effective_from
           OR OLD.schedule_item_id <> ledger_row.source_schedule_item_id
           OR NEW.schedule_item_id <> ledger_row.target_schedule_item_id
           OR OLD.assignment_id <> operation_row.source_assignment_id
           OR NEW.assignment_id <> operation_row.target_assignment_id
           OR OLD.assigned_teacher_id <> operation_row.source_teacher_id
           OR NEW.assigned_teacher_id <> operation_row.target_teacher_id
           OR OLD.revision <> ledger_row.expected_lesson_revision
           OR NEW.revision <> OLD.revision + 1
           OR ledger_row.after_lesson_revision <> NEW.revision THEN
            RAISE EXCEPTION 'physical lesson update is outside the exact replacement ledger entry';
        END IF;
    END IF;

    IF NEW.occurrence_id IS DISTINCT FROM occurrence_record.id
       OR NEW.schedule_item_id IS DISTINCT FROM occurrence_record.schedule_item_id
       OR NEW.one_off_lesson_id IS DISTINCT FROM occurrence_record.one_off_lesson_id
       OR NEW.assignment_id IS DISTINCT FROM occurrence_record.assignment_id
       OR NEW.group_id IS DISTINCT FROM occurrence_record.group_id
       OR NEW.subject_id IS DISTINCT FROM occurrence_record.subject_id
       OR NEW.semester_id IS DISTINCT FROM occurrence_record.semester_id
       OR NEW.assigned_teacher_id IS DISTINCT FROM occurrence_record.assigned_teacher_id
       OR NEW.lesson_type IS DISTINCT FROM occurrence_record.lesson_type THEN
        RAISE EXCEPTION 'physical lesson identity snapshot does not match its occurrence';
    END IF;
    IF TG_OP = 'UPDATE'
       AND (NEW.id IS DISTINCT FROM OLD.id
            OR NEW.schedule_item_id IS DISTINCT FROM OLD.schedule_item_id AND NOT identity_changed
            OR NEW.one_off_lesson_id IS DISTINCT FROM OLD.one_off_lesson_id
            OR NEW.occurrence_id IS DISTINCT FROM OLD.occurrence_id
            OR NEW.assignment_id IS DISTINCT FROM OLD.assignment_id AND NOT identity_changed
            OR NEW.group_id IS DISTINCT FROM OLD.group_id
            OR NEW.subject_id IS DISTINCT FROM OLD.subject_id
            OR NEW.semester_id IS DISTINCT FROM OLD.semester_id
            OR NEW.assigned_teacher_id IS DISTINCT FROM OLD.assigned_teacher_id AND NOT identity_changed
            OR NEW.lesson_type IS DISTINCT FROM OLD.lesson_type
            OR NEW.date IS DISTINCT FROM OLD.date
            OR NEW.lesson_number IS DISTINCT FROM OLD.lesson_number
            OR NEW.day_of_week IS DISTINCT FROM OLD.day_of_week
            OR NEW.start_time IS DISTINCT FROM OLD.start_time
            OR NEW.end_time IS DISTINCT FROM OLD.end_time
            OR NEW.room_snapshot IS DISTINCT FROM OLD.room_snapshot
            OR NEW.week_type_snapshot IS DISTINCT FROM OLD.week_type_snapshot
            OR NEW.generation IS DISTINCT FROM OLD.generation) THEN
        RAISE EXCEPTION 'physical lesson identity snapshot is immutable';
    END IF;
    IF TG_OP = 'UPDATE' AND NEW.revision < OLD.revision THEN
        RAISE EXCEPTION 'physical lesson revision cannot move backwards';
    END IF;
    RETURN NEW;
END
$$;

CREATE OR REPLACE FUNCTION reject_lesson_lifecycle_mutation()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP = 'DELETE' AND EXISTS (
        SELECT 1 FROM lesson_occurrences occurrence
         WHERE occurrence.id = OLD.occurrence_id
           AND schedule_semester_delete_authorized(occurrence.semester_id)) THEN
        RETURN OLD;
    END IF;
    RAISE EXCEPTION 'lesson lifecycle entries are append-only';
END
$$;

CREATE OR REPLACE FUNCTION protect_lesson_homework_binding_identity()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP = 'UPDATE' AND schedule_semester_delete_authorized(
            (SELECT semester_id FROM lesson_occurrences WHERE id = OLD.occurrence_id))
       AND OLD.state IN ('PENDING', 'ACTIVE', 'ARCHIVED') AND NEW.state = 'ARCHIVED'
       AND NEW.homework_id IS NULL
       AND (OLD.state <> 'ARCHIVED' OR OLD.homework_id IS NOT NULL)
       AND NEW.binding_id = OLD.binding_id
       AND NEW.occurrence_id = OLD.occurrence_id
       AND NEW.current_lesson_id = OLD.current_lesson_id
       AND NEW.actor_id = OLD.actor_id
       AND NEW.request_key = OLD.request_key
       AND NEW.payload_hash = OLD.payload_hash
       AND NEW.created_at = OLD.created_at
       AND NEW.revision = OLD.revision + 1 THEN
        RETURN NEW;
    END IF;
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'homework binding history is retained; archive the binding';
    END IF;
    IF NEW.binding_id IS DISTINCT FROM OLD.binding_id
       OR NEW.occurrence_id IS DISTINCT FROM OLD.occurrence_id
       OR NEW.actor_id IS DISTINCT FROM OLD.actor_id
       OR NEW.request_key IS DISTINCT FROM OLD.request_key
       OR NEW.payload_hash IS DISTINCT FROM OLD.payload_hash THEN
        RAISE EXCEPTION 'homework binding identity is immutable';
    END IF;
    IF OLD.homework_id IS NOT NULL
       AND NEW.homework_id IS DISTINCT FROM OLD.homework_id THEN
        RAISE EXCEPTION 'homework binding homework_id cannot be replaced';
    END IF;
    IF OLD.state = 'ARCHIVED' AND NEW.state <> 'ARCHIVED' THEN
        RAISE EXCEPTION 'archived homework binding cannot be reopened';
    END IF;
    IF NEW.revision < OLD.revision THEN
        RAISE EXCEPTION 'homework binding revision cannot move backwards';
    END IF;
    RETURN NEW;
END
$$;

ALTER TABLE schedule_recurring_create_replay
    DROP CONSTRAINT schedule_recurring_replay_item_fk;
DO $$
DECLARE
    expected RECORD;
    constraint_name TEXT;
    matches INTEGER;
BEGIN
    FOR expected IN SELECT * FROM (VALUES
        ('schedule_assignment_replacement_templates', 'source_schedule_item_id', 'schedule_items'),
        ('schedule_assignment_replacement_templates', 'target_schedule_item_id', 'schedule_items'),
        ('schedule_assignment_rebind_ledger', 'source_schedule_item_id', 'schedule_items'),
        ('schedule_assignment_rebind_ledger', 'target_schedule_item_id', 'schedule_items'),
        ('schedule_assignment_rebind_ledger', 'occurrence_id', 'lesson_occurrences'),
        ('schedule_assignment_rebind_ledger', 'lesson_id', 'lessons')
    ) AS identities(table_name, column_name, referenced_table)
    LOOP
        SELECT count(*), min(constraint_definition.conname::TEXT) INTO matches, constraint_name
          FROM pg_constraint constraint_definition
          JOIN pg_attribute column_definition
            ON column_definition.attrelid = constraint_definition.conrelid
           AND constraint_definition.conkey = ARRAY[column_definition.attnum]::SMALLINT[]
         WHERE constraint_definition.contype = 'f'
           AND constraint_definition.conrelid = expected.table_name::REGCLASS
           AND column_definition.attname = expected.column_name
           AND constraint_definition.confrelid = expected.referenced_table::REGCLASS;
        IF matches <> 1 THEN
            RAISE EXCEPTION 'Expected exactly one FK for %.%, found %',
                expected.table_name, expected.column_name, matches;
        END IF;
        EXECUTE format('ALTER TABLE %I DROP CONSTRAINT %I', expected.table_name, constraint_name);
    END LOOP;
END
$$;
ALTER TABLE lesson_restore_authorities
    DROP CONSTRAINT lesson_restore_authority_occurrence_fk,
    DROP CONSTRAINT lesson_restore_authority_source_lesson_fk,
    DROP CONSTRAINT lesson_restore_authority_target_lesson_fk,
    DROP CONSTRAINT lesson_restore_authority_source_item_fk,
    DROP CONSTRAINT lesson_restore_authority_target_item_fk;
ALTER TABLE lesson_transfer_operations
    DROP CONSTRAINT lesson_transfer_operations_occurrence_id_fkey,
    DROP CONSTRAINT lesson_transfer_source_fk,
    DROP CONSTRAINT lesson_transfer_target_fk;
ALTER TABLE lesson_transfer_authorities
    DROP CONSTRAINT lesson_transfer_authority_source_fk,
    DROP CONSTRAINT lesson_transfer_authority_target_fk;
ALTER TABLE schedule_transfer_replay
    DROP CONSTRAINT schedule_transfer_replay_occurrence_fk,
    DROP CONSTRAINT schedule_transfer_replay_source_fk,
    DROP CONSTRAINT schedule_transfer_replay_target_fk;
ALTER TABLE lesson_homework_bindings
    DROP CONSTRAINT lesson_homework_bindings_occurrence_fk,
    DROP CONSTRAINT lesson_homework_bindings_current_lesson_fk;
