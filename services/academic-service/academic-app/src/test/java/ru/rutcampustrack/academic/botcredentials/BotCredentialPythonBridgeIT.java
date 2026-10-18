package ru.rutcampustrack.academic.botcredentials;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;

import io.grpc.Server;
import io.grpc.ServerInterceptors;
import io.grpc.netty.GrpcSslContexts;
import io.grpc.netty.NettyServerBuilder;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
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
import ru.rutcampustrack.academic.migration.OwnedAuthPrincipals;
import ru.rutcampustrack.academic.repository.GroupRepository;
import ru.rutcampustrack.academic.repository.HomeworkCompletionRepository;
import ru.rutcampustrack.academic.repository.HomeworkRepository;
import ru.rutcampustrack.academic.repository.SemesterRepository;
import ru.rutcampustrack.academic.repository.SubjectRepository;
import ru.rutcampustrack.academic.repository.UserRepository;
import ru.rutcampustrack.auth.botcredentials.AuthBotCredentialServiceIdentityInterceptor;
import ru.rutcampustrack.auth.botcredentials.AuthBotCredentialsGrpcService;
import ru.rutcampustrack.auth.botcredentials.AuthBotOwnInitialCredentialsQuery;
import ru.rutcampustrack.shared.security.admin.AdminAccessJdbcAuthority;

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
    @EnableTransactionManagement
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
            OwnedAuthPrincipals.prepare(postgres);
            Flyway.configure()
                    .dataSource(OwnedAuthPrincipals.source(postgres, "rct_migrator"))
                    .locations(
                            "filesystem:"
                                    + root.resolve(
                                            "services/academic-service/academic-app/src/main/resources/db/migration"))
                    .load()
                    .migrate();
            var data =
                    new DriverManagerDataSource(
                            postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
            var jdbc = new JdbcTemplate(data);
            Map<String, Map<String, Object>> fixtures = fixtures(jdbc);
            var entities = new LocalContainerEntityManagerFactoryBean();
            entities.setDataSource(data);
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
            var authData = OwnedAuthPrincipals.source(postgres, "rct_auth_runtime");
            var authJdbc = new JdbcTemplate(authData);
            assertThat(authJdbc.queryForObject("SELECT current_user", String.class))
                    .isEqualTo("rct_auth_runtime");
            assertThat(
                            authJdbc.queryForObject(
                                    "SELECT rolsuper OR rolbypassrls FROM pg_roles WHERE"
                                            + " rolname=current_user",
                                    Boolean.class))
                    .isFalse();
            try (var context = new AnnotationConfigApplicationContext();
                    var authContext = new AnnotationConfigApplicationContext()) {
                var emf = entities.getObject();
                var transactionManager = new JpaTransactionManager(emf);
                context.registerBean(
                        "transactionManager",
                        JpaTransactionManager.class,
                        () -> transactionManager);
                context.registerBean(JdbcTemplate.class, () -> jdbc);
                context.registerBean(
                        AdminAccessJdbcAuthority.class, () -> new AdminAccessJdbcAuthority(jdbc));
                context.register(
                        Transactions.class,
                        BotOwnInitialCredentialsQuery.class,
                        BotCredentialMessageRepository.class,
                        BotCredentialsService.class);
                context.refresh();
                authContext.registerBean(JdbcTemplate.class, () -> authJdbc);
                authContext.registerBean(
                        "transactionManager",
                        DataSourceTransactionManager.class,
                        () -> new DataSourceTransactionManager(authData));
                authContext.registerBean(
                        AdminAccessJdbcAuthority.class,
                        () -> new AdminAccessJdbcAuthority(authJdbc));
                authContext.register(Transactions.class, AuthBotOwnInitialCredentialsQuery.class);
                authContext.refresh();
                var repositoryFactory =
                        new JpaRepositoryFactory(
                                SharedEntityManagerCreator.createSharedEntityManager(emf));
                repositoryFactory.setBeanFactory(context.getBeanFactory());
                UserRepository users = repositoryFactory.getRepository(UserRepository.class);
                GroupRepository groups = repositoryFactory.getRepository(GroupRepository.class);
                SemesterRepository semesters =
                        repositoryFactory.getRepository(SemesterRepository.class);
                var readService = new AcademicReadService(groups, users, semesters, null);
                var grpc =
                        new AcademicGrpcServiceImpl(
                                readService,
                                groups,
                                users,
                                repositoryFactory.getRepository(SubjectRepository.class),
                                null,
                                semesters,
                                null,
                                repositoryFactory.getRepository(HomeworkRepository.class),
                                repositoryFactory.getRepository(HomeworkCompletionRepository.class),
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                context.getBean(BotCredentialsService.class),
                                new GrpcUserAuthorityReader(jdbc));
                var auth = new GrpcAuthInterceptor();
                ReflectionTestUtils.setField(auth, "expectedSecret", sharedSecret);
                Path ca = certificate(temporary);
                var keys = KeyStore.getInstance("PKCS12");
                try (var stream = Files.newInputStream(temporary.resolve("server.p12"))) {
                    keys.load(stream, "np01-test-only".toCharArray());
                }
                var manager =
                        KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
                manager.init(keys, "np01-test-only".toCharArray());
                Server server =
                        NettyServerBuilder.forAddress(
                                        new InetSocketAddress(InetAddress.getLoopbackAddress(), 0))
                                .sslContext(
                                        GrpcSslContexts.configure(
                                                        io.netty.handler.ssl.SslContextBuilder
                                                                .forServer(manager))
                                                .build())
                                .addService(
                                        ServerInterceptors.intercept(
                                                grpc,
                                                auth,
                                                new BotCredentialServiceIdentityInterceptor(
                                                        serviceToken)))
                                .build();
                Server authServer =
                        NettyServerBuilder.forAddress(
                                        new InetSocketAddress(InetAddress.getLoopbackAddress(), 0))
                                .sslContext(
                                        GrpcSslContexts.configure(
                                                        io.netty.handler.ssl.SslContextBuilder
                                                                .forServer(manager))
                                                .build())
                                .addService(
                                        ServerInterceptors.intercept(
                                                new AuthBotCredentialsGrpcService(
                                                        authContext.getBean(
                                                                AuthBotOwnInitialCredentialsQuery
                                                                        .class)),
                                                new AuthBotCredentialServiceIdentityInterceptor(
                                                        authServiceToken)))
                                .build();
                try {
                    server.start();
                    authServer.start();
                    var control = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
                    var controlWorkers = Executors.newFixedThreadPool(2);
                    try {
                        control.setExecutor(controlWorkers);
                        var tx = new TransactionTemplate(transactionManager);
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
                                        Map<String, Object> facts =
                                                tx.execute(
                                                        status -> {
                                                            jdbc.queryForObject(
                                                                    "SELECT id FROM users WHERE"
                                                                            + " id=? FOR UPDATE",
                                                                    Long.class,
                                                                    own);
                                                            switch (action) {
                                                                case "change" ->
                                                                        jdbc.update(
                                                                                "UPDATE users SET"
                                                                                    + " password_hash=?,password_changed=true,initial_password=NULL"
                                                                                    + " WHERE id=?",
                                                                                UUID.randomUUID()
                                                                                        .toString(),
                                                                                own);
                                                                case "new_initial" ->
                                                                        jdbc.update(
                                                                                "UPDATE users SET"
                                                                                    + " password_hash=?,password_changed=false,initial_password=?"
                                                                                    + " WHERE id=?",
                                                                                UUID.randomUUID()
                                                                                        .toString(),
                                                                                UUID.randomUUID()
                                                                                        .toString(),
                                                                                own);
                                                                case "rebind_foreign" -> {
                                                                    jdbc.update(
                                                                            "UPDATE users SET"
                                                                                + " telegram_id=NULL"
                                                                                + " WHERE id=?",
                                                                            own);
                                                                    jdbc.update(
                                                                            "UPDATE users SET"
                                                                                + " telegram_id=?"
                                                                                + " WHERE id=?",
                                                                            ownChat,
                                                                            foreign);
                                                                }
                                                                case "restore_binding" -> {
                                                                    jdbc.update(
                                                                            "UPDATE users SET"
                                                                                + " telegram_id=?"
                                                                                + " WHERE id=?",
                                                                            foreignChat,
                                                                            foreign);
                                                                    jdbc.update(
                                                                            "UPDATE users SET"
                                                                                + " telegram_id=?"
                                                                                + " WHERE id=?",
                                                                            ownChat,
                                                                            own);
                                                                }
                                                                case "revoke" ->
                                                                        jdbc.update(
                                                                                "UPDATE"
                                                                                    + " user_role_grants"
                                                                                    + " SET status='suspended'"
                                                                                    + " WHERE"
                                                                                    + " user_id=?",
                                                                                own);
                                                                case "reactivate" ->
                                                                        jdbc.update(
                                                                                "UPDATE"
                                                                                    + " user_role_grants"
                                                                                    + " SET status='active'"
                                                                                    + " WHERE"
                                                                                    + " user_id=?",
                                                                                own);
                                                                case "archive" ->
                                                                        jdbc.update(
                                                                                "UPDATE users SET"
                                                                                    + " status='archived'"
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
                                                                                + " FROM users"
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
                            try {
                                var result = mapper.readTree(output);
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
                            assertThat(child.exitValue()).isZero();
                            assertThat(qualified).isTrue();
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
            } finally {
                entities.destroy();
            }
        }
    }

    private Map<String, Map<String, Object>> fixtures(JdbcTemplate jdbc) {
        Map<String, Map<String, Object>> result = new LinkedHashMap<>();
        for (String role : List.of("student", "teacher", "headman", "FULL", "LIMITED", "foreign")) {
            long group =
                    jdbc.queryForObject(
                            "INSERT INTO groups(name) VALUES(?) RETURNING id",
                            Long.class,
                            "NP01 " + UUID.randomUUID().toString().substring(0, 20));
            String login = "np" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
            String initial = UUID.randomUUID().toString();
            long id =
                    jdbc.queryForObject(
                            "INSERT INTO"
                                + " users(login,password_hash,last_name,first_name,initial_password,group_id)"
                                + " VALUES(?,?,'NP01','Own',?,?) RETURNING id",
                            Long.class,
                            login,
                            UUID.randomUUID().toString(),
                            initial,
                            group);
            long chat = 830000000L + id;
            jdbc.update("UPDATE users SET telegram_id=? WHERE id=?", chat, id);
            if (role.equals("FULL") || role.equals("LIMITED")) {
                jdbc.update(
                        "INSERT INTO"
                            + " admin_access_profiles(user_id,assigned,mode,permissions,version)"
                            + " VALUES(?,true,?,?::text[],1)",
                        id,
                        role,
                        role.equals("FULL") ? "{}" : "{USERS_VIEW}");
                jdbc.update(
                        "INSERT INTO user_role_grants(user_id,role,status,created_at,updated_at) "
                                + "VALUES(?,'admin','active',NOW(),NOW())",
                        id);
            } else {
                String base =
                        role.equals("teacher") || role.equals("foreign") ? "teacher" : "student";
                jdbc.update(
                        "INSERT INTO"
                            + " user_role_grants(user_id,role,status,group_id,created_at,updated_at)"
                            + " VALUES(?,?,'active',?,NOW(),NOW())",
                        id,
                        base,
                        base.equals("teacher") ? null : group);
                if (role.equals("headman"))
                    jdbc.update(
                            "INSERT INTO"
                                + " user_role_grants(user_id,role,status,group_id,created_at,updated_at)"
                                + " VALUES(?,'headman','active',?,NOW(),NOW())",
                            id,
                            group);
            }
            result.put(role, Map.of("uid", id, "chat", chat, "login", login, "initial", initial));
        }
        long student = ((Number) result.get("student").get("uid")).longValue();
        long group =
                jdbc.queryForObject(
                        "SELECT group_id FROM user_role_grants WHERE user_id=? AND role='student'",
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
