-- Auth remains the sole credential authority; preserve existing generated material.
ALTER TABLE auth_identities DROP CONSTRAINT auth_identities_generated_password_origin_check;
ALTER TABLE auth_identities ADD CONSTRAINT auth_identities_generated_password_origin_check
    CHECK (generated_password_origin IS NULL OR
        generated_password_origin IN ('initial','headman_reset','admin_reset'));
-- Existing normalization/version/bot-cleanup triggers already apply to every origin.
