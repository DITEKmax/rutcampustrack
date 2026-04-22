package ru.rutcampustrack.schedule.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
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

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for the schedule view endpoint (VIEW-01, VIEW-02).
 * Verifies access by any authenticated role and full response field coverage.
 */
@AutoConfigureMockMvc
class ScheduleViewIT extends AbstractScheduleIntegrationTest {

    @MockitoBean
    AcademicGrpcClient academicGrpcClient;

    @Autowired
    MockMvc mockMvc;

    @Autowired
    LessonRepository lessonRepository;

    @Autowired
    ScheduleItemRepository scheduleItemRepository;

    private static final Long GROUP_ID = 1L;
    private static final String DATE_FROM = "2026-04-01";
    private static final String DATE_TO = "2026-04-30";

    private ScheduleItem savedItem;

    @BeforeEach
    void setUp() {
        lessonRepository.deleteAll();
        scheduleItemRepository.deleteAll();

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
        savedItem = scheduleItemRepository.save(item);

        // 3 lessons: PLANNED, ACTIVE, CANCELLED
        createLesson(savedItem.getId(), LessonStatus.PLANNED, LocalDate.of(2026, 4, 7));
        createLesson(savedItem.getId(), LessonStatus.ACTIVE, LocalDate.of(2026, 4, 8));
        createLesson(savedItem.getId(), LessonStatus.CANCELLED, LocalDate.of(2026, 4, 9));
    }

    private void createLesson(Long scheduleItemId, LessonStatus status, LocalDate date) {
        Lesson lesson = new Lesson();
        lesson.setScheduleItemId(scheduleItemId);
        lesson.setDate(date);
        lesson.setStatus(status);
        lesson.setGeoBlocked(false);
        lesson.setCreatedAt(OffsetDateTime.now());
        lessonRepository.save(lesson);
    }

    // --- VIEW-01: Any role can view ---

    @Test
    void anyRoleCanView_student() throws Exception {
        mockMvc.perform(get("/schedule/groups/" + GROUP_ID + "/lessons")
                        .param("dateFrom", DATE_FROM)
                        .param("dateTo", DATE_TO)
                        .header("X-User-Id", "10")
                        .header("X-User-Role", "STUDENT")
                        .header("X-Group-Id", GROUP_ID.toString())
                        .header("X-Is-Headman", "false"))
                .andExpect(status().isOk());
    }

    @Test
    void anyRoleCanView_teacher() throws Exception {
        mockMvc.perform(get("/schedule/groups/" + GROUP_ID + "/lessons")
                        .param("dateFrom", DATE_FROM)
                        .param("dateTo", DATE_TO)
                        .header("X-User-Id", "20")
                        .header("X-User-Role", "TEACHER"))
                .andExpect(status().isOk());
    }

    // --- VIEW-02: Response contains all fields ---

    @Test
    void responseContainsAllFields() throws Exception {
        mockMvc.perform(get("/schedule/groups/" + GROUP_ID + "/lessons")
                        .param("dateFrom", DATE_FROM)
                        .param("dateTo", DATE_TO)
                        .header("X-User-Id", "10")
                        .header("X-User-Role", "STUDENT")
                        .header("X-Group-Id", GROUP_ID.toString())
                        .header("X-Is-Headman", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$._embedded.lessonResponseList[0].id", notNullValue()))
                .andExpect(jsonPath("$._embedded.lessonResponseList[0].groupId", is(GROUP_ID.intValue())))
                .andExpect(jsonPath("$._embedded.lessonResponseList[0].subjectId", is(100)))
                .andExpect(jsonPath("$._embedded.lessonResponseList[0].room", is("A-101")))
                .andExpect(jsonPath("$._embedded.lessonResponseList[0].status", notNullValue()))
                .andExpect(jsonPath("$._embedded.lessonResponseList[0].dayOfWeek", is(1)))
                .andExpect(jsonPath("$._embedded.lessonResponseList[0].lessonNumber", is(1)))
                .andExpect(jsonPath("$._embedded.lessonResponseList[0].startTime", notNullValue()))
                .andExpect(jsonPath("$._embedded.lessonResponseList[0].endTime", notNullValue()));
    }

    @Test
    void defaultStatusFilter_excludesCancelled() throws Exception {
        // Without status param, CANCELLED lessons should be excluded (D-18)
        mockMvc.perform(get("/schedule/groups/" + GROUP_ID + "/lessons")
                        .param("dateFrom", DATE_FROM)
                        .param("dateTo", DATE_TO)
                        .header("X-User-Id", "10")
                        .header("X-User-Role", "STUDENT")
                        .header("X-Group-Id", GROUP_ID.toString())
                        .header("X-Is-Headman", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$._embedded.lessonResponseList", hasSize(2)));
    }

    @Test
    void explicitStatusFilter_includesCancelled() throws Exception {
        // With explicit status including CANCELLED, all 3 lessons are returned
        mockMvc.perform(get("/schedule/groups/" + GROUP_ID + "/lessons")
                        .param("dateFrom", DATE_FROM)
                        .param("dateTo", DATE_TO)
                        .param("status", "PLANNED", "ACTIVE", "CLOSED", "CANCELLED")
                        .header("X-User-Id", "10")
                        .header("X-User-Role", "STUDENT")
                        .header("X-Group-Id", GROUP_ID.toString())
                        .header("X-Is-Headman", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$._embedded.lessonResponseList", hasSize(3)));
    }
}
