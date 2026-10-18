package ru.rutcampustrack.academic.config;

import io.lettuce.core.resource.ClientResources;

import org.springframework.beans.factory.DisposableBean;
import org.springframework.cache.Cache;
import org.springframework.cache.support.NoOpCache;
import org.springframework.data.redis.cache.RedisCache;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.cache.RedisCacheWriter;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;

import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/** Owns the private cache connection without registering another RedisConnectionFactory bean. */
final class DisposableCacheManager extends RedisCacheManager implements DisposableBean {
    private final LettuceConnectionFactory connectionFactory;
    private final ClientResources resources;
    private final DisposableCacheWriter guardedWriter;

    DisposableCacheManager(
            RedisCacheWriter writer,
            RedisCacheConfiguration defaults,
            Map<String, RedisCacheConfiguration> configurations,
            LettuceConnectionFactory connectionFactory,
            ClientResources resources) {
        this(
                new DisposableCacheWriter(writer),
                defaults,
                configurations,
                connectionFactory,
                resources);
    }

    private DisposableCacheManager(
            DisposableCacheWriter writer,
            RedisCacheConfiguration defaults,
            Map<String, RedisCacheConfiguration> configurations,
            LettuceConnectionFactory connectionFactory,
            ClientResources resources) {
        super(writer, defaults, configurations);
        this.guardedWriter = writer;
        this.connectionFactory = connectionFactory;
        this.resources = resources;
    }

    @Override
    protected RedisCache createRedisCache(String name, RedisCacheConfiguration configuration) {
        return new AvailableCache(super.createRedisCache(name, configuration));
    }

    @Override
    public void destroy() throws Exception {
        try {
            connectionFactory.destroy();
        } finally {
            resources.shutdown(0, 500, TimeUnit.MILLISECONDS).await(1, TimeUnit.SECONDS);
        }
    }

    /** Consult the latch on the same handle before serialization, including after a get fails. */
    private final class AvailableCache extends RedisCache {
        private final RedisCache redis;
        private final Cache bypass;

        private AvailableCache(RedisCache redis) {
            super(redis.getName(), guardedWriter, redis.getCacheConfiguration());
            this.redis = redis;
            this.bypass = new NoOpCache(redis.getName());
        }

        private Cache current() {
            return guardedWriter.isEnabled() ? redis : bypass;
        }

        @Override
        public ValueWrapper get(Object key) {
            return current().get(key);
        }

        @Override
        public <T> T get(Object key, Class<T> type) {
            return current().get(key, type);
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T get(Object key, Callable<T> valueLoader) {
            ValueWrapper hit = get(key);
            if (hit != null) return (T) hit.get();
            T value;
            try {
                value = valueLoader.call();
            } catch (Exception failure) {
                throw new ValueRetrievalException(key, valueLoader, failure);
            }
            put(key, value);
            return value;
        }

        @Override
        public CompletableFuture<ValueWrapper> retrieve(Object key) {
            return guardedWriter.isEnabled()
                    ? redis.retrieve(key)
                    : CompletableFuture.completedFuture(null);
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> CompletableFuture<T> retrieve(
                Object key, Supplier<CompletableFuture<T>> valueLoader) {
            CompletableFuture<?> lookup = retrieve(key);
            if (lookup == null) return load(key, valueLoader);
            return lookup.thenCompose(
                    hit ->
                            hit == null
                                    ? load(key, valueLoader)
                                    : CompletableFuture.completedFuture(
                                            (T)
                                                    (hit instanceof ValueWrapper wrapped
                                                            ? wrapped.get()
                                                            : hit)));
        }

        private <T> CompletableFuture<T> load(Object key, Supplier<CompletableFuture<T>> loader) {
            return loader.get()
                    .thenCompose(
                            value -> {
                                if (!guardedWriter.isEnabled()) {
                                    return CompletableFuture.completedFuture(value);
                                }
                                Object storeValue = preProcessCacheValue(value);
                                if (storeValue == null && !isAllowNullValues()) {
                                    return CompletableFuture.completedFuture(value);
                                }
                                return guardedWriter
                                        .store(
                                                getName(),
                                                serializeCacheKey(createCacheKey(key)),
                                                serializeCacheValue(storeValue),
                                                getCacheConfiguration()
                                                        .getTtlFunction()
                                                        .getTimeToLive(key, value))
                                        .thenApply(ignored -> value);
                            });
        }

        @Override
        public void put(Object key, Object value) {
            current().put(key, value);
        }

        @Override
        public ValueWrapper putIfAbsent(Object key, Object value) {
            return current().putIfAbsent(key, value);
        }

        @Override
        public void evict(Object key) {
            current().evict(key);
        }

        @Override
        public boolean evictIfPresent(Object key) {
            return current().evictIfPresent(key);
        }

        @Override
        public void clear() {
            current().clear();
        }

        @Override
        public boolean invalidate() {
            return current().invalidate();
        }
    }
}
