-- QR LOGIN capabilities have separate issuer and approval proofs; no bearer material is stored.
ALTER TABLE auth_sessions DROP CONSTRAINT auth_sessions_method_chk,
    ADD CONSTRAINT auth_sessions_method_chk CHECK (auth_method IN ('PASSWORD', 'OTP', 'TMA', 'QR'));
ALTER TABLE account_security_events DROP CONSTRAINT account_security_events_method_chk,
    ADD CONSTRAINT account_security_events_method_chk
        CHECK (auth_method IS NULL OR auth_method IN ('PASSWORD', 'OTP', 'TMA', 'QR'));

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
    confirmed_user_id BIGINT REFERENCES users(id) ON DELETE RESTRICT,
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
