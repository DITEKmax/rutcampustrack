package ru.rutcampustrack.notification.contract.api;

import org.springframework.web.bind.annotation.*;
import ru.rutcampustrack.notification.contract.dto.preferences.BotNotificationPreferencesDto;
import ru.rutcampustrack.notification.contract.dto.preferences.UpdateBotNotificationPreferencesRequest;

/** Internal transport exception; never routed by the public Gateway. */
@RequestMapping("/internal/bot/notification-preferences/{userId}/{telegramId}")
public interface BotNotificationPreferencesApi {
    @GetMapping
    BotNotificationPreferencesDto getPreferences(
            @RequestHeader(value = "X-Bot-Preferences-Token", required = false) String token,
            @PathVariable("userId") long userId, @PathVariable("telegramId") long telegramId,
            @RequestParam(value = "category", required = false) String category);

    @PostMapping
    BotNotificationPreferencesDto updatePreferences(
            @RequestHeader(value = "X-Bot-Preferences-Token", required = false) String token,
            @PathVariable("userId") long userId, @PathVariable("telegramId") long telegramId,
            @RequestBody UpdateBotNotificationPreferencesRequest request);
}
