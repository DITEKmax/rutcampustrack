ALTER TABLE semesters
    ADD COLUMN archive_release_pending BOOLEAN NOT NULL DEFAULT FALSE,
    ADD CONSTRAINT semesters_archive_release_pending_consistent
        CHECK (NOT archive_release_pending OR (NOT is_active AND NOT is_archived AND archive_transition = 'NONE'));

CREATE TABLE semester_archive_operations (
    operation_id UUID PRIMARY KEY,
    idempotency_key UUID NOT NULL UNIQUE,
    actor_id BIGINT NOT NULL,
    semester_id BIGINT NOT NULL,
    action VARCHAR(8) NOT NULL,
    operation_state VARCHAR(10) NOT NULL,
    retryable BOOLEAN NOT NULL,
    state_version BIGINT NOT NULL,
    transition VARCHAR(10) NOT NULL,
    is_active BOOLEAN NOT NULL,
    is_archived BOOLEAN NOT NULL,
    release_pending BOOLEAN NOT NULL,
    academic_status VARCHAR(20) NOT NULL,
    schedule_status VARCHAR(20) NOT NULL,
    attendance_status VARCHAR(20) NOT NULL,
    blocking_reason TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    row_version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT semester_archive_operations_action_valid
        CHECK (action IN ('ARCHIVE', 'RESTORE')),
    CONSTRAINT semester_archive_operations_state_valid
        CHECK (operation_state IN ('PENDING', 'COMPLETED', 'ERROR')),
    CONSTRAINT semester_archive_operations_transition_valid
        CHECK (transition IN ('NONE', 'ARCHIVING', 'RESTORING')),
    CONSTRAINT semester_archive_operations_academic_status_valid
        CHECK (academic_status IN ('NOT_STARTED', 'PENDING', 'READY', 'PREPARED_RESTORE', 'RELEASE_PENDING', 'RELEASED')),
    CONSTRAINT semester_archive_operations_schedule_status_valid
        CHECK (schedule_status IN ('NOT_STARTED', 'PENDING', 'READY', 'PREPARED_RESTORE', 'RELEASE_PENDING', 'RELEASED')),
    CONSTRAINT semester_archive_operations_attendance_status_valid
        CHECK (attendance_status IN ('NOT_STARTED', 'PENDING', 'READY', 'PREPARED_RESTORE', 'RELEASE_PENDING', 'RELEASED')),
    CONSTRAINT semester_archive_operations_state_version_nonnegative
        CHECK (state_version >= 0)
);

CREATE INDEX semester_archive_operations_semester_created
    ON semester_archive_operations (semester_id, created_at DESC, operation_id DESC);

CREATE UNIQUE INDEX semester_archive_operations_one_live_per_semester
    ON semester_archive_operations (semester_id)
    WHERE operation_state IN ('PENDING', 'ERROR');

-- The local Academic fence is installed in the same transaction as the
-- authoritative ARCHIVING/RESTORING transition. It is independent of the
-- operation history row so later operation replay cannot replace its epoch.
CREATE TABLE academic_semester_archive_barriers (
    semester_id BIGINT PRIMARY KEY,
    operation_id UUID NOT NULL,
    state_version BIGINT NOT NULL,
    participant_state VARCHAR(20) NOT NULL,
    blocking_reason TEXT,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT academic_semester_archive_barrier_state_valid
        CHECK (participant_state IN ('PENDING', 'READY', 'PREPARED_RESTORE', 'RELEASED')),
    CONSTRAINT academic_semester_archive_barrier_version_nonnegative
        CHECK (state_version >= 0)
);

-- These exact request identities are captured only from committed PENDING
-- publications/transfer markers while PREPARE_ARCHIVE acquires the same
-- semester lock used by every fenced business write.
CREATE TABLE academic_semester_archive_publication_admissions (
    operation_id UUID NOT NULL,
    state_version BIGINT NOT NULL,
    semester_id BIGINT NOT NULL,
    binding_id BIGINT NOT NULL,
    actor_id BIGINT NOT NULL,
    request_key UUID NOT NULL,
    payload_hash BYTEA,
    source_event_id UUID,
    admitted_homework_id BIGINT,
    consumed_at TIMESTAMPTZ,
    PRIMARY KEY (operation_id, state_version, binding_id),
    CONSTRAINT academic_semester_archive_admission_hash_valid
        CHECK (payload_hash IS NULL OR octet_length(payload_hash) = 32),
    CONSTRAINT academic_semester_archive_admission_ids_valid
        CHECK (semester_id > 0 AND binding_id > 0 AND actor_id > 0 AND state_version >= 0)
);

CREATE INDEX academic_semester_archive_admission_lookup
    ON academic_semester_archive_publication_admissions
       (semester_id, binding_id, request_key, state_version);

-- Transfer source identity and archive-terminal identity are separate. A
-- cancellation may follow an already-applied transfer batch and must retain
-- both proofs without allowing a later publication to consume the tombstone.
ALTER TABLE academic_semester_archive_publication_admissions
    ADD COLUMN resolution_state VARCHAR(24) NOT NULL DEFAULT 'ADMITTED',
    ADD COLUMN terminal_event_id UUID,
    ADD COLUMN schedule_occurrence_id BIGINT,
    ADD COLUMN schedule_revision BIGINT,
    ADD CONSTRAINT academic_semester_archive_admission_resolution_valid
        CHECK (resolution_state IN ('ADMITTED', 'CANCEL_REQUESTED', 'CANCELLED_UNPUBLISHED')),
    ADD CONSTRAINT academic_semester_archive_admission_resolution_consistent
        CHECK ((resolution_state = 'ADMITTED' AND terminal_event_id IS NULL)
            OR (resolution_state = 'CANCEL_REQUESTED'
                AND payload_hash IS NOT NULL
                AND admitted_homework_id IS NULL AND consumed_at IS NULL
                AND schedule_occurrence_id IS NOT NULL AND schedule_revision IS NOT NULL)
            OR (resolution_state = 'CANCELLED_UNPUBLISHED'
                AND admitted_homework_id IS NULL AND consumed_at IS NOT NULL
                AND payload_hash IS NOT NULL
                AND terminal_event_id IS NOT NULL
                AND schedule_occurrence_id IS NOT NULL AND schedule_revision IS NOT NULL)),
    ADD CONSTRAINT academic_semester_archive_admission_schedule_identity
        CHECK ((schedule_occurrence_id IS NULL AND schedule_revision IS NULL)
            OR (schedule_occurrence_id > 0 AND schedule_revision > 0));

CREATE UNIQUE INDEX academic_semester_archive_terminal_event_uq
    ON academic_semester_archive_publication_admissions (terminal_event_id)
    WHERE terminal_event_id IS NOT NULL;

CREATE OR REPLACE FUNCTION protect_academic_semester_archive_admission()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.operation_id IS DISTINCT FROM OLD.operation_id
       OR NEW.state_version IS DISTINCT FROM OLD.state_version
       OR NEW.semester_id IS DISTINCT FROM OLD.semester_id
       OR NEW.binding_id IS DISTINCT FROM OLD.binding_id
       OR NEW.actor_id IS DISTINCT FROM OLD.actor_id
       OR NEW.request_key IS DISTINCT FROM OLD.request_key
       OR (OLD.payload_hash IS NOT NULL AND NEW.payload_hash IS DISTINCT FROM OLD.payload_hash)
       OR (OLD.source_event_id IS NOT NULL AND NEW.source_event_id IS DISTINCT FROM OLD.source_event_id)
       OR (OLD.admitted_homework_id IS NOT NULL
           AND NEW.admitted_homework_id IS DISTINCT FROM OLD.admitted_homework_id)
       OR (OLD.schedule_occurrence_id IS NOT NULL
           AND NEW.schedule_occurrence_id IS DISTINCT FROM OLD.schedule_occurrence_id)
       OR (OLD.schedule_revision IS NOT NULL
           AND NEW.schedule_revision IS DISTINCT FROM OLD.schedule_revision)
       OR (OLD.terminal_event_id IS NOT NULL
           AND NEW.terminal_event_id IS DISTINCT FROM OLD.terminal_event_id)
       OR (OLD.consumed_at IS NOT NULL AND NEW.consumed_at IS DISTINCT FROM OLD.consumed_at)
       OR NOT (
           NEW.resolution_state = OLD.resolution_state
           OR (OLD.resolution_state = 'ADMITTED' AND NEW.resolution_state = 'CANCEL_REQUESTED')
           OR (OLD.resolution_state = 'CANCEL_REQUESTED' AND NEW.resolution_state = 'CANCELLED_UNPUBLISHED')) THEN
        RAISE EXCEPTION 'Academic archive publication admission is immutable or terminal';
    END IF;
    RETURN NEW;
END
$$;

CREATE TRIGGER academic_semester_archive_admission_identity
    BEFORE UPDATE ON academic_semester_archive_publication_admissions
    FOR EACH ROW EXECUTE FUNCTION protect_academic_semester_archive_admission();

-- Pin a null-content transfer marker/history row to the exact immutable batch
-- receipt. Legacy rows remain nullable and therefore cannot be treated as
-- proven-complete by archive reconciliation.
ALTER TABLE lesson_transfer_receipts
    ADD COLUMN source_event_id UUID;
CREATE UNIQUE INDEX lesson_transfer_receipts_source_event_uq
    ON lesson_transfer_receipts (source_event_id)
    WHERE source_event_id IS NOT NULL;

ALTER TABLE homework_binding_transfer_markers
    ADD COLUMN source_event_id UUID,
    ADD COLUMN batch_index INTEGER,
    DROP CONSTRAINT homework_transfer_marker_state_chk,
    DROP CONSTRAINT homework_transfer_marker_homework_chk,
    ADD CONSTRAINT homework_transfer_marker_source_batch_consistent
        CHECK ((source_event_id IS NULL AND batch_index IS NULL)
            OR (source_event_id IS NOT NULL AND batch_index IS NOT NULL AND batch_index >= 0)),
    ADD CONSTRAINT homework_transfer_marker_state_chk
        CHECK (state IN ('PENDING', 'APPLIED', 'CANCELLED_UNPUBLISHED')),
    ADD CONSTRAINT homework_transfer_marker_homework_chk
        CHECK ((state = 'PENDING' AND homework_id IS NULL)
            OR (state = 'APPLIED' AND homework_id IS NOT NULL)
            OR (state = 'CANCELLED_UNPUBLISHED' AND homework_id IS NULL));

ALTER TABLE homework_binding_transfer_history
    ADD COLUMN source_event_id UUID,
    ADD COLUMN batch_index INTEGER,
    ADD CONSTRAINT homework_transfer_history_source_batch_consistent
        CHECK ((source_event_id IS NULL AND batch_index IS NULL)
            OR (source_event_id IS NOT NULL AND batch_index IS NOT NULL AND batch_index >= 0));

CREATE TABLE academic_semester_archive_effect_receipts (
    source_event_id UUID PRIMARY KEY,
    event_type VARCHAR(128) NOT NULL,
    semester_id BIGINT NOT NULL,
    binding_id BIGINT NOT NULL,
    payload_hash BYTEA NOT NULL,
    state VARCHAR(10) NOT NULL DEFAULT 'PENDING',
    acknowledgement_event_id UUID NOT NULL,
    received_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    applied_at TIMESTAMPTZ,
    CONSTRAINT academic_semester_archive_effect_event_valid
        CHECK (event_type = 'homework.binding.archived'),
    CONSTRAINT academic_semester_archive_effect_hash_valid
        CHECK (semester_id > 0 AND binding_id > 0 AND octet_length(payload_hash) = 32),
    CONSTRAINT academic_semester_archive_effect_state_valid
        CHECK ((state = 'PENDING' AND applied_at IS NULL)
            OR (state = 'APPLIED' AND applied_at IS NOT NULL))
);

ALTER TABLE homework_binding_archives
    ADD COLUMN semester_id BIGINT,
    ADD COLUMN source_event_id UUID,
    ADD CONSTRAINT homework_binding_archives_semester_positive_chk
        CHECK (semester_id IS NULL OR semester_id > 0);
UPDATE homework_binding_archives marker
   SET semester_id = homework.semester_id
  FROM homeworks homework
 WHERE marker.homework_id = homework.id AND marker.semester_id IS NULL;

CREATE OR REPLACE FUNCTION protect_homework_binding_archive_identity()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    IF NEW.binding_id IS DISTINCT FROM OLD.binding_id
       OR NEW.actor_id IS DISTINCT FROM OLD.actor_id
       OR NEW.request_key IS DISTINCT FROM OLD.request_key
       OR NEW.archived_at IS DISTINCT FROM OLD.archived_at THEN
        RAISE EXCEPTION 'homework binding archive identity is immutable';
    END IF;
    IF OLD.homework_id IS NOT NULL
       AND NEW.homework_id IS DISTINCT FROM OLD.homework_id THEN
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
END;
$$;

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
                    WHERE participant_state IN ('PENDING', 'READY', 'PREPARED_RESTORE')) THEN
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

        IF barrier_state IN ('PENDING', 'READY', 'PREPARED_RESTORE') OR authority_blocked THEN
            IF TG_TABLE_NAME = 'homeworks' AND barrier_state = 'PENDING'
                    AND authority_transition = 'ARCHIVING'
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

            IF TG_TABLE_NAME = 'homeworks' AND barrier_state = 'PENDING'
                    AND authority_transition = 'ARCHIVING'
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
                       AND barrier_state = 'PENDING'
                       AND authority_transition = 'ARCHIVING') INTO event_admitted;
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

            IF TG_TABLE_NAME = 'homework_binding_transfer_markers'
                    AND TG_OP = 'UPDATE'
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
                    AND barrier_state = 'PENDING'
                    AND authority_transition = 'ARCHIVING'
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

            IF barrier_state = 'PENDING' AND authority_transition = 'ARCHIVING'
                    AND transfer_event_id IS NOT NULL
                    AND transfer_operation_id IS NOT NULL
                    AND transfer_binding_id IS NOT NULL
                    AND transfer_semester_id = target_semester_id
                    AND transfer_binding_id = (coalesce(new_row, old_row) ->> 'binding_id')::BIGINT
                    AND transfer_operation_hash ~ '^[0-9a-f]{64}$'
                    AND transfer_batch_hash ~ '^[0-9a-f]{64}$'
                    AND transfer_binding_hash ~ '^[0-9a-f]{64}$' THEN
                IF TG_TABLE_NAME = 'homeworks' AND TG_OP = 'UPDATE'
                        AND old_semester_id = target_semester_id
                        AND new_semester_id = target_semester_id
                        AND NEW.id = OLD.id AND NEW.binding_id = OLD.binding_id
                        AND NEW.actor_id = OLD.actor_id AND NEW.request_key = OLD.request_key
                        AND NEW.payload_hash = OLD.payload_hash
                        AND NEW.publication_state = OLD.publication_state
                        AND OLD.binding_id = transfer_binding_id THEN
                    RETURN NEW;
                END IF;
                IF TG_TABLE_NAME = 'homework_binding_transfer_markers'
                        AND new_semester_id = target_semester_id
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

            RAISE EXCEPTION 'Academic semester % is fenced by archive barrier', target_semester_id
                USING ERRCODE = '55000';
        END IF;
    END LOOP;

    IF TG_OP = 'DELETE' THEN RETURN OLD; END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER homeworks_semester_archive_fence
    BEFORE INSERT OR UPDATE OR DELETE ON homeworks
    FOR EACH ROW EXECUTE FUNCTION guard_academic_semester_archive_business_write();
CREATE TRIGGER homework_completions_semester_archive_fence
    BEFORE INSERT OR UPDATE OR DELETE ON homework_completions
    FOR EACH ROW EXECUTE FUNCTION guard_academic_semester_archive_business_write();
CREATE TRIGGER homework_binding_archives_semester_archive_fence
    BEFORE INSERT OR UPDATE OR DELETE ON homework_binding_archives
    FOR EACH ROW EXECUTE FUNCTION guard_academic_semester_archive_business_write();
CREATE TRIGGER homework_binding_transfer_markers_semester_archive_fence
    BEFORE INSERT OR UPDATE OR DELETE ON homework_binding_transfer_markers
    FOR EACH ROW EXECUTE FUNCTION guard_academic_semester_archive_business_write();

CREATE OR REPLACE FUNCTION retain_unproven_archive_effect_outbox_rows()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    -- Academic has no remote-receipt table for Schedule. Local application is
    -- not evidence that the consumer received the ACK, so retain it for
    -- at-least-once relay/recovery instead of inferring delivery from state.
    IF OLD.event_type = 'semester.archive.effect.ack' THEN RETURN NULL; END IF;
    RETURN OLD;
END;
$$;

CREATE TRIGGER academic_archive_effect_ack_retention
    BEFORE DELETE ON academic_outbox
    FOR EACH ROW EXECUTE FUNCTION retain_unproven_archive_effect_outbox_rows();
