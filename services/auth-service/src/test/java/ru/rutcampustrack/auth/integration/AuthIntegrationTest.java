package ru.rutcampustrack.auth.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import ru.rutcampustrack.auth.dto.LoginRequest;
import ru.rutcampustrack.auth.dto.RefreshRequest;
import ru.rutcampustrack.auth.dto.TokenResponse;

import static org.assertj.core.api.Assertions.assertThat;

class AuthIntegrationTest extends AbstractIntegrationTest {

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
    void refresh_withValidToken_returnsNewTokenPair() {
        // First login to get tokens
        LoginRequest loginRequest = new LoginRequest("student", "password");
        ResponseEntity<TokenResponse> loginResponse = restTemplate.postForEntity(
                "/auth/login", loginRequest, TokenResponse.class);
        assertThat(loginResponse.getBody()).isNotNull();
        String originalAccessToken = loginResponse.getBody().accessToken();
        String originalRefreshToken = loginResponse.getBody().refreshToken();

        // Refresh
        RefreshRequest refreshRequest = new RefreshRequest(originalRefreshToken);
        ResponseEntity<TokenResponse> refreshResponse = restTemplate.postForEntity(
                "/auth/refresh", refreshRequest, TokenResponse.class);

        assertThat(refreshResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(refreshResponse.getBody()).isNotNull();
        assertThat(refreshResponse.getBody().accessToken()).isNotBlank();
        assertThat(refreshResponse.getBody().refreshToken()).isNotBlank();
        // Refresh token must be different (has unique jti UUID)
        assertThat(refreshResponse.getBody().refreshToken()).isNotEqualTo(originalRefreshToken);
    }

    @Test
    void refresh_withUsedToken_returns401() {
        // Login
        LoginRequest loginRequest = new LoginRequest("student", "password");
        ResponseEntity<TokenResponse> loginResponse = restTemplate.postForEntity(
                "/auth/login", loginRequest, TokenResponse.class);
        assertThat(loginResponse.getBody()).isNotNull();
        String originalRefreshToken = loginResponse.getBody().refreshToken();

        // Refresh once — consumes the token
        RefreshRequest refreshRequest = new RefreshRequest(originalRefreshToken);
        restTemplate.postForEntity("/auth/refresh", refreshRequest, TokenResponse.class);

        // Attempt refresh again with the same original token — should fail
        ResponseEntity<String> secondRefreshResponse = restTemplate.postForEntity(
                "/auth/refresh", refreshRequest, String.class);

        assertThat(secondRefreshResponse.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void logout_withValidToken_returns204() {
        // Login
        LoginRequest loginRequest = new LoginRequest("student", "password");
        ResponseEntity<TokenResponse> loginResponse = restTemplate.postForEntity(
                "/auth/login", loginRequest, TokenResponse.class);
        assertThat(loginResponse.getBody()).isNotNull();
        String accessToken = loginResponse.getBody().accessToken();
        String refreshToken = loginResponse.getBody().refreshToken();

        // Logout with Authorization header
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        headers.setContentType(MediaType.APPLICATION_JSON);
        RefreshRequest logoutRequest = new RefreshRequest(refreshToken);
        HttpEntity<RefreshRequest> entity = new HttpEntity<>(logoutRequest, headers);

        ResponseEntity<Void> logoutResponse = restTemplate.postForEntity(
                "/auth/logout", entity, Void.class);

        assertThat(logoutResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        // Verify that refreshToken is now invalid
        RefreshRequest refreshRequest = new RefreshRequest(refreshToken);
        ResponseEntity<String> refreshResponse = restTemplate.postForEntity(
                "/auth/refresh", refreshRequest, String.class);
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
}
