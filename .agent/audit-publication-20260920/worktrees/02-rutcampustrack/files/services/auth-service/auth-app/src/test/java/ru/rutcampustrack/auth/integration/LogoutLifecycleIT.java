package ru.rutcampustrack.auth.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import ru.rutcampustrack.auth.dto.ConsumeWsTicketRequest;
import ru.rutcampustrack.auth.dto.LoginRequest;
import ru.rutcampustrack.auth.dto.TokenResponse;
import ru.rutcampustrack.auth.dto.WsTicketResponse;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * M03b Группа 8: end-to-end logout lifecycle.
 *
 * <p>Проверяет что POST /auth/logout:</p>
 * <ol>
 *   <li>Отдаёт 204 + Set-Cookie с Max-Age=0 (clear cookie).</li>
 *   <li>Revoke'ит refresh-token (повторный refresh → 401).</li>
 *   <li>Invalidate'ит все ws-ticket'ы пользователя (Redis keys исчезли).</li>
 * </ol>
 */
class LogoutLifecycleIT extends AbstractIntegrationTest {

    private static final String INTERNAL_SECRET =
            "test-internal-issuer-secret-32-bytes-or-more-for-test-env";

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Test
    void logout_withBearer_invalidatesWsTicketsAndRevokesRefresh() {
        // 1) Login
        ResponseEntity<TokenResponse> loginResponse = restTemplate.postForEntity(
                "/auth/login", new LoginRequest("student", "password"), TokenResponse.class);
        assertThat(loginResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        TokenResponse tokens = loginResponse.getBody();
        assertThat(tokens).isNotNull();

        String accessToken = tokens.accessToken();
        String cookieValue = extractCookieValue(loginResponse, "rct_refresh");
        String cookieHeader = "rct_refresh=" + cookieValue;

        // 2) Issue 2 ws-ticket'а → в Redis должны появиться ключи
        String ticket1 = issueWsTicket(accessToken);
        String ticket2 = issueWsTicket(accessToken);
        assertThat(ticket1).isNotBlank();
        assertThat(ticket2).isNotBlank();

        // userId из seed-студента — берём из ws_ticket_user:* индексе (единственный)
        Set<String> userSetKeys = redisTemplate.keys("ws_ticket_user:*");
        assertThat(userSetKeys).isNotNull().isNotEmpty();
        String userSetKey = userSetKeys.iterator().next();
        long userId = Long.parseLong(userSetKey.substring("ws_ticket_user:".length()));

        // Pre-condition: есть 2 ticket'а
        assertThat(redisTemplate.opsForSet().size(userSetKey)).isEqualTo(2L);
        assertThat(redisTemplate.hasKey("ws_ticket:" + ticket1)).isTrue();
        assertThat(redisTemplate.hasKey("ws_ticket:" + ticket2)).isTrue();

        // 3) Logout c Bearer + cookie
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        headers.add(HttpHeaders.COOKIE, cookieHeader);
        ResponseEntity<Void> logoutResponse = restTemplate.exchange(
                "/auth/logout", HttpMethod.POST, new HttpEntity<>(null, headers), Void.class);

        // 3a) 204 No Content
        assertThat(logoutResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        // 3b) Set-Cookie с Max-Age=0
        List<String> setCookies = logoutResponse.getHeaders().get(HttpHeaders.SET_COOKIE);
        assertThat(setCookies).isNotNull();
        assertThat(setCookies.stream().anyMatch(c ->
                c.startsWith("rct_refresh=") && c.contains("Max-Age=0"))).isTrue();

        // 3c) Все ws-ticket'ы пользователя удалены
        assertThat(redisTemplate.hasKey("ws_ticket:" + ticket1)).isFalse();
        assertThat(redisTemplate.hasKey("ws_ticket:" + ticket2)).isFalse();
        assertThat(redisTemplate.hasKey("ws_ticket_user:" + userId)).isFalse();

        // 3d) Refresh token revoked — повторный refresh через тот же cookie → 401
        HttpHeaders refreshHeaders = new HttpHeaders();
        refreshHeaders.add(HttpHeaders.COOKIE, cookieHeader);
        ResponseEntity<String> refreshResponse = restTemplate.exchange(
                "/auth/refresh", HttpMethod.POST,
                new HttpEntity<>(null, refreshHeaders), String.class);
        assertThat(refreshResponse.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void logout_withAuthorityFailureDoesNotInvalidateWsTickets() {
        ResponseEntity<TokenResponse> studentLogin = restTemplate.postForEntity(
                "/auth/login", new LoginRequest("student", "password"), TokenResponse.class);
        assertThat(studentLogin.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(studentLogin.getBody()).isNotNull();
        String studentAccessToken = studentLogin.getBody().accessToken();
        String ticket = issueWsTicket(studentAccessToken);
        String userSetKey = redisTemplate.keys("ws_ticket_user:*").stream()
                .filter(key -> Boolean.TRUE.equals(redisTemplate.opsForSet().isMember(key, ticket)))
                .findFirst()
                .orElseThrow();

        ResponseEntity<TokenResponse> teacherLogin = restTemplate.postForEntity(
                "/auth/login", new LoginRequest("teacher", "password"), TokenResponse.class);
        assertThat(teacherLogin.getStatusCode()).isEqualTo(HttpStatus.OK);
        String foreignCookie = extractCookieValue(teacherLogin, "rct_refresh");

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(studentAccessToken);
            headers.add(HttpHeaders.COOKIE, "rct_refresh=" + foreignCookie);
            ResponseEntity<String> logoutResponse = restTemplate.exchange(
                    "/auth/logout", HttpMethod.POST,
                    new HttpEntity<>(null, headers), String.class);

            assertThat(logoutResponse.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
            assertThat(logoutResponse.getHeaders().get(HttpHeaders.SET_COOKIE)).isNull();
            assertThat(logoutResponse.getBody()).contains("INVALID_SESSION");
            assertThat(redisTemplate.hasKey("ws_ticket:" + ticket)).isTrue();
            assertThat(redisTemplate.opsForSet().isMember(userSetKey, ticket)).isTrue();
        } finally {
            redisTemplate.delete("ws_ticket:" + ticket);
            redisTemplate.opsForSet().remove(userSetKey, ticket);
        }
    }

    @Test
    void logout_withoutBearer_stillClearsCookieAndRevokesRefresh() {
        // Edge-case: access не передан, refresh в cookie. Cookie-only logout
        // сам атрибутирует durable revoke и удаляет пользовательские ticket'ы.
        ResponseEntity<TokenResponse> loginResponse = restTemplate.postForEntity(
                "/auth/login", new LoginRequest("teacher", "password"), TokenResponse.class);
        assertThat(loginResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(loginResponse.getBody()).isNotNull();
        String ticket = issueWsTicket(loginResponse.getBody().accessToken());
        String userSetKey = redisTemplate.keys("ws_ticket_user:*").stream()
                .filter(key -> Boolean.TRUE.equals(redisTemplate.opsForSet().isMember(key, ticket)))
                .findFirst()
                .orElseThrow();
        String cookieValue = extractCookieValue(loginResponse, "rct_refresh");
        String cookieHeader = "rct_refresh=" + cookieValue;

        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.COOKIE, cookieHeader);

        ResponseEntity<Void> logoutResponse = restTemplate.exchange(
                "/auth/logout", HttpMethod.POST, new HttpEntity<>(null, headers), Void.class);

        assertThat(logoutResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        List<String> setCookies = logoutResponse.getHeaders().get(HttpHeaders.SET_COOKIE);
        assertThat(setCookies).isNotNull();
        assertThat(setCookies.stream().anyMatch(c ->
                c.startsWith("rct_refresh=") && c.contains("Max-Age=0"))).isTrue();

        assertThat(redisTemplate.hasKey("ws_ticket:" + ticket)).isFalse();
        assertThat(redisTemplate.hasKey(userSetKey)).isFalse();
        ResponseEntity<String> consumeResponse = restTemplate.exchange(
                "/internal/consume-ws-ticket", HttpMethod.POST,
                internalEntity(new ConsumeWsTicketRequest(ticket)), String.class);
        assertThat(consumeResponse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        // Refresh после logout → 401
        ResponseEntity<String> refreshResponse = restTemplate.exchange(
                "/auth/refresh", HttpMethod.POST,
                new HttpEntity<>(null, headers), String.class);
        assertThat(refreshResponse.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    private String issueWsTicket(String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        ResponseEntity<WsTicketResponse> response = restTemplate.exchange(
                "/auth/ws-ticket", HttpMethod.POST,
                new HttpEntity<>(null, headers), WsTicketResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody().ticket();
    }

    private HttpEntity<ConsumeWsTicketRequest> internalEntity(ConsumeWsTicketRequest body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.add("X-Internal-Issuer-Secret", INTERNAL_SECRET);
        return new HttpEntity<>(body, headers);
    }

    private static String extractCookieValue(ResponseEntity<?> response, String cookieName) {
        List<String> cookies = response.getHeaders().get(HttpHeaders.SET_COOKIE);
        assertThat(cookies).isNotNull();
        String setCookie = cookies.stream()
                .filter(c -> c.startsWith(cookieName + "="))
                .findFirst()
                .orElseThrow();
        int eq = setCookie.indexOf('=');
        int semi = setCookie.indexOf(';');
        return setCookie.substring(eq + 1, semi > 0 ? semi : setCookie.length());
    }
}
