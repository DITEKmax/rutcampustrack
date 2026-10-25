package ru.rutcampustrack.academic.botcredentials;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.PortBinding;
import com.github.dockerjava.api.model.Ports;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import io.grpc.BindableService;
import io.grpc.Server;
import io.grpc.ServerInterceptor;
import io.grpc.ServerInterceptors;
import io.grpc.netty.GrpcSslContexts;
import io.grpc.netty.NettyServerBuilder;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.SharedEntityManagerCreator;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;

import ru.rutcampustrack.auth.event.AuthOutboxEntity;
import ru.rutcampustrack.auth.event.AuthOutboxPublisherJob;
import ru.rutcampustrack.auth.event.PasswordChangedOutbox;
import ru.rutcampustrack.auth.event.RabbitOutboxEventSender;
import ru.rutcampustrack.auth.session.jdbc.JdbcSessionAuthority;
import ru.rutcampustrack.auth.session.model.AuthMethod;
import ru.rutcampustrack.auth.session.model.AuthRole;
import ru.rutcampustrack.auth.session.model.RoleGrant;
import ru.rutcampustrack.auth.session.model.RoleStatus;
import ru.rutcampustrack.auth.session.model.SecurityEvent;
import ru.rutcampustrack.auth.session.model.SessionState;
import ru.rutcampustrack.auth.session.port.CredentialSessionTransactionPort;
import ru.rutcampustrack.auth.session.port.SessionStatePort;
import ru.rutcampustrack.shared.outbox.jpa.JpaOutboxStorage;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import javax.net.ssl.KeyManagerFactory;

/**
 * Genuine Auth transaction/outbox/confirmed Rabbit → native consumer/Redis → Academic/TLS/provider.
 */
class BotCredentialBrokerBridgeIT {
    private static String closedDetail(JsonNode value, Set<String> allowed) {
        return value.isTextual() && allowed.contains(value.textValue()) ? value.textValue() : "OTHER";
    }

    private static int boundedChecks(JsonNode value) {
        if (!value.isIntegralNumber() || !value.canConvertToInt()) return -1;
        int count = value.intValue();
        return count >= 0 && count <= 100_000 ? count : -1;
    }

    private static String controlCause(Throwable failure) {
        if (failure instanceof AssertionError) return "ASSERTION";
        if (failure instanceof org.springframework.dao.DataIntegrityViolationException)
            return "DATA_INTEGRITY";
        if (failure instanceof org.springframework.dao.DataAccessException) return "DATA_ACCESS";
        if (failure instanceof ru.rutcampustrack.auth.session.AuthSessionException)
            return "AUTH_SESSION";
        if (failure instanceof IllegalArgumentException) return "ARGUMENT";
        if (failure instanceof IllegalStateException) return "STATE";
        return "OTHER";
    }

    private static final Instant COMMAND_TIME = Instant.parse("2026-10-12T00:00:00Z");
    private static final String RUNNER =
            "services/notification-bot/tests/np01_broker_bridge_runner.py";
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private final BCryptPasswordEncoder passwords = new BCryptPasswordEncoder();
    private final AtomicLong commandSequence = new AtomicLong();

    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement
    static class Transactions {}

    private record OwnedWorker(ProcessHandle handle, CompletableFuture<ProcessHandle> exit) {}

    @Test
    void actualProducerBrokerClaimAckRetryRestartAndDlqPreserveExactCards(@TempDir Path temporary)
            throws Exception {
        String python = System.getenv("NP01_PYTHON_EXECUTABLE");
        if (python == null || !Files.isExecutable(Path.of(python))) {
            throw new AssertionError(
                    "explicit pinned NP01 Python required; no skip or system fallback");
        }
        Path root = repositoryRoot();
        String namespace = "np01_" + UUID.randomUUID().toString().replace("-", "");
        String serviceToken = token();
        String authServiceToken = token();
        assertThat(authServiceToken).isNotEqualTo(serviceToken);
        String sharedSecret = token();
        String controlToken = token();
        try (var postgres =
                        new PostgreSQLContainer<>("postgres:16-alpine")
                                .withDatabaseName("rct_np01_broker")
                                .withUsername("rct_np01_broker")
                                .withPassword(token())
                                .withLabel("rct.validation.owner", namespace)
                                .withReuse(false);
                var rabbit =
                        new RabbitMQContainer("rabbitmq:3.13-management-alpine")
                                .withLabel("rct.validation.owner", namespace)
                                .withReuse(false);
                var redis =
                        new GenericContainer<>("redis:7-alpine")
                                .withExposedPorts(6379)
                                .withLabel("rct.validation.owner", namespace)
                                .withReuse(false)) {
            loopbackPorts(postgres);
            loopbackPorts(rabbit);
            loopbackPorts(redis);
            postgres.start();
            var split = new BotCredentialLifecycleIT.SplitDatabases(postgres, root);
            assertLoopbackPorts(postgres);
            rabbit.start();
            assertLoopbackPorts(rabbit);
            redis.start();
            assertLoopbackPorts(redis);
            createOwnVhost(rabbit, namespace);
            var data = split.authData;
            var jdbc = split.auth;
            var fixtures = fixtures(split);
            long own = ((Number) fixtures.get("own").get("uid")).longValue();
            long foreign = ((Number) fixtures.get("foreign").get("uid")).longValue();
            var producer =
                    new JdbcSessionAuthority(
                            jdbc,
                            new DataSourceTransactionManager(data),
                            new PasswordChangedOutbox(jdbc, mapper));
            var entities = new LocalContainerEntityManagerFactoryBean();
            entities.setDataSource(data);
            entities.setPackagesToScan("ru.rutcampustrack.auth.event");
            entities.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
            entities.setJpaPropertyMap(
                    Map.of(
                            "hibernate.hbm2ddl.auto",
                            "validate",
                            "hibernate.jdbc.time_zone",
                            "UTC"));
            entities.afterPropertiesSet();
            var broker = new CachingConnectionFactory(rabbit.getHost(), rabbit.getMappedPort(5672));
            broker.setUsername(rabbit.getAdminUsername());
            broker.setPassword(rabbit.getAdminPassword());
            broker.setVirtualHost(namespace);
            broker.setPublisherConfirmType(CachingConnectionFactory.ConfirmType.SIMPLE);
            try (var services =
                    new BotCredentialPythonBridgeIT.TlsServices(
                            split, temporary, serviceToken, authServiceToken, sharedSecret)) {
                var sharedEntities =
                        SharedEntityManagerCreator.createSharedEntityManager(entities.getObject());
                var storage = new JpaOutboxStorage<>(AuthOutboxEntity.class);
                storage.setEntityManager(sharedEntities);
                var sender = new RabbitOutboxEventSender(new RabbitTemplate(broker));
                var publisher = new AuthOutboxPublisherJob(storage, sender, null);
                var tx = new TransactionTemplate(new JpaTransactionManager(entities.getObject()));
                Path ca = services.ca;
                Server server = services.academicServer;
                Server authServer = services.authServer;
                var workers = Executors.newFixedThreadPool(2);
                var supervisor = new AtomicReference<Process>();
                List<OwnedWorker> ownedWorkers = new CopyOnWriteArrayList<>();
                HttpServer control = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
                try {
                    control.setExecutor(workers);
                    control.createContext(
                            "/control/",
                            exchange ->
                                    handleControl(
                                            exchange,
                                            controlToken,
                                            jdbc,
                                            fixtures,
                                            producer,
                                            publisher,
                                            sender,
                                            tx,
                                            own,
                                            foreign,
                                            supervisor,
                                            ownedWorkers));
                    control.start();
                    ProcessBuilder launch =
                            new ProcessBuilder(python, root.resolve(RUNNER).toString());
                    launch.directory(temporary.toFile());
                    launch.redirectError(ProcessBuilder.Redirect.DISCARD);
                    Map<String, String> env = launch.environment();
                    minimalEnvironment(launch);
                    env.put("TMPDIR", temporary.toString());
                    env.put("PYTHONPATH", root.resolve("services/notification-bot").toString());
                    env.put("PYTHONDONTWRITEBYTECODE", "1");
                    env.put("PYTHONNOUSERSITE", "1");
                    env.put("PYTHONSAFEPATH", "1");
                    env.put("NP01_AUTHORITY", "localhost:" + server.getPort());
                    env.put("NP01_CA", ca.toString());
                    env.put("NP01_AUTH_SERVICE_AUTHORITY", "localhost:" + authServer.getPort());
                    env.put("NP01_AUTH_SERVICE_CA", ca.toString());
                    env.put("NP01_AUTH_SERVICE_TOKEN", authServiceToken);
                    env.put("NP01_SERVICE_TOKEN", serviceToken);
                    env.put("NP01_SHARED_SECRET", sharedSecret);
                    env.put("NP01_CONTROL_TOKEN", controlToken);
                    env.put(
                            "NP01_CONTROL_URL",
                            "http://127.0.0.1:" + control.getAddress().getPort());
                    env.put("NP01_FIXTURES", mapper.writeValueAsString(fixtures));
                    env.put("NP01_RABBIT_HOST", rabbit.getHost());
                    env.put("NP01_RABBIT_AMQP_PORT", rabbit.getMappedPort(5672).toString());
                    env.put("NP01_RABBIT_HTTP_PORT", rabbit.getMappedPort(15672).toString());
                    env.put("NP01_RABBIT_USER", rabbit.getAdminUsername());
                    env.put("NP01_RABBIT_PASSWORD", rabbit.getAdminPassword());
                    env.put("NP01_RABBIT_VHOST", namespace);
                    env.put("NP01_REDIS_HOST", redis.getHost());
                    env.put("NP01_REDIS_PORT", redis.getMappedPort(6379).toString());
                    Process child = launch.start();
                    supervisor.set(child);
                    try {
                        if (!child.waitFor(600, TimeUnit.SECONDS)) {
                            throw new AssertionError("native broker bridge deadline exceeded");
                        }
                        var result = mapper.readTree(child.getInputStream().readAllBytes());
                        String detail =
                                String.format(
                                        "BRIDGE_DETAIL_V1 kind=BROKER phase=%s cause=%s"
                                                + " action=%s checks=%d workers_reaped=%s",
                                        closedDetail(result.path("phase"),
                                                Set.of(
                                                        "setup",
                                                        "actual_auth_change_outbox_confirm_consumer_ack",
                                                        "same_real_event_completed_dedup_ack",
                                                        "actual_unlinked_reset_original_uid_cleanup",
                                                        "uncertain_provider_keeps_real_claim_retry_without_ack",
                                                        "actual_worker_death_durable_redelivery_recovery",
                                                        "worker_death_while_default_processing_lease_held",
                                                        "held_lease_blocks_premature_ack_until_natural_expiry",
                                                        "unknown_version_real_dlq_releases_claim",
                                                        "completed",
                                                        "cleanup")),
                                        closedDetail(result.path("failure_cause"),
                                                Set.of(
                                                        "CHECK_FAILED",
                                                        "ASSERTION",
                                                        "TIMEOUT",
                                                        "HTTP_TRANSPORT",
                                                        "GRPC_UNAVAILABLE",
                                                        "GRPC_DEADLINE_EXCEEDED",
                                                        "GRPC_UNAUTHENTICATED",
                                                        "GRPC_PERMISSION_DENIED",
                                                        "GRPC_FAILED_PRECONDITION",
                                                        "GRPC_INVALID_ARGUMENT",
                                                        "GRPC_NOT_FOUND",
                                                        "GRPC_OTHER",
                                                        "CONTROL_ASSERTION",
                                                        "CONTROL_DATA_INTEGRITY",
                                                        "CONTROL_DATA_ACCESS",
                                                        "CONTROL_AUTH_SESSION",
                                                        "CONTROL_ARGUMENT",
                                                        "CONTROL_STATE",
                                                        "CONTROL_OTHER",
                                                        "CONTROL_HTTP_STATUS",
                                                        "OTHER")),
                                        closedDetail(result.path("control_action"),
                                                Set.of(
                                                        "change",
                                                        "reset",
                                                        "publish",
                                                        "replay",
                                                        "unknown_version",
                                                        "new_initial",
                                                        "rebind",
                                                        "restore_binding",
                                                        "worker_started",
                                                        "worker_reaped",
                                                        "facts",
                                                        "verify",
                                                        "NONE",
                                                        "OTHER")),
                                        boundedChecks(result.path("checks")),
                                        result.path("workers_reaped").isBoolean()
                                                ? Boolean.toString(result.path("workers_reaped").booleanValue())
                                                : "UNKNOWN");
                        assertThat(child.exitValue()).withFailMessage(detail).isZero();
                        for (String fact :
                                List.of(
                                        "qualified",
                                        "actual_tls",
                                        "real_academic_pg",
                                        "real_auth_producer",
                                        "real_rabbit",
                                        "real_redis",
                                        "actual_worker_restart",
                                        "workers_reaped",
                                        "natural_held_lease_expiry")) {
                            assertThat(result.path(fact).asBoolean(false)).as(fact).isTrue();
                        }
                        assertThat(result.path("failures").asInt(-1)).isZero();
                        assertThat(result.path("worker_restarts").asInt(-1)).isEqualTo(2);
                        int heldPttl = result.path("held_lease_initial_pttl_ms").asInt(-1);
                        assertThat(heldPttl).isGreaterThan(270_000).isLessThanOrEqualTo(300_000);
                        assertThat(result.path("held_lease_elapsed_ms").asInt(-1))
                                .isGreaterThanOrEqualTo(heldPttl - 2_000);
                        assertThat(result.path("held_lease_redeliveries").asInt(-1))
                                .isGreaterThanOrEqualTo(2);
                        System.out.println(
                                "NP01 broker bridge qualified=true native_python_exit=0 checks="
                                        + result.path("checks").asInt(-1)
                                        + " worker_restarts="
                                        + result.path("worker_restarts").asInt(-1)
                                        + " held_lease_initial_pttl_ms="
                                        + heldPttl
                                        + " held_lease_elapsed_ms="
                                        + result.path("held_lease_elapsed_ms").asInt(-1)
                                        + " held_lease_redeliveries="
                                        + result.path("held_lease_redeliveries").asInt(-1));
                        assertThat(
                                        jdbc.queryForObject(
                                                "SELECT count(*) FROM bot_credential_messages WHERE"
                                                        + " user_id=? AND cleanup_state='PENDING'",
                                                Long.class,
                                                own))
                                .isZero();
                    } finally {
                        stopProcessTree(child, ownedWorkers);
                    }
                } finally {
                    control.stop(0);
                    workers.shutdownNow();
                    assertThat(workers.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
                    authServer.shutdownNow();
                    server.shutdownNow();
                    assertThat(authServer.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
                    assertThat(server.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
                }
            } finally {
                broker.destroy();
                entities.destroy();
            }
        }
    }

    private void handleControl(
            HttpExchange exchange,
            String token,
            JdbcTemplate jdbc,
            Map<String, Map<String, Object>> fixtures,
            JdbcSessionAuthority producer,
            AuthOutboxPublisherJob publisher,
            RabbitOutboxEventSender sender,
            TransactionTemplate tx,
            long own,
            long foreign,
            AtomicReference<Process> supervisor,
            List<OwnedWorker> ownedWorkers)
            throws java.io.IOException {
        byte[] supplied =
                String.valueOf(exchange.getRequestHeaders().getFirst("x-np01-control"))
                        .getBytes(StandardCharsets.UTF_8);
        if (!"POST".equals(exchange.getRequestMethod())
                || !MessageDigest.isEqual(supplied, token.getBytes(StandardCharsets.UTF_8))) {
            exchange.sendResponseHeaders(403, -1);
            exchange.close();
            return;
        }
        String action = exchange.getRequestURI().getPath().substring("/control/".length());
        try {
            String negativeEvent = null;
            switch (action) {
                case "change" -> change(producer, jdbc, own);
                case "reset" -> {
                    var ticket = new CredentialSessionTransactionPort.CredentialHash(token());
                    assertThat(
                                    producer.issuePasswordResetTicket(
                                            own, ticket, COMMAND_TIME.plusSeconds(3600)))
                            .isTrue();
                    assertThat(
                                    producer.completePasswordReset(
                                                    new CredentialSessionTransactionPort
                                                            .PasswordResetCommand(
                                                            ticket,
                                                            new CredentialSessionTransactionPort
                                                                    .CredentialHash(
                                                                    passwords.encode(token())),
                                                            now()))
                                            .succeeded())
                            .isTrue();
                }
                case "publish" -> {
                    Integer published = tx.execute(status -> publisher.publishBatch());
                    assertThat(published).isEqualTo(1);
                }
                case "replay" -> sender.send("password.changed", event(jdbc, own));
                case "unknown_version" -> {
                    ObjectNode malformed = (ObjectNode) mapper.readTree(event(jdbc, own));
                    negativeEvent = UUID.randomUUID().toString();
                    malformed.put("event_id", negativeEvent);
                    malformed.put("event_version", 991);
                    sender.send("password.changed", mapper.writeValueAsString(malformed));
                }
                case "new_initial" ->
                        tx.executeWithoutResult(
                                status ->
                                        jdbc.update(
                                                "UPDATE auth_identities SET"
                                                    + " password_hash=?,initial_password=?,password_changed=false"
                                                    + " WHERE id=?",
                                                passwords.encode(token()),
                                                token(),
                                                own)); // Explicit isolated fixture setup, never a
                // fake Auth change.
                case "rebind" ->
                        tx.executeWithoutResult(
                                status -> {
                                    jdbc.update(
                                            "UPDATE auth_identities SET telegram_id=NULL WHERE"
                                                    + " id=?",
                                            own);
                                    jdbc.update(
                                            "UPDATE auth_identities SET telegram_id=? WHERE id=?",
                                            fixtures.get("own").get("chat"),
                                            foreign);
                                });
                case "restore_binding" ->
                        tx.executeWithoutResult(
                                status -> {
                                    jdbc.update(
                                            "UPDATE auth_identities SET telegram_id=? WHERE id=?",
                                            fixtures.get("foreign").get("chat"),
                                            foreign);
                                    jdbc.update(
                                            "UPDATE auth_identities SET telegram_id=? WHERE id=?",
                                            fixtures.get("own").get("chat"),
                                            own);
                                });
                case "worker_started" -> {
                    long pid =
                            Long.parseLong(
                                    exchange.getRequestURI().getQuery().substring("pid=".length()));
                    Process owner = supervisor.get();
                    ProcessHandle worker = ProcessHandle.of(pid).orElseThrow();
                    if (owner == null
                            || worker.parent()
                                    .map(parent -> parent.pid() != owner.pid())
                                    .orElse(true)) {
                        throw new IllegalArgumentException(
                                "only this supervisor's own worker may be registered");
                    }
                    if (ownedWorkers.stream()
                            .anyMatch(old -> old.handle().pid() == pid && !old.exit().isDone())) {
                        throw new IllegalArgumentException("own worker already registered");
                    }
                    ownedWorkers.add(new OwnedWorker(worker, worker.onExit()));
                }
                case "worker_reaped" -> {
                    long pid =
                            Long.parseLong(
                                    exchange.getRequestURI().getQuery().substring("pid=".length()));
                    var matching =
                            ownedWorkers.stream()
                                    .filter(worker -> worker.handle().pid() == pid)
                                    .toList();
                    if (matching.isEmpty())
                        throw new IllegalArgumentException("own worker registration required");
                    for (OwnedWorker worker : matching) worker.exit().get(5, TimeUnit.SECONDS);
                }
                case "facts", "verify" -> {}
                default -> throw new IllegalArgumentException("unknown own fixture action");
            }
            Map<String, Object> facts =
                    new LinkedHashMap<>(
                            jdbc.queryForMap(
                                    "SELECT"
                                        + " credentials_version,telegram_binding_version,initial_password"
                                        + " IS NULL AS initial_cleared FROM auth_identities WHERE"
                                        + " id=?",
                                    own));
            if (action.equals("worker_started")) facts.put("worker_owned", true);
            if (action.equals("worker_reaped")) facts.put("worker_reaped", true);
            facts.put(
                    "pending",
                    jdbc.queryForObject(
                            "SELECT count(*) FROM bot_credential_messages WHERE user_id=? AND"
                                    + " cleanup_state='PENDING'",
                            Long.class,
                            own));
            facts.put(
                    "revoked_sessions",
                    jdbc.queryForObject(
                            "SELECT count(*) FROM auth_sessions WHERE user_id=? AND revoked_at IS"
                                    + " NOT NULL",
                            Long.class,
                            own));
            facts.put(
                    "outbox_pending",
                    jdbc.queryForObject(
                            "SELECT count(*) FROM auth_outbox WHERE event_type='password.changed'"
                                    + " AND payload->'payload'->>'user_id'=? AND status='pending'",
                            Long.class,
                            Long.toString(own)));
            facts.put(
                    "outbox_sent",
                    jdbc.queryForObject(
                            "SELECT count(*) FROM auth_outbox WHERE event_type='password.changed'"
                                    + " AND payload->'payload'->>'user_id'=? AND status='sent'",
                            Long.class,
                            Long.toString(own)));
            List<String> events =
                    jdbc.query(
                            "SELECT payload::text FROM auth_outbox WHERE"
                                    + " event_type='password.changed' AND"
                                    + " payload->'payload'->>'user_id'=? ORDER BY"
                                    + " (payload->'payload'->>'credentials_version')::bigint"
                                    + " DESC,created_at DESC,id DESC LIMIT 1",
                            (row, index) -> row.getString(1),
                            Long.toString(own));
            if (!events.isEmpty()) {
                var produced = mapper.readTree(events.getFirst());
                facts.put(
                        "event_id",
                        negativeEvent == null ? produced.path("event_id").asText() : negativeEvent);
                facts.put(
                        "producer_original_uid",
                        produced.path("payload").path("user_id").asLong() == own);
                facts.put("producer_v2", produced.path("event_version").asInt() == 2);
                facts.put("producer_has_recipient", produced.path("payload").has("telegram_id"));
                facts.put("producer_payload_fields", produced.path("payload").size());
            }
            if (action.equals("verify")) {
                String[] tuple = exchange.getRequestURI().getQuery().split("&");
                long chat = Long.parseLong(tuple[0].substring("chat=".length()));
                long message = Long.parseLong(tuple[1].substring("message=".length()));
                facts.put(
                        "registered",
                        jdbc.queryForObject(
                                "SELECT EXISTS(SELECT 1 FROM bot_credential_messages WHERE"
                                        + " private_chat_id=? AND message_id=?)",
                                Boolean.class,
                                chat,
                                message));
            }
            byte[] output = mapper.writeValueAsBytes(facts);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, output.length);
            exchange.getResponseBody().write(output);
        } catch (Throwable failure) {
            // Never put SQL, producer commands, provider content or synthetic secrets into
            // transport/logs.
            byte[] output = mapper.writeValueAsBytes(Map.of("controlCause", controlCause(failure)));
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(500, output.length);
            exchange.getResponseBody().write(output);
        } finally {
            exchange.close();
        }
    }

    private void change(JdbcSessionAuthority authority, JdbcTemplate jdbc, long user) {
        String hash =
                jdbc.queryForObject(
                        "SELECT password_hash FROM auth_identities WHERE id=?", String.class, user);
        List<RoleGrant> grants =
                jdbc.query(
                        "SELECT id,role,status,group_id,created_at,updated_at FROM user_role_grants"
                                + " WHERE user_id=? ORDER BY id",
                        (row, index) -> {
                            long group = row.getLong("group_id");
                            Long scope = row.wasNull() ? null : group;
                            return new RoleGrant(
                                    row.getLong("id"),
                                    user,
                                    AuthRole.valueOf(
                                            row.getString("role").toUpperCase(Locale.ROOT)),
                                    RoleStatus.valueOf(
                                            row.getString("status").toUpperCase(Locale.ROOT)),
                                    scope,
                                    row.getObject("created_at", OffsetDateTime.class).toInstant(),
                                    row.getObject("updated_at", OffsetDateTime.class).toInstant());
                        },
                        user);
        UUID session = UUID.randomUUID();
        var state =
                new SessionState(
                        session,
                        user,
                        grants.getFirst().grantId(),
                        1,
                        UUID.randomUUID(),
                        null,
                        COMMAND_TIME.plusSeconds(86400),
                        COMMAND_TIME,
                        COMMAND_TIME,
                        null,
                        null,
                        AuthMethod.PASSWORD,
                        "NP01",
                        "isolated");
        long version =
                jdbc.queryForObject(
                        "SELECT roles_version FROM auth_identities WHERE id=?", Long.class, user);
        assertThat(
                        authority
                                .createSession(
                                        new SessionStatePort.CreateSessionCommand(
                                                state,
                                                version,
                                                grants,
                                                new SessionStatePort.CredentialProof(hash),
                                                new SecurityEvent(
                                                        user,
                                                        session,
                                                        SecurityEvent.Type.LOGIN,
                                                        COMMAND_TIME,
                                                        AuthMethod.PASSWORD,
                                                        "NP01",
                                                        "isolated")))
                                .succeeded())
                .isTrue();
        Instant when = now();
        var changed =
                authority.changePassword(
                        new CredentialSessionTransactionPort.ChangePasswordCommand(
                                user,
                                session,
                                new CredentialSessionTransactionPort.CredentialHash(hash),
                                new CredentialSessionTransactionPort.CredentialHash(
                                        passwords.encode(token())),
                                when,
                                new SecurityEvent(
                                        user,
                                        session,
                                        SecurityEvent.Type.PASSWORD_CHANGED,
                                        when,
                                        AuthMethod.PASSWORD,
                                        "NP01",
                                        "isolated")));
        assertThat(changed.succeeded()).isTrue();
    }

    private Instant now() {
        return COMMAND_TIME.plusSeconds(commandSequence.incrementAndGet());
    }

    private static String event(JdbcTemplate jdbc, long user) {
        return jdbc.queryForObject(
                "SELECT payload::text FROM auth_outbox WHERE event_type='password.changed' AND"
                        + " payload->'payload'->>'user_id'=? ORDER BY"
                        + " (payload->'payload'->>'credentials_version')::bigint DESC,created_at"
                        + " DESC,id DESC LIMIT 1",
                String.class,
                Long.toString(user));
    }

    private Map<String, Map<String, Object>> fixtures(
            BotCredentialLifecycleIT.SplitDatabases split) {
        Map<String, Map<String, Object>> result = new LinkedHashMap<>();
        for (String name : List.of("own", "foreign")) {
            long group = split.group();
            String login = "np" + token().substring(0, 20);
            String initial = token();
            long user = split.identity(login, passwords.encode(initial), initial, group, "student");
            var jdbc = split.authOwner;
            long chat = 850000000L + user;
            jdbc.update("UPDATE auth_identities SET telegram_id=? WHERE id=?", chat, user);
            result.put(name, Map.of("uid", user, "chat", chat, "initial", initial, "login", login));
        }
        return result;
    }

    private static void createOwnVhost(RabbitMQContainer rabbit, String vhost) throws Exception {
        String basic =
                Base64.getEncoder()
                        .encodeToString(
                                (rabbit.getAdminUsername() + ":" + rabbit.getAdminPassword())
                                        .getBytes(StandardCharsets.UTF_8));
        try (HttpClient client =
                HttpClient.newBuilder()
                        .version(HttpClient.Version.HTTP_1_1)
                        .connectTimeout(Duration.ofSeconds(5))
                        .build()) {
            String base = "http://" + rabbit.getHost() + ":" + rabbit.getMappedPort(15672);
            for (var action :
                    List.of(
                            Map.entry("/api/vhosts/" + vhost, "{}"),
                            Map.entry(
                                    "/api/permissions/" + vhost + "/" + rabbit.getAdminUsername(),
                                    "{\"configure\":\".*\",\"write\":\".*\",\"read\":\".*\"}"))) {
                var request =
                        HttpRequest.newBuilder(URI.create(base + action.getKey()))
                                .timeout(Duration.ofSeconds(10))
                                .header("Authorization", "Basic " + basic)
                                .header("Content-Type", "application/json")
                                .PUT(HttpRequest.BodyPublishers.ofString(action.getValue()))
                                .build();
                int status =
                        client.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
                assertThat(status >= 200 && status < 300).as("own vhost provisioning").isTrue();
            }
        }
    }

    private static Server server(
            BindableService grpc, Path directory, ServerInterceptor... interceptors)
            throws Exception {
        var keys = KeyStore.getInstance("PKCS12");
        try (var stream = Files.newInputStream(directory.resolve("server.p12"))) {
            keys.load(stream, "np01-test-only".toCharArray());
        }
        var manager = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        manager.init(keys, "np01-test-only".toCharArray());
        return NettyServerBuilder.forAddress(new InetSocketAddress("127.0.0.1", 0))
                .sslContext(
                        GrpcSslContexts.configure(
                                        io.netty.handler.ssl.SslContextBuilder.forServer(manager))
                                .build())
                .addService(ServerInterceptors.intercept(grpc, interceptors))
                .build();
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
        Files.setPosixFilePermissions(
                store, Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE));
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
        ProcessBuilder launch =
                new ProcessBuilder(command)
                        .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                        .redirectError(ProcessBuilder.Redirect.DISCARD);
        minimalEnvironment(launch);
        Process process = launch.start();
        try {
            assertThat(process.waitFor(30, TimeUnit.SECONDS))
                    .as("synthetic keytool deadline")
                    .isTrue();
            assertThat(process.exitValue()).isZero();
        } finally {
            stopProcessTree(process);
        }
    }

    private static void stopProcessTree(Process process) throws Exception {
        stopProcessTree(process, List.of());
    }

    private static void stopProcessTree(Process process, List<OwnedWorker> registered)
            throws Exception {
        List<OwnedWorker> descendants = new ArrayList<>(registered);
        process.descendants()
                .forEach(
                        handle -> {
                            if (descendants.stream()
                                    .noneMatch(worker -> worker.handle().pid() == handle.pid())) {
                                descendants.add(new OwnedWorker(handle, handle.onExit()));
                            }
                        });
        if (process.isAlive()) {
            // TERM lets the live Python supervisor kill/await/reap its worker first.
            process.destroy();
            if (!process.waitFor(15, TimeUnit.SECONDS)) {
                for (OwnedWorker worker : descendants) {
                    if (!worker.exit().isDone()) worker.handle().destroyForcibly();
                }
                // Keep the parent alive until child exit/reaping is observed. A timeout
                // raises an explicit unsettled cleanup failure, never a false settled result.
                awaitOwnedWorkers(descendants, process.pid());
                if (process.isAlive()) process.destroyForcibly();
                assertThat(process.waitFor(5, TimeUnit.SECONDS))
                        .as("own supervisor stopped after worker reap")
                        .isTrue();
            }
        } else {
            // Unexpected parent death: only registered/captured own handles are touched.
            // A retained zombie makes the bounded terminal check fail explicitly.
            for (OwnedWorker worker : descendants) {
                if (!worker.exit().isDone()) worker.handle().destroyForcibly();
            }
        }
        awaitOwnedWorkers(descendants, process.pid());
        assertThat(process.waitFor(5, TimeUnit.SECONDS)).as("own subprocess terminal").isTrue();
    }

    private static void awaitOwnedWorkers(List<OwnedWorker> workers, long supervisorPid)
            throws Exception {
        try {
            CompletableFuture.allOf(
                            workers.stream()
                                    .map(OwnedWorker::exit)
                                    .toArray(CompletableFuture[]::new))
                    .get(10, TimeUnit.SECONDS);
        } catch (java.util.concurrent.TimeoutException unsettled) {
            throw new AssertionError(
                    "own worker cleanup remains unsettled; supervisor_pid="
                            + supervisorPid
                            + "; own_worker_pids="
                            + workers.stream().map(worker -> worker.handle().pid()).toList(),
                    unsettled);
        }
    }

    private static void minimalEnvironment(ProcessBuilder launch) {
        Map<String, String> environment = launch.environment();
        environment.clear();
        environment.put("LANG", "C.UTF-8");
        environment.put("LC_ALL", "C.UTF-8");
        environment.put("TZ", "UTC");
    }

    private static void loopbackPorts(GenericContainer<?> container) {
        container.withCreateContainerCmdModifier(
                command -> {
                    List<PortBinding> bindings =
                            container.getExposedPorts().stream()
                                    .map(
                                            port ->
                                                    new PortBinding(
                                                            Ports.Binding.bindIpAndPort(
                                                                    "127.0.0.1", 0),
                                                            ExposedPort.tcp(port)))
                                    .toList();
                    command.getHostConfig().withPortBindings(bindings).withPublishAllPorts(false);
                });
    }

    private static void assertLoopbackPorts(GenericContainer<?> container) {
        var inspected =
                DockerClientFactory.instance()
                        .client()
                        .inspectContainerCmd(container.getContainerId())
                        .exec();
        var mappings = inspected.getNetworkSettings().getPorts().getBindings();
        Set<Integer> mapped = new LinkedHashSet<>();
        for (var entry : mappings.entrySet()) {
            if (entry.getValue() == null || entry.getValue().length == 0) continue;
            mapped.add(entry.getKey().getPort());
            for (Ports.Binding binding : entry.getValue()) {
                assertThat(binding.getHostIp())
                        .as("own inspected loopback binding")
                        .isEqualTo("127.0.0.1");
                assertThat(Integer.parseInt(binding.getHostPortSpec()))
                        .as("own inspected ephemeral host port")
                        .isPositive();
            }
        }
        assertThat(mapped)
                .as("every declared port inspected; no extra publication")
                .isEqualTo(Set.copyOf(container.getExposedPorts()));
    }

    private static String token() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static Path repositoryRoot() {
        Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (current != null) {
            if (Files.isRegularFile(current.resolve(RUNNER))) return current;
            current = current.getParent();
        }
        throw new IllegalStateException("native broker runner source unavailable");
    }
}
