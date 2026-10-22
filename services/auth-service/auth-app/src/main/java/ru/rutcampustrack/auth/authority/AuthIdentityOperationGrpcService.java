package ru.rutcampustrack.auth.authority;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;

import net.devh.boot.grpc.server.service.GrpcService;

import org.springframework.dao.DataIntegrityViolationException;

import ru.rutcampustrack.authoperations.grpc.*;
import ru.rutcampustrack.shared.security.admin.AdminAccessException;

import java.util.function.Supplier;

@GrpcService(
        interceptors = {AuthDirectedIdentityInterceptor.class, AuthActorMetadataInterceptor.class})
public final class AuthIdentityOperationGrpcService
        extends AuthIdentityOperationServiceGrpc.AuthIdentityOperationServiceImplBase {
    private final AuthIdentityOperationStore operations;

    public AuthIdentityOperationGrpcService(AuthIdentityOperationStore operations) {
        this.operations = operations;
    }

    @Override
    public void prepareIdentityOperation(
            PrepareIdentityOperationRequest request,
            StreamObserver<IdentityOperationResult> observer) {
        respond(observer, () -> operations.prepare(request));
    }

    @Override
    public void commitIdentityOperation(
            CommitIdentityOperationRequest request,
            StreamObserver<IdentityOperationResult> observer) {
        respond(observer, () -> operations.commit(request));
    }

    @Override
    public void getIdentityOperation(
            GetIdentityOperationRequest request, StreamObserver<IdentityOperationResult> observer) {
        respond(observer, () -> operations.get(request));
    }

    @Override
    public void failIdentityOperation(
            FailIdentityOperationRequest request,
            StreamObserver<IdentityOperationResult> observer) {
        respond(observer, () -> operations.fail(request));
    }

    @Override
    public void rejectUnpreparedIdentityOperation(
            RejectUnpreparedIdentityOperationRequest request,
            StreamObserver<IdentityOperationResult> observer) {
        respond(observer, () -> operations.rejectUnprepared(request));
    }

    private static void respond(
            StreamObserver<IdentityOperationResult> observer,
            Supplier<IdentityOperationResult> work) {
        try {
            AuthDirectedIdentityInterceptor.require(AuthDirectedIdentityInterceptor.ACADEMIC);
            observer.onNext(work.get());
            observer.onCompleted();
        } catch (StatusRuntimeException failure) {
            observer.onError(failure);
        } catch (AdminAccessException denied) {
            observer.onError(
                    IdentityOperationFailure.error(
                            denied.getStatusCode().value() == 503
                                    ? Status.UNAVAILABLE
                                    : Status.PERMISSION_DENIED,
                            "ADMIN_PERMISSION_DENIED"));
        } catch (DataIntegrityViolationException conflict) {
            observer.onError(
                    IdentityOperationFailure.error(
                            Status.ALREADY_EXISTS, "IDENTITY_FIELD_CONFLICT"));
        } catch (RuntimeException failure) {
            observer.onError(
                    IdentityOperationFailure.error(
                            Status.UNAVAILABLE, "IDENTITY_AUTHORITY_UNAVAILABLE"));
        }
    }
}
