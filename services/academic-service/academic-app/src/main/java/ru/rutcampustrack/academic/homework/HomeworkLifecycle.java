package ru.rutcampustrack.academic.homework;

import org.springframework.stereotype.Component;
import ru.rutcampustrack.academic.contract.enums.HomeworkBindingMode;
import ru.rutcampustrack.academic.contract.enums.HomeworkPublicationState;
import ru.rutcampustrack.academic.entity.Homework;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;

/** Effective cutoff only; Schedule remains the sole persisted placement/archive authority. */
@Component
public class HomeworkLifecycle {
    private final Clock clock;
    public HomeworkLifecycle(Clock clock) { this.clock = clock; }
    public boolean expiredDate(Homework homework) {
        return homework.getBindingMode() == HomeworkBindingMode.DATE
                && homework.getLessonDate().isBefore(LocalDate.now(clock.withZone(ZoneId.of("Europe/Moscow"))));
    }
    public boolean archived(Homework homework) {
        return homework.getPublicationState() == HomeworkPublicationState.ARCHIVED || expiredDate(homework);
    }
}
