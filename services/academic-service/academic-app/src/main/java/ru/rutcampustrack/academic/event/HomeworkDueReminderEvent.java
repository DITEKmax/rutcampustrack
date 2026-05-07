package ru.rutcampustrack.academic.event;

import com.fasterxml.jackson.annotation.JsonProperty;

public class HomeworkDueReminderEvent extends DomainEvent {

    public record Payload(
            @JsonProperty("user_id") Long userId,
            @JsonProperty("group_id") Long groupId,
            @JsonProperty("semester_id") Long semesterId,
            @JsonProperty("days_before_due") Integer daysBeforeDue,
            @JsonProperty("due_date") String dueDate,
            HomeworkNotificationItem homework
    ) {
    }

    public HomeworkDueReminderEvent(Object source,
                                    Long userId,
                                    Long groupId,
                                    Long semesterId,
                                    Integer daysBeforeDue,
                                    String dueDate,
                                    HomeworkNotificationItem homework) {
        super(source, "homework.due_reminder",
                new Payload(userId, groupId, semesterId, daysBeforeDue, dueDate, homework));
    }
}
