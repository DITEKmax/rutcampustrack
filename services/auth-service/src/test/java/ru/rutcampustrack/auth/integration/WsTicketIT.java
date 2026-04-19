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
import ru.rutcampustrack.auth.controller.InternalWsTicketController.ConsumeRequest;
import ru.rutcampustrack.auth.controller.InternalWsTicketController.ConsumeResponse;
import ru.rutcampustrack.auth.dto.LoginRequest;
import ru.rutcampustrack.auth.dto.TokenResponse;
import ru.rutcampustrack.auth.dto.WsTicketResponse;

import static org.assertj.core.api.Assertions.assertThat;

class WsTicketIT extends AbstractIntegrationTest {

    private static final String INTERNAL_SECRET =
            "test-internal-issuer-secret-32-bytes-or-more-for-test-env";

    @Autowired
    private TestRestTemplate restTemplate;

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
    void consumeTicket_happyPath_returnsIdentity() {
        String accessToken = loginAndGetAccessToken("student");
        String ticket = issueTicket(accessToken);

        ResponseEntity<ConsumeResponse> response = consume(ticket);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().userId()).isPositive();
        assertThat(response.getBody().role()).isEqualTo("STUDENT");
    }

    @Test
    void consumeTicket_twice_secondReturns404() {
        String accessToken = loginAndGetAccessToken("student");
        String ticket = issueTicket(accessToken);

        // First consume — ok
        ResponseEntity<ConsumeResponse> first = consume(ticket);
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Second consume — 404 (already consumed)
        ResponseEntity<String> second = restTemplate.exchange(
                "/internal/consume-ws-ticket", HttpMethod.POST,
                internalEntity(new ConsumeRequest(ticket)), String.class);
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void consumeTicket_unknownTicket_returns404() {
        ResponseEntity<String> response = restTemplate.exchange(
                "/internal/consume-ws-ticket", HttpMethod.POST,
                internalEntity(new ConsumeRequest("nonexistent-uuid")), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void consumeTicket_withoutInternalSecret_returns401() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> response = restTemplate.exchange(
                "/internal/consume-ws-ticket", HttpMethod.POST,
                new HttpEntity<>(new ConsumeRequest("some-ticket"), headers), String.class);

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

    private ResponseEntity<ConsumeResponse> consume(String ticket) {
        return restTemplate.exchange(
                "/internal/consume-ws-ticket", HttpMethod.POST,
                internalEntity(new ConsumeRequest(ticket)), ConsumeResponse.class);
    }

    private HttpEntity<ConsumeRequest> internalEntity(ConsumeRequest body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.add("X-Internal-Issuer-Secret", INTERNAL_SECRET);
        return new HttpEntity<>(body, headers);
    }
}
