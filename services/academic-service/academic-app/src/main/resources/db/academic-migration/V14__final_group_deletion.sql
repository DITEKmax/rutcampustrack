-- Exact full-group business-data deletion. Accounts, other groups and durable control receipts remain.

CREATE TABLE group_deletion_operations (
 operation_id uuid PRIMARY KEY, group_id bigint NOT NULL CHECK(group_id>0),
 actor_id bigint NOT NULL CHECK(actor_id>0), session_id uuid NOT NULL,
 session_version bigint NOT NULL CHECK(session_version>0), roles_version bigint NOT NULL CHECK(roles_version>0),
 preview_digest varchar(64) NOT NULL CHECK(preview_digest ~ '^[0-9a-f]{64}$'), local_digest varchar(64) NOT NULL CHECK(local_digest ~ '^[0-9a-f]{64}$'),
 academic_manifest jsonb NOT NULL CHECK(jsonb_typeof(academic_manifest)='array'), participant_receipts bytea NOT NULL,
 proof_receipt bytea NOT NULL, counts jsonb NOT NULL CHECK(jsonb_typeof(counts)='object'),
 phase varchar(16) NOT NULL CHECK(phase IN ('PREPARING','COMMITTING','COMPLETED','RELEASING','CANCELLED')),
 failure varchar(32), created_at timestamptz NOT NULL DEFAULT now(),updated_at timestamptz NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX group_deletion_current ON group_deletion_operations(group_id) WHERE phase NOT IN ('COMPLETED','CANCELLED');
-- Completed identity operation scopes are durable opaque evidence. They must not prevent
-- removing the business group. The new write fence below still prevents new scope adoption.
ALTER TABLE academic_identity_operation_groups DROP CONSTRAINT academic_operation_group_fk;

CREATE FUNCTION group_deletion_rows(bigint) RETURNS TABLE(kind text,data jsonb) LANGUAGE sql STABLE AS $rows$
SELECT 'groups'::text AS kind,to_jsonb(t) AS data FROM groups t WHERE id=$1
UNION ALL
SELECT 'academic_role_eligibility'::text AS kind,to_jsonb(t) AS data FROM academic_role_eligibility t WHERE group_id=$1
UNION ALL
SELECT 'student_group_history'::text AS kind,to_jsonb(t) AS data FROM student_group_history t WHERE group_id=$1
UNION ALL
SELECT 'group_history_coverage'::text AS kind,to_jsonb(t) AS data FROM group_history_coverage t WHERE group_id=$1
UNION ALL
SELECT 'headman_assistants'::text AS kind,to_jsonb(t) AS data FROM headman_assistants t WHERE group_id=$1
UNION ALL
SELECT 'subjects'::text AS kind,to_jsonb(t) AS data FROM subjects t WHERE group_id=$1
UNION ALL
SELECT 'subject_lesson_types'::text AS kind,to_jsonb(t) AS data FROM subject_lesson_types t WHERE subject_id IN (SELECT id FROM subjects WHERE group_id=$1)
UNION ALL
SELECT 'teacher_subject_groups'::text AS kind,to_jsonb(t) AS data FROM teacher_subject_groups t WHERE group_id=$1
UNION ALL
SELECT 'assignments'::text AS kind,to_jsonb(t) AS data FROM assignments t WHERE group_id=$1
UNION ALL
SELECT 'attendance_thresholds'::text AS kind,to_jsonb(t) AS data FROM attendance_thresholds t WHERE group_id=$1
UNION ALL
SELECT 'homeworks'::text AS kind,to_jsonb(t) AS data FROM homeworks t WHERE group_id=$1
UNION ALL
SELECT 'homework_weekly_digest_runs'::text AS kind,to_jsonb(t) AS data FROM homework_weekly_digest_runs t WHERE group_id=$1
UNION ALL
SELECT 'homework_completions'::text AS kind,to_jsonb(t) AS data FROM homework_completions t WHERE homework_id IN (SELECT id FROM homeworks WHERE group_id=$1)
UNION ALL
SELECT 'homework_edit_history'::text AS kind,to_jsonb(t) AS data FROM homework_edit_history t WHERE homework_id IN (SELECT id FROM homeworks WHERE group_id=$1)
UNION ALL
SELECT 'homework_edit_receipts'::text AS kind,to_jsonb(t) AS data FROM homework_edit_receipts t WHERE homework_id IN (SELECT id FROM homeworks WHERE group_id=$1)
UNION ALL
SELECT 'homework_edit_operations'::text AS kind,to_jsonb(t) AS data FROM homework_edit_operations t WHERE group_id=$1
UNION ALL
SELECT 'homework_edit_archive_admissions'::text AS kind,to_jsonb(t) AS data FROM homework_edit_archive_admissions t WHERE edit_operation_id IN (SELECT operation_id FROM homework_edit_operations WHERE group_id=$1)
UNION ALL
SELECT 'homework_binding_archives'::text AS kind,to_jsonb(t) AS data FROM homework_binding_archives t WHERE homework_id IN (SELECT id FROM homeworks WHERE group_id=$1)
UNION ALL
SELECT 'homework_binding_transfer_markers'::text AS kind,to_jsonb(t) AS data FROM homework_binding_transfer_markers t WHERE group_id=$1
UNION ALL
SELECT 'homework_binding_transfer_history'::text AS kind,to_jsonb(t) AS data FROM homework_binding_transfer_history t WHERE group_id=$1
$rows$;
CREATE FUNCTION group_deletion_manifest(bigint) RETURNS jsonb LANGUAGE sql STABLE AS $$
 SELECT coalesce(jsonb_agg(jsonb_build_object('kind',kind,'row_hash',encode(public.digest(convert_to(data::text,'UTF8'),'sha256'),'hex')) ORDER BY kind,data::text),'[]'::jsonb)
 FROM group_deletion_rows($1)
$$;
-- No CASCADE/SET NULL/SET DEFAULT effect may reach a row outside the exact target inventory.
-- Inspect actual FK metadata so existing cross-scope inconsistencies fail closed before commit.
CREATE FUNCTION group_deletion_manifest_closed(target_group bigint) RETURNS boolean LANGUAGE plpgsql STABLE AS $$
DECLARE fk record; join_keys text; outside_scope boolean;
BEGIN
 FOR fk IN SELECT c.*,ch.relname child_name,pa.relname parent_name FROM pg_constraint c
 JOIN pg_class ch ON ch.oid=c.conrelid JOIN pg_class pa ON pa.oid=c.confrelid
 WHERE c.contype='f' AND c.confdeltype IN ('c','n','d')
 AND pa.relname IN (SELECT DISTINCT kind FROM group_deletion_rows(target_group)) LOOP
  SELECT string_agg(format('child.%I=parent.%I',ca.attname,pa.attname),' AND ' ORDER BY keys.ordinality) INTO join_keys
  FROM unnest(fk.conkey,fk.confkey) WITH ORDINALITY keys(child_key,parent_key,ordinality)
  JOIN pg_attribute ca ON ca.attrelid=fk.conrelid AND ca.attnum=keys.child_key
  JOIN pg_attribute pa ON pa.attrelid=fk.confrelid AND pa.attnum=keys.parent_key;
  EXECUTE format('SELECT EXISTS(SELECT 1 FROM %s child JOIN %s parent ON %s
   JOIN group_deletion_rows($1) scope ON scope.kind=%L AND scope.data=to_jsonb(parent)
   WHERE NOT EXISTS(SELECT 1 FROM group_deletion_rows($1) own WHERE own.kind=%L AND own.data=to_jsonb(child)))',
   fk.conrelid::regclass,fk.confrelid::regclass,join_keys,fk.parent_name,fk.child_name)
   INTO outside_scope USING target_group;
  IF outside_scope THEN RETURN false; END IF;
 END LOOP;
 RETURN true;
END $$;

CREATE FUNCTION group_deletion_row_authorized(kind_value text, row_value jsonb) RETURNS boolean LANGUAGE sql STABLE AS $$
 SELECT EXISTS(SELECT 1 FROM group_deletion_operations op WHERE op.operation_id=NULLIF(current_setting('rutcampustrack.group_delete_operation_id',true),'')::uuid
 AND phase='COMMITTING' AND op.academic_manifest @> jsonb_build_array(jsonb_build_object('kind',kind_value,'row_hash',encode(public.digest(convert_to(row_value::text,'UTF8'),'sha256'),'hex'))))
$$;
CREATE FUNCTION guard_group_deletion_write() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE value jsonb; target bigint;
BEGIN
 IF TG_OP='DELETE' AND group_deletion_row_authorized(TG_TABLE_NAME,to_jsonb(OLD)) THEN RETURN OLD; END IF;
 IF NULLIF(current_setting('rutcampustrack.group_delete_operation_id',true),'') IS NOT NULL THEN
  RAISE EXCEPTION 'group deletion effect outside exact manifest' USING ERRCODE='23514';
 END IF;
 FOR value IN SELECT v FROM unnest(ARRAY[CASE WHEN TG_OP<>'INSERT' THEN to_jsonb(OLD) END,CASE WHEN TG_OP<>'DELETE' THEN to_jsonb(NEW) END]) v WHERE v IS NOT NULL LOOP
  target:=NULLIF(value->>'group_id','')::bigint;
  IF TG_TABLE_NAME='groups' THEN target:=(value->>'id')::bigint; END IF;
  IF value->>'subject_id' IS NOT NULL THEN
   PERFORM pg_advisory_xact_lock(4670544,(group_id%2147483647)::integer) FROM subjects WHERE id=(value->>'subject_id')::bigint;
   IF EXISTS(SELECT 1 FROM subjects s JOIN group_deletion_operations op ON op.group_id=s.group_id WHERE s.id=(value->>'subject_id')::bigint AND op.phase NOT IN ('COMPLETED','CANCELLED')) THEN
    RAISE EXCEPTION 'referenced subject group deletion in progress' USING ERRCODE='23514';
   END IF;
   IF target IS NULL THEN SELECT group_id INTO target FROM subjects WHERE id=(value->>'subject_id')::bigint; END IF;
  END IF;
  IF target IS NULL AND value->>'homework_id' IS NOT NULL THEN SELECT group_id INTO target FROM homeworks WHERE id=(value->>'homework_id')::bigint; END IF;
  IF target IS NULL AND value->>'edit_operation_id' IS NOT NULL THEN SELECT group_id INTO target FROM homework_edit_operations WHERE operation_id=(value->>'edit_operation_id')::uuid; END IF;

  IF target IS NOT NULL THEN
   PERFORM pg_advisory_xact_lock(4670544,(target%2147483647)::integer);
   IF EXISTS(SELECT 1 FROM group_deletion_operations WHERE group_id=target AND phase NOT IN ('COMPLETED','CANCELLED')) THEN RAISE EXCEPTION 'group deletion in progress' USING ERRCODE='23514'; END IF;
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
 JOIN pg_class c ON c.oid=t.tgrelid WHERE c.relname=ANY(ARRAY['groups','academic_role_eligibility','student_group_history','group_history_coverage','headman_assistants','subjects','subject_lesson_types','teacher_subject_groups','assignments','attendance_thresholds','homeworks','homework_weekly_digest_runs','homework_completions','homework_edit_history','homework_edit_receipts','homework_edit_operations','homework_edit_archive_admissions','homework_binding_archives','homework_binding_transfer_markers','homework_binding_transfer_history']) AND NOT t.tgisinternal LOOP
  SELECT pg_get_functiondef(item.oid) INTO definition;
  anchor:=strpos(definition,'BEGIN');
  IF anchor=0 THEN RAISE EXCEPTION 'group deletion trigger admission anchor unavailable: %',item.oid::regprocedure; END IF;
  EXECUTE overlay(definition PLACING E'BEGIN\n IF TG_OP=''DELETE'' AND group_deletion_row_authorized(TG_TABLE_NAME,to_jsonb(OLD)) THEN RETURN OLD; END IF;\n' FROM anchor FOR 5);
 END LOOP;
 FOR item IN SELECT unnest(ARRAY['groups','academic_role_eligibility','student_group_history','group_history_coverage','headman_assistants','subjects','subject_lesson_types','teacher_subject_groups','assignments','attendance_thresholds','homeworks','homework_weekly_digest_runs','homework_completions','homework_edit_history','homework_edit_receipts','homework_edit_operations','homework_edit_archive_admissions','homework_binding_archives','homework_binding_transfer_markers','homework_binding_transfer_history']) AS name LOOP
  EXECUTE format('CREATE TRIGGER a00_group_deletion_fence BEFORE INSERT OR UPDATE OR DELETE ON %I FOR EACH ROW EXECUTE FUNCTION guard_group_deletion_write()',item.name);
 END LOOP;
 -- Defer only local FKs between affected business rows, preserving CASCADE/SET NULL actions.
 -- RESTRICT becomes deferred NO ACTION with the same final integrity check.
 -- Cyclic physical/current lesson pointers are erased in the same transaction.
 FOR item IN SELECT c.conrelid::regclass AS child,c.conname,pg_get_constraintdef(c.oid) AS definition
 FROM pg_constraint c JOIN pg_class ch ON ch.oid=c.conrelid JOIN pg_class pa ON pa.oid=c.confrelid
 WHERE c.contype='f' AND ch.relname=ANY(ARRAY['groups','academic_role_eligibility','student_group_history','group_history_coverage','headman_assistants','subjects','subject_lesson_types','teacher_subject_groups','assignments','attendance_thresholds','homeworks','homework_weekly_digest_runs','homework_completions','homework_edit_history','homework_edit_receipts','homework_edit_operations','homework_edit_archive_admissions','homework_binding_archives','homework_binding_transfer_markers','homework_binding_transfer_history']) AND pa.relname=ANY(ARRAY['groups','academic_role_eligibility','student_group_history','group_history_coverage','headman_assistants','subjects','subject_lesson_types','teacher_subject_groups','assignments','attendance_thresholds','homeworks','homework_weekly_digest_runs','homework_completions','homework_edit_history','homework_edit_receipts','homework_edit_operations','homework_edit_archive_admissions','homework_binding_archives','homework_binding_transfer_markers','homework_binding_transfer_history']) LOOP
  EXECUTE format('ALTER TABLE %s DROP CONSTRAINT %I',item.child,item.conname);
  definition:=replace(item.definition,'ON DELETE RESTRICT','ON DELETE NO ACTION');
  IF strpos(definition,' DEFERRABLE')=0 THEN definition:=definition || ' DEFERRABLE INITIALLY IMMEDIATE'; END IF;
  EXECUTE format('ALTER TABLE %s ADD CONSTRAINT %I %s',item.child,item.conname,definition);
 END LOOP;
END $install$;

-- Retained operation scopes remain references to existing groups when first admitted.
CREATE FUNCTION guard_group_delete_identity_scope() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF TG_OP='DELETE' THEN RETURN OLD; END IF;
 PERFORM pg_advisory_xact_lock(4670544,(NEW.group_id%2147483647)::integer);
 IF NOT EXISTS(SELECT 1 FROM groups WHERE id=NEW.group_id) OR EXISTS(SELECT 1 FROM group_deletion_operations WHERE group_id=NEW.group_id AND phase NOT IN ('COMPLETED','CANCELLED')) THEN
  RAISE EXCEPTION 'group identity scope unavailable' USING ERRCODE='23514';
 END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER group_delete_identity_scope BEFORE INSERT OR UPDATE ON academic_identity_operation_groups
 FOR EACH ROW EXECUTE FUNCTION guard_group_delete_identity_scope();

CREATE FUNCTION guard_group_deletion_operation_identity() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF TG_OP='DELETE' THEN RAISE EXCEPTION 'group deletion receipt must be retained'; END IF;
 IF (to_jsonb(NEW)-ARRAY['phase','failure','updated_at']) IS DISTINCT FROM (to_jsonb(OLD)-ARRAY['phase','failure','updated_at'])
 OR OLD.phase IN ('COMPLETED','CANCELLED')
 OR NOT (NEW.phase=OLD.phase OR OLD.phase='PREPARING' AND NEW.phase IN ('COMMITTING','RELEASING')
  OR OLD.phase='COMMITTING' AND NEW.phase IN ('COMPLETED','RELEASING') OR OLD.phase='RELEASING' AND NEW.phase='CANCELLED') THEN
  RAISE EXCEPTION 'invalid group deletion transition';
 END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER group_deletion_operation_immutable BEFORE UPDATE OR DELETE ON group_deletion_operations
 FOR EACH ROW EXECUTE FUNCTION guard_group_deletion_operation_identity();

CREATE TRIGGER group_delete_availability_scope BEFORE INSERT ON academic_group_availability_guards
 FOR EACH ROW EXECUTE FUNCTION guard_group_delete_identity_scope();
CREATE TRIGGER group_delete_subject_scope BEFORE INSERT ON subject_deletion_operations
 FOR EACH ROW EXECUTE FUNCTION guard_group_delete_identity_scope();
CREATE FUNCTION guard_group_delete_semester_transition() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF EXISTS(SELECT 1 FROM group_deletion_operations WHERE phase NOT IN ('COMPLETED','CANCELLED'))
 AND (TG_OP='DELETE' OR (to_jsonb(OLD)-'name') IS DISTINCT FROM (to_jsonb(NEW)-'name')) THEN
  RAISE EXCEPTION 'accepted group deletion blocks semester transition' USING ERRCODE='23514';
 END IF;
 IF TG_OP='DELETE' THEN RETURN OLD; END IF; RETURN NEW;
END $$;
CREATE TRIGGER a00_group_delete_semester_transition BEFORE UPDATE OR DELETE ON semesters
 FOR EACH ROW EXECUTE FUNCTION guard_group_delete_semester_transition();

REVOKE EXECUTE ON FUNCTION group_deletion_rows(bigint),group_deletion_manifest(bigint),group_deletion_row_authorized(text,jsonb) FROM PUBLIC;
DO $$ BEGIN
 IF EXISTS(SELECT 1 FROM pg_roles WHERE rolname='rct_academic_runtime') THEN
  GRANT EXECUTE ON FUNCTION group_deletion_rows(bigint),group_deletion_manifest(bigint),group_deletion_row_authorized(text,jsonb) TO rct_academic_runtime;
 END IF;
END $$;

CREATE FUNCTION guard_threshold_group_scope() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF NEW.subject_id IS NOT NULL AND NOT EXISTS(SELECT 1 FROM subjects WHERE id=NEW.subject_id AND group_id=NEW.group_id) THEN
  RAISE EXCEPTION 'threshold subject belongs to another group' USING ERRCODE='23514';
 END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER threshold_group_scope BEFORE INSERT OR UPDATE ON attendance_thresholds
 FOR EACH ROW EXECUTE FUNCTION guard_threshold_group_scope();
REVOKE EXECUTE ON FUNCTION group_deletion_manifest_closed(bigint) FROM PUBLIC;
DO $$ BEGIN IF EXISTS(SELECT 1 FROM pg_roles WHERE rolname='rct_academic_runtime') THEN
 GRANT EXECUTE ON FUNCTION group_deletion_manifest_closed(bigint) TO rct_academic_runtime;
END IF; END $$;
