-- JS-ADMIN-02: teacher-specific terminal status is durable on a role grant.
ALTER TABLE user_role_grants
    DROP CONSTRAINT user_role_grants_status_chk;

ALTER TABLE user_role_grants
    ADD CONSTRAINT user_role_grants_status_chk
        CHECK (status IN ('active', 'expelled', 'graduated', 'suspended', 'dismissed', 'archived'));
