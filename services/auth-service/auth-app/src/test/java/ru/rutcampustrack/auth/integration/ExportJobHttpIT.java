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

    @Test
    void readyTeacherFilesRequireEveryCapturedCurrentPairWhileIndependentPairRemainsUsable()
            throws Exception {
        var transactions =
                new org.springframework.transaction.support.TransactionTemplate(
                        new org.springframework.jdbc.datasource.DataSourceTransactionManager(
                                Objects.requireNonNull(jdbc.getDataSource())));
        java.time.LocalDate today = java.time.LocalDate.now(java.time.ZoneId.of("Europe/Moscow"));
        long originalSemester =
                jdbc.queryForObject("SELECT id FROM semesters WHERE is_active=TRUE", Long.class);
        long ownSemester =
                transactions.execute(
                        status -> {
                            jdbc.update(
                                    "UPDATE semesters SET is_active=FALSE WHERE id=?",
                                    originalSemester);
                            return jdbc.queryForObject(
                                    "INSERT INTO semesters(name,date_from,date_to,is_active) VALUES(?,?,?,TRUE) RETURNING id",
                                    Long.class,
                                    "Teacher export scope " + UUID.randomUUID(),
                                    today.minusDays(2),
                                    today.plusDays(2));
                        });
        Throwable primaryFailure = null;
        try {
            String ownLogin =
                    "teacher_export_"
                            + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
            long teacher =
                    jdbc.queryForObject(
                            """
                    INSERT INTO users(login,password_hash,last_name,first_name,role,status)
                    SELECT ?,password_hash,'Экспорт','Преподаватель','teacher','active' FROM users WHERE login='teacher' RETURNING id
                    """,
                            Long.class,
                            ownLogin);
            jdbc.update(
                    "INSERT INTO user_role_grants(user_id,role,status) VALUES(?,'teacher','active')",
                    teacher);
            long group =
                    jdbc.queryForObject(
                            "INSERT INTO groups(name,is_active) VALUES(?,TRUE) RETURNING id",
                            Long.class,
                            "Teacher export group " + UUID.randomUUID());
            long subjectA = teacherSubject(group), subjectB = teacherSubject(group);
            long assignmentA =
                    teacherAssignment(teacher, group, subjectA, ownSemester, today.minusDays(1));
            long assignmentB =
                    teacherAssignment(teacher, group, subjectB, ownSemester, today.minusDays(1));
            String actor = login(ownLogin);
            JsonNode originalSession =
                    mapper.readTree(
                            send("/auth/session", HttpMethod.GET, null, actor, null).getBody());
            assertThat("TEACHER".equals(originalSession.path("activeRole").asText())).isTrue();
            String hash =
                    ru.rutcampustrack.shared.security.teacher.TeacherReportScope.of(
                                    List.of(
                                            new ru.rutcampustrack.shared.security.teacher
                                                    .TeacherReportScope.Tuple(group, subjectA),
                                            new ru.rutcampustrack.shared.security.teacher
                                                    .TeacherReportScope.Tuple(group, subjectB)))
                            .sha256();
            String selectedA =
                    "{\"kind\":\"TEACHER_JOURNAL\",\"teacherJournal\":{\"semesterId\":"
                            + ownSemester
                            + ",\"groupId\":"
                            + group
                            + ",\"subjectId\":"
                            + subjectA
                            + ",\"lessonTypes\":[\"lecture\"],\"format\":\"html\"}}";
            String all =
                    "{\"kind\":\"TEACHER_STATS\",\"teacherStats\":{\"semesterId\":"
                            + ownSemester
                            + ",\"scope\":\"groups\",\"lessonTypes\":[\"lecture\"],\"format\":\"html\"}}";
            String selectedB =
                    "{\"kind\":\"TEACHER_STATS\",\"teacherStats\":{\"semesterId\":"
                            + ownSemester
                            + ",\"scope\":\"groups\",\"groupId\":"
                            + group
                            + ",\"subjectId\":"
                            + subjectB
                            + ",\"lessonTypes\":[\"lecture\"],\"format\":\"html\"}}";
            // These are real protected completion calls, not a renderer-provenance claim.
            var a = readyTeacherExport(actor, selectedA, null, false);
            var ab = readyTeacherExport(actor, all, hash, true);
            var b = readyTeacherExport(actor, selectedB, null, false);
            Map<UUID, String> redemptions = new HashMap<>();
            for (var ready : List.of(a, ab, b)) {
                assertThat(
                                send(
                                                "/auth/export-jobs/" + ready.id(),
                                                HttpMethod.GET,
                                                null,
                                                actor,
                                                null)
                                        .getStatusCode())
                        .isEqualTo(HttpStatus.OK);
                var file =
                        send(
                                "/auth/export-jobs/" + ready.id() + "/file",
                                HttpMethod.GET,
                                null,
                                actor,
                                null);
                assertThat(file.getStatusCode()).isEqualTo(HttpStatus.OK);
                assertThat("<html>owned teacher scope</html>".equals(file.getBody()))
                        .as("owned file bytes match")
                        .isTrue();
                String redemption = teacherRedemption(actor, ready.id());
                redemptions.put(ready.id(), redemption);
                assertThat(
                                send(
                                                "/internal/report-download-tickets/redeem",
                                                HttpMethod.POST,
                                                redemption,
                                                null,
                                                SECRET)
                                        .getStatusCode())
                        .isEqualTo(HttpStatus.OK);
            }
            String before =
                    jdbc.queryForObject(
                            "SELECT identity::text FROM auth_export_jobs WHERE job_id=?",
                            String.class,
                            ab.id());
            assertThat(mapper.readTree(before).path("teacherScope").path("tuples").size())
                    .isEqualTo(2);
            var legacy =
                    mapper.readValue(
                                    before,
                                    ru.rutcampustrack.auth.service.StoredReportDownloadTicket.class)
                            .withTeacherScope(null);
            assertThatThrownBy(
                            () ->
                                    new ru.rutcampustrack.auth.export.TeacherReportAuthorityJdbc(
                                                    jdbc)
                                            .requireReady(legacy))
                    .isInstanceOf(ru.rutcampustrack.auth.session.SessionAdmissionException.class)
                    .satisfies(
                            error ->
                                    assertThat(
                                                    ((ru.rutcampustrack.auth.session
                                                                            .SessionAdmissionException)
                                                                    error)
                                                            .code())
                                            .isEqualTo(
                                                    ru.rutcampustrack.auth.session
                                                            .SessionAdmissionException.Code
                                                            .REPORT_PERMISSION_DENIED));
            // Guarded canonical CLOSE_UNASSIGN; no role/session/version or trigger mutation.
            transactions.executeWithoutResult(
                    status -> {
                        UUID operation = UUID.randomUUID();
                        long admin =
                                jdbc.queryForObject(
                                        "SELECT id FROM users WHERE login='admin'", Long.class);
                        jdbc.update(
                                """
                        INSERT INTO assignment_replacement_operations(operation_id,actor_id,request_key,payload_hash,
                            source_assignment_id,effective_from,source_valid_until,state,operation_kind,requested_cutoff)
                        VALUES(?,?,?,decode(repeat('11',32),'hex'),?,?,?,'PREPARED','CLOSE_UNASSIGN',?)
                        """,
                                operation,
                                admin,
                                UUID.randomUUID(),
                                assignmentA,
                                today,
                                today.plusDays(3),
                                today);
                        jdbc.update(
                                "UPDATE assignment_replacement_operations SET state='APPLIED',schedule_receipt_state='APPLIED' WHERE operation_id=?",
                                operation);
                        jdbc.queryForObject(
                                "SELECT set_config('rutcampustrack.assignment_replacement_operation_id',?,TRUE)",
                                String.class,
                                operation.toString());
                        jdbc.update(
                                "UPDATE assignments SET valid_until_exclusive=?,lifecycle_state='CLOSED' WHERE id=?",
                                today,
                                assignmentA);
                        jdbc.update(
                                "UPDATE assignment_replacement_operations SET state='COMMITTED',schedule_receipt_state='COMMITTED' WHERE operation_id=?",
                                operation);
                    });
            for (var denied : List.of(a, ab)) {
                teacherScopeDenied(
                        send(
                                "/auth/export-jobs/" + denied.id(),
                                HttpMethod.GET,
                                null,
                                actor,
                                null));
                teacherScopeDenied(
                        send(
                                "/auth/export-jobs/" + denied.id() + "/file",
                                HttpMethod.GET,
                                null,
                                actor,
                                null));
                teacherScopeDenied(
                        send(
                                "/auth/export-jobs/" + denied.id() + "/ticket",
                                HttpMethod.POST,
                                null,
                                actor,
                                null));
                teacherScopeDenied(
                        send(
                                "/internal/report-download-tickets/redeem",
                                HttpMethod.POST,
                                redemptions.get(denied.id()),
                                null,
                                SECRET));
                teacherScopeDenied(
                        send(
                                "/internal/export-jobs/" + denied.id() + "/complete",
                                HttpMethod.POST,
                                mapper.writeValueAsString(denied.completion()),
                                null,
                                SECRET));
            }
            var stillLive = send("/auth/session", HttpMethod.GET, null, actor, null);
            assertThat(stillLive.getStatusCode()).isEqualTo(HttpStatus.OK);
            JsonNode session = mapper.readTree(stillLive.getBody());
            assertThat(
                            "TEACHER".equals(session.path("activeRole").asText())
                                    && session.path("sessionVersion")
                                            .equals(originalSession.path("sessionVersion"))
                                    && session.path("rolesVersion")
                                            .equals(originalSession.path("rolesVersion")))
                    .as("same current teacher session survives tuple closure")
                    .isTrue();
            assertThat(
                            jdbc.queryForObject(
                                    "SELECT lifecycle_state='ACTIVE' FROM assignments WHERE id=?",
                                    Boolean.class,
                                    assignmentB))
                    .isTrue();
            assertThat(
                            send("/auth/export-jobs/" + b.id(), HttpMethod.GET, null, actor, null)
                                    .getStatusCode())
                    .isEqualTo(HttpStatus.OK);
            var independent =
                    send(
                            "/auth/export-jobs/" + b.id() + "/file",
                            HttpMethod.GET,
                            null,
                            actor,
                            null);
            assertThat(independent.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat("<html>owned teacher scope</html>".equals(independent.getBody())).isTrue();
            assertThat(
                            send(
                                            "/internal/report-download-tickets/redeem",
                                            HttpMethod.POST,
                                            teacherRedemption(actor, b.id()),
                                            null,
                                            SECRET)
                                    .getStatusCode())
                    .isEqualTo(HttpStatus.OK);
            assertThat(
                            send(
                                            "/internal/export-jobs/" + b.id() + "/complete",
                                            HttpMethod.POST,
                                            mapper.writeValueAsString(b.completion()),
                                            null,
                                            SECRET)
                                    .getStatusCode())
                    .isEqualTo(HttpStatus.OK);
            String after =
                    jdbc.queryForObject(
                            "SELECT identity::text FROM auth_export_jobs WHERE job_id=?",
                            String.class,
                            ab.id());
            assertThat(Objects.equals(before, after))
                    .as("denied reads preserve completed provenance")
                    .isTrue();
            assertThat(
                            jdbc.queryForObject(
                                    "SELECT count(*) FROM auth_export_jobs WHERE job_id IN (?,?,?) AND state='ready' AND content IS NOT NULL",
                                    Long.class,
                                    a.id(),
                                    ab.id(),
                                    b.id()))
                    .isEqualTo(3);
        } catch (Throwable failure) {
            primaryFailure = failure;
            throw failure;
        } finally {
            // Retain owned inactive history; restore the original single active semester.
            try {
                transactions.executeWithoutResult(
                        status -> {
                            jdbc.update(
                                    "UPDATE semesters SET is_active=FALSE WHERE id=?", ownSemester);
                            jdbc.update(
                                    "UPDATE semesters SET is_active=TRUE WHERE id=?",
                                    originalSemester);
                        });
            } catch (RuntimeException cleanupFailure) {
                var safeFailure = new AssertionError("Owned semester fixture restoration failed");
                if (primaryFailure != null) primaryFailure.addSuppressed(safeFailure);
                else throw safeFailure;
            }
        }
    }

    private long teacherSubject(long group) {
        long subject =
                jdbc.queryForObject(
                        "INSERT INTO subjects(name,type,group_id) VALUES(?,'lecture',?) RETURNING id",
                        Long.class,
                        "Teacher export subject " + UUID.randomUUID(),
                        group);
        jdbc.update(
                "INSERT INTO subject_lesson_types(subject_id,lesson_type) VALUES(?,'lecture')",
                subject);
        return subject;
    }

    private long teacherAssignment(
            long teacher, long group, long subject, long semester, java.time.LocalDate from) {
        return jdbc.queryForObject(
                "INSERT INTO assignments(teacher_id,group_id,subject_id,semester_id,lesson_type,valid_from) VALUES(?,?,?,?,'lecture',?) RETURNING id",
                Long.class,
                teacher,
                group,
                subject,
                semester,
                from);
    }

    private ReadyTeacherExport readyTeacherExport(
            String actor, String report, String hash, boolean rejectUnproven) throws Exception {
        String create = "{\"requestId\":\"" + UUID.randomUUID() + "\",\"report\":" + report + "}";
        var queued = send("/auth/export-jobs", HttpMethod.POST, create, actor, null);
        assertThat(queued.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        UUID id = UUID.fromString(mapper.readTree(queued.getBody()).path("jobId").asText());
        var claimed = send("/internal/export-jobs/claim", HttpMethod.POST, null, null, SECRET);
        assertThat(claimed.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode claim = mapper.readTree(claimed.getBody());
        assertThat(id.toString().equals(claim.path("jobId").asText())).isTrue();
        UUID lease = UUID.fromString(claim.path("leaseId").asText());
        byte[] bytes = "<html>owned teacher scope</html>".getBytes(StandardCharsets.UTF_8);
        if (rejectUnproven) {
            for (String invalid : new String[] {null, "0".repeat(64)}) {
                var completion =
                        new ExportJobModels.Complete(lease, bytes, "text/html", 0, false, invalid);
                teacherScopeDenied(
                        send(
                                "/internal/export-jobs/" + id + "/complete",
                                HttpMethod.POST,
                                mapper.writeValueAsString(completion),
                                null,
                                SECRET));
                assertThat(
                                jdbc.queryForObject(
                                        "SELECT state='preparing' AND content IS NULL AND NOT(identity ? 'teacherScope' AND identity->'teacherScope'<>'null'::jsonb) FROM auth_export_jobs WHERE job_id=?",
                                        Boolean.class,
                                        id))
                        .as("unproven completion stores no ready bytes or scope")
                        .isTrue();
            }
        }
        var completion = new ExportJobModels.Complete(lease, bytes, "text/html", 0, false, hash);
        var ready =
                send(
                        "/internal/export-jobs/" + id + "/complete",
                        HttpMethod.POST,
                        mapper.writeValueAsString(completion),
                        null,
                        SECRET);
        assertThat(ready.getStatusCode()).isEqualTo(HttpStatus.OK);
        return new ReadyTeacherExport(id, completion);
    }

    private String teacherRedemption(String actor, UUID id) throws Exception {
        var issued =
                send("/auth/export-jobs/" + id + "/ticket", HttpMethod.POST, null, actor, null);
        assertThat(issued.getStatusCode()).isEqualTo(HttpStatus.OK);
        String ticket =
                mapper.readTree(issued.getBody())
                        .path("downloadPath")
                        .asText()
                        .substring("/api/report-download/".length());
        return mapper.writeValueAsString(new RedeemReportDownloadTicketRequest(ticket));
    }

    private void teacherScopeDenied(ResponseEntity<String> response) throws Exception {
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(
                        response.getHeaders()
                                .getContentType()
                                .isCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .isTrue();
        assertThat(
                        "REPORT_PERMISSION_DENIED"
                                .equals(
                                        mapper.readTree(response.getBody())
                                                .path("extras")
                                                .path("code")
                                                .asText()))
                .as("typed current teacher scope denial")
                .isTrue();
    }

    private record ReadyTeacherExport(UUID id, ExportJobModels.Complete completion) {
        @Override
        public String toString() {
            return "ReadyTeacherExport";
        }
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
