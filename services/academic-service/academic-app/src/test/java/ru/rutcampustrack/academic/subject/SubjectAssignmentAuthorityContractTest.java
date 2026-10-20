package ru.rutcampustrack.academic.subject;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;

import ru.rutcampustrack.academic.assignment.AssignmentAuthority;
import ru.rutcampustrack.academic.grpc.ScheduleGrpcClient;
import ru.rutcampustrack.academic.repository.AssignmentRepository;
import ru.rutcampustrack.academic.repository.SemesterRepository;
import ru.rutcampustrack.academic.repository.SubjectLessonTypeRepository;
import ru.rutcampustrack.academic.repository.SubjectRepository;
import ru.rutcampustrack.academic.repository.UserRoleGrantRepository;
import ru.rutcampustrack.academic.security.RequestContext;

/** Headman route and subject closure guards for the V25 authority. */
class SubjectAssignmentAuthorityContractTest {

    private SubjectRepository subjects;
    private SubjectLessonTypeRepository types;
    private AssignmentRepository assignments;
    private SemesterRepository semesters;
    private AssignmentAuthority authority;
    private RequestContext context;
    private ScheduleGrpcClient schedule;
    private SubjectService service;

    @BeforeEach
    void setUp() {
        subjects = mock(SubjectRepository.class);
        types = mock(SubjectLessonTypeRepository.class);
        assignments = mock(AssignmentRepository.class);
        semesters = mock(SemesterRepository.class);
        authority = mock(AssignmentAuthority.class);
        context = mock(RequestContext.class);
        schedule = mock(ScheduleGrpcClient.class);
        service =
                new SubjectService(
                        subjects,
                        types,
                        assignments,
                        semesters,
                        authority,
                        mock(UserRoleGrantRepository.class),
                        context,
                        schedule,
                        mock(org.springframework.context.ApplicationEventPublisher.class));
        when(context.isHeadman()).thenReturn(true);
        when(context.getGroupId()).thenReturn(22L);
    }
}
