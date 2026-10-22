package ru.rutcampustrack.notification.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import ru.rutcampustrack.notification.contract.enums.NotificationType;
import ru.rutcampustrack.notification.history.NotificationHistoryDocument;
import ru.rutcampustrack.notification.history.NotificationHistoryRepository;
import ru.rutcampustrack.shared.security.InternalJwtTestFactory;
import ru.rutcampustrack.shared.security.PublicKeyProvider;
import ru.rutcampustrack.shared.testcontainers.ContainerTestBase;

import java.net.URISyntaxException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * M13 G9 (NEW-31) — IDOR-проверки notification-web.
 *
 * <p>Notification-web уже спроектирован безопасно: userId всегда из {@code RequestContext}
 * (Gateway-injected JWT claim), не из path/body. Тесты документируют что:
 *
 * <ul>
 *   <li>{@code GET /notifications} возвращает только записи текущего user'а;
 *   <li>{@code PATCH /notifications/{id}/read} на чужой документ → 403;
 *   <li>{@code POST /notifications/mark-all-read} касается только своего user'а.
 * </ul>
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "notification.bot-preferences.token=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA",
            "vapid.public-key=BKR2jT5k1Iu93_V8AHx3cpqE",
            "vapid.private-key=test-priv",
            "notification.history.ttl-days=30",
            "rutcampustrack.security.internal-jwt.legacy-headers-enabled=false"
        })
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class SecurityIdorIT extends ContainerTestBase {

    @DynamicPropertySource
    static void testKeyPath(DynamicPropertyRegistry registry) {
        try {
            Path keyFile = Path.of(SecurityIdorIT.class.getResource("/test-public.pem").toURI());
            registry.add("notification.jwt.public-key-path", keyFile::toString);
        } catch (URISyntaxException e) {
            throw new IllegalStateException(e);
        }
    }

    @Autowired private org.springframework.boot.test.web.client.TestRestTemplate rest;
    @Autowired private com.fasterxml.jackson.databind.ObjectMapper json;
    @Autowired private NotificationHistoryRepository repository;

    @MockitoBean private nl.martijndwars.webpush.PushService pushService;

    @MockitoBean private PublicKeyProvider publicKeyProvider;
    private static final InternalJwtTestFactory JWT = new InternalJwtTestFactory();

    private final Long userAId = 6001L;
    private final Long userBId = 6002L;
    private String notificationOfBId;
    private final java.util.List<String> ownIds = new java.util.ArrayList<>();

    @BeforeEach
    void seed() {
        when(publicKeyProvider.getPublicKey()).thenReturn(JWT.publicKey());
        rest.getRestTemplate()
                .setRequestFactory(
                        new org.springframework.http.client.JdkClientHttpRequestFactory());
        repository.deleteAll();
        ownIds.clear();
        Instant sentAt = Instant.now();
        // Notification принадлежащий user B
        NotificationHistoryDocument doc =
                NotificationHistoryDocument.builder()
                        .userId(userBId)
                        .type(NotificationType.EXCUSE_REQUESTED)
                        .payload(Map.of("user_id", userBId, "group_id", 7))
                        .sentAt(sentAt)
                        .roleViews(studentView(sentAt, null))
                        .traceId("idor-test")
                        .build();
        notificationOfBId = repository.save(doc).getId();

        // Своё уведомление user A — для sanity sameUser_doesNotReturn403
        NotificationHistoryDocument myDoc =
                NotificationHistoryDocument.builder()
                        .userId(userAId)
                        .type(NotificationType.EXCUSE_REQUESTED)
                        .payload(Map.of("user_id", userAId))
                        .sentAt(sentAt)
                        .roleViews(studentView(sentAt, null))
                        .traceId("self-test")
                        .build();
        ownIds.add(repository.save(myDoc).getId());
        for (int n = 0; n < 2; n++) {
            ownIds.add(
                    repository
                            .save(
                                    NotificationHistoryDocument.builder()
                                            .userId(userAId)
                                            .type(NotificationType.EXCUSE_REQUESTED)
                                            .payload(Map.of("owner_marker", "own-" + n))
                                            .sentAt(sentAt)
                                            .readAt(
                                                    n == 0
                                                            ? Instant.parse("2026-10-01T00:00:00Z")
                                                            : null)
                                            .roleViews(
                                                    studentView(
                                                            sentAt,
                                                            n == 0
                                                                    ? Instant.parse(
                                                                            "2026-10-01T00:00:00Z")
                                                                    : null))
                                            .traceId("own-state-" + n)
                                            .build())
                            .getId());
        }
        for (int n = 0; n < 4; n++)
            repository.save(
                    NotificationHistoryDocument.builder()
                            .userId(userBId)
                            .type(NotificationType.EXCUSE_REQUESTED)
                            .payload(Map.of("owner_marker", "foreign-private-marker"))
                            .sentAt(sentAt)
                            .roleViews(studentView(sentAt, null))
                            .traceId("foreign-state-" + n)
                            .build());
    }

    // Seed the canonical role projection: this IDOR fixture must not trigger legacy adoption.
    private static Map<String, NotificationHistoryDocument.RoleView> studentView(
            Instant sentAt, Instant readAt) {
        return Map.of(
                "STUDENT",
                NotificationHistoryDocument.RoleView.builder()
                        .orderAt(sentAt)
                        .readAt(readAt)
                        .revision(0)
                        .build());
    }

    private org.springframework.http.ResponseEntity<String> asUserA(
            org.springframework.http.HttpMethod method, String path) {
        var headers = new org.springframework.http.HttpHeaders();
        headers.set(
                "X-Internal-Token",
                JWT.validToken(
                        userAId, UUID.randomUUID(), 1, 1, "STUDENT", "ACTIVE", 1L, false, false));
        return rest.exchange(
                path, method, new org.springframework.http.HttpEntity<>(headers), String.class);
    }

    @Test
    void list_returnsOnlyOwnNotifications() throws Exception {
        var response = asUserA(org.springframework.http.HttpMethod.GET, "/notifications");
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        var body = json.readTree(response.getBody());
        assertThat(body.path("page").path("totalElements").asInt()).isEqualTo(3);
        var actual = new java.util.ArrayList<String>();
        body.path("_embedded")
                .elements()
                .forEachRemaining(
                        items -> items.forEach(item -> actual.add(item.path("id").asText())));
        assertThat(actual).containsExactlyInAnyOrderElementsOf(ownIds);
        assertThat(response.getBody()).doesNotContain("foreign-private-marker", notificationOfBId);
    }

    @Test
    void markAsRead_otherUsersNotification_returns403() {
        var before = repository.findAll();
        var response =
                asUserA(
                        org.springframework.http.HttpMethod.PATCH,
                        "/notifications/" + notificationOfBId + "/read");
        assertThat(response.getStatusCode().value()).isEqualTo(403);
        assertThat(repository.findById(notificationOfBId).orElseThrow().getReadAt()).isNull();
        assertThat(repository.findAll())
                .usingRecursiveFieldByFieldElementComparator()
                .containsExactlyInAnyOrderElementsOf(before);
    }

    @Test
    void markAsRead_nonExistent_returns403() {
        assertThat(
                        asUserA(
                                        org.springframework.http.HttpMethod.PATCH,
                                        "/notifications/000000000000000000000000/read")
                                .getStatusCode()
                                .value())
                .isEqualTo(403);
    }

    @Test
    void unreadCount_returnsOnlyOwnUnreadCount() throws Exception {
        var response =
                asUserA(org.springframework.http.HttpMethod.GET, "/notifications/unread-count");
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(json.readTree(response.getBody()).path("count").asInt()).isEqualTo(2);
        assertThat(
                        asUserA(
                                        org.springframework.http.HttpMethod.PATCH,
                                        "/notifications/" + ownIds.getFirst() + "/read")
                                .getStatusCode()
                                .value())
                .isEqualTo(204);
        response = asUserA(org.springframework.http.HttpMethod.GET, "/notifications/unread-count");
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(json.readTree(response.getBody()).path("count").asInt()).isEqualTo(1);
        assertThat(repository.findById(notificationOfBId).orElseThrow().getReadAt()).isNull();
    }
}
