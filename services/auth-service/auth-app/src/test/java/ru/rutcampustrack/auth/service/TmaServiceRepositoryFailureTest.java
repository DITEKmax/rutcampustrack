package ru.rutcampustrack.auth.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import ru.rutcampustrack.auth.config.TmaProperties;
import ru.rutcampustrack.auth.dto.TmaAuthRequest;
import ru.rutcampustrack.auth.repository.UserRepository;
import ru.rutcampustrack.auth.session.AuthSessionException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TmaServiceRepositoryFailureTest {

    private static final String BOT_TOKEN = "test_bot_token_12345";
    private static final long TELEGRAM_ID = 123456789L;

    @Test
    void authenticate_repositoryFailureIsTypedAuthorityUnavailableBeforeSessionIssue() throws Exception {
        UserRepository userRepository = mock(UserRepository.class);
        AuthService authService = mock(AuthService.class);
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        TmaService tmaService = new TmaService(
                userRepository, authService, redisTemplate,
                new TmaProperties(BOT_TOKEN, 86400), new ObjectMapper());
        when(userRepository.findByTelegramId(TELEGRAM_ID))
                .thenThrow(new DataAccessResourceFailureException("database unavailable"));

        assertThatThrownBy(() -> tmaService.authenticateWithInitData(
                new TmaAuthRequest(validInitData())))
                .isInstanceOfSatisfying(AuthSessionException.class,
                        exception -> assertThat(exception.code())
                                .isEqualTo(AuthSessionException.Code.AUTHORITY_UNAVAILABLE));

        verify(authService, never()).issueSession(any(), any(), any(), any(), any());
    }

    private static String validInitData() throws Exception {
        long authDate = System.currentTimeMillis() / 1000;
        String userJson = "{\"id\":" + TELEGRAM_ID + "}";
        String encodedUser = URLEncoder.encode(userJson, StandardCharsets.UTF_8);
        String dataCheckString = "auth_date=" + authDate + "\nuser=" + userJson;
        byte[] secretKey = hmacSha256(
                BOT_TOKEN.getBytes(StandardCharsets.UTF_8),
                "WebAppData".getBytes(StandardCharsets.UTF_8));
        byte[] hash = hmacSha256(dataCheckString.getBytes(StandardCharsets.UTF_8), secretKey);
        return "auth_date=" + authDate + "&user=" + encodedUser
                + "&hash=" + HexFormat.of().formatHex(hash);
    }

    private static byte[] hmacSha256(byte[] data, byte[] key) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        return mac.doFinal(data);
    }
}
