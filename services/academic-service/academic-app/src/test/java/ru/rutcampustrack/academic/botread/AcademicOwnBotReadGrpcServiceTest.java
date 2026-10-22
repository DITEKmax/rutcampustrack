package ru.rutcampustrack.academic.botread;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.grpc.Context;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;

import org.junit.jupiter.api.Test;

import ru.rutcampustrack.authauthority.grpc.BotReadCapability;
import ru.rutcampustrack.authauthority.grpc.CheckBotOwnAuthorityResponse;
import ru.rutcampustrack.botreads.grpc.BotAcademicReadAction;
import ru.rutcampustrack.botreads.grpc.BotAcademicScopeRequest;
import ru.rutcampustrack.botreads.grpc.BotAcademicScopeResponse;
import ru.rutcampustrack.botreads.grpc.DirectedBotIdentity;
import ru.rutcampustrack.shared.security.grpc.ServicePrincipal;

import java.util.ArrayList;
import java.util.List;

class AcademicOwnBotReadGrpcServiceTest {
    private final AuthBotReadAuthorityClient auth = mock(AuthBotReadAuthorityClient.class);
    private final AcademicOwnBotReadQuery query = mock(AcademicOwnBotReadQuery.class);
    private final AcademicOwnBotReadGrpcService service =
            new AcademicOwnBotReadGrpcService(auth, query);

    @Test
    void revocationAfterCompositionReturnsNoPartialScope() {
        var request = request(BotAcademicReadAction.BOT_ACADEMIC_READ_ACTION_TEACHER_TODAY);
        when(auth.check(any(), any()))
                .thenReturn(CheckBotOwnAuthorityResponse.getDefaultInstance())
                .thenThrow(Status.FAILED_PRECONDITION.asRuntimeException());
        when(query.read(request.getIdentity(), request.getAction()))
                .thenReturn(BotAcademicScopeResponse.newBuilder().setUserId(7).build());
        var observer = new Observation();
        Context.current()
                .withValue(
                        ServicePrincipal.CONTEXT_KEY, AcademicBotReadServiceIdentityInterceptor.BFF)
                .run(() -> service.resolveOwnBotReadScope(request, observer));
        assertThat(observer.values).isEmpty();
        assertThat(observer.completed).isFalse();
        assertThat(Status.fromThrowable(observer.error).getCode())
                .isEqualTo(Status.Code.FAILED_PRECONDITION);
    }

    @Test
    void attendanceCannotSelectTeacherCapability() {
        var observer = new Observation();
        Context.current()
                .withValue(
                        ServicePrincipal.CONTEXT_KEY,
                        AcademicBotReadServiceIdentityInterceptor.ATTENDANCE)
                .run(
                        () ->
                                service.resolveAttendanceOwnBotReadScope(
                                        request(
                                                BotAcademicReadAction
                                                        .BOT_ACADEMIC_READ_ACTION_TEACHER_TODAY),
                                        observer));
        assertThat(observer.values).isEmpty();
        assertThat(Status.fromThrowable(observer.error).getCode())
                .isEqualTo(Status.Code.INVALID_ARGUMENT);
        verifyNoInteractions(auth, query);
    }

    @Test
    void localPendingOrForeignEligibilityCannotBeReplacedByPositiveAuthAdmission() {
        var request = request(BotAcademicReadAction.BOT_ACADEMIC_READ_ACTION_HEADMAN_CURRENT);
        when(auth.check(
                        request.getIdentity(),
                        BotReadCapability.BOT_READ_CAPABILITY_HEADMAN_CURRENT_ATTENDANCE_READ))
                .thenReturn(CheckBotOwnAuthorityResponse.getDefaultInstance());
        when(query.read(request.getIdentity(), request.getAction()))
                .thenThrow(Status.PERMISSION_DENIED.asRuntimeException());
        var observer = new Observation();
        Context.current()
                .withValue(
                        ServicePrincipal.CONTEXT_KEY, AcademicBotReadServiceIdentityInterceptor.BFF)
                .run(() -> service.resolveOwnBotReadScope(request, observer));
        assertThat(observer.values).isEmpty();
        assertThat(observer.completed).isFalse();
        assertThat(Status.fromThrowable(observer.error).getCode())
                .isEqualTo(Status.Code.PERMISSION_DENIED);
    }

    private static BotAcademicScopeRequest request(BotAcademicReadAction action) {
        return BotAcademicScopeRequest.newBuilder()
                .setAction(action)
                .setIdentity(
                        DirectedBotIdentity.newBuilder()
                                .setTelegramSenderId(42)
                                .setPrivateChatId(42)
                                .setExpectedUserId(7)
                                .setExpectedBindingVersion(3)
                                .setExpectedRolesVersion(5))
                .build();
    }

    private static final class Observation implements StreamObserver<BotAcademicScopeResponse> {
        final List<BotAcademicScopeResponse> values = new ArrayList<>();
        Throwable error;
        boolean completed;

        @Override
        public void onNext(BotAcademicScopeResponse value) {
            values.add(value);
        }

        @Override
        public void onError(Throwable value) {
            error = value;
        }

        @Override
        public void onCompleted() {
            completed = true;
        }
    }
}
