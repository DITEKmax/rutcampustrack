-- Trusted initial fixture snapshot, not a replayable business provisioning command.
-- Mirrors AuthDatabaseFixture and the Java two-DB fixture's first-full bootstrap.
-- The single DO statement is atomic; a failed callback cannot leave a partial baseline.
DO $baseline$
DECLARE
    expected_fixture_key CONSTANT text := 'browser-v2-split-20261018-v1';
    expected_fingerprint CONSTANT bytea := sha256(convert_to(expected_fixture_key || ':auth:teacher-telegram-v1', 'UTF8'));
    synthetic_hash CONSTANT text := '$2a$10$A9r8miSBxjlpjxFB/z0jIerCCSOrLQP6N.sXrjBAw9l7iy4vmRFpi';
    actor record;
    operation uuid;
    final_catalog_version bigint;
BEGIN
    IF current_database() <> 'auth_db' OR current_user <> 'rct_auth_migrator'
       OR current_setting('rct.e2e.synthetic_baseline', true) IS DISTINCT FROM expected_fixture_key THEN
        RAISE EXCEPTION 'Disposable Auth baseline scope is required';
    END IF;
    PERFORM pg_advisory_xact_lock(724830210099::bigint);

    IF to_regclass('public.e2e_synthetic_baseline') IS NOT NULL THEN
        IF (SELECT count(*) FROM public.e2e_synthetic_baseline) <> 1
           OR NOT EXISTS (SELECT FROM public.e2e_synthetic_baseline m
                          WHERE m.fixture_key = expected_fixture_key AND m.fingerprint = expected_fingerprint) THEN
            RAISE EXCEPTION 'Conflicting Auth fixture marker';
        END IF;
        -- Do not inspect or reset mutable accounts, roles, credentials, sessions or catalog.
        RETURN;
    END IF;

    IF EXISTS (SELECT FROM auth_identities)
       OR EXISTS (SELECT FROM user_role_grants)
       OR EXISTS (SELECT FROM admin_access_profiles)
       OR EXISTS (SELECT FROM auth_identity_operations)
       OR EXISTS (SELECT FROM auth_sessions)
       OR EXISTS (SELECT FROM auth_identity_catalog_changes)
       OR NOT EXISTS (SELECT FROM auth_identity_catalog_clock WHERE singleton = 1 AND version = 0) THEN
        RAISE EXCEPTION 'Unmarked Auth baseline must be empty';
    END IF;

    FOR actor IN SELECT * FROM (VALUES
        (1::bigint, 'admin'::text, 'admin'::text),
        (2::bigint, 'teacher'::text, 'teacher'::text),
        (3::bigint, 'student'::text, 'student'::text)
    ) AS expected(id, login, role)
    LOOP
        operation := ('719e2e00-0000-4000-8000-' || lpad(actor.id::text, 12, '0'))::uuid;
        INSERT INTO auth_identity_operations(
            operation_id, command_hash, canonical_command, kind, authorization_kind,
            actor_id, actor_session_id, actor_session_version, actor_roles_version,
            actor_credentials_version, actor_binding_version, actor_commit_roles_version,
            actor_admin_profile_version)
        VALUES(operation, sha256(convert_to(expected_fixture_key || ':' || actor.login, 'UTF8')),
            convert_to(expected_fixture_key || ':' || actor.login, 'UTF8'), 'CREATE', 'CREATE',
            1, '719e2e00-0000-4000-9000-000000000001', 1, 1, 1, 1, 1, 1);

        -- The legacy V2 actors had no generated initial credential to disclose.
        INSERT INTO auth_identities(id, login, password_hash, initial_password,
            account_state, provision_operation_id, fence_operation_id, telegram_id)
        VALUES(actor.id, actor.login, synthetic_hash, NULL, 'PENDING', operation, operation,
            CASE WHEN actor.id = 2 THEN 900000001 ELSE NULL END);
        INSERT INTO user_role_grants(id, user_id, role, status, group_id)
        VALUES(actor.id, actor.id, actor.role, 'active',
            CASE WHEN actor.role = 'student' THEN 1 ELSE NULL END);
        IF actor.role = 'student' THEN
            INSERT INTO user_role_grants(id, user_id, role, status, group_id)
            VALUES(4, actor.id, 'headman', 'active', 1);
        ELSIF actor.role = 'admin' THEN
            INSERT INTO admin_access_profiles(user_id, assigned, mode, permissions, version)
            VALUES(actor.id, TRUE, 'FULL', '{}', 1);
        END IF;

        INSERT INTO auth_identity_operation_targets(operation_id, user_id,
            expected_roles_version, final_roles_version, expected_credentials_version,
            prepared_credentials_version, expected_binding_version, prepared_binding_version,
            final_binding_version, expected_academic_revision, committed_academic_revision,
            desired_grants, desired_admin_profile, desired_account_state)
        SELECT operation, u.id, 0, 1, 0, 1, 0, 1, 1, 0, 1,
            (SELECT jsonb_agg(jsonb_build_object('grantId', g.id, 'role', upper(g.role),
                'status', upper(g.status), 'groupId', coalesce(g.group_id, 0)) ORDER BY g.id)
             FROM user_role_grants g WHERE g.user_id = u.id),
            CASE WHEN actor.role = 'admin'
                THEN '{"assigned":true,"mode":"FULL","permissions":[],"version":1}'::jsonb
                ELSE '{"assigned":false,"permissions":[],"version":0}'::jsonb END,
            'ACTIVE'
        FROM auth_identities u WHERE u.id = actor.id;

        SELECT version + 1 INTO STRICT final_catalog_version
        FROM auth_identity_catalog_clock WHERE singleton = 1;
        UPDATE auth_identity_operations SET phase = 'COMMITTED', revision = revision + 1,
            finished_at = NOW(), committed_catalog_version = final_catalog_version
        WHERE operation_id = operation;
        -- The production fence-release guard checks the exact committed local-revision receipt.
        -- Its real trigger emits the final ACTIVE catalog snapshot after all grants/profile exist.
        UPDATE auth_identities SET account_state = 'ACTIVE', fence_operation_id = NULL
        WHERE id = actor.id;
    END LOOP;

    IF EXISTS (SELECT FROM auth_identities u
               JOIN auth_identity_operations o ON o.operation_id = u.provision_operation_id
               JOIN auth_identity_catalog_changes c ON c.catalog_version = u.catalog_version
               WHERE u.account_state <> 'ACTIVE' OR u.fence_operation_id IS NOT NULL
                  OR o.phase <> 'COMMITTED' OR o.committed_catalog_version <> u.catalog_version
                  OR c.projection->>'account_state' <> 'ACTIVE'
                  OR c.projection->>'academic_revision' <> '1'
                  OR u.telegram_id IS DISTINCT FROM CASE WHEN u.id = 2 THEN 900000001 ELSE NULL END
                  OR u.telegram_binding_version <> 1
                  OR c.projection->>'telegram_id' IS DISTINCT FROM
                      CASE WHEN u.id = 2 THEN '900000001' ELSE NULL END
                  OR c.projection->>'telegram_binding_version' IS DISTINCT FROM '1') THEN
        RAISE EXCEPTION 'Auth fixture final receipt or catalog is inconsistent';
    END IF;
    PERFORM setval(pg_get_serial_sequence('auth_identities', 'id'), 3, TRUE);
    PERFORM setval(pg_get_serial_sequence('user_role_grants', 'id'), 4, TRUE);

    CREATE TABLE public.e2e_synthetic_baseline(
        fixture_key text PRIMARY KEY CHECK(fixture_key = 'browser-v2-split-20261018-v1'),
        fingerprint bytea NOT NULL CHECK(octet_length(fingerprint) = 32)
    );
    REVOKE ALL ON public.e2e_synthetic_baseline FROM PUBLIC, rct_auth_runtime;
    CREATE TRIGGER e2e_synthetic_baseline_immutable BEFORE UPDATE OR DELETE
        ON public.e2e_synthetic_baseline FOR EACH ROW
        EXECUTE FUNCTION protect_auth_identity_catalog_change();
    INSERT INTO public.e2e_synthetic_baseline VALUES(expected_fixture_key, expected_fingerprint);
END
$baseline$;
