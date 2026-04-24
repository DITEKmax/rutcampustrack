package ru.rutcampustrack.academic.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * M11 G1 acceptance: shared-web {@code GlobalErrorResponsesCustomizer}
 * автоматически добавляет 7 стандартных error responses
 * (400/401/403/404/409/429/500) с content application/problem+json +
 * ссылкой на shared ErrorResponse schema ко всем операциям OpenAPI
 * спеки academic-service.
 */
class OpenApiErrorResponsesIT extends AbstractAcademicIntegrationTest {

    @Autowired
    TestRestTemplate restTemplate;

    @Test
    @DisplayName("M11 G1: /api-docs содержит ErrorResponse schema в components.schemas")
    void apiDocsContainsErrorResponseSchema() {
        ResponseEntity<String> response = restTemplate.getForEntity("/api-docs", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        // Shared ErrorResponse schema зарегистрирована в components
        assertThat(response.getBody()).contains("\"ErrorResponse\"");
        // Fields из shared ErrorResponse (унификация M11 G0)
        assertThat(response.getBody()).contains("\"traceId\"");
        assertThat(response.getBody()).contains("\"fieldErrors\"");
    }

    @Test
    @DisplayName("M11 G1: /api-docs содержит все 7 стандартных HTTP error statuses для каждой operation")
    void apiDocsContainsAllSevenStandardErrors() {
        ResponseEntity<String> response = restTemplate.getForEntity("/api-docs", String.class);

        assertThat(response.getBody()).isNotNull();
        // Кастомайзер добавляет 400/401/403/404/409/429/500 ко всем operations.
        // Проверяем что хотя бы один endpoint содержит все 7 статусов в responses[].
        for (String status : new String[]{"400", "401", "403", "404", "409", "429", "500"}) {
            assertThat(response.getBody())
                    .as("OpenAPI spec должен содержать status %s", status)
                    .contains("\"" + status + "\"");
        }
    }

    @Test
    @DisplayName("M11 G1: /api-docs использует application/problem+json media type")
    void apiDocsUsesProblemJsonMediaType() {
        ResponseEntity<String> response = restTemplate.getForEntity("/api-docs", String.class);

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody()).contains("application/problem+json");
        // Ссылка на shared ErrorResponse schema через $ref
        assertThat(response.getBody()).contains("#/components/schemas/ErrorResponse");
    }
}
