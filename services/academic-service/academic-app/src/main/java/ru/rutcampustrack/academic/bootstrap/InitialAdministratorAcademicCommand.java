package ru.rutcampustrack.academic.bootstrap;

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

import ru.rutcampustrack.academic.identitycatalog.JdbcIdentityCatalogProjection;
import ru.rutcampustrack.authauthority.grpc.IdentityCatalogChangesResponse;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

import javax.sql.DataSource;

/** Operator-only own-Academic-DB steps; never receives the initial password or request file. */
public final class InitialAdministratorAcademicCommand {
    private static final ObjectMapper JSON =
            new ObjectMapper()
                    .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
                    .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    private static final Set<String> BASE =
            Set.of(
                    "version",
                    "operationId",
                    "login",
                    "userId",
                    "rolesVersion",
                    "commandHash",
                    "profileDigest",
                    "authStatus");
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;

    public InitialAdministratorAcademicCommand(DataSource data) {
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
            var input = JSON.readTree(bytes);
            // Bind existing ConfigData/configtree only. No Spring context or auto-configuration.
            phase = "CONFIG";
            var environment = new StandardEnvironment();
            ConfigDataEnvironmentPostProcessor.applyTo(environment);
            var data =
                    new DriverManagerDataSource(
                            environment.getRequiredProperty("spring.datasource.url"),
                            environment.getRequiredProperty("spring.flyway.user"),
                            environment.getRequiredProperty("spring.flyway.password"));
            phase = "EXECUTE";
            var result = new InitialAdministratorAcademicCommand(data).execute(args[0], input);
            phase = "OUTPUT";
            protocol.println(JSON.writeValueAsString(result));
        } catch (Exception failure) {
            protocol.println("{\"status\":\"REFUSED\"}");
            if (diagnostics)
                diagnostic.println(
                        "BOOTSTRAP_OWNER_FAILURE_V1 owner=ACADEMIC phase="
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
        if (!Boolean.TRUE.equals(
                jdbc.queryForObject(
                        """
                        SELECT current_database()='academic_db' AND session_user='rct_migrator'
                            AND current_user=session_user
                        """,
                        Boolean.class))) throw refused();
        if (input == null || !input.isObject()) throw refused();
        return switch (stage) {
            case "apply" -> apply(input);
            case "verify" -> verify(input);
            case "cursor" -> cursor(input);
            case "finalize" -> finalizeProjection(input);
            default -> throw refused();
        };
    }

    private ObjectNode apply(JsonNode input) {
        fields(input, "profile");
        Plan plan = plan(input);
        JsonNode profile = input.get("profile");
        if (profile == null
                || !profile.isObject()
                || profile.size() != 3
                || !profile.has("firstName")
                || !profile.has("lastName")
                || !profile.has("middleName")) throw refused();
        String first = name(profile, "firstName", false);
        String last = name(profile, "lastName", false);
        String middle = name(profile, "middleName", true);
        if (!MessageDigest.isEqual(sha(canonicalProfile(first, last, middle)), plan.profile()))
            throw refused();
        return tx.execute(
                status -> {
                    lock(plan.id());
                    var existing = find(plan.id());
                    if (existing != null) {
                        same(existing, plan);
                        return reply(existing);
                    }
                    if (!"PREPARED".equals(text(input, "authStatus"))) throw refused();
                    if (Boolean.TRUE.equals(
                            jdbc.queryForObject(
                                    """
SELECT EXISTS(SELECT 1 FROM academic_initial_admin_bootstrap)
    OR EXISTS(SELECT 1 FROM academic_profiles WHERE directory_role='ADMIN' OR id=?)
    OR EXISTS(SELECT 1 FROM academic_identity_operations WHERE operation_id=?)
""",
                                    Boolean.class,
                                    plan.uid(),
                                    plan.id()))) throw refused();
                    jdbc.update(
                            """
INSERT INTO academic_profiles(id,directory_role,directory_status,last_name,
    first_name,middle_name,academic_revision,applied_auth_roles_version,created_at,updated_at)
VALUES(?,'ADMIN','ACTIVE',?,?,?,1,1,NOW(),NOW())
""",
                            plan.uid(),
                            last,
                            first,
                            middle.isEmpty() ? null : middle);
                    byte[] receipt = receiptHash(plan);
                    jdbc.update(
                            """
INSERT INTO academic_initial_admin_bootstrap(operation_id,user_id,command_hash,
    profile_digest,receipt_hash,academic_revision,applied_auth_roles_version)
VALUES(?,?,?,?,?,1,1)
""",
                            plan.id(),
                            plan.uid(),
                            plan.hash(),
                            plan.profile(),
                            receipt);
                    return reply(find(plan.id()));
                });
    }

    private ObjectNode verify(JsonNode input) {
        fields(input);
        Plan plan = plan(input);
        return tx.execute(
                status -> {
                    lock(plan.id());
                    Receipt receipt = required(plan);
                    if ("PREPARED".equals(text(input, "authStatus"))) {
                        var profiles =
                                jdbc.query(
                                        """
SELECT first_name,last_name,coalesce(middle_name,''),
    directory_role='ADMIN' AND directory_status='ACTIVE' AND archived_at IS NULL
    AND academic_revision=1 AND applied_auth_roles_version=1
FROM academic_profiles WHERE id=? FOR UPDATE
""",
                                        (rs, n) -> {
                                            if (!rs.getBoolean(4)) throw refused();
                                            return sha(
                                                    canonicalProfile(
                                                            rs.getString(1),
                                                            rs.getString(2),
                                                            rs.getString(3)));
                                        },
                                        plan.uid());
                        if (profiles.size() != 1
                                || !MessageDigest.isEqual(profiles.getFirst(), plan.profile()))
                            throw refused();
                    }
                    return reply(receipt);
                });
    }

    private ObjectNode cursor(JsonNode input) {
        fields(input);
        var receipt = required(plan(input));
        var out = reply(receipt);
        out.put("afterCatalogVersion", new JdbcIdentityCatalogProjection(jdbc).frontier());
        return out;
    }

    private ObjectNode finalizeProjection(JsonNode input) {
        fields(input, "committedCatalogVersion", "catalogPayload");
        Plan plan = plan(input);
        if (!"COMMITTED".equals(text(input, "authStatus"))) throw refused();
        long target = number(input, "committedCatalogVersion");
        if (target <= 0) throw refused();
        return tx.execute(
                status -> {
                    lock(plan.id());
                    var receipt = required(plan);
                    var projection = new JdbcIdentityCatalogProjection(jdbc);
                    long frontier = projection.frontier();
                    if (frontier < target) {
                        String payload = text(input, "catalogPayload");
                        if (payload.length() > 1_000_000) throw refused();
                        try {
                            var response =
                                    IdentityCatalogChangesResponse.parseFrom(
                                            Base64.getDecoder().decode(payload));
                            frontier = projection.apply(response);
                        } catch (Exception invalid) {
                            throw refused();
                        }
                    }
                    if (frontier >= target && receipt.finalized() == 0) {
                        jdbc.update(
                                """
UPDATE academic_initial_admin_bootstrap SET finalized_catalog_version=?
WHERE operation_id=? AND finalized_catalog_version IS NULL
""",
                                target,
                                plan.id());
                    }
                    var out = reply(required(plan));
                    out.put("afterCatalogVersion", frontier);
                    out.put("status", frontier >= target ? "READY" : "MORE");
                    return out;
                });
    }

    private Receipt required(Plan plan) {
        Receipt receipt = find(plan.id());
        if (receipt == null) throw refused();
        same(receipt, plan);
        return receipt;
    }

    private Receipt find(UUID id) {
        var rows =
                jdbc.query(
                        """
SELECT user_id,command_hash,profile_digest,receipt_hash,finalized_catalog_version
FROM academic_initial_admin_bootstrap WHERE operation_id=?
""",
                        (rs, n) ->
                                new Receipt(
                                        id,
                                        rs.getLong(1),
                                        rs.getBytes(2),
                                        rs.getBytes(3),
                                        rs.getBytes(4),
                                        rs.getLong(5)),
                        id);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    private void lock(UUID id) {
        jdbc.queryForList("SELECT pg_advisory_xact_lock(?)", 724830210081L);
        jdbc.queryForList(
                "SELECT pg_advisory_xact_lock(hashtextextended(?,172903))",
                "academic-identity-api:" + id);
    }

    private static Plan plan(JsonNode input) {
        if (number(input, "version") != 1 || number(input, "rolesVersion") != 1) throw refused();
        UUID id = UUID.fromString(text(input, "operationId"));
        long uid = number(input, "userId");
        if (uid <= 0 || !Set.of("PREPARED", "COMMITTED").contains(text(input, "authStatus")))
            throw refused();
        String login = text(input, "login");
        if (!login.matches("[A-Za-z0-9_.-]{3,32}")) throw refused();
        byte[] profile = digest(input, "profileDigest");
        byte[] hash = digest(input, "commandHash");
        try {
            byte[] expected =
                    sha(
                            JSON.writeValueAsBytes(
                                    new TreeMap<>(
                                            Map.of(
                                                    "version",
                                                    1,
                                                    "operationId",
                                                    id.toString(),
                                                    "login",
                                                    login,
                                                    "profileDigest",
                                                    hex(profile)))));
            if (!MessageDigest.isEqual(hash, expected)) throw refused();
        } catch (Exception invalid) {
            throw refused();
        }
        return new Plan(id, uid, hash, profile);
    }

    private static byte[] canonicalProfile(String first, String last, String middle) {
        try {
            return JSON.writeValueAsBytes(
                    new TreeMap<>(
                            Map.of("firstName", first, "lastName", last, "middleName", middle)));
        } catch (Exception invalid) {
            throw refused();
        }
    }

    private static byte[] receiptHash(Plan p) {
        try {
            return sha(
                    JSON.writeValueAsBytes(
                            new TreeMap<>(
                                    Map.of(
                                            "version",
                                            1,
                                            "operationId",
                                            p.id().toString(),
                                            "commandHash",
                                            hex(p.hash()),
                                            "profileDigest",
                                            hex(p.profile()),
                                            "userId",
                                            p.uid(),
                                            "rolesVersion",
                                            1,
                                            "academicRevision",
                                            1))));
        } catch (Exception invalid) {
            throw refused();
        }
    }

    private static ObjectNode reply(Receipt receipt) {
        var out = JSON.createObjectNode();
        out.put("version", 1)
                .put("operationId", receipt.id().toString())
                .put("userId", receipt.uid())
                .put("commandHash", hex(receipt.hash()))
                .put("profileDigest", hex(receipt.profile()))
                .put("rolesVersion", 1)
                .put("academicRevision", 1)
                .put("receiptHash", hex(receipt.receipt()))
                .put("status", receipt.finalized() > 0 ? "READY" : "APPLIED");
        return out;
    }

    private static void same(Receipt r, Plan p) {
        if (r.uid() != p.uid()
                || !MessageDigest.isEqual(r.hash(), p.hash())
                || !MessageDigest.isEqual(r.profile(), p.profile())
                || !MessageDigest.isEqual(r.receipt(), receiptHash(p))) throw refused();
    }

    private static String name(JsonNode node, String key, boolean optional) {
        String value = text(node, key);
        if ((!optional && value.isBlank())
                || value.length() > 128
                || !StandardCharsets.UTF_8.newEncoder().canEncode(value)) throw refused();
        return value;
    }

    private static void fields(JsonNode node, String... extra) {
        var allowed = new java.util.HashSet<>(BASE);
        allowed.addAll(java.util.List.of(extra));
        node.fieldNames()
                .forEachRemaining(
                        k -> {
                            if (!allowed.contains(k)) throw refused();
                        });
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

    private static byte[] sha(byte[] bytes) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(bytes);
        } catch (Exception invalid) {
            throw refused();
        }
    }

    private static String hex(byte[] bytes) {
        return HexFormat.of().formatHex(bytes);
    }

    private static IllegalArgumentException refused() {
        return new IllegalArgumentException("INITIAL_ADMIN_REFUSED");
    }

    private record Plan(UUID id, long uid, byte[] hash, byte[] profile) {}

    private record Receipt(
            UUID id, long uid, byte[] hash, byte[] profile, byte[] receipt, long finalized) {}
}
