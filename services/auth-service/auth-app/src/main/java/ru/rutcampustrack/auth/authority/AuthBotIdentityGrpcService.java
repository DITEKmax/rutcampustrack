package ru.rutcampustrack.auth.authority;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;

import net.devh.boot.grpc.server.service.GrpcService;

import ru.rutcampustrack.authauthority.grpc.*;

import java.util.function.Supplier;

/** Bot authority is derived from current durable Auth grants, never a legacy default role. */
@GrpcService(interceptors = AuthDirectedIdentityInterceptor.class)
public final class AuthBotIdentityGrpcService
        extends AuthIdentityGrpcServiceGrpc.AuthIdentityGrpcServiceImplBase {
    private final AuthBotAuthorityQuery query;
    private final AcademicIdentityPresentationClient presentation;

    public AuthBotIdentityGrpcService(
            AuthBotAuthorityQuery query, AcademicIdentityPresentationClient presentation) {
        this.query = query;
        this.presentation = presentation;
    }

    @Override
    public void getOwnBotMenuScope(
            GetOwnBotMenuScopeRequest request,
            StreamObserver<GetOwnBotMenuScopeResponse> observer) {
        respond(
                observer,
                () -> {
                    AuthDirectedIdentityInterceptor.require(AuthDirectedIdentityInterceptor.BOT);
                    var snapshot =
                            query.find(request.getTelegramSenderId(), request.getPrivateChatId());
                    if (snapshot == null) return GetOwnBotMenuScopeResponse.getDefaultInstance();
                    return GetOwnBotMenuScopeResponse.newBuilder()
                            .setFound(true)
                            .setUserId(snapshot.userId())
                            .setTelegramBindingVersion(snapshot.bindingVersion())
                            .setRolesVersion(snapshot.rolesVersion())
                            .addAllGrants(snapshot.grants())
                            .build();
                });
    }

    @Override
    public void getOwnBotProfile(
            GetOwnBotProfileRequest request, StreamObserver<GetOwnBotProfileResponse> observer) {
        respond(
                observer,
                () -> {
                    AuthDirectedIdentityInterceptor.require(AuthDirectedIdentityInterceptor.BOT);
                    var snapshot =
                            query.find(request.getTelegramSenderId(), request.getPrivateChatId());
                    if (snapshot == null) return GetOwnBotProfileResponse.getDefaultInstance();
                    var scope =
                            IdentityPresentationRequest.newBuilder().addUserIds(snapshot.userId());
                    snapshot.grants().stream()
                            .map(BotActiveGrant::getGroupId)
                            .filter(id -> id > 0)
                            .distinct()
                            .forEach(scope::addGroupIds);
                    var labels = presentation.resolve(scope.build());
                    var current =
                            query.find(request.getTelegramSenderId(), request.getPrivateChatId());
                    if (current == null
                            || current.userId() != snapshot.userId()
                            || current.bindingVersion() != snapshot.bindingVersion()
                            || current.rolesVersion() != snapshot.rolesVersion())
                        throw Status.FAILED_PRECONDITION
                                .withDescription("bot authority changed")
                                .asRuntimeException();
                    var result =
                            GetOwnBotProfileResponse.newBuilder()
                                    .setFound(true)
                                    .setUserId(current.userId())
                                    .setTelegramBindingVersion(current.bindingVersion())
                                    .setRolesVersion(current.rolesVersion())
                                    .setLogin(current.login())
                                    .setPasswordChanged(current.passwordChanged());
                    var users =
                            labels.getUsersList().stream()
                                    .filter(u -> u.getUserId() == current.userId())
                                    .toList();
                    if (users.size() != 1)
                        throw Status.UNAVAILABLE
                                .withDescription("identity presentation unavailable")
                                .asRuntimeException();
                    result.setDisplayName(users.getFirst().getDisplayName());
                    if (scope.getGroupIdsCount() == 1)
                        labels.getGroupsList().stream()
                                .filter(g -> g.getGroupId() == scope.getGroupIds(0))
                                .findFirst()
                                .ifPresent(g -> result.setGroupName(g.getLabel()));
                    return result.build();
                });
    }

    @Override
    public void checkBotOwnAuthority(
            CheckBotOwnAuthorityRequest request,
            StreamObserver<CheckBotOwnAuthorityResponse> observer) {
        respond(
                observer,
                () -> {
                    AuthDirectedIdentityInterceptor.require(AuthDirectedIdentityInterceptor.BFF);
                    if (request.getRequiredCapability()
                            == BotReadCapability.BOT_READ_CAPABILITY_HOMEWORK_READ)
                        throw AuthBotAuthorityQuery.denied();
                    return query.check(request);
                });
    }

    @Override
    public void checkAcademicBotOwnAuthority(
            CheckBotOwnAuthorityRequest request,
            StreamObserver<CheckBotOwnAuthorityResponse> observer) {
        respond(
                observer,
                () -> {
                    AuthDirectedIdentityInterceptor.require(
                            AuthDirectedIdentityInterceptor.ACADEMIC);
                    return query.check(request);
                });
    }

    @Override
    public void checkAttendanceBotOwnAuthority(
            CheckBotOwnAuthorityRequest request,
            StreamObserver<CheckBotOwnAuthorityResponse> observer) {
        respond(
                observer,
                () -> {
                    AuthDirectedIdentityInterceptor.require(
                            AuthDirectedIdentityInterceptor.ATTENDANCE);
                    if (request.getRequiredCapability()
                            != BotReadCapability
                                    .BOT_READ_CAPABILITY_HEADMAN_CURRENT_ATTENDANCE_READ)
                        throw AuthBotAuthorityQuery.denied();
                    return query.check(request);
                });
    }

    private static <T> void respond(StreamObserver<T> observer, Supplier<T> work) {
        try {
            observer.onNext(work.get());
            observer.onCompleted();
        } catch (StatusRuntimeException failure) {
            observer.onError(failure);
        } catch (RuntimeException failure) {
            observer.onError(
                    Status.UNAVAILABLE
                            .withDescription("Auth authority unavailable")
                            .asRuntimeException());
        }
    }
}
