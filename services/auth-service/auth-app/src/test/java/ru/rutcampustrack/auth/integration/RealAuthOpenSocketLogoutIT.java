package ru.rutcampustrack.auth.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Comparator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.MapPropertySource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.messaging.converter.StringMessageConverter;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import ru.rutcampustrack.auth.AuthApplication;
import ru.rutcampustrack.auth.dto.LoginRequest;
import ru.rutcampustrack.auth.dto.TokenResponse;
import ru.rutcampustrack.auth.dto.WsTicketResponse;
import ru.rutcampustrack.auth.service.JwtService;
import ru.rutcampustrack.notification.config.SubscriptionAuthInterceptor;
import ru.rutcampustrack.notification.config.TicketHandshakeInterceptor;
import ru.rutcampustrack.notification.config.WebSocketConfig;
import ru.rutcampustrack.notification.config.WsSessionAdmissionOutboundInterceptor;
import ru.rutcampustrack.notification.config.WsSessionBindingRegistry;
import ru.rutcampustrack.notification.config.WsSessionBindingWebSocketDecoratorFactory;
import ru.rutcampustrack.notification.config.WsTicketClient;
import ru.rutcampustrack.notification.config.WsTicketProperties;

/**
 * P09: genuine Auth HTTP/PG/Redis logout joined to the production Notification socket stack. Direct
 * broker publication exercises private recipients and outbound admission; Rabbit producers,
 * browser/edge/TLS and full Notification persistence are outside this finite test.
 */
@SpringBootTest(
        classes = AuthApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class RealAuthOpenSocketLogoutIT extends AbstractIntegrationTest {

    private static final String INTERNAL_SECRET =
            "synthetic-p09-auth-socket-internal-secret-32-bytes";
    private static final Path KEYS = isolatedKeys();
    private static final Duration DELIVERY_BUDGET = Duration.ofSeconds(5);

    @LocalServerPort int authPort;
    @Autowired TestRestTemplate http;
    @Autowired JdbcTemplate jdbc;
    @Autowired JwtService signer;

    @DynamicPropertySource
    static void ownKeysAndIssuer(DynamicPropertyRegistry properties) {
        properties.add("jwt.key-dir", KEYS::toString);
        properties.add("rutcampustrack.security.internal-issuer.secret", () -> INTERNAL_SECRET);
    }

    @AfterAll
    static void removeOwnedSyntheticKeys() throws IOException {
        Throwable cleanupFailure = null;
        try (var files = Files.walk(KEYS)) {
            for (Path path : files.sorted(Comparator.reverseOrder()).toList()) {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException | RuntimeException | AssertionError failure) {
                    if (cleanupFailure == null) cleanupFailure = failure;
                    else cleanupFailure.addSuppressed(failure);
                }
            }
        } catch (IOException | RuntimeException | AssertionError failure) {
            if (cleanupFailure == null) cleanupFailure = failure;
            else cleanupFailure.addSuppressed(failure);
        }
        if (cleanupFailure instanceof IOException failure) throw failure;
        if (cleanupFailure instanceof RuntimeException failure) throw failure;
        if (cleanupFailure instanceof AssertionError failure) throw failure;
    }

    @Test
    void realAuthLogoutStopsNewPrivateDeliveryOnSameOpenSocketWhileControlStillReceives()
            throws Exception {
        Actor revokedActor = null;
        Actor controlActor = null;
        StompSession revokedSocket = null;
        StompSession controlSocket = null;
        WebSocketStompClient client = new WebSocketStompClient(new StandardWebSocketClient());
        client.setMessageConverter(new StringMessageConverter());
        var revokedReceived = new CopyOnWriteArrayList<String>();
        var controlReceived = new CopyOnWriteArrayList<String>();
        Throwable primaryFailure = null;

        try (var notification = notificationSocketContext()) {
            revokedActor = login("student");
            controlActor = login("teacher");
            assertThat(revokedActor.userId()).isNotEqualTo(controlActor.userId());
            assertThat(sessionRevoked(revokedActor)).isFalse();
            assertThat(sessionRevoked(controlActor)).isFalse();

            int socketPort = notification.getWebServer().getPort();
            revokedSocket = connect(client, socketPort, revokedActor);
            controlSocket = connect(client, socketPort, controlActor);
            String revokedTopic = "/topic/user/" + revokedActor.userId();
            String controlTopic = "/topic/user/" + controlActor.userId();
            revokedSocket.subscribe(revokedTopic, recorder(revokedReceived));
            controlSocket.subscribe(controlTopic, recorder(controlReceived));
            var messages = notification.getBean(SimpMessagingTemplate.class);

            // Actual delivery establishes subscription readiness without a guessed sleep.
            await().atMost(DELIVERY_BUDGET)
                    .untilAsserted(
                            () -> {
                                messages.convertAndSend(revokedTopic, "before-revoked-actor");
                                messages.convertAndSend(controlTopic, "before-control-actor");
                                assertThat(revokedReceived).contains("before-revoked-actor");
                                assertThat(controlReceived).contains("before-control-actor");
                            });
            assertThat(revokedReceived).doesNotContain("before-control-actor");
            assertThat(controlReceived).doesNotContain("before-revoked-actor");
            assertThat(revokedSocket.isConnected()).isTrue();
            assertThat(controlSocket.isConnected()).isTrue();

            // A genuine HTTP logout must commit durable revocation before the next private send.
            logout(revokedActor);
            assertThat(sessionRevoked(revokedActor)).isTrue();
            assertThat(sessionRevoked(controlActor)).isFalse();
            messages.convertAndSend(revokedTopic, "after-revoked-actor");
            messages.convertAndSend(controlTopic, "after-control-actor");

            StompSession sameRevokedSocket = revokedSocket;
            await().atMost(DELIVERY_BUDGET)
                    .untilAsserted(
                            () -> {
                                assertThat(controlReceived).contains("after-control-actor");
                                assertThat(sameRevokedSocket.isConnected()).isFalse();
                            });
            // Closure is the observation barrier: no later frame can reach this same transport.
            assertThat(revokedReceived)
                    .doesNotContain("after-revoked-actor", "after-control-actor");
            assertThat(controlReceived)
                    .doesNotContain("before-revoked-actor", "after-revoked-actor");
            assertThat(controlSocket.isConnected()).isTrue();
            assertThat(sessionRevoked(controlActor)).isFalse();
        } catch (Exception | AssertionError failure) {
            // try-with-resources has already suppressed a context-close failure on this primary.
            primaryFailure = failure;
            throw failure;
        } finally {
            Throwable cleanupFailure = primaryFailure;
            try {
                if (revokedSocket != null && revokedSocket.isConnected())
                    revokedSocket.disconnect();
            } catch (RuntimeException | AssertionError failure) {
                if (cleanupFailure == null) cleanupFailure = failure;
                else cleanupFailure.addSuppressed(failure);
            }
            try {
                if (controlSocket != null && controlSocket.isConnected())
                    controlSocket.disconnect();
            } catch (RuntimeException | AssertionError failure) {
                if (cleanupFailure == null) cleanupFailure = failure;
                else cleanupFailure.addSuppressed(failure);
            }
            try {
                client.stop();
            } catch (RuntimeException | AssertionError failure) {
                if (cleanupFailure == null) cleanupFailure = failure;
                else cleanupFailure.addSuppressed(failure);
            }
            try {
                logoutIfAdmitted(revokedActor);
            } catch (RuntimeException | AssertionError failure) {
                if (cleanupFailure == null) cleanupFailure = failure;
                else cleanupFailure.addSuppressed(failure);
            }
            try {
                logoutIfAdmitted(controlActor);
            } catch (RuntimeException | AssertionError failure) {
                if (cleanupFailure == null) cleanupFailure = failure;
                else cleanupFailure.addSuppressed(failure);
            }
            if (primaryFailure == null) {
                if (cleanupFailure instanceof RuntimeException failure) throw failure;
                if (cleanupFailure instanceof AssertionError failure) throw failure;
            }
        }
    }

    private ServletWebServerApplicationContext notificationSocketContext() {
        return (ServletWebServerApplicationContext)
                new SpringApplicationBuilder(NotificationSocketConfig.class)
                        .web(WebApplicationType.SERVLET)
                        .profiles("test")
                        .registerShutdownHook(false)
                        .initializers(
                                context ->
                                        context.getEnvironment()
                                                .getPropertySources()
                                                .addFirst(
                                                        new MapPropertySource(
                                                                "p09-owned-notification",
                                                                Map.of(
                                                                        "server.address",
                                                                        "127.0.0.1",
                                                                        "server.port",
                                                                        "0",
                                                                        "spring.application.name",
                                                                        "p09-notification-socket",
                                                                        "rutcampustrack.notification.ws-ticket.auth-service-url",
                                                                        "http://127.0.0.1:"
                                                                                + authPort,
                                                                        "rutcampustrack.notification.ws-ticket.internal-issuer-secret",
                                                                        INTERNAL_SECRET))))
                        .run();
    }

    private Actor login(String login) {
        var response =
                http.postForEntity(
                        "/auth/login", new LoginRequest(login, "password"), TokenResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        TokenResponse tokens = response.getBody();
        if (tokens == null) throw new AssertionError("Synthetic Auth login omitted tokens");
        var claims = signer.parseSessionAccessToken(tokens.accessToken()).getPayload();
        return new Actor(
                tokens.accessToken(),
                Long.parseLong(claims.getSubject()),
                UUID.fromString(claims.get("sid", String.class)));
    }

    private StompSession connect(WebSocketStompClient client, int port, Actor actor)
            throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(actor.accessToken());
        var response =
                http.exchange(
                        "/auth/ws-ticket",
                        HttpMethod.POST,
                        new HttpEntity<>(null, headers),
                        WsTicketResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        WsTicketResponse issued = response.getBody();
        if (issued == null) throw new AssertionError("Synthetic Auth omitted WebSocket ticket");
        WebSocketHttpHeaders socketHeaders = new WebSocketHttpHeaders();
        socketHeaders.set("X-RCT-WS-Ticket", issued.ticket());
        return client.connectAsync(
                        "ws://127.0.0.1:" + port + "/ws/websocket",
                        socketHeaders,
                        new StompHeaders(),
                        new StompSessionHandlerAdapter() {})
                .get(5, TimeUnit.SECONDS);
    }

    private void logout(Actor actor) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(actor.accessToken());
        var response =
                http.exchange(
                        "/auth/logout",
                        HttpMethod.POST,
                        new HttpEntity<>(null, headers),
                        Void.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    private void logoutIfAdmitted(Actor actor) {
        if (actor != null && !sessionRevoked(actor)) logout(actor);
    }

    private boolean sessionRevoked(Actor actor) {
        return Boolean.TRUE.equals(
                jdbc.queryForObject(
                        "SELECT revoked_at IS NOT NULL FROM auth_sessions WHERE sid=? AND user_id=?",
                        Boolean.class,
                        actor.sessionId(),
                        actor.userId()));
    }

    private static StompFrameHandler recorder(CopyOnWriteArrayList<String> received) {
        return new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return String.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                received.add((String) payload);
            }
        };
    }

    private static Path isolatedKeys() {
        try {
            return Files.createTempDirectory("rct-p09-auth-socket-keys-");
        } catch (IOException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    private record Actor(String accessToken, long userId, UUID sessionId) {
        @Override
        public String toString() {
            return "Actor[synthetic credentials and identity redacted]";
        }
    }

    // Explicitly registered only in the secondary context; Auth component scanning excludes it.
    @TestConfiguration(proxyBeanMethods = false)
    @EnableAutoConfiguration(
            excludeName = {
                "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration",
                "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration",
                "org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration",
                "org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration",
                "org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration",
                "org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration",
                "org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration",
                "org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration",
                "org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration",
                "org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration",
                "org.springframework.boot.actuate.autoconfigure.security.servlet.ManagementWebSecurityAutoConfiguration"
            })
    @EnableConfigurationProperties(WsTicketProperties.class)
    @Import({
        WebSocketConfig.class,
        TicketHandshakeInterceptor.class,
        WsTicketClient.class,
        SubscriptionAuthInterceptor.class,
        WsSessionBindingRegistry.class,
        WsSessionAdmissionOutboundInterceptor.class,
        WsSessionBindingWebSocketDecoratorFactory.class
    })
    static class NotificationSocketConfig {}
}
