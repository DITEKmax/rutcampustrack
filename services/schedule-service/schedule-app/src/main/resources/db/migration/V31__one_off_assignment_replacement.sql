-- Extend V28's exact origin-neutral binding ledger only for current planned one-off REPLACE.
-- The existing append-only and deferred completion triggers remain attached and unchanged.
-- V28 occurrence and V29 physical-snapshot guards below retain all other branches, including room receipts.
CREATE OR REPLACE FUNCTION validate_assignment_binding_ledger() RETURNS TRIGGER LANGUAGE plpgsql AS $$
DECLARE operation schedule_assignment_replacement_operations%ROWTYPE;
        occurrence lesson_occurrences%ROWTYPE;
        lesson lessons%ROWTYPE;
BEGIN
 IF TG_OP='DELETE' AND schedule_semester_delete_authorized(OLD.semester_id) THEN RETURN OLD; END IF;
 IF TG_OP<>'INSERT' THEN RAISE EXCEPTION 'assignment binding history is append-only'; END IF;
 IF EXISTS(SELECT 1 FROM schedule_assignment_replacement_operations
     WHERE operation_id=NEW.operation_id AND operation_kind='REPLACE') THEN
   PERFORM pg_advisory_xact_lock(5452097,(NEW.semester_id%2147483647)::integer);
   IF EXISTS(SELECT 1 FROM schedule_semester_archive_barriers
       WHERE semester_id=NEW.semester_id AND participant_state<>'RELEASED') THEN
     RAISE EXCEPTION 'semester blocks one-off replacement';
   END IF;
 END IF;
 SELECT * INTO operation FROM schedule_assignment_replacement_operations WHERE operation_id=NEW.operation_id FOR UPDATE;
 IF NOT FOUND OR operation.state<>'APPLIED' OR operation.semester_id<>NEW.semester_id
    OR NEW.accepted_at IS DISTINCT FROM NOW() OR operation.operation_kind NOT IN ('CLOSE_UNASSIGN','ADOPT_UNASSIGNED','REPLACE')
    OR NULLIF(current_setting('rutcampustrack.assignment_binding_operation_id',true),'')::UUID IS DISTINCT FROM NEW.operation_id THEN
   RAISE EXCEPTION 'binding history requires its exact applied operation';
 END IF;
 IF operation.operation_kind='REPLACE' THEN
   PERFORM assignment_id FROM schedule_assignment_fences
    WHERE assignment_id IN (operation.source_assignment_id,operation.target_assignment_id)
    ORDER BY assignment_id FOR UPDATE;
   IF NOT EXISTS(SELECT 1 FROM schedule_assignment_fences source_fence
       JOIN schedule_assignment_fences target_fence ON target_fence.assignment_id=operation.target_assignment_id
       WHERE source_fence.assignment_id=operation.source_assignment_id
         AND source_fence.group_id=operation.group_id AND target_fence.group_id=operation.group_id
         AND source_fence.subject_id=operation.subject_id AND target_fence.subject_id=operation.subject_id
         AND source_fence.semester_id=operation.semester_id AND target_fence.semester_id=operation.semester_id
         AND source_fence.lesson_type=operation.lesson_type AND target_fence.lesson_type=operation.lesson_type
         AND source_fence.assigned_teacher_id=operation.source_teacher_id
         AND target_fence.assigned_teacher_id=operation.target_teacher_id
         AND source_fence.valid_from=operation.source_valid_from AND target_fence.valid_from=operation.effective_from
         AND source_fence.cap_until_exclusive=operation.valid_until_exclusive
         AND target_fence.cap_until_exclusive=operation.valid_until_exclusive
         AND source_fence.creation_cap_until_exclusive=operation.effective_from
         AND target_fence.creation_cap_until_exclusive=operation.valid_until_exclusive) THEN
     RAISE EXCEPTION 'one-off replacement requires its exact source and target fences';
   END IF;
   PERFORM origin.id FROM schedule_one_off_lessons origin
    JOIN lesson_occurrences current_occurrence ON current_occurrence.one_off_lesson_id=origin.id
    WHERE current_occurrence.id=NEW.occurrence_id FOR UPDATE OF origin;
 END IF;
 SELECT * INTO occurrence FROM lesson_occurrences WHERE id=NEW.occurrence_id FOR UPDATE;
 IF NOT FOUND OR occurrence.revision<>NEW.expected_occurrence_revision
   OR occurrence.current_lesson_id IS DISTINCT FROM NEW.lesson_id OR occurrence.occurrence_date<>NEW.occurrence_date
   OR occurrence.assignment_id IS DISTINCT FROM NEW.before_assignment_id
   OR occurrence.assigned_teacher_id IS DISTINCT FROM NEW.before_teacher_id
   OR occurrence.group_id<>operation.group_id OR occurrence.subject_id<>operation.subject_id
   OR occurrence.semester_id<>operation.semester_id OR occurrence.lesson_type<>operation.lesson_type
   OR occurrence.occurrence_date<COALESCE(operation.requested_cutoff,operation.effective_from) OR occurrence.occurrence_date>=operation.valid_until_exclusive THEN
   RAISE EXCEPTION 'binding history does not match current occurrence authority';
 END IF;
 IF NEW.lesson_id IS NOT NULL THEN
   SELECT * INTO lesson FROM lessons WHERE id=NEW.lesson_id FOR UPDATE;
   IF NOT FOUND OR lesson.occurrence_id<>NEW.occurrence_id OR lesson.revision<>NEW.expected_lesson_revision
     OR lesson.assignment_id IS DISTINCT FROM NEW.before_assignment_id OR lesson.assigned_teacher_id IS DISTINCT FROM NEW.before_teacher_id
     OR lesson.status::text IN ('active','closed') OR (lesson.date+lesson.start_time) AT TIME ZONE 'Europe/Moscow'<=NEW.accepted_at THEN
     RAISE EXCEPTION 'held or started lessons cannot change their binding';
   END IF;
 ELSIF NEW.occurrence_date<=(NEW.accepted_at AT TIME ZONE 'Europe/Moscow')::date THEN
   RAISE EXCEPTION 'unmaterialized past occurrences cannot change their binding';
 END IF;
 IF operation.operation_kind='REPLACE' THEN
   -- Only exact current one-off snapshots use this origin-neutral ledger for replacement.
   -- Recurring rows continue to require V19's template/rebind ledger.
   IF occurrence.one_off_lesson_id IS NULL OR occurrence.schedule_item_id IS NOT NULL
     OR NEW.lesson_id IS NULL OR lesson.one_off_lesson_id IS DISTINCT FROM occurrence.one_off_lesson_id
     OR lesson.schedule_item_id IS NOT NULL OR lesson.status::text<>'planned'
     OR lesson.date<>occurrence.occurrence_date OR lesson.generation<>occurrence.generation
     OR lesson.group_id<>occurrence.group_id OR lesson.subject_id<>occurrence.subject_id
     OR lesson.semester_id<>occurrence.semester_id OR lesson.lesson_type<>occurrence.lesson_type
     OR NEW.before_assignment_id IS DISTINCT FROM operation.source_assignment_id
     OR NEW.before_teacher_id IS DISTINCT FROM operation.source_teacher_id
     OR NEW.after_assignment_id IS DISTINCT FROM operation.target_assignment_id
     OR NEW.after_teacher_id IS DISTINCT FROM operation.target_teacher_id THEN
     RAISE EXCEPTION 'one-off replacement requires its exact planned source and target';
   END IF;
 ELSIF operation.operation_kind='CLOSE_UNASSIGN' THEN
   IF NEW.before_assignment_id IS DISTINCT FROM operation.source_assignment_id
     OR NEW.before_teacher_id IS DISTINCT FROM operation.source_teacher_id
     OR NEW.after_assignment_id IS NOT NULL OR NEW.after_teacher_id IS NOT NULL THEN
     RAISE EXCEPTION 'closure can only unassign its exact source';
   END IF;
 ELSE
   IF NEW.before_assignment_id IS NOT NULL OR NEW.before_teacher_id IS NOT NULL
     OR NEW.after_assignment_id IS DISTINCT FROM operation.target_assignment_id
     OR NEW.after_teacher_id IS DISTINCT FROM operation.target_teacher_id
     OR NOT EXISTS(SELECT 1 FROM schedule_assignment_binding_ledger prior
         JOIN schedule_assignment_replacement_operations closure ON closure.operation_id=prior.operation_id
          WHERE prior.occurrence_id=NEW.occurrence_id AND prior.after_assignment_id IS NULL
            AND closure.operation_kind='CLOSE_UNASSIGN' AND closure.state='COMMITTED') THEN
     RAISE EXCEPTION 'adoption requires retained closure provenance and its exact target';
   END IF;
 END IF;
 RETURN NEW;
END $$;

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
 IF (NEW.assignment_id IS NULL) <> (NEW.assigned_teacher_id IS NULL) THEN
   RAISE EXCEPTION 'assignment and teacher binding presence must agree';
 END IF;
 IF TG_OP='UPDATE' AND (NEW.assignment_id IS DISTINCT FROM OLD.assignment_id
      OR NEW.assigned_teacher_id IS DISTINCT FROM OLD.assigned_teacher_id) AND EXISTS (
   SELECT 1 FROM schedule_assignment_binding_ledger ledger
    JOIN schedule_assignment_replacement_operations operation ON operation.operation_id=ledger.operation_id
    WHERE ledger.occurrence_id=OLD.id
      AND ledger.operation_id=NULLIF(current_setting('rutcampustrack.assignment_binding_operation_id',true),'')::UUID
      AND operation.state='APPLIED'
      AND (operation.operation_kind IN ('CLOSE_UNASSIGN','ADOPT_UNASSIGNED')
        OR (operation.operation_kind='REPLACE' AND OLD.one_off_lesson_id IS NOT NULL
            AND OLD.schedule_item_id IS NULL AND ledger.accepted_at=NOW()))
      AND ledger.before_assignment_id IS NOT DISTINCT FROM OLD.assignment_id
      AND ledger.before_teacher_id IS NOT DISTINCT FROM OLD.assigned_teacher_id
      AND ledger.after_assignment_id IS NOT DISTINCT FROM NEW.assignment_id
      AND ledger.after_teacher_id IS NOT DISTINCT FROM NEW.assigned_teacher_id
      AND ledger.expected_occurrence_revision=OLD.revision AND ledger.after_occurrence_revision=NEW.revision
      AND (to_jsonb(NEW)-ARRAY['assignment_id','assigned_teacher_id','revision'])
        = (to_jsonb(OLD)-ARRAY['assignment_id','assigned_teacher_id','revision'])) THEN
   RETURN NEW;
 END IF;
 IF TG_OP='UPDATE' AND (NEW.assignment_id IS DISTINCT FROM OLD.assignment_id
     OR NEW.assigned_teacher_id IS DISTINCT FROM OLD.assigned_teacher_id)
     AND (NEW.assignment_id IS NULL OR OLD.assignment_id IS NULL) THEN
   RAISE EXCEPTION 'nullable binding change requires its exact lifecycle ledger';
 END IF;

    IF OLD.schedule_item_id IS NOT NULL AND OLD.current_lesson_id IS NOT NULL AND NEW.current_lesson_id IS DISTINCT FROM OLD.current_lesson_id
       AND NULLIF(current_setting('rutcampustrack.template_restore_operation_id', true),'') IS NOT NULL THEN
        SELECT * INTO template_row FROM lesson_template_restore_authorities
         WHERE operation_id = NULLIF(current_setting('rutcampustrack.template_restore_operation_id', true),'')::UUID
           AND occurrence_id = OLD.id FOR UPDATE;
        IF template_row.operation_id IS NULL OR OLD.current_lesson_id <> template_row.source_lesson_id
           OR OLD.revision <> template_row.expected_revision OR OLD.generation <> template_row.expected_generation
           OR NEW.current_lesson_id <> template_row.target_lesson_id
           OR NEW.schedule_item_id <> template_row.target_schedule_item_id
           OR NEW.assignment_id IS DISTINCT FROM template_row.target_assignment_id OR NEW.assigned_teacher_id IS DISTINCT FROM template_row.target_teacher_id
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
               OR OLD.assignment_id IS DISTINCT FROM one_off_restore_row.source_assignment_id
               OR OLD.assigned_teacher_id IS DISTINCT FROM one_off_restore_row.source_teacher_id
               OR OLD.revision <> one_off_restore_row.expected_occurrence_revision
               OR OLD.generation <> one_off_restore_row.expected_generation
               OR NEW.current_lesson_id IS DISTINCT FROM one_off_restore_row.target_lesson_id
               OR NEW.assignment_id IS DISTINCT FROM one_off_restore_row.target_assignment_id
               OR NEW.assigned_teacher_id IS DISTINCT FROM one_off_restore_row.target_teacher_id
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
           OR NEW.assignment_id IS DISTINCT FROM OLD.assignment_id
           OR NEW.group_id <> OLD.group_id
           OR NEW.subject_id <> OLD.subject_id
           OR NEW.semester_id <> OLD.semester_id
           OR NEW.assigned_teacher_id IS DISTINCT FROM OLD.assigned_teacher_id
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
               OR OLD.assignment_id IS DISTINCT FROM restore_row.source_assignment_id
               OR OLD.assigned_teacher_id IS DISTINCT FROM restore_row.source_teacher_id
               OR OLD.revision <> restore_row.expected_occurrence_revision
               OR OLD.generation <> restore_row.expected_generation
               OR NEW.current_lesson_id IS DISTINCT FROM restore_row.target_lesson_id
               OR NEW.schedule_item_id IS DISTINCT FROM restore_row.target_schedule_item_id
               OR NEW.assignment_id IS DISTINCT FROM restore_row.target_assignment_id
               OR NEW.assigned_teacher_id IS DISTINCT FROM restore_row.target_teacher_id
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
           OR OLD.assignment_id IS DISTINCT FROM operation_row.source_assignment_id
           OR NEW.assignment_id IS DISTINCT FROM operation_row.target_assignment_id
           OR OLD.assigned_teacher_id IS DISTINCT FROM operation_row.source_teacher_id
           OR NEW.assigned_teacher_id IS DISTINCT FROM operation_row.target_teacher_id
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


CREATE OR REPLACE FUNCTION validate_lesson_physical_snapshot()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
DECLARE
    occurrence_record lesson_occurrences%ROWTYPE;
    operation_id_value UUID;
    operation_row schedule_assignment_replacement_operations%ROWTYPE;
    ledger_row schedule_assignment_rebind_ledger%ROWTYPE;
    identity_changed BOOLEAN;
BEGIN
 IF (NEW.assignment_id IS NULL) <> (NEW.assigned_teacher_id IS NULL) THEN
   RAISE EXCEPTION 'assignment and teacher binding presence must agree';
 END IF;
 IF TG_OP='UPDATE' AND (NEW.assignment_id IS DISTINCT FROM OLD.assignment_id
      OR NEW.assigned_teacher_id IS DISTINCT FROM OLD.assigned_teacher_id) AND EXISTS (
   SELECT 1 FROM schedule_assignment_binding_ledger ledger
    JOIN schedule_assignment_replacement_operations operation ON operation.operation_id=ledger.operation_id
    WHERE ledger.lesson_id=OLD.id AND ledger.occurrence_id=OLD.occurrence_id
      AND ledger.operation_id=NULLIF(current_setting('rutcampustrack.assignment_binding_operation_id',true),'')::UUID
      AND operation.state='APPLIED'
      AND (operation.operation_kind IN ('CLOSE_UNASSIGN','ADOPT_UNASSIGNED')
        OR (operation.operation_kind='REPLACE' AND OLD.one_off_lesson_id IS NOT NULL
            AND OLD.schedule_item_id IS NULL AND ledger.accepted_at=NOW()))
      AND ledger.before_assignment_id IS NOT DISTINCT FROM OLD.assignment_id
      AND ledger.before_teacher_id IS NOT DISTINCT FROM OLD.assigned_teacher_id
      AND ledger.after_assignment_id IS NOT DISTINCT FROM NEW.assignment_id
      AND ledger.after_teacher_id IS NOT DISTINCT FROM NEW.assigned_teacher_id
      AND ledger.expected_lesson_revision=OLD.revision AND ledger.after_lesson_revision=NEW.revision
      AND (to_jsonb(NEW)-ARRAY['assignment_id','assigned_teacher_id','revision'])
        = (to_jsonb(OLD)-ARRAY['assignment_id','assigned_teacher_id','revision'])) THEN
   RETURN NEW;
 END IF;
 IF TG_OP='UPDATE' AND (NEW.assignment_id IS DISTINCT FROM OLD.assignment_id
     OR NEW.assigned_teacher_id IS DISTINCT FROM OLD.assigned_teacher_id)
     AND (NEW.assignment_id IS NULL OR OLD.assignment_id IS NULL) THEN
   RAISE EXCEPTION 'nullable binding change requires its exact lifecycle ledger';
 END IF;
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
           OR OLD.assignment_id IS DISTINCT FROM operation_row.source_assignment_id
           OR NEW.assignment_id IS DISTINCT FROM operation_row.target_assignment_id
           OR OLD.assigned_teacher_id IS DISTINCT FROM operation_row.source_teacher_id
           OR NEW.assigned_teacher_id IS DISTINCT FROM operation_row.target_teacher_id
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
            OR NEW.room_snapshot IS DISTINCT FROM OLD.room_snapshot AND NOT exact_lesson_room_change(OLD,NEW)
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
