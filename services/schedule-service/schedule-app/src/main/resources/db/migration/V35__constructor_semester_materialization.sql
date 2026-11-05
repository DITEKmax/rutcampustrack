-- Physical semester extent and dated teacher permission are separate authorities.
-- A natural unassigned origin is not a fabricated CLOSE_UNASSIGN operation.
CREATE TABLE constructor_semester_materializations (
    operation_id UUID PRIMARY KEY,
    actor_id BIGINT,
    source_kind VARCHAR(24) NOT NULL DEFAULT 'USER' CHECK(source_kind IN('USER','SYSTEM_BACKFILL')),
    request_kind VARCHAR(16) NOT NULL DEFAULT 'CREATE' CHECK(request_kind IN('CREATE','BACKFILL')),
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
    CHECK((source_kind='USER' AND actor_id IS NOT NULL AND actor_id>0) OR (source_kind='SYSTEM_BACKFILL' AND actor_id IS NULL AND request_kind='BACKFILL'))
);
CREATE UNIQUE INDEX constructor_materialization_request_key ON constructor_semester_materializations
    (source_kind,COALESCE(actor_id,0),request_key);

CREATE TABLE constructor_system_materialization_runs (
    operation_id UUID PRIMARY KEY,
    group_id BIGINT NOT NULL CHECK(group_id>0),
    semester_id BIGINT NOT NULL CHECK(semester_id>0),
    item_ids BIGINT[] NOT NULL CHECK(cardinality(item_ids) BETWEEN 1 AND 128),
    scope_hash BYTEA NOT NULL CHECK(octet_length(scope_hash)=32),
    state VARCHAR(16) NOT NULL DEFAULT 'PENDING' CHECK(state IN('PENDING','COMPLETED')),
    attempts INTEGER NOT NULL DEFAULT 0 CHECK(attempts>=0),
    next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE FUNCTION guard_constructor_system_run() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP='DELETE' OR OLD.state<>'PENDING'
       OR (to_jsonb(NEW)-ARRAY['state','attempts','next_attempt_at','updated_at']) IS DISTINCT FROM
          (to_jsonb(OLD)-ARRAY['state','attempts','next_attempt_at','updated_at']) THEN
        RAISE EXCEPTION 'system materialization admission is immutable';
    END IF;
    RETURN NEW;
END $$;


CREATE TRIGGER constructor_system_run_guard BEFORE UPDATE OR DELETE ON constructor_system_materialization_runs
    FOR EACH ROW EXECUTE FUNCTION guard_constructor_system_run();

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
    lesson_ids BIGINT[] NOT NULL CHECK(cardinality(lesson_ids) BETWEEN 1 AND 128),
    lesson_count INTEGER NOT NULL CHECK(lesson_count=cardinality(lesson_ids)),
    coverage_unavailable_count INTEGER NOT NULL DEFAULT 0 CHECK(coverage_unavailable_count BETWEEN 0 AND lesson_count),
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
        IF (to_jsonb(NEW)-ARRAY['state','attempts','lease_token','lease_until','next_attempt_at','updated_at','coverage_unavailable_count'])
            IS DISTINCT FROM (to_jsonb(OLD)-ARRAY['state','attempts','lease_token','lease_until','next_attempt_at','updated_at','coverage_unavailable_count'])
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
    SELECT * INTO fence FROM schedule_assignment_fences WHERE assignment_id=NEW.origin_assignment_id FOR UPDATE;
    SELECT * INTO item FROM schedule_items WHERE id=NEW.schedule_item_id FOR UPDATE;
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
    IF EXISTS(SELECT 1 FROM lesson_occurrences existing WHERE existing.schedule_item_id=ANY(operation.family_ids)
        AND existing.occurrence_date=NEW.occurrence_date) OR EXISTS(SELECT 1 FROM lessons existing
        WHERE existing.schedule_item_id=ANY(operation.family_ids) AND existing.date=NEW.occurrence_date) THEN
        RAISE EXCEPTION 'constructor family date is already covered, including retained exceptions';
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
          AND value->>'status'=CASE
              WHEN origin.occurrence_date+origin.end_time+interval '5 minutes' <= origin.created_at AT TIME ZONE 'Europe/Moscow' THEN 'closed'
              WHEN origin.occurrence_date+origin.start_time <= origin.created_at AT TIME ZONE 'Europe/Moscow' THEN 'active'
              ELSE 'planned' END
          AND (value->>'generation')::bigint=1 AND (value->>'revision')::bigint=1)
$$;

CREATE FUNCTION constructor_initial_pointer_authorized(before_value JSONB,after_value JSONB)
RETURNS BOOLEAN LANGUAGE SQL STABLE AS $$
    SELECT before_value->>'current_lesson_id' IS NULL
       AND after_value->>'current_lesson_id' IS NOT NULL
       AND before_value->>'one_off_lesson_id' IS NULL
       AND (before_value-'current_lesson_id')=(after_value-'current_lesson_id')
       AND constructor_occurrence_insert_authorized(before_value)
       AND EXISTS(SELECT 1 FROM constructor_generation_origins origin
           JOIN constructor_semester_materializations operation ON operation.operation_id=origin.operation_id
           JOIN lessons lesson ON lesson.id=origin.lesson_id AND lesson.occurrence_id=origin.occurrence_id
           WHERE origin.operation_id=NULLIF(current_setting('rutcampustrack.constructor_materialization_operation_id',TRUE),'')::uuid
             AND operation.state='GENERATING'
             AND origin.occurrence_id=(before_value->>'id')::bigint
             AND origin.lesson_id=(after_value->>'current_lesson_id')::bigint
             AND lesson.one_off_lesson_id IS NULL
             AND constructor_lesson_insert_authorized(to_jsonb(lesson)))
$$;

-- Preserve the established functions verbatim, adding only a narrowly proven
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
    SELECT pg_get_functiondef('protect_occurrence_identity()'::regprocedure) INTO definition;
    anchor:=strpos(definition,'BEGIN');
    IF anchor=0 THEN RAISE EXCEPTION 'constructor initial pointer admission anchor changed'; END IF;
    EXECUTE overlay(definition PLACING E'BEGIN\n IF TG_OP=''UPDATE'' AND constructor_initial_pointer_authorized(to_jsonb(OLD),to_jsonb(NEW)) THEN RETURN NEW; END IF;\n' FROM anchor FOR 5);
END $$;

ALTER FUNCTION assignment_unassigned_origin(BIGINT) RENAME TO assignment_unassigned_origin_legacy;
CREATE FUNCTION constructor_natural_unassigned_origin(occurrence_value BIGINT) RETURNS BIGINT LANGUAGE SQL STABLE AS $$
    WITH RECURSIVE admitted AS (
        SELECT origin.*,operation.semester_from,operation.semester_until_exclusive,
               occurrence.current_lesson_id,occurrence.generation AS current_generation,
               occurrence.occurrence_date AS current_date
          FROM constructor_generation_origins origin
          JOIN constructor_semester_materializations operation ON operation.operation_id=origin.operation_id
          JOIN lesson_occurrences occurrence ON occurrence.id=origin.occurrence_id
         WHERE occurrence.id=occurrence_value AND origin.initial_assignment_id IS NULL
           AND origin.initial_teacher_id IS NULL AND occurrence.assignment_id IS NULL
           AND occurrence.assigned_teacher_id IS NULL AND operation.state<>'GENERATING'
           AND occurrence.schedule_item_id=origin.schedule_item_id
           AND occurrence.group_id=origin.group_id AND occurrence.subject_id=origin.subject_id
           AND occurrence.semester_id=origin.semester_id AND occurrence.lesson_type=origin.lesson_type
           AND occurrence.occurrence_date>=operation.semester_from
           AND occurrence.occurrence_date<operation.semester_until_exclusive
           AND NOT EXISTS(SELECT 1 FROM schedule_assignment_binding_ledger earlier
               WHERE earlier.occurrence_id=occurrence.id AND earlier.after_assignment_id IS NOT NULL)
    ), proven(lesson_id,generation,lesson_date) AS (
        -- The original physical identity is immutable even after it becomes transferred.
        SELECT origin.lesson_id,1::bigint,origin.occurrence_date
          FROM admitted origin JOIN lessons lesson ON lesson.id=origin.lesson_id
         WHERE lesson.occurrence_id=origin.occurrence_id AND lesson.generation=1
           AND lesson.schedule_item_id=origin.schedule_item_id AND lesson.date=origin.occurrence_date
           AND lesson.group_id=origin.group_id AND lesson.subject_id=origin.subject_id
           AND lesson.semester_id=origin.semester_id AND lesson.lesson_type=origin.lesson_type
           AND lesson.lesson_number=origin.lesson_number
           AND lesson.start_time=origin.start_time AND lesson.end_time=origin.end_time
           AND lesson.room_snapshot IS NOT DISTINCT FROM origin.room_snapshot
           AND lesson.week_type_snapshot=origin.week_type_snapshot::text
           AND lesson.assignment_id IS NULL AND lesson.assigned_teacher_id IS NULL
        UNION
        SELECT transfer.target_lesson_id,transfer.target_generation,authority.target_date
          FROM proven source CROSS JOIN admitted origin
          JOIN lesson_transfer_operations transfer ON transfer.occurrence_id=origin.occurrence_id
           AND transfer.source_lesson_id=source.lesson_id AND transfer.source_generation=source.generation
          JOIN lesson_transfer_authorities authority ON authority.operation_id=transfer.operation_id
           AND authority.occurrence_id=transfer.occurrence_id
           AND authority.source_lesson_id=transfer.source_lesson_id
           AND authority.target_lesson_id=transfer.target_lesson_id
           AND authority.actor_id=transfer.actor_id AND authority.operation_hash=transfer.operation_hash
           AND authority.expected_generation=transfer.source_generation
           AND authority.expected_occurrence_revision=transfer.expected_occurrence_revision
          JOIN lessons lesson ON lesson.id=source.lesson_id AND lesson.occurrence_id=origin.occurrence_id
           AND lesson.generation=source.generation AND lesson.date=source.lesson_date
         WHERE transfer.state IN('PENDING','COMPLETED')
           AND transfer.target_generation=source.generation+1
           AND lesson.status='transferred'
           AND lesson.revision=authority.expected_lesson_revision+1
           AND (transfer.operation_id=NULLIF(current_setting('rutcampustrack.lesson_transfer_operation_id',TRUE),'')::uuid
               OR EXISTS(SELECT 1 FROM lesson_lifecycle_entries accepted
                   WHERE accepted.transfer_operation_id=transfer.operation_id
                     AND accepted.occurrence_id=origin.occurrence_id AND accepted.action='TRANSFERRED'
                     AND accepted.lesson_id=transfer.source_lesson_id
                     AND accepted.target_lesson_id=transfer.target_lesson_id
                     AND accepted.generation=transfer.target_generation
                     AND accepted.revision=transfer.result_occurrence_revision
                     AND accepted.actor_id=transfer.actor_id))
           AND lesson.schedule_item_id=origin.schedule_item_id
           AND lesson.group_id=origin.group_id AND lesson.subject_id=origin.subject_id
           AND lesson.semester_id=origin.semester_id AND lesson.lesson_type=origin.lesson_type
           AND lesson.assignment_id IS NULL AND lesson.assigned_teacher_id IS NULL
           AND authority.target_date>=origin.semester_from
           AND authority.target_date<origin.semester_until_exclusive
    )
    SELECT origin.origin_assignment_id FROM admitted origin JOIN proven current_identity
      ON current_identity.lesson_id=origin.current_lesson_id
     AND current_identity.generation=origin.current_generation
     AND current_identity.lesson_date=origin.current_date
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
          AND (value->>'semester_id')::bigint=occurrence.semester_id
          AND (value->>'occurrence_date')::date=occurrence.occurrence_date
          AND occurrence.occurrence_date>=operation.requested_cutoff
          AND occurrence.occurrence_date<operation.valid_until_exclusive
          AND EXISTS(SELECT 1 FROM schedule_assignment_fences target WHERE target.assignment_id=operation.target_assignment_id
              AND target.assigned_teacher_id=operation.target_teacher_id AND target.group_id=occurrence.group_id
              AND target.subject_id=occurrence.subject_id AND target.semester_id=occurrence.semester_id
              AND target.lesson_type=occurrence.lesson_type AND occurrence.occurrence_date>=target.valid_from
              AND occurrence.occurrence_date<target.creation_cap_until_exclusive)
          AND NULLIF(value->>'before_assignment_id','') IS NULL AND NULLIF(value->>'before_teacher_id','') IS NULL
          AND (value->>'after_assignment_id')::bigint=operation.target_assignment_id
          AND (value->>'after_teacher_id')::bigint=operation.target_teacher_id
          AND (value->>'expected_occurrence_revision')::bigint=occurrence.revision
          AND (value->>'after_occurrence_revision')::bigint=occurrence.revision+1
          AND NULLIF(value->>'lesson_id','')::bigint IS NOT DISTINCT FROM occurrence.current_lesson_id
          AND (lesson.id IS NULL AND occurrence.occurrence_date>(NOW() AT TIME ZONE 'Europe/Moscow')::date
              OR lesson.assignment_id IS NULL AND lesson.assigned_teacher_id IS NULL
              AND (lesson.status='closed' OR lesson.status NOT IN('active','closed')
                  AND (lesson.date+lesson.start_time) AT TIME ZONE 'Europe/Moscow'>NOW())
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

-- Every committed past generation has durable exact delivery work, even on an early return.
CREATE FUNCTION require_constructor_materialization_finished() RETURNS TRIGGER LANGUAGE plpgsql AS $$
DECLARE operation constructor_semester_materializations%ROWTYPE;
BEGIN
    SELECT * INTO operation FROM constructor_semester_materializations WHERE operation_id=NEW.operation_id;
    IF operation.state='GENERATING' OR EXISTS(SELECT 1 FROM constructor_generation_origins origin
        WHERE origin.operation_id=operation.operation_id
          AND origin.occurrence_date+origin.end_time+interval '5 minutes'<=origin.created_at AT TIME ZONE 'Europe/Moscow'
          AND NOT EXISTS(SELECT 1 FROM constructor_materialization_work work
              WHERE work.operation_id=operation.operation_id AND origin.lesson_id=ANY(work.lesson_ids))) THEN
        RAISE EXCEPTION 'semester generation cannot commit without exact durable historical delivery work';
    END IF;
    IF TG_OP='INSERT' AND EXISTS(SELECT 1 FROM constructor_generation_origins origin
        WHERE origin.operation_id=operation.operation_id AND NOT EXISTS(
            SELECT 1 FROM lesson_occurrences occurrence JOIN lessons lesson
              ON lesson.id=occurrence.current_lesson_id AND lesson.occurrence_id=occurrence.id
            WHERE occurrence.id=origin.occurrence_id AND lesson.id=origin.lesson_id
              AND occurrence.schedule_item_id=origin.schedule_item_id
              AND lesson.schedule_item_id=origin.schedule_item_id)) THEN
        RAISE EXCEPTION 'semester generation cannot commit an incomplete occurrence or physical lesson';
    END IF;
    RETURN NEW;
END $$;
CREATE CONSTRAINT TRIGGER constructor_materialization_finished AFTER INSERT OR UPDATE ON constructor_semester_materializations
    DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION require_constructor_materialization_finished();

-- Natural initial absence is bounded by the semester, not the teacher interval.
-- Preserve the legacy closure branch and every existing exact transfer check.
DO $$ DECLARE definition TEXT; old_text TEXT; new_text TEXT; BEGIN
    SELECT pg_get_functiondef('validate_lesson_transfer_authority_insert()'::regprocedure) INTO definition;
    old_text:='IF NOT FOUND OR NEW.target_date<fence_row.valid_from OR NEW.target_date>=fence_row.cap_until_exclusive';
    new_text:='IF NOT FOUND OR (constructor_natural_unassigned_origin(occurrence_row.id) IS NULL AND (NEW.target_date<fence_row.valid_from OR NEW.target_date>=fence_row.cap_until_exclusive))
        OR (constructor_natural_unassigned_origin(occurrence_row.id) IS NOT NULL AND NOT EXISTS (
            SELECT 1 FROM constructor_generation_origins origin JOIN constructor_semester_materializations operation
              ON operation.operation_id=origin.operation_id WHERE origin.occurrence_id=occurrence_row.id
              AND NEW.target_date>=operation.semester_from AND NEW.target_date<operation.semester_until_exclusive))';
    IF strpos(definition,old_text)=0 THEN RAISE EXCEPTION 'natural transfer admission anchor changed'; END IF;
    EXECUTE replace(definition,old_text,new_text);
END $$;
