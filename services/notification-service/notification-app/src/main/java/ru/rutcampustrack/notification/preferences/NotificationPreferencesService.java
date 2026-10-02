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

    public NotificationPreferencesService(NotificationPreferencesStore store, Clock clock) {
        this.store = store;
        this.clock = clock;
    }

    public NotificationPreferencesDto getForUser(long userId) {
        return active(store.user(userId));
    }

    public NotificationPreferencesDto updateForUser(long userId, UpdateNotificationPreferencesRequest request) {
        return active(store.patchUser(userId, request, Instant.now(clock)));
    }

    public boolean isEnabledForUser(Long userId, String eventType) {
        String category = categoryForEvent(eventType);
        if (userId == null || userId <= 0 || category == null) {
            throw new IllegalArgumentException("Classified notification recipient is required");
        }
        return isCategoryEnabledForUser(userId, category);
    }

    public boolean isReminderEnabledForUser(long userId) {
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
