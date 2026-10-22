package ru.rutcampustrack.auth.authority;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;

import net.devh.boot.grpc.server.service.GrpcService;

import ru.rutcampustrack.auth.session.AuthSessionException;
import ru.rutcampustrack.authsecurity.grpc.*;
import ru.rutcampustrack.shared.security.admin.AdminAccessException;

import java.util.function.Supplier;

@GrpcService(
        interceptors = {AuthDirectedIdentityInterceptor.class, AuthActorMetadataInterceptor.class})
public final class AuthSecurityCapabilityGrpcService
        extends AuthSecurityCapabilityServiceGrpc.AuthSecurityCapabilityServiceImplBase {
    private final AuthReportCapabilityService reports;
    private final AuthPasswordStepUpService passwords;
    private final ru.rutcampustrack.auth.service.SubjectDeletionProofService subjects;

    public AuthSecurityCapabilityGrpcService(
            AuthReportCapabilityService reports,
            AuthPasswordStepUpService passwords,
            ru.rutcampustrack.auth.service.SubjectDeletionProofService subjects) {
        this.reports = reports;
        this.passwords = passwords;
        this.subjects = subjects;
    }

    @Override
    public void admitSessionIdentity(
            StoredSessionIdentity request, StreamObserver<LiveSessionAuthority> reply) {
        respond(reply, () -> reports.admitSession(request));
    }

    @Override
    public void admitStoredSession(
            AdmitStoredSessionRequest request, StreamObserver<AdmittedReportAuthority> reply) {
        respond(reply, () -> reports.admit(request));
    }

    @Override
    public void issueReportCapability(
            IssueReportCapabilityRequest request, StreamObserver<IssuedReportCapability> reply) {
        respond(reply, () -> reports.issue(request));
    }

    @Override
    public void redeemReportCapability(
            RedeemReportCapabilityRequest request, StreamObserver<RedeemedReportCapability> reply) {
        respond(reply, () -> reports.redeem(request));
    }

    @Override
    public void confirmPasswordStepUp(
            PasswordStepUpRequest request, StreamObserver<PasswordStepUpReceipt> reply) {
        respond(reply, () -> passwords.confirm(request));
    }

    @Override
    public void checkPasswordStepUpReceipt(
            PasswordStepUpReceiptRequest request, StreamObserver<PasswordStepUpReceipt> reply) {
        respond(reply, () -> passwords.check(request));
    }

    @Override
    public void challengeSubjectDeletion(
            SubjectDeletionChallengeRequest request,
            StreamObserver<SubjectDeletionChallenge> reply) {
        respond(
                reply,
                () -> {
                    if (!request.hasBinding()
                            || !request.getUnknownFields().asMap().isEmpty()
                            || request.getPassword().isBlank()
                            || request.getPassword().length() > 256)
                        throw Status.INVALID_ARGUMENT.asRuntimeException();
                    var result =
                            subjects.challenge(
                                    new ru.rutcampustrack.auth.dto.SubjectDeletionProofModels
                                            .ChallengeRequest(
                                            AuthActorMetadataInterceptor.ACTOR_TOKEN.get(),
                                            request.getPassword(),
                                            binding(request.getBinding())));
                    return SubjectDeletionChallenge.newBuilder()
                            .setChallengeId(result.challengeId())
                            .setExpiresAt(result.expiresAt().toString())
                            .setAttemptsRemaining(result.attemptsRemaining())
                            .build();
                });
    }

    @Override
    public void verifySubjectDeletion(
            SubjectDeletionVerifyRequest request,
            StreamObserver<SubjectDeletionVerification> reply) {
        respond(
                reply,
                () -> {
                    if (!request.hasBinding()
                            || !request.getUnknownFields().asMap().isEmpty()
                            || !request.getCode().matches("[0-9]{6}")
                            || !request.getChallengeId().matches("[A-Za-z0-9_-]{32}"))
                        throw Status.INVALID_ARGUMENT.asRuntimeException();
                    var result =
                            subjects.verify(
                                    new ru.rutcampustrack.auth.dto.SubjectDeletionProofModels
                                            .VerifyRequest(
                                            AuthActorMetadataInterceptor.ACTOR_TOKEN.get(),
                                            binding(request.getBinding()),
                                            request.getChallengeId(),
                                            request.getCode()));
                    var value =
                            SubjectDeletionVerification.newBuilder().setAccepted(result.accepted());
                    if (result.receiptId() != null)
                        value.setReceiptId(result.receiptId().toString());
                    return value.build();
                });
    }

    @Override
    public void checkSubjectDeletionReceipt(
            SubjectDeletionReceiptRequest request, StreamObserver<SubjectDeletionReceipt> reply) {
        respond(
                reply,
                () -> {
                    if (!request.hasBinding() || !request.getUnknownFields().asMap().isEmpty())
                        throw Status.INVALID_ARGUMENT.asRuntimeException();
                    return subjects.receipt(
                            AuthActorMetadataInterceptor.ACTOR_TOKEN.get(),
                            binding(request.getBinding()),
                            uuid(request.getReceiptId()));
                });
    }

    @Override
    public void checkStoredSubjectDeletionReceipt(
            SubjectDeletionReceiptRequest request, StreamObserver<SubjectDeletionReceipt> reply) {
        respond(
                reply,
                () -> {
                    if (!request.hasBinding() || !request.getUnknownFields().asMap().isEmpty())
                        throw Status.INVALID_ARGUMENT.asRuntimeException();
                    return subjects.storedReceipt(
                            binding(request.getBinding()), uuid(request.getReceiptId()));
                });
    }

    private static ru.rutcampustrack.auth.dto.SubjectDeletionProofModels.Binding binding(
            SubjectDeletionBinding b) {
        if (!b.getUnknownFields().asMap().isEmpty()
                || b.getSubjectId() <= 0
                || b.getSemesterId() <= 0
                || b.getGroupId() <= 0
                || !b.getPreviewDigest().matches("[0-9a-f]{64}"))
            throw Status.INVALID_ARGUMENT.asRuntimeException();
        return new ru.rutcampustrack.auth.dto.SubjectDeletionProofModels.Binding(
                b.getSubjectId(),
                b.getSemesterId(),
                b.getGroupId(),
                uuid(b.getOperationId()),
                b.getPreviewDigest());
    }

    private static java.util.UUID uuid(String value) {
        try {
            var id = java.util.UUID.fromString(value);
            if (!id.toString().equals(value)) throw new IllegalArgumentException();
            return id;
        } catch (RuntimeException invalid) {
            throw Status.INVALID_ARGUMENT.asRuntimeException();
        }
    }

    private static <T> void respond(StreamObserver<T> reply, Supplier<T> work) {
        try {
            AuthDirectedIdentityInterceptor.require(AuthDirectedIdentityInterceptor.ACADEMIC);
            reply.onNext(work.get());
            reply.onCompleted();
        } catch (StatusRuntimeException known) {
            reply.onError(known);
        } catch (AuthSessionException denied) {
            reply.onError(
                    (denied.code() == AuthSessionException.Code.AUTHORITY_UNAVAILABLE
                                    ? Status.UNAVAILABLE
                                    : Status.PERMISSION_DENIED)
                            .withDescription("Current session authority unavailable")
                            .asRuntimeException());
        } catch (AdminAccessException denied) {
            reply.onError(
                    (denied.getStatusCode().value() == 503
                                    ? Status.UNAVAILABLE
                                    : Status.PERMISSION_DENIED)
                            .withDescription("Current permission unavailable")
                            .asRuntimeException());
        } catch (org.springframework.web.server.ResponseStatusException failure) {
            Status status =
                    switch (failure.getStatusCode().value()) {
                        case 400 -> Status.INVALID_ARGUMENT;
                        case 401 -> Status.UNAUTHENTICATED;
                        case 403 -> Status.PERMISSION_DENIED;
                        case 404 -> Status.NOT_FOUND;
                        case 409 -> Status.FAILED_PRECONDITION;
                        case 429 -> Status.RESOURCE_EXHAUSTED;
                        default -> Status.UNAVAILABLE;
                    };
            reply.onError(
                    status.withDescription("Security proof unavailable").asRuntimeException());
        } catch (RuntimeException unavailable) {
            reply.onError(
                    Status.UNAVAILABLE
                            .withDescription("Security capability unavailable")
                            .asRuntimeException());
        }
    }
}
