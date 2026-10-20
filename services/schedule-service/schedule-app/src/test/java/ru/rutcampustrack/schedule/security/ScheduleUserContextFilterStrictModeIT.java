package ru.rutcampustrack.schedule.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import ru.rutcampustrack.schedule.integration.AbstractScheduleIntegrationTest;
import ru.rutcampustrack.shared.security.InternalJwtTestFactory;

import java.util.UUID;

@AutoConfigureMockMvc
@TestPropertySource(
        properties = "rutcampustrack.security.internal-jwt.legacy-headers-enabled=false")
class ScheduleUserContextFilterStrictModeIT extends AbstractScheduleIntegrationTest {

    private static final UUID SESSION_ID = UUID.fromString("dddddddd-dddd-4ddd-8ddd-dddddddddddd");

    @Autowired private MockMvc mockMvc;
    @Autowired private org.springframework.boot.test.web.client.TestRestTemplate http;

    @Autowired private InternalJwtTestFactory factory;

    @org.junit.jupiter.api.BeforeEach
    void isolateStrictModeDataset() {
        resetScheduleData();
    }

    @Test
    void noHeaders_strictMode_returns401() throws Exception {
        mockMvc.perform(get("/schedule/items")).andExpect(status().isUnauthorized());
    }

    @Test
    void legacyHeaders_strictMode_returns401() throws Exception {
        mockMvc.perform(
                        get("/schedule/items")
                                .header("X-User-Id", "1")
                                .header("X-User-Role", "ADMIN"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void validInternalToken_strictMode_passes() throws Exception {
        String token =
                factory.validToken(1L, SESSION_ID, 1L, 1L, "ADMIN", "ACTIVE", null, false, false);
        var headers = new org.springframework.http.HttpHeaders();
        headers.set("X-Internal-Token", token);
        var response =
                http.exchange(
                        "/schedule/items?groupId=10&semesterId=1",
                        org.springframework.http.HttpMethod.GET,
                        new org.springframework.http.HttpEntity<>(headers),
                        com.fasterxml.jackson.databind.JsonNode.class);
        assertThat(response.getStatusCode()).isEqualTo(org.springframework.http.HttpStatus.OK);
        assertThat(response.getBody().path("page").path("totalElements").asLong()).isZero();
    }
}
