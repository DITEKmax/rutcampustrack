package ru.rutcampustrack.academic.botread;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;

import net.devh.boot.grpc.server.service.GrpcService;

import ru.rutcampustrack.authauthority.grpc.BotReadCapability;
import ru.rutcampustrack.botreads.grpc.AcademicOwnBotReadServiceGrpc;
import ru.rutcampustrack.botreads.grpc.BotAcademicReadAction;
import ru.rutcampustrack.botreads.grpc.BotAcademicScopeRequest;
import ru.rutcampustrack.botreads.grpc.BotAcademicScopeResponse;

/** Auth checks bracket the local domain transaction; no RPC runs under its locks. */
@GrpcService
public final class AcademicOwnBotReadGrpcService
        extends AcademicOwnBotReadServiceGrpc.AcademicOwnBotReadServiceImplBase {
    private final AuthBotReadAuthorityClient auth;
    private final AcademicOwnBotReadQuery query;

    public AcademicOwnBotReadGrpcService(
            AuthBotReadAuthorityClient auth, AcademicOwnBotReadQuery query) {
        this.auth = auth;
        this.query = query;
    }

    @Override
    public void resolveOwnBotReadScope(
            BotAcademicScopeRequest request, StreamObserver<BotAcademicScopeResponse> observer) {
        resolve(request, observer, false);
    }

    @Override
    public void resolveAttendanceOwnBotReadScope(
            BotAcademicScopeRequest request, StreamObserver<BotAcademicScopeResponse> observer) {
        resolve(request, observer, true);
    }

    private void resolve(
            BotAcademicScopeRequest request,
            StreamObserver<BotAcademicScopeResponse> observer,
            boolean attendance) {
        try {
            AcademicBotReadServiceIdentityInterceptor.require(
                    attendance
                            ? AcademicBotReadServiceIdentityInterceptor.ATTENDANCE
                            : AcademicBotReadServiceIdentityInterceptor.BFF);
            var action = request.getAction();
            if (action != BotAcademicReadAction.BOT_ACADEMIC_READ_ACTION_TEACHER_TODAY
                            && action
                                    != BotAcademicReadAction
                                            .BOT_ACADEMIC_READ_ACTION_HEADMAN_CURRENT
                    || attendance
                            && action
                                    != BotAcademicReadAction
                                            .BOT_ACADEMIC_READ_ACTION_HEADMAN_CURRENT) {
                throw Status.INVALID_ARGUMENT
                        .withDescription("fixed own-read action required")
                        .asRuntimeException();
            }
            var capability =
                    action == BotAcademicReadAction.BOT_ACADEMIC_READ_ACTION_TEACHER_TODAY
                            ? BotReadCapability.BOT_READ_CAPABILITY_TEACHER_TODAY_READ
                            : BotReadCapability.BOT_READ_CAPABILITY_HEADMAN_CURRENT_ATTENDANCE_READ;
            auth.check(request.getIdentity(), capability);
            var result = query.read(request.getIdentity(), action);
            auth.check(request.getIdentity(), capability);
            observer.onNext(result);
            observer.onCompleted();
        } catch (StatusRuntimeException error) {
            var code = error.getStatus().getCode();
            observer.onError(
                    switch (code) {
                        case INVALID_ARGUMENT,
                                FAILED_PRECONDITION,
                                PERMISSION_DENIED,
                                UNAUTHENTICATED ->
                                error;
                        default -> AuthBotReadAuthorityClient.unavailable();
                    });
        } catch (RuntimeException error) {
            observer.onError(AuthBotReadAuthorityClient.unavailable());
        }
    }
}
