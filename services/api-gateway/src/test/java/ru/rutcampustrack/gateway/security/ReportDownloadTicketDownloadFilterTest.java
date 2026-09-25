package ru.rutcampustrack.gateway.security;

import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import ru.rutcampustrack.auth.dto.AuthAdmissionResponse;
import ru.rutcampustrack.auth.dto.IssueReportDownloadTicketRequest;
import ru.rutcampustrack.auth.dto.ReportDownloadFormat;
import ru.rutcampustrack.auth.dto.ReportDownloadKind;
import ru.rutcampustrack.auth.dto.ReportDownloadTicketRedemptionResponse;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.absent;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.github.tomakehurst.wiremock.WireMockServer;

class ReportDownloadTicketDownloadFilterTest {

    private static final String TICKET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopq";
    private static final String INTERNAL_TOKEN = "signed-short-lived-internal-token";

    @Test
    void headmanStatsUsesFixedAttendanceRouteAndReturnsBoundAttachmentWithTelegramCors() {
        WireMockServer attendance = server();
        attendance.start();
        try {
            byte[] file = new byte[]{7, 8, 9};
            attendance.stubFor(post(urlEqualTo("/attendance/reports/headman/stats/export"))
                    .withHeader("X-Internal-Token", equalTo(INTERNAL_TOKEN))
                    .withHeader(HttpHeaders.AUTHORIZATION, absent())
                    .withHeader("X-User-Id", absent())
                    .withHeader("X-User-Role", absent())
                    .withHeader("X-Group-Id", absent())
                    .withHeader("X-Is-Headman", absent())
                    .withRequestBody(matchingJsonPath("$.subjectId", equalTo("41")))
                    .withRequestBody(matchingJsonPath("$.lessonTypes[0]", equalTo("LECTURE")))
                    .withRequestBody(matchingJsonPath("$.sorts[0].field", equalTo("presentPercent")))
                    .withRequestBody(matchingJsonPath("$.sorts[0].descending", equalTo("true")))
                    .withRequestBody(matchingJsonPath("$.filters[0].contains", equalTo("Ada")))
                    .withRequestBody(matchingJsonPath("$.format", equalTo("xlsx")))
                    .willReturn(aResponse().withStatus(200)
                            .withHeader(HttpHeaders.CONTENT_TYPE, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                            .withBody(file)));

            var auth = mock(InternalJwtIssuerClient.class);
            var verifier = mock(InternalJwtIssuerFilter.class);
            var attemptRateLimiter = allowedAttemptRateLimiter();
            var properties = properties(attendance.baseUrl());
            var filter = new ReportDownloadTicketDownloadFilter(auth, verifier, properties, attemptRateLimiter,
                    WebClient.builder().baseUrl("http://127.0.0.1:1").build(),
                    WebClient.builder().baseUrl(attendance.baseUrl()).build());
            var report = new IssueReportDownloadTicketRequest(
                    ReportDownloadKind.HEADMAN_STATS, null, null, null, null,
                    new IssueReportDownloadTicketRequest.HeadmanStatsParameters(
                            41L, List.of("LECTURE"),
                            List.of(new IssueReportDownloadTicketRequest.HeadmanStatsSort("presentPercent", true)),
                            List.of(new IssueReportDownloadTicketRequest.HeadmanStatsFilter(
                                    "displayName", "Ada", null, null)),
                            ReportDownloadFormat.XLSX));
            when(auth.redeemReportTicket(TICKET)).thenReturn(Mono.just(Optional.of(redemption(report))));

            var request = MockServerHttpRequest.get("/api/report-download/" + TICKET)
                    .header(HttpHeaders.ORIGIN, "https://web.telegram.org")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer caller-access-token")
                    .header("X-User-Id", "999")
                    .header("X-User-Role", "ADMIN")
                    .header("X-Group-Id", "999")
                    .header("X-Is-Headman", "false")
                    .build();
            MockServerWebExchange exchange = MockServerWebExchange.from(request);
            StepVerifier.create(filter.filter(exchange, mock(GatewayFilterChain.class))).verifyComplete();

            assertEquals(HttpStatus.OK, exchange.getResponse().getStatusCode());
            assertEquals("no-store", exchange.getResponse().getHeaders().getCacheControl());
            assertEquals("no-referrer", exchange.getResponse().getHeaders().getFirst("Referrer-Policy"));
            assertEquals("https://web.telegram.org",
                    exchange.getResponse().getHeaders().getFirst(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
            assertTrue(exchange.getResponse().getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION)
                    .contains("headman-stats.xlsx"));
            assertArrayEquals(file, responseBytes(exchange));
            verify(verifier).verifyInternalReportDownloadToken(
                    org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                    org.mockito.ArgumentMatchers.anyString());
            attendance.verify(postRequestedFor(urlEqualTo("/attendance/reports/headman/stats/export")));
        } finally {
            attendance.stop();
        }
    }

    @Test
    void headmanTrendPngUsesFixedAttendanceRouteAndRealImageMime() {
        WireMockServer attendance = server();
        attendance.start();
        try {
            byte[] png = new byte[]{(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10, 0};
            attendance.stubFor(post(urlEqualTo("/attendance/reports/headman/stats/trend/export"))
                    .withHeader("X-Internal-Token", equalTo(INTERNAL_TOKEN))
                    .withHeader(HttpHeaders.AUTHORIZATION, absent())
                    .withRequestBody(matchingJsonPath("$.query.mode", equalTo("SUBJECT")))
                    .withRequestBody(matchingJsonPath("$.query.subjectId", equalTo("41")))
                    .withRequestBody(matchingJsonPath("$.query.lessonTypes[0]", equalTo("LECTURE")))
                    .withRequestBody(matchingJsonPath("$.format", equalTo("png")))
                    .willReturn(aResponse().withStatus(200)
                            .withHeader(HttpHeaders.CONTENT_TYPE, "image/png")
                            .withBody(png)));

            var auth = mock(InternalJwtIssuerClient.class);
            var filter = new ReportDownloadTicketDownloadFilter(auth, mock(InternalJwtIssuerFilter.class),
                    properties(attendance.baseUrl()), allowedAttemptRateLimiter(),
                    WebClient.builder().baseUrl("http://127.0.0.1:1").build(),
                    WebClient.builder().baseUrl(attendance.baseUrl()).build());
            var report = new IssueReportDownloadTicketRequest(ReportDownloadKind.HEADMAN_STATS_TREND,
                    null, null, null, null, null,
                    new IssueReportDownloadTicketRequest.HeadmanStatsTrendParameters(
                            IssueReportDownloadTicketRequest.HeadmanStatsTrendMode.SUBJECT,
                            null, 41L, List.of("LECTURE"), ReportDownloadFormat.PNG));
            when(auth.redeemReportTicket(TICKET)).thenReturn(Mono.just(Optional.of(redemption(report))));

            MockServerWebExchange exchange = exchange(HttpMethod.GET, "/api/report-download/" + TICKET,
                    false, null);
            StepVerifier.create(filter.filter(exchange, mock(GatewayFilterChain.class))).verifyComplete();

            assertEquals(HttpStatus.OK, exchange.getResponse().getStatusCode());
            assertEquals(MediaType.parseMediaType("image/png"), exchange.getResponse().getHeaders().getContentType());
            assertTrue(exchange.getResponse().getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION)
                    .contains("headman-stats-trend.png"));
            assertArrayEquals(png, responseBytes(exchange));
            String body = attendance.getAllServeEvents().get(0).getRequest().getBodyAsString();
            assertFalse(body.contains("groupId"));
            attendance.verify(postRequestedFor(urlEqualTo("/attendance/reports/headman/stats/trend/export")));
        } finally {
            attendance.stop();
        }
    }

    @Test
    void teacherJournalUsesOnlyFixedBffExportAndStoredQuery() {
        WireMockServer mobile = server();
        mobile.start();
        try {
            mobile.stubFor(com.github.tomakehurst.wiremock.client.WireMock.get(
                            urlEqualTo("/api/v1/teacher/journal/export?semesterId=5&groupId=31&subjectId=8&lessonType=LECTURE&lessonType=LAB&format=pdf"))
                    .withHeader("X-Internal-Token", equalTo(INTERNAL_TOKEN))
                    .withHeader(HttpHeaders.AUTHORIZATION, absent())
                    .willReturn(aResponse().withStatus(200)
                            .withHeader(HttpHeaders.CONTENT_TYPE, "application/pdf")
                            .withBody("%PDF-test")));
            var auth = mock(InternalJwtIssuerClient.class);
            var attemptRateLimiter = allowedAttemptRateLimiter();
            var filter = new ReportDownloadTicketDownloadFilter(auth, mock(InternalJwtIssuerFilter.class),
                    properties(mobile.baseUrl()), attemptRateLimiter,
                    WebClient.builder().baseUrl(mobile.baseUrl()).build(),
                    WebClient.builder().baseUrl("http://127.0.0.1:1").build());
            var report = new IssueReportDownloadTicketRequest(
                    ReportDownloadKind.TEACHER_JOURNAL,
                    new IssueReportDownloadTicketRequest.TeacherJournalParameters(
                            5L, 31L, 8L, List.of("LECTURE", "LAB"), ReportDownloadFormat.PDF),
                    null, null, null, null);
            when(auth.redeemReportTicket(TICKET)).thenReturn(Mono.just(Optional.of(redemption(report))));

            MockServerWebExchange exchange = exchange(HttpMethod.GET, "/api/report-download/" + TICKET,
                    false, null);
            StepVerifier.create(filter.filter(exchange, mock(GatewayFilterChain.class))).verifyComplete();

            assertEquals(HttpStatus.OK, exchange.getResponse().getStatusCode());
            assertTrue(exchange.getResponse().getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION)
                    .contains("teacher-journal.pdf"));
            mobile.verify(com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor(urlEqualTo(
                    "/api/v1/teacher/journal/export?semesterId=5&groupId=31&subjectId=8&lessonType=LECTURE&lessonType=LAB&format=pdf")));
        } finally {
            mobile.stop();
        }
    }

    @Test
    void teacherStatsHtmlUsesDynamicAcceptAndStillValidatesActualMediaType() {
        WireMockServer mobile = server();
        mobile.start();
        try {
            byte[] html = "<!doctype html><html>teacher stats</html>"
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8);
            String path = "/api/v1/teacher/stats/export?semesterId=2&scope=students&groupId=2"
                    + "&subjectId=1&lessonType=lecture&format=html";
            mobile.stubFor(com.github.tomakehurst.wiremock.client.WireMock.get(urlEqualTo(path))
                    .withHeader("X-Internal-Token", equalTo(INTERNAL_TOKEN))
                    .withHeader(HttpHeaders.ACCEPT, equalTo("*/*"))
                    .willReturn(aResponse().withStatus(200)
                            .withHeader(HttpHeaders.CONTENT_TYPE, "text/html")
                            .withBody(html)));

            var auth = mock(InternalJwtIssuerClient.class);
            var filter = new ReportDownloadTicketDownloadFilter(auth, mock(InternalJwtIssuerFilter.class),
                    properties(mobile.baseUrl()), allowedAttemptRateLimiter(),
                    WebClient.builder().baseUrl(mobile.baseUrl()).build(),
                    WebClient.builder().baseUrl("http://127.0.0.1:1").build());
            when(auth.redeemReportTicket(TICKET))
                    .thenReturn(Mono.just(Optional.of(redemption(teacherStatsHtmlReport()))));

            MockServerWebExchange exchange = exchange(HttpMethod.GET, "/api/report-download/" + TICKET,
                    false, null);
            StepVerifier.create(filter.filter(exchange, mock(GatewayFilterChain.class))).verifyComplete();

            assertEquals(HttpStatus.OK, exchange.getResponse().getStatusCode());
            assertEquals(MediaType.TEXT_HTML, exchange.getResponse().getHeaders().getContentType());
            assertArrayEquals(html, responseBytes(exchange));
            mobile.verify(com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor(urlEqualTo(path))
                    .withHeader(HttpHeaders.ACCEPT, equalTo("*/*")));
        } finally {
            mobile.stop();
        }
    }

    @Test
    void teacherStatsHtmlRejectsUnexpectedDownstreamMediaType() {
        WireMockServer mobile = server();
        mobile.start();
        try {
            String path = "/api/v1/teacher/stats/export?semesterId=2&scope=students&groupId=2"
                    + "&subjectId=1&lessonType=lecture&format=html";
            mobile.stubFor(com.github.tomakehurst.wiremock.client.WireMock.get(urlEqualTo(path))
                    .withHeader(HttpHeaders.ACCEPT, equalTo("*/*"))
                    .willReturn(aResponse().withStatus(200)
                            .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                            .withBody("{}")));

            var auth = mock(InternalJwtIssuerClient.class);
            var filter = new ReportDownloadTicketDownloadFilter(auth, mock(InternalJwtIssuerFilter.class),
                    properties(mobile.baseUrl()), allowedAttemptRateLimiter(),
                    WebClient.builder().baseUrl(mobile.baseUrl()).build(),
                    WebClient.builder().baseUrl("http://127.0.0.1:1").build());
            when(auth.redeemReportTicket(TICKET))
                    .thenReturn(Mono.just(Optional.of(redemption(teacherStatsHtmlReport()))));

            MockServerWebExchange exchange = exchange(HttpMethod.GET, "/api/report-download/" + TICKET,
                    false, null);
            StepVerifier.create(filter.filter(exchange, mock(GatewayFilterChain.class))).verifyComplete();

            assertEquals(HttpStatus.SERVICE_UNAVAILABLE, exchange.getResponse().getStatusCode());
            assertEquals(MediaType.APPLICATION_PROBLEM_JSON, exchange.getResponse().getHeaders().getContentType());
            mobile.verify(com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor(urlEqualTo(path))
                    .withHeader(HttpHeaders.ACCEPT, equalTo("*/*")));
        } finally {
            mobile.stop();
        }
    }

    @Test
    void nonGetOverrideQueryAndRangeFailBeforeTicketRedemption() {
        var auth = mock(InternalJwtIssuerClient.class);
        var filter = new ReportDownloadTicketDownloadFilter(auth, mock(InternalJwtIssuerFilter.class),
                properties("http://127.0.0.1:1"), allowedAttemptRateLimiter(),
                WebClient.builder().baseUrl("http://127.0.0.1:1").build(),
                WebClient.builder().baseUrl("http://127.0.0.1:1").build());

        assertStatus(filter, exchange(HttpMethod.POST, "/api/report-download/" + TICKET, false, null),
                HttpStatus.METHOD_NOT_ALLOWED);
        assertStatus(filter, exchange(HttpMethod.GET, "/api/report-download/" + TICKET + "?url=https://evil.test",
                false, null), HttpStatus.BAD_REQUEST);
        assertStatus(filter, exchange(HttpMethod.GET, "/api/report-download/" + TICKET, false, "bytes=0-1"),
                HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE);
        verifyNoInteractions(auth);
    }

    @Test
    void unknownCapabilityReturnsNotFoundWithoutBackendCall() {
        var auth = mock(InternalJwtIssuerClient.class);
        var filter = new ReportDownloadTicketDownloadFilter(auth, mock(InternalJwtIssuerFilter.class),
                properties("http://127.0.0.1:1"), allowedAttemptRateLimiter(),
                WebClient.builder().baseUrl("http://127.0.0.1:1").build(),
                WebClient.builder().baseUrl("http://127.0.0.1:1").build());
        when(auth.redeemReportTicket(TICKET)).thenReturn(Mono.just(Optional.empty()));

        assertStatus(filter, exchange(HttpMethod.GET, "/api/report-download/" + TICKET, false, null),
                HttpStatus.NOT_FOUND);
        verify(auth).redeemReportTicket(TICKET);
    }

    @Test
    void downstreamPermissionDenialIsPreservedAndItsBodyIsNotForwarded() {
        WireMockServer mobile = server();
        mobile.start();
        try {
            mobile.stubFor(com.github.tomakehurst.wiremock.client.WireMock.get(
                            urlEqualTo("/api/v1/teacher/journal/export?semesterId=5&groupId=31&subjectId=8&lessonType=LECTURE&format=pdf"))
                    .withHeader("X-Internal-Token", equalTo(INTERNAL_TOKEN))
                    .willReturn(aResponse().withStatus(403)
                            .withHeader(HttpHeaders.CONTENT_TYPE, "application/problem+json")
                            .withBody("private downstream detail with report ids")));
            var auth = mock(InternalJwtIssuerClient.class);
            var attemptRateLimiter = allowedAttemptRateLimiter();
            var report = new IssueReportDownloadTicketRequest(
                    ReportDownloadKind.TEACHER_JOURNAL,
                    new IssueReportDownloadTicketRequest.TeacherJournalParameters(
                            5L, 31L, 8L, List.of("LECTURE"), ReportDownloadFormat.PDF),
                    null, null, null, null);
            when(auth.redeemReportTicket(TICKET)).thenReturn(Mono.just(Optional.of(redemption(report))));
            var filter = new ReportDownloadTicketDownloadFilter(auth, mock(InternalJwtIssuerFilter.class),
                    properties(mobile.baseUrl()), attemptRateLimiter,
                    WebClient.builder().baseUrl(mobile.baseUrl()).build(),
                    WebClient.builder().baseUrl("http://127.0.0.1:1").build());

            MockServerWebExchange exchange = exchange(HttpMethod.GET, "/api/report-download/" + TICKET,
                    false, null);
            StepVerifier.create(filter.filter(exchange, mock(GatewayFilterChain.class))).verifyComplete();

            assertEquals(HttpStatus.FORBIDDEN, exchange.getResponse().getStatusCode());
            String body = exchange.getResponse().getBodyAsString().block();
            assertTrue(body.contains("Report access denied"));
            assertTrue(!body.contains("private downstream detail"));
        } finally {
            mobile.stop();
        }
    }

    @Test
    void publicAttemptBudgetStopsUnknownTicketCallsBeforeAuthAndUsesCanonicalPeer() throws Exception {
        var auth = mock(InternalJwtIssuerClient.class);
        var attemptRateLimiter = mock(ReportDownloadAttemptRateLimiter.class);
        when(attemptRateLimiter.tryAcquire("203.0.113.7"))
                .thenReturn(Mono.just(true), Mono.just(false));
        when(auth.redeemReportTicket(TICKET)).thenReturn(Mono.just(Optional.empty()));
        var filter = new ReportDownloadTicketDownloadFilter(auth, mock(InternalJwtIssuerFilter.class),
                properties("http://127.0.0.1:1"), attemptRateLimiter,
                WebClient.builder().baseUrl("http://127.0.0.1:1").build(),
                WebClient.builder().baseUrl("http://127.0.0.1:1").build());

        MockServerWebExchange firstAttempt = exchangeFromPeer(TICKET, "198.51.100.41");
        MockServerWebExchange exhaustedAttempt = exchangeFromPeer(TICKET, "198.51.100.42");
        assertStatus(filter, firstAttempt, HttpStatus.NOT_FOUND);
        assertStatus(filter, exhaustedAttempt, HttpStatus.TOO_MANY_REQUESTS);

        verify(attemptRateLimiter, times(2)).tryAcquire("203.0.113.7");
        verify(auth, times(1)).redeemReportTicket(TICKET);
        verifyNoMoreInteractions(auth);
    }

    @Test
    void downstream422IsPreservedAndItsBodyIsNotForwarded() {
        WireMockServer attendance = server();
        attendance.start();
        try {
            attendance.stubFor(com.github.tomakehurst.wiremock.client.WireMock.get(
                            urlEqualTo("/attendance/reports/headman-weekly/current?weekStart=2026-09-28&format=pdf"))
                    .withHeader("X-Internal-Token", equalTo(INTERNAL_TOKEN))
                    .willReturn(aResponse().withStatus(422)
                            .withHeader(HttpHeaders.CONTENT_TYPE, "application/problem+json")
                            .withBody("private week is outside the active semester")));
            var auth = mock(InternalJwtIssuerClient.class);
            var report = new IssueReportDownloadTicketRequest(
                    ReportDownloadKind.HEADMAN_WEEKLY_CURRENT, null, null,
                    new IssueReportDownloadTicketRequest.HeadmanWeeklyCurrentParameters(
                            java.time.LocalDate.parse("2026-09-28"), ReportDownloadFormat.PDF),
                    null, null);
            when(auth.redeemReportTicket(TICKET)).thenReturn(Mono.just(Optional.of(redemption(report))));
            var filter = new ReportDownloadTicketDownloadFilter(auth, mock(InternalJwtIssuerFilter.class),
                    properties(attendance.baseUrl()), allowedAttemptRateLimiter(),
                    WebClient.builder().baseUrl("http://127.0.0.1:1").build(),
                    WebClient.builder().baseUrl(attendance.baseUrl()).build());

            MockServerWebExchange exchange = exchange(HttpMethod.GET, "/api/report-download/" + TICKET,
                    false, null);
            StepVerifier.create(filter.filter(exchange, mock(GatewayFilterChain.class))).verifyComplete();

            assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, exchange.getResponse().getStatusCode());
            String body = exchange.getResponse().getBodyAsString().block();
            assertTrue(body.contains("422"));
            assertTrue(body.contains("Report selection rejected"));
            assertTrue(!body.contains("private week is outside the active semester"));
            attendance.verify(com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor(urlEqualTo(
                    "/attendance/reports/headman-weekly/current?weekStart=2026-09-28&format=pdf")));
        } finally {
            attendance.stop();
        }
    }

    private static WireMockServer server() {
        return new WireMockServer(com.github.tomakehurst.wiremock.core.WireMockConfiguration.options()
                .dynamicPort());
    }

    private static IssueReportDownloadTicketRequest teacherStatsHtmlReport() {
        return new IssueReportDownloadTicketRequest(ReportDownloadKind.TEACHER_STATS, null,
                new IssueReportDownloadTicketRequest.TeacherStatsParameters(
                        2L, "students", 2L, 1L, List.of("lecture"), List.of(), List.of(),
                        ReportDownloadFormat.HTML), null, null, null);
    }

    private static ReportDownloadAttemptRateLimiter allowedAttemptRateLimiter() {
        var rateLimiter = mock(ReportDownloadAttemptRateLimiter.class);
        when(rateLimiter.tryAcquire(org.mockito.ArgumentMatchers.anyString())).thenReturn(Mono.just(true));
        return rateLimiter;
    }

    private static MockServerWebExchange exchangeFromPeer(String ticket, String forwardedFor) throws Exception {
        var peerAddress = InetAddress.getByAddress(new byte[]{(byte) 203, 0, 113, 7});
        var request = MockServerHttpRequest.get("/api/report-download/" + ticket)
                .remoteAddress(new InetSocketAddress(peerAddress, 41_000))
                .header("X-Forwarded-For", forwardedFor)
                .build();
        return MockServerWebExchange.from(request);
    }

    private static ReportDownloadBackendProperties properties(String url) {
        ReportDownloadBackendProperties properties = new ReportDownloadBackendProperties();
        properties.setMobileBffUrl(url);
        properties.setAttendanceServiceUrl(url);
        properties.setTimeoutMillis(2_000);
        return properties;
    }

    private static ReportDownloadTicketRedemptionResponse redemption(IssueReportDownloadTicketRequest report) {
        Instant expiry = Instant.now().plusSeconds(45).truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
        boolean headman = report.kind().name().startsWith("HEADMAN");
        AuthAdmissionResponse admission = new AuthAdmissionResponse(INTERNAL_TOKEN, expiry,
                "11111111-2222-4333-8444-555555555555", "17", "1", "1",
                headman ? "HEADMAN" : "TEACHER", "ACTIVE", headman ? "31" : null,
                headman, false);
        return new ReportDownloadTicketRedemptionResponse(admission, expiry,
                report.bindingHash(), report);
    }

    private static MockServerWebExchange exchange(HttpMethod method, String path, boolean telegramOrigin,
                                                   String range) {
        var builder = MockServerHttpRequest.method(method, path);
        if (telegramOrigin) {
            builder.header(HttpHeaders.ORIGIN, "https://web.telegram.org");
        }
        if (range != null) {
            builder.header(HttpHeaders.RANGE, range);
        }
        return MockServerWebExchange.from(builder.build());
    }

    private static void assertStatus(ReportDownloadTicketDownloadFilter filter,
                                     MockServerWebExchange exchange,
                                     HttpStatus expected) {
        StepVerifier.create(filter.filter(exchange, mock(GatewayFilterChain.class))).verifyComplete();
        assertEquals(expected, exchange.getResponse().getStatusCode());
        assertEquals("no-store", exchange.getResponse().getHeaders().getCacheControl());
    }

    private static byte[] responseBytes(MockServerWebExchange exchange) {
        DataBuffer buffer = DataBufferUtils.join(exchange.getResponse().getBody()).block();
        if (buffer == null) {
            return new byte[0];
        }
        try {
            byte[] bytes = new byte[buffer.readableByteCount()];
            buffer.read(bytes);
            return bytes;
        } finally {
            DataBufferUtils.release(buffer);
        }
    }
}
