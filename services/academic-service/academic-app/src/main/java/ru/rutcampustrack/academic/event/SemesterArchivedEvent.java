package ru.rutcampustrack.academic.event;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Published after a semester is deactivated (archived) and the database
 * transaction commits successfully.
 * <p>
 * Per EVENT-02, D-01 (minimal payload): only semester_id is required.
 * Consumers call gRPC GetActiveSemester to get current semester data.
 */
public class SemesterArchivedEvent extends DomainEvent {

    public record Payload(@JsonProperty("semester_id") Long semesterId) {}

    public SemesterArchivedEvent(Object source, Long semesterId) {
        super(source, "semester.archived", new Payload(semesterId));
    }
}
