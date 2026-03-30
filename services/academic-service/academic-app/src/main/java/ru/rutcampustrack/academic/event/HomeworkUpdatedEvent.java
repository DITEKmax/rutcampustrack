package ru.rutcampustrack.academic.event;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Published when an existing homework assignment is updated.
 * <p>
 * Per EVENT-03: includes event_id UUID for idempotent downstream processing.
 * Per D-01 (minimal payload): includes title to allow notification display without gRPC call.
 */
public class HomeworkUpdatedEvent extends DomainEvent {

    public record Payload(
            @JsonProperty("homework_id") Long homeworkId,
            @JsonProperty("group_id") Long groupId,
            String title
    ) {}

    public HomeworkUpdatedEvent(Object source, Long homeworkId, Long groupId, String title) {
        super(source, "homework.updated", new Payload(homeworkId, groupId, title));
    }
}
