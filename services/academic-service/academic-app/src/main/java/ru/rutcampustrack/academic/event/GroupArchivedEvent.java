package ru.rutcampustrack.academic.event;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Published when a group reaches maxCourse and is archived by {@code GroupArchivalService}
 * (BUG-006-6 / план 58-06).
 *
 * <p>Per EVENT-01/D-01 (минимальный payload): передаётся только group_id. Consumers вызывают {@code
 * AcademicGrpcService.GetGroup} для актуального имени (с суффиксом «(выпуск YYYY)») и статуса.
 *
 * <p>План 07 расширит payload-контракт (JSON Schema) при необходимости.
 */
public class GroupArchivedEvent extends DomainEvent {

    public record Payload(
            @JsonProperty("group_id") Long groupId,
            @JsonProperty("group_name") String groupName,
            @JsonProperty("actor_user_id") Long actorUserId,
            @JsonProperty("notification_cause") String notificationCause,
            @JsonProperty("cycle_semester_id") Long cycleSemesterId) {}

    public GroupArchivedEvent(Object source, Long groupId) {
        this(source, groupId, null, null);
    }

    public GroupArchivedEvent(Object source, Long groupId, String groupName, Long actorUserId) {
        super(source, "group.archived", new Payload(groupId, groupName, actorUserId, null, null));
    }

    private GroupArchivedEvent(
            Object source, Long groupId, String groupName, long cycleSemesterId) {
        super(
                source,
                "group.archived",
                new Payload(groupId, groupName, null, "AUTOMATIC_GROUP_CYCLE", cycleSemesterId));
    }

    public static GroupArchivedEvent automaticCycle(
            Object source, Long groupId, String groupName, long cycleSemesterId) {
        return new GroupArchivedEvent(source, groupId, groupName, cycleSemesterId);
    }
}
