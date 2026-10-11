package ru.rutcampustrack.auth.integration;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxyUtil;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.*;
import ru.rutcampustrack.auth.security.InternalIssuerSecretFilter;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;

class ReportProofValidationIT extends AbstractIntegrationTest {
    @Autowired TestRestTemplate http;
    @Autowired StringRedisTemplate redis;

    @Test
    void internalRedeemRejectsMalformedProofWithoutEchoOrStateEffects() {
        String marker = "RCT_INVALID_REPORT_PROOF_SENTINEL";
        String route = "/internal/report-download-tickets/redeem";
        var body = Map.of("ticket", marker);
        var before = redis.keys("*");
        Logger logger = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        ListAppender<ILoggingEvent> logs = new ListAppender<>();
        logs.start();
        logger.addAppender(logs);
        try {
            var anonymous = http.postForEntity(route, body, String.class);
            assertThat(anonymous.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
            assertThat(anonymous.getBody().contains(marker)).isFalse();
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set(InternalIssuerSecretFilter.HEADER,
                    "test-internal-issuer-secret-32-bytes-or-more-for-test-env");
            var response = http.postForEntity(route, new HttpEntity<>(body, headers), String.class);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(response.getHeaders().getCacheControl()).contains("no-store");
            assertThat(redis.keys("*")).isEqualTo(before);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().contains(marker)).as("rejected report proof must not be echoed").isFalse();
            boolean logged = logs.list.stream().anyMatch(event -> event.getFormattedMessage().contains(marker)
                    || (event.getThrowableProxy() != null
                        && ThrowableProxyUtil.asString(event.getThrowableProxy()).contains(marker)));
            assertThat(logged).as("rejected report proof must not be logged").isFalse();
        } finally {
            logger.detachAppender(logs);
            logs.stop();
        }
    }
}
