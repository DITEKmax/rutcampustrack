-- Separate HEADMAN terminal receipts; positive operation RV+1 guards stay unchanged.
-- No grant/profile/catalog/session mutation or authority fence belongs to a NOOP.
CREATE TABLE auth_headman_noop_receipts (
    operation_id uuid PRIMARY KEY,
    command_hash bytea NOT NULL CHECK (octet_length(command_hash)=32),
    canonical_command bytea NOT NULL CHECK (octet_length(canonical_command) BETWEEN 1 AND 262144),
    actor_id bigint NOT NULL REFERENCES auth_identities(id),
    actor_session_id uuid NOT NULL,
    actor_session_version bigint NOT NULL CHECK (actor_session_version>0),
    actor_roles_version bigint NOT NULL CHECK (actor_roles_version>0),
    target_id bigint NOT NULL REFERENCES auth_identities(id),
    group_id bigint NOT NULL CHECK (group_id>0),
    academic_revision bigint NOT NULL CHECK (academic_revision>0),
    result_payload bytea NOT NULL CHECK (octet_length(result_payload) BETWEEN 1 AND 65536),
    created_at timestamptz NOT NULL DEFAULT now()
);
CREATE FUNCTION guard_auth_headman_noop_receipt() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN RAISE EXCEPTION 'headman noop receipt is immutable'; END $$;
CREATE TRIGGER auth_headman_noop_receipt_immutable BEFORE UPDATE OR DELETE
    ON auth_headman_noop_receipts FOR EACH ROW
    EXECUTE FUNCTION guard_auth_headman_noop_receipt();
REVOKE ALL ON auth_headman_noop_receipts FROM PUBLIC;
REVOKE EXECUTE ON FUNCTION guard_auth_headman_noop_receipt() FROM PUBLIC;
