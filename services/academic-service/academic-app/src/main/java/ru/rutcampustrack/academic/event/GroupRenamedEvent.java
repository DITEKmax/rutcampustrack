package ru.rutcampustrack.academic.event;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Published when {@code GroupPromotionService} переименовывает группу при переходе
 * на следующий курс (BUG-006-6 / план 58-06) либо админ меняет имя группы вручную.
 *
 * <p>Payload содержит {@code group_id} (для инвалидации кешей в consumer'ах) и
 * {@code new_name} — имя группы после изменения. {@code new_name} используется
 * Web Push / Telegram / PWA, чтобы показать пользователю понятное тело уведомления
 * («Новое название: БИ-2401») вместо абстрактного «группа переименована».
 */
public class GroupRenamedEvent extends DomainEvent {

    public record Payload(
            @JsonProperty("group_id") Long groupId,
            @JsonProperty("new_name") String newName
    ) {}

    public GroupRenamedEvent(Object source, Long groupId, String newName) {
        super(source, "group.renamed", new Payload(groupId, newName));
    }
}
