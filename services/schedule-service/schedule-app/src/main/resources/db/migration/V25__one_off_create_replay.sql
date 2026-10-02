-- Accepted one-off creation identity is retained across cancellation. The
-- existing authorized final-semester deletion may remove it through physical
-- lesson/occurrence/origin cascades, without changing participant delete order.
CREATE TABLE schedule_one_off_create_replay
(
    actor_id BIGINT NOT NULL CHECK (actor_id > 0),
    request_key UUID NOT NULL,
    payload_hash BYTEA NOT NULL CHECK (octet_length(payload_hash) = 32),
    semester_id BIGINT NOT NULL CHECK (semester_id > 0),
    one_off_lesson_id BIGINT NOT NULL REFERENCES schedule_one_off_lessons(id) ON DELETE CASCADE,
    occurrence_id BIGINT NOT NULL REFERENCES lesson_occurrences(id) ON DELETE CASCADE,
    physical_lesson_id BIGINT NOT NULL REFERENCES lessons(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY(actor_id, request_key)
);

CREATE FUNCTION protect_one_off_create_replay()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP = 'DELETE' AND schedule_semester_delete_authorized(OLD.semester_id) THEN
        RETURN OLD;
    END IF;
    RAISE EXCEPTION 'accepted one-off creation receipt is immutable';
END
$$;

CREATE TABLE one_off_lesson_restore_authorities (
    operation_id UUID PRIMARY KEY,
    semester_id BIGINT NOT NULL CHECK (semester_id > 0),
    occurrence_id BIGINT NOT NULL REFERENCES lesson_occurrences(id) ON DELETE CASCADE,
    source_lesson_id BIGINT NOT NULL REFERENCES lessons(id) ON DELETE CASCADE DEFERRABLE INITIALLY DEFERRED,
    source_assignment_id BIGINT NOT NULL CHECK (source_assignment_id > 0),
    source_teacher_id BIGINT NOT NULL CHECK (source_teacher_id > 0),
    expected_occurrence_revision BIGINT NOT NULL CHECK (expected_occurrence_revision > 0),
    expected_generation BIGINT NOT NULL CHECK (expected_generation > 0),
    expected_lesson_revision BIGINT NOT NULL CHECK (expected_lesson_revision > 0),
    target_lesson_id BIGINT NOT NULL REFERENCES lessons(id) ON DELETE CASCADE DEFERRABLE INITIALLY DEFERRED,
    target_assignment_id BIGINT NOT NULL CHECK (target_assignment_id > 0),
    target_teacher_id BIGINT NOT NULL CHECK (target_teacher_id > 0),
    target_generation BIGINT NOT NULL,
    replacement_operation_ids UUID[] NOT NULL,
    actor_id BIGINT NOT NULL CHECK (actor_id > 0),
    created_at TIMESTAMPTZ NOT NULL,
    UNIQUE (occurrence_id, expected_occurrence_revision),
    CHECK (target_generation = expected_generation + 1 AND target_lesson_id <> source_lesson_id
           AND target_assignment_id <> source_assignment_id AND target_teacher_id <> source_teacher_id),
    CHECK (cardinality(replacement_operation_ids) BETWEEN 1 AND 32
           AND array_position(replacement_operation_ids, NULL) IS NULL)
);
CREATE TRIGGER one_off_restore_immutable_trg
    BEFORE UPDATE OR DELETE ON one_off_lesson_restore_authorities
    FOR EACH ROW EXECUTE FUNCTION protect_one_off_create_replay();
CREATE TRIGGER one_off_restore_semester_guard_trg
    BEFORE INSERT OR UPDATE OR DELETE ON one_off_lesson_restore_authorities
    FOR EACH ROW EXECUTE FUNCTION guard_schedule_semester_deletion_write();

ALTER TABLE lesson_lifecycle_entries
    ADD COLUMN one_off_restore_operation_id UUID REFERENCES one_off_lesson_restore_authorities(operation_id),
    ADD CONSTRAINT lesson_lifecycle_one_authority_chk CHECK (
        num_nonnulls(restore_operation_id, transfer_operation_id, one_off_restore_operation_id) <= 1
        AND (one_off_restore_operation_id IS NULL OR action = 'RESTORED'));

CREATE FUNCTION validate_one_off_restore_authority()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
DECLARE
    occurrence_row lesson_occurrences%ROWTYPE;
    source_row lessons%ROWTYPE;
    operation_row schedule_assignment_replacement_operations%ROWTYPE;
    fence_row schedule_assignment_fences%ROWTYPE;
    assignment_value BIGINT;
    teacher_value BIGINT;
    operation_value UUID;
    visited UUID[] := ARRAY[]::UUID[];
BEGIN
    IF NEW.operation_id IS DISTINCT FROM NULLIF(current_setting(
        'rutcampustrack.one_off_restore_operation_id', true), '')::UUID THEN
        RAISE EXCEPTION 'one-off restore requires its exact transaction authority';
    END IF;
    SELECT * INTO occurrence_row FROM lesson_occurrences WHERE id = NEW.occurrence_id FOR UPDATE;
    SELECT * INTO source_row FROM lessons WHERE id = NEW.source_lesson_id FOR UPDATE;
    IF occurrence_row.id IS NULL OR source_row.id IS NULL
       OR occurrence_row.schedule_item_id IS NOT NULL OR occurrence_row.one_off_lesson_id IS NULL
       OR occurrence_row.current_lesson_id IS DISTINCT FROM NEW.source_lesson_id
       OR occurrence_row.semester_id IS DISTINCT FROM NEW.semester_id
       OR occurrence_row.assignment_id IS DISTINCT FROM NEW.source_assignment_id
       OR occurrence_row.assigned_teacher_id IS DISTINCT FROM NEW.source_teacher_id
       OR occurrence_row.revision <> NEW.expected_occurrence_revision
       OR occurrence_row.generation <> NEW.expected_generation
       OR source_row.occurrence_id IS DISTINCT FROM occurrence_row.id
       OR source_row.one_off_lesson_id IS DISTINCT FROM occurrence_row.one_off_lesson_id
       OR source_row.schedule_item_id IS NOT NULL OR source_row.status::text <> 'cancelled'
       OR source_row.assignment_id IS DISTINCT FROM NEW.source_assignment_id
       OR source_row.assigned_teacher_id IS DISTINCT FROM NEW.source_teacher_id
       OR source_row.generation <> NEW.expected_generation OR source_row.revision <> NEW.expected_lesson_revision
       OR source_row.date IS DISTINCT FROM occurrence_row.occurrence_date
       OR occurrence_row.occurrence_date < (CURRENT_TIMESTAMP AT TIME ZONE 'Europe/Moscow')::DATE THEN
        RAISE EXCEPTION 'one-off restore source is not the exact current canceled future generation';
    END IF;
    assignment_value := NEW.source_assignment_id;
    teacher_value := NEW.source_teacher_id;
    FOREACH operation_value IN ARRAY NEW.replacement_operation_ids LOOP
        SELECT * INTO operation_row FROM schedule_assignment_replacement_operations
         WHERE operation_id = operation_value FOR UPDATE;
        IF operation_row.operation_id IS NULL OR operation_value = ANY(visited)
           OR operation_row.state <> 'COMMITTED'
           OR operation_row.source_assignment_id <> assignment_value
           OR operation_row.source_teacher_id <> teacher_value
           OR operation_row.group_id <> occurrence_row.group_id
           OR operation_row.subject_id <> occurrence_row.subject_id
           OR operation_row.semester_id <> occurrence_row.semester_id
           OR operation_row.lesson_type <> occurrence_row.lesson_type
           OR occurrence_row.occurrence_date < operation_row.effective_from
           OR occurrence_row.occurrence_date >= operation_row.valid_until_exclusive
           OR (SELECT count(*) FROM schedule_assignment_replacement_operations
                WHERE source_assignment_id = assignment_value
                  AND effective_from <= occurrence_row.occurrence_date
                  AND occurrence_row.occurrence_date < valid_until_exclusive) <> 1 THEN
            RAISE EXCEPTION 'one-off restore replacement path is not exact committed authority';
        END IF;
        visited := array_append(visited, operation_value);
        assignment_value := operation_row.target_assignment_id;
        teacher_value := operation_row.target_teacher_id;
    END LOOP;
    IF assignment_value <> NEW.target_assignment_id OR teacher_value <> NEW.target_teacher_id
       OR EXISTS (SELECT 1 FROM schedule_assignment_replacement_operations
            WHERE source_assignment_id = assignment_value AND effective_from <= occurrence_row.occurrence_date
              AND occurrence_row.occurrence_date < valid_until_exclusive)
       OR EXISTS (SELECT 1 FROM schedule_assignment_replacement_operations
            WHERE target_assignment_id = assignment_value AND state <> 'COMMITTED') THEN
        RAISE EXCEPTION 'one-off restore target is not the terminal committed assignment';
    END IF;
    SELECT * INTO fence_row FROM schedule_assignment_fences WHERE assignment_id = assignment_value FOR UPDATE;
    IF fence_row.assignment_id IS NULL OR fence_row.assigned_teacher_id <> teacher_value
       OR fence_row.group_id <> occurrence_row.group_id OR fence_row.subject_id <> occurrence_row.subject_id
       OR fence_row.semester_id <> occurrence_row.semester_id OR fence_row.lesson_type <> occurrence_row.lesson_type
       OR occurrence_row.occurrence_date < fence_row.valid_from
       OR occurrence_row.occurrence_date >= fence_row.creation_cap_until_exclusive THEN
        RAISE EXCEPTION 'one-off restore target violates exact assignment creation fence';
    END IF;
    RETURN NEW;
END
$$;
CREATE TRIGGER one_off_restore_authority_guard_trg BEFORE INSERT ON one_off_lesson_restore_authorities
    FOR EACH ROW EXECUTE FUNCTION validate_one_off_restore_authority();

CREATE FUNCTION require_completed_one_off_restore()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
DECLARE
    occurrence_row lesson_occurrences%ROWTYPE;
    source_row lessons%ROWTYPE;
    target_row lessons%ROWTYPE;
BEGIN
    SELECT * INTO occurrence_row FROM lesson_occurrences WHERE id = NEW.occurrence_id;
    SELECT * INTO source_row FROM lessons WHERE id = NEW.source_lesson_id;
    SELECT * INTO target_row FROM lessons WHERE id = NEW.target_lesson_id;
    IF occurrence_row.id IS NULL OR source_row.id IS NULL OR target_row.id IS NULL
       OR occurrence_row.current_lesson_id IS DISTINCT FROM NEW.target_lesson_id
       OR occurrence_row.revision <> NEW.expected_occurrence_revision + 1
       OR occurrence_row.generation <> NEW.target_generation
       OR occurrence_row.assignment_id <> NEW.target_assignment_id
       OR occurrence_row.assigned_teacher_id <> NEW.target_teacher_id
       OR source_row.status::text <> 'cancelled' OR source_row.revision <> NEW.expected_lesson_revision
       OR target_row.status::text <> 'planned' OR target_row.revision <> 1
       OR target_row.generation <> NEW.target_generation
       OR (SELECT physical_lesson_id FROM schedule_one_off_lessons WHERE id = occurrence_row.one_off_lesson_id)
            IS DISTINCT FROM NEW.target_lesson_id
       OR NOT EXISTS (SELECT 1 FROM lesson_lifecycle_entries entry
            WHERE entry.one_off_restore_operation_id = NEW.operation_id AND entry.action = 'RESTORED'
              AND entry.occurrence_id = NEW.occurrence_id AND entry.lesson_id = NEW.source_lesson_id
              AND entry.target_lesson_id = NEW.target_lesson_id AND entry.actor_id = NEW.actor_id
              AND entry.revision = NEW.expected_occurrence_revision + 1 AND entry.generation = NEW.target_generation) THEN
        RAISE EXCEPTION 'one-off restore authority must commit with its exact completed generation and history';
    END IF;
    RETURN NEW;
END
$$;
CREATE CONSTRAINT TRIGGER one_off_restore_completion_guard_trg
    AFTER INSERT ON one_off_lesson_restore_authorities DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW EXECUTE FUNCTION require_completed_one_off_restore();

CREATE TRIGGER one_off_create_replay_immutable_trg
    BEFORE UPDATE OR DELETE ON schedule_one_off_create_replay
    FOR EACH ROW EXECUTE FUNCTION protect_one_off_create_replay();

CREATE TRIGGER one_off_create_replay_semester_guard_trg
    BEFORE INSERT OR UPDATE OR DELETE ON schedule_one_off_create_replay
    FOR EACH ROW EXECUTE FUNCTION guard_schedule_semester_deletion_write();

-- V19's recurring branch stays exact. ONE_OFF has a real logical origin,
-- not a fabricated template; its tuple must match that origin and the fence.
CREATE OR REPLACE FUNCTION validate_recurring_occurrence_fence()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
DECLARE
    fence_record schedule_assignment_fences%ROWTYPE;
    schedule_item_record schedule_items%ROWTYPE;
    one_off_record schedule_one_off_lessons%ROWTYPE;
BEGIN
    SELECT * INTO fence_record FROM schedule_assignment_fences
     WHERE assignment_id = NEW.assignment_id FOR UPDATE;
    IF NOT FOUND THEN RAISE EXCEPTION 'assignment fence % does not exist', NEW.assignment_id; END IF;
    IF NEW.schedule_item_id IS NULL THEN
        SELECT * INTO one_off_record FROM schedule_one_off_lessons
         WHERE id = NEW.one_off_lesson_id FOR UPDATE;
        IF NOT FOUND THEN RAISE EXCEPTION 'one-off origin % does not exist', NEW.one_off_lesson_id; END IF;
        IF NEW.one_off_lesson_id IS DISTINCT FROM one_off_record.id
           OR NEW.group_id IS DISTINCT FROM one_off_record.group_id
           OR NEW.subject_id IS DISTINCT FROM one_off_record.subject_id
           OR NEW.semester_id IS DISTINCT FROM one_off_record.semester_id
           OR NEW.occurrence_date IS DISTINCT FROM one_off_record.date
           OR NEW.assignment_id IS DISTINCT FROM fence_record.assignment_id
           OR NEW.group_id IS DISTINCT FROM fence_record.group_id
           OR NEW.subject_id IS DISTINCT FROM fence_record.subject_id
           OR NEW.semester_id IS DISTINCT FROM fence_record.semester_id
           OR NEW.assigned_teacher_id IS DISTINCT FROM fence_record.assigned_teacher_id
           OR NEW.lesson_type IS DISTINCT FROM fence_record.lesson_type
           OR NEW.occurrence_date < fence_record.valid_from
           OR NEW.occurrence_date >= fence_record.creation_cap_until_exclusive THEN
            RAISE EXCEPTION 'one-off occurrence violates exact origin or assignment creation fence';
        END IF;
        RETURN NEW;
    END IF;
    SELECT * INTO schedule_item_record FROM schedule_items
     WHERE id = NEW.schedule_item_id FOR UPDATE;
    IF NOT FOUND THEN RAISE EXCEPTION 'schedule item % does not exist', NEW.schedule_item_id; END IF;
    IF schedule_item_record.assignment_id IS DISTINCT FROM fence_record.assignment_id
       OR schedule_item_record.group_id IS DISTINCT FROM fence_record.group_id
       OR schedule_item_record.subject_id IS DISTINCT FROM fence_record.subject_id
       OR schedule_item_record.semester_id IS DISTINCT FROM fence_record.semester_id
       OR NEW.assignment_id IS DISTINCT FROM fence_record.assignment_id
       OR NEW.group_id IS DISTINCT FROM fence_record.group_id
       OR NEW.subject_id IS DISTINCT FROM fence_record.subject_id
       OR NEW.semester_id IS DISTINCT FROM fence_record.semester_id
       OR NEW.assigned_teacher_id IS DISTINCT FROM fence_record.assigned_teacher_id
       OR NEW.lesson_type IS DISTINCT FROM fence_record.lesson_type
       OR NEW.occurrence_date < fence_record.valid_from
       OR NEW.occurrence_date >= fence_record.creation_cap_until_exclusive THEN
        RAISE EXCEPTION 'recurring occurrence violates assignment creation fence %', NEW.assignment_id;
    END IF;
    RETURN NEW;
END
$$;

-- Explicit one-off branches; existing recurring authority branches remain unchanged.
CREATE OR REPLACE FUNCTION protect_occurrence_identity()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
DECLARE
    one_off_restore_row one_off_lesson_restore_authorities%ROWTYPE;
    operation_id_value UUID;
    operation_row schedule_assignment_replacement_operations%ROWTYPE;
    ledger_row schedule_assignment_rebind_ledger%ROWTYPE;
    restore_operation_id_value UUID;
    restore_row lesson_restore_authorities%ROWTYPE;
    transfer_operation_id_value UUID;
    transfer_row lesson_transfer_authorities%ROWTYPE;
BEGIN
    IF OLD.one_off_lesson_id IS NOT NULL THEN
        IF NEW.one_off_lesson_id IS DISTINCT FROM OLD.one_off_lesson_id
           OR NEW.schedule_item_id IS NOT NULL OR OLD.schedule_item_id IS NOT NULL
           OR NEW.id IS DISTINCT FROM OLD.id OR NEW.occurrence_date IS DISTINCT FROM OLD.occurrence_date
           OR NEW.group_id IS DISTINCT FROM OLD.group_id OR NEW.subject_id IS DISTINCT FROM OLD.subject_id
           OR NEW.semester_id IS DISTINCT FROM OLD.semester_id OR NEW.lesson_type IS DISTINCT FROM OLD.lesson_type
           OR NEW.created_at IS DISTINCT FROM OLD.created_at
           OR NEW.revision < OLD.revision OR NEW.revision > OLD.revision + 1 THEN
            RAISE EXCEPTION 'one-off occurrence origin, scope, date and ordering are immutable';
        END IF;
        IF OLD.current_lesson_id IS NULL AND NEW.current_lesson_id IS NOT NULL
           AND NEW.assignment_id = OLD.assignment_id AND NEW.assigned_teacher_id = OLD.assigned_teacher_id
           AND OLD.generation = 1 AND OLD.revision = 1 AND NEW.generation = 1 AND NEW.revision = 1 THEN
            IF NOT EXISTS (SELECT 1 FROM lessons lesson JOIN schedule_one_off_lessons origin ON origin.id = OLD.one_off_lesson_id
                 WHERE lesson.id = NEW.current_lesson_id AND lesson.occurrence_id = OLD.id
                   AND lesson.one_off_lesson_id = origin.id AND lesson.schedule_item_id IS NULL
                   AND lesson.assignment_id = OLD.assignment_id AND lesson.assigned_teacher_id = OLD.assigned_teacher_id
                   AND lesson.group_id = OLD.group_id AND lesson.subject_id = OLD.subject_id
                   AND lesson.semester_id = OLD.semester_id AND lesson.lesson_type = OLD.lesson_type
                   AND lesson.date = OLD.occurrence_date AND lesson.date = origin.date
                   AND lesson.lesson_number = origin.lesson_number AND lesson.room_snapshot IS NOT DISTINCT FROM origin.classroom
                   AND lesson.start_time IS NOT NULL AND lesson.end_time > lesson.start_time
                   AND lesson.day_of_week = extract(isodow FROM lesson.date) AND lesson.week_type_snapshot = 'all'
                   AND lesson.generation = 1 AND lesson.revision = 1 AND lesson.status::text = 'planned') THEN
                RAISE EXCEPTION 'initial one-off pointer requires its exact generation-1 physical snapshot';
            END IF;
            RETURN NEW;
        END IF;
        IF NEW.current_lesson_id IS DISTINCT FROM OLD.current_lesson_id
           OR NEW.assignment_id IS DISTINCT FROM OLD.assignment_id
           OR NEW.assigned_teacher_id IS DISTINCT FROM OLD.assigned_teacher_id
           OR NEW.generation IS DISTINCT FROM OLD.generation THEN
            SELECT * INTO one_off_restore_row FROM one_off_lesson_restore_authorities
             WHERE operation_id = NULLIF(current_setting('rutcampustrack.one_off_restore_operation_id', true), '')::UUID
               AND occurrence_id = OLD.id FOR UPDATE;
            IF one_off_restore_row.operation_id IS NULL
               OR OLD.current_lesson_id IS DISTINCT FROM one_off_restore_row.source_lesson_id
               OR OLD.assignment_id <> one_off_restore_row.source_assignment_id
               OR OLD.assigned_teacher_id <> one_off_restore_row.source_teacher_id
               OR OLD.revision <> one_off_restore_row.expected_occurrence_revision
               OR OLD.generation <> one_off_restore_row.expected_generation
               OR NEW.current_lesson_id IS DISTINCT FROM one_off_restore_row.target_lesson_id
               OR NEW.assignment_id <> one_off_restore_row.target_assignment_id
               OR NEW.assigned_teacher_id <> one_off_restore_row.target_teacher_id
               OR NEW.generation <> one_off_restore_row.target_generation
               OR NEW.revision <> OLD.revision + 1 THEN
                RAISE EXCEPTION 'one-off occurrence update is outside exact restore authority';
            END IF;
        END IF;
        RETURN NEW;
    END IF;
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
    one_off_restore_row one_off_lesson_restore_authorities%ROWTYPE;
    one_off_source_row lessons%ROWTYPE;
    operation_id_value UUID;
    restore_row lesson_restore_authorities%ROWTYPE;
    transfer_row lesson_transfer_authorities%ROWTYPE;
BEGIN
    IF NEW.one_off_lesson_id IS NOT NULL AND NEW.generation > 1 THEN
        SELECT * INTO one_off_restore_row FROM one_off_lesson_restore_authorities
         WHERE operation_id = NULLIF(current_setting('rutcampustrack.one_off_restore_operation_id', true), '')::UUID
           AND occurrence_id = NEW.occurrence_id AND target_lesson_id = NEW.id FOR UPDATE;
        SELECT * INTO one_off_source_row FROM lessons WHERE id = one_off_restore_row.source_lesson_id;
        IF one_off_restore_row.operation_id IS NULL OR one_off_source_row.id IS NULL
           OR NEW.schedule_item_id IS NOT NULL OR NEW.generation <> one_off_restore_row.target_generation
           OR NEW.revision <> 1 OR NEW.status::text <> 'planned'
           OR NEW.assignment_id <> one_off_restore_row.target_assignment_id
           OR NEW.assigned_teacher_id <> one_off_restore_row.target_teacher_id
           OR NEW.one_off_lesson_id IS DISTINCT FROM one_off_source_row.one_off_lesson_id
           OR NEW.group_id IS DISTINCT FROM one_off_source_row.group_id
           OR NEW.subject_id IS DISTINCT FROM one_off_source_row.subject_id
           OR NEW.semester_id IS DISTINCT FROM one_off_source_row.semester_id
           OR NEW.lesson_type IS DISTINCT FROM one_off_source_row.lesson_type
           OR NEW.date IS DISTINCT FROM one_off_source_row.date
           OR NEW.lesson_number IS DISTINCT FROM one_off_source_row.lesson_number
           OR NEW.day_of_week IS DISTINCT FROM one_off_source_row.day_of_week
           OR NEW.start_time IS DISTINCT FROM one_off_source_row.start_time
           OR NEW.end_time IS DISTINCT FROM one_off_source_row.end_time
           OR NEW.room_snapshot IS DISTINCT FROM one_off_source_row.room_snapshot
           OR NEW.week_type_snapshot IS DISTINCT FROM one_off_source_row.week_type_snapshot
           OR NEW.is_geo_blocked IS DISTINCT FROM one_off_source_row.is_geo_blocked
           OR NEW.is_blocked_by_headman IS DISTINCT FROM one_off_source_row.is_blocked_by_headman
           OR NEW.blocked_by_user_id IS DISTINCT FROM one_off_source_row.blocked_by_user_id
           OR NEW.blocked_at IS DISTINCT FROM one_off_source_row.blocked_at
           OR NEW.cancel_reason IS NOT NULL OR NEW.cancelled_by IS NOT NULL OR NEW.cancelled_at IS NOT NULL THEN
            RAISE EXCEPTION 'one-off physical generation differs from exact restore authority and source snapshot';
        END IF;
        RETURN NEW;
    END IF;
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
CREATE OR REPLACE FUNCTION validate_lesson_restore_lifecycle_entry()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
DECLARE
    one_off_restore_row one_off_lesson_restore_authorities%ROWTYPE;
    restore_row lesson_restore_authorities%ROWTYPE;
    occurrence_row lesson_occurrences%ROWTYPE;
    target_lesson_row lessons%ROWTYPE;
BEGIN
    IF NEW.one_off_restore_operation_id IS NOT NULL THEN
        SELECT * INTO one_off_restore_row FROM one_off_lesson_restore_authorities
         WHERE operation_id = NEW.one_off_restore_operation_id AND occurrence_id = NEW.occurrence_id;
        SELECT * INTO occurrence_row FROM lesson_occurrences WHERE id = NEW.occurrence_id;
        SELECT * INTO target_lesson_row FROM lessons WHERE id = one_off_restore_row.target_lesson_id;
        IF one_off_restore_row.operation_id IS NULL OR occurrence_row.id IS NULL OR target_lesson_row.id IS NULL
           OR NEW.restore_operation_id IS NOT NULL OR NEW.transfer_operation_id IS NOT NULL
           OR NEW.action <> 'RESTORED' OR NEW.lesson_id <> one_off_restore_row.source_lesson_id
           OR NEW.target_lesson_id IS DISTINCT FROM one_off_restore_row.target_lesson_id
           OR NEW.actor_id <> one_off_restore_row.actor_id OR NEW.reason IS NOT NULL
           OR NEW.revision <> one_off_restore_row.expected_occurrence_revision + 1
           OR NEW.generation <> one_off_restore_row.target_generation
           OR occurrence_row.current_lesson_id IS DISTINCT FROM one_off_restore_row.target_lesson_id
           OR occurrence_row.revision <> NEW.revision OR occurrence_row.generation <> NEW.generation
           OR target_lesson_row.status::text <> 'planned' THEN
            RAISE EXCEPTION 'one-off RESTORED history differs from exact completed generation';
        END IF;
        RETURN NEW;
    END IF;
    IF NEW.restore_operation_id IS NULL THEN
        IF NEW.action = 'RESTORED' AND NEW.target_lesson_id IS NOT NULL THEN
            RAISE EXCEPTION 'replacement restore history requires its exact restore operation';
        END IF;
        RETURN NEW;
    END IF;
    IF NEW.action <> 'RESTORED' THEN
        RAISE EXCEPTION 'restore operation can authorize only RESTORED history';
    END IF;
    SELECT * INTO restore_row FROM lesson_restore_authorities
     WHERE operation_id = NEW.restore_operation_id
       AND occurrence_id = NEW.occurrence_id;
    SELECT * INTO occurrence_row FROM lesson_occurrences WHERE id = NEW.occurrence_id;
    SELECT * INTO target_lesson_row FROM lessons
     WHERE id = restore_row.target_lesson_id
       AND occurrence_id = restore_row.occurrence_id;
    IF NOT FOUND OR NEW.revision <> restore_row.expected_occurrence_revision + 1
       OR NEW.lesson_id <> restore_row.source_lesson_id
       OR NEW.target_lesson_id <> restore_row.target_lesson_id
       OR NEW.generation <> restore_row.target_generation
       OR NEW.actor_id <> restore_row.actor_id
       OR NEW.reason IS NOT NULL
       OR occurrence_row.current_lesson_id <> restore_row.target_lesson_id
       OR occurrence_row.revision <> NEW.revision
       OR occurrence_row.generation <> NEW.generation
       OR target_lesson_row.status::text <> 'planned' THEN
        RAISE EXCEPTION 'RESTORED history is outside its exact current-generation authority';
    END IF;
    RETURN NEW;
END
$$;
