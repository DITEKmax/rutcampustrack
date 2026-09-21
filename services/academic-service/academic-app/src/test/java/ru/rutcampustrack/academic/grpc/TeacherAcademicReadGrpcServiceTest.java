package ru.rutcampustrack.academic.grpc;

import io.grpc.Context;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.Test;
import ru.rutcampustrack.academic.entity.Assignment;
import ru.rutcampustrack.academic.entity.Group;
import ru.rutcampustrack.academic.entity.Semester;
import ru.rutcampustrack.academic.entity.Subject;
import ru.rutcampustrack.academic.repository.AssignmentRepository;
import ru.rutcampustrack.academic.repository.GroupRepository;
import ru.rutcampustrack.academic.repository.SemesterRepository;
import ru.rutcampustrack.academic.repository.SubjectRepository;
import ru.rutcampustrack.academic.repository.UserRoleGrantRepository;
import ru.rutcampustrack.shared.security.InternalJwtClaims;
import ru.rutcampustrack.teacher.grpc.TeacherAssignmentsRequest;
import ru.rutcampustrack.teacher.grpc.TeacherAssignmentsResponse;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Dated teacher assignment reads must retain historical and future authority. */
class TeacherAcademicReadGrpcServiceTest {

    private static final long TEACHER_ID = 71L;

    @Test
    void inactiveSemesterStillReturnsAssignmentForHistoricalRange() {
        AssignmentRepository assignments = mock(AssignmentRepository.class);
        GroupRepository groups = mock(GroupRepository.class);
        SemesterRepository semesters = mock(SemesterRepository.class);
        SubjectRepository subjects = mock(SubjectRepository.class);
        UserRoleGrantRepository grants = mock(UserRoleGrantRepository.class);

        Semester semester = mock(Semester.class);
        when(semester.getDateFrom()).thenReturn(LocalDate.of(2025, 9, 1));
        when(semester.getDateTo()).thenReturn(LocalDate.of(2026, 1, 31));
        when(semester.isActive()).thenReturn(false);
        Assignment assignment = assignment(12L, LocalDate.of(2025, 10, 1), null);
        Subject subject = mock(Subject.class);
        Group group = mock(Group.class);
        when(subject.getName()).thenReturn("Математика");
        when(group.getName()).thenReturn("УИТ-311");
        when(semesters.findById(9L)).thenReturn(Optional.of(semester));
        when(assignments.findByTeacherIdAndSemesterId(TEACHER_ID, 9L)).thenReturn(List.of(assignment));
        when(subjects.findById(22L)).thenReturn(Optional.of(subject));
        when(groups.findById(33L)).thenReturn(Optional.of(group));
        when(grants.findByUserIdAndRoleAndStatus(TEACHER_ID, "teacher", "active"))
                .thenReturn(List.of(mock(ru.rutcampustrack.academic.entity.UserRoleGrant.class)));

        TeacherAcademicReadGrpcService service = new TeacherAcademicReadGrpcService(
                assignments, groups, semesters, subjects, grants);
        RecordingObserver<TeacherAssignmentsResponse> observer = new RecordingObserver<>();
        InternalJwtClaims claims = teacherClaims();

        Context.current().withValue(TeacherAcademicGrpcIdentity.CLAIMS, claims).run(() ->
                service.listTeacherAssignments(TeacherAssignmentsRequest.newBuilder()
                        .setSemesterId(9L)
                        .setDateFrom("2025-12-01")
                        .setDateTo("2025-12-15")
                        .build(), observer));

        assertThat(observer.error).isNull();
        assertThat(observer.value.getAssignmentsList()).singleElement().satisfies(value -> {
            assertThat(value.getAssignmentId()).isEqualTo(12L);
            assertThat(value.getValidUntilExclusive()).isEqualTo("2026-02-01");
        });
    }

    @Test
    void teacherRoleIsRequiredAtDedicatedBoundary() {
        TeacherAcademicReadGrpcService service = new TeacherAcademicReadGrpcService(
                mock(AssignmentRepository.class), mock(GroupRepository.class),
                mock(SemesterRepository.class), mock(SubjectRepository.class),
                mock(UserRoleGrantRepository.class));
        RecordingObserver<TeacherAssignmentsResponse> observer = new RecordingObserver<>();
        InternalJwtClaims student = new InternalJwtClaims(
                7L, UUID.randomUUID(), 1L, 1L, "STUDENT", "ACTIVE", 33L, false, false);

        Context.current().withValue(TeacherAcademicGrpcIdentity.CLAIMS, student).run(() ->
                service.listTeacherAssignments(TeacherAssignmentsRequest.newBuilder()
                        .setSemesterId(9L).setDateFrom("2025-12-01").setDateTo("2025-12-15").build(), observer));

        assertThat(observer.value).isNull();
        assertThat(io.grpc.Status.fromThrowable(observer.error).getCode())
                .isEqualTo(io.grpc.Status.Code.PERMISSION_DENIED);
    }

    private static Assignment assignment(long id, LocalDate validFrom, LocalDate validUntilExclusive) {
        Assignment value = mock(Assignment.class);
        when(value.getId()).thenReturn(id);
        when(value.getTeacherId()).thenReturn(TEACHER_ID);
        when(value.getSubjectId()).thenReturn(22L);
        when(value.getGroupId()).thenReturn(33L);
        when(value.getSemesterId()).thenReturn(9L);
        when(value.getLessonType()).thenReturn(ru.rutcampustrack.academic.contract.enums.SubjectType.LECTURE);
        when(value.getValidFrom()).thenReturn(validFrom);
        when(value.getValidUntilExclusive()).thenReturn(validUntilExclusive);
        return value;
    }

    private static InternalJwtClaims teacherClaims() {
        return new InternalJwtClaims(
                TEACHER_ID, UUID.randomUUID(), 1L, 1L, "TEACHER", "ACTIVE", null, false, false);
    }

    private static final class RecordingObserver<T> implements StreamObserver<T> {
        private T value;
        private Throwable error;

        @Override public void onNext(T value) { this.value = value; }
        @Override public void onError(Throwable error) { this.error = error; }
        @Override public void onCompleted() { }
    }
}
