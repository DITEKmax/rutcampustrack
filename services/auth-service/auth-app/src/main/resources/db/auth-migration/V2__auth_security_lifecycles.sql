-- REVIEW DRAFT: retained Auth security lifecycle, extracted from accepted
-- V46/V57/V59/V60; only owner identity reference/fence invalidation changes.
-- No Academic operation FK, domain selector, jobs, or artifact tables.

CREATE TABLE qr_login_issuers (
    id UUID PRIMARY KEY,
    secret_hash BYTEA NOT NULL CHECK (octet_length(secret_hash) = 32),
    current_challenge_id UUID,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE qr_login_challenges (
    id UUID PRIMARY KEY,
    issuer_id UUID NOT NULL REFERENCES qr_login_issuers(id) ON DELETE CASCADE,
    purpose VARCHAR(16) NOT NULL CHECK (purpose = 'LOGIN'),
    approval_hash BYTEA NOT NULL CHECK (octet_length(approval_hash) = 32),
    state VARCHAR(16) NOT NULL CHECK (state IN ('PENDING', 'CONFIRMED', 'REJECTED', 'EXPIRED')),
    browser_label VARCHAR(160) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL CHECK (expires_at > created_at),
    confirmed_user_id BIGINT REFERENCES auth_identities(id) ON DELETE RESTRICT,
    confirmed_source_sid UUID,
    confirmed_source_sv BIGINT,
    confirmed_source_rv BIGINT,
    confirmed_at TIMESTAMPTZ,
    issued_target_sid UUID UNIQUE,
    issued_at TIMESTAMPTZ,
    replay_until TIMESTAMPTZ,
    target_sv BIGINT,
    target_rv BIGINT,
    initial_refresh_jti_hash BYTEA,
    access_expires_at TIMESTAMPTZ,
    signing_fingerprint VARCHAR(64),
    CONSTRAINT qr_login_challenge_issuer_uq UNIQUE (id, issuer_id),
    CONSTRAINT qr_login_source_owner_fk FOREIGN KEY (confirmed_source_sid, confirmed_user_id)
        REFERENCES auth_sessions(sid, user_id) ON DELETE RESTRICT,
    CONSTRAINT qr_login_target_owner_fk FOREIGN KEY (issued_target_sid, confirmed_user_id)
        REFERENCES auth_sessions(sid, user_id) ON DELETE RESTRICT,
    CONSTRAINT qr_login_confirmation_pair_chk CHECK (
        (confirmed_user_id IS NULL AND confirmed_source_sid IS NULL AND confirmed_source_sv IS NULL
            AND confirmed_source_rv IS NULL AND confirmed_at IS NULL)
        OR (confirmed_user_id IS NOT NULL AND confirmed_source_sid IS NOT NULL
            AND confirmed_source_sv IS NOT NULL AND confirmed_source_sv > 0
            AND confirmed_source_rv IS NOT NULL AND confirmed_source_rv > 0 AND confirmed_at IS NOT NULL)),
    CONSTRAINT qr_login_confirmed_state_chk CHECK (state <> 'CONFIRMED' OR confirmed_user_id IS NOT NULL),
    CONSTRAINT qr_login_confirmation_state_chk CHECK (state IN ('CONFIRMED', 'EXPIRED') OR confirmed_user_id IS NULL),
    CONSTRAINT qr_login_exchange_receipt_chk CHECK (
        (issued_target_sid IS NULL AND issued_at IS NULL AND replay_until IS NULL
            AND target_sv IS NULL AND target_rv IS NULL AND initial_refresh_jti_hash IS NULL
            AND access_expires_at IS NULL AND signing_fingerprint IS NULL)
        OR (issued_target_sid IS NOT NULL AND issued_at IS NOT NULL AND replay_until IS NOT NULL AND replay_until > issued_at
            AND target_sv IS NOT NULL AND target_sv > 0 AND target_rv IS NOT NULL AND target_rv > 0
            AND initial_refresh_jti_hash IS NOT NULL AND octet_length(initial_refresh_jti_hash) = 32
            AND access_expires_at IS NOT NULL AND access_expires_at > issued_at
            AND signing_fingerprint IS NOT NULL AND signing_fingerprint ~ '^[0-9a-f]{64}$'
            AND confirmed_user_id IS NOT NULL))
);

ALTER TABLE qr_login_issuers ADD CONSTRAINT qr_login_current_owned_challenge_fk
    FOREIGN KEY (current_challenge_id, id) REFERENCES qr_login_challenges(id, issuer_id)
    DEFERRABLE INITIALLY DEFERRED;
CREATE INDEX qr_login_challenges_expiry_idx ON qr_login_challenges(expires_at);

CREATE FUNCTION protect_qr_login_issuer() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.id IS DISTINCT FROM OLD.id OR NEW.secret_hash IS DISTINCT FROM OLD.secret_hash
       OR NEW.created_at IS DISTINCT FROM OLD.created_at THEN
        RAISE EXCEPTION 'QR issuer identity is immutable';
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER qr_login_issuer_guard BEFORE UPDATE ON qr_login_issuers
    FOR EACH ROW EXECUTE FUNCTION protect_qr_login_issuer();

CREATE FUNCTION protect_qr_login_challenge() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    IF (NEW.id, NEW.issuer_id, NEW.purpose, NEW.approval_hash, NEW.browser_label, NEW.created_at, NEW.expires_at)
       IS DISTINCT FROM
       (OLD.id, OLD.issuer_id, OLD.purpose, OLD.approval_hash, OLD.browser_label, OLD.created_at, OLD.expires_at) THEN
        RAISE EXCEPTION 'QR challenge intent is immutable';
    END IF;
    IF (OLD.state = 'EXPIRED' AND NEW.state <> 'EXPIRED')
       OR (OLD.state = 'REJECTED' AND NEW.state NOT IN ('REJECTED', 'EXPIRED'))
       OR (OLD.state = 'CONFIRMED' AND NEW.state NOT IN ('CONFIRMED', 'EXPIRED')) THEN
        RAISE EXCEPTION 'QR decision is terminal';
    END IF;
    IF OLD.confirmed_user_id IS NOT NULL AND
       (NEW.confirmed_user_id, NEW.confirmed_source_sid, NEW.confirmed_source_sv, NEW.confirmed_source_rv, NEW.confirmed_at)
       IS DISTINCT FROM
       (OLD.confirmed_user_id, OLD.confirmed_source_sid, OLD.confirmed_source_sv, OLD.confirmed_source_rv, OLD.confirmed_at) THEN
        RAISE EXCEPTION 'QR approval identity is immutable';
    END IF;
    IF OLD.confirmed_user_id IS NULL AND NEW.confirmed_user_id IS NOT NULL
       AND (OLD.state <> 'PENDING' OR NEW.state <> 'CONFIRMED') THEN
        RAISE EXCEPTION 'QR approval requires a pending decision';
    END IF;
    IF OLD.issued_target_sid IS NOT NULL AND
       (NEW.issued_target_sid, NEW.issued_at, NEW.replay_until, NEW.target_sv, NEW.target_rv, NEW.initial_refresh_jti_hash,
            NEW.access_expires_at, NEW.signing_fingerprint)
       IS DISTINCT FROM
       (OLD.issued_target_sid, OLD.issued_at, OLD.replay_until, OLD.target_sv, OLD.target_rv, OLD.initial_refresh_jti_hash,
            OLD.access_expires_at, OLD.signing_fingerprint) THEN
        RAISE EXCEPTION 'QR accepted exchange receipt is immutable';
    END IF;
    IF OLD.issued_target_sid IS NULL AND NEW.issued_target_sid IS NOT NULL
       AND (OLD.state <> 'CONFIRMED' OR NEW.state <> 'CONFIRMED') THEN
        RAISE EXCEPTION 'QR exchange requires an accepted approval';
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER qr_login_challenge_guard BEFORE UPDATE ON qr_login_challenges
    FOR EACH ROW EXECUTE FUNCTION protect_qr_login_challenge();

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
           AND NOT (NEW.account_state = 'ARCHIVED' AND OLD.account_state <> 'ARCHIVED') THEN
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
    AFTER UPDATE OR DELETE ON auth_identities
    FOR EACH ROW EXECUTE FUNCTION invalidate_user_bot_credential_messages();

CREATE FUNCTION normalize_generated_password_origin() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.password_changed OR NEW.initial_password IS NULL THEN
        NEW.generated_password_origin := NULL;
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER a_auth_identity_normalize_generated_password_origin
BEFORE INSERT OR UPDATE ON auth_identities FOR EACH ROW EXECUTE FUNCTION normalize_generated_password_origin();
-- V57 remains the sole credentials_version/bot-message invalidation owner.

CREATE TABLE subject_deletion_proofs (
    challenge_hash varchar(64) PRIMARY KEY,
    operation_id uuid NOT NULL,
    subject_id bigint NOT NULL CHECK (subject_id > 0),
    semester_id bigint NOT NULL CHECK (semester_id > 0),
    group_id bigint NOT NULL CHECK (group_id > 0),
    actor_id bigint NOT NULL CHECK (actor_id > 0),
    session_id uuid NOT NULL,
    session_version bigint NOT NULL,
    roles_version bigint NOT NULL,
    credentials_version bigint NOT NULL,
    binding_version bigint NOT NULL,
    telegram_id bigint NOT NULL,
    preview_digest varchar(64) NOT NULL CHECK (preview_digest ~ '^[0-9a-f]{64}$'),
    code_hash varchar(64) NOT NULL,
    attempts integer NOT NULL DEFAULT 0 CHECK (attempts >= 0),
    created_at timestamptz NOT NULL,
    expires_at timestamptz NOT NULL,
    consumed_at timestamptz,
    receipt_id uuid UNIQUE,
    CHECK (expires_at > created_at),
    CHECK ((consumed_at IS NULL) = (receipt_id IS NULL))
);

CREATE INDEX subject_deletion_proof_actor_time ON subject_deletion_proofs (actor_id, created_at);
