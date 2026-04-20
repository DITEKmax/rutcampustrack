package ru.rutcampustrack.shared.observability;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/**
 * M05 G9 DRY (NEW-149): shared helper для параллельного fan-out'а gRPC
 * call'ов через {@link CompletableFuture}.
 *
 * <p>Распаковывает {@link CompletableFuture#join()} в исходный
 * {@code RuntimeException} — если задача упала с
 * {@code StatusRuntimeException} (через {@code *GrpcClient} wrapper'ы
 * на bounded {@code grpcTaskExecutor}), {@link CompletionException}
 * оборачивает cause, но нам нужен оригинальный тип для существующих
 * error-handler'ов (AMQP nack → DLQ, {@code @ControllerAdvice} → 503).
 *
 * <p>Раньше helper дублировался inline в
 * {@code attendance.event.LessonEventService} и
 * {@code attendance.marking.MarkingService} (по 11 строк × 2). Вынесено
 * здесь — новые параллельные fan-out'ы в других сервисах будут
 * переиспользовать без копипаста.
 */
public final class AsyncGrpcUtils {

    private AsyncGrpcUtils() {}

    /**
     * Эквивалент {@code future.join()} с разворачиванием
     * {@link CompletionException#getCause()} в оригинальный
     * {@link RuntimeException}. Если cause не RuntimeException —
     * пробрасывает исходный CompletionException.
     */
    public static <T> T joinOrUnwrap(CompletableFuture<T> future) {
        try {
            return future.join();
        } catch (CompletionException ce) {
            Throwable cause = ce.getCause();
            if (cause instanceof RuntimeException re) {
                throw re;
            }
            throw ce;
        }
    }
}
