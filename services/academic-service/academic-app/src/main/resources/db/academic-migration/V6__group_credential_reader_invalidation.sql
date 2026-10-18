-- Domain lifecycle guard. Auth retains only its credential-reader invalidation manifest.
CREATE TABLE academic_group_availability_operations (
    operation_id uuid PRIMARY KEY,
    action varchar(24) NOT NULL CHECK(action IN ('DEACTIVATE','RESTORE','MANUAL_CYCLE','AUTOMATIC_CYCLE')),
    request_digest varchar(64) NOT NULL CHECK(request_digest ~ '^[0-9a-f]{64}$'),
    scope_group_id bigint NOT NULL CHECK(scope_group_id>=0),
    local_payload bytea NOT NULL CHECK(octet_length(local_payload) BETWEEN 1 AND 1048576),
    local_command_hash bytea NOT NULL CHECK(octet_length(local_command_hash)=32),
    local_noop boolean NOT NULL DEFAULT false,
    auth_payload bytea CHECK(octet_length(auth_payload) BETWEEN 1 AND 1048576),
    auth_command_hash bytea CHECK(octet_length(auth_command_hash)=32),
    actor_mode varchar(7) NOT NULL CHECK(actor_mode IN ('HUMAN','SERVICE')),
    actor_id bigint CHECK(actor_id>0),
    actor_session_id uuid,
    actor_session_version bigint CHECK(actor_session_version>0),
    actor_roles_version bigint CHECK(actor_roles_version>0),
    supersedes_operation_id uuid REFERENCES academic_group_availability_operations(operation_id) ON DELETE RESTRICT,
    phase varchar(24) NOT NULL CHECK(phase IN ('ACCEPTED','AUTH_INVALIDATED','LOCAL_COMMITTED','SUCCEEDED','FAILED_SAFE','REJECTED')),
    auth_result bytea,
    rejection_reason varchar(24) CHECK(rejection_reason IN ('ACTOR_DENIED','TARGET_CONFLICT','ACADEMIC_REJECTED')),
    local_apply_rejected boolean NOT NULL DEFAULT false,
    group_receipts bytea,
    headman_receipts bytea,
    revision bigint NOT NULL DEFAULT 1 CHECK(revision>0),
    superseded_by_operation_id uuid REFERENCES academic_group_availability_operations(operation_id) ON DELETE RESTRICT,
    next_attempt_at timestamptz,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CHECK ((local_noop AND action IN ('RESTORE','MANUAL_CYCLE') AND phase='SUCCEEDED' AND auth_payload IS NULL AND auth_command_hash IS NULL) OR (NOT local_noop AND auth_payload IS NOT NULL AND auth_command_hash IS NOT NULL)),
    CHECK ((actor_mode='SERVICE' AND action='AUTOMATIC_CYCLE' AND supersedes_operation_id IS NULL AND actor_id IS NULL AND actor_session_id IS NULL AND actor_session_version IS NULL AND actor_roles_version IS NULL)
        OR (actor_mode='HUMAN' AND actor_id IS NOT NULL AND actor_session_id IS NOT NULL AND actor_session_version IS NOT NULL AND actor_roles_version IS NOT NULL)),
    CHECK(phase NOT IN ('LOCAL_COMMITTED','SUCCEEDED') OR group_receipts IS NOT NULL AND headman_receipts IS NOT NULL)
);
CREATE TABLE academic_group_availability_guards (
    group_id bigint PRIMARY KEY REFERENCES groups(id) ON DELETE RESTRICT,
    operation_id uuid NOT NULL REFERENCES academic_group_availability_operations(operation_id) ON DELETE RESTRICT,
    original_group_revision bigint NOT NULL CHECK(original_group_revision>0)
);
CREATE TABLE academic_group_availability_headmen (
    user_id bigint PRIMARY KEY REFERENCES academic_profiles(id) ON DELETE RESTRICT,
    operation_id uuid NOT NULL REFERENCES academic_group_availability_operations(operation_id) ON DELETE RESTRICT
);
CREATE INDEX academic_group_availability_recovery ON academic_group_availability_operations(next_attempt_at,operation_id)
    WHERE phase IN ('ACCEPTED','AUTH_INVALIDATED','LOCAL_COMMITTED');
CREATE FUNCTION protect_group_availability_identity() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP='DELETE' THEN RAISE EXCEPTION 'group availability operation is retained' USING ERRCODE='23514'; END IF;
    IF ROW(NEW.operation_id,NEW.action,NEW.request_digest,NEW.scope_group_id,NEW.local_payload,NEW.local_command_hash,
            NEW.local_noop,NEW.auth_payload,NEW.auth_command_hash,NEW.actor_mode,NEW.actor_id,NEW.actor_session_id,NEW.actor_session_version,
            NEW.actor_roles_version,NEW.supersedes_operation_id,NEW.created_at)
        IS DISTINCT FROM ROW(OLD.operation_id,OLD.action,OLD.request_digest,OLD.scope_group_id,OLD.local_payload,OLD.local_command_hash,
            OLD.local_noop,OLD.auth_payload,OLD.auth_command_hash,OLD.actor_mode,OLD.actor_id,OLD.actor_session_id,OLD.actor_session_version,
            OLD.actor_roles_version,OLD.supersedes_operation_id,OLD.created_at) THEN
        RAISE EXCEPTION 'group availability command is immutable' USING ERRCODE='23514';
    END IF;
    IF OLD.group_receipts IS NOT NULL AND NEW.group_receipts IS DISTINCT FROM OLD.group_receipts THEN
        RAISE EXCEPTION 'group availability domain receipts are immutable' USING ERRCODE='23514';
    END IF;
    IF OLD.headman_receipts IS NOT NULL AND NEW.headman_receipts IS DISTINCT FROM OLD.headman_receipts THEN
        RAISE EXCEPTION 'group availability headman receipts are immutable' USING ERRCODE='23514';
    END IF;
    RETURN NEW;
END
$$;
CREATE TRIGGER group_availability_identity_immutable BEFORE UPDATE OR DELETE ON academic_group_availability_operations
    FOR EACH ROW EXECUTE FUNCTION protect_group_availability_identity();
