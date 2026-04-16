package ru.rutcampustrack.schedule.lesson;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
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
 *   Mondays in range with their ISO-week parity:
 *     Feb 2  → ISO week  6 → EVEN
 *     Feb 9  → ISO week  7 → ODD
 *     Feb 16 → ISO week  8 → EVEN
 *     Feb 23 → ISO week  9 → ODD
 *     Mar 2  → ISO week 10 → EVEN
 *   ODD  template: Feb 9, Feb 23
 *   EVEN template: Feb 2, Feb 16, Mar 2
 *
 * The `firstWeekType` parameter is accepted for signature compatibility but
 * IGNORED — parity is derived from the ISO week number of each candidate date.
 */
@ExtendWith(MockitoExtension.class)
class LessonGenerationServiceTest {

    @Mock
    private LessonRepository lessonRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

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
        service = new LessonGenerationService(lessonRepository, fixedClock, eventPublisher);
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
    // computeLessonDates — ODD week type (ISO-odd weeks)
    // =========================================================================

    /**
     * ODD template: selects Mondays whose ISO-week number is odd.
     * In this semester that's Feb 9 (ISO 7) and Feb 23 (ISO 9).
     * `firstWeekType` is ignored — same result regardless of value.
     */
    @Test
    void computeLessonDates_oddTemplate_firstWeekOdd_returnsOddWeekDates() {
        List<LocalDate> result = service.computeLessonDates(
                SEM_START, SEM_END, WeekType.ODD, DAY_MONDAY, WeekType.ODD);

        assertThat(result).containsExactly(
                LocalDate.of(2026, 2, 9),
                LocalDate.of(2026, 2, 23));
    }

    // =========================================================================
    // computeLessonDates — EVEN week type (ISO-even weeks)
    // =========================================================================

    /**
     * EVEN template: selects Mondays whose ISO-week number is even.
     * In this semester that's Feb 2 (ISO 6), Feb 16 (ISO 8), Mar 2 (ISO 10).
     */
    @Test
    void computeLessonDates_evenTemplate_firstWeekOdd_returnsEvenWeekDates() {
        List<LocalDate> result = service.computeLessonDates(
                SEM_START, SEM_END, WeekType.ODD, DAY_MONDAY, WeekType.EVEN);

        assertThat(result).containsExactly(
                LocalDate.of(2026, 2, 2),
                LocalDate.of(2026, 2, 16),
                LocalDate.of(2026, 3, 2));
    }

    // =========================================================================
    // computeLessonDates — firstWeekType is ignored
    // =========================================================================

    /**
     * Verifies the ISO algorithm does not depend on firstWeekType: passing
     * WeekType.EVEN as firstWeekType yields the same ODD-week dates as
     * passing WeekType.ODD.
     */
    @Test
    void computeLessonDates_oddTemplate_firstWeekEven_returnsSameIsoOddDates() {
        List<LocalDate> result = service.computeLessonDates(
                SEM_START, SEM_END, WeekType.EVEN, DAY_MONDAY, WeekType.ODD);

        assertThat(result).containsExactly(
                LocalDate.of(2026, 2, 9),
                LocalDate.of(2026, 2, 23));
    }

    /**
     * Same insensitivity for EVEN templates.
     */
    @Test
    void computeLessonDates_evenTemplate_firstWeekEven_returnsSameIsoEvenDates() {
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
        // ODD template under ISO algorithm: Feb 9 (ISO 7) and Feb 23 (ISO 9)
        assertThat(savedLessons).hasSize(2);

        savedLessons.forEach(lesson -> {
            assertThat(lesson.getScheduleItemId()).isEqualTo(42L);
            assertThat(lesson.getStatus()).isEqualTo(LessonStatus.PLANNED);
            assertThat(lesson.isGeoBlocked()).isFalse();
            assertThat(lesson.getCreatedAt()).isNotNull();
        });

        assertThat(savedLessons.stream().map(Lesson::getDate).toList()).containsExactly(
                LocalDate.of(2026, 2, 9),
                LocalDate.of(2026, 2, 23));
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
