-- A fresh authorized attempt keeps the original business intent and never drops its read guard.
ALTER TABLE academic_identity_operations
    ADD CONSTRAINT academic_identity_original_intent UNIQUE(operation_id,local_command_hash,kind);
CREATE TABLE academic_identity_reconciliations (
    operation_id uuid PRIMARY KEY REFERENCES academic_identity_operations(operation_id) ON DELETE RESTRICT,
    failed_operation_id uuid NOT NULL REFERENCES academic_identity_operations(operation_id) ON DELETE RESTRICT,
    original_operation_id uuid NOT NULL REFERENCES academic_identity_operations(operation_id) ON DELETE RESTRICT,
    original_local_command_hash bytea NOT NULL CHECK (octet_length(original_local_command_hash)=32),
    authorization_kind varchar(24) NOT NULL CHECK (authorization_kind IN
        ('CREATE','ROLE','TRANSFER','HEADMAN_REPLACE','ARCHIVE','RESTORE','ADMIN_ACCESS','TELEGRAM_BINDING')),
    business_effects_committed boolean NOT NULL,
    active boolean NOT NULL DEFAULT true,
    created_at timestamptz NOT NULL DEFAULT now(),
    CHECK (operation_id<>failed_operation_id AND operation_id<>original_operation_id),
    FOREIGN KEY(original_operation_id,original_local_command_hash,authorization_kind)
        REFERENCES academic_identity_operations(operation_id,local_command_hash,kind) ON DELETE RESTRICT
);
CREATE UNIQUE INDEX academic_identity_one_reconciliation_successor
    ON academic_identity_reconciliations(failed_operation_id) WHERE active;

CREATE FUNCTION academic_identity_reconciliation_immutable() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP='DELETE' THEN
        RAISE EXCEPTION 'identity reconciliation evidence is immutable';
    END IF;
    IF ROW(NEW.operation_id,NEW.failed_operation_id,NEW.original_operation_id,
           NEW.original_local_command_hash,NEW.authorization_kind,NEW.business_effects_committed,NEW.created_at)
       IS DISTINCT FROM ROW(OLD.operation_id,OLD.failed_operation_id,OLD.original_operation_id,
           OLD.original_local_command_hash,OLD.authorization_kind,OLD.business_effects_committed,OLD.created_at)
       OR (NOT OLD.active AND NEW.active) THEN
        RAISE EXCEPTION 'identity reconciliation evidence is immutable';
    END IF;
    IF OLD.active AND NOT NEW.active AND NOT EXISTS (
        SELECT 1 FROM academic_identity_operations WHERE operation_id=OLD.operation_id AND phase='REJECTED'
    ) THEN
        RAISE EXCEPTION 'only a durable negative Auth reservation closes a reconciliation attempt';
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER academic_identity_reconciliation_immutable
    BEFORE UPDATE OR DELETE ON academic_identity_reconciliations
    FOR EACH ROW EXECUTE FUNCTION academic_identity_reconciliation_immutable();
