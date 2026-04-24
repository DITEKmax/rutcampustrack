package ru.rutcampustrack.notification.history;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedResourcesAssembler;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.PagedModel;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import ru.rutcampustrack.notification.contract.api.NotificationApi;
import ru.rutcampustrack.notification.contract.dto.history.NotificationHistoryDto;
import ru.rutcampustrack.notification.contract.dto.history.UnreadCountDto;
import ru.rutcampustrack.notification.contract.enums.UserRole;
import ru.rutcampustrack.notification.exception.AccessDeniedException;
import ru.rutcampustrack.notification.security.RequestContext;
import ru.rutcampustrack.notification.security.RequireRole;

/**
 * REST controller для notification history (M10 G5).
 *
 * <p>userId — из {@link RequestContext} (Gateway-inject'ит из JWT).
 * Каждый user видит только собственную историю.
 */
@RestController
public class NotificationController implements NotificationApi {

    private final NotificationHistoryService service;
    private final RequestContext requestContext;

    public NotificationController(NotificationHistoryService service,
                                  RequestContext requestContext) {
        this.service = service;
        this.requestContext = requestContext;
    }

    @Override
    @RequireRole({UserRole.STUDENT, UserRole.TEACHER, UserRole.ADMIN})
    public ResponseEntity<PagedModel<EntityModel<NotificationHistoryDto>>> list(
            boolean unreadOnly,
            Pageable pageable,
            PagedResourcesAssembler<NotificationHistoryDto> assembler) {
        Long userId = requestContext.getUserId();
        Page<NotificationHistoryDto> page = service.list(userId, unreadOnly, pageable);
        return ResponseEntity.ok(assembler.toModel(page));
    }

    @Override
    @RequireRole({UserRole.STUDENT, UserRole.TEACHER, UserRole.ADMIN})
    public ResponseEntity<UnreadCountDto> unreadCount() {
        return ResponseEntity.ok(service.getUnreadCount(requestContext.getUserId()));
    }

    @Override
    @RequireRole({UserRole.STUDENT, UserRole.TEACHER, UserRole.ADMIN})
    public ResponseEntity<Void> markAsRead(@PathVariable String id) {
        boolean ok = service.markAsRead(id, requestContext.getUserId());
        if (!ok) {
            // Чужое / несуществующее уведомление. 403 (AccessDenied handler)
            // вместо 404 чтобы не раскрывать существование чужих notification
            // документов по id.
            throw new AccessDeniedException("Notification not accessible");
        }
        return ResponseEntity.noContent().build();
    }

    @Override
    @RequireRole({UserRole.STUDENT, UserRole.TEACHER, UserRole.ADMIN})
    public ResponseEntity<Void> markAllRead() {
        service.markAllRead(requestContext.getUserId());
        return ResponseEntity.noContent().build();
    }
}
