package ru.rutcampustrack.notification.contract.dto.preferences;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.Map;

@Schema(description = "Notification preference update request")
public record UpdateNotificationPreferencesRequest(
        @Schema(description = "Per-category enabled flags. Unknown categories are ignored by the service.")
        Map<String, Boolean> categories,

        @Schema(description = "UTC instant until which all user-facing notifications are muted. Null removes mute.")
        Instant mutedUntil
) {}
