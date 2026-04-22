package ru.rutcampustrack.notification.ws;

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
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import ru.rutcampustrack.notification.config.WsTicketClient;
import ru.rutcampustrack.shared.testcontainers.ContainerTestBase;

import java.lang.reflect.Type;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * M08 G9 (P1-9) — full WebSocket lifecycle integration test for notification-web.
 *
 * <p>Спускает реальный Spring Boot + WebSocket сервер (RANDOM_PORT), подключается
 * как клиент через {@link StandardWebSocketClient} (ws://…, SockJS не нужен —
 * Spring STOMP client сам умеет native WebSocket), подписывается на group topic,
 * публикует через server-side {@link SimpMessagingTemplate} и проверяет, что
 * клиент получил сообщение в заданное окно времени.
 *
 * <p>Покрывает контракт:
 * <ul>
 *   <li>handshake требует {@code ?ticket=<...>} (при отсутствии — handshake
 *       rejected; покрыто отдельным тестом).</li>
 *   <li>After valid ticket — session attributes заполняются (user_id / group_id /
 *       role / is_headman).</li>
 *   <li>{@code /topic/group/{groupId}} — non-headman destination. Broker
 *       доставляет sub'у, который подписан на свой groupId.</li>
 * </ul>
 *
 * <p>M03b Группа 4 — ticket-based handshake, {@code WsTicketClient} mocked
 * чтобы не требовать живой auth-service для теста.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "vapid.public-key=BKR2jT5k1Iu93_V8AHx3cpqE",
                "vapid.private-key=test-priv",
                "notification.jwt.public-key-path=classpath:test-public.pem",
                "rutcampustrack.push.cleanup.retention-days=90",
                "notification.ws.allowed-origins=http://localhost,https://ruttrack.site",
                "spring.rabbitmq.listener.simple.auto-startup=false"
        }
)
@DisplayName("STOMP WebSocket lifecycle (M08 G9)")
class StompIntegrationIT extends ContainerTestBase {

    @DynamicPropertySource
    static void testKeyPath(DynamicPropertyRegistry registry) {
        try {
            java.nio.file.Path keyFile = java.nio.file.Path.of(
                    StompIntegrationIT.class.getResource("/test-public.pem").toURI());
            registry.add("notification.jwt.public-key-path", keyFile::toString);
        } catch (java.net.URISyntaxException e) {
            throw new IllegalStateException(e);
        }
    }

    @LocalServerPort
    int port;

    @Autowired
    SimpMessagingTemplate messagingTemplate;

    /** M03b Группа 4 — обычно ходит в auth-service /internal/consume-ws-ticket. */
    @MockitoBean
    WsTicketClient ticketClient;

    /** Mock PushService — иначе WebPushConfig парсит VAPID через BouncyCastle
     *  ECCurve; тестовые VAPID-значения невалидны. */
    @MockitoBean
    nl.martijndwars.webpush.PushService pushService;

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
        String validTicket = "abc-uuid-ticket";
        when(ticketClient.consume(validTicket)).thenReturn(Optional.of(
                new WsTicketClient.TicketIdentity(100L, "STUDENT", groupId, false, Instant.now())));

        // SockJS-enabled endpoint: native WebSocket client ходит на /ws/websocket
        // (Spring SockJS adds /websocket suffix для fallback raw WebSocket transport).
        String url = "ws://localhost:" + port + "/ws/websocket?ticket=" + validTicket;
        BlockingQueue<Map<String, Object>> received = new LinkedBlockingQueue<>();

        StompSession session = stompClient
                .connectAsync(url, new WebSocketHttpHeaders(), new StompSessionHandlerAdapter() {})
                .get(5, TimeUnit.SECONDS);
        try {
            session.subscribe("/topic/group/" + groupId, new StompFrameHandler() {
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

            // Дожидаемся подписки, иначе convertAndSend может уйти до subscription
            // (SubscribeAck приходит асинхронно, но Spring send fallback'ом буферизует
            // только ЕСЛИ broker это умеет — in-memory broker не буферизует).
            TimeUnit.MILLISECONDS.sleep(300);

            Map<String, Object> wsMessage = Map.of(
                    "type", "lesson.started",
                    "payload", Map.of("lesson_id", 1, "group_id", groupId));
            messagingTemplate.convertAndSend("/topic/group/" + groupId, wsMessage);

            Map<String, Object> msg = received.poll(5, TimeUnit.SECONDS);
            assertThat(msg).as("client должен получить message с /topic/group/42").isNotNull();
            assertThat(msg.get("type")).isEqualTo("lesson.started");
            assertThat(msg).containsKey("payload");
        } finally {
            if (session.isConnected()) session.disconnect();
        }
    }

    @Test
    @DisplayName("handshake без ticket → connection fails")
    void missingTicketRejectsHandshake() {
        String url = "ws://localhost:" + port + "/ws/websocket";

        // Пустая подписка — ожидаем ExecutionException с 401/Handshake failure.
        assertThatThrownBy(() -> stompClient
                .connectAsync(url, new WebSocketHttpHeaders(), new StompSessionHandlerAdapter() {})
                .get(5, TimeUnit.SECONDS))
                .isInstanceOfAny(ExecutionException.class, TimeoutException.class);
    }

    @Test
    @DisplayName("handshake с невалидным ticket → connection fails")
    void invalidTicketRejectsHandshake() {
        when(ticketClient.consume("bad-ticket")).thenReturn(Optional.empty());

        String url = "ws://localhost:" + port + "/ws/websocket?ticket=bad-ticket";

        assertThatThrownBy(() -> stompClient
                .connectAsync(url, new WebSocketHttpHeaders(), new StompSessionHandlerAdapter() {})
                .get(5, TimeUnit.SECONDS))
                .isInstanceOfAny(ExecutionException.class, TimeoutException.class);
    }

}
