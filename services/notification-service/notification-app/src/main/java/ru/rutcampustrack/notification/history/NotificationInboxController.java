package ru.rutcampustrack.notification.history;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import ru.rutcampustrack.notification.contract.api.NotificationInboxApi;
import ru.rutcampustrack.notification.contract.dto.history.*;
import ru.rutcampustrack.notification.contract.dto.preferences.NotificationPreferencesV2Dto;
import ru.rutcampustrack.notification.preferences.NotificationPreferencesV2Service;
import ru.rutcampustrack.notification.security.*;

@RestController
public class NotificationInboxController implements NotificationInboxApi {
    private final RequestContext context;
    private final NotificationCatalog catalog;
    private final NotificationInboxService inbox;
    private final NotificationPreferencesV2Service preferences;

    public NotificationInboxController(
            RequestContext context,
            NotificationCatalog catalog,
            NotificationInboxService inbox,
            NotificationPreferencesV2Service preferences) {
        this.context = context;
        this.catalog = catalog;
        this.inbox = inbox;
        this.preferences = preferences;
    }

    @Override
    public ResponseEntity<NotificationCatalogDto> catalog() {
        context.requireSelectedRole();
        return ResponseEntity.ok()
                .cacheControl(org.springframework.http.CacheControl.noStore())
                .body(catalog.catalog(java.util.Set.of()));
    }

    @Override
    public ResponseEntity<NotificationInboxDto.Page> inbox(
            int limit, String cursor, boolean unreadOnly) {
        context.requireSelectedRole();
        return ok(inbox.list(context, limit, cursor, unreadOnly));
    }

    @Override
    public ResponseEntity<NotificationInboxDto.Count> inboxUnreadCount() {
        context.requireSelectedRole();
        return ok(inbox.count(context));
    }

    @Override
    public ResponseEntity<NotificationInboxDto.ReadResult> readState(
            String id, NotificationInboxDto.ReadState request) {
        context.requireSelectedRole();
        return ok(inbox.readState(context, id, request));
    }

    @Override
    public ResponseEntity<NotificationInboxDto.AllReadResult> inboxMarkAllRead() {
        context.requireSelectedRole();
        return ok(
                new NotificationInboxDto.AllReadResult(
                        context.requireSelectedRole(), inbox.markAllRead(context)));
    }

    @Override
    public ResponseEntity<NotificationPreferencesV2Dto> preferencesV2() {
        context.requireSelectedRole();
        return ok(preferences.get(context.getUserId()));
    }

    @Override
    public ResponseEntity<NotificationPreferencesV2Dto> updatePreferencesV2(
            NotificationPreferencesV2Dto.Update request) {
        context.requireSelectedRole();
        return ok(preferences.update(context.getUserId(), request));
    }

    private static <T> ResponseEntity<T> ok(T body) {
        return ResponseEntity.ok()
                .cacheControl(org.springframework.http.CacheControl.noStore())
                .body(body);
    }
}
