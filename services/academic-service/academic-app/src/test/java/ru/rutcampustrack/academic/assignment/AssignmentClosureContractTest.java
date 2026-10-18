package ru.rutcampustrack.academic.assignment;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import ru.rutcampustrack.academic.entity.Assignment;
import ru.rutcampustrack.academic.repository.AssignmentRepository;
import ru.rutcampustrack.academic.repository.SemesterRepository;
import ru.rutcampustrack.academic.repository.SubjectRepository;
import ru.rutcampustrack.academic.repository.UserRepository;
import ru.rutcampustrack.academic.repository.UserRoleGrantRepository;
import ru.rutcampustrack.academic.security.RequestContext;

import java.time.LocalDate;

/** The assignment alias forwards its exact durable command without deleting history. */
class AssignmentClosureContractTest {

    private AssignmentRepository assignmentRepository;
    private AssignmentAuthority authority;
    private RequestContext requestContext;
    private AssignmentService service;
    private final AssignmentClosureService closure = mock(AssignmentClosureService.class);

    @BeforeEach
    void setUp() {
        assignmentRepository = mock(AssignmentRepository.class);
        authority = mock(AssignmentAuthority.class);
        requestContext = mock(RequestContext.class);
        service =
                new AssignmentService(
                        assignmentRepository,
                        authority,
                        mock(UserRepository.class),
                        mock(SemesterRepository.class),
                        mock(SubjectRepository.class),
                        mock(UserRoleGrantRepository.class),
                        requestContext,
                        closure);
        when(requestContext.isHeadman()).thenReturn(true);
        when(requestContext.getGroupId()).thenReturn(22L);
    }

    @Test
    void deleteAssignmentDelegatesExactKeyAndDateWithoutRowDeletion() {
        java.util.UUID key = java.util.UUID.randomUUID();
        service.removeAssignment(71L, LocalDate.of(2026, 9, 15), key);
        verify(closure).close(71L, null, null, LocalDate.of(2026, 9, 15), key);
        verify(assignmentRepository, never()).delete(any(Assignment.class));
        verify(assignmentRepository, never()).save(any(Assignment.class));
    }
}
