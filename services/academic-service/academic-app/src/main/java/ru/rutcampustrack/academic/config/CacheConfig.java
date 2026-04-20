package ru.rutcampustrack.academic.config;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import com.fasterxml.jackson.datatype.hibernate6.Hibernate6Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.support.NoOpCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;

import java.time.Duration;

@Configuration
@EnableCaching
public class CacheConfig {

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
     * Redis-backed CacheManager when RedisConnectionFactory is available, NoOpCacheManager otherwise.
     * Using ObjectProvider avoids @ConditionalOnBean timing issues in user @Configuration classes:
     * Spring evaluates @ConditionalOnBean before autoconfiguration registers RedisConnectionFactory,
     * causing the condition to fail even when Redis IS configured. ObjectProvider resolves lazily
     * at bean-creation time, after all beans are registered.
     *
     * Non-cache integration tests exclude RedisAutoConfiguration → no factory → NoOpCacheManager.
     * Cache integration tests keep Redis → factory is present → RedisCacheManager.
     */
    @Bean
    public CacheManager cacheManager(ObjectProvider<RedisConnectionFactory> connectionFactoryProvider) {
        RedisConnectionFactory connectionFactory = connectionFactoryProvider.getIfAvailable();
        if (connectionFactory == null) {
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

        ObjectMapper om = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .registerModule(hibernate6Module)
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .activateDefaultTyping(
                        LaissezFaireSubTypeValidator.instance,
                        ObjectMapper.DefaultTyping.NON_FINAL,
                        JsonTypeInfo.As.PROPERTY);

        GenericJackson2JsonRedisSerializer serializer =
                new GenericJackson2JsonRedisSerializer(om);

        RedisCacheConfiguration base = RedisCacheConfiguration.defaultCacheConfig()
                .disableCachingNullValues()
                .serializeValuesWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(serializer));

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(base.entryTtl(Duration.ofMinutes(5)))
                .withCacheConfiguration("groups", base.entryTtl(groupsTtl))
                .withCacheConfiguration("group_members", base.entryTtl(groupMembersTtl))
                .withCacheConfiguration("users", base.entryTtl(usersTtl))
                .withCacheConfiguration("active_semester", base.entryTtl(activeSemesterTtl))
                .withCacheConfiguration("campus_geofence", base.entryTtl(campusGeofenceTtl))
                .withCacheConfiguration("rbac", base.entryTtl(rbacTtl))
                .withCacheConfiguration("subject", base.entryTtl(subjectTtl))
                .build();
    }
}
