-- Nullable legacy unknowns: only actual accepted Schedule snapshots populate these identifiers.
ALTER TABLE homeworks
    ADD COLUMN placement_occurrence_id BIGINT,
    ADD COLUMN placement_lesson_id BIGINT,
    ADD COLUMN placement_binding_revision BIGINT,
    ADD CONSTRAINT homeworks_placement_identity_chk CHECK (
        (placement_binding_revision IS NULL AND placement_occurrence_id IS NULL AND placement_lesson_id IS NULL)
        OR (placement_binding_revision IS NOT NULL AND placement_binding_revision > 0 AND (
            (placement_occurrence_id IS NULL AND placement_lesson_id IS NULL)
            OR (binding_mode = 'LESSON' AND placement_occurrence_id IS NOT NULL AND placement_lesson_id IS NOT NULL
                AND placement_occurrence_id > 0 AND placement_lesson_id > 0))));

ALTER TABLE homework_edit_operations ADD COLUMN result_placement_identity JSONB;

DO $$ DECLARE fn TEXT; updated TEXT;
BEGIN
    SELECT pg_get_functiondef('protect_homework_edit_operation()'::regprocedure) INTO fn;
    updated := replace(fn, '''last_failure'']', '''last_failure'',''result_placement_identity'']');
    updated := replace(updated, 'OR NEW.result_revision IS DISTINCT FROM OLD.result_revision',
        'OR NEW.result_placement_identity IS DISTINCT FROM OLD.result_placement_identity
         OR NEW.result_revision IS DISTINCT FROM OLD.result_revision');
    IF updated = fn THEN RAISE EXCEPTION 'V47 edit result identity guard anchor changed'; END IF;
    EXECUTE updated;
END $$;

CREATE OR REPLACE FUNCTION protect_homework_placement_identity()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    IF OLD.placement_binding_revision IS NOT NULL
       AND (NEW.placement_binding_revision IS NULL OR NEW.placement_binding_revision < OLD.placement_binding_revision) THEN
        RAISE EXCEPTION 'homework placement revision cannot move backwards';
    END IF;
    IF NEW.placement_binding_revision = OLD.placement_binding_revision
       AND (NEW.placement_occurrence_id IS DISTINCT FROM OLD.placement_occurrence_id
            OR NEW.placement_lesson_id IS DISTINCT FROM OLD.placement_lesson_id) THEN
        RAISE EXCEPTION 'homework placement revision has immutable identity';
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER homework_placement_identity_guard BEFORE UPDATE ON homeworks
    FOR EACH ROW EXECUTE FUNCTION protect_homework_placement_identity();

-- Accepted edit continuation already proves content/scope/revision from its frozen operation.
-- Permit only its authoritative placement identity alongside those exact admitted content fields.
DO $$ DECLARE fn TEXT; updated TEXT;
BEGIN
    SELECT pg_get_functiondef('academic_homework_edit_continuation_admitted(jsonb,jsonb,bigint)'::regprocedure) INTO fn;
    updated := replace(fn, '''create_intent'',''create_intent_kind'']',
        '''create_intent'',''create_intent_kind'',''placement_occurrence_id'',''placement_lesson_id'',''placement_binding_revision'']');
    updated := replace(updated, 'AND edit.result_snapshot = jsonb_build_object',
        'AND ((edit.result_placement_identity IS NULL
                AND old_row -> ''placement_occurrence_id'' = new_row -> ''placement_occurrence_id''
                AND old_row -> ''placement_lesson_id'' = new_row -> ''placement_lesson_id''
                AND old_row -> ''placement_binding_revision'' = new_row -> ''placement_binding_revision'')
            OR edit.result_placement_identity = jsonb_build_object(
                ''placement_occurrence_id'', new_row -> ''placement_occurrence_id'',
                ''placement_lesson_id'', new_row -> ''placement_lesson_id'',
                ''placement_binding_revision'', new_row -> ''placement_binding_revision''))
          AND edit.result_snapshot = jsonb_build_object');
    IF updated = fn THEN RAISE EXCEPTION 'V47 accepted edit continuation anchor changed'; END IF;
    EXECUTE updated;
END $$;
