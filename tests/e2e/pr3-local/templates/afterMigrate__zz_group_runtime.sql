-- New schedule_db ONLY; run after the exact afterMigrate.sql callback.
DO $group_runtime$
DECLARE n text; rel regclass; seq record; f record;
BEGIN
 IF current_database()<>'schedule_db' OR current_user<>'rct_migrator' THEN RAISE EXCEPTION 'Wrong isolated database/migration identity'; END IF;
 IF EXISTS(SELECT 1 FROM pg_auth_members WHERE member=(SELECT oid FROM pg_roles WHERE rolname='rct_group_schedule_runtime')) THEN RAISE EXCEPTION 'Runtime must have no memberships'; END IF;
 IF EXISTS(SELECT 1 FROM pg_roles WHERE rolname='rct_group_schedule_runtime' AND (rolsuper OR rolinherit OR rolcreatedb OR rolcreaterole OR rolreplication OR rolbypassrls)) THEN RAISE EXCEPTION 'Unsafe runtime role'; END IF;
 REVOKE ALL ON SCHEMA public FROM rct_group_schedule_runtime;
 GRANT USAGE ON SCHEMA public TO rct_group_schedule_runtime;
 FOREACH n IN ARRAY ARRAY[
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
            'shedlock'] LOOP
  rel:=to_regclass(format('public.%I',n));
  IF rel IS NULL THEN CONTINUE; END IF;
  IF NOT has_table_privilege('rct_runtime',rel,'SELECT,INSERT,UPDATE,DELETE') THEN RAISE EXCEPTION 'Exact base callback must run first'; END IF;
  EXECUTE format('GRANT SELECT,INSERT,UPDATE,DELETE ON TABLE %s TO rct_group_schedule_runtime',rel);
  FOR seq IN SELECT ns.nspname,c.relname FROM pg_class c JOIN pg_namespace ns ON ns.oid=c.relnamespace JOIN pg_depend d ON d.classid='pg_class'::regclass AND d.objid=c.oid WHERE c.relkind='S' AND d.refobjid=rel AND d.deptype IN('a','i') LOOP
   EXECUTE format('GRANT USAGE,SELECT ON SEQUENCE %I.%I TO rct_group_schedule_runtime',seq.nspname,seq.relname);
  END LOOP;
 END LOOP;
 -- Only explicit migration-granted EXECUTE privileges of the closed runtime role.
 FOR f IN SELECT DISTINCT p.oid::regprocedure AS signature FROM pg_proc p JOIN pg_namespace ns ON ns.oid=p.pronamespace CROSS JOIN LATERAL aclexplode(p.proacl)a WHERE ns.nspname='public' AND p.prokind='f' AND a.grantee=(SELECT oid FROM pg_roles WHERE rolname='rct_runtime') AND a.privilege_type='EXECUTE' LOOP
  EXECUTE format('GRANT EXECUTE ON FUNCTION %s TO rct_group_schedule_runtime',f.signature);
 END LOOP;
 REVOKE ALL ON TABLE public.flyway_schema_history FROM rct_group_schedule_runtime;
 IF has_schema_privilege('rct_group_schedule_runtime','public','CREATE') OR has_table_privilege('rct_group_schedule_runtime','group_deletion_participants','TRUNCATE') THEN RAISE EXCEPTION 'Runtime DDL/TRUNCATE forbidden'; END IF;
END $group_runtime$;
