ALTER TABLE schedule_assignment_replacement_operations
 ADD COLUMN operation_kind VARCHAR(24) NOT NULL DEFAULT 'REPLACE',
 ADD COLUMN requested_cutoff DATE,
 ALTER COLUMN source_assignment_id DROP NOT NULL, ALTER COLUMN target_assignment_id DROP NOT NULL,
 ALTER COLUMN source_teacher_id DROP NOT NULL, ALTER COLUMN target_teacher_id DROP NOT NULL,
 DROP CONSTRAINT schedule_assignment_replacement_source_chk,
 DROP CONSTRAINT schedule_assignment_replacement_target_chk,
 DROP CONSTRAINT schedule_assignment_replacement_teachers_chk,
 DROP CONSTRAINT schedule_assignment_replacement_pair_chk,
 DROP CONSTRAINT schedule_assignment_replacement_interval_chk;

ALTER TABLE schedule_assignment_replacement_operations ADD CONSTRAINT schedule_assignment_lifecycle_tuple_chk CHECK (
 (operation_kind='REPLACE' AND source_assignment_id IS NOT NULL AND target_assignment_id IS NOT NULL
  AND source_teacher_id IS NOT NULL AND target_teacher_id IS NOT NULL
  AND source_assignment_id>0 AND target_assignment_id>0 AND source_assignment_id<>target_assignment_id
  AND source_teacher_id>0 AND target_teacher_id>0 AND source_teacher_id<>target_teacher_id
  AND source_valid_from<=effective_from AND effective_from<valid_until_exclusive AND requested_cutoff IS NULL)
 OR (operation_kind='CLOSE_UNASSIGN' AND source_assignment_id IS NOT NULL AND source_teacher_id IS NOT NULL
  AND source_assignment_id>0 AND source_teacher_id>0 AND target_assignment_id IS NULL AND target_teacher_id IS NULL
  AND requested_cutoff IS NOT NULL AND source_valid_from<valid_until_exclusive
  AND effective_from=GREATEST(source_valid_from,requested_cutoff) AND effective_from<=valid_until_exclusive)
 OR (operation_kind='ADOPT_UNASSIGNED' AND source_assignment_id IS NULL AND source_teacher_id IS NULL
  AND target_assignment_id IS NOT NULL AND target_teacher_id IS NOT NULL
  AND target_assignment_id>0 AND target_teacher_id>0 AND source_valid_from=effective_from
  AND effective_from<valid_until_exclusive AND requested_cutoff IS NOT NULL AND requested_cutoff=effective_from)
);

ALTER TABLE lesson_occurrences ALTER COLUMN assignment_id DROP NOT NULL, ALTER COLUMN assigned_teacher_id DROP NOT NULL,
 ADD CONSTRAINT lesson_occurrences_binding_pair_chk CHECK ((assignment_id IS NULL)=(assigned_teacher_id IS NULL));
ALTER TABLE lessons ALTER COLUMN assignment_id DROP NOT NULL, ALTER COLUMN assigned_teacher_id DROP NOT NULL,
 ADD CONSTRAINT lessons_binding_pair_chk CHECK ((assignment_id IS NULL)=(assigned_teacher_id IS NULL));

CREATE TABLE schedule_assignment_binding_ledger (
 operation_id UUID NOT NULL REFERENCES schedule_assignment_replacement_operations(operation_id) ON DELETE RESTRICT,
 semester_id BIGINT NOT NULL CHECK(semester_id>0),
 occurrence_id BIGINT NOT NULL REFERENCES lesson_occurrences(id) ON DELETE RESTRICT,
 lesson_id BIGINT REFERENCES lessons(id) ON DELETE RESTRICT,
 occurrence_date DATE NOT NULL,
 expected_occurrence_revision BIGINT NOT NULL CHECK(expected_occurrence_revision>0),
 after_occurrence_revision BIGINT NOT NULL,
 expected_lesson_revision BIGINT,
 after_lesson_revision BIGINT,
 before_assignment_id BIGINT, before_teacher_id BIGINT,
 after_assignment_id BIGINT, after_teacher_id BIGINT,
 accepted_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
 PRIMARY KEY(operation_id,occurrence_id),
 CHECK(after_occurrence_revision=expected_occurrence_revision+1),
 CHECK((lesson_id IS NULL AND expected_lesson_revision IS NULL AND after_lesson_revision IS NULL)
    OR (lesson_id IS NOT NULL AND expected_lesson_revision IS NOT NULL AND after_lesson_revision IS NOT NULL
        AND expected_lesson_revision>0 AND after_lesson_revision=expected_lesson_revision+1)),
 CHECK((before_assignment_id IS NULL)=(before_teacher_id IS NULL)),
 CHECK((after_assignment_id IS NULL)=(after_teacher_id IS NULL))
);

CREATE TABLE schedule_assignment_template_close_ledger (
 operation_id UUID NOT NULL REFERENCES schedule_assignment_replacement_operations(operation_id) ON DELETE RESTRICT,
 semester_id BIGINT NOT NULL CHECK(semester_id>0),
 schedule_item_id BIGINT NOT NULL REFERENCES schedule_items(id) ON DELETE RESTRICT,
 expected_revision BIGINT NOT NULL CHECK(expected_revision>0),
 after_revision BIGINT NOT NULL CHECK(after_revision=expected_revision+1),
 accepted_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
 PRIMARY KEY(operation_id,schedule_item_id)
);
CREATE FUNCTION validate_assignment_template_close_ledger() RETURNS TRIGGER LANGUAGE plpgsql AS $$
DECLARE operation schedule_assignment_replacement_operations%ROWTYPE;
        item schedule_items%ROWTYPE;
BEGIN
 IF TG_OP='DELETE' AND schedule_semester_delete_authorized(OLD.semester_id) THEN RETURN OLD; END IF;
 IF TG_OP<>'INSERT' THEN RAISE EXCEPTION 'assignment template closure history is append-only'; END IF;
 SELECT * INTO operation FROM schedule_assignment_replacement_operations WHERE operation_id=NEW.operation_id FOR UPDATE;
 SELECT * INTO item FROM schedule_items WHERE id=NEW.schedule_item_id FOR UPDATE;
 IF operation.operation_id IS NULL OR item.id IS NULL OR operation.operation_kind<>'CLOSE_UNASSIGN'
    OR operation.state<>'PREPARED' OR operation.semester_id<>NEW.semester_id
    OR NEW.accepted_at IS DISTINCT FROM NOW()
    OR operation.operation_id IS DISTINCT FROM NULLIF(current_setting('rutcampustrack.schedule_assignment_rebind_operation_id',true),'')::UUID
    OR item.assignment_id<>operation.source_assignment_id OR item.group_id<>operation.group_id
    OR item.subject_id<>operation.subject_id OR item.semester_id<>operation.semester_id
    OR NOT item.is_active OR item.lifecycle_revision<>NEW.expected_revision THEN
   RAISE EXCEPTION 'template close history requires its exact prepared source operation';
 END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER assignment_template_close_ledger_guard BEFORE INSERT OR UPDATE OR DELETE
 ON schedule_assignment_template_close_ledger FOR EACH ROW EXECUTE FUNCTION validate_assignment_template_close_ledger();

CREATE FUNCTION guard_assignment_template_close() RETURNS TRIGGER LANGUAGE plpgsql AS $$
DECLARE operation_id_value UUID;
BEGIN
 operation_id_value:=NULLIF(current_setting('rutcampustrack.schedule_assignment_rebind_operation_id',true),'')::UUID;
 IF operation_id_value IS NOT NULL AND EXISTS(SELECT 1 FROM schedule_assignment_replacement_operations operation
       WHERE operation.operation_id=operation_id_value AND operation.operation_kind='CLOSE_UNASSIGN') THEN
   IF NOT EXISTS(SELECT 1 FROM schedule_assignment_template_close_ledger ledger
      JOIN schedule_assignment_replacement_operations operation ON operation.operation_id=ledger.operation_id
       WHERE ledger.operation_id=operation_id_value AND ledger.schedule_item_id=OLD.id
         AND operation.state='PREPARED' AND OLD.assignment_id=operation.source_assignment_id
         AND OLD.lifecycle_revision=ledger.expected_revision AND NEW.lifecycle_revision=ledger.after_revision
         AND OLD.is_active AND NOT NEW.is_active AND NEW.deactivated_at=ledger.accepted_at
         AND (to_jsonb(NEW)-ARRAY['is_active','lifecycle_revision','deactivated_at'])=
             (to_jsonb(OLD)-ARRAY['is_active','lifecycle_revision','deactivated_at'])) THEN
      RAISE EXCEPTION 'source template close requires its exact immutable closure receipt';
   END IF;
 END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER assignment_template_close_guard BEFORE UPDATE ON schedule_items
 FOR EACH ROW EXECUTE FUNCTION guard_assignment_template_close();

CREATE FUNCTION require_completed_assignment_template_close() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
 IF NOT EXISTS(SELECT 1 FROM schedule_items item WHERE item.id=NEW.schedule_item_id
      AND NOT item.is_active AND item.lifecycle_revision=NEW.after_revision AND item.deactivated_at=NEW.accepted_at) THEN
   RAISE EXCEPTION 'assignment template close receipt must be consumed by its exact update';
 END IF;
 RETURN NULL;
END $$;
CREATE CONSTRAINT TRIGGER assignment_template_close_complete AFTER INSERT ON schedule_assignment_template_close_ledger
 DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION require_completed_assignment_template_close();

CREATE FUNCTION validate_assignment_binding_ledger() RETURNS TRIGGER LANGUAGE plpgsql AS $$
DECLARE operation schedule_assignment_replacement_operations%ROWTYPE;
        occurrence lesson_occurrences%ROWTYPE;
        lesson lessons%ROWTYPE;
BEGIN
 IF TG_OP='DELETE' AND schedule_semester_delete_authorized(OLD.semester_id) THEN RETURN OLD; END IF;
 IF TG_OP<>'INSERT' THEN RAISE EXCEPTION 'assignment binding history is append-only'; END IF;
 SELECT * INTO operation FROM schedule_assignment_replacement_operations WHERE operation_id=NEW.operation_id FOR UPDATE;
 IF NOT FOUND OR operation.state<>'APPLIED' OR operation.semester_id<>NEW.semester_id
    OR NEW.accepted_at IS DISTINCT FROM NOW() OR operation.operation_kind NOT IN ('CLOSE_UNASSIGN','ADOPT_UNASSIGNED')
    OR NULLIF(current_setting('rutcampustrack.assignment_binding_operation_id',true),'')::UUID IS DISTINCT FROM NEW.operation_id THEN
   RAISE EXCEPTION 'binding history requires its exact applied operation';
 END IF;
 SELECT * INTO occurrence FROM lesson_occurrences WHERE id=NEW.occurrence_id FOR UPDATE;
 IF NOT FOUND OR occurrence.revision<>NEW.expected_occurrence_revision
   OR occurrence.current_lesson_id IS DISTINCT FROM NEW.lesson_id OR occurrence.occurrence_date<>NEW.occurrence_date
   OR occurrence.assignment_id IS DISTINCT FROM NEW.before_assignment_id
   OR occurrence.assigned_teacher_id IS DISTINCT FROM NEW.before_teacher_id
   OR occurrence.group_id<>operation.group_id OR occurrence.subject_id<>operation.subject_id
   OR occurrence.semester_id<>operation.semester_id OR occurrence.lesson_type<>operation.lesson_type
   OR occurrence.occurrence_date<operation.requested_cutoff OR occurrence.occurrence_date>=operation.valid_until_exclusive THEN
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
 IF operation.operation_kind='CLOSE_UNASSIGN' THEN
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
CREATE TRIGGER assignment_binding_ledger_guard BEFORE INSERT OR UPDATE OR DELETE
 ON schedule_assignment_binding_ledger FOR EACH ROW EXECUTE FUNCTION validate_assignment_binding_ledger();

CREATE FUNCTION require_completed_assignment_binding() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
 IF NOT EXISTS(SELECT 1 FROM lesson_occurrences occurrence WHERE occurrence.id=NEW.occurrence_id
      AND occurrence.revision=NEW.after_occurrence_revision AND occurrence.current_lesson_id IS NOT DISTINCT FROM NEW.lesson_id
      AND occurrence.assignment_id IS NOT DISTINCT FROM NEW.after_assignment_id
      AND occurrence.assigned_teacher_id IS NOT DISTINCT FROM NEW.after_teacher_id)
    OR NEW.lesson_id IS NOT NULL AND NOT EXISTS(SELECT 1 FROM lessons lesson WHERE lesson.id=NEW.lesson_id
      AND lesson.occurrence_id=NEW.occurrence_id AND lesson.revision=NEW.after_lesson_revision
      AND lesson.assignment_id IS NOT DISTINCT FROM NEW.after_assignment_id
      AND lesson.assigned_teacher_id IS NOT DISTINCT FROM NEW.after_teacher_id) THEN
   RAISE EXCEPTION 'assignment binding receipt must be consumed by its exact current-row updates';
 END IF;
 RETURN NULL;
END $$;
CREATE CONSTRAINT TRIGGER assignment_binding_complete AFTER INSERT ON schedule_assignment_binding_ledger
 DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION require_completed_assignment_binding();

CREATE FUNCTION protect_schedule_lifecycle_discriminator() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
 IF NEW.operation_kind IS DISTINCT FROM OLD.operation_kind OR NEW.requested_cutoff IS DISTINCT FROM OLD.requested_cutoff THEN
   RAISE EXCEPTION 'schedule assignment lifecycle discriminator is immutable';
 END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER schedule_lifecycle_discriminator_guard BEFORE UPDATE ON schedule_assignment_replacement_operations
 FOR EACH ROW EXECUTE FUNCTION protect_schedule_lifecycle_discriminator();

CREATE OR REPLACE FUNCTION protect_schedule_assignment_fence() RETURNS TRIGGER LANGUAGE plpgsql AS $$
DECLARE operation schedule_assignment_replacement_operations%ROWTYPE;
BEGIN
 IF TG_OP='DELETE' THEN RAISE EXCEPTION 'assignment fences are retained'; END IF;
 IF NEW.assignment_id IS DISTINCT FROM OLD.assignment_id OR NEW.group_id IS DISTINCT FROM OLD.group_id
   OR NEW.subject_id IS DISTINCT FROM OLD.subject_id OR NEW.semester_id IS DISTINCT FROM OLD.semester_id
   OR NEW.assigned_teacher_id IS DISTINCT FROM OLD.assigned_teacher_id OR NEW.lesson_type IS DISTINCT FROM OLD.lesson_type
   OR NEW.valid_from IS DISTINCT FROM OLD.valid_from THEN RAISE EXCEPTION 'assignment fence provenance is immutable'; END IF;
 IF NEW.revision<OLD.revision OR NEW.cap_until_exclusive>OLD.cap_until_exclusive
   OR NEW.creation_cap_until_exclusive>OLD.creation_cap_until_exclusive THEN RAISE EXCEPTION 'assignment fence cannot widen'; END IF;
 IF NEW.cap_until_exclusive<OLD.cap_until_exclusive AND (
   EXISTS(SELECT 1 FROM lessons WHERE assignment_id=OLD.assignment_id AND date>=NEW.cap_until_exclusive)
   OR EXISTS(SELECT 1 FROM lesson_occurrences WHERE assignment_id=OLD.assignment_id AND occurrence_date>=NEW.cap_until_exclusive)) THEN
   RAISE EXCEPTION 'assignment retention cap cannot exclude retained rows';
 END IF;
 IF NEW.creation_cap_until_exclusive<OLD.creation_cap_until_exclusive THEN
   SELECT * INTO operation FROM schedule_assignment_replacement_operations
    WHERE operation_id=NULLIF(current_setting('rutcampustrack.schedule_assignment_rebind_operation_id',true),'')::UUID FOR UPDATE;
   IF NOT FOUND OR operation.operation_kind NOT IN ('REPLACE','CLOSE_UNASSIGN') OR operation.state<>'PREPARED'
     OR operation.source_assignment_id IS DISTINCT FROM OLD.assignment_id OR NEW.creation_cap_until_exclusive<>operation.effective_from
     OR operation.group_id<>OLD.group_id OR operation.subject_id<>OLD.subject_id OR operation.semester_id<>OLD.semester_id
     OR operation.source_teacher_id<>OLD.assigned_teacher_id OR operation.lesson_type<>OLD.lesson_type THEN
     RAISE EXCEPTION 'creation cap requires exact prepared source authority';
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
      AND operation.state='APPLIED' AND operation.operation_kind IN ('CLOSE_UNASSIGN','ADOPT_UNASSIGNED')
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
      AND operation.state='APPLIED' AND operation.operation_kind IN ('CLOSE_UNASSIGN','ADOPT_UNASSIGNED')
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
CREATE FUNCTION assignment_unassigned_origin(occurrence_value BIGINT) RETURNS BIGINT LANGUAGE sql STABLE AS $$
 SELECT closure.source_assignment_id
 FROM lesson_occurrences occurrence JOIN schedule_assignment_binding_ledger ledger ON ledger.occurrence_id=occurrence.id
 JOIN schedule_assignment_replacement_operations closure ON closure.operation_id=ledger.operation_id
 WHERE occurrence.id=occurrence_value AND occurrence.assignment_id IS NULL AND occurrence.assigned_teacher_id IS NULL
 AND ledger.after_assignment_id IS NULL AND ledger.after_teacher_id IS NULL
 AND closure.operation_kind='CLOSE_UNASSIGN' AND closure.state='COMMITTED'
 AND closure.group_id=occurrence.group_id AND closure.subject_id=occurrence.subject_id
 AND closure.semester_id=occurrence.semester_id AND closure.lesson_type=occurrence.lesson_type
 ORDER BY ledger.accepted_at DESC,ledger.operation_id LIMIT 1
$$;

CREATE OR REPLACE FUNCTION validate_lesson_transfer_authority_insert()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
DECLARE
    configured_operation UUID;
    operation_row lesson_transfer_operations%ROWTYPE;
    occurrence_row lesson_occurrences%ROWTYPE;
    source_row lessons%ROWTYPE;
    fence_row schedule_assignment_fences%ROWTYPE;
BEGIN
    configured_operation := NULLIF(current_setting('rutcampustrack.lesson_transfer_operation_id', true), '')::UUID;
    IF configured_operation IS NULL OR configured_operation <> NEW.operation_id THEN
        RAISE EXCEPTION 'lesson transfer requires its exact transaction operation id';
    END IF;
    SELECT * INTO operation_row FROM lesson_transfer_operations
     WHERE operation_id = NEW.operation_id AND occurrence_id = NEW.occurrence_id FOR UPDATE;
    SELECT * INTO occurrence_row FROM lesson_occurrences WHERE id = NEW.occurrence_id FOR UPDATE;
    SELECT * INTO source_row FROM lessons
     WHERE id = NEW.source_lesson_id AND occurrence_id = NEW.occurrence_id FOR UPDATE;
    IF operation_row.operation_id IS NULL OR occurrence_row.id IS NULL OR source_row.id IS NULL
       OR operation_row.operation_hash IS DISTINCT FROM NEW.operation_hash
       OR operation_row.state <> 'PENDING'
       OR operation_row.actor_id <> NEW.actor_id
       OR operation_row.source_lesson_id <> NEW.source_lesson_id
       OR operation_row.target_lesson_id <> NEW.target_lesson_id
       OR operation_row.expected_occurrence_revision <> NEW.expected_occurrence_revision
       OR operation_row.source_generation <> NEW.expected_generation
       OR occurrence_row.current_lesson_id <> NEW.source_lesson_id
       OR occurrence_row.revision <> NEW.expected_occurrence_revision
       OR occurrence_row.generation <> NEW.expected_generation
       OR occurrence_row.occurrence_date <> source_row.date
       OR source_row.status::text <> 'planned'
       OR source_row.revision <> NEW.expected_lesson_revision
       OR source_row.generation <> NEW.expected_generation
       OR source_row.id = NEW.target_lesson_id
       OR (operation_row.template_operation_id IS NULL AND NEW.target_date <= CURRENT_DATE)
       OR (operation_row.template_operation_id IS NOT NULL AND (
              NEW.target_date <> source_row.date OR NEW.target_lesson_number <> source_row.lesson_number
              OR NEW.target_start_time <> source_row.start_time OR NEW.target_end_time <> source_row.end_time
              OR NEW.target_room IS NOT DISTINCT FROM source_row.room_snapshot
              OR (source_row.date + source_row.start_time) AT TIME ZONE 'Europe/Moscow' <= NEW.created_at)) THEN
        RAISE EXCEPTION 'lesson transfer source is not the exact future planned generation';
    END IF;
    IF occurrence_row.assignment_id IS NULL AND occurrence_row.assigned_teacher_id IS NULL THEN
        SELECT * INTO fence_row FROM schedule_assignment_fences
          WHERE assignment_id=assignment_unassigned_origin(occurrence_row.id) FOR UPDATE;
        IF NOT FOUND OR NEW.target_date<fence_row.valid_from OR NEW.target_date>=fence_row.cap_until_exclusive
          OR fence_row.group_id<>occurrence_row.group_id OR fence_row.subject_id<>occurrence_row.subject_id
          OR fence_row.semester_id<>occurrence_row.semester_id OR fence_row.lesson_type<>occurrence_row.lesson_type THEN
          RAISE EXCEPTION 'unassigned transfer must retain its proved original scope';
        END IF;
        RETURN NEW;
    END IF;
    SELECT * INTO fence_row FROM schedule_assignment_fences
     WHERE assignment_id = occurrence_row.assignment_id FOR UPDATE;
    IF fence_row.assignment_id IS NULL OR NEW.target_date < fence_row.valid_from
       OR NEW.target_date >= fence_row.creation_cap_until_exclusive
       OR fence_row.group_id <> occurrence_row.group_id
       OR fence_row.subject_id <> occurrence_row.subject_id
       OR fence_row.semester_id <> occurrence_row.semester_id
       OR fence_row.assigned_teacher_id IS DISTINCT FROM occurrence_row.assigned_teacher_id
       OR fence_row.lesson_type <> occurrence_row.lesson_type THEN
        RAISE EXCEPTION 'lesson transfer target is outside the current assignment and semester scope';
    END IF;
    RETURN NEW;
END
$$;

CREATE OR REPLACE FUNCTION validate_lesson_insert_fence()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
DECLARE
    fence_record schedule_assignment_fences%ROWTYPE;
BEGIN
 IF NEW.assignment_id IS NULL AND NEW.assigned_teacher_id IS NULL AND EXISTS (
   SELECT 1 FROM lesson_transfer_authorities authority JOIN lesson_transfer_operations operation
     ON operation.operation_id=authority.operation_id
   JOIN lesson_occurrences occurrence ON occurrence.id=NEW.occurrence_id
   WHERE authority.operation_id=NULLIF(current_setting('rutcampustrack.lesson_transfer_operation_id',true),'')::UUID
     AND operation.state='PENDING' AND operation.target_lesson_id=NEW.id AND authority.target_lesson_id=NEW.id
     AND authority.occurrence_id=NEW.occurrence_id AND authority.operation_hash=operation.operation_hash
     AND assignment_unassigned_origin(NEW.occurrence_id) IS NOT NULL
     AND NEW.group_id=occurrence.group_id AND NEW.subject_id=occurrence.subject_id
     AND NEW.semester_id=occurrence.semester_id AND NEW.lesson_type=occurrence.lesson_type
     AND NEW.assignment_id IS NOT DISTINCT FROM occurrence.assignment_id
     AND NEW.assigned_teacher_id IS NOT DISTINCT FROM occurrence.assigned_teacher_id
     AND NEW.date=authority.target_date AND NEW.lesson_number=authority.target_lesson_number
     AND NEW.start_time=authority.target_start_time AND NEW.end_time=authority.target_end_time
     AND NEW.room_snapshot IS NOT DISTINCT FROM authority.target_room
     AND NEW.generation=authority.expected_generation+1 AND NEW.revision=1 AND NEW.status::text='planned') THEN
   RETURN NEW;
 END IF;
    IF NEW.day_of_week IS NULL OR NEW.week_type_snapshot IS NULL THEN
        RAISE EXCEPTION 'new physical lessons require immutable schedule snapshots';
    END IF;
    SELECT * INTO fence_record FROM schedule_assignment_fences
     WHERE assignment_id = NEW.assignment_id FOR UPDATE;
    IF NOT FOUND THEN RAISE EXCEPTION 'assignment fence % does not exist', NEW.assignment_id; END IF;
    IF NEW.assignment_id IS DISTINCT FROM fence_record.assignment_id
       OR NEW.group_id IS DISTINCT FROM fence_record.group_id
       OR NEW.subject_id IS DISTINCT FROM fence_record.subject_id
       OR NEW.semester_id IS DISTINCT FROM fence_record.semester_id
       OR NEW.assigned_teacher_id IS DISTINCT FROM fence_record.assigned_teacher_id
       OR NEW.lesson_type IS DISTINCT FROM fence_record.lesson_type
       OR NEW.date < fence_record.valid_from
       OR NEW.date >= fence_record.creation_cap_until_exclusive THEN
        RAISE EXCEPTION 'physical lesson identity snapshot violates its creation fence';
    END IF;
    RETURN NEW;
END
$$;

CREATE FUNCTION require_exact_replacement_kind() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
 IF NOT EXISTS(SELECT 1 FROM schedule_assignment_replacement_operations operation
  WHERE operation.operation_id=NEW.operation_id AND operation.operation_kind='REPLACE') THEN
  RAISE EXCEPTION 'replacement-only history requires a replacement operation';
 END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER replacement_template_kind_guard BEFORE INSERT ON schedule_assignment_replacement_templates
 FOR EACH ROW EXECUTE FUNCTION require_exact_replacement_kind();
CREATE TRIGGER replacement_rebind_kind_guard BEFORE INSERT ON schedule_assignment_rebind_ledger
 FOR EACH ROW EXECUTE FUNCTION require_exact_replacement_kind();

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
           OR NEW.assignment_id IS DISTINCT FROM template_row.target_assignment_id OR NEW.assigned_teacher_id IS DISTINCT FROM template_row.target_teacher_id
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
           OR NEW.assignment_id IS DISTINCT FROM one_off_restore_row.target_assignment_id
           OR NEW.assigned_teacher_id IS DISTINCT FROM one_off_restore_row.target_teacher_id
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
       OR NEW.assignment_id IS DISTINCT FROM restore_row.target_assignment_id
       OR NEW.assigned_teacher_id IS DISTINCT FROM restore_row.target_teacher_id
       OR NEW.status::text <> 'planned'
       OR NEW.date <> (SELECT occurrence_date FROM lesson_occurrences WHERE id = NEW.occurrence_id) THEN
        RAISE EXCEPTION 'new physical lesson differs from exact restore authority';
    END IF;
    RETURN NEW;
END
$$;


-- Physical origin differs from the binding only for an exact retained and adopted occurrence.
CREATE FUNCTION assignment_adopted_item_matches(occurrence_value BIGINT,item_value BIGINT,
 assignment_value BIGINT,teacher_value BIGINT) RETURNS BOOLEAN LANGUAGE sql STABLE AS $$
 SELECT EXISTS (
  SELECT 1 FROM lesson_occurrences occurrence JOIN schedule_items item ON item.id=occurrence.schedule_item_id
  JOIN schedule_assignment_binding_ledger adopted ON adopted.occurrence_id=occurrence.id
  JOIN schedule_assignment_replacement_operations adoption ON adoption.operation_id=adopted.operation_id
  JOIN schedule_assignment_binding_ledger closed ON closed.occurrence_id=occurrence.id
  JOIN schedule_assignment_replacement_operations closure ON closure.operation_id=closed.operation_id
  WHERE occurrence.id=occurrence_value AND item.id=item_value AND NOT item.is_active
  AND occurrence.assignment_id=assignment_value AND occurrence.assigned_teacher_id=teacher_value
  AND adoption.operation_kind='ADOPT_UNASSIGNED' AND adoption.state='COMMITTED'
  AND adoption.target_assignment_id=assignment_value AND adoption.target_teacher_id=teacher_value
  AND adopted.before_assignment_id IS NULL AND adopted.before_teacher_id IS NULL
  AND adopted.after_assignment_id=assignment_value AND adopted.after_teacher_id=teacher_value
  AND closure.operation_kind='CLOSE_UNASSIGN' AND closure.state='COMMITTED'
  AND closure.source_assignment_id=item.assignment_id AND closed.after_assignment_id IS NULL AND closed.after_teacher_id IS NULL
  AND closed.after_occurrence_revision<=adopted.expected_occurrence_revision
  AND closure.group_id=occurrence.group_id AND closure.subject_id=occurrence.subject_id
  AND closure.semester_id=occurrence.semester_id AND closure.lesson_type=occurrence.lesson_type
  AND adoption.group_id=occurrence.group_id AND adoption.subject_id=occurrence.subject_id
  AND adoption.semester_id=occurrence.semester_id AND adoption.lesson_type=occurrence.lesson_type
  AND item.group_id=occurrence.group_id AND item.subject_id=occurrence.subject_id AND item.semester_id=occurrence.semester_id
  AND adoption.effective_from<=occurrence.occurrence_date AND occurrence.occurrence_date<adoption.valid_until_exclusive)
$$;

CREATE OR REPLACE FUNCTION validate_schedule_assignment_rebind_ledger_insert()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
DECLARE
    operation_row schedule_assignment_replacement_operations%ROWTYPE;
    occurrence_row lesson_occurrences%ROWTYPE;
    lesson_row lessons%ROWTYPE;
BEGIN
    SELECT * INTO operation_row
      FROM schedule_assignment_replacement_operations
     WHERE operation_id = NEW.operation_id
     FOR UPDATE;
    IF NOT FOUND OR operation_row.state <> 'APPLIED'
       OR NEW.occurrence_date < operation_row.effective_from
       OR NEW.occurrence_date >= operation_row.valid_until_exclusive
       OR NEW.before_assignment_id <> operation_row.source_assignment_id
       OR NEW.after_assignment_id NOT IN (operation_row.source_assignment_id,
                                           operation_row.target_assignment_id)
       OR NEW.before_teacher_id <> operation_row.source_teacher_id
       OR NEW.after_teacher_id NOT IN (operation_row.source_teacher_id,
                                       operation_row.target_teacher_id)
       OR NEW.before_schedule_item_id <> NEW.source_schedule_item_id
       OR NEW.after_schedule_item_id NOT IN (NEW.source_schedule_item_id,
                                              NEW.target_schedule_item_id) THEN
        RAISE EXCEPTION 'replacement ledger entry does not match its operation';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM schedule_assignment_replacement_templates
                    WHERE operation_id = NEW.operation_id
                      AND source_schedule_item_id = NEW.source_schedule_item_id
                      AND target_schedule_item_id = NEW.target_schedule_item_id) THEN
        RAISE EXCEPTION 'replacement ledger entry has no exact template mapping';
    END IF;
    IF NOT EXISTS (
        SELECT 1
          FROM schedule_items source_item
          JOIN schedule_items target_item ON target_item.id = NEW.target_schedule_item_id
         WHERE source_item.id = NEW.source_schedule_item_id
           AND (source_item.assignment_id = operation_row.source_assignment_id
                OR assignment_adopted_item_matches(NEW.occurrence_id,source_item.id,operation_row.source_assignment_id,operation_row.source_teacher_id))
           AND source_item.group_id = operation_row.group_id
           AND source_item.subject_id = operation_row.subject_id
           AND source_item.semester_id = operation_row.semester_id
           AND target_item.assignment_id = operation_row.target_assignment_id
           AND target_item.group_id = operation_row.group_id
           AND target_item.subject_id = operation_row.subject_id
           AND target_item.semester_id = operation_row.semester_id
           AND target_item.day_of_week = source_item.day_of_week
           AND target_item.lesson_number = source_item.lesson_number
           AND target_item.start_time = source_item.start_time
           AND target_item.end_time = source_item.end_time
           AND target_item.week_type = source_item.week_type
           AND target_item.room IS NOT DISTINCT FROM source_item.room
    ) THEN
        RAISE EXCEPTION 'replacement template does not preserve its recurring schedule tuple';
    END IF;

    SELECT * INTO occurrence_row FROM lesson_occurrences
     WHERE id = NEW.occurrence_id FOR UPDATE;
    IF NOT FOUND OR occurrence_row.schedule_item_id <> NEW.source_schedule_item_id
       OR occurrence_row.assignment_id <> operation_row.source_assignment_id
       OR occurrence_row.assigned_teacher_id <> operation_row.source_teacher_id
       OR occurrence_row.group_id <> operation_row.group_id
       OR occurrence_row.subject_id <> operation_row.subject_id
       OR occurrence_row.semester_id <> operation_row.semester_id
       OR occurrence_row.lesson_type <> operation_row.lesson_type
       OR occurrence_row.occurrence_date <> NEW.occurrence_date
       OR occurrence_row.revision <> NEW.expected_occurrence_revision
       OR occurrence_row.current_lesson_id IS DISTINCT FROM NEW.lesson_id THEN
        RAISE EXCEPTION 'replacement ledger entry does not match current occurrence revision';
    END IF;

    IF NEW.result = 'MOVED' THEN
        IF NEW.lesson_id IS NULL OR NEW.expected_lesson_revision IS NULL
           OR NEW.after_schedule_item_id <> NEW.target_schedule_item_id
           OR NEW.after_assignment_id <> operation_row.target_assignment_id
           OR NEW.after_teacher_id <> operation_row.target_teacher_id
           OR NEW.after_occurrence_revision <> NEW.expected_occurrence_revision + 1
           OR NEW.after_lesson_revision <> NEW.expected_lesson_revision + 1 THEN
            RAISE EXCEPTION 'moved replacement ledger entry is incomplete';
        END IF;
        SELECT * INTO lesson_row FROM lessons WHERE id = NEW.lesson_id FOR UPDATE;
        IF NOT FOUND OR lesson_row.status::text <> 'planned'
           OR lesson_row.occurrence_id <> NEW.occurrence_id
           OR lesson_row.schedule_item_id <> NEW.source_schedule_item_id
           OR lesson_row.assignment_id <> operation_row.source_assignment_id
           OR lesson_row.assigned_teacher_id <> operation_row.source_teacher_id
           OR lesson_row.group_id <> operation_row.group_id
           OR lesson_row.subject_id <> operation_row.subject_id
           OR lesson_row.semester_id <> operation_row.semester_id
           OR lesson_row.lesson_type <> operation_row.lesson_type
           OR lesson_row.date <> NEW.occurrence_date
           OR lesson_row.revision <> NEW.expected_lesson_revision THEN
            RAISE EXCEPTION 'only the exact current planned lesson can be rebound';
        END IF;
    ELSE
        IF NEW.after_schedule_item_id <> NEW.source_schedule_item_id
           OR NEW.after_assignment_id <> operation_row.source_assignment_id
           OR NEW.after_teacher_id <> operation_row.source_teacher_id
           OR NEW.after_occurrence_revision <> NEW.expected_occurrence_revision
           OR NEW.after_lesson_revision IS DISTINCT FROM NEW.expected_lesson_revision THEN
            RAISE EXCEPTION 'skipped replacement rows must retain source snapshots';
        END IF;
        IF NEW.lesson_id IS NULL THEN
            IF NEW.result <> 'SKIPPED_NO_CURRENT_LESSON'
               OR NEW.expected_lesson_revision IS NOT NULL THEN
                RAISE EXCEPTION 'missing current lesson must have an explicit skip disposition';
            END IF;
        ELSE
            SELECT * INTO lesson_row FROM lessons WHERE id = NEW.lesson_id FOR UPDATE;
            IF NOT FOUND OR lesson_row.occurrence_id <> NEW.occurrence_id
               OR lesson_row.status::text = 'planned'
               OR lesson_row.revision <> NEW.expected_lesson_revision THEN
                RAISE EXCEPTION 'skipped replacement entry does not match held lesson';
            END IF;
            IF (lesson_row.status::text = 'cancelled' AND NEW.result <> 'SKIPPED_CANCELLED')
               OR (lesson_row.status::text = 'active' AND NEW.result <> 'SKIPPED_ACTIVE')
               OR (lesson_row.status::text = 'closed' AND NEW.result <> 'SKIPPED_CLOSED')
               OR (lesson_row.status::text NOT IN ('cancelled', 'active', 'closed')
                   AND NEW.result <> 'SKIPPED_ALREADY_STARTED') THEN
                RAISE EXCEPTION 'skip disposition does not match current lesson status';
            END IF;
        END IF;
    END IF;
    RETURN NEW;
END
$$;

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
    IF NOT FOUND OR (source_item_row.assignment_id <> occurrence_row.assignment_id
         AND NOT assignment_adopted_item_matches(NEW.occurrence_id,source_item_row.id,occurrence_row.assignment_id,occurrence_row.assigned_teacher_id))
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
        IF NOT FOUND OR (current_item_row.assignment_id <> operation_row.source_assignment_id
             AND NOT assignment_adopted_item_matches(NEW.occurrence_id,current_item_row.id,operation_row.source_assignment_id,operation_row.source_teacher_id))
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
       OR (current_item_row.assignment_id <> NEW.target_assignment_id
         AND NOT assignment_adopted_item_matches(NEW.occurrence_id,current_item_row.id,NEW.target_assignment_id,NEW.target_teacher_id))
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
