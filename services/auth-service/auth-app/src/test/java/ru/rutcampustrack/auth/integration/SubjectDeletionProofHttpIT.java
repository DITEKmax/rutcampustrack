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
    @Autowired ru.rutcampustrack.auth.service.SubjectDeletionProofService proofs;
    @Autowired InternalIssuerProperties issuer;
    @Autowired org.springframework.data.redis.core.StringRedisTemplate redis;

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
    void storedConsumedProofContinuesWithoutBearerButCannotChangeItsBoundIntent() throws Exception {
        var f = fixture();
        var b = binding(f);
        var receipt = acceptedReceipt(f, b);
        var current = proofs.storedReceipt(b, receipt);
        assertThat(current.getActor().getUserId()).isEqualTo(f.user());
        assertThat(current.getActor().getRole()).isEqualTo("HEADMAN");
        assertThat(current.getBinding().getOperationId()).isEqualTo(b.operationId().toString());
        var changed =
                new Binding(
                        b.subjectId(),
                        b.semesterId(),
                        b.groupId(),
                        UUID.randomUUID(),
                        b.previewDigest());
        assertThatThrownBy(() -> proofs.storedReceipt(changed, receipt))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        assertThat(proofs.storedReceipt(b, receipt)).isEqualTo(current);
    }

    @Test
    void storedReceiptRejectsCredentialBindingRoleSessionGenerationChangesAndExpiry()
            throws Exception {
        for (String change :
                List.of("CREDENTIAL", "BINDING", "ROLE", "SESSION", "ROLES_VERSION", "EXPIRY")) {
            var f = fixture();
            var b = binding(f);
            var receipt = acceptedReceipt(f, b);
            var before = proofs.storedReceipt(b, receipt);
            switch (change) {
                case "CREDENTIAL" ->
                        jdbc.update(
                                "UPDATE auth_identities SET"
                                        + " password_changed=true,initial_password=NULL WHERE id=?",
                                f.user());
                case "BINDING" -> {
                    long reserved =
                            jdbc.queryForObject(
                                    "SELECT"
                                        + " nextval(pg_get_serial_sequence('auth_identities','id'))",
                                    Long.class);
                    jdbc.update(
                            "UPDATE auth_identities SET telegram_id=? WHERE id=?",
                            f.telegram() - f.user() + reserved,
                            f.user());
                }
                case "ROLE" ->
                        jdbc.update(
                                "UPDATE user_role_grants SET status='suspended' WHERE user_id=? AND"
                                        + " role='headman'",
                                f.user());
                case "SESSION" ->
                        jdbc.update(
                                "UPDATE auth_sessions SET"
                                    + " revoked_at=now(),revoke_reason='SECURITY_REVOKED',session_version=session_version+1"
                                    + " WHERE sid=?",
                                UUID.fromString(before.getActor().getSessionId()));
                case "ROLES_VERSION" -> {
                    var owner = authFixtureOwnerJdbc();
                    new org.springframework.transaction.support.TransactionTemplate(
                                    new org.springframework.jdbc.datasource
                                            .DataSourceTransactionManager(owner.getDataSource()))
                            .execute(
                                    status -> {
                                        UUID op =
                                                ru.rutcampustrack.auth.support.AuthDatabaseFixture
                                                        .fence(owner, f.user());
                                        ru.rutcampustrack.auth.support.AuthDatabaseFixture.unfence(
                                                owner, op, f.user());
                                        return null;
                                    });
                }
                case "EXPIRY" ->
                        jdbc.update(
                                "UPDATE subject_deletion_proofs SET created_at=now()-interval '2"
                                    + " days',expires_at=now()-interval '1 day' WHERE receipt_id=?",
                                receipt);
            }
            assertThatThrownBy(() -> proofs.storedReceipt(b, receipt))
                    .as(change)
                    .isInstanceOfAny(
                            org.springframework.web.server.ResponseStatusException.class,
                            ru.rutcampustrack.auth.session.AuthSessionException.class);
        }
    }

    private UUID acceptedReceipt(Fixture f, Binding b) throws Exception {
        String token = token(f);
        var challenge = call("challenge", new ChallengeRequest(token, f.password(), b));
        assertPrivate(challenge, 200);
        String challengeId = json.readTree(challenge.getBody()).path("challengeId").asText();
        var verified =
                call(
                        "verify",
                        new VerifyRequest(
                                token, b, challengeId, ownCode(f.telegram(), challengeId)));
        assertPrivate(verified, 200);
        return UUID.fromString(json.readTree(verified.getBody()).path("receiptId").asText());
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
        // Reserve a user identity gap so future formula fixtures cannot reuse this Telegram id.
        long reservedUserId =
                jdbc.queryForObject(
                        "SELECT nextval(pg_get_serial_sequence('auth_identities','id'))",
                        Long.class);
        long replacementTelegram = f.telegram() - f.user() + reservedUserId;
        assertThat(replacementTelegram > 0 && replacementTelegram != f.telegram()).isTrue();
        assertThat(
                        count(
                                "SELECT count(*) FROM auth_identities WHERE telegram_id=?",
                                replacementTelegram))
                .isZero();
        assertThat(
                        jdbc.update(
                                "UPDATE auth_identities SET telegram_id=? WHERE id=?",
                                replacementTelegram,
                                f.user()))
                .isEqualTo(1);
        assertPrivate(call("verify", new VerifyRequest(token, b, id, code)), 409);
        assertThat(
                        count(
                                "SELECT count(*) FROM subject_deletion_proofs WHERE actor_id=? AND"
                                        + " consumed_at IS NOT NULL",
                                f.user()))
                .isZero();
        var roleOnly = fixture();
        String roleToken = token(roleOnly);
        var roleBinding = binding(roleOnly);
        var roleChallenge =
                call(
                        "challenge",
                        new ChallengeRequest(roleToken, roleOnly.password(), roleBinding));
        assertPrivate(roleChallenge, 200);
        String roleId = json.readTree(roleChallenge.getBody()).path("challengeId").asText();
        String roleCode = ownCode(roleOnly.telegram(), roleId);
        long sessionRows =
                count("SELECT count(*) FROM auth_sessions WHERE user_id=?", roleOnly.user());
        // Fresh actor/proof: Telegram binding has not been changed for this independent denial.
        jdbc.update(
                "UPDATE user_role_grants SET status='suspended',updated_at=CURRENT_TIMESTAMP WHERE"
                    + " user_id=? AND role='headman'",
                roleOnly.user());
        assertPrivate(
                call("verify", new VerifyRequest(roleToken, roleBinding, roleId, roleCode)), 409);
        assertThat(
                        count(
                                "SELECT count(*) FROM subject_deletion_proofs WHERE actor_id=? AND"
                                    + " (receipt_id IS NOT NULL OR consumed_at IS NOT NULL)",
                                roleOnly.user()))
                .isZero();
        assertThat(count("SELECT count(*) FROM auth_sessions WHERE user_id=?", roleOnly.user()))
                .isEqualTo(sessionRows);
        assertThat(ownEvents(roleOnly.telegram(), "otp.verified")).isZero();
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
        authFixtureOwnerJdbc()
                .execute(
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
                authFixtureOwnerJdbc()
                        .execute("ALTER TABLE auth_outbox DROP CONSTRAINT " + constraint);
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
        assertThat(response.getHeaders().getValuesAsList(HttpHeaders.CACHE_CONTROL))
                .containsOnly("no-store");
        assertThat(response.getHeaders().get(HttpHeaders.CACHE_CONTROL))
                .isNotEmpty()
                .allSatisfy(value -> assertThat(value).doesNotContain("\""));
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

    private static final java.util.concurrent.atomic.AtomicLong DOMAIN_IDS =
            new java.util.concurrent.atomic.AtomicLong(2_000_000);

    private Fixture fixture() {
        // Auth binds the fixed security purpose to opaque Academic IDs. It owns no domain rows.
        long group = DOMAIN_IDS.incrementAndGet(),
                semester = DOMAIN_IDS.incrementAndGet(),
                subject = DOMAIN_IDS.incrementAndGet();
        String password = "Subject-confirm-synthetic-26!";
        long user =
                ru.rutcampustrack.auth.support.AuthDatabaseFixture.identity(
                        jdbc,
                        "sd-" + UUID.randomUUID().toString().substring(0, 20),
                        Instant.now(),
                        passwords.encode(password),
                        password);
        long telegram = 700000000000L + user;
        jdbc.update("UPDATE auth_identities SET telegram_id=? WHERE id=?", telegram, user);
        for (String role : List.of("student", "headman"))
            jdbc.update(
                    "INSERT INTO user_role_grants(user_id,role,status,group_id) VALUES"
                            + " (?,?,'active',?)",
                    user,
                    role,
                    group);
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
                                        "SELECT password_hash FROM auth_identities WHERE id=?",
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

    @Test
    void loginOtpCannotAuthorizeSubjectDeletionAndRemainsUsableForLogin() throws Exception {
        var f = fixture();
        String token = token(f);
        var b = binding(f);
        String loginCode = null;
        Throwable primary = null;
        try {
            var loginRequest =
                    rest.postForEntity(
                            "/auth/otp/request", Map.of("telegramId", f.telegram()), String.class);
            assertThat(loginRequest.getStatusCode().value()).isEqualTo(204);
            assertThat(loginRequest.getBody() == null || loginRequest.getBody().isBlank()).isTrue();
            loginCode = redis.opsForValue().get("otp:" + f.telegram());
            assertThat(loginCode != null && loginCode.matches("[0-9]{6}")).isTrue();
            assertThat(
                            Long.toString(f.telegram())
                                    .equals(redis.opsForValue().get("otp_code:" + loginCode)))
                    .as("The public LOGIN issuer owns both proof indexes")
                    .isTrue();

            var challenge = call("challenge", new ChallengeRequest(token, f.password(), b));
            assertPrivate(challenge, 200);
            String challengeId = json.readTree(challenge.getBody()).path("challengeId").asText();
            String dangerousCode = ownCode(f.telegram(), challengeId);
            assertThat(!Objects.equals(loginCode, dangerousCode))
                    .as("Independently issued purpose proofs have distinct fixture values")
                    .isTrue();
            long sessionsBefore =
                    count("SELECT count(*) FROM auth_sessions WHERE user_id=?", f.user());
            assertThat(
                            count(
                                    "SELECT count(*) FROM subject_deletion_proofs WHERE actor_id=?"
                                            + " AND receipt_id IS NOT NULL",
                                    f.user()))
                    .isZero();

            var denied = call("verify", new VerifyRequest(token, b, challengeId, loginCode));
            assertPrivate(denied, 409);
            assertThat(
                            count(
                                    "SELECT count(*) FROM subject_deletion_proofs WHERE actor_id=?"
                                            + " AND (receipt_id IS NOT NULL OR consumed_at IS NOT"
                                            + " NULL)",
                                    f.user()))
                    .as("A LOGIN proof cannot produce a dangerous receipt")
                    .isZero();
            assertThat(ownEvents(f.telegram(), "otp.verified")).isZero();
            assertThat(
                            count(
                                    "SELECT count(*) FROM information_schema.tables WHERE"
                                            + " table_schema='public' AND table_name IN"
                                            + " ('subjects','semesters','groups','users')"))
                    .as("Auth proof admission cannot read or mutate Academic tables")
                    .isZero();
            assertThat(count("SELECT count(*) FROM auth_sessions WHERE user_id=?", f.user()))
                    .isEqualTo(sessionsBefore);
            assertThat(loginCode.equals(redis.opsForValue().get("otp:" + f.telegram())))
                    .as("Refusal preserves the original LOGIN proof")
                    .isTrue();

            var login =
                    rest.postForEntity(
                            "/auth/otp/verify",
                            Map.of("telegramId", f.telegram(), "code", loginCode),
                            String.class);
            assertThat(login.getStatusCode().value()).isEqualTo(200);
            var pair = json.readTree(login.getBody());
            assertThat(
                            !pair.path("accessToken").asText().isBlank()
                                    && !pair.path("refreshToken").asText().isBlank())
                    .as("The original public LOGIN proof remains usable")
                    .isTrue();
            assertThat(count("SELECT count(*) FROM auth_sessions WHERE user_id=?", f.user()))
                    .isEqualTo(sessionsBefore + 1);
            assertThat(redis.hasKey("otp:" + f.telegram())).isFalse();
            assertThat(redis.hasKey("otp_code:" + loginCode)).isFalse();

            var accepted = call("verify", new VerifyRequest(token, b, challengeId, dangerousCode));
            assertPrivate(accepted, 200);
            var receipt = json.readTree(accepted.getBody());
            assertThat(
                            receipt.path("accepted").asBoolean()
                                    && !receipt.path("receiptId").asText().isBlank())
                    .as("The correctly scoped dangerous proof still succeeds")
                    .isTrue();
            assertThat(
                            count(
                                    "SELECT count(*) FROM subject_deletion_proofs WHERE actor_id=?"
                                        + " AND receipt_id IS NOT NULL AND consumed_at IS NOT NULL",
                                    f.user()))
                    .isOne();
            assertThat(
                            count(
                                    "SELECT count(*) FROM auth_outbox WHERE"
                                            + " event_type='otp.verified' AND"
                                            + " payload->'payload'->>'purpose'='subject_delete' AND"
                                            + " payload->'payload'->>'challenge_id'=?",
                                    challengeId))
                    .isOne();
        } catch (RuntimeException | Error failure) {
            primary = failure;
            throw failure;
        } finally {
            try {
                var keys =
                        new ArrayList<>(
                                List.of(
                                        "otp:" + f.telegram(), "otp_attempts:" + f.telegram(),
                                        "otp_sent:" + f.telegram(),
                                                "otp_verify_attempts:" + f.telegram()));
                if (loginCode != null) keys.add("otp_code:" + loginCode);
                redis.delete(keys);
            } catch (RuntimeException failure) {
                var fixed = new IllegalStateException("Owned LOGIN proof cleanup failed");
                if (primary == null) throw fixed;
                primary.addSuppressed(fixed);
            }
        }
    }

    private record Fixture(
            long user, long group, long semester, long subject, long telegram, String password) {
        public String toString() {
            return "SubjectProofFixture[redacted]";
        }
    }
}
