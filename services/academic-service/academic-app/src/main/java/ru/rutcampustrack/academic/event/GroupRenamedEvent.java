package ru.rutcampustrack.academic.event;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Published when {@code GroupPromotionService} переименовывает группу при переходе на следующий
 * курс (BUG-006-6 / план 58-06) либо админ меняет имя группы вручную.
 *
 * <p>Payload содержит {@code group_id} (для инвалидации кешей в consumer'ах) и {@code new_name} —
 * имя группы после изменения. {@code new_name} используется Web Push / Telegram / PWA, чтобы
 * показать пользователю понятное тело уведомления («Новое название: БИ-2401») вместо абстрактного
 * «группа переименована».
 */
public class GroupRenamedEvent extends DomainEvent {

    public record Payload(
            @JsonProperty("group_id") Long groupId,
            @JsonProperty("new_name") String newName,
            @JsonProperty("actor_user_id") Long actorUserId,
            @JsonProperty("notification_cause") String notificationCause,
            @JsonProperty("cycle_semester_id") Long cycleSemesterId) {}

    public GroupRenamedEvent(Object source, Long groupId, String newName) {
        this(source, groupId, newName, null);
    }

    public GroupRenamedEvent(Object source, Long groupId, String newName, Long actorUserId) {
        super(source, "group.renamed", new Payload(groupId, newName, actorUserId, null, null));
    }

    private GroupRenamedEvent(Object source, Long groupId, String newName, long cycleSemesterId) {
        super(
                source,
                "group.renamed",
                new Payload(groupId, newName, null, "AUTOMATIC_GROUP_CYCLE", cycleSemesterId));
    }

    public static GroupRenamedEvent automaticCycle(
            Object source, Long groupId, String newName, long cycleSemesterId) {
        return new GroupRenamedEvent(source, groupId, newName, cycleSemesterId);
    }
}
