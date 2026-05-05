package ru.rutcampustrack.notification.preferences;

import org.springframework.hateoas.EntityModel;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import ru.rutcampustrack.notification.contract.api.NotificationPreferencesApi;
import ru.rutcampustrack.notification.contract.dto.preferences.NotificationPreferencesDto;
import ru.rutcampustrack.notification.contract.dto.preferences.UpdateNotificationPreferencesRequest;
import ru.rutcampustrack.notification.contract.enums.UserRole;
import ru.rutcampustrack.notification.security.RequestContext;
import ru.rutcampustrack.notification.security.RequireRole;

@RestController
public class NotificationPreferencesController implements NotificationPreferencesApi {

    private final NotificationPreferencesService service;
    private final RequestContext requestContext;

    public NotificationPreferencesController(NotificationPreferencesService service,
                                             RequestContext requestContext) {
        this.service = service;
        this.requestContext = requestContext;
    }

    @Override
    @RequireRole({UserRole.STUDENT})
    public ResponseEntity<EntityModel<NotificationPreferencesDto>> getPreferences() {
        return ResponseEntity.ok(EntityModel.of(service.getForUser(requestContext.getUserId())));
    }

    @Override
    @RequireRole({UserRole.STUDENT})
    public ResponseEntity<EntityModel<NotificationPreferencesDto>> updatePreferences(
            @RequestBody UpdateNotificationPreferencesRequest request) {
        NotificationPreferencesDto dto = service.updateForUser(requestContext.getUserId(), request);
        return ResponseEntity.ok(EntityModel.of(dto));
    }
}
