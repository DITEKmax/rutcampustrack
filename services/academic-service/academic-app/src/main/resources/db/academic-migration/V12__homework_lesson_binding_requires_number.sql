-- CHECK accepts UNKNOWN: a LESSON row with a NULL number otherwise passes BETWEEN.
-- Keep DATE rows nullable, and validate retained rows without rewriting their binding.
ALTER TABLE homeworks
    DROP CONSTRAINT homeworks_binding_mode_chk,
    ADD CONSTRAINT homeworks_binding_mode_chk CHECK (
        (binding_mode = 'LESSON' AND lesson_number IS NOT NULL AND lesson_number BETWEEN 1 AND 8)
        OR (binding_mode = 'DATE' AND lesson_number IS NULL));
