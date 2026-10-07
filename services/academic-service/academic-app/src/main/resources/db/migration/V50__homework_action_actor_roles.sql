-- Only newly admitted commands receive an authorization snapshot. Legacy rows stay unknown.
ALTER TABLE homeworks ADD COLUMN create_actor_role VARCHAR(32),
    ADD COLUMN archive_action_actor_id BIGINT,
    ADD COLUMN archive_action_actor_role VARCHAR(32),
    ADD CONSTRAINT homework_create_actor_role_known CHECK (create_actor_role IN ('HEADMAN', 'ASSISTANT')),
    ADD CONSTRAINT homework_archive_actor_snapshot CHECK (
        (archive_action_actor_id IS NULL AND archive_action_actor_role IS NULL)
        OR (archive_action_actor_id IS NOT NULL AND archive_action_actor_id > 0
            AND archive_action_actor_role IS NOT NULL AND archive_action_actor_role IN ('HEADMAN', 'ASSISTANT')));

ALTER TABLE homework_edit_operations ADD COLUMN actor_role VARCHAR(32)
    CHECK (actor_role IN ('HEADMAN', 'ASSISTANT'));
-- V45's whole-command identity guard already makes the added operation column immutable.

CREATE FUNCTION protect_homework_action_actor_roles()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.create_actor_role IS DISTINCT FROM OLD.create_actor_role THEN
        RAISE EXCEPTION 'homework creation authority snapshot is immutable';
    END IF;
    IF OLD.archive_action_actor_id IS NOT NULL
       AND (NEW.archive_action_actor_id IS DISTINCT FROM OLD.archive_action_actor_id
            OR NEW.archive_action_actor_role IS DISTINCT FROM OLD.archive_action_actor_role) THEN
        RAISE EXCEPTION 'homework manual archive authority snapshot is immutable';
    END IF;
    IF OLD.publication_state = 'ARCHIVED' AND OLD.archive_action_actor_id IS NULL
       AND NEW.archive_action_actor_id IS NOT NULL THEN
        RAISE EXCEPTION 'terminal homework cannot receive a new manual archive author';
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER homework_action_actor_roles_guard BEFORE UPDATE ON homeworks
    FOR EACH ROW EXECUTE FUNCTION protect_homework_action_actor_roles();
