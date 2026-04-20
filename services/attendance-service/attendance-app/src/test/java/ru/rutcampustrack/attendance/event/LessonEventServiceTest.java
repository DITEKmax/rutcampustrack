package ru.rutcampustrack.attendance.event;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.data.mongodb.core.BulkOperations;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import ru.rutcampustrack.academic.grpc.GroupMembersResponse;
import ru.rutcampustrack.academic.grpc.StudentInfo;
import ru.rutcampustrack.attendance.checkin.AttendanceDocument;
import ru.rutcampustrack.attendance.exception.ScheduleServiceUnavailableException;
import ru.rutcampustrack.attendance.grpc.AcademicGrpcClient;
import ru.rutcampustrack.attendance.grpc.ScheduleGrpcClient;
import ru.rutcampustrack.attendance.semester.SemesterCacheService;
import ru.rutcampustrack.schedule.grpc.LessonResponse;

import com.mongodb.client.result.UpdateResult;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for LessonEventService business logic.
 * Pure Mockito — no Spring context loaded.
 */
@ExtendWith(MockitoExtension.class)
class LessonEventServiceTest {

    @Mock
    private MongoTemplate mongoTemplate;

    @Mock
    private ScheduleGrpcClient scheduleGrpcClient;

    @Mock
    private AcademicGrpcClient academicGrpcClient;

    @Mock
    private SemesterCacheService semesterCacheService;

    @Mock
    private BulkOperations bulkOps;

    private LessonEventService lessonEventService;

    @BeforeEach
    void setUp() {
        // M05 G8: SyncTaskExecutor запускает Runnable в текущем потоке —
        // сохраняет детерминизм Mockito verify'ов, не вводя параллелизма
        // в unit-тесте (он проверяет correctness, не latency).
        lessonEventService = new LessonEventService(
                mongoTemplate, scheduleGrpcClient, academicGrpcClient,
                semesterCacheService, new SyncTaskExecutor());
        // Lenient: not all tests use bulkOps (e.g. empty group, cancellation tests)
        lenient().when(mongoTemplate.bulkOps(any(BulkOperations.BulkMode.class), eq(AttendanceDocument.class)))
                .thenReturn(bulkOps);
        // Lenient: updateMulti returns non-null UpdateResult to prevent NPE in logger
        lenient().when(mongoTemplate.updateMulti(any(Query.class), any(Update.class), eq(AttendanceDocument.class)))
                .thenReturn(mock(UpdateResult.class));
    }

    // -------------------------------------------------------------------------
    // Test 1: processLessonClosed calls bulkOps.upsert for each student
    // -------------------------------------------------------------------------

    @Test
    void processLessonClosed_callsBulkUpsertForEachStudent() {
        LessonResponse lesson = LessonResponse.newBuilder()
                .setId(1L).setGroupId(10L).setSubjectId(5L)
                .setDate("2026-04-01").setLessonNumber(1).setStatus("closed")
                .build();
        when(scheduleGrpcClient.getLessonById(1L)).thenReturn(lesson);

        GroupMembersResponse members = GroupMembersResponse.newBuilder()
                .addStudents(StudentInfo.newBuilder().setUserId(100L).setDisplayName("Student 100").build())
                .addStudents(StudentInfo.newBuilder().setUserId(101L).setDisplayName("Student 101").build())
                .addStudents(StudentInfo.newBuilder().setUserId(102L).setDisplayName("Student 102").build())
                .build();
        when(academicGrpcClient.getGroupMembers(10L)).thenReturn(members);
        when(semesterCacheService.getActiveSemesterId()).thenReturn(1L);

        lessonEventService.processLessonClosed(1L, 10L);

        verify(bulkOps, times(3)).upsert(any(Query.class), any(Update.class));
        verify(bulkOps, times(1)).execute();
    }

    // -------------------------------------------------------------------------
    // Test 2: processLessonClosed with empty group skips bulk ops entirely
    // -------------------------------------------------------------------------

    @Test
    void processLessonClosed_emptyGroup_skipsWithoutBulkOps() {
        LessonResponse lesson = LessonResponse.newBuilder()
                .setId(1L).setGroupId(10L).setSubjectId(5L)
                .setDate("2026-04-01").setLessonNumber(1).setStatus("closed")
                .build();
        when(scheduleGrpcClient.getLessonById(1L)).thenReturn(lesson);
        when(academicGrpcClient.getGroupMembers(10L)).thenReturn(GroupMembersResponse.newBuilder().build());

        lessonEventService.processLessonClosed(1L, 10L);

        verify(mongoTemplate, never()).bulkOps(any(BulkOperations.BulkMode.class), eq(AttendanceDocument.class));
        verify(bulkOps, never()).execute();
    }

    // -------------------------------------------------------------------------
    // Test 3: null semesterId (cache miss) still executes bulk ops
    // -------------------------------------------------------------------------

    @Test
    void processLessonClosed_nullSemesterId_stillExecutes() {
        LessonResponse lesson = LessonResponse.newBuilder()
                .setId(1L).setGroupId(10L).setSubjectId(5L)
                .setDate("2026-04-01").setLessonNumber(1).setStatus("closed")
                .build();
        when(scheduleGrpcClient.getLessonById(1L)).thenReturn(lesson);

        GroupMembersResponse members = GroupMembersResponse.newBuilder()
                .addStudents(StudentInfo.newBuilder().setUserId(100L).setDisplayName("Student 100").build())
                .addStudents(StudentInfo.newBuilder().setUserId(101L).setDisplayName("Student 101").build())
                .build();
        when(academicGrpcClient.getGroupMembers(10L)).thenReturn(members);
        when(semesterCacheService.getActiveSemesterId()).thenReturn(null);

        lessonEventService.processLessonClosed(1L, 10L);

        verify(bulkOps, times(2)).upsert(any(Query.class), any(Update.class));
        verify(bulkOps, times(1)).execute();
    }

    // -------------------------------------------------------------------------
    // Test 4: gRPC failure propagates exception (no swallowing)
    // -------------------------------------------------------------------------

    @Test
    void processLessonClosed_grpcFailure_propagatesException() {
        when(scheduleGrpcClient.getLessonById(1L))
                .thenThrow(new ScheduleServiceUnavailableException("Schedule Service unavailable: UNAVAILABLE"));

        assertThrows(ScheduleServiceUnavailableException.class,
                () -> lessonEventService.processLessonClosed(1L, 10L));
    }

    // -------------------------------------------------------------------------
    // Test 5: processLessonCancelled calls updateMulti
    // -------------------------------------------------------------------------

    @Test
    void processLessonCancelled_callsUpdateMulti() {
        lessonEventService.processLessonCancelled(1L);

        verify(mongoTemplate, times(1))
                .updateMulti(any(Query.class), any(Update.class), eq(AttendanceDocument.class));
    }

    // -------------------------------------------------------------------------
    // Test 6: processLessonCancelled does not invoke gRPC clients
    // -------------------------------------------------------------------------

    @Test
    void processLessonCancelled_grpcNotInvolved() {
        lessonEventService.processLessonCancelled(1L);

        verifyNoInteractions(scheduleGrpcClient);
        verifyNoInteractions(academicGrpcClient);
    }
}
