package ru.rutcampustrack.academic.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;
import ru.rutcampustrack.academic.contract.dto.assistant.AssignAssistantRequest;
import ru.rutcampustrack.academic.contract.dto.assistant.UpdateAssistantPermissionsRequest;
import ru.rutcampustrack.academic.contract.enums.AssistantPermission;
import ru.rutcampustrack.academic.integration.AbstractAcademicIntegrationTest;

import java.time.LocalDate;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * M13 G9 (NEW-31) — IDOR-проверки academic-service.
 *
 * <p>Сетап: две группы (A, B), студент A в группе A, студент B + headman B
 * в группе B. Каждый тест: студент/headman A → endpoint, оперирующий ресурсом
 * группы B → ожидаем 403.
 *
 * <p>Покрытие (≥10 endpoint'ов согласно AC-17):
 * <ul>
 *   <li>{@code GET /academic/homeworks/{id}} — другой группы</li>
 *   <li>{@code GET /academic/homeworks?groupId=...} — чужой groupId</li>
 *   <li>{@code POST /academic/homeworks/{id}/complete} — чужой homework</li>
 *   <li>{@code DELETE /academic/homeworks/{id}/complete} — чужой homework</li>
 *   <li>{@code GET /academic/assistants?groupId=...} — чужая группа</li>
 *   <li>{@code POST /academic/assistants} — body.groupId чужой</li>
 *   <li>{@code PATCH /academic/assistants/{id}/permissions} — assistant другой группы</li>
 *   <li>{@code DELETE /academic/assistants/{id}} — assistant другой группы</li>
 *   <li>{@code GET /academic/assignments?groupId=...} — чужой groupId</li>
 *   <li>{@code DELETE /academic/assignments/{id}} — assignment другой группы</li>
 *   <li>{@code GET /academic/subjects/{id}} — subject другой группы</li>
 * </ul>
 */
@AutoConfigureMockMvc
@Transactional
class SecurityIdorIT extends AbstractAcademicIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper objectMapper;

    // Группа A — куда «прибывает» атакующий
    private Long groupAId;
    private Long studentAId;
    private Long headmanAId;

    // Группа B — её ресурсы пытается прочитать/изменить атакующий
    private Long groupBId;
    private Long studentBId;
    private Long headmanBId;

    // Ресурсы группы B
    private Long subjectBId;
    private Long semesterId;
    private Long homeworkBId;
    private Long assistantBId;
    private Long assignmentBId;
    private Long teacherId;

    @BeforeEach
    void seed() {
        // Группы
        groupAId = jdbc.queryForObject(
                "INSERT INTO groups (name, is_active, created_at) VALUES (?, true, NOW()) RETURNING id",
                Long.class, "IDOR-A-" + System.nanoTime());
        groupBId = jdbc.queryForObject(
                "INSERT INTO groups (name, is_active, created_at) VALUES (?, true, NOW()) RETURNING id",
                Long.class, "IDOR-B-" + System.nanoTime());

        // Студенты
        studentAId = createStudent("idor_student_a_" + System.nanoTime(), groupAId, false);
        headmanAId = createStudent("idor_headman_a_" + System.nanoTime(), groupAId, true);
        studentBId = createStudent("idor_student_b_" + System.nanoTime(), groupBId, false);
        headmanBId = createStudent("idor_headman_b_" + System.nanoTime(), groupBId, true);

        // Преподаватель — нужен для assignment
        teacherId = jdbc.queryForObject(
                "INSERT INTO users (login, password_hash, last_name, first_name, role, status, " +
                "is_headman, password_changed, created_at, updated_at, employee_number) " +
                "VALUES (?, '$2a$10$dummy', 'Тичеров', 'Тич', 'teacher', 'active', false, true, NOW(), NOW(), ?) RETURNING id",
                Long.class, "idor_teacher_" + System.nanoTime(), "EMP-" + System.nanoTime());

        // Subject группы B
        subjectBId = jdbc.queryForObject(
                "INSERT INTO subjects (name, type, group_id) VALUES (?, 'lecture', ?) RETURNING id",
                Long.class, "IDOR Subject B " + System.nanoTime(), groupBId);

        // Активный семестр (деактивируем существующие чтобы exclusion constraint не упал)
        jdbc.update("UPDATE semesters SET is_active = false WHERE is_active = true");
        semesterId = jdbc.queryForObject(
                "INSERT INTO semesters (name, date_from, date_to, is_active, created_at) " +
                "VALUES (?, '2041-09-01', '2041-12-31', true, NOW()) RETURNING id",
                Long.class, "IDOR Sem " + System.nanoTime());

        // Homework группы B (опубликованный headman'ом B)
        homeworkBId = jdbc.queryForObject(
                "INSERT INTO homeworks (group_id, subject_id, semester_id, title, description, " +
                "published_by, lesson_date, lesson_number, created_at, updated_at) " +
                "VALUES (?, ?, ?, 'IDOR HW B', 'desc', ?, '2041-10-15', 1, NOW(), NOW()) RETURNING id",
                Long.class, groupBId, subjectBId, semesterId, headmanBId);

        // Assistant группы B
        assistantBId = jdbc.queryForObject(
                "INSERT INTO headman_assistants (group_id, student_id, permissions, assigned_by, " +
                "is_active, assigned_at) " +
                "VALUES (?, ?, ARRAY['manage_homework']::VARCHAR[], ?, true, NOW()) RETURNING id",
                Long.class, groupBId, studentBId, headmanBId);

        // Assignment группы B
        assignmentBId = jdbc.queryForObject(
                "INSERT INTO teacher_subject_groups (teacher_id, subject_id, group_id, semester_id) " +
                "VALUES (?, ?, ?, ?) RETURNING id",
                Long.class, teacherId, subjectBId, groupBId, semesterId);
    }

    private Long createStudent(String login, Long groupId, boolean headman) {
        return jdbc.queryForObject(
                "INSERT INTO users (login, password_hash, last_name, first_name, role, status, " +
                "group_id, is_headman, password_changed, created_at, updated_at) " +
                "VALUES (?, '$2a$10$dummy', 'Студентов', 'Студ', 'student', 'active', ?, ?, true, NOW(), NOW()) RETURNING id",
                Long.class, login, groupId, headman);
    }

    /** Заголовки атакующего STUDENT'а из группы A (без headman). */
    private MockHttpServletRequestBuilder asStudentA(MockHttpServletRequestBuilder b) {
        return b.header("X-User-Id", studentAId)
                .header("X-User-Role", "STUDENT")
                .header("X-Group-Id", groupAId)
                .header("X-Is-Headman", "false");
    }

    /** Заголовки атакующего HEADMAN'а из группы A. */
    private MockHttpServletRequestBuilder asHeadmanA(MockHttpServletRequestBuilder b) {
        return b.header("X-User-Id", headmanAId)
                .header("X-User-Role", "STUDENT")
                .header("X-Group-Id", groupAId)
                .header("X-Is-Headman", "true");
    }

    // ============================================================
    // Homework IDOR
    // ============================================================

    @Test
    void getHomework_otherGroup_returns403() throws Exception {
        mockMvc.perform(asStudentA(get("/academic/homeworks/{id}", homeworkBId)))
                .andExpect(status().isForbidden());
    }

    @Test
    void listHomeworks_foreignGroupId_returns403() throws Exception {
        mockMvc.perform(asStudentA(
                        get("/academic/homeworks")
                                .param("groupId", groupBId.toString())
                                .param("semesterId", semesterId.toString())))
                .andExpect(status().isForbidden());
    }

    @Test
    void markComplete_foreignHomework_returns403() throws Exception {
        mockMvc.perform(asStudentA(post("/academic/homeworks/{id}/complete", homeworkBId)))
                .andExpect(status().isForbidden());
    }

    @Test
    void unmarkComplete_foreignHomework_returns403() throws Exception {
        mockMvc.perform(asStudentA(delete("/academic/homeworks/{id}/complete", homeworkBId)))
                .andExpect(status().isForbidden());
    }

    // ============================================================
    // Assistant IDOR (headman A → группа B)
    // ============================================================

    @Test
    void listAssistants_foreignGroupId_returns403() throws Exception {
        mockMvc.perform(asHeadmanA(
                        get("/academic/assistants").param("groupId", groupBId.toString())))
                .andExpect(status().isForbidden());
    }

    @Test
    void assignAssistant_foreignGroupId_returns403() throws Exception {
        // Сигнатура: (studentId, groupId, permissions)
        AssignAssistantRequest req = new AssignAssistantRequest(
                studentBId, groupBId, List.of(AssistantPermission.MANAGE_HOMEWORK));
        mockMvc.perform(asHeadmanA(post("/academic/assistants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req))))
                .andExpect(status().isForbidden());
    }

    @Test
    void updateAssistantPermissions_foreignAssistant_returns403() throws Exception {
        UpdateAssistantPermissionsRequest req = new UpdateAssistantPermissionsRequest(
                List.of(AssistantPermission.MANAGE_HOMEWORK));
        mockMvc.perform(asHeadmanA(patch("/academic/assistants/{id}/permissions", assistantBId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req))))
                .andExpect(status().isForbidden());
    }

    @Test
    void revokeAssistant_foreignAssistant_returns403() throws Exception {
        mockMvc.perform(asHeadmanA(delete("/academic/assistants/{id}", assistantBId)))
                .andExpect(status().isForbidden());
    }

    // ============================================================
    // Assignment IDOR
    // ============================================================

    @Test
    void listAssignments_foreignGroupId_returns403() throws Exception {
        mockMvc.perform(asStudentA(
                        get("/academic/assignments")
                                .param("groupId", groupBId.toString())
                                .param("semesterId", semesterId.toString())))
                .andExpect(status().isForbidden());
    }

    @Test
    void removeAssignment_foreignAssignment_returns403() throws Exception {
        mockMvc.perform(asHeadmanA(delete("/academic/assignments/{id}", assignmentBId)))
                .andExpect(status().isForbidden());
    }

    // ============================================================
    // Subject IDOR
    // ============================================================

    @Test
    void getSubject_foreignGroup_returns403() throws Exception {
        mockMvc.perform(asStudentA(get("/academic/subjects/{id}", subjectBId)))
                .andExpect(status().isForbidden());
    }

    // ============================================================
    // Sanity: same-group access работает (не 403)
    // ============================================================

    @Test
    void getHomework_sameGroup_doesNotReturn403() throws Exception {
        // Headman B читает свой homework — 200
        mockMvc.perform(get("/academic/homeworks/{id}", homeworkBId)
                        .header("X-User-Id", headmanBId)
                        .header("X-User-Role", "STUDENT")
                        .header("X-Group-Id", groupBId)
                        .header("X-Is-Headman", "true"))
                .andExpect(status().isOk());
    }

    @Test
    void listHomeworks_sameGroup_doesNotReturn403() throws Exception {
        mockMvc.perform(get("/academic/homeworks")
                        .param("groupId", groupBId.toString())
                        .param("semesterId", semesterId.toString())
                        .header("X-User-Id", studentBId)
                        .header("X-User-Role", "STUDENT")
                        .header("X-Group-Id", groupBId)
                        .header("X-Is-Headman", "false"))
                .andExpect(status().isOk());
    }
}
