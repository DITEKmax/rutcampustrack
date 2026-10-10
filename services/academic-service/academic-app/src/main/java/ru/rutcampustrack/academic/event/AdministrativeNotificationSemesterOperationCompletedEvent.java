package ru.rutcampustrack.academic.event;

import com.fasterxml.jackson.annotation.JsonProperty;
import ru.rutcampustrack.academic.entity.Semester;
import ru.rutcampustrack.academic.entity.SemesterArchiveOperation;
import ru.rutcampustrack.shared.events.EventVersion;

import java.time.LocalDate;
import java.util.UUID;

/** Stored in the same outbox transaction as the locked terminal operation state. */
@EventVersion(1)
public class AdministrativeNotificationSemesterOperationCompletedEvent extends DomainEvent {
    public record Payload(
            @JsonProperty("operation_id") UUID operationId,
            @JsonProperty("semester_id") long semesterId,
            @JsonProperty("action") String action,
            @JsonProperty("outcome") String outcome,
            @JsonProperty("semester_name") String semesterName,
            @JsonProperty("date_from") LocalDate dateFrom,
            @JsonProperty("date_to") LocalDate dateTo,
            @JsonProperty("actor_user_id") Long actorUserId) {}

    public AdministrativeNotificationSemesterOperationCompletedEvent(Object source,
            SemesterArchiveOperation operation, Semester semester, String outcome) {
        super(source, "semester.operation.completed", new Payload(operation.getOperationId(),
                operation.getSemesterId(), operation.getAction().name(), outcome,
                semester.getName(), semester.getDateFrom(), semester.getDateTo(),
                operation.getActorId() > 0 ? operation.getActorId() : null));
    }
}
