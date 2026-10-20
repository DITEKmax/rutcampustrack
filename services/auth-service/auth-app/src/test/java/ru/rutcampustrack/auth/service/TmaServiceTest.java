package ru.rutcampustrack.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;

import ru.rutcampustrack.auth.config.TmaProperties;
import ru.rutcampustrack.auth.dto.TmaAuthRequest;
import ru.rutcampustrack.auth.dto.TokenResponse;
import ru.rutcampustrack.auth.entity.User;
import ru.rutcampustrack.auth.exception.TmaValidationException;
import ru.rutcampustrack.auth.repository.UserRepository;
import ru.rutcampustrack.auth.session.model.AuthMethod;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Collectors;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

class TmaServiceTest {

    private static final String SYNTHETIC_BOT_TOKEN = "synthetic-tma-test-token";
    private final UserRepository users = mock(UserRepository.class);
    private final AuthService auth = mock(AuthService.class);
    private final TmaService service =
            new TmaService(
                    users,
                    auth,
                    mock(StringRedisTemplate.class),
                    new TmaProperties(SYNTHETIC_BOT_TOKEN, 86400),
                    new ObjectMapper());

    @Test
    void signedMalformedAuthDatesAreSafeValidationFailures() throws Exception {
        for (String date :
                new String[] {
                    null, "not-a-number", "9223372036854775808", "-9223372036854775808"
                }) {
            assertThatThrownBy(
                            () ->
                                    service.authenticateWithInitData(
                                            new TmaAuthRequest(signed(date))))
                    .isInstanceOf(TmaValidationException.class);
        }
        verifyNoInteractions(users, auth);
    }

    @Test
    void signedFutureAuthDateCannotIssueSession() throws Exception {
        assertThatThrownBy(
                        () ->
                                service.authenticateWithInitData(
                                        new TmaAuthRequest(signed(Long.toString(Long.MAX_VALUE)))))
                .isInstanceOf(TmaValidationException.class);
        verifyNoInteractions(users, auth);
    }

    @Test
    void acceptedPastWindowIncludesExactBoundaryAndRejectsNextSecond() throws Exception {
        long now = 1_800_000_000L;
        TmaService fixedService =
                new TmaService(
                        users,
                        auth,
                        mock(StringRedisTemplate.class),
                        new TmaProperties(SYNTHETIC_BOT_TOKEN, 86400),
                        new ObjectMapper(),
                        Clock.fixed(Instant.ofEpochSecond(now), ZoneOffset.UTC));
        User linked = mock(User.class);
        TokenResponse result = new TokenResponse("synthetic-access", "synthetic-refresh", 900);
        when(users.findByTelegramId(123456789L)).thenReturn(Optional.of(linked));
        when(auth.issueSession(linked, AuthMethod.TMA, null, null, null)).thenReturn(result);
        assertThat(
                        fixedService.authenticateWithInitData(
                                new TmaAuthRequest(signed(Long.toString(now - 86400)))))
                .isSameAs(result);
        assertThat(
                        fixedService.authenticateWithInitData(
                                new TmaAuthRequest(signed(Long.toString(now)))))
                .isSameAs(result);
        assertThatThrownBy(
                        () ->
                                fixedService.authenticateWithInitData(
                                        new TmaAuthRequest(signed(Long.toString(now - 86401)))))
                .isInstanceOf(TmaValidationException.class);
        assertThatThrownBy(
                        () ->
                                fixedService.authenticateWithInitData(
                                        new TmaAuthRequest(signed(Long.toString(now + 1)))))
                .isInstanceOf(TmaValidationException.class);
    }

    @Test
    void fixedTelegramSpecVectorAcceptsReservedCharactersAndRejectsBitFlip() {
        // Independently calculated with Python stdlib hmac per Telegram WebAppData specification:
        // HMAC-SHA256(key="WebAppData", data=synthetic bot token), then sorted decoded key=value
        // lines.
        // auth_date=1800000000\nquery_id=synthetic+query/=anchor\nuser={"id":123456789,"first_name":"A&B +/= Ю"}
        String vector =
                "hash=50f15f55f30f6e9c41ada7dafb38525b90a6e97315775daf3f785a0410619b0a&user=%7B%22id%22%3A123456789%2C%22first_name%22%3A%22A%26B+%2B%2F%3D+%D0%AE%22%7D&query_id=synthetic%2Bquery%2F%3Danchor&auth_date=1800000000";
        TmaService fixed =
                new TmaService(
                        users,
                        auth,
                        mock(StringRedisTemplate.class),
                        new TmaProperties(SYNTHETIC_BOT_TOKEN, 86400),
                        new ObjectMapper(),
                        Clock.fixed(Instant.ofEpochSecond(1800000000), ZoneOffset.UTC));
        User linked = mock(User.class);
        TokenResponse issued = new TokenResponse("synthetic-access", "synthetic-refresh", 900);
        when(users.findByTelegramId(123456789L)).thenReturn(Optional.of(linked));
        when(auth.issueSession(linked, AuthMethod.TMA, null, null, null)).thenReturn(issued);
        assertThat(fixed.authenticateWithInitData(new TmaAuthRequest(vector))).isSameAs(issued);
        org.mockito.Mockito.clearInvocations(users, auth);
        assertThatThrownBy(
                        () ->
                                fixed.authenticateWithInitData(
                                        new TmaAuthRequest(vector.replace("hash=5", "hash=4"))))
                .isInstanceOf(TmaValidationException.class);
        verifyNoInteractions(users, auth);
    }

    private static String signed(String authDate) throws Exception {
        Map<String, String> parameters = new TreeMap<>();
        if (authDate != null) parameters.put("auth_date", authDate);
        parameters.put("user", "{\"id\":123456789}");
        String data =
                parameters.entrySet().stream()
                        .map(entry -> entry.getKey() + "=" + entry.getValue())
                        .collect(Collectors.joining("\n"));
        byte[] key =
                hmac(
                        SYNTHETIC_BOT_TOKEN.getBytes(StandardCharsets.UTF_8),
                        "WebAppData".getBytes(StandardCharsets.UTF_8));
        parameters.put(
                "hash", HexFormat.of().formatHex(hmac(data.getBytes(StandardCharsets.UTF_8), key)));
        return parameters.entrySet().stream()
                .map(
                        entry ->
                                entry.getKey()
                                        + "="
                                        + URLEncoder.encode(
                                                entry.getValue(), StandardCharsets.UTF_8))
                .collect(Collectors.joining("&"));
    }

    private static byte[] hmac(byte[] data, byte[] key) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        return mac.doFinal(data);
    }
}
