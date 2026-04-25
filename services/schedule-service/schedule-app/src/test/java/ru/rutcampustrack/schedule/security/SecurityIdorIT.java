package ru.rutcampustrack.schedule.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;
import ru.rutcampustrack.academic.grpc.SemesterResponse;
import ru.rutcampustrack.schedule.grpc.AcademicGrpcClient;
import ru.rutcampustrack.schedule.integration.AbstractScheduleIntegrationTest;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * M13 G9 (NEW-31) — IDOR-проверки schedule-service.
 *
 * <p>Schedule имеет 2 уровня защиты:
 * <ol>
 *   <li>Write-операции (cancel/restore/blockLesson/...) проверяют headman через
 *       gRPC {@code AcademicGrpcClient.isHeadman}. Mock возвращает false для
 *       чужой группы → AccessDenied.</li>
 *   <li>Read-операции (getLessons/listScheduleItems/...) проверяют
 *       {@code requestContext.groupId == targetGroupId} (M13 G9 fix).</li>
 * </ol>
 */
@AutoConfigureMockMvc
@Transactional
class SecurityIdorIT extends AbstractScheduleIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;

    @MockitoBean private AcademicGrpcClient academicGrpcClient;

    // Группы
    private final Long groupAId = 1001L;
    private final Long groupBId = 1002L;
    // Студенты
    private final Long studentAId = 9001L;
    private final Long headmanAId = 9002L;
    private final Long headmanBId = 9003L;
    // Семестр
    private final Long semesterId = 5001L;

    private Long scheduleItemBId;
    private Long lessonBId;
    private Long oneOffLessonBId;

    @BeforeEach
    void seed() {
        // schedule_items для группы B (V3 удалил teacher_id)
        scheduleItemBId = jdbc.queryForObject(
                "INSERT INTO schedule_items (group_id, subject_id, semester_id, " +
                "day_of_week, lesson_number, start_time, end_time, week_type, is_active, created_at) " +
                "VALUES (?, 100, ?, 1, 2, '10:00', '11:30', 'all', true, NOW()) RETURNING id",
                Long.class, groupBId, semesterId);

        // lesson группы B на эту schedule_item
        lessonBId = jdbc.queryForObject(
                "INSERT INTO lessons (schedule_item_id, date, status, is_geo_blocked, created_at) " +
                "VALUES (?, '2041-10-15', 'planned', false, NOW()) RETURNING id",
                Long.class, scheduleItemBId);

        // one-off для группы B
        oneOffLessonBId = jdbc.queryForObject(
                "INSERT INTO schedule_one_off_lessons (group_id, subject_id, semester_id, date, " +
                "lesson_number, classroom, created_by, created_at) " +
                "VALUES (?, 100, ?, '2041-10-20', 3, 'A-101', ?, NOW()) RETURNING id",
                Long.class, groupBId, semesterId, headmanBId);

        // gRPC mock — headman группы B != group A
        when(academicGrpcClient.isHeadman(anyLong(), anyLong())).thenReturn(false);
        // ADMIN/TEACHER редко bypass — для read endpoint'ов нужен active semester
        SemesterResponse activeSem = SemesterResponse.newBuilder()
                .setId(semesterId)
                .setName("Test sem")
                .setDateFrom("2041-09-01")
                .setDateTo("2041-12-31")
                .setFirstWeekType("odd")
                .build();
        when(academicGrpcClient.getActiveSemester()).thenReturn(activeSem);
    }

    private MockHttpServletRequestBuilder asStudentA(MockHttpServletRequestBuilder b) {
        return b.header("X-User-Id", studentAId)
                .header("X-User-Role", "STUDENT")
                .header("X-Group-Id", groupAId)
                .header("X-Is-Headman", "false");
    }

    private MockHttpServletRequestBuilder asHeadmanA(MockHttpServletRequestBuilder b) {
        return b.header("X-User-Id", headmanAId)
                .header("X-User-Role", "STUDENT")
                .header("X-Group-Id", groupAId)
                .header("X-Is-Headman", "true");
    }

    // ============================================================
    // Read IDOR (M13 G9 fix)
    // ============================================================

    @Test
    void getLessons_foreignGroupId_returns403() throws Exception {
        mockMvc.perform(asStudentA(
                        get("/schedule/groups/{groupId}/lessons", groupBId)
                                .param("dateFrom", "2041-09-01")
                                .param("dateTo", "2041-12-31")))
                .andExpect(status().isForbidden());
    }

    @Test
    void listScheduleItems_foreignGroupId_returns403() throws Exception {
        mockMvc.perform(asStudentA(
                        get("/schedule/items")
                                .param("groupId", groupBId.toString())
                                .param("semesterId", semesterId.toString())))
                .andExpect(status().isForbidden());
    }

    @Test
    void getScheduleItem_foreignGroup_returns403() throws Exception {
        mockMvc.perform(asStudentA(get("/schedule/items/{id}", scheduleItemBId)))
                .andExpect(status().isForbidden());
    }

    @Test
    void listOneOffLessons_foreignGroupId_returns403() throws Exception {
        mockMvc.perform(asStudentA(
                        get("/schedule/one-off-lessons")
                                .param("groupId", groupBId.toString())
                                .param("dateFrom", "2041-09-01")
                                .param("dateTo", "2041-12-31")))
                .andExpect(status().isForbidden());
    }

    // ============================================================
    // Write IDOR (через gRPC isHeadman=false для чужой группы)
    // ============================================================

    @Test
    void cancelLesson_foreignGroup_returns403() throws Exception {
        mockMvc.perform(asHeadmanA(
                        patch("/schedule/lessons/{id}/cancel", lessonBId)
                                .contentType("application/json")
                                .content("{\"reason\":\"test\"}")))
                .andExpect(status().isForbidden());
    }

    @Test
    void restoreLesson_foreignGroup_returns403() throws Exception {
        mockMvc.perform(asHeadmanA(patch("/schedule/lessons/{id}/restore", lessonBId)))
                .andExpect(status().isForbidden());
    }

    @Test
    void toggleGeoBlock_foreignGroup_returns403() throws Exception {
        mockMvc.perform(asHeadmanA(
                        patch("/schedule/lessons/{id}/geo-block", lessonBId)
                                .contentType("application/json")
                                .content("{\"blocked\":true}")))
                .andExpect(status().isForbidden());
    }

    @Test
    void blockLesson_foreignGroup_returns403() throws Exception {
        mockMvc.perform(asHeadmanA(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                .post("/schedule/lessons/{id}/blockage", lessonBId)))
                .andExpect(status().isForbidden());
    }

    @Test
    void unblockLesson_foreignGroup_returns403() throws Exception {
        mockMvc.perform(asHeadmanA(delete("/schedule/lessons/{id}/blockage", lessonBId)))
                .andExpect(status().isForbidden());
    }

    @Test
    void deleteOneOffLesson_foreignGroup_returns403() throws Exception {
        mockMvc.perform(asHeadmanA(delete("/schedule/one-off-lessons/{id}", oneOffLessonBId)))
                .andExpect(status().isForbidden());
    }

    @Test
    void deleteScheduleItem_foreignGroup_returns403() throws Exception {
        mockMvc.perform(asHeadmanA(delete("/schedule/items/{id}", scheduleItemBId)))
                .andExpect(status().isForbidden());
    }
}
