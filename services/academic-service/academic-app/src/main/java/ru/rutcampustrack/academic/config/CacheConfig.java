package ru.rutcampustrack.academic.config;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.EnableCaching;
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

    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        ObjectMapper om = new ObjectMapper()
                .registerModule(new JavaTimeModule())
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
                .build();
    }
}
