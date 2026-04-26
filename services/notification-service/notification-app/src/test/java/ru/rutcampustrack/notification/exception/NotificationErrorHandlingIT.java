package ru.rutcampustrack.notification.exception;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import ru.rutcampustrack.notification.contract.enums.UserRole;
import ru.rutcampustrack.notification.push.PushSubscriptionRepository;
import ru.rutcampustrack.notification.push.WebPushDeliveryService;
import ru.rutcampustrack.notification.security.RequestContext;
import ru.rutcampustrack.shared.testcontainers.ContainerTestBase;

import java.nio.file.Path;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * M01 Группа 8 acceptance: shared-web {@code GlobalExceptionHandler}
 * подхватывается через component scan и отдаёт RFC 9457 Problem Details
 * на невалидный body / malformed JSON / method-not-allowed / AccessDenied.
 *
 * <p>Расширяет {@link ContainerTestBase} — реальные Mongo/Rabbit через
 * Testcontainers (P2-8/2), spring-properties пробрасываются автоматически.
 */
// M14 G9: legacy-headers-enabled=true override — после G1 (CRIT-01)
// production default флипнут на false, и DualModeUserContextFilter в
// strict mode отбрасывает запросы без Internal JWT с 401. Этот IT
// использует MockMvc + ручную установку RequestContext через bean,
// без реального gateway-issued JWT. Override включает legacy mode
// чтобы handler-маппинг работал (validation/method-not-allowed/access-denied).
// Аналогично SecurityIdorIT в M14 G1 — notification-service не имеет
// общего application-test.yml override (как academic/schedule/attendance).
@SpringBootTest(properties = {
        "vapid.public-key=BKR2jT5k1Iu93_V8AHx3cpqE",
        "vapid.private-key=test-priv",
        "rutcampustrack.security.internal-jwt.legacy-headers-enabled=true"
})
@AutoConfigureMockMvc
class NotificationErrorHandlingIT extends ContainerTestBase {

    @org.springframework.test.context.DynamicPropertySource
    static void testKeyPath(org.springframework.test.context.DynamicPropertyRegistry registry) {
        // JwtHandshakeInterceptor требует реальный файл публичного ключа — используем заглушку
        // из src/test/resources. Путь через URI → правильный absolute-path на всех ОС.
        try {
            Path keyFile = Path.of(
                    NotificationErrorHandlingIT.class.getResource("/test-public.pem").toURI());
            registry.add("notification.jwt.public-key-path", keyFile::toString);
        } catch (java.net.URISyntaxException e) {
            throw new IllegalStateException(e);
        }
    }

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private PushSubscriptionRepository repository;

    @MockitoBean
    private WebPushDeliveryService delivery;

    /** Mock PushService bean — иначе WebPushConfig попытается разобрать VAPID ключи
     *  (BouncyCastle ECCurve), а тестовые значения не валидны. */
    @MockitoBean
    private nl.martijndwars.webpush.PushService pushService;

    @Autowired
    private RequestContext requestContext;

    @BeforeEach
    void setStudentRole() {
        // @RequireRole(STUDENT) — нужен для subscribe/unsubscribe чтобы дойти до validation.
        requestContext.setUserId(1L);
        requestContext.setGroupId(2L);
        requestContext.setRole(UserRole.STUDENT);
    }

    @Test
    @DisplayName("POST /push/subscribe с невалидным body → 400 + problem+json + fieldErrors[]")
    void invalidBodyReturnsRfc7807() throws Exception {
        // SubscribeRequest требует endpoint (NotBlank) и keys (NotNull с p256dh/auth NotBlank).
        // Пустой body нарушает validation → MethodArgumentNotValidException.
        mvc.perform(post("/push/subscribe")
                        .contentType("application/json")
                        .content("{\"endpoint\":\"\",\"keys\":{\"p256dh\":\"\",\"auth\":\"\"}}"))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("Content-Type", containsString("application/problem+json")))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.type").value(containsString("validation-failed")))
                .andExpect(jsonPath("$.title").value("Ошибка валидации"))
                .andExpect(jsonPath("$.instance").value("/push/subscribe"))
                .andExpect(jsonPath("$.timestamp").exists())
                // M11 G0: invalidParams[] → fieldErrors[] (унификация с frontend
                // и 3 другими сервисами academic/schedule/attendance, которые
                // всегда использовали fieldErrors).
                .andExpect(jsonPath("$.fieldErrors").isArray())
                .andExpect(jsonPath("$.fieldErrors.length()").value(
                        org.hamcrest.Matchers.greaterThanOrEqualTo(1)));
    }

    @Test
    @DisplayName("POST /push/subscribe с malformed JSON → 400 malformed-request")
    void malformedJsonReturnsBadRequest() throws Exception {
        mvc.perform(post("/push/subscribe")
                        .contentType("application/json")
                        .content("not-a-json"))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("Content-Type", containsString("application/problem+json")))
                .andExpect(jsonPath("$.type").value(containsString("malformed-request")));
    }

    @Test
    @DisplayName("Неподдерживаемый HTTP метод → 405")
    void methodNotAllowed() throws Exception {
        // /push/vapid-public-key — GET-endpoint. PUT не разрешён.
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .put("/push/vapid-public-key"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().string("Content-Type", containsString("application/problem+json")))
                .andExpect(jsonPath("$.type").value(containsString("method-not-allowed")));
    }

    @Test
    @DisplayName("AccessDenied (доменный) → 403 + problem+json (локальный handler notification-service)")
    void accessDeniedReturns403() throws Exception {
        // Не-STUDENT роль вызовет RoleCheckAspect → наш AccessDeniedException.
        requestContext.setRole(UserRole.TEACHER);

        mvc.perform(delete("/push/subscribe")
                        .contentType("application/json")
                        .content("{\"endpoint\":\"https://push.example.com/x\"}"))
                .andExpect(status().isForbidden())
                .andExpect(header().string("Content-Type", containsString("application/problem+json")))
                .andExpect(jsonPath("$.type").value(containsString("access-denied")))
                .andExpect(jsonPath("$.title").value("Доступ запрещён"));
    }
}
