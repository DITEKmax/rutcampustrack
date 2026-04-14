package ru.rutcampustrack.schedule.integration;

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
import ru.rutcampustrack.schedule.contract.dto.item.CreateScheduleItemRequest;
import ru.rutcampustrack.schedule.contract.enums.WeekType;
import ru.rutcampustrack.schedule.exception.AcademicServiceUnavailableException;
import ru.rutcampustrack.schedule.exception.ResourceNotFoundException;
import ru.rutcampustrack.schedule.grpc.AcademicGrpcClient;
import ru.rutcampustrack.schedule.item.entity.ScheduleItem;
import ru.rutcampustrack.schedule.item.repository.ScheduleItemRepository;
import ru.rutcampustrack.schedule.lesson.repository.LessonRepository;

import java.time.LocalTime;
import java.time.OffsetDateTime;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for ScheduleItem CRUD API (TMPL-01 through TMPL-05).
 * Uses MockMvc + @MockitoBean AcademicGrpcClient (D-04 — mock the wrapper, not the stub).
 */
@AutoConfigureMockMvc
class ScheduleItemApiTest extends AbstractScheduleIntegrationTest {

    private static final Long GROUP_ID = 1L;
    private static final Long SEMESTER_ID = 10L;
    private static final Long SUBJECT_ID = 100L;
    private static final Long TEACHER_ID = 200L;
    private static final Long USER_ID = 42L;

    @MockitoBean
    AcademicGrpcClient academicGrpcClient;

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ScheduleItemRepository scheduleItemRepository;

    @Autowired
    LessonRepository lessonRepository;

    @Autowired
    ObjectMapper objectMapper;

    private static final SemesterResponse MOCK_SEMESTER = SemesterResponse.newBuilder()
            .setId(SEMESTER_ID)
            .setName("Spring 2026")
            .setDateFrom("2026-02-02")
            .setDateTo("2026-06-30")
            .setFirstWeekType("odd")
            .build();

    @BeforeEach
    void setUp() {
        lessonRepository.deleteAll();
        scheduleItemRepository.deleteAll();
        lessonNumberCounter = 1;
        // Configure mocks for happy path
        when(academicGrpcClient.validateGroup(GROUP_ID))
                .thenReturn(GroupResponse.newBuilder()
                        .setId(GROUP_ID)
                        .setName("Test Group")
                        // 58-04: proto field `code` удалён (reserved 3).
                        .setIsActive(true)
                        .build());
        when(academicGrpcClient.getActiveSemester()).thenReturn(MOCK_SEMESTER);
        when(academicGrpcClient.parseSemesterFirstWeekType(MOCK_SEMESTER))
                .thenReturn(WeekType.ODD);
        when(academicGrpcClient.isHeadman(USER_ID, GROUP_ID)).thenReturn(true);
    }

    // ---------- Header helpers ----------

    private MockHttpServletRequestBuilder withHeadmanHeaders(MockHttpServletRequestBuilder builder) {
        return builder
                .header("X-User-Id", USER_ID)
                .header("X-User-Role", "STUDENT")
                .header("X-Group-Id", GROUP_ID)
                .header("X-Is-Headman", "true");
    }

    private MockHttpServletRequestBuilder withAdminHeaders(MockHttpServletRequestBuilder builder) {
        return builder
                .header("X-User-Id", 999L)
                .header("X-User-Role", "ADMIN");
    }

    private MockHttpServletRequestBuilder withStudentHeaders(MockHttpServletRequestBuilder builder) {
        return builder
                .header("X-User-Id", 55L)
                .header("X-User-Role", "STUDENT")
                .header("X-Group-Id", GROUP_ID)
                .header("X-Is-Headman", "false");
    }

    // ---------- Request builders ----------

    private String createRequestJson() throws Exception {
        return objectMapper.writeValueAsString(new CreateScheduleItemRequest(
                GROUP_ID, SUBJECT_ID, TEACHER_ID, SEMESTER_ID,
                (short) 1, (short) 1, LocalTime.of(8, 30), LocalTime.of(10, 0),
                WeekType.ALL, "A-101"
        ));
    }

    private int lessonNumberCounter = 1;

    private ScheduleItem saveActiveItem() {
        ScheduleItem item = new ScheduleItem();
        item.setGroupId(GROUP_ID);
        item.setSubjectId(SUBJECT_ID);
        item.setTeacherId(TEACHER_ID);
        item.setSemesterId(SEMESTER_ID);
        item.setDayOfWeek((short) 1);
        item.setLessonNumber((short) lessonNumberCounter++);
        item.setStartTime(LocalTime.of(8, 30));
        item.setEndTime(LocalTime.of(10, 0));
        item.setWeekType(WeekType.ALL);
        item.setRoom("A-101");
        item.setActive(true);
        item.setCreatedAt(OffsetDateTime.now());
        return scheduleItemRepository.save(item);
    }

    // ---------- TMPL-01: Create ----------

    @Test
    void createTemplate_headman_success() throws Exception {
        mockMvc.perform(withHeadmanHeaders(post("/schedule/items"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createRequestJson()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.groupId", is(GROUP_ID.intValue())))
                .andExpect(jsonPath("$.subjectId", is(SUBJECT_ID.intValue())))
                .andExpect(jsonPath("$.room", is("A-101")))
                .andExpect(jsonPath("$.active", is(true)))
                .andExpect(jsonPath("$._links.self.href", containsString("/schedule/items/")));

        org.assertj.core.api.Assertions.assertThat(scheduleItemRepository.count()).isEqualTo(1);
    }

    @Test
    void createTemplate_admin_success() throws Exception {
        // ADMIN bypasses headman check — no isHeadman mock needed
        mockMvc.perform(withAdminHeaders(post("/schedule/items"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createRequestJson()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.groupId", is(GROUP_ID.intValue())));
    }

    @Test
    void createTemplate_nonHeadmanStudent_returns403() throws Exception {
        mockMvc.perform(withStudentHeaders(post("/schedule/items"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createRequestJson()))
                .andExpect(status().isForbidden());
    }

    // ---------- TMPL-02: Update ----------

    @Test
    void updateTemplate_headman_success() throws Exception {
        ScheduleItem saved = saveActiveItem();

        String updateJson = objectMapper.writeValueAsString(
                new ru.rutcampustrack.schedule.contract.dto.item.UpdateScheduleItemRequest(
                        SUBJECT_ID, TEACHER_ID,
                        (short) 2, (short) 2,
                        LocalTime.of(10, 10), LocalTime.of(11, 40),
                        WeekType.ALL, "B-202"
                )
        );

        mockMvc.perform(withHeadmanHeaders(put("/schedule/items/{id}", saved.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.room", is("B-202")))
                .andExpect(jsonPath("$.lessonNumber", is(2)))
                .andExpect(jsonPath("$.groupId", is(GROUP_ID.intValue())))
                .andExpect(jsonPath("$.semesterId", is(SEMESTER_ID.intValue())));
    }

    // ---------- TMPL-03: Soft delete ----------

    @Test
    void deleteTemplate_softDelete() throws Exception {
        ScheduleItem saved = saveActiveItem();

        mockMvc.perform(withHeadmanHeaders(delete("/schedule/items/{id}", saved.getId())))
                .andExpect(status().isNoContent());

        ScheduleItem fromDb = scheduleItemRepository.findById(saved.getId()).orElseThrow();
        org.assertj.core.api.Assertions.assertThat(fromDb.isActive()).isFalse();
    }

    // ---------- TMPL-04: List active only ----------

    @Test
    void listTemplates_returnsActiveOnly() throws Exception {
        // 2 active
        saveActiveItem();
        saveActiveItem();
        // 1 deactivated
        ScheduleItem inactive = saveActiveItem();
        inactive.setActive(false);
        scheduleItemRepository.save(inactive);

        mockMvc.perform(withHeadmanHeaders(
                        get("/schedule/items")
                                .param("groupId", GROUP_ID.toString())
                                .param("semesterId", SEMESTER_ID.toString())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements", is(2)));
    }

    // ---------- TMPL-05: gRPC validation ----------

    @Test
    void createTemplate_grpcFailure_returns503() throws Exception {
        when(academicGrpcClient.validateGroup(GROUP_ID))
                .thenThrow(new AcademicServiceUnavailableException("gRPC down"));

        mockMvc.perform(withHeadmanHeaders(post("/schedule/items"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createRequestJson()))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    void createTemplate_invalidGroupId_returns404() throws Exception {
        String requestJson = objectMapper.writeValueAsString(new CreateScheduleItemRequest(
                999L, SUBJECT_ID, TEACHER_ID, SEMESTER_ID,
                (short) 1, (short) 1, LocalTime.of(8, 30), LocalTime.of(10, 0),
                WeekType.ALL, "A-101"
        ));
        when(academicGrpcClient.isHeadman(USER_ID, 999L)).thenReturn(true);
        when(academicGrpcClient.validateGroup(999L))
                .thenThrow(new ResourceNotFoundException("Group", "id", 999L));

        mockMvc.perform(withHeadmanHeaders(post("/schedule/items"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isNotFound());
    }
}
