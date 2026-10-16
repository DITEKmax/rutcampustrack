package ru.rutcampustrack.auth.integration;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxyUtil;
import ch.qos.logback.core.read.ListAppender;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.*;
import ru.rutcampustrack.auth.security.InternalIssuerSecretFilter;

class ReportProofValidationIT extends AbstractIntegrationTest {
    @Autowired TestRestTemplate http;
    @Autowired StringRedisTemplate redis;

    @Test
    void internalRedeemRejectsMalformedProofWithoutEchoOrStateEffects() throws Exception {
        String marker = "RCT_INVALID_REPORT_PROOF_SENTINEL_" + UUID.randomUUID();
        String route = "/internal/report-download-tickets/redeem";
        var body = Map.of("ticket", marker);
        String digest =
                HexFormat.of()
                        .formatHex(
                                MessageDigest.getInstance("SHA-256")
                                        .digest(marker.getBytes(StandardCharsets.UTF_8)));
        var canaryKeys =
                List.of(
                        "report_download_ticket:" + digest,
                        "report_download_ticket_redemption:" + digest);
        var expectedValues = List.of("owned-ticket-canary", "1");
        var ownedKeys = new ArrayList<String>();
        Throwable primaryFailure = null;
        Logger logger = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        ListAppender<ILoggingEvent> logs = new ListAppender<>();
        logs.start();
        logger.addAppender(logs);
        try {
            for (int index = 0; index < canaryKeys.size(); index++) {
                assertThat(
                                redis.opsForValue()
                                        .setIfAbsent(
                                                canaryKeys.get(index), expectedValues.get(index)))
                        .as("request-owned report canary is unused")
                        .isTrue();
                ownedKeys.add(canaryKeys.get(index));
            }
            var anonymous = http.postForEntity(route, body, String.class);
            assertThat(anonymous.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
            assertThat(anonymous.getBody().contains(marker)).isFalse();
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set(
                    InternalIssuerSecretFilter.HEADER,
                    "test-internal-issuer-secret-32-bytes-or-more-for-test-env");
            var response = http.postForEntity(route, new HttpEntity<>(body, headers), String.class);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(response.getHeaders().getCacheControl()).contains("no-store");
            assertThat(redis.opsForValue().multiGet(canaryKeys))
                    .containsExactlyElementsOf(expectedValues);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().contains(marker))
                    .as("rejected report proof must not be echoed")
                    .isFalse();
            boolean logged =
                    logs.list.stream()
                            .anyMatch(
                                    event ->
                                            event.getFormattedMessage().contains(marker)
                                                    || (event.getThrowableProxy() != null
                                                            && ThrowableProxyUtil.asString(
                                                                            event
                                                                                    .getThrowableProxy())
                                                                    .contains(marker)));
            assertThat(logged).as("rejected report proof must not be logged").isFalse();
        } catch (RuntimeException | Error failure) {
            primaryFailure = failure;
            throw failure;
        } finally {
            logger.detachAppender(logs);
            logs.stop();
            try {
                if (!ownedKeys.isEmpty()) redis.delete(ownedKeys);
            } catch (RuntimeException | Error cleanupFailure) {
                if (primaryFailure != null) primaryFailure.addSuppressed(cleanupFailure);
                else throw cleanupFailure;
            }
        }
    }
}
