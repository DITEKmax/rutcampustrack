package ru.rutcampustrack.schedule.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import ru.rutcampustrack.academic.grpc.AssignmentInfo;
import ru.rutcampustrack.academic.grpc.GroupResponse;
import ru.rutcampustrack.academic.grpc.SemesterResponse;
import ru.rutcampustrack.schedule.contract.dto.item.CreateScheduleItemRequest;
import ru.rutcampustrack.schedule.contract.dto.item.UpdateScheduleItemRequest;
import ru.rutcampustrack.schedule.contract.enums.LessonStatus;
import ru.rutcampustrack.schedule.contract.enums.WeekType;
import ru.rutcampustrack.schedule.grpc.AcademicGrpcClient;
import ru.rutcampustrack.schedule.item.repository.ScheduleItemRepository;
import ru.rutcampustrack.schedule.lesson.entity.Lesson;
import ru.rutcampustrack.schedule.lesson.repository.LessonRepository;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

/**
 * Integration tests proving the full lesson auto-generation flow (LSSN-01, LSSN-02, D-01, D-06,
 * D-07).
 *
 * <p>Uses a short 3-week semester (Feb 2–22, 2026) for predictable lesson counts.
 *
 * <p>dayOfWeek values stored in DB (aligned with java.time.DayOfWeek: 1=Mon..7=Sun): 2 => TUESDAY 3
 * => WEDNESDAY
 *
 * <p>ISO-week parity of in-range dates (inverted algorithm — firstWeekType ignored): Feb 3 (Tue) /
 * Feb 4 (Wed) → ISO week 6 (even) → mapped to ODD Feb 10 (Tue) / Feb 11 (Wed) → ISO week 7 (odd) →
 * mapped to EVEN Feb 17 (Tue) / Feb 18 (Wed) → ISO week 8 (even) → mapped to ODD
 *
 * <p>Tuesdays (dayOfWeek=2) total 3; ODD-template matches only Feb 10 (1 date). Wednesdays
 * (dayOfWeek=3) total 3.
 */
@AutoConfigureMockMvc
class LessonGenerationIT extends AbstractScheduleIntegrationTest {

    private static final Long GROUP_ID = 2L;
    private static final Long SEMESTER_ID = 10L;
    private static final Long SUBJECT_ID = 101L;
    private static final Long USER_ID = 43L;
    private static final Long ASSIGNMENT_ID = 501L;
    private static final Long TEACHER_ID = 700L;

    // Short 3-week semester: Feb 2 (Mon) to Feb 22 (Sun) 2026
    // firstWeekType=ODD => week 0 (Feb 2-8) = ODD, week 1 (Feb 9-15) = EVEN, week 2 (Feb 16-22) =
    // ODD
    private static final SemesterResponse MOCK_SEMESTER =
            SemesterResponse.newBuilder()
                    .setId(SEMESTER_ID)
                    .setName("Test Spring 2026")
                    .setDateFrom("2026-02-02")
                    .setDateTo("2026-02-22")
                    .setFirstWeekType("odd")
                    .build();

    @MockitoBean AcademicGrpcClient academicGrpcClient;

    @Autowired MockMvc mockMvc;

    @Autowired ObjectMapper objectMapper;

    @Autowired LessonRepository lessonRepository;

    @Autowired ScheduleItemRepository scheduleItemRepository;

    @BeforeEach
    void setUp() {
        resetScheduleData();
        when(academicGrpcClient.getSemesterArchiveAuthorityState(SEMESTER_ID))
                .thenReturn(
                        ru.rutcampustrack.academic.grpc.SemesterStateResponse.newBuilder()
                                .setId(SEMESTER_ID)
                                .build());

        when(academicGrpcClient.getActiveSemester()).thenReturn(MOCK_SEMESTER);
        when(academicGrpcClient.parseSemesterFirstWeekType(MOCK_SEMESTER)).thenReturn(WeekType.ODD);
        when(academicGrpcClient.validateGroup(anyLong()))
                .thenReturn(
                        GroupResponse.newBuilder()
                                .setId(GROUP_ID)
                                .setName("G1")
                                .setIsActive(true)
                                .build());
        when(academicGrpcClient.isHeadman(anyLong(), anyLong())).thenReturn(true);
        when(academicGrpcClient.getAssignmentsByIds(List.of(ASSIGNMENT_ID)))
                .thenReturn(
                        List.of(
                                AssignmentInfo.newBuilder()
                                        .setId(ASSIGNMENT_ID)
                                        .setTeacherId(TEACHER_ID)
                                        .setSubjectId(SUBJECT_ID)
                                        .setGroupId(GROUP_ID)
                                        .setSemesterId(SEMESTER_ID)
                                        .setLessonType("lecture")
                                        .setValidFrom("2026-02-02")
                                        .setValidUntilExclusive("2026-02-23")
                                        .build()));
    }

    // ---------- Header helpers ----------

    private MockHttpServletRequestBuilder withHeadmanHeaders(
            MockHttpServletRequestBuilder builder) {
        return builder.header("X-User-Id", USER_ID)
                .header("X-User-Role", "STUDENT")
                .header("X-Group-Id", GROUP_ID)
                .header("X-Is-Headman", "true");
    }

    // ---------- Request helpers ----------

    private String createRequestJson(short dayOfWeek, WeekType weekType) throws Exception {
        return objectMapper.writeValueAsString(
                new CreateScheduleItemRequest(
                        ASSIGNMENT_ID,
                        GROUP_ID,
                        SUBJECT_ID,
                        SEMESTER_ID,
                        dayOfWeek,
                        (short) 1,
                        LocalTime.of(8, 30),
                        LocalTime.of(9, 50),
                        weekType,
                        "A-101"));
    }

    private String updateRequestJson(short dayOfWeek, WeekType weekType, String room)
            throws Exception {
        return objectMapper.writeValueAsString(
                new UpdateScheduleItemRequest(
                        SUBJECT_ID,
                        dayOfWeek,
                        (short) 1,
                        LocalTime.of(8, 30),
                        LocalTime.of(9, 50),
                        weekType,
                        room));
    }

    /** Creates a schedule item via POST and returns the created item's ID from JSON response. */
    private Long createItemViaApi(short dayOfWeek, WeekType weekType) throws Exception {
        MvcResult result =
                mockMvc.perform(
                                withHeadmanHeaders(post("/schedule/items"))
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .header("Idempotency-Key", UUID.randomUUID().toString())
                                        .content(createRequestJson(dayOfWeek, weekType)))
                        .andExpect(status().isCreated())
                        .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    // ---------- LSSN-01: Create generates lessons ----------

    /**
     * POST creates template AND generates all matching lessons in the same transaction (D-01,
     * LSSN-01). dayOfWeek=2 => TUESDAY. Feb 2-22 Tuesdays: Feb 3, 10, 17 = 3 lessons for
     * weekType=ALL.
     */
    @Test
    void createGeneratesLessons() throws Exception {
        createItemViaApi((short) 2, WeekType.ALL);

        List<Lesson> lessons = lessonRepository.findAll();
        assertThat(lessons).hasSize(3);
        assertThat(lessons).allMatch(l -> l.getStatus() == LessonStatus.PLANNED);
        assertThat(lessons)
                .extracting(Lesson::getDate)
                .containsExactlyInAnyOrder(
                        LocalDate.of(2026, 2, 3),
                        LocalDate.of(2026, 2, 10),
                        LocalDate.of(2026, 2, 17));
    }

    /**
     * Week parity respected: ODD template selects even-ISO weeks (inverted mapping, LSSN-02).
     * dayOfWeek=2 => TUESDAY. Tuesdays: Feb 3 (ISO 6→ODD), Feb 10 (ISO 7→EVEN), Feb 17 (ISO 8→ODD).
     * With weekType=ODD: expected lessons = Feb 3, Feb 17 (2 lessons).
     */
    @Test
    void createGeneratesLessonsOddWeeks() throws Exception {
        createItemViaApi((short) 2, WeekType.ODD);

        List<Lesson> lessons = lessonRepository.findAll();
        assertThat(lessons).hasSize(2);
        assertThat(lessons)
                .extracting(Lesson::getDate)
                .containsExactlyInAnyOrder(LocalDate.of(2026, 2, 3), LocalDate.of(2026, 2, 17));
    }

    // ---------- scheduleItemId association ----------

    /** All generated lessons reference the correct schedule item (LSSN-01 data correctness). */
    @Test
    void generatedLessonsHaveCorrectScheduleItemId() throws Exception {
        Long itemId = createItemViaApi((short) 1, WeekType.ALL);

        List<Lesson> lessons = lessonRepository.findAll();
        assertThat(lessons).isNotEmpty();
        assertThat(lessons).allMatch(l -> itemId.equals(l.getScheduleItemId()));
    }

    // ---------- Direct DB save (bypass generation) still works ----------

}
