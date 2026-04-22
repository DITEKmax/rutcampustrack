package ru.rutcampustrack.schedule.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import ru.rutcampustrack.schedule.contract.enums.LessonStatus;
import ru.rutcampustrack.schedule.contract.enums.WeekType;
import ru.rutcampustrack.schedule.grpc.AcademicGrpcClient;
import ru.rutcampustrack.schedule.item.entity.ScheduleItem;
import ru.rutcampustrack.schedule.item.repository.ScheduleItemRepository;
import ru.rutcampustrack.schedule.lesson.entity.Lesson;
import ru.rutcampustrack.schedule.lesson.repository.LessonRepository;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.Map;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for lesson operations: cancel (LSSN-04), restore (LSSN-05),
 * mass-cancel (LSSN-06), and geo-block toggle (LSSN-07).
 */
@AutoConfigureMockMvc
class LessonApiIT extends AbstractScheduleIntegrationTest {

    @MockitoBean
    AcademicGrpcClient academicGrpcClient;

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    LessonRepository lessonRepository;

    @Autowired
    ScheduleItemRepository scheduleItemRepository;

    private static final Long GROUP_ID = 1L;
    private static final Long USER_ID = 42L;

    @BeforeEach
    void setUp() {
        lessonRepository.deleteAll();
        scheduleItemRepository.deleteAll();
        when(academicGrpcClient.isHeadman(USER_ID, GROUP_ID)).thenReturn(true);
    }

    private ScheduleItem createScheduleItem() {
        ScheduleItem item = new ScheduleItem();
        item.setGroupId(GROUP_ID);
        item.setSubjectId(100L);
        item.setSemesterId(10L);
        item.setDayOfWeek((short) 1);
        item.setLessonNumber((short) 1);
        item.setStartTime(LocalTime.of(8, 30));
        item.setEndTime(LocalTime.of(10, 0));
        item.setWeekType(WeekType.ALL);
        item.setRoom("A-101");
        item.setActive(true);
        item.setCreatedAt(OffsetDateTime.now());
        return scheduleItemRepository.save(item);
    }

    private Lesson createLesson(Long scheduleItemId, LessonStatus status, LocalDate date) {
        Lesson lesson = new Lesson();
        lesson.setScheduleItemId(scheduleItemId);
        lesson.setDate(date);
        lesson.setStatus(status);
        lesson.setGeoBlocked(false);
        lesson.setCreatedAt(OffsetDateTime.now());
        return lessonRepository.save(lesson);
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder withHeadmanHeaders(
            org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request) {
        return request
                .header("X-User-Id", USER_ID.toString())
                .header("X-User-Role", "STUDENT")
                .header("X-Group-Id", GROUP_ID.toString())
                .header("X-Is-Headman", "true");
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder withAdminHeaders(
            org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request) {
        return request
                .header("X-User-Id", "999")
                .header("X-User-Role", "ADMIN");
    }

    // --- LSSN-04: Cancel ---

    @Test
    void cancelLesson_planned_success() throws Exception {
        ScheduleItem item = createScheduleItem();
        Lesson lesson = createLesson(item.getId(), LessonStatus.PLANNED, LocalDate.of(2026, 4, 1));

        mockMvc.perform(withHeadmanHeaders(
                patch("/schedule/lessons/" + lesson.getId() + "/cancel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("reason", "Snow day")))
        ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("CANCELLED")))
                .andExpect(jsonPath("$.cancelReason", is("Snow day")));

        Lesson saved = lessonRepository.findById(lesson.getId()).orElseThrow();
        org.assertj.core.api.Assertions.assertThat(saved.getStatus()).isEqualTo(LessonStatus.CANCELLED);
    }

    @Test
    void cancelLesson_activeLesson_succeeds() throws Exception {
        // After UX request: HEADMAN/ADMIN can cancel ACTIVE/CLOSED lessons too,
        // not only PLANNED. The only forbidden source state is CANCELLED itself.
        ScheduleItem item = createScheduleItem();
        Lesson lesson = createLesson(item.getId(), LessonStatus.ACTIVE, LocalDate.of(2026, 4, 1));

        mockMvc.perform(withHeadmanHeaders(
                patch("/schedule/lessons/" + lesson.getId() + "/cancel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("reason", "Cancel running lesson")))
        ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("CANCELLED")));
    }

    @Test
    void cancelLesson_closedLesson_succeeds() throws Exception {
        // CLOSED lessons can be retroactively cancelled so headman can correct
        // historical attendance records (UX requirement).
        ScheduleItem item = createScheduleItem();
        Lesson lesson = createLesson(item.getId(), LessonStatus.CLOSED, LocalDate.of(2026, 3, 1));

        mockMvc.perform(withHeadmanHeaders(
                patch("/schedule/lessons/" + lesson.getId() + "/cancel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("reason", "Replaced retroactively")))
        ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("CANCELLED")));
    }

    @Test
    void cancelLesson_alreadyCancelled_returns422() throws Exception {
        ScheduleItem item = createScheduleItem();
        Lesson lesson = createLesson(item.getId(), LessonStatus.CANCELLED, LocalDate.of(2026, 4, 1));

        mockMvc.perform(withHeadmanHeaders(
                patch("/schedule/lessons/" + lesson.getId() + "/cancel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("reason", "Double cancel")))
        ))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void cancelLesson_missingReason_returns400() throws Exception {
        ScheduleItem item = createScheduleItem();
        Lesson lesson = createLesson(item.getId(), LessonStatus.PLANNED, LocalDate.of(2026, 4, 1));

        mockMvc.perform(withHeadmanHeaders(
                patch("/schedule/lessons/" + lesson.getId() + "/cancel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("reason", "")))
        ))
                .andExpect(status().isBadRequest());
    }

    // --- LSSN-05: Restore ---

    @Test
    void restoreLesson_cancelled_success() throws Exception {
        ScheduleItem item = createScheduleItem();
        Lesson lesson = createLesson(item.getId(), LessonStatus.CANCELLED, LocalDate.of(2026, 4, 1));
        lesson.setCancelReason("old reason");
        lessonRepository.save(lesson);

        mockMvc.perform(withHeadmanHeaders(
                patch("/schedule/lessons/" + lesson.getId() + "/restore")
        ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("PLANNED")))
                .andExpect(jsonPath("$.cancelReason", nullValue()));

        Lesson saved = lessonRepository.findById(lesson.getId()).orElseThrow();
        org.assertj.core.api.Assertions.assertThat(saved.getStatus()).isEqualTo(LessonStatus.PLANNED);
        org.assertj.core.api.Assertions.assertThat(saved.getCancelReason()).isNull();
    }

    @Test
    void restoreLesson_planned_returns422() throws Exception {
        ScheduleItem item = createScheduleItem();
        Lesson lesson = createLesson(item.getId(), LessonStatus.PLANNED, LocalDate.of(2026, 4, 1));

        mockMvc.perform(withHeadmanHeaders(
                patch("/schedule/lessons/" + lesson.getId() + "/restore")
        ))
                .andExpect(status().isUnprocessableEntity());
    }

    // --- LSSN-06: Mass Cancel ---

    @Test
    void massCancelLessons_success() throws Exception {
        ScheduleItem item = createScheduleItem();
        Lesson planned1 = createLesson(item.getId(), LessonStatus.PLANNED, LocalDate.of(2026, 4, 1));
        Lesson planned2 = createLesson(item.getId(), LessonStatus.PLANNED, LocalDate.of(2026, 4, 2));
        Lesson active = createLesson(item.getId(), LessonStatus.ACTIVE, LocalDate.of(2026, 4, 3));

        String body = objectMapper.writeValueAsString(Map.of(
                "groupId", GROUP_ID,
                "dateFrom", "2026-04-01",
                "dateTo", "2026-04-03",
                "reason", "Holiday"
        ));

        mockMvc.perform(withHeadmanHeaders(
                post("/schedule/lessons/mass-cancel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
        ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cancelledCount", is(2)));

        org.assertj.core.api.Assertions.assertThat(lessonRepository.findById(planned1.getId()).orElseThrow().getStatus())
                .isEqualTo(LessonStatus.CANCELLED);
        org.assertj.core.api.Assertions.assertThat(lessonRepository.findById(planned2.getId()).orElseThrow().getStatus())
                .isEqualTo(LessonStatus.CANCELLED);
        org.assertj.core.api.Assertions.assertThat(lessonRepository.findById(active.getId()).orElseThrow().getStatus())
                .isEqualTo(LessonStatus.ACTIVE);
    }

    // --- LSSN-07: Geo-block Toggle ---

    // --- v9.0: Headman hard-lock (blockage) ---

    @Test
    void blockLesson_planned_success() throws Exception {
        ScheduleItem item = createScheduleItem();
        Lesson lesson = createLesson(item.getId(), LessonStatus.PLANNED, LocalDate.of(2026, 4, 20));

        mockMvc.perform(withHeadmanHeaders(
                post("/schedule/lessons/" + lesson.getId() + "/blockage")
        ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.blockedByHeadman", is(true)))
                .andExpect(jsonPath("$.blockedByUserId", is(USER_ID.intValue())));

        Lesson saved = lessonRepository.findById(lesson.getId()).orElseThrow();
        org.assertj.core.api.Assertions.assertThat(saved.isBlockedByHeadman()).isTrue();
        org.assertj.core.api.Assertions.assertThat(saved.getBlockedByUserId()).isEqualTo(USER_ID);
        org.assertj.core.api.Assertions.assertThat(saved.getBlockedAt()).isNotNull();
    }

    @Test
    void blockLesson_cancelled_returns422() throws Exception {
        ScheduleItem item = createScheduleItem();
        Lesson lesson = createLesson(item.getId(), LessonStatus.CANCELLED, LocalDate.of(2026, 4, 20));

        mockMvc.perform(withHeadmanHeaders(
                post("/schedule/lessons/" + lesson.getId() + "/blockage")
        ))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void blockLesson_closed_returns422() throws Exception {
        // Past lessons are CLOSED — blocking them has no effect on checkin,
        // so we reject the request to avoid confusing UX.
        ScheduleItem item = createScheduleItem();
        Lesson lesson = createLesson(item.getId(), LessonStatus.CLOSED, LocalDate.of(2026, 3, 1));

        mockMvc.perform(withHeadmanHeaders(
                post("/schedule/lessons/" + lesson.getId() + "/blockage")
        ))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void unblockLesson_success() throws Exception {
        ScheduleItem item = createScheduleItem();
        Lesson lesson = createLesson(item.getId(), LessonStatus.PLANNED, LocalDate.of(2026, 4, 20));
        lesson.setBlockedByHeadman(true);
        lesson.setBlockedByUserId(USER_ID);
        lesson.setBlockedAt(OffsetDateTime.now());
        lessonRepository.save(lesson);

        mockMvc.perform(withHeadmanHeaders(
                delete("/schedule/lessons/" + lesson.getId() + "/blockage")
        ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.blockedByHeadman", is(false)))
                .andExpect(jsonPath("$.blockedByUserId", nullValue()));

        Lesson saved = lessonRepository.findById(lesson.getId()).orElseThrow();
        org.assertj.core.api.Assertions.assertThat(saved.isBlockedByHeadman()).isFalse();
        org.assertj.core.api.Assertions.assertThat(saved.getBlockedByUserId()).isNull();
        org.assertj.core.api.Assertions.assertThat(saved.getBlockedAt()).isNull();
    }

    @Test
    void blockLesson_notHeadman_returns403() throws Exception {
        when(academicGrpcClient.isHeadman(USER_ID, GROUP_ID)).thenReturn(false);
        ScheduleItem item = createScheduleItem();
        Lesson lesson = createLesson(item.getId(), LessonStatus.PLANNED, LocalDate.of(2026, 4, 20));

        mockMvc.perform(withHeadmanHeaders(
                post("/schedule/lessons/" + lesson.getId() + "/blockage")
        ))
                .andExpect(status().isForbidden());
    }

    @Test
    void toggleGeoBlock_success() throws Exception {
        ScheduleItem item = createScheduleItem();
        Lesson lesson = createLesson(item.getId(), LessonStatus.PLANNED, LocalDate.of(2026, 4, 1));

        // Toggle on
        mockMvc.perform(withHeadmanHeaders(
                patch("/schedule/lessons/" + lesson.getId() + "/geo-block")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("blocked", true)))
        ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.geoBlocked", is(true)));

        org.assertj.core.api.Assertions.assertThat(lessonRepository.findById(lesson.getId()).orElseThrow().isGeoBlocked())
                .isTrue();

        // Toggle off
        mockMvc.perform(withHeadmanHeaders(
                patch("/schedule/lessons/" + lesson.getId() + "/geo-block")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("blocked", false)))
        ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.geoBlocked", is(false)));
    }
}
