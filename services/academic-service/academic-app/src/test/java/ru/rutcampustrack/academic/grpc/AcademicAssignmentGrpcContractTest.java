package ru.rutcampustrack.academic.grpc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.grpc.stub.StreamObserver;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import ru.rutcampustrack.academic.entity.Assignment;
import ru.rutcampustrack.academic.entity.Semester;
import ru.rutcampustrack.academic.homework.HomeworkStudentService;
import ru.rutcampustrack.academic.repository.AssignmentRepository;
import ru.rutcampustrack.academic.repository.GroupRepository;
import ru.rutcampustrack.academic.repository.HomeworkCompletionRepository;
import ru.rutcampustrack.academic.repository.HomeworkRepository;
import ru.rutcampustrack.academic.repository.SemesterRepository;
import ru.rutcampustrack.academic.repository.SubjectRepository;
import ru.rutcampustrack.academic.repository.UserRepository;
import ru.rutcampustrack.academic.repository.UserRoleGrantRepository;

/** gRPC source contract: historical IDs are atomic and null ends become concrete. */
class AcademicAssignmentGrpcContractTest {

    private static final ZoneId MOSCOW = ZoneId.of("Europe/Moscow");

    @Test
    void assignmentsByIdsReturnsIdentityAndEffectiveEnd() {
        AssignmentRepository assignments = mock(AssignmentRepository.class);
        SemesterRepository semesters = mock(SemesterRepository.class);
        Assignment assignment = mock(Assignment.class);
        Semester semester = new Semester();
        semester.setDateTo(LocalDate.of(2026, 9, 30));
        when(assignment.getId()).thenReturn(71L);
        when(assignment.getTeacherId()).thenReturn(7L);
        when(assignment.getSubjectId()).thenReturn(11L);
        when(assignment.getGroupId()).thenReturn(22L);
        when(assignment.getSemesterId()).thenReturn(33L);
        when(assignment.getLessonType())
                .thenReturn(ru.rutcampustrack.academic.contract.enums.SubjectType.LECTURE);
        when(assignment.getValidFrom()).thenReturn(LocalDate.of(2026, 9, 1));
        when(assignment.getValidUntilExclusive()).thenReturn(null);
        when(assignment.getLifecycleState())
                .thenReturn(ru.rutcampustrack.academic.assignment.AssignmentLifecycle.ACTIVE);
        when(assignments.findAllById(List.of(71L))).thenReturn(List.of(assignment));
        when(semesters.findById(33L)).thenReturn(Optional.of(semester));

        AcademicGrpcServiceImpl service =
                new AcademicGrpcServiceImpl(
                        mock(AcademicReadService.class),
                        mock(GroupRepository.class),
                        mock(UserRepository.class),
                        mock(SubjectRepository.class),
                        assignments,
                        semesters,
                        mock(UserRoleGrantRepository.class),
                        mock(HomeworkRepository.class),
                        mock(HomeworkCompletionRepository.class),
                        mock(HeadmanRateLimiter.class),
                        mock(HomeworkStudentService.class));
        RecordingObserver<AssignmentsByIdsResponse> observer = new RecordingObserver<>();

        service.getAssignmentsByIds(
                AssignmentsByIdsRequest.newBuilder().addAssignmentIds(71L).build(), observer);

        assertThat(observer.error).isNull();
        assertThat(observer.value.getAssignmentsList())
                .singleElement()
                .satisfies(
                        info -> {
                            assertThat(info.getId()).isEqualTo(71L);
                            assertThat(info.getValidUntilExclusive()).isEqualTo("2026-10-01");
                        });
    }

    @Test
    void teacherSubjectsReturnsOnlyCurrentEffectiveAssignmentsWithConcreteEnd() {
        AssignmentRepository assignments = mock(AssignmentRepository.class);
        SemesterRepository semesters = mock(SemesterRepository.class);
        UserRoleGrantRepository grants = mock(UserRoleGrantRepository.class);
        SubjectRepository subjects = mock(SubjectRepository.class);
        GroupRepository groups = mock(GroupRepository.class);
        LocalDate today = LocalDate.now(MOSCOW);
        LocalDate semesterFrom = today.minusDays(30);
        LocalDate semesterTo = today.plusDays(90);
        LocalDate activeFrom = today.minusDays(10);
        LocalDate futureFrom = today.plusDays(30);
        LocalDate expiredUntilExclusive = today.minusDays(1);
        Semester semester = new Semester();
        semester.setDateFrom(semesterFrom);
        semester.setDateTo(semesterTo);
        semester.setActive(true);
        Assignment active = assignment(71L, activeFrom, null);
        Assignment future = assignment(72L, futureFrom, null);
        Assignment expired = assignment(73L, semesterFrom, expiredUntilExclusive);
        when(grants.findByUserIdAndRoleAndStatus(7L, "teacher", "active"))
                .thenReturn(List.of(mock(ru.rutcampustrack.academic.entity.UserRoleGrant.class)));
        when(semesters.findById(33L)).thenReturn(Optional.of(semester));
        when(assignments.findByTeacherIdAndSemesterId(7L, 33L))
                .thenReturn(List.of(active, future, expired));
        ru.rutcampustrack.academic.entity.Subject subject =
                mock(ru.rutcampustrack.academic.entity.Subject.class);
        when(subject.getName()).thenReturn("Математика");
        ru.rutcampustrack.academic.entity.Group group =
                mock(ru.rutcampustrack.academic.entity.Group.class);
        when(group.getName()).thenReturn("ПИ-101");
        when(subjects.findById(11L)).thenReturn(Optional.of(subject));
        when(groups.findById(22L)).thenReturn(Optional.of(group));

        AcademicGrpcServiceImpl service =
                new AcademicGrpcServiceImpl(
                        mock(AcademicReadService.class),
                        groups,
                        mock(UserRepository.class),
                        subjects,
                        assignments,
                        semesters,
                        grants,
                        mock(HomeworkRepository.class),
                        mock(HomeworkCompletionRepository.class),
                        mock(HeadmanRateLimiter.class),
                        mock(HomeworkStudentService.class));
        RecordingObserver<TeacherSubjectsResponse> observer = new RecordingObserver<>();

        service.getTeacherSubjects(
                TeacherSubjectsRequest.newBuilder().setTeacherId(7L).setSemesterId(33L).build(),
                observer);

        assertThat(observer.error).isNull();
        assertThat(observer.value.getSubjectsList())
                .singleElement()
                .satisfies(
                        info -> {
                            assertThat(info.getAssignmentId()).isEqualTo(71L);
                            assertThat(info.getLessonType()).isEqualTo("lecture");
                            assertThat(info.getValidFrom()).isEqualTo(activeFrom.toString());
                            assertThat(info.getValidUntilExclusive())
                                    .isEqualTo(semesterTo.plusDays(1).toString());
                        });
    }

    @Test
    void lifecycleRpcKeepsFrozenTextCodesAfterDomainTyping() {
        var assignments = mock(AssignmentRepository.class);
        var operations =
                mock(
                        ru.rutcampustrack.academic.repository
                                .AssignmentReplacementOperationRepository.class);
        var operation =
                mock(ru.rutcampustrack.academic.entity.AssignmentReplacementOperation.class);
        var operationId = java.util.UUID.randomUUID();
        var from = LocalDate.of(2026, 9, 1);
        var until = LocalDate.of(2026, 10, 1);
        when(operations.findById(operationId)).thenReturn(Optional.of(operation));
        when(operation.getOperationId()).thenReturn(operationId);
        when(operation.getOperationKind())
                .thenReturn(
                        ru.rutcampustrack.academic.assignment.AssignmentOperationKind
                                .ADOPT_UNASSIGNED);
        when(operation.getState())
                .thenReturn(
                        ru.rutcampustrack.academic.assignment.AssignmentOperationState.PREPARED);
        when(operation.getTargetAssignmentId()).thenReturn(71L);
        when(operation.getEffectiveFrom()).thenReturn(from);
        when(operation.getRequestedCutoff()).thenReturn(from);
        when(operation.getSourceValidUntil()).thenReturn(until);
        when(operation.getTargetValidUntil()).thenReturn(until);
        when(operation.getPayloadHash()).thenReturn(new byte[32]);
        Assignment assignment = assignment(71L, from, until);
        when(assignments.findById(71L)).thenReturn(Optional.of(assignment));
        var service =
                new AcademicGrpcServiceImpl(
                        null,
                        null,
                        null,
                        null,
                        assignments,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        operations,
                        null);
        for (var state : ru.rutcampustrack.academic.assignment.AssignmentLifecycle.values()) {
            when(assignment.getLifecycleState()).thenReturn(state);
            var observer = new RecordingObserver<PreparedAssignmentCloseResponse>();
            service.getPreparedAssignmentCloseOperation(
                    PreparedAssignmentCloseRequest.newBuilder()
                            .setOperationId(operationId.toString())
                            .build(),
                    observer);
            assertThat(observer.error).isNull();
            assertThat(observer.value.getTargetLifecycleState()).isEqualTo(state.getCode());
            assertThat(observer.value.getLessonType()).isEqualTo("lecture");
        }
    }

    private static Assignment assignment(Long id, LocalDate from, LocalDate until) {
        Assignment assignment = mock(Assignment.class);
        when(assignment.getId()).thenReturn(id);
        when(assignment.getTeacherId()).thenReturn(7L);
        when(assignment.getSubjectId()).thenReturn(11L);
        when(assignment.getGroupId()).thenReturn(22L);
        when(assignment.getSemesterId()).thenReturn(33L);
        when(assignment.getLessonType())
                .thenReturn(ru.rutcampustrack.academic.contract.enums.SubjectType.LECTURE);
        when(assignment.getValidFrom()).thenReturn(from);
        when(assignment.getValidUntilExclusive()).thenReturn(until);
        when(assignment.getLifecycleState())
                .thenReturn(ru.rutcampustrack.academic.assignment.AssignmentLifecycle.ACTIVE);
        return assignment;
    }

    private static final class RecordingObserver<T> implements StreamObserver<T> {
        private T value;
        private Throwable error;

        @Override
        public void onNext(T value) {
            this.value = value;
        }

        @Override
        public void onError(Throwable error) {
            this.error = error;
        }

        @Override
        public void onCompleted() {}
    }
}
