-- A cancelled recurring occurrence may be restored after its assignment has
-- been replaced. Keep the old physical lesson and authorize the new immutable
-- generation from the exact committed replacement chain.
CREATE TABLE lesson_restore_authorities
(
    operation_id                 UUID PRIMARY KEY,
    occurrence_id                BIGINT      NOT NULL,
    source_lesson_id             BIGINT      NOT NULL,
    source_schedule_item_id      BIGINT      NOT NULL,
    source_assignment_id         BIGINT      NOT NULL,
    source_teacher_id            BIGINT      NOT NULL,
    expected_occurrence_revision BIGINT      NOT NULL,
    expected_generation          BIGINT      NOT NULL,
    expected_lesson_revision     BIGINT      NOT NULL,
    target_lesson_id             BIGINT      NOT NULL,
    target_schedule_item_id      BIGINT      NOT NULL,
    target_assignment_id         BIGINT      NOT NULL,
    target_teacher_id            BIGINT      NOT NULL,
    target_generation            BIGINT      NOT NULL,
    replacement_operation_ids    UUID[]      NOT NULL,
    actor_id                     BIGINT      NOT NULL,
    created_at                   TIMESTAMPTZ NOT NULL,

    CONSTRAINT lesson_restore_authority_occurrence_fk
        FOREIGN KEY (occurrence_id) REFERENCES lesson_occurrences (id) ON DELETE RESTRICT,
    CONSTRAINT lesson_restore_authority_source_lesson_fk
        FOREIGN KEY (source_lesson_id, occurrence_id)
        REFERENCES lessons (id, occurrence_id) ON DELETE RESTRICT,
    CONSTRAINT lesson_restore_authority_target_lesson_fk
        FOREIGN KEY (target_lesson_id, occurrence_id)
        REFERENCES lessons (id, occurrence_id) ON DELETE RESTRICT DEFERRABLE INITIALLY DEFERRED,
    CONSTRAINT lesson_restore_authority_source_item_fk
        FOREIGN KEY (source_schedule_item_id) REFERENCES schedule_items (id) ON DELETE RESTRICT,
    CONSTRAINT lesson_restore_authority_target_item_fk
        FOREIGN KEY (target_schedule_item_id) REFERENCES schedule_items (id) ON DELETE RESTRICT,
    CONSTRAINT lesson_restore_authority_ids_chk CHECK (
        occurrence_id > 0 AND source_lesson_id > 0 AND source_schedule_item_id > 0
        AND source_assignment_id > 0 AND source_teacher_id > 0
        AND target_lesson_id > 0 AND target_schedule_item_id > 0
        AND target_assignment_id > 0 AND target_teacher_id > 0 AND actor_id > 0),
    CONSTRAINT lesson_restore_authority_revisions_chk CHECK (
        expected_occurrence_revision > 0 AND expected_generation > 0
        AND expected_lesson_revision > 0 AND target_generation = expected_generation + 1),
    CONSTRAINT lesson_restore_authority_target_distinct_chk CHECK (
        source_lesson_id <> target_lesson_id
        AND (source_schedule_item_id <> target_schedule_item_id
             OR source_assignment_id <> target_assignment_id
             OR source_teacher_id <> target_teacher_id)),
    CONSTRAINT lesson_restore_authority_path_chk CHECK (
        cardinality(replacement_operation_ids) > 0
        AND array_position(replacement_operation_ids, NULL) IS NULL),
    CONSTRAINT lesson_restore_authority_occurrence_revision_uq
        UNIQUE (occurrence_id, expected_occurrence_revision)
);

ALTER TABLE lesson_lifecycle_entries
    ADD COLUMN restore_operation_id UUID,
    ADD CONSTRAINT lesson_lifecycle_restore_authority_fk
        FOREIGN KEY (restore_operation_id)
        REFERENCES lesson_restore_authorities (operation_id) ON DELETE RESTRICT,
    ADD CONSTRAINT lesson_lifecycle_restore_action_chk
        CHECK (restore_operation_id IS NULL OR action = 'RESTORED');

CREATE UNIQUE INDEX uq_lesson_lifecycle_restore_operation
    ON lesson_lifecycle_entries (restore_operation_id)
    WHERE restore_operation_id IS NOT NULL;

CREATE OR REPLACE FUNCTION reject_lesson_restore_authority_mutation()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION 'lesson restore authorities are append-only';
END
$$;

CREATE TRIGGER lesson_restore_authorities_append_only_trg
    BEFORE UPDATE OR DELETE ON lesson_restore_authorities
    FOR EACH ROW EXECUTE FUNCTION reject_lesson_restore_authority_mutation();

CREATE OR REPLACE FUNCTION validate_lesson_restore_authority_insert()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
DECLARE
    configured_operation UUID;
    occurrence_row lesson_occurrences%ROWTYPE;
    source_lesson_row lessons%ROWTYPE;
    source_item_row schedule_items%ROWTYPE;
    current_item_row schedule_items%ROWTYPE;
    next_item_row schedule_items%ROWTYPE;
    operation_row schedule_assignment_replacement_operations%ROWTYPE;
    template_row schedule_assignment_replacement_templates%ROWTYPE;
    fence_row schedule_assignment_fences%ROWTYPE;
    current_item_id BIGINT;
    current_assignment_id BIGINT;
    current_teacher_id BIGINT;
    eligible_operation_count INTEGER;
    pending_operation_count INTEGER;
BEGIN
    configured_operation := NULLIF(current_setting(
        'rutcampustrack.lesson_restore_operation_id', true), '')::UUID;
    IF configured_operation IS NULL OR configured_operation <> NEW.operation_id THEN
        RAISE EXCEPTION 'lesson restore requires its exact transaction operation id';
    END IF;

    SELECT * INTO occurrence_row
      FROM lesson_occurrences
     WHERE id = NEW.occurrence_id
     FOR UPDATE;
    SELECT * INTO source_lesson_row
      FROM lessons
     WHERE id = NEW.source_lesson_id AND occurrence_id = NEW.occurrence_id
     FOR UPDATE;
    IF NOT FOUND OR occurrence_row.current_lesson_id IS DISTINCT FROM NEW.source_lesson_id
       OR occurrence_row.revision <> NEW.expected_occurrence_revision
       OR occurrence_row.generation <> NEW.expected_generation
       OR occurrence_row.schedule_item_id IS DISTINCT FROM NEW.source_schedule_item_id
       OR occurrence_row.assignment_id <> NEW.source_assignment_id
       OR occurrence_row.assigned_teacher_id <> NEW.source_teacher_id
       OR source_lesson_row.status::text <> 'cancelled'
       OR source_lesson_row.revision <> NEW.expected_lesson_revision
       OR source_lesson_row.generation <> NEW.expected_generation
       OR source_lesson_row.schedule_item_id IS DISTINCT FROM NEW.source_schedule_item_id
       OR source_lesson_row.assignment_id <> NEW.source_assignment_id
       OR source_lesson_row.assigned_teacher_id <> NEW.source_teacher_id
       OR source_lesson_row.date <> occurrence_row.occurrence_date THEN
        RAISE EXCEPTION 'lesson restore source is no longer the exact cancelled current generation';
    END IF;
    IF occurrence_row.schedule_item_id IS NULL THEN
        RAISE EXCEPTION 'replacement restore authority requires a recurring schedule item';
    END IF;

    SELECT * INTO source_item_row FROM schedule_items
     WHERE id = occurrence_row.schedule_item_id;
    IF NOT FOUND OR source_item_row.assignment_id <> occurrence_row.assignment_id
       OR source_item_row.group_id <> occurrence_row.group_id
       OR source_item_row.subject_id <> occurrence_row.subject_id
       OR source_item_row.semester_id <> occurrence_row.semester_id THEN
        RAISE EXCEPTION 'lesson restore source template does not match the occurrence';
    END IF;

    current_item_id := occurrence_row.schedule_item_id;
    current_assignment_id := occurrence_row.assignment_id;
    current_teacher_id := occurrence_row.assigned_teacher_id;
    FOREACH configured_operation IN ARRAY NEW.replacement_operation_ids LOOP
        SELECT * INTO operation_row FROM schedule_assignment_replacement_operations
         WHERE operation_id = configured_operation FOR UPDATE;
        SELECT * INTO template_row FROM schedule_assignment_replacement_templates
         WHERE operation_id = configured_operation
           AND source_schedule_item_id = current_item_id;
        IF NOT FOUND OR operation_row.state <> 'COMMITTED'
           OR operation_row.source_assignment_id <> current_assignment_id
           OR operation_row.source_teacher_id <> current_teacher_id
           OR operation_row.group_id <> occurrence_row.group_id
           OR operation_row.subject_id <> occurrence_row.subject_id
           OR operation_row.semester_id <> occurrence_row.semester_id
           OR operation_row.lesson_type <> occurrence_row.lesson_type
           OR occurrence_row.occurrence_date < operation_row.effective_from
           OR occurrence_row.occurrence_date >= operation_row.valid_until_exclusive THEN
            RAISE EXCEPTION 'lesson restore replacement path is not committed or not effective on occurrence date';
        END IF;
        SELECT * INTO current_item_row FROM schedule_items WHERE id = current_item_id;
        SELECT * INTO next_item_row FROM schedule_items
         WHERE id = template_row.target_schedule_item_id;
        IF NOT FOUND OR current_item_row.assignment_id <> operation_row.source_assignment_id
           OR next_item_row.assignment_id <> operation_row.target_assignment_id
           OR current_item_row.group_id <> next_item_row.group_id
           OR current_item_row.subject_id <> next_item_row.subject_id
           OR current_item_row.semester_id <> next_item_row.semester_id
           OR current_item_row.day_of_week <> next_item_row.day_of_week
           OR current_item_row.lesson_number <> next_item_row.lesson_number
           OR current_item_row.start_time <> next_item_row.start_time
           OR current_item_row.end_time <> next_item_row.end_time
           OR current_item_row.week_type <> next_item_row.week_type
           OR current_item_row.room IS DISTINCT FROM next_item_row.room THEN
            RAISE EXCEPTION 'lesson restore replacement template does not preserve the exact slot';
        END IF;
        SELECT * INTO fence_row FROM schedule_assignment_fences
         WHERE assignment_id = operation_row.target_assignment_id;
        IF NOT FOUND OR fence_row.assigned_teacher_id <> operation_row.target_teacher_id
           OR fence_row.group_id <> operation_row.group_id
           OR fence_row.subject_id <> operation_row.subject_id
           OR fence_row.semester_id <> operation_row.semester_id
           OR fence_row.lesson_type <> operation_row.lesson_type THEN
            RAISE EXCEPTION 'lesson restore replacement target differs from its exact assignment fence';
        END IF;
        SELECT COUNT(*) INTO eligible_operation_count
          FROM schedule_assignment_replacement_operations candidate
          JOIN schedule_assignment_replacement_templates candidate_template
            ON candidate_template.operation_id = candidate.operation_id
           AND candidate_template.source_schedule_item_id = current_item_id
         WHERE candidate.source_assignment_id = current_assignment_id
           AND candidate.state = 'COMMITTED'
           AND candidate.effective_from <= occurrence_row.occurrence_date
           AND occurrence_row.occurrence_date < candidate.valid_until_exclusive;
        IF eligible_operation_count <> 1 THEN
            RAISE EXCEPTION 'lesson restore replacement path is ambiguous';
        END IF;
        current_item_id := template_row.target_schedule_item_id;
        current_assignment_id := operation_row.target_assignment_id;
        current_teacher_id := operation_row.target_teacher_id;
    END LOOP;

    SELECT COUNT(*) INTO eligible_operation_count
      FROM schedule_assignment_replacement_operations candidate
      JOIN schedule_assignment_replacement_templates candidate_template
        ON candidate_template.operation_id = candidate.operation_id
       AND candidate_template.source_schedule_item_id = current_item_id
     WHERE candidate.source_assignment_id = current_assignment_id
       AND candidate.state = 'COMMITTED'
       AND candidate.effective_from <= occurrence_row.occurrence_date
       AND occurrence_row.occurrence_date < candidate.valid_until_exclusive;
    SELECT COUNT(*) INTO pending_operation_count
      FROM schedule_assignment_replacement_operations candidate
      JOIN schedule_assignment_replacement_templates candidate_template
        ON candidate_template.operation_id = candidate.operation_id
       AND candidate_template.source_schedule_item_id = current_item_id
     WHERE candidate.source_assignment_id = current_assignment_id
       AND candidate.state <> 'COMMITTED'
       AND candidate.effective_from <= occurrence_row.occurrence_date
       AND occurrence_row.occurrence_date < candidate.valid_until_exclusive;
    IF eligible_operation_count <> 0 OR pending_operation_count <> 0 THEN
        RAISE EXCEPTION 'lesson restore target is not the terminal committed assignment for its date';
    END IF;

    SELECT * INTO current_item_row FROM schedule_items WHERE id = current_item_id;
    SELECT * INTO fence_row FROM schedule_assignment_fences
     WHERE assignment_id = current_assignment_id;
    IF current_item_id <> NEW.target_schedule_item_id
       OR current_assignment_id <> NEW.target_assignment_id
       OR current_teacher_id <> NEW.target_teacher_id
       OR current_item_row.assignment_id <> NEW.target_assignment_id
       OR current_item_row.group_id <> occurrence_row.group_id
       OR current_item_row.subject_id <> occurrence_row.subject_id
       OR current_item_row.semester_id <> occurrence_row.semester_id
       OR current_item_row.day_of_week <> source_item_row.day_of_week
       OR current_item_row.lesson_number <> source_item_row.lesson_number
       OR current_item_row.start_time <> source_item_row.start_time
       OR current_item_row.end_time <> source_item_row.end_time
       OR current_item_row.week_type <> source_item_row.week_type
       OR current_item_row.room IS DISTINCT FROM source_item_row.room
       OR fence_row.assignment_id IS NULL
       OR fence_row.assigned_teacher_id <> NEW.target_teacher_id
       OR fence_row.group_id <> occurrence_row.group_id
       OR fence_row.subject_id <> occurrence_row.subject_id
       OR fence_row.semester_id <> occurrence_row.semester_id
       OR fence_row.lesson_type <> occurrence_row.lesson_type
       OR occurrence_row.occurrence_date < fence_row.valid_from
       OR occurrence_row.occurrence_date >= fence_row.cap_until_exclusive
       OR occurrence_row.occurrence_date >= fence_row.creation_cap_until_exclusive THEN
        RAISE EXCEPTION 'lesson restore target is not the exact valid assignment/template for this date';
    END IF;
    IF EXISTS (SELECT 1 FROM lessons WHERE id = NEW.target_lesson_id) THEN
        RAISE EXCEPTION 'lesson restore target physical identity already exists';
    END IF;
    RETURN NEW;
END
$$;

CREATE TRIGGER lesson_restore_authorities_validate_trg
    BEFORE INSERT ON lesson_restore_authorities
    FOR EACH ROW EXECUTE FUNCTION validate_lesson_restore_authority_insert();

CREATE OR REPLACE FUNCTION protect_occurrence_identity()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
DECLARE
    operation_id_value UUID;
    operation_row schedule_assignment_replacement_operations%ROWTYPE;
    ledger_row schedule_assignment_rebind_ledger%ROWTYPE;
    restore_operation_id_value UUID;
    restore_row lesson_restore_authorities%ROWTYPE;
BEGIN
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

    -- Preserve the original V19 creation path: a newly inserted generation-1
    -- occurrence may attach its first physical lesson once. The FK and the
    -- exact tuple check below ensure this is the physical lesson for this
    -- occurrence, not a way to redirect an existing canonical pointer.
    IF OLD.current_lesson_id IS NULL
       AND NEW.current_lesson_id IS NOT NULL
       AND OLD.schedule_item_id IS NOT NULL
       AND OLD.one_off_lesson_id IS NULL
       AND NEW.schedule_item_id IS NOT DISTINCT FROM OLD.schedule_item_id
       AND NEW.assignment_id = OLD.assignment_id
       AND NEW.assigned_teacher_id = OLD.assigned_teacher_id
       AND NEW.generation = OLD.generation
       AND NEW.generation = 1
       AND NEW.revision = OLD.revision
       AND NEW.revision = 1 THEN
        IF NOT EXISTS (
            SELECT 1 FROM lessons lesson
             WHERE lesson.id = NEW.current_lesson_id
               AND lesson.occurrence_id = OLD.id
               AND lesson.schedule_item_id = OLD.schedule_item_id
               AND lesson.assignment_id = OLD.assignment_id
               AND lesson.group_id = OLD.group_id
               AND lesson.subject_id = OLD.subject_id
               AND lesson.semester_id = OLD.semester_id
               AND lesson.assigned_teacher_id = OLD.assigned_teacher_id
               AND lesson.lesson_type = OLD.lesson_type
               AND lesson.date = OLD.occurrence_date
               AND lesson.generation = 1
               AND lesson.revision = 1
        ) THEN
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
             WHERE operation_id = restore_operation_id_value
               AND occurrence_id = OLD.id
             FOR UPDATE;
            IF NOT FOUND
               OR OLD.current_lesson_id IS DISTINCT FROM restore_row.source_lesson_id
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
         WHERE operation_id = operation_id_value
           AND occurrence_id = OLD.id
           AND result = 'MOVED' FOR UPDATE;
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
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
DECLARE
    operation_id_value UUID;
    restore_row lesson_restore_authorities%ROWTYPE;
BEGIN
    IF NEW.generation <= 1 THEN
        RETURN NEW;
    END IF;
    operation_id_value := NULLIF(current_setting(
        'rutcampustrack.lesson_restore_operation_id', true), '')::UUID;
    IF operation_id_value IS NULL THEN
        RAISE EXCEPTION 'new physical lesson generation requires an exact restore authority';
    END IF;
    SELECT * INTO restore_row FROM lesson_restore_authorities
     WHERE operation_id = operation_id_value
       AND occurrence_id = NEW.occurrence_id
       AND target_lesson_id = NEW.id
     FOR UPDATE;
    IF NOT FOUND OR NEW.generation <> restore_row.target_generation
       OR NEW.revision <> 1
       OR NEW.schedule_item_id <> restore_row.target_schedule_item_id
       OR NEW.assignment_id <> restore_row.target_assignment_id
       OR NEW.assigned_teacher_id <> restore_row.target_teacher_id
       OR NEW.status::text <> 'planned'
       OR NEW.date <> (SELECT occurrence_date FROM lesson_occurrences WHERE id = NEW.occurrence_id) THEN
        RAISE EXCEPTION 'new physical lesson differs from the exact restore authority';
    END IF;
    RETURN NEW;
END
$$;

CREATE TRIGGER lessons_restore_authority_guard_trg
    BEFORE INSERT ON lessons
    FOR EACH ROW EXECUTE FUNCTION validate_restored_physical_lesson_insert();

CREATE OR REPLACE FUNCTION validate_lesson_restore_lifecycle_entry()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
DECLARE
    restore_row lesson_restore_authorities%ROWTYPE;
    occurrence_row lesson_occurrences%ROWTYPE;
    target_lesson_row lessons%ROWTYPE;
BEGIN
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

CREATE TRIGGER lesson_lifecycle_restore_authority_guard_trg
    BEFORE INSERT ON lesson_lifecycle_entries
    FOR EACH ROW EXECUTE FUNCTION validate_lesson_restore_lifecycle_entry();

CREATE OR REPLACE FUNCTION require_completed_lesson_restore_authority()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
DECLARE
    occurrence_row lesson_occurrences%ROWTYPE;
    target_lesson_row lessons%ROWTYPE;
BEGIN
    SELECT * INTO occurrence_row FROM lesson_occurrences WHERE id = NEW.occurrence_id;
    SELECT * INTO target_lesson_row FROM lessons
     WHERE id = NEW.target_lesson_id AND occurrence_id = NEW.occurrence_id;
    IF NOT FOUND OR occurrence_row.current_lesson_id <> NEW.target_lesson_id
       OR occurrence_row.schedule_item_id <> NEW.target_schedule_item_id
       OR occurrence_row.assignment_id <> NEW.target_assignment_id
       OR occurrence_row.assigned_teacher_id <> NEW.target_teacher_id
       OR occurrence_row.generation <> NEW.target_generation
       OR occurrence_row.revision <> NEW.expected_occurrence_revision + 1
       OR target_lesson_row.generation <> NEW.target_generation
       OR target_lesson_row.status::text <> 'planned'
       OR NOT EXISTS (
            SELECT 1 FROM lesson_lifecycle_entries entry
             WHERE entry.restore_operation_id = NEW.operation_id
               AND entry.occurrence_id = NEW.occurrence_id
               AND entry.revision = NEW.expected_occurrence_revision + 1
               AND entry.action = 'RESTORED'
               AND entry.lesson_id = NEW.source_lesson_id
               AND entry.target_lesson_id = NEW.target_lesson_id
               AND entry.generation = NEW.target_generation
               AND entry.actor_id = NEW.actor_id
       ) THEN
        RAISE EXCEPTION 'lesson restore authority has no matching current generation and lifecycle history';
    END IF;
    RETURN NULL;
END
$$;

CREATE CONSTRAINT TRIGGER lesson_restore_authority_completion_guard_trg
    AFTER INSERT ON lesson_restore_authorities
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW EXECUTE FUNCTION require_completed_lesson_restore_authority();
