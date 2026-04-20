package ru.rutcampustrack.attendance.grpc;

import java.util.concurrent.ThreadPoolExecutor;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * M05 G8 (NEW-149): bounded executor для параллельного fan-out двух
 * independent gRPC-call'ов (`LessonEventService.processLessonClosed`,
 * `MarkingService.markBatch`).
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
 * <p>M05 audit fix (bug-hunter 3.1): {@link ThreadPoolExecutor.CallerRunsPolicy}
 * при переполнении очереди — task выполняется на caller-thread'е
 * (RabbitMQ consumer / HTTP worker). Это гарантирует, что под event-storm'ом
 * AMQP consumer не теряет heartbeat из-за RejectedExecutionException'а,
 * а gracefully деградирует до sequential выполнения. Альтернатива
 * (AbortPolicy default) роняет весь processLessonClosed и messages
 * возвращаются в очередь бесконечно.
 *
 * <p>Deadline: callers всё равно оборачивают gRPC-stub в
 * {@code withDeadlineAfter(3s)}, но время ожидания в queue его не
 * включает (стартует внутри таска). Под перегрузкой это может растянуть
 * wall-time обработки; accept'им trade-off — CallerRunsPolicy лучше чем
 * падение heartbeat.
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
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}
