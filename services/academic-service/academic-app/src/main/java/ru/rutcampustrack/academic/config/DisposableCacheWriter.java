package ru.rutcampustrack.academic.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.cache.CacheStatistics;
import org.springframework.data.redis.cache.CacheStatisticsCollector;
import org.springframework.data.redis.cache.RedisCacheWriter;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

/**
 * Cache I/O is optional; a failed invalidation must never make old data readable again.
 *
 * <p>The first failure disables this process's entire disposable cache until restart. A restart
 * uses a new key namespace, including when an old timed-out write eventually reaches Redis. Domain
 * value loaders run outside these guards and their exceptions still reach the caller.
 */
final class DisposableCacheWriter implements RedisCacheWriter {
    private static final Logger log = LoggerFactory.getLogger(DisposableCacheWriter.class);

    private final RedisCacheWriter delegate;
    private final AtomicBoolean enabled;

    DisposableCacheWriter(RedisCacheWriter delegate) {
        this(delegate, new AtomicBoolean(true));
    }

    private DisposableCacheWriter(RedisCacheWriter delegate, AtomicBoolean enabled) {
        this.delegate = delegate;
        this.enabled = enabled;
    }

    boolean isEnabled() {
        return enabled.get();
    }

    @Override
    public byte[] get(String name, byte[] key) {
        return read(() -> delegate.get(name, key));
    }

    @Override
    public byte[] get(String name, byte[] key, Duration ttl) {
        return read(() -> delegate.get(name, key, ttl));
    }

    @Override
    public void put(String name, byte[] key, byte[] value, Duration ttl) {
        write(() -> delegate.put(name, key, value, ttl));
    }

    @Override
    public byte[] putIfAbsent(String name, byte[] key, byte[] value, Duration ttl) {
        return read(() -> delegate.putIfAbsent(name, key, value, ttl));
    }

    @Override
    public void remove(String name, byte[] key) {
        write(() -> delegate.remove(name, key));
    }

    @Override
    public void clean(String name, byte[] pattern) {
        write(() -> delegate.clean(name, pattern));
    }

    @Override
    public boolean supportsAsyncRetrieve() {
        return delegate.supportsAsyncRetrieve();
    }

    @Override
    public CompletableFuture<byte[]> retrieve(String name, byte[] key, Duration ttl) {
        return async(() -> delegate.retrieve(name, key, ttl));
    }

    @Override
    public CompletableFuture<Void> store(String name, byte[] key, byte[] value, Duration ttl) {
        return async(() -> delegate.store(name, key, value, ttl));
    }

    @Override
    public CacheStatistics getCacheStatistics(String name) {
        return delegate.getCacheStatistics(name);
    }

    @Override
    public void clearStatistics(String name) {
        delegate.clearStatistics(name);
    }

    @Override
    public RedisCacheWriter withStatisticsCollector(CacheStatisticsCollector collector) {
        return new DisposableCacheWriter(delegate.withStatisticsCollector(collector), enabled);
    }

    private byte[] read(Supplier<byte[]> operation) {
        if (!enabled.get()) return null;
        try {
            byte[] result = operation.get();
            // A concurrent invalidation failure also makes an in-flight cache hit unusable.
            return enabled.get() ? result : null;
        } catch (RuntimeException failure) {
            disable();
            return null;
        }
    }

    private void write(Runnable operation) {
        if (!enabled.get()) return;
        try {
            operation.run();
        } catch (RuntimeException failure) {
            disable();
        }
    }

    private <T> CompletableFuture<T> async(Supplier<CompletableFuture<T>> operation) {
        if (!enabled.get()) return CompletableFuture.completedFuture(null);
        try {
            return operation
                    .get()
                    .handle(
                            (result, failure) -> {
                                if (failure != null) disable();
                                return enabled.get() ? result : null;
                            });
        } catch (RuntimeException failure) {
            disable();
            return CompletableFuture.completedFuture(null);
        }
    }

    private void disable() {
        if (enabled.compareAndSet(true, false)) {
            // No exception/key/value: a Redis error can retain credential-bearing bind data.
            log.warn("Disposable Academic cache disabled until process restart");
        }
    }
}
