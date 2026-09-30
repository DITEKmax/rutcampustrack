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
import ru.rutcampustrack.schedule.lesson.entity.Lesson;
import ru.rutcampustrack.schedule.oneoff.entity.OneOffLesson;
import ru.rutcampustrack.schedule.oneoff.repository.OneOffLessonRepository;
import ru.rutcampustrack.schedule.exception.RecurringLifecycleNotReadyException;
import ru.rutcampustrack.schedule.exception.ConflictException;
import ru.rutcampustrack.schedule.grpc.ScheduleSemesterArchiveWriteFence;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeSet;

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
    private final ScheduleSemesterArchiveWriteFence archiveWriteFence;

    public SubjectDeletedCascadeService(ScheduleItemRepository scheduleItemRepository,
                                        OneOffLessonRepository oneOffLessonRepository,
                                        LessonRepository lessonRepository,
                                         ApplicationEventPublisher eventPublisher,
                                         ScheduleSemesterArchiveWriteFence archiveWriteFence) {
        this.scheduleItemRepository = scheduleItemRepository;
        this.oneOffLessonRepository = oneOffLessonRepository;
        this.lessonRepository = lessonRepository;
        this.eventPublisher = eventPublisher;
        this.archiveWriteFence = archiveWriteFence;
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

        if (lessonRepository.countCanonicalReferencesBySubjectId(subjectId) > 0) {
            throw new RecurringLifecycleNotReadyException("subject cascade with retained canonical history");
        }

        List<Lesson> lessons = lessonIds.isEmpty() ? List.of() : lessonRepository.findAllById(lessonIds);
        TreeSet<Long> semesterIds = new TreeSet<>();
        for (ScheduleItem item : items) {
            if (item.getSemesterId() == null || item.getSemesterId() <= 0) {
                throw new ConflictException("Нельзя удалить расписание без подтверждённого semester scope");
            }
            semesterIds.add(item.getSemesterId());
        }
        for (OneOffLesson oneOff : oneOffs) {
            if (oneOff.getSemesterId() == null || oneOff.getSemesterId() <= 0) {
                throw new ConflictException("Нельзя удалить разовую пару без подтверждённого semester scope");
            }
            semesterIds.add(oneOff.getSemesterId());
        }
        for (Lesson lesson : lessons) {
            if (lesson.getSemesterId() == null || lesson.getSemesterId() <= 0) {
                throw new ConflictException("Нельзя удалить пару без подтверждённого semester scope");
            }
            semesterIds.add(lesson.getSemesterId());
        }
        archiveWriteFence.requireWritableSemesters(semesterIds);

        if (!lessonIds.isEmpty()) {
            Map<Long, List<Long>> lessonIdsBySemester = new LinkedHashMap<>();
            for (Lesson lesson : lessons) {
                lessonIdsBySemester.computeIfAbsent(lesson.getSemesterId(), ignored -> new java.util.ArrayList<>())
                        .add(lesson.getId());
            }
            lessonIdsBySemester.forEach((semesterId, scopedLessonIds) ->
                    eventPublisher.publishEvent(new LessonDeletedEvent(this, scopedLessonIds, semesterId)));
        }

        for (OneOffLesson oneOff : oneOffs) {
            eventPublisher.publishEvent(new OneOffLessonCancelledEvent(
                    this,
                    oneOff.getGroupId(),
                    oneOff.getSubjectId(),
                    oneOff.getDate(),
                    oneOff.getLessonNumber().intValue(),
                    oneOff.getSemesterId()));
        }

        oneOffLessonRepository.deleteAll(oneOffs);
        // Physical delete cascades to lessons via schedule_items FK.
        scheduleItemRepository.deleteAll(items);

        log.info("subject.deleted cascade: subject={} items={} oneOff={} lessons={}",
                subjectId, items.size(), oneOffs.size(), lessonIds.size());
    }
}
