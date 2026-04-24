package ru.rutcampustrack.auth.integration;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.jdbc.Sql;
import ru.rutcampustrack.auth.dto.RefreshRequest;
import ru.rutcampustrack.auth.dto.TmaAuthRequest;
import ru.rutcampustrack.auth.dto.TokenResponse;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.context.jdbc.Sql.ExecutionPhase.AFTER_TEST_METHOD;

@Tag("security-contract")
@Sql(scripts = "classpath:sql/set-telegram-id.sql")
@Sql(scripts = "classpath:sql/clear-telegram-id.sql", executionPhase = AFTER_TEST_METHOD)
class TmaIT extends AbstractIntegrationTest {

    private static final String BOT_TOKEN = "test_bot_token_12345";

    @Autowired
    TestRestTemplate restTemplate;

    @Test
    void tma_withValidInitData_returnsTokenPair() {
        String initData = buildValidInitData(123456789L, BOT_TOKEN);
        TmaAuthRequest request = new TmaAuthRequest(initData);
        ResponseEntity<TokenResponse> response = restTemplate.postForEntity(
                "/auth/tma", request, TokenResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().accessToken()).isNotBlank();
        assertThat(response.getBody().refreshToken()).isNotBlank();
        assertThat(response.getBody().expiresIn()).isGreaterThan(0);
    }

    @Test
    void tma_withTamperedHash_returns401() {
        String initData = "auth_date=" + (System.currentTimeMillis() / 1000)
                + "&user=" + URLEncoder.encode("{\"id\":123456789}", StandardCharsets.UTF_8)
                + "&hash=deadbeefdeadbeef";
        TmaAuthRequest request = new TmaAuthRequest(initData);
        ResponseEntity<String> response = restTemplate.postForEntity(
                "/auth/tma", request, String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getHeaders().getContentType().toString()).contains("application/problem+json");
    }

    @Test
    void tma_withExpiredAuthDate_returns401() {
        // auth_date from 2 days ago (exceeds 86400 max age)
        long expiredAuthDate = System.currentTimeMillis() / 1000 - 172800;
        String initData = buildValidInitDataWithAuthDate(123456789L, BOT_TOKEN, expiredAuthDate);
        TmaAuthRequest request = new TmaAuthRequest(initData);
        ResponseEntity<String> response = restTemplate.postForEntity(
                "/auth/tma", request, String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void tma_withUnlinkedTelegramId_returns401() {
        // telegram_id 999999999 is not in DB
        String initData = buildValidInitData(999999999L, BOT_TOKEN);
        TmaAuthRequest request = new TmaAuthRequest(initData);
        ResponseEntity<String> response = restTemplate.postForEntity(
                "/auth/tma", request, String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    /**
     * M08 G8 security contract (NEW-164) — replay with same initData and
     * hash is accepted as long as auth_date is within {@code tma.auth-date-max-age-seconds}
     * window. Telegram docs explicitly allow replays up to 24h, so we verify the
     * window enforcement is in place rather than a per-hash one-shot guard.
     *
     * <p>Captured in docs/testing.md — Security contract tests: TMA replay semantics.
     */
    @Test
    void tma_withReplayedInitDataInsideWindow_returnsToken() {
        String initData = buildValidInitData(123456789L, BOT_TOKEN);
        TmaAuthRequest request = new TmaAuthRequest(initData);

        ResponseEntity<TokenResponse> firstResponse = restTemplate.postForEntity(
                "/auth/tma", request, TokenResponse.class);
        assertThat(firstResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Exact same initData, same hash, same auth_date → still valid (by design)
        ResponseEntity<TokenResponse> replayResponse = restTemplate.postForEntity(
                "/auth/tma", request, TokenResponse.class);
        assertThat(replayResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(replayResponse.getBody()).isNotNull();
        // New token pair each time — jti rotates, old refresh tokens remain valid
        // independently (replay-protection lives at refresh-token level, not initData)
        assertThat(replayResponse.getBody().refreshToken())
                .isNotEqualTo(firstResponse.getBody().refreshToken());
    }

    /**
     * M08 G8 security contract (NEW-164) — an initData with a bit-flipped
     * signature is rejected even though the payload is otherwise identical to
     * a valid request. Protects against an attacker who snooped a hash and
     * tries to forge variants.
     */
    @Test
    void tma_withBitFlippedSignature_returns401() {
        String validInitData = buildValidInitData(123456789L, BOT_TOKEN);
        // Flip the first hex char of the hash — still syntactically valid length,
        // but HMAC comparison must fail via MessageDigest.isEqual (constant-time).
        int hashIdx = validInitData.lastIndexOf("&hash=") + "&hash=".length();
        char firstHashChar = validInitData.charAt(hashIdx);
        char flipped = firstHashChar == '0' ? '1' : '0';
        String tampered = validInitData.substring(0, hashIdx) + flipped + validInitData.substring(hashIdx + 1);

        TmaAuthRequest request = new TmaAuthRequest(tampered);
        ResponseEntity<String> response = restTemplate.postForEntity(
                "/auth/tma", request, String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    /**
     * M08 G8 security contract (NEW-164) — an initData signed with a
     * different bot token is rejected. Protects against an attacker who
     * gained control of a different bot in the same namespace.
     */
    @Test
    void tma_withDifferentBotTokenSignature_returns401() {
        // Sign with an attacker's bot token, not the one auth-service trusts
        String initData = buildValidInitData(123456789L, "attacker_bot_token_67890");
        TmaAuthRequest request = new TmaAuthRequest(initData);

        ResponseEntity<String> response = restTemplate.postForEntity(
                "/auth/tma", request, String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    /**
     * M08 G8 security contract (NEW-164) — missing `hash` field in initData
     * is rejected (not silently treated as "no hash required").
     */
    @Test
    void tma_withMissingHashField_returns401() {
        long authDate = System.currentTimeMillis() / 1000;
        String initData = "auth_date=" + authDate
                + "&user=" + URLEncoder.encode("{\"id\":123456789}", StandardCharsets.UTF_8);
        TmaAuthRequest request = new TmaAuthRequest(initData);

        ResponseEntity<String> response = restTemplate.postForEntity(
                "/auth/tma", request, String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void refreshBody_withValidToken_returnsNewPair() {
        // First authenticate via TMA to get a refresh token
        String initData = buildValidInitData(123456789L, BOT_TOKEN);
        ResponseEntity<TokenResponse> tmaResponse = restTemplate.postForEntity(
                "/auth/tma", new TmaAuthRequest(initData), TokenResponse.class);
        assertThat(tmaResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(tmaResponse.getBody()).isNotNull();
        String refreshToken = tmaResponse.getBody().refreshToken();

        // Use refresh-body endpoint
        RefreshRequest refreshRequest = new RefreshRequest(refreshToken);
        ResponseEntity<TokenResponse> response = restTemplate.postForEntity(
                "/auth/refresh-body", refreshRequest, TokenResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().accessToken()).isNotBlank();
        assertThat(response.getBody().refreshToken()).isNotBlank();
    }

    @Test
    void refreshBody_withInvalidToken_returns401() {
        RefreshRequest request = new RefreshRequest("invalid_refresh_token");
        ResponseEntity<String> response = restTemplate.postForEntity(
                "/auth/refresh-body", request, String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // --- Helper methods ---

    private String buildValidInitData(long telegramId, String botToken) {
        return buildValidInitDataWithAuthDate(telegramId, botToken, System.currentTimeMillis() / 1000);
    }

    private String buildValidInitDataWithAuthDate(long telegramId, String botToken, long authDate) {
        try {
            String userJson = "{\"id\":" + telegramId + ",\"first_name\":\"Test\",\"username\":\"test_user\"}";

            Map<String, String> params = new LinkedHashMap<>();
            params.put("auth_date", String.valueOf(authDate));
            params.put("query_id", "test_query_123");
            params.put("user", userJson);

            // Sort by key to build data-check-string
            String dataCheckString = params.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .map(e -> e.getKey() + "=" + e.getValue())
                    .collect(Collectors.joining("\n"));

            // Compute secret key: HMAC_SHA256(data=botToken, key="WebAppData")
            byte[] secretKey = hmacSha256(
                    botToken.getBytes(StandardCharsets.UTF_8),
                    "WebAppData".getBytes(StandardCharsets.UTF_8));

            // Compute hash: HMAC_SHA256(data=dataCheckString, key=secretKey)
            byte[] computedHash = hmacSha256(
                    dataCheckString.getBytes(StandardCharsets.UTF_8),
                    secretKey);

            String hash = HexFormat.of().formatHex(computedHash);

            // Build final query string with URL-encoded values
            return "auth_date=" + authDate
                    + "&query_id=" + URLEncoder.encode("test_query_123", StandardCharsets.UTF_8)
                    + "&user=" + URLEncoder.encode(userJson, StandardCharsets.UTF_8)
                    + "&hash=" + hash;
        } catch (Exception e) {
            throw new RuntimeException("Failed to build valid initData", e);
        }
    }

    private byte[] hmacSha256(byte[] data, byte[] key) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        return mac.doFinal(data);
    }
}
