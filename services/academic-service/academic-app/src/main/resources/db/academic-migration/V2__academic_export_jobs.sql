-- Academic is the only durable queue/artifact owner. No Auth foreign keys or caller tokens.
CREATE TABLE academic_export_jobs (
    job_id uuid PRIMARY KEY,
    user_id bigint NOT NULL CHECK (user_id > 0),
    session_id uuid NOT NULL,
    request_id uuid NOT NULL,
    binding_hash varchar(64) NOT NULL CHECK (binding_hash ~ '^[0-9a-f]{64}$'),
    identity jsonb NOT NULL,
    state varchar(16) NOT NULL DEFAULT 'queued' CHECK (state IN ('queued','preparing','ready','error')),
    attempts integer NOT NULL DEFAULT 0 CHECK (attempts BETWEEN 0 AND 3),
    lease_id uuid,
    lease_until timestamptz,
    requires_full boolean NOT NULL DEFAULT false,
    content bytea,
    content_type varchar(128),
    row_count integer CHECK (row_count >= 0 AND row_count <= 10000),
    error_code varchar(32) CHECK (error_code IN ('AUTHORITY_DENIED','GENERATION_FAILED','EXPORT_TOO_LARGE')),
    created_at timestamptz NOT NULL DEFAULT now(),
    expires_at timestamptz NOT NULL,
    CONSTRAINT academic_export_jobs_request UNIQUE (user_id,session_id,request_id),
    CONSTRAINT academic_export_jobs_ready CHECK ((state='ready')=(content IS NOT NULL)),
    CONSTRAINT academic_export_jobs_size CHECK (content IS NULL OR octet_length(content) BETWEEN 1 AND 20971520),
    CONSTRAINT academic_export_jobs_running CHECK (state<>'preparing' OR (lease_id IS NOT NULL AND lease_until IS NOT NULL)),
    CONSTRAINT academic_export_jobs_retention CHECK (expires_at>created_at AND expires_at<=created_at+interval '10 minutes')
);
CREATE INDEX academic_export_jobs_queue ON academic_export_jobs(created_at,job_id) WHERE state IN ('queued','preparing');
CREATE INDEX academic_export_jobs_expiry ON academic_export_jobs(expires_at,job_id);
