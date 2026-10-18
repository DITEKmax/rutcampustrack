-- Trusted initial fixture snapshot in Academic's own DB; never copies Auth authority.
-- Keep the original V2 IDs/logins' profile mapping for existing browser consumers.
-- The single DO statement is atomic; a failed callback cannot leave a partial baseline.
DO $baseline$
DECLARE
    expected_fixture_key CONSTANT text := 'browser-v2-split-20261018-v1';
    expected_fingerprint CONSTANT bytea := sha256(convert_to(expected_fixture_key || ':academic', 'UTF8'));
BEGIN
    IF current_database() <> 'academic_db' OR current_user <> 'rct_migrator'
       OR current_setting('rct.e2e.synthetic_baseline', true) IS DISTINCT FROM expected_fixture_key THEN
        RAISE EXCEPTION 'Disposable Academic baseline scope is required';
    END IF;
    PERFORM pg_advisory_xact_lock(724830210099::bigint);

    IF to_regclass('public.e2e_synthetic_baseline') IS NOT NULL THEN
        IF (SELECT count(*) FROM public.e2e_synthetic_baseline) <> 1
           OR NOT EXISTS (SELECT FROM public.e2e_synthetic_baseline m
                          WHERE m.fixture_key = expected_fixture_key AND m.fingerprint = expected_fingerprint) THEN
            RAISE EXCEPTION 'Conflicting Academic fixture marker';
        END IF;
        -- A successful initialization is immutable. Subsequent legitimate domain changes survive.
        RETURN;
    END IF;

    IF EXISTS (SELECT FROM academic_profiles)
       OR EXISTS (SELECT FROM academic_role_eligibility)
       OR EXISTS (SELECT FROM groups)
       OR EXISTS (SELECT FROM semesters)
       OR EXISTS (SELECT FROM student_group_history)
       OR EXISTS (SELECT FROM group_history_coverage)
       OR EXISTS (SELECT FROM campus_settings)
       OR EXISTS (SELECT FROM academic_identity_operations)
       OR EXISTS (SELECT FROM academic_identity_read_model)
       OR NOT EXISTS (SELECT FROM academic_identity_catalog_cursor
                      WHERE singleton AND applied_catalog_version = 0) THEN
        RAISE EXCEPTION 'Unmarked Academic baseline must be empty';
    END IF;

    INSERT INTO academic_profiles(id, directory_role, directory_status, last_name, first_name,
        middle_name, employee_number, academic_revision, applied_auth_roles_version,
        created_at, updated_at)
    VALUES
        (1, 'ADMIN', 'ACTIVE', 'Администратов', 'Админ', 'Системович', NULL, 1, 1, NOW(), NOW()),
        (2, 'TEACHER', 'ACTIVE', 'Преподавателев', 'Учитель', 'Знаниевич', 'T00001', 1, 1, NOW(), NOW()),
        (3, 'STUDENT', 'ACTIVE', 'Студентов', 'Студент', 'Староста', NULL, 1, 1, NOW(), NOW());
    INSERT INTO groups(id, name, is_active, alphabetic_code, numeric_code, current_course,
        training_duration_years, duration_status, academic_revision)
    VALUES(1, 'ИВТ-211', TRUE, 'ИВТ', '211', 2, 4, 'KNOWN', 1);
    INSERT INTO academic_role_eligibility(user_id, role, status, group_id, updated_at)
    VALUES(2, 'TEACHER', 'ACTIVE', NULL, NOW()),
          (3, 'STUDENT', 'ACTIVE', 1, NOW()),
          (3, 'HEADMAN', 'ACTIVE', 1, NOW());
    INSERT INTO semesters(id, name, date_from, date_to, is_active, semester_type, academic_year)
    VALUES(1, 'Spring 2026', '2026-02-01', '2026-06-30', TRUE, 'SPRING', 2025);
    INSERT INTO student_group_history(id, user_id, group_id, joined_at, reason)
    VALUES(1, 3, 1, '2026-02-01', 'initial-enrollment');
    INSERT INTO group_history_coverage(group_id, coverage_from, writer_version)
    VALUES(1, '2026-02-01', 'managed_v1');
    INSERT INTO campus_settings(id, name, lat, lng, radius_m)
    VALUES(1, 'Main Campus', 55.788204, 37.606762, 200);

    -- Only sequences with explicitly assigned snapshot IDs are advanced; repeat never resets them.
    PERFORM setval(pg_get_serial_sequence('groups', 'id'), 1, TRUE);
    PERFORM setval(pg_get_serial_sequence('semesters', 'id'), 1, TRUE);
    PERFORM setval(pg_get_serial_sequence('student_group_history', 'id'), 1, TRUE);
    PERFORM setval(pg_get_serial_sequence('campus_settings', 'id'), 1, TRUE);

    -- Do not populate academic_identity_read_model: its normal owner-fed RPC synchronizer
    -- consumes the real final Auth catalog only after this local baseline is committed.
    CREATE TABLE public.e2e_synthetic_baseline(
        fixture_key text PRIMARY KEY CHECK(fixture_key = 'browser-v2-split-20261018-v1'),
        fingerprint bytea NOT NULL CHECK(octet_length(fingerprint) = 32)
    );
    REVOKE ALL ON public.e2e_synthetic_baseline FROM PUBLIC, rct_academic_runtime;
    CREATE TRIGGER e2e_synthetic_baseline_immutable BEFORE UPDATE OR DELETE
        ON public.e2e_synthetic_baseline FOR EACH ROW
        EXECUTE FUNCTION protect_identity_preflight_rejection();
    INSERT INTO public.e2e_synthetic_baseline VALUES(expected_fixture_key, expected_fingerprint);
END
$baseline$;
