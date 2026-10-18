-- Restricted NONAUTHORITATIVE read-model feed for the Academic registry only.
-- No password/initial/hash, session/refresh/OTP, credential generation, or
-- permission authority enters this feed. Telegram ID is protected PII.
CREATE TABLE auth_identity_catalog_clock (
    singleton SMALLINT PRIMARY KEY CHECK(singleton=1),
    version BIGINT NOT NULL CHECK(version>=0)
);
INSERT INTO auth_identity_catalog_clock(singleton,version) VALUES(1,0);
CREATE TABLE auth_identity_catalog_changes (
    catalog_version BIGINT PRIMARY KEY CHECK(catalog_version>0),
    user_id BIGINT NOT NULL REFERENCES auth_identities(id),
    projection JSONB NOT NULL CHECK(jsonb_typeof(projection)='object'),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
ALTER TABLE auth_identities ADD COLUMN catalog_version BIGINT NOT NULL DEFAULT 0 CHECK(catalog_version>=0);
ALTER TABLE auth_identity_operations ADD COLUMN committed_catalog_version BIGINT;
ALTER TABLE auth_identity_operations ADD CONSTRAINT auth_operation_catalog_receipt
    CHECK((phase='COMMITTED')=(committed_catalog_version IS NOT NULL));

CREATE FUNCTION capture_auth_identity_catalog() RETURNS TRIGGER LANGUAGE plpgsql AS $$
DECLARE next_version BIGINT; state_code TEXT; eligibility_revision BIGINT; grants JSONB;
BEGIN
    IF TG_OP='UPDATE' AND ROW(NEW.login,NEW.account_state,NEW.roles_version,
        NEW.telegram_id,NEW.telegram_username,NEW.telegram_binding_version,
        NEW.password_changed,NEW.fence_operation_id)
        IS NOT DISTINCT FROM ROW(OLD.login,OLD.account_state,OLD.roles_version,
        OLD.telegram_id,OLD.telegram_username,OLD.telegram_binding_version,
        OLD.password_changed,OLD.fence_operation_id) THEN RETURN NULL; END IF;
    -- A row counter (not a sequence) is transactional: rollback leaves no gap.
    UPDATE auth_identity_catalog_clock SET version=version+1 WHERE singleton=1
        RETURNING version INTO next_version;
    IF next_version IS NULL THEN RAISE EXCEPTION 'identity catalog clock unavailable'; END IF;
    state_code:=CASE WHEN NEW.account_state='PENDING' THEN 'PENDING'
                     WHEN NEW.fence_operation_id IS NOT NULL THEN 'FENCED'
                     ELSE NEW.account_state END;
    SELECT coalesce(jsonb_agg(jsonb_build_object('grant_id',g.id,'role',upper(g.role),
        'status',upper(g.status),'group_id',g.group_id) ORDER BY g.id),'[]'::jsonb)
        INTO grants FROM user_role_grants g WHERE g.user_id=NEW.id;
    SELECT coalesce(max(t.committed_academic_revision),0) INTO eligibility_revision
        FROM auth_identity_operation_targets t JOIN auth_identity_operations o USING(operation_id)
        WHERE t.user_id=NEW.id AND o.phase='COMMITTED';
    INSERT INTO auth_identity_catalog_changes(catalog_version,user_id,projection)
        VALUES(next_version,NEW.id,jsonb_build_object(
            'user_id',NEW.id,'login',NEW.login,'account_state',state_code,
            'roles_version',NEW.roles_version,'telegram_binding_version',NEW.telegram_binding_version,
            'telegram_id',NEW.telegram_id,'telegram_username',NEW.telegram_username,
            'password_changed',NEW.password_changed,'grants',grants,
            'academic_revision',eligibility_revision));
    UPDATE auth_identities SET catalog_version=next_version WHERE id=NEW.id;
    RETURN NULL;
END $$;
CREATE TRIGGER auth_identity_catalog_capture AFTER INSERT OR UPDATE OF
    login,account_state,roles_version,telegram_id,telegram_username,
    telegram_binding_version,password_changed,fence_operation_id ON auth_identities
    FOR EACH ROW EXECUTE FUNCTION capture_auth_identity_catalog();

CREATE FUNCTION protect_auth_identity_catalog_change() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN RAISE EXCEPTION 'identity catalog history is immutable'; END $$;
CREATE TRIGGER auth_identity_catalog_immutable BEFORE UPDATE OR DELETE ON auth_identity_catalog_changes
    FOR EACH ROW EXECUTE FUNCTION protect_auth_identity_catalog_change();
REVOKE ALL ON auth_identity_catalog_clock,auth_identity_catalog_changes FROM PUBLIC;
REVOKE EXECUTE ON FUNCTION capture_auth_identity_catalog(),protect_auth_identity_catalog_change() FROM PUBLIC;
