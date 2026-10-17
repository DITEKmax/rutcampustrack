package ru.rutcampustrack.attendance.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import ru.rutcampustrack.academic.grpc.GroupMembersResponse;
import ru.rutcampustrack.academic.grpc.StudentInfo;
import ru.rutcampustrack.attendance.checkin.AttendanceDocument;
import ru.rutcampustrack.attendance.contract.enums.AttendanceSource;
import ru.rutcampustrack.attendance.contract.enums.AttendanceStatus;
import ru.rutcampustrack.attendance.grpc.AcademicGrpcClient;
import ru.rutcampustrack.schedule.grpc.LessonResponse;
import ru.rutcampustrack.shared.security.InternalJwtTestFactory;
import ru.rutcampustrack.shared.web.api.exception.ErrorResponse;

/**
 * Integration tests for all 4 attendance report endpoints. Proves RPRT-01 (lesson attendance),
 * RPRT-02 (journal grid), RPRT-03 (student stats), RPRT-04 (student records), and authorization
 * rules.
 */
class ReportIT extends AbstractAttendanceIntegrationTest {

    @Autowired private TestRestTemplate restTemplate;

    @Autowired private ObjectMapper objectMapper;
    @Autowired private ru.rutcampustrack.attendance.report.ReportService reportService;

    @Autowired private InternalJwtTestFactory internalJwtTestFactory;

    private static final Long LESSON_ID = 1L;
    private static final Long GROUP_ID = 10L;
    private static final Long SUBJECT_ID = 5L;
    private static final Long SUBJECT_ID_2 = 6L;
    private static final Long SEMESTER_ID = 1L;
    private static final Long HEADMAN_USER_ID = 100L;
    private static final Long STUDENT_USER_ID = 101L;
    private static final Long REGULAR_STUDENT_ID = 103L;

    @BeforeEach
    void setUp() {
        // Use remove (not dropCollection) to preserve indexes between tests
        mongoTemplate.remove(new Query(), AttendanceDocument.class);
        Mockito.reset(scheduleGrpcClient, academicGrpcClient, semesterCacheService);

        // Common lenient stubs — individual tests may override
        lenient().when(semesterCacheService.getActiveSemesterId()).thenReturn(SEMESTER_ID);

        lenient()
                .when(scheduleGrpcClient.getLessonById(LESSON_ID))
                .thenReturn(
                        LessonResponse.newBuilder()
                                .setId(LESSON_ID)
                                .setGroupId(GROUP_ID)
                                .setSubjectId(SUBJECT_ID)
                                .setSemesterId(SEMESTER_ID)
                                .setDate("2026-04-01")
                                .setLessonNumber(1)
                                .setStartTime("08:00")
                                .setEndTime("09:30")
                                .setStatus("closed")
                                .build());

        GroupMembersResponse reportRoster =
                GroupMembersResponse.newBuilder()
                        .setAsOfDate("2026-04-01")
                        .setSemesterId(SEMESTER_ID)
                        .addStudents(
                                StudentInfo.newBuilder()
                                        .setUserId(100L)
                                        .setDisplayName("Student A")
                                        .build())
                        .addStudents(
                                StudentInfo.newBuilder()
                                        .setUserId(101L)
                                        .setDisplayName("Student B")
                                        .build())
                        .addStudents(
                                StudentInfo.newBuilder()
                                        .setUserId(102L)
                                        .setDisplayName("Student C")
                                        .build())
                        .build();
        lenient()
                .when(
                        academicGrpcClient.getGroupMembers(
                                GROUP_ID, LocalDate.of(2026, 4, 1), SEMESTER_ID))
                .thenReturn(reportRoster);
        // Journal grid predates the concrete-lesson endpoint and keeps its
        // existing group roster call.
        lenient().when(academicGrpcClient.getGroupMembers(GROUP_ID)).thenReturn(reportRoster);

        // D-13: subject name resolution via gRPC batch call
        lenient()
                .when(academicGrpcClient.getSubjectsByIds(any()))
                .thenReturn(Map.of(SUBJECT_ID, "Mathematics", SUBJECT_ID_2, "Physics"));
        lenient()
                .when(academicGrpcClient.getSubjectDetailsByIds(any()))
                .thenReturn(
                        Map.of(
                                SUBJECT_ID, subjectDetails("Mathematics", "lecture"),
                                SUBJECT_ID_2, subjectDetails("Physics", "lab")));

        // Every lesson id passed to scheduleGrpcClient.getLessonsByIds is a valid
        // non-terminal lesson for the happy-path report tests.
        lenient()
                .when(scheduleGrpcClient.getLessonsByIds(any()))
                .thenAnswer(
                        inv -> {
                            java.util.List<Long> ids = inv.getArgument(0);
                            return ids.stream()
                                    .map(
                                            id ->
                                                    ru.rutcampustrack.schedule.grpc.LessonInfo
                                                            .newBuilder()
                                                            .setLessonId(id)
                                                            .setStatus("closed")
                                                            .build())
                                    .toList();
                        });
    }

    // -------------------------------------------------------------------------
    // Helper
    // -------------------------------------------------------------------------

    private void insertAttendance(
            Long lessonId, Long userId, Long subjectId, AttendanceStatus status, LocalDate date) {
        mongoTemplate.save(
                AttendanceDocument.builder()
                        .lessonId(lessonId)
                        .userId(userId)
                        .groupId(GROUP_ID)
                        .subjectId(subjectId)
                        .semesterId(SEMESTER_ID)
                        .lessonNumber(1)
                        .lessonDate(date)
                        .status(status)
                        .source(AttendanceSource.STUDENT_GEO)
                        .markedBy(userId)
                        .createdAt(Instant.now())
                        .updatedAt(Instant.now())
                        .build());
    }

    private void insertAttendance(Long lessonId, Long userId, AttendanceStatus status) {
        insertAttendance(lessonId, userId, SUBJECT_ID, status, LocalDate.of(2026, 4, 1));
    }

    private static AcademicGrpcClient.SubjectDetails subjectDetails(String name, String type) {
        return new AcademicGrpcClient.SubjectDetails(name, type);
    }

    // -------------------------------------------------------------------------
    // Test 1 — RPRT-01: Headman gets lesson attendance with full roster (left-join)
    // -------------------------------------------------------------------------

    @Test
    void getLessonAttendance_headman_returnsAllGroupMembers() throws Exception {
        when(academicGrpcClient.hasAssistantPermission(GROUP_ID, "VIEW_STATS")).thenReturn(true);
        when(academicGrpcClient.getHeadmanJournalSemesterState(GROUP_ID, SEMESTER_ID))
                .thenReturn(
                        ru.rutcampustrack.academic.grpc.SemesterStateResponse.newBuilder()
                                .setId(SEMESTER_ID)
                                .build());
        insertAttendance(LESSON_ID, 100L, AttendanceStatus.PRESENT);
        insertAttendance(LESSON_ID, 101L, AttendanceStatus.EXCUSED);
        // userId 102 has no record — an empty status remains empty.

        mockMvc.perform(
                        get("/attendance/reports/lesson/{lessonId}", LESSON_ID)
                                .header("X-User-Id", HEADMAN_USER_ID.toString())
                                .header("X-User-Role", "STUDENT")
                                .header("X-Group-Id", GROUP_ID.toString())
                                .header("X-Is-Headman", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entries").isArray())
                .andExpect(jsonPath("$.entries.length()").value(3))
                .andExpect(jsonPath("$.entries[?(@.userId == 100)].status").value("present"))
                .andExpect(jsonPath("$.entries[?(@.userId == 100)].symbol").value("+"))
                .andExpect(
                        jsonPath("$.entries[?(@.userId == 102)].status")
                                .value(hasItem(nullValue())))
                .andExpect(
                        jsonPath("$.entries[?(@.userId == 102)].symbol")
                                .value(hasItem(nullValue())));
    }

    @Test
    void getLessonAttendance_unknownStoredStatusRefusesWithoutChangingKnownRows() throws Exception {
        when(academicGrpcClient.hasAssistantPermission(GROUP_ID, "VIEW_STATS")).thenReturn(true);
        when(academicGrpcClient.getHeadmanJournalSemesterState(GROUP_ID, SEMESTER_ID))
                .thenReturn(
                        ru.rutcampustrack.academic.grpc.SemesterStateResponse.newBuilder()
                                .setId(SEMESTER_ID)
                                .build());
        insertAttendance(LESSON_ID, HEADMAN_USER_ID, AttendanceStatus.PRESENT);
        insertAttendance(LESSON_ID, STUDENT_USER_ID, AttendanceStatus.ABSENT);

        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(List.of(MediaType.APPLICATION_JSON, MediaType.APPLICATION_PROBLEM_JSON));
        headers.set(
                "X-Internal-Token",
                internalJwtTestFactory.validToken(
                        HEADMAN_USER_ID,
                        UUID.randomUUID(),
                        1L,
                        1L,
                        "HEADMAN",
                        "ACTIVE",
                        GROUP_ID,
                        true,
                        false));
        HttpEntity<Void> request = new HttpEntity<>(headers);
        String path = "/attendance/reports/lesson/" + LESSON_ID;

        // Use the real random-port HTTP server, including token validation and advice resolution.
        assertKnownLessonAttendance(
                restTemplate.exchange(path, HttpMethod.GET, request, String.class));

        var collection = mongoTemplate.getCollection("attendances");
        Document target =
                collection
                        .find(
                                new Document("lesson_id", LESSON_ID)
                                        .append("user_id", STUDENT_USER_ID))
                        .first();
        assertThat(target).isNotNull();
        Document identity = new Document("_id", target.get("_id"));
        String unknownCode = "future_status_http_boundary";
        try {
            assertThat(
                            collection
                                    .updateOne(
                                            identity,
                                            new Document(
                                                    "$set", new Document("status", unknownCode)))
                                    .getModifiedCount())
                    .isEqualTo(1);
            List<Document> attendanceBefore = rawDocuments("attendances");
            List<Document> outboxBefore = rawDocuments("attendance_outbox");

            ResponseEntity<String> refused =
                    restTemplate.exchange(path, HttpMethod.GET, request, String.class);

            assertThat(refused.getStatusCode())
                    .as("unknown stored AttendanceStatus is a typed HTTP conflict")
                    .isEqualTo(HttpStatus.CONFLICT);
            assertThat(refused.getHeaders().getContentType())
                    .isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
            assertThat(refused.getHeaders().getCacheControl()).isEqualTo("no-store");
            assertThat(refused.getBody())
                    .isNotNull()
                    .doesNotContain(
                            unknownCode,
                            "ConversionFailedException",
                            "IllegalArgumentException",
                            "ru.rutcampustrack",
                            "org.springframework");
            JsonNode problem = objectMapper.readTree(refused.getBody());
            assertThat(problem.path("status").asInt()).isEqualTo(409);
            assertThat(problem.path("type").asText())
                    .isEqualTo(ErrorResponse.PROBLEM_BASE + "conflict");
            assertThat(problem.path("instance").asText()).isEqualTo(path);
            assertThat(problem.path("detail").asText()).isNotBlank();
            assertThat(problem.has("entries")).isFalse();
            assertThat(rawDocuments("attendances")).isEqualTo(attendanceBefore);
            assertThat(rawDocuments("attendance_outbox")).isEqualTo(outboxBefore);
        } finally {
            collection.updateOne(
                    identity, new Document("$set", new Document("status", target.get("status"))));
        }

        assertKnownLessonAttendance(
                restTemplate.exchange(path, HttpMethod.GET, request, String.class));
    }

    private List<Document> rawDocuments(String collection) {
        return mongoTemplate
                .getCollection(collection)
                .find()
                .sort(new Document("_id", 1))
                .into(new ArrayList<>());
    }

    private void assertKnownLessonAttendance(ResponseEntity<String> response) throws Exception {
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = objectMapper.readTree(response.getBody());
        assertThat(body.path("lessonId").asLong()).isEqualTo(LESSON_ID);
        assertThat(body.path("entries").isArray()).isTrue();
        Map<Long, String> statuses = new HashMap<>();
        for (JsonNode entry : body.path("entries")) {
            statuses.put(
                    entry.path("userId").asLong(),
                    entry.path("status").isNull() ? null : entry.path("status").asText());
        }
        assertThat(statuses)
                .hasSize(3)
                .containsEntry(HEADMAN_USER_ID, "present")
                .containsEntry(STUDENT_USER_ID, "absent")
                .containsEntry(102L, null);
    }

    // -------------------------------------------------------------------------
    // Test 2 — RPRT-01 auth: Regular student (not headman) gets 403
    // -------------------------------------------------------------------------

    @Test
    void getLessonAttendance_regularStudentForbidden() throws Exception {
        mockMvc.perform(
                        get("/attendance/reports/lesson/{lessonId}", LESSON_ID)
                                .header("X-User-Id", REGULAR_STUDENT_ID.toString())
                                .header("X-User-Role", "STUDENT")
                                .header("X-Group-Id", GROUP_ID.toString())
                                .header("X-Is-Headman", "false"))
                .andExpect(status().isForbidden());
    }

    // -------------------------------------------------------------------------
    // Test 3 — RPRT-02: Journal grid shape — dates array + students array with cells
    // -------------------------------------------------------------------------

    @Test
    void getJournal_headman_returnsGridShape() throws Exception {
        // Insert attendance for 2 different dates for 2 students
        insertAttendance(
                LESSON_ID, 100L, SUBJECT_ID, AttendanceStatus.PRESENT, LocalDate.of(2026, 4, 1));
        insertAttendance(
                LESSON_ID, 101L, SUBJECT_ID, AttendanceStatus.ABSENT, LocalDate.of(2026, 4, 1));
        insertAttendance(2L, 100L, SUBJECT_ID, AttendanceStatus.PRESENT, LocalDate.of(2026, 4, 2));
        insertAttendance(2L, 101L, SUBJECT_ID, AttendanceStatus.EXCUSED, LocalDate.of(2026, 4, 2));
        // Same-week controls must not become cells in the selected subject/date interval.
        insertAttendance(3L, 100L, SUBJECT_ID_2, AttendanceStatus.ABSENT, LocalDate.of(2026, 4, 1));
        insertAttendance(4L, 100L, SUBJECT_ID, AttendanceStatus.ABSENT, LocalDate.of(2026, 3, 31));
        insertAttendance(5L, 101L, SUBJECT_ID, AttendanceStatus.PRESENT, LocalDate.of(2026, 4, 3));

        var result =
                mockMvc.perform(
                                get("/attendance/reports/journal")
                                        .param("groupId", GROUP_ID.toString())
                                        .param("subjectId", SUBJECT_ID.toString())
                                        .param("dateFrom", "2026-04-01")
                                        .param("dateTo", "2026-04-02")
                                        .header("X-User-Id", HEADMAN_USER_ID.toString())
                                        .header("X-User-Role", "STUDENT")
                                        .header("X-Group-Id", GROUP_ID.toString())
                                        .header("X-Is-Headman", "true"))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.dates").isArray())
                        .andExpect(jsonPath("$.dates.length()").value(2))
                        .andExpect(jsonPath("$.students").isArray())
                        .andExpect(jsonPath("$.students.length()").value(3))
                        .andExpect(jsonPath("$.students[0].records[0].symbol").isString())
                        .andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(body.path("groupId").asLong()).isEqualTo(GROUP_ID);
        assertThat(body.path("subjectId").asLong()).isEqualTo(SUBJECT_ID);
        assertThat(body.path("dates"))
                .isEqualTo(objectMapper.valueToTree(List.of("2026-04-01", "2026-04-02")));
        Map<Long, List<List<String>>> recordsByStudent = new HashMap<>();
        for (JsonNode student : body.path("students")) {
            List<List<String>> cells = new ArrayList<>();
            for (JsonNode cell : student.path("records")) {
                cells.add(
                        List.of(
                                cell.path("lessonId").asText(),
                                cell.path("date").asText(),
                                cell.path("status").asText(),
                                cell.path("symbol").asText()));
            }
            assertThat(recordsByStudent.put(student.path("userId").asLong(), cells)).isNull();
        }
        assertThat(recordsByStudent).containsOnlyKeys(100L, 101L, 102L);
        assertThat(recordsByStudent.get(100L))
                .containsExactlyInAnyOrder(
                        List.of("1", "2026-04-01", "present", "+"),
                        List.of("2", "2026-04-02", "present", "+"));
        assertThat(recordsByStudent.get(101L))
                .containsExactlyInAnyOrder(
                        List.of("1", "2026-04-01", "absent", "н"),
                        List.of("2", "2026-04-02", "excused", "у"));
        assertThat(recordsByStudent.get(102L)).isEmpty();
    }

    // -------------------------------------------------------------------------
    // Test 4 — RPRT-03: Student stats excludes CANCELLED + resolves subject names via gRPC
    // -------------------------------------------------------------------------

    @Test
    void getStudentStats_excludesCancelled() throws Exception {
        // Insert 3 docs for STUDENT_USER_ID: PRESENT, ABSENT, CANCELLED (same subject)
        insertAttendance(LESSON_ID, STUDENT_USER_ID, AttendanceStatus.PRESENT);
        insertAttendance(2L, STUDENT_USER_ID, AttendanceStatus.ABSENT);
        insertAttendance(
                3L,
                STUDENT_USER_ID,
                SUBJECT_ID,
                AttendanceStatus.CANCELLED,
                LocalDate.of(2026, 4, 3));

        when(academicGrpcClient.getSubjectDetailsByIds(any()))
                .thenReturn(Map.of(SUBJECT_ID, subjectDetails("Mathematics", "lecture")));

        mockMvc.perform(
                        get("/attendance/reports/student/stats")
                                .header("X-User-Id", STUDENT_USER_ID.toString())
                                .header("X-User-Role", "STUDENT")
                                .header("X-Group-Id", GROUP_ID.toString())
                                .header("X-Is-Headman", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subjects").isArray())
                .andExpect(jsonPath("$.subjects.length()").value(1))
                .andExpect(jsonPath("$.subjects[0].total").value(2))
                .andExpect(jsonPath("$.subjects[0].percentage").value(50.0))
                .andExpect(jsonPath("$.subjects[0].subjectName").value("Mathematics"))
                .andExpect(jsonPath("$.subjects[0].subjectType").value("lecture"))
                .andExpect(jsonPath("$.overall.total").value(2));
    }

    @Test
    void getStudentStats_excludesAuthoritativeCancelledAndTransferredStoredRecords()
            throws Exception {
        insertAttendance(
                11L,
                STUDENT_USER_ID,
                SUBJECT_ID,
                AttendanceStatus.PRESENT,
                LocalDate.of(2026, 4, 1));
        insertAttendance(
                12L,
                STUDENT_USER_ID,
                SUBJECT_ID,
                AttendanceStatus.ABSENT,
                LocalDate.of(2026, 4, 2));
        doReturn(
                        java.util.List.of(
                                ru.rutcampustrack.schedule.grpc.LessonInfo.newBuilder()
                                        .setLessonId(11L)
                                        .setStatus("cancelled")
                                        .build(),
                                ru.rutcampustrack.schedule.grpc.LessonInfo.newBuilder()
                                        .setLessonId(12L)
                                        .setStatus("transferred")
                                        .build()))
                .when(scheduleGrpcClient)
                .getLessonsByIds(any());

        mockMvc.perform(
                        get("/attendance/reports/student/stats")
                                .header("X-User-Id", STUDENT_USER_ID.toString())
                                .header("X-User-Role", "STUDENT")
                                .header("X-Group-Id", GROUP_ID.toString())
                                .header("X-Is-Headman", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subjects").isEmpty())
                .andExpect(jsonPath("$.overall.total").value(0));
    }

    // -------------------------------------------------------------------------
    // Test 5 — RPRT-04: Student records filtered by subjectId
    // -------------------------------------------------------------------------

    @Test
    void getStudentRecords_filteredBySubject() throws Exception {
        insertAttendance(
                LESSON_ID,
                STUDENT_USER_ID,
                SUBJECT_ID,
                AttendanceStatus.PRESENT,
                LocalDate.of(2026, 4, 1));
        insertAttendance(
                2L,
                STUDENT_USER_ID,
                SUBJECT_ID_2,
                AttendanceStatus.ABSENT,
                LocalDate.of(2026, 4, 2));

        mockMvc.perform(
                        get("/attendance/reports/student/records")
                                .param("subjectId", SUBJECT_ID.toString())
                                .header("X-User-Id", STUDENT_USER_ID.toString())
                                .header("X-User-Role", "STUDENT")
                                .header("X-Group-Id", GROUP_ID.toString())
                                .header("X-Is-Headman", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$._embedded.attendanceRecordEntryList").isArray())
                .andExpect(jsonPath("$._embedded.attendanceRecordEntryList.length()").value(1));
    }

    // -------------------------------------------------------------------------
    // Test 6 — RPRT-04: Student records — all subjects (no subjectId filter)
    // -------------------------------------------------------------------------

    @Test
    void getStudentRecords_allSubjects() throws Exception {
        insertAttendance(
                LESSON_ID,
                STUDENT_USER_ID,
                SUBJECT_ID,
                AttendanceStatus.PRESENT,
                LocalDate.of(2026, 4, 1));
        insertAttendance(
                2L,
                STUDENT_USER_ID,
                SUBJECT_ID_2,
                AttendanceStatus.ABSENT,
                LocalDate.of(2026, 4, 2));

        mockMvc.perform(
                        get("/attendance/reports/student/records")
                                .header("X-User-Id", STUDENT_USER_ID.toString())
                                .header("X-User-Role", "STUDENT")
                                .header("X-Group-Id", GROUP_ID.toString())
                                .header("X-Is-Headman", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$._embedded.attendanceRecordEntryList").isArray())
                .andExpect(jsonPath("$._embedded.attendanceRecordEntryList.length()").value(2));
    }

    @Test
    void teacherJournalMetricsReadPersistedMarksAndExcludeCancelledAndFutureLessons() {
        long teacherId = 200L;
        when(academicGrpcClient.getCurrentTeacherSubjects(teacherId))
                .thenReturn(
                        ru.rutcampustrack.academic.grpc.TeacherSubjectsResponse.newBuilder()
                                .addSubjects(
                                        ru.rutcampustrack.academic.grpc.TeacherSubjectInfo
                                                .newBuilder()
                                                .setGroupId(GROUP_ID)
                                                .setSubjectId(SUBJECT_ID))
                                .build());
        List<Long> ids = List.of(1L, 2L, 3L, 4L);
        when(scheduleGrpcClient.getLessonsByIds(ids))
                .thenReturn(
                        ids.stream()
                                .map(
                                        id ->
                                                ru.rutcampustrack.schedule.grpc.LessonInfo
                                                        .newBuilder()
                                                        .setLessonId(id)
                                                        .setGroupId(GROUP_ID)
                                                        .setSubjectId(SUBJECT_ID)
                                                        .setSemesterId(SEMESTER_ID)
                                                        .setAssignmentId(900L)
                                                        .setTeacherId(teacherId)
                                                        .setLessonType("lecture")
                                                        .setDate("2026-04-01")
                                                        .setStartsAt("2026-04-01T08:00:00")
                                                        .setStatus(
                                                                id == 3
                                                                        ? "cancelled"
                                                                        : id == 4
                                                                                ? "planned"
                                                                                : "closed")
                                                        .build())
                                .toList());
        when(academicGrpcClient.getGroupMembers(GROUP_ID, LocalDate.of(2026, 4, 1), SEMESTER_ID))
                .thenReturn(
                        GroupMembersResponse.newBuilder()
                                .setSemesterId(SEMESTER_ID)
                                .setAsOfDate("2026-04-01")
                                .addStudents(
                                        StudentInfo.newBuilder()
                                                .setUserId(STUDENT_USER_ID)
                                                .setDisplayName("Студент"))
                                .build());
        insertAttendance(1L, STUDENT_USER_ID, AttendanceStatus.PRESENT);
        insertAttendance(2L, STUDENT_USER_ID, AttendanceStatus.FREE_ATTENDANCE);
        insertAttendance(3L, STUDENT_USER_ID, AttendanceStatus.ABSENT);
        var result = reportService.getTeacherJournalMetrics(ids, teacherId, STUDENT_USER_ID);
        assertThat(result.students())
                .singleElement()
                .satisfies(
                        row -> {
                            assertThat(row.present())
                                    .isEqualTo(
                                            new ru.rutcampustrack.attendance.report.ReportService
                                                    .TeacherMetric(1, 2, 50));
                            assertThat(row.presentOrExcused())
                                    .isEqualTo(
                                            new ru.rutcampustrack.attendance.report.ReportService
                                                    .TeacherMetric(2, 2, 100));
                        });
        assertThat(result.series())
                .extracting(
                        ru.rutcampustrack.attendance.report.ReportService
                                        .TeacherJournalLessonMetrics
                                ::lessonId)
                .containsExactly(1L, 2L);
    }
}
