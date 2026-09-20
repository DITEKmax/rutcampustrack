package ru.rutcampustrack.schedule.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import ru.rutcampustrack.schedule.contract.enums.LessonStatus;
import ru.rutcampustrack.schedule.grpc.AcademicGrpcClient;
import ru.rutcampustrack.schedule.item.entity.ScheduleItem;
import ru.rutcampustrack.schedule.item.repository.ScheduleItemRepository;
import ru.rutcampustrack.schedule.lesson.entity.Lesson;
import ru.rutcampustrack.schedule.lesson.repository.LessonRepository;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for lesson operations: cancel (LSSN-04), restore (LSSN-05),
 * removed mass-cancel route (LSSN-06), and geo-block toggle (LSSN-07).
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
    JdbcTemplate jdbcTemplate;

    @Autowired
    ScheduleItemRepository scheduleItemRepository;

    private static final AtomicLong FIXTURE_SEQUENCE =
            new AtomicLong(System.currentTimeMillis() * 1000L);
    private static final Long USER_ID = 42L;
    private Long testGroupId;

    @BeforeEach
    void setUp() {
        // V17 retains physical lesson history and rejects DELETE. Each test
        // therefore owns a fresh positive group identity instead of clearing
        // the shared reused PostgreSQL database.
        testGroupId = FIXTURE_SEQUENCE.incrementAndGet();
        when(academicGrpcClient.isHeadman(USER_ID, testGroupId)).thenReturn(true);
    }

    private ScheduleItem createScheduleItem() {
        Long itemId = jdbcTemplate.queryForObject("""
                INSERT INTO schedule_items
                    (assignment_id, group_id, subject_id, semester_id, day_of_week,
                     lesson_number, start_time, end_time, week_type, room)
                VALUES (?, ?, 100, 10, 1, 1, '08:30'::time, '10:00'::time, 'all', 'A-101')
                RETURNING id
                """, Long.class, testGroupId, testGroupId);
        return scheduleItemRepository.findById(itemId).orElseThrow();
    }

    private Lesson createLesson(Long scheduleItemId, LessonStatus status, LocalDate date) {
        Long occurrenceId = jdbcTemplate.queryForObject("""
                INSERT INTO lesson_occurrences
                    (schedule_item_id, occurrence_date, assignment_id, group_id,
                     subject_id, semester_id, assigned_teacher_id, lesson_type)
                VALUES (?, ?, ?, ?, 100, 10, 700, 'lecture')
                RETURNING id
                """, Long.class, scheduleItemId, date, testGroupId, testGroupId);
        Long lessonId = jdbcTemplate.queryForObject("""
                INSERT INTO lessons
                    (schedule_item_id, occurrence_id, assignment_id, group_id, subject_id,
                     semester_id, assigned_teacher_id, lesson_type, lesson_number,
                     start_time, end_time, generation, revision, date, status, is_geo_blocked)
                VALUES (?, ?, ?, ?, 100, 10, 700, 'lecture', 1,
                        '08:30'::time, '10:00'::time, 1, 1, ?, ?::lesson_status, false)
                RETURNING id
                """, Long.class, scheduleItemId, occurrenceId, testGroupId, testGroupId,
                date, status.name().toLowerCase());
        jdbcTemplate.update("UPDATE lesson_occurrences SET current_lesson_id = ? WHERE id = ?",
                lessonId, occurrenceId);
        return lessonRepository.findById(lessonId).orElseThrow();
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder withHeadmanHeaders(
            org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request) {
        return request
                .header("X-User-Id", USER_ID.toString())
                .header("X-User-Role", "STUDENT")
                .header("X-Group-Id", testGroupId.toString())
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
                .andExpect(jsonPath("$.cancelReason", is("Snow day")))
                .andExpect(jsonPath("$.cancelledBy").exists())
                .andExpect(jsonPath("$.cancelledAt").exists());

        Lesson saved = lessonRepository.findById(lesson.getId()).orElseThrow();
        org.assertj.core.api.Assertions.assertThat(saved.getStatus()).isEqualTo(LessonStatus.CANCELLED);
        org.assertj.core.api.Assertions.assertThat(saved.getCancelledBy()).isNotNull();
        org.assertj.core.api.Assertions.assertThat(saved.getCancelledAt()).isNotNull();
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
        lesson.setCancelledBy(99L);
        lesson.setCancelledAt(java.time.OffsetDateTime.now());
        lessonRepository.save(lesson);

        mockMvc.perform(withHeadmanHeaders(
                patch("/schedule/lessons/" + lesson.getId() + "/restore")
        ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("PLANNED")))
                .andExpect(jsonPath("$.cancelReason", nullValue()))
                .andExpect(jsonPath("$.cancelledBy", nullValue()))
                .andExpect(jsonPath("$.cancelledAt", nullValue()));

        Lesson saved = lessonRepository.findById(lesson.getId()).orElseThrow();
        org.assertj.core.api.Assertions.assertThat(saved.getStatus()).isEqualTo(LessonStatus.PLANNED);
        org.assertj.core.api.Assertions.assertThat(saved.getCancelReason()).isNull();
        org.assertj.core.api.Assertions.assertThat(saved.getCancelledBy()).isNull();
        org.assertj.core.api.Assertions.assertThat(saved.getCancelledAt()).isNull();
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

    // --- LSSN-06: Removed mass-cancel route ---

    @Test
    void massCancelLessons_removedRoute_doesNotMutateLessonOrPublishEvent() throws Exception {
        ScheduleItem item = createScheduleItem();
        Lesson lesson = createLesson(item.getId(), LessonStatus.PLANNED, LocalDate.of(2026, 4, 1));
        long lessonCountBefore = lessonRepository.count();
        long cancelledEventsBefore = outboxStorage.findPending(1000).stream()
                .filter(record -> "lesson.cancelled".equals(record.eventType()))
                .count();

        String body = objectMapper.writeValueAsString(Map.of(
                "groupId", testGroupId,
                "dateFrom", "2026-04-01",
                "dateTo", "2026-04-03",
                "reason", "Holiday"
        ));

        mockMvc.perform(withHeadmanHeaders(
                post("/schedule/lessons/mass-cancel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
        ))
                .andExpect(status().isNotFound());

        Lesson unchanged = lessonRepository.findById(lesson.getId()).orElseThrow();
        org.assertj.core.api.Assertions.assertThat(lessonRepository.count()).isEqualTo(lessonCountBefore);
        org.assertj.core.api.Assertions.assertThat(unchanged.getStatus()).isEqualTo(LessonStatus.PLANNED);
        org.assertj.core.api.Assertions.assertThat(unchanged.getCancelReason()).isNull();
        org.assertj.core.api.Assertions.assertThat(unchanged.getCancelledBy()).isNull();
        org.assertj.core.api.Assertions.assertThat(unchanged.getCancelledAt()).isNull();
        org.assertj.core.api.Assertions.assertThat(outboxStorage.findPending(1000).stream()
                .filter(record -> "lesson.cancelled".equals(record.eventType()))
                .count()).isEqualTo(cancelledEventsBefore);
    }

    @Test
    void apiDocs_omitRemovedMassCancelContract() throws Exception {
        mockMvc.perform(get("/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/schedule/lessons/mass-cancel']").doesNotExist())
                .andExpect(jsonPath("$.components.schemas.MassCancelRequest").doesNotExist())
                .andExpect(jsonPath("$.components.schemas.MassCancelResponse").doesNotExist());
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
    void blockLesson_active_success() throws Exception {
        ScheduleItem item = createScheduleItem();
        Lesson lesson = createLesson(item.getId(), LessonStatus.ACTIVE, LocalDate.of(2026, 4, 20));

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
        when(academicGrpcClient.isHeadman(USER_ID, testGroupId)).thenReturn(false);
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

    // --- VIEW-01: lessons listing with case-insensitive status filter ---

    /**
     * Regression: web-panel headman lessons page sends
     * {@code ?status=planned,active,closed,cancelled} (lowercase, CSV).
     * Without {@link ru.rutcampustrack.schedule.config.WebConfig} the Spring
     * binder fails to parse lowercase enum values and returns 400.
     */
    @Test
    void getLessons_lowercaseStatusCsv_isAccepted() throws Exception {
        ScheduleItem item = createScheduleItem();
        createLesson(item.getId(), LessonStatus.PLANNED, LocalDate.of(2026, 4, 28));
        createLesson(item.getId(), LessonStatus.CANCELLED, LocalDate.of(2026, 4, 29));

        mockMvc.perform(withHeadmanHeaders(
                get("/schedule/groups/" + testGroupId + "/lessons")
                        .param("dateFrom", "2026-04-28")
                        .param("dateTo", "2026-05-27")
                        .param("size", "500")
                        .param("status", "planned,active,closed,cancelled")
        ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements", is(2)));
    }
}
