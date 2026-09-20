package ru.rutcampustrack.auth.service;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import ru.rutcampustrack.auth.config.OtpProperties;
import ru.rutcampustrack.auth.dto.OtpRequest;
import ru.rutcampustrack.auth.dto.OtpVerifyByCodeRequest;
import ru.rutcampustrack.auth.dto.OtpVerifyRequest;
import ru.rutcampustrack.auth.dto.TokenResponse;
import ru.rutcampustrack.auth.entity.User;
import ru.rutcampustrack.auth.event.OtpRequestedEvent;
import ru.rutcampustrack.auth.event.OtpVerifiedEvent;
import ru.rutcampustrack.auth.exception.InvalidCredentialsException;
import ru.rutcampustrack.auth.exception.OtpExpiredException;
import ru.rutcampustrack.auth.exception.OtpRateLimitException;
import ru.rutcampustrack.auth.repository.UserRepository;
import ru.rutcampustrack.auth.session.AuthSessionException;
import ru.rutcampustrack.shared.observability.BusinessMetrics;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
public class OtpService {

    private static final long PROOF_MISSING = 0L;
    private static final long PROOF_CONSUMED = 1L;
    private static final long PROOF_MISMATCH = 2L;

    /**
     * The forward and reverse proof indexes are consumed together.  The
     * reverse lookup path rechecks both indexes after it has resolved the
     * owner, so a direct verify and a verify-by-code request share one Redis
     * winner.  All proof cleanup is part of the same atomic operation.
     */
    private static final String CONSUME_PROOF_SCRIPT = """
            local direct = redis.call('GET', KEYS[1])
            if not direct then
                return 0
            end
            if direct ~= ARGV[1] then
                return 2
            end
            local reverse = redis.call('GET', KEYS[2])
            if not reverse then
                return 0
            end
            if reverse ~= ARGV[2] then
                return 2
            end
            redis.call('DEL', KEYS[1], KEYS[2], KEYS[3], KEYS[4], KEYS[5])
            return 1
            """;

    private final StringRedisTemplate redisTemplate;
    private final OtpProperties otpProperties;
    private final UserRepository userRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final BusinessMetrics businessMetrics;
    private final AuthService authService;
    private final SecureRandom secureRandom = new SecureRandom();
    private final DefaultRedisScript<Long> consumeProofScript;

    /** OTP proof ends at the common session seam. */
    public OtpService(StringRedisTemplate redisTemplate,
                      OtpProperties otpProperties,
                      UserRepository userRepository,
                      AuthService authService,
                      ApplicationEventPublisher eventPublisher,
                      BusinessMetrics businessMetrics) {
        this.redisTemplate = redisTemplate;
        this.otpProperties = otpProperties;
        this.userRepository = userRepository;
        this.eventPublisher = eventPublisher;
        this.businessMetrics = businessMetrics;
        this.authService = authService;
        this.consumeProofScript = new DefaultRedisScript<>(CONSUME_PROOF_SCRIPT, Long.class);
    }

    /**
     * M09 G2 (08 P0-2) — requestOtp больше НЕ возвращает код наружу.
     * Код идёт в notification-bot через RabbitMQ event {@code otp.requested};
     * bot отправляет его в Telegram. HTTP body ответа пустое (204 No Content).
     * См. DECISIONS D4 — почему прямая публикация, а не shared-outbox.
     */
    public void requestOtp(OtpRequest request) {
        Long telegramId = request.telegramId();

        findUserByTelegramId(telegramId);

        // Check resend cooldown
        if (Boolean.TRUE.equals(redisTemplate.hasKey("otp_sent:" + telegramId))) {
            throw new OtpRateLimitException("Please wait before requesting a new code");
        }

        // Check attempt count
        String attemptsValue = redisTemplate.opsForValue().get("otp_attempts:" + telegramId);
        if (attemptsValue != null && Integer.parseInt(attemptsValue) >= otpProperties.maxAttempts()) {
            throw new OtpRateLimitException("Too many OTP requests. Try again later");
        }

        // Generate 6-digit code, retry on collision with another live OTP
        String code;
        Duration ttl = Duration.ofSeconds(otpProperties.ttlSeconds());
        int collisionRetries = 5;
        while (true) {
            code = String.format("%06d", secureRandom.nextInt(1_000_000));
            Boolean reserved = redisTemplate.opsForValue()
                    .setIfAbsent("otp_code:" + code, telegramId.toString(), ttl);
            if (Boolean.TRUE.equals(reserved)) {
                break;
            }
            if (--collisionRetries <= 0) {
                throw new OtpRateLimitException("Could not allocate unique code, please retry");
            }
        }

        // Drop previous code mapping for this user (if any) before overwriting forward index
        String previousCode = redisTemplate.opsForValue().get("otp:" + telegramId);
        if (previousCode != null) {
            redisTemplate.delete("otp_code:" + previousCode);
        }

        // Store OTP code with TTL (forward index)
        redisTemplate.opsForValue().set("otp:" + telegramId, code, ttl);

        // Set resend cooldown
        redisTemplate.opsForValue().set("otp_sent:" + telegramId, "1",
                Duration.ofSeconds(otpProperties.resendCooldownSeconds()));

        // Increment attempt counter (set expiration only on first attempt)
        Long newAttemptCount = redisTemplate.opsForValue().increment("otp_attempts:" + telegramId);
        if (Long.valueOf(1L).equals(newAttemptCount)) {
            redisTemplate.expire("otp_attempts:" + telegramId,
                    otpProperties.attemptsWindowSeconds(), TimeUnit.SECONDS);
        }

        // M04 Группа 8 — otp.request{channel=telegram}. Единственный канал
        // на текущий момент; SMS будет добавлен — tag выделен под это.
        businessMetrics.otpRequestCounter("telegram").increment();

        // M09 G2 (08 P0-2) — doставка кода через RabbitMQ event → bot. fire-and-forget
        // (DomainEventListener обрабатывает @EventListener → rabbitTemplate).
        // Retry клиента при потере события → Redis TTL просрочится, клиент
        // получит новый код (NOTES Q1 вариант C).
        eventPublisher.publishEvent(new OtpRequestedEvent(
                this, telegramId, code, otpProperties.ttlSeconds()));
    }

    public TokenResponse verifyOtp(OtpVerifyRequest request) {
        Long telegramId = request.telegramId();

        User user = findUserByTelegramId(telegramId);

        String requestCode = request.code() == null ? "" : request.code();
        String storedCode = redisValue("otp:" + telegramId);
        if (storedCode == null) {
            businessMetrics.otpVerifyCounter("expired").increment();
            throw new OtpExpiredException();
        }

        // M09 Группа 1 (01 P0-5): constant-time compare — путь не ветвится
        // по содержимому кода, устраняя timing side-channel.
        if (!constantTimeCodeEquals(storedCode, requestCode)) {
            throw directMismatch(telegramId);
        }

        long proofStatus = consumeProof(telegramId, requestCode, false);
        if (proofStatus != PROOF_CONSUMED) {
            if (proofStatus == PROOF_MISMATCH) {
                throw directMismatch(telegramId);
            }
            businessMetrics.otpVerifyCounter("expired").increment();
            throw new OtpExpiredException();
        }

        TokenResponse response = issueTokens(user, telegramId, requestCode);
        businessMetrics.otpVerifyCounter("success").increment();
        return response;
    }

    /**
     * M16 G3 — verifyOtpByCode с brute-force защитой по IP (HIGH SA-H1).
     *
     * <p>До M16 endpoint только проверял существование {@code otp_code:<code>}
     * в Redis без счётчика попыток. Атакующий мог перебирать 6-digit
     * пространство кодов; единственная защита — Gateway RateLimiter
     * 5 req/min/IP + fail-open при Redis outage. При botnet-атаке
     * birthday-attack speedup делал brute-force реалистичным за часы.
     *
     * <p>Сейчас дополнительный counter {@code otp_verify_by_code_miss:<ip>}
     * с TTL 5 минут. После 20 mismatch'ей с одного IP — {@code 429
     * OtpRateLimitException}. Counter инкрементится **только при mismatch**,
     * чтобы не штрафовать legitimate users (paste с typo → попадание в
     * existing live code не штрафуется).
     *
     * <p>Reset-on-success **не делается**: атакующий мог бы случайно
     * угадать valid code, обнулить counter и продолжить перебор.
     */
    public TokenResponse verifyOtpByCode(OtpVerifyByCodeRequest request, String clientIp) {
        String code = request.code() == null ? "" : request.code();
        String missKey = "otp_verify_by_code_miss:" + (clientIp == null ? "unknown" : clientIp);

        // Pre-check: уже исчерпал лимит → сразу 429 без проверки кода.
        String currentMisses = redisValue(missKey);
        if (currentMisses != null
                && Integer.parseInt(currentMisses) >= otpProperties.verifyByCodeMissesPerWindow()) {
            businessMetrics.otpVerifyCounter("throttled").increment();
            throw new OtpRateLimitException("Too many verification attempts");
        }

        String telegramIdStr = redisValue("otp_code:" + code);
        if (telegramIdStr == null) {
            throw reverseMismatch(missKey);
        }

        Long telegramId;
        try {
            telegramId = Long.valueOf(telegramIdStr);
        } catch (NumberFormatException exception) {
            throw new AuthSessionException(AuthSessionException.Code.AUTHORITY_UNAVAILABLE, exception);
        }

        String storedCode = redisValue("otp:" + telegramId);
        if (storedCode == null || !constantTimeCodeEquals(storedCode, code)) {
            throw reverseMismatch(missKey);
        }

        User user = findUserByTelegramId(telegramId);

        long proofStatus = consumeProof(telegramId, code, true);
        if (proofStatus != PROOF_CONSUMED) {
            if (proofStatus == PROOF_MISMATCH) {
                throw reverseMismatch(missKey);
            }
            businessMetrics.otpVerifyCounter("expired").increment();
            throw new OtpExpiredException();
        }

        TokenResponse response = issueTokens(user, telegramId, code);
        businessMetrics.otpVerifyCounter("success").increment();
        return response;
    }

    private TokenResponse issueTokens(User user, Long telegramId, String code) {
        // Proof keys and counters were consumed atomically before this seam.
        TokenResponse response = authService.issueSession(user,
                ru.rutcampustrack.auth.session.model.AuthMethod.OTP,
                null, null, null);

        // Notify notification-bot that OTP was consumed — triggers removal of
        // Telegram messages with the code and the preceding user request.
        if (telegramId != null) {
            eventPublisher.publishEvent(new OtpVerifiedEvent(this, telegramId));
        }

        return response;
    }

    private long consumeProof(long telegramId, String code, boolean byCode) {
        try {
            Long result = redisTemplate.execute(
                    consumeProofScript,
                    List.of(
                            "otp:" + telegramId,
                            "otp_code:" + code,
                            "otp_attempts:" + telegramId,
                            "otp_sent:" + telegramId,
                            "otp_verify_attempts:" + telegramId),
                    code,
                    Long.toString(telegramId),
                    byCode ? "by-code" : "by-telegram");
            return result == null ? PROOF_MISSING : result;
        } catch (DataAccessException exception) {
            throw new AuthSessionException(AuthSessionException.Code.AUTHORITY_UNAVAILABLE, exception);
        }
    }

    private String redisValue(String key) {
        try {
            return redisTemplate.opsForValue().get(key);
        } catch (DataAccessException exception) {
            throw new AuthSessionException(AuthSessionException.Code.AUTHORITY_UNAVAILABLE, exception);
        }
    }

    private User findUserByTelegramId(Long telegramId) {
        try {
            return userRepository.findByTelegramId(telegramId)
                    .orElseThrow(InvalidCredentialsException::new);
        } catch (DataAccessException exception) {
            throw new AuthSessionException(AuthSessionException.Code.AUTHORITY_UNAVAILABLE, exception);
        }
    }

    private OtpExpiredException directMismatch(long telegramId) {
        String verifyKey = "otp_verify_attempts:" + telegramId;
        Long attempts;
        try {
            // IMP-03: Track verification attempts, annul OTP after 3 failures.
            attempts = redisTemplate.opsForValue().increment(verifyKey);
            if (attempts != null && attempts == 1L) {
                redisTemplate.expire(verifyKey, otpProperties.ttlSeconds(), TimeUnit.SECONDS);
            }
            if (attempts != null && attempts >= 3) {
                redisTemplate.delete("otp:" + telegramId);
                redisTemplate.delete(verifyKey);
            }
        } catch (DataAccessException exception) {
            throw new AuthSessionException(AuthSessionException.Code.AUTHORITY_UNAVAILABLE, exception);
        }
        if (attempts != null && attempts >= 3) {
            businessMetrics.otpVerifyCounter("annulled").increment();
            throw new OtpRateLimitException("Too many verification attempts. Request a new code");
        }
        businessMetrics.otpVerifyCounter("mismatch").increment();
        return new OtpExpiredException();
    }

    private OtpExpiredException reverseMismatch(String missKey) {
        try {
            // Mismatch counter TTL is set only on its first increment.
            Long newMisses = redisTemplate.opsForValue().increment(missKey);
            if (newMisses != null && newMisses == 1L) {
                redisTemplate.expire(missKey,
                        otpProperties.verifyByCodeWindowSeconds(), TimeUnit.SECONDS);
            }
        } catch (DataAccessException exception) {
            throw new AuthSessionException(AuthSessionException.Code.AUTHORITY_UNAVAILABLE, exception);
        }
        businessMetrics.otpVerifyCounter("mismatch").increment();
        return new OtpExpiredException();
    }

    private static boolean constantTimeCodeEquals(String expected, String actual) {
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                actual.getBytes(StandardCharsets.UTF_8));
    }
}
