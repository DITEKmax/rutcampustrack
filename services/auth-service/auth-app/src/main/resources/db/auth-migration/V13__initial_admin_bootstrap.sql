-- An explicit operator trust root. No runtime API, automatic seed or invented session.
ALTER TABLE auth_identity_operations
 ADD COLUMN initial_admin_bootstrap boolean NOT NULL DEFAULT false,
 ALTER COLUMN actor_id DROP NOT NULL,
 ALTER COLUMN actor_session_id DROP NOT NULL,
 ALTER COLUMN actor_session_version DROP NOT NULL,
 ALTER COLUMN actor_roles_version DROP NOT NULL,
 ALTER COLUMN actor_credentials_version DROP NOT NULL,
 ALTER COLUMN actor_binding_version DROP NOT NULL,
 ALTER COLUMN actor_commit_roles_version DROP NOT NULL,
 ALTER COLUMN actor_admin_profile_version DROP NOT NULL;
ALTER TABLE auth_identity_operations ADD CONSTRAINT auth_bootstrap_actor_origin CHECK (
 (NOT initial_admin_bootstrap AND actor_id IS NOT NULL AND actor_session_id IS NOT NULL
  AND actor_session_version IS NOT NULL AND actor_roles_version IS NOT NULL
  AND actor_credentials_version IS NOT NULL AND actor_binding_version IS NOT NULL
  AND actor_commit_roles_version IS NOT NULL AND actor_admin_profile_version IS NOT NULL)
 OR (initial_admin_bootstrap AND kind='CREATE' AND authorization_kind='CREATE'
  AND actor_id IS NULL AND actor_session_id IS NULL AND actor_session_version IS NULL
  AND actor_roles_version IS NULL AND actor_credentials_version IS NULL
  AND actor_binding_version IS NULL AND actor_commit_roles_version IS NULL
  AND actor_admin_profile_version IS NULL));
CREATE UNIQUE INDEX auth_one_initial_admin_operation
 ON auth_identity_operations(initial_admin_bootstrap) WHERE initial_admin_bootstrap;
CREATE TABLE auth_initial_admin_bootstrap (
 singleton boolean PRIMARY KEY DEFAULT true CHECK(singleton),
 operation_id uuid NOT NULL UNIQUE REFERENCES auth_identity_operations(operation_id),
 user_id bigint NOT NULL UNIQUE REFERENCES auth_identities(id),
 command_hash bytea NOT NULL CHECK(octet_length(command_hash)=32),
 profile_digest bytea NOT NULL CHECK(octet_length(profile_digest)=32),
 login varchar(32) NOT NULL,
 created_at timestamptz NOT NULL DEFAULT now()
);

CREATE FUNCTION guard_auth_initial_admin_operator() RETURNS trigger LANGUAGE plpgsql
 SET search_path=pg_catalog,public,pg_temp AS $$
DECLARE operation uuid; bootstrap boolean;
BEGIN
 IF TG_TABLE_NAME='auth_identity_operations' THEN
  bootstrap:=CASE WHEN TG_OP='DELETE' THEN OLD.initial_admin_bootstrap ELSE NEW.initial_admin_bootstrap END;
  IF TG_OP='UPDATE' AND NEW.initial_admin_bootstrap IS DISTINCT FROM OLD.initial_admin_bootstrap THEN
   RAISE EXCEPTION 'bootstrap origin is immutable';
  END IF;
 ELSIF TG_TABLE_NAME='auth_initial_admin_bootstrap' THEN
  bootstrap:=true;
  IF TG_OP<>'INSERT' THEN RAISE EXCEPTION 'bootstrap receipt is immutable'; END IF;
 ELSE
  operation:=CASE WHEN TG_OP='DELETE' THEN OLD.operation_id ELSE NEW.operation_id END;
  IF TG_TABLE_NAME IN ('auth_identity_operation_rejections','auth_admin_access_noop_receipts') THEN
   PERFORM pg_advisory_xact_lock(724830210081::bigint);
  END IF;
  SELECT initial_admin_bootstrap INTO bootstrap FROM auth_identity_operations WHERE operation_id=operation;
 END IF;
 IF coalesce(bootstrap,false) AND TG_TABLE_NAME IN
   ('auth_identity_operation_rejections','auth_admin_access_noop_receipts') THEN
  RAISE EXCEPTION 'bootstrap operation UUID is reserved';
 END IF;
 IF coalesce(bootstrap,false) AND
   (current_database()<>'auth_db' OR session_user<>'rct_auth_migrator') THEN
  RAISE EXCEPTION 'initial administrator requires its own operator principal';
 END IF;
 IF TG_OP='DELETE' THEN RETURN OLD; END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER auth_initial_admin_origin_guard BEFORE INSERT OR UPDATE OR DELETE ON auth_identity_operations
 FOR EACH ROW EXECUTE FUNCTION guard_auth_initial_admin_operator();
CREATE TRIGGER auth_initial_admin_target_guard BEFORE INSERT OR UPDATE OR DELETE ON auth_identity_operation_targets
 FOR EACH ROW EXECUTE FUNCTION guard_auth_initial_admin_operator();
CREATE TRIGGER auth_initial_admin_receipt_guard BEFORE INSERT OR UPDATE OR DELETE ON auth_initial_admin_bootstrap
 FOR EACH ROW EXECUTE FUNCTION guard_auth_initial_admin_operator();
CREATE TRIGGER auth_initial_admin_negative_guard BEFORE INSERT ON auth_identity_operation_rejections
 FOR EACH ROW EXECUTE FUNCTION guard_auth_initial_admin_operator();
CREATE TRIGGER auth_initial_admin_noop_guard BEFORE INSERT ON auth_admin_access_noop_receipts
 FOR EACH ROW EXECUTE FUNCTION guard_auth_initial_admin_operator();

-- Deferred validation sees the whole prepared/committed local transaction.
CREATE FUNCTION check_auth_initial_admin_shape() RETURNS trigger LANGUAGE plpgsql
 SET search_path=pg_catalog,public,pg_temp AS $$
DECLARE op auth_identity_operations%ROWTYPE; receipt auth_initial_admin_bootstrap%ROWTYPE;
 target auth_identity_operation_targets%ROWTYPE; identity_row auth_identities%ROWTYPE;
BEGIN
 SELECT * INTO op FROM auth_identity_operations WHERE operation_id=NEW.operation_id;
 IF NOT op.initial_admin_bootstrap THEN RETURN NULL; END IF;
 SELECT * INTO STRICT receipt FROM auth_initial_admin_bootstrap WHERE operation_id=op.operation_id;
 IF receipt.command_hash<>op.command_hash OR
  (SELECT count(*) FROM auth_identity_operation_targets WHERE operation_id=op.operation_id)<>1 THEN
  RAISE EXCEPTION 'invalid initial administrator receipt';
 END IF;
 SELECT * INTO STRICT target FROM auth_identity_operation_targets WHERE operation_id=op.operation_id;
 SELECT * INTO STRICT identity_row FROM auth_identities WHERE id=receipt.user_id;
 IF target.user_id<>receipt.user_id OR target.expected_roles_version<>0 OR target.final_roles_version<>1
  OR target.expected_academic_revision<>0 OR target.prepared_credentials_version<>1
  OR target.prepared_binding_version<>1 OR target.final_binding_version<>1
  OR target.desired_account_state<>'ACTIVE'
  OR target.desired_grants<>'[{"role":"admin","status":"active","group_id":null}]'::jsonb
  OR target.desired_admin_profile<>'{"assigned":true,"mode":"FULL","permissions":[]}'::jsonb
  OR identity_row.provision_operation_id<>op.operation_id OR identity_row.login<>receipt.login THEN
  RAISE EXCEPTION 'invalid initial administrator shape';
 END IF;
 IF op.phase='PREPARED_FENCED' AND
  (identity_row.account_state<>'PENDING' OR identity_row.fence_operation_id IS DISTINCT FROM op.operation_id
   OR target.committed_academic_revision IS NOT NULL) THEN
  RAISE EXCEPTION 'initial administrator is not safely pending';
 END IF;
 IF op.phase='COMMITTED' AND target.committed_academic_revision IS DISTINCT FROM 1::bigint THEN
  RAISE EXCEPTION 'initial administrator requires its Academic receipt';
 END IF;
 RETURN NULL;
END $$;
CREATE CONSTRAINT TRIGGER auth_initial_admin_shape AFTER INSERT OR UPDATE ON auth_identity_operations
 DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION check_auth_initial_admin_shape();
REVOKE ALL ON auth_initial_admin_bootstrap FROM PUBLIC;
REVOKE EXECUTE ON FUNCTION guard_auth_initial_admin_operator(),check_auth_initial_admin_shape() FROM PUBLIC;
DO $$ BEGIN
 IF EXISTS(SELECT 1 FROM pg_roles WHERE rolname='rct_auth_runtime') THEN
  REVOKE ALL ON auth_initial_admin_bootstrap FROM rct_auth_runtime;
 END IF;
END $$;
