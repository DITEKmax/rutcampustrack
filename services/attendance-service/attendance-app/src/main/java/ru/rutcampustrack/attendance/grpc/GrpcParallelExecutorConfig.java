package ru.rutcampustrack.attendance.grpc;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * M05 G8 (NEW-149): bounded executor для параллельного fan-out двух
 * independent gRPC-call'ов (`LessonEventService.processLessonClosed`,
 * `MarkingService.markBatch`, `ReportService.getStudentStats`).
 *
 * <p>Sizing:
 * <ul>
 *   <li>core = 2 — типичный fan-out factor (scheduleClient + academicClient).</li>
 *   <li>max = 8 — burst during event storm (multiple lesson.closed в
 *       течение одной минуты).</li>
 *   <li>queue = 100 — backpressure: при flood'е events лишние tasks
 *       встают в очередь, а не создают новые потоки.</li>
 * </ul>
 *
 * <p>Deadline propagation: gRPC stub уже имеет
 * {@code withDeadlineAfter(3, SECONDS)}, поэтому task'и fail-fast даже
 * если их долго держит queue. `.join()` в caller'е трансформирует
 * `CompletionException` → `StatusRuntimeException`.
 */
@Configuration
public class GrpcParallelExecutorConfig {

    @Bean(name = "grpcTaskExecutor")
    public ThreadPoolTaskExecutor grpcTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("grpc-parallel-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(5);
        executor.initialize();
        return executor;
    }
}
