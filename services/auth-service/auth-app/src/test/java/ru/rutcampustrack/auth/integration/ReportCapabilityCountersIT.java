package ru.rutcampustrack.auth.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;

import ru.rutcampustrack.auth.authority.AuthReportCapabilityService;
import ru.rutcampustrack.auth.dto.LoginRequest;
import ru.rutcampustrack.auth.dto.TokenResponse;
import ru.rutcampustrack.auth.service.JwtService;
import ru.rutcampustrack.authsecurity.grpc.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;
import java.util.concurrent.*;

class ReportCapabilityCountersIT extends AbstractIntegrationTest {
    @Autowired private AuthReportCapabilityService service;
    @Autowired private TestRestTemplate rest;
    @Autowired private JwtService jwt;
    @Autowired private StringRedisTemplate redis;

    @Test
    void issueTenAndRedeemTwentyLimitsRemainPerSessionAndTicket() throws Exception {
        var first = identity();
        var second = identity();
        assertThat(first.getSessionId()).isNotEqualTo(second.getSessionId());
        var one = issue(first);
        var two = issue(second);
        for (int n = 1; n < 8; n++) {
            issue(first);
            issue(second);
        }
        assertThat(race(() -> issue(first))).isEqualTo(2);
        assertThat(race(() -> issue(second))).isEqualTo(2);
        assertLimit(() -> issue(first));
        assertLimit(() -> issue(second));
        String issueKey =
                "report_download_ticket_issue:"
                        + digest(first.getUserId() + ":" + first.getSessionId());
        assertCounter(issueKey, "10");
        for (int n = 0; n < 18; n++) {
            redeem(one);
            redeem(two);
        }
        assertThat(race(() -> redeem(one))).isEqualTo(2);
        assertThat(race(() -> redeem(two))).isEqualTo(2);
        assertLimit(() -> redeem(one));
        assertLimit(() -> redeem(two));
        assertCounter("report_download_ticket_redemption:" + digest(one.getTicket()), "20");
        assertCounter("report_download_ticket_redemption:" + digest(two.getTicket()), "20");
        assertCounter(issueKey, "10");
    }

    private StoredSessionIdentity identity() {
        var response =
                rest.postForEntity(
                        "/auth/login",
                        new LoginRequest("teacher", "password"),
                        TokenResponse.class);
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isNotNull();
        var claims = jwt.parseAccessToken(response.getBody().accessToken()).getPayload();
        return StoredSessionIdentity.newBuilder()
                .setUserId(Long.parseLong(claims.getSubject()))
                .setSessionId(claims.get("sid", String.class))
                .setSessionVersion(Long.parseLong(claims.get("sv", String.class)))
                .setRolesVersion(Long.parseLong(claims.get("rv", String.class)))
                .setRole("TEACHER")
                .setStatus("ACTIVE")
                .build();
    }

    private IssuedReportCapability issue(StoredSessionIdentity identity) {
        return service.issue(
                IssueReportCapabilityRequest.newBuilder()
                        .setOwnerResourceId(UUID.randomUUID().toString())
                        .setAuthority(
                                AdmitStoredSessionRequest.newBuilder()
                                        .setIdentity(identity)
                                        .setReportKind("TEACHER_JOURNAL")
                                        .setSelectorHash("a".repeat(64))
                                        .setNotAfter(Instant.now().plusSeconds(600).toString()))
                        .build());
    }

    private RedeemedReportCapability redeem(IssuedReportCapability issued) {
        return service.redeem(
                RedeemReportCapabilityRequest.newBuilder().setTicket(issued.getTicket()).build());
    }

    private void assertCounter(String key, String expected) {
        assertThat(redis.opsForValue().get(key)).isEqualTo(expected);
        assertThat(redis.getExpire(key, TimeUnit.SECONDS)).isBetween(1L, 60L);
    }

    private static void assertLimit(Callable<?> action) {
        assertThatThrownBy(action::call)
                .isInstanceOfSatisfying(
                        StatusRuntimeException.class,
                        error ->
                                assertThat(error.getStatus().getCode())
                                        .isEqualTo(Status.Code.RESOURCE_EXHAUSTED));
    }

    private static long race(Callable<?> action) throws Exception {
        var start = new CountDownLatch(1);
        var ready = new CountDownLatch(4);
        var pool = Executors.newFixedThreadPool(4);
        try {
            var futures = new java.util.ArrayList<Future<Boolean>>();
            for (int n = 0; n < 4; n++)
                futures.add(
                        pool.submit(
                                () -> {
                                    ready.countDown();
                                    assertThat(start.await(5, TimeUnit.SECONDS)).isTrue();
                                    try {
                                        action.call();
                                        return true;
                                    } catch (StatusRuntimeException error) {
                                        assertThat(error.getStatus().getCode())
                                                .isEqualTo(Status.Code.RESOURCE_EXHAUSTED);
                                        return false;
                                    }
                                }));
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            long winners = 0;
            for (var future : futures) if (future.get(10, TimeUnit.SECONDS)) winners++;
            return winners;
        } finally {
            start.countDown();
            pool.shutdownNow();
            assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    private static String digest(String value) throws Exception {
        return HexFormat.of()
                .formatHex(
                        MessageDigest.getInstance("SHA-256")
                                .digest(value.getBytes(StandardCharsets.UTF_8)));
    }
}
