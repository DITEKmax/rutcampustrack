package ru.rutcampustrack.academic.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;

import ru.rutcampustrack.shared.testcontainers.MigrationTestUtils;

import javax.sql.DataSource;

/**
 * M08 Группа 3 (P2-8/3) — Flyway migration integration tests для academic_db.
 *
 * <p>Покрывает миграции V1..V38. Три template'а (D5 exception — fresh container без reuse):
 *
 * <ol>
 *   <li><b>freshInstallAppliesAllMigrations</b> — clean → migrate → applied>0, pending=0.
 *   <li><b>checksumConsistency</b> — migrate → validate no-op, repeat migrate no-op.
 *   <li><b>dataPreservationAcrossMigrations</b> — V1 baseline → INSERT group+user → V2..V17 →
 *       assert rows на месте. Покрывает V3 login_sequences, V7 avatars, V8 group unification, V9
 *       groups_archived_at, V12 subjects_group_id и прочие schema evolutions.
 * </ol>
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FlywayMigrationIT {

    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16")
                    .withDatabaseName("academic_db")
                    .withUsername("rct_user")
                    .withPassword("rct_dev_pass");
    // ВАЖНО: без .withReuse(true) — M08 D5 exception (fresh DB обязательна).

    private DataSource dataSource;
    private JdbcTemplate jdbc;

    @BeforeAll
    void startContainer() {
        POSTGRES.start();
        OwnedAuthPrincipals.prepare(POSTGRES);
        DriverManagerDataSource ds = new DriverManagerDataSource();
        ds.setDriverClassName("org.postgresql.Driver");
        ds.setUrl(POSTGRES.getJdbcUrl());
        ds.setUsername(POSTGRES.getUsername());
        ds.setPassword(POSTGRES.getPassword());
        this.dataSource = ds;
        this.jdbc = new JdbcTemplate(ds);
    }

    @AfterAll
    void stopContainer() {
        POSTGRES.stop();
    }

    @Test
    void freshInstallAppliesAllMigrations() {
        OwnedAuthPrincipals.cleanCommands(jdbc);
        MigrationTestUtils.clean(dataSource);
        Flyway flyway =
                Flyway.configure()
                        .dataSource(dataSource)
                        .locations("classpath:db/migration")
                        .cleanDisabled(false)
                        .load();

        MigrateResult result = flyway.migrate();

        assertThat(result.migrationsExecuted).isPositive();
        assertThat(result.success).isTrue();

        MigrationInfo[] pending = flyway.info().pending();
        assertThat(pending).isEmpty();

        MigrationInfo current = flyway.info().current();
        assertThat(current).isNotNull();
        assertThat(current.getState().isApplied()).isTrue();
        assertThat(current.getVersion())
                .isEqualTo(
                        java.util.Arrays.stream(flyway.info().all())
                                .map(MigrationInfo::getVersion)
                                .filter(java.util.Objects::nonNull)
                                .max(org.flywaydb.core.api.MigrationVersion::compareTo)
                                .orElseThrow());
        assertThat(
                        jdbc.queryForObject(
                                """
                                SELECT COUNT(*) FROM information_schema.tables
                                 WHERE table_schema = current_schema()
                                   AND table_name IN ('lesson_transfer_receipts',
                                                      'homework_binding_transfer_markers',
                                                      'homework_binding_transfer_history')
                                """,
                                Integer.class))
                .isEqualTo(3);
    }

    @Test
    void checksumConsistencyAfterFullMigrate() {
        OwnedAuthPrincipals.cleanCommands(jdbc);
        MigrationTestUtils.clean(dataSource);
        Flyway flyway =
                Flyway.configure()
                        .dataSource(dataSource)
                        .locations("classpath:db/migration")
                        .cleanDisabled(false)
                        .load();
        flyway.migrate();
        flyway.validate();

        MigrateResult second = flyway.migrate();
        assertThat(second.migrationsExecuted).isZero();
    }

    @Test
    void dataPreservationAcrossMigrations() {
        OwnedAuthPrincipals.cleanCommands(jdbc);
        MigrationTestUtils.clean(dataSource);

        // Step 1: V1 baseline.
        MigrationTestUtils.runMigrationsUpTo(dataSource, "1");

        // Step 2: INSERT group + user.
        // V1 schema: groups(id, name, code, is_active, created_at),
        //            users(id, login, password_hash, last_name, first_name,
        //                  ..., role, status, group_id, ...).
        Long groupId =
                jdbc.queryForObject(
                        "INSERT INTO groups (name, code, is_active) "
                                + "VALUES ('Test Group M08', 'TG-M08', TRUE) RETURNING id",
                        Long.class);
        assertThat(groupId).isNotNull();

        Long userId =
                jdbc.queryForObject(
                        "INSERT INTO users (login, password_hash, last_name, first_name, role,"
                            + " group_id) VALUES ('test_m08_user', '$2a$10$XXXXXXXXXXXXXXXXXXXXXX',"
                            + " 'Testov', 'Test', 'student'::user_role, ?) RETURNING id",
                        Long.class,
                        groupId);
        assertThat(userId).isNotNull();

        // Step 3: apply V2..V17.
        MigrationTestUtils.runAllMigrations(dataSource);

        // Step 4: assert rows все ещё на месте.
        Integer groupCount =
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM groups WHERE id = ?", Integer.class, groupId);
        assertThat(groupCount)
                .as("V1 group должна сохраниться после V2..V17 миграций")
                .isEqualTo(1);

        Integer userCount =
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM users WHERE id = ?", Integer.class, userId);
        assertThat(userCount).as("V1 user должен сохраниться после V2..V17 миграций").isEqualTo(1);
    }

    @Test
    void authOwnershipCutoverUsesRealPrincipalsAndPreservesOneCommitAcrossUpgradeAndRestart() {
        OwnedAuthPrincipals.cleanCommands(jdbc);
        MigrationTestUtils.clean(dataSource);
        OwnedAuthPrincipals.prepare(POSTGRES);
        var migrator = OwnedAuthPrincipals.source(POSTGRES, "rct_migrator");
        Flyway.configure()
                .dataSource(migrator)
                .locations("classpath:db/migration")
                .target("61")
                .load()
                .migrate();
        long legacy =
                jdbc.queryForObject(
                        "INSERT INTO users(login,last_name,first_name,role) VALUES"
                            + " ('ownership_legacy','Synthetic','Legacy','teacher') RETURNING id",
                        Long.class);
        long actor = jdbc.queryForObject("SELECT id FROM users WHERE login='admin'", Long.class);
        long grant =
                jdbc.queryForObject(
                        "SELECT id FROM user_role_grants WHERE user_id=? AND role='admin'",
                        Long.class,
                        actor);
        java.util.UUID sid = java.util.UUID.randomUUID();
        insertSession(actor, grant, sid);
        // Equivalent old callback access exists before the additive ownership cutover.
        jdbc.execute(
                "GRANT SELECT,INSERT,UPDATE,DELETE ON users,auth_sessions,user_role_grants TO"
                        + " rct_runtime");
        var old = new JdbcTemplate(OwnedAuthPrincipals.source(POSTGRES, "rct_runtime"));
        assertThat(old.queryForObject("SELECT count(*) FROM users WHERE id=?", Long.class, legacy))
                .isOne();
        var full =
                Flyway.configure().dataSource(migrator).locations("classpath:db/migration").load();
        assertThat(full.migrate().migrationsExecuted).isOne();
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM users WHERE id=? AND"
                                        + " login='ownership_legacy'",
                                Long.class,
                                legacy))
                .isOne();
        var academicSource = OwnedAuthPrincipals.source(POSTGRES, "rct_academic_runtime");
        var academic = new JdbcTemplate(academicSource);
        var authSource = OwnedAuthPrincipals.source(POSTGRES, "rct_auth_runtime");
        var auth = new JdbcTemplate(authSource);
        assertThat(academic.queryForObject("SELECT current_user", String.class))
                .isEqualTo("rct_academic_runtime");
        assertThat(auth.queryForObject("SELECT current_user", String.class))
                .isEqualTo("rct_auth_runtime");
        assertThat(
                        jdbc.queryForObject(
                                "SELECT bool_and(NOT rolsuper AND NOT rolcreatedb AND NOT"
                                    + " rolcreaterole AND NOT rolbypassrls) FROM pg_roles WHERE"
                                    + " rolname IN"
                                    + " ('rct_auth_runtime','rct_academic_runtime','rct_migrator','rct_auth_command_owner')",
                                Boolean.class))
                .isTrue();
        assertDenied(
                () -> old.queryForObject("SELECT id FROM users WHERE id=?", Long.class, legacy));
        assertDenied(
                () ->
                        academic.queryForObject(
                                "SELECT password_hash FROM users WHERE id=?",
                                String.class,
                                legacy));
        assertDenied(
                () ->
                        academic.queryForObject(
                                "SELECT initial_password FROM users WHERE id=?",
                                String.class,
                                legacy));
        assertDenied(() -> academic.queryForList("SELECT sid FROM auth_sessions"));
        assertDenied(
                () ->
                        academic.update(
                                "UPDATE user_role_grants SET status='archived' WHERE user_id=?",
                                legacy));
        assertDenied(
                () ->
                        academic.queryForObject(
                                "SELECT auth_api.put_grant(?,'teacher','active',NULL)",
                                Object.class,
                                legacy));
        assertDenied(() -> auth.update("UPDATE users SET last_name='foreign' WHERE id=?", legacy));
        assertDenied(() -> auth.update("UPDATE groups SET name='foreign'"));
        assertDenied(() -> academic.execute("CREATE TABLE public.foreign_ddl(id bigint)"));
        assertDenied(() -> auth.execute("CREATE TABLE public.foreign_ddl(id bigint)"));

        var claims =
                new ru.rutcampustrack.shared.security.InternalJwtClaims(
                        actor,
                        sid,
                        1,
                        academic.queryForObject(
                                "SELECT roles_version FROM users WHERE id=?", Long.class, actor),
                        "ADMIN",
                        "ACTIVE",
                        null,
                        false,
                        false);
        var commands = new ru.rutcampustrack.shared.security.authdb.AuthDataCommands(academic);
        var tx =
                new org.springframework.transaction.support.TransactionTemplate(
                        new org.springframework.jdbc.datasource.DataSourceTransactionManager(
                                academicSource));
        long group =
                academic.queryForObject(
                        "INSERT INTO groups(name) VALUES ('Ownership group') RETURNING id",
                        Long.class);
        long user =
                tx.execute(
                        status -> {
                            long id =
                                    academic.queryForObject(
                                            "INSERT INTO users(last_name,first_name,group_id)"
                                                + " VALUES ('Synthetic','Created',?) RETURNING id",
                                            Long.class,
                                            group);
                            commands.initialize(
                                    claims,
                                    id,
                                    "ownership_created",
                                    "synthetic-prepared-hash",
                                    "synthetic-initial",
                                    "student",
                                    "active",
                                    9_000_014L,
                                    null);
                            commands.grant(claims, id, "student", "active", group);
                            academic.update(
                                    "INSERT INTO"
                                        + " student_group_history(user_id,group_id,joined_at,reason)"
                                        + " VALUES (?,?,DATE '2026-09-01','initial-enrollment')",
                                    id,
                                    group);
                            return id;
                        });
        assertThat(
                        academic.queryForObject(
                                "SELECT credentials_version=1 AND telegram_binding_version=1 AND"
                                    + " generated_password_origin='initial' FROM users WHERE id=?",
                                Boolean.class,
                                user))
                .isTrue();
        assertThat(
                        academic.queryForObject(
                                "SELECT count(*) FROM student_group_history WHERE user_id=?",
                                Long.class,
                                user))
                .isOne();
        assertThat(commands.initialPassword(claims, user).equals("synthetic-initial")).isTrue();
        assertDenied(
                () -> academic.update("UPDATE users SET password_hash='foreign' WHERE id=?", user));
        assertThatThrownBy(
                        () ->
                                tx.executeWithoutResult(
                                        status -> {
                                            long id =
                                                    academic.queryForObject(
                                                            "INSERT INTO"
                                                                + " users(last_name,first_name)"
                                                                + " VALUES ('Synthetic','Rollback')"
                                                                + " RETURNING id",
                                                            Long.class);
                                            commands.initialize(
                                                    claims,
                                                    id,
                                                    "ownership_rollback",
                                                    "synthetic-hash",
                                                    "synthetic-initial",
                                                    "teacher",
                                                    "active",
                                                    null,
                                                    null);
                                            commands.grant(claims, id, "teacher", "active", null);
                                            throw new IllegalStateException("synthetic rollback");
                                        }))
                .isInstanceOf(IllegalStateException.class);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM users WHERE login='ownership_rollback'",
                                Long.class))
                .isZero();
        assertThatThrownBy(
                        () ->
                                tx.executeWithoutResult(
                                        status ->
                                                academic.update(
                                                        "INSERT INTO users(last_name,first_name)"
                                                                + " VALUES"
                                                                + " ('Synthetic','Incomplete')")))
                .isInstanceOf(RuntimeException.class);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM users WHERE first_name='Incomplete'",
                                Long.class))
                .isZero();

        long card =
                tx.execute(
                        status -> {
                            academic.queryForObject(
                                    "SELECT auth_api.register_bot_message(?,?,?,?,?)",
                                    Integer.class,
                                    user,
                                    9_000_014L,
                                    71L,
                                    1L,
                                    1L);
                            return academic.queryForObject(
                                    "SELECT record_id FROM bot_credential_messages WHERE user_id=?",
                                    Long.class,
                                    user);
                        });
        long studentGrant =
                academic.queryForObject(
                        "SELECT id FROM user_role_grants WHERE user_id=? AND role='student'",
                        Long.class,
                        user);
        java.util.UUID studentSid = java.util.UUID.randomUUID();
        insertSession(user, studentGrant, studentSid);
        var authTx =
                new org.springframework.transaction.support.TransactionTemplate(
                        new org.springframework.jdbc.datasource.DataSourceTransactionManager(
                                authSource));
        long beforeOutbox = auth.queryForObject("SELECT count(*) FROM auth_outbox", Long.class);
        assertThatThrownBy(
                        () ->
                                authTx.executeWithoutResult(
                                        status -> {
                                            resetCredentialTuple(auth, user, studentSid);
                                            throw new IllegalStateException(
                                                    "synthetic reset rollback");
                                        }))
                .isInstanceOf(IllegalStateException.class);
        assertThat(
                        academic.queryForObject(
                                "SELECT credentials_version FROM users WHERE id=?",
                                Long.class,
                                user))
                .isEqualTo(1);
        assertThat(
                        auth.queryForObject(
                                "SELECT revoked_at IS NULL FROM auth_sessions WHERE sid=?",
                                Boolean.class,
                                studentSid))
                .isTrue();
        assertThat(
                        academic.queryForObject(
                                "SELECT cleanup_state FROM bot_credential_messages WHERE"
                                        + " record_id=?",
                                String.class,
                                card))
                .isEqualTo("NOT_REQUIRED");
        assertThat(auth.queryForObject("SELECT count(*) FROM auth_outbox", Long.class))
                .isEqualTo(beforeOutbox);
        authTx.executeWithoutResult(status -> resetCredentialTuple(auth, user, studentSid));
        assertThat(
                        academic.queryForObject(
                                "SELECT credentials_version=2 AND password_changed AND"
                                    + " generated_password_origin IS NULL FROM users WHERE id=?",
                                Boolean.class,
                                user))
                .isTrue();
        assertThat(
                        auth.queryForObject(
                                "SELECT revoked_at IS NOT NULL FROM auth_sessions WHERE sid=?",
                                Boolean.class,
                                studentSid))
                .isTrue();
        assertThat(
                        academic.queryForObject(
                                "SELECT cleanup_state='PENDING' AND record_revision=2 AND"
                                        + " invalidated_by_credentials_version=2 FROM"
                                        + " bot_credential_messages WHERE record_id=?",
                                Boolean.class,
                                card))
                .isTrue();
        assertThat(auth.queryForObject("SELECT count(*) FROM auth_outbox", Long.class))
                .isEqualTo(beforeOutbox + 1);
        // Persistent owner guards apply even when the caller has genuine current FULL claims.
        long beforeAdminVersion = claims.rolesVersion();
        assertThatThrownBy(
                        () ->
                                commands.synchronize(
                                        claims,
                                        actor,
                                        "admin",
                                        "suspended",
                                        null,
                                        null,
                                        false,
                                        false))
                .isInstanceOfSatisfying(
                        ru.rutcampustrack.shared.security.admin.AdminAccessException.class,
                        denied -> assertThat(denied.getStatusCode().value()).isEqualTo(403));
        assertThatThrownBy(() -> commands.project(claims, actor, "admin", "expelled", null, null))
                .isInstanceOf(ru.rutcampustrack.shared.security.admin.AdminAccessException.class);
        assertThatThrownBy(() -> commands.grant(claims, actor, "admin", "archived", null))
                .isInstanceOf(ru.rutcampustrack.shared.security.admin.AdminAccessException.class);
        assertThat(
                        academic.queryForObject(
                                "SELECT roles_version=? AND status='active'::account_status FROM"
                                        + " users WHERE id=?",
                                Boolean.class,
                                beforeAdminVersion,
                                actor))
                .isTrue();
        assertThat(
                        academic.queryForObject(
                                "SELECT count(*) FROM admin_access_profiles p JOIN user_role_grants"
                                    + " g ON g.user_id=p.user_id JOIN users u ON u.id=p.user_id"
                                    + " WHERE p.assigned AND p.mode='FULL' AND g.role='admin' AND"
                                    + " g.status='active' AND u.status<>'archived'",
                                Long.class))
                .isPositive();
        // Future auth_api functions are private by default under both actual creating roles.
        var migrationJdbc = new JdbcTemplate(migrator);
        migrationJdbc.execute(
                "CREATE FUNCTION auth_api.u14_migrator_private_probe() RETURNS integer LANGUAGE sql"
                        + " AS 'SELECT 1'");
        assertDenied(
                () ->
                        academic.queryForObject(
                                "SELECT auth_api.u14_migrator_private_probe()", Integer.class));
        try (var connection = migrator.getConnection();
                var statement = connection.createStatement()) {
            statement.execute("SET ROLE rct_auth_command_owner");
            statement.execute(
                    "CREATE FUNCTION auth_api.u14_owner_private_probe() RETURNS integer LANGUAGE"
                            + " sql AS 'SELECT 1'");
            statement.execute("RESET ROLE");
        } catch (java.sql.SQLException unavailable) {
            throw new IllegalStateException("Owned command-owner default privilege probe failed");
        }
        assertDenied(
                () ->
                        academic.queryForObject(
                                "SELECT auth_api.u14_owner_private_probe()", Integer.class));
        assertDenied(
                () ->
                        auth.queryForObject(
                                "SELECT auth_api.u14_owner_private_probe()", Integer.class));
        migrationJdbc.execute("DROP FUNCTION auth_api.u14_migrator_private_probe()");
        migrationJdbc.execute("DROP FUNCTION auth_api.u14_owner_private_probe()");
        // A full administrator may also be the old HEADMAN. Its own version changes inside this one
        // command.
        commands.grant(claims, actor, "student", "active", group);
        var headmanActor =
                new ru.rutcampustrack.shared.security.InternalJwtClaims(
                        actor,
                        sid,
                        1,
                        academic.queryForObject(
                                "SELECT roles_version FROM users WHERE id=?", Long.class, actor),
                        "ADMIN",
                        "ACTIVE",
                        null,
                        false,
                        false);
        commands.replaceHeadman(headmanActor, null, actor, group, false, null, null);
        var replacingActor =
                new ru.rutcampustrack.shared.security.InternalJwtClaims(
                        actor,
                        sid,
                        1,
                        academic.queryForObject(
                                "SELECT roles_version FROM users WHERE id=?", Long.class, actor),
                        "ADMIN",
                        "ACTIVE",
                        null,
                        false,
                        false);
        commands.replaceHeadman(replacingActor, actor, user, group, false, null, null);
        assertThat(
                        academic.queryForObject(
                                "SELECT user_id FROM user_role_grants WHERE role='headman' AND"
                                        + " status='active' AND group_id=?",
                                Long.class,
                                group))
                .isEqualTo(user);
        assertThat(
                        auth.queryForObject(
                                "SELECT active_role_grant_id=? AND revoked_at IS NULL FROM"
                                        + " auth_sessions WHERE sid=?",
                                Boolean.class,
                                grant,
                                sid))
                .isTrue();
        assertThatThrownBy(() -> commands.initialPassword(replacingActor, user))
                .isInstanceOf(ru.rutcampustrack.shared.security.admin.AdminAccessException.class);
        // A new Flyway instance models repeat startup: no DDL/grants/data replay, no credential
        // regeneration.
        var restarted =
                Flyway.configure().dataSource(migrator).locations("classpath:db/migration").load();
        restarted.validate();
        assertThat(restarted.migrate().migrationsExecuted).isZero();
        assertDenied(() -> academic.queryForList("SELECT * FROM auth_sessions"));
        assertThat(
                        academic.queryForObject(
                                "SELECT credentials_version FROM users WHERE id=?",
                                Long.class,
                                user))
                .isEqualTo(2);
    }

    private void insertSession(long user, long grant, java.util.UUID sid) {
        jdbc.update(
                "INSERT INTO"
                    + " auth_sessions(sid,user_id,active_role_grant_id,session_version,current_refresh_jti,refresh_expires_at,created_at,last_seen_at,auth_method)"
                    + " VALUES (?,?,?,1,?,NOW()+INTERVAL '1 day',NOW(),NOW(),'PASSWORD')",
                sid,
                user,
                grant,
                java.util.UUID.randomUUID());
    }

    private static void resetCredentialTuple(JdbcTemplate auth, long user, java.util.UUID sid) {
        auth.update(
                "UPDATE users SET"
                    + " password_hash='synthetic-replacement-hash',initial_password=NULL,password_changed=true"
                    + " WHERE id=?",
                user);
        auth.update(
                "UPDATE auth_sessions SET"
                    + " revoked_at=NOW(),revoke_reason='PASSWORD_CHANGED',session_version=session_version+1"
                    + " WHERE sid=? AND user_id=?",
                sid,
                user);
        auth.update(
                "INSERT INTO account_security_events(user_id,sid,event_type,occurred_at) VALUES"
                        + " (?,?,'PASSWORD_CHANGED',NOW())",
                user,
                sid);
        auth.update(
                "INSERT INTO auth_outbox(event_type,payload) VALUES"
                        + " ('password.changed','{}'::jsonb)");
    }

    private static void assertDenied(
            org.assertj.core.api.ThrowableAssert.ThrowingCallable operation) {
        assertThatThrownBy(operation)
                .isInstanceOf(org.springframework.dao.DataAccessException.class)
                .satisfies(
                        failure ->
                                assertThat(
                                                ((java.sql.SQLException)
                                                                org.springframework.core
                                                                        .NestedExceptionUtils
                                                                        .getMostSpecificCause(
                                                                                failure))
                                                        .getSQLState())
                                        .isEqualTo("42501"));
    }
}
