package ru.rutcampustrack.academic.assignment;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ru.rutcampustrack.academic.entity.Assignment;
import ru.rutcampustrack.academic.exception.AssignmentClosureNotReadyException;
import ru.rutcampustrack.academic.repository.AssignmentRepository;
import ru.rutcampustrack.academic.repository.SemesterRepository;
import ru.rutcampustrack.academic.repository.SubjectRepository;
import ru.rutcampustrack.academic.repository.UserRepository;
import ru.rutcampustrack.academic.repository.UserRoleGrantRepository;
import ru.rutcampustrack.academic.security.RequestContext;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** The two REST close entry points must fail with zero assignment writes. */
class AssignmentClosureContractTest {

    private AssignmentRepository assignmentRepository;
    private AssignmentAuthority authority;
    private RequestContext requestContext;
    private AssignmentService service;

    @BeforeEach
    void setUp() {
        assignmentRepository = mock(AssignmentRepository.class);
        authority = mock(AssignmentAuthority.class);
        requestContext = mock(RequestContext.class);
        service = new AssignmentService(assignmentRepository, authority,
                mock(UserRepository.class), mock(SemesterRepository.class),
                mock(SubjectRepository.class), mock(UserRoleGrantRepository.class), requestContext);
        when(requestContext.isHeadman()).thenReturn(true);
        when(requestContext.getGroupId()).thenReturn(22L);
    }

    @Test
    void deleteAssignmentAuthenticatesRelationThenReturnsTypedClosureFailure() {
        Assignment assignment = mock(Assignment.class);
        when(assignment.getGroupId()).thenReturn(22L);
        when(assignment.getSemesterId()).thenReturn(33L);
        when(assignment.getValidFrom()).thenReturn(LocalDate.of(2026, 9, 1));
        when(assignment.getValidUntilExclusive()).thenReturn(null);
        when(assignmentRepository.findById(71L)).thenReturn(Optional.of(assignment));

        assertThatThrownBy(() -> service.removeAssignment(71L, LocalDate.of(2026, 9, 15)))
                .isInstanceOf(AssignmentClosureNotReadyException.class);
        verify(assignmentRepository, never()).delete(any(Assignment.class));
        verify(assignmentRepository, never()).save(any(Assignment.class));
    }
}
