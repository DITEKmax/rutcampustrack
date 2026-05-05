package ru.rutcampustrack.notification.contract.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.hateoas.EntityModel;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import ru.rutcampustrack.notification.contract.dto.preferences.NotificationPreferencesDto;
import ru.rutcampustrack.notification.contract.dto.preferences.UpdateNotificationPreferencesRequest;

@Tag(name = "Notification Preferences", description = "User notification delivery settings")
@RequestMapping("/notifications/preferences")
public interface NotificationPreferencesApi {

    @Operation(summary = "Get current user's notification preferences")
    @ApiResponse(responseCode = "200", description = "Preferences returned")
    @GetMapping
    ResponseEntity<EntityModel<NotificationPreferencesDto>> getPreferences();

    @Operation(summary = "Replace current user's notification preferences")
    @ApiResponse(responseCode = "200", description = "Preferences saved")
    @PutMapping
    ResponseEntity<EntityModel<NotificationPreferencesDto>> updatePreferences(
            @RequestBody UpdateNotificationPreferencesRequest request);
}
