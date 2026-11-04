-- A constructor correction is narrower than subject/group deletion. It retains
-- every control receipt and admits only exact rows in its sealed local manifest.
CREATE TABLE constructor_deletion_operations (
    operation_id UUID PRIMARY KEY,
    actor_id BIGINT NOT NULL CHECK (actor_id > 0),
    request_key UUID NOT NULL,
    kind VARCHAR(32) NOT NULL CHECK (kind = 'DELETE_UNUSED_SERIES'),
    requested_item_id BIGINT NOT NULL CHECK (requested_item_id > 0),
    group_id BIGINT NOT NULL CHECK (group_id > 0),
    subject_id BIGINT NOT NULL CHECK (subject_id > 0),
    semester_id BIGINT NOT NULL CHECK (semester_id > 0),
    family_ids BIGINT[] NOT NULL CHECK (cardinality(family_ids) > 0),
    request_hash BYTEA NOT NULL CHECK (octet_length(request_hash) = 32),
    preview_revision VARCHAR(64) NOT NULL CHECK (preview_revision ~ '^[0-9a-f]{64}$'),
    manifest JSONB NOT NULL CHECK (jsonb_typeof(manifest) = 'array'),
    attendance_command BYTEA NOT NULL,
    academic_command BYTEA NOT NULL,
    state VARCHAR(16) NOT NULL CHECK (state IN
        ('PREPARING','SEALED','COMMITTING','COMPLETED','RELEASING','RELEASED')),
    deleted_count BIGINT NOT NULL DEFAULT 0 CHECK (deleted_count >= 0),
    protected_count BIGINT NOT NULL DEFAULT 0 CHECK (protected_count >= 0),
    error_code VARCHAR(64),
    attempts INTEGER NOT NULL DEFAULT 0 CHECK (attempts >= 0),
    next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(actor_id, request_key)
);

CREATE INDEX constructor_deletion_recovery
    ON constructor_deletion_operations(next_attempt_at, operation_id)
    WHERE state NOT IN ('COMPLETED','RELEASED');
CREATE INDEX constructor_deletion_family ON constructor_deletion_operations USING gin(family_ids);

CREATE TABLE constructor_system_audit_proofs (
    payload_hash BYTEA PRIMARY KEY CHECK(octet_length(payload_hash)=32),
    lesson_id BIGINT NOT NULL CHECK(lesson_id>0),
    occurrence_id BIGINT NOT NULL CHECK(occurrence_id>0),
    lifecycle_entry_id BIGINT CHECK(lifecycle_entry_id>0),
    origin_operation_id UUID,
    source_kind VARCHAR(24) NOT NULL DEFAULT 'USER' CHECK(source_kind IN('USER','SYSTEM_BACKFILL')),
    action VARCHAR(32) NOT NULL CHECK(action IN('LESSON_CREATED','LESSON_CANCELLED')),
    CHECK(source_kind='USER' AND lifecycle_entry_id IS NOT NULL AND origin_operation_id IS NULL
       OR source_kind='SYSTEM_BACKFILL' AND lifecycle_entry_id IS NULL AND origin_operation_id IS NOT NULL
          AND action='LESSON_CREATED')
);
CREATE TRIGGER constructor_system_audit_proof_immutable
    BEFORE UPDATE OR DELETE ON constructor_system_audit_proofs
    FOR EACH ROW EXECUTE FUNCTION reject_schedule_transfer_replay_mutation();

CREATE FUNCTION constructor_deletion_rows(BIGINT[])
RETURNS TABLE(kind TEXT, data JSONB) LANGUAGE SQL STABLE AS $rows$
    SELECT 'schedule_items'::text, to_jsonb(t) FROM schedule_items t WHERE id = ANY($1)
    UNION ALL SELECT 'lesson_occurrences', to_jsonb(t) FROM lesson_occurrences t
        WHERE schedule_item_id = ANY($1)
    UNION ALL SELECT 'lessons', to_jsonb(t) FROM lessons t WHERE schedule_item_id = ANY($1)
    UNION ALL SELECT 'lesson_lifecycle_entries', to_jsonb(t) FROM lesson_lifecycle_entries t
        WHERE occurrence_id IN (SELECT id FROM lesson_occurrences WHERE schedule_item_id = ANY($1))
$rows$;

CREATE FUNCTION constructor_deletion_manifest(BIGINT[]) RETURNS JSONB LANGUAGE SQL STABLE AS $$
    SELECT COALESCE(jsonb_agg(jsonb_build_object('kind',kind,'row_hash',
        encode(public.digest(convert_to(data::text,'UTF8'),'sha256'),'hex'))
        ORDER BY kind,data::text),'[]'::jsonb) FROM constructor_deletion_rows($1)
$$;

CREATE FUNCTION constructor_deletion_row_authorized(kind_value TEXT, row_value JSONB)
RETURNS BOOLEAN LANGUAGE SQL STABLE AS $$
    SELECT EXISTS(SELECT 1 FROM constructor_deletion_operations operation
        WHERE operation.operation_id = NULLIF(
            current_setting('rutcampustrack.constructor_delete_operation_id',TRUE),'')::uuid
          AND operation.kind = 'DELETE_UNUSED_SERIES' AND operation.state = 'COMMITTING'
          AND operation.manifest @> jsonb_build_array(jsonb_build_object(
              'kind',kind_value,'row_hash',encode(public.digest(
                  convert_to(row_value::text,'UTF8'),'sha256'),'hex'))))
$$;

CREATE FUNCTION guard_constructor_deletion_operation() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP='INSERT' THEN
        PERFORM pg_advisory_xact_lock(5452097,(NEW.semester_id % 2147483647)::integer);
        IF EXISTS(SELECT 1 FROM schedule_semester_archive_barriers
            WHERE semester_id=NEW.semester_id AND participant_state<>'RELEASED') THEN
            RAISE EXCEPTION 'semester archive or deletion already admitted';
        END IF;
        RETURN NEW;
    END IF;
    IF TG_OP='DELETE' THEN RAISE EXCEPTION 'constructor control receipt must be retained'; END IF;
    IF (to_jsonb(NEW)-ARRAY['state','deleted_count','protected_count','error_code','attempts',
            'next_attempt_at','updated_at']) IS DISTINCT FROM
       (to_jsonb(OLD)-ARRAY['state','deleted_count','protected_count','error_code','attempts',
            'next_attempt_at','updated_at'])
       OR OLD.state IN ('COMPLETED','RELEASED')
       OR NOT (NEW.state=OLD.state
           OR OLD.state='PREPARING' AND NEW.state IN ('SEALED','RELEASING')
           OR OLD.state='SEALED' AND NEW.state IN ('COMMITTING','RELEASING')
           OR OLD.state='COMMITTING' AND NEW.state='COMPLETED'
           OR OLD.state='RELEASING' AND NEW.state='RELEASED') THEN
        RAISE EXCEPTION 'constructor operation identity or transition changed';
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER constructor_deletion_operation_guard
    BEFORE INSERT OR UPDATE OR DELETE ON constructor_deletion_operations
    FOR EACH ROW EXECUTE FUNCTION guard_constructor_deletion_operation();

CREATE FUNCTION guard_constructor_deletion_archive_race() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    PERFORM pg_advisory_xact_lock(5452097,(NEW.semester_id % 2147483647)::integer);
    IF NEW.participant_state<>'RELEASED' AND EXISTS(
        SELECT 1 FROM constructor_deletion_operations WHERE semester_id=NEW.semester_id
            AND state IN('PREPARING','SEALED','COMMITTING','RELEASING')) THEN
        RAISE EXCEPTION 'constructor deletion is not complete';
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER a0_constructor_delete_archive_race
    BEFORE INSERT OR UPDATE ON schedule_semester_archive_barriers
    FOR EACH ROW EXECUTE FUNCTION guard_constructor_deletion_archive_race();

CREATE FUNCTION guard_constructor_scope_write() RETURNS TRIGGER LANGUAGE plpgsql AS $$
DECLARE value JSONB; target_item BIGINT;
BEGIN
    IF TG_OP='DELETE' AND constructor_deletion_row_authorized(TG_TABLE_NAME,to_jsonb(OLD)) THEN
        RETURN OLD;
    END IF;
    FOR value IN SELECT v FROM unnest(ARRAY[
        CASE WHEN TG_OP<>'INSERT' THEN to_jsonb(OLD) END,
        CASE WHEN TG_OP<>'DELETE' THEN to_jsonb(NEW) END]) v WHERE v IS NOT NULL LOOP
        target_item := CASE WHEN TG_TABLE_NAME='schedule_items' THEN (value->>'id')::bigint
                            ELSE NULLIF(value->>'schedule_item_id','')::bigint END;
        IF target_item IS NULL AND value->>'occurrence_id' IS NOT NULL THEN
            SELECT schedule_item_id INTO target_item FROM lesson_occurrences
                WHERE id=(value->>'occurrence_id')::bigint;
        END IF;
        IF target_item IS NOT NULL THEN
            -- This trigger runs after the existing semester/archive guards.
            -- Their common transaction lock precedes this fresh operation read;
            -- introducing a new item lock here would invert lesson-writer order.
            IF EXISTS(SELECT 1 FROM constructor_deletion_operations operation
                WHERE target_item=ANY(operation.family_ids) AND operation.state<>'RELEASED') THEN
                RAISE EXCEPTION 'constructor scope is fenced for exact deletion' USING ERRCODE='23514';
            END IF;
        END IF;
    END LOOP;
    IF TG_OP='DELETE' THEN RETURN OLD; END IF;
    RETURN NEW;
END $$;

-- Existing guards continue to enforce every ordinary rule. The new admission
-- is valid only for an unchanged row in this operation's immutable manifest.
DO $install$
DECLARE item RECORD; definition TEXT; anchor INTEGER;
BEGIN
    FOR item IN SELECT DISTINCT p.oid FROM pg_trigger trigger_row
        JOIN pg_proc p ON p.oid=trigger_row.tgfoid JOIN pg_class table_row ON table_row.oid=trigger_row.tgrelid
        WHERE table_row.relname=ANY(ARRAY['schedule_items','lesson_occurrences','lessons',
            'lesson_lifecycle_entries']) AND NOT trigger_row.tgisinternal LOOP
        SELECT pg_get_functiondef(item.oid) INTO definition;
        anchor:=strpos(definition,'BEGIN');
        IF anchor=0 THEN RAISE EXCEPTION 'constructor admission anchor unavailable: %',item.oid::regprocedure; END IF;
        EXECUTE overlay(definition PLACING E'BEGIN\n IF TG_OP=''DELETE'' AND constructor_deletion_row_authorized(TG_TABLE_NAME,to_jsonb(OLD)) THEN RETURN OLD; END IF;\n'
            FROM anchor FOR 5);
    END LOOP;
    FOR item IN SELECT unnest(ARRAY['schedule_items','lesson_occurrences','lessons',
        'lesson_lifecycle_entries','lesson_homework_bindings']) AS name LOOP
        EXECUTE format('CREATE TRIGGER zz_constructor_scope_fence BEFORE INSERT OR UPDATE OR DELETE ON %I FOR EACH ROW EXECUTE FUNCTION guard_constructor_scope_write()',item.name);
    END LOOP;
END $install$;

REVOKE EXECUTE ON FUNCTION constructor_deletion_rows(BIGINT[]),
    constructor_deletion_manifest(BIGINT[]),constructor_deletion_row_authorized(TEXT,JSONB) FROM PUBLIC;
DO $$ BEGIN
    IF EXISTS(SELECT 1 FROM pg_roles WHERE rolname='rct_runtime') THEN
        GRANT EXECUTE ON FUNCTION constructor_deletion_rows(BIGINT[]),
            constructor_deletion_manifest(BIGINT[]),constructor_deletion_row_authorized(TEXT,JSONB) TO rct_runtime;
    END IF;
END $$;
