package ru.rutcampustrack.academic.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import ru.rutcampustrack.academic.contract.dto.assistant.AssignAssistantRequest;
import ru.rutcampustrack.academic.contract.dto.assistant.UpdateAssistantPermissionsRequest;
import ru.rutcampustrack.academic.contract.dto.group.AssignHeadmanRequest;
import ru.rutcampustrack.academic.contract.dto.homework.CreateHomeworkRequest;
import ru.rutcampustrack.academic.contract.dto.user.CreateUserRequest;
import ru.rutcampustrack.academic.contract.dto.user.RoleGrantUpdateRequest;
import ru.rutcampustrack.academic.contract.dto.user.TransferStudentRequest;
import ru.rutcampustrack.academic.contract.enums.AssistantPermission;
import ru.rutcampustrack.academic.contract.enums.RoleGrantStatus;
import ru.rutcampustrack.academic.contract.enums.UserRole;
import ru.rutcampustrack.academic.integration.AbstractAcademicIntegrationTest;
import ru.rutcampustrack.academic.integration.AcademicIdentityIntegrationFixture.SelectedActor;
import ru.rutcampustrack.auth.session.model.AuthRole;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * M13 G9 (NEW-31) — IDOR-проверки academic-service.
 *
 * <p>Сетап: две группы (A, B), студент A в группе A, студент B + headman B в группе B. Каждый тест:
 * студент/headman A → endpoint, оперирующий ресурсом группы B → ожидаем 403.
 *
 * <p>Покрытие (≥10 endpoint'ов согласно AC-17):
 *
 * <ul>
 *   <li>{@code GET /academic/homeworks/{id}} — другой группы
 *   <li>{@code GET /academic/homeworks?groupId=...} — чужой groupId
 *   <li>{@code POST /academic/homeworks/{id}/complete} — чужой homework
 *   <li>{@code DELETE /academic/homeworks/{id}/complete} — чужой homework
 *   <li>{@code GET /academic/assistants?groupId=...} — чужая группа
 *   <li>{@code POST /academic/assistants} — body.groupId чужой
 *   <li>{@code PATCH /academic/assistants/{id}/permissions} — assistant другой группы
 *   <li>{@code DELETE /academic/assistants/{id}} — assistant другой группы
 *   <li>{@code GET /academic/assignments?groupId=...} — чужой groupId
 *   <li>{@code DELETE /academic/assignments/{id}} — assignment другой группы
 *   <li>{@code GET /academic/subjects/{id}} — subject другой группы
 * </ul>
 */
@AutoConfigureMockMvc
class SecurityIdorIT extends AbstractAcademicIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper objectMapper;

    @Autowired
    private org.springframework.transaction.PlatformTransactionManager transactionManager;

    // Группа A — куда «прибывает» атакующий
    private Long groupAId;
    private Long studentAId;
    private Long headmanAId;

    // Группа B — её ресурсы пытается прочитать/изменить атакующий
    private Long groupBId;
    private Long studentBId;
    private Long headmanBId;
    private Long replacementHeadmanBId;

    // Ресурсы группы B
    private Long subjectBId;
    private Long semesterId;
    private Long homeworkBId;
    private Long assistantBId;
    private Long assignmentBId;
    private Long teacherId;
    private SelectedActor admin;
    private SelectedActor studentA;
    private SelectedActor headmanA;
    private SelectedActor studentB;
    private SelectedActor headmanB;

    @BeforeEach
    void seed() throws Exception {
        var canonical = identityFixture.canonical();
        admin = identityFixture.select(canonical.adminId(), AuthRole.ADMIN);
        groupAId = canonical.groupId();
        headmanAId = canonical.studentId();
        teacherId = canonical.teacherId();
        groupBId =
                jdbc.queryForObject(
                        "INSERT INTO"
                                + " groups(name,is_active,alphabetic_code,numeric_code,current_course,training_duration_years,duration_status)"
                                + " VALUES('ИДОР-212',true,'ИДОР','212',2,4,'KNOWN') RETURNING id",
                        Long.class);
        jdbc.update(
                "INSERT INTO"
                        + " group_history_coverage(group_id,coverage_from,writer_version,created_at) "
                        + "VALUES(?,'2026-02-01',?,NOW())",
                groupBId,
                ru.rutcampustrack.academic.history.HistoricalMembershipService.WRITER_VERSION);

        // Real identity creation and Auth session selection cannot run inside an Academic TX.
        studentAId = createStudent("СтудентА", groupAId, 610000000101L);
        studentBId = createStudent("СтудентБ", groupBId, 610000000102L);
        headmanBId = createStudent("СтаростаБ", groupBId, 610000000103L);
        replacementHeadmanBId = createStudent("НовыйСтаростаБ", groupBId, 610000000104L);
        assignHeadman(headmanBId, null);
        identityFixture.catchUpCatalog();
        studentA = identityFixture.select(studentAId, AuthRole.STUDENT);
        headmanA = identityFixture.select(headmanAId, AuthRole.HEADMAN);
        studentB = identityFixture.select(studentBId, AuthRole.STUDENT);
        headmanB = identityFixture.select(headmanBId, AuthRole.HEADMAN);

        // Durable assignment closure reads committed Academic resources in a separate TX.
        new org.springframework.transaction.support.TransactionTemplate(transactionManager)
                .executeWithoutResult(
                        status -> {
                            subjectBId =
                                    jdbc.queryForObject(
                                            "INSERT INTO subjects (name, type, group_id) VALUES (?,"
                                                    + " 'lecture', ?) RETURNING id",
                                            Long.class,
                                            "IDOR Subject B " + System.nanoTime(),
                                            groupBId);
                            jdbc.update(
                                    "INSERT INTO subject_lesson_types(subject_id,lesson_type)"
                                            + " VALUES (?,'lecture')",
                                    subjectBId);
                            jdbc.update(
                                    "UPDATE semesters SET is_active = false WHERE is_active ="
                                            + " true");
                            semesterId =
                                    jdbc.queryForObject(
                                            "INSERT INTO semesters (name, date_from, date_to,"
                                                    + " is_active, created_at) VALUES (?, '2041-09-01',"
                                                    + " '2041-12-31', true, NOW()) RETURNING id",
                                            Long.class,
                                            "IDOR Sem " + System.nanoTime());
                            homeworkBId =
                                    jdbc.queryForObject(
                                            "INSERT INTO homeworks (group_id, subject_id,"
                                                    + " semester_id, title, description, published_by,"
                                                    + " lesson_date, lesson_number, binding_id,"
                                                    + " actor_id, request_key, payload_hash,"
                                                    + " publication_state, created_at, updated_at)"
                                                    + " VALUES (?, ?, ?, 'IDOR HW B', 'desc', ?,"
                                                    + " '2041-10-15', 1, ?, ?, ?, decode(repeat('ab',"
                                                    + " 32), 'hex'), 'ACTIVE', NOW(), NOW()) RETURNING"
                                                    + " id",
                                            Long.class,
                                            groupBId,
                                            subjectBId,
                                            semesterId,
                                            headmanBId,
                                            Math.abs(System.nanoTime()),
                                            headmanBId,
                                            UUID.randomUUID());
                            assistantBId =
                                    jdbc.queryForObject(
                                            "INSERT INTO headman_assistants (group_id, student_id,"
                                                    + " permissions, assigned_by, is_active,"
                                                    + " assigned_at) VALUES (?, ?,"
                                                    + " ARRAY['manage_homework']::VARCHAR[], ?, true,"
                                                    + " NOW()) RETURNING id",
                                            Long.class,
                                            groupBId,
                                            studentBId,
                                            headmanBId);
                            assignmentBId =
                                    jdbc.queryForObject(
                                            "INSERT INTO assignments (teacher_id, subject_id,"
                                                    + " group_id, semester_id, lesson_type, valid_from)"
                                                    + " VALUES (?, ?, ?, ?, 'lecture', '2041-09-01')"
                                                    + " RETURNING id",
                                            Long.class,
                                            teacherId,
                                            subjectBId,
                                            groupBId,
                                            semesterId);
                        });
    }

    private Long createStudent(String name, Long groupId, long telegramId) {
        return identityFixture
                .seedUser(
                        new CreateUserRequest(
                                "ИДОР",
                                name,
                                "Проверка",
                                UserRole.STUDENT,
                                groupId,
                                null,
                                telegramId),
                        admin)
                .userId();
    }

    private void assignHeadman(long candidateId, Long expectedHeadmanId) throws Exception {
        mockMvc.perform(
                        put("/academic/groups/{id}/headman", groupBId)
                                .with(identityFixture.signed(admin, UUID.randomUUID()))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        objectMapper.writeValueAsString(
                                                new AssignHeadmanRequest(
                                                        candidateId, expectedHeadmanId))))
                .andExpect(status().isOk());
    }

    private MockHttpServletRequestBuilder asStudentA(MockHttpServletRequestBuilder b) {
        return b.with(identityFixture.signed(studentA));
    }

    private MockHttpServletRequestBuilder asHeadmanA(MockHttpServletRequestBuilder b) {
        return b.with(identityFixture.signed(headmanA));
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
        mockMvc.perform(
                        asStudentA(
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
        mockMvc.perform(
                        asHeadmanA(
                                get("/academic/assistants").param("groupId", groupBId.toString())))
                .andExpect(status().isForbidden());
    }

    @Test
    void assignAssistant_foreignGroupId_returns403() throws Exception {
        // Сигнатура: (studentId, groupId, permissions)
        AssignAssistantRequest req =
                new AssignAssistantRequest(
                        studentBId, groupBId, List.of(AssistantPermission.MANAGE_HOMEWORK));
        mockMvc.perform(
                        asHeadmanA(
                                post("/academic/assistants")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(objectMapper.writeValueAsString(req))))
                .andExpect(status().isForbidden());
    }

    @Test
    void updateAssistantPermissions_foreignAssistant_returns403() throws Exception {
        UpdateAssistantPermissionsRequest req =
                new UpdateAssistantPermissionsRequest(List.of(AssistantPermission.MANAGE_HOMEWORK));
        mockMvc.perform(
                        asHeadmanA(
                                patch("/academic/assistants/{id}/permissions", assistantBId)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(objectMapper.writeValueAsString(req))))
                .andExpect(status().isForbidden());
    }

    @Test
    void revokeAssistant_foreignAssistant_returns403() throws Exception {
        mockMvc.perform(asHeadmanA(delete("/academic/assistants/{id}", assistantBId)))
                .andExpect(status().isForbidden());
    }

    @Test
    void assignRevokeAssign_preservesHistoryAndCannotUpdateRevokedRow() throws Exception {
        AssignAssistantRequest request =
                new AssignAssistantRequest(
                        studentAId, groupAId, List.of(AssistantPermission.MANAGE_HOMEWORK));
        String requestJson = objectMapper.writeValueAsString(request);

        mockMvc.perform(
                        asHeadmanA(
                                post("/academic/assistants")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(requestJson)))
                .andExpect(status().isCreated());
        Long firstAssignmentId =
                jdbc.queryForObject(
                        "SELECT id FROM headman_assistants WHERE group_id = ? AND student_id = ? AND is_active = true",
                        Long.class,
                        groupAId,
                        studentAId);

        mockMvc.perform(asHeadmanA(delete("/academic/assistants/{id}", firstAssignmentId)))
                .andExpect(status().isNoContent());

        UpdateAssistantPermissionsRequest update =
                new UpdateAssistantPermissionsRequest(List.of(AssistantPermission.MARK_ATTENDANCE));
        mockMvc.perform(
                        asHeadmanA(
                                patch("/academic/assistants/{id}/permissions", firstAssignmentId)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(objectMapper.writeValueAsString(update))))
                .andExpect(status().isConflict());
        assertThat(
                        jdbc.queryForObject(
                                "SELECT is_active FROM headman_assistants WHERE id = ?",
                                Boolean.class,
                                firstAssignmentId))
                .isFalse();

        mockMvc.perform(
                        asHeadmanA(
                                post("/academic/assistants")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(requestJson)))
                .andExpect(status().isCreated());

        assertThat(
                        jdbc.queryForObject(
                                "SELECT COUNT(*) FROM headman_assistants WHERE group_id = ? AND student_id = ?",
                                Integer.class,
                                groupAId,
                                studentAId))
                .isEqualTo(2);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT COUNT(*) FROM headman_assistants WHERE group_id = ? AND student_id = ? AND is_active = true",
                                Integer.class,
                                groupAId,
                                studentAId))
                .isEqualTo(1);
    }

    @Test
    void staleHeadmanAndStudentGrantCannotCreateUpdateOrDeleteHomework() throws Exception {
        CreateHomeworkRequest create =
                new CreateHomeworkRequest(
                        "stale grant",
                        "must be denied",
                        null,
                        subjectBId,
                        groupBId,
                        semesterId,
                        LocalDate.of(2041, 10, 16),
                        1,
                        UUID.randomUUID());
        String createJson = objectMapper.writeValueAsString(create);

        // Capture the genuine session before canonical replacement suspends its HEADMAN grant.
        SelectedActor staleHeadman = headmanB;
        assignHeadman(replacementHeadmanBId, headmanBId);
        assertHomeworkMutationsDenied(staleHeadman, createJson, status().isUnauthorized());

        // Headman replacement revokes assistants. Restore a real active assistant before transfer.
        var replacement = identityFixture.select(replacementHeadmanBId, AuthRole.HEADMAN);
        mockMvc.perform(
                        post("/academic/assistants")
                                .with(identityFixture.signed(replacement))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        objectMapper.writeValueAsString(
                                                new AssignAssistantRequest(
                                                        studentBId,
                                                        groupBId,
                                                        List.of(
                                                                AssistantPermission
                                                                        .MANAGE_HOMEWORK)))))
                .andExpect(status().isCreated());
        assertThat(
                        jdbc.queryForObject(
                                "SELECT COUNT(*) FROM headman_assistants WHERE group_id=? AND"
                                        + " student_id=? AND is_active=true",
                                Integer.class,
                                groupBId,
                                studentBId))
                .isEqualTo(1);
        SelectedActor staleStudent = identityFixture.select(studentBId, AuthRole.STUDENT);
        mockMvc.perform(
                        post("/academic/users/{id}/transfer", studentBId)
                                .with(identityFixture.signed(admin, UUID.randomUUID()))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        objectMapper.writeValueAsString(
                                                new TransferStudentRequest(
                                                        groupAId, "IDOR stale membership"))))
                .andExpect(status().isOk());

        assertThat(
                        jdbc.queryForObject(
                                "SELECT COUNT(*) FROM headman_assistants WHERE group_id=? AND"
                                        + " student_id=? AND is_active=true",
                                Integer.class,
                                groupBId,
                                studentBId))
                .isZero();

        // Invalidated sessions fail live admission with 401; valid foreign-scope actors get 403.
        assertHomeworkMutationsDenied(staleStudent, createJson, status().isUnauthorized());
        SelectedActor movedStudent = identityFixture.select(studentBId, AuthRole.STUDENT);
        assertThat(movedStudent.claims().groupId()).isEqualTo(groupAId);
        assertHomeworkMutationsDenied(movedStudent, createJson, status().isForbidden());

        mockMvc.perform(
                        put("/academic/users/{id}/roles/STUDENT", studentBId)
                                .with(identityFixture.signed(admin, UUID.randomUUID()))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        objectMapper.writeValueAsString(
                                                new RoleGrantUpdateRequest(
                                                        RoleGrantStatus.SUSPENDED,
                                                        null,
                                                        null,
                                                        null))))
                .andExpect(status().isOk());
        assertHomeworkMutationsDenied(movedStudent, createJson, status().isUnauthorized());
    }

    private void assertHomeworkMutationsDenied(
            SelectedActor actor,
            String createJson,
            org.springframework.test.web.servlet.ResultMatcher denied)
            throws Exception {
        var before =
                jdbc.queryForMap(
                        "SELECT title,description,publication_state FROM homeworks WHERE id=?",
                        homeworkBId);
        int countBefore =
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM homeworks WHERE group_id=?", Integer.class, groupBId);
        mockMvc.perform(
                        post("/academic/homeworks")
                                .with(identityFixture.signed(actor))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(createJson))
                .andExpect(denied);
        mockMvc.perform(
                        put("/academic/homeworks/{id}", homeworkBId)
                                .with(identityFixture.signed(actor))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"title\":\"stale\",\"description\":\"denied\",\"expectedRevision\":1,"
                                                + "\"requestKey\":\""
                                                + UUID.randomUUID()
                                                + "\"}"))
                .andExpect(denied);
        mockMvc.perform(
                        delete("/academic/homeworks/{id}", homeworkBId)
                                .with(identityFixture.signed(actor)))
                .andExpect(denied);
        assertThat(
                        jdbc.queryForMap(
                                "SELECT title,description,publication_state FROM homeworks WHERE"
                                        + " id=?",
                                homeworkBId))
                .isEqualTo(before);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT COUNT(*) FROM homeworks WHERE group_id=?",
                                Integer.class,
                                groupBId))
                .isEqualTo(countBefore);
    }

    // ============================================================
    // Assignment IDOR
    // ============================================================

    @Test
    void listAssignments_foreignGroupId_returns403() throws Exception {
        mockMvc.perform(
                        asStudentA(
                                get("/academic/assignments")
                                        .param("groupId", groupBId.toString())
                                        .param("semesterId", semesterId.toString())))
                .andExpect(status().isForbidden());
    }

    @Test
    void removeAssignment_foreignAssignment_returns403() throws Exception {
        mockMvc.perform(
                        asHeadmanA(
                                delete("/academic/assignments/{id}", assignmentBId)
                                        .param("validUntilExclusive", "2041-10-16")
                                        .header("Idempotency-Key", UUID.randomUUID())))
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
        mockMvc.perform(
                        get("/academic/homeworks/{id}", homeworkBId)
                                .with(identityFixture.signed(headmanB)))
                .andExpect(status().isOk());
    }

    @Test
    void listHomeworks_sameGroup_doesNotReturn403() throws Exception {
        mockMvc.perform(
                        get("/academic/homeworks")
                                .param("groupId", groupBId.toString())
                                .param("semesterId", semesterId.toString())
                                .with(identityFixture.signed(studentB)))
                .andExpect(status().isOk());
    }
}
