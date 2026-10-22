CREATE TABLE schedule_subject_deletion_participants (
    operation_id uuid PRIMARY KEY,
    subject_id bigint NOT NULL CHECK (subject_id > 0),
    semester_id bigint NOT NULL CHECK (semester_id > 0),
    group_id bigint NOT NULL CHECK (group_id > 0),
    actor_id bigint NOT NULL CHECK (actor_id > 0),
    participant_digest varchar(64) NOT NULL CHECK (participant_digest ~ '^[0-9a-f]{64}$'),
    manifest jsonb NOT NULL CHECK (jsonb_typeof(manifest) = 'array'),
    command_manifest bytea NOT NULL,
    manifest_digest varchar(64) NOT NULL CHECK (manifest_digest ~ '^[0-9a-f]{64}$'),
    state varchar(16) NOT NULL CHECK (state IN ('PREPARED', 'SEALED', 'COMMITTING', 'DELETED', 'RELEASED')),
    updated_at timestamptz NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX schedule_subject_delete_epoch ON schedule_subject_deletion_participants (subject_id)
WHERE
    state <> 'RELEASED';

-- The exception is usable only for an exact immutable row in this sealed subject manifest.
CREATE FUNCTION schedule_subject_delete_row_authorized (kind text, old_row jsonb)
    RETURNS boolean
    LANGUAGE SQL
    STABLE
    AS $$
    SELECT
        EXISTS (
            SELECT
                1
            FROM
                schedule_subject_deletion_participants operation
            WHERE
                operation.operation_id = NULLIF (current_setting('rutcampustrack.subject_delete_operation_id', TRUE), '')::uuid
                AND operation.state = 'COMMITTING'
                AND (operation.manifest @> jsonb_build_array(jsonb_build_object('kind', kind, 'row_hash', encode(public.digest(convert_to(old_row::text, 'UTF8'), 'sha256'), 'hex')))
                    OR (kind = 'schedule_one_off_lessons'
                        AND old_row ->> 'physical_lesson_id' IS NULL
                        AND operation.manifest @> jsonb_build_array(jsonb_build_object('kind', kind, 'detached_hash', encode(public.digest(convert_to(old_row::text, 'UTF8'), 'sha256'), 'hex'))))))
$$;

CREATE FUNCTION schedule_subject_delete_binding_authorized (old_row jsonb, new_row jsonb)
    RETURNS boolean
    LANGUAGE SQL
    STABLE
    AS $$
    SELECT
        schedule_subject_delete_row_authorized ('lesson_homework_bindings', old_row)
        AND new_row ->> 'state' = 'ARCHIVED'
        AND new_row ->> 'homework_id' IS NULL
        AND (old_row ->> 'state' <> 'ARCHIVED'
            OR old_row ->> 'homework_id' IS NOT NULL)
        AND (new_row ->> 'revision')::bigint = (old_row ->> 'revision')::bigint + 1
        AND (new_row - ARRAY['state', 'homework_id', 'revision', 'updated_at']) = (old_row - ARRAY['state', 'homework_id', 'revision', 'updated_at'])
$$;

CREATE FUNCTION guard_schedule_subject_deletion_write ()
    RETURNS TRIGGER
    LANGUAGE plpgsql
    AS $$
DECLARE
    before_row jsonb;
    after_row jsonb;
    row_data jsonb;
    affected_subject bigint;
    affected_semester bigint;
    active_operation uuid;
    operation_state text;
BEGIN
    IF TG_OP <> 'INSERT' THEN
        before_row = to_jsonb (OLD);
    END IF;
    IF TG_OP <> 'DELETE' THEN
        after_row = to_jsonb (NEW);
    END IF;
    FOR row_data IN
    SELECT
        *
    FROM
        unnest(ARRAY[before_row, after_row])
    WHERE
        unnest IS NOT NULL LOOP
            affected_subject = NULLIF (row_data ->> 'subject_id', '')::bigint;
            affected_semester = NULLIF (row_data ->> 'semester_id', '')::bigint;
            IF TG_TABLE_NAME = 'lesson_lifecycle_entries' THEN
                SELECT
                    subject_id,
                    semester_id INTO affected_subject,
                    affected_semester
                FROM
                    lesson_occurrences
                WHERE
                    id = (row_data ->> 'occurrence_id')::bigint;
            END IF;
            IF affected_subject IS NULL OR affected_semester IS NULL THEN
                RAISE EXCEPTION 'subject deletion fence cannot resolve exact scope';
            END IF;
            PERFORM
                pg_advisory_xact_lock(5452097, (affected_semester % 2147483647)::integer);
            PERFORM
                pg_advisory_xact_lock(5452098, (affected_subject % 2147483647)::integer);
            SELECT
                operation_id,
                state INTO active_operation,
                operation_state
            FROM
                schedule_subject_deletion_participants
            WHERE
                subject_id = affected_subject
                AND state <> 'RELEASED';
            IF active_operation IS NOT NULL AND NOT (operation_state = 'COMMITTING' AND active_operation = NULLIF (current_setting('rutcampustrack.subject_delete_operation_id', TRUE), '')::uuid AND (TG_OP = 'DELETE' OR TG_TABLE_NAME = 'schedule_one_off_lessons' AND TG_OP = 'UPDATE' AND schedule_subject_delete_row_authorized (TG_TABLE_NAME, before_row) AND after_row ->> 'physical_lesson_id' IS NULL AND (after_row - 'physical_lesson_id') = (before_row - 'physical_lesson_id') OR TG_TABLE_NAME = 'lesson_homework_bindings' AND TG_OP = 'UPDATE' AND schedule_subject_delete_binding_authorized (before_row, after_row))) THEN
                RAISE EXCEPTION 'subject is fenced for deletion';
            END IF;
        END LOOP;
    IF TG_OP = 'DELETE' THEN
        RETURN OLD;
    END IF;
    RETURN NEW;
END
$$;

CREATE TRIGGER a0_subject_delete_item
    BEFORE INSERT OR UPDATE OR DELETE ON schedule_items
    FOR EACH ROW
    EXECUTE FUNCTION guard_schedule_subject_deletion_write ();

CREATE TRIGGER a0_subject_delete_oneoff
    BEFORE INSERT OR UPDATE OR DELETE ON schedule_one_off_lessons
    FOR EACH ROW
    EXECUTE FUNCTION guard_schedule_subject_deletion_write ();

CREATE TRIGGER a0_subject_delete_occurrence
    BEFORE INSERT OR UPDATE OR DELETE ON lesson_occurrences
    FOR EACH ROW
    EXECUTE FUNCTION guard_schedule_subject_deletion_write ();

CREATE TRIGGER a0_subject_delete_lesson
    BEFORE INSERT OR UPDATE OR DELETE ON lessons
    FOR EACH ROW
    EXECUTE FUNCTION guard_schedule_subject_deletion_write ();

CREATE TRIGGER a0_subject_delete_lifecycle
    BEFORE INSERT OR UPDATE OR DELETE ON lesson_lifecycle_entries
    FOR EACH ROW
    EXECUTE FUNCTION guard_schedule_subject_deletion_write ();

CREATE TRIGGER a_subject_delete_binding
    BEFORE INSERT OR UPDATE OR DELETE ON lesson_homework_bindings
    FOR EACH ROW
    EXECUTE FUNCTION guard_schedule_subject_deletion_write ();

CREATE TRIGGER a0_subject_delete_assignment_fence
    BEFORE INSERT OR UPDATE OR DELETE ON schedule_assignment_fences
    FOR EACH ROW
    EXECUTE FUNCTION guard_schedule_subject_deletion_write ();

-- Existing INSERT/UPDATE history policies continue to call their unchanged function.
-- Only exact subject-manifest DELETEs take the separate reviewed authority.
DROP TRIGGER lessons_history_guard_trg ON lessons;

CREATE TRIGGER lessons_history_guard_trg
    BEFORE INSERT OR UPDATE ON lessons
    FOR EACH ROW
    EXECUTE FUNCTION validate_lesson_physical_snapshot ();

CREATE TRIGGER lessons_subject_delete_history_guard
    BEFORE DELETE ON lessons
    FOR EACH ROW
    WHEN (NOT schedule_subject_delete_row_authorized ('lessons', to_jsonb (OLD)))
    EXECUTE FUNCTION validate_lesson_physical_snapshot ();

DROP TRIGGER lesson_lifecycle_entries_append_only_trg ON lesson_lifecycle_entries;

CREATE TRIGGER lesson_lifecycle_entries_append_only_trg
    BEFORE UPDATE ON lesson_lifecycle_entries
    FOR EACH ROW
    EXECUTE FUNCTION reject_lesson_lifecycle_mutation ();

CREATE TRIGGER lesson_lifecycle_subject_delete_guard
    BEFORE DELETE ON lesson_lifecycle_entries
    FOR EACH ROW
    WHEN (NOT schedule_subject_delete_row_authorized ('lesson_lifecycle_entries', to_jsonb (OLD)))
    EXECUTE FUNCTION reject_lesson_lifecycle_mutation ();

CREATE FUNCTION guard_subject_deletion_archive_race ()
    RETURNS TRIGGER
    LANGUAGE plpgsql
    AS $$
BEGIN
    IF NEW.participant_state <> 'RELEASED' AND EXISTS (
        SELECT
            1
        FROM
            schedule_subject_deletion_participants
        WHERE
            semester_id = NEW.semester_id AND state IN ('PREPARED', 'SEALED', 'COMMITTING')) THEN
        RAISE EXCEPTION 'subject deletion is not complete';
    END IF;
    RETURN NEW;
END
$$;

CREATE TRIGGER a0_subject_delete_archive_race
    BEFORE INSERT OR UPDATE ON schedule_semester_archive_barriers
    FOR EACH ROW
    EXECUTE FUNCTION guard_subject_deletion_archive_race ();

-- V24's exact durable placement receipt/acknowledgement guards remain intact.
DO $$
DECLARE
    definition text;
    updated text;
    anchor integer;
BEGIN
    SELECT
        pg_get_functiondef('protect_lesson_homework_binding_identity()'::regprocEDURE) INTO definition;
    anchor := strpos(definition, E'BEGIN\n');
    IF anchor = 0 THEN
        RAISE EXCEPTION 'subject deletion placement admission anchor changed';
    END IF;
    updated := overlay(definition PLACING E'BEGIN\n    IF TG_OP = ''UPDATE'' AND schedule_subject_delete_binding_authorized(to_jsonb(OLD),to_jsonb(NEW)) THEN RETURN NEW; END IF;\n' FROM anchor FOR 6);
    IF updated = definition THEN
        RAISE EXCEPTION 'subject deletion placement admission not installed';
    END IF;
    EXECUTE updated;
END
$$;

-- Retain immutable replay/restore/binding/room receipts with their ORIGINAL IDs. A subject
-- cascade removes the physical domain only. Ordinary reference creation and parent mutation
-- retain the old FK semantics; only an exact sealed subject-manifest DELETE may detach it.
CREATE FUNCTION validate_subject_retained_reference ()
    RETURNS TRIGGER
    LANGUAGE plpgsql
    AS $$
DECLARE
    link jsonb := TG_ARGV[0]::jsonb;
    pair jsonb;
    predicate text := '';
    value jsonb := to_jsonb (NEW);
    present boolean;
BEGIN
    FOR pair IN
    SELECT
        *
    FROM
        jsonb_array_elements(link -> 'pairs')
        LOOP
            IF value -> (pair ->> 0) IS NULL OR value -> (pair ->> 0) = 'null'::jsonb THEN
                RETURN NEW;
            END IF;
            predicate := predicate || CASE WHEN predicate = '' THEN ''
            ELSE
                ' AND '
            END || format('(to_jsonb(parent)->%L)=($1->%L)', pair ->> 1, pair ->> 0);
        END LOOP;
    EXECUTE format('SELECT TRUE FROM %s parent WHERE %s FOR KEY SHARE', link ->> 'parent', predicate) INTO present
    USING value;
    IF present IS DISTINCT FROM TRUE THEN
        RAISE EXCEPTION 'retained receipt requires its exact existing parent tuple'
            USING ERRCODE = '23503';
        END IF;
        RETURN NEW;
END
$$;

CREATE FUNCTION fence_subject_retained_reference ()
    RETURNS TRIGGER
    LANGUAGE plpgsql
    AS $$
DECLARE
    link jsonb := TG_ARGV[0]::jsonb;
    pair jsonb;
    predicate text := '';
    value jsonb := to_jsonb (NEW);
    target record;
BEGIN
    FOR pair IN
    SELECT
        *
    FROM
        jsonb_array_elements(link -> 'pairs')
        LOOP
            IF value -> (pair ->> 0) IS NULL OR value -> (pair ->> 0) = 'null'::jsonb THEN
                RETURN NEW;
            END IF;
            predicate := predicate || CASE WHEN predicate = '' THEN ''
            ELSE
                ' AND '
            END || format('(to_jsonb(parent)->%L)=($1->%L)', pair ->> 1, pair ->> 0);
        END LOOP;
    FOR target IN EXECUTE format('SELECT subject_id,semester_id FROM %s parent WHERE %s', link ->> 'parent', predicate)
    USING value LOOP
        PERFORM pg_advisory_xact_lock(5452097, (target.semester_id % 2147483647)::integer);
        PERFORM
            pg_advisory_xact_lock(5452098, (target.subject_id % 2147483647)::integer);
        IF EXISTS (
            SELECT
                1
            FROM
                schedule_subject_deletion_participants p
            WHERE
                p.subject_id = target.subject_id
                AND p.state <> 'RELEASED') THEN
        RAISE EXCEPTION 'subject is fenced for retained receipt creation'
            USING ERRCODE = '23514';
        END IF;
    END LOOP;
    RETURN NEW;
END
$$;

-- Closed V25--V29 receipt inventory, not a blanket FK disable. Every replaced FK gets an
-- equivalent child check and ordinary parent restriction/cascade BEFORE it is removed.
DO $$
DECLARE
    fk record;
    parent record;
    pair record;
    child_name text;
    parent_name text;
    descriptor jsonb;
    pairs jsonb;
    child_predicate text;
    parent_predicate text;
    retain_predicate text;
    parent_body text;
    select_rows text := '';
    create_trigger text;
    suffix text;
BEGIN
    FOR parent IN
    SELECT
        c.oid,
        c.relname,
        n.nspname
    FROM
        pg_class c
        JOIN pg_namespace n ON n.oid = c.relnamespace
    WHERE
        n.nspname = current_schema()
        AND c.relname IN ('lessons', 'lesson_occurrences', 'schedule_items', 'schedule_one_off_lessons')
    ORDER BY
        c.relname LOOP
            parent_name := format('%I.%I', parent.nspname, parent.relname);
            parent_body := E'BEGIN\n IF TG_OP=''UPDATE'' AND (to_jsonb(OLD)->''id'') IS NOT DISTINCT FROM (to_jsonb(NEW)->''id'') AND (to_jsonb(OLD)->''occurrence_id'') IS NOT DISTINCT FROM (to_jsonb(NEW)->''occurrence_id'') THEN RETURN NEW; END IF;\n IF TG_OP=''DELETE'' AND schedule_subject_delete_row_authorized(TG_TABLE_NAME,to_jsonb(OLD)) THEN RETURN OLD; END IF;\n';
            FOR fk IN
            SELECT
                co.*,
                c.relname AS child_rel,
                n.nspname AS child_schema
            FROM
                pg_constraint co
                JOIN pg_class c ON c.oid = co.conrelid
                JOIN pg_namespace n ON n.oid = c.relnamespace
            WHERE
                co.contype = 'f'
                AND co.confrelid = parent.oid
                AND co.conrelid IN ('schedule_one_off_create_replay'::regclass, 'one_off_lesson_restore_authorities'::regclass, 'schedule_recurring_lifecycle_replay'::regclass, 'lesson_template_restore_authorities'::regclass, 'schedule_assignment_binding_ledger'::regclass, 'schedule_assignment_template_close_ledger'::regclass, 'lesson_room_change_ledger'::regclass)
            ORDER BY
                c.relname,
                co.conname LOOP
                    IF fk.confmatchtype <> 's' OR fk.confdeltype NOT IN ('r', 'c') OR fk.confupdtype <> 'a' THEN
                        RAISE EXCEPTION 'retained receipt FK semantics changed';
                    END IF;
                    pairs := '[]'::jsonb;
                    child_predicate := '';
                    parent_predicate := '';
                    retain_predicate := '';
                    FOR pair IN
                    SELECT
                        child.attname AS child_column,
                        ref.attname AS parent_column
                    FROM
                        unnest(fk.conkey, fk.confkey) AS keys (child_key,
        parent_key)
    JOIN pg_attribute child ON child.attrelid = fk.conrelid
        AND child.attnum = keys.child_key
    JOIN pg_attribute ref ON ref.attrelid = fk.confrelid
        AND ref.attnum = keys.parent_key LOOP
            pairs := pairs || jsonb_build_array(jsonb_build_array(pair.child_column, pair.parent_column));
            child_predicate := child_predicate || CASE WHEN child_predicate = '' THEN ''
            ELSE
                ' AND '
            END || format('(to_jsonb(receipt)->%L)=(to_jsonb(OLD)->%L)', pair.child_column, pair.parent_column);
            parent_predicate := parent_predicate || CASE WHEN parent_predicate = '' THEN ''
            ELSE
                ' AND '
            END || format('(to_jsonb(receipt)->%L)=(to_jsonb(parent)->%L)', pair.child_column, pair.parent_column);
        END LOOP;
                    child_name := format('%I.%I', fk.child_schema, fk.child_rel);
                    descriptor := jsonb_build_object('parent', parent_name, 'pairs', pairs);
                    suffix := substr(md5(fk.child_rel || ':' || fk.conname), 1, 20);
                    create_trigger := format('CREATE CONSTRAINT TRIGGER sd_reference_%s AFTER INSERT OR UPDATE ON %s %s FOR EACH ROW EXECUTE FUNCTION validate_subject_retained_reference(%L)', suffix, child_name, CASE WHEN fk.condeferrable THEN
                            'DEFERRABLE INITIALLY ' || CASE WHEN fk.condeferred THEN
                                'DEFERRED'
                            ELSE
                                'IMMEDIATE'
                            END
                        ELSE
                            'NOT DEFERRABLE'
                        END, descriptor::text);
                    EXECUTE create_trigger;
                    EXECUTE format('CREATE TRIGGER a0_sd_reference_fence_%s BEFORE INSERT OR UPDATE ON %s FOR EACH ROW EXECUTE FUNCTION fence_subject_retained_reference(%L)', suffix, child_name, descriptor::text);
                    parent_body := parent_body || format(E' IF EXISTS(SELECT 1 FROM %s receipt WHERE %s) THEN\n', child_name, child_predicate);
                    IF fk.confdeltype = 'c' THEN
                        parent_body := parent_body || format(E'  IF TG_OP=''DELETE'' THEN DELETE FROM %s receipt WHERE %s; ELSE RAISE EXCEPTION ''retained receipt still references parent'' USING ERRCODE=''23503''; END IF;\n', child_name, child_predicate);
                    ELSE
                        parent_body := parent_body || E'  RAISE EXCEPTION ''retained receipt still references parent'' USING ERRCODE=''23503'';\n';
                    END IF;
                    parent_body := parent_body || E' END IF;\n';
                    select_rows := select_rows || CASE WHEN select_rows = '' THEN ''
                    ELSE
                        ' UNION '
                    END || format('SELECT %L::text AS kind,to_jsonb(receipt) AS data FROM %s receipt WHERE EXISTS(SELECT 1 FROM %s parent WHERE parent.subject_id=$1 AND %s)', fk.child_rel, child_name, parent_name, parent_predicate);
                    EXECUTE format('ALTER TABLE %s DROP CONSTRAINT %I', child_name, fk.conname);
                END LOOP;
            parent_body := parent_body || E' IF TG_OP=''DELETE'' THEN RETURN OLD; END IF; RETURN NEW;\nEND';
            EXECUTE format('CREATE FUNCTION guard_sd_retained_%I() RETURNS TRIGGER LANGUAGE plpgsql AS %L', parent.relname, parent_body);
            EXECUTE format('CREATE TRIGGER sd_retained_parent_guard BEFORE DELETE OR UPDATE ON %s FOR EACH ROW EXECUTE FUNCTION guard_sd_retained_%I()', parent_name, parent.relname);
        END LOOP;
    IF select_rows = '' THEN
        RAISE EXCEPTION 'retained receipt inventory is empty';
    END IF;
    EXECUTE format('CREATE FUNCTION schedule_subject_retained_rows(bigint) RETURNS TABLE(kind text,data jsonb) LANGUAGE sql STABLE AS %L', select_rows);
END
$$;
