package ru.rutcampustrack.academic.semester;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import ru.rutcampustrack.academic.contract.dto.semester.CreateSemesterRequest;
import ru.rutcampustrack.academic.contract.dto.semester.OverlapCheckResponse;
import ru.rutcampustrack.academic.contract.dto.semester.UpdateSemesterRequest;
import ru.rutcampustrack.academic.contract.enums.SemesterType;
import ru.rutcampustrack.academic.entity.Semester;
import ru.rutcampustrack.academic.exception.BadRequestException;
import ru.rutcampustrack.academic.exception.ConflictException;
import ru.rutcampustrack.academic.repository.AssignmentRepository;
import ru.rutcampustrack.academic.repository.SemesterRepository;

import java.lang.reflect.Field;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for SemesterService validation added in BUG-006 п.7 / plan 58-05.
 *
 * Covers:
 * <ul>
 *   <li>{@code create}: fully completed range → 400, overlap → 409 "dates".</li>
 *   <li>{@code update}: dateTo&lt;today → 409 "status", overlap → 409 "dates".</li>
 *   <li>{@code checkOverlap}: dry-run endpoint used by async validator.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class SemesterServiceTest {

    @Mock private SemesterRepository semesterRepository;
    @Mock private SemesterAssembler semesterAssembler;
    @Mock private EntityManager entityManager;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private AssignmentRepository assignmentRepository;

    @InjectMocks private SemesterService semesterService;

    // ---------- create ----------

    @Test
    void createSemester_fullyCompletedRange_throwsBadRequest() {
        LocalDate yesterday = LocalDate.now().minusDays(2);
        LocalDate completed = LocalDate.now().minusDays(1);
        CreateSemesterRequest req = new CreateSemesterRequest("Past", yesterday, completed);

        assertThatThrownBy(() -> semesterService.createSemester(req))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Нельзя создать завершённый семестр");

        verify(semesterRepository, never()).save(any());
    }

    @Test
    void createSemester_startedInPastAndStillCurrent_savesAndReturns() {
        LocalDate from = LocalDate.now().minusDays(10);
        LocalDate to = LocalDate.now().plusMonths(4);
        CreateSemesterRequest req = new CreateSemesterRequest("Current", from, to);

        when(semesterRepository.findFirstOverlapping(from, to, null))
                .thenReturn(Optional.empty());
        when(semesterRepository.save(any(Semester.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        Semester saved = semesterService.createSemester(req);

        assertThat(saved.getDateFrom()).isEqualTo(from);
        assertThat(saved.getDateTo()).isEqualTo(to);
        assertThat(saved.isActive()).isFalse();
    }

    @Test
    void createSemester_overlapsExisting_throwsConflictWithDatesField() {
        LocalDate from = LocalDate.now().plusDays(1);
        LocalDate to = LocalDate.now().plusMonths(4);
        CreateSemesterRequest req = new CreateSemesterRequest("Spring 2030", from, to);

        Semester existing = newSemester(7L, "Fall 2029");
        when(semesterRepository.findFirstOverlapping(from, to, null))
                .thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> semesterService.createSemester(req))
                .isInstanceOfSatisfying(ConflictException.class, ex -> {
                    assertThat(ex.getField()).isEqualTo("dates");
                    assertThat(ex.getMessage()).contains("Fall 2029");
                });

        verify(semesterRepository, never()).save(any());
    }

    @Test
    void createSemester_noOverlap_savesAndReturns() {
        LocalDate from = LocalDate.now().plusDays(1);
        LocalDate to = LocalDate.now().plusMonths(4);
        CreateSemesterRequest req = new CreateSemesterRequest("Winter 2030", from, to, SemesterType.AUTUMN, 2030);

        when(semesterRepository.findFirstOverlapping(from, to, null))
                .thenReturn(Optional.empty());
        when(semesterRepository.save(any(Semester.class)))
                .thenAnswer(inv -> inv.getArgument(0));

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
        LocalDate autumnFrom = LocalDate.of(2026, 9, 1);
        LocalDate autumnTo = LocalDate.of(2027, 1, 31);
        CreateSemesterRequest create = new CreateSemesterRequest(
                "Клиентское название не является авторитетным",
                autumnFrom,
                autumnTo,
                SemesterType.AUTUMN,
                2026
        );
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
        UpdateSemesterRequest update = new UpdateSemesterRequest(
                "Клиентское название не является авторитетным",
                springFrom,
                springTo,
                SemesterType.SPRING,
                2026
        );

        Semester updated = semesterService.updateSemester(id, update);

        assertThat(updated.getName()).isEqualTo("Весенний 2026/2027");
        assertThat(updated.getAcademicYear()).isEqualTo(2026);
        assertThat(updated.getSemesterType()).isEqualTo(SemesterType.SPRING);
    }

    @Test
    void createSemester_academicYearCannotOverflowTheDisplayedRange() {
        CreateSemesterRequest request = new CreateSemesterRequest(
                "не используется",
                LocalDate.of(9999, 1, 1),
                LocalDate.of(9999, 2, 1),
                SemesterType.AUTUMN,
                9999
        );

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
        completed.setDateTo(LocalDate.now().minusYears(4));  // dateTo in the past
        when(semesterRepository.findByIdForUpdate(id)).thenReturn(Optional.of(completed));

        UpdateSemesterRequest req = new UpdateSemesterRequest(
                "Fall 2020 (edit)",
                LocalDate.now().plusDays(1),
                LocalDate.now().plusMonths(3));

        assertThatThrownBy(() -> semesterService.updateSemester(id, req))
                .isInstanceOfSatisfying(ConflictException.class, ex -> {
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
        UpdateSemesterRequest req = new UpdateSemesterRequest("Spring 2030 edit", newFrom, newTo, SemesterType.SPRING, 2030);

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
    void updateSemester_legacyRowKeepsItsNameUntilTypeAndYearAreExplicitlySelected() {
        Long id = 13L;
        Semester legacy = newSemester(id, "Старое название");
        legacy.setDateFrom(LocalDate.now().plusDays(10));
        legacy.setDateTo(LocalDate.now().plusMonths(4));
        when(semesterRepository.findByIdForUpdate(id)).thenReturn(Optional.of(legacy));

        LocalDate from = LocalDate.now().plusDays(11);
        LocalDate to = LocalDate.now().plusMonths(5);
        when(semesterRepository.findFirstOverlapping(from, to, id)).thenReturn(Optional.empty());
        when(semesterRepository.save(any(Semester.class))).thenAnswer(inv -> inv.getArgument(0));

        Semester updated = semesterService.updateSemester(id,
                new UpdateSemesterRequest("Старое название", from, to));

        assertThat(updated.getName()).isEqualTo("Старое название");
        assertThat(updated.getSemesterType()).isNull();
        assertThat(updated.getAcademicYear()).isNull();
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
        UpdateSemesterRequest req = new UpdateSemesterRequest("Spring 2030", newFrom, newTo);

        Semester other = newSemester(11L, "Summer 2030");
        when(semesterRepository.findFirstOverlapping(newFrom, newTo, id))
                .thenReturn(Optional.of(other));

        assertThatThrownBy(() -> semesterService.updateSemester(id, req))
                .isInstanceOfSatisfying(ConflictException.class, ex -> {
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
