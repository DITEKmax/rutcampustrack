package ru.rutcampustrack.academic.homework;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import ru.rutcampustrack.academic.contract.enums.HomeworkBindingMode;
import ru.rutcampustrack.academic.contract.enums.HomeworkPublicationState;
import ru.rutcampustrack.academic.entity.Homework;
import ru.rutcampustrack.shared.web.time.EducationalTime;

/** Effective cutoff only; Schedule remains the sole persisted placement/archive authority. */
@Component
public class HomeworkLifecycle {
    private final Clock clock;

    @Autowired
    public HomeworkLifecycle(Clock clock, ObjectProvider<EducationalTime> educationalTime) {
        EducationalTime instructionalTime = educationalTime.getIfAvailable();
        this.clock = instructionalTime == null ? clock : instructionalTime.clock();
    }

    public HomeworkLifecycle(Clock clock) {
        this.clock = clock;
    }

    public boolean expiredDate(Homework homework) {
        return homework.getBindingMode() == HomeworkBindingMode.DATE
                && homework.getLessonDate()
                        .isBefore(LocalDate.now(clock.withZone(ZoneId.of("Europe/Moscow"))));
    }

    public boolean archived(Homework homework) {
        return homework.getPublicationState() == HomeworkPublicationState.ARCHIVED
                || expiredDate(homework);
    }
}
