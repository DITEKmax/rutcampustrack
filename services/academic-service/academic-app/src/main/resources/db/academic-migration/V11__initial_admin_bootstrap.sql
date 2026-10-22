-- Only the explicit, own-DB operator command creates this first-admin receipt.
CREATE TABLE academic_initial_admin_bootstrap (
 singleton boolean PRIMARY KEY DEFAULT true CHECK(singleton),
 operation_id uuid NOT NULL UNIQUE,
 user_id bigint NOT NULL UNIQUE REFERENCES academic_profiles(id) ON DELETE RESTRICT,
 command_hash bytea NOT NULL CHECK(octet_length(command_hash)=32),
 profile_digest bytea NOT NULL CHECK(octet_length(profile_digest)=32),
 receipt_hash bytea NOT NULL CHECK(octet_length(receipt_hash)=32),
 academic_revision bigint NOT NULL CHECK(academic_revision=1),
 applied_auth_roles_version bigint NOT NULL CHECK(applied_auth_roles_version=1),
 finalized_catalog_version bigint CHECK(finalized_catalog_version>0),
 created_at timestamptz NOT NULL DEFAULT now()
);
CREATE FUNCTION guard_academic_initial_admin_operator() RETURNS trigger LANGUAGE plpgsql
 SET search_path=pg_catalog,public,pg_temp AS $$
BEGIN
 IF current_database()<>'academic_db' OR session_user<>'rct_migrator' THEN
  RAISE EXCEPTION 'initial administrator requires its own operator principal';
 END IF;
 IF TG_OP='DELETE' OR (TG_OP='UPDATE' AND
   ((to_jsonb(NEW)-'finalized_catalog_version') IS DISTINCT FROM (to_jsonb(OLD)-'finalized_catalog_version')
    OR OLD.finalized_catalog_version IS NOT NULL OR NEW.finalized_catalog_version IS NULL)) THEN
  RAISE EXCEPTION 'initial administrator receipt is immutable';
 END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER academic_initial_admin_receipt_guard BEFORE INSERT OR UPDATE OR DELETE
 ON academic_initial_admin_bootstrap FOR EACH ROW EXECUTE FUNCTION guard_academic_initial_admin_operator();
REVOKE ALL ON academic_initial_admin_bootstrap FROM PUBLIC;
REVOKE EXECUTE ON FUNCTION guard_academic_initial_admin_operator() FROM PUBLIC;
DO $$ BEGIN
 IF EXISTS(SELECT 1 FROM pg_roles WHERE rolname='rct_academic_runtime') THEN
  REVOKE ALL ON academic_initial_admin_bootstrap FROM rct_academic_runtime;
 END IF;
END $$;
