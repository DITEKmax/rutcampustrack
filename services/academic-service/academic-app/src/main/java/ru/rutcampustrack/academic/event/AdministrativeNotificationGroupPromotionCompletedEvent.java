package ru.rutcampustrack.academic.event;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDate;
import ru.rutcampustrack.shared.events.EventVersion;

/** One aggregate for an applied promotion plan, with counts from its persisted ledger. */
@EventVersion(1)
public class AdministrativeNotificationGroupPromotionCompletedEvent extends DomainEvent {
    public record Payload(
            @JsonProperty("cycle_semester_id") long cycleSemesterId,
            @JsonProperty("cycle_date_to") LocalDate cycleDateTo,
            @JsonProperty("promoted") long promoted,
            @JsonProperty("graduated") long graduated,
            @JsonProperty("skipped") long skipped,
            @JsonProperty("actor_user_id") Long actorUserId) {}

    public AdministrativeNotificationGroupPromotionCompletedEvent(
            Object source,
            long cycleSemesterId,
            LocalDate cycleDateTo,
            long promoted,
            long graduated,
            long skipped,
            Long actorUserId) {
        super(
                source,
                "group.promotion.completed",
                new Payload(
                        cycleSemesterId, cycleDateTo, promoted, graduated, skipped, actorUserId));
    }
}
