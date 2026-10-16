package ru.rutcampustrack.auth.integration;

import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import ru.rutcampustrack.auth.config.InternalIssuerProperties;
import ru.rutcampustrack.auth.dto.SubjectDeletionProofModels.*;
import ru.rutcampustrack.auth.service.JwtService;
import ru.rutcampustrack.auth.session.SessionLifecycleService;
import ru.rutcampustrack.auth.session.model.*;
import ru.rutcampustrack.auth.session.port.AuthSessionQueryPort;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.*;

/** Real private HTTP + PG. Codes/passwords/tokens are compared only as private booleans. */
class SubjectDeletionProofHttpIT extends AbstractIntegrationTest {
    @Autowired TestRestTemplate rest;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @Autowired PasswordEncoder passwords;
    @Autowired SessionLifecycleService sessions;
    @Autowired AuthSessionQueryPort queries;
    @Autowired JwtService jwt;
    @Autowired InternalIssuerProperties issuer;

    @Test
    void passwordAndExactPurposeScopedProofProduceOneDurableReplayableReceipt() throws Exception {
        var f = fixture();
        var b = binding(f);
        String token = token(f);
        var challenge = call("challenge", new ChallengeRequest(token, f.password(), b));
        assertPrivate(challenge, 200);
        JsonNode handle = json.readTree(challenge.getBody());
        String id = handle.path("challengeId").asText();
        String code = ownCode(f.telegram(), id);
        assertThat(id.matches("[A-Za-z0-9_-]{32}")).isTrue();
        assertThat(
                        !String.valueOf(challenge.getBody()).contains(code)
                                && !String.valueOf(challenge.getBody()).contains(f.password()))
                .isTrue();
        assertThat(
                        count(
                                "SELECT count(*) FROM subject_deletion_proofs WHERE actor_id=? AND"
                                        + " consumed_at IS NULL",
                                f.user()))
                .isEqualTo(1);
        var changed =
                new Binding(
                        f.subject(), f.semester(), f.group(), UUID.randomUUID(), b.previewDigest());
        assertPrivate(call("verify", new VerifyRequest(token, changed, id, code)), 409);
        var changedDigest =
                new Binding(f.subject(), f.semester(), f.group(), b.operationId(), "c".repeat(64));
        assertPrivate(call("verify", new VerifyRequest(token, changedDigest, id, code)), 409);

        String wrong = code.equals("000000") ? "000001" : "000000";
        assertPrivate(call("verify", new VerifyRequest(token, b, id, wrong)), 409);
        assertThat(
                        count(
                                "SELECT sum(attempts) FROM subject_deletion_proofs WHERE"
                                        + " actor_id=?",
                                f.user()))
                .isEqualTo(1);
        var verified = call("verify", new VerifyRequest(token, b, id, code));
        assertPrivate(verified, 200);
        JsonNode receipt = json.readTree(verified.getBody());
        assertThat(receipt.path("accepted").asBoolean()).isTrue();
        String receiptId = receipt.path("receiptId").asText();
        assertThat(!receiptId.isBlank()).isTrue();
        var replay = call("verify", new VerifyRequest(token, b, id, code));
        assertPrivate(replay, 200);
        assertThat(json.readTree(replay.getBody()).path("receiptId").asText().equals(receiptId))
                .isTrue();
        assertThat(
                        count(
                                "SELECT count(*) FROM subject_deletion_proofs WHERE actor_id=? AND"
                                        + " receipt_id IS NOT NULL",
                                f.user()))
                .isEqualTo(1);
        assertThat(ownEvents(f.telegram(), "otp.requested")).isEqualTo(1);
        assertThat(ownEvents(f.telegram(), "otp.verified")).isEqualTo(1);
        assertThat(
                        count(
                                "SELECT count(*) FROM auth_outbox WHERE event_type='otp.verified'"
                                        + " AND payload->'payload'->>'purpose'='subject_delete' AND"
                                        + " payload->'payload'->>'challenge_id'=?",
                                id))
                .isEqualTo(1);
    }

    @Test
    void wrongPasswordForeignScopeBindingAndRevocationCannotAuthorizeDeletion() throws Exception {
        var f = fixture();
        String token = token(f);
        var b = binding(f);
        assertPrivate(
                call("challenge", new ChallengeRequest(token, "different-synthetic-password", b)),
                403);
        var other = fixture();
        assertPrivate(
                call("challenge", new ChallengeRequest(token, f.password(), binding(other))), 403);
        assertThat(ownEvents(f.telegram(), "otp.requested")).isZero();
        var challenge = call("challenge", new ChallengeRequest(token, f.password(), b));
        assertPrivate(challenge, 200);
        String id = json.readTree(challenge.getBody()).path("challengeId").asText();
        String code = ownCode(f.telegram(), id);
        jdbc.update("UPDATE users SET telegram_id=telegram_id+1 WHERE id=?", f.user());
        assertPrivate(call("verify", new VerifyRequest(token, b, id, code)), 409);
        assertThat(
                        count(
                                "SELECT count(*) FROM subject_deletion_proofs WHERE actor_id=? AND"
                                        + " consumed_at IS NOT NULL",
                                f.user()))
                .isZero();
        jdbc.update(
                "UPDATE user_role_grants SET status='suspended',updated_at=CURRENT_TIMESTAMP WHERE"
                        + " user_id=? AND role='headman'",
                f.user());
        int denied = call("verify", new VerifyRequest(token, b, id, code)).getStatusCode().value();
        assertThat(denied == 401 || denied == 403 || denied == 409).isTrue();
        assertThat(ownEvents(f.telegram(), "otp.verified")).isZero();
    }

    @Test
    void expiryAndDurableOutboxFailureLeaveNoUsablePartialProof() throws Exception {
        var f = fixture();
        String token = token(f);
        var b = binding(f);
        refuseOwnOutbox(
                f.telegram(),
                () -> {
                    var denied = call("challenge", new ChallengeRequest(token, f.password(), b));
                    assertPrivate(denied, 503);
                    assertThat(
                                    count(
                                            "SELECT count(*) FROM subject_deletion_proofs WHERE"
                                                    + " actor_id=?",
                                            f.user()))
                            .isZero();
                });
        var challenge = call("challenge", new ChallengeRequest(token, f.password(), b));
        assertPrivate(challenge, 200);
        String id = json.readTree(challenge.getBody()).path("challengeId").asText();
        String code = ownCode(f.telegram(), id);
        refuseOwnOutbox(
                f.telegram(),
                () -> {
                    assertPrivate(call("verify", new VerifyRequest(token, b, id, code)), 503);
                    assertThat(
                                    count(
                                            "SELECT count(*) FROM subject_deletion_proofs WHERE"
                                                    + " actor_id=? AND consumed_at IS NOT NULL",
                                            f.user()))
                            .isZero();
                });
        jdbc.update(
                "UPDATE subject_deletion_proofs SET created_at=CURRENT_TIMESTAMP-INTERVAL '2"
                        + " hours',expires_at=CURRENT_TIMESTAMP-INTERVAL '1 hour' WHERE actor_id=?",
                f.user());
        assertPrivate(call("verify", new VerifyRequest(token, b, id, code)), 409);
        assertThat(ownEvents(f.telegram(), "otp.verified")).isZero();
    }

    @Test
    void concurrentExactCodeConsumptionHasOneReceiptAndOneVerifiedOutboxEvent() throws Exception {
        var f = fixture();
        String token = token(f);
        var b = binding(f);
        var response = call("challenge", new ChallengeRequest(token, f.password(), b));
        assertPrivate(response, 200);
        String id = json.readTree(response.getBody()).path("challengeId").asText();
        String code = ownCode(f.telegram(), id);
        var pool = Executors.newFixedThreadPool(2);
        var ready = new CountDownLatch(2);
        var release = new CountDownLatch(1);
        var futures = new ArrayList<Future<ResponseEntity<String>>>();
        try {
            for (int i = 0; i < 2; i++)
                futures.add(
                        pool.submit(
                                () -> {
                                    ready.countDown();
                                    if (!release.await(5, TimeUnit.SECONDS))
                                        throw new IllegalStateException(
                                                "Proof race release missing");
                                    return call("verify", new VerifyRequest(token, b, id, code));
                                }));
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            release.countDown();
            var first = futures.get(0).get(10, TimeUnit.SECONDS);
            var second = futures.get(1).get(10, TimeUnit.SECONDS);
            assertPrivate(first, 200);
            assertPrivate(second, 200);
            assertThat(
                            json.readTree(first.getBody())
                                    .path("receiptId")
                                    .asText()
                                    .equals(
                                            json.readTree(second.getBody())
                                                    .path("receiptId")
                                                    .asText()))
                    .isTrue();
            assertThat(ownEvents(f.telegram(), "otp.verified")).isEqualTo(1);
        } finally {
            release.countDown();
            for (var future : futures) if (!future.isDone()) future.cancel(true);
            pool.shutdownNow();
            assertThat(pool.awaitTermination(10, TimeUnit.SECONDS))
                    .as("owned proof requests reaped")
                    .isTrue();
        }
    }

    private void refuseOwnOutbox(long telegram, Runnable action) {
        String constraint = "subject_proof_" + UUID.randomUUID().toString().replace("-", "");
        jdbc.execute(
                "ALTER TABLE auth_outbox ADD CONSTRAINT "
                        + constraint
                        + " CHECK ((payload->'payload'->>'telegram_id') IS DISTINCT FROM '"
                        + telegram
                        + "') NOT VALID");
        Throwable primary = null;
        try {
            action.run();
        } catch (RuntimeException | Error failure) {
            primary = failure;
            throw failure;
        } finally {
            try {
                jdbc.execute("ALTER TABLE auth_outbox DROP CONSTRAINT " + constraint);
            } catch (RuntimeException failure) {
                var fixed = new IllegalStateException("Owned proof outbox control cleanup failed");
                if (primary != null) primary.addSuppressed(fixed);
                else throw fixed;
            }
        }
    }

    private ResponseEntity<String> call(String tail, Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Internal-Issuer-Secret", issuer.getSecret());
        return rest.exchange(
                "/internal/auth/subject-deletion/" + tail,
                HttpMethod.POST,
                new HttpEntity<>(body, headers),
                String.class);
    }

    private void assertPrivate(ResponseEntity<String> response, int expected) {
        assertThat(response.getStatusCode().value()).isEqualTo(expected);
        assertThat(response.getHeaders().getCacheControl()).isEqualTo("no-store");
    }

    private long ownEvents(long telegram, String type) {
        return count(
                "SELECT count(*) FROM auth_outbox WHERE event_type=? AND"
                        + " payload->'payload'->>'telegram_id'=?",
                type,
                Long.toString(telegram));
    }

    private String ownCode(long telegram, String challenge) {
        return jdbc.queryForObject(
                "SELECT payload->'payload'->>'code' FROM auth_outbox WHERE"
                        + " event_type='otp.requested' AND"
                        + " payload->'payload'->>'purpose'='subject_delete' AND"
                        + " payload->'payload'->>'telegram_id'=? AND"
                        + " payload->'payload'->>'challenge_id'=?",
                String.class,
                Long.toString(telegram),
                challenge);
    }

    private long count(String sql, Object... args) {
        return jdbc.queryForObject(sql, Long.class, args);
    }

    private Binding binding(Fixture f) {
        return new Binding(f.subject(), f.semester(), f.group(), UUID.randomUUID(), "a".repeat(64));
    }

    private Fixture fixture() {
        long group =
                jdbc.queryForObject(
                        "INSERT INTO groups(name) VALUES(?) RETURNING id",
                        Long.class,
                        "sd-" + UUID.randomUUID().toString().substring(0, 20));
        long semester = jdbc.queryForObject("SELECT id FROM semesters WHERE is_active", Long.class);
        String password = "Subject-confirm-synthetic-26!";
        long user =
                jdbc.queryForObject(
                        "INSERT INTO"
                            + " users(login,password_hash,last_name,first_name,group_id,is_headman)"
                            + " VALUES(?,?,'Synthetic','Proof',?,TRUE) RETURNING id",
                        Long.class,
                        "sd-" + UUID.randomUUID().toString().substring(0, 20),
                        passwords.encode(password),
                        group);
        long telegram = 700000000000L + user;
        jdbc.update("UPDATE users SET telegram_id=? WHERE id=?", telegram, user);
        for (String role : List.of("student", "headman"))
            jdbc.update(
                    "INSERT INTO"
                        + " user_role_grants(user_id,role,status,group_id,created_at,updated_at)"
                        + " VALUES(?,?,'active',?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",
                    user,
                    role,
                    group);
        long subject =
                jdbc.queryForObject(
                        "INSERT INTO subjects(name,type,group_id,semester_id)"
                                + " VALUES(?,'lecture',?,?) RETURNING id",
                        Long.class,
                        "Own deletion subject",
                        group,
                        semester);
        return new Fixture(user, group, semester, subject, telegram, password);
    }

    private String token(Fixture f) {
        var authority = queries.findLoginAuthority(f.user()).orElseThrow();
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        UUID sid = UUID.randomUUID();
        var created =
                sessions.createSession(
                        new SessionLifecycleService.CreateSessionRequest(
                                f.user(),
                                sid,
                                UUID.randomUUID(),
                                now,
                                now.plusSeconds(3600),
                                AuthMethod.PASSWORD,
                                authority.rolesVersion(),
                                authority.grants(),
                                jdbc.queryForObject(
                                        "SELECT password_hash FROM users WHERE id=?",
                                        String.class,
                                        f.user()),
                                "subject-proof-it",
                                null));
        assertThat(created.succeeded()).isTrue();
        var snapshot = created.snapshot();
        if (snapshot.activeRole() == null || snapshot.activeRole().role() != AuthRole.HEADMAN) {
            var selected =
                    sessions.selectRole(
                            new SessionLifecycleService.SelectRoleRequest(
                                    f.user(),
                                    sid,
                                    AuthRole.HEADMAN,
                                    snapshot.sessionVersion(),
                                    now,
                                    AuthMethod.PASSWORD,
                                    "subject-proof-it",
                                    null));
            assertThat(selected.succeeded()).isTrue();
            snapshot = selected.snapshot();
        }
        return jwt.generateInternalToken(snapshot, now, now.plusSeconds(120));
    }

    private record Fixture(
            long user, long group, long semester, long subject, long telegram, String password) {
        public String toString() {
            return "SubjectProofFixture[redacted]";
        }
    }
}
