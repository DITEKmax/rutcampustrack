package ru.rutcampustrack.auth.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.jdbc.Sql;
import org.testcontainers.containers.RabbitMQContainer;
import ru.rutcampustrack.auth.dto.LoginRequest;
import ru.rutcampustrack.auth.dto.OtpRequest;
import ru.rutcampustrack.auth.dto.TokenResponse;
import ru.rutcampustrack.auth.events.EventSchemaValidator;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * M09 G2 (08 P0-2) — AuthOtpFlowIT.
 *
 * <p>Проверяет сквозной flow OTP:
 * <ol>
 *   <li>{@code POST /auth/otp/request} → 204 без тела (код не утекает в HTTP)</li>
 *   <li>Код лежит в Redis по ключу {@code otp:<telegramId>}</li>
 *   <li>RabbitMQ получает {@code otp.requested} event с тем же кодом</li>
 * </ol>
 *
 * <p>Uses the canonical auth integration fixture (Academic V1..V24 + Redis)
 * and adds RabbitMQ for the real OTP and password-change event assertions.
 */
@TestPropertySource(properties = {
        // Перекрываем exclude из application-test.yml — нужен RabbitTemplate.
        "spring.autoconfigure.exclude=",
        "auth.password-reset-url=https://recovery.example.test/password-reset"
})
@Sql(scripts = "classpath:sql/set-telegram-id.sql")
@Sql(scripts = "classpath:sql/clear-telegram-id.sql", executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
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

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private AmqpAdmin amqpAdmin;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbc;
    private long passwordChangedEventCountBeforeTest;

    @BeforeEach
    void cleanOtpRedisKeys() {
        Set<String> keys = redisTemplate.keys("otp*");
        if (keys != null && !keys.isEmpty()) redisTemplate.delete(keys);
        keys = redisTemplate.keys("password_reset:*");
        if (keys != null && !keys.isEmpty()) redisTemplate.delete(keys);

        Long userId = jdbc.queryForObject("SELECT id FROM users WHERE login = 'student'", Long.class);
        jdbc.update("UPDATE auth_sessions SET revoked_at = NOW(), revoke_reason = 'SECURITY_REVOKED' "
                + "WHERE user_id = ? AND revoked_at IS NULL", userId);
        jdbc.update("DELETE FROM password_reset_tokens WHERE user_id = ?", userId);
        passwordChangedEventCountBeforeTest = jdbc.queryForObject(
                "SELECT COUNT(*) FROM account_security_events WHERE user_id = ? "
                        + "AND event_type = 'PASSWORD_CHANGED'", Long.class, userId);
        jdbc.update("UPDATE users SET password_hash = ?, password_changed = false, "
                + "initial_password = NULL, telegram_id = ? WHERE id = ?",
                PASSWORD_HASH, TELEGRAM_ID, userId);

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
        ResponseEntity<Void> response = restTemplate.postForEntity(
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
    void passwordResetIsPurposeBoundAtomicAndRevokesEverySession() throws Exception {
        TokenResponse firstSession = loginStudent("password");
        TokenResponse secondSession = loginStudent("password");
        assertThat(activeSessionCount()).isEqualTo(2);

        ResponseEntity<String> invalidRequest = restTemplate.postForEntity(
                "/auth/password-reset/request",
                Map.of("login", "student", "telegramId", TELEGRAM_ID), String.class);
        assertThat(invalidRequest.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(invalidRequest.getHeaders().getCacheControl().split("\\s*,\\s*")).containsOnly("no-store");

        ResponseEntity<JsonNode> request = restTemplate.postForEntity(
                "/auth/password-reset/request", Map.of("login", "student"), JsonNode.class);
        assertThat(request.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(request.getHeaders().getCacheControl().split("\\s*,\\s*")).containsOnly("no-store");
        assertThat(request.getBody()).isNotNull();
        String challengeId = request.getBody().path("challengeId").asText();
        assertThat(challengeId).hasSize(32);
        assertThat(request.getBody().path("ttlSeconds").asInt()).isEqualTo(120);
        assertThat(request.getBody().has("attemptsRemaining")).isFalse();
        assertThat(request.getBody().has("code")).isFalse();

        ResponseEntity<JsonNode> unknown = restTemplate.postForEntity(
                "/auth/password-reset/request", Map.of("login", "unknown-reset-user"), JsonNode.class);
        assertThat(unknown.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(unknown.getHeaders().getCacheControl().split("\\s*,\\s*")).containsOnly("no-store");
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
        ResponseEntity<JsonNode> botAlias = restTemplate.postForEntity(
                "/auth/password-reset/request", Map.of("telegramId", TELEGRAM_ID), JsonNode.class);
        assertThat(botAlias.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(botAlias.getBody()).isNotNull();
        assertThat(botAlias.getBody().has("attemptsRemaining")).isFalse();
        assertThat(rabbitTemplate.receive(TEST_QUEUE, 100)).isNull();

        // A reset-purpose code cannot enter the login OTP route or create a session.
        ResponseEntity<String> loginVerify = restTemplate.postForEntity(
                "/auth/otp/verify", Map.of("telegramId", TELEGRAM_ID, "code", code), String.class);
        assertThat(loginVerify.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(activeSessionCount()).isEqualTo(2);

        ResponseEntity<JsonNode> verified = restTemplate.postForEntity(
                "/auth/password-reset/verify",
                Map.of("challengeId", challengeId, "code", code), JsonNode.class);
        assertThat(verified.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(verified.getHeaders().getCacheControl().split("\\s*,\\s*")).containsOnly("no-store");
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
            Future<ResponseEntity<String>> first = executor.submit(() -> {
                start.await(10, TimeUnit.SECONDS);
                return restTemplate.postForEntity("/auth/password-reset/complete",
                        Map.of("resetTicket", resetTicket, "newPassword", "NewStrongPassword42!"),
                        String.class);
            });
            Future<ResponseEntity<String>> second = executor.submit(() -> {
                start.await(10, TimeUnit.SECONDS);
                return restTemplate.postForEntity("/auth/password-reset/complete",
                        Map.of("resetTicket", resetTicket, "newPassword", "NewStrongPassword42!"),
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

        Message passwordChangedMessage = receiveWithRetry(TEST_QUEUE);
        assertThat(passwordChangedMessage)
                .as("после committed reset отправляется password.changed")
                .isNotNull();
        String passwordChangedJson = new String(passwordChangedMessage.getBody(), java.nio.charset.StandardCharsets.UTF_8);
        JsonNode passwordChanged = objectMapper.readTree(passwordChangedJson);
        assertThat(EventSchemaValidator.validate("password.changed.json", passwordChangedJson))
                .as("password.changed event соответствует публичной schema")
                .isEmpty();
        assertThat(passwordChanged.path("event_type").asText()).isEqualTo("password.changed");
        JsonNode changedPayload = passwordChanged.path("payload");
        assertThat(changedPayload.path("telegram_id").asLong()).isEqualTo(TELEGRAM_ID);
        assertThat(changedPayload.size()).isEqualTo(1);
        assertThat(changedPayload.has("password")).isFalse();
        assertThat(changedPayload.has("reset_ticket")).isFalse();
        assertThat(changedPayload.has("code")).isFalse();
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM account_security_events WHERE user_id = "
                        + "(SELECT id FROM users WHERE login = 'student') AND event_type = 'PASSWORD_CHANGED'",
                Long.class)).isEqualTo(passwordChangedEventCountBeforeTest + 1);
        assertThat(rabbitTemplate.receive(TEST_QUEUE, 100)).isNull();

        assertThat(activeSessionCount()).isZero();
        assertThat(bearerSessionStatus(firstSession.accessToken())).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(bearerSessionStatus(secondSession.accessToken())).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(refreshStatus(firstSession.refreshToken())).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(loginStudent("NewStrongPassword42!").accessToken()).isNotBlank();
    }

    @Test
    @Timeout(value = 25, unit = TimeUnit.SECONDS)
    void passwordResetFailedAttemptsSurviveANewChallenge() throws Exception {
        assertThat(activeSessionCount()).isZero();

        ResponseEntity<JsonNode> request = restTemplate.postForEntity(
                "/auth/password-reset/request", Map.of("telegramId", TELEGRAM_ID), JsonNode.class);
        assertThat(request.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        String challengeId = request.getBody().path("challengeId").asText();
        JsonNode event = objectMapper.readTree(receiveWithRetry(TEST_QUEUE).getBody());
        JsonNode payload = event.path("payload");
        String code = payload.path("code").asText();
        String wrongCode = String.format("%06d", (Integer.parseInt(code) + 1) % 1_000_000);

        for (int attempt = 1; attempt <= 3; attempt++) {
            ResponseEntity<String> rejected = restTemplate.postForEntity(
                    "/auth/password-reset/verify",
                    Map.of("challengeId", challengeId, "code", wrongCode), String.class);
            assertThat(rejected.getStatusCode()).isEqualTo(
                    attempt == 3 ? HttpStatus.TOO_MANY_REQUESTS : HttpStatus.BAD_REQUEST);
        }

        Set<String> cooldownKeys = redisTemplate.keys("password_reset:request_cooldown:*");
        if (cooldownKeys != null && !cooldownKeys.isEmpty()) {
            redisTemplate.delete(cooldownKeys);
        }
        ResponseEntity<JsonNode> nextRequest = restTemplate.postForEntity(
                "/auth/password-reset/request", Map.of("login", "student"), JsonNode.class);
        assertThat(nextRequest.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(nextRequest.getBody()).isNotNull();
        assertThat(nextRequest.getBody().has("attemptsRemaining")).isFalse();
        assertThat(rabbitTemplate.receive(TEST_QUEUE, 100)).isNull();

        ResponseEntity<String> limited = restTemplate.postForEntity(
                "/auth/password-reset/verify",
                Map.of("challengeId", nextRequest.getBody().path("challengeId").asText(),
                        "code", wrongCode), String.class);
        assertThat(limited.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);

        String freshChallengeId = nextRequest.getBody().path("challengeId").asText();
        ResponseEntity<String> noActiveSession = restTemplate.postForEntity(
                "/auth/password-reset/verify",
                Map.of("challengeId", freshChallengeId, "code", payload.path("code").asText()),
                String.class);
        assertThat(noActiveSession.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(activeSessionCount()).isZero();
    }

    @Test
    @Timeout(value = 25, unit = TimeUnit.SECONDS)
    void passwordResetCompletesWhenTheAccountHasNoActiveSessions() throws Exception {
        assertThat(activeSessionCount()).isZero();

        ResponseEntity<JsonNode> request = restTemplate.postForEntity(
                "/auth/password-reset/request", Map.of("login", "student"), JsonNode.class);
        assertThat(request.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        String challengeId = request.getBody().path("challengeId").asText();
        JsonNode event = objectMapper.readTree(receiveWithRetry(TEST_QUEUE).getBody());
        String code = event.path("payload").path("code").asText();

        ResponseEntity<JsonNode> verified = restTemplate.postForEntity(
                "/auth/password-reset/verify",
                Map.of("challengeId", challengeId, "code", code), JsonNode.class);
        assertThat(verified.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(activeSessionCount()).isZero();
        String resetTicket = verified.getBody().path("resetTicket").asText();

        ResponseEntity<String> completed = restTemplate.postForEntity(
                "/auth/password-reset/complete",
                Map.of("resetTicket", resetTicket, "newPassword", "NoSessionPassword42!"), String.class);
        assertThat(completed.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(completed.getHeaders().getCacheControl()).isEqualTo("no-store");
        assertThat(activeSessionCount()).isZero();

        ResponseEntity<String> replay = restTemplate.postForEntity(
                "/auth/password-reset/complete",
                Map.of("resetTicket", resetTicket, "newPassword", "NoSessionPassword42!"), String.class);
        assertThat(replay.getStatusCode()).isEqualTo(HttpStatus.GONE);
        assertThat(loginStudent("NoSessionPassword42!").accessToken()).isNotBlank();
    }

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void adminRecoveryWithoutTelegramRestoresStudentAndTeacher() throws Exception {
        TokenResponse admin = loginAdmin();
        for (String login : List.of("student", "teacher")) {
            long id = jdbc.queryForObject("SELECT id FROM users WHERE login = ?", Long.class, login);
            List<Map<String, Object>> identityBefore = jdbc.queryForList(
                    "SELECT role, status, group_id FROM user_role_grants WHERE user_id = ? ORDER BY id", id);
            jdbc.update("UPDATE users SET telegram_id = NULL, password_hash = ? WHERE id = ?", PASSWORD_HASH, id);
            TokenResponse old = loginUser(login, "password");
            String beforeHash = jdbc.queryForObject("SELECT password_hash FROM users WHERE id = ?", String.class, id);
            String beforeStatus = jdbc.queryForObject("SELECT status::text FROM users WHERE id = ?", String.class, id);
            try {
                // The public no-Telegram request still gives only an indistinguishable challenge.
                ResponseEntity<JsonNode> decoy = restTemplate.postForEntity("/auth/password-reset/request",
                        Map.of("login", login), JsonNode.class);
                assertThat(decoy.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
                assertThat(decoy.getBody().has("resetTicket")).isFalse();
                assertThat(rabbitTemplate.receive(TEST_QUEUE, 100)).isNull();

                ResponseEntity<JsonNode> issued = issueAdminRecovery(admin.accessToken(), id);
                assertThat(issued.getStatusCode()).isEqualTo(HttpStatus.CREATED);
                assertThat(issued.getHeaders().getCacheControl().split("\\s*,\\s*")).containsOnly("no-store");
                String link = issued.getBody().path("url").asText();
                assertThat(link).startsWith("https://recovery.example.test/password-reset#resetTicket=");
                assertThat(issued.getBody().path("expiresInSeconds").asInt()).isEqualTo(120);
                String ticket = recoveryTicket(link);
                assertThat(ticket).matches("[A-Za-z0-9_-]{43}");
                assertThat(jdbc.queryForObject("SELECT token_hash FROM password_reset_tokens WHERE user_id = ? ORDER BY id DESC LIMIT 1",
                        String.class, id)).hasSize(64).isNotEqualTo(ticket);
                assertThat(jdbc.queryForObject("SELECT password_hash FROM users WHERE id = ?", String.class, id)).isEqualTo(beforeHash);
                assertThat(bearerSessionStatus(old.accessToken())).isEqualTo(HttpStatus.OK);

                ResponseEntity<String> completed = restTemplate.postForEntity("/auth/password-reset/complete",
                        Map.of("resetTicket", ticket, "newPassword", "AdminRecoveryPassword42!"), String.class);
                assertThat(completed.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
                assertThat(bearerSessionStatus(old.accessToken())).isEqualTo(HttpStatus.UNAUTHORIZED);
                assertThat(refreshStatus(old.refreshToken())).isEqualTo(HttpStatus.UNAUTHORIZED);
                assertThat(restTemplate.postForEntity("/auth/password-reset/complete",
                        Map.of("resetTicket", ticket, "newPassword", "AnotherRecoveryPassword42!"), String.class)
                        .getStatusCode()).isEqualTo(HttpStatus.GONE);
                assertThat(loginUser(login, "AdminRecoveryPassword42!").accessToken()).isNotBlank();
                assertThat(jdbc.queryForObject("SELECT status::text FROM users WHERE id = ?", String.class, id)).isEqualTo(beforeStatus);
                assertThat(jdbc.queryForList("SELECT role, status, group_id FROM user_role_grants WHERE user_id = ? ORDER BY id", id))
                        .isEqualTo(identityBefore);
                assertThat(rabbitTemplate.receive(TEST_QUEUE, 100)).isNull();
            } finally {
                jdbc.update("UPDATE users SET password_hash = ? WHERE id = ?", PASSWORD_HASH, id);
                jdbc.update("UPDATE auth_sessions SET revoked_at = NOW(), revoke_reason = 'SECURITY_REVOKED' WHERE user_id = ? AND revoked_at IS NULL", id);
                jdbc.update("DELETE FROM password_reset_tokens WHERE user_id = ?", id);
            }
        }
    }

    @Test
    @Timeout(value = 20, unit = TimeUnit.SECONDS)
    void adminRecoveryRejectsWrongRoleRevokedSessionAndExpiredTicket() {
        long targetId = jdbc.queryForObject("SELECT id FROM users WHERE login = 'student'", Long.class);
        TokenResponse student = loginStudent("password");
        assertThat(issueAdminRecovery(student.accessToken(), targetId).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM password_reset_tokens WHERE user_id = ?", Integer.class, targetId)).isZero();
        TokenResponse admin = loginAdmin();
        ResponseEntity<JsonNode> issued = issueAdminRecovery(admin.accessToken(), targetId);
        assertThat(issued.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String ticket = recoveryTicket(issued.getBody().path("url").asText());
        jdbc.update("UPDATE password_reset_tokens SET expires_at = NOW() - INTERVAL '1 second' WHERE user_id = ?", targetId);
        assertThat(restTemplate.postForEntity("/auth/password-reset/complete",
                Map.of("resetTicket", ticket, "newPassword", "ExpiredRecoveryPassword42!"), String.class)
                .getStatusCode()).isEqualTo(HttpStatus.GONE);
        assertThat(bearerSessionStatus(student.accessToken())).isEqualTo(HttpStatus.OK);
        jdbc.update("UPDATE auth_sessions SET revoked_at = NOW(), revoke_reason = 'SECURITY_REVOKED' "
                + "WHERE user_id = (SELECT id FROM users WHERE login = 'admin') AND revoked_at IS NULL");
        ResponseEntity<JsonNode> denied = issueAdminRecovery(admin.accessToken(), targetId);
        assertThat(denied.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(denied.getHeaders().getCacheControl().split("\\s*,\\s*")).containsOnly("no-store");
    }

    private ResponseEntity<JsonNode> issueAdminRecovery(String token, long id) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.set("Host", "untrusted.example.test");
        return restTemplate.exchange("/auth/admin/users/" + id + "/password-reset-link", HttpMethod.POST,
                new HttpEntity<>(headers), JsonNode.class);
    }

    private static String recoveryTicket(String link) {
        String fragment = java.net.URI.create(link).getRawFragment();
        return fragment.substring("resetTicket=".length(), fragment.indexOf('&'));
    }

    private TokenResponse loginStudent(String password) {
        return loginUser("student", password);
    }

    private TokenResponse loginAdmin() {
        TokenResponse bootstrap = loginUser("admin", "password");
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(bootstrap.accessToken());
        ResponseEntity<JsonNode> selected = restTemplate.exchange("/auth/session/active-role", HttpMethod.PUT,
                new HttpEntity<>(Map.of("role", "ADMIN", "expectedSessionVersion", "1"), headers), JsonNode.class);
        assertThat(selected.getStatusCode()).isEqualTo(HttpStatus.OK);
        return new TokenResponse(selected.getBody().path("accessToken").asText(), bootstrap.refreshToken(),
                selected.getBody().path("expiresIn").asLong());
    }

    private TokenResponse loginUser(String login, String password) {
        ResponseEntity<TokenResponse> response = restTemplate.postForEntity(
                "/auth/login", new LoginRequest(login, password), TokenResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        return response.getBody();
    }

    private int activeSessionCount() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM auth_sessions "
                + "WHERE user_id = (SELECT id FROM users WHERE login = 'student') "
                + "AND revoked_at IS NULL", Integer.class);
    }

    private HttpStatusCode bearerSessionStatus(String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        return restTemplate.exchange("/auth/session", HttpMethod.GET,
                new HttpEntity<>(headers), String.class).getStatusCode();
    }

    private HttpStatusCode refreshStatus(String refreshToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.COOKIE, "rct_refresh=" + refreshToken);
        return restTemplate.exchange("/auth/refresh", HttpMethod.POST,
                new HttpEntity<>(headers), String.class).getStatusCode();
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
