ALTER TABLE homeworks
    DROP CONSTRAINT homeworks_publication_state_chk;

ALTER TABLE homeworks
    ADD CONSTRAINT homeworks_publication_state_chk
        CHECK (publication_state IN ('PENDING', 'ACTIVE', 'ARCHIVED'));

-- Publication archive is a terminal state.  The V25 identity trigger
-- protected the old two-state contract by rejecting every ACTIVE -> non-
-- ACTIVE update; replace that transition guard so the coordinated terminal
-- archive can commit while ARCHIVED can never be resurrected.
CREATE OR REPLACE FUNCTION protect_homework_binding_identity()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    IF NEW.binding_id IS DISTINCT FROM OLD.binding_id
       OR NEW.actor_id IS DISTINCT FROM OLD.actor_id
       OR NEW.request_key IS DISTINCT FROM OLD.request_key
       OR NEW.payload_hash IS DISTINCT FROM OLD.payload_hash THEN
        RAISE EXCEPTION 'homework binding identity is immutable';
    END IF;
    IF OLD.publication_state = 'ACTIVE'
       AND NEW.publication_state NOT IN ('ACTIVE', 'ARCHIVED') THEN
        RAISE EXCEPTION 'active homework publication cannot be reverted to pending';
    END IF;
    IF OLD.publication_state = 'ARCHIVED' AND NEW.publication_state <> 'ARCHIVED' THEN
        RAISE EXCEPTION 'archived homework publication cannot be reopened';
    END IF;
    RETURN NEW;
END
$$;
