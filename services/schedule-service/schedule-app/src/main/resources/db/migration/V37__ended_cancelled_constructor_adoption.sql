-- Extend only the existing natural adoption predicate. The exact origin, fence,
-- ledger and snapshot guards continue to apply to the same physical lesson.
DO $migration$
DECLARE
    definition TEXT;
    original TEXT := $original$lesson.status='closed' OR lesson.status NOT IN('active','closed')$original$;
    replacement TEXT := $replacement$lesson.status='closed'
                  OR (lesson.status='cancelled'
                      AND (lesson.date+lesson.end_time) AT TIME ZONE 'Europe/Moscow'<=NOW())
                  OR lesson.status NOT IN('active','closed')$replacement$;
BEGIN
    SELECT pg_get_functiondef('constructor_natural_adoption_authorized(jsonb)'::regprocedure) INTO STRICT definition;
    IF length(definition) - length(replace(definition, original, '')) <> length(original) THEN
        RAISE EXCEPTION 'constructor retrospective adoption status predicate changed';
    END IF;
    EXECUTE replace(definition, original, replacement);
END
$migration$;
