package ru.rutcampustrack.auth.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

class ActuatorIT extends AbstractIntegrationTest {

    @Autowired
    TestRestTemplate restTemplate;

    @Test
    void healthEndpointReturnsUp() {
        ResponseEntity<String> response = restTemplate.getForEntity("/actuator/health", String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("UP");
    }

    @Test
    void infoEndpointRequiresAuth() {
        // REC-03: Only /actuator/health is public; /actuator/info requires authentication
        ResponseEntity<String> response = restTemplate.getForEntity("/actuator/info", String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void envEndpointRequiresAuth() {
        ResponseEntity<String> response = restTemplate.getForEntity("/actuator/env", String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void beansEndpointRequiresAuth() {
        ResponseEntity<String> response = restTemplate.getForEntity("/actuator/beans", String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void heapdumpEndpointRequiresAuth() {
        ResponseEntity<String> response = restTemplate.getForEntity("/actuator/heapdump", String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void actuatorIndexRequiresAuth() {
        // REC-03: /actuator index is not public
        ResponseEntity<String> response = restTemplate.getForEntity("/actuator", String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
