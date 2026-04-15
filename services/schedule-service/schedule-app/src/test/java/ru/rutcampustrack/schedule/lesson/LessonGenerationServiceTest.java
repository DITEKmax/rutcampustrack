package ru.rutcampustrack.schedule.lesson;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.rutcampustrack.schedule.contract.enums.LessonStatus;
import ru.rutcampustrack.schedule.contract.enums.WeekType;
import ru.rutcampustrack.schedule.item.entity.ScheduleItem;
import ru.rutcampustrack.schedule.lesson.entity.Lesson;
import ru.rutcampustrack.schedule.lesson.repository.LessonRepository;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Unit tests for LessonGenerationService.computeLessonDates() parity algorithm.
 *
 * Test data summary (all dates in 2026):
 *   Semester: Feb 2 (Mon) to Mar 2 (Mon)
 *   Mondays in range: Feb 2, Feb 9, Feb 16, Feb 23, Mar 2 = 5 Mondays
 *   Anchor = Feb 2 (Monday of the week containing Feb 2 = Feb 2 itself)
 *   With firstWeekType=ODD:
 *     Week 0 (Feb 2)  = ODD
 *     Week 1 (Feb 9)  = EVEN
 *     Week 2 (Feb 16) = ODD
 *     Week 3 (Feb 23) = EVEN
 *     Week 4 (Mar 2)  = ODD
 *   ODD template:  Feb 2, Feb 16, Mar 2
 *   EVEN template: Feb 9, Feb 23
 */
@ExtendWith(MockitoExtension.class)
class LessonGenerationServiceTest {

    @Mock
    private LessonRepository lessonRepository;

    private LessonGenerationService service;

    // Fixed clock for deterministic OffsetDateTime.now() in generateLessons
    private final Clock fixedClock = Clock.fixed(
            LocalDate.of(2026, 2, 1).atStartOfDay(ZoneId.of("UTC")).toInstant(),
            ZoneId.of("UTC"));

    // Semester: Mon Feb 2 — Mon Mar 2 2026
    private static final LocalDate SEM_START = LocalDate.of(2026, 2, 2);  // Monday
    private static final LocalDate SEM_END   = LocalDate.of(2026, 3, 2);  // Monday

    // dayOfWeek: 1 = Monday (project convention aligned with java.time.DayOfWeek: 1=Mon..7=Sun)
    private static final short DAY_MONDAY = 1;

    @BeforeEach
    void setUp() {
        service = new LessonGenerationService(lessonRepository, fixedClock);
    }

    // =========================================================================
    // computeLessonDates — ALL week type
    // =========================================================================

    /**
     * WeekType.ALL returns every occurrence of the target day-of-week in the semester.
     * Semester Feb 2 – Mar 2 has 5 Mondays: Feb 2, 9, 16, 23, Mar 2.
     */
    @Test
    void computeLessonDates_allWeekType_returnsEveryMatchingDay() {
        List<LocalDate> result = service.computeLessonDates(
                SEM_START, SEM_END, WeekType.ODD, DAY_MONDAY, WeekType.ALL);

        assertThat(result).containsExactly(
                LocalDate.of(2026, 2, 2),
                LocalDate.of(2026, 2, 9),
                LocalDate.of(2026, 2, 16),
                LocalDate.of(2026, 2, 23),
                LocalDate.of(2026, 3, 2));
    }

    // =========================================================================
    // computeLessonDates — ODD week type, firstWeekType = ODD
    // =========================================================================

    /**
     * ODD template with firstWeekType=ODD: weeks 0,2,4 (Feb 2, Feb 16, Mar 2) are ODD.
     */
    @Test
    void computeLessonDates_oddTemplate_firstWeekOdd_returnsOddWeekDates() {
        List<LocalDate> result = service.computeLessonDates(
                SEM_START, SEM_END, WeekType.ODD, DAY_MONDAY, WeekType.ODD);

        assertThat(result).containsExactly(
                LocalDate.of(2026, 2, 2),
                LocalDate.of(2026, 2, 16),
                LocalDate.of(2026, 3, 2));
    }

    // =========================================================================
    // computeLessonDates — EVEN week type, firstWeekType = ODD
    // =========================================================================

    /**
     * EVEN template with firstWeekType=ODD: weeks 1,3 (Feb 9, Feb 23) are EVEN.
     */
    @Test
    void computeLessonDates_evenTemplate_firstWeekOdd_returnsEvenWeekDates() {
        List<LocalDate> result = service.computeLessonDates(
                SEM_START, SEM_END, WeekType.ODD, DAY_MONDAY, WeekType.EVEN);

        assertThat(result).containsExactly(
                LocalDate.of(2026, 2, 9),
                LocalDate.of(2026, 2, 23));
    }

    // =========================================================================
    // computeLessonDates — inverted: firstWeekType = EVEN
    // =========================================================================

    /**
     * ODD template with firstWeekType=EVEN: week 0 = EVEN, week 1 = ODD, week 2 = EVEN ...
     * ODD template matches weeks 1,3 → Feb 9, Feb 23.
     */
    @Test
    void computeLessonDates_oddTemplate_firstWeekEven_returnsFlippedDates() {
        List<LocalDate> result = service.computeLessonDates(
                SEM_START, SEM_END, WeekType.EVEN, DAY_MONDAY, WeekType.ODD);

        assertThat(result).containsExactly(
                LocalDate.of(2026, 2, 9),
                LocalDate.of(2026, 2, 23));
    }

    /**
     * EVEN template with firstWeekType=EVEN: EVEN pattern matches weeks 0,2,4
     * → Feb 2, Feb 16, Mar 2.
     */
    @Test
    void computeLessonDates_evenTemplate_firstWeekEven_returnsEvenWeekDates() {
        List<LocalDate> result = service.computeLessonDates(
                SEM_START, SEM_END, WeekType.EVEN, DAY_MONDAY, WeekType.EVEN);

        assertThat(result).containsExactly(
                LocalDate.of(2026, 2, 2),
                LocalDate.of(2026, 2, 16),
                LocalDate.of(2026, 3, 2));
    }

    // =========================================================================
    // computeLessonDates — semester starts on a non-Monday (Sunday)
    // =========================================================================

    /**
     * Semester starts on Sunday Feb 1, 2026.
     * The anchor (previousOrSame Monday) = Jan 26.
     * Feb 2 (Mon) is in week 1 from anchor (Jan 26).
     * Feb 9 (Mon) is in week 2 from anchor.
     *
     * With ALL template: finds both Feb 2 and Feb 9.
     */
    @Test
    void computeLessonDates_semesterStartsSunday_firstLessonIsNextMonday() {
        LocalDate sundayStart = LocalDate.of(2026, 2, 1);  // Sunday
        LocalDate shortEnd    = LocalDate.of(2026, 2, 9);  // Next Monday (inclusive)

        // ALL template: should find Monday Feb 2 and Monday Feb 9
        List<LocalDate> allResult = service.computeLessonDates(
                sundayStart, shortEnd, WeekType.ODD, DAY_MONDAY, WeekType.ALL);

        assertThat(allResult).containsExactly(
                LocalDate.of(2026, 2, 2),
                LocalDate.of(2026, 2, 9));
    }

    // =========================================================================
    // computeLessonDates — no matching day in very short range
    // =========================================================================

    /**
     * A 3-day semester (Tue–Thu) with dayOfWeek=1 (Monday) produces empty result.
     */
    @Test
    void computeLessonDates_noMatchingDayInRange_returnsEmpty() {
        // Feb 3, 2026 = Tuesday
        LocalDate tuesdayStart = LocalDate.of(2026, 2, 3);
        LocalDate thursdayEnd  = LocalDate.of(2026, 2, 5); // Thursday

        List<LocalDate> result = service.computeLessonDates(
                tuesdayStart, thursdayEnd, WeekType.ODD, DAY_MONDAY, WeekType.ALL);

        assertThat(result).isEmpty();
    }

    // =========================================================================
    // generateLessons — calls saveAll with correctly constructed Lesson entities
    // =========================================================================

    /**
     * generateLessons() must call lessonRepository.saveAll() with Lesson entities
     * that have status=PLANNED, isGeoBlocked=false, and the correct scheduleItemId.
     */
    @Test
    void generateLessons_callsRepositorySaveAllWithCorrectEntities() {
        ScheduleItem item = buildScheduleItem(42L, DAY_MONDAY, WeekType.ODD);

        service.generateLessons(item, SEM_START, SEM_END, WeekType.ODD);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Lesson>> captor = ArgumentCaptor.forClass(List.class);
        verify(lessonRepository, times(1)).saveAll(captor.capture());

        List<Lesson> savedLessons = captor.getValue();
        // ODD template, firstWeekType=ODD: should yield Feb 2, Feb 16, Mar 2
        assertThat(savedLessons).hasSize(3);

        savedLessons.forEach(lesson -> {
            assertThat(lesson.getScheduleItemId()).isEqualTo(42L);
            assertThat(lesson.getStatus()).isEqualTo(LessonStatus.PLANNED);
            assertThat(lesson.isGeoBlocked()).isFalse();
            assertThat(lesson.getCreatedAt()).isNotNull();
        });

        assertThat(savedLessons.stream().map(Lesson::getDate).toList()).containsExactly(
                LocalDate.of(2026, 2, 2),
                LocalDate.of(2026, 2, 16),
                LocalDate.of(2026, 3, 2));
    }

    /**
     * generateLessons() with WeekType.ALL produces lessons for every Monday.
     */
    @Test
    void generateLessons_allWeekType_generatesAllMondayLessons() {
        ScheduleItem item = buildScheduleItem(7L, DAY_MONDAY, WeekType.ALL);

        service.generateLessons(item, SEM_START, SEM_END, WeekType.ODD);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Lesson>> captor = ArgumentCaptor.forClass(List.class);
        verify(lessonRepository).saveAll(captor.capture());

        assertThat(captor.getValue()).hasSize(5); // all 5 Mondays
    }

    // =========================================================================
    // regenerateFromDate — deletes then regenerates
    // =========================================================================

    /**
     * regenerateFromDate() must call deletePlannedFromDate() before saveAll().
     * It should only generate dates >= fromDate.
     */
    @Test
    void regenerateFromDate_deletesOldLessonsAndRegeneratesFromDate() {
        ScheduleItem item = buildScheduleItem(99L, DAY_MONDAY, WeekType.ALL);
        LocalDate fromDate = LocalDate.of(2026, 2, 16); // Monday, start of week 2

        service.regenerateFromDate(item, SEM_START, SEM_END, WeekType.ODD, fromDate);

        // Must delete planned lessons from Feb 16 onward
        verify(lessonRepository).deletePlannedFromDate(99L, fromDate);

        // Must save lessons for Feb 16, Feb 23, Mar 2 (3 Mondays from Feb 16 onwards)
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Lesson>> captor = ArgumentCaptor.forClass(List.class);
        verify(lessonRepository).saveAll(captor.capture());

        assertThat(captor.getValue().stream().map(Lesson::getDate).toList()).containsExactly(
                LocalDate.of(2026, 2, 16),
                LocalDate.of(2026, 2, 23),
                LocalDate.of(2026, 3, 2));
    }

    // =========================================================================
    // Helper
    // =========================================================================

    private ScheduleItem buildScheduleItem(Long id, short dayOfWeek, WeekType weekType) {
        ScheduleItem item = new ScheduleItem();
        // Use reflection to set id (no public setter — it's generated)
        try {
            java.lang.reflect.Field idField = ScheduleItem.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(item, id);
        } catch (Exception e) {
            throw new RuntimeException("Could not set ScheduleItem.id via reflection", e);
        }
        item.setDayOfWeek(dayOfWeek);
        item.setWeekType(weekType);
        return item;
    }
}
