package ru.rutcampustrack.auth.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.context.ApplicationEventPublisher;
import ru.rutcampustrack.auth.config.OtpProperties;
import ru.rutcampustrack.auth.dto.PasswordResetRequest;
import ru.rutcampustrack.auth.dto.PasswordResetRequestResponse;
import ru.rutcampustrack.auth.dto.PasswordResetVerifyRequest;
import ru.rutcampustrack.auth.dto.PasswordResetVerifyResponse;
import ru.rutcampustrack.auth.entity.User;
import ru.rutcampustrack.auth.event.PasswordChangedEvent;
import ru.rutcampustrack.auth.event.OtpRequestedEvent;
import ru.rutcampustrack.auth.exception.PasswordResetException;
import ru.rutcampustrack.auth.session.AuthSessionException;
import ru.rutcampustrack.auth.session.SessionLifecycleService;
import ru.rutcampustrack.auth.session.port.CredentialSessionTransactionPort;
import ru.rutcampustrack.auth.repository.UserRepository;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** Purpose-isolated Telegram OTP and one-use reset-ticket flow. */
@Service
public final class PasswordResetService {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetService.class);
    private static final long PROOF_EXPIRED = 0L;
    private static final long PROOF_CONSUMED = 1L;
    private static final long PROOF_LIMITED = 2L;
    private static final long PROOF_INVALID = 3L;

    private static final String REQUEST_BUDGET_SCRIPT_TEXT = """
            local sent = tonumber(redis.call('GET', KEYS[1]) or '0')
            if sent >= tonumber(ARGV[1]) or redis.call('EXISTS', KEYS[2]) == 1 then
                return 0
            end
            sent = redis.call('INCR', KEYS[1])
            if sent == 1 then
                redis.call('EXPIRE', KEYS[1], tonumber(ARGV[2]))
            end
            redis.call('SET', KEYS[2], '1', 'EX', tonumber(ARGV[3]))
            return 1
            """;

    private static final String STORE_REAL_CHALLENGE_SCRIPT_TEXT = """
            local failures = tonumber(redis.call('GET', KEYS[5]) or '0')
            if failures >= tonumber(ARGV[1]) then
                redis.call('SET', KEYS[1], ARGV[3], 'EX', tonumber(ARGV[4]))
                redis.call('SET', KEYS[2], '0', 'EX', tonumber(ARGV[4]))
                redis.call('SET', KEYS[3], ARGV[5], 'EX', tonumber(ARGV[4]))
                return '0'
            end
            local previous = redis.call('GET', KEYS[4])
            if previous then
                redis.call('DEL', ARGV[7] .. previous .. ':code')
            end
            redis.call('SET', KEYS[1], ARGV[3], 'EX', tonumber(ARGV[4]))
            redis.call('SET', KEYS[2], ARGV[2], 'EX', tonumber(ARGV[4]))
            redis.call('SET', KEYS[3], ARGV[5], 'EX', tonumber(ARGV[4]))
            redis.call('SET', KEYS[4], ARGV[6], 'EX', tonumber(ARGV[4]))
            return tostring(tonumber(ARGV[1]) - failures)
            """;

    private static final String STORE_DECOY_CHALLENGE_SCRIPT_TEXT = """
            redis.call('SET', KEYS[1], ARGV[1], 'EX', tonumber(ARGV[3]))
            redis.call('SET', KEYS[2], '0', 'EX', tonumber(ARGV[3]))
            redis.call('SET', KEYS[3], ARGV[2], 'EX', tonumber(ARGV[3]))
            return 1
            """;

    private static final String RECORD_MISMATCH_SCRIPT_TEXT = """
            local account = redis.call('GET', KEYS[1])
            local user = redis.call('GET', KEYS[2])
            local storedCode = redis.call('GET', KEYS[3])
            if not account or not user or not storedCode or account ~= ARGV[1] then
                return '0:0:0'
            end
            if user ~= '0' and redis.call('GET', KEYS[4]) ~= ARGV[2] then
                return '0:0:0'
            end
            local failures = tonumber(redis.call('GET', KEYS[5]) or '0')
            local limit = tonumber(ARGV[3])
            if failures >= limit then
                return '2:0:' .. tostring(redis.call('TTL', KEYS[5]))
            end
            failures = redis.call('INCR', KEYS[5])
            if failures == 1 then
                redis.call('EXPIRE', KEYS[5], tonumber(ARGV[4]))
            end
            local remaining = math.max(0, limit - failures)
            local ttl = redis.call('TTL', KEYS[5])
            if remaining == 0 then
                redis.call('DEL', KEYS[3])
                if user ~= '0' and redis.call('GET', KEYS[4]) == ARGV[2] then
                    redis.call('DEL', KEYS[4])
                end
                return '2:' .. tostring(remaining) .. ':' .. tostring(ttl)
            end
            return '1:' .. tostring(remaining) .. ':' .. tostring(ttl)
            """;

    private static final String CONSUME_CHALLENGE_SCRIPT_TEXT = """
            local account = redis.call('GET', KEYS[1])
            local user = redis.call('GET', KEYS[2])
            local storedCode = redis.call('GET', KEYS[3])
            if not account or not user or not storedCode or account ~= ARGV[1]
                    or storedCode ~= ARGV[3] then
                return '0:0:0'
            end
            if user == '0' or redis.call('GET', KEYS[4]) ~= ARGV[2] then
                return '3:0:0'
            end
            local failures = tonumber(redis.call('GET', KEYS[5]) or '0')
            if failures >= tonumber(ARGV[4]) then
                return '2:0:' .. tostring(redis.call('TTL', KEYS[5]))
            end
            local attemptsRemaining = math.max(0, tonumber(ARGV[4]) - failures)
            redis.call('DEL', KEYS[3], KEYS[4])
            return '1:' .. tostring(attemptsRemaining) .. ':0'
            """;

    private final StringRedisTemplate redisTemplate;
    private final OtpProperties otpProperties;
    private final UserRepository userRepository;
    private final SessionLifecycleService sessionLifecycle;
    private final AuthService authService;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;
    private final SecureRandom secureRandom = new SecureRandom();
    private final DefaultRedisScript<Long> requestBudgetScript =
            new DefaultRedisScript<>(REQUEST_BUDGET_SCRIPT_TEXT, Long.class);
    private final DefaultRedisScript<String> storeRealChallengeScript =
            new DefaultRedisScript<>(STORE_REAL_CHALLENGE_SCRIPT_TEXT, String.class);
    private final DefaultRedisScript<Long> storeDecoyChallengeScript =
            new DefaultRedisScript<>(STORE_DECOY_CHALLENGE_SCRIPT_TEXT, Long.class);
    private final DefaultRedisScript<String> recordMismatchScript =
            new DefaultRedisScript<>(RECORD_MISMATCH_SCRIPT_TEXT, String.class);
    private final DefaultRedisScript<String> consumeChallengeScript =
            new DefaultRedisScript<>(CONSUME_CHALLENGE_SCRIPT_TEXT, String.class);

    @Autowired
    public PasswordResetService(
            StringRedisTemplate redisTemplate,
            OtpProperties otpProperties,
            UserRepository userRepository,
            SessionLifecycleService sessionLifecycle,
            AuthService authService,
            ApplicationEventPublisher eventPublisher
    ) {
        this(redisTemplate, otpProperties, userRepository, sessionLifecycle, authService,
                eventPublisher, Clock.systemUTC());
    }

    PasswordResetService(
            StringRedisTemplate redisTemplate,
            OtpProperties otpProperties,
            UserRepository userRepository,
            SessionLifecycleService sessionLifecycle,
            AuthService authService,
            ApplicationEventPublisher eventPublisher,
            Clock clock
    ) {
        this.redisTemplate = Objects.requireNonNull(redisTemplate, "redisTemplate");
        this.otpProperties = Objects.requireNonNull(otpProperties, "otpProperties");
        this.userRepository = Objects.requireNonNull(userRepository, "userRepository");
        this.sessionLifecycle = Objects.requireNonNull(sessionLifecycle, "sessionLifecycle");
        this.authService = Objects.requireNonNull(authService, "authService");
        this.eventPublisher = Objects.requireNonNull(eventPublisher, "eventPublisher");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public PasswordResetRequestResponse request(PasswordResetRequest request) {
        Objects.requireNonNull(request, "request");
        if (!request.isExactlyOneIdentifier()) {
            throw new IllegalArgumentException("exactly one recovery identifier is required");
        }
        User requestedUser = findUserForReset(request);
        String accountKey = requestedUser != null && requestedUser.getId() != null
                ? accountKeyForUser(requestedUser.getId())
                : unknownAccountKey(request);
        String challengeId = randomToken(24);
        String challengeHash = sha256(challengeId);
        String code = newCode();
        int ttlSeconds = otpProperties.ttlSeconds();

        Long budgetAccepted = execute(requestBudgetScript,
                List.of(requestWindowKey(accountKey), requestCooldownKey(accountKey)),
                otpProperties.maxAttempts(), otpProperties.attemptsWindowSeconds(),
                Math.max(1, otpProperties.resendCooldownSeconds()));

        if (budgetAccepted == null || budgetAccepted != 1L) {
            return new PasswordResetRequestResponse(challengeId, ttlSeconds);
        }

        User user = requestedUser != null
                && requestedUser.getId() != null
                && requestedUser.getTelegramId() != null
                && requestedUser.getTelegramId() > 0
                ? requestedUser
                : null;
        if (user == null) {
            storeDecoyChallenge(challengeHash, accountKey, code, ttlSeconds);
            return new PasswordResetRequestResponse(challengeId, ttlSeconds);
        }

        long telegramId = user.getTelegramId();
        int attemptsRemaining = storeRealChallenge(
                challengeHash, accountKey, user.getId(), code, ttlSeconds);
        if (attemptsRemaining > 0) {
            try {
                eventPublisher.publishEvent(new OtpRequestedEvent(
                        this, telegramId, code, ttlSeconds,
                        "password_reset", challengeId, attemptsRemaining));
            } catch (RuntimeException exception) {
                // The public response remains indistinguishable for known and unknown IDs.
                log.warn("Password-reset OTP notification could not be published ({})",
                        exception.getClass().getSimpleName());
            }
        }
        return new PasswordResetRequestResponse(challengeId, ttlSeconds);
    }

    public PasswordResetVerifyResponse verify(PasswordResetVerifyRequest request) {
        Objects.requireNonNull(request, "request");
        String challengeHash = sha256(request.challengeId());
        String ownerKey = challengeAccountKey(challengeHash);
        String userKey = challengeUserKey(challengeHash);
        String codeKey = challengeCodeKey(challengeHash);
        String accountKey = redisValue(ownerKey);
        String userIdValue = redisValue(userKey);
        String storedCode = redisValue(codeKey);
        if (accountKey == null || userIdValue == null || storedCode == null) {
            throw otpExpired();
        }

        if (!constantTimeCodeEquals(storedCode, request.code())) {
            throw recordMismatch(challengeHash, accountKey);
        }

        long userId = parseUserId(userIdValue);
        if (userId <= 0) {
            // Decoy challenges for unknown accounts have the same response shape as a bad code.
            throw recordMismatch(challengeHash, accountKey);
        }

        String consumed = execute(consumeChallengeScript,
                List.of(ownerKey, userKey, codeKey, activeChallengeKey(accountKey), verifyFailuresKey(accountKey)),
                accountKey, challengeHash, storedCode, otpProperties.maxAttempts());
        ScriptResult result = parseScriptResult(consumed, 3);
        if (result.status() == PROOF_EXPIRED || result.status() == PROOF_INVALID) {
            throw otpExpired();
        }
        if (result.status() == PROOF_LIMITED) {
            throw rateLimited(0, result.retryAfterSeconds());
        }
        if (result.status() != PROOF_CONSUMED) {
            throw new AuthSessionException(AuthSessionException.Code.AUTHORITY_UNAVAILABLE);
        }

        User user = findUserById(userId);
        if (user == null) {
            throw otpExpired();
        }
        String resetTicket = randomToken(32);
        Instant now = clock.instant();
        int expiresInSeconds = otpProperties.ttlSeconds();
        boolean ticketSaved = sessionLifecycle.issuePasswordResetTicket(
                userId,
                new CredentialSessionTransactionPort.CredentialHash(sha256(resetTicket)),
                now.plusSeconds(expiresInSeconds));
        if (!ticketSaved) {
            throw new AuthSessionException(AuthSessionException.Code.AUTHORITY_UNAVAILABLE);
        }
        return new PasswordResetVerifyResponse(
                resetTicket, expiresInSeconds, result.attemptsRemaining());
    }

    public void complete(String resetTicket, String newPassword) {
        if (resetTicket == null || resetTicket.isBlank()) {
            throw new PasswordResetException(
                    PasswordResetException.Code.RESET_TICKET_INVALID, null, null);
        }
        long userId = authService.completePasswordReset(
                new CredentialSessionTransactionPort.CredentialHash(sha256(resetTicket)),
                newPassword);
        // The credential transaction has committed before this lookup/publication.
        // Notification trouble must never turn a consumed ticket into an apparent reset failure.
        try {
            User user = findUserById(userId);
            Long telegramId = user == null ? null : user.getTelegramId();
            if (telegramId != null && telegramId > 0) {
                eventPublisher.publishEvent(new PasswordChangedEvent(this, telegramId));
            }
        } catch (RuntimeException exception) {
            log.warn("Password-change notification could not be published ({})",
                    exception.getClass().getSimpleName());
        }
    }

    private User findUserForReset(PasswordResetRequest request) {
        try {
            if (request.telegramId() != null) {
                return userRepository.findByTelegramId(request.telegramId()).orElse(null);
            }
            return userRepository.findByLogin(request.login().trim()).orElse(null);
        } catch (DataAccessException exception) {
            throw new AuthSessionException(AuthSessionException.Code.AUTHORITY_UNAVAILABLE, exception);
        }
    }

    private User findUserById(long userId) {
        try {
            return userRepository.findById(userId).orElse(null);
        } catch (DataAccessException exception) {
            throw new AuthSessionException(AuthSessionException.Code.AUTHORITY_UNAVAILABLE, exception);
        }
    }

    private int storeRealChallenge(
            String challengeHash,
            String accountKey,
            long userId,
            String code,
            int ttlSeconds
    ) {
        String result = execute(storeRealChallengeScript,
                List.of(challengeAccountKey(challengeHash), challengeUserKey(challengeHash),
                        challengeCodeKey(challengeHash), activeChallengeKey(accountKey),
                        verifyFailuresKey(accountKey)),
                otpProperties.maxAttempts(), userId, accountKey, ttlSeconds, code,
                challengeHash, challengePrefix());
        return result == null ? 0 : parseNonNegativeInt(result, 0);
    }

    private void storeDecoyChallenge(String challengeHash, String accountKey, String code, int ttlSeconds) {
        execute(storeDecoyChallengeScript,
                List.of(challengeAccountKey(challengeHash), challengeUserKey(challengeHash),
                        challengeCodeKey(challengeHash)),
                accountKey, code, ttlSeconds);
    }

    private PasswordResetException recordMismatch(String challengeHash, String accountKey) {
        String result = execute(recordMismatchScript,
                List.of(challengeAccountKey(challengeHash), challengeUserKey(challengeHash),
                        challengeCodeKey(challengeHash), activeChallengeKey(accountKey),
                        verifyFailuresKey(accountKey)),
                accountKey, challengeHash, otpProperties.maxAttempts(),
                otpProperties.attemptsWindowSeconds());
        ScriptResult parsed = parseScriptResult(result, 3);
        if (parsed.status() == PROOF_EXPIRED) {
            return otpExpired();
        }
        if (parsed.status() == PROOF_LIMITED) {
            return rateLimited(0, parsed.retryAfterSeconds());
        }
        if (parsed.status() == PROOF_INVALID) {
            return new PasswordResetException(
                    PasswordResetException.Code.OTP_INVALID,
                    parsed.attemptsRemaining(), null);
        }
        return new PasswordResetException(
                PasswordResetException.Code.OTP_INVALID,
                parsed.attemptsRemaining(), null);
    }

    private String redisValue(String key) {
        try {
            return redisTemplate.opsForValue().get(key);
        } catch (DataAccessException exception) {
            throw new AuthSessionException(AuthSessionException.Code.AUTHORITY_UNAVAILABLE, exception);
        }
    }

    private <T> T execute(DefaultRedisScript<T> script, List<String> keys, Object... args) {
        try {
            Object[] serializedArgs = Arrays.stream(args)
                    .map(String::valueOf)
                    .toArray();
            return redisTemplate.execute(script, keys, serializedArgs);
        } catch (DataAccessException exception) {
            throw new AuthSessionException(AuthSessionException.Code.AUTHORITY_UNAVAILABLE, exception);
        }
    }

    private ScriptResult parseScriptResult(String value, int fields) {
        if (value == null) {
            throw new AuthSessionException(AuthSessionException.Code.AUTHORITY_UNAVAILABLE);
        }
        String[] parts = value.split(":", -1);
        if (parts.length != fields) {
            throw new AuthSessionException(AuthSessionException.Code.AUTHORITY_UNAVAILABLE);
        }
        return new ScriptResult(parseLong(parts[0]),
                parts.length > 1 ? parseInt(parts[1], -1) : -1,
                parts.length > 2 ? parseInt(parts[2], 0) : 0);
    }

    private PasswordResetException otpExpired() {
        return new PasswordResetException(PasswordResetException.Code.OTP_EXPIRED, null, null);
    }

    private PasswordResetException rateLimited(int attemptsRemaining, int retryAfterSeconds) {
        return new PasswordResetException(PasswordResetException.Code.OTP_RATE_LIMITED,
                attemptsRemaining, Math.max(0, retryAfterSeconds));
    }

    private static long parseUserId(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException exception) {
            throw new AuthSessionException(AuthSessionException.Code.AUTHORITY_UNAVAILABLE, exception);
        }
    }

    private static int parseNonNegativeInt(String value, int fallback) {
        int parsed = parseInt(value, fallback);
        return Math.max(0, parsed);
    }

    private static int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private static long parseLong(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException exception) {
            throw new AuthSessionException(AuthSessionException.Code.AUTHORITY_UNAVAILABLE, exception);
        }
    }

    private String newCode() {
        return String.format(java.util.Locale.ROOT, "%06d", secureRandom.nextInt(1_000_000));
    }

    private String randomToken(int bytes) {
        byte[] raw = new byte[bytes];
        secureRandom.nextBytes(raw);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
    }

    private static String accountKeyForUser(long userId) {
        return sha256("password-reset-account:user:" + userId);
    }

    private static String unknownAccountKey(PasswordResetRequest request) {
        String identifier = request.telegramId() != null
                ? "telegram:" + request.telegramId()
                : "login:" + request.login().trim().toLowerCase(Locale.ROOT);
        return sha256("password-reset-account:unknown:" + identifier);
    }

    private static String requestWindowKey(String accountKey) {
        return "password_reset:request_window:" + accountKey;
    }

    private static String requestCooldownKey(String accountKey) {
        return "password_reset:request_cooldown:" + accountKey;
    }

    private static String verifyFailuresKey(String accountKey) {
        return "password_reset:verify_failures:" + accountKey;
    }

    private static String activeChallengeKey(String accountKey) {
        return "password_reset:active:" + accountKey;
    }

    private static String challengePrefix() {
        return "password_reset:challenge:";
    }

    private static String challengeAccountKey(String challengeHash) {
        return challengePrefix() + challengeHash + ":account";
    }

    private static String challengeUserKey(String challengeHash) {
        return challengePrefix() + challengeHash + ":user";
    }

    private static String challengeCodeKey(String challengeHash) {
        return challengePrefix() + challengeHash + ":code";
    }

    private static boolean constantTimeCodeEquals(String expected, String actual) {
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                actual.getBytes(StandardCharsets.UTF_8));
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private record ScriptResult(long status, int attemptsRemaining, int retryAfterSeconds) {
    }
}
