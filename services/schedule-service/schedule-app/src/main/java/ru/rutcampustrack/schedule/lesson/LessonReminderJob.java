package ru.rutcampustrack.schedule.lesson;

import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import ru.rutcampustrack.schedule.event.LessonReminderEvent;
import ru.rutcampustrack.schedule.item.entity.ScheduleItem;
import ru.rutcampustrack.schedule.item.repository.ScheduleItemRepository;
import ru.rutcampustrack.schedule.lesson.entity.Lesson;
import ru.rutcampustrack.schedule.lesson.repository.LessonRepository;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Cron job that publishes a {@code lesson.reminder} event for every active
 * lesson once its midpoint is reached.
 *
 * <p>Runs alongside {@link LessonStatusTransitionJob} (the latter handles
 * PLANNED→ACTIVE and ACTIVE→CLOSED). Idempotency anchor:
 * {@code lessons.reminder_midpoint_sent_at} (V15). Setting it inside the
 * same {@code @Transactional} as the outbox write (via the
 * {@code @TransactionalEventListener(BEFORE_COMMIT)} pattern) makes the
 * pair atomic — a Rabbit publish failure rolls back the marker, and the
 * next tick retries.
 *
 * <p>Per-user filtering ("did this student already check in?") is the
 * consumers' responsibility (notification-bot consults Redis, web/PWA
 * consult their local attendance state). The event itself is a single
 * group-level fan-out so we publish it exactly once per lesson.
 */
@Component
@Slf4j
public class LessonReminderJob {

    private final LessonRepository lessonRepository;
    private final ScheduleItemRepository scheduleItemRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    public LessonReminderJob(LessonRepository lessonRepository,
                             ScheduleItemRepository scheduleItemRepository,
                             ApplicationEventPublisher eventPublisher,
                             Clock clock) {
        this.lessonRepository = lessonRepository;
        this.scheduleItemRepository = scheduleItemRepository;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    @Scheduled(fixedDelay = 60_000)
    @SchedulerLock(name = "lesson-reminder-job",
                   lockAtMostFor = "PT2M",
                   lockAtLeastFor = "PT10S")
    @Transactional
    public void runReminders() {
        LocalDateTime nowMoscow = LocalDateTime.now(clock);
        List<Lesson> due = lessonRepository.findActiveDueForMidpointReminder(nowMoscow);
        if (due.isEmpty()) {
            return;
        }
        OffsetDateTime nowOffset = OffsetDateTime.now(clock);
        for (Lesson lesson : due) {
            ScheduleItem item = scheduleItemRepository.findById(lesson.getScheduleItemId())
                    .orElseThrow(() -> new IllegalStateException(
                            "ScheduleItem not found for lesson " + lesson.getId()));
            eventPublisher.publishEvent(new LessonReminderEvent(this,
                    lesson.getId(), item.getGroupId(), item.getSubjectId(),
                    item.getLessonNumber(),
                    item.getStartTime(), item.getEndTime(), item.getRoom(),
                    "midpoint"));
            lesson.setReminderMidpointSentAt(nowOffset);
        }
        lessonRepository.saveAll(due);
        log.info("Lesson midpoint reminders published: count={}", due.size());
    }
}
