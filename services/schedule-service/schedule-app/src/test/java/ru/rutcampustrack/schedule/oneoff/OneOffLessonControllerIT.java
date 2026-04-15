package ru.rutcampustrack.schedule.oneoff;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import ru.rutcampustrack.academic.grpc.GroupResponse;
import ru.rutcampustrack.academic.grpc.SemesterResponse;
import ru.rutcampustrack.schedule.contract.dto.oneoff.CreateOneOffLessonRequest;
import ru.rutcampustrack.schedule.contract.enums.WeekType;
import ru.rutcampustrack.schedule.grpc.AcademicGrpcClient;
import ru.rutcampustrack.schedule.integration.AbstractScheduleIntegrationTest;
import ru.rutcampustrack.schedule.item.entity.ScheduleItem;
import ru.rutcampustrack.schedule.item.repository.ScheduleItemRepository;
import ru.rutcampustrack.schedule.lesson.repository.LessonRepository;
import ru.rutcampustrack.schedule.oneoff.entity.OneOffLesson;
import ru.rutcampustrack.schedule.oneoff.repository.OneOffLessonRepository;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for one-off lesson API (Phase 60-03).
 * Covers AC-05/AC-06, D-08/D-09/D-11/D-21/D-22/D-23.
 */
@AutoConfigureMockMvc
class OneOffLessonControllerIT extends AbstractScheduleIntegrationTest {

    private static final Long GROUP_ID = 1L;
    private static final Long OTHER_GROUP_ID = 2L;
    private static final Long SEMESTER_ID = 10L;
    private static final Long SUBJECT_ID = 100L;
    private static final Long HEADMAN_USER_ID = 42L;
    // Semester "Spring 2026" spans Feb-Jun; pick a Monday (2026-04-20), lesson slot 1.
    private static final LocalDate TARGET_DATE = LocalDate.of(2026, 4, 20);
    private static final short TARGET_LESSON = (short) 1;
    // day_of_week in schedule_items: 1=Mon..7=Sun (aligned with java.time.DayOfWeek).
    // 2026-04-20 is Monday → 1.
    private static final short TARGET_DOW = (short) 1;

    @MockitoBean
    AcademicGrpcClient academicGrpcClient;

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    OneOffLessonRepository oneOffLessonRepository;

    @Autowired
    ScheduleItemRepository scheduleItemRepository;

    @Autowired
    LessonRepository lessonRepository;

    private static final SemesterResponse MOCK_SEMESTER = SemesterResponse.newBuilder()
            .setId(SEMESTER_ID)
            .setName("Spring 2026")
            .setDateFrom("2026-02-02")   // Monday
            .setDateTo("2026-06-30")
            .setFirstWeekType("odd")
            .build();

    @BeforeEach
    void setUp() {
        lessonRepository.deleteAll();
        oneOffLessonRepository.deleteAll();
        scheduleItemRepository.deleteAll();

        when(academicGrpcClient.getActiveSemester()).thenReturn(MOCK_SEMESTER);
        when(academicGrpcClient.parseSemesterFirstWeekType(MOCK_SEMESTER))
                .thenReturn(WeekType.ODD);
        when(academicGrpcClient.validateGroup(GROUP_ID))
                .thenReturn(GroupResponse.newBuilder()
                        .setId(GROUP_ID).setName("Test Group").setIsActive(true).build());
        when(academicGrpcClient.validateGroup(OTHER_GROUP_ID))
                .thenReturn(GroupResponse.newBuilder()
                        .setId(OTHER_GROUP_ID).setName("Other Group").setIsActive(true).build());
        when(academicGrpcClient.isHeadman(HEADMAN_USER_ID, GROUP_ID)).thenReturn(true);
        when(academicGrpcClient.isHeadman(HEADMAN_USER_ID, OTHER_GROUP_ID)).thenReturn(false);
    }

    // ---------- Header helpers ----------

    private MockHttpServletRequestBuilder withHeadmanHeaders(MockHttpServletRequestBuilder b) {
        return b.header("X-User-Id", HEADMAN_USER_ID)
                .header("X-User-Role", "STUDENT")
                .header("X-Group-Id", GROUP_ID)
                .header("X-Is-Headman", "true");
    }

    private String createRequestJson(Long groupId, LocalDate date, short lesson) throws Exception {
        return objectMapper.writeValueAsString(new CreateOneOffLessonRequest(
                groupId, SUBJECT_ID, date, lesson, "C-303"));
    }

    private OneOffLesson persistOneOff(Long groupId, LocalDate date, short lesson) {
        OneOffLesson o = new OneOffLesson();
        o.setGroupId(groupId);
        o.setSubjectId(SUBJECT_ID);
        o.setSemesterId(SEMESTER_ID);
        o.setDate(date);
        o.setLessonNumber(lesson);
        o.setClassroom("B-202");
        o.setCreatedBy(HEADMAN_USER_ID);
        return oneOffLessonRepository.save(o);
    }

    private ScheduleItem persistActiveTemplate(short dayOfWeek, short lessonNumber, WeekType weekType) {
        ScheduleItem item = new ScheduleItem();
        item.setGroupId(GROUP_ID);
        item.setSubjectId(SUBJECT_ID);
        item.setSemesterId(SEMESTER_ID);
        item.setDayOfWeek(dayOfWeek);
        item.setLessonNumber(lessonNumber);
        item.setStartTime(LocalTime.of(8, 30));
        item.setEndTime(LocalTime.of(10, 0));
        item.setWeekType(weekType);
        item.setRoom("A-101");
        item.setActive(true);
        item.setCreatedAt(OffsetDateTime.now());
        return scheduleItemRepository.save(item);
    }

    // ---------- 1. Create — happy path ----------

    @Test
    void create_validRequest_returns201() throws Exception {
        mockMvc.perform(withHeadmanHeaders(post("/schedule/one-off-lessons"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createRequestJson(GROUP_ID, TARGET_DATE, TARGET_LESSON)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.groupId", is(GROUP_ID.intValue())))
                .andExpect(jsonPath("$.subjectId", is(SUBJECT_ID.intValue())))
                .andExpect(jsonPath("$.semesterId", is(SEMESTER_ID.intValue())))
                .andExpect(jsonPath("$.date", is(TARGET_DATE.toString())))
                .andExpect(jsonPath("$.lessonNumber", is((int) TARGET_LESSON)))
                .andExpect(jsonPath("$.classroom", is("C-303")))
                .andExpect(jsonPath("$.createdBy", is(HEADMAN_USER_ID.intValue())));

        assertThat(oneOffLessonRepository.count()).isEqualTo(1);
    }

    // ---------- 2. Template conflict → 409 (D-09) ----------

    @Test
    void create_whenTemplateSlotActive_returns409() throws Exception {
        // active ALL-week template on the same (group, dow, slot) → always conflicts.
        persistActiveTemplate(TARGET_DOW, TARGET_LESSON, WeekType.ALL);

        mockMvc.perform(withHeadmanHeaders(post("/schedule/one-off-lessons"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createRequestJson(GROUP_ID, TARGET_DATE, TARGET_LESSON)))
                .andExpect(status().isConflict());

        assertThat(oneOffLessonRepository.count()).isZero();
    }

    // ---------- 3. Duplicate one-off → 409 (D-21) ----------

    @Test
    void create_whenDuplicateOneOff_returns409() throws Exception {
        persistOneOff(GROUP_ID, TARGET_DATE, TARGET_LESSON);

        mockMvc.perform(withHeadmanHeaders(post("/schedule/one-off-lessons"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createRequestJson(GROUP_ID, TARGET_DATE, TARGET_LESSON)))
                .andExpect(status().isConflict());
    }

    // ---------- 4. Delete on past date → 200/204 (D-22) ----------

    @Test
    void delete_pastDate_succeeds() throws Exception {
        LocalDate pastDate = LocalDate.of(2026, 2, 9); // Monday, semester already started
        OneOffLesson saved = persistOneOff(GROUP_ID, pastDate, (short) 2);

        mockMvc.perform(withHeadmanHeaders(delete("/schedule/one-off-lessons/{id}", saved.getId())))
                .andExpect(status().isNoContent());

        assertThat(oneOffLessonRepository.findById(saved.getId())).isEmpty();
    }

    // ---------- 5. Headman of wrong group → 403 (D-11) ----------

    @Test
    void create_headmanOfOtherGroup_returns403() throws Exception {
        mockMvc.perform(withHeadmanHeaders(post("/schedule/one-off-lessons"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createRequestJson(OTHER_GROUP_ID, TARGET_DATE, TARGET_LESSON)))
                .andExpect(status().isForbidden());

        assertThat(oneOffLessonRepository.count()).isZero();
    }

    // ---------- 6. List returns only requested group ----------

    @Test
    void list_returnsOnlyGroupLessons() throws Exception {
        persistOneOff(GROUP_ID, LocalDate.of(2026, 4, 20), (short) 1);
        persistOneOff(GROUP_ID, LocalDate.of(2026, 4, 21), (short) 2);
        persistOneOff(OTHER_GROUP_ID, LocalDate.of(2026, 4, 22), (short) 3);

        mockMvc.perform(withHeadmanHeaders(get("/schedule/one-off-lessons")
                        .param("groupId", GROUP_ID.toString())
                        .param("dateFrom", "2026-04-01")
                        .param("dateTo", "2026-04-30")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$._embedded.oneOffLessonResponseList", hasSize(2)))
                .andExpect(jsonPath("$._embedded.oneOffLessonResponseList[0].groupId",
                        is(GROUP_ID.intValue())));
    }
}
