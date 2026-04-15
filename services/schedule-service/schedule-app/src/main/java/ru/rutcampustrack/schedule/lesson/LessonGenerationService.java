package ru.rutcampustrack.schedule.lesson;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.rutcampustrack.schedule.contract.enums.WeekType;
import ru.rutcampustrack.schedule.event.LessonDeletedEvent;
import ru.rutcampustrack.schedule.item.entity.ScheduleItem;
import ru.rutcampustrack.schedule.lesson.entity.Lesson;
import ru.rutcampustrack.schedule.lesson.repository.LessonRepository;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;

/**
 * Service responsible for generating Lesson entities from ScheduleItem templates.
 *
 * The core algorithm anchors week parity to the semester start date:
 * - Week 0 (the week containing semesterStart) has parity = firstWeekType.
 * - Week 1 has opposite parity, week 2 has firstWeekType again, and so on.
 * - This correctly handles cases where the semester starts mid-week
 *   (the anchor is the Monday of the start week).
 *
 * Satisfies LSSN-01 (generate lessons for a schedule item) and
 * LSSN-02 (week parity algorithm).
 */
@Service
public class LessonGenerationService {

    private final LessonRepository lessonRepository;
    private final Clock clock;
    private final ApplicationEventPublisher eventPublisher;

    public LessonGenerationService(
            LessonRepository lessonRepository,
            Clock clock,
            ApplicationEventPublisher eventPublisher
    ) {
        this.lessonRepository = lessonRepository;
        this.clock = clock;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Publishes {@link LessonDeletedEvent} for the given ids (no-op if empty).
     * Called right before a physical delete so downstream consumers (attendance-
     * service) can drop records keyed to the about-to-vanish lesson ids.
     * The {@code @TransactionalEventListener(AFTER_COMMIT)} in DomainEventListener
     * ensures the message is dispatched only if the surrounding transaction commits.
     */
    private void publishDeleted(List<Long> ids) {
        if (ids == null || ids.isEmpty()) return;
        eventPublisher.publishEvent(new LessonDeletedEvent(this, ids));
    }

    /**
     * Computes all lesson dates for a given schedule item template within [semesterStart, semesterEnd].
     *
     * @param semesterStart    First day of the semester (inclusive)
     * @param semesterEnd      Last day of the semester (inclusive)
     * @param firstWeekType    Parity of the first semester week (ODD or EVEN; ALL is not valid here)
     * @param dayOfWeek        1=Monday .. 6=Saturday (schedule_items convention, aligned with java.time.DayOfWeek)
     * @param templateWeekType The schedule item's week type: ALL, ODD, or EVEN
     * @return ordered list of LocalDate occurrences
     */
    public List<LocalDate> computeLessonDates(
            LocalDate semesterStart,
            LocalDate semesterEnd,
            WeekType firstWeekType,
            short dayOfWeek,
            WeekType templateWeekType) {

        // dayOfWeek is aligned with java.time.DayOfWeek (1=Mon..7=Sun).
        // Legacy rows may still have 0; treat them as Monday for backward compatibility
        // (V5 migration widened the CHECK to 0..7 without normalising old data).
        int normalisedDow = dayOfWeek == 0 ? 1 : dayOfWeek;
        DayOfWeek targetJavaDow = DayOfWeek.of(normalisedDow);

        // Anchor: the Monday of the week that contains semesterStart.
        // All week indices are computed relative to this Monday.
        LocalDate anchor = semesterStart.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));

        List<LocalDate> dates = new ArrayList<>();
        LocalDate current = semesterStart;

        while (!current.isAfter(semesterEnd)) {
            if (current.getDayOfWeek() == targetJavaDow) {
                if (templateWeekType == WeekType.ALL) {
                    dates.add(current);
                } else {
                    // Number of full weeks from the anchor to the Monday of current's week
                    LocalDate currentWeekMonday = current.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
                    long weeksSinceStart = ChronoUnit.WEEKS.between(anchor, currentWeekMonday);

                    // Even-indexed weeks (0, 2, 4 ...) have the same parity as firstWeekType.
                    // Odd-indexed weeks (1, 3, 5 ...) have the opposite parity.
                    WeekType currentParity = (weeksSinceStart % 2 == 0)
                            ? firstWeekType
                            : (firstWeekType == WeekType.ODD ? WeekType.EVEN : WeekType.ODD);

                    if (currentParity == templateWeekType) {
                        dates.add(current);
                    }
                }
            }
            current = current.plusDays(1);
        }

        return dates;
    }

    /**
     * Generates Lesson entities for a ScheduleItem and persists them.
     * Lessons are created with status=PLANNED and isGeoBlocked=false.
     *
     * @param item          The schedule item template
     * @param semesterStart Semester start date
     * @param semesterEnd   Semester end date
     * @param firstWeekType Parity of the first semester week
     */
    @Transactional
    public void generateLessons(
            ScheduleItem item,
            LocalDate semesterStart,
            LocalDate semesterEnd,
            WeekType firstWeekType) {

        List<LocalDate> dates = computeLessonDates(
                semesterStart, semesterEnd, firstWeekType,
                item.getDayOfWeek(), item.getWeekType());

        List<Lesson> lessons = buildLessons(item.getId(), dates);
        lessonRepository.saveAll(lessons);
    }

    /**
     * Deletes PLANNED lessons from `fromDate` onward and re-generates them.
     * Used when a ScheduleItem is updated (day, time, room, weekType) and
     * the generated lessons need to be refreshed for the remaining semester.
     *
     * @param item          Updated schedule item
     * @param semesterStart Semester start date (needed for parity anchor)
     * @param semesterEnd   Semester end date
     * @param firstWeekType Parity of the first semester week
     * @param fromDate      Only regenerate dates on or after this date
     */
    @Transactional
    public void regenerateFromDate(
            ScheduleItem item,
            LocalDate semesterStart,
            LocalDate semesterEnd,
            WeekType firstWeekType,
            LocalDate fromDate) {

        List<Long> staleIds = lessonRepository.findPlannedIdsFromDate(item.getId(), fromDate);
        lessonRepository.deletePlannedFromDate(item.getId(), fromDate);
        publishDeleted(staleIds);

        // Only generate dates >= fromDate (no backfill for already-passed dates)
        LocalDate effectiveStart = fromDate.isAfter(semesterStart) ? fromDate : semesterStart;
        List<LocalDate> dates = computeLessonDates(
                effectiveStart, semesterEnd, firstWeekType,
                item.getDayOfWeek(), item.getWeekType());

        List<Lesson> lessons = buildLessons(item.getId(), dates);
        lessonRepository.saveAll(lessons);
    }

    /**
     * Deletes all PLANNED lessons for a schedule item starting from today.
     * Called by ScheduleItemService.deleteScheduleItem so that disabling a
     * template also clears future planned occurrences from the calendar
     * (Bug: «удалил из матрицы — осталось в "Парах на 2 недели"»).
     *
     * Past lessons are intentionally preserved — they may carry attendance.
     */
    @Transactional
    public void deletePlannedLessonsFromToday(Long scheduleItemId) {
        LocalDate today = LocalDate.now(clock);
        List<Long> staleIds = lessonRepository.findPlannedIdsFromDate(scheduleItemId, today);
        lessonRepository.deletePlannedFromDate(scheduleItemId, today);
        publishDeleted(staleIds);
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private List<Lesson> buildLessons(Long scheduleItemId, List<LocalDate> dates) {
        OffsetDateTime now = OffsetDateTime.now(clock.withZone(ZoneOffset.UTC));
        return dates.stream().map(date -> {
            Lesson lesson = new Lesson();
            lesson.setScheduleItemId(scheduleItemId);
            lesson.setDate(date);
            lesson.setStatus(ru.rutcampustrack.schedule.contract.enums.LessonStatus.PLANNED);
            lesson.setGeoBlocked(false);
            lesson.setCreatedAt(now);
            return lesson;
        }).toList();
    }
}
