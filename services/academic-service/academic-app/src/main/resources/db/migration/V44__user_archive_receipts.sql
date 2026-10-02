-- No credential or former authority snapshot: restore never reactivates grants.
CREATE TABLE user_archive_preview (
    preview_digest VARCHAR(64) PRIMARY KEY CHECK (preview_digest ~ '^[0-9a-f]{64}$'),
    owner_id BIGINT NOT NULL REFERENCES users(id),
    target_id BIGINT NOT NULL REFERENCES users(id),
    academic_digest VARCHAR(64) NOT NULL,
    requires_password BOOLEAN NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX user_archive_preview_expiry ON user_archive_preview(expires_at);
CREATE TABLE user_archive_receipt (
    operation_id UUID PRIMARY KEY,
    owner_id BIGINT NOT NULL REFERENCES users(id),
    target_id BIGINT NOT NULL REFERENCES users(id),
    action VARCHAR(16) NOT NULL CHECK (action IN ('ARCHIVE', 'RESTORE')),
    preview_digest VARCHAR(64),
    requires_password BOOLEAN NOT NULL,
    completed_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CHECK ((action = 'ARCHIVE' AND preview_digest IS NOT NULL AND preview_digest ~ '^[0-9a-f]{64}$')
        OR (action = 'RESTORE' AND preview_digest IS NULL AND NOT requires_password))
);
