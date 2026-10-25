-- Exact full-group business-data deletion. Accounts, other groups and durable control receipts remain.

CREATE TABLE group_deletion_participants (
 operation_id uuid PRIMARY KEY, group_id bigint NOT NULL CHECK(group_id>0), actor_id bigint NOT NULL CHECK(actor_id>0),
 participant_digest varchar(64) NOT NULL, preview_digest varchar(64) NOT NULL,
 manifest jsonb NOT NULL, counts jsonb NOT NULL, command_manifest bytea NOT NULL,
 state varchar(16) NOT NULL CHECK(state IN ('PREPARED','SEALED','COMMITTING','DELETED','RELEASED'))
);
CREATE UNIQUE INDEX group_deletion_current ON group_deletion_participants(group_id) WHERE state<>'RELEASED';

CREATE FUNCTION group_deletion_rows(bigint) RETURNS TABLE(kind text,data jsonb) LANGUAGE sql STABLE AS $rows$
SELECT 'schedule_items'::text AS kind,to_jsonb(t) AS data FROM schedule_items t WHERE group_id=$1
UNION ALL
SELECT 'schedule_one_off_lessons'::text AS kind,to_jsonb(t) AS data FROM schedule_one_off_lessons t WHERE group_id=$1
UNION ALL
SELECT 'lesson_occurrences'::text AS kind,to_jsonb(t) AS data FROM lesson_occurrences t WHERE group_id=$1
UNION ALL
SELECT 'lessons'::text AS kind,to_jsonb(t) AS data FROM lessons t WHERE group_id=$1
UNION ALL
SELECT 'lesson_homework_bindings'::text AS kind,to_jsonb(t) AS data FROM lesson_homework_bindings t WHERE group_id=$1
UNION ALL
SELECT 'schedule_assignment_fences'::text AS kind,to_jsonb(t) AS data FROM schedule_assignment_fences t WHERE group_id=$1
UNION ALL
SELECT 'lesson_lifecycle_entries'::text AS kind,to_jsonb(t) AS data FROM lesson_lifecycle_entries t WHERE occurrence_id IN (SELECT id FROM lesson_occurrences WHERE group_id=$1)
$rows$;
CREATE FUNCTION group_deletion_manifest(bigint) RETURNS jsonb LANGUAGE sql STABLE AS $$
 SELECT coalesce(jsonb_agg(jsonb_build_object('kind',kind,'row_hash',encode(public.digest(convert_to(data::text,'UTF8'),'sha256'),'hex')) ORDER BY kind,data::text),'[]'::jsonb)
 FROM group_deletion_rows($1)
$$;
CREATE FUNCTION group_deletion_row_authorized(kind_value text, row_value jsonb) RETURNS boolean LANGUAGE sql STABLE AS $$
 SELECT EXISTS(SELECT 1 FROM group_deletion_participants op WHERE op.operation_id=NULLIF(current_setting('rutcampustrack.group_delete_operation_id',true),'')::uuid
 AND state='COMMITTING' AND op.manifest @> jsonb_build_array(jsonb_build_object('kind',kind_value,'row_hash',encode(public.digest(convert_to(row_value::text,'UTF8'),'sha256'),'hex'))))
$$;
CREATE FUNCTION guard_group_deletion_write() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE value jsonb; target bigint;
BEGIN
 IF TG_OP='DELETE' AND group_deletion_row_authorized(TG_TABLE_NAME,to_jsonb(OLD)) THEN RETURN OLD; END IF;
 FOR value IN SELECT v FROM unnest(ARRAY[CASE WHEN TG_OP<>'INSERT' THEN to_jsonb(OLD) END,CASE WHEN TG_OP<>'DELETE' THEN to_jsonb(NEW) END]) v WHERE v IS NOT NULL LOOP
  target:=NULLIF(value->>'group_id','')::bigint;




  IF target IS NULL AND value->>'occurrence_id' IS NOT NULL THEN SELECT group_id INTO target FROM lesson_occurrences WHERE id=(value->>'occurrence_id')::bigint; END IF;
  IF target IS NOT NULL THEN
   PERFORM pg_advisory_xact_lock(4670544,(target%2147483647)::integer);
   IF EXISTS(SELECT 1 FROM group_deletion_participants WHERE group_id=target AND state<>'RELEASED') THEN RAISE EXCEPTION 'group deletion in progress' USING ERRCODE='23514'; END IF;
  END IF;
 END LOOP;
 IF TG_OP='DELETE' THEN RETURN OLD; END IF; RETURN NEW;
END $$;
-- Keep every old rule for ordinary operations. Only an exact DELETE from this operation's
-- sealed immutable manifest is admitted before the pre-existing rule executes.
DO $install$
DECLARE item record; definition text; anchor integer;
BEGIN
 FOR item IN SELECT DISTINCT p.oid FROM pg_trigger t JOIN pg_proc p ON p.oid=t.tgfoid
 JOIN pg_class c ON c.oid=t.tgrelid WHERE c.relname=ANY(ARRAY['schedule_items','schedule_one_off_lessons','lesson_occurrences','lessons','lesson_homework_bindings','schedule_assignment_fences','lesson_lifecycle_entries']) AND NOT t.tgisinternal LOOP
  SELECT pg_get_functiondef(item.oid) INTO definition;
  anchor:=strpos(definition,'BEGIN');
  IF anchor=0 THEN RAISE EXCEPTION 'group deletion trigger admission anchor unavailable: %',item.oid::regprocedure; END IF;
  EXECUTE overlay(definition PLACING E'BEGIN\n IF TG_OP=''DELETE'' AND group_deletion_row_authorized(TG_TABLE_NAME,to_jsonb(OLD)) THEN RETURN OLD; END IF;\n' FROM anchor FOR 5);
 END LOOP;
 FOR item IN SELECT unnest(ARRAY['schedule_items','schedule_one_off_lessons','lesson_occurrences','lessons','lesson_homework_bindings','schedule_assignment_fences','lesson_lifecycle_entries']) AS name LOOP
  EXECUTE format('CREATE TRIGGER a00_group_deletion_fence BEFORE INSERT OR UPDATE OR DELETE ON %I FOR EACH ROW EXECUTE FUNCTION guard_group_deletion_write()',item.name);
 END LOOP;
 -- Defer only local FKs between affected business rows, preserving CASCADE/SET NULL actions.
 -- RESTRICT becomes deferred NO ACTION with the same final integrity check.
 -- Cyclic physical/current lesson pointers are erased in the same transaction.
 FOR item IN SELECT c.conrelid::regclass AS child,c.conname,pg_get_constraintdef(c.oid) AS definition
 FROM pg_constraint c JOIN pg_class ch ON ch.oid=c.conrelid JOIN pg_class pa ON pa.oid=c.confrelid
 WHERE c.contype='f' AND ch.relname=ANY(ARRAY['schedule_items','schedule_one_off_lessons','lesson_occurrences','lessons','lesson_homework_bindings','schedule_assignment_fences','lesson_lifecycle_entries']) AND pa.relname=ANY(ARRAY['schedule_items','schedule_one_off_lessons','lesson_occurrences','lessons','lesson_homework_bindings','schedule_assignment_fences','lesson_lifecycle_entries']) LOOP
  EXECUTE format('ALTER TABLE %s DROP CONSTRAINT %I',item.child,item.conname);
  definition:=replace(item.definition,'ON DELETE RESTRICT','ON DELETE NO ACTION');
  IF strpos(definition,' DEFERRABLE')=0 THEN definition:=definition || ' DEFERRABLE INITIALLY IMMEDIATE'; END IF;
  EXECUTE format('ALTER TABLE %s ADD CONSTRAINT %I %s',item.child,item.conname,definition);
 END LOOP;
END $install$;

CREATE FUNCTION guard_group_deletion_participant_identity() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF TG_OP='DELETE' THEN RAISE EXCEPTION 'group deletion participant receipt must be retained'; END IF;
 IF (to_jsonb(NEW)-'state') IS DISTINCT FROM (to_jsonb(OLD)-'state') OR OLD.state IN ('DELETED','RELEASED')
 OR NOT (NEW.state=OLD.state OR OLD.state='PREPARED' AND NEW.state IN ('SEALED','RELEASED')
  OR OLD.state='SEALED' AND NEW.state IN ('COMMITTING','RELEASED') OR OLD.state='COMMITTING' AND NEW.state='DELETED') THEN
  RAISE EXCEPTION 'invalid group deletion participant transition';
 END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER group_deletion_participant_immutable BEFORE UPDATE OR DELETE ON group_deletion_participants
 FOR EACH ROW EXECUTE FUNCTION guard_group_deletion_participant_identity();

CREATE TRIGGER a00_group_deletion_subject_prepare BEFORE INSERT ON schedule_subject_deletion_participants
 FOR EACH ROW EXECUTE FUNCTION guard_group_deletion_write();

REVOKE EXECUTE ON FUNCTION group_deletion_rows(bigint),group_deletion_manifest(bigint),group_deletion_row_authorized(text,jsonb) FROM PUBLIC;
DO $$ BEGIN
 IF EXISTS(SELECT 1 FROM pg_roles WHERE rolname='rct_runtime') THEN
  GRANT EXECUTE ON FUNCTION group_deletion_rows(bigint),group_deletion_manifest(bigint),group_deletion_row_authorized(text,jsonb) TO rct_runtime;
 END IF;
END $$;
