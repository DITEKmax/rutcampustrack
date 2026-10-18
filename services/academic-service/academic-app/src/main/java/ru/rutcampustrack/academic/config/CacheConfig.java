package ru.rutcampustrack.academic.config;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import com.fasterxml.jackson.databind.jsontype.PolymorphicTypeValidator;
import com.fasterxml.jackson.datatype.hibernate6.Hibernate6Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import io.lettuce.core.ClientOptions;
import io.lettuce.core.SocketOptions;
import io.lettuce.core.TimeoutOptions;
import io.lettuce.core.resource.DefaultClientResources;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.support.NoOpCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.BatchStrategies;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheWriter;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

@Configuration
@EnableCaching
public class CacheConfig {

    @Value("${rct.cache.redis.host:}")
    private String cacheHost;

    @Value("${rct.cache.redis.port:6379}")
    private int cachePort;

    @Value("${rct.cache.redis.username:}")
    private String cacheUsername;

    @Value("${rct.cache.redis.password:}")
    private String cachePassword;

    @Value("${cache.ttl.groups:PT5M}")
    private Duration groupsTtl;

    @Value("${cache.ttl.group-members:PT5M}")
    private Duration groupMembersTtl;

    @Value("${cache.ttl.users:PT5M}")
    private Duration usersTtl;

    @Value("${cache.ttl.active-semester:PT10M}")
    private Duration activeSemesterTtl;

    @Value("${cache.ttl.campus-geofence:PT1H}")
    private Duration campusGeofenceTtl;

    @Value("${cache.ttl.rbac:PT1M}")
    private Duration rbacTtl;

    @Value("${cache.ttl.subject:PT10M}")
    private Duration subjectTtl;

    /**
     * Disposable cache has its own process and private client. Boot's default Redis factory and
     * templates continue to serve critical limits/tickets; missing cache configuration never
     * silently routes cache traffic into that critical pool.
     */
    @Bean
    public CacheManager cacheManager() {
        if (cacheHost.isBlank()) {
            return new NoOpCacheManager();
        }

        // Use OBJECT_AND_NON_CONCRETE default typing to preserve class information for polymorphic
        // types (List, Map, abstract classes) while avoiding conflicts with JavaTimeModule.
        // OffsetDateTime is declared as a concrete type in entity fields, so it won't receive
        // @class type info — JavaTimeModule serializes it as an ISO string without interference.
        // Hibernate6Module serializes proxies as their real class — prevents Hibernate proxy
        // class names (Group$$HibernateProxy$...) from being recorded in the @class field.
        Hibernate6Module hibernate6Module = new Hibernate6Module();
        hibernate6Module.enable(Hibernate6Module.Feature.FORCE_LAZY_LOADING);

        // M06 G8a + G9 hot-patch (security-audit H3): narrow polymorphic
        // whitelist. `java.util.` was too broad — пропускало gadget-классы
        // типа ServiceLoader$LazyIterator, PriorityQueue с custom Comparator,
        // FutureTask. Теперь — explicit список collection-типов, дат и BigDecimal.
        //
        // Block'ирует gadget-chains через произвольные ClassPath-классы в
        // @class поле Redis-payload'а (защита от attacker с RW access к Redis).
        // String/Integer/Long/Boolean — final, не требуют @class; не в списке.
        PolymorphicTypeValidator ptv =
                BasicPolymorphicTypeValidator.builder()
                        .allowIfSubType("ru.rutcampustrack.")
                        // Collection types — нужны для List/Set/Map поля в DTO.
                        .allowIfSubType("java.util.ArrayList")
                        .allowIfSubType("java.util.LinkedList")
                        .allowIfSubType("java.util.HashMap")
                        .allowIfSubType("java.util.LinkedHashMap")
                        .allowIfSubType("java.util.TreeMap")
                        .allowIfSubType("java.util.HashSet")
                        .allowIfSubType("java.util.LinkedHashSet")
                        .allowIfSubType("java.util.TreeSet")
                        .allowIfSubType("java.util.Collections$")
                        .allowIfSubType("java.util.Optional")
                        // java.time types для OffsetDateTime/Instant/LocalDate.
                        .allowIfSubType("java.time.")
                        // BigDecimal/BigInteger для numeric-полей.
                        .allowIfSubType("java.math.BigDecimal")
                        .allowIfSubType("java.math.BigInteger")
                        .build();

        ObjectMapper om =
                new ObjectMapper()
                        .registerModule(new JavaTimeModule())
                        .registerModule(hibernate6Module)
                        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                        .activateDefaultTyping(
                                ptv,
                                ObjectMapper.DefaultTyping.NON_FINAL,
                                JsonTypeInfo.As.PROPERTY);

        GenericJackson2JsonRedisSerializer serializer = new GenericJackson2JsonRedisSerializer(om);

        RedisCacheConfiguration base =
                RedisCacheConfiguration.defaultCacheConfig()
                        .prefixCacheNameWith("rct:cache:" + UUID.randomUUID() + ":")
                        .disableCachingNullValues()
                        .serializeValuesWith(
                                RedisSerializationContext.SerializationPair.fromSerializer(
                                        serializer));

        var resources =
                DefaultClientResources.builder()
                        .ioThreadPoolSize(2)
                        .computationThreadPoolSize(2)
                        .build();
        var standalone = new RedisStandaloneConfiguration(cacheHost, cachePort);
        if (!cacheUsername.isBlank()) standalone.setUsername(cacheUsername);
        if (!cachePassword.isEmpty()) standalone.setPassword(cachePassword);
        var options =
                ClientOptions.builder()
                        .autoReconnect(false)
                        .disconnectedBehavior(ClientOptions.DisconnectedBehavior.REJECT_COMMANDS)
                        .requestQueueSize(64)
                        .timeoutOptions(TimeoutOptions.enabled(Duration.ofMillis(200)))
                        .socketOptions(
                                SocketOptions.builder()
                                        .connectTimeout(Duration.ofMillis(500))
                                        .build())
                        .build();
        var client =
                LettuceClientConfiguration.builder()
                        .clientResources(resources)
                        .clientOptions(options)
                        .clientName("academic-disposable-cache")
                        .commandTimeout(Duration.ofMillis(200))
                        .shutdownQuietPeriod(Duration.ZERO)
                        .shutdownTimeout(Duration.ofMillis(500))
                        .build();
        var factory = new LettuceConnectionFactory(standalone, client);
        try {
            factory.afterPropertiesSet();
            // SCAN is bounded per batch and scoped to this namespace. Never KEYS or FLUSHDB.
            var writer =
                    RedisCacheWriter.nonLockingRedisCacheWriter(factory, BatchStrategies.scan(128));
            return new DisposableCacheManager(
                    writer,
                    base.entryTtl(Duration.ofMinutes(5)),
                    Map.of(
                            "groups", base.entryTtl(groupsTtl),
                            "group_members", base.entryTtl(groupMembersTtl),
                            "users", base.entryTtl(usersTtl),
                            "active_semester", base.entryTtl(activeSemesterTtl),
                            "campus_geofence", base.entryTtl(campusGeofenceTtl),
                            "rbac", base.entryTtl(rbacTtl),
                            "subject", base.entryTtl(subjectTtl)),
                    factory,
                    resources);
        } catch (RuntimeException failure) {
            factory.destroy();
            resources.shutdown(0, 500, java.util.concurrent.TimeUnit.MILLISECONDS);
            throw failure;
        }
    }
}
