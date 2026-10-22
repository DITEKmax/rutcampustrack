-- Retain only a closed Auth field-conflict subtype with the durable negative intent.
-- Submitted values, credentials and arbitrary remote error text are never stored.
ALTER TABLE academic_identity_operations
    ADD COLUMN rejection_code varchar(32)
    CHECK (rejection_code IS NULL OR rejection_code = 'TELEGRAM_ID_CONFLICT');
ALTER TABLE academic_identity_operations
    ADD CONSTRAINT identity_rejection_code_reason CHECK (
        rejection_code IS NULL OR (rejection_reason IS NOT NULL AND rejection_reason = 'TARGET_CONFLICT'));
