package ru.rutcampustrack.academic.group;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.rutcampustrack.academic.entity.Group;
import ru.rutcampustrack.academic.event.GroupArchivedEvent;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * Архивация группы при выпуске (BUG-006-6 / план 58-06).
 *
 * <p>Архивированная группа получает суффикс {@code " (выпуск YYYY)"} в имя
 * (год берётся из {@link Clock} — для тестируемости), флаг {@code is_active=false}
 * и метку {@code archived_at=now()}. После коммита публикуется
 * {@link GroupArchivedEvent} для downstream-сервисов.
 *
 * <p>Clock инжектируется отдельно от {@link java.time.OffsetDateTime#now()}, чтобы
 * юнит-тесты и {@code GroupPromotionService} могли зафиксировать дату без таймшифтов.
 */
@Service
public class GroupArchivalService {

    private final ApplicationEventPublisher publisher;
    private final Clock clock;

    public GroupArchivalService(ApplicationEventPublisher publisher, Clock clock) {
        this.publisher = publisher;
        this.clock = clock;
    }

    /**
     * Архивировать группу. Если уже архивирована — {@link IllegalStateException}
     * (идемпотентность через выброс: promotion не должен архивировать дважды).
     */
    @Transactional
    public void archive(Group group) {
        archive(group, LocalDate.now(clock).getYear());
    }

    /** Archive as part of a semester cycle, using that semester's graduation year. */
    @Transactional
    public void archive(Group group, int graduationYear) {
        if (!group.isActive()) {
            throw new IllegalStateException("Group already archived: " + group.getId());
        }
        group.setName(buildArchivedName(group.getName(), graduationYear));
        group.setActive(false);
        group.setArchivedAt(OffsetDateTime.now(clock));
        publisher.publishEvent(new GroupArchivedEvent(this, group.getId()));
    }

    /**
     * Построить архивное имя группы: {@code "<active> (выпуск YYYY)"}.
     */
    public String buildArchivedName(String active, int year) {
        return active + " (выпуск " + year + ")";
    }
}
