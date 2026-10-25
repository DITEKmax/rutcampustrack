package ru.rutcampustrack.auth.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import ru.rutcampustrack.auth.dto.*;
import ru.rutcampustrack.auth.service.JwtService;
import ru.rutcampustrack.auth.support.AuthDatabaseFixture;

/** Real HTTP/Security + Auth-only PostgreSQL authority, CAS, audit/outbox and commit failures. */
@TestPropertySource(
        properties = "auth.password-reset-url=https://client.example.test/password-reset")
@Timeout(60)
class AdminGeneratedPasswordHttpIT extends AbstractIntegrationTest {
    private static final String HASH =
            "$2a$10$A9r8miSBxjlpjxFB/z0jIerCCSOrLQP6N.sXrjBAw9l7iy4vmRFpi";
    @Autowired TestRestTemplate http;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @Autowired JwtService jwt;
    @Autowired PasswordEncoder encoder;
    @Autowired PlatformTransactionManager manager;

    @Test
    void generationHasDurableReadbackRevokesOldMaterialAndDisappearsAfterOwnPasswordChange()
            throws Exception {
        Actor admin = admin(true, true);
        User target = user("teacher", null, null);
        long telegram = 8_100_000_000L + target.id();
        jdbc.update("UPDATE auth_identities SET telegram_id=? WHERE id=?", telegram, target.id());
        jdbc.update(
                """
                INSERT INTO bot_credential_messages(user_id,private_chat_id,message_id,telegram_binding_version,credentials_version)
                SELECT id,telegram_id,?,telegram_binding_version,credentials_version FROM auth_identities WHERE id=?
                """,
                target.id(),
                target.id());
        TokenResponse old = login(target.login(), "password");
        JsonNode initial = view(read(admin.token(), target.id()));
        assertThat(initial.path("state").asText()).isEqualTo("GENERATED");
        assertThat(initial.path("origin").asText()).isEqualTo("initial");
        String before = initial.path("credentialsVersion").asText();
        var link =
                call(
                        admin.token(),
                        "/auth/admin/users/" + target.id() + "/password-reset-link",
                        HttpMethod.POST,
                        null);
        assertThat(link.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String ticket = resetTicket(json.readTree(link.getBody()).path("url").asText());
        var generatedResponse = generate(admin.token(), target.id(), before);
        JsonNode generated = view(generatedResponse);
        String password = generated.path("password").asText();
        assertThat(!password.isBlank() && password.length() == 12 && !password.equals("password"))
                .isTrue();
        assertThat(generated.path("origin").asText()).isEqualTo("admin_reset");
        assertThat(generated.path("credentialsVersion").asText())
                .isEqualTo(Long.toString(Long.parseLong(before) + 1));
        assertThat(matches(target.id(), password)).isTrue();
        assertThat(loginStatus(target.login(), "password")).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(call(old.accessToken(), "/auth/session", HttpMethod.GET, null).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
        HttpHeaders refreshHeaders = new HttpHeaders();
        refreshHeaders.set(HttpHeaders.COOKIE, "rct_refresh=" + old.refreshToken());
        assertThat(
                        http.exchange(
                                        "/auth/refresh",
                                        HttpMethod.POST,
                                        new HttpEntity<>(refreshHeaders),
                                        String.class)
                                .getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(
                        http.postForEntity(
                                        "/auth/password-reset/complete",
                                        new PasswordResetCompleteRequest(
                                                ticket, "Rejected-reset-2026!"),
                                        String.class)
                                .getStatusCode())
                .isEqualTo(HttpStatus.GONE);
        JsonNode readback = view(read(admin.token(), target.id()));
        assertThat(readback.path("password").asText().equals(password))
                .as("readback returns the committed secret, without regeneration")
                .isTrue();
        assertThat(readback.path("credentialsVersion").asText())
                .isEqualTo(generated.path("credentialsVersion").asText());
        var replay = generate(admin.token(), target.id(), before);
        problem(replay, HttpStatus.CONFLICT, "CREDENTIAL_VERSION_CONFLICT");
        assertThat(replay.getBody().contains(password)).isFalse();
        assertThat(
                        count(
                                "SELECT count(*) FROM account_security_events WHERE user_id=? AND event_type='PASSWORD_CHANGED'",
                                target.id()))
                .isEqualTo(1);
        assertThat(
                        count(
                                "SELECT count(*) FROM audit_log WHERE target_id=? AND action='ADMIN_PASSWORD_RESET'",
                                target.id()))
                .isEqualTo(1);
        assertThat(
                        count(
                                "SELECT count(*) FROM auth_outbox WHERE event_type='password.changed' AND payload->'payload'->>'user_id'=?",
                                Long.toString(target.id())))
                .isEqualTo(1);
        assertThat(
                        count(
                                "SELECT count(*) FROM bot_credential_messages WHERE user_id=? AND cleanup_state='PENDING' AND invalidated_by_credentials_version=2",
                                target.id()))
                .isEqualTo(1);
        String payload =
                jdbc.queryForObject(
                        "SELECT payload::text FROM auth_outbox WHERE event_type='password.changed' AND payload->'payload'->>'user_id'=?",
                        String.class,
                        Long.toString(target.id()));
        assertThat(
                        payload.contains(password)
                                || payload.contains(HASH)
                                || payload.contains("initial_password"))
                .as("outbox carries only versioned notification metadata")
                .isFalse();
        String audit =
                jdbc.queryForObject(
                        "SELECT extras::text FROM audit_log WHERE target_id=? AND action='ADMIN_PASSWORD_RESET'",
                        String.class,
                        target.id());
        assertThat(audit.contains(password) || audit.contains(HASH)).isFalse();
        TokenResponse current = login(target.login(), password);
        String chosen = "User-chosen-2026!";
        assertThat(
                        call(
                                        current.accessToken(),
                                        "/auth/change-password",
                                        HttpMethod.POST,
                                        new ChangePasswordRequest(password, chosen))
                                .getStatusCode())
                .isEqualTo(HttpStatus.NO_CONTENT);
        JsonNode after = view(read(admin.token(), target.id()));
        assertThat(after.path("state").asText()).isEqualTo("USER_CHANGED");
        assertThat(after.path("origin").isNull() && after.path("password").isNull()).isTrue();
        assertThat(
                        count(
                                "SELECT count(*) FROM auth_identities WHERE id=? AND password_changed AND initial_password IS NULL AND generated_password_origin IS NULL AND credentials_version=3",
                                target.id()))
                .isEqualTo(1);
        assertThat(matches(target.id(), chosen)).isTrue();
        assertThat(loginStatus(target.login(), password)).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(loginStatus(target.login(), chosen)).isEqualTo(HttpStatus.OK);
    }

    @Test
    void onlyLivePermittedAdminCanAccessAndUnavailableTargetCannotChange() throws Exception {
        User target = user("student", "active", null);
        Actor noPermission = admin(false, false);
        Actor permitted = admin(false, true);
        User ordinary = user("teacher", null, null);
        User bootstrap = user(null, null, null);
        for (String actor :
                Arrays.asList(
                        null,
                        login(ordinary.login(), "password").accessToken(),
                        login(bootstrap.login(), "password").accessToken(),
                        noPermission.token())) {
            for (HttpMethod method : List.of(HttpMethod.GET, HttpMethod.POST)) {
                String suffix =
                        method == HttpMethod.GET ? "/initial-password" : "/generated-password";
                var denied =
                        call(
                                actor,
                                "/auth/admin/users/" + target.id() + suffix,
                                method,
                                Map.of("expectedCredentialsVersion", "1"));
                assertThat(denied.getStatusCode().is4xxClientError()).isTrue();
                noStore(denied);
                assertThat(
                                denied.getBody() == null
                                        || !denied.getBody().contains("initial_password"))
                        .isTrue();
            }
        }
        assertThat(matches(target.id(), "password")).isTrue();
        assertThat(view(read(permitted.token(), target.id())).path("credentialsVersion").asText())
                .isEqualTo("1");
        Actor stale = admin(true, true);
        UUID sid =
                UUID.fromString(
                        jwt.parseSessionAccessToken(stale.token())
                                .getPayload()
                                .get("sid", String.class));
        jdbc.update("UPDATE auth_sessions SET session_version=session_version+1 WHERE sid=?", sid);
        problem(
                generate(stale.token(), target.id(), "1"),
                HttpStatus.CONFLICT,
                "SESSION_STATE_STALE");
        Actor revoked = admin(true, true);
        assertThat(call(revoked.token(), "/auth/logout", HttpMethod.POST, null).getStatusCode())
                .isEqualTo(HttpStatus.NO_CONTENT);
        problem(
                generate(revoked.token(), target.id(), "1"),
                HttpStatus.UNAUTHORIZED,
                "SESSION_REVOKED");
        User archived = user("teacher", null, null);
        new TransactionTemplate(manager)
                .executeWithoutResult(
                        status -> {
                            UUID operation = AuthDatabaseFixture.fence(jdbc, archived.id());
                            jdbc.update(
                                    "UPDATE auth_identities SET account_state='ARCHIVED' WHERE id=?",
                                    archived.id());
                            AuthDatabaseFixture.unfence(jdbc, operation, archived.id());
                        });
        User fenced = user("teacher", null, null);
        new TransactionTemplate(manager)
                .executeWithoutResult(status -> AuthDatabaseFixture.fence(jdbc, fenced.id()));
        for (long unavailable : List.of(archived.id(), fenced.id())) {
            problem(
                    read(permitted.token(), unavailable),
                    HttpStatus.CONFLICT,
                    "CREDENTIAL_TARGET_UNAVAILABLE");
            problem(
                    generate(permitted.token(), unavailable, "1"),
                    HttpStatus.CONFLICT,
                    "CREDENTIAL_TARGET_UNAVAILABLE");
            assertThat(matches(unavailable, "password")).isTrue();
        }
        problem(
                read(permitted.token(), Long.MAX_VALUE),
                HttpStatus.NOT_FOUND,
                "CREDENTIAL_TARGET_NOT_FOUND");
        assertThat(
                        count(
                                "SELECT count(*) FROM audit_log WHERE target_id=? AND action='ADMIN_PASSWORD_RESET'",
                                target.id()))
                .isZero();
    }

    @Test
    void fullCanResetActiveAdminLimitedCannotAndSelfUnknownOutcomeDoesNotAuthorizeBlindRetry()
            throws Exception {
        Actor full = admin(true, true);
        Actor limited = admin(false, true);
        Actor target = admin(true, true);
        var grants =
                jdbc.queryForList(
                        "SELECT * FROM user_role_grants WHERE user_id=? ORDER BY id",
                        target.user().id());
        var profile =
                jdbc.queryForList(
                        "SELECT assigned,mode,permissions::text,version,updated_at FROM admin_access_profiles WHERE user_id=?",
                        target.user().id());
        problem(
                read(limited.token(), target.user().id()),
                HttpStatus.FORBIDDEN,
                "ADMIN_TARGET_PROTECTED");
        problem(
                generate(limited.token(), target.user().id(), "1"),
                HttpStatus.FORBIDDEN,
                "ADMIN_TARGET_PROTECTED");
        JsonNode generated = view(generate(full.token(), target.user().id(), "1"));
        assertThat(generated.path("origin").asText()).isEqualTo("admin_reset");
        assertThat(
                        grants.equals(
                                jdbc.queryForList(
                                        "SELECT * FROM user_role_grants WHERE user_id=? ORDER BY id",
                                        target.user().id())))
                .isTrue();
        assertThat(
                        profile.equals(
                                jdbc.queryForList(
                                        "SELECT assigned,mode,permissions::text,version,updated_at FROM admin_access_profiles WHERE user_id=?",
                                        target.user().id())))
                .isTrue();
        assertThat(call(target.token(), "/auth/session", HttpMethod.GET, null).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
        String recoveryActor =
                login(target.user().login(), generated.path("password").asText()).accessToken();
        // Existing FULL target policy permits self. Its old bearer is correctly revoked too.
        assertThat(generate(full.token(), full.user().id(), "1").getStatusCode())
                .isEqualTo(HttpStatus.OK);
        problem(read(full.token(), full.user().id()), HttpStatus.UNAUTHORIZED, "SESSION_REVOKED");
        problem(
                generate(full.token(), full.user().id(), "1"),
                HttpStatus.UNAUTHORIZED,
                "SESSION_REVOKED");
        JsonNode selfReadback = view(read(recoveryActor, full.user().id()));
        assertThat(selfReadback.path("credentialsVersion").asText()).isEqualTo("2");
        assertThat(loginStatus(full.user().login(), selfReadback.path("password").asText()))
                .isEqualTo(HttpStatus.OK);
    }

    @Test
    void simultaneousAdminGenerationsHaveOneWinnerAndLosingRevisionCannotDiscloseOrOverwriteIt()
            throws Exception {
        Actor first = admin(true, true);
        Actor second = admin(true, true);
        User target = user("teacher", null, null);
        CyclicBarrier start = new CyclicBarrier(2);
        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            Future<ResponseEntity<String>> a =
                    pool.submit(
                            () -> {
                                start.await();
                                return generate(first.token(), target.id(), "1");
                            });
            Future<ResponseEntity<String>> b =
                    pool.submit(
                            () -> {
                                start.await();
                                return generate(second.token(), target.id(), "1");
                            });
            var results = List.of(a.get(20, TimeUnit.SECONDS), b.get(20, TimeUnit.SECONDS));
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
            JsonNode committed = view(winner);
            problem(loser, HttpStatus.CONFLICT, "CREDENTIAL_VERSION_CONFLICT");
            assertThat(loser.getBody().contains(committed.path("password").asText())).isFalse();
            JsonNode readback = view(read(first.token(), target.id()));
            assertThat(
                            readback.path("password")
                                    .asText()
                                    .equals(committed.path("password").asText()))
                    .isTrue();
            assertThat(readback.path("credentialsVersion").asText()).isEqualTo("2");
            assertThat(
                            count(
                                    "SELECT count(*) FROM audit_log WHERE target_id=? AND action='ADMIN_PASSWORD_RESET'",
                                    target.id()))
                    .isEqualTo(1);
            assertThat(
                            count(
                                    "SELECT count(*) FROM account_security_events WHERE user_id=? AND event_type='PASSWORD_CHANGED'",
                                    target.id()))
                    .isEqualTo(1);
        }
    }

    @Test
    void deferredAuditAndOutboxFailuresRollBackCredentialSessionsTicketsEventsAndDisclosure()
            throws Exception {
        Actor admin = admin(true, true);
        User target = user("teacher", null, null);
        jdbc.update(
                "UPDATE auth_identities SET telegram_id=? WHERE id=?",
                8_200_000_000L + target.id(),
                target.id());
        login(target.login(), "password");
        assertThat(
                        call(
                                        admin.token(),
                                        "/auth/admin/users/" + target.id() + "/password-reset-link",
                                        HttpMethod.POST,
                                        null)
                                .getStatusCode())
                .isEqualTo(HttpStatus.CREATED);
        JdbcTemplate owner = authFixtureOwnerJdbc();
        for (String table : List.of("audit_log", "auth_outbox")) {
            String condition =
                    table.equals("audit_log")
                            ? "NEW.target_id="
                                    + target.id()
                                    + " AND NEW.action IN ('ADMIN_PASSWORD_RESET','ADMIN_PASSWORD_SHOWN')"
                            : "NEW.payload->'payload'->>'user_id'='" + target.id() + "'";
            owner.execute(
                    "CREATE FUNCTION admin_credential_commit_fixture() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF "
                            + condition
                            + " THEN RAISE EXCEPTION 'ADMIN_CREDENTIAL_SQL_SENTINEL'; END IF; RETURN NEW; END $$");
            owner.execute(
                    "CREATE CONSTRAINT TRIGGER admin_credential_commit_fixture AFTER INSERT ON "
                            + table
                            + " DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION admin_credential_commit_fixture()");
            try {
                Snapshot before = snapshot(target.id());
                if (table.equals("audit_log")) {
                    var deniedRead = read(admin.token(), target.id());
                    problem(deniedRead, HttpStatus.SERVICE_UNAVAILABLE, "AUTHORITY_UNAVAILABLE");
                    assertThat(deniedRead.getBody().contains("ADMIN_CREDENTIAL_SQL_SENTINEL"))
                            .isFalse();
                }
                var deniedWrite = generate(admin.token(), target.id(), "1");
                problem(deniedWrite, HttpStatus.SERVICE_UNAVAILABLE, "AUTHORITY_UNAVAILABLE");
                assertThat(
                                deniedWrite.getBody().contains("ADMIN_CREDENTIAL_SQL_SENTINEL")
                                        || deniedWrite.getBody().contains("password_hash"))
                        .isFalse();
                assertThat(before.equals(snapshot(target.id())))
                        .as("late failure rolls every credential side effect back")
                        .isTrue();
                assertThat(matches(target.id(), "password")).isTrue();
            } finally {
                owner.execute("DROP TRIGGER admin_credential_commit_fixture ON " + table);
                owner.execute("DROP FUNCTION admin_credential_commit_fixture()");
            }
        }
        assertThat(view(read(admin.token(), target.id())).path("credentialsVersion").asText())
                .isEqualTo("1");
    }

    @Test
    void invalidVersionAndUnavailableGeneratedMaterialNeverEchoSecretsOrMutateCredential()
            throws Exception {
        Actor admin = admin(true, true);
        User target = user("teacher", null, null);
        for (String version :
                List.of("0", "01", "-1", "9223372036854775808", "REJECTED_CREDENTIAL_SENTINEL")) {
            var response = generate(admin.token(), target.id(), version);
            problem(response, HttpStatus.BAD_REQUEST, "INVALID_REQUEST");
            assertThat(response.getBody().contains("REJECTED_CREDENTIAL_SENTINEL")).isFalse();
        }
        for (Object body : List.of(Map.of(), "{REJECTED_CREDENTIAL_SENTINEL")) {
            var response =
                    call(
                            admin.token(),
                            "/auth/admin/users/" + target.id() + "/generated-password",
                            HttpMethod.POST,
                            body);
            problem(response, HttpStatus.BAD_REQUEST, "INVALID_REQUEST");
            assertThat(response.getBody().contains("REJECTED_CREDENTIAL_SENTINEL")).isFalse();
        }
        jdbc.update(
                "UPDATE auth_identities SET generated_password_origin=NULL WHERE id=?",
                target.id());
        JsonNode unavailable = view(read(admin.token(), target.id()));
        assertThat(unavailable.path("state").asText()).isEqualTo("UNAVAILABLE");
        assertThat(unavailable.path("password").isNull() && unavailable.path("origin").isNull())
                .isTrue();
        assertThat(matches(target.id(), "password")).isTrue();
        JsonNode result = view(generate(admin.token(), target.id(), "1"));
        assertThat(result.path("origin").asText()).isEqualTo("admin_reset");
        assertThat(result.path("credentialsVersion").asText()).isEqualTo("2");
    }

    private Actor admin(boolean full, boolean credentials) {
        User user =
                user(
                        "admin",
                        null,
                        full
                                ? List.of()
                                : credentials
                                        ? List.of("USERS_VIEW", "USER_CREDENTIALS_MANAGE")
                                        : List.of("USERS_VIEW"));
        return new Actor(user, login(user.login(), "password").accessToken());
    }

    private User user(String role, String status, List<String> permissions) {
        String login = "admincred-" + UUID.randomUUID().toString().substring(0, 18);
        long id = AuthDatabaseFixture.identity(jdbc, login, Instant.now(), HASH, "password");
        if (role != null)
            jdbc.update(
                    "INSERT INTO user_role_grants(user_id,role,status,group_id) VALUES (?,?,?,?)",
                    id,
                    role,
                    status == null ? "active" : status,
                    role.equals("student") ? 1L : null);
        if (role != null && role.equals("admin")) {
            String mode = permissions.isEmpty() ? "FULL" : "LIMITED";
            jdbc.update(
                    "INSERT INTO admin_access_profiles(user_id,assigned,mode,permissions,version) VALUES (?,TRUE,?,?::text[],1)",
                    id,
                    mode,
                    "{" + String.join(",", permissions) + "}");
        }
        return new User(id, login);
    }

    private TokenResponse login(String login, String password) {
        var response =
                http.postForEntity(
                        "/auth/login", new LoginRequest(login, password), TokenResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody() != null).isTrue();
        TokenResponse initial = response.getBody();
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(initial.accessToken());
        var current =
                http.exchange(
                        "/auth/session",
                        HttpMethod.GET,
                        new HttpEntity<>(headers),
                        CurrentSessionResponse.class);
        assertThat(current.getStatusCode()).isEqualTo(HttpStatus.OK);
        var selectable =
                current.getBody().roles().stream().filter(RoleGrantResponse::selectable).toList();
        if (current.getBody().activeRole() == null && selectable.size() == 1) {
            // Complete the real bootstrap -> selected-role protocol before protected operations.
            var selected =
                    http.exchange(
                            "/auth/session/active-role",
                            HttpMethod.PUT,
                            new HttpEntity<>(
                                    new SelectActiveRoleRequest(
                                            selectable.getFirst().role(),
                                            current.getBody().sessionVersion()),
                                    headers),
                            SelectActiveRoleResponse.class);
            assertThat(selected.getStatusCode()).isEqualTo(HttpStatus.OK);
            return new TokenResponse(
                    selected.getBody().accessToken(),
                    initial.refreshToken(),
                    selected.getBody().expiresIn());
        }
        return initial;
    }

    private HttpStatusCode loginStatus(String login, String password) {
        return http.postForEntity("/auth/login", new LoginRequest(login, password), String.class)
                .getStatusCode();
    }

    private ResponseEntity<String> read(String token, long target) {
        return call(
                token, "/auth/admin/users/" + target + "/initial-password", HttpMethod.GET, null);
    }

    private ResponseEntity<String> generate(String token, long target, String version) {
        return call(
                token,
                "/auth/admin/users/" + target + "/generated-password",
                HttpMethod.POST,
                Map.of("expectedCredentialsVersion", version));
    }

    private ResponseEntity<String> call(String token, String path, HttpMethod method, Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (token != null) headers.setBearerAuth(token);
        return http.exchange(path, method, new HttpEntity<>(body, headers), String.class);
    }

    private JsonNode view(ResponseEntity<String> response) throws Exception {
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        noStore(response);
        JsonNode body = json.readTree(response.getBody());
        assertThat(body.path("credentialsVersion").isTextual()).isTrue();
        assertThat(body.size()).isEqualTo(5);
        return body;
    }

    private void problem(ResponseEntity<String> response, HttpStatus status, String code) {
        assertThat(response.getStatusCode()).isEqualTo(status);
        noStore(response);
        assertThat(
                        response.getHeaders()
                                .getContentType()
                                .isCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .isTrue();
        assertThat(response.getBody())
                .contains(code)
                .doesNotContain("password_hash", "PSQLException", "SQLSTATE");
    }

    private static void noStore(ResponseEntity<?> response) {
        assertThat(response.getHeaders().getCacheControl()).contains("no-store");
    }

    private long count(String sql, Object... args) {
        return jdbc.queryForObject(sql, Long.class, args);
    }

    private boolean matches(long user, String password) {
        return encoder.matches(
                password,
                jdbc.queryForObject(
                        "SELECT password_hash FROM auth_identities WHERE id=?",
                        String.class,
                        user));
    }

    private static String resetTicket(String link) {
        String fragment = java.net.URI.create(link).getRawFragment();
        return fragment.substring("resetTicket=".length(), fragment.indexOf('&'));
    }

    private Snapshot snapshot(long target) {
        return new Snapshot(
                jdbc.queryForList(
                        "SELECT password_hash,initial_password,password_changed,generated_password_origin,credentials_version FROM auth_identities WHERE id=?",
                        target),
                jdbc.queryForList(
                        "SELECT * FROM auth_sessions WHERE user_id=? ORDER BY sid", target),
                jdbc.queryForList(
                        "SELECT * FROM password_reset_tokens WHERE user_id=? ORDER BY id", target),
                count(
                        "SELECT count(*) FROM auth_outbox WHERE event_type='password.changed' AND payload->'payload'->>'user_id'=?",
                        Long.toString(target)),
                count(
                        "SELECT count(*) FROM audit_log WHERE target_id=? AND action IN ('ADMIN_PASSWORD_SHOWN','ADMIN_PASSWORD_RESET')",
                        target),
                count(
                        "SELECT count(*) FROM account_security_events WHERE user_id=? AND event_type='PASSWORD_CHANGED'",
                        target));
    }

    private record User(long id, String login) {}

    private record Actor(User user, String token) {
        @Override
        public String toString() {
            return "Actor[redacted]";
        }
    }

    private record Snapshot(
            List<Map<String, Object>> credentials,
            List<Map<String, Object>> sessions,
            List<Map<String, Object>> tickets,
            long outbox,
            long audit,
            long events) {
        @Override
        public String toString() {
            return "CredentialSnapshot[redacted]";
        }
    }
}
