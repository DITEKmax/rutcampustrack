package ru.rutcampustrack.auth.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import ru.rutcampustrack.auth.dto.LoginRequest;
import ru.rutcampustrack.auth.dto.TokenResponse;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * M08 G8 security contract (NEW-164) — SameSite cookie contract for
 * {@code rct_refresh}. Replaces the originally-scoped {@code CsrfDoubleSubmitIT}
 * per DECISIONS D6: M03b deliberately chose SameSite=Strict + same-origin
 * over double-submit token for v0.0.0, so the real CSRF-mitigation contract
 * to pin is the cookie attribute set, not a nonexistent X-CSRF-TOKEN header.
 *
 * <p>What this IT guards:
 * <ul>
 *   <li>cookie attributes (HttpOnly/Secure/SameSite=Strict/Path/Max-Age)
 *       stay intact — regression guard against a well-meaning refactor that
 *       drops an attribute.
 *   <li>refresh without cookie is rejected — proof that cross-origin refresh
 *       attack is blocked by cookie absence, not by a CSRF token check.
 *   <li>refresh with cookie but WITHOUT an {@code X-CSRF-TOKEN} header
 *       succeeds — guard against someone introducing a double-submit check
 *       without updating every frontend interceptor (would break login).
 *   <li>logout cookie preserves the same security attributes.
 * </ul>
 */
@Tag("security-contract")
@DisplayName("Security contract — SameSite cookie (M08 G8)")
class SameSiteCookieContractIT extends AbstractIntegrationTest {

    private static final String REFRESH_COOKIE = "rct_refresh";

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    @DisplayName("login issues cookie with HttpOnly + Secure + SameSite=Strict + Path=/api/auth")
    void loginCookie_carriesFullAttributeSet() {
        ResponseEntity<TokenResponse> response = restTemplate.postForEntity(
                "/auth/login", new LoginRequest("student", "password"), TokenResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        String refreshCookie = findRefreshCookie(response);

        assertThat(refreshCookie)
                .as("cookie must have every security attribute — drop one and CSRF-protection regresses")
                .contains("HttpOnly")
                .contains("Secure")
                .contains("SameSite=Strict")
                .contains("Path=/api/auth")
                .contains("Max-Age=604800");
    }

    @Test
    @DisplayName("/auth/refresh без cookie → 401 (cross-origin attack blocked by cookie absence)")
    void refresh_withoutCookie_returns401() {
        ResponseEntity<String> response = restTemplate.exchange(
                "/auth/refresh", HttpMethod.POST, HttpEntity.EMPTY, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("/auth/refresh с cookie БЕЗ X-CSRF-TOKEN header → 200 (regression guard)")
    void refresh_withCookie_withoutCsrfHeader_returns200() {
        // D6: we deliberately do NOT require X-CSRF-TOKEN for v0.0.0.
        // If someone adds a double-submit check without coordinating a
        // frontend interceptor, this test fails and stops the regression.
        ResponseEntity<TokenResponse> loginResponse = restTemplate.postForEntity(
                "/auth/login", new LoginRequest("student", "password"), TokenResponse.class);
        String cookieValue = extractCookieValue(findRefreshCookie(loginResponse));

        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.COOKIE, REFRESH_COOKIE + "=" + cookieValue);
        // Intentionally no X-CSRF-TOKEN
        HttpEntity<Void> entity = new HttpEntity<>(null, headers);

        ResponseEntity<TokenResponse> response = restTemplate.exchange(
                "/auth/refresh", HttpMethod.POST, entity, TokenResponse.class);

        assertThat(response.getStatusCode())
                .as("v0.0.0 does not require X-CSRF-TOKEN — introducing one here breaks PWA/web-panel")
                .isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().accessToken()).isNotBlank();
    }

    @Test
    @DisplayName("/auth/refresh игнорирует произвольный X-CSRF-TOKEN header (не ломает happy-path)")
    void refresh_withArbitraryCsrfHeader_stillReturns200() {
        // Same guard direction: if someone sends an X-CSRF-TOKEN we do nothing with it.
        ResponseEntity<TokenResponse> loginResponse = restTemplate.postForEntity(
                "/auth/login", new LoginRequest("student", "password"), TokenResponse.class);
        String cookieValue = extractCookieValue(findRefreshCookie(loginResponse));

        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.COOKIE, REFRESH_COOKIE + "=" + cookieValue);
        headers.add("X-CSRF-TOKEN", "some-random-value-that-noone-validates");
        HttpEntity<Void> entity = new HttpEntity<>(null, headers);

        ResponseEntity<TokenResponse> response = restTemplate.exchange(
                "/auth/refresh", HttpMethod.POST, entity, TokenResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("/auth/logout clears cookie with same security attributes and Max-Age=0")
    void logoutCookie_carriesClearAttributeSet() {
        ResponseEntity<TokenResponse> loginResponse = restTemplate.postForEntity(
                "/auth/login", new LoginRequest("student", "password"), TokenResponse.class);
        String cookieValue = extractCookieValue(findRefreshCookie(loginResponse));

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(loginResponse.getBody().accessToken());
        headers.add(HttpHeaders.COOKIE, REFRESH_COOKIE + "=" + cookieValue);
        HttpEntity<Void> entity = new HttpEntity<>(null, headers);

        ResponseEntity<Void> logoutResponse = restTemplate.exchange(
                "/auth/logout", HttpMethod.POST, entity, Void.class);
        assertThat(logoutResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        List<String> cookies = logoutResponse.getHeaders().get(HttpHeaders.SET_COOKIE);
        assertThat(cookies).isNotNull();
        String clearCookie = cookies.stream()
                .filter(c -> c.startsWith(REFRESH_COOKIE + "="))
                .findFirst()
                .orElseThrow();

        assertThat(clearCookie)
                .as("clear-cookie must keep full security attributes — losing them leaks a half-set cookie")
                .contains("HttpOnly")
                .contains("Secure")
                .contains("SameSite=Strict")
                .contains("Path=/api/auth")
                .contains("Max-Age=0");
    }

    private static String findRefreshCookie(ResponseEntity<?> response) {
        List<String> cookies = response.getHeaders().get(HttpHeaders.SET_COOKIE);
        assertThat(cookies).as("response must set rct_refresh cookie").isNotNull().isNotEmpty();
        return cookies.stream()
                .filter(c -> c.startsWith(REFRESH_COOKIE + "="))
                .findFirst()
                .orElseThrow(() -> new AssertionError("rct_refresh cookie missing in " + cookies));
    }

    private static String extractCookieValue(String setCookieHeader) {
        int eq = setCookieHeader.indexOf('=');
        int semi = setCookieHeader.indexOf(';');
        return setCookieHeader.substring(eq + 1, semi > 0 ? semi : setCookieHeader.length());
    }
}
