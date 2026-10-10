package ru.rutcampustrack.notification.preferences;

import org.springframework.stereotype.Service;
import ru.rutcampustrack.notification.contract.dto.preferences.NotificationPreferencesDto;
import ru.rutcampustrack.notification.contract.dto.preferences.UpdateNotificationPreferencesRequest;

import java.time.Clock;
import java.time.Instant;

@Service
public class NotificationPreferencesService {
    private final NotificationPreferencesStore store;
    private final Clock clock;
    private NotificationPreferencesV2Service v2;

    public NotificationPreferencesService(NotificationPreferencesStore store, Clock clock) {
        this.store = store;
        this.clock = clock;
    }

    @org.springframework.beans.factory.annotation.Autowired
    public NotificationPreferencesService(NotificationPreferencesStore store, Clock clock, NotificationPreferencesV2Service v2) {
        this(store, clock); this.v2 = v2;
    }

    public NotificationPreferencesDto getForUser(long userId) {
        return active(store.user(userId));
    }

    public NotificationPreferencesDto updateForUser(long userId, UpdateNotificationPreferencesRequest request) {
        return active(store.patchUser(userId, request, Instant.now(clock)));
    }

    public boolean isEnabledForUser(Long userId, String eventType) {
        if (v2 != null && !"excuse.decided".equals(eventType) && !"late_checkin.decided".equals(eventType)) {
            return isEnabledForUser(userId, eventType, java.util.Map.of());
        }
        String category = categoryForEvent(eventType);
        if (userId == null || userId <= 0 || category == null) {
            throw new IllegalArgumentException("Classified notification recipient is required");
        }
        return isCategoryEnabledForUser(userId, category);
    }

    public boolean isEnabledForUser(Long userId, String eventType, java.util.Map<String, Object> payload) {
        if (v2 == null) return isEnabledForUser(userId, eventType);
        if (userId == null || userId <= 0) throw new IllegalArgumentException("Classified notification recipient is required");
        var type = ru.rutcampustrack.notification.history.NotificationCatalog.eventType(eventType, payload);
        return type.isPresent() && v2.appEnabled(userId, type.get().name());
    }

    public boolean isReminderEnabledForUser(long userId) {
        if (v2 != null) return v2.appEnabled(userId, "LESSON_REMINDER");
        return isCategoryEnabledForUser(userId, "reminders");
    }

    public boolean isCategoryEnabledForUser(long userId, String category) {
        if ((category == null || !NotificationPreferencesStore.CATEGORIES.contains(category))) {
            throw new IllegalArgumentException("Unknown notification category");
        }
        NotificationPreferencesDto prefs = getForUser(userId);
        return prefs.mutedUntil() == null && prefs.categories().get(category);
    }

    public String categoryForEvent(String eventType) {
        return switch (eventType) {
            case "lesson.started", "lesson.reminder" -> "reminders";
            case "lesson.blocked", "lesson.cancelled", "lesson.closed" -> "lessons";
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

    private NotificationPreferencesDto active(NotificationPreferencesDto prefs) {
        Instant mute = prefs.mutedUntil();
        return new NotificationPreferencesDto(prefs.categories(),
                mute != null && mute.isAfter(Instant.now(clock)) ? mute : null);
    }
}
