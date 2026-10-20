package ru.rutcampustrack.academic.semester;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import ru.rutcampustrack.academic.contract.dto.semester.CreateSemesterRequest;
import ru.rutcampustrack.academic.contract.dto.semester.OverlapCheckResponse;
import ru.rutcampustrack.academic.contract.dto.semester.UpdateSemesterRequest;
import ru.rutcampustrack.academic.contract.enums.SemesterType;
import ru.rutcampustrack.academic.entity.Semester;
import ru.rutcampustrack.academic.exception.BadRequestException;
import ru.rutcampustrack.academic.exception.ConflictException;
import ru.rutcampustrack.academic.repository.AssignmentRepository;
import ru.rutcampustrack.academic.repository.SemesterRepository;
import ru.rutcampustrack.academic.security.RequestContext;
import ru.rutcampustrack.shared.web.time.EducationalTime;

import java.lang.reflect.Field;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.TimeZone;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Unit tests for SemesterService validation added in BUG-006 п.7 / plan 58-05.
 *
 * <p>Covers:
 *
 * <ul>
 *   <li>{@code create}: fully completed range → 400, overlap → 409 "dates".
 *   <li>{@code update}: dateTo&lt;today → 409 "status", overlap → 409 "dates".
 *   <li>{@code checkOverlap}: dry-run endpoint used by async validator.
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class SemesterServiceTest {

    @Mock private SemesterRepository semesterRepository;
    @Mock private SemesterAssembler semesterAssembler;
    @Mock private EntityManager entityManager;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private AssignmentRepository assignmentRepository;
    @Mock private JdbcTemplate jdbcTemplate;

    private SemesterService semesterService;

    @BeforeEach
    void directServiceTransactionContext() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        semesterService =
                new SemesterService(
                        semesterRepository,
                        semesterAssembler,
                        entityManager,
                        eventPublisher,
                        assignmentRepository,
                        jdbcTemplate,
                        Clock.system(ZoneId.of("Europe/Moscow")));
    }

    @AfterEach
    void clearDirectServiceTransactionContext() {
        TransactionSynchronizationManager.setActualTransactionActive(false);
    }

    // ---------- create ----------

    @Test
    void createSemester_fullyCompletedRange_throwsBadRequest() {
        LocalDate yesterday = LocalDate.now().minusDays(2);
        LocalDate completed = LocalDate.now().minusDays(1);
        CreateSemesterRequest req =
                new CreateSemesterRequest("Past", yesterday, completed, SemesterType.SPRING, 2026);

        assertThatThrownBy(() -> semesterService.createSemester(req))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Нельзя создать завершённый семестр");

        verify(semesterRepository, never()).save(any());
    }

    @Test
    void createSemester_startedInPastAndStillCurrent_savesAndReturns() {
        LocalDate from = LocalDate.now().minusDays(10);
        LocalDate to = LocalDate.now().plusMonths(4);
        CreateSemesterRequest req =
                new CreateSemesterRequest("Current", from, to, SemesterType.SPRING, 2026);

        when(semesterRepository.findFirstOverlapping(from, to, null)).thenReturn(Optional.empty());
        when(semesterRepository.save(any(Semester.class))).thenAnswer(inv -> inv.getArgument(0));

        Semester saved = semesterService.createSemester(req);

        assertThat(saved.getDateFrom()).isEqualTo(from);
        assertThat(saved.getDateTo()).isEqualTo(to);
        assertThat(saved.isActive()).isFalse();
    }

    @Test
    void createSemester_overlapsExisting_throwsConflictWithDatesField() {
        LocalDate from = LocalDate.now().plusDays(1);
        LocalDate to = LocalDate.now().plusMonths(4);
        CreateSemesterRequest req =
                new CreateSemesterRequest("Spring 2030", from, to, SemesterType.SPRING, 2030);

        Semester existing = newSemester(7L, "Fall 2029");
        when(semesterRepository.findFirstOverlapping(from, to, null))
                .thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> semesterService.createSemester(req))
                .isInstanceOfSatisfying(
                        ConflictException.class,
                        ex -> {
                            assertThat(ex.getField()).isEqualTo("dates");
                            assertThat(ex.getMessage()).contains("Fall 2029");
                        });

        verify(semesterRepository, never()).save(any());
    }

    @Test
    void createSemester_noOverlap_savesAndReturns() {
        LocalDate from = LocalDate.now().plusDays(1);
        LocalDate to = LocalDate.now().plusMonths(4);
        CreateSemesterRequest req =
                new CreateSemesterRequest("Winter 2030", from, to, SemesterType.AUTUMN, 2030);

        when(semesterRepository.findFirstOverlapping(from, to, null)).thenReturn(Optional.empty());
        when(semesterRepository.save(any(Semester.class))).thenAnswer(inv -> inv.getArgument(0));

        Semester saved = semesterService.createSemester(req);

        assertThat(saved.getName()).isEqualTo("Осенний 2030/2031");
        assertThat(saved.getDateFrom()).isEqualTo(from);
        assertThat(saved.getDateTo()).isEqualTo(to);
        assertThat(saved.getSemesterType()).isEqualTo(SemesterType.AUTUMN);
        assertThat(saved.getAcademicYear()).isEqualTo(2030);
        assertThat(saved.isActive()).isFalse();
    }

    @Test
    void createAndUpdate_typedSemesterNamesUseTheExplicitAcademicYear() {
        semesterService =
                new SemesterService(
                        semesterRepository,
                        semesterAssembler,
                        entityManager,
                        eventPublisher,
                        assignmentRepository,
                        jdbcTemplate,
                        Clock.fixed(
                                Instant.parse("2026-08-15T09:00:00Z"), ZoneId.of("Europe/Moscow")));
        LocalDate autumnFrom = LocalDate.of(2026, 9, 1);
        LocalDate autumnTo = LocalDate.of(2027, 1, 31);
        CreateSemesterRequest create =
                new CreateSemesterRequest(
                        "Клиентское название не является авторитетным",
                        autumnFrom,
                        autumnTo,
                        SemesterType.AUTUMN,
                        2026);
        when(semesterRepository.findFirstOverlapping(autumnFrom, autumnTo, null))
                .thenReturn(Optional.empty());
        when(semesterRepository.save(any(Semester.class))).thenAnswer(inv -> inv.getArgument(0));

        Semester created = semesterService.createSemester(create);

        assertThat(created.getName()).isEqualTo("Осенний 2026/2027");
        assertThat(created.getAcademicYear()).isEqualTo(2026);

        Long id = 91L;
        Semester existing = newSemester(id, "Осенний 2026/2027");
        existing.setDateFrom(autumnFrom);
        existing.setDateTo(autumnTo);
        existing.setSemesterType(SemesterType.AUTUMN);
        existing.setAcademicYear(2026);
        when(semesterRepository.findByIdForUpdate(id)).thenReturn(Optional.of(existing));

        LocalDate springFrom = LocalDate.of(2027, 2, 1);
        LocalDate springTo = LocalDate.of(2027, 6, 30);
        when(semesterRepository.findFirstOverlapping(springFrom, springTo, id))
                .thenReturn(Optional.empty());
        UpdateSemesterRequest update =
                new UpdateSemesterRequest(
                        "Клиентское название не является авторитетным",
                        springFrom,
                        springTo,
                        SemesterType.SPRING,
                        2026);

        Semester updated = semesterService.updateSemester(id, update);

        assertThat(updated.getName()).isEqualTo("Весенний 2026/2027");
        assertThat(updated.getAcademicYear()).isEqualTo(2026);
        assertThat(updated.getSemesterType()).isEqualTo(SemesterType.SPRING);
    }

    @Test
    void createSemester_academicYearCannotOverflowTheDisplayedRange() {
        CreateSemesterRequest request =
                new CreateSemesterRequest(
                        "не используется",
                        LocalDate.of(9999, 1, 1),
                        LocalDate.of(9999, 2, 1),
                        SemesterType.AUTUMN,
                        9999);

        assertThatThrownBy(() -> semesterService.createSemester(request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("от 1 до 9998");
        verify(semesterRepository, never()).findFirstOverlapping(any(), any(), any());
    }

    // ---------- update ----------

    @Test
    void updateSemester_completedSemester_throwsConflictStatus() {
        Long id = 42L;
        Semester completed = newSemester(id, "Fall 2020");
        completed.setDateFrom(LocalDate.now().minusYears(5));
        completed.setDateTo(LocalDate.now().minusYears(4)); // dateTo in the past
        when(semesterRepository.findByIdForUpdate(id)).thenReturn(Optional.of(completed));

        UpdateSemesterRequest req =
                new UpdateSemesterRequest(
                        "Fall 2020 (edit)",
                        LocalDate.now().plusDays(1),
                        LocalDate.now().plusMonths(3));

        assertThatThrownBy(() -> semesterService.updateSemester(id, req))
                .isInstanceOfSatisfying(
                        ConflictException.class,
                        ex -> {
                            assertThat(ex.getField()).isEqualTo("status");
                            assertThat(ex.getMessage()).contains("завершённый");
                        });

        verify(semesterRepository, never()).save(any());
    }

    @Test
    void updateSemester_activeSemester_noOverlap_updatesSuccessfully() {
        Long id = 5L;
        Semester active = newSemester(id, "Spring 2030");
        LocalDate today = LocalDate.now();
        active.setDateFrom(today.plusDays(10));
        active.setDateTo(today.plusMonths(4));
        when(semesterRepository.findByIdForUpdate(id)).thenReturn(Optional.of(active));

        LocalDate newFrom = today.plusDays(5);
        LocalDate newTo = today.plusMonths(5);
        UpdateSemesterRequest req =
                new UpdateSemesterRequest(
                        "Spring 2030 edit", newFrom, newTo, SemesterType.SPRING, 2030);

        when(semesterRepository.findFirstOverlapping(newFrom, newTo, id))
                .thenReturn(Optional.empty());
        when(semesterRepository.save(any(Semester.class))).thenAnswer(inv -> inv.getArgument(0));

        Semester result = semesterService.updateSemester(id, req);

        assertThat(result.getName()).isEqualTo("Весенний 2030/2031");
        assertThat(result.getDateFrom()).isEqualTo(newFrom);
        assertThat(result.getDateTo()).isEqualTo(newTo);
        assertThat(result.getSemesterType()).isEqualTo(SemesterType.SPRING);
        assertThat(result.getAcademicYear()).isEqualTo(2030);
    }

    @Test
    void updateSemester_legacyRowRequiresExplicitClassificationWithoutInventingIt() {
        Long id = 13L;
        Semester legacy = newSemester(id, "Старое название");
        legacy.setDateFrom(LocalDate.now().plusDays(10));
        legacy.setDateTo(LocalDate.now().plusMonths(4));
        when(semesterRepository.findByIdForUpdate(id)).thenReturn(Optional.of(legacy));

        assertThatThrownBy(
                        () ->
                                semesterService.updateSemester(
                                        id,
                                        new UpdateSemesterRequest(
                                                "Старое название",
                                                legacy.getDateFrom(),
                                                legacy.getDateTo())))
                .isInstanceOfSatisfying(
                        BadRequestException.class,
                        error -> assertThat(error.getField()).isEqualTo("semesterType"));
        assertThat(legacy.getSemesterType()).isNull();
        assertThat(legacy.getAcademicYear()).isNull();
        verify(semesterRepository, never()).save(any());
    }

    @Test
    void createSemester_requiresTypeAndAcademicYearBeforeSaving() {
        LocalDate from = LocalDate.now();
        LocalDate to = from.plusMonths(1);
        assertThatThrownBy(
                        () ->
                                semesterService.createSemester(
                                        new CreateSemesterRequest("Legacy", from, to)))
                .isInstanceOfSatisfying(
                        BadRequestException.class,
                        error -> assertThat(error.getField()).isEqualTo("semesterType"));
        assertThatThrownBy(
                        () ->
                                semesterService.createSemester(
                                        new CreateSemesterRequest(
                                                "Legacy", from, to, SemesterType.SPRING, null)))
                .isInstanceOfSatisfying(
                        BadRequestException.class,
                        error -> assertThat(error.getField()).isEqualTo("academicYear"));
        verify(semesterRepository, never()).save(any());
    }

    @Test
    void expiredRestoreUsesMoscowDayBeforeChangingVersionOrTransition() {
        Clock midnightMoscow =
                Clock.fixed(Instant.parse("2026-10-08T21:30:00Z"), ZoneId.of("Europe/Moscow"));
        SemesterService service =
                new SemesterService(
                        semesterRepository,
                        semesterAssembler,
                        entityManager,
                        eventPublisher,
                        assignmentRepository,
                        jdbcTemplate,
                        midnightMoscow);
        Semester archived = newSemester(72L, "Истёкший архив");
        archived.setArchived(true);
        archived.setDateFrom(LocalDate.of(2026, 9, 1));
        archived.setDateTo(LocalDate.of(2026, 10, 8));
        archived.setStateVersion(3);
        when(semesterRepository.findByIdForUpdate(72L)).thenReturn(Optional.of(archived));

        assertThatThrownBy(() -> service.beginRestoreTransition(72L))
                .isInstanceOfSatisfying(
                        ConflictException.class,
                        error -> assertThat(error.getField()).isEqualTo("dateTo"));
        assertThat(archived.getStateVersion()).isEqualTo(3);
        assertThat(archived.getArchiveTransition().name()).isEqualTo("NONE");
        verify(semesterRepository, never()).saveAndFlush(any());
    }

    @Test
    void updateSemester_overlappingOtherSemester_throwsConflictDates() {
        Long id = 5L;
        Semester active = newSemester(id, "Spring 2030");
        LocalDate today = LocalDate.now();
        active.setDateFrom(today.plusDays(10));
        active.setDateTo(today.plusMonths(4));
        when(semesterRepository.findByIdForUpdate(id)).thenReturn(Optional.of(active));

        LocalDate newFrom = today.plusDays(5);
        LocalDate newTo = today.plusMonths(5);
        UpdateSemesterRequest req =
                new UpdateSemesterRequest("Spring 2030", newFrom, newTo, SemesterType.SPRING, 2030);

        Semester other = newSemester(11L, "Summer 2030");
        when(semesterRepository.findFirstOverlapping(newFrom, newTo, id))
                .thenReturn(Optional.of(other));

        assertThatThrownBy(() -> semesterService.updateSemester(id, req))
                .isInstanceOfSatisfying(
                        ConflictException.class,
                        ex -> {
                            assertThat(ex.getField()).isEqualTo("dates");
                            assertThat(ex.getMessage()).contains("Summer 2030");
                        });

        verify(semesterRepository, never()).save(any());
    }

    // ---------- checkOverlap ----------

    @Test
    void checkOverlap_returnsOverlapTrue_withConflictingName() {
        LocalDate from = LocalDate.now().plusDays(1);
        LocalDate to = LocalDate.now().plusMonths(3);
        Semester existing = newSemester(3L, "Autumn 2030");

        when(semesterRepository.findFirstOverlapping(from, to, null))
                .thenReturn(Optional.of(existing));

        OverlapCheckResponse result = semesterService.checkOverlap(from, to, null);

        assertThat(result.overlaps()).isTrue();
        assertThat(result.conflictingName()).isEqualTo("Autumn 2030");
    }

    @Test
    void checkOverlap_excludesSelfInEditMode() {
        LocalDate from = LocalDate.now().plusDays(1);
        LocalDate to = LocalDate.now().plusMonths(3);
        Long selfId = 99L;

        when(semesterRepository.findFirstOverlapping(from, to, selfId))
                .thenReturn(Optional.empty());

        OverlapCheckResponse result = semesterService.checkOverlap(from, to, selfId);

        assertThat(result.overlaps()).isFalse();
        assertThat(result.conflictingName()).isNull();
        verify(semesterRepository).findFirstOverlapping(eq(from), eq(to), eq(selfId));
        verify(semesterRepository, never()).findFirstOverlapping(any(), any(), isNull());
    }

    // ---------- helpers ----------

    @Test
    void educationalSemesterDatesUseMoscowWithoutReplacingSecurityClock() {
        TimeZone previousZone = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
            LocalDate utcDay = LocalDate.now(ZoneOffset.UTC);
            AtomicReference<Instant> teachingInstant =
                    new AtomicReference<>(utcDay.atTime(21, 30).toInstant(ZoneOffset.UTC));
            // Match the existing EventIT clock: withZone snapshots its current instant.
            Clock midnightMoscow =
                    new Clock() {
                        @Override
                        public ZoneId getZone() {
                            return ZoneOffset.UTC;
                        }

                        @Override
                        public Clock withZone(ZoneId zone) {
                            return Clock.fixed(instant(), zone);
                        }

                        @Override
                        public Instant instant() {
                            return teachingInstant.get();
                        }
                    };
            LocalDate teachingDay = utcDay.plusDays(1);
            SemesterService direct =
                    new SemesterService(
                            semesterRepository,
                            semesterAssembler,
                            entityManager,
                            eventPublisher,
                            assignmentRepository,
                            jdbcTemplate,
                            midnightMoscow);
            org.mockito.Mockito.lenient()
                    .when(semesterRepository.save(any(Semester.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            // At 00:30 Moscow this range ended yesterday, although the UTC date is unchanged.
            assertThatThrownBy(
                            () ->
                                    direct.createSemester(
                                            new CreateSemesterRequest(
                                                    "Completed",
                                                    utcDay.minusDays(1),
                                                    utcDay,
                                                    SemesterType.AUTUMN,
                                                    utcDay.getYear())))
                    .isInstanceOfSatisfying(
                            BadRequestException.class,
                            error -> assertThat(error.getField()).isEqualTo("dateTo"));
            Semester completed = newSemester(88L, "Completed");
            completed.setDateFrom(utcDay.minusDays(1));
            completed.setDateTo(utcDay);
            when(semesterRepository.findByIdForUpdate(88L)).thenReturn(Optional.of(completed));
            assertThatThrownBy(
                            () ->
                                    direct.updateSemester(
                                            88L,
                                            new UpdateSemesterRequest(
                                                    "Completed",
                                                    completed.getDateFrom(),
                                                    completed.getDateTo(),
                                                    SemesterType.AUTUMN,
                                                    utcDay.getYear())))
                    .isInstanceOfSatisfying(
                            ConflictException.class,
                            error -> assertThat(error.getField()).isEqualTo("status"));
            verify(semesterRepository, never()).save(any());

            Clock realClock =
                    Clock.fixed(utcDay.atTime(6, 0).toInstant(ZoneOffset.UTC), ZoneOffset.UTC);
            try (var context = new AnnotationConfigApplicationContext()) {
                context.registerBean(SemesterRepository.class, () -> semesterRepository);
                context.registerBean(SemesterAssembler.class, () -> semesterAssembler);
                context.registerBean(EntityManager.class, () -> entityManager);
                context.registerBean(ApplicationEventPublisher.class, () -> eventPublisher);
                context.registerBean(AssignmentRepository.class, () -> assignmentRepository);
                context.registerBean(JdbcTemplate.class, () -> jdbcTemplate);
                context.registerBean(
                        RequestContext.class, () -> org.mockito.Mockito.mock(RequestContext.class));
                context.registerBean(Clock.class, () -> realClock);
                context.registerBean(
                        "defaultEducationalTime",
                        EducationalTime.class,
                        () -> new EducationalTime(realClock));
                context.registerBean(
                        "fixtureEducationalTime",
                        EducationalTime.class,
                        () -> new EducationalTime(midnightMoscow),
                        definition -> definition.setPrimary(true));
                context.registerBean(SemesterService.class);
                context.registerBean(SemesterActivationJob.class);
                context.refresh();
                assertThat(LocalDate.now(realClock)).isEqualTo(utcDay);
                assertThat(LocalDate.now(midnightMoscow.withZone(ZoneId.of("Europe/Moscow"))))
                        .isEqualTo(teachingDay);
                SemesterService service = context.getBean(SemesterService.class);

                completed.setArchived(true);
                completed.setStateVersion(4);
                assertThatThrownBy(() -> service.beginRestoreTransition(88L))
                        .isInstanceOfSatisfying(
                                ConflictException.class,
                                error -> assertThat(error.getField()).isEqualTo("dateTo"));
                assertThat(completed.getStateVersion()).isEqualTo(4);
                assertThat(completed.getArchiveTransition().name()).isEqualTo("NONE");

                Semester due = newSemester(89L, "Due");
                LocalDate nextTeachingDay = teachingDay.plusDays(1);
                due.setDateFrom(nextTeachingDay);
                due.setDateTo(nextTeachingDay.plusDays(30));
                due.setSemesterType(SemesterType.AUTUMN);
                due.setAcademicYear(teachingDay.getYear());
                due.setActivationScheduled(true);
                when(semesterRepository.findDueScheduledActivationIds(any(LocalDate.class)))
                        .thenAnswer(
                                inv ->
                                        nextTeachingDay.equals(inv.getArgument(0))
                                                ? List.of(89L)
                                                : List.of());
                // A wrong date must fail the observable state assertion, not unused-stub checks.
                org.mockito.Mockito.lenient()
                        .when(semesterRepository.findByIdForUpdate(89L))
                        .thenReturn(Optional.of(due));
                org.mockito.Mockito.lenient()
                        .when(semesterRepository.findById(89L))
                        .thenReturn(Optional.of(due));
                org.mockito.Mockito.lenient()
                        .when(semesterRepository.saveAndFlush(any(Semester.class)))
                        .thenAnswer(inv -> inv.getArgument(0));
                context.getBean(SemesterActivationJob.class).activateDueSemesters();
                assertThat(due.isActive()).isFalse();
                assertThat(due.isActivationScheduled()).isTrue();
                teachingInstant.set(utcDay.plusDays(1).atTime(21, 30).toInstant(ZoneOffset.UTC));
                context.getBean(SemesterActivationJob.class).activateDueSemesters();
                assertThat(due.isActive()).isTrue();
                assertThat(due.isActivationScheduled()).isFalse();
                assertThat(due.getStateVersion()).isEqualTo(1);
                context.getBean(SemesterActivationJob.class).activateDueSemesters();
                assertThat(due.getStateVersion()).isEqualTo(1);
            }
        } finally {
            TimeZone.setDefault(previousZone);
        }
    }

    @Test
    void activationRejectsClassificationOverlapAndExpiredCurrentBeforeNoopOrMutation() {
        var fixed = Clock.fixed(Instant.parse("2026-10-08T21:30:00Z"), java.time.ZoneOffset.UTC);
        var service =
                new SemesterService(
                        semesterRepository,
                        semesterAssembler,
                        entityManager,
                        eventPublisher,
                        assignmentRepository,
                        jdbcTemplate,
                        fixed);
        Semester current = newSemester(81L, "Текущий");
        current.setActive(true);
        current.setDateFrom(LocalDate.of(2026, 9, 1));
        current.setDateTo(LocalDate.of(2026, 10, 8));
        when(semesterRepository.findByIdForUpdate(81L)).thenReturn(Optional.of(current));
        assertThatThrownBy(() -> service.activateSemester(81L))
                .isInstanceOf(BadRequestException.class);
        current.setSemesterType(SemesterType.AUTUMN);
        current.setAcademicYear(2026);
        assertThatThrownBy(() -> service.activateSemester(81L))
                .isInstanceOfSatisfying(
                        ConflictException.class,
                        error -> assertThat(error.getField()).isEqualTo("dateTo"));
        current.setDateTo(LocalDate.of(2026, 12, 31));
        when(semesterRepository.findFirstOverlapping(
                        current.getDateFrom(), current.getDateTo(), 81L))
                .thenReturn(Optional.of(newSemester(82L, "Пересечение")));
        assertThatThrownBy(() -> service.activateSemester(81L))
                .isInstanceOfSatisfying(
                        ConflictException.class,
                        error -> assertThat(error.getField()).isEqualTo("dates"));
        assertThat(current.isActive()).isTrue();
        assertThat(current.isActivationScheduled()).isFalse();
        assertThat(current.getStateVersion()).isZero();
        verify(semesterRepository, never()).deactivateAllActive();
        verify(semesterRepository, never()).saveAndFlush(any());
        verifyNoActivationEffects();
    }

    private void verifyNoActivationEffects() {
        org.mockito.Mockito.verifyNoInteractions(eventPublisher, entityManager);
    }

    private static Semester newSemester(Long id, String name) {
        Semester s = new Semester();
        s.setName(name);
        try {
            Field idField = Semester.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(s, id);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
        return s;
    }
}
