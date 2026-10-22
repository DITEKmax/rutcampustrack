package ru.rutcampustrack.notification.exception;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;

import ru.rutcampustrack.notification.push.PushSubscriptionRepository;
import ru.rutcampustrack.notification.security.RequestContext;

/** MVC exception mapping boundary; signed authorization is covered in SecurityIdorIT. */
class NotificationErrorHandlingIT {
    private MockMvc mvc;

    @BeforeEach
    void setStudentRole() {
        var context = org.mockito.Mockito.mock(RequestContext.class);
        var push =
                new ru.rutcampustrack.notification.push.PushController(
                        org.mockito.Mockito.mock(PushSubscriptionRepository.class),
                        context,
                        "synthetic-vapid-key");
        mvc =
                org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(
                                push, new DeniedController())
                        .setControllerAdvice(
                                new ru.rutcampustrack.shared.web.exception.GlobalExceptionHandler(),
                                new NotificationExceptionHandler())
                        .build();
    }

    @org.springframework.web.bind.annotation.RestController
    static class DeniedController {
        @org.springframework.web.bind.annotation.DeleteMapping("/synthetic-domain-denial")
        void denied() {
            throw new AccessDeniedException("Synthetic domain denial");
        }
    }

    @Test
    @DisplayName("POST /push/subscribe с невалидным body → 400 + problem+json + fieldErrors[]")
    void invalidBodyReturnsRfc7807() throws Exception {
        // SubscribeRequest требует endpoint (NotBlank) и keys (NotNull с p256dh/auth NotBlank).
        // Пустой body нарушает validation → MethodArgumentNotValidException.
        mvc.perform(
                        post("/push/subscribe")
                                .contentType("application/json")
                                .content(
                                        "{\"endpoint\":\"\",\"keys\":{\"p256dh\":\"\",\"auth\":\"\"}}"))
                .andExpect(status().isBadRequest())
                .andExpect(
                        header().string("Content-Type", containsString("application/problem+json")))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.type").value(containsString("validation-failed")))
                .andExpect(jsonPath("$.title").value("Ошибка валидации"))
                .andExpect(jsonPath("$.instance").value("/push/subscribe"))
                .andExpect(jsonPath("$.timestamp").exists())
                // M11 G0: invalidParams[] → fieldErrors[] (унификация с frontend
                // и 3 другими сервисами academic/schedule/attendance, которые
                // всегда использовали fieldErrors).
                .andExpect(jsonPath("$.fieldErrors").isArray())
                .andExpect(
                        jsonPath("$.fieldErrors.length()")
                                .value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)));
    }

    @Test
    @DisplayName("POST /push/subscribe с malformed JSON → 400 malformed-request")
    void malformedJsonReturnsBadRequest() throws Exception {
        mvc.perform(post("/push/subscribe").contentType("application/json").content("not-a-json"))
                .andExpect(status().isBadRequest())
                .andExpect(
                        header().string("Content-Type", containsString("application/problem+json")))
                .andExpect(jsonPath("$.type").value(containsString("malformed-request")));
    }

    @Test
    @DisplayName("Неподдерживаемый HTTP метод → 405")
    void methodNotAllowed() throws Exception {
        // /push/vapid-public-key — GET-endpoint. PUT не разрешён.
        mvc.perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(
                                "/push/vapid-public-key"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(
                        header().string("Content-Type", containsString("application/problem+json")))
                .andExpect(jsonPath("$.type").value(containsString("method-not-allowed")));
    }

    @Test
    @DisplayName(
            "AccessDenied (доменный) → 403 + problem+json (локальный handler notification-service)")
    void accessDeniedReturns403() throws Exception {
        mvc.perform(delete("/synthetic-domain-denial"))
                .andExpect(status().isForbidden())
                .andExpect(
                        header().string("Content-Type", containsString("application/problem+json")))
                .andExpect(jsonPath("$.type").value(containsString("access-denied")))
                .andExpect(jsonPath("$.title").value("Доступ запрещён"));
    }
}
