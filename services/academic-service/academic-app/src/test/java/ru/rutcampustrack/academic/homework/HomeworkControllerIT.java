package ru.rutcampustrack.academic.homework;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.dao.DataAccessException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import ru.rutcampustrack.academic.contract.dto.homework.CreateHomeworkRequest;
import ru.rutcampustrack.academic.exception.ScheduleServiceUnavailableException;
import ru.rutcampustrack.academic.grpc.ScheduleGrpcClient;
import ru.rutcampustrack.academic.integration.AbstractAcademicIntegrationTest;
import ru.rutcampustrack.academic.integration.AcademicIdentityIntegrationFixture.SelectedActor;
import ru.rutcampustrack.academic.contract.dto.user.CreateUserRequest;
import ru.rutcampustrack.academic.contract.enums.UserRole;
import ru.rutcampustrack.auth.session.model.AuthRole;
import ru.rutcampustrack.schedule.grpc.HomeworkBindingResponse;
import ru.rutcampustrack.schedule.grpc.HomeworkBindingState;
import ru.rutcampustrack.schedule.grpc.LessonInfo;
import ru.rutcampustrack.schedule.grpc.LessonResponse;

/**
 * Phase 61-03: MockMvc integration tests для {@code POST /academic/homeworks}.
 *
 * <p>Покрывает D-06 (HEADMAN-only), D-04 (lesson must exist + subject match). ScheduleGrpcClient
 * замокан — тесты не поднимают реальный schedule-service.
 *
 * <p>The common owned two-database fixture resets committed setup before each test. Auth RPCs
 * execute outside local Academic transactions.
 */
@AutoConfigureMockMvc
class HomeworkControllerIT extends AbstractAcademicIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private PlatformTransactionManager transactionManager;

    @MockitoBean private ScheduleGrpcClient scheduleGrpcClient;

    // Seed data
    private SelectedActor studentActor, headmanActor;
    private Long seedStudentId; // отдельная canonical STUDENT роль без HEADMAN
    private Long seedHeadmanId; // canonical HEADMAN из fresh owned seed
    private Long seedAdminId;
    private Long seedGroupId;
    private Long subjectId;
    private Long semesterId;
    private UUID publicationRequestKey;
    private LocalDate fixtureToday;

    @BeforeEach
    void setUp() {
        fixtureToday = LocalDate.now(java.time.ZoneId.of("Europe/Moscow"));
        seedGroupId = identityFixture.canonical().groupId();
        seedAdminId = identityFixture.canonical().adminId();
        seedHeadmanId = identityFixture.canonical().studentId();
        var administrator = identityFixture.select(seedAdminId, AuthRole.ADMIN);
        seedStudentId =
                identityFixture
                        .seedUser(
                                new CreateUserRequest(
                                        "Обычный",
                                        "Студент",
                                        null,
                                        UserRole.STUDENT,
                                        seedGroupId,
                                        null,
                                        610000005201L),
                                administrator)
                        .userId();
        studentActor = identityFixture.select(seedStudentId, AuthRole.STUDENT);
        headmanActor = identityFixture.select(seedHeadmanId, AuthRole.HEADMAN);
        publicationRequestKey = null;
    }

    private void ensureSubjectAndSemester() {
        new TransactionTemplate(transactionManager)
                .executeWithoutResult(
                        status -> {
                            // Subject для группы (Phase 60-01 V12: subjects.group_id NOT NULL)
                            subjectId =
                                    jdbcTemplate.queryForObject(
                                            "INSERT INTO subjects (name, type, group_id) VALUES"
                                                + " ('HW Test Subject', 'lecture', ?) RETURNING id",
                                            Long.class,
                                            seedGroupId);
                            jdbcTemplate.update(
                                    "INSERT INTO subject_lesson_types(subject_id,lesson_type)"
                                            + " VALUES(?,'lecture')",
                                    subjectId);

                            // Деактивируем активные семестры чтобы не конфликтовать с exclusion
                            // constraint
                            jdbcTemplate.update(
                                    "UPDATE semesters SET is_active = false WHERE is_active ="
                                            + " true");

                            semesterId = insertHomeworkSemester("HW Ctrl IT Semester");
                        });
    }

    private void ensureHeadmanUser() {
        seedHeadmanId = identityFixture.canonical().studentId();
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor signedStudentRole() {
        return request -> {
            if (request.getHeader("X-Internal-Token") != null) return request;
            long actor = Long.parseLong(request.getHeader("X-User-Id"));
            boolean headman = Boolean.parseBoolean(request.getHeader("X-Is-Headman"));
            SelectedActor selected;
            if (actor == seedHeadmanId && headman) selected = headmanActor;
            else if (actor == seedStudentId && !headman) selected = studentActor;
            else
                throw new IllegalArgumentException("Only the selected fixture actors are admitted");
            return identityFixture.signed(selected).postProcessRequest(request);
        };
    }

    private Long insertHomeworkSemester(String name) {
        boolean spring = fixtureToday.getMonthValue() <= 6;
        return jdbcTemplate.queryForObject(
                """
INSERT INTO semesters(name,date_from,date_to,is_active,semester_type,academic_year,created_at)
VALUES(?,?,?,FALSE,?,?,NOW()) RETURNING id
""",
                Long.class,
                name,
                fixtureToday.minusDays(1),
                fixtureToday.plusDays(30),
                spring ? "SPRING" : "AUTUMN",
                fixtureToday.getYear() - (spring ? 1 : 0));
    }

    private String requestBody(LocalDate date) throws Exception {
        return requestBody(date, UUID.fromString("11111111-1111-4111-8111-111111111111"));
    }

    private String requestBody(LocalDate date, UUID requestKey) throws Exception {
        return objectMapper.writeValueAsString(
                new CreateHomeworkRequest(
                        "IT HW Title",
                        "description",
                        null,
                        subjectId,
                        seedGroupId,
                        semesterId,
                        date,
                        1,
                        requestKey));
    }

    private void stubLessonFound() {
        stubLessonFound(fixtureToday.plusDays(2));
    }

    private void stubLessonFound(LocalDate lessonDate) {
        when(scheduleGrpcClient.resolveLesson(anyLong(), any(), anyInt()))
                .thenReturn(
                        Optional.of(
                                LessonResponse.newBuilder()
                                        .setGroupId(seedGroupId)
                                        .setSubjectId(subjectId)
                                        .setSemesterId(semesterId)
                                        .setLessonNumber(1)
                                        .setDate(lessonDate.toString())
                                        .setOccurrenceId(7001L)
                                        .setRevision(1L)
                                        .setStatus("planned")
                                        .build()));
        when(scheduleGrpcClient.reserveHomeworkBinding(
                        anyLong(), any(UUID.class), anyLong(), any(byte[].class)))
                .thenReturn(
                        homeworkBinding(
                                7101L, null, HomeworkBindingState.HOMEWORK_BINDING_STATE_PENDING));
        when(scheduleGrpcClient.confirmHomeworkBinding(anyLong(), anyLong(), any(UUID.class)))
                .thenAnswer(
                        invocation ->
                                homeworkBinding(
                                        (Long) invocation.getArgument(0),
                                        (Long) invocation.getArgument(1),
                                        HomeworkBindingState.HOMEWORK_BINDING_STATE_ACTIVE));
    }

    private HomeworkBindingResponse homeworkBinding(
            long bindingId, Long homeworkId, HomeworkBindingState state) {
        return homeworkBinding(bindingId, homeworkId, state, fixtureToday.plusDays(2));
    }

    private HomeworkBindingResponse homeworkBinding(
            long bindingId, Long homeworkId, HomeworkBindingState state, LocalDate lessonDate) {
        LessonInfo current =
                LessonInfo.newBuilder()
                        .setLessonId(7002L)
                        .setGroupId(seedGroupId)
                        .setSubjectId(subjectId)
                        .setStartsAt(lessonDate + "T09:00")
                        .setLessonNumber(1)
                        .setDate(lessonDate.toString())
                        .setOccurrenceId(7001L)
                        .setAssignmentId(1L)
                        .setSemesterId(semesterId)
                        .setTeacherId(1L)
                        .setLessonType("lecture")
                        .setGeneration(1L)
                        .setRevision(1L)
                        .setStatus("planned")
                        .build();
        HomeworkBindingResponse.Builder response =
                HomeworkBindingResponse.newBuilder()
                        .setBindingId(bindingId)
                        .setOccurrenceId(7001L)
                        .setCurrentLesson(current)
                        .setState(state)
                        .setBindingMode("LESSON")
                        .setRevision(
                                state == HomeworkBindingState.HOMEWORK_BINDING_STATE_ACTIVE
                                        ? 2L
                                        : 1L)
                        .setGroupId(seedGroupId)
                        .setSubjectId(subjectId)
                        .setSemesterId(semesterId)
                        .setDate(lessonDate.toString())
                        .setLessonNumber(1);
        if (homeworkId != null) {
            response.setHomeworkId(homeworkId);
        }
        return response.build();
    }

    private void ensurePublicationSubjectAndSemester() {
        new TransactionTemplate(transactionManager)
                .executeWithoutResult(
                        status -> {
                            subjectId =
                                    jdbcTemplate.queryForObject(
                                            "INSERT INTO subjects (name, type, group_id) VALUES (?,"
                                                    + " 'lecture', ?) RETURNING id",
                                            Long.class,
                                            "HW Publication Subject " + UUID.randomUUID(),
                                            seedGroupId);
                            jdbcTemplate.update(
                                    "INSERT INTO subject_lesson_types (subject_id, lesson_type)"
                                            + " VALUES (?, 'lecture')",
                                    subjectId);
                            semesterId =
                                    insertHomeworkSemester(
                                            "HW Publication Semester " + UUID.randomUUID());
                        });
    }

    private void cleanupPublicationFixture() {
        // AcademicFixtureResetListener discards the isolated fixture before the next scenario.
    }

    // =========================================================================
    // D-06: ADMIN → 403
    // =========================================================================

    @Test
    void createHomework_returns403_forAdmin() throws Exception {
        ensureSubjectAndSemester();
        stubLessonFound();

        mockMvc.perform(
                        post("/academic/homeworks")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody(fixtureToday.plusDays(2)))
                                .header("X-User-Id", seedAdminId)
                                .header("X-User-Role", "ADMIN")
                                .with(signedAdmin())
                                .header("X-Group-Id", "")
                                .header("X-Is-Headman", "false")
                                .with(signedStudentRole()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status", is(403)));
    }

    // =========================================================================
    // D-06: плейн STUDENT (is_headman=false) → 403
    // =========================================================================

    @Test
    void createHomework_returns403_forPlainStudent() throws Exception {
        ensureSubjectAndSemester();
        stubLessonFound();

        mockMvc.perform(
                        post("/academic/homeworks")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody(fixtureToday.plusDays(2)))
                                .header("X-User-Id", seedStudentId)
                                .header("X-User-Role", "STUDENT")
                                .header("X-Group-Id", seedGroupId)
                                .header("X-Is-Headman", "false")
                                .with(signedStudentRole()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status", is(403)));
    }

    // =========================================================================
    // D-06 happy path: HEADMAN → 201
    // =========================================================================

    @Test
    void createHomework_returns201_forHeadman() throws Exception {
        publicationRequestKey = UUID.randomUUID();
        LocalDate lessonDate = fixtureToday.plusDays(2);
        ensurePublicationSubjectAndSemester();
        ensureHeadmanUser();
        try {
            stubLessonFound(lessonDate);

            mockMvc.perform(
                            post("/academic/homeworks")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(requestBody(lessonDate, publicationRequestKey))
                                    .header("X-User-Id", seedHeadmanId)
                                    .header("X-User-Role", "STUDENT")
                                    .header("X-Group-Id", seedGroupId)
                                    .header("X-Is-Headman", "true")
                                    .with(signedStudentRole()))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.title", is("IT HW Title")))
                    .andExpect(jsonPath("$.lessonNumber", is(1)))
                    .andExpect(jsonPath("$.publishedBy", is(seedHeadmanId.intValue())));
        } finally {
            cleanupPublicationFixture();
        }
    }

    // =========================================================================
    // D-04: lesson not found → 400
    // =========================================================================

    @Test
    void createHomework_returns400_whenLessonNotInSchedule() throws Exception {
        ensureSubjectAndSemester();
        ensureHeadmanUser();
        when(scheduleGrpcClient.resolveLesson(anyLong(), any(), anyInt()))
                .thenReturn(Optional.empty());

        mockMvc.perform(
                        post("/academic/homeworks")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody(fixtureToday.plusDays(2)))
                                .header("X-User-Id", seedHeadmanId)
                                .header("X-User-Role", "STUDENT")
                                .header("X-Group-Id", seedGroupId)
                                .header("X-Is-Headman", "true")
                                .with(signedStudentRole()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.detail", containsString("расписании")));
    }

    // =========================================================================
    // Sanity: ≥2 ДЗ на одну пару создаются подряд (нет UNIQUE constraint)
    // =========================================================================

    @Test
    void createHomework_allowsMultiplePerLesson() throws Exception {
        publicationRequestKey = UUID.randomUUID();
        UUID secondRequestKey = UUID.randomUUID();
        LocalDate lessonDate = fixtureToday.plusDays(2);
        ensurePublicationSubjectAndSemester();
        ensureHeadmanUser();
        try {
            stubLessonFound(lessonDate);
            AtomicLong nextBindingId = new AtomicLong(7101L);
            when(scheduleGrpcClient.reserveHomeworkBinding(
                            anyLong(), any(UUID.class), anyLong(), any(byte[].class)))
                    .thenAnswer(
                            invocation ->
                                    homeworkBinding(
                                            nextBindingId.getAndIncrement(),
                                            null,
                                            HomeworkBindingState.HOMEWORK_BINDING_STATE_PENDING,
                                            lessonDate));
            when(scheduleGrpcClient.confirmHomeworkBinding(anyLong(), anyLong(), any(UUID.class)))
                    .thenAnswer(
                            invocation ->
                                    homeworkBinding(
                                            (Long) invocation.getArgument(0),
                                            (Long) invocation.getArgument(1),
                                            HomeworkBindingState.HOMEWORK_BINDING_STATE_ACTIVE,
                                            lessonDate));

            mockMvc.perform(
                            post("/academic/homeworks")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(requestBody(lessonDate, publicationRequestKey))
                                    .header("X-User-Id", seedHeadmanId)
                                    .header("X-User-Role", "STUDENT")
                                    .header("X-Group-Id", seedGroupId)
                                    .header("X-Is-Headman", "true")
                                    .with(signedStudentRole()))
                    .andExpect(status().isCreated());

            mockMvc.perform(
                            post("/academic/homeworks")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(requestBody(lessonDate, secondRequestKey))
                                    .header("X-User-Id", seedHeadmanId)
                                    .header("X-User-Role", "STUDENT")
                                    .header("X-Group-Id", seedGroupId)
                                    .header("X-Is-Headman", "true")
                                    .with(signedStudentRole()))
                    .andExpect(status().isCreated());

            assertThat(
                    jdbcTemplate.queryForObject(
                            "SELECT COUNT(*) FROM homeworks WHERE subject_id = ?",
                            Integer.class,
                            subjectId),
                    is(2));
        } finally {
            cleanupPublicationFixture();
        }
    }

    // =========================================================================
    // Publication protocol: durable PENDING → 202/hidden → same-key ACTIVE/outbox
    // =========================================================================

    @Test
    void publicationPendingIsDurableHiddenAndSameKeyRetryActivatesOnce() throws Exception {
        publicationRequestKey = UUID.randomUUID();
        LocalDate lessonDate = fixtureToday.plusDays(2);
        ensurePublicationSubjectAndSemester();
        ensureHeadmanUser();
        try {
            stubLessonFound(lessonDate);
            when(scheduleGrpcClient.confirmHomeworkBinding(anyLong(), anyLong(), any(UUID.class)))
                    .thenThrow(new ScheduleServiceUnavailableException("temporary schedule outage"))
                    .thenAnswer(
                            invocation ->
                                    homeworkBinding(
                                            7101L,
                                            (Long) invocation.getArgument(1),
                                            HomeworkBindingState.HOMEWORK_BINDING_STATE_ACTIVE,
                                            lessonDate));

            String body =
                    requestBody(lessonDate, publicationRequestKey)
                            .replace("IT HW Title", "Pending IT Homework");
            mockMvc.perform(
                            post("/academic/homeworks")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(body)
                                    .header("X-User-Id", seedHeadmanId)
                                    .header("X-User-Role", "STUDENT")
                                    .header("X-Group-Id", seedGroupId)
                                    .header("X-Is-Headman", "true")
                                    .with(signedStudentRole()))
                    .andExpect(status().isAccepted())
                    .andExpect(jsonPath("$.state", is("PENDING")))
                    .andExpect(jsonPath("$.requestKey", is(publicationRequestKey.toString())));

            Long homeworkId =
                    jdbcTemplate.queryForObject(
                            "SELECT id FROM homeworks WHERE request_key = ?",
                            Long.class,
                            publicationRequestKey);
            assertThat(
                    jdbcTemplate.queryForObject(
                            "SELECT COUNT(*) FROM homeworks "
                                    + "WHERE request_key = ? AND publication_state = 'PENDING'",
                            Integer.class,
                            publicationRequestKey),
                    is(1));

            mockMvc.perform(
                            get("/academic/homeworks/{id}", homeworkId)
                                    .header("X-User-Id", seedStudentId)
                                    .header("X-User-Role", "STUDENT")
                                    .header("X-Group-Id", seedGroupId)
                                    .header("X-Is-Headman", "false")
                                    .with(signedStudentRole()))
                    .andExpect(status().isNotFound());
            String hiddenList =
                    mockMvc.perform(
                                    get("/academic/homeworks")
                                            .param("groupId", seedGroupId.toString())
                                            .param("semesterId", semesterId.toString())
                                            .header("X-User-Id", seedStudentId)
                                            .header("X-User-Role", "STUDENT")
                                            .header("X-Group-Id", seedGroupId)
                                            .header("X-Is-Headman", "false")
                                            .with(signedStudentRole()))
                            .andExpect(status().isOk())
                            .andReturn()
                            .getResponse()
                            .getContentAsString();
            assertThat(hiddenList, not(containsString("Pending IT Homework")));

            mockMvc.perform(
                            post("/academic/homeworks")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(body)
                                    .header("X-User-Id", seedHeadmanId)
                                    .header("X-User-Role", "STUDENT")
                                    .header("X-Group-Id", seedGroupId)
                                    .header("X-Is-Headman", "true")
                                    .with(signedStudentRole()))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id", is(homeworkId.intValue())))
                    .andExpect(jsonPath("$.title", is("Pending IT Homework")));

            assertThat(
                    jdbcTemplate.queryForObject(
                            "SELECT publication_state FROM homeworks WHERE id = ?",
                            String.class,
                            homeworkId),
                    is("ACTIVE"));
            assertThat(
                    jdbcTemplate.queryForObject(
                            "SELECT COUNT(*) FROM academic_outbox "
                                    + "WHERE event_type = 'homework.published' "
                                    + "AND payload -> 'payload' ->> 'homework_id' = ?",
                            Integer.class,
                            homeworkId.toString()),
                    is(1));
            verify(scheduleGrpcClient, times(1))
                    .reserveHomeworkBinding(
                            anyLong(), any(UUID.class), anyLong(), any(byte[].class));
            verify(scheduleGrpcClient, times(2))
                    .confirmHomeworkBinding(anyLong(), eq(homeworkId), eq(publicationRequestKey));

            mockMvc.perform(
                            get("/academic/homeworks/{id}", homeworkId)
                                    .header("X-User-Id", seedStudentId)
                                    .header("X-User-Role", "STUDENT")
                                    .header("X-Group-Id", seedGroupId)
                                    .header("X-Is-Headman", "false")
                                    .with(signedStudentRole()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.title", is("Pending IT Homework")));
        } finally {
            cleanupPublicationFixture();
        }
    }

    @Test
    void publicationStateGuardAllowsArchiveAndRejectsPendingOrReopen() throws Exception {
        publicationRequestKey = UUID.randomUUID();
        LocalDate lessonDate = fixtureToday.plusDays(2);
        ensurePublicationSubjectAndSemester();
        ensureHeadmanUser();
        try {
            stubLessonFound(lessonDate);
            mockMvc.perform(
                            post("/academic/homeworks")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(requestBody(lessonDate, publicationRequestKey))
                                    .header("X-User-Id", seedHeadmanId)
                                    .header("X-User-Role", "STUDENT")
                                    .header("X-Group-Id", seedGroupId)
                                    .header("X-Is-Headman", "true")
                                    .with(signedStudentRole()))
                    .andExpect(status().isCreated());

            Long homeworkId =
                    jdbcTemplate.queryForObject(
                            "SELECT id FROM homeworks WHERE request_key = ?",
                            Long.class,
                            publicationRequestKey);
            assertThrows(
                    DataAccessException.class,
                    () ->
                            jdbcTemplate.update(
                                    "UPDATE homeworks SET publication_state = 'PENDING' WHERE id ="
                                            + " ?",
                                    homeworkId));

            jdbcTemplate.update(
                    "UPDATE homeworks SET publication_state = 'ARCHIVED' WHERE id = ?", homeworkId);
            assertThat(
                    jdbcTemplate.queryForObject(
                            "SELECT publication_state FROM homeworks WHERE id = ?",
                            String.class,
                            homeworkId),
                    is("ARCHIVED"));

            assertThrows(
                    DataAccessException.class,
                    () ->
                            jdbcTemplate.update(
                                    "UPDATE homeworks SET publication_state = 'ACTIVE' WHERE id ="
                                            + " ?",
                                    homeworkId));
            assertThat(
                    jdbcTemplate.queryForObject(
                            "SELECT publication_state FROM homeworks WHERE id = ?",
                            String.class,
                            homeworkId),
                    is("ARCHIVED"));
        } finally {
            cleanupPublicationFixture();
        }
    }
}
