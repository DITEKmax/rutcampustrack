package ru.rutcampustrack.academic.event;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Published AFTER_COMMIT when a Subject row is physically deleted from
 * academic-service. schedule-service listens for this event and cascades
 * the deletion to schedule_items and schedule_one_off_lessons (which in
 * turn triggers lesson.deleted → attendance cleanup).
 *
 * <p>Consumers: schedule-service (subject.deleted listener).
 */
public class SubjectDeletedEvent extends DomainEvent {

    public record Payload(
            @JsonProperty("subject_id") Long subjectId
    ) {}

    public SubjectDeletedEvent(Object source, Long subjectId) {
        super(source, "subject.deleted", new Payload(subjectId));
    }
}
