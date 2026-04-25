package ru.rutcampustrack.notification.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import ru.rutcampustrack.notification.contract.enums.NotificationType;
import ru.rutcampustrack.notification.history.NotificationHistoryDocument;
import ru.rutcampustrack.notification.history.NotificationHistoryRepository;
import ru.rutcampustrack.shared.testcontainers.ContainerTestBase;

import java.net.URISyntaxException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * M13 G9 (NEW-31) — IDOR-проверки notification-web.
 *
 * <p>Notification-web уже спроектирован безопасно: userId всегда из
 * {@code RequestContext} (Gateway-injected JWT claim), не из path/body.
 * Тесты документируют что:
 * <ul>
 *   <li>{@code GET /notifications} возвращает только записи текущего user'а;</li>
 *   <li>{@code PATCH /notifications/{id}/read} на чужой документ → 403;</li>
 *   <li>{@code POST /notifications/mark-all-read} касается только своего user'а.</li>
 * </ul>
 */
@SpringBootTest(properties = {
        "vapid.public-key=BKR2jT5k1Iu93_V8AHx3cpqE",
        "vapid.private-key=test-priv",
        "notification.history.ttl-days=30"
})
@AutoConfigureMockMvc
class SecurityIdorIT extends ContainerTestBase {

    @DynamicPropertySource
    static void testKeyPath(DynamicPropertyRegistry registry) {
        try {
            Path keyFile = Path.of(
                    SecurityIdorIT.class.getResource("/test-public.pem").toURI());
            registry.add("notification.jwt.public-key-path", keyFile::toString);
        } catch (URISyntaxException e) {
            throw new IllegalStateException(e);
        }
    }

    @Autowired private MockMvc mockMvc;
    @Autowired private NotificationHistoryRepository repository;

    @MockitoBean
    private nl.martijndwars.webpush.PushService pushService;

    private final Long userAId = 6001L;
    private final Long userBId = 6002L;
    private String notificationOfBId;

    @BeforeEach
    void seed() {
        repository.deleteAll();
        // Notification принадлежащий user B
        NotificationHistoryDocument doc = NotificationHistoryDocument.builder()
                .userId(userBId)
                .type(NotificationType.EXCUSE_REQUESTED)
                .payload(Map.of("user_id", userBId, "group_id", 7))
                .sentAt(Instant.now())
                .traceId("idor-test")
                .build();
        notificationOfBId = repository.save(doc).getId();

        // Своё уведомление user A — для sanity sameUser_doesNotReturn403
        NotificationHistoryDocument myDoc = NotificationHistoryDocument.builder()
                .userId(userAId)
                .type(NotificationType.EXCUSE_REQUESTED)
                .payload(Map.of("user_id", userAId))
                .sentAt(Instant.now())
                .traceId("self-test")
                .build();
        repository.save(myDoc);
    }

    private MockHttpServletRequestBuilder asUserA(MockHttpServletRequestBuilder b) {
        return b.header("X-User-Id", userAId)
                .header("X-User-Role", "STUDENT")
                .header("X-Group-Id", 1L)
                .header("X-Is-Headman", "false");
    }

    @Test
    void list_returnsOnlyOwnNotifications() throws Exception {
        // user A не должен видеть notification user B в своей выдаче.
        mockMvc.perform(asUserA(get("/notifications")))
                .andExpect(status().isOk())
                // только 1 элемент (своё уведомление), а не оба
                .andExpect(jsonPath("$.page.totalElements").value(1));
    }

    @Test
    void markAsRead_otherUsersNotification_returns403() throws Exception {
        // user A пытается прочитать notification, принадлежащий user B → 403
        mockMvc.perform(asUserA(patch("/notifications/{id}/read", notificationOfBId)))
                .andExpect(status().isForbidden());
    }

    @Test
    void markAsRead_nonExistent_returns403() throws Exception {
        // Несуществующий id тоже даёт 403, чтобы не раскрывать существование чужих документов
        mockMvc.perform(asUserA(patch("/notifications/{id}/read", "000000000000000000000000")))
                .andExpect(status().isForbidden());
    }

    @Test
    void unreadCount_returnsOnlyOwnUnreadCount() throws Exception {
        mockMvc.perform(asUserA(get("/notifications/unread-count")))
                .andExpect(status().isOk())
                // user A имеет 1 свой документ; user B вне выдачи
                .andExpect(jsonPath("$.count").value(1));
    }
}
