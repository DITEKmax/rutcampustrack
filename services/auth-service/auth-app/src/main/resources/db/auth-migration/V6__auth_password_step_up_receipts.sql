-- Purpose-bound security admission only. Resource IDs/digests are opaque; no
-- Academic table, preview material, password, or bearer is stored here.
CREATE TABLE auth_password_step_up_receipts (
    receipt_id UUID PRIMARY KEY,
    purpose VARCHAR(32) NOT NULL CHECK(purpose IN ('USER_ARCHIVE','SEMESTER_DELETE','MAP_FLOOR_DELETE','MAP_BUILDING_DELETE')),
    target_id BIGINT NOT NULL CHECK(target_id>0),
    operation_id UUID NOT NULL,
    preview_digest VARCHAR(64) NOT NULL CHECK(preview_digest ~ '^[0-9a-f]{64}$'),
    actor_id BIGINT NOT NULL REFERENCES auth_identities(id),
    session_id UUID NOT NULL,
    session_version BIGINT NOT NULL CHECK(session_version>0),
    roles_version BIGINT NOT NULL CHECK(roles_version>0),
    credentials_version BIGINT NOT NULL CHECK(credentials_version>0),
    binding_version BIGINT NOT NULL CHECK(binding_version>0),
    admin_profile_version BIGINT NOT NULL CHECK(admin_profile_version>0),
    created_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL CHECK(expires_at>created_at),
    UNIQUE(operation_id,purpose,actor_id,session_id)
);
CREATE FUNCTION guard_auth_password_step_up_receipt() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'password step-up receipt must be retained unchanged';
END $$;
CREATE TRIGGER auth_password_step_up_receipt_immutable BEFORE UPDATE OR DELETE
ON auth_password_step_up_receipts FOR EACH ROW EXECUTE FUNCTION guard_auth_password_step_up_receipt();
REVOKE EXECUTE ON FUNCTION guard_auth_password_step_up_receipt() FROM PUBLIC;
