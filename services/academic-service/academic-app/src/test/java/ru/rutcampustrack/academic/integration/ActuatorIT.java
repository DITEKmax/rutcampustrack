package ru.rutcampustrack.academic.integration;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;

@TestPropertySource(properties = "management.prometheus.metrics.export.enabled=true")
class ActuatorIT extends AbstractAcademicIntegrationTest {

    @Autowired TestRestTemplate restTemplate;
    @Autowired com.fasterxml.jackson.databind.ObjectMapper mapper;

    @Test
    void healthEndpointReturnsUp() throws Exception {
        ResponseEntity<String> response =
                restTemplate.getForEntity("/actuator/health", String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(mapper.readTree(response.getBody()).path("status").asText()).isEqualTo("UP");
    }

    @Test
    void infoEndpointReturns200() {
        ResponseEntity<String> response = restTemplate.getForEntity("/actuator/info", String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void envEndpointReturns404() {
        ResponseEntity<String> response = restTemplate.getForEntity("/actuator/env", String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void beansEndpointReturns404() {
        ResponseEntity<String> response =
                restTemplate.getForEntity("/actuator/beans", String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void heapdumpEndpointReturns404() {
        ResponseEntity<String> response =
                restTemplate.getForEntity("/actuator/heapdump", String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void actuatorIndexShowsOnlyHealthAndInfo() throws Exception {
        ResponseEntity<String> response = restTemplate.getForEntity("/actuator", String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        var links = mapper.readTree(response.getBody()).path("_links");
        java.util.Set<String> names = new java.util.HashSet<>();
        links.fieldNames().forEachRemaining(names::add);
        assertThat(names)
                .containsExactlyInAnyOrder("self", "health", "health-path", "info", "prometheus");
    }
}
