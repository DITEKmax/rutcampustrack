-- V8: Reset ISO parity reconciler marker so it re-runs on next boot.
-- The parity mapping was inverted (even ISO → EVEN, should be even ISO → ODD).
-- Clearing the marker lets IsoParityReconciler regenerate PLANNED lessons
-- with the corrected parity.
DELETE FROM iso_parity_reconciliation WHERE id = 1;
