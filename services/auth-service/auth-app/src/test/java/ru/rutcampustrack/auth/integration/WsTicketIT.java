package ru.rutcampustrack.auth.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import ru.rutcampustrack.auth.dto.ConsumeWsTicketRequest;
import ru.rutcampustrack.auth.dto.ConsumeWsTicketResponse;
import ru.rutcampustrack.auth.dto.LoginRequest;
import ru.rutcampustrack.auth.dto.TokenResponse;
import ru.rutcampustrack.auth.dto.WsTicketResponse;
import ru.rutcampustrack.auth.dto.WsSessionAdmissionRequest;

import static org.assertj.core.api.Assertions.assertThat;

class WsTicketIT extends AbstractIntegrationTest {

    private static final String INTERNAL_SECRET =
            "test-internal-issuer-secret-32-bytes-or-more-for-test-env";

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void issueTicket_withValidAccessToken_returnsTicket() {
        String accessToken = loginAndGetAccessToken("student");

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        ResponseEntity<WsTicketResponse> response = restTemplate.exchange(
                "/auth/ws-ticket", HttpMethod.POST,
                new HttpEntity<>(null, headers), WsTicketResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().ticket()).isNotBlank();
        assertThat(response.getBody().expiresAt()).isNotNull();
    }

    @Test
    void issueTicket_withoutAuthentication_returnsUnauthenticated() {
        // Spring Security default возвращает 403 Forbidden (вместо 401 Unauthorized)
        // для anon access без AuthenticationEntryPoint — матчит поведение
        // существующих authenticated endpoints (/auth/change-password).
        ResponseEntity<String> response = restTemplate.exchange(
                "/auth/ws-ticket", HttpMethod.POST, HttpEntity.EMPTY, String.class);

        assertThat(response.getStatusCode())
                .isIn(HttpStatus.UNAUTHORIZED, HttpStatus.FORBIDDEN);
    }

    @Test
    void consumeTicket_happyPath_returnsFullIdentity() {
        String accessToken = loginAndGetAccessToken("student");
        String ticket = issueTicket(accessToken);

        ResponseEntity<ConsumeWsTicketResponse> response = consume(ticket);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().userId()).isPositive();
        assertThat(response.getBody().sessionId()).isNotBlank();
        assertThat(response.getBody().sessionVersion()).isPositive();
        assertThat(response.getBody().rolesVersion()).isPositive();
        assertThat(response.getBody().role()).isEqualTo("STUDENT");
        assertThat(response.getBody().status()).isEqualTo("ACTIVE");
        assertThat(response.getBody().groupId()).isPositive();
        assertThat(response.getBody().isHeadman()).isFalse();
        assertThat(response.getBody().readOnly()).isFalse();

        ResponseEntity<Void> admitted = admit(response.getBody().admissionRequest());
        assertThat(admitted.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(admitted.getHeaders().getCacheControl()).contains("no-store");
    }

    @Test
    void consumeTicket_revokedBeforeConsume_isRejectedAndBurned() {
        String accessToken = loginAndGetAccessToken("student");
        String ticket = issueTicket(accessToken);
        Long userId = jdbc.queryForObject(
                "SELECT id FROM users WHERE login = ?", Long.class, "student");
        String originalStatus = jdbc.queryForObject(
                "SELECT status FROM user_role_grants WHERE user_id = ? AND role = 'student'",
                String.class, userId);
        int changed = jdbc.update("""
                UPDATE user_role_grants
                   SET status = 'suspended', updated_at = CURRENT_TIMESTAMP
                 WHERE user_id = ? AND role = 'student'
                """, userId);
        assertThat(changed).isEqualTo(1);

        try {
            ResponseEntity<String> rejected = restTemplate.exchange(
                    "/internal/consume-ws-ticket", HttpMethod.POST,
                    internalEntity(new ConsumeWsTicketRequest(ticket)), String.class);
            assertThat(rejected.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

            ResponseEntity<String> secondConsume = restTemplate.exchange(
                    "/internal/consume-ws-ticket", HttpMethod.POST,
                    internalEntity(new ConsumeWsTicketRequest(ticket)), String.class);
            assertThat(secondConsume.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        } finally {
            jdbc.update("""
                    UPDATE user_role_grants
                       SET status = ?, updated_at = CURRENT_TIMESTAMP
                     WHERE user_id = ? AND role = 'student'
                    """, originalStatus, userId);
        }
    }

    @Test
    void liveAdmission_afterSessionLogout_returnsUnauthorized() {
        String accessToken = loginAndGetAccessToken("student");
        ConsumeWsTicketResponse identity = consume(issueTicket(accessToken)).getBody();
        assertThat(identity).isNotNull();
        assertThat(logout(accessToken).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        ResponseEntity<String> response = restTemplate.exchange(
                "/internal/auth/admit-ws-session", HttpMethod.POST,
                internalEntity(identity.admissionRequest()), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void consumeTicket_twice_secondReturns404() {
        String accessToken = loginAndGetAccessToken("student");
        String ticket = issueTicket(accessToken);

        // First consume — ok
        ResponseEntity<ConsumeWsTicketResponse> first = consume(ticket);
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Second consume — 404 (already consumed)
        ResponseEntity<String> second = restTemplate.exchange(
                "/internal/consume-ws-ticket", HttpMethod.POST,
                internalEntity(new ConsumeWsTicketRequest(ticket)), String.class);
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void consumeTicket_unknownTicket_returns404() {
        ResponseEntity<String> response = restTemplate.exchange(
                "/internal/consume-ws-ticket", HttpMethod.POST,
                internalEntity(new ConsumeWsTicketRequest("nonexistent-uuid")), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void consumeTicket_withoutInternalSecret_returns401() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> response = restTemplate.exchange(
                "/internal/consume-ws-ticket", HttpMethod.POST,
                new HttpEntity<>(new ConsumeWsTicketRequest("some-ticket"), headers), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    private String loginAndGetAccessToken(String login) {
        ResponseEntity<TokenResponse> response = restTemplate.postForEntity(
                "/auth/login", new LoginRequest(login, "password"), TokenResponse.class);
        return response.getBody().accessToken();
    }

    private String issueTicket(String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        ResponseEntity<WsTicketResponse> response = restTemplate.exchange(
                "/auth/ws-ticket", HttpMethod.POST,
                new HttpEntity<>(null, headers), WsTicketResponse.class);
        return response.getBody().ticket();
    }

    private ResponseEntity<ConsumeWsTicketResponse> consume(String ticket) {
        return restTemplate.exchange(
                "/internal/consume-ws-ticket", HttpMethod.POST,
                internalEntity(new ConsumeWsTicketRequest(ticket)), ConsumeWsTicketResponse.class);
    }

    private ResponseEntity<Void> admit(WsSessionAdmissionRequest identity) {
        return restTemplate.exchange("/internal/auth/admit-ws-session", HttpMethod.POST,
                internalEntity(identity), Void.class);
    }

    private ResponseEntity<Void> logout(String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        return restTemplate.exchange("/auth/logout", HttpMethod.POST,
                new HttpEntity<>(null, headers), Void.class);
    }

    private <T> HttpEntity<T> internalEntity(T body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.add("X-Internal-Issuer-Secret", INTERNAL_SECRET);
        return new HttpEntity<>(body, headers);
    }
}
