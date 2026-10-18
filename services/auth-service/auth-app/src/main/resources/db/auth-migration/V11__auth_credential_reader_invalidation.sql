-- Conservative invalidation of stale Academic HEADMAN credential preflights.
-- No domain tables, grant/account mutation, or identity-wide admission fence.
CREATE TABLE auth_credential_reader_invalidations (
 operation_id uuid PRIMARY KEY,
 command_hash bytea NOT NULL CHECK(octet_length(command_hash)=32),
 canonical_command bytea NOT NULL CHECK(octet_length(canonical_command) BETWEEN 1 AND 1048576),
 action varchar(32) NOT NULL CHECK(action IN ('GROUP_DEACTIVATE','GROUP_RESTORE','MANUAL_CYCLE_ARCHIVE','AUTOMATIC_CYCLE_ARCHIVE')),
 automatic boolean NOT NULL,
 actor_id bigint REFERENCES auth_identities(id), actor_session_id uuid,
 actor_session_version bigint, actor_commit_roles_version bigint,
 actor_credentials_version bigint, actor_binding_version bigint, actor_profile_version bigint,
 phase varchar(16) NOT NULL DEFAULT 'INVALIDATED' CHECK(phase IN ('INVALIDATED','COMMITTED','FAILED_SAFE')),
 revision bigint NOT NULL DEFAULT 1 CHECK(revision>0),
 committed_catalog_version bigint CHECK(committed_catalog_version>=0),
 commit_receipt bytea,
 supersedes_operation_id uuid REFERENCES auth_credential_reader_invalidations(operation_id),
 superseded_by_operation_id uuid REFERENCES auth_credential_reader_invalidations(operation_id) DEFERRABLE INITIALLY DEFERRED,
 created_at timestamptz NOT NULL DEFAULT now(),
 CHECK((phase='COMMITTED')=(committed_catalog_version IS NOT NULL)),
 CHECK((phase='COMMITTED')=(commit_receipt IS NOT NULL)),
 CHECK(((automatic AND actor_id IS NULL AND actor_session_id IS NULL AND actor_session_version IS NULL
   AND actor_commit_roles_version IS NULL AND actor_credentials_version IS NULL
   AND actor_binding_version IS NULL AND actor_profile_version IS NULL)
 OR (NOT automatic AND actor_id IS NOT NULL AND actor_session_id IS NOT NULL
   AND actor_session_version IS NOT NULL AND actor_session_version>0
   AND actor_commit_roles_version IS NOT NULL AND actor_commit_roles_version>0
   AND actor_credentials_version IS NOT NULL AND actor_credentials_version>0
   AND actor_binding_version IS NOT NULL AND actor_binding_version>0 AND actor_profile_version IS NOT NULL AND actor_profile_version>0)) IS TRUE),
 CHECK(superseded_by_operation_id IS NULL OR phase='FAILED_SAFE')
);
CREATE TABLE auth_credential_reader_groups (
 operation_id uuid NOT NULL REFERENCES auth_credential_reader_invalidations(operation_id),
 group_id bigint NOT NULL CHECK(group_id>0),
 expected_group_revision bigint NOT NULL CHECK(expected_group_revision>0),
 headman_user_id bigint REFERENCES auth_identities(id),
 expected_roles_version bigint, final_roles_version bigint,
 expected_academic_revision bigint,
 committed_group_revision bigint, committed_academic_revision bigint,
 reserved boolean NOT NULL DEFAULT true,
 PRIMARY KEY(operation_id,group_id), UNIQUE(operation_id,headman_user_id),
 CHECK((headman_user_id IS NULL AND expected_roles_version IS NULL AND final_roles_version IS NULL
   AND expected_academic_revision IS NULL AND committed_academic_revision IS NULL)
 OR (headman_user_id IS NOT NULL AND expected_roles_version IS NOT NULL AND final_roles_version IS NOT NULL
   AND expected_academic_revision IS NOT NULL AND expected_roles_version>0
   AND final_roles_version=expected_roles_version+1 AND expected_academic_revision>0)),
 CHECK(committed_group_revision IS NULL OR committed_group_revision=expected_group_revision+1),
 CHECK(committed_academic_revision IS NULL OR committed_academic_revision=expected_academic_revision+1)
);
CREATE UNIQUE INDEX auth_one_pending_credential_reader_group ON auth_credential_reader_groups(group_id) WHERE reserved;
CREATE FUNCTION protect_auth_credential_reader_operation() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF TG_OP='DELETE' THEN RAISE EXCEPTION 'credential-reader receipt is immutable'; END IF;
 IF (to_jsonb(NEW)-ARRAY['phase','revision','committed_catalog_version','commit_receipt','superseded_by_operation_id'])
  IS DISTINCT FROM (to_jsonb(OLD)-ARRAY['phase','revision','committed_catalog_version','commit_receipt','superseded_by_operation_id'])
  OR NEW.revision<>OLD.revision+1 OR OLD.phase='COMMITTED'
  OR (OLD.phase='FAILED_SAFE' AND (NEW.phase<>'FAILED_SAFE' OR OLD.superseded_by_operation_id IS NOT NULL))
  OR (OLD.phase='INVALIDATED' AND NEW.phase NOT IN ('COMMITTED','FAILED_SAFE')) THEN
  RAISE EXCEPTION 'invalid credential-reader receipt transition';
 END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER auth_credential_reader_operation_guard BEFORE UPDATE OR DELETE ON auth_credential_reader_invalidations
 FOR EACH ROW EXECUTE FUNCTION protect_auth_credential_reader_operation();
CREATE FUNCTION protect_auth_credential_reader_group() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF TG_OP='DELETE' THEN RAISE EXCEPTION 'credential-reader manifest is immutable'; END IF;
 IF (to_jsonb(NEW)-ARRAY['committed_group_revision','committed_academic_revision','reserved'])
  IS DISTINCT FROM (to_jsonb(OLD)-ARRAY['committed_group_revision','committed_academic_revision','reserved'])
  OR (OLD.committed_group_revision IS NOT NULL AND NEW.committed_group_revision IS DISTINCT FROM OLD.committed_group_revision)
  OR (OLD.committed_academic_revision IS NOT NULL AND NEW.committed_academic_revision IS DISTINCT FROM OLD.committed_academic_revision)
  OR (NOT OLD.reserved AND NEW.reserved) THEN RAISE EXCEPTION 'invalid credential-reader manifest transition'; END IF;
 IF OLD.reserved AND NOT NEW.reserved AND NOT EXISTS(SELECT 1 FROM auth_credential_reader_invalidations o
   WHERE o.operation_id=NEW.operation_id AND (o.phase='COMMITTED' OR o.superseded_by_operation_id IS NOT NULL))
 THEN RAISE EXCEPTION 'credential-reader reservation requires durable resolution'; END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER auth_credential_reader_group_guard BEFORE UPDATE OR DELETE ON auth_credential_reader_groups
 FOR EACH ROW EXECUTE FUNCTION protect_auth_credential_reader_group();

CREATE OR REPLACE FUNCTION guard_auth_identity_versions() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.id<>OLD.id OR NEW.login<>OLD.login OR NEW.provision_operation_id<>OLD.provision_operation_id
        OR NEW.roles_version<OLD.roles_version OR NEW.roles_version>OLD.roles_version+1 THEN
        RAISE EXCEPTION 'invalid identity generation';
    END IF;
    IF NEW.roles_version<>OLD.roles_version AND NEW.fence_operation_id IS NULL
      AND NOT (OLD.fence_operation_id IS NULL
       AND (to_jsonb(NEW)-ARRAY['roles_version','updated_at','catalog_version'])
          IS NOT DISTINCT FROM (to_jsonb(OLD)-ARRAY['roles_version','updated_at','catalog_version'])
       AND EXISTS(SELECT 1 FROM auth_credential_reader_groups t
         JOIN auth_credential_reader_invalidations o USING(operation_id)
         WHERE t.headman_user_id=OLD.id AND t.reserved AND o.phase='INVALIDATED'
           AND t.expected_roles_version=OLD.roles_version AND t.final_roles_version=NEW.roles_version)) THEN
        RAISE EXCEPTION 'new authority generation requires a prepared fence';
    END IF;
    IF OLD.fence_operation_id IS NOT NULL AND NEW.fence_operation_id IS NULL
        AND NOT EXISTS (SELECT 1 FROM auth_identity_operations o
            JOIN auth_identity_operation_targets t ON t.operation_id=o.operation_id
            WHERE o.operation_id=OLD.fence_operation_id AND o.phase='COMMITTED'
              AND t.user_id=NEW.id AND t.final_roles_version=NEW.roles_version
              AND t.committed_academic_revision IS NOT NULL) THEN
        RAISE EXCEPTION 'fence release requires an exact committed Academic receipt';
    END IF;
    IF OLD.account_state='PENDING' AND NEW.account_state<>'PENDING'
        AND NEW.fence_operation_id IS NOT NULL THEN
        RAISE EXCEPTION 'pending identity activation requires committed fence release';
    END IF;
    IF ROW(NEW.password_hash,NEW.initial_password,NEW.password_changed)
        IS DISTINCT FROM ROW(OLD.password_hash,OLD.initial_password,OLD.password_changed) THEN
        IF NEW.credentials_version<>OLD.credentials_version THEN
            RAISE EXCEPTION 'credential version is tuple maintained';
        END IF;
        NEW.credentials_version:=OLD.credentials_version+1;
    ELSIF NEW.credentials_version<>OLD.credentials_version THEN
        RAISE EXCEPTION 'credential version is tuple maintained';
    END IF;
    IF NEW.telegram_id IS DISTINCT FROM OLD.telegram_id THEN
        IF NEW.telegram_binding_version<>OLD.telegram_binding_version THEN
            RAISE EXCEPTION 'binding version is tuple maintained';
        END IF;
        NEW.telegram_binding_version:=OLD.telegram_binding_version+1;
    ELSIF NEW.telegram_binding_version<>OLD.telegram_binding_version THEN
        RAISE EXCEPTION 'binding version is tuple maintained';
    END IF;
    RETURN NEW;
END $$;

CREATE FUNCTION append_auth_identity_catalog_snapshot(identity_id bigint) RETURNS bigint LANGUAGE plpgsql AS $$
DECLARE next_version bigint; state_code text; eligibility_revision bigint; grants jsonb;
 identity_row auth_identities%ROWTYPE;
BEGIN
 SELECT * INTO STRICT identity_row FROM auth_identities WHERE id=identity_id;
    -- A row counter (not a sequence) is transactional: rollback leaves no gap.
    UPDATE auth_identity_catalog_clock SET version=version+1 WHERE singleton=1
        RETURNING version INTO next_version;
    IF next_version IS NULL THEN RAISE EXCEPTION 'identity catalog clock unavailable'; END IF;
    state_code:=CASE WHEN identity_row.account_state='PENDING' THEN 'PENDING'
                     WHEN identity_row.fence_operation_id IS NOT NULL THEN 'FENCED'
                     ELSE identity_row.account_state END;
    SELECT coalesce(jsonb_agg(jsonb_build_object('grant_id',g.id,'role',upper(g.role),
        'status',upper(g.status),'group_id',g.group_id) ORDER BY g.id),'[]'::jsonb)
        INTO grants FROM user_role_grants g WHERE g.user_id=identity_row.id;
    SELECT coalesce(max(committed_academic_revision),0) INTO eligibility_revision FROM (
      SELECT t.committed_academic_revision FROM auth_identity_operation_targets t
       JOIN auth_identity_operations o USING(operation_id) WHERE t.user_id=identity_row.id AND o.phase='COMMITTED'
      UNION ALL SELECT g.committed_academic_revision FROM auth_credential_reader_groups g
       JOIN auth_credential_reader_invalidations o USING(operation_id)
       WHERE g.headman_user_id=identity_row.id AND o.phase='COMMITTED') revisions;
    INSERT INTO auth_identity_catalog_changes(catalog_version,user_id,projection)
        VALUES(next_version,identity_row.id,jsonb_build_object(
            'user_id',identity_row.id,'login',identity_row.login,'account_state',state_code,
            'roles_version',identity_row.roles_version,'telegram_binding_version',identity_row.telegram_binding_version,
            'telegram_id',identity_row.telegram_id,'telegram_username',identity_row.telegram_username,
            'password_changed',identity_row.password_changed,'grants',grants,
            'academic_revision',eligibility_revision));
    UPDATE auth_identities SET catalog_version=next_version WHERE id=identity_row.id;
 RETURN next_version;
END $$;
CREATE OR REPLACE FUNCTION capture_auth_identity_catalog() RETURNS TRIGGER LANGUAGE plpgsql AS $$
DECLARE next_version BIGINT; state_code TEXT; eligibility_revision BIGINT; grants JSONB;
BEGIN
    IF TG_OP='UPDATE' AND ROW(NEW.login,NEW.account_state,NEW.roles_version,
        NEW.telegram_id,NEW.telegram_username,NEW.telegram_binding_version,
        NEW.password_changed,NEW.fence_operation_id)
        IS NOT DISTINCT FROM ROW(OLD.login,OLD.account_state,OLD.roles_version,
        OLD.telegram_id,OLD.telegram_username,OLD.telegram_binding_version,
        OLD.password_changed,OLD.fence_operation_id) THEN RETURN NULL; END IF;
    PERFORM append_auth_identity_catalog_snapshot(NEW.id);
    RETURN NULL;
END $$;
REVOKE ALL ON auth_credential_reader_invalidations,auth_credential_reader_groups FROM PUBLIC;
REVOKE EXECUTE ON FUNCTION protect_auth_credential_reader_operation(),protect_auth_credential_reader_group(),append_auth_identity_catalog_snapshot(bigint) FROM PUBLIC;
DO $$ BEGIN
 IF EXISTS(SELECT 1 FROM pg_roles WHERE rolname='rct_auth_runtime') THEN
   GRANT EXECUTE ON FUNCTION append_auth_identity_catalog_snapshot(bigint) TO rct_auth_runtime;
 END IF;
END $$;

-- Atomic negative reservation excludes a delayed concurrent Prepare for this UUID.
CREATE TABLE auth_credential_reader_rejections (
 operation_id uuid PRIMARY KEY,
 command_hash bytea NOT NULL CHECK(octet_length(command_hash)=32),
 canonical_command bytea NOT NULL CHECK(octet_length(canonical_command) BETWEEN 1 AND 1048576),
 automatic boolean NOT NULL,
 original_actor bytea,
 reason varchar(24) NOT NULL CHECK(reason IN ('ACTOR_DENIED','TARGET_CONFLICT','ACADEMIC_REJECTED')),
 created_at timestamptz NOT NULL DEFAULT now(),
 CHECK(automatic=(original_actor IS NULL))
);
CREATE TRIGGER auth_credential_reader_rejection_immutable BEFORE UPDATE OR DELETE ON auth_credential_reader_rejections
 FOR EACH ROW EXECUTE FUNCTION protect_auth_identity_catalog_change();
REVOKE ALL ON auth_credential_reader_rejections FROM PUBLIC;
