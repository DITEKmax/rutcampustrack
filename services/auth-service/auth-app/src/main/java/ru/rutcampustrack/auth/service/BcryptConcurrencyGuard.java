package ru.rutcampustrack.auth.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import ru.rutcampustrack.auth.exception.OtpRateLimitException;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * M03b Группа 10 (KI-7): ограничивает параллельное выполнение bcrypt operations
 * (password verification/encoding) семафором N=20 permits. Bcrypt по дизайну
 * CPU-expensive — атакующий может заспамить сервер concurrent-запросами с
 * заведомо invalid-паролями до того как LoginRateLimiter посчитает failures
 * и заблокирует IP. Семафор гарантирует что одновременно работает не более N
 * bcrypt-сессий; запросы сверх лимита получают 429 {@link OtpRateLimitException}
 * fail-fast вместо задержки.
 *
 * <p>Настройка через {@code rct.auth.bcrypt.max-concurrent} (default 20) и
 * {@code rct.auth.bcrypt.acquire-timeout-ms} (default 0 — fail-fast без ожидания).</p>
 */
@Component
public class BcryptConcurrencyGuard {

    private static final Logger log = LoggerFactory.getLogger(BcryptConcurrencyGuard.class);

    private final Semaphore semaphore;
    private final long acquireTimeoutMs;

    public BcryptConcurrencyGuard(
            @Value("${rct.auth.bcrypt.max-concurrent:20}") int maxConcurrent,
            @Value("${rct.auth.bcrypt.acquire-timeout-ms:0}") long acquireTimeoutMs) {
        if (maxConcurrent <= 0) {
            throw new IllegalArgumentException("rct.auth.bcrypt.max-concurrent must be > 0");
        }
        this.semaphore = new Semaphore(maxConcurrent, true);
        this.acquireTimeoutMs = acquireTimeoutMs;
    }

    /**
     * Выполняет {@code work.get()} под guard'ом семафора. Если permit не получен
     * в течение {@code acquireTimeoutMs} — бросает {@link OtpRateLimitException}
     * (429). Если работа бросает исключение — permit всё равно возвращается.
     */
    public <T> T execute(Supplier<T> work) {
        boolean acquired;
        try {
            acquired = acquireTimeoutMs > 0
                    ? semaphore.tryAcquire(acquireTimeoutMs, TimeUnit.MILLISECONDS)
                    : semaphore.tryAcquire();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new OtpRateLimitException("Server busy, retry later");
        }
        if (!acquired) {
            log.warn("Bcrypt concurrency limit reached ({} permits), rejecting request",
                    semaphore.availablePermits() + semaphore.getQueueLength());
            throw new OtpRateLimitException("Server busy, retry later");
        }
        try {
            return work.get();
        } finally {
            semaphore.release();
        }
    }

    /** Для тестов. */
    public int availablePermits() {
        return semaphore.availablePermits();
    }
}
