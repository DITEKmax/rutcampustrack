package ru.rutcampustrack.auth.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Connection;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import ru.rutcampustrack.auth.api.DevicePreferencesApi;
import ru.rutcampustrack.auth.device.DevicePreferencesService;
import ru.rutcampustrack.auth.dto.LoginRequest;
import ru.rutcampustrack.auth.dto.TokenResponse;
import ru.rutcampustrack.auth.service.JwtService;
import ru.rutcampustrack.auth.support.AuthDatabaseFixture;

/** Real HTTP + Auth-only Flyway + restricted runtime PostgreSQL principal. */
@Timeout(60)
class DevicePreferencesHttpIT extends AbstractIntegrationTest {
    private static final String PATH = "/auth/device-preferences";
    private static final String HASH =
            "$2a$10$A9r8miSBxjlpjxFB/z0jIerCCSOrLQP6N.sXrjBAw9l7iy4vmRFpi";
    @Autowired TestRestTemplate http;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @Autowired JwtService jwt;
    @Autowired DevicePreferencesService preferences;

    @Test
    void browserPreferenceSurvivesLogoutLoginAndAccountSwitchWhileAnotherJarIsIndependent()
            throws Exception {
        String login = seed("active");
        String bearer = login(login);
        var initial = get(bearer, null);
        assertPreference(initial, null, "0");
        String cookie = cookie(initial);
        assertThat(initial.getHeaders().getFirst(HttpHeaders.SET_COOKIE))
                .contains("Secure", "HttpOnly", "SameSite=Strict", "Path=/", "Max-Age=31536000")
                .doesNotContain("Domain=");
        assertThat(cookie.substring(cookie.indexOf('=') + 1)).matches("[A-Za-z0-9_-]{43}");
        assertPreference(put(bearer, cookie, "dark", "0"), "dark", "1");
        assertPreference(get(bearer, cookie), "dark", "1");
        var logout = call("/auth/logout", HttpMethod.POST, bearer, cookie, null);
        assertThat(logout.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(logout.getHeaders().get(HttpHeaders.SET_COOKIE))
                .allMatch(value -> !value.startsWith(DevicePreferencesApi.COOKIE_NAME + "="));
        assertPreference(get(login(login), cookie), "dark", "1");
        String anotherAccount = login(seed("active"));
        assertPreference(get(anotherAccount, cookie), "dark", "1");
        assertPreference(put(anotherAccount, cookie, "system", "1"), "system", "2");
        var independent = get(anotherAccount, null);
        assertPreference(independent, null, "0");
        assertThat(cookie(independent)).isNotEqualTo(cookie);
        assertPreference(put(anotherAccount, cookie(independent), "light", "0"), "light", "1");
        assertPreference(get(anotherAccount, cookie), "system", "2");
        var columns =
                jdbc.queryForList(
                        "SELECT column_name FROM information_schema.columns WHERE table_name='auth_device_preferences' ORDER BY ordinal_position",
                        String.class);
        assertThat(columns).containsExactly("selector_hash", "theme", "revision", "expires_at");
        byte[] persisted =
                jdbc.queryForObject(
                        "SELECT selector_hash FROM auth_device_preferences WHERE selector_hash=?",
                        byte[].class,
                        selectorHash(cookie));
        assertThat(persisted).hasSize(32).isNotEqualTo(cookie.getBytes(StandardCharsets.US_ASCII));
    }

    @Test
    void cookieNeverAuthenticatesAndRevokedStaleBootstrapAndReadOnlySessionsAreHandled()
            throws Exception {
        String actor = login(seed("active"));
        String cookie = cookie(get(actor, null));
        for (HttpMethod method : List.of(HttpMethod.GET, HttpMethod.PUT)) {
            var noBearer =
                    call(
                            PATH,
                            method,
                            null,
                            cookie,
                            Map.of("theme", "dark", "expectedRevision", "0"));
            problem(noBearer, HttpStatus.UNAUTHORIZED, "INVALID_SESSION", cookie);
        }
        String bootstrap = login(seed(null));
        assertThat(jwt.parseBootstrapToken(bootstrap)).isNotNull();
        assertPreference(get(bootstrap, cookie), null, "0");
        assertPreference(put(bootstrap, cookie, "dark", "0"), "dark", "1");
        String readOnly = login(seed("expelled"));
        assertThat(
                        jwt.parseSessionAccessToken(readOnly)
                                .getPayload()
                                .get("readOnly", Boolean.class))
                .isTrue();
        assertPreference(put(readOnly, cookie, "light", "1"), "light", "2");
        var claims = jwt.parseSessionAccessToken(actor).getPayload();
        UUID sid = UUID.fromString(claims.get("sid", String.class));
        jdbc.update("UPDATE auth_sessions SET session_version=session_version+1 WHERE sid=?", sid);
        problem(get(actor, cookie), HttpStatus.CONFLICT, "SESSION_STATE_STALE", cookie);
        problem(
                put(actor, cookie, "dark", "2"),
                HttpStatus.CONFLICT,
                "SESSION_STATE_STALE",
                cookie);
        assertThat(call("/auth/logout", HttpMethod.POST, readOnly, cookie, null).getStatusCode())
                .isEqualTo(HttpStatus.NO_CONTENT);
        problem(get(readOnly, cookie), HttpStatus.UNAUTHORIZED, "SESSION_REVOKED", cookie);
        problem(
                put(readOnly, cookie, "dark", "2"),
                HttpStatus.UNAUTHORIZED,
                "SESSION_REVOKED",
                cookie);
        assertPreference(get(bootstrap, cookie), "light", "2");
    }

    @Test
    void expiredMissingMalformedContextRequiresGetAndInvalidInputIsRedacted() throws Exception {
        String actor = login(seed("active"));
        problem(put(actor, null, "dark", "0"), HttpStatus.CONFLICT, "DEVICE_CONTEXT_LOST", null);
        problem(
                put(actor, DevicePreferencesApi.COOKIE_NAME + "=sentinel", "dark", "0"),
                HttpStatus.CONFLICT,
                "DEVICE_CONTEXT_LOST",
                "sentinel");
        String cookie = cookie(get(actor, null));
        for (String body :
                List.of(
                        "{\"theme\":null,\"expectedRevision\":\"0\"}",
                        "{\"theme\":\"sentinel\",\"expectedRevision\":\"0\"}",
                        "{\"theme\":\"dark\",\"expectedRevision\":\"01\"}",
                        "{\"theme\":\"dark\",\"expectedRevision\":\"-1\"}",
                        "{\"theme\":\"dark\",\"expectedRevision\":\"9223372036854775808\"}",
                        "{\"theme\":\"dark\"}",
                        "{sentinel")) {
            problem(
                    call(PATH, HttpMethod.PUT, actor, cookie, body),
                    HttpStatus.BAD_REQUEST,
                    "INVALID_REQUEST",
                    "sentinel");
        }
        jdbc.update(
                "UPDATE auth_device_preferences SET expires_at=NOW()-INTERVAL '1 second' WHERE selector_hash=?",
                selectorHash(cookie));
        problem(
                put(actor, cookie, "dark", "0"),
                HttpStatus.CONFLICT,
                "DEVICE_CONTEXT_LOST",
                cookie);
        var replaced = get(actor, cookie);
        assertPreference(replaced, null, "0");
        assertThat(cookie(replaced)).isNotEqualTo(cookie);
        assertThat(preferences.cleanup(1)).isEqualTo(1);
        assertPreference(get(actor, cookie(replaced)), null, "0");
    }

    @Test
    void concurrentCasHasOneWinnerAndLostResponseIsReadBackWithoutDuplicateWrite()
            throws Exception {
        String actor = login(seed("active"));
        String cookie = cookie(get(actor, null));
        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            CyclicBarrier start = new CyclicBarrier(2);
            var first =
                    pool.submit(
                            () -> {
                                start.await();
                                return put(actor, cookie, "light", "0");
                            });
            var second =
                    pool.submit(
                            () -> {
                                start.await();
                                return put(actor, cookie, "dark", "0");
                            });
            var results =
                    List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS));
            assertThat(results)
                    .extracting(ResponseEntity::getStatusCode)
                    .containsExactlyInAnyOrder(HttpStatus.OK, HttpStatus.CONFLICT);
            var winner =
                    results.stream()
                            .filter(r -> r.getStatusCode() == HttpStatus.OK)
                            .findFirst()
                            .orElseThrow();
            var loser =
                    results.stream()
                            .filter(r -> r.getStatusCode() == HttpStatus.CONFLICT)
                            .findFirst()
                            .orElseThrow();
            problem(loser, HttpStatus.CONFLICT, "DEVICE_REVISION_CONFLICT", cookie);
            assertPreference(
                    get(actor, cookie),
                    json.readTree(winner.getBody()).path("theme").asText(),
                    "1");
        }
        // A lost response does not license blind replay: the old CAS revision cannot write twice.
        assertPreference(put(actor, cookie, "system", "1"), "system", "2");
        problem(
                put(actor, cookie, "system", "1"),
                HttpStatus.CONFLICT,
                "DEVICE_REVISION_CONFLICT",
                cookie);
        assertPreference(get(actor, cookie), "system", "2");
    }

    @Test
    void storageAndDeferredCommitFailureNeverAcknowledgeOrLeakJdbcDetails() throws Exception {
        String actor = login(seed("active"));
        String cookie = cookie(get(actor, null));
        JdbcTemplate owner = authFixtureOwnerJdbc();
        owner.execute(
                "ALTER TABLE auth_device_preferences RENAME TO device_preferences_unavailable_fixture");
        try {
            problem(
                    get(actor, cookie),
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "DEVICE_PREFERENCES_UNAVAILABLE",
                    cookie);
            problem(
                    put(actor, cookie, "dark", "0"),
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "DEVICE_PREFERENCES_UNAVAILABLE",
                    cookie);
        } finally {
            owner.execute(
                    "ALTER TABLE device_preferences_unavailable_fixture RENAME TO auth_device_preferences");
        }
        owner.execute(
                "CREATE FUNCTION device_preferences_reject_commit_fixture() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'DEVICE_SQL_SENTINEL'; END $$");
        owner.execute(
                "CREATE CONSTRAINT TRIGGER device_preferences_commit_fixture AFTER UPDATE ON auth_device_preferences DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION device_preferences_reject_commit_fixture()");
        try {
            problem(
                    put(actor, cookie, "dark", "0"),
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "DEVICE_PREFERENCES_UNAVAILABLE",
                    "DEVICE_SQL_SENTINEL");
        } finally {
            owner.execute(
                    "DROP TRIGGER device_preferences_commit_fixture ON auth_device_preferences");
            owner.execute("DROP FUNCTION device_preferences_reject_commit_fixture()");
        }
        assertPreference(get(actor, cookie), null, "0");
    }

    @Test
    void admissionLocksRemainUntilPreferenceCommitBeforeConcurrentLogoutCanRevoke()
            throws Exception {
        String actor = login(seed("active"));
        String cookie = cookie(get(actor, null));
        JdbcTemplate owner = authFixtureOwnerJdbc();
        owner.execute(
                "CREATE FUNCTION device_preferences_wait_fixture() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN PERFORM pg_advisory_xact_lock(2026102401); RETURN NEW; END $$");
        owner.execute(
                "CREATE TRIGGER device_preferences_wait_fixture BEFORE UPDATE ON auth_device_preferences FOR EACH ROW EXECUTE FUNCTION device_preferences_wait_fixture()");
        try (Connection gate = owner.getDataSource().getConnection();
                ExecutorService pool = Executors.newFixedThreadPool(2)) {
            gate.setAutoCommit(false);
            gate.createStatement().execute("SELECT pg_advisory_xact_lock(2026102401)");
            try {
                Future<ResponseEntity<String>> write =
                        pool.submit(() -> put(actor, cookie, "dark", "0"));
                await(
                        () ->
                                owner.queryForObject(
                                                "SELECT count(*) FROM pg_stat_activity WHERE wait_event='advisory' AND query LIKE 'UPDATE auth_device_preferences SET theme=%'",
                                                Integer.class)
                                        == 1);
                Future<ResponseEntity<String>> logout =
                        pool.submit(
                                () -> call("/auth/logout", HttpMethod.POST, actor, cookie, null));
                // PostgreSQL proves another request is waiting on admission's user lock.
                await(
                        () ->
                                owner.queryForObject(
                                                "SELECT count(*) FROM pg_stat_activity WHERE wait_event_type='Lock' AND query LIKE '%FROM auth_identities%' AND query LIKE '%FOR UPDATE%'",
                                                Integer.class)
                                        >= 1);
                assertThat(logout.isDone()).isFalse();
                gate.commit();
                assertPreference(write.get(20, TimeUnit.SECONDS), "dark", "1");
                assertThat(logout.get(20, TimeUnit.SECONDS).getStatusCode())
                        .isEqualTo(HttpStatus.NO_CONTENT);
            } finally {
                gate.rollback();
            }
        } finally {
            owner.execute(
                    "DROP TRIGGER device_preferences_wait_fixture ON auth_device_preferences");
            owner.execute("DROP FUNCTION device_preferences_wait_fixture()");
        }
        problem(
                put(actor, cookie, "light", "1"),
                HttpStatus.UNAUTHORIZED,
                "SESSION_REVOKED",
                cookie);
        assertPreference(get(login(seed("active")), cookie), "dark", "1");
    }

    private String seed(String status) {
        String login = "device-" + UUID.randomUUID().toString().substring(0, 20);
        long user = AuthDatabaseFixture.identity(jdbc, login, Instant.now(), HASH, "password");
        if (status != null)
            jdbc.update(
                    "INSERT INTO user_role_grants(user_id,role,status,group_id) VALUES (?,'student',?,1)",
                    user,
                    status);
        return login;
    }

    private String login(String login) {
        var response =
                http.postForEntity(
                        "/auth/login", new LoginRequest(login, "password"), TokenResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody().accessToken();
    }

    private ResponseEntity<String> get(String actor, String cookie) {
        return call(PATH, HttpMethod.GET, actor, cookie, null);
    }

    private ResponseEntity<String> put(String actor, String cookie, String theme, String revision) {
        return call(
                PATH,
                HttpMethod.PUT,
                actor,
                cookie,
                Map.of("theme", theme, "expectedRevision", revision));
    }

    private ResponseEntity<String> call(
            String path, HttpMethod method, String actor, String cookie, Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (actor != null) headers.setBearerAuth(actor);
        if (cookie != null) headers.set(HttpHeaders.COOKIE, cookie);
        return http.exchange(path, method, new HttpEntity<>(body, headers), String.class);
    }

    private String cookie(ResponseEntity<String> response) {
        String header = response.getHeaders().getFirst(HttpHeaders.SET_COOKIE);
        assertThat(header).startsWith(DevicePreferencesApi.COOKIE_NAME + "=");
        return header.substring(0, header.indexOf(';'));
    }

    private byte[] selectorHash(String cookie) throws Exception {
        return MessageDigest.getInstance("SHA-256")
                .digest(
                        cookie.substring(cookie.indexOf('=') + 1)
                                .getBytes(StandardCharsets.US_ASCII));
    }

    private void assertPreference(ResponseEntity<String> response, String theme, String revision)
            throws Exception {
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getCacheControl()).isEqualTo("no-store");
        JsonNode body = json.readTree(response.getBody());
        assertThat(body.size()).isEqualTo(2);
        assertThat(body.has("theme")).isTrue();
        if (theme == null) assertThat(body.path("theme").isNull()).isTrue();
        else assertThat(body.path("theme").asText()).isEqualTo(theme);
        assertThat(body.path("revision").isTextual()).isTrue();
        assertThat(body.path("revision").asText()).isEqualTo(revision);
    }

    private void problem(
            ResponseEntity<String> response, HttpStatus status, String code, String sensitive) {
        assertThat(response.getStatusCode()).isEqualTo(status);
        assertThat(response.getHeaders().getCacheControl()).isEqualTo("no-store");
        assertThat(
                        response.getHeaders()
                                .getContentType()
                                .isCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .isTrue();
        assertThat(response.getBody())
                .contains(code)
                .doesNotContain("selector_hash", "PSQLException", "SQLSTATE");
        if (sensitive != null) assertThat(response.getBody()).doesNotContain(sensitive);
    }

    private static void await(BooleanSupplier condition) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) Thread.sleep(25);
        assertThat(condition.getAsBoolean())
                .as("Expected observable PostgreSQL lock wait")
                .isTrue();
    }
}
