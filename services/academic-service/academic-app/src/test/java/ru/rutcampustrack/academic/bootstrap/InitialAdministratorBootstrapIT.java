package ru.rutcampustrack.academic.bootstrap;

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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Fresh synthetic two-DB bootstrap through the actual offline command handlers and Auth lifecycle.
 */
class InitialAdministratorBootstrapIT {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String DB_PASSWORD = "synthetic-bootstrap-database-only";
    private static final String INITIAL = "Synthetic-bootstrap-123!";
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
        postgres =
                new PostgreSQLContainer<>("postgres:16")
                        .withDatabaseName("auth_db")
                        .withUsername("fixture_owner")
                        .withPassword(DB_PASSWORD)
                        .withReuse(false);
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
        auth.update("DELETE FROM user_role_grants WHERE user_id=?", uid);
        auth.update(
                "UPDATE admin_access_profiles SET assigned=FALSE,mode=NULL WHERE user_id=?", uid);
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
