-- Flyway11.7.2 afterMigrate callback, before application JPA initialization.
-- Only the current, explicitly reviewed application tables gain runtime DML.
-- Unknown future tables fail closed until this allowlist is deliberately updated.
DO $runtime_grants$
DECLARE
    application_tables text[];
    table_name text;
    table_id regclass;
    sequence_record record;
BEGIN
    IF current_user <> 'rct_migrator' THEN
        RAISE EXCEPTION 'Runtime grants require the migration identity';
    END IF;
    CASE current_database()
        WHEN 'academic_db' THEN
            -- The additive Auth/Academic ownership migration is the single
            -- authority for table/column/sequence and exact command grants.
            -- Never restore shared legacy DML after that migration has revoked it.
            REVOKE CREATE ON SCHEMA public FROM PUBLIC, rct_runtime,
                rct_auth_runtime, rct_academic_runtime;
            RETURN;
        WHEN 'schedule_db' THEN application_tables := ARRAY[
            'event_consumer_processed',
            'homework_placement_archive_admissions',
            'homework_placement_operations',
            'iso_parity_reconciliation',
            'lesson_homework_bindings',
            'lesson_lifecycle_entries',
            'lesson_occurrences',
            'lesson_restore_authorities',
            'lesson_room_change_ledger',
            'lesson_template_restore_authorities',
            'lesson_transfer_authorities',
            'lesson_transfer_binding_batches',
            'lesson_transfer_operations',
            'lesson_transfer_participant_receipts',
            'lessons',
            'one_off_lesson_restore_authorities',
            'schedule_assignment_binding_ledger',
            'schedule_assignment_fences',
            'schedule_assignment_rebind_ledger',
            'schedule_assignment_replacement_operations',
            'schedule_assignment_replacement_templates',
            'schedule_assignment_template_close_ledger',
            'schedule_items',
            'schedule_one_off_create_replay',
            'schedule_one_off_lessons',
            'schedule_outbox',
            'schedule_recurring_create_replay',
            'schedule_recurring_lifecycle_replay',
            'schedule_semester_archive_barriers',
            'schedule_semester_archive_effect_ledger',
            'schedule_subject_deletion_participants',
            'group_deletion_participants',
            'schedule_transfer_replay',
            'shedlock'];
        ELSE RAISE EXCEPTION 'Unexpected application database';
    END CASE;
    REVOKE CREATE ON SCHEMA public FROM PUBLIC, rct_runtime;
    GRANT USAGE ON SCHEMA public TO rct_runtime;
    FOREACH table_name IN ARRAY application_tables LOOP
        table_id := to_regclass(format('public.%I', table_name));
        IF table_id IS NULL THEN CONTINUE; END IF;
        EXECUTE format('GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE %s TO rct_runtime', table_id);
        FOR sequence_record IN
            SELECT ns.nspname, seq.relname
            FROM pg_catalog.pg_class seq
            JOIN pg_catalog.pg_namespace ns ON ns.oid = seq.relnamespace
            JOIN pg_catalog.pg_depend dep ON dep.classid = 'pg_class'::regclass AND dep.objid = seq.oid
            WHERE seq.relkind = 'S' AND dep.refobjid = table_id AND dep.deptype IN ('a', 'i')
        LOOP
            EXECUTE format('GRANT USAGE, SELECT ON SEQUENCE %I.%I TO rct_runtime',
                           sequence_record.nspname, sequence_record.relname);
        END LOOP;
    END LOOP;
    IF to_regclass('public.flyway_schema_history') IS NOT NULL THEN
        REVOKE ALL ON TABLE public.flyway_schema_history FROM rct_runtime;
    END IF;
END
$runtime_grants$;
