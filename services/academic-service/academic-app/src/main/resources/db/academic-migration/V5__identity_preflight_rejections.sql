-- A local negative admission contains no Auth command, credential, proof or request body.
-- Both positive acceptance and this receipt use the same own-DB per-UUID transaction lock.
CREATE TABLE academic_identity_preflight_rejections (
    operation_id uuid PRIMARY KEY,
    actor_id bigint NOT NULL CHECK(actor_id>0),
    action varchar(24) NOT NULL CHECK(action IN ('CREATE','PROFILE','ROLE','TRANSFER','HEADMAN_REPLACE','ARCHIVE','RESTORE','ADMIN_ACCESS','TELEGRAM_BINDING','RECONCILE')),
    target_id bigint NOT NULL CHECK(target_id>=0),
    request_digest varchar(64) NOT NULL CHECK(request_digest ~ '^[0-9a-f]{64}$'),
    reason varchar(32) NOT NULL DEFAULT 'LOCAL_PREFLIGHT_REJECTED' CHECK(reason='LOCAL_PREFLIGHT_REJECTED'),
    created_at timestamptz NOT NULL DEFAULT now()
);
CREATE FUNCTION protect_identity_preflight_rejection() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'identity preflight rejection is immutable' USING ERRCODE='23514';
END
$$;
CREATE TRIGGER identity_preflight_rejection_immutable
    BEFORE UPDATE OR DELETE ON academic_identity_preflight_rejections
    FOR EACH ROW EXECUTE FUNCTION protect_identity_preflight_rejection();
