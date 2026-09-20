package ru.rutcampustrack.academic.semester;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import ru.rutcampustrack.academic.contract.dto.semester.UpdateSemesterRequest;
import ru.rutcampustrack.academic.entity.Semester;
import ru.rutcampustrack.academic.exception.ConflictException;
import ru.rutcampustrack.academic.repository.AssignmentRepository;
import ru.rutcampustrack.academic.repository.SemesterRepository;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** The semester date guard must share the row lock protocol with assignment create. */
class SemesterAssignmentLockContractTest {

    @Test
    void referencedSemesterCannotChangeDatesAfterRowLock() {
        SemesterRepository semesters = mock(SemesterRepository.class);
        AssignmentRepository assignments = mock(AssignmentRepository.class);
        LocalDate today = LocalDate.now();
        LocalDate existingFrom = today.minusDays(30);
        LocalDate existingTo = today.plusDays(60);
        Semester existing = semester(41L, existingFrom, existingTo);
        when(semesters.findByIdForUpdate(41L)).thenReturn(Optional.of(existing));
        when(assignments.existsBySemesterId(41L)).thenReturn(true);
        SemesterService service = service(semesters, assignments);

        assertThatThrownBy(() -> service.updateSemester(41L,
                new UpdateSemesterRequest("Autumn", existingFrom.plusDays(1), existingTo)))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Нельзя изменить даты");
        verify(semesters, never()).save(any(Semester.class));
        inOrder(semesters, assignments).verify(semesters).findByIdForUpdate(41L);
    }

    private static SemesterService service(SemesterRepository semesters, AssignmentRepository assignments) {
        return new SemesterService(semesters, mock(SemesterAssembler.class), mock(EntityManager.class),
                mock(ApplicationEventPublisher.class), assignments);
    }

    private static Semester semester(Long id, LocalDate from, LocalDate to) {
        Semester semester = new Semester();
        try {
            var field = Semester.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(semester, id);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
        semester.setDateFrom(from);
        semester.setDateTo(to);
        return semester;
    }
}
