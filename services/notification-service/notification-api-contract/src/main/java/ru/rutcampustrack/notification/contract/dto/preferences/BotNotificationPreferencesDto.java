package ru.rutcampustrack.notification.contract.dto.preferences;

import java.time.Instant;
import java.util.Map;

/** Private Bot -> Notification contract; a snapshot without category is not a delivery permit. */
public record BotNotificationPreferencesDto(
        long userId, long telegramId, boolean globalEnabled,
        Map<String, Boolean> categories, Instant mutedUntil,
        Map<String, Boolean> canonicalCategories, Instant canonicalMutedUntil,
        Boolean eligible
) {}
