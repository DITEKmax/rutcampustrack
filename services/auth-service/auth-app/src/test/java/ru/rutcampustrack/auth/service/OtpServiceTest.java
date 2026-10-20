package ru.rutcampustrack.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.micrometer.core.instrument.Counter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;

import ru.rutcampustrack.auth.config.OtpProperties;
import ru.rutcampustrack.auth.dto.OtpRequest;
import ru.rutcampustrack.auth.dto.OtpVerifyByCodeRequest;
import ru.rutcampustrack.auth.dto.OtpVerifyRequest;
import ru.rutcampustrack.auth.entity.User;
import ru.rutcampustrack.auth.exception.OtpExpiredException;
import ru.rutcampustrack.auth.exception.OtpRateLimitException;
import ru.rutcampustrack.auth.repository.UserRepository;
import ru.rutcampustrack.auth.session.AuthSessionException;
import ru.rutcampustrack.shared.observability.BusinessMetrics;

import java.lang.reflect.Field;
import java.util.Optional;

/**
 * M09 Группа 1 (01 P0-5) — unit-проверка что verifyOtp использует {@link
 * java.security.MessageDigest#isEqual(byte[], byte[])} для сравнения кодов (constant-time) вместо
 * {@link String#equals(Object)}.
 *
 * <p>Гарантия constant-time обеспечивается JDK-имплементацией MessageDigest.isEqual; unit-тест ниже
 * (a) статически проверяет реальные call paths обоих verify методов к MessageDigest.isEqual; (b)
 * проверяет что functional-поведение верно (корректный код → success-путь, неверный → mismatch).
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
        when(redis.execute(any(RedisScript.class), anyList(), any(), any(), any())).thenReturn(1L);

        otpService =
                new OtpService(
                        redis,
                        otpProperties,
                        userRepository,
                        authService,
                        eventPublisher,
                        businessMetrics);
    }

    @Test
    @DisplayName(
            "verifyOtp: корректный код того же размера — не падает в OtpExpiredException"
                + " (mismatch-путь)")
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

        assertThatThrownBy(() -> otpService.verifyOtp(new OtpVerifyRequest(telegramId, "000000")))
                .isInstanceOf(OtpExpiredException.class);
    }

    @Test
    @DisplayName(
            "verifyOtp: null код не падает по NPE (защита от Bad Input до MessageDigest.isEqual)")
    void verifyOtp_nullCode_handledGracefully() {
        Long telegramId = 333L;
        User user = activeUser();
        when(userRepository.findByTelegramId(telegramId)).thenReturn(Optional.of(user));
        when(valueOps.get("otp:" + telegramId)).thenReturn("123456");
        when(valueOps.increment("otp_verify_attempts:" + telegramId)).thenReturn(1L);

        assertThatThrownBy(() -> otpService.verifyOtp(new OtpVerifyRequest(telegramId, null)))
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
        when(redis.execute(any(RedisScript.class), anyList(), any(), any(), any())).thenReturn(0L);

        assertThatThrownBy(() -> otpService.verifyOtp(new OtpVerifyRequest(telegramId, "123456")))
                .isInstanceOf(OtpExpiredException.class);

        verifyNoInteractions(authService, eventPublisher);
    }

    @Test
    @DisplayName(
            "OtpService source: использует MessageDigest.isEqual вместо String.equals для"
                + " request.code()")
    void verifyOtp_constantTimeCompare_sourceAssertion() throws Exception {
        var type =
                new com.tngtech.archunit.core.importer.ClassFileImporter()
                        .importClasses(OtpService.class)
                        .get(OtpService.class);
        for (String entry : java.util.List.of("verifyOtp", "verifyOtpByCode")) {
            var reachable = new java.util.HashSet<com.tngtech.archunit.core.domain.JavaMethod>();
            collectOtpCalls(
                    type.getMethods().stream()
                            .filter(method -> method.getName().equals(entry))
                            .findFirst()
                            .orElseThrow(),
                    reachable);
            var comparisons =
                    reachable.stream()
                            .flatMap(method -> method.getMethodCallsFromSelf().stream())
                            .toList();
            assertThat(comparisons)
                    .as(entry + " reaches the approved JDK comparison on its actual call path")
                    .anySatisfy(
                            call -> {
                                assertThat(call.getTargetOwner().getName())
                                        .isEqualTo("java.security.MessageDigest");
                                assertThat(call.getName()).isEqualTo("isEqual");
                            });
            assertThat(comparisons)
                    .noneSatisfy(
                            call -> {
                                assertThat(call.getTargetOwner().getName())
                                        .isEqualTo("java.lang.String");
                                assertThat(call.getName()).isEqualTo("equals");
                            });
        }
        // Structural call-path guard only: constant-time data flow still requires crypto review.
        // Redis atomicity and both reverse-index requirements are proved by OtpIT, not source text.
    }

    @Test
    @DisplayName("requestOtp: repository failure is typed authority unavailable before Redis work")
    void requestOtp_repositoryFailureIsTypedAuthorityUnavailable() {
        when(userRepository.findByTelegramId(666L))
                .thenThrow(new DataAccessResourceFailureException("database unavailable"));

        assertThatThrownBy(() -> otpService.requestOtp(new OtpRequest(666L)))
                .isInstanceOfSatisfying(
                        AuthSessionException.class,
                        exception ->
                                org.assertj.core.api.Assertions.assertThat(exception.code())
                                        .isEqualTo(
                                                AuthSessionException.Code.AUTHORITY_UNAVAILABLE));
        verify(valueOps, never()).get(anyString());
        verify(valueOps, never()).set(anyString(), anyString(), any());
    }

    @Test
    @DisplayName("repository failure during OTP verification is typed 503 and cannot touch proof")
    void verifyOtp_repositoryFailureIsTypedAuthorityUnavailable() {
        when(userRepository.findByTelegramId(444L))
                .thenThrow(new DataAccessResourceFailureException("database unavailable"));

        assertThatThrownBy(() -> otpService.verifyOtp(new OtpVerifyRequest(444L, "123456")))
                .isInstanceOfSatisfying(
                        AuthSessionException.class,
                        exception ->
                                org.assertj.core.api.Assertions.assertThat(exception.code())
                                        .isEqualTo(
                                                AuthSessionException.Code.AUTHORITY_UNAVAILABLE));
        verify(valueOps, never()).get(anyString());
        verify(redis, never()).execute(any(RedisScript.class), anyList(), any(), any(), any());
    }

    // ========================================================================
    // M16 G3 — verifyOtpByCode brute-force protection (HIGH SA-H1)
    // ========================================================================

    @Test
    @DisplayName(
            "verifyOtpByCode: counter < limit, mismatch → инкремент counter + OtpExpiredException")
    void verifyOtpByCode_mismatchUnderLimit_incrementsCounter() {
        String ip = "1.2.3.4";
        String missKey = "otp_verify_by_code_miss:" + ip;
        when(valueOps.get(missKey)).thenReturn("5"); // ниже лимита 20
        when(valueOps.get("otp_code:000000")).thenReturn(null);
        when(valueOps.increment(missKey)).thenReturn(6L);

        assertThatThrownBy(
                        () -> otpService.verifyOtpByCode(new OtpVerifyByCodeRequest("000000"), ip))
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

        assertThatThrownBy(
                        () -> otpService.verifyOtpByCode(new OtpVerifyByCodeRequest("111111"), ip))
                .isInstanceOf(OtpExpiredException.class);

        // Verify EXPIRE был выставлен (только при первом INCR)
        org.mockito.Mockito.verify(redis)
                .expire(
                        org.mockito.ArgumentMatchers.eq(missKey),
                        org.mockito.ArgumentMatchers.eq(300L),
                        org.mockito.ArgumentMatchers.eq(java.util.concurrent.TimeUnit.SECONDS));
    }

    @Test
    @DisplayName(
            "verifyOtpByCode: counter == limit (20) → 429 OtpRateLimitException БЕЗ проверки кода")
    void verifyOtpByCode_atLimit_throws429() {
        String ip = "9.9.9.9";
        String missKey = "otp_verify_by_code_miss:" + ip;
        when(valueOps.get(missKey)).thenReturn("20"); // на лимите

        assertThatThrownBy(
                        () -> otpService.verifyOtpByCode(new OtpVerifyByCodeRequest("123456"), ip))
                .isInstanceOf(OtpRateLimitException.class);

        // otp_code:* НЕ должен запрашиваться — pre-check срубил раньше.
        org.mockito.Mockito.verify(valueOps, org.mockito.Mockito.never()).get("otp_code:123456");
    }

    @Test
    @DisplayName(
            "verifyOtpByCode: repository failure is typed authority unavailable before consume")
    void verifyOtpByCode_repositoryFailureIsTypedAuthorityUnavailable() {
        String code = "123456";
        String ip = "192.0.2.55";
        Long telegramId = 777L;
        when(valueOps.get("otp_verify_by_code_miss:" + ip)).thenReturn(null);
        when(valueOps.get("otp_code:" + code)).thenReturn(telegramId.toString());
        when(valueOps.get("otp:" + telegramId)).thenReturn(code);
        when(userRepository.findByTelegramId(telegramId))
                .thenThrow(new DataAccessResourceFailureException("database unavailable"));

        assertThatThrownBy(() -> otpService.verifyOtpByCode(new OtpVerifyByCodeRequest(code), ip))
                .isInstanceOfSatisfying(
                        AuthSessionException.class,
                        exception ->
                                org.assertj.core.api.Assertions.assertThat(exception.code())
                                        .isEqualTo(
                                                AuthSessionException.Code.AUTHORITY_UNAVAILABLE));
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
        assertThatThrownBy(
                        () ->
                                otpService.verifyOtpByCode(
                                        new OtpVerifyByCodeRequest("777777"), "10.0.0.1"))
                .isInstanceOf(OtpExpiredException.class);

        // IP-B сразу throttled
        assertThatThrownBy(
                        () ->
                                otpService.verifyOtpByCode(
                                        new OtpVerifyByCodeRequest("777777"), "10.0.0.2"))
                .isInstanceOf(OtpRateLimitException.class);
    }

    @Test
    @DisplayName("verifyOtpByCode: null IP → используется literal 'unknown' как key")
    void verifyOtpByCode_nullIp_usesUnknownKey() {
        when(valueOps.get("otp_verify_by_code_miss:unknown")).thenReturn(null);
        when(valueOps.get("otp_code:222222")).thenReturn(null);
        when(valueOps.increment("otp_verify_by_code_miss:unknown")).thenReturn(1L);

        assertThatThrownBy(
                        () ->
                                otpService.verifyOtpByCode(
                                        new OtpVerifyByCodeRequest("222222"), null))
                .isInstanceOf(OtpExpiredException.class);
    }

    private static java.nio.file.Path locateSource() {
        // build.gradle-ый layout: services/auth-service/src/main/java/...
        java.nio.file.Path p =
                java.nio.file.Paths.get(
                        "src/main/java/ru/rutcampustrack/auth/service/OtpService.java");
        return p.toAbsolutePath();
    }

    private static User activeUser() {
        User user = new User();
        try {
            Field id = User.class.getDeclaredField("id");
            id.setAccessible(true);
            id.set(user, 1L);
            Field accountState = User.class.getDeclaredField("accountState");
            accountState.setAccessible(true);
            accountState.set(user, "ACTIVE");
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
        return user;
    }

    private static void collectOtpCalls(
            com.tngtech.archunit.core.domain.JavaMethod method,
            java.util.Set<com.tngtech.archunit.core.domain.JavaMethod> reachable) {
        if (!reachable.add(method)) return;
        method.getMethodCallsFromSelf().stream()
                .filter(call -> call.getTargetOwner().equals(method.getOwner()))
                .map(call -> call.getTarget().resolveMember())
                .flatMap(java.util.Optional::stream)
                .forEach(delegate -> collectOtpCalls(delegate, reachable));
    }
}
