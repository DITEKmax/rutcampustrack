-- Legacy scope is deliberately not inferred or repaired.
ALTER TABLE subjects
    ADD COLUMN semester_id bigint REFERENCES semesters (id);

CREATE TABLE subject_deletion_operations (
    operation_id uuid PRIMARY KEY,
    idempotency_key uuid NOT NULL UNIQUE,
    subject_id bigint NOT NULL CHECK (subject_id > 0),
    semester_id bigint NOT NULL CHECK (semester_id > 0),
    group_id bigint NOT NULL CHECK (group_id > 0),
    actor_id bigint NOT NULL CHECK (actor_id > 0),
    session_id uuid NOT NULL,
    confirmation_hash varchar(64) NOT NULL CHECK (confirmation_hash ~ '^[0-9a-f]{64}$'),
    preview_digest varchar(64) NOT NULL CHECK (preview_digest ~ '^[0-9a-f]{64}$'),
    academic_digest varchar(64) NOT NULL,
    academic_manifest jsonb NOT NULL CHECK (jsonb_typeof(academic_manifest) = 'array'),
    schedule_digest varchar(64) NOT NULL,
    attendance_digest varchar(64) NOT NULL,
    manifest bytea NOT NULL,
    phase varchar(16) NOT NULL CHECK (phase IN ('PREPARING', 'SEALED', 'COMMITTING', 'COMPLETED', 'RELEASING', 'CANCELLED')),
    schedule_state varchar(16) NOT NULL DEFAULT 'PENDING',
    attendance_state varchar(16) NOT NULL DEFAULT 'PENDING',
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    last_failure varchar(32),
    CHECK (schedule_state IN ('PENDING', 'PREPARED', 'SEALED', 'DELETED', 'RELEASED')),
    CHECK (attendance_state IN ('PENDING', 'PREPARED', 'SEALED', 'DELETED', 'RELEASED'))
);

CREATE UNIQUE INDEX subject_deletion_one_current ON subject_deletion_operations (subject_id)
WHERE
    phase NOT IN ('COMPLETED', 'CANCELLED');

CREATE FUNCTION guard_subject_semester_identity ()
    RETURNS TRIGGER
    LANGUAGE plpgsql
    AS $$
BEGIN
    IF TG_OP = 'UPDATE' AND NEW.semester_id IS DISTINCT FROM OLD.semester_id THEN
        RAISE EXCEPTION 'subject semester identity is immutable';
    END IF;
    RETURN NEW;
END
$$;

CREATE TRIGGER subject_semester_identity
    BEFORE INSERT OR UPDATE ON subjects
    FOR EACH ROW
    EXECUTE FUNCTION guard_subject_semester_identity ();

-- Auth owns proof rows; Academic atomically binds one consumed proof to its exact command.
CREATE TABLE subject_deletion_proofs (
    challenge_hash varchar(64) PRIMARY KEY,
    operation_id uuid NOT NULL,
    subject_id bigint NOT NULL CHECK (subject_id > 0),
    semester_id bigint NOT NULL CHECK (semester_id > 0),
    group_id bigint NOT NULL CHECK (group_id > 0),
    actor_id bigint NOT NULL CHECK (actor_id > 0),
    session_id uuid NOT NULL,
    session_version bigint NOT NULL,
    roles_version bigint NOT NULL,
    credentials_version bigint NOT NULL,
    binding_version bigint NOT NULL,
    telegram_id bigint NOT NULL,
    preview_digest varchar(64) NOT NULL CHECK (preview_digest ~ '^[0-9a-f]{64}$'),
    code_hash varchar(64) NOT NULL,
    attempts integer NOT NULL DEFAULT 0 CHECK (attempts >= 0),
    created_at timestamptz NOT NULL,
    expires_at timestamptz NOT NULL,
    consumed_at timestamptz,
    receipt_id uuid UNIQUE,
    CHECK (expires_at > created_at),
    CHECK ((consumed_at IS NULL) = (receipt_id IS NULL))
);

CREATE INDEX subject_deletion_proof_actor_time ON subject_deletion_proofs (actor_id, created_at);

ALTER TABLE subject_deletion_operations
    ADD COLUMN proof_receipt_id uuid NOT NULL UNIQUE REFERENCES subject_deletion_proofs (receipt_id);

CREATE FUNCTION academic_subject_delete_row_authorized (kind text, old_row jsonb)
    RETURNS boolean
    LANGUAGE SQL
    STABLE
    AS $$
    SELECT
        EXISTS (
            SELECT
                1
            FROM
                subject_deletion_operations operation
            WHERE
                operation.operation_id = NULLIF (current_setting('rutcampustrack.subject_delete_operation_id', TRUE), '')::uuid
                AND operation.phase = 'COMMITTING'
                AND operation.schedule_state = 'DELETED'
                AND operation.attendance_state = 'DELETED'
                AND operation.academic_manifest @> jsonb_build_array(jsonb_build_object('kind', kind, 'row_hash', encode(public.digest(convert_to(old_row::text, 'UTF8'), 'sha256'), 'hex'))))
$$;

CREATE FUNCTION guard_academic_subject_write ()
    RETURNS TRIGGER
    LANGUAGE plpgsql
    AS $$
DECLARE
    old_row jsonb;
    new_row jsonb;
    row_data jsonb;
    target_subject bigint;
    target_semester bigint;
    operation_id_value uuid;
    phase_value text;
    bound_semester bigint;
BEGIN
    IF TG_OP <> 'INSERT' THEN
        old_row = to_jsonb (OLD);
    END IF;
    IF TG_OP <> 'DELETE' THEN
        new_row = to_jsonb (NEW);
    END IF;
    FOR row_data IN
    SELECT
        *
    FROM
        unnest(ARRAY[old_row, new_row])
    WHERE
        unnest IS NOT NULL LOOP
            target_subject = NULLIF (row_data ->> 'subject_id', '')::bigint;
            -- Global/group-only thresholds have no subject lifecycle identity.
            IF TG_TABLE_NAME = 'attendance_thresholds' AND target_subject IS NULL THEN
                CONTINUE;
            END IF;
            IF TG_TABLE_NAME = 'subjects' THEN
                target_subject = (row_data ->> 'id')::bigint;
            END IF;
            IF target_subject IS NULL AND row_data ->> 'homework_id' IS NOT NULL THEN
                SELECT
                    subject_id INTO target_subject
                FROM
                    homeworks
                WHERE
                    id = (row_data ->> 'homework_id')::bigint;
            END IF;
            IF target_subject IS NULL AND TG_TABLE_NAME = 'homework_edit_archive_admissions' THEN
                SELECT
                    subject_id INTO target_subject
                FROM
                    homework_edit_operations
                WHERE
                    operation_id = (row_data ->> 'edit_operation_id')::uuid;
            END IF;
            IF target_subject IS NULL THEN
                SELECT
                    operation.subject_id INTO target_subject
                FROM
                    subject_deletion_operations operation
                WHERE
                    operation.phase NOT IN ('COMPLETED', 'CANCELLED')
                    AND operation.academic_manifest @> jsonb_build_array(jsonb_build_object('kind', TG_TABLE_NAME, 'row_hash', encode(public.digest(convert_to(row_data::text, 'UTF8'), 'sha256'), 'hex')));
            END IF;
            IF target_subject IS NULL THEN
                CONTINUE;
            END IF;
            PERFORM
                1
            FROM
                subjects
            WHERE
                id = target_subject
            FOR UPDATE;
            SELECT
                semester_id INTO bound_semester
            FROM
                subjects
            WHERE
                id = target_subject;
            -- Preserve ordinary writes to unresolved legacy rows without inferring a binding.
            -- The destructive API independently refuses every NULL/ambiguous legacy subject.
            IF TG_TABLE_NAME IN ('assignments', 'teacher_subject_groups', 'homeworks') AND TG_OP <> 'DELETE' AND bound_semester IS NOT NULL AND bound_semester IS DISTINCT FROM (new_row ->> 'semester_id')::bigint THEN
                RAISE EXCEPTION 'subject belongs to a different semester';
            END IF;
            SELECT
                operation_id,
                phase INTO operation_id_value,
                phase_value
            FROM
                subject_deletion_operations
            WHERE
                subject_id = target_subject
                AND phase NOT IN ('COMPLETED', 'CANCELLED');
            IF operation_id_value IS NOT NULL AND NOT (phase_value = 'COMMITTING' AND operation_id_value = NULLIF (current_setting('rutcampustrack.subject_delete_operation_id', TRUE), '')::uuid AND (TG_OP = 'DELETE' AND academic_subject_delete_row_authorized (TG_TABLE_NAME, old_row) OR TG_OP = 'UPDATE' AND TG_TABLE_NAME = 'homework_binding_archives' AND new_row ->> 'homework_id' IS NULL AND (new_row - 'homework_id') = (old_row - 'homework_id') AND academic_subject_delete_row_authorized (TG_TABLE_NAME, old_row) OR TG_OP = 'UPDATE' AND TG_TABLE_NAME = 'homework_binding_transfer_markers' AND new_row ->> 'superseded_by_edit_operation_id' IS NULL AND (new_row - 'superseded_by_edit_operation_id') = (old_row - 'superseded_by_edit_operation_id') AND academic_subject_delete_row_authorized (TG_TABLE_NAME, old_row))) THEN
                RAISE EXCEPTION 'subject is fenced for deletion';
            END IF;
        END LOOP;
    IF TG_OP = 'DELETE' THEN
        RETURN OLD;
    END IF;
    RETURN NEW;
END
$$;

CREATE TRIGGER a0_subject_delete_fence
    BEFORE INSERT OR UPDATE OR DELETE ON subjects
    FOR EACH ROW
    EXECUTE FUNCTION guard_academic_subject_write ();

CREATE TRIGGER a0_subject_delete_fence
    BEFORE INSERT OR UPDATE OR DELETE ON subject_lesson_types
    FOR EACH ROW
    EXECUTE FUNCTION guard_academic_subject_write ();

CREATE TRIGGER a0_subject_delete_fence
    BEFORE INSERT OR UPDATE OR DELETE ON assignments
    FOR EACH ROW
    EXECUTE FUNCTION guard_academic_subject_write ();

CREATE TRIGGER a0_subject_delete_fence
    BEFORE INSERT OR UPDATE OR DELETE ON teacher_subject_groups
    FOR EACH ROW
    EXECUTE FUNCTION guard_academic_subject_write ();

CREATE TRIGGER a0_subject_delete_fence
    BEFORE INSERT OR UPDATE OR DELETE ON attendance_thresholds
    FOR EACH ROW
    EXECUTE FUNCTION guard_academic_subject_write ();

CREATE TRIGGER a0_subject_delete_fence
    BEFORE INSERT OR UPDATE OR DELETE ON homeworks
    FOR EACH ROW
    EXECUTE FUNCTION guard_academic_subject_write ();

CREATE TRIGGER a0_subject_delete_fence
    BEFORE INSERT OR UPDATE OR DELETE ON homework_completions
    FOR EACH ROW
    EXECUTE FUNCTION guard_academic_subject_write ();

CREATE TRIGGER a0_subject_delete_fence
    BEFORE INSERT OR UPDATE OR DELETE ON homework_edit_history
    FOR EACH ROW
    EXECUTE FUNCTION guard_academic_subject_write ();

CREATE TRIGGER a0_subject_delete_fence
    BEFORE INSERT OR UPDATE OR DELETE ON homework_edit_receipts
    FOR EACH ROW
    EXECUTE FUNCTION guard_academic_subject_write ();

CREATE TRIGGER a0_subject_delete_fence
    BEFORE INSERT OR UPDATE OR DELETE ON homework_edit_operations
    FOR EACH ROW
    EXECUTE FUNCTION guard_academic_subject_write ();

CREATE TRIGGER a0_subject_delete_fence
    BEFORE INSERT OR UPDATE OR DELETE ON homework_edit_archive_admissions
    FOR EACH ROW
    EXECUTE FUNCTION guard_academic_subject_write ();

CREATE TRIGGER a0_subject_delete_fence
    BEFORE INSERT OR UPDATE OR DELETE ON homework_binding_archives
    FOR EACH ROW
    EXECUTE FUNCTION guard_academic_subject_write ();

CREATE TRIGGER a0_subject_delete_fence
    BEFORE INSERT OR UPDATE OR DELETE ON homework_binding_transfer_markers
    FOR EACH ROW
    EXECUTE FUNCTION guard_academic_subject_write ();

-- Preserve the complete current guards, including V47 edit identities and V49 lifecycle.
-- Only the exact sealed row receives an additional deletion/neutralization admission.
DO $$
DECLARE
    definition text;
    updated text;
    anchor integer;
    function_name text;
    admission text;
BEGIN
    FOREACH function_name IN ARRAY ARRAY['protect_assignment_identity', 'retain_homework_edit_evidence', 'protect_homework_edit_operation', 'protect_homework_binding_archive_identity', 'protect_homework_transfer_edit_supersession'] LOOP
        SELECT
            pg_get_functiondef((function_name || '()')::regprocEDURE) INTO definition;
        IF function_name IN ('protect_assignment_identity', 'retain_homework_edit_evidence', 'protect_homework_edit_operation') THEN
            admission := E'    IF TG_OP = ''DELETE'' AND academic_subject_delete_row_authorized(TG_TABLE_NAME,to_jsonb(OLD)) THEN RETURN OLD; END IF;\n';
        ELSIF function_name = 'protect_homework_binding_archive_identity' THEN
            admission := E'    IF TG_OP = ''UPDATE'' AND NEW.homework_id IS NULL AND (to_jsonb(NEW)-''homework_id'')=(to_jsonb(OLD)-''homework_id'') AND academic_subject_delete_row_authorized(TG_TABLE_NAME,to_jsonb(OLD)) THEN RETURN NEW; END IF;\n';
        ELSE
            admission := E'    IF TG_OP = ''UPDATE'' AND NEW.superseded_by_edit_operation_id IS NULL AND (to_jsonb(NEW)-''superseded_by_edit_operation_id'')=(to_jsonb(OLD)-''superseded_by_edit_operation_id'') AND academic_subject_delete_row_authorized(TG_TABLE_NAME,to_jsonb(OLD)) THEN RETURN NEW; END IF;\n';
        END IF;
        anchor := strpos(definition, E'BEGIN\n');
        IF anchor = 0 THEN
            RAISE EXCEPTION 'subject deletion guard admission anchor changed';
        END IF;
        updated := overlay(definition PLACING E'BEGIN\n' || admission FROM anchor FOR 6);
        IF updated = definition THEN
            RAISE EXCEPTION 'subject deletion guard admission not installed';
        END IF;
        EXECUTE updated;
    END LOOP;
END
$$;

-- The semester row lock orders this check with the subject command's acceptance transaction.
-- No archive/activation may begin in the gap between irreversible intent and remote receipts.
CREATE FUNCTION guard_subject_delete_semester_transition ()
    RETURNS TRIGGER
    LANGUAGE plpgsql
    AS $$
BEGIN
    IF EXISTS (
        SELECT
            1
        FROM
            subject_deletion_operations operation
        WHERE
            operation.semester_id = OLD.id
            AND operation.phase NOT IN ('COMPLETED', 'CANCELLED'))
        AND (TG_OP = 'DELETE'
            OR (to_jsonb (OLD) - ARRAY['name']) IS DISTINCT FROM (to_jsonb (NEW) - ARRAY['name'])) THEN
        RAISE EXCEPTION 'accepted subject deletion blocks semester transition'
        USING ERRCODE = '23514';
    END IF;
    IF TG_OP = 'DELETE' THEN
        RETURN OLD;
    END IF;
    RETURN NEW;
END
$$;

CREATE TRIGGER a0_subject_delete_semester_transition
    BEFORE UPDATE OR DELETE ON semesters
    FOR EACH ROW
    EXECUTE FUNCTION guard_subject_delete_semester_transition ();
