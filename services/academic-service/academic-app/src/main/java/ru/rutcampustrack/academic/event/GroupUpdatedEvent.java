package ru.rutcampustrack.academic.event;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Published when group composition changes: student transferred,
 * group updated (name/code/active), or group deleted.
 * <p>
 * Per EVENT-01, D-01 (minimal payload): only group_id is required.
 * Consumers call gRPC GetGroup to retrieve full data if needed.
 */
public class GroupUpdatedEvent extends DomainEvent {

    public record Payload(@JsonProperty("group_id") Long groupId) {}

    public GroupUpdatedEvent(Object source, Long groupId) {
        super(source, "group.updated", new Payload(groupId));
    }
}
