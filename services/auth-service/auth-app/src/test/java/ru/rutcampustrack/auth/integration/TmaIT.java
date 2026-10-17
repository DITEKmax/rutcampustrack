package ru.rutcampustrack.auth.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.context.jdbc.Sql.ExecutionPhase.AFTER_TEST_METHOD;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;
import ru.rutcampustrack.auth.dto.CurrentSessionResponse;
import ru.rutcampustrack.auth.dto.TmaAuthRequest;
import ru.rutcampustrack.auth.dto.TokenResponse;

@Tag("security-contract")
@Sql(scripts = "classpath:sql/set-telegram-id.sql")
@Sql(scripts = "classpath:sql/clear-telegram-id.sql", executionPhase = AFTER_TEST_METHOD)
class TmaIT extends AbstractIntegrationTest {

    private static final String BOT_TOKEN = "test_bot_token_12345";

    @Autowired TestRestTemplate restTemplate;

    @Autowired JdbcTemplate jdbc;

    @Test
    void tma_withValidInitData_returnsTokenPair() {
        String initData = buildValidInitData(123456789L, BOT_TOKEN);
        TmaAuthRequest request = new TmaAuthRequest(initData);
        ResponseEntity<TokenResponse> response =
                restTemplate.postForEntity("/auth/tma", request, TokenResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().accessToken()).isNotBlank();
        assertThat(response.getBody().refreshToken()).isNotBlank();
        assertThat(response.getBody().expiresIn()).isGreaterThan(0);
    }

    @Test
    void tma_withTamperedHash_returns401() {
        String initData =
                "auth_date="
                        + (System.currentTimeMillis() / 1000)
                        + "&user="
                        + URLEncoder.encode("{\"id\":123456789}", StandardCharsets.UTF_8)
                        + "&hash=deadbeefdeadbeef";
        TmaAuthRequest request = new TmaAuthRequest(initData);
        ResponseEntity<String> response =
                restTemplate.postForEntity("/auth/tma", request, String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getHeaders().getContentType().toString())
                .contains("application/problem+json");
    }

    @Test
    void tma_withExpiredAuthDate_returns401() {
        // auth_date from 2 days ago (exceeds 86400 max age)
        long expiredAuthDate = System.currentTimeMillis() / 1000 - 172800;
        String initData = buildValidInitDataWithAuthDate(123456789L, BOT_TOKEN, expiredAuthDate);
        TmaAuthRequest request = new TmaAuthRequest(initData);
        ResponseEntity<String> response =
                restTemplate.postForEntity("/auth/tma", request, String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void tma_withUnlinkedTelegramId_returns401() {
        // telegram_id 999999999 is not in DB
        String initData = buildValidInitData(999999999L, BOT_TOKEN);
        TmaAuthRequest request = new TmaAuthRequest(initData);
        ResponseEntity<String> response =
                restTemplate.postForEntity("/auth/tma", request, String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    /**
     * M08 G8 security contract (NEW-164) — replay with same initData and hash is accepted as long
     * as auth_date is within {@code tma.auth-date-max-age-seconds} window. Owner decision
     * 2026-10-11 keeps the 24-hour reuse window with fresh authority checks rather than a per-hash
     * one-shot guard.
     *
     * <p>Captured in docs/testing/testing.md — Security contract tests: TMA replay semantics.
     */
    @Test
    void tma_withReplayedInitDataInsideWindow_returnsToken() {
        String initData = buildValidInitData(123456789L, BOT_TOKEN);
        TmaAuthRequest request = new TmaAuthRequest(initData);

        ResponseEntity<TokenResponse> firstResponse =
                restTemplate.postForEntity("/auth/tma", request, TokenResponse.class);
        assertThat(firstResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Exact same initData, same hash, same auth_date → still valid (by design)
        ResponseEntity<TokenResponse> replayResponse =
                restTemplate.postForEntity("/auth/tma", request, TokenResponse.class);
        assertThat(replayResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(replayResponse.getBody()).isNotNull();
        // New token pair each time — jti rotates, old refresh tokens remain valid
        // independently (replay-protection lives at refresh-token level, not initData)
        assertThat(replayResponse.getBody().refreshToken())
                .isNotEqualTo(firstResponse.getBody().refreshToken());
    }

    /**
     * M08 G8 security contract (NEW-164) — an initData with a bit-flipped signature is rejected
     * even though the payload is otherwise identical to a valid request. Protects against an
     * attacker who snooped a hash and tries to forge variants.
     */
    @Test
    void tma_withBitFlippedSignature_returns401() {
        String validInitData = buildValidInitData(123456789L, BOT_TOKEN);
        // Flip the first hex char of the hash — still syntactically valid length,
        // but HMAC comparison must fail via MessageDigest.isEqual (constant-time).
        int hashIdx = validInitData.lastIndexOf("&hash=") + "&hash=".length();
        char firstHashChar = validInitData.charAt(hashIdx);
        char flipped = firstHashChar == '0' ? '1' : '0';
        String tampered =
                validInitData.substring(0, hashIdx)
                        + flipped
                        + validInitData.substring(hashIdx + 1);

        TmaAuthRequest request = new TmaAuthRequest(tampered);
        ResponseEntity<String> response =
                restTemplate.postForEntity("/auth/tma", request, String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    /**
     * M08 G8 security contract (NEW-164) — an initData signed with a different bot token is
     * rejected. Protects against an attacker who gained control of a different bot in the same
     * namespace.
     */
    @Test
    void tma_withDifferentBotTokenSignature_returns401() {
        // Sign with an attacker's bot token, not the one auth-service trusts
        String initData = buildValidInitData(123456789L, "attacker_bot_token_67890");
        TmaAuthRequest request = new TmaAuthRequest(initData);

        ResponseEntity<String> response =
                restTemplate.postForEntity("/auth/tma", request, String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    /**
     * M08 G8 security contract (NEW-164) — missing `hash` field in initData is rejected (not
     * silently treated as "no hash required").
     */
    @Test
    void tma_withMissingHashField_returns401() {
        long authDate = System.currentTimeMillis() / 1000;
        String initData =
                "auth_date="
                        + authDate
                        + "&user="
                        + URLEncoder.encode("{\"id\":123456789}", StandardCharsets.UTF_8);
        TmaAuthRequest request = new TmaAuthRequest(initData);

        ResponseEntity<String> response =
                restTemplate.postForEntity("/auth/tma", request, String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(
            strings = {
                "not-a-number",
                "9223372036854775808",
                "-9223372036854775808",
                "9223372036854775807"
            })
    void signedMalformedOrFutureDateReturns401WithoutSessionWrites(String authDate)
            throws Exception {
        Map<String, String> parameters = new LinkedHashMap<>();
        if (authDate != null) parameters.put("auth_date", authDate);
        parameters.put("user", "{\"id\":123456789}");
        long before = jdbc.queryForObject("SELECT count(*) FROM auth_sessions", Long.class);
        var response =
                restTemplate.postForEntity(
                        "/auth/tma",
                        new TmaAuthRequest(signParameters(parameters, BOT_TOKEN)),
                        String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody())
                .contains("tma-validation-failed")
                .doesNotContain("NumberFormatException");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM auth_sessions", Long.class))
                .isEqualTo(before);
    }

    @Test
    void replayCannotAuthenticateAfterTelegramBindingIsRemoved() {
        String initData = buildValidInitData(123456789L, BOT_TOKEN);
        var request = new TmaAuthRequest(initData);
        assertThat(
                        restTemplate
                                .postForEntity("/auth/tma", request, TokenResponse.class)
                                .getStatusCode())
                .isEqualTo(HttpStatus.OK);
        jdbc.update("UPDATE users SET telegram_id = NULL WHERE login = 'student'");
        long before = jdbc.queryForObject("SELECT count(*) FROM auth_sessions", Long.class);
        assertThat(restTemplate.postForEntity("/auth/tma", request, String.class).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM auth_sessions", Long.class))
                .isEqualTo(before);
    }

    // --- Helper methods ---

    private String buildValidInitData(long telegramId, String botToken) {
        return buildValidInitDataWithAuthDate(
                telegramId, botToken, System.currentTimeMillis() / 1000);
    }

    private String buildValidInitDataWithAuthDate(long telegramId, String botToken, long authDate) {
        try {
            String userJson =
                    "{\"id\":"
                            + telegramId
                            + ",\"first_name\":\"Test\",\"username\":\"test_user\"}";

            Map<String, String> params = new LinkedHashMap<>();
            params.put("auth_date", String.valueOf(authDate));
            params.put("query_id", "test_query_123");
            params.put("user", userJson);

            // Sort by key to build data-check-string
            String dataCheckString =
                    params.entrySet().stream()
                            .sorted(Map.Entry.comparingByKey())
                            .map(e -> e.getKey() + "=" + e.getValue())
                            .collect(Collectors.joining("\n"));

            // Compute secret key: HMAC_SHA256(data=botToken, key="WebAppData")
            byte[] secretKey =
                    hmacSha256(
                            botToken.getBytes(StandardCharsets.UTF_8),
                            "WebAppData".getBytes(StandardCharsets.UTF_8));

            // Compute hash: HMAC_SHA256(data=dataCheckString, key=secretKey)
            byte[] computedHash =
                    hmacSha256(dataCheckString.getBytes(StandardCharsets.UTF_8), secretKey);

            String hash = HexFormat.of().formatHex(computedHash);

            // Build final query string with URL-encoded values
            return "auth_date="
                    + authDate
                    + "&query_id="
                    + URLEncoder.encode("test_query_123", StandardCharsets.UTF_8)
                    + "&user="
                    + URLEncoder.encode(userJson, StandardCharsets.UTF_8)
                    + "&hash="
                    + hash;
        } catch (Exception e) {
            throw new RuntimeException("Failed to build valid initData", e);
        }
    }

    @Test
    @ResourceLock("java.util.Locale.default")
    void tmaReadsExistingUserUnderTurkishLocale() {
        Locale previous = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            var response =
                    restTemplate.postForEntity(
                            "/auth/tma",
                            new TmaAuthRequest(buildValidInitData(123456789L, BOT_TOKEN)),
                            TokenResponse.class);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().accessToken()).isNotBlank();
        } finally {
            Locale.setDefault(previous);
        }
    }

    @Test
    void replayReadsChangedGrantsInsteadOfReissuingPreviousSelectedAuthority() {
        var request = new TmaAuthRequest(buildValidInitData(123456789L, BOT_TOKEN));
        var first = restTemplate.postForEntity("/auth/tma", request, TokenResponse.class);
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.OK);
        long userId =
                jdbc.queryForObject("SELECT id FROM users WHERE login = 'student'", Long.class);
        var grants =
                jdbc.queryForList(
                        "SELECT id, status FROM user_role_grants WHERE user_id = ?", userId);
        try {
            jdbc.update(
                    "UPDATE user_role_grants SET status = 'suspended' WHERE user_id = ?", userId);
            jdbc.update("UPDATE users SET roles_version = roles_version + 1 WHERE id = ?", userId);
            var replay = restTemplate.postForEntity("/auth/tma", request, TokenResponse.class);
            assertThat(replay.getStatusCode()).isEqualTo(HttpStatus.OK);
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(replay.getBody().accessToken());
            var current =
                    restTemplate.exchange(
                            "/auth/session",
                            HttpMethod.GET,
                            new HttpEntity<>(headers),
                            CurrentSessionResponse.class);
            assertThat(current.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(current.getBody().activeRole()).isNull();
            assertThat(current.getBody().roles())
                    .allSatisfy(grant -> assertThat(grant.status()).isEqualTo("SUSPENDED"));
        } finally {
            for (var grant : grants)
                jdbc.update(
                        "UPDATE user_role_grants SET status = ? WHERE id = ?",
                        grant.get("status"),
                        grant.get("id"));
            jdbc.update("UPDATE users SET roles_version = roles_version + 1 WHERE id = ?", userId);
        }
    }

    private String signParameters(Map<String, String> parameters, String botToken)
            throws Exception {
        String data =
                parameters.entrySet().stream()
                        .sorted(Map.Entry.comparingByKey())
                        .map(entry -> entry.getKey() + "=" + entry.getValue())
                        .collect(Collectors.joining("\n"));
        byte[] key =
                hmacSha256(
                        botToken.getBytes(StandardCharsets.UTF_8),
                        "WebAppData".getBytes(StandardCharsets.UTF_8));
        String payload =
                parameters.entrySet().stream()
                        .map(
                                entry ->
                                        entry.getKey()
                                                + "="
                                                + URLEncoder.encode(
                                                        entry.getValue(), StandardCharsets.UTF_8))
                        .collect(Collectors.joining("&"));
        return payload
                + "&hash="
                + HexFormat.of().formatHex(hmacSha256(data.getBytes(StandardCharsets.UTF_8), key));
    }

    private byte[] hmacSha256(byte[] data, byte[] key) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        return mac.doFinal(data);
    }
}
