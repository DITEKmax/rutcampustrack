package ru.rutcampustrack.academic.assignment;

import org.junit.jupiter.api.Test;
import ru.rutcampustrack.academic.contract.enums.SubjectType;
import ru.rutcampustrack.academic.entity.Assignment;
import ru.rutcampustrack.academic.entity.Semester;
import ru.rutcampustrack.academic.entity.Subject;
import ru.rutcampustrack.academic.entity.User;
import ru.rutcampustrack.academic.exception.BadRequestException;
import ru.rutcampustrack.academic.exception.ConflictException;
import ru.rutcampustrack.academic.repository.AssignmentRepository;
import ru.rutcampustrack.academic.repository.SemesterRepository;
import ru.rutcampustrack.academic.repository.SubjectLessonTypeRepository;
import ru.rutcampustrack.academic.repository.SubjectRepository;
import ru.rutcampustrack.academic.repository.UserRepository;
import ru.rutcampustrack.academic.repository.UserRoleGrantRepository;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/** Focused source tests for the V25 inclusive semester / exclusive assignment dates. */
class AssignmentAuthorityContractTest {

    @Test
    void semesterBoundsAndNullEndUseExclusiveDayAfterSemester() {
        Semester semester = semester(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30));
        AssignmentAuthority.validateDates(semester,
                LocalDate.of(2026, 9, 30), null);

        Assignment assignment = new Assignment(7L, 11L, 22L, 33L,
                SubjectType.LECTURE, LocalDate.of(2026, 9, 30), null);
        assertThat(AssignmentAuthority.effectiveEnd(assignment, semester))
                .isEqualTo(LocalDate.of(2026, 10, 1));
    }

    @Test
    void dateBeforeSemesterAndEndAfterExclusiveBoundaryAreRejected() {
        Semester semester = semester(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30));

        assertThatThrownBy(() -> AssignmentAuthority.validateDates(
                semester, LocalDate.of(2026, 8, 31), null))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> AssignmentAuthority.validateDates(
                semester, LocalDate.of(2026, 9, 30), LocalDate.of(2026, 10, 2)))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void createLocksSemesterBeforeOverlapCheckAndInsert() {
        AssignmentRepository assignments = mock(AssignmentRepository.class);
        SemesterRepository semesters = mock(SemesterRepository.class);
        SubjectRepository subjects = mock(SubjectRepository.class);
        SubjectLessonTypeRepository types = mock(SubjectLessonTypeRepository.class);
        UserRepository users = mock(UserRepository.class);
        UserRoleGrantRepository grants = mock(UserRoleGrantRepository.class);
        Semester semester = semester(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30));
        Subject subject = mock(Subject.class);
        User teacher = mock(User.class);
        when(semesters.findByIdForUpdate(33L)).thenReturn(java.util.Optional.of(semester));
        when(subjects.findById(11L)).thenReturn(java.util.Optional.of(subject));
        when(subject.getGroupId()).thenReturn(22L);
        when(types.existsBySubjectIdAndLessonType(11L, SubjectType.LECTURE)).thenReturn(true);
        when(users.findById(7L)).thenReturn(java.util.Optional.of(teacher));
        when(teacher.getId()).thenReturn(7L);
        when(grants.findByUserIdAndRoleAndStatus(7L, "teacher", "active"))
                .thenReturn(java.util.List.of(mock(ru.rutcampustrack.academic.entity.UserRoleGrant.class)));
        when(assignments.findOverlapping(7L, 11L, 22L, 33L, "lecture",
                LocalDate.of(2026, 9, 1), null)).thenReturn(java.util.Optional.empty());
        when(assignments.save(any(Assignment.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        AssignmentAuthority authority = new AssignmentAuthority(assignments, semesters, subjects,
                types, users, grants);
        authority.create(7L, 11L, 22L, 33L, SubjectType.LECTURE,
                LocalDate.of(2026, 9, 1), null);

        var order = inOrder(semesters, assignments);
        order.verify(semesters).findByIdForUpdate(33L);
        order.verify(assignments).findOverlapping(7L, 11L, 22L, 33L, "lecture",
                LocalDate.of(2026, 9, 1), null);
        order.verify(assignments).save(any(Assignment.class));
    }

    @Test
    void overlappingSameTeacherTypeIsRejectedWithoutInsert() {
        AssignmentRepository assignments = mock(AssignmentRepository.class);
        SemesterRepository semesters = mock(SemesterRepository.class);
        SubjectRepository subjects = mock(SubjectRepository.class);
        SubjectLessonTypeRepository types = mock(SubjectLessonTypeRepository.class);
        UserRepository users = mock(UserRepository.class);
        UserRoleGrantRepository grants = mock(UserRoleGrantRepository.class);
        Semester semester = semester(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30));
        Subject subject = mock(Subject.class);
        User teacher = mock(User.class);
        when(semesters.findByIdForUpdate(33L)).thenReturn(java.util.Optional.of(semester));
        when(subjects.findById(11L)).thenReturn(java.util.Optional.of(subject));
        when(subject.getGroupId()).thenReturn(22L);
        when(types.existsBySubjectIdAndLessonType(11L, SubjectType.LECTURE)).thenReturn(true);
        when(users.findById(7L)).thenReturn(java.util.Optional.of(teacher));
        when(teacher.getId()).thenReturn(7L);
        when(grants.findByUserIdAndRoleAndStatus(7L, "teacher", "active"))
                .thenReturn(java.util.List.of(mock(ru.rutcampustrack.academic.entity.UserRoleGrant.class)));
        when(assignments.findOverlapping(7L, 11L, 22L, 33L, "lecture",
                LocalDate.of(2026, 9, 1), null)).thenReturn(java.util.Optional.of(mock(Assignment.class)));

        AssignmentAuthority authority = new AssignmentAuthority(assignments, semesters, subjects,
                types, users, grants);
        assertThatThrownBy(() -> authority.create(7L, 11L, 22L, 33L, SubjectType.LECTURE,
                LocalDate.of(2026, 9, 1), null)).isInstanceOf(ConflictException.class);
        verify(assignments, never()).save(any(Assignment.class));
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
