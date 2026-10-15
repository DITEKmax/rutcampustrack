package ru.rutcampustrack.auth.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.FanoutExchange;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.RabbitMQContainer;
import ru.rutcampustrack.auth.dto.LoginRequest;
import ru.rutcampustrack.auth.dto.OtpRequest;
import ru.rutcampustrack.auth.dto.TokenResponse;
import ru.rutcampustrack.auth.event.AuthOutboxCleanupJob;
import ru.rutcampustrack.auth.event.AuthOutboxPublisherJob;
import ru.rutcampustrack.auth.events.EventSchemaValidator;
import ru.rutcampustrack.auth.session.AuthSessionException;
import ru.rutcampustrack.auth.session.port.CredentialSessionTransactionPort;
import ru.rutcampustrack.shared.outbox.OutboxEventSender;
import ru.rutcampustrack.shared.outbox.OutboxStorage;

/**
 * M09 G2 (08 P0-2) — AuthOtpFlowIT.
 *
 * <p>Проверяет сквозной flow OTP:
 *
 * <ol>
 *   <li>{@code POST /auth/otp/request} → 204 без тела (код не утекает в HTTP)
 *   <li>Код лежит в Redis по ключу {@code otp:<telegramId>}
 *   <li>RabbitMQ получает {@code otp.requested} event с тем же кодом
 * </ol>
 *
 * <p>Uses the canonical auth integration fixture (Academic V1..V24 + Redis) and adds RabbitMQ for
 * the real OTP and password-change event assertions.
 */
@TestPropertySource(
        properties = {
            // Перекрываем exclude из application-test.yml — нужен RabbitTemplate.
            "spring.autoconfigure.exclude=",
            "auth.password-reset-url=https://recovery.example.test/password-reset"
        })
@Sql(scripts = "classpath:sql/set-telegram-id.sql")
@Sql(
        scripts = "classpath:sql/clear-telegram-id.sql",
        executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
class AuthOtpFlowIT extends AbstractIntegrationTest {

    private static final String EXCHANGE = "rut-uit.events";
    // Уникальный per-JVM-run suffix: Rabbit testcontainer reuse=true, старые
    // очереди с autoDelete=true (до M09 G2 правки) могли остаться и мешать.
    private static final String TEST_QUEUE = "auth-otp-flow-it." + System.nanoTime();
    private static final long TELEGRAM_ID = 123456789L;
    private static final String PASSWORD_HASH =
            "$2a$10$A9r8miSBxjlpjxFB/z0jIerCCSOrLQP6N.sXrjBAw9l7iy4vmRFpi";
    private static final RabbitMQContainer RABBITMQ;

    static {
        RABBITMQ = new RabbitMQContainer("rabbitmq:3.13-management-alpine").withReuse(false);
        RABBITMQ.start();
    }

    @Autowired private TestRestTemplate restTemplate;

    @Autowired private StringRedisTemplate redisTemplate;

    @Autowired private RabbitTemplate rabbitTemplate;

    @Autowired private AmqpAdmin amqpAdmin;

    @Autowired private ObjectMapper objectMapper;

    @Autowired private JdbcTemplate jdbc;
    @Autowired private OutboxStorage outboxStorage;
    @Autowired private OutboxEventSender outboxSender;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private CredentialSessionTransactionPort credentialTransactions;
    private long passwordChangedEventCountBeforeTest;

    @BeforeEach
    void cleanOtpRedisKeys() {
        Set<String> keys = redisTemplate.keys("otp*");
        if (keys != null && !keys.isEmpty()) redisTemplate.delete(keys);
        keys = redisTemplate.keys("password_reset:*");
        if (keys != null && !keys.isEmpty()) redisTemplate.delete(keys);

        Long userId =
                jdbc.queryForObject("SELECT id FROM users WHERE login = 'student'", Long.class);
        jdbc.update(
                "DELETE FROM auth_outbox WHERE payload -> 'payload' ->> 'telegram_id' = ?",
                Long.toString(TELEGRAM_ID));
        jdbc.update(
                "UPDATE auth_sessions SET revoked_at = NOW(), revoke_reason = 'SECURITY_REVOKED' "
                        + "WHERE user_id = ? AND revoked_at IS NULL",
                userId);
        jdbc.update("DELETE FROM password_reset_tokens WHERE user_id = ?", userId);
        passwordChangedEventCountBeforeTest =
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM account_security_events WHERE user_id = ? "
                                + "AND event_type = 'PASSWORD_CHANGED'",
                        Long.class,
                        userId);
        jdbc.update(
                "UPDATE users SET password_hash = ?, password_changed = false, "
                        + "initial_password = NULL, telegram_id = ? WHERE id = ?",
                PASSWORD_HASH,
                TELEGRAM_ID,
                userId);

        // Declare test queue and bind к exchange — чтобы event попал именно сюда.
        // durable=false, exclusive=false, autoDelete=false — queue должен пережить
        // закрытие канала между publish и receive (иначе 404 NOT_FOUND).
        FanoutExchange exchange = new FanoutExchange(EXCHANGE, true, false);
        Queue queue = new Queue(TEST_QUEUE, false, false, false);
        amqpAdmin.declareExchange(exchange);
        amqpAdmin.declareQueue(queue);
        Binding binding = BindingBuilder.bind(queue).to(exchange);
        amqpAdmin.declareBinding(binding);
        // Drain любые события, оставленные предыдущими тестами
        while (rabbitTemplate.receive(TEST_QUEUE, 10) != null) {
            // drain
        }
    }

    @AfterEach
    void dropTestQueue() {
        try {
            amqpAdmin.deleteQueue(TEST_QUEUE);
        } catch (RuntimeException ignored) {
            // best-effort — последующий @BeforeEach всё равно re-declare
        }
    }

    @Test
    @Timeout(value = 20, unit = TimeUnit.SECONDS)
    void requestOtp_204Body_redisHasCode_rabbitReceivesEvent() throws Exception {
        ResponseEntity<Void> response =
                restTemplate.postForEntity(
                        "/auth/otp/request", new OtpRequest(TELEGRAM_ID), Void.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(response.getBody()).isNull();

        String code = redisTemplate.opsForValue().get("otp:" + TELEGRAM_ID);
        assertThat(code).as("код должен лежать в Redis").isNotBlank().hasSize(6);

        // Polling на Rabbit — event публикуется асинхронно через @EventListener.
        Message message = receiveWithRetry(TEST_QUEUE);
        assertThat(message).as("otp.requested должен прилететь в Rabbit").isNotNull();

        JsonNode envelope = objectMapper.readTree(message.getBody());
        assertThat(envelope.path("event_type").asText()).isEqualTo("otp.requested");
        assertThat(envelope.path("source").asText()).isEqualTo("auth-service");
        assertThat(envelope.path("event_version").asInt()).isEqualTo(1);
        assertThat(envelope.path("trace_id").asText()).isNotBlank();
        assertThat(envelope.path("event_id").asText()).isNotBlank();

        JsonNode payload = envelope.path("payload");
        assertThat(payload.path("telegram_id").asLong()).isEqualTo(TELEGRAM_ID);
        assertThat(payload.path("purpose").asText()).isEqualTo("login");
        assertThat(payload.path("code").asText())
                .as("event.payload.code должен совпадать с Redis")
                .isEqualTo(code);
        assertThat(payload.path("ttl_seconds").asInt()).isGreaterThan(0);
    }

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void unicodePasswordChangeAndResetPreserveExactCredentialsAtHttpBoundaries() throws Exception {
        String changeLogin =
                "uni-c-" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
        String resetLogin =
                "uni-r-" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
        long resetTelegram = (UUID.randomUUID().getMostSignificantBits() & Long.MAX_VALUE) | 1L;
        String twelveScalars = "abcdefghij\u0661!";
        String seventyTwoBytes = "\uD83D\uDE00".repeat(17) + "ab1!";
        List<String> invalidPasswords =
                List.of("abcdefghi\u0661!", seventyTwoBytes + "a", "unpaired");
        assertThat(twelveScalars.codePointCount(0, twelveScalars.length())).isEqualTo(12);
        assertThat(seventyTwoBytes.getBytes(StandardCharsets.UTF_8).length).isEqualTo(72);
        assertThat(
                        invalidPasswords
                                .getFirst()
                                .codePointCount(0, invalidPasswords.getFirst().length()))
                .isEqualTo(11);
        assertThat(invalidPasswords.get(1).getBytes(StandardCharsets.UTF_8).length).isEqualTo(73);
        Long changeUser = null;
        Long resetUser = null;
        Throwable primaryFailure = null;
        try {
            changeUser = seedUnicodePasswordActor(changeLogin, null);
            resetUser = seedUnicodePasswordActor(resetLogin, resetTelegram);
            for (long userId : List.of(changeUser, resetUser)) {
                assertThat(
                                jdbc.queryForObject(
                                        "SELECT initial_password IS NOT NULL AND NOT password_changed FROM users WHERE id = ?",
                                        Boolean.class,
                                        userId))
                        .isTrue();
            }
            TokenResponse changeSession = unicodePasswordLogin(changeLogin, "password");
            HttpHeaders changeHeaders = new HttpHeaders();
            changeHeaders.setBearerAuth(changeSession.accessToken());
            List<Object> changeBefore = unicodePasswordState(changeUser);
            for (String invalid : invalidPasswords) {
                ResponseEntity<JsonNode> rejected =
                        unicodePasswordPost(
                                "/auth/change-password",
                                changeHeaders,
                                Map.of("currentPassword", "password", "newPassword", invalid),
                                invalid.equals("unpaired"));
                assertUnicodePasswordRejection(rejected, invalid, "password");
                assertThat(
                                rejected.getBody()
                                        .path("extras")
                                        .path("code")
                                        .asText()
                                        .equals("PASSWORD_POLICY_VIOLATION"))
                        .isTrue();
                assertThat(unicodePasswordState(changeUser).equals(changeBefore)).isTrue();
            }
            ResponseEntity<JsonNode> wrongCurrent =
                    unicodePasswordPost(
                            "/auth/change-password",
                            changeHeaders,
                            Map.of(
                                    "currentPassword",
                                    "wrong-password",
                                    "newPassword",
                                    twelveScalars),
                            false);
            assertUnicodePasswordRejection(wrongCurrent, "wrong-password", twelveScalars);
            assertThat(
                            wrongCurrent
                                    .getBody()
                                    .path("extras")
                                    .path("code")
                                    .asText()
                                    .equals("CURRENT_PASSWORD_INVALID"))
                    .isTrue();
            assertThat(unicodePasswordState(changeUser).equals(changeBefore)).isTrue();
            ResponseEntity<JsonNode> changed =
                    unicodePasswordPost(
                            "/auth/change-password",
                            changeHeaders,
                            Map.of("currentPassword", "password", "newPassword", twelveScalars),
                            false);
            assertThat(changed.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
            assertThat(changed.getHeaders().getCacheControl()).isEqualTo("no-store");
            assertThat(changed.getBody() == null).isTrue();
            assertThat(
                            changed.getHeaders().getFirst(HttpHeaders.SET_COOKIE) != null
                                    && changed.getHeaders()
                                            .getFirst(HttpHeaders.SET_COOKIE)
                                            .contains("Max-Age=0"))
                    .isTrue();
            assertUnicodePasswordCommitted(changeUser, twelveScalars, "abcdefghik\u0661!");
            assertThat(bearerSessionStatus(changeSession.accessToken()))
                    .isEqualTo(HttpStatus.UNAUTHORIZED);
            assertThat(refreshStatus(changeSession.refreshToken()))
                    .isEqualTo(HttpStatus.UNAUTHORIZED);
            assertUnicodePasswordLoginDenied(changeLogin, "password");
            assertUnicodePasswordLoginDenied(changeLogin, "abcdefghik\u0661!");
            unicodePasswordLogin(changeLogin, twelveScalars);

            TokenResponse resetFirst = unicodePasswordLogin(resetLogin, "password");
            TokenResponse resetSecond = unicodePasswordLogin(resetLogin, "password");
            ResponseEntity<JsonNode> requested =
                    restTemplate.postForEntity(
                            "/auth/password-reset/request",
                            Map.of("login", resetLogin),
                            JsonNode.class);
            assertThat(requested.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
            assertThat(requested.getHeaders().getCacheControl()).isEqualTo("no-store");
            assertThat(requested.getBody() != null).isTrue();
            String challenge = requested.getBody().path("challengeId").asText();
            Message message = receiveWithRetry(TEST_QUEUE);
            assertThat(message != null).isTrue();
            JsonNode payload = objectMapper.readTree(message.getBody()).path("payload");
            assertThat(
                            payload.path("purpose").asText().equals("password_reset")
                                    && payload.path("challenge_id").asText().equals(challenge)
                                    && payload.path("telegram_id").asLong() == resetTelegram)
                    .isTrue();
            String code = payload.path("code").asText();
            ResponseEntity<JsonNode> verified =
                    restTemplate.postForEntity(
                            "/auth/password-reset/verify",
                            Map.of("challengeId", challenge, "code", code),
                            JsonNode.class);
            assertThat(verified.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(verified.getHeaders().getCacheControl()).isEqualTo("no-store");
            assertThat(verified.getBody() != null).isTrue();
            String ticket = verified.getBody().path("resetTicket").asText();
            assertThat(
                            !ticket.isBlank()
                                    && !verified.getBody().has("accessToken")
                                    && !verified.getBody().has("refreshToken"))
                    .isTrue();
            List<Object> resetBefore = unicodePasswordState(resetUser);
            assertThat(
                            jdbc.queryForObject(
                                    "SELECT COUNT(*) FROM password_reset_tokens WHERE user_id = ? AND used_at IS NULL",
                                    Integer.class,
                                    resetUser))
                    .isEqualTo(1);
            for (String invalid : invalidPasswords) {
                ResponseEntity<JsonNode> rejected =
                        unicodePasswordPost(
                                "/auth/password-reset/complete",
                                new HttpHeaders(),
                                Map.of("resetTicket", ticket, "newPassword", invalid),
                                invalid.equals("unpaired"));
                assertUnicodePasswordRejection(rejected, invalid, ticket);
                assertThat(
                                rejected.getBody()
                                        .path("extras")
                                        .path("code")
                                        .asText()
                                        .equals("PASSWORD_POLICY_VIOLATION"))
                        .isTrue();
                assertThat(unicodePasswordState(resetUser).equals(resetBefore)).isTrue();
            }
            // The same real proof survives every invalid password; no fabricated replacement
            // ticket.
            ResponseEntity<JsonNode> completed =
                    unicodePasswordPost(
                            "/auth/password-reset/complete",
                            new HttpHeaders(),
                            Map.of("resetTicket", ticket, "newPassword", seventyTwoBytes),
                            false);
            assertThat(completed.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
            assertThat(completed.getHeaders().getCacheControl()).isEqualTo("no-store");
            assertThat(completed.getBody() == null).isTrue();
            assertUnicodePasswordCommitted(
                    resetUser, seventyTwoBytes, "\uD83D\uDE00".repeat(17) + "ac1!");
            assertThat(
                            jdbc.queryForObject(
                                    "SELECT COUNT(*) FROM password_reset_tokens WHERE user_id = ? AND used_at IS NOT NULL",
                                    Integer.class,
                                    resetUser))
                    .isEqualTo(1);
            assertThat(bearerSessionStatus(resetFirst.accessToken()))
                    .isEqualTo(HttpStatus.UNAUTHORIZED);
            assertThat(bearerSessionStatus(resetSecond.accessToken()))
                    .isEqualTo(HttpStatus.UNAUTHORIZED);
            assertThat(refreshStatus(resetFirst.refreshToken())).isEqualTo(HttpStatus.UNAUTHORIZED);
            assertThat(refreshStatus(resetSecond.refreshToken()))
                    .isEqualTo(HttpStatus.UNAUTHORIZED);
            assertUnicodePasswordLoginDenied(resetLogin, "password");
            assertUnicodePasswordLoginDenied(resetLogin, "\uD83D\uDE00".repeat(17) + "ac1!");
            unicodePasswordLogin(resetLogin, seventyTwoBytes);
        } catch (Exception | AssertionError failure) {
            primaryFailure = failure;
            throw failure;
        } finally {
            try {
                cleanUnicodePasswordActors(changeUser, resetUser);
            } catch (RuntimeException | AssertionError cleanupFailure) {
                if (primaryFailure != null) primaryFailure.addSuppressed(cleanupFailure);
                else throw cleanupFailure;
            }
        }
    }

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void ordinaryPasswordResetDoesNotIssueOrCompleteForActiveAdmin() throws Exception {
        String suffix = UUID.randomUUID().toString();
        String adminLogin = "reset-admin-" + suffix;
        String studentLogin = "reset-student-" + suffix;
        long admin = seedUnicodePasswordActor(adminLogin, 810000000001L);
        Long student = null;
        Throwable primary = null;
        try {
            activateOnlyAdminGrant(admin);
            loginUser(adminLogin, "password");
            List<Object> before = unicodePasswordState(admin);
            ResponseEntity<JsonNode> requested = requestOwnedReset(Map.of("login", adminLogin));
            String challenge = requested.getBody().path("challengeId").asText();
            String code = ownedResetCode(challenge);
            Message delivered = rabbitTemplate.receive(TEST_QUEUE, 100);
            ResponseEntity<JsonNode> verified = verifyOwnedReset(challenge, code);
            // Exercise the unsafe old implementation through completion before asserting denial.
            ResponseEntity<JsonNode> completed = null;
            if (verified.getStatusCode() == HttpStatus.OK
                    && verified.getBody() != null
                    && verified.getBody().hasNonNull("resetTicket")) {
                completed = completeOwnedReset(verified.getBody().path("resetTicket").asText());
            }
            assertThat(delivered == null).isTrue();
            assertThat(verified.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(verified.getBody() != null && !verified.getBody().has("resetTicket"))
                    .isTrue();
            assertThat(completed == null || completed.getStatusCode() != HttpStatus.NO_CONTENT)
                    .isTrue();
            assertThat(unicodePasswordState(admin).equals(before)).isTrue();

            ResponseEntity<JsonNode> alias = requestOwnedReset(Map.of("telegramId", 810000000001L));
            ResponseEntity<JsonNode> unknown =
                    requestOwnedReset(Map.of("login", "missing-" + suffix));
            assertThat(
                            alias.getBody().size() == requested.getBody().size()
                                    && unknown.getBody().size() == requested.getBody().size())
                    .isTrue();
            assertThat(rabbitTemplate.receive(TEST_QUEUE, 100) == null).isTrue();
            assertThat(unicodePasswordState(admin).equals(before)).isTrue();

            // The same public flow remains usable for an ordinary current non-ADMIN account.
            student = seedUnicodePasswordActor(studentLogin, 810000000002L);
            loginUser(studentLogin, "password");
            ResponseEntity<JsonNode> positive = requestOwnedReset(Map.of("login", studentLogin));
            String positiveChallenge = positive.getBody().path("challengeId").asText();
            JsonNode event =
                    objectMapper.readTree(receiveWithRetry(TEST_QUEUE).getBody()).path("payload");
            assertThat(
                            event.path("challenge_id").asText().equals(positiveChallenge)
                                    && event.path("purpose").asText().equals("password_reset"))
                    .isTrue();
            ResponseEntity<JsonNode> ticket =
                    verifyOwnedReset(positiveChallenge, event.path("code").asText());
            assertThat(ticket.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(ticket.getBody() != null && ticket.getBody().hasNonNull("resetTicket"))
                    .isTrue();
            assertThat(
                            completeOwnedReset(ticket.getBody().path("resetTicket").asText())
                                    .getStatusCode())
                    .isEqualTo(HttpStatus.NO_CONTENT);
            assertUnicodePasswordCommitted(student, "AdminBoundaryReset42!", "password");
            assertThat(unicodePasswordState(admin).equals(before)).isTrue();
        } catch (Exception | AssertionError failure) {
            primary = failure;
            throw failure;
        } finally {
            try {
                cleanUnicodePasswordActors(admin, student);
            } catch (RuntimeException | AssertionError cleanupFailure) {
                if (primary != null) primary.addSuppressed(cleanupFailure);
                else throw cleanupFailure;
            }
        }
    }

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void adminActivationInvalidatesPendingOtpAndResetTicketWithoutEffects() throws Exception {
        String suffix = UUID.randomUUID().toString();
        String otpLogin = "reset-pending-otp-" + suffix;
        String ticketLogin = "reset-pending-ticket-" + suffix;
        long otpUser = seedUnicodePasswordActor(otpLogin, 810000000003L);
        Long ticketUser = null;
        Throwable primary = null;
        try {
            loginUser(otpLogin, "password");
            ResponseEntity<JsonNode> otp = requestOwnedReset(Map.of("login", otpLogin));
            String challenge = otp.getBody().path("challengeId").asText();
            JsonNode otpEvent =
                    objectMapper.readTree(receiveWithRetry(TEST_QUEUE).getBody()).path("payload");
            assertThat(otpEvent.path("challenge_id").asText().equals(challenge)).isTrue();
            activateOnlyAdminGrant(otpUser);
            List<Object> otpBefore = unicodePasswordState(otpUser);
            ResponseEntity<JsonNode> denied =
                    verifyOwnedReset(challenge, otpEvent.path("code").asText());
            assertThat(denied.getStatusCode()).isEqualTo(HttpStatus.GONE);
            assertThat(denied.getBody() != null && !denied.getBody().has("resetTicket")).isTrue();
            assertThat(unicodePasswordState(otpUser).equals(otpBefore)).isTrue();

            ticketUser = seedUnicodePasswordActor(ticketLogin, 810000000004L);
            loginUser(ticketLogin, "password");
            ResponseEntity<JsonNode> request = requestOwnedReset(Map.of("login", ticketLogin));
            String ticketChallenge = request.getBody().path("challengeId").asText();
            JsonNode ticketEvent =
                    objectMapper.readTree(receiveWithRetry(TEST_QUEUE).getBody()).path("payload");
            assertThat(ticketEvent.path("challenge_id").asText().equals(ticketChallenge)).isTrue();
            ResponseEntity<JsonNode> verified =
                    verifyOwnedReset(ticketChallenge, ticketEvent.path("code").asText());
            assertThat(verified.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(verified.getBody() != null && verified.getBody().hasNonNull("resetTicket"))
                    .isTrue();
            String ticket = verified.getBody().path("resetTicket").asText();
            activateOnlyAdminGrant(ticketUser);
            List<Object> ticketBefore = unicodePasswordState(ticketUser);
            assertThat(completeOwnedReset(ticket).getStatusCode()).isEqualTo(HttpStatus.GONE);
            assertThat(unicodePasswordState(ticketUser).equals(ticketBefore)).isTrue();

            // The transaction boundary independently rejects promotion after an outer precheck.
            long promotedUser = ticketUser;
            assertThatThrownBy(
                            () ->
                                    credentialTransactions.issuePasswordResetTicket(
                                            promotedUser,
                                            new CredentialSessionTransactionPort.CredentialHash(
                                                    resetChallengeHash(
                                                            UUID.randomUUID().toString())),
                                            Instant.now().plusSeconds(120)))
                    .isInstanceOfSatisfying(
                            AuthSessionException.class,
                            error ->
                                    assertThat(error.code())
                                            .isEqualTo(AuthSessionException.Code.ROLE_NOT_GRANTED));
            assertThat(unicodePasswordState(ticketUser).equals(ticketBefore)).isTrue();
            assertThat(unicodePasswordState(otpUser).equals(otpBefore)).isTrue();
            assertThat(rabbitTemplate.receive(TEST_QUEUE, 100) == null).isTrue();
        } catch (Exception | AssertionError failure) {
            primary = failure;
            throw failure;
        } finally {
            try {
                cleanUnicodePasswordActors(otpUser, ticketUser);
            } catch (RuntimeException | AssertionError cleanupFailure) {
                if (primary != null) primary.addSuppressed(cleanupFailure);
                else throw cleanupFailure;
            }
        }
    }

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void passwordResetIsPurposeBoundAtomicAndRevokesEverySession() throws Exception {
        TokenResponse firstSession = loginStudent("password");
        TokenResponse secondSession = loginStudent("password");
        assertThat(activeSessionCount()).isEqualTo(2);

        ResponseEntity<String> invalidRequest =
                restTemplate.postForEntity(
                        "/auth/password-reset/request",
                        Map.of("login", "student", "telegramId", TELEGRAM_ID),
                        String.class);
        assertThat(invalidRequest.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(invalidRequest.getHeaders().getCacheControl().split("\\s*,\\s*"))
                .containsOnly("no-store");

        ResponseEntity<JsonNode> request =
                restTemplate.postForEntity(
                        "/auth/password-reset/request", Map.of("login", "student"), JsonNode.class);
        assertThat(request.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(request.getHeaders().getCacheControl().split("\\s*,\\s*"))
                .containsOnly("no-store");
        assertThat(request.getBody()).isNotNull();
        String challengeId = request.getBody().path("challengeId").asText();
        assertThat(challengeId).hasSize(32);
        assertThat(request.getBody().path("ttlSeconds").asInt()).isEqualTo(120);
        assertThat(request.getBody().has("attemptsRemaining")).isFalse();
        assertThat(request.getBody().has("code")).isFalse();

        ResponseEntity<JsonNode> unknown =
                restTemplate.postForEntity(
                        "/auth/password-reset/request",
                        Map.of("login", "unknown-reset-user"),
                        JsonNode.class);
        assertThat(unknown.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(unknown.getHeaders().getCacheControl().split("\\s*,\\s*"))
                .containsOnly("no-store");
        assertThat(unknown.getBody()).isNotNull();
        assertThat(unknown.getBody().path("ttlSeconds").asInt()).isEqualTo(120);
        assertThat(unknown.getBody().size()).isEqualTo(request.getBody().size());
        assertThat(unknown.getBody().has("attemptsRemaining")).isFalse();
        assertThat(unknown.getBody().has("code")).isFalse();

        JsonNode event = objectMapper.readTree(receiveWithRetry(TEST_QUEUE).getBody());
        JsonNode payload = event.path("payload");
        String code = payload.path("code").asText();
        assertThat(payload.path("purpose").asText()).isEqualTo("password_reset");
        assertThat(payload.path("challenge_id").asText()).isEqualTo(challengeId);
        assertThat(payload.path("telegram_id").asLong()).isEqualTo(TELEGRAM_ID);
        assertThat(payload.path("ttl_seconds").asInt()).isEqualTo(120);
        assertThat(payload.path("attempts_remaining").asInt()).isEqualTo(3);

        // A bot request for the same account shares the login request's account window.
        ResponseEntity<JsonNode> botAlias =
                restTemplate.postForEntity(
                        "/auth/password-reset/request",
                        Map.of("telegramId", TELEGRAM_ID),
                        JsonNode.class);
        assertThat(botAlias.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(botAlias.getBody()).isNotNull();
        assertThat(botAlias.getBody().has("attemptsRemaining")).isFalse();
        assertThat(rabbitTemplate.receive(TEST_QUEUE, 100)).isNull();

        // A reset-purpose code cannot enter the login OTP route or create a session.
        ResponseEntity<String> loginVerify =
                restTemplate.postForEntity(
                        "/auth/otp/verify",
                        Map.of("telegramId", TELEGRAM_ID, "code", code),
                        String.class);
        assertThat(loginVerify.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(activeSessionCount()).isEqualTo(2);

        ResponseEntity<JsonNode> verified =
                restTemplate.postForEntity(
                        "/auth/password-reset/verify",
                        Map.of("challengeId", challengeId, "code", code),
                        JsonNode.class);
        assertThat(verified.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(verified.getHeaders().getCacheControl().split("\\s*,\\s*"))
                .containsOnly("no-store");
        assertThat(verified.getBody()).isNotNull();
        String resetTicket = verified.getBody().path("resetTicket").asText();
        assertThat(resetTicket).isNotBlank();
        assertThat(verified.getBody().path("expiresInSeconds").asInt()).isEqualTo(120);
        assertThat(verified.getBody().path("attemptsRemaining").asInt()).isEqualTo(3);
        assertThat(verified.getBody().has("accessToken")).isFalse();
        assertThat(verified.getBody().has("refreshToken")).isFalse();

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            CyclicBarrier start = new CyclicBarrier(2);
            Future<ResponseEntity<String>> first =
                    executor.submit(
                            () -> {
                                start.await(10, TimeUnit.SECONDS);
                                return restTemplate.postForEntity(
                                        "/auth/password-reset/complete",
                                        Map.of(
                                                "resetTicket",
                                                resetTicket,
                                                "newPassword",
                                                "NewStrongPassword42!"),
                                        String.class);
                            });
            Future<ResponseEntity<String>> second =
                    executor.submit(
                            () -> {
                                start.await(10, TimeUnit.SECONDS);
                                return restTemplate.postForEntity(
                                        "/auth/password-reset/complete",
                                        Map.of(
                                                "resetTicket",
                                                resetTicket,
                                                "newPassword",
                                                "NewStrongPassword42!"),
                                        String.class);
                            });
            assertThat(first.get(20, TimeUnit.SECONDS).getStatusCode())
                    .isIn(HttpStatus.NO_CONTENT, HttpStatus.GONE);
            assertThat(second.get(20, TimeUnit.SECONDS).getStatusCode())
                    .isIn(HttpStatus.NO_CONTENT, HttpStatus.GONE);
            assertThat(List.of(first.get().getStatusCode(), second.get().getStatusCode()))
                    .containsExactlyInAnyOrder(HttpStatus.NO_CONTENT, HttpStatus.GONE);
        } finally {
            executor.shutdownNow();
        }

        assertThat(outboxStorage.countPending()).isEqualTo(1);
        assertThat(rabbitTemplate.receive(TEST_QUEUE, 100)).isNull();
        assertThat(publishAuthOutbox(outboxSender)).isEqualTo(1);
        Message passwordChangedMessage = receiveWithRetry(TEST_QUEUE);
        assertThat(passwordChangedMessage)
                .as("после committed reset отправляется password.changed")
                .isNotNull();
        String passwordChangedJson =
                new String(
                        passwordChangedMessage.getBody(), java.nio.charset.StandardCharsets.UTF_8);
        JsonNode passwordChanged = objectMapper.readTree(passwordChangedJson);
        assertThat(EventSchemaValidator.validate("password.changed.json", passwordChangedJson))
                .as("password.changed event соответствует публичной schema")
                .isEmpty();
        assertThat(passwordChanged.path("event_type").asText()).isEqualTo("password.changed");
        JsonNode changedPayload = passwordChanged.path("payload");
        assertThat(changedPayload.path("telegram_id").asLong()).isEqualTo(TELEGRAM_ID);
        assertThat(passwordChanged.path("event_version").asInt()).isEqualTo(2);
        Map<String, Object> changedIdentity =
                jdbc.queryForMap(
                        "SELECT id, credentials_version, telegram_binding_version, telegram_id "
                                + "FROM users WHERE login = 'student'");
        var changedFields = new java.util.HashSet<String>();
        changedPayload.fieldNames().forEachRemaining(changedFields::add);
        assertThat(changedFields)
                .containsExactlyInAnyOrder(
                        "user_id",
                        "credentials_version",
                        "telegram_binding_version",
                        "telegram_id");
        assertThat(changedPayload.path("user_id").asLong())
                .isEqualTo(((Number) changedIdentity.get("id")).longValue());
        assertThat(changedPayload.path("credentials_version").asLong())
                .isPositive()
                .isEqualTo(((Number) changedIdentity.get("credentials_version")).longValue());
        assertThat(changedPayload.path("telegram_binding_version").asLong())
                .isPositive()
                .isEqualTo(((Number) changedIdentity.get("telegram_binding_version")).longValue());
        assertThat(changedPayload.path("telegram_id").asLong())
                .isEqualTo(((Number) changedIdentity.get("telegram_id")).longValue());
        assertThat(
                        passwordChangedJson.contains("NewStrongPassword42!")
                                || passwordChangedJson.contains(resetTicket))
                .as("password event must omit changed password and reset ticket values")
                .isFalse();
        assertThat(changedPayload.has("password")).isFalse();
        assertThat(changedPayload.has("reset_ticket")).isFalse();
        assertThat(changedPayload.has("code")).isFalse();
        assertThat(
                        jdbc.queryForObject(
                                "SELECT COUNT(*) FROM account_security_events WHERE user_id = "
                                        + "(SELECT id FROM users WHERE login = 'student') AND event_type = 'PASSWORD_CHANGED'",
                                Long.class))
                .isEqualTo(passwordChangedEventCountBeforeTest + 1);
        assertThat(rabbitTemplate.receive(TEST_QUEUE, 100)).isNull();

        assertThat(activeSessionCount()).isZero();
        assertThat(bearerSessionStatus(firstSession.accessToken()))
                .isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(bearerSessionStatus(secondSession.accessToken()))
                .isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(refreshStatus(firstSession.refreshToken())).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(loginStudent("NewStrongPassword42!").accessToken()).isNotBlank();
    }

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void passwordChangeIntentSurvivesBrokerFailureAndPublisherRestart() throws Exception {
        TokenResponse oldSession = loginStudent("password");
        CachingConnectionFactory connections =
                (CachingConnectionFactory) rabbitTemplate.getConnectionFactory();
        CachingConnectionFactory publisherConnections =
                (CachingConnectionFactory) connections.getPublisherConnectionFactory();
        assertThat(publisherConnections).isNotNull();
        String brokerAddress = RABBITMQ.getHost() + ":" + RABBITMQ.getMappedPort(5672);
        try {
            // Spring also has a dedicated publisher factory and may use an
            // address list instead of its port property. Disconnect both own
            // factories from the real broker, including their cached channels.
            connections.setAddresses("127.0.0.1:1");
            publisherConnections.setAddresses("127.0.0.1:1");
            connections.resetConnection();
            publisherConnections.resetConnection();
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(oldSession.accessToken());
            ResponseEntity<Void> changed =
                    restTemplate.exchange(
                            "/auth/change-password",
                            HttpMethod.POST,
                            new HttpEntity<>(
                                    Map.of(
                                            "currentPassword",
                                            "password",
                                            "newPassword",
                                            "OutboxNewPassword42!"),
                                    headers),
                            Void.class);
            assertThat(changed.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
            assertThat(bearerSessionStatus(oldSession.accessToken()))
                    .isEqualTo(HttpStatus.UNAUTHORIZED);
            assertThat(loginStudent("OutboxNewPassword42!").accessToken()).isNotBlank();
            assertThat(outboxStorage.countPending()).isEqualTo(1);
            assertThatThrownBy(() -> publishAuthOutbox(outboxSender))
                    .isInstanceOf(IllegalStateException.class);
            assertThat(outboxStorage.countPending()).isEqualTo(1);
        } finally {
            connections.setAddresses(brokerAddress);
            publisherConnections.setAddresses(brokerAddress);
            connections.resetConnection();
            publisherConnections.resetConnection();
        }

        String storedJson =
                jdbc.queryForObject(
                        "SELECT payload::text FROM auth_outbox WHERE status = 'pending'",
                        String.class);
        JsonNode storedEvent = objectMapper.readTree(storedJson);
        assertThat(EventSchemaValidator.validate("password.changed.json", storedJson)).isEmpty();
        assertThat(storedEvent.path("event_version").asInt()).isEqualTo(2);
        JsonNode storedPayload = storedEvent.path("payload");
        Map<String, Object> storedIdentity =
                jdbc.queryForMap(
                        "SELECT id, credentials_version, telegram_binding_version, telegram_id "
                                + "FROM users WHERE login = 'student'");
        var storedFields = new java.util.HashSet<String>();
        storedPayload.fieldNames().forEachRemaining(storedFields::add);
        assertThat(storedFields)
                .containsExactlyInAnyOrder(
                        "user_id",
                        "credentials_version",
                        "telegram_binding_version",
                        "telegram_id");
        assertThat(storedPayload.path("user_id").asLong())
                .isEqualTo(((Number) storedIdentity.get("id")).longValue());
        assertThat(storedPayload.path("credentials_version").asLong())
                .isPositive()
                .isEqualTo(((Number) storedIdentity.get("credentials_version")).longValue());
        assertThat(storedPayload.path("telegram_binding_version").asLong())
                .isPositive()
                .isEqualTo(((Number) storedIdentity.get("telegram_binding_version")).longValue());
        assertThat(storedPayload.path("telegram_id").asLong())
                .isEqualTo(((Number) storedIdentity.get("telegram_id")).longValue());
        assertThat(storedEvent.path("payload").path("telegram_id").asLong()).isEqualTo(TELEGRAM_ID);
        assertThat(storedJson)
                .doesNotContain("OutboxNewPassword42!", "password_hash", "reset_ticket", "code");

        // Age must not expire an undelivered intent.
        jdbc.update(
                "UPDATE auth_outbox SET created_at = NOW() - INTERVAL '8 days' WHERE status = 'pending'");
        new TransactionTemplate(transactionManager)
                .executeWithoutResult(
                        status ->
                                assertThat(
                                                new AuthOutboxCleanupJob(
                                                                outboxStorage, Clock.systemUTC(), 7)
                                                        .runCleanup())
                                        .isZero());
        assertThat(outboxStorage.countPending()).isEqualTo(1);

        // Broker accepts the event, then the publisher loses its result before
        // marking sent. A restarted publisher retries the same logical event.
        assertThatThrownBy(
                        () ->
                                publishAuthOutbox(
                                        (type, payload) -> {
                                            outboxSender.send(type, payload);
                                            throw new IllegalStateException(
                                                    "simulated process loss after broker confirmation");
                                        }))
                .isInstanceOf(IllegalStateException.class);
        assertThat(outboxStorage.countPending()).isEqualTo(1);
        Message firstDelivery = receiveWithRetry(TEST_QUEUE);
        assertThat(firstDelivery).isNotNull();
        assertThat(objectMapper.readTree(firstDelivery.getBody())).isEqualTo(storedEvent);

        assertThat(publishAuthOutbox(outboxSender)).isEqualTo(1);
        Message retryDelivery = receiveWithRetry(TEST_QUEUE);
        assertThat(retryDelivery).isNotNull();
        assertThat(objectMapper.readTree(retryDelivery.getBody())).isEqualTo(storedEvent);
        assertThat(outboxStorage.countPending()).isZero();
        assertThat(publishAuthOutbox(outboxSender)).isZero();
        assertThat(rabbitTemplate.receive(TEST_QUEUE, 100)).isNull();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM auth_outbox", Long.class))
                .isEqualTo(1L);
    }

    private int publishAuthOutbox(OutboxEventSender sender) {
        // Recreate the stateless publisher on every invocation, retaining only
        // database state, as after an Auth process restart.
        return new TransactionTemplate(transactionManager)
                .execute(
                        status ->
                                new AuthOutboxPublisherJob(outboxStorage, sender, null)
                                        .publishBatch());
    }

    @Test
    @Timeout(value = 25, unit = TimeUnit.SECONDS)
    void passwordResetFailedAttemptsSurviveANewChallenge() throws Exception {
        assertThat(activeSessionCount()).isZero();

        ResponseEntity<JsonNode> request =
                restTemplate.postForEntity(
                        "/auth/password-reset/request",
                        Map.of("telegramId", TELEGRAM_ID),
                        JsonNode.class);
        assertThat(request.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        String challengeId = request.getBody().path("challengeId").asText();
        JsonNode event = objectMapper.readTree(receiveWithRetry(TEST_QUEUE).getBody());
        JsonNode payload = event.path("payload");
        String code = payload.path("code").asText();
        String wrongCode = String.format("%06d", (Integer.parseInt(code) + 1) % 1_000_000);

        for (int attempt = 1; attempt <= 3; attempt++) {
            ResponseEntity<String> rejected =
                    restTemplate.postForEntity(
                            "/auth/password-reset/verify",
                            Map.of("challengeId", challengeId, "code", wrongCode),
                            String.class);
            assertThat(rejected.getStatusCode())
                    .isEqualTo(
                            attempt == 3 ? HttpStatus.TOO_MANY_REQUESTS : HttpStatus.BAD_REQUEST);
        }

        Set<String> cooldownKeys = redisTemplate.keys("password_reset:request_cooldown:*");
        if (cooldownKeys != null && !cooldownKeys.isEmpty()) {
            redisTemplate.delete(cooldownKeys);
        }
        ResponseEntity<JsonNode> nextRequest =
                restTemplate.postForEntity(
                        "/auth/password-reset/request", Map.of("login", "student"), JsonNode.class);
        assertThat(nextRequest.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(nextRequest.getBody()).isNotNull();
        assertThat(nextRequest.getBody().has("attemptsRemaining")).isFalse();
        assertThat(rabbitTemplate.receive(TEST_QUEUE, 100)).isNull();

        ResponseEntity<String> limited =
                restTemplate.postForEntity(
                        "/auth/password-reset/verify",
                        Map.of(
                                "challengeId",
                                nextRequest.getBody().path("challengeId").asText(),
                                "code",
                                wrongCode),
                        String.class);
        assertThat(limited.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);

        String freshChallengeId = nextRequest.getBody().path("challengeId").asText();
        ResponseEntity<String> noActiveSession =
                restTemplate.postForEntity(
                        "/auth/password-reset/verify",
                        Map.of(
                                "challengeId",
                                freshChallengeId,
                                "code",
                                payload.path("code").asText()),
                        String.class);
        assertThat(noActiveSession.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(activeSessionCount()).isZero();
    }

    @Test
    @Timeout(value = 180, unit = TimeUnit.SECONDS)
    void passwordResetVerifyRejectsNaturallyExpiredAndConsumedChallengesWithoutMutation()
            throws Exception {
        long userId =
                jdbc.queryForObject("SELECT id FROM users WHERE login = 'student'", Long.class);
        // Sensitive snapshots remain in memory; assertion failures expose booleans only.
        java.util.function.Supplier<List<Object>> protectedState =
                () ->
                        List.of(
                                jdbc.queryForList(
                                        "SELECT password_hash, password_changed, initial_password FROM users WHERE id = ?",
                                        userId),
                                jdbc.queryForList(
                                        "SELECT * FROM auth_sessions WHERE user_id = ? ORDER BY sid",
                                        userId));
        java.util.function.Supplier<List<Map<String, Object>>> tickets =
                () ->
                        jdbc.queryForList(
                                "SELECT * FROM password_reset_tokens WHERE user_id = ? ORDER BY id",
                                userId);
        JsonNode originalState = objectMapper.valueToTree(protectedState.get());
        JsonNode originalTickets = objectMapper.valueToTree(tickets.get());

        long requestedAt = System.nanoTime();
        ResponseEntity<JsonNode> requested =
                restTemplate.postForEntity(
                        "/auth/password-reset/request", Map.of("login", "student"), JsonNode.class);
        assertThat(requested.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(requested.getBody() != null).isTrue();
        int ttlSeconds = requested.getBody().path("ttlSeconds").asInt();
        assertThat(ttlSeconds).isEqualTo(120); // Same existing policy in main and test profile.
        String challenge = requested.getBody().path("challengeId").asText();
        String challengeHash =
                java.util.HexFormat.of()
                        .formatHex(
                                java.security.MessageDigest.getInstance("SHA-256")
                                        .digest(
                                                challenge.getBytes(
                                                        java.nio.charset.StandardCharsets.UTF_8)));
        List<String> keys =
                List.of(
                        "password_reset:challenge:" + challengeHash + ":account",
                        "password_reset:challenge:" + challengeHash + ":user",
                        "password_reset:challenge:" + challengeHash + ":code");
        String code = redisTemplate.opsForValue().get(keys.get(2));
        assertThat(code != null && code.matches("\\d{6}")).isTrue();
        for (String key : keys) {
            Long remaining = redisTemplate.getExpire(key, TimeUnit.MILLISECONDS);
            assertThat(remaining != null && remaining > 0 && remaining <= ttlSeconds * 1000L)
                    .isTrue();
        }
        long deadline = requestedAt + TimeUnit.SECONDS.toNanos(ttlSeconds + 10L);
        // Observe real Redis wall-clock expiration; never DEL/EXPIRE or override TTL/Clock.
        while (keys.stream().anyMatch(key -> Boolean.TRUE.equals(redisTemplate.hasKey(key)))
                && System.nanoTime() < deadline) {
            Thread.sleep(200);
        }
        assertThat(
                        keys.stream()
                                .allMatch(
                                        key ->
                                                Long.valueOf(-2)
                                                        .equals(redisTemplate.getExpire(key))))
                .isTrue();
        ResponseEntity<JsonNode> expired =
                restTemplate.postForEntity(
                        "/auth/password-reset/verify",
                        Map.of("challengeId", challenge, "code", code),
                        JsonNode.class);
        assertThat(expired.getStatusCode()).isEqualTo(HttpStatus.GONE);
        assertThat(expired.getBody() != null).isTrue();
        assertThat(expired.getBody().path("extras").path("code").asText()).isEqualTo("OTP_EXPIRED");
        assertThat(expired.getHeaders().getCacheControl().split("\\s*,\\s*"))
                .containsOnly("no-store");
        assertThat(
                        expired.getBody().has("resetTicket")
                                || expired.getBody().has("accessToken")
                                || expired.getBody().has("refreshToken")
                                || expired.getBody().has("session"))
                .isFalse();
        assertThat(objectMapper.valueToTree(protectedState.get()).equals(originalState)).isTrue();
        assertThat(objectMapper.valueToTree(tickets.get()).equals(originalTickets)).isTrue();
        System.out.printf(
                "OTP_CHALLENGE_EXPIRY ttlSeconds=%d elapsedMs=%d keysAbsent=true httpStatus=410 code=OTP_EXPIRED stateUnchanged=true ticketDelta=0%n",
                ttlSeconds, TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - requestedAt));

        // Cooldown has naturally elapsed. A fresh valid verify is only the consume precondition.
        ResponseEntity<JsonNode> fresh =
                restTemplate.postForEntity(
                        "/auth/password-reset/request", Map.of("login", "student"), JsonNode.class);
        assertThat(fresh.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(fresh.getBody() != null).isTrue();
        String freshChallenge = fresh.getBody().path("challengeId").asText();
        String freshHash =
                java.util.HexFormat.of()
                        .formatHex(
                                java.security.MessageDigest.getInstance("SHA-256")
                                        .digest(
                                                freshChallenge.getBytes(
                                                        java.nio.charset.StandardCharsets.UTF_8)));
        String freshPrefix = "password_reset:challenge:" + freshHash;
        String freshCode = redisTemplate.opsForValue().get(freshPrefix + ":code");
        assertThat(freshCode != null && freshCode.matches("\\d{6}")).isTrue();
        Map<String, String> proof = Map.of("challengeId", freshChallenge, "code", freshCode);
        ResponseEntity<JsonNode> consumed =
                restTemplate.postForEntity("/auth/password-reset/verify", proof, JsonNode.class);
        assertThat(consumed.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(
                        consumed.getBody() != null
                                && consumed.getBody().path("resetTicket").isTextual()
                                && !consumed.getBody().path("resetTicket").asText().isBlank())
                .isTrue();
        assertThat(Long.valueOf(-2).equals(redisTemplate.getExpire(freshPrefix + ":code")))
                .isTrue();
        assertThat(
                        redisTemplate.getExpire(freshPrefix + ":account") > 0
                                && redisTemplate.getExpire(freshPrefix + ":user") > 0)
                .isTrue();
        JsonNode consumedTickets = objectMapper.valueToTree(tickets.get());
        assertThat(consumedTickets.size()).isEqualTo(originalTickets.size() + 1);
        assertThat(objectMapper.valueToTree(protectedState.get()).equals(originalState)).isTrue();

        ResponseEntity<JsonNode> reused =
                restTemplate.postForEntity("/auth/password-reset/verify", proof, JsonNode.class);
        assertThat(reused.getStatusCode()).isEqualTo(HttpStatus.GONE);
        assertThat(reused.getBody() != null).isTrue();
        assertThat(reused.getBody().path("extras").path("code").asText()).isEqualTo("OTP_EXPIRED");
        assertThat(reused.getHeaders().getCacheControl().split("\\s*,\\s*"))
                .containsOnly("no-store");
        assertThat(
                        reused.getBody().has("resetTicket")
                                || reused.getBody().has("accessToken")
                                || reused.getBody().has("refreshToken")
                                || reused.getBody().has("session"))
                .isFalse();
        assertThat(objectMapper.valueToTree(protectedState.get()).equals(originalState)).isTrue();
        assertThat(objectMapper.valueToTree(tickets.get()).equals(consumedTickets)).isTrue();
        System.out.println(
                "OTP_CHALLENGE_REUSE codeConsumed=true ownerAndUserLive=true httpStatus=410 code=OTP_EXPIRED stateUnchanged=true ticketDelta=0");
    }

    @Test
    @Timeout(value = 25, unit = TimeUnit.SECONDS)
    void passwordResetCompletesWhenTheAccountHasNoActiveSessions() throws Exception {
        assertThat(activeSessionCount()).isZero();

        ResponseEntity<JsonNode> request =
                restTemplate.postForEntity(
                        "/auth/password-reset/request", Map.of("login", "student"), JsonNode.class);
        assertThat(request.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        String challengeId = request.getBody().path("challengeId").asText();
        JsonNode event = objectMapper.readTree(receiveWithRetry(TEST_QUEUE).getBody());
        String code = event.path("payload").path("code").asText();

        ResponseEntity<JsonNode> verified =
                restTemplate.postForEntity(
                        "/auth/password-reset/verify",
                        Map.of("challengeId", challengeId, "code", code),
                        JsonNode.class);
        assertThat(verified.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(activeSessionCount()).isZero();
        String resetTicket = verified.getBody().path("resetTicket").asText();

        ResponseEntity<String> completed =
                restTemplate.postForEntity(
                        "/auth/password-reset/complete",
                        Map.of("resetTicket", resetTicket, "newPassword", "NoSessionPassword42!"),
                        String.class);
        assertThat(completed.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(completed.getHeaders().getCacheControl()).isEqualTo("no-store");
        assertThat(activeSessionCount()).isZero();

        ResponseEntity<String> replay =
                restTemplate.postForEntity(
                        "/auth/password-reset/complete",
                        Map.of("resetTicket", resetTicket, "newPassword", "NoSessionPassword42!"),
                        String.class);
        assertThat(replay.getStatusCode()).isEqualTo(HttpStatus.GONE);
        assertThat(loginStudent("NoSessionPassword42!").accessToken()).isNotBlank();
    }

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void adminRecoveryWithoutTelegramRestoresStudentAndTeacher() throws Exception {
        TokenResponse admin = loginAdmin();
        for (String login : List.of("student", "teacher")) {
            long id =
                    jdbc.queryForObject("SELECT id FROM users WHERE login = ?", Long.class, login);
            List<Map<String, Object>> identityBefore =
                    jdbc.queryForList(
                            "SELECT role, status, group_id FROM user_role_grants WHERE user_id = ? ORDER BY id",
                            id);
            jdbc.update(
                    "UPDATE users SET telegram_id = NULL, password_hash = ? WHERE id = ?",
                    PASSWORD_HASH,
                    id);
            TokenResponse old = loginUser(login, "password");
            String beforeHash =
                    jdbc.queryForObject(
                            "SELECT password_hash FROM users WHERE id = ?", String.class, id);
            String beforeStatus =
                    jdbc.queryForObject(
                            "SELECT status::text FROM users WHERE id = ?", String.class, id);
            try {
                // The public no-Telegram request still gives only an indistinguishable challenge.
                ResponseEntity<JsonNode> decoy =
                        restTemplate.postForEntity(
                                "/auth/password-reset/request",
                                Map.of("login", login),
                                JsonNode.class);
                assertThat(decoy.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
                assertThat(decoy.getBody().has("resetTicket")).isFalse();
                assertThat(rabbitTemplate.receive(TEST_QUEUE, 100)).isNull();

                ResponseEntity<JsonNode> issued = issueAdminRecovery(admin.accessToken(), id);
                assertThat(issued.getStatusCode()).isEqualTo(HttpStatus.CREATED);
                assertThat(issued.getHeaders().getCacheControl().split("\\s*,\\s*"))
                        .containsOnly("no-store");
                String link = issued.getBody().path("url").asText();
                assertThat(link)
                        .startsWith("https://recovery.example.test/password-reset#resetTicket=");
                assertThat(issued.getBody().path("expiresInSeconds").asInt()).isEqualTo(3600);
                String ticket = recoveryTicket(link);
                assertThat(ticket).matches("[A-Za-z0-9_-]{43}");
                assertThat(
                                jdbc.queryForObject(
                                        "SELECT token_hash FROM password_reset_tokens WHERE user_id = ? ORDER BY id DESC LIMIT 1",
                                        String.class,
                                        id))
                        .hasSize(64)
                        .isNotEqualTo(ticket);
                assertThat(
                                jdbc.queryForObject(
                                        "SELECT password_hash FROM users WHERE id = ?",
                                        String.class,
                                        id))
                        .isEqualTo(beforeHash);
                assertThat(bearerSessionStatus(old.accessToken())).isEqualTo(HttpStatus.OK);

                ResponseEntity<String> completed =
                        restTemplate.postForEntity(
                                "/auth/password-reset/complete",
                                Map.of(
                                        "resetTicket",
                                        ticket,
                                        "newPassword",
                                        "AdminRecoveryPassword42!"),
                                String.class);
                assertThat(completed.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
                assertThat(bearerSessionStatus(old.accessToken()))
                        .isEqualTo(HttpStatus.UNAUTHORIZED);
                assertThat(refreshStatus(old.refreshToken())).isEqualTo(HttpStatus.UNAUTHORIZED);
                assertThat(
                                restTemplate
                                        .postForEntity(
                                                "/auth/password-reset/complete",
                                                Map.of(
                                                        "resetTicket",
                                                        ticket,
                                                        "newPassword",
                                                        "AnotherRecoveryPassword42!"),
                                                String.class)
                                        .getStatusCode())
                        .isEqualTo(HttpStatus.GONE);
                assertThat(loginUser(login, "AdminRecoveryPassword42!").accessToken()).isNotBlank();
                assertThat(
                                jdbc.queryForObject(
                                        "SELECT status::text FROM users WHERE id = ?",
                                        String.class,
                                        id))
                        .isEqualTo(beforeStatus);
                assertThat(
                                jdbc.queryForList(
                                        "SELECT role, status, group_id FROM user_role_grants WHERE user_id = ? ORDER BY id",
                                        id))
                        .isEqualTo(identityBefore);
                assertThat(rabbitTemplate.receive(TEST_QUEUE, 100)).isNull();
            } finally {
                jdbc.update("UPDATE users SET password_hash = ? WHERE id = ?", PASSWORD_HASH, id);
                jdbc.update(
                        "UPDATE auth_sessions SET revoked_at = NOW(), revoke_reason = 'SECURITY_REVOKED' WHERE user_id = ? AND revoked_at IS NULL",
                        id);
                jdbc.update("DELETE FROM password_reset_tokens WHERE user_id = ?", id);
            }
        }
    }

    @Test
    @Timeout(value = 20, unit = TimeUnit.SECONDS)
    void adminRecoveryRejectsWrongRoleRevokedSessionAndExpiredTicket() {
        long targetId =
                jdbc.queryForObject("SELECT id FROM users WHERE login = 'student'", Long.class);
        TokenResponse student = loginStudent("password");
        assertThat(issueAdminRecovery(student.accessToken(), targetId).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT COUNT(*) FROM password_reset_tokens WHERE user_id = ?",
                                Integer.class,
                                targetId))
                .isZero();
        TokenResponse admin = loginAdmin();
        ResponseEntity<JsonNode> issued = issueAdminRecovery(admin.accessToken(), targetId);
        assertThat(issued.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String ticket = recoveryTicket(issued.getBody().path("url").asText());
        jdbc.update(
                "UPDATE password_reset_tokens SET expires_at = NOW() - INTERVAL '1 second' WHERE user_id = ?",
                targetId);
        assertThat(
                        restTemplate
                                .postForEntity(
                                        "/auth/password-reset/complete",
                                        Map.of(
                                                "resetTicket",
                                                ticket,
                                                "newPassword",
                                                "ExpiredRecoveryPassword42!"),
                                        String.class)
                                .getStatusCode())
                .isEqualTo(HttpStatus.GONE);
        assertThat(bearerSessionStatus(student.accessToken())).isEqualTo(HttpStatus.OK);
        jdbc.update(
                "UPDATE auth_sessions SET revoked_at = NOW(), revoke_reason = 'SECURITY_REVOKED' "
                        + "WHERE user_id = (SELECT id FROM users WHERE login = 'admin') AND revoked_at IS NULL");
        ResponseEntity<JsonNode> denied = issueAdminRecovery(admin.accessToken(), targetId);
        assertThat(denied.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(denied.getHeaders().getCacheControl().split("\\s*,\\s*"))
                .containsOnly("no-store");
    }

    private ResponseEntity<JsonNode> issueAdminRecovery(String token, long id) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.set("Host", "untrusted.example.test");
        return restTemplate.exchange(
                "/auth/admin/users/" + id + "/password-reset-link",
                HttpMethod.POST,
                new HttpEntity<>(headers),
                JsonNode.class);
    }

    private static String recoveryTicket(String link) {
        String fragment = java.net.URI.create(link).getRawFragment();
        return fragment.substring("resetTicket=".length(), fragment.indexOf('&'));
    }

    private long seedUnicodePasswordActor(String login, Long telegramId) {
        Long userId =
                new TransactionTemplate(transactionManager)
                        .execute(
                                status -> {
                                    Long id =
                                            jdbc.queryForObject(
                                                    """
                    INSERT INTO users (login, password_hash, last_name, first_name, role, status,
                        is_headman, initial_password, password_changed, telegram_id, created_at, updated_at)
                    VALUES (?, ?, 'Unicode', 'Boundary', CAST('student' AS user_role),
                        CAST('active' AS account_status), FALSE, 'password', FALSE, ?, NOW(), NOW())
                    RETURNING id
                    """,
                                                    Long.class,
                                                    login,
                                                    PASSWORD_HASH,
                                                    telegramId);
                                    jdbc.update(
                                            "INSERT INTO user_role_grants (user_id, role, status, created_at, updated_at) "
                                                    + "VALUES (?, 'student', 'active', NOW(), NOW())",
                                            id);
                                    return id;
                                });
        assertThat(userId != null).isTrue();
        return userId;
    }

    private void activateOnlyAdminGrant(long userId) {
        new TransactionTemplate(transactionManager)
                .executeWithoutResult(
                        status -> {
                            jdbc.queryForObject(
                                    "SELECT id FROM users WHERE id = ? FOR UPDATE",
                                    Long.class,
                                    userId);
                            assertThat(
                                            jdbc.update(
                                                    "UPDATE user_role_grants SET role = 'admin', updated_at = NOW() WHERE user_id = ?",
                                                    userId))
                                    .isEqualTo(1);
                            assertThat(
                                            jdbc.update(
                                                    "UPDATE users SET role = CAST('admin' AS user_role), roles_version = roles_version + 1, updated_at = NOW() WHERE id = ?",
                                                    userId))
                                    .isEqualTo(1);
                        });
    }

    private ResponseEntity<JsonNode> requestOwnedReset(Map<String, ?> identifier) {
        ResponseEntity<JsonNode> response =
                restTemplate.postForEntity(
                        "/auth/password-reset/request", identifier, JsonNode.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(hasNoStoreDirective(response.getHeaders().getCacheControl())).isTrue();
        assertThat(
                        response.getBody() != null
                                && response.getBody().size() == 2
                                && response.getBody().path("challengeId").asText().length() == 32
                                && response.getBody().path("ttlSeconds").asInt() == 120)
                .isTrue();
        return response;
    }

    private ResponseEntity<JsonNode> verifyOwnedReset(String challenge, String code) {
        return restTemplate.postForEntity(
                "/auth/password-reset/verify",
                Map.of("challengeId", challenge, "code", code),
                JsonNode.class);
    }

    private ResponseEntity<JsonNode> completeOwnedReset(String ticket) {
        return restTemplate.postForEntity(
                "/auth/password-reset/complete",
                Map.of("resetTicket", ticket, "newPassword", "AdminBoundaryReset42!"),
                JsonNode.class);
    }

    private String ownedResetCode(String challenge) throws Exception {
        String code =
                redisTemplate
                        .opsForValue()
                        .get("password_reset:challenge:" + resetChallengeHash(challenge) + ":code");
        assertThat(code != null && !code.isBlank()).isTrue();
        return code;
    }

    private String resetChallengeHash(String challenge) throws Exception {
        return HexFormat.of()
                .formatHex(
                        MessageDigest.getInstance("SHA-256")
                                .digest(challenge.getBytes(StandardCharsets.UTF_8)));
    }

    private ResponseEntity<JsonNode> unicodePasswordPost(
            String path, HttpHeaders headers, Map<String, String> body, boolean malformed)
            throws Exception {
        headers.setContentType(new MediaType("application", "json", StandardCharsets.UTF_8));
        String json = objectMapper.writeValueAsString(body);
        if (malformed) {
            // Send an actual escaped lone surrogate; UTF-8 conversion must not replace it first.
            assertThat(json.contains("\"newPassword\":\"unpaired\"")).isTrue();
            json =
                    json.replace(
                            "\"newPassword\":\"unpaired\"",
                            "\"newPassword\":\"abcdefghij\\u0661!\\uD800\"");
        }
        return restTemplate.exchange(
                path, HttpMethod.POST, new HttpEntity<>(json, headers), JsonNode.class);
    }

    private void assertUnicodePasswordRejection(
            ResponseEntity<JsonNode> response, String invalid, String proof) throws Exception {
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(hasNoStoreDirective(response.getHeaders().getCacheControl())).isTrue();
        assertThat(response.getBody() != null).isTrue();
        JsonNode body = response.getBody();
        String rendered = body.toString();
        assertThat(
                        !body.has("accessToken")
                                && !body.has("refreshToken")
                                && !body.has("resetTicket")
                                && !body.has("session"))
                .isTrue();
        // Comparisons stay in RAM. The legacy fixture password is a common diagnostic word,
        // so test its quoted value rather than rejecting legitimate prose containing that word.
        assertThat(
                        !rendered.contains(invalid)
                                && !rendered.contains(objectMapper.writeValueAsString(proof))
                                && !rendered.contains("abcdefghij\u0661!")
                                && !rendered.contains("\\uD800")
                                && rendered.indexOf((char) 0xD800) < 0)
                .isTrue();
    }

    private boolean hasNoStoreDirective(String cacheControl) {
        if (cacheControl == null) return false;
        boolean quoted = false;
        boolean escaped = false;
        boolean noStore = false;
        int start = 0;
        for (int index = 0; index < cacheControl.length(); index++) {
            char character = cacheControl.charAt(index);
            if (escaped) {
                escaped = false;
            } else if (quoted && character == '\\') {
                escaped = true;
            } else if (character == '"') {
                quoted = !quoted;
            } else if (!quoted && character == ',') {
                noStore |= "no-store".equalsIgnoreCase(cacheControl.substring(start, index).trim());
                start = index + 1;
            }
        }
        return !quoted
                && !escaped
                && (noStore || "no-store".equalsIgnoreCase(cacheControl.substring(start).trim()));
    }

    private List<Object> unicodePasswordState(long userId) {
        // Admission may update last_seen_at; failed-attempt audit/counters remain permitted.
        return List.of(
                jdbc.queryForList(
                        "SELECT password_hash, initial_password, password_changed, credentials_version "
                                + "FROM users WHERE id = ?",
                        userId),
                jdbc.queryForList(
                        "SELECT sid, active_role_grant_id, session_version, current_refresh_jti, "
                                + "previous_refresh_jti, refresh_expires_at, revoked_at, revoke_reason "
                                + "FROM auth_sessions WHERE user_id = ? ORDER BY sid",
                        userId),
                jdbc.queryForList(
                        "SELECT * FROM password_reset_tokens WHERE user_id = ? ORDER BY id",
                        userId),
                jdbc.queryForList(
                        "SELECT * FROM account_security_events WHERE user_id = ? "
                                + "AND event_type = 'PASSWORD_CHANGED' ORDER BY id",
                        userId),
                jdbc.queryForList(
                        "SELECT id, event_type, payload, created_at FROM auth_outbox WHERE event_type = 'password.changed' "
                                + "AND payload -> 'payload' ->> 'user_id' = ? ORDER BY id",
                        Long.toString(userId)));
    }

    private void assertUnicodePasswordCommitted(long userId, String exact, String different) {
        assertThat(
                        jdbc.queryForObject(
                                "SELECT initial_password IS NULL AND password_changed FROM users WHERE id = ?",
                                Boolean.class,
                                userId))
                .isTrue();
        String hash =
                jdbc.queryForObject(
                        "SELECT password_hash FROM users WHERE id = ?", String.class, userId);
        BCryptPasswordEncoder bcrypt = new BCryptPasswordEncoder();
        assertThat(
                        hash != null
                                && !hash.equals(exact)
                                && bcrypt.matches(exact, hash)
                                && !bcrypt.matches(different, hash))
                .isTrue();
        assertThat(
                        jdbc.queryForObject(
                                "SELECT COUNT(*) FROM auth_sessions WHERE user_id = ? AND revoked_at IS NULL",
                                Integer.class,
                                userId))
                .isZero();
        assertThat(
                        jdbc.queryForObject(
                                "SELECT COUNT(*) FROM account_security_events WHERE user_id = ? "
                                        + "AND event_type = 'PASSWORD_CHANGED'",
                                Integer.class,
                                userId))
                .isEqualTo(1);
    }

    private TokenResponse unicodePasswordLogin(String login, String password) {
        ResponseEntity<TokenResponse> response =
                restTemplate.postForEntity(
                        "/auth/login", new LoginRequest(login, password), TokenResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getCacheControl()).isEqualTo("no-store");
        assertThat(
                        response.getBody() != null
                                && response.getBody().accessToken() != null
                                && !response.getBody().accessToken().isBlank()
                                && response.getBody().refreshToken() != null
                                && !response.getBody().refreshToken().isBlank())
                .isTrue();
        assertThat(bearerSessionStatus(response.getBody().accessToken())).isEqualTo(HttpStatus.OK);
        return response.getBody();
    }

    private void assertUnicodePasswordLoginDenied(String login, String password) {
        long userId =
                jdbc.queryForObject("SELECT id FROM users WHERE login = ?", Long.class, login);
        List<Object> before = unicodePasswordState(userId);
        ResponseEntity<JsonNode> response =
                restTemplate.postForEntity(
                        "/auth/login", new LoginRequest(login, password), JsonNode.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getHeaders().getCacheControl()).isEqualTo("no-store");
        assertThat(
                        response.getBody() != null
                                && !response.getBody().has("accessToken")
                                && !response.getBody().has("refreshToken")
                                && !response.getBody().toString().contains("\"" + password + "\""))
                .isTrue();
        assertThat(unicodePasswordState(userId).equals(before)).isTrue();
    }

    private void cleanUnicodePasswordActors(Long... userIds) {
        // Audit is append-only and keeps user/session foreign keys. Retain those rows
        // for these unique actors until the fresh, non-reused PostgreSQL fixture is disposed.
        // Only mutable actor-owned intents are cleared here, including the global outbox count.
        RuntimeException cleanupFailure = null;
        for (Long userId : userIds) {
            if (userId == null) continue;
            try {
                new TransactionTemplate(transactionManager)
                        .executeWithoutResult(
                                status -> {
                                    jdbc.update(
                                            "DELETE FROM auth_outbox WHERE payload -> 'payload' ->> 'user_id' = ?",
                                            Long.toString(userId));
                                    jdbc.update(
                                            "DELETE FROM password_reset_tokens WHERE user_id = ?",
                                            userId);
                                });
            } catch (RuntimeException failure) {
                if (cleanupFailure == null) cleanupFailure = failure;
                else cleanupFailure.addSuppressed(failure);
            }
        }
        if (cleanupFailure != null) throw cleanupFailure;
    }

    private TokenResponse loginStudent(String password) {
        return loginUser("student", password);
    }

    private TokenResponse loginAdmin() {
        TokenResponse bootstrap = loginUser("admin", "password");
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(bootstrap.accessToken());
        ResponseEntity<JsonNode> selected =
                restTemplate.exchange(
                        "/auth/session/active-role",
                        HttpMethod.PUT,
                        new HttpEntity<>(
                                Map.of("role", "ADMIN", "expectedSessionVersion", "1"), headers),
                        JsonNode.class);
        assertThat(selected.getStatusCode()).isEqualTo(HttpStatus.OK);
        return new TokenResponse(
                selected.getBody().path("accessToken").asText(),
                bootstrap.refreshToken(),
                selected.getBody().path("expiresIn").asLong());
    }

    private TokenResponse loginUser(String login, String password) {
        ResponseEntity<TokenResponse> response =
                restTemplate.postForEntity(
                        "/auth/login", new LoginRequest(login, password), TokenResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        return response.getBody();
    }

    private int activeSessionCount() {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM auth_sessions "
                        + "WHERE user_id = (SELECT id FROM users WHERE login = 'student') "
                        + "AND revoked_at IS NULL",
                Integer.class);
    }

    private HttpStatusCode bearerSessionStatus(String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        return restTemplate
                .exchange("/auth/session", HttpMethod.GET, new HttpEntity<>(headers), String.class)
                .getStatusCode();
    }

    private HttpStatusCode refreshStatus(String refreshToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.COOKIE, "rct_refresh=" + refreshToken);
        return restTemplate
                .exchange("/auth/refresh", HttpMethod.POST, new HttpEntity<>(headers), String.class)
                .getStatusCode();
    }

    private Message receiveWithRetry(String queue) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 8000L;
        while (System.currentTimeMillis() < deadline) {
            Message msg = rabbitTemplate.receive(queue, 500);
            if (msg != null) return msg;
            Thread.sleep(100);
        }
        return null;
    }

    @DynamicPropertySource
    static void overrideRabbitProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.rabbitmq.host", RABBITMQ::getHost);
        registry.add("spring.rabbitmq.port", () -> RABBITMQ.getMappedPort(5672));
        registry.add("spring.rabbitmq.username", RABBITMQ::getAdminUsername);
        registry.add("spring.rabbitmq.password", RABBITMQ::getAdminPassword);
        registry.add("tma.bot-token", () -> "test_bot_token_12345");
        registry.add("tma.auth-date-max-age-seconds", () -> "86400");
    }
}
