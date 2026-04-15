package ru.rutcampustrack.schedule.subject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.rutcampustrack.schedule.event.LessonDeletedEvent;
import ru.rutcampustrack.schedule.event.OneOffLessonCancelledEvent;
import ru.rutcampustrack.schedule.item.entity.ScheduleItem;
import ru.rutcampustrack.schedule.item.repository.ScheduleItemRepository;
import ru.rutcampustrack.schedule.lesson.repository.LessonRepository;
import ru.rutcampustrack.schedule.oneoff.entity.OneOffLesson;
import ru.rutcampustrack.schedule.oneoff.repository.OneOffLessonRepository;

import java.util.List;

/**
 * Cascades a {@code subject.deleted} event from academic-service into
 * schedule-service's own state:
 *
 * <ol>
 *   <li>Collects all lesson ids attached to any schedule_item of the subject
 *       (any status — attendance may exist on CLOSED lessons).</li>
 *   <li>Publishes {@link LessonDeletedEvent} so attendance-service drops
 *       attendance docs via its existing {@code lesson.deleted} handler.</li>
 *   <li>Publishes {@link OneOffLessonCancelledEvent} for every one-off row
 *       of the subject so attendance-service clears those docs too.</li>
 *   <li>Physically removes {@code schedule_one_off_lessons} and
 *       {@code schedule_items}. The FK {@code lessons.schedule_item_id} has
 *       {@code ON DELETE CASCADE} (V1 baseline) so {@code lessons} rows go
 *       with the items.</li>
 * </ol>
 *
 * <p>Idempotent: if no schedule_items / one-off exist for the subject, all
 * queries are no-ops.
 */
@Service
public class SubjectDeletedCascadeService {

    private static final Logger log = LoggerFactory.getLogger(SubjectDeletedCascadeService.class);

    private final ScheduleItemRepository scheduleItemRepository;
    private final OneOffLessonRepository oneOffLessonRepository;
    private final LessonRepository lessonRepository;
    private final ApplicationEventPublisher eventPublisher;

    public SubjectDeletedCascadeService(ScheduleItemRepository scheduleItemRepository,
                                        OneOffLessonRepository oneOffLessonRepository,
                                        LessonRepository lessonRepository,
                                        ApplicationEventPublisher eventPublisher) {
        this.scheduleItemRepository = scheduleItemRepository;
        this.oneOffLessonRepository = oneOffLessonRepository;
        this.lessonRepository = lessonRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public void cascade(Long subjectId) {
        List<Long> lessonIds = lessonRepository.findIdsBySubjectId(subjectId);
        List<ScheduleItem> items = scheduleItemRepository.findBySubjectId(subjectId);
        List<OneOffLesson> oneOffs = oneOffLessonRepository.findBySubjectId(subjectId);

        if (items.isEmpty() && oneOffs.isEmpty()) {
            log.info("subject.deleted: nothing to cascade for subject {}", subjectId);
            return;
        }

        if (!lessonIds.isEmpty()) {
            eventPublisher.publishEvent(new LessonDeletedEvent(this, lessonIds));
        }

        for (OneOffLesson oneOff : oneOffs) {
            eventPublisher.publishEvent(new OneOffLessonCancelledEvent(
                    this,
                    oneOff.getGroupId(),
                    oneOff.getSubjectId(),
                    oneOff.getDate(),
                    oneOff.getLessonNumber().intValue()));
        }

        oneOffLessonRepository.deleteAll(oneOffs);
        // Physical delete cascades to lessons via schedule_items FK.
        scheduleItemRepository.deleteAll(items);

        log.info("subject.deleted cascade: subject={} items={} oneOff={} lessons={}",
                subjectId, items.size(), oneOffs.size(), lessonIds.size());
    }
}
