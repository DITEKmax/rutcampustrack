package ru.rutcampustrack.notification.history;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * Caffeine L1 cache для unread-count endpoint (M10 G4 / P2-10/3).
 *
 * <p>Single-instance assumption: у notification-web в MVP один replica,
 * поэтому локальный кэш без cluster-sync достаточно. Если понадобится
 * scale-out → миграция на Redis (см. OWNER-ANSWERS P2-6/4).
 *
 * <p>TTL 30s соответствует badge-UX: приемлемая «задержка» между
 * реальным unread-count и отображаемым в UI; STOMP publisher
 * invalidate'ит cache мгновенно при новом событии ({@link
 * NotificationHistoryService}).
 */
@Configuration
@EnableCaching
public class CaffeineConfig {

    public static final String UNREAD_COUNT_CACHE = "unread-count";

    @Bean
    public CacheManager notificationCacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager(UNREAD_COUNT_CACHE);
        manager.setCaffeine(Caffeine.newBuilder()
                .maximumSize(10_000)
                .expireAfterWrite(30, TimeUnit.SECONDS));
        return manager;
    }
}
