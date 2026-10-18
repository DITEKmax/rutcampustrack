-- A prepared immutable command is not permission to grant after actor revocation.
-- Store only nonsecret generations; never retain an actor JWT or refresh proof.
ALTER TABLE auth_identity_operations
    ADD COLUMN authorization_kind VARCHAR(32) NOT NULL CHECK(authorization_kind IN
        ('CREATE','ROLE','TRANSFER','HEADMAN_REPLACE','ARCHIVE','RESTORE','ADMIN_ACCESS','TELEGRAM_BINDING')),
    ADD COLUMN actor_credentials_version BIGINT NOT NULL CHECK(actor_credentials_version>0),
    ADD COLUMN actor_binding_version BIGINT NOT NULL CHECK(actor_binding_version>0),
    ADD COLUMN actor_commit_roles_version BIGINT NOT NULL CHECK(actor_commit_roles_version>0),
    ADD COLUMN actor_admin_profile_version BIGINT NOT NULL CHECK(actor_admin_profile_version>0);
ALTER TABLE auth_identity_operation_targets
    ADD COLUMN final_binding_version BIGINT NOT NULL CHECK(final_binding_version>0);

CREATE OR REPLACE FUNCTION guard_auth_identity_operation() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP='DELETE' THEN RAISE EXCEPTION 'identity operation result must be retained'; END IF;
    IF (to_jsonb(NEW)-ARRAY['phase','revision','error_code','updated_at','finished_at','committed_catalog_version'])
        IS DISTINCT FROM
       (to_jsonb(OLD)-ARRAY['phase','revision','error_code','updated_at','finished_at','committed_catalog_version'])
        OR OLD.phase<>'PREPARED_FENCED'
        OR NEW.phase NOT IN ('COMMITTED','FAILED_SAFE')
        OR NEW.revision<>OLD.revision+1 THEN
        RAISE EXCEPTION 'invalid identity operation transition';
    END IF;
    RETURN NEW;
END $$;
REVOKE EXECUTE ON FUNCTION guard_auth_identity_operation() FROM PUBLIC;
