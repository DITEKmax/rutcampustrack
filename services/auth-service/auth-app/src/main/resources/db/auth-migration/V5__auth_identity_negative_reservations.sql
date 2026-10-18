-- A negative result is serialized with positive PREPARE by the same Auth local
-- advisory operation lock. It never references/changes target authority rows.
CREATE TABLE auth_identity_operation_rejections (
    operation_id UUID PRIMARY KEY,
    command_hash BYTEA NOT NULL CHECK(octet_length(command_hash)=32),
    canonical_command BYTEA NOT NULL,
    kind VARCHAR(32) NOT NULL CHECK(kind IN ('CREATE','ROLE','TRANSFER','HEADMAN_REPLACE','ARCHIVE','RESTORE','ADMIN_ACCESS','TELEGRAM_BINDING','RECONCILE')),
    actor_id BIGINT NOT NULL REFERENCES auth_identities(id),
    actor_session_id UUID NOT NULL,
    actor_session_version BIGINT NOT NULL CHECK(actor_session_version>0),
    actor_roles_version BIGINT NOT NULL CHECK(actor_roles_version>0),
    reason VARCHAR(32) NOT NULL CHECK(reason IN ('ACTOR_DENIED','TARGET_CONFLICT','ACADEMIC_REJECTED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE FUNCTION guard_auth_identity_rejection() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'identity rejection must be retained unchanged';
END $$;
CREATE TRIGGER auth_identity_rejection_immutable BEFORE UPDATE OR DELETE
ON auth_identity_operation_rejections FOR EACH ROW EXECUTE FUNCTION guard_auth_identity_rejection();
REVOKE EXECUTE ON FUNCTION guard_auth_identity_rejection() FROM PUBLIC;
