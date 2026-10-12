package ru.rutcampustrack.academic.botcredentials;

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
import ru.rutcampustrack.academic.repository.GroupRepository;
import ru.rutcampustrack.academic.repository.UserRepository;
import ru.rutcampustrack.shared.security.admin.AdminAccessJdbcAuthority;

import javax.net.ssl.KeyManagerFactory;
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

import static org.assertj.core.api.Assertions.assertThat;

/** Actual Academic/JPA/JDBC/TLS → Python client/Dispatcher/provider. Auth producer is tested separately. */
class BotCredentialPythonBridgeIT {
    private final ObjectMapper mapper = new ObjectMapper();

    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement
    static class Transactions { }

    @Test
    void actualAcademicTlsOwnCardsReachPythonDispatcherAndExactProviderCleanup(@TempDir Path temporary) throws Exception {
        String python = System.getenv("NP01_PYTHON_EXECUTABLE");
        if (python == null || !Files.isExecutable(Path.of(python))) {
            throw new AssertionError("explicit pinned NP01 Python interpreter required; no skip or implicit environment fallback");
        }
        Path root = repositoryRoot();
        String serviceToken = token();
        String sharedSecret = UUID.randomUUID().toString();
        String controlToken = token();
        try (var postgres = new PostgreSQLContainer<>("postgres:16-alpine")
                .withDatabaseName("rct_np01_bridge").withUsername("rct_np01_bridge")
                .withPassword("rct_np01_disposable").withReuse(false)) {
            postgres.start();
            Flyway.configure().dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                    .locations("filesystem:" + root.resolve("services/academic-service/academic-app/src/main/resources/db/migration"))
                    .load().migrate();
            var data = new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
            var jdbc = new JdbcTemplate(data);
            Map<String, Map<String, Object>> fixtures = fixtures(jdbc);
            var entities = new LocalContainerEntityManagerFactoryBean();
            entities.setDataSource(data);
            entities.setPackagesToScan("ru.rutcampustrack.academic.entity", "ru.rutcampustrack.academic.config");
            entities.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
            entities.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto", "validate", "hibernate.jdbc.time_zone", "UTC"));
            entities.afterPropertiesSet();
            try (var context = new AnnotationConfigApplicationContext()) {
                var emf = entities.getObject();
                var transactionManager = new JpaTransactionManager(emf);
                context.registerBean("transactionManager", JpaTransactionManager.class, () -> transactionManager);
                context.registerBean(JdbcTemplate.class, () -> jdbc);
                context.registerBean(AdminAccessJdbcAuthority.class, () -> new AdminAccessJdbcAuthority(jdbc));
                context.register(Transactions.class, BotOwnInitialCredentialsQuery.class,
                        BotCredentialMessageRepository.class, BotCredentialsService.class);
                context.refresh();
                var repositoryFactory = new JpaRepositoryFactory(SharedEntityManagerCreator.createSharedEntityManager(emf));
                repositoryFactory.setBeanFactory(context.getBeanFactory());
                UserRepository users = repositoryFactory.getRepository(UserRepository.class);
                GroupRepository groups = repositoryFactory.getRepository(GroupRepository.class);
                var readService = new AcademicReadService(groups, users, null, null);
                var grpc = new AcademicGrpcServiceImpl(readService, groups, users,
                        null, null, null, null, null, null, null, null, null, null, null, null, null, null,
                        context.getBean(BotCredentialsService.class));
                var auth = new GrpcAuthInterceptor();
                ReflectionTestUtils.setField(auth, "expectedSecret", sharedSecret);
                Path ca = certificate(temporary);
                var keys = KeyStore.getInstance("PKCS12");
                try (var stream = Files.newInputStream(temporary.resolve("server.p12"))) {
                    keys.load(stream, "np01-test-only".toCharArray());
                }
                var manager = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
                manager.init(keys, "np01-test-only".toCharArray());
                Server server = NettyServerBuilder.forAddress(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0))
                        .sslContext(GrpcSslContexts.configure(io.netty.handler.ssl.SslContextBuilder.forServer(manager)).build())
                        .addService(ServerInterceptors.intercept(grpc, auth,
                                new BotCredentialServiceIdentityInterceptor(serviceToken))).build().start();
                try {
                    var control = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
                    var controlWorkers = Executors.newFixedThreadPool(2);
                    try {
                        control.setExecutor(controlWorkers);
                        var tx = new TransactionTemplate(transactionManager);
                        long own = ((Number) fixtures.get("student").get("uid")).longValue();
                        long foreign = ((Number) fixtures.get("foreign").get("uid")).longValue();
                        long ownChat = ((Number) fixtures.get("student").get("chat")).longValue();
                        long foreignChat = ((Number) fixtures.get("foreign").get("chat")).longValue();
                        control.createContext("/control/", exchange -> {
                            byte[] supplied = String.valueOf(exchange.getRequestHeaders().getFirst("x-np01-control"))
                                    .getBytes(StandardCharsets.UTF_8);
                            if (!"POST".equals(exchange.getRequestMethod()) || !MessageDigest.isEqual(
                                    supplied, controlToken.getBytes(StandardCharsets.UTF_8))) {
                                exchange.sendResponseHeaders(403, -1); exchange.close(); return;
                            }
                            String action = exchange.getRequestURI().getPath().substring("/control/".length());
                            try {
                                Map<String, Object> facts = tx.execute(status -> {
                                    jdbc.queryForObject("SELECT id FROM users WHERE id=? FOR UPDATE", Long.class, own);
                                    switch (action) {
                                        case "change" -> jdbc.update("UPDATE users SET password_hash=?,password_changed=true,initial_password=NULL WHERE id=?", UUID.randomUUID().toString(), own);
                                        case "new_initial" -> jdbc.update("UPDATE users SET password_hash=?,password_changed=false,initial_password=? WHERE id=?", UUID.randomUUID().toString(), UUID.randomUUID().toString(), own);
                                        case "rebind_foreign" -> {
                                            jdbc.update("UPDATE users SET telegram_id=NULL WHERE id=?", own);
                                            jdbc.update("UPDATE users SET telegram_id=? WHERE id=?", ownChat, foreign);
                                        }
                                        case "restore_binding" -> {
                                            jdbc.update("UPDATE users SET telegram_id=? WHERE id=?", foreignChat, foreign);
                                            jdbc.update("UPDATE users SET telegram_id=? WHERE id=?", ownChat, own);
                                        }
                                        case "revoke" -> jdbc.update("UPDATE user_role_grants SET status='suspended' WHERE user_id=?", own);
                                        case "reactivate" -> jdbc.update("UPDATE user_role_grants SET status='active' WHERE user_id=?", own);
                                        case "archive" -> jdbc.update("UPDATE users SET status='archived' WHERE id=?", own);
                                        case "facts", "verify" -> { }
                                        default -> throw new IllegalArgumentException("unknown isolated fixture control");
                                    }
                                    var versions = jdbc.queryForMap("SELECT credentials_version,telegram_binding_version FROM users WHERE id=?", own);
                                    versions.put("pending", jdbc.queryForObject("SELECT count(*) FROM bot_credential_messages WHERE user_id=? AND cleanup_state='PENDING'", Long.class, own));
                                    if (action.equals("verify")) {
                                        String[] ids = exchange.getRequestURI().getQuery().split("&");
                                        long chat = Long.parseLong(ids[0].substring("chat=".length()));
                                        long message = Long.parseLong(ids[1].substring("message=".length()));
                                        versions.put("registered", jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM bot_credential_messages WHERE private_chat_id=? AND message_id=?)", Boolean.class, chat, message));
                                    }
                                    return versions;
                                });
                                byte[] response = mapper.writeValueAsBytes(facts);
                                exchange.getResponseHeaders().set("Content-Type", "application/json");
                                exchange.sendResponseHeaders(200, response.length);
                                exchange.getResponseBody().write(response);
                            } catch (RuntimeException failure) {
                                exchange.sendResponseHeaders(500, -1);
                            } finally { exchange.close(); }
                        });
                        control.start();
                        var process = new ProcessBuilder(python,
                                root.resolve("services/notification-bot/tests/np01_bridge_runner.py").toString());
                        process.directory(temporary.toFile()); // No .env/private config discovery.
                        process.redirectError(ProcessBuilder.Redirect.DISCARD);
                        var environment = process.environment();
                        environment.put("PYTHONPATH", root.resolve("services/notification-bot").toString());
                        environment.put("PYTHONDONTWRITEBYTECODE", "1");
                        environment.put("NP01_AUTHORITY", "localhost:" + server.getPort());
                        environment.put("NP01_CA", ca.toString());
                        environment.put("NP01_SERVICE_TOKEN", serviceToken);
                        environment.put("NP01_SHARED_SECRET", sharedSecret);
                        environment.put("NP01_CONTROL_TOKEN", controlToken);
                        environment.put("NP01_CONTROL_URL", "http://127.0.0.1:" + control.getAddress().getPort());
                        environment.put("NP01_FIXTURES", mapper.writeValueAsString(fixtures));
                        Process child = process.start();
                        if (!child.waitFor(90, TimeUnit.SECONDS)) {
                            child.destroyForcibly(); child.waitFor(5, TimeUnit.SECONDS);
                            throw new AssertionError("NP01 native Python bridge timed out");
                        }
                        var output = child.getInputStream().readAllBytes();
                        boolean qualified;
                        try {
                            var result = mapper.readTree(output);
                            qualified = result.path("qualified").asBoolean(false) && result.path("actual_tls").asBoolean(false)
                                    && result.path("real_academic_pg").asBoolean(false) && result.path("failures").asInt(-1) == 0;
                        } catch (Exception invalid) {
                            throw new AssertionError("NP01 bridge did not return safe qualified outcomes");
                        }
                        assertThat(child.exitValue()).isZero();
                        assertThat(qualified).isTrue();
                        assertThat(jdbc.queryForObject("SELECT count(*) FROM bot_credential_messages WHERE user_id=? AND cleanup_state='PENDING'", Long.class, own)).isZero();
                    } finally {
                        control.stop(0);
                        controlWorkers.shutdownNow();
                        assertThat(controlWorkers.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
                    }
                } finally {
                    server.shutdownNow();
                    assertThat(server.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
                }
            } finally { entities.destroy(); }
        }
    }

    private Map<String, Map<String, Object>> fixtures(JdbcTemplate jdbc) {
        Map<String, Map<String, Object>> result = new LinkedHashMap<>();
        for (String role : List.of("student", "teacher", "headman", "FULL", "LIMITED", "foreign")) {
            long group = jdbc.queryForObject("INSERT INTO groups(name) VALUES(?) RETURNING id", Long.class,
                    "NP01 " + UUID.randomUUID().toString().substring(0, 20));
            String login = "np" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
            String initial = UUID.randomUUID().toString();
            long id = jdbc.queryForObject("INSERT INTO users(login,password_hash,last_name,first_name,initial_password,group_id) VALUES(?,?,'NP01','Own',?,?) RETURNING id",
                    Long.class, login, UUID.randomUUID().toString(), initial, group);
            long chat = 830000000L + id;
            jdbc.update("UPDATE users SET telegram_id=? WHERE id=?", chat, id);
            if (role.equals("FULL") || role.equals("LIMITED")) {
                jdbc.update("INSERT INTO admin_access_profiles(user_id,assigned,mode,permissions,version) VALUES(?,true,?,?::text[],1)", id, role, role.equals("FULL") ? "{}" : "{USERS_VIEW}");
                jdbc.update("INSERT INTO user_role_grants(user_id,role,status,created_at,updated_at) "
                        + "VALUES(?,'admin','active',NOW(),NOW())", id);
            } else {
                String base = role.equals("teacher") || role.equals("foreign") ? "teacher" : "student";
                jdbc.update("INSERT INTO user_role_grants(user_id,role,status,group_id,created_at,updated_at) "
                        + "VALUES(?,?,'active',?,NOW(),NOW())", id, base, base.equals("teacher") ? null : group);
                if (role.equals("headman")) jdbc.update("INSERT INTO user_role_grants(user_id,role,status,group_id,created_at,updated_at) "
                        + "VALUES(?,'headman','active',?,NOW(),NOW())", id, group);
            }
            result.put(role, Map.of("uid", id, "chat", chat, "login", login, "initial", initial));
        }
        return result;
    }

    private static Path certificate(Path directory) throws Exception {
        Path store = directory.resolve("server.p12");
        Path ca = directory.resolve("ca.pem");
        keytool(List.of("-genkeypair", "-alias", "server", "-keyalg", "RSA", "-keysize", "2048", "-validity", "1",
                "-dname", "CN=localhost", "-ext", "SAN=dns:localhost,ip:127.0.0.1", "-keystore", store.toString(),
                "-storetype", "PKCS12", "-storepass", "np01-test-only", "-keypass", "np01-test-only", "-noprompt"));
        keytool(List.of("-exportcert", "-alias", "server", "-keystore", store.toString(), "-storepass", "np01-test-only", "-rfc", "-file", ca.toString()));
        return ca;
    }

    private static void keytool(List<String> arguments) throws Exception {
        var command = new ArrayList<String>();
        command.add(Path.of(System.getProperty("java.home"), "bin", "keytool").toString());
        command.addAll(arguments);
        Process process = new ProcessBuilder(command).redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectError(ProcessBuilder.Redirect.DISCARD).start();
        if (!process.waitFor(30, TimeUnit.SECONDS)) {
            process.destroyForcibly(); process.waitFor(5, TimeUnit.SECONDS);
            throw new AssertionError("synthetic TLS material generation timed out");
        }
        if (process.exitValue() != 0) throw new AssertionError("synthetic TLS material generation failed");
    }

    private static String token() {
        byte[] bytes = new byte[32]; new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static Path repositoryRoot() {
        Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (current != null) {
            if (Files.isRegularFile(current.resolve("services/notification-bot/tests/np01_bridge_runner.py"))) return current;
            current = current.getParent();
        }
        throw new IllegalStateException("NP01 bridge runner source unavailable");
    }
}
