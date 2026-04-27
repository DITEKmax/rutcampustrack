package ru.rutcampustrack.academic.grpc;

/**
 * M16 G7 — rate-limit per-user для gRPC isHeadman вызовов.
 *
 * <p>Раньше (M06 G8b) был in-memory ConcurrentHashMap внутри
 * {@link AcademicGrpcServiceImpl} — не работал при horizontal
 * scale-out (каждый pod видит свои bucket'ы → headman c 4 pods может
 * делать 4 × limit calls/min суммарно). Теперь — Redis-backed
 * shared counter.
 */
public interface HeadmanRateLimiter {

    /**
     * Атомарно проверяет/инкрементит счётчик. Возвращает {@code true} если
     * вызов прошёл (под лимитом), {@code false} если лимит исчерпан.
     *
     * <p>При Redis outage реализация может fail-open (вернуть {@code true})
     * с логом + counter — headman RL защищает от flood, а не от brute-force,
     * fail-open приемлем (см. DECISIONS § D11).
     */
    boolean tryConsume(long userId);
}
