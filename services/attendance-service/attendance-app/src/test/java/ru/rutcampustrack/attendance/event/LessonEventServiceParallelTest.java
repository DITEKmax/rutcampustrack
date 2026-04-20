package ru.rutcampustrack.attendance.event;

import com.mongodb.client.result.UpdateResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.mongodb.core.BulkOperations;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import ru.rutcampustrack.academic.grpc.GroupMembersResponse;
import ru.rutcampustrack.academic.grpc.StudentInfo;
import ru.rutcampustrack.attendance.grpc.AcademicGrpcClient;
import ru.rutcampustrack.attendance.grpc.ScheduleGrpcClient;
import ru.rutcampustrack.attendance.semester.SemesterCacheService;
import ru.rutcampustrack.schedule.grpc.LessonResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

/**
 * M05 G8 (NEW-149): проверяет что {@link LessonEventService#processLessonClosed}
 * параллелит два gRPC-вызова через {@code grpcTaskExecutor}. Мок-клиенты
 * симулируют 200ms latency каждый. Wall-time:
 * <ul>
 *   <li>Параллельно: ~210ms (200ms + overhead).</li>
 *   <li>Sequential baseline: ~420ms (200ms × 2).</li>
 * </ul>
 *
 * <p>Ассерт: wall-time &lt; 350ms подтверждает параллельное выполнение
 * (лимит 350ms оставляет margin для CI jitter, но исключает sequential path).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LessonEventServiceParallelTest {

    private static final long SIM_LATENCY_MS = 200;

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
    void processLessonClosed_runsGrpcCallsInParallel() throws Exception {
        // Сэмулируем 200ms latency на каждый gRPC-call
        when(scheduleGrpcClient.getLessonById(any())).thenAnswer(inv -> {
            Thread.sleep(SIM_LATENCY_MS);
            return LessonResponse.newBuilder()
                    .setId(1L)
                    .setGroupId(10L)
                    .setSubjectId(5L)
                    .setLessonNumber(1)
                    .setDate("2026-04-20")
                    .build();
        });
        when(academicGrpcClient.getGroupMembers(any())).thenAnswer(inv -> {
            Thread.sleep(SIM_LATENCY_MS);
            return GroupMembersResponse.newBuilder()
                    .addStudents(StudentInfo.newBuilder().setUserId(100L).build())
                    .build();
        });
        when(semesterCacheService.getActiveSemesterId()).thenReturn(2L);
        when(mongoTemplate.bulkOps(any(), any(Class.class))).thenReturn(bulkOps);
        doAnswer(inv -> bulkOps).when(bulkOps).upsert(any(), any());
        when(bulkOps.execute()).thenReturn(null);

        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(100);
        executor.initialize();

        LessonEventService svc = new LessonEventService(
                mongoTemplate, scheduleGrpcClient, academicGrpcClient,
                semesterCacheService, executor);

        long start = System.currentTimeMillis();
        svc.processLessonClosed(1L, 10L);
        long elapsed = System.currentTimeMillis() - start;

        // Параллельно: ~200ms + overhead. Sequential было бы ~400ms.
        assertThat(elapsed)
                .as("processLessonClosed wall-time должно быть < 350ms "
                        + "(параллельно 200ms × 2) — sequential baseline ~400ms")
                .isLessThan(350L);

        executor.shutdown();
    }
}
