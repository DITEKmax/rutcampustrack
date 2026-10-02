package ru.rutcampustrack.schedule.homework;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Terminal DATE cutoff is the start of the following Moscow day. */
@Component
@Profile("!test")
public class HomeworkDateDeadlineJob {
    private final HomeworkPlacementService placement;
    public HomeworkDateDeadlineJob(HomeworkPlacementService placement) { this.placement = placement; }

    @Scheduled(fixedDelay = 30_000)
    @SchedulerLock(name = "homework-date-deadline", lockAtMostFor = "PT2M", lockAtLeastFor = "PT5S")
    public void archiveDueDates() { placement.archiveExpiredDates(); }
}
