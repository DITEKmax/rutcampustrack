package ru.rutcampustrack.notification.alert;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * M04 Группа 9 — unit tests для /internal/alert.
 *
 * <p>Покрывает:
 * <ul>
 *   <li>Happy path: valid Bearer → alerts publishes each via {@link AlertPublisher}.</li>
 *   <li>Auth: missing / wrong / empty header / empty secret property — 401.</li>
 *   <li>Malformed body (no alerts array) — 400.</li>
 *   <li>Каждый alert в пачке → отдельный publish call с нужными полями.</li>
 * </ul>
 */
class AlertControllerTest {

    private AlertPublisher publisher;
    private AlertController controller;

    @BeforeEach
    void setUp() {
        publisher = mock(AlertPublisher.class);
        controller = new AlertController(publisher, new AlertProperties("secret-token"));
    }

    @Test
    void happyPath_publishesEachAlertAndReturns200() {
        Map<String, Object> body = Map.of(
                "alerts", List.of(
                        Map.of(
                                "status", "firing",
                                "labels", Map.of(
                                        "alertname", "ServiceDown",
                                        "severity", "critical",
                                        "job", "auth-service"
                                ),
                                "annotations", Map.of(
                                        "summary", "auth-service недоступен",
                                        "description", "Scrape failed > 1m"
                                )
                        ),
                        Map.of(
                                "status", "resolved",
                                "labels", Map.of("alertname", "DiskUsageHigh", "severity", "warning"),
                                "annotations", Map.of()
                        )
                )
        );

        ResponseEntity<?> response = controller.receive("Bearer secret-token", body);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isInstanceOfSatisfying(Map.class,
                m -> assertThat(m.get("published")).isEqualTo(2));

        var captor = forClass(AlertPayload.class);
        verify(publisher, times(2)).publishFired(captor.capture());
        List<AlertPayload> published = captor.getAllValues();
        assertThat(published.get(0).name()).isEqualTo("ServiceDown");
        assertThat(published.get(0).severity()).isEqualTo("critical");
        assertThat(published.get(0).status()).isEqualTo("firing");
        assertThat(published.get(1).name()).isEqualTo("DiskUsageHigh");
        assertThat(published.get(1).status()).isEqualTo("resolved");
    }

    @Test
    void missingAuthorization_returns401_andDoesNotPublish() {
        ResponseEntity<?> response = controller.receive(null, Map.of("alerts", List.of()));
        assertThat(response.getStatusCode().value()).isEqualTo(401);
        verify(publisher, never()).publishFired(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void wrongBearerToken_returns401() {
        ResponseEntity<?> response = controller.receive("Bearer wrong-token", Map.of("alerts", List.of()));
        assertThat(response.getStatusCode().value()).isEqualTo(401);
        verify(publisher, never()).publishFired(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void nonBearerAuthorization_returns401() {
        ResponseEntity<?> response = controller.receive("Basic abc123", Map.of("alerts", List.of()));
        assertThat(response.getStatusCode().value()).isEqualTo(401);
    }

    @Test
    void emptySecretProperty_rejectsAllRequests() {
        AlertController c = new AlertController(publisher, new AlertProperties(""));
        ResponseEntity<?> response = c.receive("Bearer whatever", Map.of("alerts", List.of()));
        assertThat(response.getStatusCode().value()).isEqualTo(401);
    }

    @Test
    void malformedBody_noAlertsArray_returns400() {
        ResponseEntity<?> response = controller.receive("Bearer secret-token", Map.of("version", "4"));
        assertThat(response.getStatusCode().value()).isEqualTo(400);
        verify(publisher, never()).publishFired(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void emptyAlertsArray_returns200WithZero() {
        ResponseEntity<?> response = controller.receive("Bearer secret-token", Map.of("alerts", List.of()));
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isInstanceOfSatisfying(Map.class,
                m -> assertThat(m.get("published")).isEqualTo(0));
    }
}
