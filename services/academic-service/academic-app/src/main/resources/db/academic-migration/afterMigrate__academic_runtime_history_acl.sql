-- Runtime owns domain DML, never the migration ledger. Isolated fixtures may
-- omit the production runtime role.
DO $$
BEGIN
    IF EXISTS(SELECT 1 FROM pg_roles WHERE rolname='rct_academic_runtime') THEN
        REVOKE ALL ON TABLE public.flyway_schema_history FROM rct_academic_runtime;
    END IF;
END $$;
