package ru.rutcampustrack.notification.contract.dto.history;

import java.time.Instant;
import java.util.List;

/** Role-bound factual presentation. IDs in actions are strings, never executable URLs. */
public final class NotificationInboxDto {
    private NotificationInboxDto() {}

    public record Page(String role, List<Item> items, String nextCursor) {}

    public record Item(
            String id,
            String type,
            String heading,
            @com.fasterxml.jackson.annotation.JsonInclude(
                            com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
                    String summary,
            Instant sentAt,
            Instant orderAt,
            Instant readAt,
            String revision,
            List<Detail> details,
            Action action) {}

    public record Detail(String label, String value) {}

    @com.fasterxml.jackson.annotation.JsonInclude(
            com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    public record Action(
            String kind,
            String role,
            String lessonId,
            String lessonDate,
            String requestId,
            String requestKind,
            String homeworkId,
            String groupId,
            String weekStart) {}

    public record Count(String role, long count) {}

    public record ReadState(
            @com.fasterxml.jackson.annotation.JsonProperty(required = true) Boolean read,
            @com.fasterxml.jackson.annotation.JsonProperty(required = true)
                    String expectedRevision) {}

    public record ReadResult(String role, Item item, long unreadCount) {}

    public record AllReadResult(String role, long unreadCount) {}
}
