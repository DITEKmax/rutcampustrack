-- Marker table used by IsoParityReconciler to run exactly once.
-- Insert-only — the reconciler writes a single row after it finishes
-- regenerating future PLANNED lessons to align with ISO-week parity.
-- Flyway itself only creates the table; population is deferred to the
-- application lifecycle because regeneration requires calling the
-- academic-service over gRPC (cannot be done from a SQL migration).

CREATE TABLE IF NOT EXISTS iso_parity_reconciliation (
    id           SMALLINT PRIMARY KEY,
    executed_at  TIMESTAMPTZ NOT NULL,
    note         TEXT
);
