package ru.rutcampustrack.academic.grpc;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import net.devh.boot.grpc.server.service.GrpcService;
import ru.rutcampustrack.academic.notification.NotificationAudienceResolver;

@GrpcService
public final class NotificationAudienceGrpcService
        extends NotificationAudienceGrpcServiceGrpc.NotificationAudienceGrpcServiceImplBase {
    private final NotificationAudienceResolver resolver;

    public NotificationAudienceGrpcService(NotificationAudienceResolver resolver) { this.resolver = resolver; }

    @Override
    public void resolveNotificationAudience(NotificationAudienceRequest request,
                                            StreamObserver<NotificationAudienceResponse> observer) {
        try {
            NotificationAudienceServiceAdmission.require();
            observer.onNext(resolver.resolve(request));
            observer.onCompleted();
        } catch (StatusRuntimeException error) {
            observer.onError(error);
        } catch (RuntimeException error) {
            observer.onError(Status.UNAVAILABLE.withDescription("notification authority unavailable").asRuntimeException());
        }
    }
}
