package ru.rutcampustrack.auth.integration;

import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import ru.rutcampustrack.auth.dto.*;

/** Actual HTTP filters/JWT/session authority/PG/Redis ticket path; no mocked auth admission. */
class ExportJobHttpIT extends AbstractIntegrationTest {
    private static final String SECRET = "synthetic-export-http-issuer-secret-32-bytes";
    private static final Path KEYS = isolatedKeys();
    @Autowired TestRestTemplate http;
    @Autowired ObjectMapper mapper;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean RabbitTemplate rabbit;

    @DynamicPropertySource
    static void ownKeysAndIssuer(DynamicPropertyRegistry properties) {
        properties.add("jwt.key-dir", KEYS::toString);
        properties.add("rutcampustrack.security.internal-issuer.secret", () -> SECRET);
    }

    private static Path isolatedKeys() {
        try {
            return Files.createTempDirectory("rct-export-http-keys-");
        } catch (java.io.IOException failure) {
            throw new IllegalStateException("Isolated test key directory unavailable");
        }
    }

    @Test
    void realBearerQueuedClaimReadyTicketAndDownloadDenyAfterLiveRevocation() throws Exception {
        String report = "{\"kind\":\"ADMIN_USERS\",\"adminUsers\":{\"format\":\"html\"}}";
        String create = "{\"requestId\":\"" + UUID.randomUUID() + "\",\"report\":" + report + "}";
        ResponseEntity<String> anonymous =
                send("/auth/export-jobs", HttpMethod.POST, create, null, null);
        // The existing direct Auth security entry point denies absent bearer credentials with 403.
        assertThat(anonymous.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(anonymous.getHeaders().getCacheControl()).isEqualTo("no-store");
        for (String secret : List.of("", "wrong-synthetic-secret")) {
            var denied = send("/internal/export-jobs/claim", HttpMethod.POST, null, null, secret);
            assertThat(denied.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
            assertThat(denied.getHeaders().getCacheControl()).isEqualTo("no-store");
        }
        String teacher = login("teacher");
        assertThat(
                        send("/auth/export-jobs", HttpMethod.POST, create, teacher, null)
                                .getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        String actor = login("admin");
        var session = send("/auth/session", HttpMethod.GET, null, actor, null);
        assertThat(session.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode active = mapper.readTree(session.getBody());
        assertThat(active.path("activeRole").asText()).isEqualTo("ADMIN");
        var queued = send("/auth/export-jobs", HttpMethod.POST, create, actor, null);
        String safeCode = mapper.readTree(queued.getBody()).path("code").asText();
        assertThat(queued.getStatusCode())
                .as("queued HTTP code: %s", safeCode.matches("[A-Z_]{1,64}") ? safeCode : "none")
                .isEqualTo(HttpStatus.ACCEPTED);
        assertThat(queued.getHeaders().getCacheControl()).isEqualTo("no-store");
        JsonNode status = mapper.readTree(queued.getBody());
        UUID id = UUID.fromString(status.path("jobId").asText());
        // A previous legitimate export shares this database; audit history must remain intact.
        UUID historicalJobId = UUID.randomUUID();
        assertThat(historicalJobId).isNotEqualTo(id);
        long historicalAuditId =
                jdbc.queryForObject(
                        "INSERT INTO audit_log(user_id,action,target_type,extras,succeeded,created_at) "
                                + "SELECT id,'registry.export','ADMIN_USERS',?::jsonb,TRUE,NOW()-interval '1 day' "
                                + "FROM users WHERE login='admin' RETURNING id",
                        Long.class,
                        mapper.writeValueAsString(
                                Map.of(
                                        "kind",
                                        "ADMIN_USERS",
                                        "rowCount",
                                        1,
                                        "jobId",
                                        historicalJobId.toString())));
        assertThat(status.path("state").asText()).isEqualTo("queued");
        assertThat(status.has("content")).isFalse();
        assertThat(status.has("internalToken")).isFalse();
        assertThat(
                        send("/auth/export-jobs/" + id + "/file", HttpMethod.GET, null, actor, null)
                                .getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
        var claimed = send("/internal/export-jobs/claim", HttpMethod.POST, null, null, SECRET);
        assertThat(claimed.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode claim = mapper.readTree(claimed.getBody());
        assertThat(claim.path("jobId").asText()).isEqualTo(id.toString());
        assertThat(claim.path("authority").path("admission").path("role").asText())
                .isEqualTo("ADMIN");
        assertThat(
                        claim.path("authority")
                                .path("admission")
                                .path("internalToken")
                                .asText()
                                .split("\\."))
                .hasSize(3);
        byte[] bytes = "<html>committed synthetic registry</html>".getBytes(StandardCharsets.UTF_8);
        var completion =
                new ExportJobModels.Complete(
                        UUID.fromString(claim.path("leaseId").asText()),
                        bytes,
                        "text/html",
                        3,
                        true);
        for (int repeat = 0; repeat < 2; repeat++) {
            var ready =
                    send(
                            "/internal/export-jobs/" + id + "/complete",
                            HttpMethod.POST,
                            mapper.writeValueAsString(completion),
                            null,
                            SECRET);
            assertThat(ready.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(mapper.readTree(ready.getBody()).path("state").asText()).isEqualTo("ready");
        }
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM audit_log WHERE action='registry.export' AND extras->>'jobId'=?",
                                Long.class,
                                id.toString()))
                .isEqualTo(1);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM audit_log WHERE id=? AND extras->>'jobId'=?",
                                Long.class,
                                historicalAuditId,
                                historicalJobId.toString()))
                .isEqualTo(1);
        for (String envelope :
                jdbc.queryForList(
                        "SELECT payload::text FROM auth_outbox WHERE event_type='export.state.changed'",
                        String.class)) {
            assertThat(
                            ru.rutcampustrack.auth.events.EventSchemaValidator.validate(
                                    "export.state.changed.json", envelope))
                    .isEmpty();
        }
        var saved = send("/auth/export-jobs/" + id + "/file", HttpMethod.GET, null, actor, null);
        assertThat(saved.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(saved.getBody().getBytes(StandardCharsets.UTF_8)).isEqualTo(bytes);
        assertThat(saved.getHeaders().getCacheControl()).isEqualTo("no-store");
        var capability =
                send("/auth/export-jobs/" + id + "/ticket", HttpMethod.POST, null, actor, null);
        assertThat(capability.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode ticket = mapper.readTree(capability.getBody());
        String opaque =
                ticket.path("downloadPath").asText().substring("/api/report-download/".length());
        String redemption =
                mapper.writeValueAsString(new RedeemReportDownloadTicketRequest(opaque));
        var redeemed =
                send(
                        "/internal/report-download-tickets/redeem",
                        HttpMethod.POST,
                        redemption,
                        null,
                        SECRET);
        assertThat(redeemed.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(mapper.readTree(redeemed.getBody()).path("exportJobId").asText())
                .isEqualTo(id.toString());
        var logout = send("/auth/logout", HttpMethod.POST, null, actor, null);
        assertThat(logout.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(
                        send("/auth/export-jobs/" + id + "/file", HttpMethod.GET, null, actor, null)
                                .getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(
                        send("/auth/export-jobs/" + id, HttpMethod.GET, null, actor, null)
                                .getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(
                        send(
                                        "/internal/report-download-tickets/redeem",
                                        HttpMethod.POST,
                                        redemption,
                                        null,
                                        SECRET)
                                .getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    private String login(String login) throws Exception {
        var response =
                http.postForEntity(
                        "/auth/login", new LoginRequest(login, "password"), TokenResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        String token = Objects.requireNonNull(response.getBody()).accessToken();
        if (login.equals("admin")) {
            // ADMIN is explicitly selected by the accepted client role chooser, never a login
            // default.
            var session = send("/auth/session", HttpMethod.GET, null, token, null);
            assertThat(session.getStatusCode()).isEqualTo(HttpStatus.OK);
            String version = mapper.readTree(session.getBody()).path("sessionVersion").asText();
            var selected =
                    send(
                            "/auth/session/active-role",
                            HttpMethod.PUT,
                            mapper.writeValueAsString(
                                    new SelectActiveRoleRequest("ADMIN", version)),
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
        if (secret != null && !secret.isEmpty()) headers.set("X-Internal-Issuer-Secret", secret);
        return http.exchange(path, method, new HttpEntity<>(body, headers), String.class);
    }
}
