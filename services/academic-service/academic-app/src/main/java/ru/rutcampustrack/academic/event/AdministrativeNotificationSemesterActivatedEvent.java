package ru.rutcampustrack.academic.event;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDate;
import ru.rutcampustrack.academic.entity.Semester;
import ru.rutcampustrack.shared.events.EventVersion;

/** A real activation, never a scheduled intent or an activation replay. */
@EventVersion(1)
public class AdministrativeNotificationSemesterActivatedEvent extends DomainEvent {
    public record Payload(
            @JsonProperty("semester_id") long semesterId,
            @JsonProperty("semester_name") String semesterName,
            @JsonProperty("date_from") LocalDate dateFrom,
            @JsonProperty("date_to") LocalDate dateTo,
            @JsonProperty("actor_user_id") Long actorUserId) {}

    public AdministrativeNotificationSemesterActivatedEvent(
            Object source, Semester semester, Long actorUserId) {
        super(
                source,
                "semester.activated",
                new Payload(
                        semester.getId(),
                        semester.getName(),
                        semester.getDateFrom(),
                        semester.getDateTo(),
                        actorUserId));
    }
}
