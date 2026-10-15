package ru.rutcampustrack.auth.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import ru.rutcampustrack.auth.dto.ChangePasswordRequest;
import ru.rutcampustrack.auth.dto.PasswordResetCompleteRequest;
import ru.rutcampustrack.auth.event.PasswordChangedOutbox;
import ru.rutcampustrack.auth.service.JwtService;
import ru.rutcampustrack.auth.session.SessionLifecycleService;
import ru.rutcampustrack.auth.session.model.AuthMethod;
import ru.rutcampustrack.auth.session.model.AuthRole;
import ru.rutcampustrack.auth.session.port.AuthSessionQueryPort;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/** Real HTTP/Security/PG transaction proof. Secret comparisons stay boolean and private in RAM. */
@TestPropertySource(
        properties = "auth.password-reset-url=https://client.example.test/password-reset")
class HeadmanCredentialsHttpIT extends AbstractIntegrationTest {
    @Autowired TestRestTemplate rest;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @Autowired PasswordEncoder encoder;
    @Autowired SessionLifecycleService sessions;
    @Autowired AuthSessionQueryPort queries;
    @Autowired JwtService jwt;
    @MockitoSpyBean PasswordChangedOutbox outbox;

    @AfterEach
    void restoreOutbox() {
        reset(outbox);
    }

    @Test
    void generatedResetIsScopedVersionedAuditedAndClearedByStudentChange() throws Exception {
        Fixture f = fixture();
        String oldStudent = bearer(f.student, AuthRole.STUDENT);
        String actor = bearer(f.headman, AuthRole.HEADMAN);
        JsonNode initial =
                body(call(actor, f.student, "/initial-password", HttpMethod.GET, null), 200);
        assertThat(initial.path("state").asText()).isEqualTo("GENERATED");
        assertThat(initial.path("origin").asText()).isEqualTo("initial");
        String before = initial.path("credentialsVersion").asText();
        JsonNode generated =
                body(
                        call(
                                actor,
                                f.student,
                                "/generated-password",
                                HttpMethod.POST,
                                Map.of("expectedCredentialsVersion", before)),
                        200);
        String password = generated.path("password").asText();
        assertThat(!password.isBlank() && !password.equals(f.initial))
                .as("new generated password is private and distinct")
                .isTrue();
        assertThat(generated.path("origin").asText()).isEqualTo("headman_reset");
        assertThat(Long.parseLong(generated.path("credentialsVersion").asText()))
                .isEqualTo(Long.parseLong(before) + 1);
        assertThat(matches(f.student, password))
                .as("stored hash verifies the returned generated value")
                .isTrue();
        assertThat(
                        exchange(oldStudent, "/auth/session", HttpMethod.GET, null)
                                .getStatusCode()
                                .value())
                .isEqualTo(401);
        assertThat(
                        call(
                                        actor,
                                        f.student,
                                        "/generated-password",
                                        HttpMethod.POST,
                                        Map.of("expectedCredentialsVersion", before))
                                .getStatusCode()
                                .value())
                .isEqualTo(409);
        assertThat(
                        count(
                                "SELECT count(*) FROM account_security_events WHERE user_id=? AND"
                                        + " event_type='PASSWORD_CHANGED'",
                                f.student))
                .isEqualTo(1);
        assertThat(
                        count(
                                "SELECT count(*) FROM audit_log WHERE target_id=? AND"
                                        + " action='HEADMAN_PASSWORD_RESET'",
                                f.student))
                .isEqualTo(1);
        assertThat(
                        count(
                                "SELECT count(*) FROM auth_outbox WHERE"
                                        + " event_type='password.changed' AND"
                                        + " payload->'payload'->>'user_id'=?",
                                Long.toString(f.student)))
                .isEqualTo(1);
        String currentStudent = bearer(f.student, AuthRole.STUDENT);
        String replacement = "Student-chosen-2026!";
        assertThat(
                        exchange(
                                        currentStudent,
                                        "/auth/change-password",
                                        HttpMethod.POST,
                                        new ChangePasswordRequest(password, replacement))
                                .getStatusCode()
                                .is2xxSuccessful())
                .isTrue();
        JsonNode changed =
                body(call(actor, f.student, "/initial-password", HttpMethod.GET, null), 200);
        assertThat(changed.path("state").asText()).isEqualTo("STUDENT_CHANGED");
        assertThat(changed.path("password").isNull() && changed.path("origin").isNull())
                .as("chosen credential is never disclosed")
                .isTrue();
        assertThat(
                        count(
                                "SELECT count(*) FROM users WHERE id=? AND initial_password IS NULL"
                                        + " AND generated_password_origin IS NULL",
                                f.student))
                .isEqualTo(1);
        assertThat(matches(f.student, replacement)).isTrue();
    }

    @Test
    void linkIssuePreservesSessionAndUsesExistingOneUsePublicCompletion() throws Exception {
        Fixture f = fixture();
        String studentBearer = bearer(f.student, AuthRole.STUDENT);
        String actor = bearer(f.headman, AuthRole.HEADMAN);
        long version = count("SELECT credentials_version FROM users WHERE id=?", f.student);
        JsonNode first =
                body(call(actor, f.student, "/password-reset-link", HttpMethod.POST, null), 200);
        JsonNode second =
                body(call(actor, f.student, "/password-reset-link", HttpMethod.POST, null), 200);
        assertThat(
                        exchange(studentBearer, "/auth/session", HttpMethod.GET, null)
                                .getStatusCode()
                                .value())
                .isEqualTo(200);
        assertThat(count("SELECT credentials_version FROM users WHERE id=?", f.student))
                .isEqualTo(version);
        String firstTicket = ticket(first), secondTicket = ticket(second);
        assertThat(
                        count(
                                "SELECT count(*) FROM password_reset_tokens WHERE user_id=? AND"
                                        + " token_hash IN (?,?)",
                                f.student,
                                firstTicket,
                                secondTicket))
                .as("only ticket hashes are durable")
                .isZero();
        assertThat(
                        rest.postForEntity(
                                        "/auth/password-reset/complete",
                                        new PasswordResetCompleteRequest(
                                                firstTicket, "Chosen-reset-2026!"),
                                        String.class)
                                .getStatusCode()
                                .is4xxClientError())
                .isTrue();
        ResponseEntity<String> completed =
                rest.postForEntity(
                        "/auth/password-reset/complete",
                        new PasswordResetCompleteRequest(secondTicket, "Chosen-reset-2026!"),
                        String.class);
        assertThat(completed.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(
                        rest.postForEntity(
                                        "/auth/password-reset/complete",
                                        new PasswordResetCompleteRequest(
                                                secondTicket, "Another-reset-2026!"),
                                        String.class)
                                .getStatusCode()
                                .is4xxClientError())
                .isTrue();
        assertThat(
                        exchange(studentBearer, "/auth/session", HttpMethod.GET, null)
                                .getStatusCode()
                                .value())
                .isEqualTo(401);
        assertThat(matches(f.student, "Chosen-reset-2026!")).isTrue();
        JsonNode changed =
                body(call(actor, f.student, "/initial-password", HttpMethod.GET, null), 200);
        assertThat(changed.path("state").asText()).isEqualTo("STUDENT_CHANGED");
    }

    @Test
    void foreignHelperActiveAdminAndTerminalTargetsCannotReadOrMutate() throws Exception {
        Fixture f = fixture();
        String headman = bearer(f.headman, AuthRole.HEADMAN);
        String student = bearer(f.student, AuthRole.STUDENT);
        long foreignGroup = group();
        long foreign = user(foreignGroup, false, f.initial);
        assertForbidden(headman, foreign);
        assertForbidden(student, f.student);
        jdbc.update(
                "INSERT INTO user_role_grants(user_id,role,status) VALUES (?,'admin','active')",
                f.student);
        assertForbidden(headman, f.student);
        jdbc.update(
                "UPDATE user_role_grants SET status='suspended' WHERE user_id=? AND role='admin'",
                f.student);
        jdbc.update(
                "UPDATE user_role_grants SET status='expelled' WHERE user_id=? AND role='student'",
                f.student);
        assertForbidden(headman, f.student);
        assertThat(
                        count(
                                "SELECT count(*) FROM audit_log WHERE target_id=? AND action IN"
                                        + " ('HEADMAN_PASSWORD_RESET','HEADMAN_RESET_LINK_ISSUED')",
                                f.student))
                .isZero();
        assertThat(matches(f.student, f.initial)).isTrue();
    }

    @Test
    void outboxFailureRollsBackCredentialGenerationSessionsTicketsAndAudit() throws Exception {
        Fixture f = fixture();
        String actor = bearer(f.headman, AuthRole.HEADMAN);
        String student = bearer(f.student, AuthRole.STUDENT);
        JsonNode current =
                body(call(actor, f.student, "/initial-password", HttpMethod.GET, null), 200);
        body(call(actor, f.student, "/password-reset-link", HttpMethod.POST, null), 200);
        long events =
                count("SELECT count(*) FROM account_security_events WHERE user_id=?", f.student);
        doThrow(
                        new org.springframework.dao.DataAccessResourceFailureException(
                                "synthetic credential outbox failure"))
                .when(outbox)
                .append(eq(f.student), any());
        assertThat(
                        call(
                                        actor,
                                        f.student,
                                        "/generated-password",
                                        HttpMethod.POST,
                                        Map.of(
                                                "expectedCredentialsVersion",
                                                current.path("credentialsVersion").asText()))
                                .getStatusCode()
                                .value())
                .isEqualTo(503);
        assertThat(matches(f.student, f.initial)).isTrue();
        assertThat(count("SELECT credentials_version FROM users WHERE id=?", f.student))
                .isEqualTo(Long.parseLong(current.path("credentialsVersion").asText()));
        assertThat(count("SELECT count(*) FROM account_security_events WHERE user_id=?", f.student))
                .isEqualTo(events);
        assertThat(
                        count(
                                "SELECT count(*) FROM password_reset_tokens WHERE user_id=? AND"
                                        + " used_at IS NULL",
                                f.student))
                .isEqualTo(1);
        assertThat(
                        count(
                                "SELECT count(*) FROM audit_log WHERE target_id=? AND"
                                        + " action='HEADMAN_PASSWORD_RESET'",
                                f.student))
                .isZero();
        assertThat(exchange(student, "/auth/session", HttpMethod.GET, null).getStatusCode().value())
                .isEqualTo(200);
    }

    @Test
    void unknownOriginIsUnavailableAndGeneratedViewIsNeverCachedAfterActorRevocation()
            throws Exception {
        Fixture f = fixture();
        jdbc.update("UPDATE users SET generated_password_origin=NULL WHERE id=?", f.student);
        String actor = bearer(f.headman, AuthRole.HEADMAN);
        JsonNode unknown =
                body(call(actor, f.student, "/initial-password", HttpMethod.GET, null), 200);
        assertThat(unknown.path("state").asText()).isEqualTo("UNAVAILABLE");
        assertThat(unknown.path("password").isNull() && unknown.path("origin").isNull()).isTrue();
        assertThat(
                        exchange(actor, "/auth/logout-all", HttpMethod.POST, null)
                                .getStatusCode()
                                .is2xxSuccessful())
                .isTrue();
        ResponseEntity<String> revoked =
                call(actor, f.student, "/initial-password", HttpMethod.GET, null);
        assertThat(revoked.getStatusCode().value()).isEqualTo(401);
        assertThat(revoked.getHeaders().getCacheControl()).isEqualTo("no-store");
        assertThat(revoked.getBody() == null || !revoked.getBody().contains(f.initial)).isTrue();
    }

    @Test
    void v59BackfillsOnlyConsistentGeneratedRowsAndPreservesExactV57GenerationSemantics() {
        String schema = "headman_credential_" + UUID.randomUUID().toString().replace("-", "");
        jdbc.execute("CREATE SCHEMA " + schema);
        String url = POSTGRES.getJdbcUrl();
        var dataSource =
                new org.springframework.jdbc.datasource.DriverManagerDataSource(
                        url + (url.contains("?") ? "&" : "?") + "currentSchema=" + schema,
                        POSTGRES.getUsername(),
                        POSTGRES.getPassword());
        var isolated = new JdbcTemplate(dataSource);
        java.nio.file.Path root =
                java.nio.file.Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (root != null
                && !java.nio.file.Files.isDirectory(
                        root.resolve(
                                "services/academic-service/academic-app/src/main/resources/db/migration")))
            root = root.getParent();
        if (root == null)
            throw new IllegalStateException("canonical academic migrations unavailable");
        String location =
                "filesystem:"
                        + root.resolve(
                                "services/academic-service/academic-app/src/main/resources/db/migration");
        org.flywaydb.core.Flyway.configure()
                .dataSource(dataSource)
                .schemas(schema)
                .locations(location)
                .target("58")
                .load()
                .migrate();
        isolated.update(
                """
INSERT INTO users(login,last_name,first_name,password_hash,initial_password,password_changed)
VALUES ('consistent','Synthetic','Legacy',?,'synthetic-initial',FALSE),
       ('changed','Synthetic','Legacy',?,NULL,TRUE),
       ('unknown','Synthetic','Legacy',NULL,'synthetic-initial',FALSE)
""",
                encoder.encode("synthetic-initial"),
                encoder.encode("synthetic-chosen"));
        var before =
                isolated.queryForList(
                        "SELECT installed_rank,version,checksum FROM flyway_schema_history ORDER BY"
                                + " installed_rank");
        var flyway =
                org.flywaydb.core.Flyway.configure()
                        .dataSource(dataSource)
                        .schemas(schema)
                        .locations(location)
                        .load();
        assertThat(flyway.migrate().migrationsExecuted).isEqualTo(1);
        flyway.validate();
        assertThat(
                        isolated.queryForObject(
                                "SELECT generated_password_origin='initial' AND"
                                    + " credentials_version=1 FROM users WHERE login='consistent'",
                                Boolean.class))
                .isTrue();
        assertThat(
                        isolated.queryForObject(
                                "SELECT count(*) FROM users WHERE login IN ('changed','unknown')"
                                        + " AND generated_password_origin IS NULL AND"
                                        + " credentials_version=1",
                                Long.class))
                .isEqualTo(2);
        isolated.update(
                "UPDATE users SET"
                    + " password_hash=?,initial_password='synthetic-reset',generated_password_origin='headman_reset'"
                    + " WHERE login='consistent'",
                encoder.encode("synthetic-reset"));
        assertThat(
                        isolated.queryForObject(
                                "SELECT credentials_version=2 AND"
                                    + " generated_password_origin='headman_reset' FROM users WHERE"
                                    + " login='consistent'",
                                Boolean.class))
                .isTrue();
        isolated.update(
                "UPDATE users SET password_hash=?,initial_password=NULL,password_changed=TRUE WHERE"
                        + " login='consistent'",
                encoder.encode("synthetic-user-chosen"));
        assertThat(
                        isolated.queryForObject(
                                "SELECT credentials_version=3 AND generated_password_origin IS NULL"
                                        + " FROM users WHERE login='consistent'",
                                Boolean.class))
                .isTrue();
        assertThat(
                        isolated.queryForList(
                                "SELECT installed_rank,version,checksum FROM flyway_schema_history"
                                        + " ORDER BY installed_rank LIMIT ?",
                                before.size()))
                .isEqualTo(before);
        assertThat(flyway.migrate().migrationsExecuted).isZero();
        // The private test schema is retained only until this owned container is torn down.
    }

    @Test
    void invalidVersionCannotEchoUntrustedCredentialMaterial() {
        Fixture f = fixture();
        String actor = bearer(f.headman, AuthRole.HEADMAN);
        String marker = "synthetic-private-rejected-version";
        ResponseEntity<String> invalid =
                call(
                        actor,
                        f.student,
                        "/generated-password",
                        HttpMethod.POST,
                        Map.of("expectedCredentialsVersion", marker));
        assertThat(invalid.getStatusCode().value()).isEqualTo(400);
        assertThat(invalid.getHeaders().getCacheControl()).isEqualTo("no-store");
        assertThat(invalid.getBody() == null || !invalid.getBody().contains(marker)).isTrue();
        assertThat(matches(f.student, f.initial)).isTrue();
    }

    @Test
    void auditInsertFailurePreventsDisclosureAndRollsBackResetAndLinkIssue() throws Throwable {
        Fixture f = fixture();
        String actor = bearer(f.headman, AuthRole.HEADMAN);
        String student = bearer(f.student, AuthRole.STUDENT);
        JsonNode view =
                body(call(actor, f.student, "/initial-password", HttpMethod.GET, null), 200);
        JsonNode priorLink =
                body(call(actor, f.student, "/password-reset-link", HttpMethod.POST, null), 200);
        String priorTicket = ticket(priorLink);
        CredentialState before = credentialState(f.student);
        String suffix = UUID.randomUUID().toString().replace("-", "");
        String function = "headman_audit_fault_" + suffix;
        String trigger = "headman_audit_trigger_" + suffix;
        Throwable primary = null;
        try {
            // Owned identifiers and a numeric own target only; no credential value enters SQL text.
            jdbc.execute(
                    "CREATE FUNCTION "
                            + function
                            + "() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN "
                            + "IF NEW.target_type = 'user' AND NEW.target_id = "
                            + f.student
                            + " AND NEW.action IN"
                            + " ('HEADMAN_PASSWORD_SHOWN','HEADMAN_PASSWORD_RESET','HEADMAN_RESET_LINK_ISSUED')"
                            + " THEN RAISE EXCEPTION 'synthetic credential audit unavailable' USING"
                            + " ERRCODE = '23514'; END IF; RETURN NEW; END; $$");
            jdbc.execute(
                    "CREATE TRIGGER "
                            + trigger
                            + " BEFORE INSERT ON audit_log FOR EACH ROW EXECUTE FUNCTION "
                            + function
                            + "()");
            ResponseEntity<String> shown =
                    call(actor, f.student, "/initial-password", HttpMethod.GET, null);
            assertAuditUnavailable(shown, f.initial, priorTicket);
            assertThat(before.equals(credentialState(f.student)))
                    .as("failed disclosure leaves all own durable state unchanged")
                    .isTrue();
            ResponseEntity<String> reset =
                    call(
                            actor,
                            f.student,
                            "/generated-password",
                            HttpMethod.POST,
                            Map.of(
                                    "expectedCredentialsVersion",
                                    view.path("credentialsVersion").asText()));
            assertAuditUnavailable(reset, f.initial, priorTicket);
            assertThat(before.equals(credentialState(f.student)))
                    .as(
                            "late reset audit failure rolls back credentials sessions tickets"
                                    + " events and outbox")
                    .isTrue();
            ResponseEntity<String> link =
                    call(actor, f.student, "/password-reset-link", HttpMethod.POST, null);
            assertAuditUnavailable(link, f.initial, priorTicket);
            assertThat(before.equals(credentialState(f.student)))
                    .as(
                            "failed link audit keeps the prior ticket and creates no durable"
                                    + " replacement")
                    .isTrue();
            assertThat(
                            exchange(student, "/auth/session", HttpMethod.GET, null)
                                    .getStatusCode()
                                    .value())
                    .isEqualTo(200);
        } catch (Throwable failure) {
            primary = failure;
            throw failure;
        } finally {
            boolean cleanupFailed = false;
            try {
                jdbc.execute("DROP TRIGGER IF EXISTS " + trigger + " ON audit_log");
            } catch (RuntimeException failure) {
                cleanupFailed = true;
            }
            try {
                jdbc.execute("DROP FUNCTION IF EXISTS " + function + "()");
            } catch (RuntimeException failure) {
                cleanupFailed = true;
            }
            if (cleanupFailed) {
                var fixed =
                        new IllegalStateException("owned credential audit fault cleanup failed");
                if (primary != null) primary.addSuppressed(fixed);
                else throw fixed;
            }
        }
        // Genuine public completion proves a failed reissue did not supersede the original usable
        // link.
        assertThat(
                        rest.postForEntity(
                                        "/auth/password-reset/complete",
                                        new PasswordResetCompleteRequest(
                                                priorTicket, "After-audit-fault-2026!"),
                                        String.class)
                                .getStatusCode()
                                .is2xxSuccessful())
                .isTrue();
        assertThat(matches(f.student, "After-audit-fault-2026!")).isTrue();
        assertThat(exchange(student, "/auth/session", HttpMethod.GET, null).getStatusCode().value())
                .isEqualTo(401);
    }

    @Test
    void concurrentHttpResetsWithSameVersionCommitExactlyOneWinner() throws Throwable {
        Fixture f = fixture();
        String firstOld = bearer(f.student, AuthRole.STUDENT);
        String secondOld = bearer(f.student, AuthRole.STUDENT);
        String actor = bearer(f.headman, AuthRole.HEADMAN);
        JsonNode current =
                body(call(actor, f.student, "/initial-password", HttpMethod.GET, null), 200);
        String version = current.path("credentialsVersion").asText();
        var factory = new org.springframework.http.client.SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(2));
        factory.setReadTimeout(Duration.ofSeconds(10));
        var bounded = new org.springframework.web.client.RestTemplate(factory);
        bounded.setErrorHandler(
                new org.springframework.web.client.DefaultResponseErrorHandler() {
                    @Override
                    public boolean hasError(
                            org.springframework.http.client.ClientHttpResponse response) {
                        return false;
                    }
                });
        CountDownLatch ready = new CountDownLatch(2), release = new CountDownLatch(1);
        List<Thread> owned = Collections.synchronizedList(new ArrayList<>());
        var executor =
                Executors.newFixedThreadPool(
                        2,
                        task -> {
                            Thread thread = new Thread(task, "headman-credential-http-race");
                            owned.add(thread);
                            return thread;
                        });
        Callable<ResponseEntity<String>> reset =
                () -> {
                    ready.countDown();
                    if (!release.await(5, TimeUnit.SECONDS))
                        throw new IllegalStateException("owned reset release deadline exceeded");
                    HttpHeaders headers = new HttpHeaders();
                    headers.setBearerAuth(actor);
                    headers.setContentType(MediaType.APPLICATION_JSON);
                    try {
                        return bounded.exchange(
                                rest.getRootUri()
                                        + "/auth/headman/students/"
                                        + f.student
                                        + "/generated-password",
                                HttpMethod.POST,
                                new HttpEntity<>(
                                        Map.of("expectedCredentialsVersion", version), headers),
                                String.class);
                    } catch (org.springframework.web.client.RestClientException failure) {
                        throw new IllegalStateException("owned reset HTTP transport failed");
                    }
                };
        Future<ResponseEntity<String>> first = null, second = null;
        Throwable primary = null;
        try {
            first = executor.submit(reset);
            second = executor.submit(reset);
            assertThat(ready.await(5, TimeUnit.SECONDS))
                    .as("both owned HTTP callers await one release")
                    .isTrue();
            release.countDown();
            ResponseEntity<String> a = first.get(20, TimeUnit.SECONDS),
                    b = second.get(20, TimeUnit.SECONDS);
            assertThat(List.of(a.getStatusCode().value(), b.getStatusCode().value()))
                    .containsExactlyInAnyOrder(200, 409);
            ResponseEntity<String> winner = a.getStatusCode().value() == 200 ? a : b;
            ResponseEntity<String> loser = a.getStatusCode().value() == 409 ? a : b;
            JsonNode result = body(winner, 200);
            String password = result.path("password").asText();
            assertThat(!password.isBlank() && matches(f.student, password))
                    .as("only the returned winner verifies against the durable hash")
                    .isTrue();
            assertThat(
                            "headman_reset".equals(result.path("origin").asText())
                                    && Long.toString(Long.parseLong(version) + 1)
                                            .equals(result.path("credentialsVersion").asText()))
                    .as("winner origin and version match the committed generation")
                    .isTrue();
            assertThat(count("SELECT credentials_version FROM users WHERE id=?", f.student))
                    .isEqualTo(Long.parseLong(version) + 1);
            assertThat(
                            count(
                                    "SELECT count(*) FROM users WHERE id=? AND initial_password=?"
                                            + " AND NOT password_changed AND"
                                            + " generated_password_origin='headman_reset'",
                                    f.student,
                                    password))
                    .isEqualTo(1);
            assertThat(loser.getHeaders().getCacheControl()).isEqualTo("no-store");
            assertThat(loser.getBody() == null || !loser.getBody().contains(password))
                    .as("losing response discloses no winner credential")
                    .isTrue();
            assertThat(
                            count(
                                    "SELECT count(*) FROM audit_log WHERE target_id=? AND"
                                            + " action='HEADMAN_PASSWORD_RESET'",
                                    f.student))
                    .isEqualTo(1);
            assertThat(
                            count(
                                    "SELECT count(*) FROM account_security_events WHERE user_id=?"
                                            + " AND event_type='PASSWORD_CHANGED'",
                                    f.student))
                    .isEqualTo(1);
            assertThat(
                            count(
                                    "SELECT count(*) FROM auth_outbox WHERE"
                                            + " event_type='password.changed' AND"
                                            + " payload->'payload'->>'user_id'=?",
                                    Long.toString(f.student)))
                    .isEqualTo(1);
            assertThat(
                            count(
                                    "SELECT count(*) FROM auth_sessions WHERE user_id=? AND"
                                            + " revoked_at IS NOT NULL AND"
                                            + " revoke_reason='PASSWORD_CHANGED'",
                                    f.student))
                    .isEqualTo(2);
            for (String token : List.of(firstOld, secondOld))
                assertThat(
                                exchange(token, "/auth/session", HttpMethod.GET, null)
                                        .getStatusCode()
                                        .value())
                        .isEqualTo(401);
        } catch (Throwable failure) {
            primary = failure;
            throw failure;
        } finally {
            release.countDown();
            if (first != null) first.cancel(true);
            if (second != null) second.cancel(true);
            executor.shutdownNow();
            boolean reaped = false;
            try {
                reaped =
                        executor.awaitTermination(15, TimeUnit.SECONDS)
                                && owned.stream().noneMatch(Thread::isAlive);
            } catch (InterruptedException failure) {
                Thread.currentThread().interrupt();
            }
            if (!reaped) {
                var fixed =
                        new IllegalStateException(
                                "owned credential HTTP callers did not terminate");
                if (primary != null) primary.addSuppressed(fixed);
                else throw fixed;
            }
        }
    }

    private void assertAuditUnavailable(
            ResponseEntity<String> response, String initial, String priorTicket) throws Exception {
        JsonNode error = body(response, 503);
        assertThat(
                        "AUTHORITY_UNAVAILABLE".equals(error.path("extras").path("code").asText())
                                && "The authentication session request was denied"
                                        .equals(error.path("detail").asText()))
                .as("audit failure returns the fixed generic authority problem")
                .isTrue();
        assertThat(
                        !error.has("password")
                                && !error.has("url")
                                && !response.getBody().contains(initial)
                                && !response.getBody().contains(priorTicket)
                                && !response.getBody()
                                        .contains("synthetic credential audit unavailable"))
                .as("audit refusal returns only a fixed generic problem and no credential material")
                .isTrue();
    }

    private CredentialState credentialState(long userId) {
        return new CredentialState(
                jdbc.queryForMap(
                        "SELECT"
                            + " password_hash,initial_password,password_changed,generated_password_origin,credentials_version,telegram_binding_version"
                            + " FROM users WHERE id=?",
                        userId),
                jdbc.queryForList(
                        "SELECT"
                            + " sid,session_version,current_refresh_jti,previous_refresh_jti,refresh_expires_at,revoked_at,revoke_reason"
                            + " FROM auth_sessions WHERE user_id=? ORDER BY sid",
                        userId),
                jdbc.queryForList(
                        "SELECT id,token_hash,expires_at,used_at FROM password_reset_tokens WHERE"
                                + " user_id=? ORDER BY id",
                        userId),
                count("SELECT count(*) FROM account_security_events WHERE user_id=?", userId),
                count(
                        "SELECT count(*) FROM auth_outbox WHERE payload->'payload'->>'user_id'=?",
                        Long.toString(userId)),
                count(
                        "SELECT count(*) FROM audit_log WHERE target_type='user' AND target_id=?",
                        userId));
    }

    private record CredentialState(
            Map<String, Object> credentials,
            List<Map<String, Object>> sessions,
            List<Map<String, Object>> tickets,
            long eventCount,
            long outboxCount,
            long auditCount) {
        @Override
        public String toString() {
            return "CredentialState[<redacted>]";
        }
    }

    private void assertForbidden(String token, long target) {
        assertThat(
                        call(token, target, "/initial-password", HttpMethod.GET, null)
                                .getStatusCode()
                                .value())
                .isEqualTo(403);
        assertThat(
                        call(
                                        token,
                                        target,
                                        "/generated-password",
                                        HttpMethod.POST,
                                        Map.of("expectedCredentialsVersion", "1"))
                                .getStatusCode()
                                .value())
                .isEqualTo(403);
        assertThat(
                        call(token, target, "/password-reset-link", HttpMethod.POST, null)
                                .getStatusCode()
                                .value())
                .isEqualTo(403);
    }

    private Fixture fixture() {
        long group = group();
        String initial = "Initial-generated-26!";
        long headman = user(group, true, initial), student = user(group, false, initial);
        jdbc.update("UPDATE users SET telegram_id=? WHERE id=?", 800000000000L + student, student);
        return new Fixture(headman, student, initial);
    }

    private long group() {
        return jdbc.queryForObject(
                "INSERT INTO groups(name) VALUES (?) RETURNING id",
                Long.class,
                "credential-it-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16));
    }

    private long user(long group, boolean headman, String initial) {
        long id =
                jdbc.queryForObject(
                        """
INSERT INTO users(login,password_hash,last_name,first_name,group_id,is_headman,initial_password)
VALUES (?,?,'Credential','Synthetic',?,?,?) RETURNING id
""",
                        Long.class,
                        "hc-" + UUID.randomUUID().toString().substring(0, 20),
                        encoder.encode(initial),
                        group,
                        headman,
                        initial);
        jdbc.update(
                "INSERT INTO user_role_grants(user_id,role,status,group_id) VALUES"
                        + " (?,'student','active',?)",
                id,
                group);
        if (headman)
            jdbc.update(
                    "INSERT INTO user_role_grants(user_id,role,status,group_id) VALUES"
                            + " (?,'headman','active',?)",
                    id,
                    group);
        return id;
    }

    private String bearer(long userId, AuthRole role) {
        var authority = queries.findLoginAuthority(userId).orElseThrow();
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        UUID sid = UUID.randomUUID();
        var created =
                sessions.createSession(
                        new SessionLifecycleService.CreateSessionRequest(
                                userId,
                                sid,
                                UUID.randomUUID(),
                                now,
                                now.plusSeconds(3600),
                                AuthMethod.PASSWORD,
                                authority.rolesVersion(),
                                authority.grants(),
                                null,
                                "credential-it",
                                null));
        assertThat(created.succeeded()).isTrue();
        var snapshot = created.snapshot();
        if (snapshot.activeRole() == null || snapshot.activeRole().role() != role) {
            var selected =
                    sessions.selectRole(
                            new SessionLifecycleService.SelectRoleRequest(
                                    userId,
                                    sid,
                                    role,
                                    snapshot.sessionVersion(),
                                    now,
                                    AuthMethod.PASSWORD,
                                    "credential-it",
                                    null));
            assertThat(selected.succeeded()).isTrue();
            snapshot = selected.snapshot();
        }
        return jwt.generateSessionAccessToken(snapshot, now, now.plusSeconds(900));
    }

    private ResponseEntity<String> call(
            String token, long target, String tail, HttpMethod method, Object body) {
        return exchange(token, "/auth/headman/students/" + target + tail, method, body);
    }

    private ResponseEntity<String> exchange(
            String token, String path, HttpMethod method, Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return rest.exchange(path, method, new HttpEntity<>(body, headers), String.class);
    }

    private JsonNode body(ResponseEntity<String> response, int status) throws Exception {
        assertThat(response.getStatusCode().value()).isEqualTo(status);
        assertThat(response.getHeaders().getCacheControl()).isEqualTo("no-store");
        return json.readTree(response.getBody());
    }

    private boolean matches(long id, String value) {
        return encoder.matches(
                value,
                jdbc.queryForObject(
                        "SELECT password_hash FROM users WHERE id=?", String.class, id));
    }

    private long count(String sql, Object... args) {
        return jdbc.queryForObject(sql, Long.class, args);
    }

    private String ticket(JsonNode response) {
        try {
            return new java.net.URI(response.path("url").asText())
                    .getFragment()
                    .split("&", 2)[0]
                    .substring("resetTicket=".length());
        } catch (Exception exception) {
            throw new IllegalStateException("invalid synthetic recovery link");
        }
    }

    private record Fixture(long headman, long student, String initial) {
        @Override
        public String toString() {
            return "Fixture[<redacted>]";
        }
    }
}
