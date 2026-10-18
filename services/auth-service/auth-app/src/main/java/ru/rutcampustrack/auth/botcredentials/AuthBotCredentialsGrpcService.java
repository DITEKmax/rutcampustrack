package ru.rutcampustrack.auth.botcredentials;

import io.grpc.BindableService;
import io.grpc.ServerServiceDefinition;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.ServerCalls;
import io.grpc.stub.StreamObserver;

import net.devh.boot.grpc.server.service.GrpcService;

import ru.rutcampustrack.academic.grpc.AcademicGrpcServiceGrpc;
import ru.rutcampustrack.academic.grpc.BotOwnInitialCredentialsRequest;
import ru.rutcampustrack.academic.grpc.BotOwnInitialCredentialsResponse;

/** Reuses the published protobuf read wire, exposing only that method on Auth. */
@GrpcService(interceptors = AuthBotCredentialServiceIdentityInterceptor.class)
public final class AuthBotCredentialsGrpcService implements BindableService {
    private final AuthBotOwnInitialCredentialsQuery credentials;

    public AuthBotCredentialsGrpcService(AuthBotOwnInitialCredentialsQuery credentials) {
        this.credentials = credentials;
    }

    @Override
    public ServerServiceDefinition bindService() {
        return ServerServiceDefinition.builder(AcademicGrpcServiceGrpc.SERVICE_NAME)
                .addMethod(
                        AcademicGrpcServiceGrpc.getGetOwnBotInitialCredentialsMethod(),
                        ServerCalls.asyncUnaryCall(this::read))
                .build();
    }

    private void read(
            BotOwnInitialCredentialsRequest request,
            StreamObserver<BotOwnInitialCredentialsResponse> observer) {
        try {
            var response = credentials.read(request);
            observer.onNext(response);
            observer.onCompleted();
        } catch (StatusRuntimeException error) {
            observer.onError(error);
        } catch (RuntimeException error) {
            // Never propagate database messages, request metadata or credential values.
            observer.onError(
                    Status.UNAVAILABLE
                            .withDescription("own credential authority unavailable")
                            .asRuntimeException());
        }
    }
}
