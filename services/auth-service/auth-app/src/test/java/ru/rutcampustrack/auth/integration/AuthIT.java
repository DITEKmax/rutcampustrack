package ru.rutcampustrack.auth.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import ru.rutcampustrack.auth.dto.LoginRequest;
import ru.rutcampustrack.auth.dto.TokenResponse;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AuthIT extends AbstractIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void login_withSeedStudent_returnsTokenPair() {
        LoginRequest request = new LoginRequest("student", "password");

        ResponseEntity<TokenResponse> response = restTemplate.postForEntity(
                "/auth/login", request, TokenResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().accessToken()).isNotBlank();
        assertThat(response.getBody().refreshToken()).isNotBlank();
        assertThat(response.getBody().expiresIn()).isEqualTo(900L);
    }

    @Test
    void login_withSeedAdmin_returnsTokenPair() {
        LoginRequest request = new LoginRequest("admin", "password");

        ResponseEntity<TokenResponse> response = restTemplate.postForEntity(
                "/auth/login", request, TokenResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().accessToken()).isNotBlank();
    }

    @Test
    void login_withSeedTeacher_returnsTokenPair() {
        LoginRequest request = new LoginRequest("teacher", "password");

        ResponseEntity<TokenResponse> response = restTemplate.postForEntity(
                "/auth/login", request, TokenResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().accessToken()).isNotBlank();
    }

    @Test
    void login_withInvalidPassword_returns401() {
        LoginRequest request = new LoginRequest("student", "wrong");

        ResponseEntity<String> response = restTemplate.postForEntity(
                "/auth/login", request, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getHeaders().getContentType()).isNotNull();
        assertThat(response.getHeaders().getContentType().toString())
                .contains("application/problem+json");
    }

    @Test
    void login_withNonexistentUser_returns401() {
        LoginRequest request = new LoginRequest("nobody", "password");

        ResponseEntity<String> response = restTemplate.postForEntity(
                "/auth/login", request, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void login_setsRefreshCookie_withStrictAttributes() {
        ResponseEntity<TokenResponse> response = restTemplate.postForEntity(
                "/auth/login", new LoginRequest("student", "password"), TokenResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<String> cookies = response.getHeaders().get(HttpHeaders.SET_COOKIE);
        assertThat(cookies).isNotNull().isNotEmpty();
        String refreshCookie = cookies.stream()
                .filter(c -> c.startsWith("rct_refresh="))
                .findFirst()
                .orElseThrow();
        assertThat(refreshCookie).contains("HttpOnly");
        assertThat(refreshCookie).contains("Secure");
        assertThat(refreshCookie).contains("SameSite=Strict");
        assertThat(refreshCookie).contains("Path=/api/auth");
        assertThat(refreshCookie).contains("Max-Age=604800");
    }

    @Test
    void refresh_viaCookie_returnsNewTokenPair_andRotatesCookie() {
        // Login — cookie ставится
        ResponseEntity<TokenResponse> loginResponse = restTemplate.postForEntity(
                "/auth/login", new LoginRequest("student", "password"), TokenResponse.class);
        assertThat(loginResponse.getBody()).isNotNull();
        String originalRefreshToken = loginResponse.getBody().refreshToken();
        String originalSetCookie = loginResponse.getHeaders().get(HttpHeaders.SET_COOKIE).stream()
                .filter(c -> c.startsWith("rct_refresh="))
                .findFirst().orElseThrow();
        String cookieHeader = "rct_refresh=" + extractCookieValue(originalSetCookie);

        // Refresh с cookie (без body)
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.COOKIE, cookieHeader);
        HttpEntity<Void> entity = new HttpEntity<>(null, headers);

        ResponseEntity<TokenResponse> refreshResponse = restTemplate.exchange(
                "/auth/refresh", HttpMethod.POST, entity, TokenResponse.class);

        assertThat(refreshResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(refreshResponse.getBody()).isNotNull();
        assertThat(refreshResponse.getBody().accessToken()).isNotBlank();
        assertThat(refreshResponse.getBody().refreshToken()).isNotBlank()
                .isNotEqualTo(originalRefreshToken);

        // Cookie rotated
        List<String> rotatedCookies = refreshResponse.getHeaders().get(HttpHeaders.SET_COOKIE);
        assertThat(rotatedCookies).isNotNull();
        assertThat(rotatedCookies.stream().anyMatch(c -> c.startsWith("rct_refresh="))).isTrue();
    }

    @Test
    void refresh_withoutCookie_returns401() {
        ResponseEntity<String> response = restTemplate.exchange(
                "/auth/refresh", HttpMethod.POST, HttpEntity.EMPTY, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void refresh_withUsedCookie_returns401() {
        // Login → get cookie
        ResponseEntity<TokenResponse> loginResponse = restTemplate.postForEntity(
                "/auth/login", new LoginRequest("student", "password"), TokenResponse.class);
        String cookieHeader = "rct_refresh=" + extractCookieValue(
                loginResponse.getHeaders().get(HttpHeaders.SET_COOKIE).stream()
                        .filter(c -> c.startsWith("rct_refresh=")).findFirst().orElseThrow());

        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.COOKIE, cookieHeader);
        HttpEntity<Void> entity = new HttpEntity<>(null, headers);

        // First refresh — consumes the token
        restTemplate.exchange("/auth/refresh", HttpMethod.POST, entity, TokenResponse.class);

        // Second refresh with same cookie — should 401
        ResponseEntity<String> second = restTemplate.exchange(
                "/auth/refresh", HttpMethod.POST, entity, String.class);
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void logout_viaCookie_returns204_andClearsCookie() {
        // Login → cookie
        ResponseEntity<TokenResponse> loginResponse = restTemplate.postForEntity(
                "/auth/login", new LoginRequest("student", "password"), TokenResponse.class);
        String cookieValue = extractCookieValue(
                loginResponse.getHeaders().get(HttpHeaders.SET_COOKIE).stream()
                        .filter(c -> c.startsWith("rct_refresh=")).findFirst().orElseThrow());
        String cookieHeader = "rct_refresh=" + cookieValue;

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(loginResponse.getBody().accessToken());
        headers.add(HttpHeaders.COOKIE, cookieHeader);
        HttpEntity<Void> entity = new HttpEntity<>(null, headers);

        ResponseEntity<Void> logoutResponse = restTemplate.exchange(
                "/auth/logout", HttpMethod.POST, entity, Void.class);
        assertThat(logoutResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        // Logout cookie — Max-Age=0
        List<String> cookies = logoutResponse.getHeaders().get(HttpHeaders.SET_COOKIE);
        assertThat(cookies).isNotNull();
        assertThat(cookies.stream().anyMatch(c ->
                c.startsWith("rct_refresh=") && c.contains("Max-Age=0"))).isTrue();

        // После logout этот cookie больше не refresh'ится
        ResponseEntity<String> refreshResponse = restTemplate.exchange(
                "/auth/refresh", HttpMethod.POST, entity, String.class);
        assertThat(refreshResponse.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void publicKey_returnsRsaPem() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                "/auth/public-key", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        // Response is a JSON object with publicKey field containing PEM
        assertThat(response.getBody()).contains("BEGIN PUBLIC KEY");
    }

    private static String extractCookieValue(String setCookieHeader) {
        // "rct_refresh=<value>; Path=/api/auth; ..."
        int eq = setCookieHeader.indexOf('=');
        int semi = setCookieHeader.indexOf(';');
        return setCookieHeader.substring(eq + 1, semi > 0 ? semi : setCookieHeader.length());
    }
}
