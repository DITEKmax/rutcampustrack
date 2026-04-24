package ru.rutcampustrack.notification.contract.dto.history;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Response DTO для /unread-count endpoint (M10).
 *
 * <p>Без HATEOAS links — badge endpoint, полный граф навигации не нужен.
 * CLAUDE.md допускает record для simple response без RepresentationModel.
 */
@Schema(description = "Количество непрочитанных уведомлений пользователя (для badge-индикатора)")
public record UnreadCountDto(
        @Schema(description = "Число непрочитанных уведомлений", example = "7")
        long count
) {
}
