package ru.rutcampustrack.auth.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.*;
import org.springframework.test.context.jdbc.Sql;
import ru.rutcampustrack.auth.dto.ChangePasswordRequest;
import ru.rutcampustrack.auth.dto.LoginRequest;
import ru.rutcampustrack.auth.dto.OtpRequest;
import ru.rutcampustrack.auth.dto.OtpVerifyByCodeRequest;
import ru.rutcampustrack.auth.dto.OtpVerifyRequest;
import ru.rutcampustrack.auth.dto.TokenResponse;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@Sql(scripts = "classpath:sql/set-telegram-id.sql")
@Sql(scripts = "classpath:sql/clear-telegram-id.sql", executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
class OtpIT extends AbstractIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @BeforeEach
    void cleanOtpRedisKeys() {
        Set<String> otpKeys = redisTemplate.keys("otp:*");
        if (otpKeys != null && !otpKeys.isEmpty()) redisTemplate.delete(otpKeys);
        Set<String> attemptKeys = redisTemplate.keys("otp_attempts:*");
        if (attemptKeys != null && !attemptKeys.isEmpty()) redisTemplate.delete(attemptKeys);
        Set<String> sentKeys = redisTemplate.keys("otp_sent:*");
        if (sentKeys != null && !sentKeys.isEmpty()) redisTemplate.delete(sentKeys);
        Set<String> codeKeys = redisTemplate.keys("otp_code:*");
        if (codeKeys != null && !codeKeys.isEmpty()) redisTemplate.delete(codeKeys);
    }

    @Test
    void otpRequest_withValidTelegramId_returns204NoBody() {
        OtpRequest request = new OtpRequest(123456789L);

        ResponseEntity<Void> response = restTemplate.postForEntity(
                "/auth/otp/request", request, Void.class);

        // M09 G2 (08 P0-2) — 204 No Content, код в body отсутствует,
        // доставляется через RabbitMQ event в notification-bot.
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(response.getBody()).isNull();

        // Tests читают код из Redis напрямую (в prod его видит только bot).
        String code = redisTemplate.opsForValue().get("otp:123456789");
        assertThat(code).isNotBlank();
        assertThat(code).hasSize(6);
    }

    @Test
    void otpVerify_withCorrectCode_returnsTokenPair() {
        OtpRequest otpRequest = new OtpRequest(123456789L);
        ResponseEntity<Void> requestResponse = restTemplate.postForEntity(
                "/auth/otp/request", otpRequest, Void.class);
        assertThat(requestResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        // Tests читают код из Redis — в prod код попадает в Telegram через event.
        String code = redisTemplate.opsForValue().get("otp:123456789");
        assertThat(code).isNotBlank();

        OtpVerifyRequest verifyRequest = new OtpVerifyRequest(123456789L, code);
        ResponseEntity<TokenResponse> verifyResponse = restTemplate.postForEntity(
                "/auth/otp/verify", verifyRequest, TokenResponse.class);

        assertThat(verifyResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(verifyResponse.getBody()).isNotNull();
        assertThat(verifyResponse.getBody().accessToken()).isNotBlank();
        assertThat(verifyResponse.getBody().refreshToken()).isNotBlank();
    }

    @Test
    void otpVerify_withWrongCode_returns401() {
        // Request OTP first to ensure there is a valid OTP in Redis
        OtpRequest otpRequest = new OtpRequest(123456789L);
        restTemplate.postForEntity("/auth/otp/request", otpRequest, Void.class);

        // Verify with wrong code
        OtpVerifyRequest verifyRequest = new OtpVerifyRequest(123456789L, "000000");
        ResponseEntity<String> verifyResponse = restTemplate.postForEntity(
                "/auth/otp/verify", verifyRequest, String.class);

        assertThat(verifyResponse.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void otpVerifyByCode_withCorrectCode_returnsTokenPair() {
        OtpRequest otpRequest = new OtpRequest(123456789L);
        restTemplate.postForEntity("/auth/otp/request", otpRequest, Void.class);
        String code = redisTemplate.opsForValue().get("otp:123456789");

        // Verify by code only (no telegram_id)
        OtpVerifyByCodeRequest verifyRequest = new OtpVerifyByCodeRequest(code);
        ResponseEntity<TokenResponse> verifyResponse = restTemplate.postForEntity(
                "/auth/otp/verify-by-code", verifyRequest, TokenResponse.class);

        assertThat(verifyResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(verifyResponse.getBody()).isNotNull();
        assertThat(verifyResponse.getBody().accessToken()).isNotBlank();
        assertThat(verifyResponse.getBody().refreshToken()).isNotBlank();
    }

    @Test
    void otpVerifyByCode_withUnknownCode_returns401() {
        OtpVerifyByCodeRequest verifyRequest = new OtpVerifyByCodeRequest("000000");
        ResponseEntity<String> verifyResponse = restTemplate.postForEntity(
                "/auth/otp/verify-by-code", verifyRequest, String.class);

        assertThat(verifyResponse.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void otpRequest_withNonexistentTelegramId_returns401() {
        OtpRequest request = new OtpRequest(999999999L);

        ResponseEntity<String> response = restTemplate.postForEntity(
                "/auth/otp/request", request, String.class);

        // OtpService throws InvalidCredentialsException when telegramId not found -> 401
        assertThat(response.getStatusCode().is4xxClientError()).isTrue();
    }

    @Test
    @Sql(scripts = "classpath:sql/reset-student-password.sql",
         executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
    void changePassword_withCorrectCurrent_returns204() {
        // Login as student to get access token
        LoginRequest loginRequest = new LoginRequest("student", "password");
        ResponseEntity<TokenResponse> loginResponse = restTemplate.postForEntity(
                "/auth/login", loginRequest, TokenResponse.class);
        assertThat(loginResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(loginResponse.getBody()).isNotNull();
        String accessToken = loginResponse.getBody().accessToken();

        // Change password
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        headers.setContentType(MediaType.APPLICATION_JSON);
        ChangePasswordRequest changeRequest = new ChangePasswordRequest("password", "NewPass1x");
        HttpEntity<ChangePasswordRequest> entity = new HttpEntity<>(changeRequest, headers);

        ResponseEntity<Void> changeResponse = restTemplate.postForEntity(
                "/auth/change-password", entity, Void.class);
        assertThat(changeResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        // Verify can login with new password
        LoginRequest newLoginRequest = new LoginRequest("student", "NewPass1x");
        ResponseEntity<TokenResponse> newLoginResponse = restTemplate.postForEntity(
                "/auth/login", newLoginRequest, TokenResponse.class);
        assertThat(newLoginResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void changePassword_withWrongCurrent_returns401() {
        // Login as admin to get access token
        LoginRequest loginRequest = new LoginRequest("admin", "password");
        ResponseEntity<TokenResponse> loginResponse = restTemplate.postForEntity(
                "/auth/login", loginRequest, TokenResponse.class);
        assertThat(loginResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(loginResponse.getBody()).isNotNull();
        String accessToken = loginResponse.getBody().accessToken();

        // Change password with wrong current
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        headers.setContentType(MediaType.APPLICATION_JSON);
        ChangePasswordRequest changeRequest = new ChangePasswordRequest("wrongpass", "NewPass1x");
        HttpEntity<ChangePasswordRequest> entity = new HttpEntity<>(changeRequest, headers);

        ResponseEntity<String> changeResponse = restTemplate.postForEntity(
                "/auth/change-password", entity, String.class);
        assertThat(changeResponse.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
