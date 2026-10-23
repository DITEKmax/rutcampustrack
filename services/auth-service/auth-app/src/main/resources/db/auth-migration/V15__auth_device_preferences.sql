-- Opaque browser context only: no identity/session/IP/user-agent association.
CREATE TABLE auth_device_preferences (
    selector_hash bytea PRIMARY KEY CHECK (octet_length(selector_hash)=32),
    theme varchar(6) CHECK (theme IN ('light','dark','system')),
    revision bigint NOT NULL DEFAULT 0 CHECK (revision>=0),
    expires_at timestamptz NOT NULL
);
CREATE INDEX auth_device_preferences_expiry ON auth_device_preferences(expires_at);
REVOKE ALL ON auth_device_preferences FROM PUBLIC;
-- Runtime bootstrap may precede this additive migration.
DO $$ BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname='rct_auth_runtime') THEN
        GRANT SELECT,INSERT,UPDATE,DELETE ON auth_device_preferences TO rct_auth_runtime;
    END IF;
END $$;
