CREATE TABLE schedule_semester_archive_barriers (
    semester_id BIGINT PRIMARY KEY,
    operation_id UUID NOT NULL,
    state_version BIGINT NOT NULL,
    participant_state VARCHAR(20) NOT NULL,
    blocking_reason TEXT,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT schedule_semester_archive_barrier_state_valid
        CHECK (participant_state IN ('PENDING', 'READY', 'PREPARED_RESTORE', 'RELEASED')),
    CONSTRAINT schedule_semester_archive_barrier_version_nonnegative
        CHECK (state_version >= 0)
);

CREATE TABLE schedule_semester_archive_effect_ledger (
    event_id UUID NOT NULL,
    target VARCHAR(16) NOT NULL,
    event_type VARCHAR(128) NOT NULL,
    semester_id BIGINT,
    scope_id BIGINT NOT NULL,
    payload_hash BYTEA NOT NULL,
    event_payload JSONB NOT NULL,
    state VARCHAR(10) NOT NULL DEFAULT 'PENDING',
    receipt_event_id UUID,
    blocking_reason TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    applied_at TIMESTAMPTZ,
    PRIMARY KEY (event_id, target, event_type, scope_id),
    CONSTRAINT schedule_semester_archive_effect_target_valid
        CHECK (target IN ('ATTENDANCE', 'ACADEMIC')),
    CONSTRAINT schedule_semester_archive_effect_scope_valid
        CHECK ((semester_id IS NULL AND scope_id = 0) OR (semester_id > 0 AND scope_id = semester_id)),
    CONSTRAINT schedule_semester_archive_effect_hash_valid
        CHECK (octet_length(payload_hash) = 32),
    CONSTRAINT schedule_semester_archive_effect_state_valid
        CHECK (state IN ('PENDING', 'APPLIED', 'ERROR')),
    CONSTRAINT schedule_semester_archive_effect_receipt_consistent
        CHECK ((state = 'APPLIED' AND receipt_event_id IS NOT NULL AND applied_at IS NOT NULL)
            OR (state <> 'APPLIED' AND receipt_event_id IS NULL AND applied_at IS NULL))
);

CREATE INDEX schedule_semester_archive_effects_pending
    ON schedule_semester_archive_effect_ledger (semester_id, created_at)
    WHERE state <> 'APPLIED';

CREATE OR REPLACE FUNCTION guard_semester_archive_business_write()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
DECLARE
    old_row JSONB;
    new_row JSONB;
    old_semester_id BIGINT;
    new_semester_id BIGINT;
    target_semester_id BIGINT;
    barrier_state VARCHAR(20);
    authority_version BIGINT;
    confirmed_pending_binding BOOLEAN := FALSE;
    cancelled_unpublished_binding BOOLEAN := FALSE;
    cancel_operation_id UUID;
    cancel_state_version BIGINT;
    cancel_binding_id BIGINT;
    cancel_occurrence_id BIGINT;
    cancel_actor_id BIGINT;
    cancel_request_key UUID;
    cancel_payload_hash TEXT;
    cancel_revision BIGINT;
BEGIN
    IF TG_OP <> 'INSERT' THEN old_row := to_jsonb(OLD); END IF;
    IF TG_OP <> 'DELETE' THEN new_row := to_jsonb(NEW); END IF;

    IF TG_TABLE_NAME = 'lesson_homework_bindings' THEN
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

    cancel_operation_id := nullif(current_setting('rutcampustrack.archive_cancel_operation_id', TRUE), '')::UUID;
    cancel_state_version := nullif(current_setting('rutcampustrack.archive_cancel_state_version', TRUE), '')::BIGINT;
    cancel_binding_id := nullif(current_setting('rutcampustrack.archive_cancel_binding_id', TRUE), '')::BIGINT;
    cancel_occurrence_id := nullif(current_setting('rutcampustrack.archive_cancel_occurrence_id', TRUE), '')::BIGINT;
    cancel_actor_id := nullif(current_setting('rutcampustrack.archive_cancel_actor_id', TRUE), '')::BIGINT;
    cancel_request_key := nullif(current_setting('rutcampustrack.archive_cancel_request_key', TRUE), '')::UUID;
    cancel_payload_hash := nullif(current_setting('rutcampustrack.archive_cancel_payload_hash', TRUE), '');
    cancel_revision := nullif(current_setting('rutcampustrack.archive_cancel_revision', TRUE), '')::BIGINT;

    IF (old_semester_id IS NULL AND new_semester_id IS NULL) THEN
        RAISE EXCEPTION 'semester archive write fence cannot resolve the affected semester';
    END IF;

    FOR target_semester_id IN
        SELECT DISTINCT candidate
          FROM unnest(ARRAY[old_semester_id, new_semester_id]) AS candidate
         WHERE candidate IS NOT NULL
         ORDER BY candidate
    LOOP
        PERFORM pg_advisory_xact_lock(5452097, (target_semester_id % 2147483647)::INTEGER);
        SELECT participant_state, state_version INTO barrier_state, authority_version
          FROM schedule_semester_archive_barriers
         WHERE semester_id = target_semester_id;

        IF barrier_state IN ('PENDING', 'READY', 'PREPARED_RESTORE') THEN
            confirmed_pending_binding := TG_TABLE_NAME = 'lesson_homework_bindings'
                AND TG_OP = 'UPDATE'
                AND barrier_state = 'PENDING'
                AND old_semester_id = target_semester_id
                AND new_semester_id = target_semester_id
                AND old_row ->> 'state' = 'PENDING'
                AND new_row ->> 'state' = 'ACTIVE'
                AND old_row ->> 'binding_id' = new_row ->> 'binding_id'
                AND old_row ->> 'occurrence_id' = new_row ->> 'occurrence_id'
                AND old_row ->> 'current_lesson_id' = new_row ->> 'current_lesson_id'
                AND old_row ->> 'actor_id' = new_row ->> 'actor_id'
                AND old_row ->> 'request_key' = new_row ->> 'request_key'
                AND old_row ->> 'payload_hash' = new_row ->> 'payload_hash'
                AND old_row ->> 'homework_id' IS NULL
                AND new_row ->> 'homework_id' IS NOT NULL
                AND (new_row ->> 'revision')::BIGINT = (old_row ->> 'revision')::BIGINT + 1;
            cancelled_unpublished_binding := TG_TABLE_NAME = 'lesson_homework_bindings'
                AND TG_OP = 'UPDATE'
                AND barrier_state = 'PENDING'
                AND authority_version = cancel_state_version
                AND cancel_operation_id = (SELECT operation_id FROM schedule_semester_archive_barriers
                                            WHERE semester_id = target_semester_id)
                AND cancel_binding_id = (old_row ->> 'binding_id')::BIGINT
                AND cancel_binding_id = (new_row ->> 'binding_id')::BIGINT
                AND cancel_occurrence_id = (old_row ->> 'occurrence_id')::BIGINT
                AND cancel_occurrence_id = (new_row ->> 'occurrence_id')::BIGINT
                AND cancel_actor_id = (old_row ->> 'actor_id')::BIGINT
                AND cancel_actor_id = (new_row ->> 'actor_id')::BIGINT
                AND cancel_request_key = (old_row ->> 'request_key')::UUID
                AND cancel_request_key = (new_row ->> 'request_key')::UUID
                AND cancel_payload_hash = encode(OLD.payload_hash, 'hex')
                AND OLD.payload_hash = NEW.payload_hash
                AND OLD.state = 'PENDING' AND NEW.state = 'ARCHIVED'
                AND OLD.homework_id IS NULL AND NEW.homework_id IS NULL
                AND OLD.revision = cancel_revision AND NEW.revision = cancel_revision + 1
                AND old_semester_id = target_semester_id AND new_semester_id = target_semester_id;
            IF NOT confirmed_pending_binding AND NOT cancelled_unpublished_binding THEN
                RAISE EXCEPTION 'semester % is fenced by archive barrier', target_semester_id
                    USING ERRCODE = '55000';
            END IF;
        END IF;
    END LOOP;

    IF TG_OP = 'DELETE' THEN RETURN OLD; END IF;
    RETURN NEW;
END
$$;

CREATE TRIGGER schedule_items_semester_archive_fence
    BEFORE INSERT OR UPDATE OR DELETE ON schedule_items
    FOR EACH ROW EXECUTE FUNCTION guard_semester_archive_business_write();
CREATE TRIGGER lessons_semester_archive_fence
    BEFORE INSERT OR UPDATE OR DELETE ON lessons
    FOR EACH ROW EXECUTE FUNCTION guard_semester_archive_business_write();
CREATE TRIGGER lesson_occurrences_semester_archive_fence
    BEFORE INSERT OR UPDATE OR DELETE ON lesson_occurrences
    FOR EACH ROW EXECUTE FUNCTION guard_semester_archive_business_write();
CREATE TRIGGER one_off_lessons_semester_archive_fence
    BEFORE INSERT OR UPDATE OR DELETE ON schedule_one_off_lessons
    FOR EACH ROW EXECUTE FUNCTION guard_semester_archive_business_write();
CREATE TRIGGER lesson_homework_bindings_semester_archive_fence
    BEFORE INSERT OR UPDATE OR DELETE ON lesson_homework_bindings
    FOR EACH ROW EXECUTE FUNCTION guard_semester_archive_business_write();

CREATE OR REPLACE FUNCTION retain_unproven_archive_effect_outbox_rows()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
DECLARE
    envelope_event_id UUID;
BEGIN
    IF OLD.event_type NOT IN ('lesson.closed', 'lesson.cancelled', 'lesson.deleted',
            'lesson.one_off.cancelled', 'homework.binding.archived') THEN
        RETURN OLD;
    END IF;
    BEGIN
        envelope_event_id := nullif(OLD.payload ->> 'event_id', '')::UUID;
    EXCEPTION WHEN OTHERS THEN
        RETURN NULL;
    END;
    IF envelope_event_id IS NULL OR NOT EXISTS (
            SELECT 1 FROM schedule_semester_archive_effect_ledger effect
             WHERE effect.event_id = envelope_event_id
               AND effect.event_type = OLD.event_type) OR EXISTS (
            SELECT 1 FROM schedule_semester_archive_effect_ledger effect
             WHERE effect.event_id = envelope_event_id
               AND effect.event_type = OLD.event_type
               AND (effect.event_payload IS DISTINCT FROM OLD.payload OR effect.state <> 'APPLIED')) THEN
        RETURN NULL;
    END IF;
    RETURN OLD;
END
$$;

CREATE TRIGGER schedule_outbox_archive_effect_retention
    BEFORE DELETE ON schedule_outbox
    FOR EACH ROW EXECUTE FUNCTION retain_unproven_archive_effect_outbox_rows();
