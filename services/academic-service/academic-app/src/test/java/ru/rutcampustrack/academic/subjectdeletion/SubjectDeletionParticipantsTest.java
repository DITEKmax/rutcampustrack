package ru.rutcampustrack.academic.subjectdeletion;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import io.grpc.*;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import ru.rutcampustrack.academic.contract.dto.subject.SubjectDeletionModels.Phase;
import ru.rutcampustrack.academic.security.RequestContext;
import ru.rutcampustrack.authsecurity.grpc.AuthSecurityCapabilityServiceGrpc;
import ru.rutcampustrack.authsecurity.grpc.StoredSessionIdentity;
import ru.rutcampustrack.authsecurity.grpc.SubjectDeletionBinding;
import ru.rutcampustrack.shared.security.InternalJwtClaims;
import ru.rutcampustrack.shared.security.grpc.DirectedServiceCredential;
import ru.rutcampustrack.subjectdeletion.grpc.*;

import java.time.*;
import java.util.*;
import java.util.Base64;
import java.util.function.Function;

/** Captures the real stub CallCredentials; no socket or service process is started. */
class SubjectDeletionParticipantsTest {
    private static final String TOKEN =
            Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[32]);

    @Test
    void bothParticipantClientsEmitServiceProofOnlyAfterExactTlsAuthorityAdmission() {
        for (boolean schedule : new boolean[] {true, false}) {
            var channel =
                    new CapturingChannel(
                            schedule ? "schedule-service" : "attendance-service",
                            SecurityLevel.PRIVACY_AND_INTEGRITY,
                            false);
            var client = client(channel, schedule);
            var response = schedule ? client.schedule(command()) : client.attendance(command());
            assertThat(response.getSubjectId()).isEqualTo(20);
            assertThat(channel.released).isTrue();
            assertThat(channel.deadlineBounded).isTrue();
        }
    }

    @Test
    void plaintextOrWrongAuthorityNeverEmitsCredentialAndCannotReturnReceipt() {
        for (var channel :
                java.util.List.of(
                        new CapturingChannel("schedule-service", SecurityLevel.NONE, false),
                        new CapturingChannel(
                                "foreign-service", SecurityLevel.PRIVACY_AND_INTEGRITY, false))) {
            var client = client(channel, true);
            assertThatThrownBy(() -> client.schedule(command()))
                    .isInstanceOf(ResponseStatusException.class);
            assertThat(channel.released).isFalse();
        }
    }

    @Test
    void mismatchedReceiptCannotBecomeAConfirmedParticipant() {
        var channel =
                new CapturingChannel("schedule-service", SecurityLevel.PRIVACY_AND_INTEGRITY, true);
        assertThatThrownBy(() -> client(channel, true).schedule(command()))
                .isInstanceOf(ResponseStatusException.class);
    }

    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2026-10-18T12:00:00Z"), ZoneOffset.UTC);
    private static final UUID SID = UUID.fromString("00000000-0000-4000-8000-000000000042");
    private static final UUID PROOF = UUID.fromString("00000000-0000-4000-8000-000000000043");

    @Test
    void authReceiptRequiresTlsExactAuthorityAndReleasesSignedActorOnlyAfterTransportAdmission() {
        var wrongMethod =
                new AuthChannel(
                        "auth-service", SecurityLevel.PRIVACY_AND_INTEGRITY, this::authResponse);
        wrongMethod.wrongMethod = true;
        for (var channel :
                List.of(
                        new AuthChannel(
                                "auth-service",
                                SecurityLevel.PRIVACY_AND_INTEGRITY,
                                this::authResponse),
                        new AuthChannel("auth-service", SecurityLevel.NONE, this::authResponse),
                        new AuthChannel(
                                "foreign-service",
                                SecurityLevel.PRIVACY_AND_INTEGRITY,
                                this::authResponse),
                        wrongMethod)) {
            var context = new RequestContext();
            context.setClaims(
                    new InternalJwtClaims(42, SID, 1, 2, "HEADMAN", "ACTIVE", 10L, true, false));
            context.setInternalToken("synthetic-signed-actor");
            var client = authClient(context, channel);
            if (channel.security == SecurityLevel.PRIVACY_AND_INTEGRITY
                    && channel.authority.equals("auth-service")
                    && !channel.wrongMethod) {
                var admission =
                        client.check(
                                new SubjectDeletionAcademicData.Target(
                                        20, 30L, 10, "", context.getClaims()),
                                UUID.fromString(command().getOperationId()),
                                "a".repeat(64),
                                PROOF);
                assertThat(
                                admission.matches(
                                        PROOF,
                                        UUID.fromString(command().getOperationId()),
                                        "a".repeat(64),
                                        20,
                                        30,
                                        10,
                                        42,
                                        SID,
                                        CLOCK.instant()))
                        .isTrue();
                var target =
                        new SubjectDeletionAcademicData.Target(
                                20, 30L, 10, "", context.getClaims());
                UUID operation = UUID.fromString(command().getOperationId());
                assertThat(
                                client.challenge(
                                                target,
                                                new ru.rutcampustrack.academic.contract.dto.subject
                                                        .SubjectDeletionModels.ChallengeRequest(
                                                        operation,
                                                        "a".repeat(64),
                                                        "synthetic-password"))
                                        .challengeId())
                        .isEqualTo("S".repeat(32));
                assertThat(
                                client.verify(
                                        target,
                                        new ru.rutcampustrack.academic.contract.dto.subject
                                                .SubjectDeletionModels.ConfirmRequest(
                                                operation,
                                                "a".repeat(64),
                                                "S".repeat(32),
                                                "123456")))
                        .isEqualTo(PROOF);
                assertThat(channel.methods)
                        .containsExactly(
                                "CheckSubjectDeletionReceipt",
                                "ChallengeSubjectDeletion",
                                "VerifySubjectDeletion");
                assertThat(channel.bearer).isEqualTo("synthetic-signed-actor");
                assertThat(channel.released).isTrue();
            } else {
                assertThatThrownBy(
                                () ->
                                        client.check(
                                                new SubjectDeletionAcademicData.Target(
                                                        20, 30L, 10, "", context.getClaims()),
                                                UUID.fromString(command().getOperationId()),
                                                "a".repeat(64),
                                                PROOF))
                        .isInstanceOf(ResponseStatusException.class);
                assertThat(channel.released).isFalse();
                assertThat(channel.bearer).isNull();
            }
            assertThat(channel.deadlineBounded).isTrue();
        }
    }

    @Test
    void
            storedReceiptRecoveryHasNoRequestScopeAndCannotAcceptDifferentActorScopeGenerationOrExpiry() {
        var responses =
                List.of(
                        authReceipt(),
                        authReceipt().toBuilder()
                                .setReceiptId(UUID.randomUUID().toString())
                                .build(),
                        authReceipt().toBuilder()
                                .setActor(authReceipt().getActor().toBuilder().setUserId(99))
                                .build(),
                        authReceipt().toBuilder()
                                .setActor(
                                        authReceipt().getActor().toBuilder()
                                                .setSessionId(UUID.randomUUID().toString()))
                                .build(),
                        authReceipt().toBuilder()
                                .setBinding(authReceipt().getBinding().toBuilder().setGroupId(99))
                                .build(),
                        authReceipt().toBuilder().setCredentialsVersion(0).build(),
                        authReceipt().toBuilder()
                                .setActor(authReceipt().getActor().toBuilder().setSessionVersion(0))
                                .build(),
                        authReceipt().toBuilder().setExpiresAt(CLOCK.instant().toString()).build());
        for (var response : responses) {
            var context = mock(RequestContext.class);
            var channel =
                    new AuthChannel(
                            "auth-service",
                            SecurityLevel.PRIVACY_AND_INTEGRITY,
                            ignored -> response);
            var client = authClient(context, channel);
            if (response.equals(authReceipt())) {
                assertThat(client.checkStored(operation(Phase.PREPARING))).isNotNull();
            } else {
                assertThatThrownBy(() -> client.checkStored(operation(Phase.PREPARING)))
                        .isInstanceOfSatisfying(
                                ResponseStatusException.class,
                                denied ->
                                        assertThat(denied.getStatusCode().value()).isEqualTo(503));
            }
            assertThat(channel.bearer).isNull();
            assertThat(channel.methods).containsExactly("CheckStoredSubjectDeletionReceipt");
            verifyNoInteractions(context);
        }
    }

    @Test
    void unknownAuthOutcomeLeavesDurablePrecommitPendingWithoutReleaseOrDestructiveRpc() {
        for (var status : List.of(Status.DEADLINE_EXCEEDED, Status.UNAVAILABLE)) {
            var context = mock(RequestContext.class);
            var auth =
                    authClient(
                            context,
                            new AuthChannel(
                                    "auth-service",
                                    SecurityLevel.PRIVACY_AND_INTEGRITY,
                                    ignored -> {
                                        throw status.asRuntimeException();
                                    }));
            var commands = mock(SubjectDeletionCommands.class);
            var participants = mock(SubjectDeletionParticipants.class);
            var operation = operation(Phase.PREPARING);
            when(commands.get(operation.id())).thenReturn(operation);
            new SubjectDeletionCoordinator(commands, participants, CLOCK, auth)
                    .advance(operation.id());
            verify(commands).failure(operation.id());
            verify(commands, never()).beginCommit(any(), any());
            verify(commands, never()).release(any(), anyString());
            verifyNoInteractions(participants, context);
        }
    }

    @Test
    void acceptedCommittingIntentResumesWithoutNewAuthOrProofConsumption() {
        var commands = mock(SubjectDeletionCommands.class);
        var participants = mock(SubjectDeletionParticipants.class);
        var auth = mock(AuthSubjectDeletionClient.class);
        var operation = operation(Phase.COMMITTING);
        when(commands.get(operation.id())).thenReturn(operation);
        new SubjectDeletionCoordinator(commands, participants, CLOCK, auth).advance(operation.id());
        verify(commands).finish(operation.id());
        verify(participants)
                .schedule(argThat(command -> command.getAction() == SubjectDeletionAction.COMMIT));
        verify(participants)
                .attendance(
                        argThat(command -> command.getAction() == SubjectDeletionAction.COMMIT));
        verifyNoInteractions(auth);
        verify(commands, never()).release(any(), anyString());
    }

    @Test
    void authReceiptCannotStartNetworkWorkInsideLocalTransaction() {
        var context = mock(RequestContext.class);
        var channel =
                new AuthChannel(
                        "auth-service",
                        SecurityLevel.PRIVACY_AND_INTEGRITY,
                        ignored -> authReceipt());
        var auth = authClient(context, channel);
        org.springframework.transaction.support.TransactionSynchronizationManager
                .setActualTransactionActive(true);
        try {
            assertThatThrownBy(() -> auth.checkStored(operation(Phase.PREPARING)))
                    .isInstanceOfSatisfying(
                            ResponseStatusException.class,
                            rejected ->
                                    assertThat(rejected.getStatusCode().value()).isEqualTo(503));
            assertThat(channel.released).isFalse();
            assertThat(channel.deadlineBounded).isFalse();
            verifyNoInteractions(context);
        } finally {
            org.springframework.transaction.support.TransactionSynchronizationManager
                    .setActualTransactionActive(false);
        }
    }

    private AuthSubjectDeletionClient authClient(RequestContext context, Channel channel) {
        var client = new AuthSubjectDeletionClient(context, TOKEN, "auth-service", CLOCK);
        var legacy = new ru.rutcampustrack.academic.grpc.GrpcSecretClientInterceptor();
        ReflectionTestUtils.setField(legacy, "secret", "synthetic-configured-legacy-secret");
        ReflectionTestUtils.setField(
                client,
                "stub",
                AuthSecurityCapabilityServiceGrpc.newBlockingStub(
                        ClientInterceptors.intercept(channel, legacy)));
        return client;
    }

    private SubjectDeletionOperation operation(Phase phase) {
        return new SubjectDeletionOperation(
                UUID.fromString(command().getOperationId()),
                UUID.randomUUID(),
                20,
                30,
                10,
                42,
                SID,
                "a".repeat(64),
                "confirmation",
                "academic",
                "b".repeat(64),
                "c".repeat(64),
                command().toByteArray(),
                phase,
                "SEALED",
                "SEALED",
                CLOCK.instant(),
                null,
                PROOF);
    }

    private Object authResponse(Object request) {
        if (request instanceof ru.rutcampustrack.authsecurity.grpc.SubjectDeletionChallengeRequest)
            return ru.rutcampustrack.authsecurity.grpc.SubjectDeletionChallenge.newBuilder()
                    .setChallengeId("S".repeat(32))
                    .setAttemptsRemaining(3)
                    .setExpiresAt(CLOCK.instant().plusSeconds(120).toString())
                    .build();
        if (request instanceof ru.rutcampustrack.authsecurity.grpc.SubjectDeletionVerifyRequest)
            return ru.rutcampustrack.authsecurity.grpc.SubjectDeletionVerification.newBuilder()
                    .setAccepted(true)
                    .setReceiptId(PROOF.toString())
                    .build();
        return authReceipt();
    }

    private ru.rutcampustrack.authsecurity.grpc.SubjectDeletionReceipt authReceipt() {
        return ru.rutcampustrack.authsecurity.grpc.SubjectDeletionReceipt.newBuilder()
                .setReceiptId(PROOF.toString())
                .setBinding(
                        SubjectDeletionBinding.newBuilder()
                                .setOperationId(command().getOperationId())
                                .setSubjectId(20)
                                .setSemesterId(30)
                                .setGroupId(10)
                                .setPreviewDigest("a".repeat(64)))
                .setActor(
                        StoredSessionIdentity.newBuilder()
                                .setUserId(42)
                                .setSessionId(SID.toString())
                                .setSessionVersion(1)
                                .setRolesVersion(2)
                                .setRole("HEADMAN")
                                .setStatus("ACTIVE")
                                .setGroupId(10)
                                .setIsHeadman(true))
                .setCredentialsVersion(1)
                .setTelegramBindingVersion(1)
                .setTelegramId(500)
                .setExpiresAt(CLOCK.instant().plusSeconds(120).toString())
                .build();
    }

    private static final class AuthChannel extends Channel {
        final String authority;
        final SecurityLevel security;
        final Function<Object, Object> response;
        boolean released, deadlineBounded, wrongMethod;
        final Set<String> methods = new LinkedHashSet<>();
        String bearer;

        AuthChannel(String authority, SecurityLevel security, Function<Object, Object> response) {
            this.authority = authority;
            this.security = security;
            this.response = response;
        }

        @Override
        public String authority() {
            return authority;
        }

        @Override
        public <Q, S> ClientCall<Q, S> newCall(MethodDescriptor<Q, S> method, CallOptions options) {
            methods.add(method.getBareMethodName());
            deadlineBounded =
                    options.getDeadline() != null
                            && options.getDeadline()
                                            .timeRemaining(java.util.concurrent.TimeUnit.SECONDS)
                                    <= 3;
            return new ClientCall<>() {
                Listener<S> listener;
                Q request;
                Metadata prepared;

                public void start(Listener<S> value, Metadata headers) {
                    listener = value;
                    prepared = new Metadata();
                    prepared.merge(headers);
                    assertThat(
                                    headers.get(
                                            Metadata.Key.of(
                                                    "x-grpc-secret",
                                                    Metadata.ASCII_STRING_MARSHALLER)))
                            .isNull();
                    assertThat(headers.get(DirectedServiceCredential.TOKEN_METADATA_KEY)).isNull();
                }

                public void request(int amount) {}

                public void cancel(String message, Throwable cause) {}

                public void sendMessage(Q value) {
                    request = value;
                }

                public void halfClose() {
                    options.getCredentials()
                            .applyRequestMetadata(
                                    new CallCredentials.RequestInfo() {
                                        public MethodDescriptor<?, ?> getMethodDescriptor() {
                                            return wrongMethod
                                                    ? method.toBuilder()
                                                            .setFullMethodName(
                                                                    "rutcampustrack.authsecurity.AuthSecurityCapabilityService/ForeignMethod")
                                                            .build()
                                                    : method;
                                        }

                                        public SecurityLevel getSecurityLevel() {
                                            return security;
                                        }

                                        public String getAuthority() {
                                            return authority;
                                        }

                                        public Attributes getTransportAttrs() {
                                            return Attributes.EMPTY;
                                        }
                                    },
                                    Runnable::run,
                                    new CallCredentials.MetadataApplier() {
                                        public void fail(Status status) {
                                            listener.onClose(status, new Metadata());
                                        }

                                        @SuppressWarnings("unchecked")
                                        public void apply(Metadata headers) {
                                            // gRPC creates the transport stream only after
                                            // CallCredentials
                                            // succeed. Prepared interceptor metadata alone is not
                                            // wire emission.
                                            var wire = new Metadata();
                                            wire.merge(prepared);
                                            wire.merge(headers);
                                            released = true;
                                            assertThat(
                                                            wire.get(
                                                                    DirectedServiceCredential
                                                                            .TOKEN_METADATA_KEY))
                                                    .isEqualTo(TOKEN);
                                            assertThat(
                                                            wire.get(
                                                                    Metadata.Key.of(
                                                                            "x-grpc-secret",
                                                                            Metadata
                                                                                    .ASCII_STRING_MARSHALLER)))
                                                    .isNull();
                                            bearer =
                                                    wire.get(
                                                            Metadata.Key.of(
                                                                    "x-internal-token",
                                                                    Metadata
                                                                            .ASCII_STRING_MARSHALLER));
                                            try {
                                                listener.onMessage((S) response.apply(request));
                                                listener.onClose(Status.OK, new Metadata());
                                            } catch (StatusRuntimeException failed) {
                                                listener.onClose(
                                                        failed.getStatus(), new Metadata());
                                            }
                                        }
                                    });
                }
            };
        }
    }

    private SubjectDeletionParticipants client(Channel channel, boolean schedule) {
        var client =
                new SubjectDeletionParticipants(
                        TOKEN, TOKEN, "schedule-service", "attendance-service");
        ReflectionTestUtils.setField(
                client,
                schedule ? "schedule" : "attendance",
                SubjectDeletionParticipantGrpc.newBlockingStub(channel));
        return client;
    }

    private SubjectDeletionCommand command() {
        return SubjectDeletionCommand.newBuilder()
                .setOperationId("00000000-0000-4000-8000-000000000001")
                .setSubjectId(20)
                .setSemesterId(30)
                .setGroupId(10)
                .setActorId(42)
                .setAction(SubjectDeletionAction.PREPARE)
                .setManifestDigest("a".repeat(64))
                .setParticipantDigest("b".repeat(64))
                .build();
    }

    private static class CapturingChannel extends Channel {
        final String authority;
        final SecurityLevel security;
        final boolean mismatch;
        boolean released, deadlineBounded;

        CapturingChannel(String authority, SecurityLevel security, boolean mismatch) {
            this.authority = authority;
            this.security = security;
            this.mismatch = mismatch;
        }

        @Override
        public String authority() {
            return authority;
        }

        @Override
        public <Q, S> ClientCall<Q, S> newCall(MethodDescriptor<Q, S> method, CallOptions options) {
            deadlineBounded =
                    options.getDeadline() != null
                            && options.getDeadline()
                                            .timeRemaining(java.util.concurrent.TimeUnit.SECONDS)
                                    <= 3;
            return new ClientCall<>() {
                Listener<S> listener;
                Q request;

                public void start(Listener<S> listener, Metadata headers) {
                    this.listener = listener;
                    assertThat(headers.containsKey(DirectedServiceCredential.TOKEN_METADATA_KEY))
                            .isFalse();
                }

                public void request(int amount) {}

                public void cancel(String message, Throwable cause) {}

                public void sendMessage(Q request) {
                    this.request = request;
                }

                public void halfClose() {
                    assertThat(options.getCredentials()).isNotNull();
                    options.getCredentials()
                            .applyRequestMetadata(
                                    new CallCredentials.RequestInfo() {
                                        public MethodDescriptor<?, ?> getMethodDescriptor() {
                                            return method;
                                        }

                                        public SecurityLevel getSecurityLevel() {
                                            return security;
                                        }

                                        public String getAuthority() {
                                            return authority;
                                        }

                                        public Attributes getTransportAttrs() {
                                            return Attributes.EMPTY;
                                        }
                                    },
                                    Runnable::run,
                                    new CallCredentials.MetadataApplier() {
                                        public void fail(Status status) {
                                            listener.onClose(status, new Metadata());
                                        }

                                        @SuppressWarnings("unchecked")
                                        public void apply(Metadata headers) {
                                            released = true;
                                            assertThat(
                                                            TOKEN.equals(
                                                                    headers.get(
                                                                            DirectedServiceCredential
                                                                                    .TOKEN_METADATA_KEY)))
                                                    .isTrue();
                                            var c = (SubjectDeletionCommand) request;
                                            var r =
                                                    SubjectDeletionReceipt.newBuilder()
                                                            .setOperationId(c.getOperationId())
                                                            .setSubjectId(
                                                                    mismatch
                                                                            ? 999
                                                                            : c.getSubjectId())
                                                            .setSemesterId(c.getSemesterId())
                                                            .setGroupId(c.getGroupId())
                                                            .setActorId(c.getActorId())
                                                            .setAction(c.getAction())
                                                            .setParticipantDigest(
                                                                    c.getParticipantDigest())
                                                            .setManifestDigest(
                                                                    c.getManifestDigest())
                                                            .build();
                                            listener.onMessage((S) r);
                                            listener.onClose(Status.OK, new Metadata());
                                        }
                                    });
                }
            };
        }
    }
}
