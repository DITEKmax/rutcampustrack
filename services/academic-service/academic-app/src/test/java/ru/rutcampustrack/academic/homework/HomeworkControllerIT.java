package ru.rutcampustrack.academic.homework;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import ru.rutcampustrack.academic.contract.dto.homework.CreateHomeworkRequest;
import ru.rutcampustrack.academic.grpc.ScheduleGrpcClient;
import ru.rutcampustrack.academic.integration.AbstractAcademicIntegrationTest;
import ru.rutcampustrack.schedule.grpc.LessonResponse;

import java.time.LocalDate;
import java.util.Optional;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Phase 61-03: MockMvc integration tests для {@code POST /academic/homeworks}.
 *
 * <p>Покрывает D-06 (HEADMAN-only), D-04 (lesson must exist + subject match).
 * ScheduleGrpcClient замокан — тесты не поднимают реальный schedule-service.
 *
 * <p>Runs in @Transactional — состояние БД откатывается после каждого теста.
 */
@AutoConfigureMockMvc
class HomeworkControllerIT extends AbstractAcademicIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JdbcTemplate jdbcTemplate;

    @MockitoBean private ScheduleGrpcClient scheduleGrpcClient;

    // Seed data
    private Long seedStudentId;   // обычный студент без is_headman
    private Long seedHeadmanId;   // студент с is_headman=true (создаём per test)
    private Long seedAdminId;
    private Long seedGroupId;
    private Long subjectId;
    private Long semesterId;

    @BeforeEach
    void setUp() {
        seedStudentId = jdbcTemplate.queryForObject(
                "SELECT id FROM users WHERE login = 'student'", Long.class);
        seedGroupId = jdbcTemplate.queryForObject(
                "SELECT id FROM groups WHERE name = 'ИВТ-211'", Long.class);
        seedAdminId = jdbcTemplate.queryForObject(
                "SELECT id FROM users WHERE login = 'admin'", Long.class);
    }

    private void ensureSubjectAndSemester() {
        // Subject для группы (Phase 60-01 V12: subjects.group_id NOT NULL)
        subjectId = jdbcTemplate.queryForObject(
                "INSERT INTO subjects (name, type, group_id) VALUES ('HW Test Subject', 'lecture', ?) RETURNING id",
                Long.class, seedGroupId);

        // Деактивируем активные семестры чтобы не конфликтовать с exclusion constraint
        jdbcTemplate.update("UPDATE semesters SET is_active = false WHERE is_active = true");

        semesterId = jdbcTemplate.queryForObject(
                "INSERT INTO semesters (name, date_from, date_to, is_active, created_at) " +
                "VALUES ('HW Ctrl IT Semester', '2041-02-01', '2041-06-30', false, NOW()) RETURNING id",
                Long.class);
    }

    private void ensureHeadmanUser() {
        seedHeadmanId = jdbcTemplate.queryForObject(
                "INSERT INTO users (login, password_hash, last_name, first_name, role, status, " +
                "group_id, is_headman, password_changed, created_at, updated_at) " +
                "VALUES (?, '$2a$10$dummy', 'Старостов', 'Старо', 'student', 'active', ?, true, false, NOW(), NOW()) RETURNING id",
                Long.class, "headman_hw_ct_" + System.nanoTime(), seedGroupId);
    }

    private String requestBody(LocalDate date) throws Exception {
        return objectMapper.writeValueAsString(new CreateHomeworkRequest(
                "IT HW Title", "description", null,
                subjectId, seedGroupId, semesterId,
                date, 1));
    }

    private void stubLessonFound() {
        when(scheduleGrpcClient.resolveLesson(anyLong(), any(), anyInt()))
                .thenReturn(Optional.of(
                        LessonResponse.newBuilder()
                                .setGroupId(seedGroupId)
                                .setSubjectId(subjectId)
                                .setLessonNumber(1)
                                .setStatus("planned")
                                .build()));
    }

    // =========================================================================
    // D-06: ADMIN → 403
    // =========================================================================

    @Test
    @Transactional
    void createHomework_returns403_forAdmin() throws Exception {
        ensureSubjectAndSemester();
        stubLessonFound();

        mockMvc.perform(post("/academic/homeworks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody(LocalDate.now().plusDays(2)))
                        .header("X-User-Id", seedAdminId)
                        .header("X-User-Role", "ADMIN")
                        .header("X-Group-Id", "")
                        .header("X-Is-Headman", "false"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status", is(403)));
    }

    // =========================================================================
    // D-06: плейн STUDENT (is_headman=false) → 403
    // =========================================================================

    @Test
    @Transactional
    void createHomework_returns403_forPlainStudent() throws Exception {
        ensureSubjectAndSemester();
        stubLessonFound();

        mockMvc.perform(post("/academic/homeworks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody(LocalDate.now().plusDays(2)))
                        .header("X-User-Id", seedStudentId)
                        .header("X-User-Role", "STUDENT")
                        .header("X-Group-Id", seedGroupId)
                        .header("X-Is-Headman", "false"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status", is(403)));
    }

    // =========================================================================
    // D-06 happy path: HEADMAN → 201
    // =========================================================================

    @Test
    @Transactional
    void createHomework_returns201_forHeadman() throws Exception {
        ensureSubjectAndSemester();
        ensureHeadmanUser();
        stubLessonFound();

        mockMvc.perform(post("/academic/homeworks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody(LocalDate.now().plusDays(2)))
                        .header("X-User-Id", seedHeadmanId)
                        .header("X-User-Role", "STUDENT")
                        .header("X-Group-Id", seedGroupId)
                        .header("X-Is-Headman", "true"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title", is("IT HW Title")))
                .andExpect(jsonPath("$.lessonNumber", is(1)))
                .andExpect(jsonPath("$.publishedBy", is(seedHeadmanId.intValue())));
    }

    // =========================================================================
    // D-04: lesson not found → 400
    // =========================================================================

    @Test
    @Transactional
    void createHomework_returns400_whenLessonNotInSchedule() throws Exception {
        ensureSubjectAndSemester();
        ensureHeadmanUser();
        when(scheduleGrpcClient.resolveLesson(anyLong(), any(), anyInt()))
                .thenReturn(Optional.empty());

        mockMvc.perform(post("/academic/homeworks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody(LocalDate.now().plusDays(2)))
                        .header("X-User-Id", seedHeadmanId)
                        .header("X-User-Role", "STUDENT")
                        .header("X-Group-Id", seedGroupId)
                        .header("X-Is-Headman", "true"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.detail", containsString("расписании")));
    }

    // =========================================================================
    // Sanity: ≥2 ДЗ на одну пару создаются подряд (нет UNIQUE constraint)
    // =========================================================================

    @Test
    @Transactional
    void createHomework_allowsMultiplePerLesson() throws Exception {
        ensureSubjectAndSemester();
        ensureHeadmanUser();
        stubLessonFound();

        String body = requestBody(LocalDate.now().plusDays(2));

        mockMvc.perform(post("/academic/homeworks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .header("X-User-Id", seedHeadmanId)
                        .header("X-User-Role", "STUDENT")
                        .header("X-Group-Id", seedGroupId)
                        .header("X-Is-Headman", "true"))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/academic/homeworks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .header("X-User-Id", seedHeadmanId)
                        .header("X-User-Role", "STUDENT")
                        .header("X-Group-Id", seedGroupId)
                        .header("X-Is-Headman", "true"))
                .andExpect(status().isCreated());
    }
}
