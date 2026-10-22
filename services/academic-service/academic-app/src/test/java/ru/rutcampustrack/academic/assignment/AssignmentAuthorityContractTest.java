package ru.rutcampustrack.academic.assignment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import ru.rutcampustrack.academic.contract.enums.SubjectType;
import ru.rutcampustrack.academic.entity.Assignment;
import ru.rutcampustrack.academic.entity.Semester;
import ru.rutcampustrack.academic.exception.BadRequestException;

import java.time.LocalDate;

/** Focused source tests for the V25 inclusive semester / exclusive assignment dates. */
class AssignmentAuthorityContractTest {

    @Test
    void semesterBoundsAndNullEndUseExclusiveDayAfterSemester() {
        Semester semester = semester(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30));
        AssignmentAuthority.validateDates(semester, LocalDate.of(2026, 9, 30), null);

        Assignment assignment =
                new Assignment(
                        7L, 11L, 22L, 33L, SubjectType.LECTURE, LocalDate.of(2026, 9, 30), null);
        assertThat(AssignmentAuthority.effectiveEnd(assignment, semester))
                .isEqualTo(LocalDate.of(2026, 10, 1));
    }

    @Test
    void dateBeforeSemesterAndEndAfterExclusiveBoundaryAreRejected() {
        Semester semester = semester(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30));

        assertThatThrownBy(
                        () ->
                                AssignmentAuthority.validateDates(
                                        semester, LocalDate.of(2026, 8, 31), null))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(
                        () ->
                                AssignmentAuthority.validateDates(
                                        semester,
                                        LocalDate.of(2026, 9, 30),
                                        LocalDate.of(2026, 10, 2)))
                .isInstanceOf(BadRequestException.class);
    }

    private static Semester semester(LocalDate from, LocalDate to) {
        Semester semester = new Semester();
        setId(semester, 33L);
        semester.setDateFrom(from);
        semester.setDateTo(to);
        return semester;
    }

    private static void setId(Semester semester, Long id) {
        try {
            var field = Semester.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(semester, id);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }
}
