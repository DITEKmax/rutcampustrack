package ru.rutcampustrack.auth.integration;

import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.util.ReflectionTestUtils;
import ru.rutcampustrack.auth.dto.*;
import ru.rutcampustrack.auth.export.ExportReportAuthority;
import ru.rutcampustrack.auth.security.SessionPrincipal;
import ru.rutcampustrack.auth.service.AuthService;
import ru.rutcampustrack.auth.service.JwtService;
import ru.rutcampustrack.auth.session.model.AuthRole;
import ru.rutcampustrack.auth.session.model.RoleStatus;
import ru.rutcampustrack.gateway.config.PublicKeyConfig;
import ru.rutcampustrack.gateway.security.ExportJobWorker;
import ru.rutcampustrack.gateway.security.InternalIssuerClientProperties;
import ru.rutcampustrack.gateway.security.InternalJwtIssuerClient;
import ru.rutcampustrack.gateway.security.InternalJwtIssuerFilter;
import ru.rutcampustrack.gateway.security.ReportDownloadAttemptRateLimiter;
import ru.rutcampustrack.gateway.security.ReportDownloadBackendProperties;
import ru.rutcampustrack.gateway.security.ReportDownloadTicketDownloadFilter;

/**
 * Real Auth HTTP/PG/Redis/RSA and real Gateway worker/verifier; only file backend and Rabbit
 * transport are fixtures.
 */
class ExportCrossLayerBoundaryIT extends AbstractIntegrationTest {
    private static final String SECRET = "synthetic-export-cross-layer-secret-32-bytes";
    private static final String USERS =
            "{\"kind\":\"ADMIN_USERS\",\"adminUsers\":{\"format\":\"html\"}}";
    private static final String GROUPS =
            "{\"kind\":\"ADMIN_GROUPS\",\"adminGroups\":{\"format\":\"html\"}}";
    private static final byte[] FILE =
            "<html>synthetic worker registry</html>".getBytes(StandardCharsets.UTF_8);
    private static final Path KEYS = isolatedKeys();
    @LocalServerPort int port;
    @Autowired TestRestTemplate http;
    @Autowired ObjectMapper mapper;
    @Autowired StringRedisTemplate redis;
    @Autowired ReactiveStringRedisTemplate reactiveRedis;
    @Autowired AuthService liveSessions;
    @Autowired JwtService signer;
    @MockitoSpyBean JdbcTemplate jdbc;
    @MockitoSpyBean ExportReportAuthority authority;
    @MockitoBean RabbitTemplate rabbit;

    @DynamicPropertySource
    static void ownProperties(DynamicPropertyRegistry properties) {
        properties.add("jwt.key-dir", KEYS::toString);
        properties.add("rutcampustrack.security.internal-issuer.secret", () -> SECRET);
    }

    @AfterAll
    static void removeOwnSyntheticKeys() throws java.io.IOException {
        try (var files = Files.walk(KEYS)) {
            for (Path path : files.sorted(java.util.Comparator.reverseOrder()).toList())
                Files.deleteIfExists(path);
        }
    }

    @BeforeEach
    void fullFixture() {
        jdbc.update("DELETE FROM auth_export_jobs");
        jdbc.update(
                "UPDATE admin_access_profiles SET version=version+1,assigned=true,mode='FULL',permissions='{}' WHERE user_id=(SELECT id FROM users WHERE login='admin')");
    }

    @Test
    void freshAuthClaimPassesRealGatewayWorkerAndCommitsReadyBytes() throws Exception {
        String actor = login("admin");
        UUID job = enqueue(actor, USERS);
        try (Backend backend = new Backend()) {
            var auth = client(authOrigin());
            var reports = reports(auth, backend.origin());
            var worker = new ExportJobWorker(auth, reports);
            try {
                for (int attempt = 1; attempt <= 3 && !state(job).equals("ready"); attempt++) {
                    worker.tick();
                    int expected = attempt;
                    await().atMost(Duration.ofSeconds(10))
                            .until(
                                    () -> {
                                        String state = state(job);
                                        int attempts =
                                                jdbc.queryForObject(
                                                        "SELECT attempts FROM auth_export_jobs WHERE job_id=?",
                                                        Integer.class,
                                                        job);
                                        return state.equals("ready")
                                                || attempts >= expected
                                                        && !state.equals("preparing");
                                    });
                }
                assertThat(state(job)).isEqualTo("ready");
                assertThat(backend.requests.get()).isEqualTo(1);
                assertThat(backend.validJobHeaders.get()).isTrue();
                var saved =
                        send(
                                "/auth/export-jobs/" + job + "/file",
                                HttpMethod.GET,
                                null,
                                actor,
                                null);
                assertThat(saved.getStatusCode()).isEqualTo(HttpStatus.OK);
                assertThat(saved.getHeaders().getCacheControl()).isEqualTo("no-store");
                assertThat(saved.getBody().getBytes(StandardCharsets.UTF_8)).isEqualTo(FILE);
                assertThat(
                                jdbc.queryForObject(
                                        "SELECT count(*) FROM audit_log WHERE action='registry.export' AND extras->>'jobId'=?",
                                        Long.class,
                                        job.toString()))
                        .isEqualTo(1);
                assertThat(
                                jdbc.queryForObject(
                                        "SELECT count(*) FROM auth_outbox WHERE payload->'payload'->>'job_id'=? AND payload->'payload'->>'state'='ready'",
                                        Long.class,
                                        job.toString()))
                        .isEqualTo(1);
            } finally {
                worker.stop();
            }
        }
    }

    @Test
    void
            legacySixtySecondAuthorityWorksWhileTamperedChangedExpiredAndLongCapabilitiesNeverDispatch()
                    throws Exception {
        String actor = login("admin");
        var auth = client(authOrigin());
        var issued = send("/auth/report-download-tickets", HttpMethod.POST, USERS, actor, null);
        assertThat(issued.getStatusCode()).isEqualTo(HttpStatus.OK);
        String opaque =
                mapper.readTree(issued.getBody())
                        .path("downloadPath")
                        .asText()
                        .substring("/api/report-download/".length());
        var good =
                Objects.requireNonNull(
                                auth.redeemReportTicket(opaque).block(Duration.ofSeconds(10)))
                        .orElseThrow();
        try (Backend backend = new Backend()) {
            var reports = reports(auth, backend.origin());
            assertThat(Duration.between(Instant.now(), good.ticketExpiresAt()).getSeconds())
                    .isBetween(1L, 60L);
            assertThat(
                            Objects.requireNonNull(
                                            reports.generate(good, null, null)
                                                    .block(Duration.ofSeconds(5)))
                                    .content())
                    .isEqualTo(FILE);
            var identity = good.admission();
            String token = identity.internalToken();
            int signature = token.lastIndexOf('.') + 1;
            String forged =
                    token.substring(0, signature)
                            + (token.charAt(signature) == 'A' ? 'B' : 'A')
                            + token.substring(signature + 1);
            var changedIdentity =
                    new AuthAdmissionResponse(
                            forged,
                            identity.expiresAt(),
                            identity.sessionId(),
                            identity.userId(),
                            identity.sessionVersion(),
                            identity.rolesVersion(),
                            identity.role(),
                            identity.status(),
                            identity.groupId(),
                            identity.isHeadman(),
                            identity.readOnly());
            var tampered =
                    new ReportDownloadTicketRedemptionResponse(
                            changedIdentity,
                            good.ticketExpiresAt(),
                            good.reportBindingHash(),
                            good.report());
            assertThatThrownBy(
                            () ->
                                    reports.generate(tampered, null, null)
                                            .block(Duration.ofSeconds(5)))
                    .isInstanceOf(RuntimeException.class);
            var changedReport =
                    mapper.readValue(
                            USERS.replace(
                                    "\"format\":\"html\"",
                                    "\"search\":\"changed\",\"format\":\"html\""),
                            IssueReportDownloadTicketRequest.class);
            var mismatch =
                    new ReportDownloadTicketRedemptionResponse(
                            identity,
                            good.ticketExpiresAt(),
                            changedReport.bindingHash(),
                            changedReport);
            assertThatThrownBy(
                            () ->
                                    reports.generate(mismatch, null, null)
                                            .block(Duration.ofSeconds(5)))
                    .isInstanceOf(RuntimeException.class);
            var principal =
                    new SessionPrincipal(
                            Long.parseLong(identity.userId()),
                            UUID.fromString(identity.sessionId()),
                            Long.parseLong(identity.sessionVersion()),
                            Long.parseLong(identity.rolesVersion()),
                            AuthRole.valueOf(identity.role()),
                            RoleStatus.valueOf(identity.status()),
                            null,
                            false,
                            false);
            var snapshot = liveSessions.admit(principal);
            Instant now = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
            for (boolean expired : List.of(true, false)) {
                Instant issuedAt = expired ? now.minusSeconds(120) : now;
                Instant tokenExpiry = expired ? now.minusSeconds(60) : now.plusSeconds(20);
                Instant capabilityExpiry = expired ? tokenExpiry : now.plusSeconds(120);
                String signed =
                        signer.generateInternalReportDownloadToken(
                                snapshot,
                                issuedAt,
                                tokenExpiry,
                                good.reportBindingHash(),
                                capabilityExpiry);
                var invalidIdentity =
                        new AuthAdmissionResponse(
                                signed,
                                tokenExpiry,
                                identity.sessionId(),
                                identity.userId(),
                                identity.sessionVersion(),
                                identity.rolesVersion(),
                                identity.role(),
                                identity.status(),
                                identity.groupId(),
                                identity.isHeadman(),
                                identity.readOnly());
                var invalid =
                        new ReportDownloadTicketRedemptionResponse(
                                invalidIdentity,
                                capabilityExpiry,
                                good.reportBindingHash(),
                                good.report());
                assertThatThrownBy(
                                () ->
                                        reports.generate(invalid, null, null)
                                                .block(Duration.ofSeconds(5)))
                        .isInstanceOf(RuntimeException.class);
            }
            assertThat(backend.requests.get()).isEqualTo(1);
        }
    }

    @Test
    void expiryBetweenRealAdmissionAndContentReadIsNotFoundWithoutBytes() throws Exception {
        String actor = login("admin");
        UUID job = enqueue(actor, USERS);
        ExportJobModels.Claim claim =
                Objects.requireNonNull(
                                client(authOrigin()).claimExport().block(Duration.ofSeconds(10)))
                        .orElseThrow();
        complete(claim);
        AtomicBoolean first = new AtomicBoolean(true);
        doAnswer(
                        call -> {
                            Object result = call.callRealMethod();
                            if (first.compareAndSet(true, false))
                                jdbc.update(
                                        "UPDATE auth_export_jobs SET expires_at=NOW()-interval '1 second' WHERE job_id=?",
                                        job);
                            return result;
                        })
                .when(authority)
                .admit(any(), any(), any(), anyBoolean());
        var result = send("/auth/export-jobs/" + job + "/file", HttpMethod.GET, null, actor, null);
        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(result.getHeaders().getCacheControl()).isEqualTo("no-store");
        assertThat(result.getBody()).doesNotContain("synthetic worker registry");
    }

    @ParameterizedTest
    @ValueSource(strings = {"teacher", "student"})
    void legacyRegistryIssuanceRequiresSelectedAdminBeforeCapabilityStorage(String user)
            throws Exception {
        String actor = login(user);
        int before = capabilities();
        var denied = send("/auth/report-download-tickets", HttpMethod.POST, USERS, actor, null);
        assertThat(denied.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(capabilities()).isEqualTo(before);
    }

    @Test
    void limitedLegacyIssuanceUsesCorrespondingCurrentViewGrantAndRevocation() throws Exception {
        limited("GROUPS_VIEW");
        String groups = login("admin");
        assertThat(
                        send("/auth/report-download-tickets", HttpMethod.POST, GROUPS, groups, null)
                                .getStatusCode())
                .isEqualTo(HttpStatus.OK);
        int before = capabilities();
        assertThat(
                        send("/auth/report-download-tickets", HttpMethod.POST, USERS, groups, null)
                                .getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(capabilities()).isEqualTo(before);
        limited("USERS_VIEW");
        assertThat(
                        send("/auth/report-download-tickets", HttpMethod.POST, GROUPS, groups, null)
                                .getStatusCode()
                                .is4xxClientError())
                .isTrue();
        assertThat(capabilities()).isEqualTo(before);
        String users = login("admin");
        assertThat(
                        send("/auth/report-download-tickets", HttpMethod.POST, USERS, users, null)
                                .getStatusCode())
                .isEqualTo(HttpStatus.OK);
        before = capabilities();
        assertThat(
                        send("/auth/report-download-tickets", HttpMethod.POST, GROUPS, users, null)
                                .getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(capabilities()).isEqualTo(before);
    }

    @Test
    void unavailableLiveRegistryAuthorityCannotIssueACapability() throws Exception {
        String actor = login("admin");
        int before = capabilities();
        doThrow(new CannotGetJdbcConnectionException("synthetic scoped profile outage"))
                .when(jdbc)
                .query(
                        contains("FROM users u LEFT JOIN user_role_grants g"),
                        any(RowMapper.class),
                        anyLong());
        var denied = send("/auth/report-download-tickets", HttpMethod.POST, USERS, actor, null);
        assertThat(denied.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(capabilities()).isEqualTo(before);
    }

    @Test
    void limitedWithoutViewGrantsCannotIssueEitherRegistryCapability() throws Exception {
        limited("DASHBOARD_VIEW");
        String actor = login("admin");
        int before = capabilities();
        for (String report : List.of(USERS, GROUPS)) {
            assertThat(
                            send(
                                            "/auth/report-download-tickets",
                                            HttpMethod.POST,
                                            report,
                                            actor,
                                            null)
                                    .getStatusCode())
                    .isEqualTo(HttpStatus.FORBIDDEN);
            assertThat(capabilities()).isEqualTo(before);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"8", "\"8\""})
    void actualAuthKindBoundaryRejectsOrdinalAndNumericString(String wire) throws Exception {
        String actor = login("admin");
        String report = USERS.replace("\"ADMIN_USERS\"", wire);
        var denied = send("/auth/export-jobs", HttpMethod.POST, create(report), actor, null);
        assertThat(denied.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM auth_export_jobs", Long.class))
                .isZero();
    }

    @Test
    void configuredAuthMapperPreservesAllCanonicalStringCodesAndRejectsMalformedRequiredKinds()
            throws Exception {
        for (ReportDownloadKind kind : ReportDownloadKind.values()) {
            String wire = "\"" + kind.name() + "\"";
            assertThat(mapper.writeValueAsString(kind)).isEqualTo(wire);
            assertThat(mapper.readValue(wire, ReportDownloadKind.class)).isEqualTo(kind);
        }
        for (String invalid : List.of("8", "\"8\"", "\"UNKNOWN\"", "[]", "{}", "true")) {
            assertThatThrownBy(() -> mapper.readValue(invalid, ReportDownloadKind.class))
                    .isInstanceOf(com.fasterxml.jackson.core.JsonProcessingException.class);
        }
        String actor = login("admin");
        for (String invalid : List.of("null", "\"UNKNOWN\"", "[]", "{}", "true")) {
            var denied =
                    send(
                            "/auth/export-jobs",
                            HttpMethod.POST,
                            create(USERS.replace("\"ADMIN_USERS\"", invalid)),
                            actor,
                            null);
            assertThat(denied.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM auth_export_jobs", Long.class))
                .isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"8", "\"8\"", "null", "\"UNKNOWN\"", "[]", "{}", "true"})
    void actualGatewayKindDecoderRejectsNoncanonicalRequiredKinds(String wire) throws Exception {
        String actor = login("admin");
        enqueue(actor, USERS);
        ExportJobModels.Claim claim =
                Objects.requireNonNull(
                                client(authOrigin()).claimExport().block(Duration.ofSeconds(10)))
                        .orElseThrow();
        String response =
                mapper.writeValueAsString(claim.authority()).replace("\"ADMIN_USERS\"", wire);
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext(
                "/internal/report-download-tickets/redeem",
                exchange -> {
                    exchange.getRequestBody().close();
                    byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
                    exchange.getResponseHeaders().set("Content-Type", "application/json");
                    exchange.sendResponseHeaders(200, bytes.length);
                    try (var out = exchange.getResponseBody()) {
                        out.write(bytes);
                    }
                });
        server.start();
        try {
            var gateway = client("http://127.0.0.1:" + server.getAddress().getPort());
            assertThatThrownBy(
                            () ->
                                    gateway.redeemReportTicket("A".repeat(43))
                                            .block(Duration.ofSeconds(5)))
                    .isInstanceOf(RuntimeException.class);
        } finally {
            server.stop(0);
        }
    }

    private static Path isolatedKeys() {
        try {
            return Files.createTempDirectory("rct-export-boundary-keys-");
        } catch (java.io.IOException failure) {
            throw new IllegalStateException("Isolated synthetic keys unavailable");
        }
    }

    private String authOrigin() {
        return "http://127.0.0.1:" + port;
    }

    private static InternalJwtIssuerClient client(String origin) {
        var properties = new InternalIssuerClientProperties();
        properties.setAuthServiceUrl(origin);
        properties.setSecret(SECRET);
        properties.setTimeoutMillis(5000);
        return new InternalJwtIssuerClient(properties);
    }

    private ReportDownloadTicketDownloadFilter reports(
            InternalJwtIssuerClient auth, String backend) {
        var key = new PublicKeyConfig();
        ReflectionTestUtils.setField(key, "authServiceUrl", authOrigin());
        key.init();
        assertThat(key.isReady()).isTrue();
        var properties = new ReportDownloadBackendProperties();
        properties.setAcademicServiceUrl(backend);
        properties.setTimeoutMillis(5000);
        return new ReportDownloadTicketDownloadFilter(
                auth,
                new InternalJwtIssuerFilter(auth, key),
                properties,
                new ReportDownloadAttemptRateLimiter(reactiveRedis));
    }

    private String state(UUID job) {
        return jdbc.queryForObject(
                "SELECT state FROM auth_export_jobs WHERE job_id=?", String.class, job);
    }

    private static String create(String report) {
        return "{\"requestId\":\"" + UUID.randomUUID() + "\",\"report\":" + report + "}";
    }

    private UUID enqueue(String actor, String report) throws Exception {
        var result = send("/auth/export-jobs", HttpMethod.POST, create(report), actor, null);
        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        return UUID.fromString(mapper.readTree(result.getBody()).path("jobId").asText());
    }

    private void complete(ExportJobModels.Claim claim) throws Exception {
        var result =
                send(
                        "/internal/export-jobs/" + claim.jobId() + "/complete",
                        HttpMethod.POST,
                        mapper.writeValueAsString(
                                new ExportJobModels.Complete(
                                        claim.leaseId(), FILE, "text/html", 3, true)),
                        null,
                        SECRET);
        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    private int capabilities() {
        return Objects.requireNonNull(redis.keys("report_download_ticket:*")).size();
    }

    private void limited(String permission) {
        jdbc.update(
                "UPDATE admin_access_profiles SET version=version+1,mode='LIMITED',permissions=ARRAY[?] WHERE user_id=(SELECT id FROM users WHERE login='admin')",
                permission);
    }

    private String login(String login) throws Exception {
        var response =
                http.postForEntity(
                        "/auth/login", new LoginRequest(login, "password"), TokenResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        String token = Objects.requireNonNull(response.getBody()).accessToken();
        if (login.equals("admin")) {
            var session = send("/auth/session", HttpMethod.GET, null, token, null);
            JsonNode value = mapper.readTree(session.getBody());
            var selected =
                    send(
                            "/auth/session/active-role",
                            HttpMethod.PUT,
                            mapper.writeValueAsString(
                                    new SelectActiveRoleRequest(
                                            "ADMIN", value.path("sessionVersion").asText())),
                            token,
                            null);
            assertThat(selected.getStatusCode()).isEqualTo(HttpStatus.OK);
            token = mapper.readTree(selected.getBody()).path("accessToken").asText();
        }
        return token;
    }

    private ResponseEntity<String> send(
            String path, HttpMethod method, String body, String bearer, String secret) {
        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (bearer != null) headers.setBearerAuth(bearer);
        if (secret != null) headers.set("X-Internal-Issuer-Secret", secret);
        return http.exchange(path, method, new HttpEntity<>(body, headers), String.class);
    }

    private static final class Backend implements AutoCloseable {
        final AtomicInteger requests = new AtomicInteger();
        final AtomicBoolean validJobHeaders = new AtomicBoolean();
        final HttpServer server;

        Backend() throws java.io.IOException {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext(
                    "/academic/registry-exports/users",
                    exchange -> {
                        exchange.getRequestBody().close();
                        requests.incrementAndGet();
                        String job = exchange.getRequestHeaders().getFirst("X-Export-Job-Id");
                        String lease = exchange.getRequestHeaders().getFirst("X-Export-Job-Lease");
                        validJobHeaders.set(
                                "POST".equals(exchange.getRequestMethod())
                                        && job != null
                                        && lease != null
                                        && UUID.fromString(job) != null
                                        && UUID.fromString(lease) != null);
                        exchange.getResponseHeaders().set("Content-Type", "text/html");
                        exchange.getResponseHeaders().set("X-Export-Row-Count", "3");
                        exchange.getResponseHeaders().set("X-Export-Requires-Full", "true");
                        exchange.sendResponseHeaders(200, FILE.length);
                        try (var out = exchange.getResponseBody()) {
                            out.write(FILE);
                        }
                    });
            server.start();
        }

        String origin() {
            return "http://127.0.0.1:" + server.getAddress().getPort();
        }

        public void close() {
            server.stop(0);
        }
    }
}
