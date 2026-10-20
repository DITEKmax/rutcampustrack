package ru.rutcampustrack.notification.ws;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import ru.rutcampustrack.auth.dto.ConsumeWsTicketResponse;
import ru.rutcampustrack.auth.dto.WsSessionAdmissionRequest;
import ru.rutcampustrack.notification.config.WsTicketClient;
import ru.rutcampustrack.shared.testcontainers.ContainerTestBase;

import java.lang.reflect.Type;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * M08 G9 (P1-9) — full WebSocket lifecycle integration test for notification-web.
 *
 * <p>Спускает реальный Spring Boot + WebSocket сервер (RANDOM_PORT), подключается как клиент через
 * {@link StandardWebSocketClient} (ws://…, SockJS не нужен — Spring STOMP client сам умеет native
 * WebSocket), подписывается на group topic, публикует через server-side {@link
 * SimpMessagingTemplate} и проверяет, что клиент получил сообщение в заданное окно времени.
 *
 * <p>Покрывает контракт:
 *
 * <ul>
 *   <li>handshake требует {@code ?ticket=<...>} (при отсутствии — handshake rejected; покрыто
 *       отдельным тестом).
 *   <li>After valid ticket — session attributes заполняются (user_id / group_id / role /
 *       is_headman).
 *   <li>{@code /topic/group/{groupId}} — non-headman destination. Broker доставляет sub'у, который
 *       подписан на свой groupId.
 * </ul>
 *
 * <p>M03b Группа 4 — ticket-based handshake, {@code WsTicketClient} mocked чтобы не требовать живой
 * auth-service для теста.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "notification.bot-preferences.token=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA",
            "vapid.public-key=BKR2jT5k1Iu93_V8AHx3cpqE",
            "vapid.private-key=test-priv",
            "notification.jwt.public-key-path=classpath:test-public.pem",
            "rutcampustrack.push.cleanup.retention-days=90",
            "notification.ws.allowed-origins=http://localhost,https://ruttrack.site",
            "spring.rabbitmq.listener.simple.auto-startup=false"
        })
@DisplayName("STOMP WebSocket lifecycle (M08 G9)")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class StompIntegrationIT extends ContainerTestBase {

    @DynamicPropertySource
    static void testKeyPath(DynamicPropertyRegistry registry) {
        try {
            java.nio.file.Path keyFile =
                    java.nio.file.Path.of(
                            StompIntegrationIT.class.getResource("/test-public.pem").toURI());
            registry.add("notification.jwt.public-key-path", keyFile::toString);
        } catch (java.net.URISyntaxException e) {
            throw new IllegalStateException(e);
        }
    }

    @LocalServerPort int port;

    @Autowired SimpMessagingTemplate messagingTemplate;

    /** M03b Группа 4 — обычно ходит в auth-service /internal/consume-ws-ticket. */
    @MockitoBean WsTicketClient ticketClient;

    /**
     * Mock PushService — иначе WebPushConfig парсит VAPID через BouncyCastle ECCurve; тестовые
     * VAPID-значения невалидны.
     */
    @MockitoBean nl.martijndwars.webpush.PushService pushService;

    private WebSocketStompClient stompClient;

    @BeforeEach
    void setUp() {
        // SockJS намеренно выключен — подключаемся напрямую как native WebSocket client.
        // WebSocketConfig использует .withSockJS(), но Spring endpoint также принимает
        // native WS handshake на /ws (SockJS — transport-level fallback).
        StandardWebSocketClient wsClient = new StandardWebSocketClient();
        stompClient = new WebSocketStompClient(wsClient);
        stompClient.setMessageConverter(new MappingJackson2MessageConverter());
    }

    @Test
    @DisplayName("happy path — valid ticket → subscribe → server-send → client receives")
    void validTicketSubscribesAndReceivesBroadcast() throws Exception {
        long groupId = 42L;
        String validTicket = UUID.randomUUID().toString();
        when(ticketClient.consume(validTicket))
                .thenReturn(
                        Optional.of(
                                new ConsumeWsTicketResponse(
                                        100L,
                                        UUID.randomUUID().toString(),
                                        1,
                                        1,
                                        "STUDENT",
                                        "ACTIVE",
                                        groupId,
                                        false,
                                        false,
                                        Instant.now())));
        when(ticketClient.admit(any(WsSessionAdmissionRequest.class))).thenReturn(true);

        // SockJS-enabled endpoint: native WebSocket client ходит на /ws/websocket
        // (Spring SockJS adds /websocket suffix для fallback raw WebSocket transport).
        String url = "ws://localhost:" + port + "/ws/websocket?ticket=" + validTicket;
        BlockingQueue<Map<String, Object>> received = new LinkedBlockingQueue<>();

        StompSession session =
                stompClient
                        .connectAsync(
                                url,
                                new WebSocketHttpHeaders(),
                                new StompSessionHandlerAdapter() {})
                        .get(5, TimeUnit.SECONDS);
        try {
            session.subscribe(
                    "/topic/group/" + groupId,
                    new StompFrameHandler() {
                        @Override
                        public Type getPayloadType(StompHeaders headers) {
                            return Map.class;
                        }

                        @Override
                        @SuppressWarnings("unchecked")
                        public void handleFrame(StompHeaders headers, Object payload) {
                            received.offer((Map<String, Object>) payload);
                        }
                    });

            Map<String, Object> wsMessage =
                    Map.of(
                            "type",
                            "lesson.started",
                            "payload",
                            Map.of("lesson_id", 1, "group_id", groupId));
            org.awaitility.Awaitility.await()
                    .atMost(java.time.Duration.ofSeconds(5))
                    .untilAsserted(
                            () -> {
                                messagingTemplate.convertAndSend(
                                        "/topic/group/" + groupId, wsMessage);
                                Map<String, Object> msg = received.poll();
                                assertThat(msg)
                                        .as(
                                                "observable own-group subscription readiness and"
                                                    + " delivery")
                                        .isEqualTo(wsMessage);
                            });
        } finally {
            if (session.isConnected()) session.disconnect();
        }
    }

    @org.junit.jupiter.api.AfterEach
    void closeClient() {
        if (stompClient != null) stompClient.stop();
    }

    @Test
    @DisplayName("handshake без ticket → HTTP Upgrade 401")
    void missingTicketRejectsHandshake() throws Exception {
        assertValidUpgradeControl();
        assertDeniedUpgrade("ws://localhost:" + port + "/ws/websocket");
    }

    @Test
    @DisplayName("handshake с невалидным ticket → HTTP Upgrade 401")
    void invalidTicketRejectsHandshake() throws Exception {
        assertValidUpgradeControl();
        String unknown = UUID.randomUUID().toString();
        when(ticketClient.consume(unknown)).thenReturn(Optional.empty());
        assertDeniedUpgrade("ws://localhost:" + port + "/ws/websocket?ticket=" + unknown);
    }

    private void assertValidUpgradeControl() throws Exception {
        when(ticketClient.admit(any(WsSessionAdmissionRequest.class))).thenReturn(true);
        String ticket = UUID.randomUUID().toString();
        when(ticketClient.consume(ticket))
                .thenReturn(
                        Optional.of(
                                new ConsumeWsTicketResponse(
                                        100L,
                                        UUID.randomUUID().toString(),
                                        1,
                                        1,
                                        "STUDENT",
                                        "ACTIVE",
                                        42L,
                                        false,
                                        false,
                                        Instant.now())));
        try (var http = java.net.http.HttpClient.newHttpClient()) {
            var socket =
                    http.newWebSocketBuilder()
                            .buildAsync(
                                    java.net.URI.create(
                                            "ws://localhost:"
                                                    + port
                                                    + "/ws/websocket?ticket="
                                                    + ticket),
                                    new java.net.http.WebSocket.Listener() {})
                            .get(5, TimeUnit.SECONDS);
            socket.abort();
        }
    }

    private void assertDeniedUpgrade(String url) throws Exception {
        try (var http = java.net.http.HttpClient.newHttpClient()) {
            // A timeout is a test failure; only an observed HTTP 401 proves auth denial.
            try {
                var socket =
                        http.newWebSocketBuilder()
                                .buildAsync(
                                        java.net.URI.create(url),
                                        new java.net.http.WebSocket.Listener() {})
                                .get(5, TimeUnit.SECONDS);
                socket.abort();
                throw new AssertionError("Unauthorized WebSocket upgrade succeeded");
            } catch (ExecutionException error) {
                assertThat(error.getCause())
                        .isInstanceOf(java.net.http.WebSocketHandshakeException.class);
                assertThat(
                                ((java.net.http.WebSocketHandshakeException) error.getCause())
                                        .getResponse()
                                        .statusCode())
                        .isEqualTo(401);
            }
        }
    }
}
