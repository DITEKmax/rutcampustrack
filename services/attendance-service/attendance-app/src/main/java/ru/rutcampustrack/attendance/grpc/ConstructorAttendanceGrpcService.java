package ru.rutcampustrack.attendance.grpc;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import net.devh.boot.grpc.server.service.GrpcService;
import ru.rutcampustrack.attendance.constructor.*;
import ru.rutcampustrack.constructor.grpc.*;
import ru.rutcampustrack.shared.security.*;
import ru.rutcampustrack.shared.security.grpc.ServicePrincipal;

@GrpcService
public class ConstructorAttendanceGrpcService extends ConstructorAttendanceParticipantGrpc.ConstructorAttendanceParticipantImplBase {
    private final ConstructorDeletionParticipant deletion;
    private final ConstructorPastMaterializer materializer;
    private final InternalJwtValidator validator;
    public ConstructorAttendanceGrpcService(ConstructorDeletionParticipant deletion, ConstructorPastMaterializer materializer, InternalJwtValidator validator) {
        this.deletion = deletion; this.materializer = materializer; this.validator = validator;
    }

    @Override public void applyDeletion(ConstructorDeletionCommand command, StreamObserver<ConstructorParticipantReceipt> observer) {
        respond(observer, () -> {
            requireSchedule();
            String token = ConstructorAttendanceIdentityInterceptor.ACTOR_TOKEN.get();
            if (token != null) {
                InternalJwtClaims actor;
                try { actor = validator.validate(token); } catch (InternalJwtException invalid) { throw Status.UNAUTHENTICATED.asRuntimeException(); }
                if (actor.userId() != command.getOriginalActorId() || actor.readOnly()
                        || !("ADMIN".equals(actor.role()) || actor.isHeadman() && java.util.Objects.equals(actor.groupId(), command.getScope().getGroupId())))
                    throw Status.PERMISSION_DENIED.asRuntimeException();
            } else if (command.getAction() == ConstructorDeletionAction.CONSTRUCTOR_DELETION_PREVIEW) {
                throw Status.UNAUTHENTICATED.withDescription("Signed actor required for preview").asRuntimeException();
            }
            // Directed Schedule owns durable accepted commands. Recovery never requires a new JWT.
            return deletion.apply(command);
        });
    }

    @Override public void materializePast(PastLessonMaterializationCommand command, StreamObserver<PastLessonMaterializationReceipt> observer) {
        respond(observer, () -> { requireSchedule(); return materializer.apply(command); });
    }

    private static void requireSchedule() {
        if (!ServicePrincipal.SCHEDULE_SERVICE.equals(ServicePrincipal.CONTEXT_KEY.get())) throw Status.UNAUTHENTICATED.asRuntimeException();
    }
    private static <T> void respond(StreamObserver<T> observer, java.util.function.Supplier<T> action) {
        try { observer.onNext(action.get()); observer.onCompleted(); }
        catch (StatusRuntimeException denied) { observer.onError(denied); }
        catch (IllegalStateException | IllegalArgumentException conflict) { observer.onError(Status.FAILED_PRECONDITION.withDescription("Constructor scope or history conflict").asRuntimeException()); }
        catch (RuntimeException unavailable) { observer.onError(Status.UNAVAILABLE.withDescription("Constructor participant unavailable").asRuntimeException()); }
    }
}
