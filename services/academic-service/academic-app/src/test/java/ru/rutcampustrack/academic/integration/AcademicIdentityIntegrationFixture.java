package ru.rutcampustrack.academic.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.PortBinding;
import com.github.dockerjava.api.model.Ports;

import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.ServerInterceptors;
import io.grpc.netty.GrpcSslContexts;
import io.grpc.netty.NettyChannelBuilder;
import io.grpc.netty.NettyServerBuilder;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import org.flywaydb.core.Flyway;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.SharedEntityManagerCreator;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;

import ru.rutcampustrack.academic.contract.dto.user.CreateUserRequest;
import ru.rutcampustrack.academic.contract.enums.UserRole;
import ru.rutcampustrack.academic.identitycatalog.AuthIdentityDataClient;
import ru.rutcampustrack.academic.identitycatalog.IdentityCatalogSynchronizer;
import ru.rutcampustrack.academic.identityoperation.AcademicUserIdentityOperations;
import ru.rutcampustrack.academic.identitypresentation.AcademicIdentityPresentationGrpcService;
import ru.rutcampustrack.academic.identitypresentation.IdentityPresentationServiceIdentityInterceptor;
import ru.rutcampustrack.academic.security.AcademicUserContextFilter;
import ru.rutcampustrack.academic.user.UserService;
import ru.rutcampustrack.auth.authority.*;
import ru.rutcampustrack.auth.config.InternalIssuerProperties;
import ru.rutcampustrack.auth.config.JwtProperties;
import ru.rutcampustrack.auth.config.OtpProperties;
import ru.rutcampustrack.auth.event.PasswordChangedOutbox;
import ru.rutcampustrack.auth.repository.UserRepository;
import ru.rutcampustrack.auth.security.AuthAdminAccess;
import ru.rutcampustrack.auth.service.*;
import ru.rutcampustrack.auth.session.SessionLifecycleService;
import ru.rutcampustrack.auth.session.jdbc.JdbcAuthSessionQueryAdapter;
import ru.rutcampustrack.auth.session.jdbc.JdbcSessionAuthority;
import ru.rutcampustrack.auth.session.model.AuthMethod;
import ru.rutcampustrack.auth.session.model.AuthRole;
import ru.rutcampustrack.auth.session.model.SessionSnapshot;
import ru.rutcampustrack.authauthority.grpc.AcademicIdentityPresentationServiceGrpc;
import ru.rutcampustrack.authsecurity.grpc.AuthSecurityCapabilityServiceGrpc;
import ru.rutcampustrack.authsecurity.grpc.LiveSessionAuthority;
import ru.rutcampustrack.authsecurity.grpc.StoredSessionIdentity;
import ru.rutcampustrack.shared.observability.BusinessMetrics;
import ru.rutcampustrack.shared.security.InternalJwtClaims;
import ru.rutcampustrack.shared.security.InternalJwtTestFactory;
import ru.rutcampustrack.shared.security.admin.AdminAccessProfile;
import ru.rutcampustrack.shared.security.grpc.DirectedServiceCredential;
import ru.rutcampustrack.shared.security.grpc.ServicePrincipal;
import ru.rutcampustrack.shared.security.grpc.ServiceTokenCallCredentials;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

import javax.net.ssl.KeyManagerFactory;

/**
 * Shared synthetic integration fixture, not an authority implementation. Auth owns every identity,
 * grant, credential and session; Academic owns every profile and domain row. All admission and
 * mutations use the production services and directed TLS protocol.
 */
public final class AcademicIdentityIntegrationFixture implements AutoCloseable {
    public static final String FIRST_FULL_TEST_PASSWORD = "synthetic-first-fixture-password";
    private static final String ACADEMIC_DB = "academic_db";
    private static final String AUTH_DB = "auth_db";
    private static final String ACADEMIC_RUNTIME = "rct_academic_runtime";
    private static final String AUTH_RUNTIME = "rct_auth_runtime";
    private static final String ACADEMIC_MIGRATOR = "rct_migrator";
    private static final String AUTH_MIGRATOR = "rct_auth_migrator";
    private final String namespace = "academic-fixture-" + UUID.randomUUID();
    private final String databasePassword = randomToken();
    private final String academicToAuth = randomToken();
    private final String presentationToken = randomToken();
    private final InternalJwtTestFactory signer = new InternalJwtTestFactory();
    private final ReentrantLock testLease = new ReentrantLock();
    private final AnnotationConfigApplicationContext authContext =
            new AnnotationConfigApplicationContext();
    private final LocalContainerEntityManagerFactoryBean authEntities =
            new LocalContainerEntityManagerFactoryBean();
    private boolean entitiesInitialized;
    private final io.netty.channel.nio.NioEventLoopGroup authBoss =
            new io.netty.channel.nio.NioEventLoopGroup(1);
    private final io.netty.channel.nio.NioEventLoopGroup authWorkers =
            new io.netty.channel.nio.NioEventLoopGroup(1);
    private final io.netty.channel.nio.NioEventLoopGroup presentationBoss =
            new io.netty.channel.nio.NioEventLoopGroup(1);
    private final io.netty.channel.nio.NioEventLoopGroup presentationWorkers =
            new io.netty.channel.nio.NioEventLoopGroup(1);
    private final io.netty.channel.nio.NioEventLoopGroup clientLoops =
            new io.netty.channel.nio.NioEventLoopGroup(1);
    private final ExecutorService authCalls = Executors.newFixedThreadPool(2);
    private final ExecutorService presentationCalls = Executors.newFixedThreadPool(2);
    private final ExecutorService clientCalls = Executors.newFixedThreadPool(2);
    private final Path root;
    private final Path temporary;
    private final PostgreSQLContainer<?> postgres;
    private final GenericContainer<?> authRedis;
    private final String containerId;
    private final String redisContainerId;
    private final DriverManagerDataSource academicData;
    private final DriverManagerDataSource authData;
    private final DriverManagerDataSource academicOwnerData;
    private final DriverManagerDataSource authOwnerData;
    private final JdbcTemplate academic;
    private final JdbcTemplate auth;
    private final JdbcTemplate academicOwner;
    private final JdbcTemplate authOwner;
    private final List<String> academicTables;
    private final List<String> authTables;
    private final long academicOid;
    private final long authOid;
    private final Path certificate;
    private final Server authServer;
    private final ManagedChannel authChannel;
    private final AuthSecurityCapabilityServiceGrpc.AuthSecurityCapabilityServiceBlockingStub
            security;
    private volatile ApplicationContext application;
    private Server presentationServer;
    private ManagedChannel presentationChannel;
    private CanonicalUsers canonical;
    private final AtomicBoolean closed = new AtomicBoolean();
    private final AtomicBoolean dataTransportUnavailable = new AtomicBoolean();

    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement
    static class Transactions {}

    public static AcademicIdentityIntegrationFixture start() {
        try {
            return new AcademicIdentityIntegrationFixture();
        } catch (Exception failure) {
            throw new IllegalStateException(
                    "Owned split integration fixture could not start", failure);
        }
    }

    private AcademicIdentityIntegrationFixture() throws Exception {
        root = repositoryRoot();
        temporary = Files.createTempDirectory("academic-auth-fixture-");
        postgres =
                new PostgreSQLContainer<>("postgres:16")
                        .withDatabaseName(ACADEMIC_DB)
                        .withUsername("fixture_owner")
                        .withPassword(databasePassword)
                        .withReuse(false)
                        .withLabel("rct.fixture.owner", namespace);
        loopback(postgres, 5432);
        authRedis =
                new GenericContainer<>("redis:7-alpine")
                        .withExposedPorts(6379)
                        .withReuse(false)
                        .withLabel("rct.fixture.owner", namespace);
        loopback(authRedis, 6379);
        try {
            postgres.start();
            authRedis.start();
            containerId = postgres.getContainerId();
            redisContainerId = authRedis.getContainerId();
            var administrator =
                    new JdbcTemplate(
                            new DriverManagerDataSource(
                                    postgres.getJdbcUrl(),
                                    postgres.getUsername(),
                                    postgres.getPassword()));
            for (String role :
                    List.of(ACADEMIC_MIGRATOR, AUTH_MIGRATOR, ACADEMIC_RUNTIME, AUTH_RUNTIME)) {
                administrator.execute(
                        "CREATE ROLE "
                                + role
                                + " LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION"
                                + " NOBYPASSRLS PASSWORD '"
                                + databasePassword
                                + "'");
            }
            administrator.execute("CREATE DATABASE auth_db OWNER rct_auth_migrator");
            administrator.execute("ALTER DATABASE academic_db OWNER TO rct_migrator");
            administrator.execute("REVOKE CONNECT ON DATABASE postgres,template1 FROM PUBLIC");
            String authUrl = postgres.getJdbcUrl().replace("/academic_db", "/auth_db");
            academicOwnerData =
                    new DriverManagerDataSource(
                            postgres.getJdbcUrl(), ACADEMIC_MIGRATOR, databasePassword);
            authOwnerData = new DriverManagerDataSource(authUrl, AUTH_MIGRATOR, databasePassword);
            academicData =
                    new DriverManagerDataSource(
                            postgres.getJdbcUrl(), ACADEMIC_RUNTIME, databasePassword);
            authData = new DriverManagerDataSource(authUrl, AUTH_RUNTIME, databasePassword);
            academicOwner = new JdbcTemplate(academicOwnerData);
            authOwner = new JdbcTemplate(authOwnerData);
            configureDatabase(
                    administrator,
                    postgres.getJdbcUrl(),
                    ACADEMIC_DB,
                    ACADEMIC_MIGRATOR,
                    ACADEMIC_RUNTIME);
            configureDatabase(administrator, authUrl, AUTH_DB, AUTH_MIGRATOR, AUTH_RUNTIME);
            migrate(
                    academicOwnerData,
                    "services/academic-service/academic-app/src/main/resources/db/academic-migration");
            migrate(
                    authOwnerData,
                    "services/auth-service/auth-app/src/main/resources/db/auth-migration");
            academic = new JdbcTemplate(academicData);
            auth = new JdbcTemplate(authData);
            academicTables = tables(academicOwner);
            authTables = tables(authOwner);
            academicOid = databaseOid(academic);
            authOid = databaseOid(auth);
            assertIsolatedRuntime(academic, ACADEMIC_DB, ACADEMIC_RUNTIME);
            assertIsolatedRuntime(auth, AUTH_DB, AUTH_RUNTIME);
            if (!Boolean.TRUE.equals(
                            academic.queryForObject(
                                    "SELECT to_regclass('auth_identities') IS NULL AND"
                                            + " to_regclass('user_role_grants') IS NULL AND"
                                            + " to_regclass('auth_sessions') IS NULL",
                                    Boolean.class))
                    || !Boolean.TRUE.equals(
                            auth.queryForObject(
                                    "SELECT to_regclass('academic_profiles') IS NULL AND"
                                            + " to_regclass('groups') IS NULL",
                                    Boolean.class)))
                throw new IllegalStateException("Split fixture schema ownership is invalid");
            certificate = certificate(temporary);
            writeSigningKeys();
            configureAuthContext();
            var identity = new AuthDirectedIdentityInterceptor("", "", academicToAuth, "");
            var actor = new AuthActorMetadataInterceptor();
            authServer =
                    nettyServer(authCalls)
                            .addService(
                                    ServerInterceptors.intercept(
                                            new AuthIdentityOperationGrpcService(
                                                    authContext.getBean(
                                                            AuthIdentityOperationStore.class)),
                                            identity,
                                            actor))
                            .addService(
                                    ServerInterceptors.intercept(
                                            new AuthDataAuthorityGrpcService(
                                                    authContext.getBean(
                                                            AuthIdentityAuthorityQuery.class),
                                                    authContext.getBean(
                                                            AuthAdministrativeAuthorityQuery
                                                                    .class)),
                                            identity,
                                            actor))
                            .addService(
                                    ServerInterceptors.intercept(
                                            new AuthSecurityCapabilityGrpcService(
                                                    authContext.getBean(
                                                            AuthReportCapabilityService.class),
                                                    authContext.getBean(
                                                            AuthPasswordStepUpService.class),
                                                    authContext.getBean(
                                                            SubjectDeletionProofService.class)),
                                            identity,
                                            actor))
                            .addService(
                                    ServerInterceptors.intercept(
                                            new AuthCredentialReaderInvalidationGrpcService(
                                                    authContext.getBean(
                                                            AuthCredentialReaderInvalidationStore
                                                                    .class)),
                                            identity,
                                            actor))
                            .build()
                            .start();
            authChannel = channel(authServer.getPort());
            security = AuthSecurityCapabilityServiceGrpc.newBlockingStub(authChannel);
            Runtime.getRuntime().addShutdownHook(new Thread(this::close, namespace + "-cleanup"));
        } catch (Exception failure) {
            try {
                close();
            } catch (RuntimeException cleanup) {
                failure.addSuppressed(cleanup);
            }
            throw failure;
        }
    }

    private void configureDatabase(
            JdbcTemplate administrator, String url, String db, String migrator, String runtime) {
        administrator.execute("REVOKE ALL ON DATABASE " + db + " FROM PUBLIC");
        administrator.execute("GRANT CONNECT ON DATABASE " + db + " TO " + runtime);
        administrator.execute(
                "GRANT CONNECT,CREATE,TEMPORARY ON DATABASE " + db + " TO " + migrator);
        var owner =
                new JdbcTemplate(
                        new DriverManagerDataSource(
                                url, postgres.getUsername(), postgres.getPassword()));
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
        owner.execute(
                "ALTER DEFAULT PRIVILEGES FOR ROLE "
                        + migrator
                        + " IN SCHEMA public REVOKE EXECUTE ON FUNCTIONS FROM PUBLIC");
        owner.execute("ALTER ROLE " + runtime + " SET search_path TO pg_catalog,public");
    }

    private void migrate(DriverManagerDataSource data, String location) {
        Flyway.configure()
                .dataSource(data)
                .locations("filesystem:" + root.resolve(location))
                .load()
                .migrate();
    }

    private void configureAuthContext() {
        authEntities.setDataSource(authData);
        authEntities.setPackagesToScan(
                "ru.rutcampustrack.auth.entity", "ru.rutcampustrack.auth.event");
        authEntities.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
        authEntities.setJpaPropertyMap(
                Map.of(
                        "hibernate.hbm2ddl.auto",
                        "validate",
                        "hibernate.jdbc.time_zone",
                        "UTC",
                        "hibernate.default_schema",
                        "public"));
        authEntities.afterPropertiesSet();
        entitiesInitialized = true;
        var manager = new JpaTransactionManager(authEntities.getObject());
        manager.setDataSource(authData);
        authContext.registerBean(
                "transactionManager", PlatformTransactionManager.class, () -> manager);
        authContext.registerBean(JdbcTemplate.class, () -> auth);
        authContext.registerBean(
                ObjectMapper.class, () -> new ObjectMapper().findAndRegisterModules());
        var clients =
                io.lettuce.core.resource.DefaultClientResources.builder()
                        .ioThreadPoolSize(2)
                        .computationThreadPoolSize(2)
                        .build();
        authContext.registerBean(
                io.lettuce.core.resource.DefaultClientResources.class, () -> clients);
        authContext.registerBean(
                LettuceConnectionFactory.class,
                () ->
                        new LettuceConnectionFactory(
                                new RedisStandaloneConfiguration(
                                        authRedis.getHost(), authRedis.getMappedPort(6379)),
                                LettuceClientConfiguration.builder()
                                        .clientResources(clients)
                                        .build()));
        authContext.registerBean(
                StringRedisTemplate.class,
                () -> new StringRedisTemplate(authContext.getBean(LettuceConnectionFactory.class)));
        authContext.registerBean(
                JwtProperties.class,
                () -> new JwtProperties(temporary.resolve("jwt").toString(), 900, 86400));
        authContext.registerBean(
                InternalIssuerProperties.class,
                () -> {
                    var properties = new InternalIssuerProperties();
                    properties.setSecret(randomToken());
                    properties.setTokenTtlSeconds(300);
                    return properties;
                });
        authContext.registerBean(
                OtpProperties.class, () -> new OtpProperties(6, 120, 5, 3600, 30, 20, 60));
        authContext.registerBean(
                org.springframework.security.crypto.password.PasswordEncoder.class,
                () -> new BCryptPasswordEncoder());
        authContext.registerBean(
                BcryptConcurrencyGuard.class, () -> new BcryptConcurrencyGuard(2, 0));
        authContext.registerBean(
                BusinessMetrics.class, () -> new BusinessMetrics(new SimpleMeterRegistry()));
        var repositories =
                new JpaRepositoryFactory(
                        SharedEntityManagerCreator.createSharedEntityManager(
                                authEntities.getObject()));
        repositories.setBeanFactory(authContext.getBeanFactory());
        authContext.registerBean(
                UserRepository.class, () -> repositories.getRepository(UserRepository.class));
        authContext.registerBean(
                AcademicIdentityPresentationClient.class,
                () -> new AcademicIdentityPresentationClient(presentationToken, "localhost"));
        authContext.registerBean(
                JdbcAuthSessionQueryAdapter.class,
                () ->
                        new JdbcAuthSessionQueryAdapter(
                                auth,
                                java.time.Clock.systemUTC(),
                                authContext.getBean(AcademicIdentityPresentationClient.class)));
        authContext.registerBean(
                SessionLifecycleService.class,
                () -> {
                    var authority = authContext.getBean(JdbcSessionAuthority.class);
                    return new SessionLifecycleService(authority, authority);
                });
        authContext.register(
                Transactions.class,
                JwtService.class,
                LoginRateLimiter.class,
                RedisReportDownloadTicketStore.class,
                PasswordChangedOutbox.class,
                JdbcSessionAuthority.class,
                AuthService.class,
                AuthAdminAccess.class,
                AuthInternalActorReader.class,
                AuthIdentityOperationStore.class,
                AuthIdentityAuthorityQuery.class,
                AuthAdministrativeAuthorityQuery.class,
                AuthReportCapabilityService.class,
                SemesterDeletionConfirmationService.class,
                AuthPasswordStepUpService.class,
                SubjectDeletionProofService.class,
                AuthCredentialReaderInvalidationStore.class);
        authContext.refresh();
    }

    public void properties(DynamicPropertyRegistry registry, String kind) {
        registry.add("rct.fixture.kind", () -> kind);
        registry.add("spring.datasource.url", academicData::getUrl);
        registry.add("spring.datasource.username", () -> ACADEMIC_RUNTIME);
        registry.add("spring.datasource.password", () -> databasePassword);
        registry.add("spring.flyway.url", academicOwnerData::getUrl);
        registry.add("spring.flyway.user", () -> ACADEMIC_MIGRATOR);
        registry.add("spring.flyway.password", () -> databasePassword);
        registry.add("spring.flyway.locations", () -> "classpath:db/academic-migration");
        registry.add(
                "grpc.client.auth-service.address",
                () -> "static://localhost:" + authServer.getPort());
        registry.add("grpc.client.auth-service.negotiation-type", () -> "TLS");
        registry.add("grpc.client.auth-service.security.authority-override", () -> "localhost");
        registry.add(
                "grpc.client.auth-service.security.trust-cert-collection", certificate::toString);
        registry.add("grpc.service-identity.academic-to-auth-token", () -> academicToAuth);
        registry.add(
                "grpc.service-identity.auth-to-academic-presentation-token",
                () -> presentationToken);
        registry.add("rutcampustrack.security.internal-jwt.legacy-headers-enabled", () -> false);
    }

    public PostgreSQLContainer<?> postgres() {
        return postgres;
    }

    public InternalJwtTestFactory signer() {
        return signer;
    }

    public JdbcTemplate authJdbc() {
        return auth;
    }

    public JdbcTemplate academicJdbc() {
        return academic;
    }

    public JdbcTemplate fixtureOwnerAuthJdbc() {
        return authOwner;
    }

    public JdbcTemplate fixtureOwnerAcademicJdbc() {
        return academicOwner;
    }

    public TransactionTemplate authTransactions() {
        return new TransactionTemplate(authContext.getBean(PlatformTransactionManager.class));
    }

    public TransactionTemplate academicTransactions() {
        return new TransactionTemplate(application().getBean(PlatformTransactionManager.class));
    }

    public CanonicalUsers canonical() {
        return Objects.requireNonNull(canonical, "Fixture has not reset");
    }

    public ManagedChannel authChannel() {
        return authChannel;
    }

    /**
     * Fail only this real client's transport; current session admission remains on the real
     * listener.
     */
    public AutoCloseable unavailableAdminAuthority(AuthIdentityDataClient client) {
        outsideTransaction();
        if (!testLease.isHeldByCurrentThread())
            throw new IllegalStateException("Transport failure requires this fixture's test lease");
        Objects.requireNonNull(client);
        if (application().getBean(AuthIdentityDataClient.class) != client)
            throw new IllegalArgumentException("This fixture's real Auth data client required");
        Object original = ReflectionTestUtils.getField(client, "stub");
        if (original == null) throw new IllegalStateException("Actual Auth data client required");
        if (!dataTransportUnavailable.compareAndSet(false, true))
            throw new IllegalStateException("Nested or concurrent transport failure is forbidden");
        ServerSocket unavailable = null;
        ManagedChannel failedChannel = null;
        try {
            unavailable = new ServerSocket();
            unavailable.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0));
            failedChannel = channel(unavailable.getLocalPort());
            ReflectionTestUtils.setField(
                    client,
                    "stub",
                    ru.rutcampustrack.authauthority.grpc.AuthDataAuthorityServiceGrpc
                            .newBlockingStub(failedChannel));
            var socket = unavailable;
            var failed = failedChannel;
            var restored = new AtomicBoolean();
            return () -> {
                if (!restored.compareAndSet(false, true)) return;
                try {
                    ReflectionTestUtils.setField(client, "stub", original);
                } finally {
                    try {
                        failed.shutdownNow();
                        try {
                            socket.close();
                        } finally {
                            await(null, failed);
                        }
                    } finally {
                        dataTransportUnavailable.set(false);
                    }
                }
            };
        } catch (Exception failure) {
            try {
                ReflectionTestUtils.setField(client, "stub", original);
            } catch (RuntimeException restore) {
                failure.addSuppressed(restore);
            }
            try {
                try {
                    if (failedChannel != null) {
                        failedChannel.shutdownNow();
                        await(null, failedChannel);
                    }
                } finally {
                    if (unavailable != null) unavailable.close();
                }
            } catch (Exception cleanup) {
                failure.addSuppressed(cleanup);
            } finally {
                dataTransportUnavailable.set(false);
            }
            throw new IllegalStateException(
                    "Owned unavailable transport could not be created", failure);
        }
    }

    public synchronized void attach(ApplicationContext context) {
        if (application == context) return;
        if (presentationServer != null) {
            presentationServer.shutdownNow();
            presentationChannel.shutdownNow();
            await(presentationServer, presentationChannel);
        }
        application = context;
        try {
            presentationServer =
                    nettyServer(presentationCalls)
                            .addService(
                                    ServerInterceptors.intercept(
                                            context.getBean(
                                                    AcademicIdentityPresentationGrpcService.class),
                                            new IdentityPresentationServiceIdentityInterceptor(
                                                    presentationToken)))
                            .build()
                            .start();
            presentationChannel = channel(presentationServer.getPort());
            ReflectionTestUtils.setField(
                    authContext.getBean(AcademicIdentityPresentationClient.class),
                    "stub",
                    AcademicIdentityPresentationServiceGrpc.newBlockingStub(presentationChannel));
        } catch (Exception failure) {
            throw new IllegalStateException(
                    "Owned Academic presentation listener unavailable", failure);
        }
    }

    public SelectedActor select(long userId, AuthRole role) {
        outsideTransaction();
        var query = authContext.getBean(JdbcAuthSessionQueryAdapter.class);
        var authority =
                query.findLoginAuthority(userId)
                        .orElseThrow(
                                () -> new IllegalArgumentException("No active fixture identity"));
        String hash =
                auth.queryForObject(
                        "SELECT password_hash FROM auth_identities WHERE id=?",
                        String.class,
                        userId);
        var lifecycle = authContext.getBean(SessionLifecycleService.class);
        var now = Instant.now();
        var created =
                lifecycle.createSession(
                        new SessionLifecycleService.CreateSessionRequest(
                                userId,
                                UUID.randomUUID(),
                                UUID.randomUUID(),
                                now,
                                now.plusSeconds(86400),
                                AuthMethod.PASSWORD,
                                authority.rolesVersion(),
                                authority.grants(),
                                hash,
                                "owned-integration-fixture",
                                namespace));
        if (!created.succeeded())
            throw new IllegalStateException("Actual Auth session creation rejected");
        SessionSnapshot snapshot = created.snapshot();
        var selected =
                lifecycle.selectRole(
                        new SessionLifecycleService.SelectRoleRequest(
                                userId,
                                snapshot.sessionId(),
                                role,
                                snapshot.sessionVersion(),
                                Instant.now(),
                                AuthMethod.PASSWORD,
                                "owned-integration-fixture",
                                namespace));
        if (!selected.succeeded())
            throw new IllegalStateException("Actual Auth role selection rejected");
        snapshot = selected.snapshot();
        var grant = Objects.requireNonNull(snapshot.activeRole());
        var identity =
                StoredSessionIdentity.newBuilder()
                        .setUserId(userId)
                        .setSessionId(snapshot.sessionId().toString())
                        .setSessionVersion(snapshot.sessionVersion())
                        .setRolesVersion(snapshot.rolesVersion())
                        .setRole(grant.role().name())
                        .setStatus(grant.status().name())
                        .setIsHeadman(grant.role() == AuthRole.HEADMAN)
                        .setReadOnly(snapshot.isReadOnly());
        if (grant.groupId() != null) identity.setGroupId(grant.groupId());
        return new SelectedActor(admit(identity.build()));
    }

    private LiveSessionAuthority admit(StoredSessionIdentity identity) {
        outsideTransaction();
        var credential =
                DirectedServiceCredential.tryCreate(
                                ServicePrincipal.ACADEMIC_SERVICE,
                                new ServicePrincipal("auth-service"),
                                academicToAuth)
                        .orElseThrow();
        var admitted =
                security.withCallCredentials(
                                ServiceTokenCallCredentials.forMethod(
                                        credential,
                                        "localhost",
                                        AuthSecurityCapabilityServiceGrpc
                                                .getAdmitSessionIdentityMethod()
                                                .getFullMethodName()))
                        .withDeadlineAfter(3, TimeUnit.SECONDS)
                        .admitSessionIdentity(identity);
        if (!identity.equals(admitted.getIdentity()) || admitted.getInternalToken().isBlank())
            throw new IllegalStateException("Actual Auth returned a different selected identity");
        return admitted;
    }

    public SeededUser seedUser(CreateUserRequest request, SelectedActor actor) {
        outsideTransaction();
        try (var ignored = request(actor, UUID.randomUUID())) {
            var response =
                    application().getBean(UserService.class).createUser(request).getContent();
            if (response == null || response.getId() == null)
                throw new IllegalStateException("Actual create did not return identity");
            // The permitted creation response contains initial material; retain only nonsecret
            // fields.
            return new SeededUser(response.getId(), response.getLogin());
        }
    }

    public void catchUpCatalog() {
        outsideTransaction();
        application().getBean(IdentityCatalogSynchronizer.class).poll();
    }

    public RequestPostProcessor signed(SelectedActor actor) {
        return signed(actor, null);
    }

    public RequestPostProcessor signed(SelectedActor actor, UUID key) {
        Objects.requireNonNull(actor);
        return request -> {
            if (request.getHeader("X-Internal-Token") == null)
                request.addHeader("X-Internal-Token", actor.internalToken());
            if (request.getHeader("X-User-Id") == null)
                request.addHeader("X-User-Id", Long.toString(actor.userId()));
            if (request.getHeader("X-User-Role") == null)
                request.addHeader("X-User-Role", actor.claims().domainRole());
            if (key != null && request.getHeader("Idempotency-Key") == null)
                request.addHeader("Idempotency-Key", key.toString());
            return request;
        };
    }

    public RequestScope request(SelectedActor actor) {
        return request(actor, UUID.randomUUID());
    }

    public RequestScope request(SelectedActor actor, UUID key) {
        outsideTransaction();
        var request = new MockHttpServletRequest("POST", "/academic/fixture-service-call");
        request.setServletPath("/academic/fixture-service-call");
        signed(actor, key).postProcessRequest(request);
        var response = new MockHttpServletResponse();
        var previous = RequestContextHolder.getRequestAttributes();
        var attributes = new ServletRequestAttributes(request, response);
        RequestContextHolder.setRequestAttributes(attributes);
        var accepted = new AtomicBoolean();
        try {
            application()
                    .getBean(AcademicUserContextFilter.class)
                    .doFilter(
                            request,
                            response,
                            (acceptedRequest, acceptedResponse) -> accepted.set(true));
            if (!accepted.get())
                throw new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatusCode.valueOf(response.getStatus()),
                        "Actual Academic admission rejected");
            return new RequestScope(previous, attributes, request, response);
        } catch (Exception failure) {
            attributes.requestCompleted();
            restore(previous);
            if (failure instanceof RuntimeException runtime) throw runtime;
            throw new IllegalStateException("Actual Academic request admission failed", failure);
        }
    }

    /** Only this fixture's own two synthetic databases may be reset. */
    public void beforeTest(ApplicationContext context) {
        testLease.lock();
        try {
            attach(context);
            assertOwned(academicOwnerData, ACADEMIC_DB, academicOid, academicTables);
            assertOwned(authOwnerData, AUTH_DB, authOid, authTables);
            truncate(academicOwner, academicTables);
            truncate(authOwner, authTables);
            academicOwner.update(
                    "INSERT INTO"
                            + " academic_identity_catalog_cursor(singleton,applied_catalog_version)"
                            + " VALUES(TRUE,0)");
            academicOwner.update(
                    "INSERT INTO campus_map_activity_coverage(singleton) VALUES(TRUE)");
            authOwner.update(
                    "INSERT INTO auth_identity_catalog_clock(singleton,version) VALUES(1,0)");
            resetAuthRedis();
            seedCanonical();
        } catch (RuntimeException failure) {
            testLease.unlock();
            throw failure;
        }
    }

    public void afterTest() {
        RequestContextHolder.resetRequestAttributes();
        if (testLease.isHeldByCurrentThread()) testLease.unlock();
    }

    /** Uses the real Auth outbox storage/sender; Event base supplies its actual owned Rabbit. */
    public void flushAuthOutbox() {
        outsideTransaction();
        var storage = authOutboxStorage();
        var sender =
                new ru.rutcampustrack.auth.event.RabbitOutboxEventSender(
                        application()
                                .getBean(
                                        org.springframework.amqp.rabbit.core.RabbitTemplate.class));
        var publisher =
                new ru.rutcampustrack.auth.event.AuthOutboxPublisherJob(storage, sender, null);
        authTransactions().executeWithoutResult(status -> publisher.publishBatch());
    }

    public void drainAuthSetupOutbox() {
        outsideTransaction();
        var storage = authOutboxStorage();
        authTransactions()
                .executeWithoutResult(
                        status ->
                                storage.findPending(1000)
                                        .forEach(row -> storage.markSent(row.id())));
    }

    private ru.rutcampustrack.shared.outbox.jpa.JpaOutboxStorage<
                    ru.rutcampustrack.auth.event.AuthOutboxEntity>
            authOutboxStorage() {
        var storage =
                new ru.rutcampustrack.shared.outbox.jpa.JpaOutboxStorage<>(
                        ru.rutcampustrack.auth.event.AuthOutboxEntity.class);
        storage.setEntityManager(
                SharedEntityManagerCreator.createSharedEntityManager(authEntities.getObject()));
        return storage;
    }

    private void seedCanonical() {
        long adminId = bootstrapFirstFull();
        academicOwner.update(
                """
INSERT INTO academic_profiles(id,directory_role,directory_status,last_name,first_name,middle_name,
    academic_revision,applied_auth_roles_version,created_at,updated_at)
VALUES(?,'ADMIN','ACTIVE','Администратов','Админ','Системович',1,1,NOW(),NOW())
""",
                adminId);
        catchUpCatalog();
        long group =
                academicOwner.queryForObject(
                        """
INSERT INTO groups(name,is_active,alphabetic_code,numeric_code,current_course,training_duration_years,duration_status)
VALUES('ИВТ-211',TRUE,'ИВТ','211',2,4,'KNOWN') RETURNING id
""",
                        Long.class);
        long semester =
                academicOwner.queryForObject(
                        """
INSERT INTO semesters(name,date_from,date_to,is_active,semester_type,academic_year)
VALUES('Spring 2026','2026-02-01','2026-06-30',TRUE,'SPRING',2025) RETURNING id
""",
                        Long.class);
        academicOwner.update(
                "INSERT INTO"
                    + " group_history_coverage(group_id,coverage_from,writer_version,created_at)"
                    + " VALUES(?,'2026-02-01',?,NOW())",
                group,
                ru.rutcampustrack.academic.history.HistoricalMembershipService.WRITER_VERSION);
        academicOwner.update(
                "INSERT INTO campus_settings(name,lat,lng,radius_m) VALUES('Main"
                        + " Campus',55.788204,37.606762,200)");
        SelectedActor firstFull = select(adminId, AuthRole.ADMIN);
        var teacher =
                seedUser(
                        new CreateUserRequest(
                                "Преподавателев",
                                "Учитель",
                                "Знаниевич",
                                UserRole.TEACHER,
                                null,
                                "T00001",
                                null),
                        firstFull);
        var student =
                seedUser(
                        new CreateUserRequest(
                                "Студентов",
                                "Студент",
                                "Староста",
                                UserRole.STUDENT,
                                group,
                                null,
                                610000000003L),
                        firstFull);
        try (var ignored = request(firstFull)) {
            application()
                    .getBean(AcademicUserIdentityOperations.class)
                    .headman(group, student.userId(), null, false, null);
        }
        catchUpCatalog();
        canonical =
                new CanonicalUsers(adminId, teacher.userId(), student.userId(), group, semester);
    }

    /**
     * Explicit first fixture administrator only; all target identities use the real coordinator.
     */
    private long bootstrapFirstFull() {
        String hash = new BCryptPasswordEncoder().encode(FIRST_FULL_TEST_PASSWORD);
        return new TransactionTemplate(new DataSourceTransactionManager(authOwnerData))
                .execute(
                        status -> {
                            UUID operation = UUID.randomUUID();
                            authOwner.update(
                                    """
INSERT INTO auth_identity_operations(operation_id,command_hash,canonical_command,kind,authorization_kind,
    actor_id,actor_session_id,actor_session_version,actor_roles_version,actor_credentials_version,
    actor_binding_version,actor_commit_roles_version,actor_admin_profile_version)
VALUES(?,decode(repeat('00',32),'hex'),decode('00','hex'),'CREATE','CREATE',1,?,1,1,1,1,1,1)
""",
                                    operation,
                                    UUID.randomUUID());
                            long id =
                                    authOwner.queryForObject(
                                            """
INSERT INTO auth_identities(login,password_hash,initial_password,account_state,provision_operation_id)
VALUES('admin',?,NULL,'ACTIVE',?) RETURNING id
""",
                                            Long.class,
                                            hash,
                                            operation);
                            authOwner.update(
                                    "INSERT INTO user_role_grants(user_id,role,status)"
                                            + " VALUES(?,'admin','active')",
                                    id);
                            authOwner.update(
                                    "INSERT INTO"
                                        + " admin_access_profiles(user_id,assigned,mode,permissions,version)"
                                        + " VALUES(?,TRUE,'FULL','{}',1)",
                                    id);
                            authOwner.update(
                                    """
INSERT INTO auth_identity_operation_targets(operation_id,user_id,expected_roles_version,final_roles_version,
    expected_credentials_version,prepared_credentials_version,expected_binding_version,prepared_binding_version,
    final_binding_version,expected_academic_revision,committed_academic_revision,
    desired_grants,desired_admin_profile,desired_account_state)
SELECT ?,id,0,1,credentials_version,credentials_version,telegram_binding_version,telegram_binding_version,
    telegram_binding_version,0,1,'[]'::jsonb,'{}'::jsonb,'ACTIVE' FROM auth_identities WHERE id=?
""",
                                    operation,
                                    id);
                            authOwner.update(
                                    "UPDATE auth_identity_operations SET"
                                        + " phase='COMMITTED',revision=revision+1,finished_at=NOW(),committed_catalog_version=1"
                                        + " WHERE operation_id=?",
                                    operation);
                            // The bootstrap transaction must emit a real final catalog projection
                            // after its
                            // committed receipt/grants/profile exist; never fabricate the Academic
                            // read model.
                            authOwner.update(
                                    "UPDATE auth_identities SET"
                                            + " telegram_username='fixture-bootstrap' WHERE id=?",
                                    id);
                            authOwner.update(
                                    "UPDATE auth_identities SET telegram_username=NULL WHERE id=?",
                                    id);
                            return id;
                        });
    }

    private void assertOwned(
            DriverManagerDataSource source, String db, long oid, List<String> inventory) {
        if (!postgres.isRunning()
                || !containerId.equals(postgres.getContainerId())
                || !namespace.equals(postgres.getLabels().get("rct.fixture.owner")))
            throw new IllegalStateException("Fixture resource owner changed");
        var jdbc = new JdbcTemplate(source);
        try (var connection = source.getConnection()) {
            String expected = postgres.getJdbcUrl().replace("/academic_db", "/" + db);
            if (!expected.equals(source.getUrl())
                    || !expected.equals(connection.getMetaData().getURL())
                    || !db.equals(connection.getCatalog())
                    || databaseOid(jdbc) != oid
                    || !tables(jdbc).equals(inventory))
                throw new IllegalStateException(
                        "Only the exact owned fresh synthetic database may reset");
        } catch (java.sql.SQLException failure) {
            throw new IllegalStateException("Owned fixture connection unavailable", failure);
        }
    }

    private static void truncate(JdbcTemplate owner, List<String> tables) {
        if (tables.isEmpty() || tables.stream().anyMatch(name -> !name.matches("[a-z][a-z0-9_]*")))
            throw new IllegalStateException("Invalid owned application table inventory");
        owner.execute(
                "TRUNCATE "
                        + tables.stream()
                                .map(name -> "public.\"" + name + "\"")
                                .collect(java.util.stream.Collectors.joining(","))
                        + " RESTART IDENTITY");
    }

    private void resetAuthRedis() {
        if (!authRedis.isRunning()
                || !redisContainerId.equals(authRedis.getContainerId())
                || !namespace.equals(authRedis.getLabels().get("rct.fixture.owner")))
            throw new IllegalStateException(
                    "Critical Auth Redis must be this fixture's separate owned process");
        authContext
                .getBean(StringRedisTemplate.class)
                .execute(
                        (org.springframework.data.redis.core.RedisCallback<Void>)
                                connection -> {
                                    connection.serverCommands().flushDb();
                                    return null;
                                });
    }

    private static List<String> tables(JdbcTemplate jdbc) {
        return List.copyOf(
                jdbc.queryForList(
                        "SELECT tablename FROM pg_tables WHERE schemaname='public'"
                                + " AND tablename<>'flyway_schema_history' ORDER BY tablename",
                        String.class));
    }

    private static long databaseOid(JdbcTemplate jdbc) {
        return jdbc.queryForObject(
                "SELECT oid FROM pg_database WHERE datname=current_database()", Long.class);
    }

    private static void assertIsolatedRuntime(JdbcTemplate jdbc, String db, String role) {
        var row =
                jdbc.queryForMap(
                        "SELECT current_database() db,current_user"
                            + " principal,rolsuper,rolbypassrls,rolcreatedb,rolcreaterole,rolreplication"
                            + " FROM pg_roles WHERE rolname=current_user");
        if (!db.equals(row.get("db"))
                || !role.equals(row.get("principal"))
                || Boolean.TRUE.equals(row.get("rolsuper"))
                || Boolean.TRUE.equals(row.get("rolbypassrls"))
                || Boolean.TRUE.equals(row.get("rolcreatedb"))
                || Boolean.TRUE.equals(row.get("rolcreaterole"))
                || Boolean.TRUE.equals(row.get("rolreplication"))
                || !Boolean.TRUE.equals(
                        jdbc.queryForObject(
                                """
SELECT NOT has_schema_privilege(current_user,'public','CREATE')
  AND NOT has_database_privilege(current_user,current_database(),'CREATE')
  AND NOT has_database_privilege(current_user,?,'CONNECT')
  AND NOT has_table_privilege(current_user,'public.flyway_schema_history','SELECT,INSERT,UPDATE,DELETE')
""",
                                Boolean.class,
                                db.equals(AUTH_DB) ? ACADEMIC_DB : AUTH_DB)))
            throw new IllegalStateException("Actual own-DB runtime principal required");
    }

    private ApplicationContext application() {
        return Objects.requireNonNull(application, "Fixture is not attached to Academic");
    }

    private static void outsideTransaction() {
        if (TransactionSynchronizationManager.isActualTransactionActive())
            throw new IllegalStateException(
                    "Actual Auth calls cannot run under an Academic test transaction");
    }

    private static void restore(RequestAttributes previous) {
        if (previous == null) RequestContextHolder.resetRequestAttributes();
        else RequestContextHolder.setRequestAttributes(previous);
    }

    public record SeededUser(long userId, String login) {}

    public record CanonicalUsers(
            long adminId, long teacherId, long studentId, long groupId, long semesterId) {}

    public static final class SelectedActor {
        private final StoredSessionIdentity identity;
        private final InternalJwtClaims claims;
        private final AdminAccessProfile profile;
        private final String token;

        private SelectedActor(LiveSessionAuthority admitted) {
            identity = admitted.getIdentity();
            claims =
                    new InternalJwtClaims(
                            identity.getUserId(),
                            UUID.fromString(identity.getSessionId()),
                            identity.getSessionVersion(),
                            identity.getRolesVersion(),
                            identity.getRole(),
                            identity.getStatus(),
                            identity.hasGroupId() ? identity.getGroupId() : null,
                            identity.getIsHeadman(),
                            identity.getReadOnly());
            var p = admitted.getAdminProfile();
            profile =
                    new AdminAccessProfile(
                            userId(),
                            p.getAssigned(),
                            p.getMode(),
                            p.getPermissionsList(),
                            p.getVersion());
            token = admitted.getInternalToken();
        }

        public long userId() {
            return identity.getUserId();
        }

        public UUID sessionId() {
            return UUID.fromString(identity.getSessionId());
        }

        public long sessionVersion() {
            return identity.getSessionVersion();
        }

        public long rolesVersion() {
            return identity.getRolesVersion();
        }

        public AuthRole role() {
            return AuthRole.valueOf(identity.getRole());
        }

        public InternalJwtClaims claims() {
            return claims;
        }

        public AdminAccessProfile profile() {
            return profile;
        }

        public String internalToken() {
            return token;
        }

        public StoredSessionIdentity identity() {
            return identity;
        }

        @Override
        public String toString() {
            return "SelectedActor[userId=" + userId() + ",role=" + role() + ",bearer=redacted]";
        }
    }

    public static final class RequestScope implements AutoCloseable {
        private final RequestAttributes previous;
        private final ServletRequestAttributes attributes;
        private final MockHttpServletRequest request;
        private final MockHttpServletResponse response;
        private boolean closed;

        private RequestScope(
                RequestAttributes previous,
                ServletRequestAttributes attributes,
                MockHttpServletRequest request,
                MockHttpServletResponse response) {
            this.previous = previous;
            this.attributes = attributes;
            this.request = request;
            this.response = response;
        }

        public MockHttpServletRequest servletRequest() {
            return request;
        }

        public MockHttpServletResponse servletResponse() {
            return response;
        }

        @Override
        public void close() {
            if (!closed) {
                closed = true;
                attributes.requestCompleted();
                restore(previous);
            }
        }
    }

    private void writeSigningKeys() throws Exception {
        Path directory = Files.createDirectory(temporary.resolve("jwt"));
        Files.writeString(
                directory.resolve("private.key"),
                pem("PRIVATE KEY", signer.keyPair().getPrivate().getEncoded()));
        Files.writeString(
                directory.resolve("public.key"),
                pem("PUBLIC KEY", signer.publicKey().getEncoded()));
        Files.writeString(directory.resolve("kid.txt"), InternalJwtTestFactory.KEY_ID);
    }

    private static String pem(String type, byte[] data) {
        return "-----BEGIN "
                + type
                + "-----\n"
                + Base64.getMimeEncoder(64, new byte[] {'\n'}).encodeToString(data)
                + "\n-----END "
                + type
                + "-----\n";
    }

    private NettyServerBuilder nettyServer(ExecutorService executor) throws Exception {
        var keys = KeyStore.getInstance("PKCS12");
        try (var input = Files.newInputStream(temporary.resolve("server.p12"))) {
            keys.load(input, "fixture-test-only".toCharArray());
        }
        var manager = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        manager.init(keys, "fixture-test-only".toCharArray());
        return NettyServerBuilder.forAddress(
                        new InetSocketAddress(InetAddress.getLoopbackAddress(), 0))
                .executor(executor)
                .bossEventLoopGroup(executor == authCalls ? authBoss : presentationBoss)
                .workerEventLoopGroup(executor == authCalls ? authWorkers : presentationWorkers)
                .channelType(io.netty.channel.socket.nio.NioServerSocketChannel.class)
                .sslContext(
                        GrpcSslContexts.configure(
                                        io.netty.handler.ssl.SslContextBuilder.forServer(manager))
                                .build());
    }

    private ManagedChannel channel(int port) throws Exception {
        return NettyChannelBuilder.forAddress("localhost", port)
                .overrideAuthority("localhost")
                .executor(clientCalls)
                .eventLoopGroup(clientLoops)
                .channelType(io.netty.channel.socket.nio.NioSocketChannel.class)
                .sslContext(GrpcSslContexts.forClient().trustManager(certificate.toFile()).build())
                .build();
    }

    private static Path certificate(Path directory) throws Exception {
        Path keytool = Path.of(System.getProperty("java.home"), "bin", "keytool");
        run(
                keytool.toString(),
                "-genkeypair",
                "-alias",
                "fixture",
                "-keyalg",
                "RSA",
                "-keysize",
                "2048",
                "-validity",
                "2",
                "-dname",
                "CN=localhost",
                "-ext",
                "SAN=dns:localhost,ip:127.0.0.1",
                "-storetype",
                "PKCS12",
                "-keystore",
                directory.resolve("server.p12").toString(),
                "-storepass",
                "fixture-test-only",
                "-keypass",
                "fixture-test-only");
        Path certificate = directory.resolve("server.pem");
        run(
                keytool.toString(),
                "-exportcert",
                "-rfc",
                "-alias",
                "fixture",
                "-keystore",
                directory.resolve("server.p12").toString(),
                "-storepass",
                "fixture-test-only",
                "-file",
                certificate.toString());
        return certificate;
    }

    private static void run(String... args) throws Exception {
        Process process =
                new ProcessBuilder(args)
                        .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                        .redirectError(ProcessBuilder.Redirect.DISCARD)
                        .start();
        if (!process.waitFor(30, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            process.waitFor(5, TimeUnit.SECONDS);
            throw new IllegalStateException("Owned fixture certificate setup timed out");
        }
        if (process.exitValue() != 0)
            throw new IllegalStateException("Owned fixture certificate setup failed");
    }

    private static void loopback(GenericContainer<?> resource, int port) {
        resource.withCreateContainerCmdModifier(
                command ->
                        command.getHostConfig()
                                .withPortBindings(
                                        new PortBinding(
                                                Ports.Binding.bindIpAndPort("127.0.0.1", 0),
                                                new ExposedPort(port))));
    }

    private static Path repositoryRoot() {
        Path path = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        while (path != null) {
            if (Files.isRegularFile(path.resolve("proto/academic.proto"))
                    && Files.isDirectory(
                            path.resolve(
                                    "services/auth-service/auth-app/src/main/resources/db/auth-migration")))
                return path;
            path = path.getParent();
        }
        throw new IllegalStateException("Own migration source directories unavailable");
    }

    private static String randomToken() {
        byte[] bytes = new byte[32];
        new java.security.SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static void await(Server server, ManagedChannel channel) {
        try {
            if (server != null && !server.awaitTermination(10, TimeUnit.SECONDS))
                throw new IllegalStateException("Owned server did not terminate");
            if (channel != null && !channel.awaitTermination(10, TimeUnit.SECONDS))
                throw new IllegalStateException("Owned channel did not terminate");
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(interrupted);
        }
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) return;
        List<Throwable> failures = new java.util.ArrayList<>();
        clean(
                failures,
                () -> {
                    if (presentationServer != null) presentationServer.shutdownNow();
                });
        clean(
                failures,
                () -> {
                    if (presentationChannel != null) presentationChannel.shutdownNow();
                });
        clean(
                failures,
                () -> {
                    if (authServer != null) authServer.shutdownNow();
                });
        clean(
                failures,
                () -> {
                    if (authChannel != null) authChannel.shutdownNow();
                });
        clean(failures, () -> await(presentationServer, presentationChannel));
        clean(failures, () -> await(authServer, authChannel));
        clean(failures, authContext::close);
        clean(
                failures,
                () -> {
                    if (entitiesInitialized) authEntities.destroy();
                });
        authCalls.shutdownNow();
        presentationCalls.shutdownNow();
        clientCalls.shutdownNow();
        for (var loops :
                List.of(authBoss, authWorkers, presentationBoss, presentationWorkers, clientLoops))
            clean(
                    failures,
                    () ->
                            loops.shutdownGracefully(0, 5, TimeUnit.SECONDS)
                                    .awaitUninterruptibly(10, TimeUnit.SECONDS));
        clean(
                failures,
                () -> {
                    if (authRedis.isRunning()) authRedis.stop();
                });
        clean(
                failures,
                () -> {
                    if (postgres.isRunning()) postgres.stop();
                });
        clean(
                failures,
                () -> {
                    try (var files = Files.walk(temporary)) {
                        for (var file : files.sorted(java.util.Comparator.reverseOrder()).toList())
                            Files.delete(file);
                    } catch (java.io.IOException failure) {
                        throw new IllegalStateException(
                                "Owned fixture temporary files could not be removed", failure);
                    }
                });
        if (!failures.isEmpty()) {
            var failure = new IllegalStateException("Owned fixture cleanup failed");
            failures.forEach(failure::addSuppressed);
            throw failure;
        }
    }

    private static void clean(List<Throwable> failures, Runnable action) {
        try {
            action.run();
        } catch (RuntimeException failure) {
            failures.add(failure);
        }
    }
}
