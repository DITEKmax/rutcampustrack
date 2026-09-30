ALTER TABLE semesters
    ADD COLUMN deletion_phase VARCHAR(16),
    ADD COLUMN transition_operation_id UUID;

ALTER TABLE semesters
    DROP CONSTRAINT semesters_archive_transition_valid,
    DROP CONSTRAINT semesters_archive_state_consistent,
    DROP CONSTRAINT semesters_write_blocked_inactive,
    ADD CONSTRAINT semesters_archive_transition_valid
        CHECK (archive_transition IN ('NONE', 'ARCHIVING', 'RESTORING', 'DELETING')),
    ADD CONSTRAINT semesters_archive_state_consistent
        CHECK ((is_archived AND archive_transition IN ('NONE', 'RESTORING', 'DELETING'))
            OR (NOT is_archived AND archive_transition IN ('NONE', 'ARCHIVING', 'DELETING'))),
    ADD CONSTRAINT semesters_write_blocked_inactive
        CHECK (NOT is_active OR (NOT is_archived AND archive_transition = 'NONE'
                                 AND NOT archive_release_pending)),
    ADD CONSTRAINT semesters_deletion_authority_consistent
        CHECK ((archive_transition = 'DELETING'
                AND deletion_phase IN ('PREPARING', 'RELEASING', 'DELETING')
                AND transition_operation_id IS NOT NULL AND NOT is_active
                AND NOT archive_release_pending)
            OR (archive_transition <> 'DELETING'
                AND deletion_phase IS NULL AND transition_operation_id IS NULL));

ALTER TABLE semester_archive_operations
    ADD COLUMN delete_phase VARCHAR(16),
    ADD COLUMN prior_state VARCHAR(12),
    ADD COLUMN semester_name VARCHAR(128),
    ADD COLUMN original_state_version BIGINT,
    ADD COLUMN preview_digest VARCHAR(64),
    ADD COLUMN academic_participant_digest VARCHAR(64),
    ADD COLUMN schedule_participant_digest VARCHAR(64),
    ADD COLUMN attendance_participant_digest VARCHAR(64),
    ADD COLUMN schedule_templates_count BIGINT,
    ADD COLUMN one_off_lessons_count BIGINT,
    ADD COLUMN lessons_count BIGINT,
    ADD COLUMN assignments_count BIGINT,
    ADD COLUMN homeworks_count BIGINT,
    ADD COLUMN attendance_marks_count BIGINT,
    ADD COLUMN student_requests_count BIGINT,
    ADD COLUMN prepare_expires_at TIMESTAMPTZ,
    ADD COLUMN irreversible_intent BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN academic_sealed BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN schedule_sealed BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN attendance_sealed BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN cancel_reason VARCHAR(32),
    DROP CONSTRAINT semester_archive_operations_action_valid,
    DROP CONSTRAINT semester_archive_operations_transition_valid,
    DROP CONSTRAINT semester_archive_operations_academic_status_valid,
    DROP CONSTRAINT semester_archive_operations_schedule_status_valid,
    DROP CONSTRAINT semester_archive_operations_attendance_status_valid,
    ADD CONSTRAINT semester_archive_operations_action_valid
        CHECK (action IN ('ARCHIVE', 'RESTORE', 'DELETE')),
    ADD CONSTRAINT semester_archive_operations_transition_valid
        CHECK (transition IN ('NONE', 'ARCHIVING', 'RESTORING', 'DELETING')),
    ADD CONSTRAINT semester_archive_operations_academic_status_valid
        CHECK (academic_status IN ('NOT_STARTED', 'PENDING', 'READY', 'PREPARED_RESTORE',
                                   'RELEASE_PENDING', 'RELEASED', 'DELETED')),
    ADD CONSTRAINT semester_archive_operations_schedule_status_valid
        CHECK (schedule_status IN ('NOT_STARTED', 'PENDING', 'READY', 'PREPARED_RESTORE',
                                   'RELEASE_PENDING', 'RELEASED', 'DELETED')),
    ADD CONSTRAINT semester_archive_operations_attendance_status_valid
        CHECK (attendance_status IN ('NOT_STARTED', 'PENDING', 'READY', 'PREPARED_RESTORE',
                                     'RELEASE_PENDING', 'RELEASED', 'DELETED')),
    ADD CONSTRAINT semester_archive_delete_shape_valid
        CHECK ((action <> 'DELETE' AND delete_phase IS NULL AND NOT irreversible_intent)
            OR (action = 'DELETE' AND delete_phase IN
                ('PREPARING', 'RELEASING', 'DELETING', 'COMPLETED', 'CANCELLED')
                AND prior_state IN ('ACTIVE', 'INACTIVE', 'ARCHIVED')
                AND semester_name IS NOT NULL AND original_state_version >= 0
                AND preview_digest ~ '^[0-9a-f]{64}$'
                AND academic_participant_digest ~ '^[0-9a-f]{64}$'
                AND schedule_participant_digest ~ '^[0-9a-f]{64}$'
                AND attendance_participant_digest ~ '^[0-9a-f]{64}$'
                AND schedule_templates_count >= 0 AND one_off_lessons_count >= 0
                AND lessons_count >= 0 AND assignments_count >= 0 AND homeworks_count >= 0
                AND attendance_marks_count >= 0 AND student_requests_count >= 0
                AND (irreversible_intent = (delete_phase IN ('DELETING', 'COMPLETED')))));

ALTER TABLE academic_semester_archive_barriers
    DROP CONSTRAINT academic_semester_archive_barrier_state_valid,
    ADD CONSTRAINT academic_semester_archive_barrier_state_valid
        CHECK (participant_state IN ('PENDING', 'READY', 'PREPARED_RESTORE', 'RELEASED',
                                     'DELETE_PREPARING', 'DELETE_SEALED', 'DELETED'));

CREATE OR REPLACE FUNCTION academic_semester_delete_authorized(target_semester_id BIGINT)
RETURNS BOOLEAN LANGUAGE SQL STABLE AS $$
    SELECT target_semester_id > 0
       AND nullif(current_setting('rutcampustrack.semester_delete_operation_id', TRUE), '')::UUID
            = operation.operation_id
       AND nullif(current_setting('rutcampustrack.semester_delete_state_version', TRUE), '')::BIGINT
            = operation.state_version
       AND nullif(current_setting('rutcampustrack.semester_delete_academic_digest', TRUE), '')
            = operation.academic_participant_digest
       AND operation.action = 'DELETE'
       AND operation.semester_id = target_semester_id
       AND operation.delete_phase = 'DELETING'
       AND operation.irreversible_intent
       AND operation.academic_status = 'DELETED'
       AND operation.schedule_status = 'DELETED'
       AND operation.attendance_status = 'DELETED'
       AND barrier.operation_id = operation.operation_id
       AND barrier.semester_id = operation.semester_id
       AND barrier.state_version = operation.state_version
       AND barrier.participant_state IN ('DELETE_SEALED', 'DELETED')
      FROM semester_archive_operations operation
      JOIN academic_semester_archive_barriers barrier
        ON barrier.operation_id = operation.operation_id
       AND barrier.semester_id = operation.semester_id
       AND barrier.state_version = operation.state_version
     WHERE operation.operation_id =
           nullif(current_setting('rutcampustrack.semester_delete_operation_id', TRUE), '')::UUID
       AND operation.semester_id = target_semester_id
$$;

-- An exemption is usable only while the exact durable delete epoch remains unsealed.
CREATE OR REPLACE FUNCTION academic_semester_delete_preparing(target_semester_id BIGINT)
RETURNS BOOLEAN LANGUAGE SQL STABLE AS $$
    SELECT EXISTS (
        SELECT 1 FROM semesters semester
        JOIN academic_semester_archive_barriers barrier ON barrier.semester_id = semester.id
        JOIN semester_archive_operations operation ON operation.operation_id = barrier.operation_id
         AND operation.semester_id = barrier.semester_id AND operation.state_version = barrier.state_version
        WHERE semester.id = target_semester_id AND semester.archive_transition = 'DELETING'
          AND semester.deletion_phase = 'PREPARING' AND NOT semester.is_active
          AND NOT semester.archive_release_pending
          AND semester.transition_operation_id = barrier.operation_id
          AND semester.state_version = barrier.state_version
          AND barrier.participant_state = 'DELETE_PREPARING'
          AND operation.action = 'DELETE' AND operation.delete_phase = 'PREPARING'
          AND NOT operation.irreversible_intent
    )
$$;

CREATE OR REPLACE FUNCTION guard_academic_semester_deletion_write()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
DECLARE
    old_semester_id BIGINT;
    new_semester_id BIGINT;
    affected BIGINT;
    current_transition VARCHAR(10);
    current_archived BOOLEAN;
    barrier_state VARCHAR(20);
    authorized BOOLEAN;
BEGIN
    IF TG_OP <> 'INSERT' THEN
        IF TG_TABLE_NAME = 'homework_completions' THEN
            SELECT semester_id INTO old_semester_id FROM homeworks WHERE id = OLD.homework_id;
        ELSE
            old_semester_id := OLD.semester_id;
        END IF;
    END IF;
    IF TG_OP <> 'DELETE' THEN
        IF TG_TABLE_NAME = 'homework_completions' THEN
            SELECT semester_id INTO new_semester_id FROM homeworks WHERE id = NEW.homework_id;
        ELSE
            new_semester_id := NEW.semester_id;
        END IF;
    END IF;

    FOR affected IN
        SELECT DISTINCT value FROM unnest(ARRAY[old_semester_id, new_semester_id]) AS value
         WHERE value IS NOT NULL ORDER BY value
    LOOP
        PERFORM pg_advisory_xact_lock(5452097, (affected % 2147483647)::INTEGER);
        authorized := TG_OP = 'DELETE' AND academic_semester_delete_authorized(affected);
        IF TG_TABLE_NAME = 'homework_binding_archives' THEN
        IF TG_OP = 'UPDATE' AND OLD.homework_id IS NOT NULL AND NEW.homework_id IS NULL
           AND OLD.binding_id = NEW.binding_id AND OLD.actor_id = NEW.actor_id
           AND OLD.request_key = NEW.request_key AND OLD.archived_at = NEW.archived_at
           AND OLD.semester_id = NEW.semester_id AND OLD.source_event_id IS NOT DISTINCT FROM NEW.source_event_id
           AND academic_semester_delete_authorized(affected) THEN
            authorized := TRUE;
        END IF;
        END IF;
        IF authorized THEN
            CONTINUE;
        END IF;

        SELECT archive_transition, is_archived INTO current_transition, current_archived
          FROM semesters WHERE id = affected;
        SELECT participant_state INTO barrier_state
          FROM academic_semester_archive_barriers WHERE semester_id = affected;
        IF TG_TABLE_NAME IN ('homeworks', 'homework_completions', 'homework_binding_archives')
           AND academic_semester_delete_preparing(affected) THEN
            -- These tables also run guard_academic_semester_archive_business_write,
            -- which admits only an exact captured publication or trusted effect/transfer.
            CONTINUE;
        END IF;
        IF current_transition = 'DELETING' OR current_archived
           OR barrier_state IN ('DELETE_PREPARING', 'DELETE_SEALED', 'DELETED') THEN
            RAISE EXCEPTION 'Academic semester % is fenced by final deletion', affected
                USING ERRCODE = '55000';
        END IF;
    END LOOP;
    IF TG_OP = 'DELETE' THEN RETURN OLD; END IF;
    RETURN NEW;
END
$$;

CREATE TRIGGER a_assignments_semester_deletion_fence
    BEFORE INSERT OR UPDATE OR DELETE ON assignments
    FOR EACH ROW EXECUTE FUNCTION guard_academic_semester_deletion_write();
CREATE TRIGGER a_teacher_subject_groups_semester_deletion_fence
    BEFORE INSERT OR UPDATE OR DELETE ON teacher_subject_groups
    FOR EACH ROW EXECUTE FUNCTION guard_academic_semester_deletion_write();
CREATE TRIGGER a_homeworks_semester_deletion_fence
    BEFORE INSERT OR UPDATE OR DELETE ON homeworks
    FOR EACH ROW EXECUTE FUNCTION guard_academic_semester_deletion_write();
CREATE TRIGGER a_homework_completions_semester_deletion_fence
    BEFORE INSERT OR UPDATE OR DELETE ON homework_completions
    FOR EACH ROW EXECUTE FUNCTION guard_academic_semester_deletion_write();
CREATE TRIGGER a_homework_binding_archives_semester_deletion_fence
    BEFORE UPDATE OR DELETE ON homework_binding_archives
    FOR EACH ROW EXECUTE FUNCTION guard_academic_semester_deletion_write();

CREATE OR REPLACE FUNCTION protect_assignment_identity()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        IF academic_semester_delete_authorized(OLD.semester_id) THEN RETURN OLD; END IF;
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
    IF OLD.valid_until_exclusive IS NOT NULL
       AND (NEW.valid_until_exclusive IS NULL OR NEW.valid_until_exclusive > OLD.valid_until_exclusive) THEN
        RAISE EXCEPTION 'closed assignment validity cannot be extended or reopened';
    END IF;
    IF NEW.valid_until_exclusive IS NOT NULL AND NEW.valid_until_exclusive <= NEW.valid_from THEN
        RAISE EXCEPTION 'assignment close must be after valid_from';
    END IF;
    RETURN NEW;
END
$$;

CREATE OR REPLACE FUNCTION protect_homework_binding_archive_identity()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.binding_id IS DISTINCT FROM OLD.binding_id
       OR NEW.actor_id IS DISTINCT FROM OLD.actor_id
       OR NEW.request_key IS DISTINCT FROM OLD.request_key
       OR NEW.archived_at IS DISTINCT FROM OLD.archived_at THEN
        RAISE EXCEPTION 'homework binding archive identity is immutable';
    END IF;
    IF OLD.homework_id IS NOT NULL AND NEW.homework_id IS DISTINCT FROM OLD.homework_id
       AND NOT (NEW.homework_id IS NULL AND academic_semester_delete_authorized(OLD.semester_id)) THEN
        RAISE EXCEPTION 'homework binding archive content identity is immutable';
    END IF;
    IF OLD.semester_id IS NOT NULL AND NEW.semester_id IS DISTINCT FROM OLD.semester_id THEN
        RAISE EXCEPTION 'homework binding archive semester scope is immutable';
    END IF;
    IF OLD.source_event_id IS NOT NULL AND NEW.source_event_id IS DISTINCT FROM OLD.source_event_id THEN
        RAISE EXCEPTION 'homework binding archive source event is immutable';
    END IF;
    IF NEW.source_event_id IS NOT NULL AND NOT EXISTS (
            SELECT 1 FROM academic_semester_archive_effect_receipts receipt
             WHERE receipt.source_event_id = NEW.source_event_id
               AND receipt.event_type = 'homework.binding.archived'
               AND receipt.semester_id = NEW.semester_id
               AND receipt.binding_id = NEW.binding_id
               AND receipt.state IN ('PENDING', 'APPLIED')) THEN
        RAISE EXCEPTION 'homework binding archive source event has no exact effect receipt';
    END IF;
    RETURN NEW;
END
$$;

CREATE OR REPLACE FUNCTION guard_academic_semester_delete_authority()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP = 'UPDATE'
       AND OLD.archive_transition = 'NONE' AND OLD.deletion_phase IS NULL
       AND OLD.transition_operation_id IS NULL AND NOT OLD.archive_release_pending
       AND NEW.archive_transition = 'DELETING' AND NEW.deletion_phase = 'PREPARING'
       AND NEW.state_version = OLD.state_version + 1
       AND NOT NEW.is_active AND NEW.is_archived = OLD.is_archived
       AND NOT NEW.archive_release_pending
       AND (to_jsonb(NEW) - ARRAY['is_active', 'archive_transition', 'deletion_phase',
                'transition_operation_id', 'state_version'])
         = (to_jsonb(OLD) - ARRAY['is_active', 'archive_transition', 'deletion_phase',
                'transition_operation_id', 'state_version'])
       AND EXISTS (
           SELECT 1 FROM semester_archive_operations operation
           JOIN academic_semester_archive_barriers barrier ON barrier.operation_id = operation.operation_id
            AND barrier.semester_id = operation.semester_id AND barrier.state_version = operation.state_version
           WHERE operation.operation_id = NEW.transition_operation_id
             AND operation.semester_id = OLD.id AND operation.action = 'DELETE'
             AND operation.original_state_version = OLD.state_version
             AND operation.state_version = NEW.state_version AND operation.delete_phase = 'PREPARING'
             AND operation.prior_state = CASE WHEN OLD.is_archived THEN 'ARCHIVED'
                 WHEN OLD.is_active THEN 'ACTIVE' ELSE 'INACTIVE' END
             AND NOT operation.irreversible_intent AND barrier.participant_state = 'DELETE_PREPARING') THEN
        PERFORM pg_advisory_xact_lock(5452097, (OLD.id % 2147483647)::INTEGER);
        RETURN NEW;
    END IF;
    IF TG_OP = 'UPDATE'
       AND OLD.archive_transition = 'DELETING' AND NEW.archive_transition = 'DELETING'
       AND OLD.deletion_phase = 'PREPARING' AND NEW.deletion_phase IN ('RELEASING', 'DELETING')
       AND (to_jsonb(NEW) - 'deletion_phase') = (to_jsonb(OLD) - 'deletion_phase')
       AND EXISTS (
           SELECT 1 FROM semester_archive_operations operation
           JOIN academic_semester_archive_barriers barrier ON barrier.operation_id = operation.operation_id
            AND barrier.semester_id = operation.semester_id AND barrier.state_version = operation.state_version
           WHERE operation.operation_id = OLD.transition_operation_id
             AND operation.semester_id = OLD.id AND operation.action = 'DELETE'
             AND operation.state_version = OLD.state_version AND operation.delete_phase = NEW.deletion_phase
             AND ((NEW.deletion_phase = 'RELEASING' AND NOT operation.irreversible_intent
                   AND barrier.participant_state IN ('DELETE_PREPARING', 'DELETE_SEALED'))
               OR (NEW.deletion_phase = 'DELETING' AND operation.irreversible_intent
                   AND operation.academic_sealed AND operation.schedule_sealed AND operation.attendance_sealed
                   AND operation.academic_status = 'READY' AND operation.schedule_status = 'READY'
                   AND operation.attendance_status = 'READY' AND barrier.participant_state = 'DELETE_SEALED'))) THEN
        PERFORM pg_advisory_xact_lock(5452097, (OLD.id % 2147483647)::INTEGER);
        RETURN NEW;
    END IF;
    IF TG_OP = 'UPDATE'
       AND OLD.archive_transition = 'DELETING'
       AND OLD.deletion_phase = 'RELEASING'
       AND NEW.archive_transition = 'NONE'
       AND NEW.deletion_phase IS NULL AND NEW.transition_operation_id IS NULL
       AND NEW.state_version = OLD.state_version + 1
       AND NEW.is_active = (SELECT prior_state = 'ACTIVE'
                              FROM semester_archive_operations
                             WHERE operation_id = OLD.transition_operation_id
                               AND semester_id = OLD.id AND action = 'DELETE'
                               AND state_version = OLD.state_version
                               AND delete_phase = 'RELEASING' AND NOT irreversible_intent
                               AND academic_status = 'RELEASED' AND schedule_status = 'RELEASED'
                               AND attendance_status = 'RELEASED')
       AND NEW.is_archived = (SELECT prior_state = 'ARCHIVED'
                                FROM semester_archive_operations
                               WHERE operation_id = OLD.transition_operation_id
                                 AND semester_id = OLD.id AND action = 'DELETE'
                                 AND state_version = OLD.state_version
                                 AND delete_phase = 'RELEASING' AND NOT irreversible_intent
                                 AND academic_status = 'RELEASED' AND schedule_status = 'RELEASED'
                                 AND attendance_status = 'RELEASED')
       AND NOT NEW.archive_release_pending
       AND NEW.name IS NOT DISTINCT FROM OLD.name
       AND NEW.date_from IS NOT DISTINCT FROM OLD.date_from
       AND NEW.date_to IS NOT DISTINCT FROM OLD.date_to
       AND NEW.first_week_type IS NOT DISTINCT FROM OLD.first_week_type
       AND NEW.semester_type IS NOT DISTINCT FROM OLD.semester_type
       AND NEW.academic_year IS NOT DISTINCT FROM OLD.academic_year
       AND EXISTS (SELECT 1 FROM semester_archive_operations operation
                    JOIN academic_semester_archive_barriers barrier
                      ON barrier.operation_id = operation.operation_id
                     AND barrier.semester_id = operation.semester_id
                     AND barrier.state_version = operation.state_version
                   WHERE operation.operation_id = OLD.transition_operation_id
                     AND operation.semester_id = OLD.id AND operation.action = 'DELETE'
                     AND operation.state_version = OLD.state_version
                     AND operation.delete_phase = 'RELEASING' AND NOT operation.irreversible_intent
                     AND operation.academic_status = 'RELEASED'
                     AND operation.schedule_status = 'RELEASED'
                     AND operation.attendance_status = 'RELEASED'
                     AND barrier.participant_state = 'RELEASED') THEN
        RETURN NEW;
    END IF;
    IF TG_OP = 'UPDATE'
       AND OLD.archive_transition = 'DELETING'
       AND NEW.archive_transition = 'NONE'
       AND NEW.deletion_phase IS NULL AND NEW.transition_operation_id IS NULL
       AND NOT NEW.is_active AND NOT NEW.is_archived
       AND academic_semester_delete_authorized(OLD.id) THEN
        RETURN NEW;
    END IF;
    IF TG_OP = 'DELETE'
       AND academic_semester_delete_authorized(OLD.id)
       AND NOT EXISTS (SELECT 1 FROM assignments WHERE semester_id = OLD.id)
       AND NOT EXISTS (SELECT 1 FROM homeworks WHERE semester_id = OLD.id) THEN
        RETURN OLD;
    END IF;
    IF TG_OP = 'UPDATE' THEN
        IF OLD.archive_transition = 'DELETING' OR NEW.archive_transition = 'DELETING'
           OR OLD.deletion_phase IS NOT NULL OR NEW.deletion_phase IS NOT NULL
           OR OLD.transition_operation_id IS NOT NULL OR NEW.transition_operation_id IS NOT NULL THEN
            RAISE EXCEPTION 'Academic semester deletion authority is immutable outside its exact operation'
                USING ERRCODE = '55000';
        END IF;
        RETURN NEW;
    END IF;
    RAISE EXCEPTION 'Academic semester cannot be deleted without all exact participant receipts'
        USING ERRCODE = '55000';
END
$$;

CREATE TRIGGER semesters_deletion_authority_guard
    BEFORE UPDATE OR DELETE ON semesters
    FOR EACH ROW EXECUTE FUNCTION guard_academic_semester_delete_authority();

CREATE OR REPLACE FUNCTION guard_academic_semester_archive_business_write()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
DECLARE
    old_row JSONB;
    new_row JSONB;
    old_semester_id BIGINT;
    new_semester_id BIGINT;
    target_semester_id BIGINT;
    barrier_state VARCHAR(20);
    authority_transition VARCHAR(10);
    authority_version BIGINT;
    authority_archived BOOLEAN;
    authority_release_pending BOOLEAN;
    authority_active BOOLEAN;
    authority_blocked BOOLEAN;
    archive_event_id UUID;
    request_key_text TEXT;
    transfer_event_id UUID;
    transfer_operation_id UUID;
    transfer_batch_index INTEGER;
    transfer_binding_id BIGINT;
    transfer_semester_id BIGINT;
    transfer_operation_hash TEXT;
    transfer_batch_hash TEXT;
    transfer_binding_hash TEXT;
    admitted BOOLEAN := FALSE;
    admitted_count INTEGER := 0;
    event_admitted BOOLEAN := FALSE;
BEGIN
    IF TG_OP <> 'INSERT' THEN old_row := to_jsonb(OLD); END IF;
    IF TG_OP <> 'DELETE' THEN new_row := to_jsonb(NEW); END IF;

    IF TG_TABLE_NAME = 'homework_completions' THEN
        IF old_row IS NOT NULL THEN
            SELECT semester_id INTO old_semester_id FROM homeworks
             WHERE id = (old_row ->> 'homework_id')::BIGINT;
        END IF;
        IF new_row IS NOT NULL THEN
            SELECT semester_id INTO new_semester_id FROM homeworks
             WHERE id = (new_row ->> 'homework_id')::BIGINT;
        END IF;
    ELSE
        old_semester_id := nullif(old_row ->> 'semester_id', '')::BIGINT;
        new_semester_id := nullif(new_row ->> 'semester_id', '')::BIGINT;
    END IF;

    IF old_semester_id IS NULL AND new_semester_id IS NULL THEN
        IF EXISTS (SELECT 1 FROM academic_semester_archive_barriers
                    WHERE participant_state IN ('PENDING', 'READY', 'PREPARED_RESTORE', 'DELETE_PREPARING', 'DELETE_SEALED', 'DELETED')) THEN
            RAISE EXCEPTION 'Academic archive fence cannot resolve the affected semester'
                USING ERRCODE = '55000';
        END IF;
        IF TG_OP = 'DELETE' THEN RETURN OLD; END IF;
        RETURN NEW;
    END IF;

    archive_event_id := nullif(current_setting('rutcampustrack.archive_effect_event_id', TRUE), '')::UUID;
    request_key_text := nullif(current_setting('rutcampustrack.archive_publication_request_key', TRUE), '');
    transfer_event_id := nullif(current_setting('rutcampustrack.archive_transfer_event_id', TRUE), '')::UUID;
    transfer_operation_id := nullif(current_setting('rutcampustrack.archive_transfer_operation_id', TRUE), '')::UUID;
    transfer_batch_index := nullif(current_setting('rutcampustrack.archive_transfer_batch_index', TRUE), '')::INTEGER;
    transfer_binding_id := nullif(current_setting('rutcampustrack.archive_transfer_binding_id', TRUE), '')::BIGINT;
    transfer_semester_id := nullif(current_setting('rutcampustrack.archive_transfer_semester_id', TRUE), '')::BIGINT;
    transfer_operation_hash := nullif(current_setting('rutcampustrack.archive_transfer_operation_hash', TRUE), '');
    transfer_batch_hash := nullif(current_setting('rutcampustrack.archive_transfer_batch_hash', TRUE), '');
    transfer_binding_hash := nullif(current_setting('rutcampustrack.archive_transfer_binding_hash', TRUE), '');

    FOR target_semester_id IN
        SELECT DISTINCT candidate
          FROM unnest(ARRAY[old_semester_id, new_semester_id]) AS candidate
         WHERE candidate IS NOT NULL
         ORDER BY candidate
    LOOP
        admitted := FALSE;
        admitted_count := 0;
        event_admitted := FALSE;
        PERFORM pg_advisory_xact_lock(5452097, (target_semester_id % 2147483647)::INTEGER);
        SELECT participant_state INTO barrier_state
          FROM academic_semester_archive_barriers
         WHERE semester_id = target_semester_id;
        SELECT is_active, is_archived, archive_transition, archive_release_pending, state_version
          INTO authority_active, authority_archived, authority_transition,
               authority_release_pending, authority_version
          FROM semesters WHERE id = target_semester_id;
        authority_blocked := NOT FOUND OR authority_archived OR authority_release_pending
                OR authority_transition <> 'NONE';

        IF TG_OP = 'DELETE' AND academic_semester_delete_authorized(target_semester_id) THEN
            CONTINUE;
        END IF;
        IF TG_TABLE_NAME = 'homework_binding_archives' THEN
        IF TG_OP = 'UPDATE' AND OLD.homework_id IS NOT NULL AND NEW.homework_id IS NULL
           AND (to_jsonb(NEW) - 'homework_id') = (to_jsonb(OLD) - 'homework_id')
           AND academic_semester_delete_authorized(target_semester_id) THEN
            CONTINUE;
        END IF;
        END IF;

        IF barrier_state IN ('PENDING', 'READY', 'PREPARED_RESTORE', 'DELETE_PREPARING', 'DELETE_SEALED', 'DELETED') OR authority_blocked THEN
            IF TG_TABLE_NAME = 'homeworks' THEN
                IF ((barrier_state = 'PENDING' AND authority_transition = 'ARCHIVING')
                    OR academic_semester_delete_preparing(target_semester_id))
                    AND authority_version = (SELECT state_version FROM academic_semester_archive_barriers
                                              WHERE semester_id = target_semester_id)
                    AND request_key_text IS NOT NULL THEN
                IF TG_OP = 'INSERT' AND NEW.publication_state = 'PENDING'
                        AND NEW.semester_id = target_semester_id
                        AND NEW.request_key::TEXT = request_key_text THEN
                    UPDATE academic_semester_archive_publication_admissions
                       SET admitted_homework_id = NEW.id
                     WHERE operation_id = (SELECT operation_id FROM academic_semester_archive_barriers
                                             WHERE semester_id = target_semester_id)
                       AND state_version = authority_version
                       AND semester_id = target_semester_id
                       AND binding_id = NEW.binding_id AND actor_id = NEW.actor_id
                       AND request_key = NEW.request_key AND payload_hash = NEW.payload_hash
                       AND resolution_state = 'ADMITTED'
                       AND admitted_homework_id IS NULL AND consumed_at IS NULL;
                    GET DIAGNOSTICS admitted_count = ROW_COUNT;
                    admitted := admitted_count = 1;
                ELSIF TG_OP = 'INSERT' AND NEW.publication_state = 'ARCHIVED'
                        AND NEW.semester_id = target_semester_id
                        AND NEW.request_key::TEXT = request_key_text THEN
                    UPDATE academic_semester_archive_publication_admissions admission
                       SET admitted_homework_id = NEW.id, consumed_at = now()
                     WHERE admission.operation_id = (SELECT operation_id
                         FROM academic_semester_archive_barriers WHERE semester_id = target_semester_id)
                       AND admission.state_version = authority_version
                       AND admission.semester_id = target_semester_id
                       AND admission.binding_id = NEW.binding_id AND admission.actor_id = NEW.actor_id
                       AND admission.request_key = NEW.request_key
                       AND admission.payload_hash = NEW.payload_hash
                       AND admission.admitted_homework_id IS NULL AND admission.consumed_at IS NULL
                       AND EXISTS (
                           SELECT 1 FROM homework_binding_archives marker
                           JOIN academic_semester_archive_effect_receipts receipt
                             ON receipt.source_event_id = marker.source_event_id
                          WHERE marker.binding_id = NEW.binding_id AND marker.actor_id = NEW.actor_id
                            AND marker.request_key = NEW.request_key
                            AND marker.semester_id = target_semester_id
                            AND marker.source_event_id IS NOT NULL AND receipt.result = 'APPLIED'
                            AND receipt.semester_id = target_semester_id
                            AND receipt.binding_id = NEW.binding_id);
                    GET DIAGNOSTICS admitted_count = ROW_COUNT;
                    admitted := admitted_count = 1;
                ELSIF TG_OP = 'UPDATE' AND OLD.publication_state = 'PENDING'
                        AND NEW.publication_state = 'ACTIVE'
                        AND NEW.id = OLD.id AND NEW.semester_id = OLD.semester_id
                        AND NEW.binding_id = OLD.binding_id AND NEW.actor_id = OLD.actor_id
                        AND NEW.request_key = OLD.request_key AND NEW.payload_hash = OLD.payload_hash
                        AND NEW.request_key::TEXT = request_key_text THEN
                    UPDATE academic_semester_archive_publication_admissions
                       SET consumed_at = now()
                     WHERE operation_id = (SELECT operation_id FROM academic_semester_archive_barriers
                                             WHERE semester_id = target_semester_id)
                       AND state_version = authority_version
                       AND semester_id = target_semester_id
                       AND binding_id = NEW.binding_id AND actor_id = NEW.actor_id
                       AND request_key = NEW.request_key AND payload_hash = NEW.payload_hash
                       AND resolution_state = 'ADMITTED'
                       AND admitted_homework_id = NEW.id AND consumed_at IS NULL;
                    GET DIAGNOSTICS admitted_count = ROW_COUNT;
                    admitted := admitted_count = 1;
                END IF;
                IF admitted THEN CONTINUE; END IF;
                END IF;
            END IF;

            IF TG_TABLE_NAME = 'homeworks' THEN
                IF ((barrier_state = 'PENDING' AND authority_transition = 'ARCHIVING')
                    OR academic_semester_delete_preparing(target_semester_id))
                    AND TG_OP = 'UPDATE'
                    AND NEW.publication_state = 'ARCHIVED'
                    AND OLD.publication_state IN ('PENDING', 'ACTIVE')
                    AND NEW.semester_id = OLD.semester_id
                    AND archive_event_id IS NOT NULL THEN
                SELECT EXISTS (
                    SELECT 1 FROM academic_semester_archive_effect_receipts receipt
                     WHERE receipt.source_event_id = archive_event_id
                       AND receipt.event_type = 'homework.binding.archived'
                       AND receipt.semester_id = target_semester_id
                       AND receipt.binding_id = OLD.binding_id
                       AND receipt.state = 'PENDING') INTO event_admitted;
                IF event_admitted THEN
                    UPDATE academic_semester_archive_publication_admissions
                       SET consumed_at = now()
                     WHERE operation_id = (SELECT operation_id FROM academic_semester_archive_barriers
                                             WHERE semester_id = target_semester_id)
                       AND state_version = authority_version
                       AND semester_id = target_semester_id
                       AND binding_id = OLD.binding_id AND admitted_homework_id = OLD.id
                       AND consumed_at IS NULL;
                    CONTINUE;
                END IF;
                END IF;
            END IF;

            IF TG_TABLE_NAME = 'homework_binding_archives'
                    AND TG_OP IN ('INSERT', 'UPDATE')
                    AND coalesce(new_semester_id, old_semester_id) = target_semester_id THEN
                SELECT EXISTS (
                    SELECT 1 FROM academic_semester_archive_effect_receipts receipt
                     WHERE receipt.source_event_id = archive_event_id
                       AND receipt.event_type = 'homework.binding.archived'
                       AND receipt.semester_id = target_semester_id
                       AND receipt.binding_id = (coalesce(new_row, old_row) ->> 'binding_id')::BIGINT
                       AND receipt.state = 'PENDING'
                       AND ((barrier_state = 'PENDING' AND authority_transition = 'ARCHIVING')
                    OR academic_semester_delete_preparing(target_semester_id))) INTO event_admitted;
                IF NOT event_admitted AND TG_OP <> 'DELETE'
                        AND NEW.homework_id IS NOT NULL THEN
                    SELECT EXISTS (
                        SELECT 1 FROM academic_semester_archive_publication_admissions admission
                         WHERE admission.operation_id = (SELECT operation_id
                             FROM academic_semester_archive_barriers WHERE semester_id = target_semester_id)
                           AND admission.state_version = authority_version
                           AND admission.semester_id = target_semester_id
                           AND admission.binding_id = NEW.binding_id
                           AND admission.actor_id = NEW.actor_id
                           AND admission.request_key = NEW.request_key
                           AND admission.admitted_homework_id = NEW.homework_id
                           AND admission.consumed_at IS NOT NULL
                    ) INTO event_admitted;
                END IF;
                IF event_admitted THEN CONTINUE; END IF;
            END IF;

            IF TG_TABLE_NAME = 'homework_binding_transfer_markers' THEN
                IF TG_OP = 'UPDATE'
                    AND OLD.state = 'PENDING'
                    AND NEW.state = 'CANCELLED_UNPUBLISHED'
                    AND OLD.homework_id IS NULL AND NEW.homework_id IS NULL
                    AND NEW.binding_id = OLD.binding_id
                    AND NEW.actor_id = OLD.actor_id
                    AND NEW.request_key = OLD.request_key
                    AND NEW.binding_payload_hash = OLD.binding_payload_hash
                    AND NEW.source_event_id = OLD.source_event_id
                    AND NEW.batch_index = OLD.batch_index
                    AND NEW.operation_id = OLD.operation_id
                    AND NEW.operation_hash = OLD.operation_hash
                    AND NEW.semester_id = target_semester_id
                    AND archive_event_id IS NOT NULL
                    AND ((barrier_state = 'PENDING' AND authority_transition = 'ARCHIVING')
                    OR academic_semester_delete_preparing(target_semester_id))
                    AND EXISTS (
                        SELECT 1 FROM academic_semester_archive_effect_receipts receipt
                         WHERE receipt.source_event_id = archive_event_id
                           AND receipt.event_type = 'homework.binding.archived'
                           AND receipt.semester_id = target_semester_id
                           AND receipt.binding_id = NEW.binding_id
                           AND receipt.state = 'PENDING')
                    AND EXISTS (
                        SELECT 1 FROM academic_semester_archive_publication_admissions admission
                         WHERE admission.operation_id = (SELECT operation_id
                             FROM academic_semester_archive_barriers WHERE semester_id = target_semester_id)
                           AND admission.state_version = authority_version
                           AND admission.semester_id = target_semester_id
                           AND admission.binding_id = NEW.binding_id
                           AND admission.actor_id = NEW.actor_id
                           AND admission.request_key = NEW.request_key
                           AND admission.payload_hash = NEW.binding_payload_hash
                           AND admission.source_event_id = OLD.source_event_id
                           AND admission.terminal_event_id = archive_event_id
                           AND admission.resolution_state = 'CANCEL_REQUESTED') THEN
                    RETURN NEW;
                END IF;
            END IF;

            IF ((barrier_state = 'PENDING' AND authority_transition = 'ARCHIVING')
                    OR academic_semester_delete_preparing(target_semester_id))
                    AND transfer_event_id IS NOT NULL
                    AND transfer_operation_id IS NOT NULL
                    AND transfer_binding_id IS NOT NULL
                    AND transfer_semester_id = target_semester_id
                    AND transfer_binding_id = (coalesce(new_row, old_row) ->> 'binding_id')::BIGINT
                    AND transfer_operation_hash ~ '^[0-9a-f]{64}$'
                    AND transfer_batch_hash ~ '^[0-9a-f]{64}$'
                    AND transfer_binding_hash ~ '^[0-9a-f]{64}$' THEN
                IF TG_TABLE_NAME = 'homeworks' THEN
                    IF TG_OP = 'UPDATE'
                        AND old_semester_id = target_semester_id
                        AND new_semester_id = target_semester_id
                        AND NEW.id = OLD.id AND NEW.binding_id = OLD.binding_id
                        AND NEW.actor_id = OLD.actor_id AND NEW.request_key = OLD.request_key
                        AND NEW.payload_hash = OLD.payload_hash
                        AND NEW.publication_state = OLD.publication_state
                        AND OLD.binding_id = transfer_binding_id THEN
                        RETURN NEW;
                    END IF;
                END IF;
                IF TG_TABLE_NAME = 'homework_binding_transfer_markers' THEN
                    IF new_semester_id = target_semester_id
                        AND NEW.operation_id = transfer_operation_id
                        AND NEW.source_event_id = transfer_event_id
                        AND NEW.batch_index = transfer_batch_index
                        AND NEW.binding_id = transfer_binding_id
                        AND encode(NEW.operation_hash, 'hex') = transfer_operation_hash
                        AND encode(NEW.binding_payload_hash, 'hex') = transfer_binding_hash
                        AND (TG_OP = 'INSERT' OR (OLD.semester_id = NEW.semester_id
                             AND OLD.binding_id = NEW.binding_id)) THEN
                        RETURN NEW;
                    END IF;
                END IF;
            END IF;

            RAISE EXCEPTION 'Academic semester % is fenced by archive barrier', target_semester_id
                USING ERRCODE = '55000';
        END IF;
    END LOOP;

    IF TG_OP = 'DELETE' THEN RETURN OLD; END IF;
    RETURN NEW;
END;
$$;

