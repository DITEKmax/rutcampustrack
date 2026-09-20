package ru.rutcampustrack.academic.grpc;

import com.google.protobuf.InvalidProtocolBufferException;
import io.grpc.Context;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.protobuf.StatusProto;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.CannotCreateTransactionException;
import ru.rutcampustrack.academic.homework.HomeworkStudentService;
import ru.rutcampustrack.academic.repository.GroupRepository;
import ru.rutcampustrack.academic.repository.HomeworkCompletionRepository;
import ru.rutcampustrack.academic.repository.HomeworkRepository;
import ru.rutcampustrack.academic.repository.SubjectRepository;
import ru.rutcampustrack.academic.repository.TeacherSubjectGroupRepository;
import ru.rutcampustrack.academic.repository.UserRepository;
import ru.rutcampustrack.academic.studentprojection.StudentProjectionException;
import ru.rutcampustrack.academic.studentprojection.StudentProjectionScope;
import ru.rutcampustrack.academic.studentprojection.StudentProjectionScopeService;
import ru.rutcampustrack.shared.security.InternalJwtClaims;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StudentProjectionGrpcServiceTest {

    private static final UUID SESSION_ID =
            UUID.fromString("22222222-2222-4222-8222-222222222222");

    @Test
    void resolveScopeMapsSegmentsSubjectsAndIndependentRankCohort() {
        StudentProjectionScopeService resolver = mock(StudentProjectionScopeService.class);
        InternalJwtClaims claims = claims();
        StudentProjectionScope scope = new StudentProjectionScope(
                100L,
                42L,
                LocalDate.of(2026, 9, 1),
                LocalDate.of(2026, 9, 30),
                false,
                List.of(1001L, 1002L),
                List.of(new StudentProjectionScope.Subject(
                        501L, "Algorithms", "lecture", 10L, List.of("lecture", "seminar"))),
                List.of(new StudentProjectionScope.MembershipSegment(
                        10L,
                        LocalDate.of(2026, 9, 1),
                        LocalDate.of(2026, 10, 1),
                        List.of(501L))),
                StudentProjectionScope.RankVisibility.VISIBLE,
                10L,
                true,
                LocalDate.of(2026, 9, 15));
        when(resolver.resolve(42L, claims)).thenReturn(scope);
        AcademicGrpcServiceImpl service = service(resolver);
        RecordingObserver<StudentProjectionScopeResponse> observer = new RecordingObserver<>();

        Context.current().withValue(StudentHomeworkGrpcIdentity.CLAIMS, claims).run(
                () -> service.resolveStudentProjectionScope(
                        StudentProjectionScopeRequest.newBuilder().setSemesterId(42L).build(), observer));

        assertThat(observer.error).isNull();
        assertThat(observer.value.getStudentId()).isEqualTo(100L);
        assertThat(observer.value.getSemesterId()).isEqualTo(42L);
        assertThat(observer.value.getDateFrom()).isEqualTo("2026-09-01");
        assertThat(observer.value.getDateTo()).isEqualTo("2026-09-30");
        assertThat(observer.value.getOwnMembershipSegmentsList()).singleElement()
                .satisfies(segment -> {
                    assertThat(segment.getGroupId()).isEqualTo(10L);
                    assertThat(segment.getDateUntilExclusive()).isEqualTo("2026-10-01");
                    assertThat(segment.getSubjectIdsList()).containsExactly(501L);
                });
        assertThat(observer.value.getSubjectsList()).singleElement()
                .satisfies(subject -> {
                    assertThat(subject.getSubjectId()).isEqualTo(501L);
                    assertThat(subject.getGroupId()).isEqualTo(10L);
                    assertThat(subject.getLessonTypesList()).containsExactly("lecture", "seminar");
                });
        assertThat(observer.value.getActiveRosterUserIdsList()).containsExactly(1001L, 1002L);
        assertThat(observer.value.getRankVisibility())
                .isEqualTo(StudentProjectionRankVisibility.STUDENT_PROJECTION_RANK_VISIBILITY_VISIBLE);
        assertThat(observer.value.hasRankGroupId()).isTrue();
        assertThat(observer.value.getRankGroupId()).isEqualTo(10L);
        assertThat(observer.value.getRankEligible()).isTrue();
        assertThat(observer.value.getServerDate()).isEqualTo("2026-09-15");
        verify(resolver).resolve(42L, claims);
    }

    @Test
    void resolveScopeCarriesStableTypedErrorDetailAndGrpcMapping() {
        StudentProjectionScopeService resolver = mock(StudentProjectionScopeService.class);
        InternalJwtClaims claims = claims();
        when(resolver.resolve(42L, claims))
                .thenThrow(StudentProjectionException.invalidSession("stale roles version"));
        AcademicGrpcServiceImpl service = service(resolver);
        RecordingObserver<StudentProjectionScopeResponse> observer = new RecordingObserver<>();

        Context.current().withValue(StudentHomeworkGrpcIdentity.CLAIMS, claims).run(
                () -> service.resolveStudentProjectionScope(
                        StudentProjectionScopeRequest.newBuilder().setSemesterId(42L).build(), observer));

        assertThat(observer.value).isNull();
        assertThat(observer.error).isInstanceOf(StatusRuntimeException.class);
        assertThat(Status.fromThrowable(observer.error).getCode()).isEqualTo(Status.Code.UNAUTHENTICATED);
        com.google.rpc.Status status = StatusProto.fromThrowable(observer.error);
        assertThat(status).isNotNull();
        assertThat(status.getDetailsList()).singleElement().satisfies(detail -> {
            assertThat(detail.is(AcademicProjectionErrorDetail.class)).isTrue();
            try {
                AcademicProjectionErrorDetail unpacked = detail.unpack(AcademicProjectionErrorDetail.class);
                assertThat(unpacked.getCode())
                        .isEqualTo(AcademicProjectionErrorCode.ACADEMIC_PROJECTION_ERROR_CODE_INVALID_SESSION);
            } catch (InvalidProtocolBufferException exception) {
                throw new AssertionError(exception);
            }
        });
    }

    @Test
    void unresolvedStudentScopeMapsToPermissionDeniedWithStableCode() {
        StudentProjectionScopeService resolver = mock(StudentProjectionScopeService.class);
        InternalJwtClaims claims = claims();
        when(resolver.resolve(42L, claims))
                .thenThrow(StudentProjectionException.unresolved("student history is unavailable"));
        AcademicGrpcServiceImpl service = service(resolver);
        RecordingObserver<StudentProjectionScopeResponse> observer = new RecordingObserver<>();

        Context.current().withValue(StudentHomeworkGrpcIdentity.CLAIMS, claims).run(
                () -> service.resolveStudentProjectionScope(
                        StudentProjectionScopeRequest.newBuilder().setSemesterId(42L).build(), observer));

        assertThat(observer.value).isNull();
        assertThat(observer.error).isInstanceOf(StatusRuntimeException.class);
        assertThat(Status.fromThrowable(observer.error).getCode()).isEqualTo(Status.Code.PERMISSION_DENIED);
        com.google.rpc.Status status = StatusProto.fromThrowable(observer.error);
        assertThat(status).isNotNull();
        assertThat(status.getDetailsList()).singleElement().satisfies(detail -> {
            try {
                AcademicProjectionErrorDetail unpacked = detail.unpack(AcademicProjectionErrorDetail.class);
                assertThat(unpacked.getCode()).isEqualTo(
                        AcademicProjectionErrorCode.ACADEMIC_PROJECTION_ERROR_CODE_STUDENT_SCOPE_UNRESOLVED);
            } catch (InvalidProtocolBufferException exception) {
                throw new AssertionError(exception);
            }
        });
    }

    @Test
    void inconsistentAuthorityMapsToUnavailableWithStableCode() {
        StudentProjectionScopeService resolver = mock(StudentProjectionScopeService.class);
        InternalJwtClaims claims = claims();
        when(resolver.resolve(42L, claims))
                .thenThrow(StudentProjectionException.inconsistent("current history diverges from grant"));
        AcademicGrpcServiceImpl service = service(resolver);
        RecordingObserver<StudentProjectionScopeResponse> observer = new RecordingObserver<>();

        Context.current().withValue(StudentHomeworkGrpcIdentity.CLAIMS, claims).run(
                () -> service.resolveStudentProjectionScope(
                        StudentProjectionScopeRequest.newBuilder().setSemesterId(42L).build(), observer));

        assertThat(observer.value).isNull();
        assertThat(observer.error).isInstanceOf(StatusRuntimeException.class);
        assertThat(Status.fromThrowable(observer.error).getCode()).isEqualTo(Status.Code.UNAVAILABLE);
        com.google.rpc.Status status = StatusProto.fromThrowable(observer.error);
        assertThat(status).isNotNull();
        assertThat(status.getDetailsList()).singleElement().satisfies(detail -> {
            try {
                AcademicProjectionErrorDetail unpacked = detail.unpack(AcademicProjectionErrorDetail.class);
                assertThat(unpacked.getCode()).isEqualTo(
                        AcademicProjectionErrorCode.ACADEMIC_PROJECTION_ERROR_CODE_INCONSISTENT_SOURCE);
            } catch (InvalidProtocolBufferException exception) {
                throw new AssertionError(exception);
            }
        });
    }

    @Test
    void pastClosedHistoryDivergenceMapsToUnavailableWithInconsistentSource() {
        StudentProjectionScopeService resolver = mock(StudentProjectionScopeService.class);
        InternalJwtClaims claims = claims();
        when(resolver.resolve(42L, claims))
                .thenThrow(StudentProjectionException.inconsistent(
                        "Past active scope has no current authoritative group"));
        AcademicGrpcServiceImpl service = service(resolver);
        RecordingObserver<StudentProjectionScopeResponse> observer = new RecordingObserver<>();

        Context.current().withValue(StudentHomeworkGrpcIdentity.CLAIMS, claims).run(
                () -> service.resolveStudentProjectionScope(
                        StudentProjectionScopeRequest.newBuilder().setSemesterId(42L).build(), observer));

        assertThat(observer.value).isNull();
        assertThat(observer.error).isInstanceOf(StatusRuntimeException.class);
        assertThat(Status.fromThrowable(observer.error).getCode()).isEqualTo(Status.Code.UNAVAILABLE);
        com.google.rpc.Status status = StatusProto.fromThrowable(observer.error);
        assertThat(status).isNotNull();
        try {
            AcademicProjectionErrorDetail detail = status.getDetails(0)
                    .unpack(AcademicProjectionErrorDetail.class);
            assertThat(detail.getCode()).isEqualTo(
                    AcademicProjectionErrorCode.ACADEMIC_PROJECTION_ERROR_CODE_INCONSISTENT_SOURCE);
        } catch (InvalidProtocolBufferException exception) {
            throw new AssertionError(exception);
        }
    }

    @Test
    void transactionBeginFailureMapsToUnavailableWithDependencyCode() {
        StudentProjectionScopeService resolver = mock(StudentProjectionScopeService.class);
        InternalJwtClaims claims = claims();
        when(resolver.resolve(42L, claims))
                .thenThrow(new CannotCreateTransactionException("transaction begin failed"));
        AcademicGrpcServiceImpl service = service(resolver);
        RecordingObserver<StudentProjectionScopeResponse> observer = new RecordingObserver<>();

        Context.current().withValue(StudentHomeworkGrpcIdentity.CLAIMS, claims).run(
                () -> service.resolveStudentProjectionScope(
                        StudentProjectionScopeRequest.newBuilder().setSemesterId(42L).build(), observer));

        assertThat(observer.value).isNull();
        assertThat(observer.error).isInstanceOf(StatusRuntimeException.class);
        assertThat(Status.fromThrowable(observer.error).getCode()).isEqualTo(Status.Code.UNAVAILABLE);
        com.google.rpc.Status status = StatusProto.fromThrowable(observer.error);
        assertThat(status).isNotNull();
        try {
            AcademicProjectionErrorDetail detail = status.getDetails(0)
                    .unpack(AcademicProjectionErrorDetail.class);
            assertThat(detail.getCode()).isEqualTo(
                    AcademicProjectionErrorCode.ACADEMIC_PROJECTION_ERROR_CODE_DEPENDENCY_UNAVAILABLE);
        } catch (InvalidProtocolBufferException exception) {
            throw new AssertionError(exception);
        }
    }

    @Test
    void unknownProgrammerFailureRemainsInternalAndUnspecified() {
        StudentProjectionScopeService resolver = mock(StudentProjectionScopeService.class);
        InternalJwtClaims claims = claims();
        when(resolver.resolve(42L, claims))
                .thenThrow(new IllegalStateException("programmer failure"));
        AcademicGrpcServiceImpl service = service(resolver);
        RecordingObserver<StudentProjectionScopeResponse> observer = new RecordingObserver<>();

        Context.current().withValue(StudentHomeworkGrpcIdentity.CLAIMS, claims).run(
                () -> service.resolveStudentProjectionScope(
                        StudentProjectionScopeRequest.newBuilder().setSemesterId(42L).build(), observer));

        assertThat(observer.value).isNull();
        assertThat(observer.error).isInstanceOf(StatusRuntimeException.class);
        assertThat(Status.fromThrowable(observer.error).getCode()).isEqualTo(Status.Code.INTERNAL);
        com.google.rpc.Status status = StatusProto.fromThrowable(observer.error);
        assertThat(status).isNotNull();
        try {
            AcademicProjectionErrorDetail detail = status.getDetails(0)
                    .unpack(AcademicProjectionErrorDetail.class);
            assertThat(detail.getCode())
                    .isEqualTo(AcademicProjectionErrorCode.ACADEMIC_PROJECTION_ERROR_CODE_UNSPECIFIED);
        } catch (InvalidProtocolBufferException exception) {
            throw new AssertionError(exception);
        }
    }

    private static AcademicGrpcServiceImpl service(StudentProjectionScopeService resolver) {
        return new AcademicGrpcServiceImpl(
                mock(AcademicReadService.class),
                mock(GroupRepository.class),
                mock(UserRepository.class),
                mock(SubjectRepository.class),
                mock(TeacherSubjectGroupRepository.class),
                mock(HomeworkRepository.class),
                mock(HomeworkCompletionRepository.class),
                mock(HeadmanRateLimiter.class),
                mock(HomeworkStudentService.class),
                resolver);
    }

    private static InternalJwtClaims claims() {
        return new InternalJwtClaims(
                100L, SESSION_ID, 1L, 7L, "STUDENT", "ACTIVE", 10L, false, false);
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
        public void onCompleted() {
        }
    }
}
