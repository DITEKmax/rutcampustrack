package ru.rutcampustrack.academic.group;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.rutcampustrack.academic.entity.Group;
import ru.rutcampustrack.academic.event.GroupArchivedEvent;
import ru.rutcampustrack.academic.exception.ConflictException;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

    private static final Pattern ARCHIVE_SUFFIX = Pattern.compile(
            "^ \\(выпуск (\\d{4})\\)$");

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

    /**
     * Validate the exact reversible name written by this service.  The split
     * code is returned for conflict checks; course and duration columns are
     * deliberately left untouched because old rows may not know them.
     */
    public RestorableName restorableName(Group group) {
        String archivedName = group.getName();
        if (archivedName == null) {
            throw notRestorable(group);
        }
        int suffixStart = archivedName.lastIndexOf(" (выпуск ");
        if (suffixStart <= 0) {
            throw notRestorable(group);
        }
        String activeName = archivedName.substring(0, suffixStart);
        String suffix = archivedName.substring(suffixStart);
        Matcher matcher = ARCHIVE_SUFFIX.matcher(suffix);
        if (activeName.isBlank() || !matcher.matches()) {
            throw notRestorable(group);
        }
        int graduationYear = Integer.parseInt(matcher.group(1));
        if (!Objects.equals(archivedName, buildArchivedName(activeName, graduationYear))) {
            throw notRestorable(group);
        }

        String alphabeticCode = group.getAlphabeticCode();
        String numericCode = group.getNumericCode();
        if ((alphabeticCode == null) != (numericCode == null)
                || (alphabeticCode != null
                && !Objects.equals(activeName, alphabeticCode + "-" + numericCode))) {
            throw notRestorable(group);
        }
        return new RestorableName(activeName, alphabeticCode, numericCode, graduationYear);
    }

    public record RestorableName(
            String name,
            String alphabeticCode,
            String numericCode,
            int graduationYear) {}

    private ConflictException notRestorable(Group group) {
        return new ConflictException("archived", group.getId(),
                "Архивное имя группы нельзя восстановить автоматически");
    }
}
