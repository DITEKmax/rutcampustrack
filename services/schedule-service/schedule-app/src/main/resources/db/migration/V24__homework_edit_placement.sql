-- Publication identity stays immutable; only its guarded placement may change.
ALTER TABLE lesson_homework_bindings
    ALTER COLUMN occurrence_id DROP NOT NULL,
    ALTER COLUMN current_lesson_id DROP NOT NULL,
    ADD COLUMN binding_mode VARCHAR(8) NOT NULL DEFAULT 'LESSON',
    ADD COLUMN group_id BIGINT,
    ADD COLUMN subject_id BIGINT,
    ADD COLUMN semester_id BIGINT,
    ADD COLUMN placement_date DATE,
    ADD COLUMN placement_lesson_number SMALLINT,
    ADD COLUMN original_occurrence_id BIGINT,
    ADD COLUMN original_binding_mode VARCHAR(8),
    ADD COLUMN original_date DATE,
    ADD COLUMN original_lesson_number SMALLINT,
    ADD COLUMN pending_edit_operation_id UUID;

UPDATE lesson_homework_bindings binding SET
    group_id = occurrence.group_id, subject_id = occurrence.subject_id,
    semester_id = occurrence.semester_id, placement_date = lesson.date,
    placement_lesson_number = lesson.lesson_number,
    original_occurrence_id = binding.occurrence_id, original_binding_mode = 'LESSON',
    original_date = lesson.date, original_lesson_number = lesson.lesson_number
FROM lesson_occurrences occurrence, lessons lesson
WHERE occurrence.id = binding.occurrence_id AND lesson.id = binding.current_lesson_id;

ALTER TABLE lesson_homework_bindings
    ALTER COLUMN group_id SET NOT NULL,
    ALTER COLUMN subject_id SET NOT NULL,
    ALTER COLUMN semester_id SET NOT NULL,
    ALTER COLUMN placement_date SET NOT NULL,
    ALTER COLUMN original_binding_mode SET NOT NULL,
    ALTER COLUMN original_date SET NOT NULL,
    ADD CONSTRAINT homework_binding_scope_chk CHECK (group_id > 0 AND subject_id > 0 AND semester_id > 0),
    ADD CONSTRAINT homework_binding_placement_chk CHECK (
        (binding_mode = 'LESSON' AND occurrence_id IS NOT NULL AND current_lesson_id IS NOT NULL
            AND placement_lesson_number BETWEEN 1 AND 8)
        OR (binding_mode = 'DATE' AND occurrence_id IS NULL AND current_lesson_id IS NULL
            AND placement_lesson_number IS NULL)),
    ADD CONSTRAINT homework_binding_original_chk CHECK (
        (original_binding_mode = 'LESSON' AND original_occurrence_id IS NOT NULL
            AND original_lesson_number BETWEEN 1 AND 8)
        OR (original_binding_mode = 'DATE' AND original_occurrence_id IS NULL
            AND original_lesson_number IS NULL));

CREATE TABLE homework_placement_operations (
    operation_id UUID PRIMARY KEY,
    binding_id BIGINT NOT NULL REFERENCES lesson_homework_bindings(binding_id) ON DELETE RESTRICT,
    homework_id BIGINT NOT NULL,
    actor_id BIGINT NOT NULL,
    request_key UUID NOT NULL,
    command_hash BYTEA NOT NULL CHECK (octet_length(command_hash) = 32),
    group_id BIGINT NOT NULL, subject_id BIGINT NOT NULL, semester_id BIGINT NOT NULL,
    state VARCHAR(32) NOT NULL CHECK (state IN ('APPLIED_AWAITING_ACK', 'ACKNOWLEDGED', 'NOT_ACCEPTED')),
    accepted_binding JSONB,
    accepted_revision BIGINT,
    target_mode VARCHAR(8), target_occurrence_id BIGINT, target_lesson_id BIGINT,
    target_date DATE, target_lesson_number SMALLINT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    acknowledged_at TIMESTAMPTZ,
    UNIQUE (binding_id, actor_id, request_key),
    CHECK ((state = 'NOT_ACCEPTED' AND accepted_binding IS NULL AND accepted_revision IS NULL)
        OR (state <> 'NOT_ACCEPTED' AND accepted_binding IS NOT NULL AND accepted_revision > 0))
);

CREATE OR REPLACE FUNCTION protect_homework_placement_operation()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN RAISE EXCEPTION 'homework placement receipts are retained'; END IF;
    IF OLD.state <> 'APPLIED_AWAITING_ACK' OR NEW.state <> 'ACKNOWLEDGED'
       OR (to_jsonb(NEW) - 'state' - 'acknowledged_at') IS DISTINCT FROM
          (to_jsonb(OLD) - 'state' - 'acknowledged_at')
       OR NEW.acknowledged_at IS NULL THEN
        RAISE EXCEPTION 'homework placement receipt is immutable except exact acknowledgement';
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER homework_placement_operation_guard BEFORE UPDATE OR DELETE
    ON homework_placement_operations FOR EACH ROW EXECUTE FUNCTION protect_homework_placement_operation();

CREATE TABLE homework_placement_archive_admissions (
    archive_operation_id UUID NOT NULL,
    state_version BIGINT NOT NULL,
    semester_id BIGINT NOT NULL,
    edit_operation_id UUID NOT NULL REFERENCES homework_placement_operations(operation_id),
    command_hash BYTEA NOT NULL,
    PRIMARY KEY (archive_operation_id, edit_operation_id)
);
CREATE OR REPLACE FUNCTION schedule_homework_edit_ack_admitted(old_row JSONB, new_row JSONB, target_semester BIGINT)
RETURNS BOOLEAN LANGUAGE SQL STABLE AS $$
    SELECT old_row ->> 'pending_edit_operation_id' IS NOT NULL
       AND new_row ->> 'pending_edit_operation_id' IS NULL
       AND (old_row - 'pending_edit_operation_id') = (new_row - 'pending_edit_operation_id')
       AND EXISTS (SELECT 1 FROM homework_placement_operations edit
            JOIN homework_placement_archive_admissions admitted ON admitted.edit_operation_id = edit.operation_id
            JOIN schedule_semester_archive_barriers barrier
              ON barrier.operation_id = admitted.archive_operation_id AND barrier.state_version = admitted.state_version
             AND barrier.semester_id = admitted.semester_id
            WHERE edit.operation_id = (old_row ->> 'pending_edit_operation_id')::UUID
              AND edit.binding_id = (old_row ->> 'binding_id')::BIGINT
              AND edit.command_hash = admitted.command_hash AND edit.state = 'ACKNOWLEDGED'
              AND admitted.semester_id = target_semester
              AND barrier.participant_state IN ('PENDING', 'DELETE_PREPARING'))
$$;

-- Fill scope for unchanged legacy writers and synchronize the LESSON projection.
CREATE OR REPLACE FUNCTION initialize_homework_binding_placement()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
DECLARE lesson_row lessons%ROWTYPE;
BEGIN
    IF NEW.binding_mode = 'LESSON' THEN
        SELECT * INTO lesson_row FROM lessons WHERE id = NEW.current_lesson_id AND occurrence_id = NEW.occurrence_id;
        IF lesson_row.id IS NULL THEN RAISE EXCEPTION 'homework placement requires its current physical lesson'; END IF;
        IF NEW.group_id IS NOT NULL AND (NEW.group_id <> lesson_row.group_id
            OR NEW.subject_id <> lesson_row.subject_id OR NEW.semester_id <> lesson_row.semester_id) THEN
            RAISE EXCEPTION 'homework placement cannot change scope';
        END IF;
        NEW.group_id := lesson_row.group_id; NEW.subject_id := lesson_row.subject_id;
        NEW.semester_id := lesson_row.semester_id; NEW.placement_date := lesson_row.date;
        NEW.placement_lesson_number := lesson_row.lesson_number;
    END IF;
    IF TG_OP = 'INSERT' THEN
        NEW.original_occurrence_id := NEW.occurrence_id;
        NEW.original_binding_mode := NEW.binding_mode;
        NEW.original_date := NEW.placement_date;
        NEW.original_lesson_number := NEW.placement_lesson_number;
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER a_homework_binding_placement BEFORE INSERT OR UPDATE ON lesson_homework_bindings
    FOR EACH ROW EXECUTE FUNCTION initialize_homework_binding_placement();

CREATE OR REPLACE FUNCTION protect_lesson_homework_binding_identity()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
DECLARE receipt homework_placement_operations%ROWTYPE;
        edit_id UUID;
BEGIN
    IF TG_OP = 'DELETE' THEN RAISE EXCEPTION 'homework binding history is retained'; END IF;
    IF NEW.binding_id IS DISTINCT FROM OLD.binding_id
       OR NEW.actor_id IS DISTINCT FROM OLD.actor_id OR NEW.request_key IS DISTINCT FROM OLD.request_key
       OR NEW.payload_hash IS DISTINCT FROM OLD.payload_hash
       OR NEW.group_id IS DISTINCT FROM OLD.group_id OR NEW.subject_id IS DISTINCT FROM OLD.subject_id
       OR NEW.semester_id IS DISTINCT FROM OLD.semester_id
       OR NEW.original_occurrence_id IS DISTINCT FROM OLD.original_occurrence_id
       OR NEW.original_binding_mode IS DISTINCT FROM OLD.original_binding_mode
       OR NEW.original_date IS DISTINCT FROM OLD.original_date
       OR NEW.original_lesson_number IS DISTINCT FROM OLD.original_lesson_number THEN
        RAISE EXCEPTION 'homework publication and create identity are immutable';
    END IF;
    IF OLD.homework_id IS NOT NULL AND NEW.homework_id IS DISTINCT FROM OLD.homework_id
       AND NOT (NEW.homework_id IS NULL AND NEW.state = 'ARCHIVED'
                AND schedule_semester_delete_authorized(OLD.semester_id)) THEN
        RAISE EXCEPTION 'homework binding homework_id cannot be replaced';
    END IF;
    IF OLD.state = 'ARCHIVED' AND (NEW.state <> 'ARCHIVED'
       OR NEW.binding_mode IS DISTINCT FROM OLD.binding_mode
       OR NEW.occurrence_id IS DISTINCT FROM OLD.occurrence_id
       OR NEW.current_lesson_id IS DISTINCT FROM OLD.current_lesson_id
       OR NEW.placement_date IS DISTINCT FROM OLD.placement_date
       OR NEW.placement_lesson_number IS DISTINCT FROM OLD.placement_lesson_number) THEN
        RAISE EXCEPTION 'archived homework placement cannot be changed';
    END IF;
    IF NEW.revision < OLD.revision THEN RAISE EXCEPTION 'binding revision cannot move backwards'; END IF;
    IF NEW.occurrence_id IS DISTINCT FROM OLD.occurrence_id
       OR NEW.binding_mode IS DISTINCT FROM OLD.binding_mode
       OR (NEW.binding_mode = 'DATE' AND NEW.placement_date IS DISTINCT FROM OLD.placement_date) THEN
        edit_id := nullif(current_setting('rutcampustrack.homework_edit_operation_id', TRUE), '')::UUID;
        SELECT * INTO receipt FROM homework_placement_operations WHERE operation_id = edit_id;
        IF receipt.operation_id IS NULL OR receipt.binding_id <> OLD.binding_id
           OR receipt.homework_id <> OLD.homework_id OR receipt.state <> 'APPLIED_AWAITING_ACK'
           OR receipt.accepted_revision <> NEW.revision
           OR receipt.target_mode IS DISTINCT FROM NEW.binding_mode
           OR receipt.target_occurrence_id IS DISTINCT FROM NEW.occurrence_id
           OR receipt.target_lesson_id IS DISTINCT FROM NEW.current_lesson_id
           OR receipt.target_date IS DISTINCT FROM NEW.placement_date
           OR receipt.target_lesson_number IS DISTINCT FROM NEW.placement_lesson_number
           OR NEW.pending_edit_operation_id IS DISTINCT FROM receipt.operation_id THEN
            RAISE EXCEPTION 'placement mutation requires the exact durable edit receipt';
        END IF;
    END IF;
    IF OLD.pending_edit_operation_id IS NOT NULL AND NEW.pending_edit_operation_id IS NULL THEN
        SELECT * INTO receipt FROM homework_placement_operations WHERE operation_id = OLD.pending_edit_operation_id;
        IF receipt.state <> 'ACKNOWLEDGED' THEN RAISE EXCEPTION 'placement gate requires exact acknowledgement'; END IF;
    END IF;
    RETURN NEW;
END $$;

-- Existing semester guards must resolve DATE from the authoritative binding scope.
DO $$ DECLARE fn TEXT; updated TEXT;
BEGIN
    SELECT pg_get_functiondef('guard_schedule_semester_deletion_write()'::regprocedure) INTO fn;
    updated := replace(fn, 'IF TG_TABLE_NAME IN (''lesson_homework_bindings'', ''lesson_lifecycle_entries'') THEN',
        'IF TG_TABLE_NAME = ''lesson_homework_bindings'' THEN
            old_semester_id := nullif(old_row ->> ''semester_id'', '''')::BIGINT;
            new_semester_id := nullif(new_row ->> ''semester_id'', '''')::BIGINT;
        ELSIF TG_TABLE_NAME = ''lesson_lifecycle_entries'' THEN');
    IF updated = fn THEN RAISE EXCEPTION 'V24 schedule deletion guard anchor changed'; END IF;
    updated := replace(updated, 'OLD.occurrence_id = NEW.occurrence_id', 'OLD.occurrence_id IS NOT DISTINCT FROM NEW.occurrence_id');
    updated := replace(updated, 'OLD.current_lesson_id = NEW.current_lesson_id', 'OLD.current_lesson_id IS NOT DISTINCT FROM NEW.current_lesson_id');
    updated := replace(updated, 'IF barrier_state IS NULL OR NOT deleting THEN CONTINUE; END IF;',
        'IF barrier_state IS NULL OR NOT deleting THEN CONTINUE; END IF;
         IF TG_TABLE_NAME = ''lesson_homework_bindings'' AND TG_OP = ''UPDATE''
            AND schedule_homework_edit_ack_admitted(old_row, new_row, affected_semester_id) THEN CONTINUE; END IF;');
    EXECUTE updated;
    SELECT pg_get_functiondef('guard_semester_archive_business_write()'::regprocedure) INTO fn;
    updated := replace(fn,
        'SELECT semester_id INTO old_semester_id FROM lesson_occurrences
             WHERE id = (old_row ->> ''occurrence_id'')::BIGINT;',
        'old_semester_id := nullif(old_row ->> ''semester_id'', '''')::BIGINT;');
    updated := replace(updated,
        'SELECT semester_id INTO new_semester_id FROM lesson_occurrences
             WHERE id = (new_row ->> ''occurrence_id'')::BIGINT;',
        'new_semester_id := nullif(new_row ->> ''semester_id'', '''')::BIGINT;');
    IF updated = fn THEN RAISE EXCEPTION 'V24 schedule archive guard anchor changed'; END IF;
    updated := replace(updated, 'old_row ->> ''occurrence_id'' = new_row ->> ''occurrence_id''',
        'old_row ->> ''occurrence_id'' IS NOT DISTINCT FROM new_row ->> ''occurrence_id''');
    updated := replace(updated, 'old_row ->> ''current_lesson_id'' = new_row ->> ''current_lesson_id''',
        'old_row ->> ''current_lesson_id'' IS NOT DISTINCT FROM new_row ->> ''current_lesson_id''');
    updated := replace(updated, 'IF barrier_state IN (''PENDING'', ''READY'', ''PREPARED_RESTORE'') THEN',
        'IF TG_TABLE_NAME = ''lesson_homework_bindings'' AND TG_OP = ''UPDATE''
            AND schedule_homework_edit_ack_admitted(old_row, new_row, target_semester_id) THEN CONTINUE; END IF;
         IF barrier_state IN (''PENDING'', ''READY'', ''PREPARED_RESTORE'') THEN');
    EXECUTE updated;
    SELECT pg_get_functiondef('protect_lesson_homework_binding_transfer()'::regprocedure) INTO fn;
    updated := replace(fn, 'IF NEW.current_lesson_id IS NOT DISTINCT FROM OLD.current_lesson_id THEN RETURN NEW; END IF;',
        'IF NEW.current_lesson_id IS NOT DISTINCT FROM OLD.current_lesson_id THEN RETURN NEW; END IF;
         IF EXISTS (SELECT 1 FROM homework_placement_operations edit
             WHERE edit.operation_id = nullif(current_setting(''rutcampustrack.homework_edit_operation_id'', TRUE), '''')::UUID
               AND edit.binding_id = OLD.binding_id AND edit.homework_id = OLD.homework_id
               AND edit.state = ''APPLIED_AWAITING_ACK'' AND edit.accepted_revision = NEW.revision
               AND edit.target_lesson_id IS NOT DISTINCT FROM NEW.current_lesson_id
               AND edit.target_occurrence_id IS NOT DISTINCT FROM NEW.occurrence_id
               AND edit.target_mode = NEW.binding_mode AND NEW.pending_edit_operation_id = edit.operation_id)
         THEN RETURN NEW; END IF;');
    IF updated = fn THEN RAISE EXCEPTION 'V24 schedule transfer guard anchor changed'; END IF;
    EXECUTE updated;
END $$;
