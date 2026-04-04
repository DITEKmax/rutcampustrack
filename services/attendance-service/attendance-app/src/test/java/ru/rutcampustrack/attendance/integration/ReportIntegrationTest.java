package ru.rutcampustrack.attendance.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.data.mongodb.core.query.Query;
import ru.rutcampustrack.academic.grpc.GroupMembersResponse;
import ru.rutcampustrack.academic.grpc.StudentInfo;
import ru.rutcampustrack.attendance.checkin.AttendanceDocument;
import ru.rutcampustrack.attendance.contract.enums.AttendanceSource;
import ru.rutcampustrack.attendance.contract.enums.AttendanceStatus;
import ru.rutcampustrack.schedule.grpc.LessonResponse;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for all 4 attendance report endpoints.
 * Proves RPRT-01 (lesson attendance), RPRT-02 (journal grid),
 * RPRT-03 (student stats), RPRT-04 (student records), and authorization rules.
 */
class ReportIntegrationTest extends AbstractAttendanceIntegrationTest {

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

        lenient().when(scheduleGrpcClient.getLessonById(LESSON_ID)).thenReturn(
                LessonResponse.newBuilder()
                        .setId(LESSON_ID).setGroupId(GROUP_ID).setSubjectId(SUBJECT_ID)
                        .setDate("2026-04-01").setLessonNumber(1).setStatus("closed")
                        .build()
        );

        lenient().when(academicGrpcClient.getGroupMembers(GROUP_ID)).thenReturn(
                GroupMembersResponse.newBuilder()
                        .addStudents(StudentInfo.newBuilder().setUserId(100L).setDisplayName("Student A").build())
                        .addStudents(StudentInfo.newBuilder().setUserId(101L).setDisplayName("Student B").build())
                        .addStudents(StudentInfo.newBuilder().setUserId(102L).setDisplayName("Student C").build())
                        .build()
        );

        // D-13: subject name resolution via gRPC batch call
        lenient().when(academicGrpcClient.getSubjectsByIds(any()))
                .thenReturn(Map.of(SUBJECT_ID, "Mathematics", SUBJECT_ID_2, "Physics"));
    }

    // -------------------------------------------------------------------------
    // Helper
    // -------------------------------------------------------------------------

    private void insertAttendance(Long lessonId, Long userId, Long subjectId, AttendanceStatus status,
                                  LocalDate date) {
        mongoTemplate.save(AttendanceDocument.builder()
                .lessonId(lessonId).userId(userId).groupId(GROUP_ID).subjectId(subjectId)
                .semesterId(SEMESTER_ID).lessonNumber(1).lessonDate(date)
                .status(status).source(AttendanceSource.STUDENT_GEO)
                .markedBy(userId).createdAt(Instant.now()).updatedAt(Instant.now())
                .build());
    }

    private void insertAttendance(Long lessonId, Long userId, AttendanceStatus status) {
        insertAttendance(lessonId, userId, SUBJECT_ID, status, LocalDate.of(2026, 4, 1));
    }

    // -------------------------------------------------------------------------
    // Test 1 — RPRT-01: Headman gets lesson attendance with full roster (left-join)
    // -------------------------------------------------------------------------

    @Test
    void getLessonAttendance_headman_returnsAllGroupMembers() throws Exception {
        insertAttendance(LESSON_ID, 100L, AttendanceStatus.PRESENT);
        insertAttendance(LESSON_ID, 101L, AttendanceStatus.EXCUSED);
        // userId 102 has no record — should default to ABSENT

        mockMvc.perform(get("/reports/lesson/{lessonId}", LESSON_ID)
                        .header("X-User-Id", HEADMAN_USER_ID.toString())
                        .header("X-User-Role", "STUDENT")
                        .header("X-Group-Id", GROUP_ID.toString())
                        .header("X-Is-Headman", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entries").isArray())
                .andExpect(jsonPath("$.entries.length()").value(3))
                .andExpect(jsonPath("$.entries[?(@.userId == 100)].status").value("present"))
                .andExpect(jsonPath("$.entries[?(@.userId == 100)].symbol").value("б"))
                .andExpect(jsonPath("$.entries[?(@.userId == 102)].status").value("absent"))
                .andExpect(jsonPath("$.entries[?(@.userId == 102)].symbol").value("н"));
    }

    // -------------------------------------------------------------------------
    // Test 2 — RPRT-01 auth: Regular student (not headman) gets 403
    // -------------------------------------------------------------------------

    @Test
    void getLessonAttendance_regularStudentForbidden() throws Exception {
        mockMvc.perform(get("/reports/lesson/{lessonId}", LESSON_ID)
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
        insertAttendance(LESSON_ID, 100L, SUBJECT_ID, AttendanceStatus.PRESENT, LocalDate.of(2026, 4, 1));
        insertAttendance(LESSON_ID, 101L, SUBJECT_ID, AttendanceStatus.ABSENT, LocalDate.of(2026, 4, 1));
        insertAttendance(2L, 100L, SUBJECT_ID, AttendanceStatus.PRESENT, LocalDate.of(2026, 4, 2));
        insertAttendance(2L, 101L, SUBJECT_ID, AttendanceStatus.EXCUSED, LocalDate.of(2026, 4, 2));

        mockMvc.perform(get("/reports/journal")
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
                .andExpect(jsonPath("$.students[0].cells[0].symbol").isString());
    }

    // -------------------------------------------------------------------------
    // Test 4 — RPRT-03: Student stats excludes CANCELLED + resolves subject names via gRPC
    // -------------------------------------------------------------------------

    @Test
    void getStudentStats_excludesCancelled() throws Exception {
        // Insert 3 docs for STUDENT_USER_ID: PRESENT, ABSENT, CANCELLED (same subject)
        insertAttendance(LESSON_ID, STUDENT_USER_ID, AttendanceStatus.PRESENT);
        insertAttendance(2L, STUDENT_USER_ID, AttendanceStatus.ABSENT);
        insertAttendance(3L, STUDENT_USER_ID, SUBJECT_ID, AttendanceStatus.CANCELLED, LocalDate.of(2026, 4, 3));

        when(academicGrpcClient.getSubjectsByIds(any()))
                .thenReturn(Map.of(SUBJECT_ID, "Mathematics"));

        mockMvc.perform(get("/reports/student/stats")
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
                .andExpect(jsonPath("$.overall.total").value(2));
    }

    // -------------------------------------------------------------------------
    // Test 5 — RPRT-04: Student records filtered by subjectId
    // -------------------------------------------------------------------------

    @Test
    void getStudentRecords_filteredBySubject() throws Exception {
        insertAttendance(LESSON_ID, STUDENT_USER_ID, SUBJECT_ID, AttendanceStatus.PRESENT, LocalDate.of(2026, 4, 1));
        insertAttendance(2L, STUDENT_USER_ID, SUBJECT_ID_2, AttendanceStatus.ABSENT, LocalDate.of(2026, 4, 2));

        mockMvc.perform(get("/reports/student/records")
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
        insertAttendance(LESSON_ID, STUDENT_USER_ID, SUBJECT_ID, AttendanceStatus.PRESENT, LocalDate.of(2026, 4, 1));
        insertAttendance(2L, STUDENT_USER_ID, SUBJECT_ID_2, AttendanceStatus.ABSENT, LocalDate.of(2026, 4, 2));

        mockMvc.perform(get("/reports/student/records")
                        .header("X-User-Id", STUDENT_USER_ID.toString())
                        .header("X-User-Role", "STUDENT")
                        .header("X-Group-Id", GROUP_ID.toString())
                        .header("X-Is-Headman", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$._embedded.attendanceRecordEntryList").isArray())
                .andExpect(jsonPath("$._embedded.attendanceRecordEntryList.length()").value(2));
    }
}
