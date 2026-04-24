package ru.rutcampustrack.notification.contract.dto.history;

/**
 * Response DTO для /unread-count endpoint (M10).
 *
 * <p>Без HATEOAS links — badge endpoint, полный граф навигации не нужен.
 * CLAUDE.md допускает record для simple response без RepresentationModel.
 */
public record UnreadCountDto(long count) {
}
