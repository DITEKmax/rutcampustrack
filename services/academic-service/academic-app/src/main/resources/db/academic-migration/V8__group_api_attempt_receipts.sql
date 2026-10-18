-- Preserve the original immutable V5 receipt; extend only its closed set of actual API actions.
ALTER TABLE academic_identity_preflight_rejections DROP CONSTRAINT academic_identity_preflight_rejections_action_check;
ALTER TABLE academic_identity_preflight_rejections ADD CONSTRAINT academic_identity_preflight_rejections_action_check
    CHECK(action IN ('CREATE','PROFILE','ROLE','TRANSFER','HEADMAN_REPLACE','ARCHIVE','RESTORE','ADMIN_ACCESS','TELEGRAM_BINDING','RECONCILE',
        'GROUP_DEACTIVATE','GROUP_RESTORE','GROUP_MANUAL_CYCLE','GROUP_AUTOMATIC_CYCLE','GROUP_RECONCILE'));
ALTER TABLE academic_group_availability_operations
    ADD COLUMN api_action varchar(24), ADD COLUMN api_target_id bigint, ADD COLUMN api_request_digest varchar(64);
-- The chain is unpublished. This bounded backfill also preserves any original direct-command rows.
UPDATE academic_group_availability_operations
    SET api_action='GROUP_'||action,api_target_id=scope_group_id,api_request_digest=request_digest;
ALTER TABLE academic_group_availability_operations
    ALTER COLUMN api_action SET NOT NULL,ALTER COLUMN api_target_id SET NOT NULL,ALTER COLUMN api_request_digest SET NOT NULL,
    ADD CHECK(api_action IN ('GROUP_DEACTIVATE','GROUP_RESTORE','GROUP_MANUAL_CYCLE','GROUP_AUTOMATIC_CYCLE','GROUP_RECONCILE')),
    ADD CHECK(api_target_id>=0), ADD CHECK(api_request_digest ~ '^[0-9a-f]{64}$');
CREATE FUNCTION protect_group_api_attempt_identity() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF ROW(NEW.api_action,NEW.api_target_id,NEW.api_request_digest) IS DISTINCT FROM ROW(OLD.api_action,OLD.api_target_id,OLD.api_request_digest) THEN
        RAISE EXCEPTION 'group API attempt identity is immutable' USING ERRCODE='23514';
    END IF;
    RETURN NEW;
END
$$;
CREATE TRIGGER group_api_attempt_identity BEFORE UPDATE ON academic_group_availability_operations
    FOR EACH ROW EXECUTE FUNCTION protect_group_api_attempt_identity();
