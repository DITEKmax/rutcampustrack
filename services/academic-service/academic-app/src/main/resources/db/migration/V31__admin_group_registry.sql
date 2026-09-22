-- ADMIN group registry: split code parts and retain the training-duration
-- provenance needed by the registry. Existing rows remain readable; values
-- are derived only from their canonical legacy name when that derivation is
-- unambiguous, and otherwise stay explicitly LEGACY_UNKNOWN.
ALTER TABLE groups
    ADD COLUMN alphabetic_code VARCHAR(4),
    ADD COLUMN numeric_code VARCHAR(3),
    ADD COLUMN current_course INTEGER,
    ADD COLUMN training_duration_years INTEGER,
    ADD COLUMN duration_status VARCHAR(24) NOT NULL DEFAULT 'LEGACY_UNKNOWN';

-- Reset first so invalid/contradictory legacy names stay explicitly unknown.
-- The predicate below is deliberately the same rule used by the Java fallback:
-- bachelor courses 1..4 and master courses 1..2 are derivable; every other
-- legacy combination remains nullable/LEGACY_UNKNOWN.
UPDATE groups
SET alphabetic_code = NULL,
    numeric_code = NULL,
    current_course = NULL,
    training_duration_years = NULL,
    duration_status = 'LEGACY_UNKNOWN';

UPDATE groups
SET alphabetic_code = substring(name FROM '^([А-ЯЁ][А-ЯЁа-яё]{1,3})-'),
    numeric_code = substring(name FROM '-(\d{3})'),
    current_course = substring(name FROM '-(\d)')::INTEGER,
    training_duration_years = CASE substring(name FROM '-\d(\d)\d')
        WHEN '1' THEN 4
        WHEN '7' THEN 2
    END,
    duration_status = 'KNOWN'
WHERE name ~ '^([А-ЯЁ][А-ЯЁа-яё]{1,3})-([1-4]1\d|[1-2]7\d)( \(выпуск \d{4}\))?$';

ALTER TABLE groups
    ADD CONSTRAINT groups_alphabetic_code_chk
        CHECK (alphabetic_code IS NULL OR alphabetic_code ~ '^[А-ЯЁ][А-ЯЁа-яё]{1,3}$'),
    ADD CONSTRAINT groups_numeric_code_chk
        CHECK (numeric_code IS NULL OR numeric_code ~ '^\d{3}$'),
    ADD CONSTRAINT groups_current_course_chk
        CHECK (current_course IS NULL OR current_course BETWEEN 1 AND 9),
    ADD CONSTRAINT groups_training_duration_chk
        CHECK (training_duration_years IS NULL OR training_duration_years >= 1),
    ADD CONSTRAINT groups_duration_status_chk
        CHECK (duration_status IN ('KNOWN', 'LEGACY_UNKNOWN'));

CREATE UNIQUE INDEX groups_code_pair_uq
    ON groups (alphabetic_code, numeric_code)
    WHERE is_active
      AND alphabetic_code IS NOT NULL AND numeric_code IS NOT NULL;

CREATE INDEX idx_groups_registry_code
    ON groups (alphabetic_code, numeric_code, id);
