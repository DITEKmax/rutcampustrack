package ru.rutcampustrack.notification.preferences;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.RedisSystemException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import ru.rutcampustrack.notification.contract.dto.preferences.NotificationPreferencesDto;
import ru.rutcampustrack.notification.contract.dto.preferences.UpdateNotificationPreferencesRequest;

import java.time.Clock;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

@Service
@Slf4j
public class NotificationPreferencesService {

    private static final String KEY_PREFIX = "notif:prefs:user:";
    private static final String MUTE_UNTIL_FIELD = "mute_until";
    private static final String DISABLED_VALUE = "off";

    private static final Set<String> KNOWN_CATEGORIES = Set.of(
            "lessons",
            "reminders",
            "homework",
            "tickets",
            "schedule",
            "group"
    );

    private final StringRedisTemplate redis;
    private final Clock clock;

    public NotificationPreferencesService(StringRedisTemplate redis, Clock clock) {
        this.redis = redis;
        this.clock = clock;
    }

    public NotificationPreferencesDto getForUser(long userId) {
        Map<Object, Object> values = readHash(userId);
        return toDto(values);
    }

    public NotificationPreferencesDto updateForUser(long userId, UpdateNotificationPreferencesRequest request) {
        String key = key(userId);
        try {
            Map<String, String> updates = new LinkedHashMap<>();
            if (request.categories() != null) {
                for (String category : KNOWN_CATEGORIES) {
                    Boolean enabled = request.categories().get(category);
                    if (enabled != null) {
                        updates.put(category, enabled ? "on" : DISABLED_VALUE);
                    }
                }
            }
            if (!updates.isEmpty()) {
                redis.opsForHash().putAll(key, updates);
            }

            if (request.mutedUntil() == null || !request.mutedUntil().isAfter(Instant.now(clock))) {
                redis.opsForHash().delete(key, MUTE_UNTIL_FIELD);
            } else {
                redis.opsForHash().put(key, MUTE_UNTIL_FIELD, request.mutedUntil().toString());
            }
        } catch (RuntimeException ex) {
            log.warn("Failed to update notification preferences for user {}: {}", userId, ex.toString());
            throw ex;
        }
        return getForUser(userId);
    }

    public boolean isEnabledForUser(Long userId, String eventType) {
        if (userId == null) {
            return true;
        }
        String category = categoryForEvent(eventType);
        if (category == null) {
            return true;
        }

        Map<Object, Object> values;
        try {
            values = redis.opsForHash().entries(key(userId));
        } catch (RuntimeException ex) {
            if (isRedisUnavailable(ex)) {
                log.warn("Notification preferences fail-open for user {} event {}: {}",
                        userId, eventType, ex.toString());
                return true;
            }
            throw ex;
        }

        Instant mutedUntil = parseInstant(asString(values.get(MUTE_UNTIL_FIELD)));
        if (mutedUntil != null && mutedUntil.isAfter(Instant.now(clock))) {
            return false;
        }
        return !DISABLED_VALUE.equals(asString(values.get(category)));
    }

    public String categoryForEvent(String eventType) {
        return switch (eventType) {
            case "lesson.started", "lesson.reminder" -> "reminders";
            case "lesson.cancelled" -> "lessons";
            case "lesson.one_off.created", "lesson.one_off.cancelled" -> "schedule";
            case "homework.published", "homework.updated",
                 "homework.weekly_digest", "homework.due_reminder" -> "homework";
            case "excuse.requested", "excuse.decided",
                 "late_checkin.requested", "late_checkin.decided",
                 "attendance.marked" -> "tickets";
            case "group.renamed", "group.archived" -> "group";
            default -> null;
        };
    }

    private NotificationPreferencesDto toDto(Map<Object, Object> values) {
        Map<String, Boolean> categories = new LinkedHashMap<>();
        for (String category : KNOWN_CATEGORIES) {
            categories.put(category, !DISABLED_VALUE.equals(asString(values.get(category))));
        }

        Instant mutedUntil = parseInstant(asString(values.get(MUTE_UNTIL_FIELD)));
        if (mutedUntil != null && !mutedUntil.isAfter(Instant.now(clock))) {
            mutedUntil = null;
        }
        return new NotificationPreferencesDto(categories, mutedUntil);
    }

    private Map<Object, Object> readHash(long userId) {
        try {
            return redis.opsForHash().entries(key(userId));
        } catch (RuntimeException ex) {
            if (isRedisUnavailable(ex)) {
                log.warn("Notification preferences unavailable for user {}: {}", userId, ex.toString());
                return Map.of();
            }
            throw ex;
        }
    }

    private boolean isRedisUnavailable(RuntimeException ex) {
        return ex instanceof RedisConnectionFailureException
                || ex instanceof RedisSystemException
                || ex instanceof io.lettuce.core.RedisConnectionException
                || ex instanceof io.lettuce.core.RedisCommandTimeoutException;
    }

    private static Instant parseInstant(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException ex) {
            return null;
        }
    }

    private static String asString(Object value) {
        return value instanceof String s ? s : null;
    }

    private static String key(long userId) {
        return KEY_PREFIX + userId;
    }
}
