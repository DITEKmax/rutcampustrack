package ru.rutcampustrack.schedule.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

class ActuatorIT extends AbstractScheduleIntegrationTest {

    @Autowired
    TestRestTemplate restTemplate;

    @Test
    void healthEndpointReturnsUp() {
        ResponseEntity<String> response = restTemplate.getForEntity("/actuator/health", String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("UP");
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
        ResponseEntity<String> response = restTemplate.getForEntity("/actuator/beans", String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void heapdumpEndpointReturns404() {
        ResponseEntity<String> response = restTemplate.getForEntity("/actuator/heapdump", String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void actuatorIndexShowsOnlyHealthAndInfo() {
        ResponseEntity<String> response = restTemplate.getForEntity("/actuator", String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("health");
        assertThat(response.getBody()).contains("info");
        assertThat(response.getBody()).doesNotContain("\"env\"");
        assertThat(response.getBody()).doesNotContain("\"beans\"");
        assertThat(response.getBody()).doesNotContain("\"heapdump\"");
    }
}
