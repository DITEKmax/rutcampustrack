package ru.rutcampustrack.academic.subjectdeletion;

import static org.assertj.core.api.Assertions.*;

import io.grpc.*;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import ru.rutcampustrack.shared.security.grpc.DirectedServiceCredential;
import ru.rutcampustrack.subjectdeletion.grpc.*;

import java.util.Base64;

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
