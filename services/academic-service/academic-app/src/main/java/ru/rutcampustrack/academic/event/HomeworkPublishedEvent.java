package ru.rutcampustrack.academic.event;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Published when a new homework assignment is created by a headman or assistant.
 * <p>
 * Per EVENT-03: includes event_id UUID for idempotent downstream processing.
 * Per Phase 61 D-07: payload extended with lesson_date + lesson_number so that
 * downstream consumers (notification-bot) can render a push with the exact lesson slot.
 */
public class HomeworkPublishedEvent extends DomainEvent {

    public record Payload(
            @JsonProperty("homework_id") Long homeworkId,
            @JsonProperty("group_id") Long groupId,
            @JsonProperty("subject_id") Long subjectId,
            String title,
            @JsonProperty("has_link") boolean hasLink,
            @JsonProperty("lesson_date") String lessonDate,
            @JsonProperty("lesson_number") Integer lessonNumber
    ) {}

    public HomeworkPublishedEvent(Object source, Long homeworkId, Long groupId,
                                   Long subjectId, String title, boolean hasLink,
                                   String lessonDate, Integer lessonNumber) {
        super(source, "homework.published",
                new Payload(homeworkId, groupId, subjectId, title, hasLink, lessonDate, lessonNumber));
    }
}
