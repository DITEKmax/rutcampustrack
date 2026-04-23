package ru.rutcampustrack.attendance.event;

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

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

/**
 * M05 G8 (NEW-149): проверяет что {@link LessonEventService#processLessonClosed}
 * параллелит два gRPC-вызова через {@code grpcTaskExecutor}.
 *
 * <p>M08 G12 (2026-04-23) — переписано с wall-time на CountDownLatch-pattern.
 * Причина: wall-time зависит от CPU dev-машины/CI runner'а, flaky (350ms→750ms
 * не хватало — actual 767ms наблюдался на Windows dev). CountDownLatch даёт
 * прямое доказательство concurrent-вызова без timing-noise:
 * <ul>
 *   <li>Оба gRPC-mock'а await на {@code bothStarted} latch.</li>
 *   <li>Если оба ждут одновременно → latch опускается до 0 → mock'ы идут дальше.</li>
 *   <li>Sequential execution → первый mock зависнет, тест упадёт по timeout.</li>
 * </ul>
 * Timeout 5s — далеко за любые разумные overhead'ы, но меньше CI-watchdog.
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
    void processLessonClosed_runsGrpcCallsInParallel() throws Exception {
        // Latch countdown'ится каждым mock'ом при entry. Оба await'ят
        // до total == 0 — т.е. пока ОБА gRPC-вызова не стартовали.
        // Sequential execution → первый зависнет навсегда (вторая задача
        // не запущена) → await timeout → test fails.
        CountDownLatch bothStarted = new CountDownLatch(2);

        when(scheduleGrpcClient.getLessonById(any())).thenAnswer(inv -> {
            bothStarted.countDown();
            // Await до 3s. Если parallel — вторая задача тоже дойдёт сюда,
            // latch опустится, оба продолжат. Если sequential — timeout.
            if (!bothStarted.await(3, TimeUnit.SECONDS)) {
                throw new AssertionError(
                        "Sequential execution detected: second gRPC call did not start"
                                + " while first was blocked");
            }
            return LessonResponse.newBuilder()
                    .setId(1L)
                    .setGroupId(10L)
                    .setSubjectId(5L)
                    .setLessonNumber(1)
                    .setDate("2026-04-20")
                    .build();
        });
        when(academicGrpcClient.getGroupMembers(any())).thenAnswer(inv -> {
            bothStarted.countDown();
            if (!bothStarted.await(3, TimeUnit.SECONDS)) {
                throw new AssertionError(
                        "Sequential execution detected: second gRPC call did not start"
                                + " while first was blocked");
            }
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

        svc.processLessonClosed(1L, 10L);

        // Если мы сюда дошли — оба mock'а отработали, значит оба были
        // запущены concurrently (иначе первый бы timeout-ом упал).
        assertThat(bothStarted.getCount()).isZero();

        executor.shutdown();
    }
}
