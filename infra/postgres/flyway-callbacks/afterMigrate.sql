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
        WHEN 'academic_db' THEN application_tables := ARRAY[
            'academic_initial_membership_backfills',
            'academic_outbox',
            'academic_semester_archive_barriers',
            'academic_semester_archive_effect_receipts',
            'academic_semester_archive_publication_admissions',
            'account_security_events',
            'admin_access_profiles',
            'assignment_replacement_operations',
            'assignments',
            'attendance_thresholds',
            'audit_log',
            'auth_export_jobs',
            'auth_outbox',
            'auth_sessions',
            'bot_credential_messages',
            'campus_map_activity_coverage',
            'campus_map_asset',
            'campus_map_building',
            'campus_map_catalog_revision',
            'campus_map_deletion_receipt',
            'campus_map_floor',
            'campus_map_floor_daily_demand',
            'campus_map_floor_demand_dedupe',
            'campus_map_floor_hourly_activity',
            'campus_map_open_intent',
            'campus_map_plan_format',
            'campus_map_plan_version',
            'campus_settings',
            'dashboard_scale_observations',
            'event_consumer_processed',
            'group_history_coverage',
            'group_promotion_cycle_record',
            'group_promotion_notification_cycle',
            'groups',
            'headman_assistants',
            'homework_binding_archives',
            'homework_binding_transfer_history',
            'homework_binding_transfer_markers',
            'homework_completions',
            'homework_edit_archive_admissions',
            'homework_edit_history',
            'homework_edit_operations',
            'homework_edit_receipts',
            'homework_weekly_digest_runs',
            'homeworks',
            'lesson_transfer_receipts',
            'password_reset_tokens',
            'qr_login_challenges',
            'qr_login_issuers',
            'semester_archive_operations',
            'semesters',
            'shedlock',
            'student_group_history',
            'subject_deletion_operations',
            'subject_deletion_proofs',
            'subject_lesson_types',
            'subjects',
            'teacher_subject_groups',
            'user_archive_preview',
            'user_archive_receipt',
            'user_role_grants',
            'users'];
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
