package ru.rutcampustrack.academic.botcredentials;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;

import io.grpc.Server;
import io.grpc.ServerInterceptors;
import io.grpc.netty.GrpcSslContexts;
import io.grpc.netty.NettyServerBuilder;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.SharedEntityManagerCreator;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;

import ru.rutcampustrack.academic.grpc.AcademicGrpcServiceImpl;
import ru.rutcampustrack.academic.grpc.AcademicReadService;
import ru.rutcampustrack.academic.grpc.GrpcAuthInterceptor;
import ru.rutcampustrack.academic.grpc.GrpcUserAuthorityReader;
import ru.rutcampustrack.academic.repository.GroupRepository;
import ru.rutcampustrack.academic.repository.HomeworkCompletionRepository;
import ru.rutcampustrack.academic.repository.HomeworkRepository;
import ru.rutcampustrack.academic.repository.SemesterRepository;
import ru.rutcampustrack.academic.repository.SubjectRepository;
import ru.rutcampustrack.academic.repository.UserRepository;
import ru.rutcampustrack.auth.botcredentials.AuthBotCredentialServiceIdentityInterceptor;
import ru.rutcampustrack.auth.botcredentials.AuthBotCredentialsGrpcService;
import ru.rutcampustrack.auth.botcredentials.AuthBotOwnInitialCredentialsQuery;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.KeyManagerFactory;

/**
 * Actual Academic/JPA/JDBC/TLS → Python client/Dispatcher/provider. Auth producer is tested
 * separately.
 */
class BotCredentialPythonBridgeIT {
    private final ObjectMapper mapper = new ObjectMapper();

    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement(proxyTargetClass = true)
    static class Transactions {}

    @Test
    void actualAcademicTlsOwnCardsReachPythonDispatcherAndExactProviderCleanup(
            @TempDir Path temporary) throws Exception {
        String python = System.getenv("NP01_PYTHON_EXECUTABLE");
        if (python == null || !Files.isExecutable(Path.of(python))) {
            throw new AssertionError(
                    "explicit pinned NP01 Python interpreter required; no skip or implicit"
                            + " environment fallback");
        }
        Path root = repositoryRoot();
        String serviceToken = token();
        String authServiceToken = token();
        assertThat(authServiceToken).isNotEqualTo(serviceToken);
        String sharedSecret = UUID.randomUUID().toString();
        String controlToken = token();
        try (var postgres =
                new PostgreSQLContainer<>("postgres:16-alpine")
                        .withDatabaseName("rct_np01_bridge")
                        .withUsername("rct_np01_bridge")
                        .withPassword("rct_np01_disposable")
                        .withReuse(false)) {
            postgres.start();
            var split = new BotCredentialLifecycleIT.SplitDatabases(postgres, root);
            var jdbc = split.auth;
            Map<String, Map<String, Object>> fixtures = fixtures(split);
            try (var services =
                    new TlsServices(
                            split, temporary, serviceToken, authServiceToken, sharedSecret)) {
                Server server = services.academicServer;
                Server authServer = services.authServer;
                Path ca = services.ca;
                try {
                    var control = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
                    var controlWorkers = Executors.newFixedThreadPool(2);
                    try {
                        control.setExecutor(controlWorkers);
                        var tx =
                                new TransactionTemplate(
                                        new DataSourceTransactionManager(split.authData));
                        long own = ((Number) fixtures.get("student").get("uid")).longValue();
                        long foreign = ((Number) fixtures.get("foreign").get("uid")).longValue();
                        long ownChat = ((Number) fixtures.get("student").get("chat")).longValue();
                        long foreignChat =
                                ((Number) fixtures.get("foreign").get("chat")).longValue();
                        control.createContext(
                                "/control/",
                                exchange -> {
                                    byte[] supplied =
                                            String.valueOf(
                                                            exchange.getRequestHeaders()
                                                                    .getFirst("x-np01-control"))
                                                    .getBytes(StandardCharsets.UTF_8);
                                    if (!"POST".equals(exchange.getRequestMethod())
                                            || !MessageDigest.isEqual(
                                                    supplied,
                                                    controlToken.getBytes(
                                                            StandardCharsets.UTF_8))) {
                                        exchange.sendResponseHeaders(403, -1);
                                        exchange.close();
                                        return;
                                    }
                                    String action =
                                            exchange.getRequestURI()
                                                    .getPath()
                                                    .substring("/control/".length());
                                    try {
                                        if (action.equals("revoke") || action.equals("reactivate"))
                                            split.studentGrant(own, action.equals("reactivate"));
                                        Map<String, Object> facts =
                                                tx.execute(
                                                        status -> {
                                                            jdbc.queryForObject(
                                                                    "SELECT id FROM auth_identities"
                                                                        + " WHERE id=? FOR UPDATE",
                                                                    Long.class,
                                                                    own);
                                                            switch (action) {
                                                                case "change" ->
                                                                        jdbc.update(
                                                                                "UPDATE"
                                                                                    + " auth_identities"
                                                                                    + " SET password_hash=?,password_changed=true,initial_password=NULL"
                                                                                    + " WHERE id=?",
                                                                                BotCredentialLifecycleIT
                                                                                        .REPLACEMENT_HASH,
                                                                                own);
                                                                case "new_initial" ->
                                                                        jdbc.update(
                                                                                "UPDATE"
                                                                                    + " auth_identities"
                                                                                    + " SET password_hash=?,password_changed=false,initial_password=?"
                                                                                    + " WHERE id=?",
                                                                                BotCredentialLifecycleIT
                                                                                        .PASSWORD_HASH,
                                                                                UUID.randomUUID()
                                                                                        .toString(),
                                                                                own);
                                                                case "rebind_foreign" -> {
                                                                    jdbc.update(
                                                                            "UPDATE auth_identities"
                                                                                + " SET telegram_id=NULL"
                                                                                + " WHERE id=?",
                                                                            own);
                                                                    jdbc.update(
                                                                            "UPDATE auth_identities"
                                                                                + " SET telegram_id=?"
                                                                                + " WHERE id=?",
                                                                            ownChat,
                                                                            foreign);
                                                                }
                                                                case "restore_binding" -> {
                                                                    jdbc.update(
                                                                            "UPDATE auth_identities"
                                                                                + " SET telegram_id=?"
                                                                                + " WHERE id=?",
                                                                            foreignChat,
                                                                            foreign);
                                                                    jdbc.update(
                                                                            "UPDATE auth_identities"
                                                                                + " SET telegram_id=?"
                                                                                + " WHERE id=?",
                                                                            ownChat,
                                                                            own);
                                                                }
                                                                case "revoke", "reactivate" -> {}
                                                                case "archive" ->
                                                                        jdbc.update(
                                                                                "UPDATE"
                                                                                    + " auth_identities"
                                                                                    + " SET account_state='ARCHIVED'"
                                                                                    + " WHERE id=?",
                                                                                own);
                                                                case "facts", "verify" -> {}
                                                                default ->
                                                                        throw new IllegalArgumentException(
                                                                                "unknown isolated"
                                                                                    + " fixture"
                                                                                    + " control");
                                                            }
                                                            var versions =
                                                                    jdbc.queryForMap(
                                                                            "SELECT"
                                                                                + " credentials_version,telegram_binding_version,roles_version"
                                                                                + " FROM"
                                                                                + " auth_identities"
                                                                                + " WHERE id=?",
                                                                            own);
                                                            versions.put(
                                                                    "pending",
                                                                    jdbc.queryForObject(
                                                                            "SELECT count(*) FROM"
                                                                                + " bot_credential_messages"
                                                                                + " WHERE user_id=?"
                                                                                + " AND cleanup_state='PENDING'",
                                                                            Long.class,
                                                                            own));
                                                            if (action.equals("verify")) {
                                                                String[] ids =
                                                                        exchange.getRequestURI()
                                                                                .getQuery()
                                                                                .split("&");
                                                                long chat =
                                                                        Long.parseLong(
                                                                                ids[0].substring(
                                                                                        "chat="
                                                                                                .length()));
                                                                long message =
                                                                        Long.parseLong(
                                                                                ids[1].substring(
                                                                                        "message="
                                                                                                .length()));
                                                                versions.put(
                                                                        "registered",
                                                                        jdbc.queryForObject(
                                                                                "SELECT"
                                                                                    + " EXISTS(SELECT"
                                                                                    + " 1 FROM"
                                                                                    + " bot_credential_messages"
                                                                                    + " WHERE"
                                                                                    + " private_chat_id=?"
                                                                                    + " AND message_id=?)",
                                                                                Boolean.class,
                                                                                chat,
                                                                                message));
                                                            }
                                                            return versions;
                                                        });
                                        byte[] response = mapper.writeValueAsBytes(facts);
                                        exchange.getResponseHeaders()
                                                .set("Content-Type", "application/json");
                                        exchange.sendResponseHeaders(200, response.length);
                                        exchange.getResponseBody().write(response);
                                    } catch (RuntimeException failure) {
                                        exchange.sendResponseHeaders(500, -1);
                                    } finally {
                                        exchange.close();
                                    }
                                });
                        control.start();
                        for (String branch : List.of("--own-homework", "")) {
                            var process =
                                    new ProcessBuilder(
                                            python,
                                            root.resolve(
                                                            "services/notification-bot/tests/np01_bridge_runner.py")
                                                    .toString());
                            if (!branch.isEmpty()) process.command().add(branch);
                            process.directory(
                                    temporary.toFile()); // No .env/private config discovery.
                            process.redirectError(ProcessBuilder.Redirect.DISCARD);
                            var environment = process.environment();
                            environment.put(
                                    "PYTHONPATH",
                                    root.resolve("services/notification-bot").toString());
                            environment.put("PYTHONDONTWRITEBYTECODE", "1");
                            environment.put("NP01_AUTHORITY", "localhost:" + server.getPort());
                            environment.put("NP01_CA", ca.toString());
                            environment.put(
                                    "NP01_AUTH_SERVICE_AUTHORITY",
                                    "localhost:" + authServer.getPort());
                            environment.put("NP01_AUTH_SERVICE_CA", ca.toString());
                            environment.put("NP01_AUTH_SERVICE_TOKEN", authServiceToken);
                            environment.put("NP01_SERVICE_TOKEN", serviceToken);
                            environment.put("NP01_SHARED_SECRET", sharedSecret);
                            environment.put("NP01_CONTROL_TOKEN", controlToken);
                            environment.put(
                                    "NP01_CONTROL_URL",
                                    "http://127.0.0.1:" + control.getAddress().getPort());
                            environment.put("NP01_FIXTURES", mapper.writeValueAsString(fixtures));
                            Process child = process.start();
                            if (!child.waitFor(90, TimeUnit.SECONDS)) {
                                child.destroyForcibly();
                                child.waitFor(5, TimeUnit.SECONDS);
                                throw new AssertionError("NP01 native Python bridge timed out");
                            }
                            var output = child.getInputStream().readAllBytes();
                            boolean qualified;
                            BridgePhase phase = BridgePhase.OTHER;
                            try {
                                var result = mapper.readTree(output);
                                phase = BridgePhase.from(result.path("phase"));
                                qualified =
                                        result.path("qualified").asBoolean(false)
                                                && result.path("actual_tls").asBoolean(false)
                                                && result.path("real_academic_pg").asBoolean(false)
                                                && result.path("failures").asInt(-1) == 0
                                                && (!branch.isEmpty()
                                                        || (result.path("actual_auth_tls")
                                                                        .asBoolean(false)
                                                                && result.path(
                                                                                "old_academic_denied")
                                                                        .asBoolean(false)
                                                                && result.path(
                                                                                "old_credential_denied")
                                                                        .asBoolean(false)));
                            } catch (Exception invalid) {
                                throw new AssertionError(
                                        "NP01 bridge did not return safe qualified outcomes");
                            }
                            String branchName = branch.isEmpty() ? "CREDENTIALS" : "OWN_HOMEWORK";
                            assertThat(child.exitValue())
                                    .as("NP01_BRIDGE branch=%s phase=%s", branchName, phase)
                                    .isZero();
                            assertThat(qualified)
                                    .as("NP01_BRIDGE branch=%s phase=%s", branchName, phase)
                                    .isTrue();
                        }
                        assertThat(
                                        jdbc.queryForObject(
                                                "SELECT count(*) FROM bot_credential_messages WHERE"
                                                        + " user_id=? AND cleanup_state='PENDING'",
                                                Long.class,
                                                own))
                                .isZero();
                    } finally {
                        control.stop(0);
                        controlWorkers.shutdownNow();
                        assertThat(controlWorkers.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
                    }
                } finally {
                    authServer.shutdownNow();
                    server.shutdownNow();
                    assertThat(authServer.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
                    assertThat(server.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
                }
            }
        }
    }

    /** Only fixed runner phase literals may appear in a native assertion diagnostic. */
    private enum BridgePhase {
        SETUP,
        REAL_TLS_DIRECTED_AUTH_BOUNDARIES,
        OWN_ROLES_NATIVE_DISPATCHER,
        PRIVACY_BEFORE_ANY_LOOKUP,
        FOREIGN_UID_DENIAL,
        ORIGINAL_UID_CLEANUP_AFTER_REBIND,
        EVENT_BEFORE_REGISTER,
        LATE_EDIT_AFTER_EXACT_DELETE,
        UNCERTAIN_OUTCOME_PRESERVES_DURABLE_RETRY,
        SETTLED_SANITIZE_FALLBACK,
        OLD_EVENT_CANNOT_REMOVE_LATER_CARD,
        GRANT_REVOCATION_NO_NEW_DELIVERY,
        ARCHIVE_PRESERVES_EXACT_OLD_CARD_CLEANUP,
        OWN_HOMEWORK_PROFILE,
        OWN_HOMEWORK_HANDLERS,
        OWN_HOMEWORK_TRANSPORT_DENIALS,
        OWN_HOMEWORK_REVOKED_GRANT,
        OWN_HOMEWORK_REBOUND_TELEGRAM,
        COMPLETE,
        OTHER;

        static BridgePhase from(JsonNode value) {
            if (!value.isTextual()) return OTHER;
            return switch (value.textValue()) {
                case "setup" -> SETUP;
                case "real_tls_directed_auth_boundaries" -> REAL_TLS_DIRECTED_AUTH_BOUNDARIES;
                case "own_roles_native_dispatcher" -> OWN_ROLES_NATIVE_DISPATCHER;
                case "privacy_before_any_lookup" -> PRIVACY_BEFORE_ANY_LOOKUP;
                case "foreign_uid_denial" -> FOREIGN_UID_DENIAL;
                case "original_uid_cleanup_after_rebind" -> ORIGINAL_UID_CLEANUP_AFTER_REBIND;
                case "event_before_register" -> EVENT_BEFORE_REGISTER;
                case "late_edit_after_exact_delete" -> LATE_EDIT_AFTER_EXACT_DELETE;
                case "uncertain_outcome_preserves_durable_retry" ->
                        UNCERTAIN_OUTCOME_PRESERVES_DURABLE_RETRY;
                case "settled_sanitize_fallback" -> SETTLED_SANITIZE_FALLBACK;
                case "old_event_cannot_remove_later_card" -> OLD_EVENT_CANNOT_REMOVE_LATER_CARD;
                case "grant_revocation_no_new_delivery" -> GRANT_REVOCATION_NO_NEW_DELIVERY;
                case "archive_preserves_exact_old_card_cleanup" ->
                        ARCHIVE_PRESERVES_EXACT_OLD_CARD_CLEANUP;
                case "own_homework_profile" -> OWN_HOMEWORK_PROFILE;
                case "own_homework_handlers" -> OWN_HOMEWORK_HANDLERS;
                case "own_homework_transport_denials" -> OWN_HOMEWORK_TRANSPORT_DENIALS;
                case "own_homework_revoked_grant" -> OWN_HOMEWORK_REVOKED_GRANT;
                case "own_homework_rebound_telegram" -> OWN_HOMEWORK_REBOUND_TELEGRAM;
                case "complete", "completed" -> COMPLETE;
                default -> OTHER;
            };
        }
    }

    /** Actual Auth/Acedemic TLS services over independent runtime connections. */
    static final class TlsServices implements AutoCloseable {
        final AnnotationConfigApplicationContext academicContext =
                new AnnotationConfigApplicationContext();
        final AnnotationConfigApplicationContext authContext =
                new AnnotationConfigApplicationContext();
        final LocalContainerEntityManagerFactoryBean entities =
                new LocalContainerEntityManagerFactoryBean();
        Server academicServer;
        Server authServer;
        final Path ca;
        io.grpc.ManagedChannel authChannel;
        io.grpc.ManagedChannel academicChannel;

        TlsServices(
                BotCredentialLifecycleIT.SplitDatabases split,
                Path temporary,
                String academicToken,
                String authToken,
                String sharedSecret)
                throws Exception {
            ca = certificate(temporary);
            try {
                var keys = KeyStore.getInstance("PKCS12");
                try (var stream = Files.newInputStream(temporary.resolve("server.p12"))) {
                    keys.load(stream, "np01-test-only".toCharArray());
                }
                var manager =
                        KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
                manager.init(keys, "np01-test-only".toCharArray());
                var ssl =
                        GrpcSslContexts.configure(
                                        io.netty.handler.ssl.SslContextBuilder.forServer(manager))
                                .build();
                String academicToAuth = token();
                String authToPresentation = token();
                authContext.registerBean(
                        "transactionManager",
                        DataSourceTransactionManager.class,
                        () -> new DataSourceTransactionManager(split.authData));
                authContext.registerBean(JdbcTemplate.class, () -> split.auth);
                authContext.register(
                        Transactions.class,
                        AuthBotOwnInitialCredentialsQuery.class,
                        ru.rutcampustrack.auth.botcredentials.AuthBotCredentialMessageRepository
                                .class,
                        ru.rutcampustrack.auth.authority.AuthBotAuthorityQuery.class);
                authContext.refresh();
                var labels =
                        new ru.rutcampustrack.auth.authority.AcademicIdentityPresentationClient(
                                authToPresentation, "localhost");
                authServer =
                        NettyServerBuilder.forAddress(
                                        new InetSocketAddress(InetAddress.getLoopbackAddress(), 0))
                                .sslContext(ssl)
                                .addService(
                                        ServerInterceptors.intercept(
                                                new AuthBotCredentialsGrpcService(
                                                        authContext.getBean(
                                                                AuthBotOwnInitialCredentialsQuery
                                                                        .class),
                                                        authContext.getBean(
                                                                ru.rutcampustrack.auth
                                                                        .botcredentials
                                                                        .AuthBotCredentialMessageRepository
                                                                        .class)),
                                                new AuthBotCredentialServiceIdentityInterceptor(
                                                        authToken)))
                                .addService(
                                        ServerInterceptors.intercept(
                                                new ru.rutcampustrack.auth.authority
                                                        .AuthBotIdentityGrpcService(
                                                        authContext.getBean(
                                                                ru.rutcampustrack.auth.authority
                                                                        .AuthBotAuthorityQuery
                                                                        .class),
                                                        labels),
                                                new ru.rutcampustrack.auth.authority
                                                        .AuthDirectedIdentityInterceptor(
                                                        authToken, "", academicToAuth, "")))
                                .build()
                                .start();
                authChannel =
                        io.grpc.netty.NettyChannelBuilder.forAddress(
                                        "localhost", authServer.getPort())
                                .overrideAuthority("localhost")
                                .sslContext(
                                        GrpcSslContexts.forClient()
                                                .trustManager(ca.toFile())
                                                .build())
                                .build();
                var authRead =
                        new ru.rutcampustrack.academic.botread.AuthBotReadAuthorityClient(
                                academicToAuth, "localhost");
                ReflectionTestUtils.setField(
                        authRead,
                        "stub",
                        ru.rutcampustrack.authauthority.grpc.AuthIdentityGrpcServiceGrpc
                                .newBlockingStub(authChannel));
                entities.setDataSource(split.academicData);
                entities.setPackagesToScan(
                        "ru.rutcampustrack.academic.entity", "ru.rutcampustrack.academic.config");
                entities.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
                entities.setJpaPropertyMap(
                        Map.of(
                                "hibernate.hbm2ddl.auto",
                                "validate",
                                "hibernate.jdbc.time_zone",
                                "UTC"));
                entities.afterPropertiesSet();
                var transactionManager = new JpaTransactionManager(entities.getObject());
                academicContext.registerBean(
                        "transactionManager",
                        JpaTransactionManager.class,
                        () -> transactionManager);
                academicContext.registerBean(JdbcTemplate.class, () -> split.academic);
                academicContext.registerBean(
                        ru.rutcampustrack.academic.botread.AuthBotReadAuthorityClient.class,
                        () -> authRead);
                academicContext.register(
                        Transactions.class,
                        ru.rutcampustrack.academic.identityoperation.AcademicIdentityReadFence
                                .class,
                        ru.rutcampustrack.academic.botread.AcademicBotHomeworkQuery.class,
                        ru.rutcampustrack.academic.botread.AcademicBotHomeworkService.class,
                        ru.rutcampustrack.academic.identitypresentation
                                .AcademicIdentityPresentationGrpcService.class);
                academicContext.refresh();
                var factory =
                        new JpaRepositoryFactory(
                                SharedEntityManagerCreator.createSharedEntityManager(
                                        entities.getObject()));
                factory.setBeanFactory(academicContext.getBeanFactory());
                var grpc =
                        new AcademicGrpcServiceImpl(
                                new AcademicReadService(
                                        factory.getRepository(GroupRepository.class),
                                        factory.getRepository(UserRepository.class),
                                        factory.getRepository(SemesterRepository.class),
                                        null,
                                        null,
                                        academicContext.getBean(
                                                ru.rutcampustrack.academic.identityoperation
                                                        .AcademicIdentityReadFence.class)),
                                null,
                                null,
                                factory.getRepository(SubjectRepository.class),
                                null,
                                factory.getRepository(SemesterRepository.class),
                                null,
                                factory.getRepository(HomeworkRepository.class),
                                factory.getRepository(HomeworkCompletionRepository.class),
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                (GrpcUserAuthorityReader) null);
                ReflectionTestUtils.setField(
                        grpc,
                        "botHomeworkService",
                        academicContext.getBean(
                                ru.rutcampustrack.academic.botread.AcademicBotHomeworkService
                                        .class));
                var legacy = new GrpcAuthInterceptor();
                ReflectionTestUtils.setField(legacy, "expectedSecret", sharedSecret);
                academicServer =
                        NettyServerBuilder.forAddress(
                                        new InetSocketAddress(InetAddress.getLoopbackAddress(), 0))
                                .sslContext(ssl)
                                .addService(
                                        ServerInterceptors.intercept(
                                                grpc,
                                                legacy,
                                                new ru.rutcampustrack.academic.botread
                                                        .AcademicBotHomeworkServiceIdentityInterceptor(
                                                        academicToken)))
                                .addService(
                                        ServerInterceptors.intercept(
                                                academicContext.getBean(
                                                        ru.rutcampustrack.academic
                                                                .identitypresentation
                                                                .AcademicIdentityPresentationGrpcService
                                                                .class),
                                                new ru.rutcampustrack.academic.identitypresentation
                                                        .IdentityPresentationServiceIdentityInterceptor(
                                                        authToPresentation)))
                                .build()
                                .start();
                academicChannel =
                        io.grpc.netty.NettyChannelBuilder.forAddress(
                                        "localhost", academicServer.getPort())
                                .overrideAuthority("localhost")
                                .sslContext(
                                        GrpcSslContexts.forClient()
                                                .trustManager(ca.toFile())
                                                .build())
                                .build();
                ReflectionTestUtils.setField(
                        labels,
                        "stub",
                        ru.rutcampustrack.authauthority.grpc.AcademicIdentityPresentationServiceGrpc
                                .newBlockingStub(academicChannel));
            } catch (Exception failure) {
                try {
                    close();
                } catch (Exception cleanup) {
                    failure.addSuppressed(cleanup);
                }
                throw failure;
            }
        }

        @Override
        public void close() throws Exception {
            if (academicServer != null) academicServer.shutdownNow();
            if (authServer != null) authServer.shutdownNow();
            if (academicChannel != null) academicChannel.shutdownNow();
            if (authChannel != null) authChannel.shutdownNow();
            if (academicServer != null)
                assertThat(academicServer.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
            if (authServer != null)
                assertThat(authServer.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
            if (academicChannel != null)
                assertThat(academicChannel.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
            if (authChannel != null)
                assertThat(authChannel.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
            academicContext.close();
            authContext.close();
            if (entities.getObject() != null) entities.destroy();
        }
    }

    private Map<String, Map<String, Object>> fixtures(
            BotCredentialLifecycleIT.SplitDatabases split) {
        var jdbc = split.academicOwner;
        Map<String, Map<String, Object>> result = new LinkedHashMap<>();
        for (String role : List.of("student", "teacher", "headman", "FULL", "LIMITED", "foreign")) {
            long group = split.group();
            String login = "np" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
            String initial = UUID.randomUUID().toString();
            long id =
                    split.identity(
                            login,
                            BotCredentialLifecycleIT.PASSWORD_HASH,
                            initial,
                            group,
                            role.equals("foreign") ? "teacher" : role);
            long chat = 830000000L + id;
            split.authOwner.update("UPDATE auth_identities SET telegram_id=? WHERE id=?", chat, id);
            result.put(role, Map.of("uid", id, "chat", chat, "login", login, "initial", initial));
        }
        long student = ((Number) result.get("student").get("uid")).longValue();
        long group =
                jdbc.queryForObject(
                        "SELECT group_id FROM academic_role_eligibility WHERE user_id=? AND"
                                + " role='STUDENT'",
                        Long.class,
                        student);
        jdbc.update("UPDATE groups SET is_active=true WHERE id=?", group);
        jdbc.update("UPDATE semesters SET is_active=false WHERE is_active=true");
        var day = java.time.LocalDate.now(java.time.ZoneId.of("Europe/Moscow"));
        long semester =
                jdbc.queryForObject(
                        "INSERT INTO semesters(name,date_from,date_to,is_active,created_at)"
                                + " VALUES('Own bot homework',?,?,true,NOW()) RETURNING id",
                        Long.class,
                        day.minusDays(30),
                        day.plusDays(30));
        long subject =
                jdbc.queryForObject(
                        """
                        WITH subject AS (
                            INSERT INTO subjects(name,type,group_id,semester_id)
                            VALUES('Own bot subject','lecture',?,?) RETURNING id
                        ), lesson_type AS (
                            INSERT INTO subject_lesson_types(subject_id,lesson_type)
                            SELECT id,'lecture'::subject_type FROM subject RETURNING subject_id
                        ) SELECT subject_id FROM lesson_type
                        """,
                        Long.class,
                        group,
                        semester);
        long homework =
                jdbc.queryForObject(
                        """
INSERT INTO homeworks(group_id,subject_id,semester_id,title,published_by,lesson_date,lesson_number,
    binding_mode,lesson_type,binding_id,actor_id,request_key,payload_hash,publication_state)
VALUES(?,?,?,'Own bot homework',?,?,NULL,'DATE','LECTURE',?,?,?,decode(repeat('11',32),'hex'),'ACTIVE') RETURNING id
""",
                        Long.class,
                        group,
                        subject,
                        semester,
                        student,
                        day,
                        subject,
                        student,
                        UUID.randomUUID());
        var own = new LinkedHashMap<>(result.get("student"));
        own.put("homework_id", homework);
        own.put("date_from", day.toString());
        own.put("date_to", day.toString());
        result.put("student", own);
        return result;
    }

    private static Path certificate(Path directory) throws Exception {
        Path store = directory.resolve("server.p12");
        Path ca = directory.resolve("ca.pem");
        keytool(
                List.of(
                        "-genkeypair",
                        "-alias",
                        "server",
                        "-keyalg",
                        "RSA",
                        "-keysize",
                        "2048",
                        "-validity",
                        "1",
                        "-dname",
                        "CN=localhost",
                        "-ext",
                        "SAN=dns:localhost,ip:127.0.0.1",
                        "-keystore",
                        store.toString(),
                        "-storetype",
                        "PKCS12",
                        "-storepass",
                        "np01-test-only",
                        "-keypass",
                        "np01-test-only",
                        "-noprompt"));
        keytool(
                List.of(
                        "-exportcert",
                        "-alias",
                        "server",
                        "-keystore",
                        store.toString(),
                        "-storepass",
                        "np01-test-only",
                        "-rfc",
                        "-file",
                        ca.toString()));
        return ca;
    }

    private static void keytool(List<String> arguments) throws Exception {
        var command = new ArrayList<String>();
        command.add(Path.of(System.getProperty("java.home"), "bin", "keytool").toString());
        command.addAll(arguments);
        Process process =
                new ProcessBuilder(command)
                        .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                        .redirectError(ProcessBuilder.Redirect.DISCARD)
                        .start();
        if (!process.waitFor(30, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            process.waitFor(5, TimeUnit.SECONDS);
            throw new AssertionError("synthetic TLS material generation timed out");
        }
        if (process.exitValue() != 0)
            throw new AssertionError("synthetic TLS material generation failed");
    }

    private static String token() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static Path repositoryRoot() {
        Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (current != null) {
            if (Files.isRegularFile(
                    current.resolve("services/notification-bot/tests/np01_bridge_runner.py")))
                return current;
            current = current.getParent();
        }
        throw new IllegalStateException("NP01 bridge runner source unavailable");
    }
}
