package ru.rutcampustrack.auth.bootstrap;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

import ru.rutcampustrack.auth.authority.AuthAdminJdbcAuthority;
import ru.rutcampustrack.auth.authority.AuthIdentityAuthorityQuery;
import ru.rutcampustrack.auth.config.SecurityConfig;
import ru.rutcampustrack.auth.service.BcryptConcurrencyGuard;
import ru.rutcampustrack.auth.session.PasswordPolicy;
import ru.rutcampustrack.authauthority.grpc.IdentityCatalogChangesRequest;

import java.security.MessageDigest;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

import javax.sql.DataSource;

/** Offline operator command. Never a Spring component, HTTP handler or startup initializer. */
public final class InitialAdministratorAuthCommand {
    private static final ObjectMapper JSON =
            new ObjectMapper()
                    .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
                    .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final org.springframework.security.crypto.password.PasswordEncoder passwords =
            SecurityConfig.passwordEncoder();
    private final BcryptConcurrencyGuard bcrypt = new BcryptConcurrencyGuard(1, 0);

    public InitialAdministratorAuthCommand(DataSource data) {
        jdbc = new JdbcTemplate(data);
        tx = new TransactionTemplate(new DataSourceTransactionManager(data));
        tx.setTimeout(15);
    }

    public static void main(String[] args) {
        // A plain main deliberately has no Boot LoggingSystem. Keep all library diagnostics
        // off the private protocol channel, including SQL/configuration exception details.
        var protocol = System.out;
        var diagnostic = System.err;
        boolean diagnostics = "1".equals(System.getenv("RCT_INITIAL_ADMIN_DIAGNOSTICS"));
        String phase = "INPUT";
        try {
            System.setOut(new java.io.PrintStream(java.io.OutputStream.nullOutputStream()));
            System.setErr(new java.io.PrintStream(java.io.OutputStream.nullOutputStream()));
            if (args.length != 1) throw refused();
            byte[] bytes = System.in.readNBytes(1_048_577);
            if (bytes.length > 1_048_576) throw refused();
            JsonNode request = JSON.readTree(bytes);
            // Only ConfigData binding: no application context, auto-configuration or listeners.
            phase = "CONFIG";
            var environment = new StandardEnvironment();
            ConfigDataEnvironmentPostProcessor.applyTo(environment);
            var data =
                    new DriverManagerDataSource(
                            environment.getRequiredProperty("spring.datasource.url"),
                            environment.getRequiredProperty("spring.flyway.user"),
                            environment.getRequiredProperty("spring.flyway.password"));
            phase = "EXECUTE";
            var reply = new InitialAdministratorAuthCommand(data).execute(args[0], request);
            phase = "OUTPUT";
            protocol.println(JSON.writeValueAsString(reply));
        } catch (Exception failure) {
            protocol.println("{\"status\":\"REFUSED\"}");
            if (diagnostics)
                diagnostic.println(
                        "BOOTSTRAP_OWNER_FAILURE_V1 owner=AUTH phase="
                                + phase
                                + " category="
                                + failureCategory(phase, failure));
            System.exit(2);
        }
    }

    /**
     * Closed diagnostic categories only; SQL state and exception material never leave this main.
     */
    private static String failureCategory(String phase, Exception failure) {
        if ("INPUT".equals(phase)) return "INPUT";
        if ("CONFIG".equals(phase)) return "CONFIG";
        Throwable cause = failure;
        for (int depth = 0; cause != null && depth < 8; depth++, cause = cause.getCause()) {
            if (cause instanceof java.sql.SQLException sql) {
                String state = sql.getSQLState();
                if (state != null) {
                    if (state.startsWith("28") || state.equals("42501")) return "DB_PERMISSION";
                    if (state.startsWith("08")) return "DB_CONNECT";
                    if (state.startsWith("42")) return "DB_SCHEMA";
                    if (state.startsWith("23")) return "DB_CONSTRAINT";
                }
                return "OTHER";
            }
        }
        if (failure instanceof org.springframework.jdbc.CannotGetJdbcConnectionException)
            return "DB_CONNECT";
        if (failure instanceof IllegalArgumentException) return "GUARD";
        return "OTHER";
    }

    public ObjectNode execute(String stage, JsonNode input) {
        requireOperator();
        if (input == null || !input.isObject()) throw refused();
        return switch (stage) {
            case "prepare" -> prepare(input);
            case "activate" -> activate(input);
            case "catalog" -> catalog(input);
            default -> throw refused();
        };
    }

    private ObjectNode prepare(JsonNode input) {
        fields(input, Set.of("version", "operationId", "login", "profileDigest", "password"));
        var plan = plan(input);
        var previous = find(plan.id());
        if (previous != null) {
            same(previous, plan);
            if (previous.committed()) return reply(previous);
            verifyPassword(previous, text(input, "password"));
            return reply(find(plan.id()));
        }
        String password = text(input, "password");
        if (!new PasswordPolicy().isValid(password)) throw refused();
        String encoded = bcrypt.execute(() -> passwords.encode(password));
        tx.execute(
                status -> {
                    lock();
                    var raced = find(plan.id());
                    if (raced != null) {
                        same(raced, plan);
                        return raced;
                    }
                    if (Boolean.TRUE.equals(
                            jdbc.queryForObject(
                                    "SELECT EXISTS(SELECT 1 FROM auth_initial_admin_bootstrap) OR"
                                        + " EXISTS(SELECT 1 FROM auth_identity_operations WHERE"
                                        + " operation_id=?) OR EXISTS(SELECT 1 FROM"
                                        + " auth_identity_operation_rejections WHERE"
                                        + " operation_id=?) OR EXISTS(SELECT 1 FROM"
                                        + " auth_admin_access_noop_receipts WHERE operation_id=?)",
                                    Boolean.class,
                                    plan.id(),
                                    plan.id(),
                                    plan.id()))) throw refused();
                    noAdministrator();
                    jdbc.update(
                            """
INSERT INTO auth_identity_operations(operation_id,command_hash,canonical_command,
    kind,authorization_kind,initial_admin_bootstrap)
VALUES(?,?,?,'CREATE','CREATE',TRUE)
""",
                            plan.id(),
                            plan.hash(),
                            canonical(plan.id(), plan.login(), plan.profile()));
                    long uid =
                            jdbc.queryForObject(
                                    """
INSERT INTO auth_identities(login,password_hash,initial_password,
    provision_operation_id,fence_operation_id)
VALUES(?,?,?,?,?) RETURNING id
""",
                                    Long.class,
                                    plan.login(),
                                    encoded,
                                    password,
                                    plan.id(),
                                    plan.id());
                    jdbc.update(
                            """
INSERT INTO auth_identity_operation_targets(operation_id,user_id,
    expected_roles_version,final_roles_version,expected_credentials_version,
    prepared_credentials_version,expected_binding_version,prepared_binding_version,
    final_binding_version,expected_academic_revision,desired_grants,
    desired_admin_profile,desired_account_state)
VALUES(?,?,0,1,0,1,0,1,1,0,
    '[{"role":"admin","status":"active","group_id":null}]'::jsonb,
    '{"assigned":true,"mode":"FULL","permissions":[]}'::jsonb,'ACTIVE')
""",
                            plan.id(),
                            uid);
                    jdbc.update(
                            """
INSERT INTO auth_initial_admin_bootstrap(operation_id,user_id,command_hash,
    profile_digest,login) VALUES(?,?,?,?,?)
""",
                            plan.id(),
                            uid,
                            plan.hash(),
                            plan.profile(),
                            plan.login());
                    return find(plan.id());
                });
        // A concurrent same-key prepare may have won with a different secret. Never verify BCrypt
        // while holding the authority lock, and never verify an old password after completion.
        var current = find(plan.id());
        if (!current.committed()) verifyPassword(current, password);
        return reply(current);
    }

    private ObjectNode activate(JsonNode input) {
        fields(
                input,
                Set.of(
                        "version",
                        "operationId",
                        "login",
                        "profileDigest",
                        "commandHash",
                        "userId",
                        "rolesVersion",
                        "academicReceipt"));
        var plan = plan(input);
        return tx.execute(
                status -> {
                    lock();
                    var reservation = find(plan.id());
                    if (reservation == null) throw refused();
                    same(reservation, plan);
                    binding(input, reservation);
                    if (reservation.committed()) return reply(reservation);
                    JsonNode receipt = input.get("academicReceipt");
                    if (receipt == null) throw refused();
                    fields(
                            receipt,
                            Set.of(
                                    "version",
                                    "operationId",
                                    "commandHash",
                                    "profileDigest",
                                    "userId",
                                    "rolesVersion",
                                    "academicRevision",
                                    "receiptHash"));
                    binding(receipt, reservation);
                    if (number(receipt, "academicRevision") != 1
                            || !MessageDigest.isEqual(
                                    digest(receipt, "receiptHash"), receiptHash(reservation)))
                        throw refused();
                    noAdministrator();
                    boolean pending =
                            Boolean.TRUE.equals(
                                    jdbc.queryForObject(
                                            """
SELECT account_state='PENDING' AND fence_operation_id=? AND roles_version=1
    AND credentials_version=1 AND telegram_binding_version=1
FROM auth_identities WHERE id=? FOR UPDATE
""",
                                            Boolean.class,
                                            plan.id(),
                                            reservation.uid()));
                    if (!pending) throw refused();
                    long clock =
                            jdbc.queryForObject(
                                    "SELECT version FROM auth_identity_catalog_clock WHERE"
                                            + " singleton=1 FOR UPDATE",
                                    Long.class);
                    jdbc.update(
                            """
                            UPDATE auth_identity_operation_targets SET committed_academic_revision=1
                            WHERE operation_id=? AND user_id=?
                            """,
                            plan.id(),
                            reservation.uid());
                    jdbc.update(
                            "INSERT INTO user_role_grants(user_id,role,status)"
                                    + " VALUES(?,'admin','active')",
                            reservation.uid());
                    jdbc.update(
                            """
INSERT INTO admin_access_profiles(user_id,assigned,mode,permissions,version)
VALUES(?,TRUE,'FULL','{}',1)
""",
                            reservation.uid());
                    long committed = Math.addExact(clock, 1);
                    jdbc.update(
                            """
UPDATE auth_identity_operations SET phase='COMMITTED',revision=revision+1,
    committed_catalog_version=?,updated_at=NOW(),finished_at=NOW()
WHERE operation_id=? AND initial_admin_bootstrap AND phase='PREPARED_FENCED'
""",
                            committed,
                            plan.id());
                    if (jdbc.update(
                                    """
UPDATE auth_identities SET account_state='ACTIVE',fence_operation_id=NULL,
    updated_at=NOW() WHERE id=? AND fence_operation_id=?
""",
                                    reservation.uid(),
                                    plan.id())
                            != 1) throw refused();
                    if (jdbc.queryForObject(
                                    "SELECT version FROM auth_identity_catalog_clock WHERE"
                                            + " singleton=1",
                                    Long.class)
                            != committed) throw refused();
                    return reply(find(plan.id()));
                });
    }

    private ObjectNode catalog(JsonNode input) {
        fields(
                input,
                Set.of(
                        "version",
                        "operationId",
                        "login",
                        "profileDigest",
                        "commandHash",
                        "userId",
                        "rolesVersion",
                        "afterCatalogVersion"));
        var reservation = find(plan(input).id());
        if (reservation == null || !reservation.committed()) throw refused();
        same(reservation, plan(input));
        binding(input, reservation);
        long after = number(input, "afterCatalogVersion");
        if (after < 0) throw refused();
        var query = new AuthIdentityAuthorityQuery(jdbc, JSON);
        var read = new TransactionTemplate(tx.getTransactionManager());
        read.setReadOnly(true);
        read.setIsolationLevel(
                org.springframework.transaction.TransactionDefinition.ISOLATION_REPEATABLE_READ);
        var changes =
                read.execute(
                        status ->
                                query.changes(
                                        IdentityCatalogChangesRequest.newBuilder()
                                                .setAfterCatalogVersion(after)
                                                .setLimit(200)
                                                .build()));
        var result = reply(reservation);
        result.put("catalogPayload", Base64.getEncoder().encodeToString(changes.toByteArray()));
        return result;
    }

    private void verifyPassword(Reservation reservation, String password) {
        String encoded =
                jdbc.queryForObject(
                        "SELECT password_hash FROM auth_identities WHERE id=?",
                        String.class,
                        reservation.uid());
        if (!bcrypt.execute(() -> passwords.matches(password, encoded))
                && !find(reservation.id()).committed()) throw refused();
    }

    private Reservation find(UUID id) {
        var rows =
                jdbc.query(
                        """
                        SELECT b.user_id,b.login,b.command_hash,b.profile_digest,o.phase,
                            o.committed_catalog_version FROM auth_initial_admin_bootstrap b
                        JOIN auth_identity_operations o USING(operation_id) WHERE b.operation_id=?
                        """,
                        (rs, n) ->
                                new Reservation(
                                        id,
                                        rs.getLong(1),
                                        rs.getString(2),
                                        rs.getBytes(3),
                                        rs.getBytes(4),
                                        rs.getString(5),
                                        rs.getLong(6)),
                        id);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    private void noAdministrator() {
        if (Boolean.TRUE.equals(
                jdbc.queryForObject(
                        """
                        SELECT EXISTS(SELECT 1 FROM user_role_grants WHERE role='admin')
                            OR EXISTS(SELECT 1 FROM admin_access_profiles WHERE assigned)
                        """,
                        Boolean.class))) throw refused();
    }

    private void requireOperator() {
        if (!Boolean.TRUE.equals(
                jdbc.queryForObject(
                        """
                        SELECT current_database()='auth_db' AND session_user='rct_auth_migrator'
                            AND current_user=session_user
                        """,
                        Boolean.class))) throw refused();
    }

    private void lock() {
        jdbc.queryForList("SELECT pg_advisory_xact_lock(?)", AuthAdminJdbcAuthority.GLOBAL_LOCK);
    }

    private static Plan plan(JsonNode input) {
        if (number(input, "version") != 1) throw refused();
        UUID id = UUID.fromString(text(input, "operationId"));
        String login = text(input, "login");
        if (!login.matches("[A-Za-z0-9_.-]{3,32}")) throw refused();
        byte[] profile = digest(input, "profileDigest");
        return new Plan(id, login, profile, sha(canonical(id, login, profile)));
    }

    private static byte[] canonical(UUID id, String login, byte[] profile) {
        try {
            return JSON.writeValueAsBytes(
                    new TreeMap<>(
                            Map.of(
                                    "version",
                                    1,
                                    "operationId",
                                    id.toString(),
                                    "login",
                                    login,
                                    "profileDigest",
                                    HexFormat.of().formatHex(profile))));
        } catch (Exception failure) {
            throw refused();
        }
    }

    private static byte[] receiptHash(Reservation r) {
        try {
            return sha(
                    JSON.writeValueAsBytes(
                            new TreeMap<>(
                                    Map.of(
                                            "version",
                                            1,
                                            "operationId",
                                            r.id().toString(),
                                            "commandHash",
                                            hex(r.hash()),
                                            "profileDigest",
                                            hex(r.profile()),
                                            "userId",
                                            r.uid(),
                                            "rolesVersion",
                                            1,
                                            "academicRevision",
                                            1))));
        } catch (Exception failure) {
            throw refused();
        }
    }

    private static void binding(JsonNode input, Reservation r) {
        if (number(input, "version") != 1
                || !text(input, "operationId").equals(r.id().toString())
                || number(input, "userId") != r.uid()
                || number(input, "rolesVersion") != 1
                || !MessageDigest.isEqual(digest(input, "commandHash"), r.hash())
                || !MessageDigest.isEqual(digest(input, "profileDigest"), r.profile()))
            throw refused();
    }

    private static void same(Reservation r, Plan p) {
        if (!MessageDigest.isEqual(r.hash(), p.hash())
                || !r.login().equals(p.login())
                || !MessageDigest.isEqual(r.profile(), p.profile())) throw refused();
    }

    private static ObjectNode reply(Reservation r) {
        if (!r.committed() && !"PREPARED_FENCED".equals(r.phase())) throw refused();
        var out = JSON.createObjectNode();
        out.put("version", 1)
                .put("operationId", r.id().toString())
                .put("userId", r.uid())
                .put("login", r.login())
                .put("commandHash", hex(r.hash()))
                .put("profileDigest", hex(r.profile()))
                .put("rolesVersion", 1)
                .put("status", r.committed() ? "COMMITTED" : "PREPARED")
                .put("committedCatalogVersion", r.catalog());
        return out;
    }

    private static String text(JsonNode node, String key) {
        var value = node.get(key);
        if (value == null || !value.isTextual()) throw refused();
        return value.textValue();
    }

    private static long number(JsonNode node, String key) {
        var value = node.get(key);
        if (value == null || !value.isIntegralNumber() || !value.canConvertToLong())
            throw refused();
        return value.longValue();
    }

    private static byte[] digest(JsonNode node, String key) {
        String value = text(node, key);
        if (!value.matches("[0-9a-f]{64}")) throw refused();
        return HexFormat.of().parseHex(value);
    }

    private static void fields(JsonNode node, Set<String> allowed) {
        if (node == null || !node.isObject()) throw refused();
        node.fieldNames()
                .forEachRemaining(
                        k -> {
                            if (!allowed.contains(k)) throw refused();
                        });
    }

    private static String hex(byte[] value) {
        return HexFormat.of().formatHex(value);
    }

    private static byte[] sha(byte[] value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value);
        } catch (Exception failure) {
            throw refused();
        }
    }

    private static IllegalArgumentException refused() {
        return new IllegalArgumentException("INITIAL_ADMIN_REFUSED");
    }

    private record Plan(UUID id, String login, byte[] profile, byte[] hash) {}

    private record Reservation(
            UUID id,
            long uid,
            String login,
            byte[] hash,
            byte[] profile,
            String phase,
            long catalog) {
        boolean committed() {
            return "COMMITTED".equals(phase);
        }
    }
}
