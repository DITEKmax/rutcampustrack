-- Academic owns the shared Flyway chain; Auth credential writes use these same rows.
-- Durable metadata only: never login, initial password, changed password or hash.
ALTER TABLE users
    ADD COLUMN credentials_version BIGINT NOT NULL DEFAULT 1,
    ADD COLUMN telegram_binding_version BIGINT NOT NULL DEFAULT 1,
    ADD CONSTRAINT users_credentials_version_positive CHECK (credentials_version > 0),
    ADD CONSTRAINT users_telegram_binding_version_positive CHECK (telegram_binding_version > 0);

CREATE TABLE bot_credential_messages (
    record_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id BIGINT NOT NULL CHECK (user_id > 0),
    private_chat_id BIGINT NOT NULL CHECK (private_chat_id > 0),
    message_id BIGINT NOT NULL CHECK (message_id > 0),
    telegram_binding_version BIGINT NOT NULL CHECK (telegram_binding_version > 0),
    credentials_version BIGINT NOT NULL CHECK (credentials_version > 0),
    record_revision BIGINT NOT NULL DEFAULT 1 CHECK (record_revision > 0),
    render_revision BIGINT NOT NULL DEFAULT 1 CHECK (render_revision > 0),
    render_state VARCHAR(24) NOT NULL DEFAULT 'IN_FLIGHT',
    cleanup_state VARCHAR(16) NOT NULL DEFAULT 'NOT_REQUIRED',
    invalidated_by_credentials_version BIGINT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    render_completed_at TIMESTAMPTZ,
    invalidated_at TIMESTAMPTZ,
    cleaned_at TIMESTAMPTZ,
    cleanup_event_id UUID,
    cleanup_outcome VARCHAR(16),
    -- No user FK: final account deletion must retain the original cleanup identity.
    CONSTRAINT bot_credential_messages_card_unique UNIQUE (private_chat_id, message_id),
    CONSTRAINT bot_credential_messages_render_state CHECK (
        render_state IN ('IN_FLIGHT', 'CONFIRMED_EDIT', 'NO_EDIT', 'UNCERTAIN')),
    CONSTRAINT bot_credential_messages_render_receipt CHECK (
        (render_state = 'IN_FLIGHT') = (render_completed_at IS NULL)),
    CONSTRAINT bot_credential_messages_cleanup_state CHECK (
        cleanup_state IN ('NOT_REQUIRED', 'PENDING', 'CLEANED')),
    CONSTRAINT bot_credential_messages_invalidation CHECK (
        (invalidated_by_credentials_version IS NULL AND invalidated_at IS NULL
            AND cleanup_state = 'NOT_REQUIRED')
        OR (invalidated_by_credentials_version IS NOT NULL
            AND invalidated_by_credentials_version >= credentials_version
            AND invalidated_at IS NOT NULL AND cleanup_state IN ('PENDING', 'CLEANED'))),
    CONSTRAINT bot_credential_messages_cleanup_receipt CHECK (
        (cleanup_state <> 'CLEANED' AND cleaned_at IS NULL
            AND cleanup_event_id IS NULL AND cleanup_outcome IS NULL)
        OR (cleanup_state = 'CLEANED' AND cleaned_at IS NOT NULL
            AND cleanup_event_id IS NOT NULL
            AND cleanup_outcome IS NOT NULL
            AND cleanup_outcome IN ('DELETED', 'ABSENT', 'SANITIZED'))),
    CONSTRAINT bot_credential_messages_sanitation_settled CHECK (
        cleanup_outcome IS DISTINCT FROM 'SANITIZED'
            OR render_state IN ('CONFIRMED_EDIT', 'NO_EDIT'))
);

CREATE INDEX bot_credential_messages_pending_user_record
    ON bot_credential_messages (user_id, record_id)
    WHERE cleanup_state = 'PENDING';
CREATE INDEX bot_credential_messages_current_user_generation
    ON bot_credential_messages (user_id, credentials_version)
    WHERE cleanup_state = 'NOT_REQUIRED';

CREATE FUNCTION guard_bot_credential_message_lifecycle() RETURNS TRIGGER
LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        -- Retain even cleaned identity tombstones: replay cannot reassign a known card.
        RAISE EXCEPTION 'bot credential message identity must be retained';
    END IF;
    IF TG_OP = 'INSERT' THEN
        IF NEW.record_revision <> 1 OR NEW.render_revision <> 1
           OR NEW.render_state <> 'IN_FLIGHT' OR NEW.cleanup_state <> 'NOT_REQUIRED'
           OR NEW.invalidated_by_credentials_version IS NOT NULL
           OR NEW.invalidated_at IS NOT NULL OR NEW.render_completed_at IS NOT NULL
           OR NEW.cleaned_at IS NOT NULL OR NEW.cleanup_event_id IS NOT NULL
           OR NEW.cleanup_outcome IS NOT NULL THEN
            RAISE EXCEPTION 'bot credential message must start with one render attempt';
        END IF;
        RETURN NEW;
    END IF;

    IF ROW(NEW.record_id, NEW.user_id, NEW.private_chat_id, NEW.message_id,
           NEW.telegram_binding_version, NEW.credentials_version, NEW.render_revision, NEW.created_at)
       IS DISTINCT FROM
       ROW(OLD.record_id, OLD.user_id, OLD.private_chat_id, OLD.message_id,
           OLD.telegram_binding_version, OLD.credentials_version, OLD.render_revision, OLD.created_at) THEN
        RAISE EXCEPTION 'bot credential message original identity is immutable';
    END IF;
    IF OLD.record_revision = 9223372036854775807 THEN
        RAISE EXCEPTION 'bot credential message revision exhausted';
    END IF;
    IF NEW.record_revision <> OLD.record_revision + 1 THEN
        RAISE EXCEPTION 'bot credential message revision must advance exactly once';
    END IF;
    IF OLD.invalidated_by_credentials_version IS NOT NULL
       AND ROW(NEW.invalidated_by_credentials_version, NEW.invalidated_at)
           IS DISTINCT FROM ROW(OLD.invalidated_by_credentials_version, OLD.invalidated_at) THEN
        RAISE EXCEPTION 'bot credential message first invalidation is immutable';
    END IF;
    IF OLD.render_state <> 'IN_FLIGHT'
       AND ROW(NEW.render_state, NEW.render_completed_at)
           IS DISTINCT FROM ROW(OLD.render_state, OLD.render_completed_at) THEN
        RAISE EXCEPTION 'bot credential message render receipt is terminal';
    END IF;
    IF OLD.cleanup_state = 'NOT_REQUIRED' AND NEW.cleanup_state = 'CLEANED' THEN
        RAISE EXCEPTION 'bot credential message cleanup requires prior invalidation';
    END IF;
    IF OLD.cleanup_state = 'PENDING' AND NEW.cleanup_state = 'NOT_REQUIRED' THEN
        RAISE EXCEPTION 'bot credential message cleanup cannot be revoked';
    END IF;
    IF OLD.cleanup_state = 'CLEANED'
       AND ROW(NEW.cleanup_state, NEW.cleaned_at, NEW.cleanup_event_id, NEW.cleanup_outcome)
           IS DISTINCT FROM ROW(OLD.cleanup_state, OLD.cleaned_at, OLD.cleanup_event_id, OLD.cleanup_outcome) THEN
        RAISE EXCEPTION 'bot credential message cleanup receipt is terminal';
    END IF;
    RETURN NEW;
END
$$;

CREATE TRIGGER bot_credential_messages_lifecycle
    BEFORE INSERT OR UPDATE OR DELETE ON bot_credential_messages
    FOR EACH ROW EXECUTE FUNCTION guard_bot_credential_message_lifecycle();

CREATE FUNCTION advance_user_credential_generations() RETURNS TRIGGER
LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.id IS DISTINCT FROM OLD.id THEN
        RAISE EXCEPTION 'user credential owner identity is immutable';
    END IF;
    IF NEW.credentials_version IS DISTINCT FROM OLD.credentials_version
       OR NEW.telegram_binding_version IS DISTINCT FROM OLD.telegram_binding_version THEN
        RAISE EXCEPTION 'user credential generations are maintained by tuple changes';
    END IF;
    IF ROW(NEW.password_hash, NEW.initial_password, NEW.password_changed)
       IS DISTINCT FROM ROW(OLD.password_hash, OLD.initial_password, OLD.password_changed) THEN
        IF OLD.credentials_version = 9223372036854775807 THEN
            RAISE EXCEPTION 'user credential generation exhausted';
        END IF;
        NEW.credentials_version := OLD.credentials_version + 1;
    END IF;
    IF NEW.telegram_id IS DISTINCT FROM OLD.telegram_id THEN
        IF OLD.telegram_binding_version = 9223372036854775807 THEN
            RAISE EXCEPTION 'user Telegram binding generation exhausted';
        END IF;
        NEW.telegram_binding_version := OLD.telegram_binding_version + 1;
    END IF;
    RETURN NEW;
END
$$;

CREATE TRIGGER users_credential_generations
    BEFORE UPDATE ON users
    FOR EACH ROW EXECUTE FUNCTION advance_user_credential_generations();

CREATE FUNCTION invalidate_user_bot_credential_messages() RETURNS TRIGGER
LANGUAGE plpgsql AS $$
DECLARE
    original_user_id BIGINT;
    invalidating_generation BIGINT;
BEGIN
    IF TG_OP = 'DELETE' THEN
        original_user_id := OLD.id;
        invalidating_generation := OLD.credentials_version;
    ELSE
        IF NEW.credentials_version = OLD.credentials_version
           AND NEW.telegram_binding_version = OLD.telegram_binding_version
           AND NOT (NEW.status::text = 'archived' AND OLD.status::text <> 'archived') THEN
            RETURN NEW;
        END IF;
        original_user_id := NEW.id;
        invalidating_generation := NEW.credentials_version;
    END IF;
    -- The user row lock is already held; consumers acquire user before card locks.
    -- Preserve the first fence so an earlier event still finds an older invalidated card.
    -- In-flight/uncertain render state and its stable attempt revision survive invalidation.
    UPDATE bot_credential_messages
       SET cleanup_state = 'PENDING',
           invalidated_by_credentials_version = invalidating_generation,
           invalidated_at = NOW(),
           record_revision = record_revision + 1,
           updated_at = NOW()
     WHERE user_id = original_user_id AND cleanup_state = 'NOT_REQUIRED';
    IF TG_OP = 'DELETE' THEN
        RETURN OLD;
    END IF;
    RETURN NEW;
END
$$;

CREATE TRIGGER users_invalidate_bot_credential_messages
    AFTER UPDATE OR DELETE ON users
    FOR EACH ROW EXECUTE FUNCTION invalidate_user_bot_credential_messages();
