-- Auth owns these rows. Academic remains the sole Flyway pipeline for the shared database.
CREATE TABLE auth_export_jobs (
    job_id UUID PRIMARY KEY,
    user_id BIGINT NOT NULL,
    session_id UUID NOT NULL,
    request_id UUID NOT NULL,
    binding_hash VARCHAR(64) NOT NULL,
    identity JSONB NOT NULL,
    state VARCHAR(16) NOT NULL DEFAULT 'queued' CHECK (state IN ('queued','preparing','ready','error')),
    attempts INTEGER NOT NULL DEFAULT 0 CHECK (attempts BETWEEN 0 AND 3),
    lease_id UUID,
    lease_until TIMESTAMPTZ,
    requires_full BOOLEAN NOT NULL DEFAULT FALSE,
    content BYTEA,
    content_type VARCHAR(128),
    row_count INTEGER CHECK (row_count >= 0),
    error_code VARCHAR(32),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    expires_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT auth_export_jobs_request UNIQUE (user_id, session_id, request_id),
    CONSTRAINT auth_export_jobs_ready CHECK ((state = 'ready') = (content IS NOT NULL)),
    CONSTRAINT auth_export_jobs_size CHECK (content IS NULL OR octet_length(content) BETWEEN 1 AND 20971520)
);
CREATE INDEX auth_export_jobs_queue ON auth_export_jobs (created_at) WHERE state IN ('queued','preparing');
CREATE INDEX auth_export_jobs_expiry ON auth_export_jobs (expires_at);
