package ru.rutcampustrack.academic.event;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Published when group composition changes: student transferred, group updated (name/code/active),
 * or group deleted.
 *
 * <p>Per EVENT-01, D-01 (minimal payload): only group_id is required. Consumers call gRPC GetGroup
 * to retrieve full data if needed.
 */
public class GroupUpdatedEvent extends DomainEvent {

    public record Payload(
            @JsonProperty("group_id") Long groupId,
            @JsonProperty("affected_user_ids") @JsonInclude(JsonInclude.Include.NON_NULL)
                    List<Long> affectedUserIds) {}

    public GroupUpdatedEvent(Object source, Long groupId) {
        this(source, groupId, null);
    }

    /** Frozen before deletion; no consumer may resolve these recipients from erased joins. */
    public GroupUpdatedEvent(Object source, Long groupId, List<Long> affectedUserIds) {
        super(
                source,
                "group.updated",
                new Payload(
                        groupId, affectedUserIds == null ? null : List.copyOf(affectedUserIds)));
    }
}
