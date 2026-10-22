-- Default own-schema DML grants must not make migration metadata writable by
-- the application runtime. Fixtures that omit this production role may migrate.
DO $$
BEGIN
    IF EXISTS(SELECT 1 FROM pg_roles WHERE rolname='rct_auth_runtime') THEN
        REVOKE ALL ON TABLE public.flyway_schema_history FROM rct_auth_runtime;
    END IF;
END $$;
