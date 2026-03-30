package ru.rutcampustrack.academic.event;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Published when a new homework assignment is created by a headman or assistant.
 * <p>
 * Per EVENT-03: includes event_id UUID for idempotent downstream processing.
 * Per D-01 (minimal payload): includes fields needed for notification display.
 */
public class HomeworkPublishedEvent extends DomainEvent {

    public record Payload(
            @JsonProperty("homework_id") Long homeworkId,
            @JsonProperty("group_id") Long groupId,
            @JsonProperty("subject_id") Long subjectId,
            String title,
            @JsonProperty("has_link") boolean hasLink
    ) {}

    public HomeworkPublishedEvent(Object source, Long homeworkId, Long groupId,
                                   Long subjectId, String title, boolean hasLink) {
        super(source, "homework.published", new Payload(homeworkId, groupId, subjectId, title, hasLink));
    }
}
