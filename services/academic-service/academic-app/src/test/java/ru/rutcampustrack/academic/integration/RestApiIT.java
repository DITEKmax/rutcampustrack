package ru.rutcampustrack.academic.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import ru.rutcampustrack.academic.contract.dto.user.CreateUserRequest;
import ru.rutcampustrack.academic.contract.enums.UserRole;
import ru.rutcampustrack.academic.integration.AcademicIdentityIntegrationFixture.SelectedActor;
import ru.rutcampustrack.auth.session.model.AuthRole;

/**
 * End-to-end integration tests for the REST API layer. Covers all critical paths across User,
 * Semester, Threshold, Homework domains.
 *
 * <p>The owned two-database fixture resets before each test. HTTP commands commit through the real
 * coordinator; an Academic test transaction cannot wrap an Auth operation.
 */
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@org.springframework.context.annotation.Import(RestApiIT.ConfirmationConfig.class)
class RestApiIT extends AbstractAcademicIntegrationTest {

    @org.springframework.boot.test.context.TestConfiguration
    static class ConfirmationConfig {
        @org.springframework.context.annotation.Bean(destroyMethod = "close")
        ru.rutcampustrack.academic.testhelpers.AuthConfirmationHttpFixture confirmationHttp(
                AcademicIdentityIntegrationFixture fixture) {
            return new ru.rutcampustrack.academic.testhelpers.AuthConfirmationHttpFixture(fixture);
        }

        @org.springframework.context.annotation.Bean
        @org.springframework.context.annotation.Primary
        ru.rutcampustrack.academic.semester.AuthSemesterDeletionClient actualPasswordConfirmation(
                org.springframework.boot.web.client.RestTemplateBuilder builder,
                ru.rutcampustrack.academic.security.RequestContext context,
                ru.rutcampustrack.academic.testhelpers.AuthConfirmationHttpFixture server,
                AcademicIdentityIntegrationFixture fixture) {
            return new ru.rutcampustrack.academic.semester.AuthSemesterDeletionClient(
                    builder, context, server.url(), fixture.internalIssuerProperties().getSecret());
        }
    }

    @org.springframework.test.context.bean.override.mockito.MockitoBean
    ru.rutcampustrack.academic.grpc.ScheduleGrpcClient deletionSchedule;

    @org.springframework.test.context.bean.override.mockito.MockitoBean
    ru.rutcampustrack.academic.grpc.AttendanceSemesterDeletionGrpcClient deletionAttendance;

    @Autowired private MockMvc mockMvc;
    @Autowired private org.springframework.boot.test.web.client.TestRestTemplate http;

    @Autowired private ObjectMapper objectMapper;

    @Autowired private JdbcTemplate jdbcTemplate;

    @Autowired private java.time.Clock clock;

    @Autowired private ru.rutcampustrack.academic.group.GroupService groupService;

    private Long seedStudentId;
    private Long seedGroupId;
    private Long seedAdminId;
    private String seedStudentLogin;
    private SelectedActor adminActor;
    private SelectedActor studentActor;
    private SelectedActor headmanActor;

    @BeforeEach
    void loadSeedIds() {
        var seed = identityFixture.canonical();
        seedStudentId = seed.studentId();
        seedGroupId = seed.groupId();
        seedAdminId = seed.adminId();
        seedStudentLogin =
                identityFixture
                        .authJdbc()
                        .queryForObject(
                                "SELECT login FROM auth_identities WHERE id=?",
                                String.class,
                                seedStudentId);
        adminActor = identityFixture.select(seedAdminId, AuthRole.ADMIN);
        studentActor = identityFixture.select(seedStudentId, AuthRole.STUDENT);
        headmanActor = identityFixture.select(seedStudentId, AuthRole.HEADMAN);
    }

    // =====================================================================
    // Test 1 — USER-01: Admin creates user, receives login and initial password
    // =====================================================================

    @Test
    @Order(1)
    void testAdminCreateUser_returnsLoginAndPassword() throws Exception {
        Long managedGroupId =
                groupService
                        .createGroup(
                                new ru.rutcampustrack.academic.contract.dto.group
                                        .CreateGroupRequest("ТЕС-111"))
                        .getId();
        String body =
                """
                {
                  "lastName": "Тестов",
                  "firstName": "Тест",
                  "middleName": "Тестович",
                  "role": "STUDENT",
                  "groupId": %d,
                  "telegramId": 555000001
                }
                """
                        .formatted(managedGroupId);

        MvcResult result =
                mockMvc.perform(
                                post("/academic/users")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(body)
                                        .header("X-User-Id", seedAdminId)
                                        .header("X-User-Role", "ADMIN")
                                        .with(identityFixture.signed(adminActor))
                                        .header("X-Group-Id", "")
                                        .header("X-Is-Headman", "false"))
                        .andExpect(status().isCreated())
                        .andExpect(jsonPath("$.login", is("testov-test-0001")))
                        .andExpect(jsonPath("$.initialPassword", notNullValue()))
                        .andExpect(jsonPath("$._links.self.href", notNullValue()))
                        .andReturn();

        String content = result.getResponse().getContentAsString();
        JsonNode json = objectMapper.readTree(content);
        assertThat(json.get("login").asText()).isEqualTo("testov-test-0001");
        assertThat(json.get("initialPassword").asText()).hasSizeGreaterThan(0);
    }

    @Test
    void duplicateIdentityResolvesToExistingAccountAndRoleFailuresLeaveCommittedStateUnchanged()
            throws Exception {
        // No test transaction: each HTTP mutation commits before the independent SQL readback.
        long initialAccountCount =
                identityFixture
                        .authJdbc()
                        .queryForObject("SELECT COUNT(*) FROM auth_identities", Long.class);
        Long managedGroupId =
                groupService
                        .createGroup(
                                new ru.rutcampustrack.academic.contract.dto.group
                                        .CreateGroupRequest("ДУБ-111"))
                        .getId();
        long telegramId = 880004127L;
        String occupiedEmployeeNumber = "Q04-4127";
        String availableEmployeeNumber = "Q04-8259";
        String studentBody =
                """
                {"lastName":"Дубликатов","firstName":"Тест","role":"STUDENT",
                 "groupId":%d,"telegramId":%d}
                """
                        .formatted(managedGroupId, telegramId);
        JsonNode student =
                objectMapper.readTree(
                        mockMvc.perform(
                                        post("/academic/users")
                                                .contentType(MediaType.APPLICATION_JSON)
                                                .content(studentBody)
                                                .header("X-User-Id", seedAdminId)
                                                .header("X-User-Role", "ADMIN")
                                                .with(identityFixture.signed(adminActor)))
                                .andExpect(status().isCreated())
                                .andReturn()
                                .getResponse()
                                .getContentAsString());
        long studentId = student.get("id").asLong();
        String studentLogin = student.get("login").asText();
        JsonNode teacher =
                objectMapper.readTree(
                        mockMvc.perform(
                                        post("/academic/users")
                                                .contentType(MediaType.APPLICATION_JSON)
                                                .content(
                                                        """
{"lastName":"Дубликатов","firstName":"Тест","role":"TEACHER",
 "employeeNumber":"%s"}
"""
                                                                .formatted(occupiedEmployeeNumber))
                                                .header("X-User-Id", seedAdminId)
                                                .header("X-User-Role", "ADMIN")
                                                .with(identityFixture.signed(adminActor)))
                                .andExpect(status().isCreated())
                                .andReturn()
                                .getResponse()
                                .getContentAsString());
        long teacherId = teacher.get("id").asLong();
        assertThat(teacherId).isNotEqualTo(studentId);
        assertThat(teacher.get("login").asText()).isEqualTo(studentLogin + "-2");
        assertThat(
                        identityFixture
                                .authJdbc()
                                .queryForObject("SELECT COUNT(*) FROM auth_identities", Long.class))
                .isEqualTo(initialAccountCount + 2);
        assertThat(
                        identityFixture
                                .authJdbc()
                                .queryForObject(
                                        "SELECT COUNT(*) FROM user_role_grants WHERE user_id=?",
                                        Long.class,
                                        studentId))
                .isEqualTo(1);

        java.util.function.Supplier<java.util.List<?>> durableState =
                () ->
                        java.util.List.of(
                                identityFixture
                                        .authJdbc()
                                        .queryForList(
                                                "SELECT * FROM auth_identities WHERE id IN (?, ?)"
                                                        + " ORDER BY id",
                                                studentId,
                                                teacherId),
                                jdbcTemplate.queryForList(
                                        "SELECT * FROM academic_profiles WHERE id IN (?, ?) ORDER"
                                                + " BY id",
                                        studentId,
                                        teacherId),
                                identityFixture
                                        .authJdbc()
                                        .queryForList(
                                                "SELECT * FROM user_role_grants WHERE user_id IN"
                                                        + " (?, ?) ORDER BY id",
                                                studentId,
                                                teacherId),
                                jdbcTemplate.queryForList(
                                        "SELECT * FROM student_group_history WHERE user_id IN (?,"
                                                + " ?) ORDER BY id",
                                        studentId,
                                        teacherId),
                                identityFixture
                                        .authJdbc()
                                        .queryForObject(
                                                "SELECT COUNT(*) FROM auth_identities",
                                                Long.class));
        var beforeFailures = durableState.get();

        mockMvc.perform(
                        post("/academic/users")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(studentBody)
                                .header("X-User-Id", seedAdminId)
                                .header("X-User-Role", "ADMIN")
                                .with(identityFixture.signed(adminActor)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.field", is("telegramId")));
        // Private credential/hash snapshots are compared as booleans, never assertion payloads.
        assertThat(beforeFailures.equals(durableState.get()))
                .as("Duplicate identity must preserve committed account, grant and history state")
                .isTrue();
        mockMvc.perform(
                        get("/academic/users")
                                .param("search", Long.toString(telegramId))
                                .header("X-User-Id", seedAdminId)
                                .header("X-User-Role", "ADMIN")
                                .with(identityFixture.signed(adminActor)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements", is(1)))
                .andExpect(jsonPath("$._embedded.userResponseList[0].id", is((int) studentId)))
                .andExpect(jsonPath("$._embedded.userResponseList[0].login", is(studentLogin)));

        mockMvc.perform(
                        put("/academic/users/{id}/roles/TEACHER", studentId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {"status":"ACTIVE","employeeNumber":"%s"}
                                        """
                                                .formatted(occupiedEmployeeNumber))
                                .header("X-User-Id", seedAdminId)
                                .header("X-User-Role", "ADMIN")
                                .with(identityFixture.signed(adminActor)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.field", is("employeeNumber")));
        assertThat(beforeFailures.equals(durableState.get()))
                .as(
                        "Occupied employee number must preserve committed account, grant and"
                                + " history state")
                .isTrue();

        mockMvc.perform(
                        put("/academic/users/{id}/roles/TEACHER", studentId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {"status":"ACTIVE","employeeNumber":"%s","groupId":%d}
                                        """
                                                .formatted(availableEmployeeNumber, managedGroupId))
                                .header("X-User-Id", seedAdminId)
                                .header("X-User-Role", "ADMIN")
                                .with(identityFixture.signed(adminActor)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.field", is("groupId")));
        assertThat(beforeFailures.equals(durableState.get()))
                .as(
                        "Invalid teacher role data must preserve committed account, grant and"
                                + " history state")
                .isTrue();

        String preservedCredentials =
                "SELECT"
                    + " id,login,password_hash,initial_password,telegram_id,password_changed,created_at"
                    + " FROM auth_identities WHERE id=?";
        String preservedProfile =
                "SELECT"
                    + " id,last_name,first_name,middle_name,role,status,group_id,is_headman,created_at"
                    + " FROM academic_user_directory WHERE id=?";
        var studentIdentity =
                java.util.List.of(
                        identityFixture.authJdbc().queryForMap(preservedCredentials, studentId),
                        jdbcTemplate.queryForMap(preservedProfile, studentId));
        var teacherAccount =
                java.util.List.of(
                        identityFixture
                                .authJdbc()
                                .queryForMap("SELECT * FROM auth_identities WHERE id=?", teacherId),
                        jdbcTemplate.queryForMap(
                                "SELECT * FROM academic_profiles WHERE id=?", teacherId));
        var studentGrant =
                identityFixture
                        .authJdbc()
                        .queryForList(
                                "SELECT * FROM user_role_grants WHERE user_id=? AND role='student'"
                                        + " ORDER BY id",
                                studentId);
        var studentHistory =
                jdbcTemplate.queryForList(
                        "SELECT * FROM student_group_history WHERE user_id=? ORDER BY id",
                        studentId);
        assertThat(studentGrant).hasSize(1);
        assertThat(studentHistory).hasSize(1);

        mockMvc.perform(
                        put("/academic/users/{id}/roles/TEACHER", studentId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {"status":"ACTIVE","employeeNumber":"%s"}
                                        """
                                                .formatted(availableEmployeeNumber))
                                .header("X-User-Id", seedAdminId)
                                .header("X-User-Role", "ADMIN")
                                .with(identityFixture.signed(adminActor)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is((int) studentId)))
                .andExpect(jsonPath("$.login", is(studentLogin)));

        assertThat(
                        studentIdentity.equals(
                                java.util.List.of(
                                        identityFixture
                                                .authJdbc()
                                                .queryForMap(preservedCredentials, studentId),
                                        jdbcTemplate.queryForMap(preservedProfile, studentId))))
                .as("Adding a role must preserve the existing account identity and credentials")
                .isTrue();
        assertThat(
                        teacherAccount.equals(
                                java.util.List.of(
                                        identityFixture
                                                .authJdbc()
                                                .queryForMap(
                                                        "SELECT * FROM auth_identities WHERE id=?",
                                                        teacherId),
                                        jdbcTemplate.queryForMap(
                                                "SELECT * FROM academic_profiles WHERE id=?",
                                                teacherId))))
                .as("Adding a role must preserve the other account")
                .isTrue();
        assertThat(
                        studentGrant.equals(
                                identityFixture
                                        .authJdbc()
                                        .queryForList(
                                                "SELECT * FROM user_role_grants WHERE user_id=? AND"
                                                        + " role='student' ORDER BY id",
                                                studentId)))
                .as("Adding a teacher role must preserve the existing student grant")
                .isTrue();
        assertThat(
                        studentHistory.equals(
                                jdbcTemplate.queryForList(
                                        "SELECT * FROM student_group_history WHERE user_id=? ORDER"
                                                + " BY id",
                                        studentId)))
                .as("Adding a teacher role must preserve the existing student history")
                .isTrue();
        assertThat(
                        identityFixture
                                .authJdbc()
                                .queryForObject("SELECT COUNT(*) FROM auth_identities", Long.class))
                .isEqualTo(initialAccountCount + 2);
        assertThat(
                        identityFixture
                                .authJdbc()
                                .queryForObject(
                                        "SELECT COUNT(*) FROM user_role_grants WHERE user_id=?",
                                        Long.class,
                                        studentId))
                .isEqualTo(2);
        assertThat(
                        identityFixture
                                .authJdbc()
                                .queryForObject(
                                        "SELECT COUNT(*) FROM user_role_grants WHERE user_id=? AND"
                                                + " role='teacher' AND status='active'",
                                        Long.class,
                                        studentId))
                .isEqualTo(1);
        assertThat(
                        jdbcTemplate.queryForObject(
                                "SELECT employee_number FROM academic_profiles WHERE id=?",
                                String.class,
                                studentId))
                .isEqualTo(availableEmployeeNumber);
        mockMvc.perform(
                        get("/academic/users/{id}", studentId)
                                .header("X-User-Id", seedAdminId)
                                .header("X-User-Role", "ADMIN")
                                .with(identityFixture.signed(adminActor)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is((int) studentId)))
                .andExpect(jsonPath("$.login", is(studentLogin)))
                .andExpect(jsonPath("$.roles", hasSize(2)))
                .andExpect(
                        jsonPath(
                                "$.roles[?(@.role == 'STUDENT' && @.status == 'ACTIVE')]",
                                hasSize(1)))
                .andExpect(
                        jsonPath(
                                "$.roles[?(@.role == 'TEACHER' && @.status == 'ACTIVE')]",
                                hasSize(1)));
    }

    // =====================================================================
    // Test 2 — Validation error returns RFC 7807 with fieldErrors
    // =====================================================================

    @Test
    @Order(2)
    void testAdminCreateUser_validationError_returns400WithFieldErrors() throws Exception {
        String body =
                """
                {
                  "lastName": "",
                  "firstName": "",
                  "role": "STUDENT"
                }
                """;

        mockMvc.perform(
                        post("/academic/users")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body)
                                .header("X-User-Id", seedAdminId)
                                .header("X-User-Role", "ADMIN")
                                .with(identityFixture.signed(adminActor))
                                .header("X-Group-Id", "")
                                .header("X-Is-Headman", "false"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type", containsString("validation")))
                .andExpect(jsonPath("$.fieldErrors", notNullValue()))
                .andExpect(
                        jsonPath(
                                "$.fieldErrors[?(@.field == 'lastName')]",
                                hasSize(greaterThan(0))));
    }

    // =====================================================================
    // Test 3 — Unauthorized role gets 403 Forbidden
    // =====================================================================

    @Test
    @Order(3)
    void testStudentCannotCreateUser_returns403() throws Exception {
        String body =
                """
                {
                  "lastName": "Другов",
                  "firstName": "Другой",
                  "role": "STUDENT",
                  "groupId": %d
                }
                """
                        .formatted(seedGroupId);

        mockMvc.perform(
                        post("/academic/users")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body)
                                .header("X-User-Id", seedStudentId)
                                .header("X-User-Role", "STUDENT")
                                .with(identityFixture.signed(studentActor))
                                .header("X-Group-Id", seedGroupId)
                                .header("X-Is-Headman", "false"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status", is(403)));
    }

    // =====================================================================
    // Test 4 — GSEM-02/03: Future intents preserve the current active semester
    // =====================================================================

    @Test
    @Order(4)
    void testAdminCreateAndActivateSemester() throws Exception {
        int futureYear =
                java.time.LocalDate.now(clock.withZone(java.time.ZoneId.of("Europe/Moscow")))
                                .getYear()
                        + 1;
        // Create semester 1
        String body1 =
                """
                {
                  "name": "TestSemester Alpha",
                  "dateFrom": "%d-02-01",
                  "dateTo": "%d-06-30",
                  "semesterType": "SPRING",
                  "academicYear": %d
                }
                """
                        .formatted(futureYear, futureYear, futureYear - 1);

        MvcResult r1 =
                mockMvc.perform(
                                post("/academic/semesters")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(body1)
                                        .header("X-User-Id", seedAdminId)
                                        .header("X-User-Role", "ADMIN")
                                        .with(identityFixture.signed(adminActor))
                                        .header("X-Group-Id", "")
                                        .header("X-Is-Headman", "false"))
                        .andExpect(status().isCreated())
                        .andReturn();

        Long sem1Id =
                objectMapper.readTree(r1.getResponse().getContentAsString()).get("id").asLong();

        // Create semester 2
        String body2 =
                """
                {
                  "name": "TestSemester Beta",
                  "dateFrom": "%d-09-01",
                  "dateTo": "%d-01-31",
                  "semesterType": "AUTUMN",
                  "academicYear": %d
                }
                """
                        .formatted(futureYear, futureYear + 1, futureYear);

        MvcResult r2 =
                mockMvc.perform(
                                post("/academic/semesters")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(body2)
                                        .header("X-User-Id", seedAdminId)
                                        .header("X-User-Role", "ADMIN")
                                        .with(identityFixture.signed(adminActor))
                                        .header("X-Group-Id", "")
                                        .header("X-Is-Headman", "false"))
                        .andExpect(status().isCreated())
                        .andReturn();

        Long sem2Id =
                objectMapper.readTree(r2.getResponse().getContentAsString()).get("id").asLong();

        // Future activation saves an intent and preserves the current semester.
        mockMvc.perform(
                        patch("/academic/semesters/" + sem1Id + "/activate")
                                .header("X-User-Id", seedAdminId)
                                .header("X-User-Role", "ADMIN")
                                .with(identityFixture.signed(adminActor))
                                .header("X-Group-Id", "")
                                .header("X-Is-Headman", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active", is(false)))
                .andExpect(jsonPath("$.activationScheduled", is(true)));

        // Another future intent also remains inactive until its date arrives.
        mockMvc.perform(
                        patch("/academic/semesters/" + sem2Id + "/activate")
                                .header("X-User-Id", seedAdminId)
                                .header("X-User-Role", "ADMIN")
                                .with(identityFixture.signed(adminActor))
                                .header("X-Group-Id", "")
                                .header("X-Is-Headman", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active", is(false)))
                .andExpect(jsonPath("$.activationScheduled", is(true)));

        // Both intentions survive; neither removes the existing active semester.
        mockMvc.perform(
                        get("/academic/semesters/" + sem1Id)
                                .header("X-User-Id", seedAdminId)
                                .header("X-User-Role", "ADMIN")
                                .with(identityFixture.signed(adminActor))
                                .header("X-Group-Id", "")
                                .header("X-Is-Headman", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active", is(false)));
        assertThat(
                        jdbcTemplate.queryForObject(
                                "SELECT is_active FROM semesters WHERE id=1", Boolean.class))
                .isTrue();
    }

    // =====================================================================
    // Test 5 — GSEM-04: Delete semester with wrong confirmation returns 400
    // =====================================================================

    @Test
    @Order(5)
    void testSemesterDeleteWithWrongConfirmation_returns400() throws Exception {
        long semester = identityFixture.canonical().semesterId();
        org.mockito.Mockito.when(deletionSchedule.previewSemesterDeletion(semester))
                .thenReturn(
                        ru.rutcampustrack.schedule.grpc.SemesterDeletionParticipantPreview
                                .newBuilder()
                                .setSemesterId(semester)
                                .setParticipantDigest("11".repeat(32))
                                .build());
        org.mockito.Mockito.when(deletionAttendance.preview(semester))
                .thenReturn(
                        ru.rutcampustrack.attendance.grpc.SemesterDeletionParticipantPreview
                                .newBuilder()
                                .setSemesterId(semester)
                                .setParticipantDigest("22".repeat(32))
                                .build());
        var headers = new org.springframework.http.HttpHeaders();
        headers.set("X-Internal-Token", adminActor.internalToken());
        headers.setContentType(MediaType.APPLICATION_JSON);
        var preview =
                http.exchange(
                        "/academic/semesters/" + semester + "/delete-preview",
                        org.springframework.http.HttpMethod.GET,
                        new org.springframework.http.HttpEntity<>(headers),
                        String.class);
        assertThat(preview.getStatusCode()).isEqualTo(org.springframework.http.HttpStatus.OK);
        String digest = objectMapper.readTree(preview.getBody()).path("previewDigest").asText();
        assertThat(digest).matches("[0-9a-f]{64}");
        var before = deletionState(semester);
        headers.set("Idempotency-Key", java.util.UUID.randomUUID().toString());
        var response =
                http.exchange(
                        "/academic/semesters/" + semester,
                        org.springframework.http.HttpMethod.DELETE,
                        new org.springframework.http.HttpEntity<>(
                                java.util.Map.of(
                                        "previewDigest",
                                        digest,
                                        "password",
                                        "synthetic-wrong-current-password"),
                                headers),
                        String.class);
        assertThat(response.getStatusCode())
                .isEqualTo(org.springframework.http.HttpStatus.FORBIDDEN);
        assertThat(objectMapper.readTree(response.getBody()).path("status").asInt()).isEqualTo(403);
        assertThat(deletionState(semester)).isEqualTo(before);
    }

    private java.util.List<Object> deletionState(long semester) {
        return java.util.List.of(
                jdbcTemplate.queryForMap("SELECT * FROM semesters WHERE id=?", semester),
                jdbcTemplate.queryForList(
                        "SELECT * FROM semester_archive_operations ORDER BY operation_id"),
                jdbcTemplate.queryForList("SELECT * FROM academic_outbox ORDER BY id"),
                jdbcTemplate.queryForList(
                        "SELECT to_jsonb(t)::text FROM academic_export_jobs t ORDER BY job_id",
                        String.class),
                identityFixture
                        .authJdbc()
                        .queryForList(
                                "SELECT to_jsonb(t)::text FROM auth_identity_operations t ORDER BY"
                                        + " operation_id",
                                String.class),
                jdbcTemplate.queryForList(
                        "SELECT to_jsonb(t)::text FROM academic_semester_archive_effect_receipts t"
                                + " ORDER BY source_event_id",
                        String.class),
                jdbcTemplate.queryForList(
                        "SELECT to_jsonb(t)::text FROM academic_semester_archive_barriers t ORDER"
                                + " BY semester_id",
                        String.class));
    }

    // =====================================================================
    // Test 6 — USER-06: Student gets own profile via GET /me
    // =====================================================================

    @Test
    @Order(6)
    void testStudentGetMe_returnsOwnProfile() throws Exception {
        mockMvc.perform(
                        get("/academic/users/me")
                                .header("X-User-Id", seedStudentId)
                                .header("X-User-Role", "STUDENT")
                                .with(identityFixture.signed(studentActor))
                                .header("X-Group-Id", seedGroupId)
                                .header("X-Is-Headman", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.login", is(seedStudentLogin)))
                .andExpect(jsonPath("$.role", is("STUDENT")));
    }

    // =====================================================================
    // Test 7 — THRSH-04: Threshold resolution — most specific wins
    // =====================================================================

    @Test
    @Order(7)
    void testThresholdResolution_mostSpecificWins() throws Exception {
        long group = seedGroupId;
        long semester = identityFixture.canonical().semesterId();
        long subject = thresholdSubject("threshold own", group, semester);
        long otherGroup =
                jdbcTemplate.queryForObject(
                        "INSERT INTO groups(name,is_active,created_at) VALUES('ПРГ-111',true,NOW())"
                                + " RETURNING id",
                        Long.class);
        long otherSubject = thresholdSubject("threshold other", otherGroup, semester);
        long global = threshold(null, null, 70);
        long groupThreshold = threshold(group, null, 80);
        long subjectThreshold = threshold(group, subject, 90);
        long control = threshold(otherGroup, otherSubject, 95);
        resolved(group, subject, "subject", 90, subjectThreshold);
        resolved(otherGroup, otherSubject, "subject", 95, control);
        jdbcTemplate.update("DELETE FROM attendance_thresholds WHERE id=?", subjectThreshold);
        resolved(group, subject, "group", 80, groupThreshold);
        jdbcTemplate.update("DELETE FROM attendance_thresholds WHERE id=?", groupThreshold);
        resolved(group, subject, "global", 70, global);
        jdbcTemplate.update("DELETE FROM attendance_thresholds WHERE id=?", global);
        resolved(group, subject, "default", 0, null);
        resolved(otherGroup, otherSubject, "subject", 95, control);
    }

    private long thresholdSubject(String name, long group, long semester) {
        // The deferred minimum-type constraint checks the same committed parent/child pair.
        return jdbcTemplate.queryForObject(
                "WITH subject AS (INSERT INTO subjects(name,type,group_id,semester_id)"
                        + " VALUES(?,'lecture',?,?) RETURNING id), types AS"
                        + " (INSERT INTO subject_lesson_types(subject_id,lesson_type) SELECT"
                        + " id,'lecture' FROM subject RETURNING subject_id) SELECT subject_id"
                        + " FROM types",
                Long.class,
                name,
                group,
                semester);
    }

    private long threshold(Long group, Long subject, int percent) {
        return jdbcTemplate.queryForObject(
                "INSERT INTO"
                    + " attendance_thresholds(group_id,subject_id,threshold_pct,set_by,created_at)"
                    + " VALUES(?,?,?,?,NOW()) RETURNING id",
                Long.class,
                group,
                subject,
                percent,
                seedAdminId);
    }

    private void resolved(long group, long subject, String level, int percent, Long source)
            throws Exception {
        var headers = new org.springframework.http.HttpHeaders();
        headers.set("X-Internal-Token", adminActor.internalToken());
        var response =
                http.exchange(
                        "/academic/thresholds/resolve?groupId=" + group + "&subjectId=" + subject,
                        org.springframework.http.HttpMethod.GET,
                        new org.springframework.http.HttpEntity<>(headers),
                        String.class);
        assertThat(response.getStatusCode()).isEqualTo(org.springframework.http.HttpStatus.OK);
        var json = objectMapper.readTree(response.getBody());
        assertThat(json.path("level").asText()).isEqualTo(level);
        assertThat(json.path("minPercentage").asInt()).isEqualTo(percent);
        if (source == null)
            assertThat(json.path("sourceId").isMissingNode() || json.path("sourceId").isNull())
                    .isTrue();
        else assertThat(json.path("sourceId").asLong()).isEqualTo(source);
    }

    // =====================================================================
    // Test 8 — HATEOAS pagination: List users returns PagedModel with _links
    // =====================================================================

    @Test
    @Order(8)
    void testListUsers_returnsPaginatedHateoas() throws Exception {
        mockMvc.perform(
                        get("/academic/users")
                                .param("page", "0")
                                .param("size", "2")
                                .header("X-User-Id", seedAdminId)
                                .header("X-User-Role", "ADMIN")
                                .with(identityFixture.signed(adminActor))
                                .header("X-Group-Id", "")
                                .header("X-Is-Headman", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$._embedded", notNullValue()))
                .andExpect(jsonPath("$._links.self.href", notNullValue()))
                .andExpect(jsonPath("$.page.totalElements", greaterThanOrEqualTo(3)));
    }

    // =====================================================================
    // Test 9 — D016: standalone removal cannot change the headman or assistants
    // =====================================================================

    @Test
    @Order(9)
    void testStandaloneHeadmanRemovalKeepsHeadmanAndAssistants() throws Exception {
        Long headmanId = seedStudentId;
        Long assistantId =
                identityFixture
                        .seedUser(
                                new CreateUserRequest(
                                        "Assistant",
                                        "User",
                                        null,
                                        UserRole.STUDENT,
                                        seedGroupId,
                                        null,
                                        610000009009L),
                                adminActor)
                        .userId();

        // Assign the student as an assistant to the headman's group via native SQL
        jdbcTemplate.update(
                "INSERT INTO headman_assistants (group_id, student_id, permissions, assigned_by,"
                        + " is_active, assigned_at) VALUES (?, ?,"
                        + " ARRAY['mark_attendance']::varchar(64)[], ?, true, NOW())",
                seedGroupId,
                assistantId,
                headmanId);

        // Verify assistant exists before revoke
        Integer countBefore =
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM headman_assistants WHERE group_id = ? AND is_active ="
                                + " true",
                        Integer.class,
                        seedGroupId);
        assertThat(countBefore).isGreaterThan(0);

        // Standalone removal is refused before the authority cascade.
        String patchBody =
                """
                { "isHeadman": false }
                """;
        mockMvc.perform(
                        patch("/academic/users/" + headmanId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(patchBody)
                                .header("X-User-Id", seedAdminId)
                                .header("X-User-Role", "ADMIN")
                                .with(identityFixture.signed(adminActor))
                                .header("X-Group-Id", "")
                                .header("X-Is-Headman", "false"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.field", equalTo("isHeadman")));

        // The rejected operation leaves all assistants and current headman authority unchanged.
        Integer countAfter =
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM headman_assistants WHERE group_id = ? AND is_active ="
                                + " true",
                        Integer.class,
                        seedGroupId);
        assertThat(countAfter).isEqualTo(countBefore);
        assertThat(
                        jdbcTemplate.queryForObject(
                                "SELECT is_headman FROM academic_user_directory WHERE id = ?",
                                Boolean.class,
                                headmanId))
                .isTrue();
    }

    // =====================================================================
    // Test 10 — HW-03: Student can mark and unmark homework as completed
    // =====================================================================

    @Test
    @Order(10)
    void testHomeworkCompletionToggle() throws Exception {
        // Create a subject first
        // Phase 60-01 V12: subjects.group_id NOT NULL — используем seedGroupId
        Long subjectId =
                jdbcTemplate.queryForObject(
                        "WITH subject AS (INSERT INTO subjects (name, type, group_id) VALUES"
                            + " ('Integration Test Subject', 'lecture', ?) RETURNING id), types AS"
                            + " (INSERT INTO subject_lesson_types(subject_id,lesson_type) SELECT"
                            + " id,'lecture' FROM subject RETURNING subject_id) SELECT subject_id"
                            + " FROM types",
                        Long.class,
                        seedGroupId);

        // Deactivate existing active semester to avoid constraint issues
        jdbcTemplate.update("UPDATE semesters SET is_active = false WHERE is_active = true");

        // Create a semester for homework.
        // Plan 58-05 / V10 semesters_no_overlap EXCLUDE: the date range must not
        // overlap any semester previously created in this test class (@Order 4
        // leaves two 2027 semesters in place; @Order 5 leaves a 2035-2036 one).
        // Use a far-future window that is guaranteed empty.
        Long semesterId =
                jdbcTemplate.queryForObject(
                        "INSERT INTO semesters (name, date_from, date_to, is_active, created_at)"
                                + " VALUES ('HW Test Semester', '2040-02-01', '2040-06-30', false,"
                                + " NOW()) RETURNING id",
                        Long.class);

        // Create homework as headman via native SQL (avoid headman permission check).
        // Phase 61-02 V13: lesson_date / lesson_number NOT NULL.
        Long homeworkId =
                jdbcTemplate.queryForObject(
                        "INSERT INTO homeworks (group_id, subject_id, semester_id, title,"
                            + " published_by, lesson_date, lesson_number,"
                            + " binding_id,actor_id,request_key,payload_hash,publication_state,created_at,updated_at)"
                            + " VALUES (?, ?, ?, 'Test HW', ?, '2040-03-01', 1, 20400301, ?, ?,"
                            + " decode(repeat('ab',32),'hex'),'ACTIVE',NOW(),NOW()) RETURNING id",
                        Long.class,
                        seedGroupId,
                        subjectId,
                        semesterId,
                        seedStudentId,
                        seedStudentId,
                        java.util.UUID.randomUUID());

        // Mark homework as complete (student) — M11 G3: 200→204 (void mutation)
        mockMvc.perform(
                        post("/academic/homeworks/" + homeworkId + "/complete")
                                .header("X-User-Id", seedStudentId)
                                .header("X-User-Role", "STUDENT")
                                .with(identityFixture.signed(studentActor))
                                .header("X-Group-Id", seedGroupId)
                                .header("X-Is-Headman", "false"))
                .andExpect(status().isNoContent());

        // Verify it's marked in DB
        Integer count =
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM homework_completions WHERE homework_id = ? AND"
                                + " student_id = ?",
                        Integer.class,
                        homeworkId,
                        seedStudentId);
        assertThat(count).isEqualTo(1);

        // Unmark homework
        mockMvc.perform(
                        delete("/academic/homeworks/" + homeworkId + "/complete")
                                .header("X-User-Id", seedStudentId)
                                .header("X-User-Role", "STUDENT")
                                .with(identityFixture.signed(studentActor))
                                .header("X-Group-Id", seedGroupId)
                                .header("X-Is-Headman", "false"))
                .andExpect(status().isNoContent());

        // Verify it's unmarked
        Integer countAfter =
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM homework_completions WHERE homework_id = ? AND"
                                + " student_id = ?",
                        Integer.class,
                        homeworkId,
                        seedStudentId);
        assertThat(countAfter).isEqualTo(0);
    }

    // =====================================================================
    // Test 11 — DASH-01: Dashboard stats returns all required fields
    // =====================================================================

    @Test
    @Order(11)
    void testDashboardStats_returnsAllFields() throws Exception {
        mockMvc.perform(
                        get("/academic/dashboard/stats")
                                .header("X-User-Id", seedAdminId)
                                .header("X-User-Role", "ADMIN")
                                .with(identityFixture.signed(adminActor))
                                .header("X-Group-Id", "")
                                .header("X-Is-Headman", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalStudents", greaterThanOrEqualTo(0)))
                .andExpect(jsonPath("$.totalTeachers", greaterThanOrEqualTo(0)))
                .andExpect(jsonPath("$.totalGroups", greaterThanOrEqualTo(0)))
                .andExpect(jsonPath("$.activeGroups", greaterThanOrEqualTo(0)))
                .andExpect(jsonPath("$._links.self.href", notNullValue()));
    }

    // =====================================================================
    // Test 12 — Dashboard stats returns 403 for non-admin
    // =====================================================================

    @Test
    @Order(12)
    void testDashboardStats_returns403ForStudent() throws Exception {
        mockMvc.perform(
                        get("/academic/dashboard/stats")
                                .header("X-User-Id", seedStudentId)
                                .header("X-User-Role", "STUDENT")
                                .with(identityFixture.signed(studentActor))
                                .header("X-Group-Id", seedGroupId)
                                .header("X-Is-Headman", "false"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status", is(403)));
    }
}
