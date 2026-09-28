CREATE TABLE homework_binding_archives
(
    binding_id BIGINT      NOT NULL,
    actor_id   BIGINT      NOT NULL,
    request_key UUID       NOT NULL,
    homework_id BIGINT,
    archived_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT homework_binding_archives_pk
        PRIMARY KEY (binding_id),
    CONSTRAINT homework_binding_archives_binding_positive_chk
        CHECK (binding_id > 0),
    CONSTRAINT homework_binding_archives_actor_positive_chk
        CHECK (actor_id > 0),
    CONSTRAINT homework_binding_archives_homework_positive_chk
        CHECK (homework_id IS NULL OR homework_id > 0),
    CONSTRAINT homework_binding_archives_actor_fk
        FOREIGN KEY (actor_id) REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT homework_binding_archives_homework_fk
        FOREIGN KEY (homework_id) REFERENCES homeworks (id) ON DELETE RESTRICT,
    CONSTRAINT homework_binding_archives_homework_uq
        UNIQUE (homework_id)
);

-- A terminal marker may acquire the exact Academic content id when content is
-- persisted after cancellation. Its binding/author/request identity is fixed.
CREATE OR REPLACE FUNCTION protect_homework_binding_archive_identity()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    IF NEW.binding_id IS DISTINCT FROM OLD.binding_id
       OR NEW.actor_id IS DISTINCT FROM OLD.actor_id
       OR NEW.request_key IS DISTINCT FROM OLD.request_key
       OR NEW.archived_at IS DISTINCT FROM OLD.archived_at THEN
        RAISE EXCEPTION 'homework binding archive identity is immutable';
    END IF;
    IF OLD.homework_id IS NOT NULL
       AND NEW.homework_id IS DISTINCT FROM OLD.homework_id THEN
        RAISE EXCEPTION 'homework binding archive content identity is immutable';
    END IF;
    RETURN NEW;
END
$$;

CREATE TRIGGER homework_binding_archives_identity_guard_trg
    BEFORE UPDATE ON homework_binding_archives
    FOR EACH ROW
    EXECUTE FUNCTION protect_homework_binding_archive_identity();
