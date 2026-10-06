-- An exact immutable receipt authorizes room/revision only; V28 identity guards stay in force.
CREATE TABLE lesson_room_change_ledger (
 operation_id UUID PRIMARY KEY,
 actor_id BIGINT NOT NULL CHECK(actor_id>0), request_key UUID NOT NULL,
 semester_id BIGINT NOT NULL CHECK(semester_id>0),
 lesson_id BIGINT NOT NULL REFERENCES lessons(id) ON DELETE RESTRICT,
 occurrence_id BIGINT NOT NULL REFERENCES lesson_occurrences(id) ON DELETE RESTRICT,
 expected_occurrence_revision BIGINT NOT NULL CHECK(expected_occurrence_revision>0),
 after_occurrence_revision BIGINT NOT NULL,
 expected_lesson_revision BIGINT NOT NULL CHECK(expected_lesson_revision>0),
 after_lesson_revision BIGINT NOT NULL,
 before_room VARCHAR(64), after_room VARCHAR(64),
 before_lesson JSONB NOT NULL, before_occurrence JSONB NOT NULL,
 accepted_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
 UNIQUE(actor_id,request_key),
 CHECK(after_room IS NULL OR (length(after_room)>0 AND after_room=btrim(after_room))),
 CHECK(after_occurrence_revision=expected_occurrence_revision+
       CASE WHEN before_room IS DISTINCT FROM after_room THEN 1 ELSE 0 END),
 CHECK(after_lesson_revision=expected_lesson_revision+
       CASE WHEN before_room IS DISTINCT FROM after_room THEN 1 ELSE 0 END)
);

CREATE FUNCTION validate_lesson_room_receipt() RETURNS TRIGGER LANGUAGE plpgsql AS $$
DECLARE lesson lessons%ROWTYPE; occurrence lesson_occurrences%ROWTYPE;
BEGIN
 IF TG_OP='DELETE' AND schedule_semester_delete_authorized(OLD.semester_id) THEN RETURN OLD; END IF;
 IF TG_OP<>'INSERT' THEN RAISE EXCEPTION 'lesson room receipts are append-only' USING ERRCODE='23514'; END IF;
 IF NULLIF(current_setting('rutcampustrack.lesson_room_operation_id',true),'')::UUID IS DISTINCT FROM NEW.operation_id
    OR NEW.accepted_at IS DISTINCT FROM NOW() THEN RAISE EXCEPTION 'room receipt requires exact transaction authority' USING ERRCODE='23514'; END IF;
 -- Same archive lock as every business mutation and barrier preparation.
 PERFORM pg_advisory_xact_lock(5452097,(NEW.semester_id%2147483647)::integer);
 IF EXISTS(SELECT 1 FROM schedule_semester_archive_barriers WHERE semester_id=NEW.semester_id
       AND participant_state<>'RELEASED') THEN RAISE EXCEPTION 'semester blocks room change' USING ERRCODE='23514'; END IF;
 SELECT * INTO occurrence FROM lesson_occurrences WHERE id=NEW.occurrence_id FOR UPDATE;
 SELECT * INTO lesson FROM lessons WHERE id=NEW.lesson_id FOR UPDATE;
 IF occurrence.id IS NULL OR lesson.id IS NULL OR lesson.occurrence_id<>occurrence.id
    OR occurrence.current_lesson_id IS DISTINCT FROM lesson.id OR occurrence.generation<>lesson.generation
    OR lesson.semester_id<>NEW.semester_id OR occurrence.semester_id<>NEW.semester_id
    OR to_jsonb(lesson) IS DISTINCT FROM NEW.before_lesson OR to_jsonb(occurrence) IS DISTINCT FROM NEW.before_occurrence
    OR lesson.revision<>NEW.expected_lesson_revision OR occurrence.revision<>NEW.expected_occurrence_revision
    OR lesson.room_snapshot IS DISTINCT FROM NEW.before_room OR lesson.status::text<>'planned'
    OR (lesson.date+lesson.start_time) AT TIME ZONE 'Europe/Moscow'<=NEW.accepted_at
    OR (to_jsonb(lesson)->'schedule_item_id') IS DISTINCT FROM (to_jsonb(occurrence)->'schedule_item_id')
    OR (to_jsonb(lesson)->'one_off_lesson_id') IS DISTINCT FROM (to_jsonb(occurrence)->'one_off_lesson_id')
    OR lesson.group_id<>occurrence.group_id OR lesson.subject_id<>occurrence.subject_id
    OR lesson.assignment_id IS DISTINCT FROM occurrence.assignment_id
    OR lesson.assigned_teacher_id IS DISTINCT FROM occurrence.assigned_teacher_id
    OR lesson.lesson_type<>occurrence.lesson_type THEN RAISE EXCEPTION 'room receipt does not match current upcoming lesson' USING ERRCODE='23514'; END IF;
 IF EXISTS(SELECT 1 FROM lesson_transfer_operations WHERE occurrence_id=occurrence.id AND state<>'COMPLETED')
    OR EXISTS(SELECT 1 FROM lesson_homework_bindings WHERE occurrence_id=occurrence.id
              AND (state='PENDING' OR pending_edit_operation_id IS NOT NULL)) THEN RAISE EXCEPTION 'room change conflicts with pending operation' USING ERRCODE='23514'; END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER lesson_room_receipt_guard BEFORE INSERT OR UPDATE OR DELETE ON lesson_room_change_ledger
 FOR EACH ROW EXECUTE FUNCTION validate_lesson_room_receipt();

CREATE FUNCTION exact_lesson_room_change(before_row lessons, after_row lessons) RETURNS BOOLEAN LANGUAGE sql STABLE AS $$
 SELECT EXISTS(SELECT 1 FROM lesson_room_change_ledger ledger
   WHERE ledger.operation_id=NULLIF(current_setting('rutcampustrack.lesson_room_operation_id',true),'')::UUID
     AND ledger.lesson_id=before_row.id AND ledger.occurrence_id=before_row.occurrence_id
     AND ledger.accepted_at=NOW() AND ledger.before_lesson=to_jsonb(before_row)
     AND ledger.before_room IS NOT DISTINCT FROM before_row.room_snapshot
     AND ledger.after_room IS NOT DISTINCT FROM after_row.room_snapshot
     AND ledger.expected_lesson_revision=before_row.revision AND ledger.after_lesson_revision=after_row.revision
     AND ledger.before_room IS DISTINCT FROM ledger.after_room
     AND (to_jsonb(before_row)-ARRAY['room_snapshot','revision'])=(to_jsonb(after_row)-ARRAY['room_snapshot','revision']))
$$;

-- A selected room authority cannot piggyback a lifecycle/binding/identity mutation.
CREATE FUNCTION guard_exact_room_mutation() RETURNS TRIGGER LANGUAGE plpgsql AS $$
DECLARE receipt lesson_room_change_ledger%ROWTYPE;
BEGIN
 IF NULLIF(current_setting('rutcampustrack.lesson_room_operation_id',true),'') IS NULL THEN RETURN NEW; END IF;
 SELECT * INTO receipt FROM lesson_room_change_ledger
  WHERE operation_id=NULLIF(current_setting('rutcampustrack.lesson_room_operation_id',true),'')::UUID;
 IF TG_TABLE_NAME='lessons' THEN
   IF NOT exact_lesson_room_change(OLD,NEW) THEN RAISE EXCEPTION 'room authority permits only its exact room update' USING ERRCODE='23514'; END IF;
 ELSE
   IF receipt.operation_id IS NULL OR receipt.accepted_at IS DISTINCT FROM NOW()
     OR receipt.occurrence_id<>OLD.id OR receipt.before_occurrence IS DISTINCT FROM to_jsonb(OLD)
     OR receipt.expected_occurrence_revision<>OLD.revision OR receipt.after_occurrence_revision<>NEW.revision
     OR receipt.before_room IS NOT DISTINCT FROM receipt.after_room
     OR (to_jsonb(OLD)-'revision') IS DISTINCT FROM (to_jsonb(NEW)-'revision') THEN
      RAISE EXCEPTION 'room authority permits only its exact occurrence revision' USING ERRCODE='23514';
   END IF;
 END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER lesson_exact_room_guard BEFORE UPDATE ON lessons FOR EACH ROW EXECUTE FUNCTION guard_exact_room_mutation();
CREATE TRIGGER occurrence_exact_room_guard BEFORE UPDATE ON lesson_occurrences FOR EACH ROW EXECUTE FUNCTION guard_exact_room_mutation();

CREATE FUNCTION require_completed_room_change() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
 IF NOT EXISTS(SELECT 1 FROM lessons lesson WHERE lesson.id=NEW.lesson_id
      AND to_jsonb(lesson)=NEW.before_lesson||jsonb_build_object('room_snapshot',NEW.after_room,'revision',NEW.after_lesson_revision))
    OR NOT EXISTS(SELECT 1 FROM lesson_occurrences occurrence WHERE occurrence.id=NEW.occurrence_id
      AND to_jsonb(occurrence)=NEW.before_occurrence||jsonb_build_object('revision',NEW.after_occurrence_revision)) THEN
    RAISE EXCEPTION 'room receipt must be consumed by its exact physical and occurrence updates' USING ERRCODE='23514';
 END IF;
 RETURN NULL;
END $$;
CREATE CONSTRAINT TRIGGER lesson_room_change_complete AFTER INSERT ON lesson_room_change_ledger
 DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION require_completed_room_change();

-- Below is V28 validate_lesson_physical_snapshot verbatim except its room immutability
-- predicate admits exact_lesson_room_change. All other V28 guards are retained.
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
