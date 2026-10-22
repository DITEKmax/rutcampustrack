package ru.rutcampustrack.auth.authority;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;

import net.devh.boot.grpc.server.service.GrpcService;

import ru.rutcampustrack.authauthority.grpc.*;
import ru.rutcampustrack.shared.security.admin.AdminAccessException;

import java.util.function.Supplier;

@GrpcService(
        interceptors = {AuthDirectedIdentityInterceptor.class, AuthActorMetadataInterceptor.class})
public final class AuthDataAuthorityGrpcService
        extends AuthDataAuthorityServiceGrpc.AuthDataAuthorityServiceImplBase {
    private final AuthIdentityAuthorityQuery identities;
    private final AuthAdministrativeAuthorityQuery admins;

    public AuthDataAuthorityGrpcService(
            AuthIdentityAuthorityQuery identities, AuthAdministrativeAuthorityQuery admins) {
        this.identities = identities;
        this.admins = admins;
    }

    @Override
    public void readIdentityAuthority(
            ReadIdentityAuthorityRequest request,
            StreamObserver<ReadIdentityAuthorityResponse> observer) {
        respond(observer, () -> identities.read(request));
    }

    @Override
    public void getIdentityCatalogWatermark(
            IdentityCatalogWatermarkRequest request,
            StreamObserver<IdentityCatalogWatermark> observer) {
        respond(observer, identities::watermark);
    }

    @Override
    public void readIdentityCatalogChanges(
            IdentityCatalogChangesRequest request,
            StreamObserver<IdentityCatalogChangesResponse> observer) {
        respond(observer, () -> identities.changes(request));
    }

    @Override
    public void checkAdminAccess(
            CheckAdminAccessRequest request, StreamObserver<CheckAdminAccessResponse> observer) {
        respond(observer, () -> admins.check(request));
    }

    @Override
    public void readInitialCredentials(
            ReadInitialCredentialsRequest request,
            StreamObserver<ReadInitialCredentialsResponse> observer) {
        respond(observer, () -> admins.credentials(request));
    }

    private static <T> void respond(StreamObserver<T> observer, Supplier<T> work) {
        try {
            AuthDirectedIdentityInterceptor.require(AuthDirectedIdentityInterceptor.ACADEMIC);
            observer.onNext(work.get());
            observer.onCompleted();
        } catch (StatusRuntimeException failure) {
            observer.onError(failure);
        } catch (AdminAccessException denied) {
            observer.onError(
                    (denied.getStatusCode().value() == 503
                                    ? Status.UNAVAILABLE
                                    : Status.PERMISSION_DENIED)
                            .withDescription("current administrator capability unavailable")
                            .asRuntimeException());
        } catch (RuntimeException unavailable) {
            observer.onError(
                    Status.UNAVAILABLE
                            .withDescription("Auth identity authority unavailable")
                            .asRuntimeException());
        }
    }
}
