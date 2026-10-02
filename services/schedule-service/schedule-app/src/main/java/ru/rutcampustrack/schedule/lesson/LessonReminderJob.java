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
import ru.rutcampustrack.schedule.grpc.ScheduleSemesterArchiveWriteFence;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;

/**
 * Cron job that publishes {@code lesson.reminder} events for active lessons:
 * once around the midpoint and once near the end.
 *
 * <p>Runs alongside {@link LessonStatusTransitionJob} (the latter handles
 * PLANNED→ACTIVE and ACTIVE→CLOSED). Idempotency anchors:
 * {@code lessons.reminder_midpoint_sent_at} and {@code lessons.reminder_near_end_sent_at}.
 * Setting a marker inside the
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
    private final ScheduleSemesterArchiveWriteFence archiveWriteFence;

    public LessonReminderJob(LessonRepository lessonRepository,
                             ScheduleItemRepository scheduleItemRepository,
                             ApplicationEventPublisher eventPublisher,
                             Clock clock,
                             ScheduleSemesterArchiveWriteFence archiveWriteFence) {
        this.lessonRepository = lessonRepository;
        this.scheduleItemRepository = scheduleItemRepository;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
        this.archiveWriteFence = archiveWriteFence;
    }

    @Scheduled(fixedDelay = 60_000)
    @SchedulerLock(name = "lesson-reminder-job",
                   lockAtMostFor = "PT2M",
                   lockAtLeastFor = "PT10S")
    @Transactional
    public void runReminders() {
        LocalDateTime nowMoscow = LocalDateTime.now(clock);
        List<Lesson> midpointCandidates = lessonRepository.findActiveDueForMidpointReminder(nowMoscow);
        List<Lesson> nearEndCandidates = lessonRepository.findActiveDueForNearEndReminder(nowMoscow);
        if (midpointCandidates.isEmpty() && nearEndCandidates.isEmpty()) {
            return;
        }
        java.util.ArrayList<Lesson> candidates = new java.util.ArrayList<>(midpointCandidates);
        candidates.addAll(nearEndCandidates);
        Set<Long> writableSemesters = archiveWriteFence.lockWritableSemesters(candidates.stream()
                .map(Lesson::getSemesterId)
                .filter(java.util.Objects::nonNull)
                .toList());
        List<Lesson> midpointDue = midpointCandidates.stream()
                .filter(lesson -> lesson.getSemesterId() != null
                        && writableSemesters.contains(lesson.getSemesterId()))
                .toList();
        List<Lesson> nearEndDue = nearEndCandidates.stream()
                .filter(lesson -> lesson.getSemesterId() != null
                        && writableSemesters.contains(lesson.getSemesterId()))
                .toList();
        if (midpointDue.isEmpty() && nearEndDue.isEmpty()) return;
        OffsetDateTime nowOffset = OffsetDateTime.now(clock);
        for (Lesson lesson : midpointDue) {
            publishReminder(lesson, "midpoint");
            lesson.setReminderMidpointSentAt(nowOffset);
        }
        for (Lesson lesson : nearEndDue) {
            publishReminder(lesson, "near_end");
            lesson.setReminderNearEndSentAt(nowOffset);
        }
        lessonRepository.saveAll(midpointDue);
        lessonRepository.saveAll(nearEndDue);
        log.info("Lesson reminders published: midpoint={}, nearEnd={}",
                midpointDue.size(), nearEndDue.size());
    }

    private void publishReminder(Lesson lesson, String phase) {
        ScheduleItem item = lesson.getScheduleItemId() == null ? null
                : scheduleItemRepository.findById(lesson.getScheduleItemId())
                .orElseThrow(() -> new IllegalStateException(
                        "ScheduleItem not found for lesson " + lesson.getId()));
        eventPublisher.publishEvent(new LessonReminderEvent(this,
                lesson.getId(), lesson.getGroupId() != null ? lesson.getGroupId() : item.getGroupId(),
                lesson.getSubjectId() != null ? lesson.getSubjectId() : item.getSubjectId(),
                lesson.getLessonNumber() != null ? lesson.getLessonNumber() : item.getLessonNumber(),
                lesson.getStartTime() != null ? lesson.getStartTime() : item.getStartTime(),
                lesson.getEndTime() != null ? lesson.getEndTime() : item.getEndTime(),
                lesson.getRoomSnapshot() != null ? lesson.getRoomSnapshot() : item == null ? null : item.getRoom(),
                phase));
    }
}
