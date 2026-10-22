-- A future Telegram binding must be unique before Academic commits its side.
-- CREATE already owns its Telegram ID in the pending auth_identities row.
CREATE TABLE auth_telegram_binding_reservations (
    telegram_id BIGINT PRIMARY KEY CHECK(telegram_id>0),
    user_id BIGINT NOT NULL UNIQUE REFERENCES auth_identities(id),
    operation_id UUID NOT NULL,
    FOREIGN KEY(operation_id,user_id) REFERENCES auth_identity_operation_targets(operation_id,user_id)
);
CREATE FUNCTION guard_auth_telegram_reservation() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP='DELETE' THEN
        IF NOT EXISTS(SELECT 1 FROM auth_identity_operations o JOIN auth_identities u ON u.id=OLD.user_id
            WHERE o.operation_id=OLD.operation_id AND o.phase='COMMITTED'
              AND u.fence_operation_id IS NULL AND u.telegram_id=OLD.telegram_id) THEN
            RAISE EXCEPTION 'binding reservation cannot be released before activation';
        END IF;
        RETURN OLD;
    END IF;
    IF NOT EXISTS(SELECT 1 FROM auth_identity_operations o JOIN auth_identities u ON u.id=NEW.user_id
        JOIN auth_identity_operation_targets t ON t.operation_id=o.operation_id AND t.user_id=u.id
        WHERE o.operation_id=NEW.operation_id AND o.phase='PREPARED_FENCED'
          AND o.authorization_kind IN ('TELEGRAM_BINDING','ROLE','HEADMAN_REPLACE') AND u.fence_operation_id=o.operation_id) THEN
        RAISE EXCEPTION 'binding reservation requires its own prepared target fence';
    END IF;
    IF TG_OP='UPDATE' AND (NEW.telegram_id<>OLD.telegram_id OR NEW.user_id<>OLD.user_id
        OR NOT EXISTS(SELECT 1 FROM auth_identity_operations o JOIN auth_identity_operation_targets t
            ON t.supersedes_failed_operation_id=o.operation_id
            WHERE o.operation_id=OLD.operation_id AND o.phase='FAILED_SAFE'
              AND t.operation_id=NEW.operation_id AND t.user_id=NEW.user_id)) THEN
        RAISE EXCEPTION 'binding reservation recovery must supersede its own failed operation';
    END IF;
    IF EXISTS(SELECT 1 FROM auth_identities WHERE telegram_id=NEW.telegram_id AND id<>NEW.user_id) THEN
        RAISE EXCEPTION 'Telegram identity is already bound' USING ERRCODE='23505';
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER auth_telegram_reservation_guard BEFORE INSERT OR UPDATE OR DELETE
ON auth_telegram_binding_reservations FOR EACH ROW EXECUTE FUNCTION guard_auth_telegram_reservation();
REVOKE EXECUTE ON FUNCTION guard_auth_telegram_reservation() FROM PUBLIC;

-- Also protects pending CREATE and any local identity writer against a future
-- binding reservation. This contains no foreign database or domain lookup.
CREATE FUNCTION guard_auth_reserved_telegram_identity() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.telegram_id IS NOT NULL AND EXISTS(SELECT 1 FROM auth_telegram_binding_reservations
        WHERE telegram_id=NEW.telegram_id AND user_id<>NEW.id) THEN
        RAISE EXCEPTION 'Telegram identity is reserved' USING ERRCODE='23505';
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER auth_reserved_telegram_identity BEFORE INSERT OR UPDATE OF telegram_id ON auth_identities
FOR EACH ROW EXECUTE FUNCTION guard_auth_reserved_telegram_identity();
REVOKE EXECUTE ON FUNCTION guard_auth_reserved_telegram_identity() FROM PUBLIC;
