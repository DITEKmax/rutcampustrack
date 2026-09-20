package ru.rutcampustrack.academic.subject;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ru.rutcampustrack.academic.assignment.AssignmentAuthority;
import ru.rutcampustrack.academic.contract.dto.subject.AddSubjectTeacherRequest;
import ru.rutcampustrack.academic.contract.dto.subject.CreateSubjectRequest;
import ru.rutcampustrack.academic.contract.dto.subject.InitialAssignmentRequest;
import ru.rutcampustrack.academic.contract.enums.SubjectType;
import ru.rutcampustrack.academic.entity.Assignment;
import ru.rutcampustrack.academic.entity.Semester;
import ru.rutcampustrack.academic.entity.Subject;
import ru.rutcampustrack.academic.event.SubjectDeletedEvent;
import ru.rutcampustrack.academic.exception.AssignmentClosureNotReadyException;
import ru.rutcampustrack.academic.exception.BadRequestException;
import ru.rutcampustrack.academic.grpc.ScheduleGrpcClient;
import ru.rutcampustrack.academic.repository.AssignmentRepository;
import ru.rutcampustrack.academic.repository.SemesterRepository;
import ru.rutcampustrack.academic.repository.SubjectLessonTypeRepository;
import ru.rutcampustrack.academic.repository.SubjectRepository;
import ru.rutcampustrack.academic.repository.UserRoleGrantRepository;
import ru.rutcampustrack.academic.security.RequestContext;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

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
        service = new SubjectService(subjects, types, assignments, semesters, authority,
                mock(UserRoleGrantRepository.class), context, schedule,
                mock(org.springframework.context.ApplicationEventPublisher.class));
        when(context.isHeadman()).thenReturn(true);
        when(context.getGroupId()).thenReturn(22L);
    }

    @Test
    void createLocksEveryReferencedSemesterBeforeSubjectWriteAndCarriesFullTuple() {
        Subject saved = mock(Subject.class);
        when(saved.getId()).thenReturn(11L);
        when(subjects.save(any(Subject.class))).thenReturn(saved);
        Semester semester = mock(Semester.class);
        when(semester.getId()).thenReturn(33L);
        when(authority.lockSemester(33L)).thenReturn(semester);
        InitialAssignmentRequest initial = new InitialAssignmentRequest(7L, 33L,
                SubjectType.LECTURE, LocalDate.of(2026, 9, 1), null);
        CreateSubjectRequest request = new CreateSubjectRequest("Математика", SubjectType.LECTURE,
                List.of(SubjectType.LECTURE, SubjectType.PRACTICE), List.of(initial));

        assertThat(service.createSubject(request)).isSameAs(saved);
        var order = inOrder(authority, subjects, types);
        order.verify(authority).lockSemester(33L);
        order.verify(subjects).save(any(Subject.class));
        verify(authority).createWithLockedSemester(7L, 11L, 22L, semester,
                SubjectType.LECTURE, LocalDate.of(2026, 9, 1), null);
    }

    @Test
    void nonEmptyLegacyTeacherIdsAreRejectedBeforeAnyWrite() {
        CreateSubjectRequest request = new CreateSubjectRequest("Старая форма", SubjectType.LECTURE,
                List.of(7L));

        assertThatThrownBy(() -> service.createSubject(request))
                .isInstanceOf(BadRequestException.class);
        verifyNoInteractions(subjects, types, assignments, authority);
    }

    @Test
    void addTeacherUsesHeadmanGroupAndFullEffectiveTuple() {
        Subject subject = mock(Subject.class);
        when(subject.getId()).thenReturn(11L);
        when(subject.getGroupId()).thenReturn(22L);
        when(subjects.findByIdForUpdate(11L)).thenReturn(Optional.of(subject));
        Semester semester = mock(Semester.class);
        when(authority.lockSemester(33L)).thenReturn(semester);
        Assignment assignment = mock(Assignment.class);
        when(authority.createWithLockedSemester(eq(7L), eq(11L), eq(22L), eq(semester),
                eq(SubjectType.PRACTICE), eq(LocalDate.of(2026, 9, 1)), eq(null)))
                .thenReturn(assignment);

        assertThat(service.addTeacher(11L, 7L, new AddSubjectTeacherRequest(33L,
                SubjectType.PRACTICE, LocalDate.of(2026, 9, 1), null)))
                .isSameAs(assignment);
    }

    @Test
    void subjectWithAssignmentHitsClosureFenceWithoutScheduleOrDeleteWrites() {
        Subject subject = mock(Subject.class);
        when(subject.getGroupId()).thenReturn(22L);
        when(subjects.findByIdForUpdate(11L)).thenReturn(Optional.of(subject));
        when(assignments.existsBySubjectId(11L)).thenReturn(true);

        assertThatThrownBy(() -> service.deleteSubject(11L, true))
                .isInstanceOf(AssignmentClosureNotReadyException.class);
        verify(schedule, never()).countSubjectReferences(11L);
        verify(subjects, never()).delete(any(Subject.class));
        verify(types, never()).deleteBySubjectId(11L);
    }

    @Test
    void tupleDeleteValidatesExactPathThenReturnsSameClosureFence() {
        Subject subject = mock(Subject.class);
        when(subject.getGroupId()).thenReturn(22L);
        when(subjects.findByIdForUpdate(11L)).thenReturn(Optional.of(subject));
        Assignment assignment = mock(Assignment.class);
        when(assignments.findByIdAndSubjectIdAndGroupIdAndTeacherId(71L, 11L, 22L, 7L))
                .thenReturn(Optional.of(assignment));

        assertThatThrownBy(() -> service.removeTeacher(11L, 7L, 71L,
                LocalDate.of(2026, 9, 15)))
                .isInstanceOf(AssignmentClosureNotReadyException.class);
        verify(assignments, never()).delete(any(Assignment.class));
        verify(assignments, never()).save(any(Assignment.class));
    }
}
