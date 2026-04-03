package ru.rutcampustrack.schedule.lesson;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import ru.rutcampustrack.schedule.contract.enums.LessonStatus;
import ru.rutcampustrack.schedule.event.LessonClosedEvent;
import ru.rutcampustrack.schedule.event.LessonStartedEvent;
import ru.rutcampustrack.schedule.item.entity.ScheduleItem;
import ru.rutcampustrack.schedule.item.repository.ScheduleItemRepository;
import ru.rutcampustrack.schedule.lesson.entity.Lesson;
import ru.rutcampustrack.schedule.lesson.repository.LessonRepository;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Cron job that transitions lesson statuses every minute.
 *
 * Two-phase approach:
 * Phase 1: PLANNED -> ACTIVE when (date + start_time) <= nowMoscow (CRON-01, EVNT-01)
 * Phase 2: ACTIVE  -> CLOSED when (date + end_time + 5min) <= nowMoscow (CRON-02, EVNT-02)
 *
 * Per CRON-03/D-04: No separate catch-up logic — the &lt;= queries naturally catch ALL past-due
 * lessons including those from days ago (e.g., after a server restart).
 *
 * Scheduling is enabled by SchedulingConfig which is guarded with @Profile("!test"),
 * so this job never fires automatically during integration tests.
 */
@Component
@Slf4j
public class LessonStatusTransitionJob {

    private final LessonRepository lessonRepository;
    private final ScheduleItemRepository scheduleItemRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    public LessonStatusTransitionJob(LessonRepository lessonRepository,
                                     ScheduleItemRepository scheduleItemRepository,
                                     ApplicationEventPublisher eventPublisher,
                                     Clock clock) {
        this.lessonRepository = lessonRepository;
        this.scheduleItemRepository = scheduleItemRepository;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    @Scheduled(fixedDelay = 60_000)
    @Transactional
    public void runTransitions() {
        LocalDateTime nowMoscow = LocalDateTime.now(clock);

        // Phase 1: planned -> active
        List<Lesson> toActivate = lessonRepository.findPlannedDueForActivation(nowMoscow);
        for (Lesson lesson : toActivate) {
            lesson.setStatus(LessonStatus.ACTIVE);
            ScheduleItem item = scheduleItemRepository.findById(lesson.getScheduleItemId())
                    .orElseThrow(() -> new IllegalStateException(
                            "ScheduleItem not found for lesson " + lesson.getId()));
            eventPublisher.publishEvent(new LessonStartedEvent(this,
                    lesson.getId(), item.getGroupId(), item.getSubjectId(),
                    item.getTeacherId(), item.getLessonNumber(),
                    item.getStartTime(), item.getEndTime(), item.getRoom()));
        }
        lessonRepository.saveAll(toActivate);

        // Phase 2: active -> closed (including lessons just activated in phase 1 that are also past end_time+5min)
        List<Lesson> toClose = lessonRepository.findActiveDueForClosure(nowMoscow);
        for (Lesson lesson : toClose) {
            lesson.setStatus(LessonStatus.CLOSED);
            lesson.setClosedAt(OffsetDateTime.now(clock));
            ScheduleItem item = scheduleItemRepository.findById(lesson.getScheduleItemId())
                    .orElseThrow(() -> new IllegalStateException(
                            "ScheduleItem not found for lesson " + lesson.getId()));
            eventPublisher.publishEvent(new LessonClosedEvent(this,
                    lesson.getId(), item.getGroupId(), item.getSubjectId()));
        }
        lessonRepository.saveAll(toClose);

        log.info("Cron tick: activated={}, closed={}", toActivate.size(), toClose.size());
    }
}
