-- V9: Re-reset ISO parity reconciler marker.
-- V8 already deleted the marker row once, but the reconciler failed at runtime
-- with a duplicate-key collision on UNIQUE (schedule_item_id, date) because
-- CANCELLED future lessons left over from the old parity algorithm still
-- occupied the target dates the new ISO-anchored algorithm wanted to regenerate.
-- This run pairs with the reconciler code change that now wipes both PLANNED
-- and CANCELLED future lessons before reinsert, so the retry cannot collide.
DELETE FROM iso_parity_reconciliation WHERE id = 1;
