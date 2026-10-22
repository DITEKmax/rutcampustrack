-- Terminal ADMIN_ACCESS unchanged receipts only. No identity/grant/profile/catalog
-- mutation belongs to this table; real mutations retain their existing ledger/fences.
CREATE TABLE auth_admin_access_noop_receipts (
    operation_id UUID PRIMARY KEY,
    command_hash BYTEA NOT NULL CHECK (octet_length(command_hash)=32),
    canonical_command BYTEA NOT NULL CHECK (octet_length(canonical_command) BETWEEN 1 AND 262144),
    actor_id BIGINT NOT NULL REFERENCES auth_identities(id),
    actor_session_id UUID NOT NULL,
    actor_session_version BIGINT NOT NULL CHECK (actor_session_version>0),
    actor_roles_version BIGINT NOT NULL CHECK (actor_roles_version>0),
    target_id BIGINT NOT NULL REFERENCES auth_identities(id),
    result_payload BYTEA NOT NULL CHECK (octet_length(result_payload) BETWEEN 1 AND 65536),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (actor_id<>target_id)
);
CREATE FUNCTION guard_auth_admin_access_noop_receipt() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN RAISE EXCEPTION 'admin access noop receipt is immutable'; END $$;
CREATE TRIGGER auth_admin_access_noop_receipt_immutable BEFORE UPDATE OR DELETE
    ON auth_admin_access_noop_receipts FOR EACH ROW
    EXECUTE FUNCTION guard_auth_admin_access_noop_receipt();
REVOKE ALL ON auth_admin_access_noop_receipts FROM PUBLIC;
REVOKE EXECUTE ON FUNCTION guard_auth_admin_access_noop_receipt() FROM PUBLIC;
