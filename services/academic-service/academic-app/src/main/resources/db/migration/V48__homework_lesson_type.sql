ALTER TABLE homeworks ADD COLUMN lesson_type VARCHAR(16) CHECK (lesson_type IN ('LECTURE', 'PRACTICE', 'LAB'));

-- Keep exact admitted snapshots for legacy null type; compare and fence the new field when known.
DO $$ DECLARE fn TEXT; updated TEXT;
BEGIN
    SELECT pg_get_functiondef('protect_homework_binding_identity()'::regprocedure) INTO fn;
    updated := replace(fn, 'OR NEW.revision IS DISTINCT FROM OLD.revision)',
        'OR NEW.lesson_type IS DISTINCT FROM OLD.lesson_type OR NEW.revision IS DISTINCT FROM OLD.revision)');
    IF updated = fn THEN RAISE EXCEPTION 'V48 terminal homework guard anchor changed'; END IF;
    EXECUTE updated;

    SELECT pg_get_functiondef('academic_homework_edit_continuation_admitted(jsonb,jsonb,bigint)'::regprocedure) INTO fn;
    updated := replace(fn, '''lessonNumber'', new_row -> ''lesson_number'')',
        '''lessonNumber'', new_row -> ''lesson_number'') || CASE WHEN new_row ->> ''lesson_type'' IS NULL THEN ''{}''::jsonb ELSE jsonb_build_object(''lessonType'', new_row -> ''lesson_type'') END');
    updated := replace(updated, '''binding_mode'',''revision'',', '''binding_mode'',''lesson_type'',''revision'',');
    IF updated = fn THEN RAISE EXCEPTION 'V48 edit continuation anchor changed'; END IF;
    EXECUTE updated;

    SELECT pg_get_functiondef('guard_homework_edit_business_write()'::regprocedure) INTO fn;
    updated := replace(fn, '''lessonNumber'', h.lesson_number))',
        '''lessonNumber'', h.lesson_number) || CASE WHEN h.lesson_type IS NULL THEN ''{}''::jsonb ELSE jsonb_build_object(''lessonType'', h.lesson_type) END)');
    IF updated = fn THEN RAISE EXCEPTION 'V48 transfer history anchor changed'; END IF;
    EXECUTE updated;
END $$;
