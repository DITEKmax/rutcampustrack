package ru.rutcampustrack.auth.integration;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class ActuatorIT extends AbstractIntegrationTest {

    @Autowired TestRestTemplate restTemplate;

    @Test
    void healthEndpointReturnsUp() throws Exception {
        ResponseEntity<String> response =
                restTemplate.getForEntity("/actuator/health", String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(
                        new com.fasterxml.jackson.databind.ObjectMapper()
                                .readTree(response.getBody())
                                .path("status")
                                .asText())
                .isEqualTo("UP");
    }

    @Test
    void infoEndpointNotPublic() {
        // REC-03: Only /actuator/health is public; /actuator/info requires authentication
        ResponseEntity<String> response = restTemplate.getForEntity("/actuator/info", String.class);
        assertThat(response.getStatusCode().value()).isEqualTo(403);
    }

    @Test
    void envEndpointNotExposed() throws Exception {
        // Not in management.endpoints.web.exposure.include → 404
        ResponseEntity<String> response = authenticatedGet("/actuator/env");
        assertThat(response.getStatusCode().value()).isEqualTo(404);
    }

    @Test
    void beansEndpointNotExposed() throws Exception {
        ResponseEntity<String> response = authenticatedGet("/actuator/beans");
        assertThat(response.getStatusCode().value()).isEqualTo(404);
    }

    @Test
    void heapdumpEndpointNotExposed() throws Exception {
        ResponseEntity<String> response = authenticatedGet("/actuator/heapdump");
        assertThat(response.getStatusCode().value()).isEqualTo(404);
    }

    @Test
    void actuatorIndexNotPublic() {
        // REC-03: /actuator index is not in permitAll
        ResponseEntity<String> response = restTemplate.getForEntity("/actuator", String.class);
        assertThat(response.getStatusCode().value()).isEqualTo(403);
    }

    private ResponseEntity<String> authenticatedGet(String path) throws Exception {
        var login =
                restTemplate.postForEntity(
                        "/auth/login",
                        new ru.rutcampustrack.auth.dto.LoginRequest("student", "password"),
                        ru.rutcampustrack.auth.dto.TokenResponse.class);
        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(login.getBody()).isNotNull();
        var headers = new org.springframework.http.HttpHeaders();
        headers.setBearerAuth(login.getBody().accessToken());
        var response =
                restTemplate.exchange(
                        path,
                        org.springframework.http.HttpMethod.GET,
                        new org.springframework.http.HttpEntity<>(headers),
                        String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        var body = new com.fasterxml.jackson.databind.ObjectMapper().readTree(response.getBody());
        assertThat(body.isObject()).isTrue();
        assertThat(body.path("status").asInt()).isEqualTo(404);
        assertThat(body.has("propertySources") || body.has("contexts") || body.has("beans"))
                .isFalse();
        return response;
    }
}
