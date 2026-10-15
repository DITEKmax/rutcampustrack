-- Provenance is explicit. Legacy changed/unknown credentials are never made displayable.
ALTER TABLE users ADD COLUMN generated_password_origin VARCHAR(16);
UPDATE users SET generated_password_origin = 'initial'
WHERE password_changed = FALSE AND initial_password IS NOT NULL AND initial_password ~ '[^[:space:]]' AND password_hash IS NOT NULL;
ALTER TABLE users ALTER COLUMN generated_password_origin SET DEFAULT 'initial';
ALTER TABLE users ADD CONSTRAINT generated_password_origin_code_chk
    CHECK (generated_password_origin IS NULL OR generated_password_origin IN ('initial', 'headman_reset'));

-- Current initial-user inserts already supply the generated plaintext and unchanged marker.
-- Clearing a password also clears provenance, including all existing self/reset writers.
CREATE FUNCTION normalize_generated_password_origin() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.password_changed OR NEW.initial_password IS NULL THEN
        NEW.generated_password_origin := NULL;
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER normalize_generated_password_origin
BEFORE INSERT OR UPDATE ON users FOR EACH ROW EXECUTE FUNCTION normalize_generated_password_origin();
-- V57 remains the sole credentials_version/bot-message invalidation owner.
