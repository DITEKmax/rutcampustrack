package ru.rutcampustrack.academic.event;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Published when a group reaches maxCourse and is archived by
 * {@code GroupArchivalService} (BUG-006-6 / план 58-06).
 *
 * <p>Per EVENT-01/D-01 (минимальный payload): передаётся только group_id.
 * Consumers вызывают {@code AcademicGrpcService.GetGroup} для актуального имени
 * (с суффиксом «(выпуск YYYY)») и статуса.
 *
 * <p>План 07 расширит payload-контракт (JSON Schema) при необходимости.
 */
public class GroupArchivedEvent extends DomainEvent {

    public record Payload(@JsonProperty("group_id") Long groupId) {}

    public GroupArchivedEvent(Object source, Long groupId) {
        super(source, "group.archived", new Payload(groupId));
    }
}
