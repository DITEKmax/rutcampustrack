package ru.rutcampustrack.auth.support;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Synthetic fixtures for the Auth-only migration chain; no Academic tables or bridge. */
public final class AuthDatabaseFixture {
    public static final String PASSWORD_HASH = "$2a$10$" + "a".repeat(53);
    public static final String REPLACEMENT_HASH = "$2a$10$" + "b".repeat(53);
    public static final String RUNTIME_PASSWORD = "synthetic-auth-runtime-only";

    private AuthDatabaseFixture() {}

    public static Path migrations() {
        Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        while (current != null) {
            Path candidate =
                    current.resolve(
                            "services/auth-service/auth-app/src/main/resources/db/auth-migration");
            if (Files.isRegularFile(candidate.resolve("V1__auth_identity_authority.sql")))
                return candidate;
            current = current.getParent();
        }
        throw new IllegalStateException("Auth migration directory unavailable");
    }

    public static void runtimePrincipal(JdbcTemplate jdbc) {
        jdbc.execute("CREATE ROLE rct_auth_runtime LOGIN PASSWORD '" + RUNTIME_PASSWORD + "'");
        jdbc.execute("GRANT USAGE ON SCHEMA public TO rct_auth_runtime");
        jdbc.execute(
                "GRANT SELECT, INSERT, UPDATE ON ALL TABLES IN SCHEMA public TO rct_auth_runtime");
        jdbc.execute("GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO rct_auth_runtime");
        // Unlike production bootstrap, this fixture creates the runtime role after V11.
        // Reproduce V11's exact grant for the catalog trigger's invoker-rights helper.
        jdbc.execute(
                "GRANT EXECUTE ON FUNCTION public.append_auth_identity_catalog_snapshot(bigint)"
                        + " TO rct_auth_runtime");
        jdbc.execute(
                "GRANT DELETE ON"
                        + " auth_outbox,password_reset_tokens,qr_login_challenges,qr_login_issuers,auth_device_preferences TO"
                        + " rct_auth_runtime");
    }

    public static long identity(JdbcTemplate jdbc, String login, Instant time) {
        return identity(jdbc, login, time, PASSWORD_HASH, "initial");
    }

    public static long identity(
            JdbcTemplate jdbc,
            String login,
            Instant time,
            String passwordHash,
            String initialPassword) {
        Supplier<Long> seed =
                () -> {
                    UUID operation = operation(jdbc, "CREATE");
                    long id =
                            jdbc.queryForObject(
                                    """
INSERT INTO auth_identities(login,password_hash,initial_password,account_state,
    provision_operation_id,created_at,updated_at)
VALUES (?, ?, ?, 'ACTIVE', ?, ?, ?) RETURNING id
""",
                                    Long.class,
                                    login,
                                    passwordHash,
                                    initialPassword,
                                    operation,
                                    Timestamp.from(time),
                                    Timestamp.from(time));
                    target(jdbc, operation, id, 0);
                    commit(jdbc, operation, id);
                    return id;
                };
        // Boot fixtures already use the JPA manager's JDBC connection. Reuse that
        // transaction rather than starting another manager on its bound connection.
        if (TransactionSynchronizationManager.isActualTransactionActive()) return seed.get();
        return new TransactionTemplate(new DataSourceTransactionManager(jdbc.getDataSource()))
                .execute(status -> seed.get());
    }

    /** Reserves the next authority version under the same identity-first lock as production. */
    public static UUID fence(JdbcTemplate jdbc, long id) {
        long version =
                jdbc.queryForObject(
                        "SELECT roles_version FROM auth_identities WHERE id=? FOR UPDATE",
                        Long.class,
                        id);
        UUID operation = operation(jdbc, "ROLE");
        target(jdbc, operation, id, version);
        jdbc.update(
                "UPDATE auth_identities SET fence_operation_id=?,roles_version=roles_version+1"
                        + " WHERE id=?",
                operation,
                id);
        return operation;
    }

    public static void commit(JdbcTemplate jdbc, UUID operation, long id) {
        jdbc.update(
                "UPDATE auth_identity_operation_targets SET committed_academic_revision=1 WHERE"
                        + " operation_id=? AND user_id=?",
                operation,
                id);
        jdbc.update(
                """
                UPDATE auth_identity_operations SET phase='COMMITTED',revision=revision+1,
                    finished_at=NOW(),committed_catalog_version=1 WHERE operation_id=?
                """,
                operation);
    }

    public static void unfence(JdbcTemplate jdbc, UUID operation, long id) {
        commit(jdbc, operation, id);
        jdbc.update("UPDATE auth_identities SET fence_operation_id=NULL WHERE id=?", id);
    }

    private static UUID operation(JdbcTemplate jdbc, String kind) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                """
INSERT INTO auth_identity_operations(operation_id,command_hash,canonical_command,kind,authorization_kind,
    actor_id,actor_session_id,actor_session_version,actor_roles_version,
    actor_credentials_version,actor_binding_version,actor_commit_roles_version,actor_admin_profile_version)
VALUES (?,decode(repeat('00',32),'hex'),decode('00','hex'),?,?,1,?,1,1,1,1,1,1)
""",
                id,
                kind,
                kind,
                UUID.randomUUID());
        return id;
    }

    private static void target(JdbcTemplate jdbc, UUID operation, long id, long version) {
        jdbc.update(
                """
INSERT INTO auth_identity_operation_targets(operation_id,user_id,expected_roles_version,
    final_roles_version,expected_credentials_version,prepared_credentials_version,
    expected_binding_version,prepared_binding_version,final_binding_version,
    expected_academic_revision,desired_grants,desired_admin_profile,desired_account_state)
SELECT ?,id,?,?,credentials_version,credentials_version,telegram_binding_version,
    telegram_binding_version,telegram_binding_version,0,'[]'::jsonb,'{}'::jsonb,'ACTIVE'
FROM auth_identities WHERE id=?
""",
                operation,
                version,
                version + 1,
                id);
    }
}
