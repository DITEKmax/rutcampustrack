package ru.rutcampustrack.academic.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * End-to-end integration tests for the REST API layer.
 * Covers all critical paths across User, Semester, Threshold, Homework domains.
 *
 * Uses AbstractAcademicIntegrationTest (Testcontainers PostgreSQL, no RabbitMQ/Redis).
 * Tests run in @Transactional for automatic rollback — DB state is restored per test.
 */
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class RestApiIntegrationTest extends AbstractAcademicIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    // Seed data IDs (from V2 migrations)
    private Long seedStudentId;
    private Long seedGroupId;
    private Long seedAdminId;

    @BeforeEach
    void loadSeedIds() {
        seedStudentId = jdbcTemplate.queryForObject(
                "SELECT id FROM users WHERE login = 'student'", Long.class);
        seedGroupId = jdbcTemplate.queryForObject(
                "SELECT id FROM groups WHERE name = 'IVT-21-1'", Long.class);
        seedAdminId = jdbcTemplate.queryForObject(
                "SELECT id FROM users WHERE login = 'admin'", Long.class);
    }

    // =====================================================================
    // Test 1 — USER-01: Admin creates user, receives login and initial password
    // =====================================================================

    @Test
    @Order(1)
    @Transactional
    void testAdminCreateUser_returnsLoginAndPassword() throws Exception {
        String body = """
                {
                  "lastName": "Тестов",
                  "firstName": "Тест",
                  "middleName": "Тестович",
                  "role": "STUDENT",
                  "groupId": %d
                }
                """.formatted(seedGroupId);

        MvcResult result = mockMvc.perform(post("/academic/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .header("X-User-Id", seedAdminId)
                        .header("X-User-Role", "ADMIN")
                        .header("X-Group-Id", "")
                        .header("X-Is-Headman", "false"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.login", matchesPattern("student\\d{5}")))
                .andExpect(jsonPath("$.initialPassword", notNullValue()))
                .andExpect(jsonPath("$._links.self.href", notNullValue()))
                .andReturn();

        String content = result.getResponse().getContentAsString();
        JsonNode json = objectMapper.readTree(content);
        assertThat(json.get("login").asText()).matches("student\\d{5}");
        assertThat(json.get("initialPassword").asText()).hasSizeGreaterThan(0);
    }

    // =====================================================================
    // Test 2 — Validation error returns RFC 7807 with fieldErrors
    // =====================================================================

    @Test
    @Order(2)
    void testAdminCreateUser_validationError_returns400WithFieldErrors() throws Exception {
        String body = """
                {
                  "lastName": "",
                  "firstName": "",
                  "role": "STUDENT"
                }
                """;

        mockMvc.perform(post("/academic/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .header("X-User-Id", seedAdminId)
                        .header("X-User-Role", "ADMIN")
                        .header("X-Group-Id", "")
                        .header("X-Is-Headman", "false"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type", containsString("validation")))
                .andExpect(jsonPath("$.fieldErrors", notNullValue()))
                .andExpect(jsonPath("$.fieldErrors[?(@.field == 'lastName')]", hasSize(greaterThan(0))));
    }

    // =====================================================================
    // Test 3 — Unauthorized role gets 403 Forbidden
    // =====================================================================

    @Test
    @Order(3)
    void testStudentCannotCreateUser_returns403() throws Exception {
        String body = """
                {
                  "lastName": "Другов",
                  "firstName": "Другой",
                  "role": "STUDENT",
                  "groupId": %d
                }
                """.formatted(seedGroupId);

        mockMvc.perform(post("/academic/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .header("X-User-Id", seedStudentId)
                        .header("X-User-Role", "STUDENT")
                        .header("X-Group-Id", seedGroupId)
                        .header("X-Is-Headman", "false"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status", is(403)));
    }

    // =====================================================================
    // Test 4 — GSEM-02/03: Create semesters, activate second, first deactivated atomically
    // =====================================================================

    @Test
    @Order(4)
    void testAdminCreateAndActivateSemester() throws Exception {
        // First deactivate existing active semester to avoid constraint violation
        jdbcTemplate.update("UPDATE semesters SET is_active = false WHERE is_active = true");

        // Create semester 1
        String body1 = """
                {
                  "name": "TestSemester Alpha",
                  "dateFrom": "2027-02-01",
                  "dateTo": "2027-06-30"
                }
                """;

        MvcResult r1 = mockMvc.perform(post("/academic/semesters")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body1)
                        .header("X-User-Id", seedAdminId)
                        .header("X-User-Role", "ADMIN")
                        .header("X-Group-Id", "")
                        .header("X-Is-Headman", "false"))
                .andExpect(status().isCreated())
                .andReturn();

        Long sem1Id = objectMapper.readTree(r1.getResponse().getContentAsString()).get("id").asLong();

        // Create semester 2
        String body2 = """
                {
                  "name": "TestSemester Beta",
                  "dateFrom": "2027-09-01",
                  "dateTo": "2028-01-31"
                }
                """;

        MvcResult r2 = mockMvc.perform(post("/academic/semesters")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body2)
                        .header("X-User-Id", seedAdminId)
                        .header("X-User-Role", "ADMIN")
                        .header("X-Group-Id", "")
                        .header("X-Is-Headman", "false"))
                .andExpect(status().isCreated())
                .andReturn();

        Long sem2Id = objectMapper.readTree(r2.getResponse().getContentAsString()).get("id").asLong();

        // Activate semester 1
        mockMvc.perform(patch("/academic/semesters/" + sem1Id + "/activate")
                        .header("X-User-Id", seedAdminId)
                        .header("X-User-Role", "ADMIN")
                        .header("X-Group-Id", "")
                        .header("X-Is-Headman", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active", is(true)));

        // Activate semester 2 — should deactivate semester 1
        mockMvc.perform(patch("/academic/semesters/" + sem2Id + "/activate")
                        .header("X-User-Id", seedAdminId)
                        .header("X-User-Role", "ADMIN")
                        .header("X-Group-Id", "")
                        .header("X-Is-Headman", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active", is(true)));

        // Verify semester 1 is now inactive (atomic deactivation)
        mockMvc.perform(get("/academic/semesters/" + sem1Id)
                        .header("X-User-Id", seedAdminId)
                        .header("X-User-Role", "ADMIN")
                        .header("X-Group-Id", "")
                        .header("X-Is-Headman", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active", is(false)));
    }

    // =====================================================================
    // Test 5 — GSEM-04: Delete semester with wrong confirmation returns 400
    // =====================================================================

    @Test
    @Order(5)
    @Transactional
    void testSemesterDeleteWithWrongConfirmation_returns400() throws Exception {
        // Create a semester to try to delete
        jdbcTemplate.update("UPDATE semesters SET is_active = false WHERE is_active = true");

        String createBody = """
                {
                  "name": "Fall 2025 Test",
                  "dateFrom": "2025-09-01",
                  "dateTo": "2026-01-31"
                }
                """;

        MvcResult r = mockMvc.perform(post("/academic/semesters")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody)
                        .header("X-User-Id", seedAdminId)
                        .header("X-User-Role", "ADMIN")
                        .header("X-Group-Id", "")
                        .header("X-Is-Headman", "false"))
                .andExpect(status().isCreated())
                .andReturn();

        Long semId = objectMapper.readTree(r.getResponse().getContentAsString()).get("id").asLong();

        // Attempt delete with wrong confirmation
        String deleteBody = """
                { "confirmation": "wrong name" }
                """;

        mockMvc.perform(delete("/academic/semesters/" + semId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(deleteBody)
                        .header("X-User-Id", seedAdminId)
                        .header("X-User-Role", "ADMIN")
                        .header("X-Group-Id", "")
                        .header("X-Is-Headman", "false"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)));
    }

    // =====================================================================
    // Test 6 — USER-06: Student gets own profile via GET /me
    // =====================================================================

    @Test
    @Order(6)
    void testStudentGetMe_returnsOwnProfile() throws Exception {
        mockMvc.perform(get("/academic/users/me")
                        .header("X-User-Id", seedStudentId)
                        .header("X-User-Role", "STUDENT")
                        .header("X-Group-Id", seedGroupId)
                        .header("X-Is-Headman", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.login", is("student")))
                .andExpect(jsonPath("$.role", is("STUDENT")));
    }

    // =====================================================================
    // Test 7 — THRSH-04: Threshold resolution — most specific wins
    // =====================================================================

    @Test
    @Order(7)
    @Transactional
    void testThresholdResolution_mostSpecificWins() throws Exception {
        // Set global threshold as ADMIN
        String globalBody = """
                { "minPercentage": 50 }
                """;
        mockMvc.perform(put("/academic/thresholds/global")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(globalBody)
                        .header("X-User-Id", seedAdminId)
                        .header("X-User-Role", "ADMIN")
                        .header("X-Group-Id", "")
                        .header("X-Is-Headman", "false"))
                .andExpect(status().isOk());

        // Set group threshold as HEADMAN (seed student with headman flag)
        String groupBody = """
                { "minPercentage": 70 }
                """;
        mockMvc.perform(put("/academic/thresholds/group")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(groupBody)
                        .header("X-User-Id", seedStudentId)
                        .header("X-User-Role", "STUDENT")
                        .header("X-Group-Id", seedGroupId)
                        .header("X-Is-Headman", "true"))
                .andExpect(status().isOk());

        // Resolve for group — should return group level (70%)
        mockMvc.perform(get("/academic/thresholds/resolve")
                        .param("groupId", seedGroupId.toString())
                        .header("X-User-Id", seedStudentId)
                        .header("X-User-Role", "STUDENT")
                        .header("X-Group-Id", seedGroupId)
                        .header("X-Is-Headman", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.level", is("group")))
                .andExpect(jsonPath("$.minPercentage", is(70)));

        // Resolve without groupId — should return global level (50%)
        mockMvc.perform(get("/academic/thresholds/resolve")
                        .header("X-User-Id", seedAdminId)
                        .header("X-User-Role", "ADMIN")
                        .header("X-Group-Id", "")
                        .header("X-Is-Headman", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.level", is("global")))
                .andExpect(jsonPath("$.minPercentage", is(50)));
    }

    // =====================================================================
    // Test 8 — HATEOAS pagination: List users returns PagedModel with _links
    // =====================================================================

    @Test
    @Order(8)
    void testListUsers_returnsPaginatedHateoas() throws Exception {
        mockMvc.perform(get("/academic/users")
                        .param("page", "0")
                        .param("size", "2")
                        .header("X-User-Id", seedAdminId)
                        .header("X-User-Role", "ADMIN")
                        .header("X-Group-Id", "")
                        .header("X-Is-Headman", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$._embedded", notNullValue()))
                .andExpect(jsonPath("$._links.self.href", notNullValue()))
                .andExpect(jsonPath("$.page.totalElements", greaterThanOrEqualTo(3)));
    }

    // =====================================================================
    // Test 9 — USER-04: Revoke headman cascades to deactivate all assistants
    // =====================================================================

    @Test
    @Order(9)
    @Transactional
    void testHeadmanRevokeCascadesAssistants() throws Exception {
        // The seed 'student' user has is_headman = true (from V2 migration)
        Long headmanId = seedStudentId;

        // Create a second student to serve as assistant
        Long assistantId = jdbcTemplate.queryForObject(
                "INSERT INTO users (login, password_hash, last_name, first_name, role, status, is_headman, group_id) " +
                "VALUES ('assistant_test', 'hash', 'Assistant', 'User', 'student', 'active', false, ?) RETURNING id",
                Long.class, seedGroupId);

        // Assign the student as an assistant to the headman's group via native SQL
        jdbcTemplate.update(
                "INSERT INTO headman_assistants (group_id, student_id, permissions, assigned_by, is_active, assigned_at) " +
                "VALUES (?, ?, ARRAY['mark_attendance']::varchar(64)[], ?, true, NOW())",
                seedGroupId, assistantId, headmanId
        );

        // Verify assistant exists before revoke
        Integer countBefore = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM headman_assistants WHERE group_id = ? AND is_active = true",
                Integer.class, seedGroupId);
        assertThat(countBefore).isGreaterThan(0);

        // Revoke headman via PATCH
        String patchBody = """
                { "isHeadman": false }
                """;
        mockMvc.perform(patch("/academic/users/" + headmanId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(patchBody)
                        .header("X-User-Id", seedAdminId)
                        .header("X-User-Role", "ADMIN")
                        .header("X-Group-Id", "")
                        .header("X-Is-Headman", "false"))
                .andExpect(status().isOk());

        // Verify all assistants in that group are now deactivated
        Integer countAfter = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM headman_assistants WHERE group_id = ? AND is_active = true",
                Integer.class, seedGroupId);
        assertThat(countAfter).isEqualTo(0);
    }

    // =====================================================================
    // Test 10 — HW-03: Student can mark and unmark homework as completed
    // =====================================================================

    @Test
    @Order(10)
    void testHomeworkCompletionToggle() throws Exception {
        // Create a subject first
        Long subjectId = jdbcTemplate.queryForObject(
                "INSERT INTO subjects (name, type) VALUES ('Integration Test Subject', 'lecture') RETURNING id",
                Long.class);

        // Deactivate existing active semester to avoid constraint issues
        jdbcTemplate.update("UPDATE semesters SET is_active = false WHERE is_active = true");

        // Create a semester for homework
        Long semesterId = jdbcTemplate.queryForObject(
                "INSERT INTO semesters (name, date_from, date_to, is_active, created_at) " +
                "VALUES ('HW Test Semester', '2027-02-01', '2027-06-30', false, NOW()) RETURNING id",
                Long.class);

        // Create homework as headman via native SQL (avoid headman permission check)
        Long homeworkId = jdbcTemplate.queryForObject(
                "INSERT INTO homeworks (group_id, subject_id, semester_id, title, published_by, created_at, updated_at) " +
                "VALUES (?, ?, ?, 'Test HW', ?, NOW(), NOW()) RETURNING id",
                Long.class, seedGroupId, subjectId, semesterId, seedStudentId);

        // Mark homework as complete (student)
        mockMvc.perform(post("/academic/homeworks/" + homeworkId + "/complete")
                        .header("X-User-Id", seedStudentId)
                        .header("X-User-Role", "STUDENT")
                        .header("X-Group-Id", seedGroupId)
                        .header("X-Is-Headman", "false"))
                .andExpect(status().isOk());

        // Verify it's marked in DB
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM homework_completions WHERE homework_id = ? AND student_id = ?",
                Integer.class, homeworkId, seedStudentId);
        assertThat(count).isEqualTo(1);

        // Unmark homework
        mockMvc.perform(delete("/academic/homeworks/" + homeworkId + "/complete")
                        .header("X-User-Id", seedStudentId)
                        .header("X-User-Role", "STUDENT")
                        .header("X-Group-Id", seedGroupId)
                        .header("X-Is-Headman", "false"))
                .andExpect(status().isNoContent());

        // Verify it's unmarked
        Integer countAfter = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM homework_completions WHERE homework_id = ? AND student_id = ?",
                Integer.class, homeworkId, seedStudentId);
        assertThat(countAfter).isEqualTo(0);
    }

    // =====================================================================
    // Test 11 — DASH-01: Dashboard stats returns all required fields
    // =====================================================================

    @Test
    @Order(11)
    void testDashboardStats_returnsAllFields() throws Exception {
        mockMvc.perform(get("/academic/dashboard/stats")
                        .header("X-User-Id", seedAdminId)
                        .header("X-User-Role", "ADMIN")
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
        mockMvc.perform(get("/academic/dashboard/stats")
                        .header("X-User-Id", seedStudentId)
                        .header("X-User-Role", "STUDENT")
                        .header("X-Group-Id", seedGroupId)
                        .header("X-Is-Headman", "false"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status", is(403)));
    }
}
