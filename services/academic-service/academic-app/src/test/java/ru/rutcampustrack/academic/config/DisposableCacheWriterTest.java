package ru.rutcampustrack.academic.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.lettuce.core.resource.ClientResources;

import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheWriter;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair;
import org.springframework.data.redis.serializer.RedisSerializer;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicInteger;

class DisposableCacheWriterTest {
    private final RedisCacheWriter redis = mock(RedisCacheWriter.class);
    private final DisposableCacheWriter writer = new DisposableCacheWriter(redis);

    @Test
    void failedGetLoadsAuthorityOnceAndNeverReadsRecoveredStaleCache() {
        when(redis.get(anyString(), any(byte[].class))).thenThrow(unavailable());
        Cache cache = cache();
        AtomicInteger reads = new AtomicInteger();

        assertThat(cache.get("key", () -> "authority-" + reads.incrementAndGet()))
                .isEqualTo("authority-1");
        when(redis.get(anyString(), any(byte[].class))).thenReturn(bytes("stale"));
        assertThat(cache.get("key", () -> "authority-" + reads.incrementAndGet()))
                .isEqualTo("authority-2");
        verify(redis).get(anyString(), any(byte[].class));
        verify(redis, never()).put(anyString(), any(), any(), any());
    }

    @Test
    void sameCapturedHandleBypassesSerializationAfterFailureIncludingLoaderPut() {
        when(redis.get(anyString(), any(byte[].class))).thenThrow(unavailable());
        Cache captured = cache();
        Object domainValue = new Object(); // Not serializable by the configured String serializer.
        AtomicInteger reads = new AtomicInteger();

        assertThat(
                        captured.get(
                                "key",
                                () -> {
                                    reads.incrementAndGet();
                                    return domainValue;
                                }))
                .isSameAs(domainValue);
        captured.put("second-key", domainValue);
        assertThat(captured.get("key", () -> domainValue)).isSameAs(domainValue);
        assertThat(reads).hasValue(1);
        verify(redis, never()).put(anyString(), any(), any(), any());
    }

    @Test
    void failedAsyncLookupLoadsOnceAndBypassesSerializationOnSameHandle() {
        when(redis.supportsAsyncRetrieve()).thenReturn(true);
        when(redis.retrieve(anyString(), any(), any()))
                .thenReturn(CompletableFuture.failedFuture(unavailable()));
        Cache captured = cache();
        Object domainValue = new Object();
        AtomicInteger reads = new AtomicInteger();

        assertThat(
                        captured.retrieve(
                                        "key",
                                        () -> {
                                            reads.incrementAndGet();
                                            return CompletableFuture.completedFuture(domainValue);
                                        })
                                .join())
                .isSameAs(domainValue);

        assertThat(reads).hasValue(1);
        verify(redis, never()).store(anyString(), any(), any(), any());
        verify(redis, never()).put(anyString(), any(), any(), any());
    }

    @Test
    void asyncLoaderUsesNonblockingStoreAndCompletesAfterItsFuture() {
        when(redis.supportsAsyncRetrieve()).thenReturn(true);
        when(redis.retrieve(anyString(), any(), any()))
                .thenReturn(CompletableFuture.completedFuture(null));
        var stored = new CompletableFuture<Void>();
        when(redis.store(anyString(), any(), any(), any())).thenReturn(stored);
        AtomicInteger reads = new AtomicInteger();

        var result =
                cache().retrieve(
                                "key",
                                () -> {
                                    reads.incrementAndGet();
                                    return CompletableFuture.completedFuture("authority");
                                });

        assertThat(result.isDone()).isFalse();
        verify(redis, never()).put(anyString(), any(), any(), any());
        assertThat(reads).hasValue(1);
        stored.complete(null);
        assertThat(result.join()).isEqualTo("authority");
    }

    @Test
    void asyncDomainLoaderFailurePropagatesWithoutDisablingHealthyCache() {
        when(redis.supportsAsyncRetrieve()).thenReturn(true);
        when(redis.retrieve(anyString(), any(), any()))
                .thenReturn(CompletableFuture.completedFuture(null));
        Cache captured = cache();
        var domainFailure = new IllegalStateException("synthetic-domain-failure");

        assertThatThrownBy(
                        () ->
                                captured.retrieve(
                                                "key",
                                                () -> CompletableFuture.failedFuture(domainFailure))
                                        .join())
                .isInstanceOf(CompletionException.class)
                .hasCause(domainFailure);
        verify(redis, never()).store(anyString(), any(), any(), any());

        assertThat(captured.get("next-key", () -> "authority")).isEqualTo("authority");
        verify(redis).put(anyString(), any(), any(), any());
    }

    @Test
    void failedPutKeepsComputedResultWithoutRepeatingDomainLoader() {
        doThrow(unavailable()).when(redis).put(anyString(), any(), any(), any());
        Cache cache = cache();
        AtomicInteger reads = new AtomicInteger();

        assertThat(cache.get("key", () -> "authority-" + reads.incrementAndGet()))
                .isEqualTo("authority-1");
        assertThat(reads).hasValue(1);
        assertThat(cache.get("key", () -> "authority-" + reads.incrementAndGet()))
                .isEqualTo("authority-2");
        verify(redis).put(anyString(), any(), any(), any());
    }

    @Test
    void failedEvictionDoesNotResurrectOldValueAfterRedisRecovers() {
        when(redis.get(anyString(), any(byte[].class))).thenReturn(bytes("before-commit"));
        doThrow(unavailable()).when(redis).remove(anyString(), any());
        Cache cache = cache();

        assertThat(cache.get("key", String.class)).isEqualTo("before-commit");
        cache.evict("key");
        assertThat(cache.get("key", () -> "after-commit")).isEqualTo("after-commit");
        verify(redis).get(anyString(), any(byte[].class));
    }

    @Test
    void failedClearDisablesEveryCacheInTheSameProcess() {
        doThrow(unavailable()).when(redis).clean(anyString(), any());
        var manager = manager();
        Cache first = manager.getCache("groups");
        Cache second = manager.getCache("subject");
        first.clear();

        assertThat(second.get("another-key", () -> "authority")).isEqualTo("authority");
        verify(redis, never()).get(anyString(), any(byte[].class));
    }

    @Test
    void domainLoaderFailureStillReachesCallerAndDoesNotDisableHealthyCache() {
        Cache cache = cache();
        var domainFailure = new IllegalStateException("synthetic-domain-failure");
        assertThatThrownBy(
                        () ->
                                cache.get(
                                        "key",
                                        () -> {
                                            throw domainFailure;
                                        }))
                .isInstanceOf(Cache.ValueRetrievalException.class)
                .hasCause(domainFailure);

        assertThat(cache.get("key", () -> "authority")).isEqualTo("authority");
        verify(redis).put(anyString(), any(), any(), any());
    }

    @Test
    void inFlightAsyncHitIsDiscardedAfterConcurrentInvalidationFailure() {
        var pending = new CompletableFuture<byte[]>();
        when(redis.retrieve(anyString(), any(), any())).thenReturn(pending);
        doThrow(unavailable()).when(redis).remove(anyString(), any());

        var hit = writer.retrieve("groups", bytes("key"), null);
        writer.remove("groups", bytes("key"));
        pending.complete(bytes("stale"));

        assertThat(hit.join()).isNull();
    }

    @Test
    void asyncWriteFailureAlsoDisablesFutureSynchronousReads() {
        when(redis.store(anyString(), any(), any(), any()))
                .thenReturn(CompletableFuture.failedFuture(unavailable()));

        writer.store("groups", bytes("key"), bytes("value"), Duration.ofMinutes(5)).join();

        assertThat(writer.get("groups", bytes("key"))).isNull();
        verify(redis, never()).get(anyString(), any(byte[].class));
    }

    private Cache cache() {
        return manager().getCache("groups");
    }

    private DisposableCacheManager manager() {
        var configuration =
                RedisCacheConfiguration.defaultCacheConfig()
                        .serializeValuesWith(
                                SerializationPair.fromSerializer(RedisSerializer.string()));
        var manager =
                new DisposableCacheManager(
                        redis,
                        configuration,
                        Map.of(),
                        mock(LettuceConnectionFactory.class),
                        mock(ClientResources.class));
        manager.afterPropertiesSet();
        return manager;
    }

    private static byte[] bytes(String value) {
        return RedisSerializer.string().serialize(value);
    }

    private static RedisConnectionFailureException unavailable() {
        return new RedisConnectionFailureException("synthetic cache unavailable");
    }
}
