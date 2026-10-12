package ru.rutcampustrack.auth.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import ru.rutcampustrack.auth.dto.ConsumeWsTicketRequest;
import ru.rutcampustrack.auth.dto.ConsumeWsTicketResponse;
import ru.rutcampustrack.auth.dto.LoginRequest;
import ru.rutcampustrack.auth.dto.TokenResponse;
import ru.rutcampustrack.auth.dto.WsTicketResponse;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/** NEW race/real expiry/logout boundaries, separate from existing sequential ticket checks. */
class WsTicketConcurrencyBoundaryIT extends AbstractIntegrationTest {

    private static final String INTERNAL_SECRET =
            "test-internal-issuer-secret-32-bytes-or-more-for-test-env";

    @Autowired TestRestTemplate http;
    @Autowired StringRedisTemplate redis;

    @Test
    void concurrentActualHttpConsumersHaveExactlyOneWinnerAndBurnTicket() throws Exception {
        String access = login();
        try {
            String ticket = issue(access).ticket();
            var ready = new CountDownLatch(2);
            var start = new CountDownLatch(1);
            try (var workers = Executors.newFixedThreadPool(2)) {
                var first = workers.submit(() -> {
                    ready.countDown();
                    assertThat(start.await(5, TimeUnit.SECONDS)).isTrue();
                    return consume(ticket);
                });
                var second = workers.submit(() -> {
                    ready.countDown();
                    assertThat(start.await(5, TimeUnit.SECONDS)).isTrue();
                    return consume(ticket);
                });
                assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
                start.countDown();
                assertThat(List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS)))
                        .containsExactlyInAnyOrder(HttpStatus.OK, HttpStatus.NOT_FOUND);
            }
            assertThat(redis.hasKey("ws_ticket:" + ticket)).isFalse();
            assertThat(consume(ticket)).isEqualTo(HttpStatus.NOT_FOUND);
        } finally {
            logout(access);
        }
    }

    @Test
    void actualRedisThirtySecondExpiryRejectsUnconsumedTicket() {
        String access = login();
        try {
            // Independent accepted lifetime: never read the mutable production TTL constant.
            Duration expectedLifetime = Duration.ofSeconds(30);
            Duration observationBudget = Duration.ofSeconds(5);
            Duration observationSlack = Duration.ofMillis(250);
            long observationStarted = System.nanoTime();
            Instant issuanceStarted = Instant.now();
            WsTicketResponse issued = issue(access);
            Instant issuanceReturned = Instant.now();
            Long remaining = redis.getExpire("ws_ticket:" + issued.ticket(), TimeUnit.MILLISECONDS);
            long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - observationStarted);
            assertThat(elapsedMillis).as("bounded issuance-to-Redis observation interval")
                    .isLessThanOrEqualTo(observationBudget.toMillis());
            long minimumRemaining = expectedLifetime.toMillis() - elapsedMillis - observationSlack.toMillis();
            System.out.printf("WS ticket TTL remaining_ms=%d issue_read_ms=%d minimum_ms=%d%n",
                    remaining, elapsedMillis, minimumRemaining);
            assertThat(issued.expiresAt()).as("advertised independent 30-second issuance window")
                    .isBetween(issuanceStarted.plus(expectedLifetime).minus(observationSlack),
                            issuanceReturned.plus(expectedLifetime).plus(observationSlack));
            assertThat(remaining).as("Redis retains the accepted 30-second issuance window")
                    .isBetween(minimumRemaining, expectedLifetime.toMillis());
            // Real Redis/security clock; no shortened production TTL or teaching Clock.
            await().atMost(Duration.ofSeconds(32)).pollInterval(Duration.ofMillis(100))
                    .until(() -> !Boolean.TRUE.equals(redis.hasKey("ws_ticket:" + issued.ticket())));
            assertThat(consume(issued.ticket())).isEqualTo(HttpStatus.NOT_FOUND);
        } finally {
            logout(access);
        }
    }

    @Test
    void logoutBeforeConsumeInvalidatesOutstandingTicket() {
        String access = login();
        String ticket = issue(access).ticket();
        logout(access);
        assertThat(consume(ticket)).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(redis.hasKey("ws_ticket:" + ticket)).isFalse();
    }

    private String login() {
        var response = http.postForEntity("/auth/login", new LoginRequest("student", "password"), TokenResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody() != null && response.getBody().accessToken() != null).isTrue();
        return response.getBody().accessToken();
    }

    private WsTicketResponse issue(String access) {
        var headers = new HttpHeaders();
        headers.setBearerAuth(access);
        var response = http.exchange("/auth/ws-ticket", HttpMethod.POST,
                new HttpEntity<>(null, headers), WsTicketResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody() != null).isTrue();
        return response.getBody();
    }

    private HttpStatus consume(String ticket) {
        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Internal-Issuer-Secret", INTERNAL_SECRET);
        var response = http.exchange("/internal/consume-ws-ticket", HttpMethod.POST,
                new HttpEntity<>(new ConsumeWsTicketRequest(ticket), headers), ConsumeWsTicketResponse.class);
        if (response.getStatusCode() == HttpStatus.OK) {
            assertThat(response.getBody() != null && response.getBody().userId() > 0).isTrue();
        }
        return HttpStatus.valueOf(response.getStatusCode().value());
    }

    private void logout(String access) {
        var headers = new HttpHeaders();
        headers.setBearerAuth(access);
        var response = http.exchange("/auth/logout", HttpMethod.POST, new HttpEntity<>(null, headers), Void.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }
}
