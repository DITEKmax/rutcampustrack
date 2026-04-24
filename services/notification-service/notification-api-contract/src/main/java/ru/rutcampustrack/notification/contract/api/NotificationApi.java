package ru.rutcampustrack.notification.contract.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedResourcesAssembler;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.PagedModel;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import ru.rutcampustrack.notification.contract.dto.history.NotificationHistoryDto;
import ru.rutcampustrack.notification.contract.dto.history.UnreadCountDto;

/**
 * REST API contract для notification history (M10 / NEW-167).
 *
 * <p>Contract-first: controller implements, маппинги только здесь.
 * userId берётся из JWT-контекста (не параметр) — каждый user видит
 * только собственную историю.
 */
@Tag(name = "Notifications", description = "История уведомлений пользователя (M10)")
@RequestMapping("/notifications")
public interface NotificationApi {

    @Operation(summary = "Пагинированный список уведомлений текущего user'а")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Страница notification_history"),
            @ApiResponse(responseCode = "401", description = "Неавторизован")
    })
    @GetMapping
    ResponseEntity<PagedModel<EntityModel<NotificationHistoryDto>>> list(
            @RequestParam(defaultValue = "false") boolean unreadOnly,
            Pageable pageable,
            PagedResourcesAssembler<NotificationHistoryDto> assembler);

    @Operation(summary = "Счётчик непрочитанных уведомлений (Caffeine-cached 30s)")
    @ApiResponse(responseCode = "200", description = "Количество непрочитанных")
    @GetMapping("/unread-count")
    ResponseEntity<UnreadCountDto> unreadCount();

    @Operation(summary = "Пометить уведомление прочитанным")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Помечено read"),
            @ApiResponse(responseCode = "403", description = "Чужое уведомление"),
            @ApiResponse(responseCode = "404", description = "Не найдено")
    })
    @PatchMapping("/{id}/read")
    ResponseEntity<Void> markAsRead(@PathVariable String id);

    @Operation(summary = "Пометить все уведомления прочитанными (bulk)")
    @ApiResponse(responseCode = "204", description = "Все помечены read")
    @PostMapping("/mark-all-read")
    ResponseEntity<Void> markAllRead();
}
