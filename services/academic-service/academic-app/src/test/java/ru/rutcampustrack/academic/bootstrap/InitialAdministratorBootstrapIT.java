package ru.rutcampustrack.academic.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.protobuf.ByteString;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.lettuce.core.resource.DefaultClientResources;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.dao.DataAccessException;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.SharedEntityManagerCreator;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;

import ru.rutcampustrack.auth.authority.AuthAdminJdbcAuthority;
import ru.rutcampustrack.auth.authority.AuthIdentityOperationStore;
import ru.rutcampustrack.auth.authority.AuthInternalActorReader;
import ru.rutcampustrack.auth.bootstrap.InitialAdministratorAuthCommand;
import ru.rutcampustrack.auth.config.JwtProperties;
import ru.rutcampustrack.auth.config.SecurityConfig;
import ru.rutcampustrack.auth.dto.ChangePasswordRequest;
import ru.rutcampustrack.auth.dto.LoginRequest;
import ru.rutcampustrack.auth.dto.SelectActiveRoleRequest;
import ru.rutcampustrack.auth.event.PasswordChangedOutbox;
import ru.rutcampustrack.auth.repository.UserRepository;
import ru.rutcampustrack.auth.security.SessionPrincipal;
import ru.rutcampustrack.auth.service.AuthService;
import ru.rutcampustrack.auth.service.BcryptConcurrencyGuard;
import ru.rutcampustrack.auth.service.JwtService;
import ru.rutcampustrack.auth.service.LoginRateLimiter;
import ru.rutcampustrack.auth.session.SessionLifecycleService;
import ru.rutcampustrack.auth.session.jdbc.JdbcAuthSessionQueryAdapter;
import ru.rutcampustrack.auth.session.jdbc.JdbcSessionAuthority;
import ru.rutcampustrack.auth.session.model.AuthRole;
import ru.rutcampustrack.auth.session.model.RoleStatus;
import ru.rutcampustrack.authoperations.grpc.*;
import ru.rutcampustrack.shared.observability.BusinessMetrics;
import ru.rutcampustrack.shared.security.InternalJwtTestFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Clock;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Fresh synthetic two-DB bootstrap through the actual offline command handlers and Auth lifecycle.
 */
class InitialAdministratorBootstrapIT {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String DB_PASSWORD = "synthetic-bootstrap-database-only";
    private static final String INITIAL = "Synthetic-bootstrap-123!";
    private static final java.util.regex.Pattern CLI_PHASE =
            java.util.regex.Pattern.compile(
                    "BOOTSTRAP_CLI_PHASE_V1"
                        + " phase=(PARSE|READ_REQUEST|VALIDATE_REQUEST|AUTH_PREPARE|ACADEMIC_APPLY|ACADEMIC_VERIFY|AUTH_ACTIVATE|ACADEMIC_CURSOR|AUTH_CATALOG|ACADEMIC_FINALIZE|RESULT|OTHER)"
                        + " reason=(INPUT|IO|CHILD_START|CHILD_TIMEOUT|CHILD_EXIT|OUTPUT_LIMIT|REPLY_JSON|REPLY_GUARD|PROTOCOL|OTHER)"
                        + " ownerPhase=(NONE|INPUT|CONFIG|EXECUTE|OUTPUT|NO_MARKER|OTHER)"
                        + " category=(NONE|CONFIG|INPUT|DB_CONNECT|DB_PERMISSION|DB_SCHEMA|DB_CONSTRAINT|GUARD|OTHER)\\n");
    private PostgreSQLContainer<?> postgres;
    private DriverManagerDataSource authOwner;
    private DriverManagerDataSource academicOwner;
    private DriverManagerDataSource authRuntime;
    private DriverManagerDataSource academicRuntime;
    private JdbcTemplate auth;
    private JdbcTemplate academic;
    private InitialAdministratorAuthCommand authCommand;
    private InitialAdministratorAcademicCommand academicCommand;

    @BeforeEach
    void freshOwnDatabases() {
        boolean cli = "true".equals(System.getenv("RCT_BOOTSTRAP_CLI_ACCEPTANCE"));
        if (cli
                && (!"true".equals(System.getenv("TESTCONTAINERS_RYUK_DISABLED"))
                        || !"true".equals(System.getenv("TESTCONTAINERS_CHECKS_DISABLE"))))
            throw new AssertionError("CLI lane requires explicitly disabled automatic helpers");
        postgres =
                new PostgreSQLContainer<>("postgres:16")
                        .withDatabaseName("auth_db")
                        .withUsername("fixture_owner")
                        .withPassword(DB_PASSWORD)
                        .withReuse(false);
        if (cli) {
            // Require the private configtree password even from the shared loopback namespace.
            // Unix socket trust is only for the official image's own initdb entrypoint.
            postgres.withEnv("POSTGRES_INITDB_ARGS", "--auth-host=scram-sha-256 --auth-local=trust")
                    .withEnv("POSTGRES_HOST_AUTH_METHOD", "scram-sha-256")
                    .withImagePullPolicy(image -> false)
                    .withLabel("rct.bootstrap.cli.fixture", cliProject())
                    .withCreateContainerCmdModifier(
                            command ->
                                    command.getHostConfig()
                                            .withMemory(512L * 1024 * 1024)
                                            .withNanoCPUs(1_000_000_000L));
        }
        loopback(postgres, 5432);
        postgres.start();
        var root =
                new JdbcTemplate(
                        new DriverManagerDataSource(
                                postgres.getJdbcUrl(),
                                postgres.getUsername(),
                                postgres.getPassword()));
        for (String role :
                java.util.List.of(
                        "rct_auth_migrator",
                        "rct_migrator",
                        "rct_auth_runtime",
                        "rct_academic_runtime"))
            root.execute(
                    "CREATE ROLE "
                            + role
                            + " LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOBYPASSRLS PASSWORD '"
                            + DB_PASSWORD
                            + "'");
        root.execute("ALTER DATABASE auth_db OWNER TO rct_auth_migrator");
        root.execute("CREATE DATABASE academic_db OWNER rct_migrator");
        authOwner = data("auth_db", "rct_auth_migrator");
        academicOwner = data("academic_db", "rct_migrator");
        authRuntime = data("auth_db", "rct_auth_runtime");
        academicRuntime = data("academic_db", "rct_academic_runtime");
        configure(root, "auth_db", "rct_auth_migrator", "rct_auth_runtime");
        configure(
                new JdbcTemplate(data("academic_db", "fixture_owner")),
                "academic_db",
                "rct_migrator",
                "rct_academic_runtime");
        migrate();
        auth = new JdbcTemplate(authOwner);
        academic = new JdbcTemplate(academicOwner);
        authCommand = new InitialAdministratorAuthCommand(authOwner);
        academicCommand = new InitialAdministratorAcademicCommand(academicOwner);
    }

    @AfterEach
    void releaseOnlyFreshOwnedContainer() {
        if (postgres != null) postgres.stop();
    }

    @Test
    void realFirstLoginFullProfilePasswordChangeAndTerminalRetryPreserveLaterState(
            @TempDir Path keys) throws Exception {
        ObjectNode request = request(UUID.randomUUID());
        ObjectNode prepared = authCommand.execute("prepare", request);
        assertPending(prepared);
        ObjectNode committed = complete(prepared);
        long uid = committed.path("userId").asLong();
        assertThat(new AuthAdminJdbcAuthority(new JdbcTemplate(authRuntime)).profile(uid).full())
                .isTrue();
        assertThat(
                        academic.queryForObject(
                                "SELECT directory_role FROM academic_profiles WHERE id=?",
                                String.class,
                                uid))
                .isEqualTo("ADMIN");
        String originalHash =
                auth.queryForObject(
                        "SELECT password_hash FROM auth_identities WHERE id=?", String.class, uid);

        try (var redis =
                new GenericContainer<>("redis:7-alpine").withExposedPorts(6379).withReuse(false)) {
            loopback(redis, 6379);
            redis.start();
            var resources =
                    DefaultClientResources.builder()
                            .ioThreadPoolSize(2)
                            .computationThreadPoolSize(2)
                            .build();
            var factory =
                    new LettuceConnectionFactory(
                            new RedisStandaloneConfiguration(
                                    redis.getHost(), redis.getMappedPort(6379)),
                            LettuceClientConfiguration.builder()
                                    .clientResources(resources)
                                    .build());
            var entities = new LocalContainerEntityManagerFactoryBean();
            boolean entitiesInitialized = false;
            try {
                factory.afterPropertiesSet();
                factory.start();
                var redisTemplate = new StringRedisTemplate(factory);
                var signer = new InternalJwtTestFactory();
                Files.writeString(
                        keys.resolve("private.key"),
                        pem("PRIVATE KEY", signer.keyPair().getPrivate().getEncoded()));
                Files.writeString(
                        keys.resolve("public.key"),
                        pem("PUBLIC KEY", signer.publicKey().getEncoded()));
                Files.writeString(keys.resolve("kid.txt"), InternalJwtTestFactory.KEY_ID);
                var properties = new JwtProperties(keys.toString(), 900, 86400);
                var jwt = new JwtService(properties, redisTemplate);
                jwt.init();
                entities.setDataSource(authRuntime);
                entities.setPackagesToScan("ru.rutcampustrack.auth.entity");
                entities.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
                entities.setJpaPropertyMap(
                        Map.of(
                                "hibernate.hbm2ddl.auto",
                                "validate",
                                "hibernate.default_schema",
                                "public"));
                entities.afterPropertiesSet();
                entitiesInitialized = true;
                var repository =
                        new JpaRepositoryFactory(
                                        SharedEntityManagerCreator.createSharedEntityManager(
                                                entities.getObject()))
                                .getRepository(UserRepository.class);
                var jdbc = new JdbcTemplate(authRuntime);
                var transactions = new DataSourceTransactionManager(authRuntime);
                var authority =
                        new JdbcSessionAuthority(
                                jdbc, transactions, new PasswordChangedOutbox(jdbc, JSON));
                var sessions = new SessionLifecycleService(authority, authority);
                var service =
                        new AuthService(
                                repository,
                                jwt,
                                SecurityConfig.passwordEncoder(),
                                properties,
                                new LoginRateLimiter(redisTemplate),
                                new BcryptConcurrencyGuard(1, 0),
                                new BusinessMetrics(new SimpleMeterRegistry()),
                                new JdbcAuthSessionQueryAdapter(jdbc, Clock.systemUTC(), null),
                                sessions);
                var login = service.login(new LoginRequest("first-admin", INITIAL), "127.0.0.1");
                // ADMIN-only login is deliberately neutral; use the same explicit role-selection
                // flow as AuthSessionController before protected password change.
                assertThatThrownBy(() -> jwt.parseSessionAccessToken(login.accessToken()))
                        .isInstanceOf(io.jsonwebtoken.IncorrectClaimException.class);
                var bootstrap = jwt.parseBootstrapToken(login.accessToken()).getPayload();
                assertThat(bootstrap.getSubject()).isEqualTo(Long.toString(uid));
                var neutral =
                        new SessionPrincipal(
                                uid,
                                UUID.fromString(bootstrap.get("sid", String.class)),
                                Long.parseLong(bootstrap.get("sv", String.class)),
                                Long.parseLong(bootstrap.get("rv", String.class)),
                                null,
                                null,
                                null,
                                false,
                                false);
                var selected =
                        service.selectRole(
                                neutral,
                                new SelectActiveRoleRequest(
                                        "ADMIN", bootstrap.get("sv", String.class)),
                                null,
                                null);
                var access = service.selectedAccess(selected);
                var claims = jwt.parseSessionAccessToken(access.accessToken()).getPayload();
                assertThat(claims.getSubject()).isEqualTo(Long.toString(uid));
                assertThat(claims.get("sid", String.class))
                        .isEqualTo(bootstrap.get("sid", String.class));
                assertThat(claims.get("role", String.class)).isEqualTo("ADMIN");
                var principal =
                        new SessionPrincipal(
                                uid,
                                UUID.fromString(claims.get("sid", String.class)),
                                Long.parseLong(claims.get("sv", String.class)),
                                Long.parseLong(claims.get("rv", String.class)),
                                AuthRole.ADMIN,
                                RoleStatus.ACTIVE,
                                null,
                                false,
                                false);
                String replacement = "Synthetic-replacement-456!";
                service.changePassword(principal, new ChangePasswordRequest(INITIAL, replacement));
                assertThatThrownBy(
                                () ->
                                        service.login(
                                                new LoginRequest("first-admin", INITIAL),
                                                "127.0.0.1"))
                        .isInstanceOf(
                                ru.rutcampustrack.auth.exception.InvalidCredentialsException.class);
                assertThat(
                                service.login(
                                                new LoginRequest("first-admin", replacement),
                                                "127.0.0.1")
                                        .accessToken())
                        .isNotBlank();
            } finally {
                if (entitiesInitialized) entities.destroy();
                factory.destroy();
                resources.shutdown().get(5, TimeUnit.SECONDS);
            }
        }
        var after =
                auth.queryForMap(
                        "SELECT"
                            + " password_hash,initial_password,password_changed,generated_password_origin"
                            + " FROM auth_identities WHERE id=?",
                        uid);
        assertThat(after.get("password_hash")).isNotEqualTo(originalHash);
        assertThat(after.get("initial_password")).isNull();
        assertThat(after.get("generated_password_origin")).isNull();
        assertThat(after.get("password_changed")).isEqualTo(true);
        academic.update(
                "UPDATE academic_profiles SET first_name='Изменено',academic_revision=2 WHERE id=?",
                uid);
        // Completion lookup must not require the old initial secret or replay any
        // profile/credential write.
        request.put("password", "no-longer-valid");
        var repeated = authCommand.execute("prepare", request);
        assertThat(repeated.path("status").asText()).isEqualTo("COMMITTED");
        complete(repeated);
        assertThat(
                        auth.queryForMap(
                                "SELECT"
                                    + " password_hash,initial_password,password_changed,generated_password_origin"
                                    + " FROM auth_identities WHERE id=?",
                                uid))
                .isEqualTo(after);
        assertThat(
                        academic.queryForObject(
                                "SELECT first_name FROM academic_profiles WHERE id=?",
                                String.class,
                                uid))
                .isEqualTo("Изменено");
        long catalog =
                auth.queryForObject("SELECT version FROM auth_identity_catalog_clock", Long.class);
        migrate();
        assertThat(
                        auth.queryForObject(
                                "SELECT version FROM auth_identity_catalog_clock", Long.class))
                .isEqualTo(catalog);
        // Deactivate authority in place, as the normal grant writer does: selected SID history
        // retains its composite FK to this grant even after password change/revocation.
        assertThat(
                        auth.update(
                                "UPDATE user_role_grants SET status='archived',updated_at=NOW()"
                                        + " WHERE user_id=? AND role='admin' AND status='active'",
                                uid))
                .isOne();
        auth.update(
                "UPDATE admin_access_profiles SET assigned=FALSE,mode=NULL WHERE user_id=?", uid);
        assertThat(
                        new AuthAdminJdbcAuthority(new JdbcTemplate(authRuntime))
                                .profile(uid)
                                .assigned())
                .isFalse();
        assertThat(
                        auth.queryForObject(
                                "SELECT EXISTS(SELECT 1 FROM auth_sessions s JOIN user_role_grants"
                                    + " g ON g.id=s.active_role_grant_id AND g.user_id=s.user_id"
                                    + " WHERE s.user_id=? AND g.role='admin' AND"
                                    + " g.status='archived')",
                                Boolean.class,
                                uid))
                .isTrue();
        assertThatThrownBy(() -> authCommand.execute("prepare", request(UUID.randomUUID())))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void restartAtEachDurableBoundaryKeepsOneIdentityAndUnknownActivationResult() {
        var input = request(UUID.randomUUID());
        var first = authCommand.execute("prepare", input);
        long uid = first.path("userId").asLong();
        String hash =
                auth.queryForObject(
                        "SELECT password_hash FROM auth_identities WHERE id=?", String.class, uid);
        var restartedAuth = new InitialAdministratorAuthCommand(authOwner);
        var prepared = restartedAuth.execute("prepare", input);
        assertThat(prepared).isEqualTo(first);
        assertPending(prepared);
        var academicRequest = academicBinding(prepared);
        var apply = academicRequest.deepCopy();
        apply.set("profile", profile());
        var receipt = academicCommand.execute("apply", apply);
        var restartedAcademic = new InitialAdministratorAcademicCommand(academicOwner);
        assertThat(restartedAcademic.execute("apply", apply)).isEqualTo(receipt);
        assertPending(prepared);
        var activation = binding(prepared);
        activation.set(
                "academicReceipt",
                receiptOnly(restartedAcademic.execute("verify", academicRequest)));
        var forged = activation.deepCopy();
        ((ObjectNode) forged.get("academicReceipt")).put("userId", uid + 1);
        assertThatThrownBy(() -> restartedAuth.execute("activate", forged))
                .isInstanceOf(IllegalArgumentException.class);
        assertPending(prepared);
        restartedAuth.execute("activate", activation); // Simulate a lost COMMITTED reply.
        var committed = new InitialAdministratorAuthCommand(authOwner).execute("prepare", input);
        assertThat(committed.path("userId").asLong()).isEqualTo(uid);
        assertThat(committed.path("status").asText()).isEqualTo("COMMITTED");
        assertThat(restartedAuth.execute("activate", activation)).isEqualTo(committed);
        complete(committed);
        assertThat(auth.queryForObject("SELECT count(*) FROM auth_identities", Long.class))
                .isEqualTo(1);
        assertThat(academic.queryForObject("SELECT count(*) FROM academic_profiles", Long.class))
                .isEqualTo(1);
        assertThat(auth.queryForObject("SELECT count(*) FROM user_role_grants", Long.class))
                .isEqualTo(1);
        assertThat(
                        auth.queryForObject(
                                "SELECT password_hash FROM auth_identities WHERE id=?",
                                String.class,
                                uid))
                .isEqualTo(hash);
        var changed = input.deepCopy().put("login", "another-admin");
        assertThatThrownBy(() -> restartedAuth.execute("prepare", changed))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void concurrentDifferentRequestsAndExistingAdminFailClosed() throws Exception {
        try (var pool = Executors.newFixedThreadPool(2)) {
            Callable<ObjectNode> first = () -> attempt(request(UUID.randomUUID()));
            var outcomes = pool.invokeAll(java.util.List.of(first, first));
            long successes =
                    outcomes.stream()
                            .filter(
                                    f -> {
                                        try {
                                            return f.get() != null;
                                        } catch (Exception e) {
                                            throw new AssertionError(e);
                                        }
                                    })
                            .count();
            assertThat(successes).isEqualTo(1);
            assertThat(
                            auth.queryForObject(
                                    "SELECT count(*) FROM auth_initial_admin_bootstrap",
                                    Long.class))
                    .isEqualTo(1);
        }
        // A foreign existing administrator, even archived/unassigned, may never be replaced.
        var prepared = authCommand.execute("prepare", requestFromStored());
        var apply = academicBinding(prepared);
        apply.set("profile", profile());
        academicCommand.execute("apply", apply);
        var activation = binding(prepared);
        activation.set(
                "academicReceipt",
                receiptOnly(academicCommand.execute("verify", academicBinding(prepared))));
        auth.update(
                "INSERT INTO user_role_grants(user_id,role,status) VALUES(?,'admin','archived')",
                prepared.path("userId").asLong());
        assertThatThrownBy(() -> authCommand.execute("activate", activation))
                .isInstanceOf(IllegalArgumentException.class);
        assertPending(prepared);
    }

    @Test
    void runtimeOwnDatabaseAndOrdinaryOperationPathsCannotUseReservedBootstrapUuid() {
        var prepared = authCommand.execute("prepare", request(UUID.randomUUID()));
        UUID id = UUID.fromString(prepared.path("operationId").asText());
        long uid = prepared.path("userId").asLong();
        var runtime = new JdbcTemplate(authRuntime);
        assertThatThrownBy(
                        () ->
                                new InitialAdministratorAuthCommand(authRuntime)
                                        .execute("prepare", request(UUID.randomUUID())))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(
                        () ->
                                new InitialAdministratorAuthCommand(academicOwner)
                                        .execute("prepare", request(UUID.randomUUID())))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(
                        () ->
                                new InitialAdministratorAcademicCommand(academicRuntime)
                                        .execute("apply", academicBinding(prepared)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(
                        () ->
                                runtime.update(
                                        "UPDATE auth_identity_operations SET"
                                            + " phase='FAILED_SAFE',revision=revision+1,error_code='ACADEMIC_APPLY_FAILED',finished_at=NOW()"
                                            + " WHERE operation_id=?",
                                        id))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(
                        () ->
                                runtime.update(
                                        "UPDATE auth_identity_operation_targets SET"
                                            + " committed_academic_revision=1 WHERE operation_id=?",
                                        id))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(
                        () ->
                                runtime.update(
                                        "INSERT INTO"
                                            + " auth_identity_operations(operation_id,command_hash,canonical_command,kind,authorization_kind,initial_admin_bootstrap)"
                                            + " VALUES(?,?,?,'CREATE','CREATE',TRUE)",
                                        UUID.randomUUID(),
                                        new byte[32],
                                        new byte[] {1}))
                .isInstanceOf(DataAccessException.class);
        // Removing NOT NULL for the operator branch never permits an ordinary actorless command.
        assertThatThrownBy(
                        () ->
                                auth.update(
                                        "INSERT INTO"
                                            + " auth_identity_operations(operation_id,command_hash,canonical_command,kind,authorization_kind)"
                                            + " VALUES(?,?,?,'CREATE','CREATE')",
                                        UUID.randomUUID(),
                                        new byte[32],
                                        new byte[] {1}))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> runtime.queryForList("SELECT * FROM auth_initial_admin_bootstrap"))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> runtime.execute("SET ROLE rct_auth_migrator"))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(
                        () -> {
                            try (var connection =
                                    data("academic_db", "rct_auth_runtime").getConnection()) {
                                connection.isValid(1);
                            }
                        })
                .isInstanceOf(java.sql.SQLException.class);
        // SECURITY DEFINER changes current_user, never session_user: no operator-origin escape.
        auth.execute(
                "CREATE FUNCTION public.test_bootstrap_spoof() RETURNS void LANGUAGE plpgsql"
                    + " SECURITY DEFINER AS $$ BEGIN UPDATE auth_identity_operations SET"
                    + " phase='FAILED_SAFE',revision=revision+1,error_code='ACADEMIC_APPLY_FAILED',finished_at=NOW()"
                    + " WHERE initial_admin_bootstrap; END $$");
        auth.execute("GRANT EXECUTE ON FUNCTION public.test_bootstrap_spoof() TO rct_auth_runtime");
        assertThatThrownBy(() -> runtime.execute("SELECT public.test_bootstrap_spoof()"))
                .isInstanceOf(DataAccessException.class);

        var store =
                new AuthIdentityOperationStore(
                        runtime,
                        JSON,
                        new DataSourceTransactionManager(authRuntime),
                        new AuthInternalActorReader(null),
                        SecurityConfig.passwordEncoder(),
                        new BcryptConcurrencyGuard(1, 0));
        var hash =
                ByteString.copyFrom(HexFormat.of().parseHex(prepared.path("commandHash").asText()));
        denied(
                () ->
                        store.get(
                                GetIdentityOperationRequest.newBuilder()
                                        .setOperationId(id.toString())
                                        .setCommandHash(hash)
                                        .build()));
        denied(
                () ->
                        store.commit(
                                CommitIdentityOperationRequest.newBuilder()
                                        .setOperationId(id.toString())
                                        .setCommandHash(hash)
                                        .setExpectedOperationRevision(1)
                                        .build()));
        denied(
                () ->
                        store.fail(
                                FailIdentityOperationRequest.newBuilder()
                                        .setOperationId(id.toString())
                                        .setCommandHash(hash)
                                        .setExpectedOperationRevision(1)
                                        .setErrorCode("ACADEMIC_APPLY_FAILED")
                                        .build()));
        var normal =
                PrepareIdentityOperationRequest.newBuilder()
                        .setOperationId(id.toString())
                        .setKind(IdentityOperationKind.IDENTITY_OPERATION_KIND_ADMIN_ACCESS)
                        .addTargets(
                                IdentityTargetCommand.newBuilder()
                                        .setUserId(uid)
                                        .setExpectedRolesVersion(1)
                                        .setExpectedCredentialsVersion(1)
                                        .setExpectedTelegramBindingVersion(1)
                                        .setDesiredAccountState(
                                                IdentityAccountState.IDENTITY_ACCOUNT_STATE_ACTIVE)
                                        .addDesiredGrants(
                                                AdmissionGrant.newBuilder()
                                                        .setRole("ADMIN")
                                                        .setStatus("ACTIVE"))
                                        .setDesiredAdminProfile(
                                                AdmissionAdminProfile.newBuilder()
                                                        .setAssigned(true)
                                                        .setMode("FULL")))
                        .build();
        var command =
                normal.toBuilder()
                        .setCommandHash(ByteString.copyFrom(sha(normal.toByteArray())))
                        .build();
        denied(() -> store.prepare(command));
        denied(
                () ->
                        store.rejectUnprepared(
                                RejectUnpreparedIdentityOperationRequest.newBuilder()
                                        .setCanonicalPrepare(command)
                                        .setActor(
                                                IdentityOperationActor.newBuilder()
                                                        .setUserId(uid)
                                                        .setSessionId(UUID.randomUUID().toString())
                                                        .setSessionVersion(1)
                                                        .setRolesVersion(1))
                                        .setReason(
                                                IdentityOperationRejectionReason
                                                        .IDENTITY_OPERATION_REJECTION_REASON_ACTOR_DENIED)
                                        .build()));
        assertThat(
                        runtime.queryForObject(
                                "SELECT count(*) FROM auth_identity_operation_rejections",
                                Long.class))
                .isZero();
        assertThat(
                        runtime.queryForObject(
                                "SELECT count(*) FROM auth_admin_access_noop_receipts", Long.class))
                .isZero();
        assertPending(prepared);
    }

    @Test
    @org.junit.jupiter.api.Timeout(value = 6, unit = TimeUnit.MINUTES)
    @org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(
            named = "RCT_BOOTSTRAP_CLI_ACCEPTANCE",
            matches = "true")
    void operatorCliLoadsPrivateConfigAndCommitsFirstAdministrator() throws Exception {
        String configuredDirectory = System.getenv("RCT_BOOTSTRAP_CLI_DIRECTORY");
        if (configuredDirectory == null) throw new AssertionError("private CLI directory required");
        Path directory = Path.of(configuredDirectory);
        if (!directory.isAbsolute()
                || Files.isSymbolicLink(directory)
                || !Files.isDirectory(directory)
                || !Files.getPosixFilePermissions(directory)
                        .equals(
                                java.nio.file.attribute.PosixFilePermissions.fromString(
                                        "rwx------")))
            throw new AssertionError("private CLI directory required");
        var docker = org.testcontainers.DockerClientFactory.instance().client();
        String project = cliProject();
        Path compose = directory.resolve("compose.json");
        try {
            String authImage = localCliImage("RCT_BOOTSTRAP_AUTH_IMAGE");
            String academicImage = localCliImage("RCT_BOOTSTRAP_ACADEMIC_IMAGE");
            var descriptor =
                    Map.of(
                            "name",
                            project,
                            "services",
                            Map.of(
                                    "auth-service",
                                            cliService(
                                                    authImage,
                                                    "auth_db",
                                                    "rct_auth_migrator",
                                                    project,
                                                    directory.resolve("auth-config")),
                                    "academic-service",
                                            cliService(
                                                    academicImage,
                                                    "academic_db",
                                                    "rct_migrator",
                                                    project,
                                                    directory.resolve("academic-config"))));
            Files.writeString(compose, JSON.writeValueAsString(descriptor));
            Path requestFile = directory.resolve("request.json");
            UUID operation = UUID.randomUUID();
            var input =
                    JSON.createObjectNode()
                            .put("version", 1)
                            .put("operationId", operation.toString())
                            .put("login", "first-admin")
                            .put("initialPassword", INITIAL);
            input.set("profile", profile());
            Files.createFile(
                    requestFile,
                    java.nio.file.attribute.PosixFilePermissions.asFileAttribute(
                            java.nio.file.attribute.PosixFilePermissions.fromString("rw-------")));
            Files.writeString(requestFile, JSON.writeValueAsString(input));
            Path authPassword = directory.resolve("auth-config/spring.flyway.password");
            Files.delete(authPassword);
            try {
                // The same real CLI must fail before creating anything if protected Auth
                // ConfigData is missing; localhost may never silently trust the principal.
                runOperatorCli(requestFile, compose, CliInvocation.MISSING_CONFIG, false);
                assertThat(auth.queryForObject("SELECT count(*) FROM auth_identities", Long.class))
                        .isZero();
                assertThat(
                                academic.queryForObject(
                                        "SELECT count(*) FROM academic_profiles", Long.class))
                        .isZero();
            } finally {
                Files.writeString(authPassword, DB_PASSWORD);
                Files.setPosixFilePermissions(
                        authPassword,
                        java.nio.file.attribute.PosixFilePermissions.fromString("r--r--r--"));
            }
            var first = runOperatorCli(requestFile, compose, CliInvocation.FRESH);
            assertThat(
                            first.size() == 3
                                    && first.path("operationId")
                                            .asText()
                                            .equals(operation.toString())
                                    && first.path("userId").isIntegralNumber()
                                    && first.path("userId").asLong() > 0
                                    && first.path("status").asText().equals("COMMITTED"))
                    .as("actual operator CLI returns only its committed operation binding")
                    .isTrue();
            long uid = first.path("userId").asLong();
            assertThat(
                            auth.queryForObject(
                                    "SELECT account_state='ACTIVE' AND fence_operation_id IS NULL"
                                            + " AND initial_password IS NOT NULL AND NOT"
                                            + " password_changed AND"
                                            + " generated_password_origin='initial' FROM"
                                            + " auth_identities WHERE id=?",
                                    Boolean.class,
                                    uid))
                    .isTrue();
            String encoded =
                    auth.queryForObject(
                            "SELECT password_hash FROM auth_identities WHERE id=?",
                            String.class,
                            uid);
            assertThat(SecurityConfig.passwordEncoder().matches(INITIAL, encoded)).isTrue();
            assertThat(
                            INITIAL.equals(
                                    auth.queryForObject(
                                            "SELECT initial_password FROM auth_identities WHERE"
                                                    + " id=?",
                                            String.class,
                                            uid)))
                    .as("actual operator retains the accepted initial credential display lifecycle")
                    .isTrue();
            assertThat(
                            new AuthAdminJdbcAuthority(new JdbcTemplate(authRuntime))
                                    .profile(uid)
                                    .full())
                    .isTrue();
            assertThat(
                            academic.queryForObject(
                                    "SELECT directory_role='ADMIN' AND directory_status='ACTIVE'"
                                        + " AND first_name=? AND last_name=? AND"
                                        + " academic_revision=1 AND applied_auth_roles_version=1"
                                        + " FROM academic_profiles WHERE id=?",
                                    Boolean.class,
                                    profile().path("firstName").asText(),
                                    profile().path("lastName").asText(),
                                    uid))
                    .isTrue();
            assertThat(
                            academic.queryForObject(
                                    "SELECT finalized_catalog_version IS NOT NULL AND"
                                        + " finalized_catalog_version <= (SELECT"
                                        + " applied_catalog_version FROM"
                                        + " academic_identity_catalog_cursor WHERE singleton) FROM"
                                        + " academic_initial_admin_bootstrap WHERE operation_id=?",
                                    Boolean.class,
                                    operation))
                    .isTrue();
            assertThat(
                            academic.queryForObject(
                                    "SELECT account_state='ACTIVE' AND academic_revision=1"
                                            + " FROM academic_identity_read_model WHERE user_id=?",
                                    Boolean.class,
                                    uid))
                    .isTrue();
            var repeated = runOperatorCli(requestFile, compose, CliInvocation.REPEAT);
            assertThat(repeated.equals(first))
                    .as("actual CLI retry retains the stored result")
                    .isTrue();
            assertThat(auth.queryForObject("SELECT count(*) FROM auth_identities", Long.class))
                    .isOne();
            assertThat(
                            academic.queryForObject(
                                    "SELECT count(*) FROM academic_profiles", Long.class))
                    .isOne();
            String repeatedHash =
                    auth.queryForObject(
                            "SELECT password_hash FROM auth_identities WHERE id=?",
                            String.class,
                            uid);
            assertThat(encoded.equals(repeatedHash))
                    .as("actual CLI retry preserves credentials")
                    .isTrue();
            assertThat(auth.queryForObject("SELECT count(*) FROM auth_sessions", Long.class))
                    .isZero();
        } finally {
            // The compose project has only these two one-shots, never the fixture Postgres.
            // Remove a timed-out compose child too; successful --rm children are already gone.
            for (var child :
                    docker.listContainersCmd()
                            .withShowAll(true)
                            .withLabelFilter(Map.of("com.docker.compose.project", project))
                            .exec()) {
                var labels = child.getLabels();
                if (!project.equals(labels.get("rct.bootstrap.cli.owner"))
                        || !java.util.Set.of("auth-service", "academic-service")
                                .contains(labels.get("com.docker.compose.service")))
                    throw new AssertionError("unexpected bootstrap cleanup scope");
                docker.removeContainerCmd(child.getId())
                        .withForce(true)
                        .withRemoveVolumes(true)
                        .exec();
            }
        }
    }

    private static String localCliImage(String variable) {
        String reference = System.getenv(variable);
        String expected =
                Map.of(
                                "RCT_BOOTSTRAP_AUTH_IMAGE",
                                "rct/auth-service:e2e-local",
                                "RCT_BOOTSTRAP_ACADEMIC_IMAGE",
                                "rct/academic-service:e2e-local")
                        .get(variable);
        if (reference == null || !reference.equals(expected))
            throw new AssertionError("current built bootstrap images required");
        String id =
                org.testcontainers.DockerClientFactory.instance()
                        .client()
                        .inspectImageCmd(reference)
                        .exec()
                        .getId();
        if (id == null || !id.matches("sha256:[0-9a-f]{64}"))
            throw new AssertionError("local immutable bootstrap image required");
        return id;
    }

    private static String cliProject() {
        String project = System.getenv("RCT_BOOTSTRAP_CLI_PROJECT");
        if (project == null || !project.matches("rct-bootstrap-cli-[0-9]{1,20}-[0-9]{1,20}"))
            throw new AssertionError("owned CI bootstrap project required");
        return project;
    }

    private Map<String, Object> cliService(
            String image, String database, String principal, String project, Path config)
            throws Exception {
        Files.createDirectory(
                config,
                java.nio.file.attribute.PosixFilePermissions.asFileAttribute(
                        java.nio.file.attribute.PosixFilePermissions.fromString("rwxr-xr-x")));
        Path password = config.resolve("spring.flyway.password");
        Files.writeString(password, DB_PASSWORD);
        Files.setPosixFilePermissions(
                password, java.nio.file.attribute.PosixFilePermissions.fromString("r--r--r--"));
        return Map.of(
                "image",
                image,
                "pull_policy",
                "never",
                "network_mode",
                "container:" + postgres.getContainerId(),
                "mem_limit",
                "512m",
                "cpus",
                1.0,
                "healthcheck",
                Map.of("disable", true),
                "labels",
                Map.of("rct.bootstrap.cli.owner", project),
                "environment",
                Map.of(
                        "SPRING_CONFIG_IMPORT",
                        "configtree:/run/bootstrap-db/",
                        "SPRING_DATASOURCE_URL",
                        "jdbc:postgresql://localhost:5432/" + database,
                        "SPRING_FLYWAY_USER",
                        principal,
                        "SPRING_PROFILES_ACTIVE",
                        "prod",
                        "JAVA_TOOL_OPTIONS",
                        "-Xmx256m -XX:ActiveProcessorCount=1"),
                "volumes",
                java.util.List.of(
                        Map.of(
                                "type",
                                "bind",
                                "source",
                                config.toString(),
                                "target",
                                "/run/bootstrap-db",
                                "read_only",
                                true,
                                "bind",
                                Map.of("create_host_path", false))));
    }

    private enum CliInvocation {
        MISSING_CONFIG,
        FRESH,
        REPEAT
    }

    private static ObjectNode runOperatorCli(Path request, Path compose, CliInvocation invocation)
            throws Exception {
        return runOperatorCli(request, compose, invocation, true);
    }

    private static ObjectNode runOperatorCli(
            Path request, Path compose, CliInvocation invocation, boolean success)
            throws Exception {
        Path root = Path.of("").toAbsolutePath();
        while (root != null && !Files.isRegularFile(root.resolve("settings.gradle.kts")))
            root = root.getParent();
        if (root == null) throw new AssertionError("bootstrap repository required");
        var command =
                new ProcessBuilder(
                                "python3",
                                root.resolve("scripts/bootstrap-initial-admin.py").toString(),
                                "--request-file",
                                request.toString(),
                                "--compose-file",
                                compose.toString())
                        .directory(root.toFile());
        // This exact actual CLI lane opts in; ordinary operator invocations remain unchanged.
        command.environment().put("RCT_INITIAL_ADMIN_DIAGNOSTICS", "1");
        var child = command.start();
        try {
            child.getOutputStream().close();
            if (!child.waitFor(success ? 120 : 30, TimeUnit.SECONDS))
                throw new AssertionError("bounded operator CLI did not exit");
            byte[] output = child.getInputStream().readNBytes(4097);
            byte[] errors = child.getErrorStream().readNBytes(4097);
            var diagnostic =
                    CLI_PHASE.matcher(new String(errors, java.nio.charset.StandardCharsets.UTF_8));
            boolean closedDiagnostic = errors.length <= 4096 && diagnostic.matches();
            String phase = closedDiagnostic ? diagnostic.group(1) : "OTHER";
            String reason = closedDiagnostic ? diagnostic.group(2) : "OTHER";
            String ownerPhase = closedDiagnostic ? diagnostic.group(3) : "OTHER";
            String category = closedDiagnostic ? diagnostic.group(4) : "OTHER";
            String guard = null;
            if (child.exitValue() != (success ? 0 : 2)) guard = "UNEXPECTED_EXIT";
            else if (output.length > 4096) guard = "OVERSIZED_STDOUT";
            else if (success && errors.length != 0) guard = "NONEMPTY_STDERR";
            else if (!success && !closedDiagnostic) guard = "INVALID_DIAGNOSTIC";
            else if (new String(output, java.nio.charset.StandardCharsets.UTF_8).contains(INITIAL))
                guard = "SECRET_MARKER";
            else if (!success
                    && !("AUTH_PREPARE".equals(phase)
                            && "CHILD_EXIT".equals(reason)
                            && ("CONFIG".equals(ownerPhase) && "CONFIG".equals(category)
                                    || "EXECUTE".equals(ownerPhase)
                                            && "DB_PERMISSION".equals(category))))
                // A preflight/request/launcher failure is not proof of missing protected config.
                guard = "UNEXPECTED_REFUSAL_PHASE";
            if (guard != null) {
                int actualExit = child.exitValue();
                throw new AssertionError(
                        "BOOTSTRAP_CLI_RESULT_V1 invocation="
                                + invocation.name()
                                + " guard="
                                + guard
                                + " expectedExit="
                                + (success ? 0 : 2)
                                + " actualExit="
                                + (actualExit >= 0 && actualExit <= 255 ? actualExit : "OTHER")
                                + " phase="
                                + phase
                                + " reason="
                                + reason
                                + " ownerPhase="
                                + ownerPhase
                                + " category="
                                + category);
            }
            try {
                var result = JSON.readTree(output);
                if (!(result instanceof ObjectNode object)) throw new IllegalArgumentException();
                if (!success
                        && (object.size() != 1
                                || !"REFUSED".equals(object.path("status").asText())))
                    throw new IllegalArgumentException();
                return object;
            } catch (Exception invalid) {
                throw new AssertionError("closed operator CLI JSON required");
            }
        } finally {
            if (child.isAlive()) {
                child.destroyForcibly();
                if (!child.waitFor(10, TimeUnit.SECONDS))
                    throw new AssertionError("owned operator process cleanup failed");
            }
        }
    }

    private ObjectNode attempt(ObjectNode input) {
        try {
            return new InitialAdministratorAuthCommand(authOwner).execute("prepare", input);
        } catch (IllegalArgumentException rejected) {
            return null;
        }
    }

    private ObjectNode requestFromStored() {
        return request(
                auth.queryForObject(
                        "SELECT operation_id FROM auth_initial_admin_bootstrap", UUID.class));
    }

    private ObjectNode complete(ObjectNode prepared) {
        var body = academicBinding(prepared);
        var apply = body.deepCopy();
        apply.set("profile", profile());
        academicCommand.execute("apply", apply);
        var activate = binding(prepared);
        activate.set("academicReceipt", receiptOnly(academicCommand.execute("verify", body)));
        var committed = authCommand.execute("activate", activate);
        body.put("authStatus", "COMMITTED");
        var cursor = academicCommand.execute("cursor", body);
        var finalBody =
                body.deepCopy()
                        .put(
                                "committedCatalogVersion",
                                committed.path("committedCatalogVersion").asLong());
        if (cursor.path("afterCatalogVersion").asLong()
                < committed.path("committedCatalogVersion").asLong()) {
            var catalog =
                    binding(committed)
                            .put(
                                    "afterCatalogVersion",
                                    cursor.path("afterCatalogVersion").asLong());
            finalBody.put(
                    "catalogPayload",
                    authCommand.execute("catalog", catalog).path("catalogPayload").asText());
        }
        assertThat(academicCommand.execute("finalize", finalBody).path("status").asText())
                .isEqualTo("READY");
        return committed;
    }

    private void assertPending(ObjectNode prepared) {
        assertThat(
                        auth.queryForMap(
                                "SELECT account_state,fence_operation_id FROM auth_identities WHERE"
                                        + " id=?",
                                prepared.path("userId").asLong()))
                .containsEntry("account_state", "PENDING")
                .containsEntry(
                        "fence_operation_id",
                        UUID.fromString(prepared.path("operationId").asText()));
        assertThat(auth.queryForObject("SELECT count(*) FROM auth_sessions", Long.class)).isZero();
    }

    private static void denied(Runnable action) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(
                        StatusRuntimeException.class,
                        failure ->
                                assertThat(failure.getStatus().getCode())
                                        .isEqualTo(Status.Code.PERMISSION_DENIED));
    }

    private static ObjectNode request(UUID id) {
        var out = JSON.createObjectNode();
        try {
            out.put("version", 1)
                    .put("operationId", id.toString())
                    .put("login", "first-admin")
                    .put("password", INITIAL)
                    .put(
                            "profileDigest",
                            HexFormat.of()
                                    .formatHex(
                                            sha(
                                                    JSON.writeValueAsBytes(
                                                            new TreeMap<>(
                                                                    Map.of(
                                                                            "firstName",
                                                                            "Первый",
                                                                            "lastName",
                                                                            "Администратор",
                                                                            "middleName",
                                                                            ""))))));
        } catch (Exception failure) {
            throw new AssertionError(failure);
        }
        return out;
    }

    private static ObjectNode profile() {
        return JSON.createObjectNode()
                .put("firstName", "Первый")
                .put("lastName", "Администратор")
                .put("middleName", "");
    }

    private static ObjectNode binding(ObjectNode reply) {
        var out = JSON.createObjectNode();
        for (String key :
                java.util.List.of(
                        "version",
                        "operationId",
                        "login",
                        "profileDigest",
                        "commandHash",
                        "userId",
                        "rolesVersion")) out.set(key, reply.get(key));
        return out;
    }

    private static ObjectNode academicBinding(ObjectNode reply) {
        return binding(reply).put("authStatus", reply.path("status").asText());
    }

    private static ObjectNode receiptOnly(ObjectNode reply) {
        var out = reply.deepCopy();
        out.remove("status");
        return out;
    }

    private static void loopback(GenericContainer<?> container, int port) {
        container.withCreateContainerCmdModifier(
                command ->
                        command.getHostConfig()
                                .withPortBindings(
                                        new com.github.dockerjava.api.model.PortBinding(
                                                com.github.dockerjava.api.model.Ports.Binding
                                                        .bindIpAndPort("127.0.0.1", 0),
                                                new com.github.dockerjava.api.model.ExposedPort(
                                                        port))));
    }

    private DriverManagerDataSource data(String database, String role) {
        return new DriverManagerDataSource(
                postgres.getJdbcUrl().replace("/auth_db", "/" + database), role, DB_PASSWORD);
    }

    private void configure(JdbcTemplate owner, String database, String migrator, String runtime) {
        owner.execute("REVOKE ALL ON DATABASE " + database + " FROM PUBLIC");
        owner.execute("GRANT CONNECT ON DATABASE " + database + " TO " + runtime);
        owner.execute("REVOKE ALL ON SCHEMA public FROM PUBLIC");
        owner.execute("ALTER SCHEMA public OWNER TO " + migrator);
        owner.execute("GRANT USAGE ON SCHEMA public TO " + runtime);
        owner.execute(
                "ALTER DEFAULT PRIVILEGES FOR ROLE "
                        + migrator
                        + " IN SCHEMA public GRANT SELECT,INSERT,UPDATE,DELETE ON TABLES TO "
                        + runtime);
        owner.execute(
                "ALTER DEFAULT PRIVILEGES FOR ROLE "
                        + migrator
                        + " IN SCHEMA public GRANT USAGE,SELECT ON SEQUENCES TO "
                        + runtime);
        owner.execute("ALTER ROLE " + runtime + " SET search_path TO pg_catalog,public");
    }

    private void migrate() {
        Path root = Path.of("").toAbsolutePath();
        while (root != null && !Files.isRegularFile(root.resolve("settings.gradle.kts")))
            root = root.getParent();
        if (root == null) throw new IllegalStateException("repository root required");
        Flyway.configure()
                .dataSource(authOwner)
                .locations(
                        "filesystem:"
                                + root.resolve(
                                        "services/auth-service/auth-app/src/main/resources/db/auth-migration"))
                .load()
                .migrate();
        Flyway.configure()
                .dataSource(academicOwner)
                .locations(
                        "filesystem:"
                                + root.resolve(
                                        "services/academic-service/academic-app/src/main/resources/db/academic-migration"))
                .load()
                .migrate();
    }

    private static byte[] sha(byte[] data) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(data);
        } catch (Exception failure) {
            throw new AssertionError(failure);
        }
    }

    private static String pem(String label, byte[] data) {
        return "-----BEGIN "
                + label
                + "-----\n"
                + Base64.getMimeEncoder(64, new byte[] {'\n'}).encodeToString(data)
                + "\n-----END "
                + label
                + "-----\n";
    }
}
