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
        String stage = "ACADEMIC_IDENTITY";
        boolean diagnostic =
                request.getAction()
                        == BotAcademicReadAction.BOT_ACADEMIC_READ_ACTION_HEADMAN_CURRENT;
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
            stage = "ACADEMIC_AUTH_BEFORE";
            auth.check(request.getIdentity(), capability);
            stage = "ACADEMIC_QUERY";
            var result = query.read(request.getIdentity(), action);
            stage = "ACADEMIC_AUTH_AFTER";
            auth.check(request.getIdentity(), capability);
            observer.onNext(result);
            observer.onCompleted();
        } catch (StatusRuntimeException error) {
            var code = error.getStatus().getCode();
            var mapped =
                    switch (code) {
                        case INVALID_ARGUMENT,
                                FAILED_PRECONDITION,
                                PERMISSION_DENIED,
                                UNAUTHENTICATED ->
                                error;
                        default -> AuthBotReadAuthorityClient.unavailable();
                    };
            observer.onError(
                    diagnostic
                            ? mapped.getStatus().asRuntimeException(failureProvenance(error, stage))
                            : mapped);
        } catch (RuntimeException error) {
            var mapped = AuthBotReadAuthorityClient.unavailable();
            observer.onError(
                    diagnostic
                            ? mapped.getStatus().asRuntimeException(failureProvenance(error, stage))
                            : mapped);
        }
    }

    // Diagnostic values are closed labels only, never copied exception text or arbitrary trailers.
    private static final io.grpc.Metadata.Key<String> READ_STAGE =
            io.grpc.Metadata.Key.of("rct-bot-read-stage", io.grpc.Metadata.ASCII_STRING_MARSHALLER);
    private static final io.grpc.Metadata.Key<String> READ_CAUSE =
            io.grpc.Metadata.Key.of("rct-bot-read-cause", io.grpc.Metadata.ASCII_STRING_MARSHALLER);

    private static io.grpc.Metadata failureProvenance(RuntimeException error, String stage) {
        String cause;
        if (error instanceof StatusRuntimeException rpc) {
            cause =
                    switch (rpc.getStatus().getCode()) {
                        case UNAVAILABLE -> "STATUS_UNAVAILABLE";
                        case DEADLINE_EXCEEDED -> "STATUS_DEADLINE_EXCEEDED";
                        case UNAUTHENTICATED -> "STATUS_UNAUTHENTICATED";
                        case PERMISSION_DENIED -> "STATUS_PERMISSION_DENIED";
                        case FAILED_PRECONDITION -> "STATUS_FAILED_PRECONDITION";
                        case INVALID_ARGUMENT -> "STATUS_INVALID_ARGUMENT";
                        case NOT_FOUND -> "STATUS_NOT_FOUND";
                        default -> "STATUS_OTHER";
                    };
        } else if (error instanceof org.springframework.dao.DataAccessException)
            cause = "DATA_ACCESS";
        else if (error instanceof ru.rutcampustrack.academic.history.HistoricalMembershipException)
            cause = "HISTORY";
        else if (error instanceof java.time.DateTimeException) cause = "TIME";
        else if (error instanceof IllegalArgumentException) cause = "ARGUMENT";
        else if (error instanceof IllegalStateException) cause = "STATE";
        else if (error instanceof NullPointerException) cause = "NULL";
        else cause = "RUNTIME";
        var trailers = new io.grpc.Metadata();
        trailers.put(READ_STAGE, stage);
        trailers.put(READ_CAUSE, cause);
        return trailers;
    }
}
