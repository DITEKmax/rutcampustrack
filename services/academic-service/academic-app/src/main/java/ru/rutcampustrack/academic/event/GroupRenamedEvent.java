package ru.rutcampustrack.academic.event;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Published when {@code GroupPromotionService} переименовывает группу при переходе
 * на следующий курс (BUG-006-6 / план 58-06).
 *
 * <p>Per EVENT-01/D-01 (минимальный payload): передаётся только group_id.
 * Consumers вызывают gRPC для актуального имени. План 07 расширит payload при
 * необходимости (old_name / new_name).
 */
public class GroupRenamedEvent extends DomainEvent {

    public record Payload(@JsonProperty("group_id") Long groupId) {}

    public GroupRenamedEvent(Object source, Long groupId) {
        super(source, "group.renamed", new Payload(groupId));
    }
}
