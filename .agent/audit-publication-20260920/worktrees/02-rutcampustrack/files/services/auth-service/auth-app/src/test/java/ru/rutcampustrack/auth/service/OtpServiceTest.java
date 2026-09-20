package ru.rutcampustrack.auth.service;

import io.micrometer.core.instrument.Counter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;
import ru.rutcampustrack.auth.config.JwtProperties;
import ru.rutcampustrack.auth.config.OtpProperties;
import ru.rutcampustrack.auth.dto.OtpRequest;
import ru.rutcampustrack.auth.dto.OtpVerifyByCodeRequest;
import ru.rutcampustrack.auth.dto.OtpVerifyRequest;
import ru.rutcampustrack.auth.exception.OtpRateLimitException;
import ru.rutcampustrack.auth.entity.User;
import ru.rutcampustrack.auth.entity.enums.AccountStatus;
import ru.rutcampustrack.auth.exception.OtpExpiredException;
import ru.rutcampustrack.auth.repository.UserRepository;
import ru.rutcampustrack.auth.session.AuthSessionException;
import ru.rutcampustrack.shared.observability.BusinessMetrics;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * M09 Группа 1 (01 P0-5) — unit-проверка что verifyOtp использует
 * {@link java.security.MessageDigest#isEqual(byte[], byte[])} для сравнения
 * кодов (constant-time) вместо {@link String#equals(Object)}.
 *
 * <p>Гарантия constant-time обеспечивается JDK-имплементацией MessageDigest.isEqual;
 * unit-тест ниже (a) статически валидирует исходник на отсутствие String.equals
 * против request.code() и наличие MessageDigest.isEqual; (b) проверяет что
 * functional-поведение верно (корректный код → success-путь, неверный → mismatch).
 */
class OtpServiceTest {

    private StringRedisTemplate redis;
    private ValueOperations<String, String> valueOps;
    private OtpProperties otpProperties;
    private UserRepository userRepository;
    private AuthService authService;
    private ApplicationEventPublisher eventPublisher;
    private BusinessMetrics businessMetrics;
    private OtpService otpService;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redis = mock(StringRedisTemplate.class);
        valueOps = mock(ValueOperations.class);
        // M16 G3: добавлены verifyByCodeMissesPerWindow=20, verifyByCodeWindowSeconds=300.
        otpProperties = new OtpProperties(6, 300, 5, 3600, 30, 20, 300);
        userRepository = mock(UserRepository.class);
        authService = mock(AuthService.class);
        eventPublisher = mock(ApplicationEventPublisher.class);
        businessMetrics = mock(BusinessMetrics.class);
        Counter counter = mock(Counter.class);
        when(businessMetrics.otpVerifyCounter(anyString())).thenReturn(counter);
        when(redis.opsForValue()).thenReturn(valueOps);
        when(redis.execute(any(RedisScript.class), anyList(), any(), any(), any()))
                .thenReturn(1L);

        otpService = new OtpService(redis, otpProperties, userRepository,
                authService, eventPublisher, businessMetrics);
    }

    @Test
    @DisplayName("verifyOtp: корректный код того же размера — не падает в OtpExpiredException (mismatch-путь)")
    void verifyOtp_correctCode_doesNotThrowMismatch() {
        Long telegramId = 111L;
        User user = activeUser();
        when(userRepository.findByTelegramId(telegramId)).thenReturn(Optional.of(user));
        when(valueOps.get("otp:" + telegramId)).thenReturn("123456");

        // happy path — MessageDigest.isEqual для двух равных UTF-8 строк вернёт true
        otpService.verifyOtp(new OtpVerifyRequest(telegramId, "123456"));
    }

    @Test
    @DisplayName("verifyOtp: неверный код того же размера → OtpExpiredException (mismatch branch)")
    void verifyOtp_wrongCode_throwsMismatch() {
        Long telegramId = 222L;
        User user = activeUser();
        when(userRepository.findByTelegramId(telegramId)).thenReturn(Optional.of(user));
        when(valueOps.get("otp:" + telegramId)).thenReturn("123456");
        when(valueOps.increment("otp_verify_attempts:" + telegramId)).thenReturn(1L);

        assertThatThrownBy(() ->
                otpService.verifyOtp(new OtpVerifyRequest(telegramId, "000000")))
                .isInstanceOf(OtpExpiredException.class);
    }

    @Test
    @DisplayName("verifyOtp: null код не падает по NPE (защита от Bad Input до MessageDigest.isEqual)")
    void verifyOtp_nullCode_handledGracefully() {
        Long telegramId = 333L;
        User user = activeUser();
        when(userRepository.findByTelegramId(telegramId)).thenReturn(Optional.of(user));
        when(valueOps.get("otp:" + telegramId)).thenReturn("123456");
        when(valueOps.increment("otp_verify_attempts:" + telegramId)).thenReturn(1L);

        assertThatThrownBy(() ->
                otpService.verifyOtp(new OtpVerifyRequest(telegramId, null)))
                .isInstanceOf(OtpExpiredException.class);
    }

    @Test
    @DisplayName("verifyOtp: missing reverse proof is expired and cannot issue a session")
    void verifyOtp_missingReverseProof_doesNotIssueSession() {
        Long telegramId = 555L;
        User user = activeUser();
        when(userRepository.findByTelegramId(telegramId)).thenReturn(Optional.of(user));
        when(valueOps.get("otp:" + telegramId)).thenReturn("123456");
        // The atomic script returns PROOF_MISSING when otp_code:<code> has
        // disappeared, even though the forward key still contains the code.
        when(redis.execute(any(RedisScript.class), anyList(), any(), any(), any()))
                .thenReturn(0L);

        assertThatThrownBy(() ->
                otpService.verifyOtp(new OtpVerifyRequest(telegramId, "123456")))
                .isInstanceOf(OtpExpiredException.class);

        verifyNoInteractions(authService, eventPublisher);
    }

    @Test
    @DisplayName("OtpService source: использует MessageDigest.isEqual вместо String.equals для request.code()")
    void verifyOtp_constantTimeCompare_sourceAssertion() throws Exception {
        // Структурная проверка: сорс-код OtpService не содержит `storedCode.equals(request.code())`
        // и содержит вызов MessageDigest.isEqual. Это предохраняет от регрессии —
        // если кто-то переведёт compare обратно на String.equals, тест упадёт.
        java.nio.file.Path src = locateSource();
        String code = java.nio.file.Files.readString(src);

        org.assertj.core.api.Assertions.assertThat(code)
                .as("verifyOtp must not compare OTP via String.equals (timing side-channel)")
                .doesNotContain("storedCode.equals(request.code())");
        org.assertj.core.api.Assertions.assertThat(code)
                .as("verifyOtp must use MessageDigest.isEqual for constant-time compare")
                .contains("MessageDigest.isEqual");
        org.assertj.core.api.Assertions.assertThat(code)
                .as("both public OTP forms must use one atomic Redis proof consume")
                .contains("DefaultRedisScript<Long>", "consumeProofScript", "CONSUME_PROOF_SCRIPT");
        org.assertj.core.api.Assertions.assertThat(code)
                .as("both public OTP forms must require the reverse proof")
                .contains("if not reverse then\n                return 0")
                .doesNotContain("elseif reverse and reverse ~= ARGV[2]");
    }

    @Test
    @DisplayName("requestOtp: repository failure is typed authority unavailable before Redis work")
    void requestOtp_repositoryFailureIsTypedAuthorityUnavailable() {
        when(userRepository.findByTelegramId(666L))
                .thenThrow(new DataAccessResourceFailureException("database unavailable"));

        assertThatThrownBy(() -> otpService.requestOtp(new OtpRequest(666L)))
                .isInstanceOfSatisfying(AuthSessionException.class,
                        exception -> org.assertj.core.api.Assertions.assertThat(exception.code())
                                .isEqualTo(AuthSessionException.Code.AUTHORITY_UNAVAILABLE));
        verify(valueOps, never()).get(anyString());
        verify(valueOps, never()).set(anyString(), anyString(), any());
    }

    @Test
    @DisplayName("repository failure during OTP verification is typed 503 and cannot touch proof")
    void verifyOtp_repositoryFailureIsTypedAuthorityUnavailable() {
        when(userRepository.findByTelegramId(444L))
                .thenThrow(new DataAccessResourceFailureException("database unavailable"));

        assertThatThrownBy(() -> otpService.verifyOtp(new OtpVerifyRequest(444L, "123456")))
                .isInstanceOfSatisfying(AuthSessionException.class,
                        exception -> org.assertj.core.api.Assertions.assertThat(exception.code())
                                .isEqualTo(AuthSessionException.Code.AUTHORITY_UNAVAILABLE));
        verify(valueOps, never()).get(anyString());
        verify(redis, never()).execute(any(RedisScript.class), anyList(), any(), any(), any());
    }

    // ========================================================================
    // M16 G3 — verifyOtpByCode brute-force protection (HIGH SA-H1)
    // ========================================================================

    @Test
    @DisplayName("verifyOtpByCode: counter < limit, mismatch → инкремент counter + OtpExpiredException")
    void verifyOtpByCode_mismatchUnderLimit_incrementsCounter() {
        String ip = "1.2.3.4";
        String missKey = "otp_verify_by_code_miss:" + ip;
        when(valueOps.get(missKey)).thenReturn("5"); // ниже лимита 20
        when(valueOps.get("otp_code:000000")).thenReturn(null);
        when(valueOps.increment(missKey)).thenReturn(6L);

        assertThatThrownBy(() ->
                otpService.verifyOtpByCode(new OtpVerifyByCodeRequest("000000"), ip))
                .isInstanceOf(OtpExpiredException.class);
    }

    @Test
    @DisplayName("verifyOtpByCode: первый mismatch → counter=1 + EXPIRE на ключ")
    void verifyOtpByCode_firstMismatch_setsExpire() {
        String ip = "5.6.7.8";
        String missKey = "otp_verify_by_code_miss:" + ip;
        when(valueOps.get(missKey)).thenReturn(null); // первый attempt
        when(valueOps.get("otp_code:111111")).thenReturn(null);
        when(valueOps.increment(missKey)).thenReturn(1L);

        assertThatThrownBy(() ->
                otpService.verifyOtpByCode(new OtpVerifyByCodeRequest("111111"), ip))
                .isInstanceOf(OtpExpiredException.class);

        // Verify EXPIRE был выставлен (только при первом INCR)
        org.mockito.Mockito.verify(redis).expire(
                org.mockito.ArgumentMatchers.eq(missKey),
                org.mockito.ArgumentMatchers.eq(300L),
                org.mockito.ArgumentMatchers.eq(java.util.concurrent.TimeUnit.SECONDS));
    }

    @Test
    @DisplayName("verifyOtpByCode: counter == limit (20) → 429 OtpRateLimitException БЕЗ проверки кода")
    void verifyOtpByCode_atLimit_throws429() {
        String ip = "9.9.9.9";
        String missKey = "otp_verify_by_code_miss:" + ip;
        when(valueOps.get(missKey)).thenReturn("20"); // на лимите

        assertThatThrownBy(() ->
                otpService.verifyOtpByCode(new OtpVerifyByCodeRequest("123456"), ip))
                .isInstanceOf(OtpRateLimitException.class);

        // otp_code:* НЕ должен запрашиваться — pre-check срубил раньше.
        org.mockito.Mockito.verify(valueOps, org.mockito.Mockito.never())
                .get("otp_code:123456");
    }

    @Test
    @DisplayName("verifyOtpByCode: repository failure is typed authority unavailable before consume")
    void verifyOtpByCode_repositoryFailureIsTypedAuthorityUnavailable() {
        String code = "123456";
        String ip = "192.0.2.55";
        Long telegramId = 777L;
        when(valueOps.get("otp_verify_by_code_miss:" + ip)).thenReturn(null);
        when(valueOps.get("otp_code:" + code)).thenReturn(telegramId.toString());
        when(valueOps.get("otp:" + telegramId)).thenReturn(code);
        when(userRepository.findByTelegramId(telegramId))
                .thenThrow(new DataAccessResourceFailureException("database unavailable"));

        assertThatThrownBy(() ->
                otpService.verifyOtpByCode(new OtpVerifyByCodeRequest(code), ip))
                .isInstanceOfSatisfying(AuthSessionException.class,
                        exception -> org.assertj.core.api.Assertions.assertThat(exception.code())
                                .isEqualTo(AuthSessionException.Code.AUTHORITY_UNAVAILABLE));
        verify(redis, never()).execute(any(RedisScript.class), anyList(), any(), any(), any());
        verify(valueOps, never()).increment(anyString());
    }

    @Test
    @DisplayName("verifyOtpByCode: разные IP — независимые counters")
    void verifyOtpByCode_differentIps_independentCounters() {
        // IP-A: 19 mismatches (под лимитом)
        when(valueOps.get("otp_verify_by_code_miss:10.0.0.1")).thenReturn("19");
        when(valueOps.get("otp_code:777777")).thenReturn(null);
        when(valueOps.increment("otp_verify_by_code_miss:10.0.0.1")).thenReturn(20L);

        // IP-B: на лимите (заблокирован)
        when(valueOps.get("otp_verify_by_code_miss:10.0.0.2")).thenReturn("20");

        // IP-A проходит до Redis check
        assertThatThrownBy(() ->
                otpService.verifyOtpByCode(new OtpVerifyByCodeRequest("777777"), "10.0.0.1"))
                .isInstanceOf(OtpExpiredException.class);

        // IP-B сразу throttled
        assertThatThrownBy(() ->
                otpService.verifyOtpByCode(new OtpVerifyByCodeRequest("777777"), "10.0.0.2"))
                .isInstanceOf(OtpRateLimitException.class);
    }

    @Test
    @DisplayName("verifyOtpByCode: null IP → используется literal 'unknown' как key")
    void verifyOtpByCode_nullIp_usesUnknownKey() {
        when(valueOps.get("otp_verify_by_code_miss:unknown")).thenReturn(null);
        when(valueOps.get("otp_code:222222")).thenReturn(null);
        when(valueOps.increment("otp_verify_by_code_miss:unknown")).thenReturn(1L);

        assertThatThrownBy(() ->
                otpService.verifyOtpByCode(new OtpVerifyByCodeRequest("222222"), null))
                .isInstanceOf(OtpExpiredException.class);
    }

    private static java.nio.file.Path locateSource() {
        // build.gradle-ый layout: services/auth-service/src/main/java/...
        java.nio.file.Path p = java.nio.file.Paths.get(
                "src/main/java/ru/rutcampustrack/auth/service/OtpService.java");
        return p.toAbsolutePath();
    }

    private static User activeUser() {
        User user = new User();
        try {
            Field id = User.class.getDeclaredField("id");
            id.setAccessible(true);
            id.set(user, 1L);
            Field status = User.class.getDeclaredField("status");
            status.setAccessible(true);
            status.set(user, AccountStatus.ACTIVE);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
        return user;
    }
}
