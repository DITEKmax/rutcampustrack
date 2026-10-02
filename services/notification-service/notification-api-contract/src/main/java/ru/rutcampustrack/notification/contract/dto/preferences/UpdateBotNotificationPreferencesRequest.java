package ru.rutcampustrack.notification.contract.dto.preferences;

import java.time.Instant;

/** One atomic field command. requestKey identifies a callback, including HTTP retries. */
public record UpdateBotNotificationPreferencesRequest(
        String requestKey, Operation operation, String category, Boolean enabled, Instant mutedUntil, Long durationSeconds
) {
    public enum Operation {
        TOGGLE_GLOBAL, TOGGLE_CATEGORY, SET_GLOBAL, SET_CATEGORY, SET_MUTE, MUTE_FOR, CLEAR_MUTE
    }
}
