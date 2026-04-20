package ru.rutcampustrack.attendance.marking;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import ru.rutcampustrack.academic.grpc.GroupMembersResponse;
import ru.rutcampustrack.academic.grpc.StudentInfo;
import ru.rutcampustrack.attendance.checkin.AttendanceDocument;
import ru.rutcampustrack.attendance.contract.dto.marking.MarkBatchItem;
import ru.rutcampustrack.attendance.contract.dto.marking.MarkBatchRequest;
import ru.rutcampustrack.attendance.contract.dto.marking.MarkRequest;
import ru.rutcampustrack.attendance.contract.enums.AttendanceSource;
import ru.rutcampustrack.attendance.contract.enums.AttendanceStatus;
import ru.rutcampustrack.attendance.event.AttendanceEventPublisher;
import ru.rutcampustrack.attendance.exception.AccessDeniedException;
import ru.rutcampustrack.attendance.exception.BadRequestException;
import ru.rutcampustrack.attendance.grpc.AcademicGrpcClient;
import ru.rutcampustrack.attendance.grpc.ScheduleGrpcClient;
import ru.rutcampustrack.attendance.security.RequestContext;
import ru.rutcampustrack.attendance.semester.SemesterCacheService;
import ru.rutcampustrack.schedule.grpc.LessonResponse;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for MarkingService.
 * Proves each authorization branch and core marking orchestration logic.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MarkingServiceTest {

    @Mock
    private ScheduleGrpcClient scheduleGrpcClient;

    @Mock
    private AcademicGrpcClient academicGrpcClient;

    @Mock
    private MongoTemplate mongoTemplate;

    @Mock
    private AttendanceEventPublisher eventPublisher;

    @Mock
    private SemesterCacheService semesterCacheService;

    @Mock
    private RequestContext requestContext;

    @InjectMocks
    private MarkingService markingService;

    private static final Long LESSON_ID = 42L;
    private static final Long USER_ID = 99L;
    private static final Long HEADMAN_ID = 50L;
    private static final Long GROUP_ID = 10L;
    private static final Long SUBJECT_ID = 5L;
    private static final Long SEMESTER_ID = 1L;

    private LessonResponse buildLesson(Long groupId) {
        return LessonResponse.newBuilder()
                .setId(LESSON_ID)
                .setGroupId(groupId)
                .setSubjectId(SUBJECT_ID)
                .setDate("2026-04-01")
                .setLessonNumber(1)
                .setStatus("started")
                .build();
    }

    private GroupMembersResponse buildGroupMembers(Long... userIds) {
        GroupMembersResponse.Builder builder = GroupMembersResponse.newBuilder();
        for (Long uid : userIds) {
            builder.addStudents(StudentInfo.newBuilder().setUserId(uid).build());
        }
        return builder.build();
    }

    private AttendanceDocument buildSavedDoc() {
        return AttendanceDocument.builder()
                .id("doc-1")
                .lessonId(LESSON_ID)
                .userId(USER_ID)
                .groupId(GROUP_ID)
                .subjectId(SUBJECT_ID)
                .semesterId(SEMESTER_ID)
                .lessonNumber(1)
                .lessonDate(LocalDate.of(2026, 4, 1))
                .status(AttendanceStatus.PRESENT)
                .source(AttendanceSource.HEADMAN)
                .markedBy(HEADMAN_ID)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    @BeforeEach
    void setUpHappyPath() {
        // Default: headman context
        lenient().when(requestContext.isHeadman()).thenReturn(true);
        lenient().when(requestContext.getUserId()).thenReturn(HEADMAN_ID);
        lenient().when(requestContext.getGroupId()).thenReturn(GROUP_ID);
        // Lesson belongs to headman's group
        lenient().when(scheduleGrpcClient.getLessonById(LESSON_ID)).thenReturn(buildLesson(GROUP_ID));
        // Student 99 is in group 10
        lenient().when(academicGrpcClient.getGroupMembers(GROUP_ID)).thenReturn(buildGroupMembers(USER_ID, 100L));
        lenient().when(semesterCacheService.getActiveSemesterId()).thenReturn(SEMESTER_ID);
        // mongoTemplate.findOne returns the document after upsert
        lenient().when(mongoTemplate.findOne(any(Query.class), eq(AttendanceDocument.class)))
                .thenReturn(buildSavedDoc());
    }

    // -------------------------------------------------------------------------
    // Test 1 — Happy path
    // -------------------------------------------------------------------------

    @Test
    void markAttendance_headmanMarksStudentInGroup_upsertsAndPublishesEvent() {
        AttendanceDocument result = markingService.markAttendance(
                LESSON_ID, USER_ID, new MarkRequest(AttendanceStatus.PRESENT));

        verify(mongoTemplate).upsert(any(), any(), eq(AttendanceDocument.class));
        verify(eventPublisher).publishMarked(result);
        assertThat(result).isNotNull();
        assertThat(result.getStatus()).isEqualTo(AttendanceStatus.PRESENT);
        assertThat(result.getSource()).isEqualTo(AttendanceSource.HEADMAN);
    }

    // -------------------------------------------------------------------------
    // Test 2 — Not headman
    // -------------------------------------------------------------------------

    @Test
    void markAttendance_notHeadman_throwsAccessDeniedException() {
        when(requestContext.isHeadman()).thenReturn(false);

        assertThatThrownBy(() -> markingService.markAttendance(
                LESSON_ID, USER_ID, new MarkRequest(AttendanceStatus.PRESENT)))
                .isInstanceOf(AccessDeniedException.class);

        verify(mongoTemplate, never()).upsert(any(), any(), eq(AttendanceDocument.class));
        verify(eventPublisher, never()).publishMarked(any());
    }

    // -------------------------------------------------------------------------
    // Test 3 — Headman's group doesn't match lesson's group
    // -------------------------------------------------------------------------

    @Test
    void markAttendance_lessonBelongsToOtherGroup_throwsAccessDeniedException() {
        when(scheduleGrpcClient.getLessonById(LESSON_ID)).thenReturn(buildLesson(20L)); // different group

        assertThatThrownBy(() -> markingService.markAttendance(
                LESSON_ID, USER_ID, new MarkRequest(AttendanceStatus.PRESENT)))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("чужой");

        verify(mongoTemplate, never()).upsert(any(), any(), eq(AttendanceDocument.class));
    }

    // -------------------------------------------------------------------------
    // Test 4 — Student not in headman's group
    // -------------------------------------------------------------------------

    @Test
    void markAttendance_studentNotInGroup_throwsAccessDeniedException() {
        // group members does NOT include userId 99
        when(academicGrpcClient.getGroupMembers(GROUP_ID)).thenReturn(buildGroupMembers(100L, 101L));

        assertThatThrownBy(() -> markingService.markAttendance(
                LESSON_ID, USER_ID, new MarkRequest(AttendanceStatus.PRESENT)))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("группе");

        verify(mongoTemplate, never()).upsert(any(), any(), eq(AttendanceDocument.class));
    }

    // -------------------------------------------------------------------------
    // Test 5 — CANCELLED status rejected with 400
    // -------------------------------------------------------------------------

    @Test
    void markAttendance_cancelledStatus_throwsBadRequestException() {
        assertThatThrownBy(() -> markingService.markAttendance(
                LESSON_ID, USER_ID, new MarkRequest(AttendanceStatus.CANCELLED)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("CANCELLED");

        verify(mongoTemplate, never()).upsert(any(), any(), eq(AttendanceDocument.class));
    }

    // -------------------------------------------------------------------------
    // Test 6 — Second mark (upsert) updates status, not duplicate
    // -------------------------------------------------------------------------

    @Test
    void markAttendance_secondMark_upsertsNotDuplicates() {
        // First mark: PRESENT
        markingService.markAttendance(LESSON_ID, USER_ID, new MarkRequest(AttendanceStatus.PRESENT));

        // Second mark: ABSENT
        AttendanceDocument absentDoc = buildSavedDoc();
        absentDoc.setStatus(AttendanceStatus.ABSENT);
        when(mongoTemplate.findOne(any(Query.class), eq(AttendanceDocument.class))).thenReturn(absentDoc);
        markingService.markAttendance(LESSON_ID, USER_ID, new MarkRequest(AttendanceStatus.ABSENT));

        // upsert called twice (once per mark), not insert/save
        verify(mongoTemplate, org.mockito.Mockito.times(2))
                .upsert(any(), any(), eq(AttendanceDocument.class));
    }

    // -------------------------------------------------------------------------
    // Test 7 — Event published with correct document
    // -------------------------------------------------------------------------

    @Test
    void markAttendance_successfulMark_publishesMarkedEvent() {
        AttendanceDocument result = markingService.markAttendance(
                LESSON_ID, USER_ID, new MarkRequest(AttendanceStatus.EXCUSED));

        verify(eventPublisher).publishMarked(result);
    }

    // -------------------------------------------------------------------------
    // Batch-tests (M05 D7 / P2-10/4)
    // -------------------------------------------------------------------------

    private MarkBatchItem item(Long lessonId, Long userId, AttendanceStatus status) {
        return new MarkBatchItem(lessonId, userId, status);
    }

    /**
     * BATCH-01: типичный batch 3 студента одной пары — один gRPC getLesson,
     * один gRPC getGroupMembers, три upsert'а.
     */
    @Test
    void markBatch_threeStudentsOneLesson_upsertsAllPublishesEach() {
        when(academicGrpcClient.getGroupMembers(GROUP_ID))
                .thenReturn(buildGroupMembers(99L, 100L, 101L));

        MarkBatchRequest request = new MarkBatchRequest(List.of(
                item(LESSON_ID, 99L, AttendanceStatus.PRESENT),
                item(LESSON_ID, 100L, AttendanceStatus.ABSENT),
                item(LESSON_ID, 101L, AttendanceStatus.EXCUSED)
        ));

        List<AttendanceDocument> result = markingService.markBatch(request);

        assertThat(result).hasSize(3);
        verify(scheduleGrpcClient, org.mockito.Mockito.times(1)).getLessonById(LESSON_ID);
        verify(academicGrpcClient, org.mockito.Mockito.times(1)).getGroupMembers(GROUP_ID);
        verify(mongoTemplate, org.mockito.Mockito.times(3))
                .upsert(any(), any(), eq(AttendanceDocument.class));
        verify(eventPublisher, org.mockito.Mockito.times(3)).publishMarked(any());
    }

    /**
     * BATCH-02: non-headman — весь batch отклонён до gRPC.
     */
    @Test
    void markBatch_notHeadman_throwsBeforeAnyWrite() {
        when(requestContext.isHeadman()).thenReturn(false);

        MarkBatchRequest request = new MarkBatchRequest(List.of(
                item(LESSON_ID, 99L, AttendanceStatus.PRESENT)));

        assertThatThrownBy(() -> markingService.markBatch(request))
                .isInstanceOf(AccessDeniedException.class);

        verify(mongoTemplate, never()).upsert(any(), any(), eq(AttendanceDocument.class));
        verify(eventPublisher, never()).publishMarked(any());
    }

    /**
     * BATCH-03: студент вне группы среди items — весь batch отклонён,
     * ни один upsert не выполнен (validation-first atomic).
     */
    @Test
    void markBatch_oneStudentNotInGroup_rejectsWholeBatchNoUpserts() {
        when(academicGrpcClient.getGroupMembers(GROUP_ID))
                .thenReturn(buildGroupMembers(99L, 100L)); // 200 НЕ в группе

        MarkBatchRequest request = new MarkBatchRequest(List.of(
                item(LESSON_ID, 99L, AttendanceStatus.PRESENT),
                item(LESSON_ID, 100L, AttendanceStatus.PRESENT),
                item(LESSON_ID, 200L, AttendanceStatus.PRESENT) // посторонний
        ));

        assertThatThrownBy(() -> markingService.markBatch(request))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("200");

        verify(mongoTemplate, never()).upsert(any(), any(), eq(AttendanceDocument.class));
        verify(eventPublisher, never()).publishMarked(any());
    }

    /**
     * BATCH-04: CANCELLED в любом item → BadRequestException до любого
     * write'а (validation-first).
     */
    @Test
    void markBatch_anyCancelledStatus_rejectsBatch() {
        MarkBatchRequest request = new MarkBatchRequest(List.of(
                item(LESSON_ID, 99L, AttendanceStatus.PRESENT),
                item(LESSON_ID, 100L, AttendanceStatus.CANCELLED)
        ));

        assertThatThrownBy(() -> markingService.markBatch(request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("CANCELLED");

        verify(mongoTemplate, never()).upsert(any(), any(), eq(AttendanceDocument.class));
    }

    /**
     * BATCH-05: lesson из чужой группы — весь batch отклонён.
     */
    @Test
    void markBatch_lessonFromOtherGroup_rejectsBatch() {
        when(scheduleGrpcClient.getLessonById(LESSON_ID)).thenReturn(buildLesson(999L));

        MarkBatchRequest request = new MarkBatchRequest(List.of(
                item(LESSON_ID, 99L, AttendanceStatus.PRESENT)));

        assertThatThrownBy(() -> markingService.markBatch(request))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("чужой");

        verify(mongoTemplate, never()).upsert(any(), any(), eq(AttendanceDocument.class));
    }
}
