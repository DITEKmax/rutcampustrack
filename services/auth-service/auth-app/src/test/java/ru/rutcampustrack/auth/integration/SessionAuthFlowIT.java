package ru.rutcampustrack.auth.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import ru.rutcampustrack.auth.dto.LoginRequest;
import ru.rutcampustrack.auth.dto.TokenResponse;
import ru.rutcampustrack.auth.service.JwtService;
import ru.rutcampustrack.auth.session.SessionLifecycleService;
import ru.rutcampustrack.auth.session.model.AuthMethod;
import ru.rutcampustrack.auth.session.port.AuthSessionQueryPort;

/**
 * Real Boot/Spring Security proof for the session-bound public auth flow.
 *
 * <p>The test authenticates exclusively with the bearer filter or the refresh cookie. It therefore
 * exercises the actual {@code SecurityFilterChain}, PostgreSQL authority, controller mappings, and
 * cookie-only logout path.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SessionAuthFlowIT extends AbstractIntegrationTest {

    private static final String PASSWORD_HASH =
            "$2a$10$A9r8miSBxjlpjxFB/z0jIerCCSOrLQP6N.sXrjBAw9l7iy4vmRFpi";

    @Autowired private TestRestTemplate restTemplate;

    @Autowired private JdbcTemplate jdbc;

    @Autowired private ObjectMapper objectMapper;

    @Autowired private SessionLifecycleService sessionLifecycle;

    @Autowired private AuthSessionQueryPort sessionQueries;

    @Autowired private JwtService jwtService;

    @Test
    void bootstrapBearerReachesCurrentSessionsAndRoleSelectionButNotHistoryOrPassword()
            throws Exception {
        LoginSession session = loginNeutralUser();

        ResponseEntity<String> current = bearer(session, "/auth/session", HttpMethod.GET, null);
        assertThat(current.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(current.getHeaders().getCacheControl()).isEqualTo("no-store");

        ResponseEntity<String> sessions = bearer(session, "/auth/sessions", HttpMethod.GET, null);
        assertThat(sessions.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(sessions.getHeaders().getCacheControl()).isEqualTo("no-store");

        ResponseEntity<String> role =
                bearer(
                        session,
                        "/auth/session/active-role",
                        HttpMethod.PUT,
                        "{\"role\":\"STUDENT\",\"expectedSessionVersion\":\"1\"}");
        assertThat(role.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(problemCode(role)).isEqualTo("ROLE_NOT_GRANTED");

        ResponseEntity<String> history =
                bearer(session, "/auth/account-history", HttpMethod.GET, null);
        assertThat(history.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(problemCode(history)).isEqualTo("BOOTSTRAP_SCOPE_DENIED");

        ResponseEntity<String> password =
                bearer(session, "/auth/change-password", HttpMethod.POST, "{}");
        assertThat(password.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(problemCode(password)).isEqualTo("BOOTSTRAP_SCOPE_DENIED");
    }

    @Test
    void bootstrapBearerOnlyLogoutAndLogoutAllAreAllowedByTheExactScope() throws Exception {
        LoginSession bearerLogout = loginNeutralUser();
        ResponseEntity<Void> logout = bearerOnlyLogout(bearerLogout);
        assertThat(logout.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(logout.getHeaders().getCacheControl()).isEqualTo("no-store");

        LoginSession logoutAll = loginNeutralUser();
        ResponseEntity<Void> all = bearerOnlyLogoutAll(logoutAll);
        assertThat(all.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(all.getHeaders().getCacheControl()).isEqualTo("no-store");

        ResponseEntity<String> revoked = bearer(logoutAll, "/auth/session", HttpMethod.GET, null);
        assertThat(revoked.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(problemCode(revoked)).isEqualTo("SESSION_REVOKED");
    }

    @Test
    void passwordLoginSelectsStudentAndRoleSwitchIssuesTeacherBoundAccess() throws Exception {
        String login = seedSelectableUser();
        LoginSession studentSession = loginUser(login);

        ResponseEntity<String> studentCurrent =
                bearer(studentSession, "/auth/session", HttpMethod.GET, null);
        assertThat(studentCurrent.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(studentCurrent.getHeaders().getCacheControl()).isEqualTo("no-store");
        JsonNode studentBody = objectMapper.readTree(studentCurrent.getBody());
        assertThat(studentBody.path("activeRole").asText()).isEqualTo("STUDENT");
        assertThat(studentBody.path("roles").findValuesAsText("role"))
                .contains("STUDENT", "TEACHER");

        String expectedSessionVersion = studentBody.path("sessionVersion").asText();
        ResponseEntity<String> selected =
                bearer(
                        studentSession,
                        "/auth/session/active-role",
                        HttpMethod.PUT,
                        "{\"role\":\"TEACHER\",\"expectedSessionVersion\":\""
                                + expectedSessionVersion
                                + "\"}");
        assertThat(selected.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(selected.getHeaders().getCacheControl()).isEqualTo("no-store");
        JsonNode selectedBody = objectMapper.readTree(selected.getBody());
        assertThat(selectedBody.path("accessToken").asText()).isNotBlank();
        assertThat(selectedBody.path("session").path("activeRole").asText()).isEqualTo("TEACHER");
        assertThat(selectedBody.path("session").path("sessionVersion").asText()).isEqualTo("2");

        LoginSession teacherSession =
                new LoginSession(
                        selectedBody.path("accessToken").asText(),
                        studentSession.refreshToken(),
                        studentSession.cookieValue(),
                        studentSession.setCookieHeader());
        ResponseEntity<String> teacherCurrent =
                bearer(teacherSession, "/auth/session", HttpMethod.GET, null);
        assertThat(teacherCurrent.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(teacherCurrent.getHeaders().getCacheControl()).isEqualTo("no-store");
        assertThat(objectMapper.readTree(teacherCurrent.getBody()).path("activeRole").asText())
                .isEqualTo("TEACHER");
    }

    @Test
    void refreshCookieCasHasOneWinnerAndKeepsAbsoluteExpiry() throws Exception {
        LoginSession session = loginNeutralUser();
        String originalRefresh = session.refreshToken();
        long originalExpiry = tokenExpiration(originalRefresh);
        long initialMaxAge = cookieMaxAge(session.setCookieHeader());
        long beforeRefreshEpoch = Instant.now().getEpochSecond();
        assertThat(initialMaxAge).isPositive();

        List<ResponseEntity<String>> concurrent = refreshConcurrently(originalRefresh);
        assertThat(concurrent).hasSize(2);
        List<ResponseEntity<String>> winners =
                concurrent.stream()
                        .filter(response -> response.getStatusCode().equals(HttpStatus.OK))
                        .toList();
        List<ResponseEntity<String>> losers =
                concurrent.stream()
                        .filter(response -> response.getStatusCode().equals(HttpStatus.CONFLICT))
                        .toList();
        assertThat(winners).hasSize(1);
        assertThat(losers).hasSize(1);

        ResponseEntity<String> winner = winners.get(0);
        ResponseEntity<String> loser = losers.get(0);
        assertThat(winner.getHeaders().getCacheControl()).isEqualTo("no-store");
        assertThat(loser.getHeaders().getCacheControl()).isEqualTo("no-store");
        assertThat(problemCode(loser)).isEqualTo("REFRESH_ALREADY_ROTATED");

        JsonNode winnerBody = objectMapper.readTree(winner.getBody());
        String rotatedRefresh = winnerBody.path("refreshToken").asText();
        assertThat(rotatedRefresh).isNotBlank().isNotEqualTo(originalRefresh);
        assertThat(tokenExpiration(rotatedRefresh)).isEqualTo(originalExpiry);
        String winnerCookie = refreshCookieHeader(winner.getHeaders().get(HttpHeaders.SET_COOKIE));
        long winnerMaxAge = cookieMaxAge(winnerCookie);
        assertThat(winnerMaxAge)
                .isPositive()
                .isLessThanOrEqualTo(initialMaxAge)
                .isLessThanOrEqualTo(originalExpiry - beforeRefreshEpoch);

        long beforeSecondRefreshEpoch = Instant.now().getEpochSecond();
        ResponseEntity<String> secondWinner = refresh(rotatedRefresh);
        assertThat(secondWinner.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(secondWinner.getHeaders().getCacheControl()).isEqualTo("no-store");
        JsonNode secondWinnerBody = objectMapper.readTree(secondWinner.getBody());
        String secondRefresh = secondWinnerBody.path("refreshToken").asText();
        assertThat(secondRefresh).isNotBlank().isNotEqualTo(rotatedRefresh);
        assertThat(tokenExpiration(secondRefresh)).isEqualTo(originalExpiry);
        long secondMaxAge =
                cookieMaxAge(
                        refreshCookieHeader(secondWinner.getHeaders().get(HttpHeaders.SET_COOKIE)));
        assertThat(secondMaxAge)
                .isPositive()
                .isLessThanOrEqualTo(winnerMaxAge)
                .isLessThanOrEqualTo(originalExpiry - beforeSecondRefreshEpoch);

        ResponseEntity<String> oldReplay = refresh(originalRefresh);
        assertThat(oldReplay.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(oldReplay.getHeaders().getCacheControl()).isEqualTo("no-store");
        assertThat(problemCode(oldReplay)).isEqualTo("REFRESH_REJECTED");
    }

    @Test
    void naturallyExpiredRefreshCookieIsRejectedWithoutRevokingOrMutatingItsSession()
            throws Exception {
        String login = seedStudentUser();
        long userId = jdbc.queryForObject("SELECT id FROM users WHERE login=?", Long.class, login);
        var authority = sessionQueries.findLoginAuthority(userId).orElseThrow();
        Instant createdAt = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        Instant expiresAt = createdAt.plusSeconds(15);
        UUID sessionId = UUID.randomUUID();
        UUID refreshJti = UUID.randomUUID();
        var created =
                sessionLifecycle.createSession(
                        new SessionLifecycleService.CreateSessionRequest(
                                userId,
                                sessionId,
                                refreshJti,
                                createdAt,
                                expiresAt,
                                AuthMethod.PASSWORD,
                                authority.rolesVersion(),
                                authority.grants(),
                                PASSWORD_HASH,
                                "natural-expiry-it",
                                "natural-expiry-it"));
        assertThat(created.succeeded()).as("own short-lived durable session is created").isTrue();
        String originalRefresh =
                jwtService.generateSessionRefreshToken(
                        created.snapshot(), refreshJti, createdAt, expiresAt);
        ResponseEntity<String> live = refresh(originalRefresh);
        assertThat(live.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(live.getHeaders().getCacheControl()).isEqualTo("no-store");
        JsonNode liveBody = objectMapper.readTree(live.getBody());
        String currentRefresh = liveBody.path("refreshToken").asText();
        assertThat(!currentRefresh.isBlank() && !currentRefresh.equals(originalRefresh))
                .as("pre-expiry refresh issues a new current cookie")
                .isTrue();
        assertThat(
                        cookieValue(live.getHeaders().get(HttpHeaders.SET_COOKIE))
                                .equals(currentRefresh))
                .as("the actual HTTP cookie is the current refresh token")
                .isTrue();
        assertThat(tokenExpiration(currentRefresh) == expiresAt.getEpochSecond())
                .as("rotation retains the fixture's real absolute deadline")
                .isTrue();
        LoginSession liveSession =
                new LoginSession(
                        liveBody.path("accessToken").asText(), currentRefresh, currentRefresh, "");
        ResponseEntity<String> current = bearer(liveSession, "/auth/session", HttpMethod.GET, null);
        assertThat(current.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode currentBody = objectMapper.readTree(current.getBody());
        assertThat(
                        currentBody.path("userId").asText().equals(Long.toString(userId))
                                && currentBody
                                        .path("sessionId")
                                        .asText()
                                        .equals(sessionId.toString()))
                .as("the live HTTP control reaches the exact owned session")
                .isTrue();
        var before =
                jdbc.queryForMap(
                        "SELECT current_refresh_jti, previous_refresh_jti, refresh_expires_at,"
                                + " revoked_at, revoke_reason FROM auth_sessions WHERE sid=? AND"
                                + " user_id=?",
                        sessionId,
                        userId);
        Long eventsBefore =
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM account_security_events WHERE user_id=?",
                        Long.class,
                        userId);
        long waitMillis =
                Math.max(0, Duration.between(Instant.now(), expiresAt.plusMillis(100)).toMillis());
        assertThat(waitMillis <= 16000)
                .as("the natural-expiry wait stays within the short owned fixture")
                .isTrue();
        TimeUnit.MILLISECONDS.sleep(waitMillis);
        assertThat(!Instant.now().isBefore(expiresAt))
                .as("real server wall time reaches the original deadline")
                .isTrue();

        ResponseEntity<String> expired = refresh(currentRefresh);
        assertThat(expired.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(expired.getHeaders().getCacheControl()).isEqualTo("no-store");
        assertThat(problemCode(expired)).isEqualTo("REFRESH_REJECTED");
        assertThat(expired.getHeaders().get(HttpHeaders.SET_COOKIE) == null)
                .as("expired refresh issues no cookie")
                .isTrue();
        JsonNode rejectedBody = objectMapper.readTree(expired.getBody());
        assertThat(!rejectedBody.has("accessToken") && !rejectedBody.has("refreshToken"))
                .as("expired refresh issues no token payload")
                .isTrue();
        var after =
                jdbc.queryForMap(
                        "SELECT current_refresh_jti, previous_refresh_jti, refresh_expires_at,"
                                + " revoked_at, revoke_reason FROM auth_sessions WHERE sid=? AND"
                                + " user_id=?",
                        sessionId,
                        userId);
        assertThat(before.equals(after) && after.get("revoked_at") == null)
                .as("natural expiry neither rotates nor revokes the durable session")
                .isTrue();
        assertThat(
                        eventsBefore.equals(
                                jdbc.queryForObject(
                                        "SELECT COUNT(*) FROM account_security_events WHERE"
                                                + " user_id=?",
                                        Long.class,
                                        userId)))
                .as("expired refresh appends no security event")
                .isTrue();
    }

    @Test
    void passwordChangeCommitsClearRevokesBearerAndReplacesCredential() throws Exception {
        String login = seedStudentUser();
        LoginSession oldSession = loginUser(login);
        String newPassword = "NewPassword1!";
        assertThat(
                        jdbc.queryForObject(
                                "SELECT initial_password IS NOT NULL AND NOT password_changed FROM users WHERE login=?",
                                Boolean.class,
                                login))
                .isTrue();

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(oldSession.accessToken());
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<Void> changed =
                restTemplate.exchange(
                        "/auth/change-password",
                        HttpMethod.POST,
                        new HttpEntity<>(
                                "{\"currentPassword\":\"password\",\"newPassword\":\""
                                        + newPassword
                                        + "\"}",
                                headers),
                        Void.class);
        assertThat(changed.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(changed.getHeaders().getCacheControl()).isEqualTo("no-store");
        assertThat(changed.getHeaders().getFirst(HttpHeaders.SET_COOKIE))
                .startsWith("rct_refresh=")
                .contains("Max-Age=0");

        assertThat(
                        jdbc.queryForObject(
                                "SELECT initial_password IS NULL AND password_changed FROM users WHERE login=?",
                                Boolean.class,
                                login))
                .isTrue();
        assertThat(
                        jdbc.queryForObject(
                                "SELECT password_hash=? FROM users WHERE login=?",
                                Boolean.class,
                                newPassword,
                                login))
                .isFalse();

        ResponseEntity<String> revokedBearer =
                bearer(oldSession, "/auth/session", HttpMethod.GET, null);
        assertThat(revokedBearer.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(revokedBearer.getHeaders().getCacheControl()).isEqualTo("no-store");
        assertThat(problemCode(revokedBearer)).isEqualTo("SESSION_REVOKED");

        ResponseEntity<String> oldCredential =
                restTemplate.postForEntity(
                        "/auth/login", new LoginRequest(login, "password"), String.class);
        assertThat(oldCredential.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(oldCredential.getHeaders().getCacheControl()).isEqualTo("no-store");

        ResponseEntity<TokenResponse> newCredential =
                restTemplate.postForEntity(
                        "/auth/login", new LoginRequest(login, newPassword), TokenResponse.class);
        assertThat(newCredential.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(newCredential.getHeaders().getCacheControl()).isEqualTo("no-store");
        assertThat(newCredential.getBody()).isNotNull();
        assertThat(newCredential.getBody().accessToken()).isNotBlank();
        assertThat(newCredential.getBody().refreshToken()).isNotBlank();
        assertThat(newCredential.getHeaders().getFirst(HttpHeaders.SET_COOKIE))
                .startsWith("rct_refresh=")
                .contains("Max-Age=");
    }

    @Test
    void cookieOnlyLogoutUsesRefreshAuthorityAndRevokesTheCookieSession() throws Exception {
        LoginSession session = loginNeutralUser();
        ResponseEntity<Void> logout = cookieOnlyLogout(session);

        assertThat(logout.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(logout.getHeaders().getCacheControl()).isEqualTo("no-store");
        assertThat(logout.getHeaders().getFirst(HttpHeaders.SET_COOKIE))
                .startsWith("rct_refresh=")
                .contains("Max-Age=0");

        ResponseEntity<String> replay = refresh(session.refreshToken());
        assertThat(replay.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(problemCode(replay)).isEqualTo("SESSION_REVOKED");
    }

    @Test
    void validCookieFallsBackWhenSameUsersBearerSessionIsRevoked() throws Exception {
        String login = seedNeutralUser();
        LoginSession cookieSession = loginUser(login);
        LoginSession revokedBearerSession = loginUser(login);

        assertThat(bearerOnlyLogout(revokedBearerSession).getStatusCode())
                .isEqualTo(HttpStatus.NO_CONTENT);

        ResponseEntity<Void> logout =
                cookieAndBearerLogout(
                        cookieSession.cookieValue(), revokedBearerSession.accessToken());
        assertThat(logout.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(logout.getHeaders().getFirst(HttpHeaders.SET_COOKIE))
                .startsWith("rct_refresh=")
                .contains("Max-Age=0");

        ResponseEntity<String> replay = refresh(cookieSession.refreshToken());
        assertThat(replay.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(problemCode(replay)).isEqualTo("SESSION_REVOKED");
    }

    @Test
    void liveBearerForAnotherSessionCannotRevokeCookieSession() throws Exception {
        String login = seedNeutralUser();
        LoginSession cookieSession = loginUser(login);
        LoginSession otherBearerSession = loginUser(login);

        ResponseEntity<Void> logout =
                cookieAndBearerLogout(
                        cookieSession.cookieValue(), otherBearerSession.accessToken());
        assertThat(logout.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(logout.getHeaders().getFirst(HttpHeaders.SET_COOKIE)).isNull();

        ResponseEntity<String> stillLive = refresh(cookieSession.refreshToken());
        assertThat(stillLive.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(stillLive.getHeaders().getFirst(HttpHeaders.SET_COOKIE))
                .startsWith("rct_refresh=");
    }

    private LoginSession loginNeutralUser() {
        return loginUser(seedNeutralUser());
    }

    private String seedNeutralUser() {
        String login = "flow-n-" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
        Instant now = Instant.now();
        jdbc.update(
                """
                        INSERT INTO users (
                            login, password_hash, last_name, first_name, middle_name,
                            role, status, is_headman, group_id, initial_password,
                            password_changed, created_at, updated_at
                        ) VALUES (?, ?, 'Flow', 'Neutral', NULL,
                                  CAST('student' AS user_role), CAST('active' AS account_status),
                                  FALSE, NULL, 'password', FALSE, ?, ?)
                """,
                login,
                PASSWORD_HASH,
                Timestamp.from(now),
                Timestamp.from(now));
        return login;
    }

    private String seedSelectableUser() {
        String login = seedNeutralUser();
        Instant now = Instant.now();
        jdbc.update(
                """
                        INSERT INTO user_role_grants (
                            user_id, role, status, group_id, created_at, updated_at
                        )
                        SELECT id, grant_role, 'active', NULL, ?, ?
                        FROM users
                        CROSS JOIN (VALUES ('student'), ('teacher')) AS role_values(grant_role)
                        WHERE login = ?
                """,
                Timestamp.from(now),
                Timestamp.from(now),
                login);
        return login;
    }

    private String seedStudentUser() {
        String login = seedNeutralUser();
        Instant now = Instant.now();
        jdbc.update(
                """
                        INSERT INTO user_role_grants (
                            user_id, role, status, group_id, created_at, updated_at
                        )
                        SELECT id, 'student', 'active', NULL, ?, ?
                        FROM users
                        WHERE login = ?
                """,
                Timestamp.from(now),
                Timestamp.from(now),
                login);
        return login;
    }

    private LoginSession loginUser(String login) {
        return loginUser(login, "password");
    }

    private LoginSession loginUser(String login, String password) {
        ResponseEntity<TokenResponse> response =
                restTemplate.postForEntity(
                        "/auth/login", new LoginRequest(login, password), TokenResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getCacheControl()).isEqualTo("no-store");
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().accessToken()).isNotBlank();
        assertThat(response.getBody().refreshToken()).isNotBlank();
        String setCookieHeader =
                refreshCookieHeader(response.getHeaders().get(HttpHeaders.SET_COOKIE));
        return new LoginSession(
                response.getBody().accessToken(),
                response.getBody().refreshToken(),
                cookieValue(response.getHeaders().get(HttpHeaders.SET_COOKIE)),
                setCookieHeader);
    }

    private ResponseEntity<String> bearer(
            LoginSession session, String path, HttpMethod method, String body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(session.accessToken());
        if (body != null) {
            headers.setContentType(MediaType.APPLICATION_JSON);
        }
        return restTemplate.exchange(path, method, new HttpEntity<>(body, headers), String.class);
    }

    private ResponseEntity<Void> bearerOnlyLogout(LoginSession session) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(session.accessToken());
        return restTemplate.exchange(
                "/auth/logout", HttpMethod.POST, new HttpEntity<>(null, headers), Void.class);
    }

    private ResponseEntity<Void> bearerOnlyLogoutAll(LoginSession session) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(session.accessToken());
        return restTemplate.exchange(
                "/auth/logout-all", HttpMethod.POST, new HttpEntity<>(null, headers), Void.class);
    }

    private ResponseEntity<Void> cookieOnlyLogout(LoginSession session) {
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.COOKIE, "rct_refresh=" + session.cookieValue());
        return restTemplate.exchange(
                "/auth/logout", HttpMethod.POST, new HttpEntity<>(null, headers), Void.class);
    }

    private ResponseEntity<Void> cookieAndBearerLogout(String cookieValue, String bearerToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(bearerToken);
        headers.add(HttpHeaders.COOKIE, "rct_refresh=" + cookieValue);
        return restTemplate.exchange(
                "/auth/logout", HttpMethod.POST, new HttpEntity<>(null, headers), Void.class);
    }

    private ResponseEntity<String> refresh(String refreshToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.COOKIE, "rct_refresh=" + refreshToken);
        return restTemplate.exchange(
                "/auth/refresh", HttpMethod.POST, new HttpEntity<>(null, headers), String.class);
    }

    private List<ResponseEntity<String>> refreshConcurrently(String refreshToken) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CyclicBarrier barrier = new CyclicBarrier(2);
        try {
            Future<ResponseEntity<String>> first =
                    executor.submit(
                            () -> {
                                barrier.await(30, TimeUnit.SECONDS);
                                return refresh(refreshToken);
                            });
            Future<ResponseEntity<String>> second =
                    executor.submit(
                            () -> {
                                barrier.await(30, TimeUnit.SECONDS);
                                return refresh(refreshToken);
                            });
            return List.of(first.get(30, TimeUnit.SECONDS), second.get(30, TimeUnit.SECONDS));
        } finally {
            executor.shutdownNow();
        }
    }

    private String problemCode(ResponseEntity<String> response) throws Exception {
        JsonNode body = objectMapper.readTree(response.getBody());
        return body.path("extras").path("code").asText();
    }

    private static String cookieValue(List<String> setCookieHeaders) {
        String header = refreshCookieHeader(setCookieHeaders);
        int valueStart = header.indexOf('=') + 1;
        int valueEnd = header.indexOf(';', valueStart);
        return header.substring(valueStart, valueEnd < 0 ? header.length() : valueEnd);
    }

    private static String refreshCookieHeader(List<String> setCookieHeaders) {
        return setCookieHeaders.stream()
                .filter(value -> value.startsWith("rct_refresh="))
                .findFirst()
                .orElseThrow();
    }

    private static long cookieMaxAge(String setCookieHeader) {
        String marker = "Max-Age=";
        int valueStart = setCookieHeader.indexOf(marker);
        if (valueStart < 0) {
            throw new AssertionError("Max-Age is missing from " + setCookieHeader);
        }
        valueStart += marker.length();
        int valueEnd = setCookieHeader.indexOf(';', valueStart);
        return Long.parseLong(
                setCookieHeader.substring(
                        valueStart, valueEnd < 0 ? setCookieHeader.length() : valueEnd));
    }

    private long tokenExpiration(String token) throws Exception {
        String[] parts = token.split("\\.", -1);
        assertThat(parts).hasSize(3);
        JsonNode payload =
                objectMapper.readTree(
                        new String(
                                Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8));
        assertThat(payload.path("exp").isIntegralNumber()).isTrue();
        return payload.path("exp").asLong();
    }

    private record LoginSession(
            String accessToken, String refreshToken, String cookieValue, String setCookieHeader) {}
}
