package ru.rutcampustrack.auth.service;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import ru.rutcampustrack.auth.config.JwtProperties;
import ru.rutcampustrack.auth.config.OtpProperties;
import ru.rutcampustrack.auth.dto.OtpRequest;
import ru.rutcampustrack.auth.dto.OtpVerifyByCodeRequest;
import ru.rutcampustrack.auth.dto.OtpVerifyRequest;
import ru.rutcampustrack.auth.dto.TokenResponse;
import ru.rutcampustrack.auth.entity.User;
import ru.rutcampustrack.auth.entity.enums.AccountStatus;
import ru.rutcampustrack.auth.event.OtpVerifiedEvent;
import ru.rutcampustrack.auth.exception.InvalidCredentialsException;
import ru.rutcampustrack.auth.exception.OtpExpiredException;
import ru.rutcampustrack.auth.exception.OtpRateLimitException;
import ru.rutcampustrack.auth.repository.UserRepository;
import ru.rutcampustrack.shared.observability.BusinessMetrics;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Service
public class OtpService {

    private final StringRedisTemplate redisTemplate;
    private final OtpProperties otpProperties;
    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final JwtProperties jwtProperties;
    private final ApplicationEventPublisher eventPublisher;
    private final BusinessMetrics businessMetrics;
    private final SecureRandom secureRandom = new SecureRandom();

    public OtpService(StringRedisTemplate redisTemplate,
                      OtpProperties otpProperties,
                      UserRepository userRepository,
                      JwtService jwtService,
                      JwtProperties jwtProperties,
                      ApplicationEventPublisher eventPublisher,
                      BusinessMetrics businessMetrics) {
        this.redisTemplate = redisTemplate;
        this.otpProperties = otpProperties;
        this.userRepository = userRepository;
        this.jwtService = jwtService;
        this.jwtProperties = jwtProperties;
        this.eventPublisher = eventPublisher;
        this.businessMetrics = businessMetrics;
    }

    public String requestOtp(OtpRequest request) {
        Long telegramId = request.telegramId();

        User user = userRepository.findByTelegramId(telegramId)
                .orElseThrow(InvalidCredentialsException::new);

        if (user.getStatus() != AccountStatus.ACTIVE) {
            throw new InvalidCredentialsException();
        }

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

        return code;
    }

    public TokenResponse verifyOtp(OtpVerifyRequest request) {
        Long telegramId = request.telegramId();

        User user = userRepository.findByTelegramId(telegramId)
                .orElseThrow(InvalidCredentialsException::new);

        if (user.getStatus() != AccountStatus.ACTIVE) {
            throw new InvalidCredentialsException();
        }

        String storedCode = redisTemplate.opsForValue().get("otp:" + telegramId);
        if (storedCode == null) {
            businessMetrics.otpVerifyCounter("expired").increment();
            throw new OtpExpiredException();
        }

        // M09 Группа 1 (01 P0-5): constant-time compare — путь не ветвится
        // по содержимому кода, устраняя timing side-channel.
        String requestCode = request.code() == null ? "" : request.code();
        boolean matches = MessageDigest.isEqual(
                storedCode.getBytes(StandardCharsets.UTF_8),
                requestCode.getBytes(StandardCharsets.UTF_8));
        if (!matches) {
            // IMP-03: Track verification attempts, annul OTP after 3 failures
            String verifyKey = "otp_verify_attempts:" + telegramId;
            Long attempts = redisTemplate.opsForValue().increment(verifyKey);
            if (attempts != null && attempts == 1L) {
                redisTemplate.expire(verifyKey, otpProperties.ttlSeconds(), TimeUnit.SECONDS);
            }
            if (attempts != null && attempts >= 3) {
                // Annul the OTP — force user to request a new one
                redisTemplate.delete("otp:" + telegramId);
                redisTemplate.delete(verifyKey);
                businessMetrics.otpVerifyCounter("annulled").increment();
                throw new OtpRateLimitException("Too many verification attempts. Request a new code");
            }
            businessMetrics.otpVerifyCounter("mismatch").increment();
            throw new OtpExpiredException();
        }

        TokenResponse response = issueTokens(user, telegramId, request.code());
        businessMetrics.otpVerifyCounter("success").increment();
        return response;
    }

    public TokenResponse verifyOtpByCode(OtpVerifyByCodeRequest request) {
        String code = request.code();

        String telegramIdStr = redisTemplate.opsForValue().get("otp_code:" + code);
        if (telegramIdStr == null) {
            businessMetrics.otpVerifyCounter("expired").increment();
            throw new OtpExpiredException();
        }

        Long telegramId = Long.valueOf(telegramIdStr);

        User user = userRepository.findByTelegramId(telegramId)
                .orElseThrow(InvalidCredentialsException::new);

        if (user.getStatus() != AccountStatus.ACTIVE) {
            throw new InvalidCredentialsException();
        }

        TokenResponse response = issueTokens(user, telegramId, code);
        businessMetrics.otpVerifyCounter("success").increment();
        return response;
    }

    private TokenResponse issueTokens(User user, Long telegramId, String code) {
        // Clean up all OTP-related Redis keys
        redisTemplate.delete("otp:" + telegramId);
        redisTemplate.delete("otp_code:" + code);
        redisTemplate.delete("otp_attempts:" + telegramId);
        redisTemplate.delete("otp_sent:" + telegramId);
        redisTemplate.delete("otp_verify_attempts:" + telegramId);

        // Generate JWT pair
        String accessToken = jwtService.generateAccessToken(user);
        String refreshToken = jwtService.generateRefreshToken(user);
        String jti = jwtService.extractJti(refreshToken);

        redisTemplate.opsForValue().set("refresh:" + user.getId() + ":" + jti, "valid",
                Duration.ofSeconds(jwtProperties.refreshTokenExpiration()));

        // Notify notification-bot that OTP was consumed — triggers removal of
        // Telegram messages with the code and the preceding user request.
        if (telegramId != null) {
            eventPublisher.publishEvent(new OtpVerifiedEvent(this, telegramId));
        }

        return new TokenResponse(accessToken, refreshToken, jwtProperties.accessTokenExpiration());
    }
}
