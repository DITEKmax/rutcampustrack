-- Template lifecycle never deletes an occurrence or a physical generation.
ALTER TABLE schedule_items
    ADD COLUMN lifecycle_revision BIGINT NOT NULL DEFAULT 1 CHECK (lifecycle_revision > 0),
    ADD COLUMN deactivated_at TIMESTAMPTZ,
    ADD COLUMN generation_not_before TIMESTAMPTZ;

CREATE TABLE schedule_recurring_lifecycle_replay (
    operation_id UUID PRIMARY KEY,
    actor_id BIGINT NOT NULL CHECK (actor_id > 0),
    request_key UUID NOT NULL,
    payload_hash BYTEA NOT NULL CHECK (octet_length(payload_hash) = 32),
    schedule_item_id BIGINT NOT NULL REFERENCES schedule_items(id) ON DELETE RESTRICT,
    action VARCHAR(16) NOT NULL CHECK (action IN ('UPDATE', 'DELETE', 'REACTIVATE')),
    response_snapshot JSONB NOT NULL,
    accepted_at TIMESTAMPTZ NOT NULL,
    UNIQUE (actor_id, request_key)
);

CREATE TRIGGER schedule_recurring_lifecycle_replay_append_only_trg
    BEFORE UPDATE OR DELETE ON schedule_recurring_lifecycle_replay
    FOR EACH ROW EXECUTE FUNCTION reject_schedule_transfer_replay_mutation();

ALTER TABLE lesson_lifecycle_entries
    ADD COLUMN template_operation_id UUID,
    ADD CONSTRAINT lesson_lifecycle_template_operation_fk
        FOREIGN KEY (template_operation_id) REFERENCES schedule_recurring_lifecycle_replay(operation_id)
        ON DELETE RESTRICT DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE lesson_transfer_operations
    ADD COLUMN template_operation_id UUID,
    ADD CONSTRAINT lesson_transfer_template_operation_fk
        FOREIGN KEY (template_operation_id) REFERENCES schedule_recurring_lifecycle_replay(operation_id)
        ON DELETE RESTRICT DEFERRABLE INITIALLY DEFERRED;

CREATE OR REPLACE FUNCTION protect_template_transfer_marker()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.template_operation_id IS DISTINCT FROM OLD.template_operation_id THEN
        RAISE EXCEPTION 'template transfer provenance is immutable';
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER lesson_transfer_template_marker_guard_trg
    BEFORE UPDATE ON lesson_transfer_operations
    FOR EACH ROW EXECUTE FUNCTION protect_template_transfer_marker();

CREATE INDEX idx_recurring_lifecycle_item ON schedule_recurring_lifecycle_replay(schedule_item_id);

-- A template cancellation can be resumed only as a NEW physical generation.
-- Attendance cancellation markers and archived homework remain tied to the old id.
CREATE TABLE lesson_template_restore_authorities (
    operation_id UUID NOT NULL REFERENCES schedule_recurring_lifecycle_replay(operation_id)
        ON DELETE RESTRICT DEFERRABLE INITIALLY DEFERRED,
    occurrence_id BIGINT NOT NULL REFERENCES lesson_occurrences(id) ON DELETE RESTRICT,
    source_lesson_id BIGINT NOT NULL,
    target_lesson_id BIGINT NOT NULL,
    expected_revision BIGINT NOT NULL CHECK (expected_revision > 0),
    expected_generation BIGINT NOT NULL CHECK (expected_generation > 0),
    expected_lesson_revision BIGINT NOT NULL CHECK (expected_lesson_revision > 0),
    target_schedule_item_id BIGINT NOT NULL REFERENCES schedule_items(id) ON DELETE RESTRICT,
    target_assignment_id BIGINT NOT NULL CHECK (target_assignment_id > 0),
    target_teacher_id BIGINT NOT NULL CHECK (target_teacher_id > 0),
    target_room VARCHAR(64),
    target_week_type VARCHAR(8) NOT NULL CHECK (target_week_type IN ('all','odd','even')),
    actor_id BIGINT NOT NULL CHECK (actor_id > 0),
    created_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (operation_id, occurrence_id),
    UNIQUE (occurrence_id, expected_revision),
    CHECK (source_lesson_id <> target_lesson_id),
    FOREIGN KEY (source_lesson_id, occurrence_id) REFERENCES lessons(id, occurrence_id) ON DELETE RESTRICT,
    FOREIGN KEY (target_lesson_id, occurrence_id) REFERENCES lessons(id, occurrence_id)
        ON DELETE RESTRICT DEFERRABLE INITIALLY DEFERRED
);
CREATE TRIGGER lesson_template_restore_append_only_trg
    BEFORE UPDATE OR DELETE ON lesson_template_restore_authorities
    FOR EACH ROW EXECUTE FUNCTION reject_lesson_restore_authority_mutation();

CREATE FUNCTION validate_template_restore_authority()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
DECLARE
    source_row lessons%ROWTYPE;
    occurrence_row lesson_occurrences%ROWTYPE;
    target_row schedule_items%ROWTYPE;
    fence_row schedule_assignment_fences%ROWTYPE;
BEGIN
    IF NEW.operation_id IS DISTINCT FROM NULLIF(current_setting('rutcampustrack.template_restore_operation_id', true),'')::UUID THEN
        RAISE EXCEPTION 'template restore requires its exact transaction authority';
    END IF;
    SELECT * INTO occurrence_row FROM lesson_occurrences WHERE id = NEW.occurrence_id FOR UPDATE;
    SELECT * INTO source_row FROM lessons WHERE id = NEW.source_lesson_id FOR UPDATE;
    SELECT * INTO target_row FROM schedule_items WHERE id = NEW.target_schedule_item_id FOR UPDATE;
    SELECT * INTO fence_row FROM schedule_assignment_fences WHERE assignment_id = NEW.target_assignment_id FOR UPDATE;
    IF source_row.id IS NULL OR target_row.id IS NULL OR fence_row.assignment_id IS NULL
       OR occurrence_row.current_lesson_id IS DISTINCT FROM NEW.source_lesson_id
       OR occurrence_row.revision <> NEW.expected_revision OR occurrence_row.generation <> NEW.expected_generation
       OR source_row.occurrence_id <> NEW.occurrence_id OR source_row.status::text <> 'cancelled'
       OR source_row.revision <> NEW.expected_lesson_revision
       OR source_row.generation <> NEW.expected_generation
       OR source_row.date <> occurrence_row.occurrence_date
       OR (source_row.date + source_row.start_time) AT TIME ZONE 'Europe/Moscow' <= NEW.created_at
       OR target_row.assignment_id <> NEW.target_assignment_id OR NOT target_row.is_active
       OR target_row.group_id <> occurrence_row.group_id OR target_row.subject_id <> occurrence_row.subject_id
       OR target_row.semester_id <> occurrence_row.semester_id
       OR target_row.room IS DISTINCT FROM NEW.target_room OR target_row.week_type::text <> NEW.target_week_type
       OR target_row.lesson_number <> source_row.lesson_number OR target_row.day_of_week <> source_row.day_of_week
       OR target_row.start_time <> source_row.start_time OR target_row.end_time <> source_row.end_time
       OR fence_row.assigned_teacher_id <> NEW.target_teacher_id
       OR fence_row.group_id <> occurrence_row.group_id OR fence_row.subject_id <> occurrence_row.subject_id
       OR fence_row.semester_id <> occurrence_row.semester_id OR fence_row.lesson_type <> occurrence_row.lesson_type
       OR source_row.date < fence_row.valid_from OR source_row.date >= fence_row.creation_cap_until_exclusive
       OR NOT EXISTS (SELECT 1 FROM lesson_lifecycle_entries entry
            WHERE entry.occurrence_id = NEW.occurrence_id AND entry.revision = NEW.expected_revision
              AND entry.lesson_id = NEW.source_lesson_id AND entry.action = 'CANCELLED'
              AND entry.template_operation_id IS NOT NULL)
       OR EXISTS (SELECT 1 FROM lesson_transfer_operations operation
            WHERE operation.occurrence_id = NEW.occurrence_id
              AND (operation.template_operation_id IS NULL OR operation.state <> 'COMPLETED')) THEN
        RAISE EXCEPTION 'template restore is not the exact future template-cancelled generation';
    END IF;
    -- The target must be the source or follow its exact committed replacement chain.
    IF NOT EXISTS (
        WITH RECURSIVE targets(id, assignment_id) AS (
            SELECT occurrence_row.schedule_item_id, occurrence_row.assignment_id
            UNION
            SELECT mapping.target_schedule_item_id, operation.target_assignment_id
              FROM targets
              JOIN schedule_assignment_replacement_templates mapping ON mapping.source_schedule_item_id = targets.id
              JOIN schedule_assignment_replacement_operations operation ON operation.operation_id = mapping.operation_id
             WHERE operation.state = 'COMMITTED' AND operation.source_assignment_id = targets.assignment_id
               AND operation.effective_from <= source_row.date AND source_row.date < operation.valid_until_exclusive
        ) SELECT 1 FROM targets WHERE id = NEW.target_schedule_item_id AND assignment_id = NEW.target_assignment_id
    ) THEN RAISE EXCEPTION 'template restore target is outside committed replacement history'; END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER lesson_template_restore_authority_guard_trg
    BEFORE INSERT ON lesson_template_restore_authorities
    FOR EACH ROW EXECUTE FUNCTION validate_template_restore_authority();

CREATE FUNCTION require_completed_template_restore()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
DECLARE
    occurrence_row lesson_occurrences%ROWTYPE;
    target_row lessons%ROWTYPE;
BEGIN
    SELECT * INTO occurrence_row FROM lesson_occurrences WHERE id = NEW.occurrence_id;
    SELECT * INTO target_row FROM lessons WHERE id = NEW.target_lesson_id;
    IF occurrence_row.current_lesson_id <> NEW.target_lesson_id OR occurrence_row.revision <> NEW.expected_revision + 1
       OR occurrence_row.generation <> NEW.expected_generation + 1 OR target_row.status::text <> 'planned'
       OR NOT EXISTS (SELECT 1 FROM lesson_lifecycle_entries entry
           WHERE entry.occurrence_id = NEW.occurrence_id AND entry.revision = NEW.expected_revision + 1
             AND entry.action = 'RESTORED' AND entry.lesson_id = NEW.source_lesson_id
             AND entry.target_lesson_id = NEW.target_lesson_id AND entry.template_operation_id = NEW.operation_id)
       OR NOT EXISTS (SELECT 1 FROM schedule_recurring_lifecycle_replay operation
           WHERE operation.operation_id = NEW.operation_id AND operation.action IN ('UPDATE','REACTIVATE')) THEN
        RAISE EXCEPTION 'template restore authority has no complete generation and lifecycle result';
    END IF;
    RETURN NEW;
END $$;
CREATE CONSTRAINT TRIGGER lesson_template_restore_complete_trg
    AFTER INSERT ON lesson_template_restore_authorities DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW EXECUTE FUNCTION require_completed_template_restore();

-- Existing V26 branches retained below the narrow template authority.
CREATE OR REPLACE FUNCTION protect_occurrence_identity()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
DECLARE
    template_row lesson_template_restore_authorities%ROWTYPE;
    one_off_restore_row one_off_lesson_restore_authorities%ROWTYPE;
    one_off_transfer_source lessons%ROWTYPE;
    operation_id_value UUID;
    operation_row schedule_assignment_replacement_operations%ROWTYPE;
    ledger_row schedule_assignment_rebind_ledger%ROWTYPE;
    restore_operation_id_value UUID;
    restore_row lesson_restore_authorities%ROWTYPE;
    transfer_operation_id_value UUID;
    transfer_row lesson_transfer_authorities%ROWTYPE;
BEGIN

    IF OLD.schedule_item_id IS NOT NULL AND OLD.current_lesson_id IS NOT NULL AND NEW.current_lesson_id IS DISTINCT FROM OLD.current_lesson_id
       AND NULLIF(current_setting('rutcampustrack.template_restore_operation_id', true),'') IS NOT NULL THEN
        SELECT * INTO template_row FROM lesson_template_restore_authorities
         WHERE operation_id = NULLIF(current_setting('rutcampustrack.template_restore_operation_id', true),'')::UUID
           AND occurrence_id = OLD.id FOR UPDATE;
        IF template_row.operation_id IS NULL OR OLD.current_lesson_id <> template_row.source_lesson_id
           OR OLD.revision <> template_row.expected_revision OR OLD.generation <> template_row.expected_generation
           OR NEW.current_lesson_id <> template_row.target_lesson_id
           OR NEW.schedule_item_id <> template_row.target_schedule_item_id
           OR NEW.assignment_id <> template_row.target_assignment_id OR NEW.assigned_teacher_id <> template_row.target_teacher_id
           OR NEW.revision <> OLD.revision + 1 OR NEW.generation <> OLD.generation + 1
           OR (to_jsonb(NEW) - ARRAY['schedule_item_id','assignment_id','assigned_teacher_id','current_lesson_id','revision','generation'])
               IS DISTINCT FROM (to_jsonb(OLD) - ARRAY['schedule_item_id','assignment_id','assigned_teacher_id','current_lesson_id','revision','generation']) THEN
            RAISE EXCEPTION 'occurrence update is outside exact template restore authority';
        END IF;
        RETURN NEW;
    END IF;
    -- A transfer keeps the origin/assignment/scope immutable and changes only current placement.
    transfer_operation_id_value := NULLIF(current_setting(
        'rutcampustrack.lesson_transfer_operation_id', true), '')::UUID;
    IF OLD.one_off_lesson_id IS NOT NULL AND transfer_operation_id_value IS NOT NULL THEN
        SELECT * INTO transfer_row FROM lesson_transfer_authorities
         WHERE operation_id = transfer_operation_id_value AND occurrence_id = OLD.id FOR UPDATE;
        SELECT * INTO one_off_transfer_source FROM lessons WHERE id = transfer_row.source_lesson_id;
        IF transfer_row.operation_id IS NULL OR one_off_transfer_source.id IS NULL
           OR OLD.current_lesson_id IS DISTINCT FROM transfer_row.source_lesson_id
           OR OLD.occurrence_date IS DISTINCT FROM one_off_transfer_source.date
           OR OLD.revision <> transfer_row.expected_occurrence_revision
           OR OLD.generation <> transfer_row.expected_generation
           OR one_off_transfer_source.one_off_lesson_id IS DISTINCT FROM OLD.one_off_lesson_id
           OR one_off_transfer_source.schedule_item_id IS NOT NULL OR OLD.schedule_item_id IS NOT NULL
           OR one_off_transfer_source.status::text <> 'transferred'
           OR one_off_transfer_source.revision <> transfer_row.expected_lesson_revision + 1
           OR NEW.current_lesson_id IS DISTINCT FROM transfer_row.target_lesson_id
           OR NEW.occurrence_date IS DISTINCT FROM transfer_row.target_date
           OR NEW.generation <> transfer_row.expected_generation + 1
           OR NEW.revision <> transfer_row.expected_occurrence_revision + 1
           OR (to_jsonb(NEW) - ARRAY['occurrence_date', 'current_lesson_id', 'generation', 'revision'])
               IS DISTINCT FROM (to_jsonb(OLD) - ARRAY['occurrence_date', 'current_lesson_id', 'generation', 'revision'])
           OR NOT EXISTS (SELECT 1 FROM schedule_one_off_lessons origin
                WHERE origin.id = OLD.one_off_lesson_id AND origin.physical_lesson_id = transfer_row.source_lesson_id
                  AND origin.group_id = OLD.group_id AND origin.subject_id = OLD.subject_id
                  AND origin.semester_id = OLD.semester_id) THEN
            RAISE EXCEPTION 'one-off occurrence update is outside exact transfer authority';
        END IF;
        RETURN NEW;
    END IF;
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

-- Existing V26 branches retained below the narrow template authority.
CREATE OR REPLACE FUNCTION validate_restored_physical_lesson_insert()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
DECLARE
    template_row lesson_template_restore_authorities%ROWTYPE;
    one_off_restore_row one_off_lesson_restore_authorities%ROWTYPE;
    one_off_source_row lessons%ROWTYPE;
    operation_id_value UUID;
    restore_row lesson_restore_authorities%ROWTYPE;
    transfer_row lesson_transfer_authorities%ROWTYPE;
BEGIN

    IF NEW.schedule_item_id IS NOT NULL AND NEW.generation > 1
       AND NULLIF(current_setting('rutcampustrack.template_restore_operation_id', true),'') IS NOT NULL THEN
        SELECT * INTO template_row FROM lesson_template_restore_authorities
         WHERE operation_id = NULLIF(current_setting('rutcampustrack.template_restore_operation_id', true),'')::UUID
           AND occurrence_id = NEW.occurrence_id AND target_lesson_id = NEW.id FOR UPDATE;
        IF template_row.operation_id IS NULL OR NEW.generation <> template_row.expected_generation + 1 OR NEW.revision <> 1
           OR NEW.schedule_item_id <> template_row.target_schedule_item_id
           OR NEW.assignment_id <> template_row.target_assignment_id OR NEW.assigned_teacher_id <> template_row.target_teacher_id
           OR NEW.room_snapshot IS DISTINCT FROM template_row.target_room OR NEW.week_type_snapshot <> template_row.target_week_type
           OR NEW.status::text <> 'planned' OR NEW.one_off_lesson_id IS NOT NULL
           OR NEW.date <> (SELECT date FROM lessons WHERE id = template_row.source_lesson_id)
           OR NEW.lesson_number <> (SELECT lesson_number FROM lessons WHERE id = template_row.source_lesson_id)
           OR NEW.start_time <> (SELECT start_time FROM lessons WHERE id = template_row.source_lesson_id)
           OR NEW.end_time <> (SELECT end_time FROM lessons WHERE id = template_row.source_lesson_id) THEN
            RAISE EXCEPTION 'physical generation differs from exact template restore authority';
        END IF;
        RETURN NEW;
    END IF;
    -- Exact ONE_OFF transfer authority takes precedence over the restore-only generation branch.
    operation_id_value := NULLIF(current_setting(
        'rutcampustrack.lesson_transfer_operation_id', true), '')::UUID;
    IF NEW.one_off_lesson_id IS NOT NULL AND NEW.generation > 1 AND operation_id_value IS NOT NULL THEN
        SELECT * INTO transfer_row FROM lesson_transfer_authorities
         WHERE operation_id = operation_id_value AND occurrence_id = NEW.occurrence_id
           AND target_lesson_id = NEW.id FOR UPDATE;
        SELECT * INTO one_off_source_row FROM lessons WHERE id = transfer_row.source_lesson_id;
        IF transfer_row.operation_id IS NULL OR one_off_source_row.id IS NULL
           OR NEW.schedule_item_id IS NOT NULL OR one_off_source_row.schedule_item_id IS NOT NULL
           OR NEW.one_off_lesson_id IS DISTINCT FROM one_off_source_row.one_off_lesson_id
           OR NEW.occurrence_id IS DISTINCT FROM one_off_source_row.occurrence_id
           OR NEW.assignment_id IS DISTINCT FROM one_off_source_row.assignment_id
           OR NEW.assigned_teacher_id IS DISTINCT FROM one_off_source_row.assigned_teacher_id
           OR NEW.group_id IS DISTINCT FROM one_off_source_row.group_id
           OR NEW.subject_id IS DISTINCT FROM one_off_source_row.subject_id
           OR NEW.semester_id IS DISTINCT FROM one_off_source_row.semester_id
           OR NEW.lesson_type IS DISTINCT FROM one_off_source_row.lesson_type
           OR NEW.generation <> transfer_row.expected_generation + 1 OR NEW.revision <> 1
           OR NEW.date IS DISTINCT FROM transfer_row.target_date
           OR NEW.lesson_number IS DISTINCT FROM transfer_row.target_lesson_number
           OR NEW.start_time IS DISTINCT FROM transfer_row.target_start_time
           OR NEW.end_time IS DISTINCT FROM transfer_row.target_end_time
           OR NEW.room_snapshot IS DISTINCT FROM transfer_row.target_room
           OR NEW.day_of_week IS DISTINCT FROM extract(isodow FROM NEW.date)::SMALLINT
           OR NEW.week_type_snapshot IS DISTINCT FROM one_off_source_row.week_type_snapshot
           OR NEW.is_geo_blocked IS DISTINCT FROM one_off_source_row.is_geo_blocked
           OR NEW.is_blocked_by_headman IS DISTINCT FROM one_off_source_row.is_blocked_by_headman
           OR NEW.blocked_by_user_id IS DISTINCT FROM one_off_source_row.blocked_by_user_id
           OR NEW.blocked_at IS DISTINCT FROM one_off_source_row.blocked_at
           OR NEW.status::text <> 'planned' OR NEW.cancel_reason IS NOT NULL
           OR NEW.cancelled_by IS NOT NULL OR NEW.cancelled_at IS NOT NULL THEN
            RAISE EXCEPTION 'one-off physical generation differs from exact transfer authority and source';
        END IF;
        RETURN NEW;
    END IF;
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
