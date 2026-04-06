package ru.rutcampustrack.auth.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import ru.rutcampustrack.auth.dto.AccessTokenResponse;
import ru.rutcampustrack.auth.dto.LoginRequest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AuthCookieIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    // --- Login tests ---

    @Test
    void login_withSeedStudent_returnsAccessTokenAndCookie() {
        LoginRequest request = new LoginRequest("student", "password");

        ResponseEntity<AccessTokenResponse> response = restTemplate.postForEntity(
                "/auth/login", request, AccessTokenResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().accessToken()).isNotBlank();
        assertThat(response.getBody().expiresIn()).isEqualTo(900L);

        // Must have Set-Cookie header with refresh_token
        String cookieHeader = extractSetCookieHeader(response);
        assertThat(cookieHeader).contains("refresh_token=");
        assertThat(cookieHeader).containsIgnoringCase("HttpOnly");
        assertThat(cookieHeader).containsIgnoringCase("Secure");
        assertThat(cookieHeader).contains("SameSite=Strict");
        assertThat(cookieHeader).contains("Path=/api/auth");
    }

    @Test
    void login_withSeedStudent_doesNotReturnRefreshTokenInBody() {
        LoginRequest request = new LoginRequest("student", "password");

        ResponseEntity<String> response = restTemplate.postForEntity(
                "/auth/login", request, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        // JSON body must NOT contain refreshToken field
        assertThat(response.getBody()).doesNotContain("refreshToken");
        // But must contain accessToken
        assertThat(response.getBody()).contains("accessToken");
    }

    @Test
    void login_withSeedAdmin_returnsAccessTokenAndCookie() {
        LoginRequest request = new LoginRequest("admin", "password");

        ResponseEntity<AccessTokenResponse> response = restTemplate.postForEntity(
                "/auth/login", request, AccessTokenResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().accessToken()).isNotBlank();

        String cookieHeader = extractSetCookieHeader(response);
        assertThat(cookieHeader).contains("refresh_token=");
    }

    @Test
    void login_withSeedTeacher_returnsAccessTokenAndCookie() {
        LoginRequest request = new LoginRequest("teacher", "password");

        ResponseEntity<AccessTokenResponse> response = restTemplate.postForEntity(
                "/auth/login", request, AccessTokenResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().accessToken()).isNotBlank();

        String cookieHeader = extractSetCookieHeader(response);
        assertThat(cookieHeader).contains("refresh_token=");
    }

    @Test
    void login_withInvalidPassword_returns401AndNoCookie() {
        LoginRequest request = new LoginRequest("student", "wrong");

        ResponseEntity<String> response = restTemplate.postForEntity(
                "/auth/login", request, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        // No Set-Cookie on failure
        List<String> cookies = response.getHeaders().get(HttpHeaders.SET_COOKIE);
        assertThat(cookies == null || cookies.stream().noneMatch(c -> c.startsWith("refresh_token="))).isTrue();
    }

    // --- Refresh tests ---

    @Test
    void refresh_withValidCookie_returnsNewAccessTokenAndCookie() {
        // Login to get cookie
        LoginRequest loginRequest = new LoginRequest("student", "password");
        ResponseEntity<AccessTokenResponse> loginResponse = restTemplate.postForEntity(
                "/auth/login", loginRequest, AccessTokenResponse.class);

        String cookieValue = extractRefreshCookieValue(loginResponse);

        // Refresh with cookie
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.COOKIE, "refresh_token=" + cookieValue);
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        ResponseEntity<AccessTokenResponse> refreshResponse = restTemplate.exchange(
                "/auth/refresh", HttpMethod.POST, entity, AccessTokenResponse.class);

        assertThat(refreshResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(refreshResponse.getBody()).isNotNull();
        assertThat(refreshResponse.getBody().accessToken()).isNotBlank();

        // New cookie must be set (token rotation)
        String newCookieHeader = extractSetCookieHeader(refreshResponse);
        assertThat(newCookieHeader).contains("refresh_token=");
    }

    @Test
    void refresh_withUsedCookie_returns401() {
        // Login
        LoginRequest loginRequest = new LoginRequest("student", "password");
        ResponseEntity<AccessTokenResponse> loginResponse = restTemplate.postForEntity(
                "/auth/login", loginRequest, AccessTokenResponse.class);

        String cookieValue = extractRefreshCookieValue(loginResponse);

        // First refresh (consumes the token)
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.COOKIE, "refresh_token=" + cookieValue);
        HttpEntity<Void> entity = new HttpEntity<>(headers);
        restTemplate.exchange("/auth/refresh", HttpMethod.POST, entity, AccessTokenResponse.class);

        // Second refresh with same cookie — should fail
        ResponseEntity<String> secondRefresh = restTemplate.exchange(
                "/auth/refresh", HttpMethod.POST, entity, String.class);

        assertThat(secondRefresh.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void refresh_withoutCookie_returns400() {
        // POST /auth/refresh with no cookie and no body
        HttpHeaders headers = new HttpHeaders();
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        ResponseEntity<String> response = restTemplate.exchange(
                "/auth/refresh", HttpMethod.POST, entity, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    // --- Logout tests ---

    @Test
    void logout_withValidCookie_returns204AndClearsCookie() {
        // Login
        LoginRequest loginRequest = new LoginRequest("student", "password");
        ResponseEntity<AccessTokenResponse> loginResponse = restTemplate.postForEntity(
                "/auth/login", loginRequest, AccessTokenResponse.class);

        String cookieValue = extractRefreshCookieValue(loginResponse);

        // Logout with cookie
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.COOKIE, "refresh_token=" + cookieValue);
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        ResponseEntity<Void> logoutResponse = restTemplate.exchange(
                "/auth/logout", HttpMethod.POST, entity, Void.class);

        assertThat(logoutResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        // Verify cookie is cleared (Max-Age=0)
        String clearCookie = extractSetCookieHeader(logoutResponse);
        assertThat(clearCookie).contains("refresh_token=");
        assertThat(clearCookie).contains("Max-Age=0");
    }

    @Test
    void logout_thenRefresh_returns401() {
        // Login
        LoginRequest loginRequest = new LoginRequest("student", "password");
        ResponseEntity<AccessTokenResponse> loginResponse = restTemplate.postForEntity(
                "/auth/login", loginRequest, AccessTokenResponse.class);

        String cookieValue = extractRefreshCookieValue(loginResponse);

        // Logout
        HttpHeaders logoutHeaders = new HttpHeaders();
        logoutHeaders.add(HttpHeaders.COOKIE, "refresh_token=" + cookieValue);
        HttpEntity<Void> logoutEntity = new HttpEntity<>(logoutHeaders);
        restTemplate.exchange("/auth/logout", HttpMethod.POST, logoutEntity, Void.class);

        // Try refresh with old cookie
        HttpHeaders refreshHeaders = new HttpHeaders();
        refreshHeaders.add(HttpHeaders.COOKIE, "refresh_token=" + cookieValue);
        HttpEntity<Void> refreshEntity = new HttpEntity<>(refreshHeaders);

        ResponseEntity<String> refreshResponse = restTemplate.exchange(
                "/auth/refresh", HttpMethod.POST, refreshEntity, String.class);

        assertThat(refreshResponse.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // --- Helper methods ---

    private String extractSetCookieHeader(ResponseEntity<?> response) {
        List<String> cookies = response.getHeaders().get(HttpHeaders.SET_COOKIE);
        assertThat(cookies).isNotNull().isNotEmpty();
        return cookies.stream()
                .filter(c -> c.startsWith("refresh_token="))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No refresh_token cookie found in Set-Cookie headers"));
    }

    private String extractRefreshCookieValue(ResponseEntity<?> response) {
        String cookieHeader = extractSetCookieHeader(response);
        // Format: "refresh_token=<value>; Path=/api/auth; HttpOnly; Secure; SameSite=Strict"
        String nameValue = cookieHeader.split(";")[0]; // "refresh_token=<value>"
        return nameValue.substring("refresh_token=".length());
    }
}
