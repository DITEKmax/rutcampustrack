-- A successful PREPARE consumes a fixed archive proof once. Time expiry after
-- this admission cannot lose the accepted command; actor security revocation
-- is still checked at COMMIT by the operation engine. No Academic SQL exists.
CREATE TABLE auth_identity_step_up_admissions (
    operation_id UUID PRIMARY KEY REFERENCES auth_identity_operations(operation_id),
    protocol_version INTEGER NOT NULL CHECK(protocol_version=1),
    command_hash BYTEA NOT NULL CHECK(octet_length(command_hash)=32),
    receipt_id UUID UNIQUE REFERENCES auth_password_step_up_receipts(receipt_id),
    step_up_required BOOLEAN NOT NULL,
    CHECK(NOT step_up_required OR receipt_id IS NOT NULL)
);
CREATE FUNCTION guard_auth_identity_step_up_admission() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP<>'INSERT' THEN RAISE EXCEPTION 'archive security admission is immutable'; END IF;
    IF NOT EXISTS(SELECT 1 FROM auth_identity_operations o
        WHERE o.operation_id=NEW.operation_id AND o.authorization_kind='ARCHIVE'
          AND o.phase='PREPARED_FENCED' AND o.command_hash=NEW.command_hash) THEN
        RAISE EXCEPTION 'archive security admission requires its own prepared command';
    END IF;
    IF NEW.receipt_id IS NOT NULL AND NOT EXISTS(
        SELECT 1 FROM auth_password_step_up_receipts r JOIN auth_identity_operations o
          ON o.operation_id=r.operation_id
        WHERE o.operation_id=NEW.operation_id AND r.receipt_id=NEW.receipt_id AND r.purpose='USER_ARCHIVE'
          AND r.actor_id=o.actor_id AND r.session_id=o.actor_session_id
          AND r.session_version=o.actor_session_version AND r.roles_version=o.actor_roles_version
          AND r.credentials_version=o.actor_credentials_version AND r.binding_version=o.actor_binding_version
          AND r.admin_profile_version=o.actor_admin_profile_version) THEN
        RAISE EXCEPTION 'archive security receipt does not belong to its prepared actor';
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER auth_identity_step_up_admission_guard BEFORE INSERT OR UPDATE OR DELETE
ON auth_identity_step_up_admissions FOR EACH ROW EXECUTE FUNCTION guard_auth_identity_step_up_admission();
REVOKE EXECUTE ON FUNCTION guard_auth_identity_step_up_admission() FROM PUBLIC;
