package ru.rutcampustrack.notification.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
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

import java.io.IOException;
import java.lang.reflect.Type;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Real socket/broker/outbound HTTP wiring; Auth identity/revocation is controlled.
 * This does not prove Auth PostgreSQL/Redis, gateway, browser or export delivery.
 */
@SpringBootTest(classes = PrivateWsTransportIT.Config.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class PrivateWsTransportIT {

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration(excludeName = {
            "org.springframework.boot.autoconfigure.mongo.MongoAutoConfiguration",
            "org.springframework.boot.autoconfigure.data.mongo.MongoDataAutoConfiguration",
            "org.springframework.boot.autoconfigure.data.mongo.MongoRepositoriesAutoConfiguration",
            "org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration",
            "org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration",
            "org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration"})
    @EnableConfigurationProperties(WsTicketProperties.class)
    @Import({WebSocketConfig.class, TicketHandshakeInterceptor.class, WsTicketClient.class,
            SubscriptionAuthInterceptor.class, WsSessionBindingRegistry.class,
            WsSessionAdmissionOutboundInterceptor.class, WsSessionBindingWebSocketDecoratorFactory.class})
    static class Config {}

    private static final String SERVICE_SECRET = "synthetic-internal-issuer-secret-for-local-test";
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Map<String, Long> TICKETS = new ConcurrentHashMap<>();
    private static final Map<Long, String> SESSIONS = Map.of(11L, UUID.randomUUID().toString(),
            12L, UUID.randomUUID().toString());
    private static final Map<Long, Boolean> ADMITTED = new ConcurrentHashMap<>();
    private static final Map<Long, AtomicInteger> READS = new ConcurrentHashMap<>();
    private static final HttpServer AUTHORITY = startAuthority();

    @LocalServerPort int port;
    @Autowired SimpMessagingTemplate messages;

    @DynamicPropertySource
    static void authorityProperties(DynamicPropertyRegistry properties) {
        properties.add("rutcampustrack.notification.ws-ticket.auth-service-url",
                () -> "http://127.0.0.1:" + AUTHORITY.getAddress().getPort());
        properties.add("rutcampustrack.notification.ws-ticket.internal-issuer-secret", () -> SERVICE_SECRET);
    }

    @AfterAll
    static void stopOwnedAuthority() {
        AUTHORITY.stop(0);
    }

    @Test
    void openSocketReceivesBeforeRevokeAndCannotReceiveNextPrivateMessageAfterRevoke() throws Exception {
        ADMITTED.put(11L, true);
        ADMITTED.put(12L, true);
        READS.put(11L, new AtomicInteger());
        READS.put(12L, new AtomicInteger());
        WebSocketStompClient client = new WebSocketStompClient(new StandardWebSocketClient());
        client.setMessageConverter(new StringMessageConverter());
        StompSession first = null;
        StompSession control = null;
        var firstReceived = new CopyOnWriteArrayList<String>();
        var controlReceived = new CopyOnWriteArrayList<String>();
        try {
            first = connect(client, 11L);
            control = connect(client, 12L);
            first.subscribe("/topic/user/11", recorder(firstReceived));
            control.subscribe("/topic/user/12", recorder(controlReceived));
            // Observe actual positive delivery, rather than guessing SUBSCRIBE readiness.
            await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
                messages.convertAndSend("/topic/user/11", "before");
                messages.convertAndSend("/topic/user/12", "before");
                assertThat(firstReceived).contains("before");
                assertThat(controlReceived).contains("before");
            });
            int readsBeforeRevoke = READS.get(11L).get();
            ADMITTED.put(11L, false);
            messages.convertAndSend("/topic/user/11", "after");
            messages.convertAndSend("/topic/user/12", "after");
            StompSession revoked = first;
            await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
                assertThat(controlReceived).contains("after");
                assertThat(READS.get(11L).get()).isGreaterThan(readsBeforeRevoke);
                assertThat(revoked.isConnected()).isFalse();
            });
            assertThat(firstReceived).doesNotContain("after");
            assertThat(control.isConnected()).isTrue();
        } finally {
            if (first != null && first.isConnected()) first.disconnect();
            if (control != null && control.isConnected()) control.disconnect();
            client.stop();
        }
    }

    private StompSession connect(WebSocketStompClient client, long userId) throws Exception {
        String ticket = UUID.randomUUID().toString();
        TICKETS.put(ticket, userId);
        WebSocketHttpHeaders headers = new WebSocketHttpHeaders();
        headers.set("X-RCT-WS-Ticket", ticket);
        return client.connectAsync("ws://127.0.0.1:" + port + "/ws/websocket", headers,
                new StompHeaders(), new StompSessionHandlerAdapter() {}).get(5, TimeUnit.SECONDS);
    }

    private static StompFrameHandler recorder(CopyOnWriteArrayList<String> received) {
        return new StompFrameHandler() {
            @Override public Type getPayloadType(StompHeaders headers) { return String.class; }
            @Override public void handleFrame(StompHeaders headers, Object payload) {
                received.add((String) payload);
            }
        };
    }

    private static HttpServer startAuthority() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/internal/", exchange -> {
                try (exchange) {
                    if (!SERVICE_SECRET.equals(exchange.getRequestHeaders().getFirst("X-Internal-Issuer-Secret"))) {
                        exchange.sendResponseHeaders(401, -1);
                        return;
                    }
                    var body = JSON.readTree(exchange.getRequestBody());
                    if ("/internal/consume-ws-ticket".equals(exchange.getRequestURI().getPath())) {
                        Long user = TICKETS.remove(body.path("ticket").asText());
                        if (user == null) {
                            exchange.sendResponseHeaders(404, -1);
                            return;
                        }
                        byte[] response = JSON.writeValueAsBytes(Map.of(
                                "user_id", user, "session_id", SESSIONS.get(user),
                                "session_version", 1, "roles_version", 1, "role", "STUDENT",
                                "status", "ACTIVE", "group_id", 7, "is_headman", false,
                                "read_only", false, "expires_at", "2099-01-01T00:00:00Z"));
                        exchange.getResponseHeaders().set("Content-Type", "application/json");
                        exchange.sendResponseHeaders(200, response.length);
                        exchange.getResponseBody().write(response);
                    } else if ("/internal/auth/admit-ws-session".equals(exchange.getRequestURI().getPath())) {
                        long user = body.path("user_id").asLong();
                        READS.computeIfAbsent(user, ignored -> new AtomicInteger()).incrementAndGet();
                        boolean allowed = ADMITTED.getOrDefault(user, false)
                                && body.path("session_id").asText().equals(SESSIONS.get(user));
                        exchange.sendResponseHeaders(allowed ? 204 : 401, -1);
                    } else {
                        exchange.sendResponseHeaders(404, -1);
                    }
                }
            });
            server.start();
            return server;
        } catch (IOException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }
}
