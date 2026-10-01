package ru.rutcampustrack.academic.grpc;

import io.grpc.Context;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import ru.rutcampustrack.academic.contract.enums.AccountStatus;
import ru.rutcampustrack.academic.contract.enums.HomeworkPublicationState;
import ru.rutcampustrack.academic.contract.enums.UserRole;
import ru.rutcampustrack.academic.entity.Homework;
import ru.rutcampustrack.academic.entity.User;
import ru.rutcampustrack.academic.homework.HomeworkStudentService;
import ru.rutcampustrack.academic.map.CampusMapReadService;
import ru.rutcampustrack.academic.studentprojection.StudentProjectionScopeService;
import ru.rutcampustrack.academic.repository.GroupRepository;
import ru.rutcampustrack.academic.repository.HomeworkCompletionRepository;
import ru.rutcampustrack.academic.repository.HomeworkRepository;
import ru.rutcampustrack.academic.repository.SubjectRepository;
import ru.rutcampustrack.academic.repository.TeacherSubjectGroupRepository;
import ru.rutcampustrack.academic.repository.UserRepository;
import ru.rutcampustrack.shared.security.InternalJwtClaims;
import ru.rutcampustrack.shared.security.InternalJwtException;
import ru.rutcampustrack.shared.security.InternalJwtValidator;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.params.provider.Arguments.arguments;

class StudentHomeworkGrpcIdentityInterceptorTest {
    private static final UUID SESSION_ID = UUID.fromString("22222222-2222-4222-8222-222222222222");

    private final InternalJwtValidator validator = mock(InternalJwtValidator.class);
    private final StudentHomeworkGrpcIdentityInterceptor interceptor =
            new StudentHomeworkGrpcIdentityInterceptor(validator);

    @Test
    void validTokenBindsClaimsOnlyForHomeworkMutation() {
        InternalJwtClaims claims = new InternalJwtClaims(
                100L, SESSION_ID, 1L, 1L, "STUDENT", "ACTIVE", 10L, false, false);
        when(validator.validate("signed")).thenReturn(claims);
        AtomicReference<InternalJwtClaims> seen = new AtomicReference<>();
        ServerCallHandler<Object, Object> next = (call, headers) -> {
            seen.set(StudentHomeworkGrpcIdentity.CLAIMS.get());
            return new ServerCall.Listener<>() { };
        };

        interceptor.interceptCall(call("SetHomeworkCompletion"), headers("signed"), next);

        assertThat(seen.get()).isEqualTo(claims);
    }

    @Test
    void validTokenBindsClaimsForHomeworkRead() {
        InternalJwtClaims claims = new InternalJwtClaims(
                100L, SESSION_ID, 1L, 1L, "STUDENT", "ACTIVE", 10L, false, false);
        when(validator.validate("signed")).thenReturn(claims);
        AtomicReference<InternalJwtClaims> seen = new AtomicReference<>();
        ServerCallHandler<Object, Object> next = (call, headers) -> {
            seen.set(StudentHomeworkGrpcIdentity.CLAIMS.get());
            return new ServerCall.Listener<>() { };
        };

        interceptor.interceptCall(call("GetHomeworksForWeek"), headers("signed"), next);

        assertThat(seen.get()).isEqualTo(claims);
    }

    @Test
    void validTokenBindsClaimsForStudentProjectionRead() {
        InternalJwtClaims claims = new InternalJwtClaims(
                100L, SESSION_ID, 1L, 1L, "STUDENT", "ACTIVE", 10L, false, false);
        when(validator.validate("signed")).thenReturn(claims);
        AtomicReference<InternalJwtClaims> seen = new AtomicReference<>();
        ServerCallHandler<Object, Object> next = (call, headers) -> {
            seen.set(StudentHomeworkGrpcIdentity.CLAIMS.get());
            return new ServerCall.Listener<>() { };
        };

        interceptor.interceptCall(call("ResolveStudentProjectionScope"), headers("signed"), next);

        assertThat(seen.get()).isEqualTo(claims);
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {
            "GetCampusMapManifest", "GetCampusFloorPlan", "ReadCampusMapAsset", "RecordCampusFloorOpen"})
    void validTokenBindsClaimsForEveryCampusMapOperation(String methodName) {
        InternalJwtClaims claims = new InternalJwtClaims(
                100L, SESSION_ID, 1L, 1L, "STUDENT", "ACTIVE", 10L, false, false);
        when(validator.validate("signed")).thenReturn(claims);
        AtomicReference<InternalJwtClaims> seen = new AtomicReference<>();
        ServerCallHandler<Object, Object> next = (call, headers) -> {
            seen.set(StudentHomeworkGrpcIdentity.CLAIMS.get());
            return new ServerCall.Listener<>() { };
        };

        interceptor.interceptCall(call(methodName), headers("signed"), next);

        assertThat(seen.get()).isEqualTo(claims);
    }

    @Test
    void invalidTokenClosesMutationWithUnauthenticated() {
        when(validator.validate("bad")).thenThrow(new InternalJwtException("bad token"));
        ServerCall<Object, Object> call = call("SetHomeworkCompletion");
        @SuppressWarnings("unchecked")
        ServerCall.Listener<Object> ignored = interceptor.interceptCall(
                call, headers("bad"), (nextCall, metadata) -> new ServerCall.Listener<>() { });

        ArgumentCaptor<Status> status = ArgumentCaptor.forClass(Status.class);
        verify(call).close(status.capture(), any());
        assertThat(status.getValue().getCode()).isEqualTo(Status.Code.UNAUTHENTICATED);
    }

    @ParameterizedTest(name = "{0} {1}")
    @MethodSource("invalidMapTokenCases")
    void missingOrBadMapTokenClosesUnauthenticatedWithoutCallingHandler(
            String tokenCase,
            String methodName,
            String token) {
        when(validator.validate(token)).thenThrow(new InternalJwtException(tokenCase));
        AtomicReference<Boolean> handlerCalled = new AtomicReference<>(false);
        ServerCall<Object, Object> call = call(methodName);
        ServerCallHandler<Object, Object> next = (nextCall, metadata) -> {
            handlerCalled.set(true);
            return new ServerCall.Listener<>() { };
        };

        interceptor.interceptCall(call, headers(token), next);

        ArgumentCaptor<Status> status = ArgumentCaptor.forClass(Status.class);
        verify(call).close(status.capture(), any());
        assertThat(status.getValue().getCode()).isEqualTo(Status.Code.UNAUTHENTICATED);
        assertThat(handlerCalled.get()).isFalse();
    }

    @Test
    void homeworkReadRequiresSignedIdentityBeforeRepositoryLookup() {
        UserRepository users = mock(UserRepository.class);
        HomeworkRepository homeworks = mock(HomeworkRepository.class);
        HomeworkCompletionRepository completions = mock(HomeworkCompletionRepository.class);
        SubjectRepository subjects = mock(SubjectRepository.class);
        AcademicGrpcServiceImpl service = service(users, homeworks, completions, subjects);
        RecordingObserver<HomeworksForWeekResponse> observer = new RecordingObserver<>();

        service.getHomeworksForWeek(request(100L, 10L), observer);

        assertThat(Status.fromThrowable(observer.error).getCode()).isEqualTo(Status.Code.PERMISSION_DENIED);
        verifyNoInteractions(users, homeworks, completions, subjects);
    }

    @Test
    void terminalOwnReadUsesSignedGrantWhenLegacyUserFieldsDisagree() {
        UserRepository users = mock(UserRepository.class);
        HomeworkRepository homeworks = mock(HomeworkRepository.class);
        HomeworkCompletionRepository completions = mock(HomeworkCompletionRepository.class);
        SubjectRepository subjects = mock(SubjectRepository.class);
        AcademicGrpcServiceImpl service = service(users, homeworks, completions, subjects);

        User legacyUser = mock(User.class);
        when(legacyUser.getRole()).thenReturn(UserRole.TEACHER);
        when(legacyUser.getStatus()).thenReturn(AccountStatus.SUSPENDED);
        when(legacyUser.getGroupId()).thenReturn(99L);
        when(users.findByIdIncludingArchived(100L)).thenReturn(Optional.of(legacyUser));

        Homework homework = mock(Homework.class);
        when(homework.getId()).thenReturn(700L);
        when(homework.getSubjectId()).thenReturn(701L);
        when(homework.getLessonDate()).thenReturn(LocalDate.of(2026, 3, 2));
        when(homework.getLessonNumber()).thenReturn(1);
        when(homeworks.findByGroupIdAndSemesterIdAndPublicationStateAndLessonDateBetweenOrderByLessonDateAscLessonNumberAscIdAsc(
                10L, 20L, HomeworkPublicationState.ACTIVE,
                LocalDate.of(2026, 3, 2), LocalDate.of(2026, 3, 2)))
                .thenReturn(List.of(homework));
        when(completions.findByHomeworkIdInAndStudentId(List.of(700L), 100L)).thenReturn(List.of());
        when(subjects.findAllById(Set.of(701L))).thenReturn(List.of());

        InternalJwtClaims terminalClaims = new InternalJwtClaims(
                100L, SESSION_ID, 4L, 8L, "STUDENT", "EXPELLED", 10L, false, true);
        RecordingObserver<HomeworksForWeekResponse> observer = new RecordingObserver<>();
        Context.current().withValue(StudentHomeworkGrpcIdentity.CLAIMS, terminalClaims).run(
                () -> service.getHomeworksForWeek(request(100L, 10L), observer));

        assertThat(observer.error).isNull();
        assertThat(observer.value.getHomeworksList()).hasSize(1);
        assertThat(observer.value.getHomeworks(0).getHomeworkId()).isEqualTo(700L);
        verify(users).findByIdIncludingArchived(100L);
        verify(users, never()).findById(anyLong());
    }

    @Test
    void peerRequestIsDeniedBeforeOwnUserLookup() {
        assertDenied(new InternalJwtClaims(
                        100L, SESSION_ID, 1L, 1L, "STUDENT", "ACTIVE", 10L, false, false),
                request(101L, 10L));
    }

    @Test
    void groupRequestIsDeniedBeforeOwnUserLookup() {
        assertDenied(new InternalJwtClaims(
                        100L, SESSION_ID, 1L, 1L, "STUDENT", "ACTIVE", 10L, false, false),
                request(100L, 11L));
    }

    @Test
    void wrongRoleRequestIsDeniedBeforeOwnUserLookup() {
        assertDenied(new InternalJwtClaims(
                        100L, SESSION_ID, 1L, 1L, "TEACHER", "ACTIVE", 10L, false, false),
                request(100L, 10L));
    }

    private static void assertDenied(InternalJwtClaims claims, HomeworksForWeekRequest request) {
        UserRepository users = mock(UserRepository.class);
        HomeworkRepository homeworks = mock(HomeworkRepository.class);
        HomeworkCompletionRepository completions = mock(HomeworkCompletionRepository.class);
        SubjectRepository subjects = mock(SubjectRepository.class);
        AcademicGrpcServiceImpl service = service(users, homeworks, completions, subjects);
        RecordingObserver<HomeworksForWeekResponse> observer = new RecordingObserver<>();

        Context.current().withValue(StudentHomeworkGrpcIdentity.CLAIMS, claims).run(
                () -> service.getHomeworksForWeek(request, observer));

        assertThat(Status.fromThrowable(observer.error).getCode()).isEqualTo(Status.Code.PERMISSION_DENIED);
        verifyNoInteractions(users, homeworks, completions, subjects);
    }

    private static Stream<Arguments> invalidMapTokenCases() {
        return Stream.of(
                arguments("missing token", "GetCampusMapManifest", (String) null),
                arguments("bad token", "GetCampusMapManifest", "bad"),
                arguments("missing token", "GetCampusFloorPlan", (String) null),
                arguments("bad token", "GetCampusFloorPlan", "bad"),
                arguments("missing token", "ReadCampusMapAsset", (String) null),
                arguments("bad token", "ReadCampusMapAsset", "bad"),
                arguments("missing token", "RecordCampusFloorOpen", (String) null),
                arguments("bad token", "RecordCampusFloorOpen", "bad"),
                arguments("missing token", "GetHeadmanGroupComposition", (String) null),
                arguments("bad token", "GetHeadmanGroupComposition", "bad"));
    }

    private static AcademicGrpcServiceImpl service(
            UserRepository users,
            HomeworkRepository homeworks,
            HomeworkCompletionRepository completions,
            SubjectRepository subjects) {
        return new AcademicGrpcServiceImpl(
                mock(AcademicReadService.class),
                mock(GroupRepository.class),
                users,
                subjects,
                mock(TeacherSubjectGroupRepository.class),
                homeworks,
                completions,
                mock(HeadmanRateLimiter.class),
                mock(HomeworkStudentService.class),
                mock(StudentProjectionScopeService.class),
                mock(CampusMapReadService.class));
    }

    private static HomeworksForWeekRequest request(long studentId, long groupId) {
        return HomeworksForWeekRequest.newBuilder()
                .setStudentId(studentId)
                .setGroupId(groupId)
                .setSemesterId(20L)
                .setDateFrom("2026-03-02")
                .setDateTo("2026-03-02")
                .build();
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

    private static ServerCall<Object, Object> call(String method) {
        @SuppressWarnings("unchecked")
        ServerCall<Object, Object> call = mock(ServerCall.class);
        MethodDescriptor<Object, Object> descriptor = mock(MethodDescriptor.class);
        when(descriptor.getFullMethodName())
                .thenReturn("rutcampustrack.academic.AcademicGrpcService/" + method);
        when(call.getMethodDescriptor()).thenReturn(descriptor);
        return call;
    }

    private static Metadata headers(String token) {
        Metadata metadata = new Metadata();
        if (token != null) {
            metadata.put(StudentHomeworkGrpcIdentityInterceptor.INTERNAL_TOKEN, token);
        }
        return metadata;
    }
}
