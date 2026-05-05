package ru.rutcampustrack.notification.contract.dto.preferences;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.Map;

@Schema(description = "User notification preferences shared by PWA push delivery and Telegram bot")
public record NotificationPreferencesDto(
        @Schema(description = "Per-category enabled flags. Missing categories are treated as enabled.")
        Map<String, Boolean> categories,

        @Schema(description = "UTC instant until which all user-facing notifications are muted")
        Instant mutedUntil
) {}
