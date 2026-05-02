package ru.rutcampustrack.academic.subject;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import ru.rutcampustrack.academic.integration.AbstractAcademicIntegrationTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Phase 60-01 / Task 2: end-to-end integration tests for {@link SubjectService}.
 *
 * <ul>
 *   <li>{@link #createSubject_withTwoTeachers_atomicInsert()} — 1 insert subjects + N TSG</li>
 *   <li>{@link #createSubject_rollbackOnTeacherSaveFail()} — rollback при сбое TSG</li>
 *   <li>{@link #addTeacher_and_removeTeacher()} — endpoints управления</li>
 *   <li>{@link #listSubjects_filteredByGroup()} — HEADMAN видит только свою группу</li>
 * </ul>
 */
@AutoConfigureMockMvc
class SubjectServiceIT extends AbstractAcademicIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long seedGroupId;
    private Long seedStudentId;
    private Long seedTeacherId;
    private Long seedAdminId;
    private Long otherGroupId;
    private Long otherTeacherId;

    @BeforeEach
    void loadSeedIds() {
        seedGroupId = jdbcTemplate.queryForObject(
                "SELECT id FROM groups WHERE name = 'ИВТ-211'", Long.class);
        seedStudentId = jdbcTemplate.queryForObject(
                "SELECT id FROM users WHERE login = 'student'", Long.class);
        seedTeacherId = jdbcTemplate.queryForObject(
                "SELECT id FROM users WHERE login = 'teacher'", Long.class);
        seedAdminId = jdbcTemplate.queryForObject(
                "SELECT id FROM users WHERE login = 'admin'", Long.class);

        // V2 seed содержит активный 'Spring 2026' семестр, но предыдущие IT могут оставить
        // его деактивированным (RestApi testHomeworkCompletionToggle / EventIT activateSemester).
        // Восстанавливаем активный статус, чтобы SubjectService.requireActiveSemesterId() работал.
        jdbcTemplate.update(
                "UPDATE semesters SET is_active = true WHERE name = 'Spring 2026'");
        // Деактивируем любые другие, чтобы не нарушить ожидание "ровно один активный".
        jdbcTemplate.update(
                "UPDATE semesters SET is_active = false WHERE name <> 'Spring 2026'");
    }

    @AfterEach
    void cleanup() {
        // Тесты, работающие вне @Transactional, должны чистить созданные записи вручную,
        // чтобы не просачивались в другие тесты. FK ON DELETE CASCADE на teacher_subject_groups.
        jdbcTemplate.update("DELETE FROM teacher_subject_groups");
        jdbcTemplate.update("DELETE FROM subjects");
        jdbcTemplate.update("DELETE FROM users WHERE login = 'teacher02'");
        jdbcTemplate.update("DELETE FROM groups WHERE name = 'УИТ-311'");
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private void seedOtherGroupAndTeacher() {
        jdbcTemplate.update("INSERT INTO groups (name, is_active) VALUES ('УИТ-311', true)");
        otherGroupId = jdbcTemplate.queryForObject(
                "SELECT id FROM groups WHERE name = 'УИТ-311'", Long.class);
        jdbcTemplate.update(
                "INSERT INTO users (login, password_hash, last_name, first_name, middle_name, role, status, is_headman, employee_number) "
              + "VALUES ('teacher02', '$2a$10$A9r8miSBxjlpjxFB/z0jIerCCSOrLQP6N.sXrjBAw9l7iy4vmRFpi', "
              + "'Иванов', 'Иван', 'Иванович', 'teacher', 'active', false, 'T00002')");
        otherTeacherId = jdbcTemplate.queryForObject(
                "SELECT id FROM users WHERE login = 'teacher02'", Long.class);
    }

    private int countSubjectsByName(String name) {
        Integer c = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM subjects WHERE name = ?", Integer.class, name);
        return c == null ? 0 : c;
    }

    private int countTsgBySubjectId(Long subjectId) {
        Integer c = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM teacher_subject_groups WHERE subject_id = ?", Integer.class, subjectId);
        return c == null ? 0 : c;
    }

    // =========================================================================
    // Test 1 — атомарное создание: subjects + N TSG
    // =========================================================================

    @Test
    void createSubject_withTwoTeachers_atomicInsert() throws Exception {
        seedOtherGroupAndTeacher();

        String body = """
                {
                  "name": "Дискретная математика",
                  "type": "LECTURE",
                  "teacherIds": [%d, %d]
                }
                """.formatted(seedTeacherId, otherTeacherId);

        MvcResult result = mockMvc.perform(post("/academic/subjects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .header("X-User-Id", seedStudentId)
                        .header("X-User-Role", "STUDENT")
                        .header("X-Group-Id", seedGroupId.toString())
                        .header("X-Is-Headman", "true"))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        Long subjectId = json.get("id").asLong();
        assertThat(json.get("groupId").asLong()).isEqualTo(seedGroupId);
        assertThat(json.get("teacherIds")).hasSize(2);

        // В БД: 1 запись в subjects + 2 в teacher_subject_groups
        assertThat(countSubjectsByName("Дискретная математика")).isEqualTo(1);
        assertThat(countTsgBySubjectId(subjectId)).isEqualTo(2);
    }

    // =========================================================================
    // Test 2 — rollback при сбое вставки TSG (несуществующий teacherId → FK violation)
    // =========================================================================

    @Test
    void createSubject_rollbackOnTeacherSaveFail() throws Exception {
        String body = """
                {
                  "name": "Rollback Test",
                  "type": "PRACTICE",
                  "teacherIds": [%d, 999999999]
                }
                """.formatted(seedTeacherId);

        mockMvc.perform(post("/academic/subjects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .header("X-User-Id", seedStudentId)
                        .header("X-User-Role", "STUDENT")
                        .header("X-Group-Id", seedGroupId.toString())
                        .header("X-Is-Headman", "true"))
                .andExpect(status().is5xxServerError());

        // Откат: записи subjects по имени нет
        assertThat(countSubjectsByName("Rollback Test")).isEqualTo(0);
    }

    // =========================================================================
    // Test 3 — addTeacher / removeTeacher endpoints + 409 на дубле
    // =========================================================================

    @Test
    void addTeacher_and_removeTeacher() throws Exception {
        // 1. создать предмет без преподавателей
        String createBody = """
                {
                  "name": "Физика",
                  "type": "LECTURE",
                  "teacherIds": []
                }
                """;

        MvcResult res = mockMvc.perform(post("/academic/subjects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody)
                        .header("X-User-Id", seedStudentId)
                        .header("X-User-Role", "STUDENT")
                        .header("X-Group-Id", seedGroupId.toString())
                        .header("X-Is-Headman", "true"))
                .andExpect(status().isCreated())
                .andReturn();

        Long subjectId = objectMapper.readTree(res.getResponse().getContentAsString())
                .get("id").asLong();
        assertThat(countTsgBySubjectId(subjectId)).isEqualTo(0);

        // 2. POST teacher → 201
        mockMvc.perform(post("/academic/subjects/{id}/teachers/{teacherId}", subjectId, seedTeacherId)
                        .header("X-User-Id", seedStudentId)
                        .header("X-User-Role", "STUDENT")
                        .header("X-Group-Id", seedGroupId.toString())
                        .header("X-Is-Headman", "true"))
                .andExpect(status().isCreated());

        assertThat(countTsgBySubjectId(subjectId)).isEqualTo(1);

        // 3. повторный POST → 409
        mockMvc.perform(post("/academic/subjects/{id}/teachers/{teacherId}", subjectId, seedTeacherId)
                        .header("X-User-Id", seedStudentId)
                        .header("X-User-Role", "STUDENT")
                        .header("X-Group-Id", seedGroupId.toString())
                        .header("X-Is-Headman", "true"))
                .andExpect(status().isConflict());

        // 4. DELETE teacher → 204
        mockMvc.perform(delete("/academic/subjects/{id}/teachers/{teacherId}", subjectId, seedTeacherId)
                        .header("X-User-Id", seedStudentId)
                        .header("X-User-Role", "STUDENT")
                        .header("X-Group-Id", seedGroupId.toString())
                        .header("X-Is-Headman", "true"))
                .andExpect(status().isNoContent());

        assertThat(countTsgBySubjectId(subjectId)).isEqualTo(0);
    }

    // =========================================================================
    // Test 4 — listSubjects фильтруется по группе (HEADMAN)
    // =========================================================================

    @Test
    void listSubjects_filteredByGroup() throws Exception {
        seedOtherGroupAndTeacher();

        // seed предметы в двух разных группах напрямую через JDBC
        jdbcTemplate.update(
                "INSERT INTO subjects (name, type, group_id) VALUES (?, 'lecture', ?)",
                "Own Group Subject", seedGroupId);
        jdbcTemplate.update(
                "INSERT INTO subjects (name, type, group_id) VALUES (?, 'lecture', ?)",
                "Other Group Subject", otherGroupId);

        MvcResult res = mockMvc.perform(get("/academic/subjects")
                        .header("X-User-Id", seedStudentId)
                        .header("X-User-Role", "STUDENT")
                        .header("X-Group-Id", seedGroupId.toString())
                        .header("X-Is-Headman", "true"))
                .andExpect(status().isOk())
                .andReturn();

        String body = res.getResponse().getContentAsString();
        assertThat(body).contains("Own Group Subject");
        assertThat(body).doesNotContain("Other Group Subject");

        // ADMIN видит оба
        MvcResult adminRes = mockMvc.perform(get("/academic/subjects")
                        .header("X-User-Id", seedAdminId)
                        .header("X-User-Role", "ADMIN")
                        .header("X-Group-Id", "")
                        .header("X-Is-Headman", "false"))
                .andExpect(status().isOk())
                .andReturn();
        String adminBody = adminRes.getResponse().getContentAsString();
        assertThat(adminBody).contains("Own Group Subject");
        assertThat(adminBody).contains("Other Group Subject");
    }

    @Test
    void listSubjects_forTeacher_returnsAssignedSubjects() throws Exception {
        seedOtherGroupAndTeacher();

        Long semesterId = jdbcTemplate.queryForObject(
                "SELECT id FROM semesters WHERE name = 'Spring 2026'", Long.class);
        Long assignedSubjectId = jdbcTemplate.queryForObject(
                "INSERT INTO subjects (name, type, group_id) VALUES (?, 'lecture', ?) RETURNING id",
                Long.class, "Assigned Teacher Subject", seedGroupId);
        jdbcTemplate.queryForObject(
                "INSERT INTO subjects (name, type, group_id) VALUES (?, 'lecture', ?) RETURNING id",
                Long.class, "Unassigned Teacher Subject", seedGroupId);
        Long otherTeacherSubjectId = jdbcTemplate.queryForObject(
                "INSERT INTO subjects (name, type, group_id) VALUES (?, 'lecture', ?) RETURNING id",
                Long.class, "Other Teacher Subject", otherGroupId);

        jdbcTemplate.update(
                "INSERT INTO teacher_subject_groups (teacher_id, subject_id, group_id, semester_id) VALUES (?, ?, ?, ?)",
                seedTeacherId, assignedSubjectId, seedGroupId, semesterId);
        jdbcTemplate.update(
                "INSERT INTO teacher_subject_groups (teacher_id, subject_id, group_id, semester_id) VALUES (?, ?, ?, ?)",
                otherTeacherId, otherTeacherSubjectId, otherGroupId, semesterId);

        MvcResult res = mockMvc.perform(get("/academic/subjects")
                        .header("X-User-Id", seedTeacherId)
                        .header("X-User-Role", "TEACHER")
                        .header("X-Group-Id", "")
                        .header("X-Is-Headman", "false"))
                .andExpect(status().isOk())
                .andReturn();

        String body = res.getResponse().getContentAsString();
        assertThat(body).contains("Assigned Teacher Subject");
        assertThat(body).doesNotContain("Unassigned Teacher Subject");
        assertThat(body).doesNotContain("Other Teacher Subject");
    }
}
