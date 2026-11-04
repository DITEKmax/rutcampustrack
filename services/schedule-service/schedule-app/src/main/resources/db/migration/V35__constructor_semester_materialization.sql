-- Physical semester extent and dated teacher permission are separate authorities.
-- A natural unassigned origin is not a fabricated CLOSE_UNASSIGN operation.
CREATE TABLE constructor_semester_materializations (
    operation_id UUID PRIMARY KEY,
    actor_id BIGINT NOT NULL CHECK(actor_id>0),
    request_key UUID NOT NULL,
    requested_item_id BIGINT NOT NULL CHECK(requested_item_id>0),
    group_id BIGINT NOT NULL CHECK(group_id>0),
    subject_id BIGINT NOT NULL CHECK(subject_id>0),
    semester_id BIGINT NOT NULL CHECK(semester_id>0),
    family_ids BIGINT[] NOT NULL CHECK(cardinality(family_ids)>0),
    semester_from DATE NOT NULL,
    semester_until_exclusive DATE NOT NULL CHECK(semester_until_exclusive>semester_from),
    state VARCHAR(32) NOT NULL CHECK(state IN('GENERATING','PENDING','COMPLETED','COVERAGE_UNAVAILABLE')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(actor_id,request_key)
);

CREATE TABLE constructor_generation_origins (
    occurrence_id BIGINT PRIMARY KEY CHECK(occurrence_id>0),
    lesson_id BIGINT NOT NULL UNIQUE CHECK(lesson_id>0),
    operation_id UUID NOT NULL REFERENCES constructor_semester_materializations(operation_id) ON DELETE RESTRICT,
    schedule_item_id BIGINT NOT NULL CHECK(schedule_item_id>0),
    origin_assignment_id BIGINT NOT NULL CHECK(origin_assignment_id>0),
    initial_assignment_id BIGINT,
    initial_teacher_id BIGINT,
    group_id BIGINT NOT NULL CHECK(group_id>0),
    subject_id BIGINT NOT NULL CHECK(subject_id>0),
    semester_id BIGINT NOT NULL CHECK(semester_id>0),
    lesson_type VARCHAR(16) NOT NULL CHECK(lesson_type IN('lecture','practice','lab')),
    occurrence_date DATE NOT NULL,
    lesson_number SMALLINT NOT NULL CHECK(lesson_number BETWEEN 1 AND 8),
    start_time TIME NOT NULL,
    end_time TIME NOT NULL CHECK(end_time>start_time),
    room_snapshot VARCHAR(64),
    week_type_snapshot week_type NOT NULL,
    snapshot_hash BYTEA NOT NULL CHECK(octet_length(snapshot_hash)=32),
    created_at TIMESTAMPTZ NOT NULL,
    CHECK((initial_assignment_id IS NULL)=(initial_teacher_id IS NULL))
);
CREATE INDEX constructor_origins_item_date ON constructor_generation_origins(schedule_item_id,occurrence_date);

CREATE TABLE constructor_materialization_work (
    operation_id UUID NOT NULL REFERENCES constructor_semester_materializations(operation_id) ON DELETE RESTRICT,
    batch_index INTEGER NOT NULL CHECK(batch_index>=0),
    command BYTEA NOT NULL,
    batch_hash BYTEA NOT NULL CHECK(octet_length(batch_hash)=32),
    state VARCHAR(32) NOT NULL DEFAULT 'PENDING' CHECK(state IN('PENDING','APPLIED','COVERAGE_UNAVAILABLE')),
    attempts INTEGER NOT NULL DEFAULT 0 CHECK(attempts>=0),
    lease_token UUID,
    lease_until TIMESTAMPTZ,
    next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY(operation_id,batch_index)
);
CREATE INDEX constructor_materialization_recovery ON constructor_materialization_work(next_attempt_at)
    WHERE state='PENDING';

CREATE FUNCTION guard_constructor_materialization_control() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP='DELETE' THEN RAISE EXCEPTION 'constructor materialization receipt is retained'; END IF;
    IF TG_TABLE_NAME='constructor_semester_materializations' THEN
        IF (to_jsonb(NEW)-'state') IS DISTINCT FROM (to_jsonb(OLD)-'state')
           OR OLD.state IN('COMPLETED','COVERAGE_UNAVAILABLE')
           OR NOT(NEW.state=OLD.state OR OLD.state='GENERATING' AND NEW.state IN('PENDING','COMPLETED')
               OR OLD.state='PENDING' AND NEW.state IN('COMPLETED','COVERAGE_UNAVAILABLE')) THEN
            RAISE EXCEPTION 'constructor materialization identity or transition changed';
        END IF;
    ELSE
        IF (to_jsonb(NEW)-ARRAY['state','attempts','lease_token','lease_until','next_attempt_at','updated_at'])
            IS DISTINCT FROM (to_jsonb(OLD)-ARRAY['state','attempts','lease_token','lease_until','next_attempt_at','updated_at'])
           OR OLD.state<>'PENDING' OR NEW.state NOT IN('PENDING','APPLIED','COVERAGE_UNAVAILABLE') THEN
            RAISE EXCEPTION 'constructor materialization batch identity or transition changed';
        END IF;
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER constructor_materialization_operation_guard BEFORE UPDATE OR DELETE ON constructor_semester_materializations
    FOR EACH ROW EXECUTE FUNCTION guard_constructor_materialization_control();
CREATE TRIGGER constructor_materialization_work_guard BEFORE UPDATE OR DELETE ON constructor_materialization_work
    FOR EACH ROW EXECUTE FUNCTION guard_constructor_materialization_control();

CREATE FUNCTION guard_constructor_generation_origin() RETURNS TRIGGER LANGUAGE plpgsql AS $$
DECLARE operation constructor_semester_materializations%ROWTYPE;
        item schedule_items%ROWTYPE;
        fence schedule_assignment_fences%ROWTYPE;
        assigned BOOLEAN;
BEGIN
    IF TG_OP<>'INSERT' THEN RAISE EXCEPTION 'constructor generation origin is immutable'; END IF;
    SELECT * INTO operation FROM constructor_semester_materializations WHERE operation_id=NEW.operation_id;
    SELECT * INTO item FROM schedule_items WHERE id=NEW.schedule_item_id;
    SELECT * INTO fence FROM schedule_assignment_fences WHERE assignment_id=NEW.origin_assignment_id;
    IF operation.operation_id IS NULL OR operation.state<>'GENERATING'
       OR operation.operation_id IS DISTINCT FROM NULLIF(current_setting('rutcampustrack.constructor_materialization_operation_id',TRUE),'')::uuid
       OR item.id IS NULL OR fence.assignment_id IS NULL OR NOT(item.id=ANY(operation.family_ids))
       OR item.assignment_id<>NEW.origin_assignment_id
       OR item.group_id<>NEW.group_id OR item.subject_id<>NEW.subject_id OR item.semester_id<>NEW.semester_id
       OR operation.group_id<>NEW.group_id OR operation.subject_id<>NEW.subject_id OR operation.semester_id<>NEW.semester_id
       OR fence.group_id<>NEW.group_id OR fence.subject_id<>NEW.subject_id OR fence.semester_id<>NEW.semester_id
       OR fence.lesson_type<>NEW.lesson_type
       OR NEW.occurrence_date<operation.semester_from OR NEW.occurrence_date>=operation.semester_until_exclusive
       OR extract(isodow FROM NEW.occurrence_date)::integer<>item.day_of_week
       OR NEW.week_type_snapshot='odd' AND extract(week FROM NEW.occurrence_date)::integer%2<>0
       OR NEW.week_type_snapshot='even' AND extract(week FROM NEW.occurrence_date)::integer%2<>1
       OR NEW.lesson_number<>item.lesson_number OR NEW.start_time<>item.start_time OR NEW.end_time<>item.end_time
       OR NEW.room_snapshot IS DISTINCT FROM item.room OR NEW.week_type_snapshot<>item.week_type THEN
        RAISE EXCEPTION 'constructor generation requires its exact semester origin';
    END IF;
    assigned:=NEW.occurrence_date>=fence.valid_from AND NEW.occurrence_date<fence.creation_cap_until_exclusive;
    IF assigned AND (NEW.initial_assignment_id IS DISTINCT FROM fence.assignment_id
            OR NEW.initial_teacher_id IS DISTINCT FROM fence.assigned_teacher_id)
       OR NOT assigned AND (NEW.initial_assignment_id IS NOT NULL OR NEW.initial_teacher_id IS NOT NULL) THEN
        RAISE EXCEPTION 'constructor effective binding is outside its dated authority';
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER constructor_generation_origin_guard BEFORE INSERT OR UPDATE OR DELETE ON constructor_generation_origins
    FOR EACH ROW EXECUTE FUNCTION guard_constructor_generation_origin();

CREATE FUNCTION constructor_occurrence_insert_authorized(value JSONB) RETURNS BOOLEAN LANGUAGE SQL STABLE AS $$
    SELECT EXISTS(SELECT 1 FROM constructor_generation_origins origin
        JOIN constructor_semester_materializations operation ON operation.operation_id=origin.operation_id
        WHERE origin.operation_id=NULLIF(current_setting('rutcampustrack.constructor_materialization_operation_id',TRUE),'')::uuid
          AND operation.state='GENERATING'
          AND origin.occurrence_id=(value->>'id')::bigint
          AND origin.schedule_item_id=(value->>'schedule_item_id')::bigint
          AND origin.group_id=(value->>'group_id')::bigint
          AND origin.subject_id=(value->>'subject_id')::bigint
          AND origin.semester_id=(value->>'semester_id')::bigint
          AND origin.lesson_type=value->>'lesson_type'
          AND origin.occurrence_date=(value->>'occurrence_date')::date
          AND origin.initial_assignment_id IS NOT DISTINCT FROM NULLIF(value->>'assignment_id','')::bigint
          AND origin.initial_teacher_id IS NOT DISTINCT FROM NULLIF(value->>'assigned_teacher_id','')::bigint
          AND (value->>'generation')::bigint=1 AND (value->>'revision')::bigint=1)
$$;

CREATE FUNCTION constructor_lesson_insert_authorized(value JSONB) RETURNS BOOLEAN LANGUAGE SQL STABLE AS $$
    SELECT EXISTS(SELECT 1 FROM constructor_generation_origins origin
        JOIN constructor_semester_materializations operation ON operation.operation_id=origin.operation_id
        WHERE origin.operation_id=NULLIF(current_setting('rutcampustrack.constructor_materialization_operation_id',TRUE),'')::uuid
          AND operation.state='GENERATING' AND origin.lesson_id=(value->>'id')::bigint
          AND origin.occurrence_id=(value->>'occurrence_id')::bigint
          AND origin.schedule_item_id=(value->>'schedule_item_id')::bigint
          AND origin.group_id=(value->>'group_id')::bigint AND origin.subject_id=(value->>'subject_id')::bigint
          AND origin.semester_id=(value->>'semester_id')::bigint AND origin.lesson_type=value->>'lesson_type'
          AND origin.occurrence_date=(value->>'date')::date
          AND origin.lesson_number=(value->>'lesson_number')::smallint
          AND origin.start_time=(value->>'start_time')::time AND origin.end_time=(value->>'end_time')::time
          AND origin.room_snapshot IS NOT DISTINCT FROM value->>'room_snapshot'
          AND origin.week_type_snapshot::text=value->>'week_type_snapshot'
          AND origin.initial_assignment_id IS NOT DISTINCT FROM NULLIF(value->>'assignment_id','')::bigint
          AND origin.initial_teacher_id IS NOT DISTINCT FROM NULLIF(value->>'assigned_teacher_id','')::bigint
          AND (value->>'generation')::bigint=1 AND (value->>'revision')::bigint=1)
$$;

-- Preserve both established functions verbatim, adding only a narrowly proven
-- initial semester-origin branch before the existing assignment fence checks.
DO $$ DECLARE definition TEXT; anchor INTEGER; BEGIN
    SELECT pg_get_functiondef('validate_recurring_occurrence_fence()'::regprocedure) INTO definition;
    anchor:=strpos(definition,'BEGIN');
    IF anchor=0 THEN RAISE EXCEPTION 'constructor occurrence admission anchor changed'; END IF;
    EXECUTE overlay(definition PLACING E'BEGIN\n IF TG_OP=''INSERT'' AND constructor_occurrence_insert_authorized(to_jsonb(NEW)) THEN RETURN NEW; END IF;\n' FROM anchor FOR 5);
    SELECT pg_get_functiondef('validate_lesson_insert_fence()'::regprocedure) INTO definition;
    anchor:=strpos(definition,'BEGIN');
    IF anchor=0 THEN RAISE EXCEPTION 'constructor physical admission anchor changed'; END IF;
    EXECUTE overlay(definition PLACING E'BEGIN\n IF TG_OP=''INSERT'' AND constructor_lesson_insert_authorized(to_jsonb(NEW)) THEN RETURN NEW; END IF;\n' FROM anchor FOR 5);
END $$;

ALTER FUNCTION assignment_unassigned_origin(BIGINT) RENAME TO assignment_unassigned_origin_legacy;
CREATE FUNCTION constructor_natural_unassigned_origin(occurrence_value BIGINT) RETURNS BIGINT LANGUAGE SQL STABLE AS $$
    SELECT origin.origin_assignment_id FROM constructor_generation_origins origin
    JOIN constructor_semester_materializations operation ON operation.operation_id=origin.operation_id
    JOIN lesson_occurrences occurrence ON occurrence.id=origin.occurrence_id
    WHERE occurrence.id=occurrence_value AND origin.initial_assignment_id IS NULL
      AND origin.initial_teacher_id IS NULL AND occurrence.assignment_id IS NULL
      AND occurrence.assigned_teacher_id IS NULL AND operation.state<>'GENERATING'
      AND occurrence.group_id=origin.group_id AND occurrence.subject_id=origin.subject_id
      AND occurrence.semester_id=origin.semester_id AND occurrence.lesson_type=origin.lesson_type
      AND occurrence.occurrence_date=origin.occurrence_date
$$;
CREATE FUNCTION assignment_unassigned_origin(occurrence_value BIGINT) RETURNS BIGINT LANGUAGE SQL STABLE AS $$
    SELECT COALESCE(assignment_unassigned_origin_legacy(occurrence_value),
        constructor_natural_unassigned_origin(occurrence_value))
$$;

CREATE FUNCTION constructor_natural_adoption_authorized(value JSONB) RETURNS BOOLEAN LANGUAGE SQL STABLE AS $$
    SELECT EXISTS(SELECT 1 FROM schedule_assignment_replacement_operations operation
        JOIN lesson_occurrences occurrence ON occurrence.id=(value->>'occurrence_id')::bigint
        LEFT JOIN lessons lesson ON lesson.id=occurrence.current_lesson_id
        WHERE operation.operation_id=(value->>'operation_id')::uuid
          AND operation.operation_id=NULLIF(current_setting('rutcampustrack.assignment_binding_operation_id',TRUE),'')::uuid
          AND operation.operation_kind='ADOPT_UNASSIGNED' AND operation.state='APPLIED'
          AND constructor_natural_unassigned_origin(occurrence.id) IS NOT NULL
          AND NOT EXISTS(SELECT 1 FROM schedule_assignment_binding_ledger earlier
              WHERE earlier.occurrence_id=occurrence.id AND earlier.after_assignment_id IS NOT NULL)
          AND occurrence.group_id=operation.group_id AND occurrence.subject_id=operation.subject_id
          AND occurrence.semester_id=operation.semester_id AND occurrence.lesson_type=operation.lesson_type
          AND occurrence.occurrence_date>=operation.requested_cutoff
          AND occurrence.occurrence_date<operation.valid_until_exclusive
          AND NULLIF(value->>'before_assignment_id','') IS NULL AND NULLIF(value->>'before_teacher_id','') IS NULL
          AND (value->>'after_assignment_id')::bigint=operation.target_assignment_id
          AND (value->>'after_teacher_id')::bigint=operation.target_teacher_id
          AND (value->>'expected_occurrence_revision')::bigint=occurrence.revision
          AND (value->>'after_occurrence_revision')::bigint=occurrence.revision+1
          AND NULLIF(value->>'lesson_id','')::bigint IS NOT DISTINCT FROM occurrence.current_lesson_id
          AND (lesson.id IS NULL OR lesson.assignment_id IS NULL AND lesson.assigned_teacher_id IS NULL
              AND (value->>'expected_lesson_revision')::bigint=lesson.revision
              AND (value->>'after_lesson_revision')::bigint=lesson.revision+1)
          AND (value->>'accepted_at')::timestamptz=NOW())
$$;
DO $$ DECLARE definition TEXT; anchor INTEGER; BEGIN
    SELECT pg_get_functiondef('validate_assignment_binding_ledger()'::regprocedure) INTO definition;
    anchor:=strpos(definition,'BEGIN');
    IF anchor=0 THEN RAISE EXCEPTION 'constructor retrospective adoption anchor changed'; END IF;
    EXECUTE overlay(definition PLACING E'BEGIN\n IF TG_OP=''INSERT'' AND constructor_natural_adoption_authorized(to_jsonb(NEW)) THEN RETURN NEW; END IF;\n' FROM anchor FOR 5);
END $$;
