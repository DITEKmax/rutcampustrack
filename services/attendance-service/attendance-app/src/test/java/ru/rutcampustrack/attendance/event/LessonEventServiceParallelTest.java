package ru.rutcampustrack.attendance.event;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.data.mongodb.core.BulkOperations;
import org.springframework.data.mongodb.core.MongoTemplate;
import ru.rutcampustrack.academic.grpc.GroupMembersResponse;
import ru.rutcampustrack.academic.grpc.StudentInfo;
import ru.rutcampustrack.attendance.grpc.AcademicGrpcClient;
import ru.rutcampustrack.attendance.grpc.ScheduleGrpcClient;
import ru.rutcampustrack.attendance.semester.SemesterCacheService;
import ru.rutcampustrack.schedule.grpc.LessonResponse;

import java.time.LocalDate;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Regression coverage for the historical close boundary. The previous test
 * asserted parallel current-roster calls; the contract now requires the
 * canonical Schedule snapshot first and an echoed dated Academic roster.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LessonEventServiceParallelTest {

    @Mock
    private ScheduleGrpcClient scheduleGrpcClient;

    @Mock
    private AcademicGrpcClient academicGrpcClient;

    @Mock
    private MongoTemplate mongoTemplate;

    @Mock
    private SemesterCacheService semesterCacheService;

    @Mock
    private BulkOperations bulkOps;

    @Test
    void processLessonClosed_usesCanonicalDatedRoster() {
        when(scheduleGrpcClient.getLessonById(1L)).thenReturn(LessonResponse.newBuilder()
                .setId(1L)
                .setGroupId(10L)
                .setSubjectId(5L)
                .setLessonNumber(1)
                .setDate("2026-04-20")
                .setStatus("closed")
                .setSemesterId(2L)
                .build());
        when(academicGrpcClient.getGroupMembers(10L, LocalDate.of(2026, 4, 20), 2L))
                .thenReturn(GroupMembersResponse.newBuilder()
                        .addStudents(StudentInfo.newBuilder().setUserId(100L).build())
                        .setAsOfDate("2026-04-20")
                        .setSemesterId(2L)
                        .build());
        when(mongoTemplate.bulkOps(any(), any(Class.class))).thenReturn(bulkOps);
        when(bulkOps.execute()).thenReturn(null);

        LessonEventService svc = new LessonEventService(
                mongoTemplate, scheduleGrpcClient, academicGrpcClient,
                semesterCacheService, new SyncTaskExecutor());

        svc.processLessonClosed(1L, 10L);

        verify(academicGrpcClient).getGroupMembers(
                10L, LocalDate.of(2026, 4, 20), 2L);
    }
}
