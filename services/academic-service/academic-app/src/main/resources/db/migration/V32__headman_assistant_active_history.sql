-- Keep revoked assistant assignments as history while allowing a fresh active
-- assignment for the same student and group.
ALTER TABLE headman_assistants
    DROP CONSTRAINT IF EXISTS headman_assistants_group_id_student_id_key;

CREATE UNIQUE INDEX IF NOT EXISTS headman_assistants_active_group_student_uq
    ON headman_assistants (group_id, student_id)
    WHERE is_active;
