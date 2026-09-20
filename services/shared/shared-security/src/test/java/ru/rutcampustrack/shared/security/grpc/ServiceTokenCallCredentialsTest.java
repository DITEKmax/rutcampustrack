package ru.rutcampustrack.shared.security.grpc;

import io.grpc.Attributes;
import io.grpc.CallOptions;
import io.grpc.CallCredentials;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.SecurityLevel;
import io.grpc.Status;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class ServiceTokenCallCredentialsTest {

    private static final String METHOD = ServiceIdentityServerInterceptor.SCHEDULE_INSTALL_ASSIGNMENT_CLOSE_CAP;
    private static final String OTHER_METHOD = "rutcampustrack.schedule.ScheduleGrpcService/ResolveLesson";
    private static final String AUTHORITY = "schedule.local";
    private static final String TOKEN = token((byte) 0x44);

    private final DirectedServiceCredential credential = new DirectedServiceCredential(
            ServicePrincipal.ACADEMIC_SERVICE, ServicePrincipal.SCHEDULE_SERVICE, TOKEN);
    private final ServiceTokenCallCredentials callCredentials =
            ServiceTokenCallCredentials.forMethod(credential, AUTHORITY, METHOD);

    @Test
    void emitsOnlyForExactMethodAuthorityAndConfidentialTransport() {
        AtomicReference<Metadata> applied = new AtomicReference<>();
        AtomicReference<Status> failed = new AtomicReference<>();
        callCredentials.applyRequestMetadata(
                requestInfo(METHOD, AUTHORITY, SecurityLevel.PRIVACY_AND_INTEGRITY),
                Runnable::run,
                applier(applied, failed));

        assertThat(failed).hasValue(null);
        assertThat(applied).isNotNull();
        assertThat(applied.get().get(DirectedServiceCredential.TOKEN_METADATA_KEY)).isEqualTo(TOKEN);
    }

    @Test
    void wrongMethodAndAuthorityFailBeforeTokenEmission() {
        assertScopeDenied(requestInfo(OTHER_METHOD, AUTHORITY, SecurityLevel.PRIVACY_AND_INTEGRITY));
        assertScopeDenied(requestInfo(METHOD, "other.local", SecurityLevel.PRIVACY_AND_INTEGRITY));
    }

    @Test
    void plaintextTransportFailsClosedBeforeTokenEmission() {
        AtomicReference<Metadata> applied = new AtomicReference<>();
        AtomicReference<Status> failed = new AtomicReference<>();
        callCredentials.applyRequestMetadata(
                requestInfo(METHOD, AUTHORITY, SecurityLevel.NONE),
                Runnable::run,
                applier(applied, failed));

        assertThat(applied).hasValue(null);
        assertThat(failed).isNotNull();
        assertThat(failed.get().getCode()).isEqualTo(Status.Code.UNAUTHENTICATED);
    }

    @Test
    void clientAdapterRemovesSuppliedTokenAndLeavesUnrelatedMethodUncredentialed() {
        AtomicReference<CallOptions> options = new AtomicReference<>();
        RecordingClientCall delegate = new RecordingClientCall();
        Channel channel = new Channel() {
            @Override
            public <ReqT, RespT> ClientCall<ReqT, RespT> newCall(
                    MethodDescriptor<ReqT, RespT> method, CallOptions callOptions) {
                options.set(callOptions);
                @SuppressWarnings("unchecked")
                ClientCall<ReqT, RespT> typed = (ClientCall<ReqT, RespT>) delegate;
                return typed;
            }

            @Override
            public String authority() {
                return AUTHORITY;
            }
        };
        ServiceTokenClientInterceptor adapter = new ServiceTokenClientInterceptor(callCredentials);

        ClientCall<String, String> protectedCall = adapter.interceptCall(
                descriptor(METHOD), CallOptions.DEFAULT, channel);
        Metadata supplied = new Metadata();
        supplied.put(DirectedServiceCredential.TOKEN_METADATA_KEY, "caller-supplied");
        protectedCall.start(new ClientCall.Listener<>() { }, supplied);

        assertThat(supplied.get(DirectedServiceCredential.TOKEN_METADATA_KEY)).isNull();
        assertThat(options.get().getCredentials()).isSameAs(callCredentials);

        adapter.interceptCall(descriptor(OTHER_METHOD), CallOptions.DEFAULT, channel);
        assertThat(options.get().getCredentials()).isNull();
    }

    @Test
    void malformedConfigurationIsRejectedWithoutTokenFallback() {
        assertThat(DirectedServiceCredential.tryCreate(
                ServicePrincipal.ACADEMIC_SERVICE, ServicePrincipal.SCHEDULE_SERVICE, "short"))
                .isEmpty();
    }

    private void assertScopeDenied(CallCredentials.RequestInfo requestInfo) {
        AtomicReference<Metadata> applied = new AtomicReference<>();
        AtomicReference<Status> failed = new AtomicReference<>();
        callCredentials.applyRequestMetadata(requestInfo, Runnable::run, applier(applied, failed));
        assertThat(applied).hasValue(null);
        assertThat(failed).isNotNull();
        assertThat(failed.get().getCode()).isEqualTo(Status.Code.PERMISSION_DENIED);
    }

    private static CallCredentials.MetadataApplier applier(
            AtomicReference<Metadata> applied,
            AtomicReference<Status> failed) {
        return new CallCredentials.MetadataApplier() {
            @Override
            public void apply(Metadata metadata) {
                applied.set(metadata);
            }

            @Override
            public void fail(Status status) {
                failed.set(status);
            }
        };
    }

    private static CallCredentials.RequestInfo requestInfo(
            String method,
            String authority,
            SecurityLevel securityLevel) {
        return new CallCredentials.RequestInfo() {
            @Override
            public MethodDescriptor<?, ?> getMethodDescriptor() {
                return descriptor(method);
            }

            @Override
            public SecurityLevel getSecurityLevel() {
                return securityLevel;
            }

            @Override
            public String getAuthority() {
                return authority;
            }

            @Override
            public Attributes getTransportAttrs() {
                return Attributes.EMPTY;
            }
        };
    }

    private static MethodDescriptor<String, String> descriptor(String method) {
        return MethodDescriptor.create(
                MethodDescriptor.MethodType.UNARY, method, STRING_MARSHALLER, STRING_MARSHALLER);
    }

    private static String token(byte fill) {
        byte[] bytes = new byte[DirectedServiceCredential.TOKEN_BYTES];
        java.util.Arrays.fill(bytes, fill);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static final class RecordingClientCall extends ClientCall<String, String> {
        @Override
        public void start(Listener<String> responseListener, Metadata headers) { }

        @Override
        public void request(int numMessages) { }

        @Override
        public void cancel(String message, Throwable cause) { }

        @Override
        public void halfClose() { }

        @Override
        public void sendMessage(String message) { }
    }

    private static final MethodDescriptor.Marshaller<String> STRING_MARSHALLER =
            new MethodDescriptor.Marshaller<>() {
                @Override
                public InputStream stream(String value) {
                    return new ByteArrayInputStream(value.getBytes(StandardCharsets.UTF_8));
                }

                @Override
                public String parse(InputStream stream) {
                    try {
                        return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
                    } catch (java.io.IOException error) {
                        throw new IllegalStateException(error);
                    }
                }
            };
}
