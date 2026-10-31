-- Group-scoped removal does not archive identities or invalidate unrelated role sessions.
ALTER TABLE auth_password_step_up_receipts DROP CONSTRAINT auth_password_step_up_receipts_purpose_check;
ALTER TABLE auth_password_step_up_receipts ADD CONSTRAINT auth_password_step_up_receipts_purpose_check
 CHECK(purpose IN ('USER_ARCHIVE','SEMESTER_DELETE','MAP_FLOOR_DELETE','MAP_BUILDING_DELETE','GROUP_DELETE'));
CREATE TABLE auth_group_deletion_participants (
 operation_id uuid PRIMARY KEY,group_id bigint NOT NULL CHECK(group_id>0),actor_id bigint NOT NULL CHECK(actor_id>0),
 participant_digest varchar(64) NOT NULL,preview_digest varchar(64) NOT NULL,
 manifest jsonb NOT NULL,counts jsonb NOT NULL,password_receipt bytea NOT NULL,
 state varchar(16) NOT NULL CHECK(state IN ('PREPARED','SEALED','COMMITTING','DELETED','RELEASED'))
);
CREATE UNIQUE INDEX auth_group_deletion_current ON auth_group_deletion_participants(group_id) WHERE state<>'RELEASED';
CREATE FUNCTION guard_auth_group_deletion_grant() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE value jsonb; target bigint; op uuid;
BEGIN
 FOR value IN SELECT v FROM unnest(ARRAY[CASE WHEN TG_OP<>'INSERT' THEN to_jsonb(OLD) END,CASE WHEN TG_OP<>'DELETE' THEN to_jsonb(NEW) END]) v WHERE v IS NOT NULL LOOP
  target:=NULLIF(value->>'group_id','')::bigint;
  IF target IS NOT NULL THEN
   PERFORM pg_advisory_xact_lock(4670544,(target%2147483647)::integer);
   SELECT operation_id INTO op FROM auth_group_deletion_participants WHERE group_id=target AND state<>'RELEASED';
   IF op IS NOT NULL AND NOT (TG_OP='DELETE' AND op=NULLIF(current_setting('rutcampustrack.group_delete_operation_id',true),'')::uuid
     AND EXISTS(SELECT 1 FROM auth_group_deletion_participants p WHERE p.operation_id=op AND p.state='COMMITTING'
       AND p.manifest @> jsonb_build_array(to_jsonb(OLD)))) THEN
    RAISE EXCEPTION 'group authority is sealed for deletion' USING ERRCODE='23514';
   END IF;
  END IF;
 END LOOP;
 IF TG_OP='DELETE' THEN RETURN OLD; END IF; RETURN NEW;
END $$;
CREATE TRIGGER auth_group_deletion_grant_fence BEFORE INSERT OR UPDATE OR DELETE ON user_role_grants
 FOR EACH ROW EXECUTE FUNCTION guard_auth_group_deletion_grant();
-- Existing append_auth_identity_catalog_snapshot publishes the grant removal without global RV changes.
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
CREATE TRIGGER group_deletion_participant_immutable BEFORE UPDATE OR DELETE ON auth_group_deletion_participants
 FOR EACH ROW EXECUTE FUNCTION guard_group_deletion_participant_identity();

-- Do not let a newly prepared account-wide identity fence strand group-only COMMIT.
-- Credential changes and unrelated selected role sessions retain their existing behavior.
CREATE FUNCTION guard_group_deletion_identity_prepare() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF OLD.fence_operation_id IS NULL AND NEW.fence_operation_id IS NOT NULL
 AND EXISTS(SELECT 1 FROM user_role_grants g JOIN auth_group_deletion_participants d USING(group_id)
  WHERE g.user_id=OLD.id AND d.state NOT IN ('DELETED','RELEASED')) THEN
  RAISE EXCEPTION 'identity scope is sealed for group deletion' USING ERRCODE='23514';
 END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER a00_group_deletion_identity_prepare BEFORE UPDATE ON auth_identities
 FOR EACH ROW EXECUTE FUNCTION guard_group_deletion_identity_prepare();
