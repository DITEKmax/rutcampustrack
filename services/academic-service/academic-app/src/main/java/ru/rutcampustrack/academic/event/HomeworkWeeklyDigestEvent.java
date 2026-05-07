package ru.rutcampustrack.academic.event;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public class HomeworkWeeklyDigestEvent extends DomainEvent {

    public record Payload(
            @JsonProperty("user_id") Long userId,
            @JsonProperty("group_id") Long groupId,
            @JsonProperty("semester_id") Long semesterId,
            @JsonProperty("week_start") String weekStart,
            @JsonProperty("week_end") String weekEnd,
            @JsonProperty("total_count") Integer totalCount,
            List<HomeworkNotificationItem> items
    ) {
    }

    public HomeworkWeeklyDigestEvent(Object source,
                                     Long userId,
                                     Long groupId,
                                     Long semesterId,
                                     String weekStart,
                                     String weekEnd,
                                     List<HomeworkNotificationItem> items) {
        super(source, "homework.weekly_digest",
                new Payload(userId, groupId, semesterId, weekStart, weekEnd, items.size(), List.copyOf(items)));
    }
}
