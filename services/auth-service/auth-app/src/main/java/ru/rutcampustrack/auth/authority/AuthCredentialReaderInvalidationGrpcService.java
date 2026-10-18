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
public final class AuthCredentialReaderInvalidationGrpcService
        extends AuthCredentialReaderInvalidationServiceGrpc
                .AuthCredentialReaderInvalidationServiceImplBase {
    private final AuthCredentialReaderInvalidationStore store;

    public AuthCredentialReaderInvalidationGrpcService(
            AuthCredentialReaderInvalidationStore store) {
        this.store = store;
    }

    @Override
    public void prepareCredentialReaderInvalidation(
            PrepareCredentialReaderInvalidationRequest request,
            StreamObserver<CredentialReaderInvalidationResult> observer) {
        respond(observer, () -> store.prepare(request));
    }

    @Override
    public void getCredentialReaderInvalidation(
            GetCredentialReaderInvalidationRequest request,
            StreamObserver<CredentialReaderInvalidationResult> observer) {
        respond(observer, () -> store.get(request));
    }

    @Override
    public void commitCredentialReaderInvalidation(
            CommitCredentialReaderInvalidationRequest request,
            StreamObserver<CredentialReaderInvalidationResult> observer) {
        respond(observer, () -> store.commit(request));
    }

    @Override
    public void failCredentialReaderInvalidation(
            FailCredentialReaderInvalidationRequest request,
            StreamObserver<CredentialReaderInvalidationResult> observer) {
        respond(observer, () -> store.fail(request));
    }

    @Override
    public void rejectUnpreparedCredentialReaderInvalidation(
            RejectUnpreparedCredentialReaderInvalidationRequest request,
            StreamObserver<CredentialReaderInvalidationResult> observer) {
        respond(observer, () -> store.rejectUnprepared(request));
    }

    private static void respond(
            StreamObserver<CredentialReaderInvalidationResult> observer,
            Supplier<CredentialReaderInvalidationResult> work) {
        try {
            AuthDirectedIdentityInterceptor.require(AuthDirectedIdentityInterceptor.ACADEMIC);
            observer.onNext(work.get());
            observer.onCompleted();
        } catch (StatusRuntimeException failure) {
            observer.onError(failure);
        } catch (AdminAccessException denied) {
            observer.onError(
                    Status.PERMISSION_DENIED
                            .withDescription("GROUPS_MANAGE_REQUIRED")
                            .asRuntimeException());
        } catch (DataIntegrityViolationException conflict) {
            observer.onError(CredentialReaderInvalidationCommand.conflict());
        } catch (RuntimeException unavailable) {
            observer.onError(
                    Status.UNAVAILABLE
                            .withDescription("INVALIDATION_UNAVAILABLE")
                            .asRuntimeException());
        }
    }
}
