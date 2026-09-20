package ru.rutcampustrack.shared.security.grpc;

import io.grpc.Attributes;
import io.grpc.Context;
import io.grpc.Grpc;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerCall.Listener;
import io.grpc.ServerInterceptor;
import io.grpc.Status;
import org.junit.jupiter.api.Test;

import javax.net.ssl.SSLSession;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class ServiceIdentityServerInterceptorTest {

    private static final String PROTECTED_METHOD =
            ServiceIdentityServerInterceptor.SCHEDULE_INSTALL_ASSIGNMENT_CLOSE_CAP;
    private static final String UNRELATED_METHOD =
            "rutcampustrack.schedule.ScheduleGrpcService/ResolveLesson";
    private static final String ACADEMIC_TOKEN = token((byte) 0x11);
    private static final String OTHER_TOKEN = token((byte) 0x22);
    private static final String REVERSE_TOKEN = token((byte) 0x33);

    private final DirectedServiceCredential academicCredential = new DirectedServiceCredential(
            ServicePrincipal.ACADEMIC_SERVICE, ServicePrincipal.SCHEDULE_SERVICE, ACADEMIC_TOKEN);
    private final DirectedServiceCredential otherCredential = new DirectedServiceCredential(
            new ServicePrincipal("other-service"), ServicePrincipal.SCHEDULE_SERVICE, OTHER_TOKEN);
    private final DirectedServiceCredential reverseCredential = new DirectedServiceCredential(
            ServicePrincipal.SCHEDULE_SERVICE, ServicePrincipal.ACADEMIC_SERVICE, REVERSE_TOKEN);

    @Test
    void validCredentialBindsImmutablePrincipalBeforeHandler() {
        ServiceIdentityServerInterceptor interceptor = interceptor(
                Map.of(PROTECTED_METHOD, ServicePrincipal.ACADEMIC_SERVICE),
                List.of(academicCredential));
        AtomicReference<ServicePrincipal> seen = new AtomicReference<>();
        AtomicBoolean called = new AtomicBoolean();
        ServerCall<Object, Object> call = call(PROTECTED_METHOD, tlsAttributes());

        interceptor.interceptCall(call, headers(ACADEMIC_TOKEN), handler(seen, called));

        assertThat(called.get()).isTrue();
        assertThat(seen).hasValue(ServicePrincipal.ACADEMIC_SERVICE);
    }

    @Test
    void missingTokenIsUnauthenticatedAndDoesNotReachHandler() {
        assertRejected(new ServiceIdentityServerInterceptor(
                        ServicePrincipal.SCHEDULE_SERVICE,
                        Map.of(PROTECTED_METHOD, ServicePrincipal.ACADEMIC_SERVICE),
                        List.of(academicCredential)),
                call(PROTECTED_METHOD, tlsAttributes()), new Metadata(), Status.Code.UNAUTHENTICATED);
    }

    @Test
    void duplicateMalformedAndUserJwtOnlyTokensAreUnauthenticated() {
        ServiceIdentityServerInterceptor interceptor = interceptor(
                Map.of(PROTECTED_METHOD, ServicePrincipal.ACADEMIC_SERVICE),
                List.of(academicCredential));

        Metadata duplicate = new Metadata();
        duplicate.put(DirectedServiceCredential.TOKEN_METADATA_KEY, ACADEMIC_TOKEN);
        duplicate.put(DirectedServiceCredential.TOKEN_METADATA_KEY, ACADEMIC_TOKEN);
        assertRejected(interceptor, call(PROTECTED_METHOD, tlsAttributes()), duplicate,
                Status.Code.UNAUTHENTICATED);

        Metadata malformed = new Metadata();
        malformed.put(DirectedServiceCredential.TOKEN_METADATA_KEY, "malformed");
        assertRejected(interceptor, call(PROTECTED_METHOD, tlsAttributes()), malformed,
                Status.Code.UNAUTHENTICATED);

        Metadata userJwtOnly = new Metadata();
        userJwtOnly.put(Metadata.Key.of("x-internal-token", Metadata.ASCII_STRING_MARSHALLER), ACADEMIC_TOKEN);
        assertRejected(interceptor, call(PROTECTED_METHOD, tlsAttributes()), userJwtOnly,
                Status.Code.UNAUTHENTICATED);
    }

    @Test
    void reverseAudienceCredentialIsUnauthenticated() {
        ServiceIdentityServerInterceptor interceptor = interceptor(
                Map.of(PROTECTED_METHOD, ServicePrincipal.ACADEMIC_SERVICE),
                List.of(academicCredential, reverseCredential));
        assertRejected(interceptor, call(PROTECTED_METHOD, tlsAttributes()), headers(REVERSE_TOKEN),
                Status.Code.UNAUTHENTICATED);
    }

    @Test
    void authenticatedCallerForbiddenByExactMethodIsPermissionDenied() {
        ServiceIdentityServerInterceptor interceptor = interceptor(
                Map.of(PROTECTED_METHOD, ServicePrincipal.ACADEMIC_SERVICE),
                List.of(otherCredential));
        assertRejected(interceptor, call(PROTECTED_METHOD, tlsAttributes()), headers(OTHER_TOKEN),
                Status.Code.PERMISSION_DENIED);
    }

    @Test
    void plaintextTransportIsUnauthenticatedBeforeCredentialValidation() {
        ServiceIdentityServerInterceptor interceptor = interceptor(
                Map.of(PROTECTED_METHOD, ServicePrincipal.ACADEMIC_SERVICE),
                List.of(academicCredential));
        assertRejected(interceptor, call(PROTECTED_METHOD, Attributes.EMPTY), headers(ACADEMIC_TOKEN),
                Status.Code.UNAUTHENTICATED);
    }

    @Test
    void unrelatedMethodKeepsExistingAdmissionContract() {
        ServiceIdentityServerInterceptor interceptor = interceptor(
                Map.of(PROTECTED_METHOD, ServicePrincipal.ACADEMIC_SERVICE),
                List.of(academicCredential));
        AtomicBoolean called = new AtomicBoolean();
        interceptor.interceptCall(call(UNRELATED_METHOD, Attributes.EMPTY), new Metadata(),
                (nextCall, metadata) -> {
                    called.set(true);
                    return new Listener<>() { };
                });
        assertThat(called.get()).isTrue();
    }

    @Test
    void verifiedCallComposesWithAnExistingLegacyInterceptor() {
        ServiceIdentityServerInterceptor identity = interceptor(
                Map.of(PROTECTED_METHOD, ServicePrincipal.ACADEMIC_SERVICE),
                List.of(academicCredential));
        AtomicBoolean legacyCalled = new AtomicBoolean();
        AtomicBoolean handlerCalled = new AtomicBoolean();
        ServerInterceptor legacy = new ServerInterceptor() {
            @Override
            public <ReqT, RespT> Listener<ReqT> interceptCall(
                    ServerCall<ReqT, RespT> call,
                    Metadata headers,
                    ServerCallHandler<ReqT, RespT> next) {
                legacyCalled.set(true);
                return next.startCall(call, headers);
            }
        };
        ServerCallHandler<Object, Object> handler = (call, headers) -> {
            handlerCalled.set(true);
            return new Listener<>() { };
        };

        identity.interceptCall(call(PROTECTED_METHOD, tlsAttributes()), headers(ACADEMIC_TOKEN),
                (nextCall, metadata) -> legacy.interceptCall(nextCall, metadata, handler));

        assertThat(legacyCalled.get()).isTrue();
        assertThat(handlerCalled.get()).isTrue();
    }

    @Test
    void concurrentCallsKeepTwoVerifiedPrincipalsIsolated() throws Exception {
        String otherMethod = "rutcampustrack.schedule.ScheduleGrpcService/TestOtherProtectedMethod";
        ServiceIdentityServerInterceptor identity = new ServiceIdentityServerInterceptor(
                ServicePrincipal.SCHEDULE_SERVICE,
                Map.of(
                        PROTECTED_METHOD, ServicePrincipal.ACADEMIC_SERVICE,
                        otherMethod, otherCredential.source()),
                List.of(academicCredential, otherCredential));
        CountDownLatch entered = new CountDownLatch(2);
        CountDownLatch release = new CountDownLatch(1);
        AtomicReference<ServicePrincipal> firstSeen = new AtomicReference<>();
        AtomicReference<ServicePrincipal> secondSeen = new AtomicReference<>();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> first = executor.submit(() -> invokeConcurrent(
                    identity, PROTECTED_METHOD, ACADEMIC_TOKEN, firstSeen, entered, release));
            Future<?> second = executor.submit(() -> invokeConcurrent(
                    identity, otherMethod, OTHER_TOKEN, secondSeen, entered, release));

            assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
            release.countDown();
            first.get(5, TimeUnit.SECONDS);
            second.get(5, TimeUnit.SECONDS);
            assertThat(firstSeen).hasValue(ServicePrincipal.ACADEMIC_SERVICE);
            assertThat(secondSeen).hasValue(otherCredential.source());
        } finally {
            release.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void parentContextIsRestoredAfterVerifiedCall() {
        ServiceIdentityServerInterceptor identity = interceptor(
                Map.of(PROTECTED_METHOD, ServicePrincipal.ACADEMIC_SERVICE),
                List.of(academicCredential));
        ServicePrincipal outer = new ServicePrincipal("outer-service");
        AtomicReference<ServicePrincipal> seen = new AtomicReference<>();
        AtomicReference<ServicePrincipal> restored = new AtomicReference<>();

        Context.current().withValue(ServicePrincipal.CONTEXT_KEY, outer).run(() -> {
            identity.interceptCall(call(PROTECTED_METHOD, tlsAttributes()), headers(ACADEMIC_TOKEN),
                    (nextCall, metadata) -> {
                        seen.set(ServicePrincipal.CONTEXT_KEY.get());
                        return new Listener<>() { };
                    });
            restored.set(ServicePrincipal.CONTEXT_KEY.get());
        });

        assertThat(seen).hasValue(ServicePrincipal.ACADEMIC_SERVICE);
        assertThat(restored).hasValue(outer);
    }

    private static void invokeConcurrent(
            ServiceIdentityServerInterceptor interceptor,
            String method,
            String token,
            AtomicReference<ServicePrincipal> seen,
            CountDownLatch entered,
            CountDownLatch release) {
        interceptor.interceptCall(call(method, tlsAttributes()), headers(token), (nextCall, metadata) -> {
            entered.countDown();
            try {
                if (!release.await(5, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("concurrent service identity test timed out");
                }
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(error);
            }
            seen.set(ServicePrincipal.CONTEXT_KEY.get());
            return new Listener<>() { };
        });
    }

    @Test
    void tokenNeverAppearsInCredentialStringOrStatusDescription() {
        ServiceIdentityServerInterceptor interceptor = interceptor(
                Map.of(PROTECTED_METHOD, ServicePrincipal.ACADEMIC_SERVICE),
                List.of(academicCredential));
        ServerCall<Object, Object> call = call(PROTECTED_METHOD, tlsAttributes());
        assertRejected(interceptor, call, headers("bad"), Status.Code.UNAUTHENTICATED);

        assertThat(academicCredential.toString()).doesNotContain(ACADEMIC_TOKEN);
        Status status = captureClosedStatus(call);
        assertThat(status.getDescription()).doesNotContain(ACADEMIC_TOKEN);
    }

    private static ServiceIdentityServerInterceptor interceptor(
            Map<String, ServicePrincipal> policy,
            List<DirectedServiceCredential> credentials) {
        return new ServiceIdentityServerInterceptor(
                ServicePrincipal.SCHEDULE_SERVICE, policy, credentials);
    }

    private static void assertRejected(
            ServerInterceptor interceptor,
            ServerCall<Object, Object> call,
            Metadata headers,
            Status.Code expectedCode) {
        AtomicBoolean called = new AtomicBoolean();
        interceptor.interceptCall(call, headers, (nextCall, metadata) -> {
            called.set(true);
            return new Listener<>() { };
        });
        assertThat(called.get()).isFalse();
        Status status = captureClosedStatus(call);
        assertThat(status.getCode()).isEqualTo(expectedCode);
    }

    private static Status captureClosedStatus(ServerCall<Object, Object> call) {
        assertThat(call).isInstanceOf(RecordingServerCall.class);
        return ((RecordingServerCall) call).status();
    }

    private static ServerCall<Object, Object> call(String method, Attributes attributes) {
        return new RecordingServerCall(method, attributes);
    }

    private static ServerCallHandler<Object, Object> handler(
            AtomicReference<ServicePrincipal> seen,
            AtomicBoolean called) {
        return (call, headers) -> {
            called.set(true);
            seen.set(ServicePrincipal.CONTEXT_KEY.get());
            return new Listener<>() { };
        };
    }

    private static Metadata headers(String token) {
        Metadata headers = new Metadata();
        headers.put(DirectedServiceCredential.TOKEN_METADATA_KEY, token);
        return headers;
    }

    private static Attributes tlsAttributes() {
        return Attributes.newBuilder()
                .set(Grpc.TRANSPORT_ATTR_SSL_SESSION, mock(SSLSession.class))
                .build();
    }

    private static String token(byte fill) {
        byte[] bytes = new byte[DirectedServiceCredential.TOKEN_BYTES];
        java.util.Arrays.fill(bytes, fill);
        return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static final class RecordingServerCall extends ServerCall<Object, Object> {
        private final MethodDescriptor<Object, Object> descriptor;
        private final Attributes attributes;
        private Status status;

        private RecordingServerCall(String method, Attributes attributes) {
            this.descriptor = MethodDescriptor.create(
                    MethodDescriptor.MethodType.UNARY, method, STRING_MARSHALLER, STRING_MARSHALLER);
            this.attributes = attributes;
        }

        @Override
        public void request(int numMessages) { }

        @Override
        public void sendHeaders(Metadata headers) { }

        @Override
        public void sendMessage(Object message) { }

        @Override
        public void close(Status status, Metadata trailers) {
            this.status = status;
        }

        @Override
        public boolean isCancelled() {
            return false;
        }

        @Override
        public MethodDescriptor<Object, Object> getMethodDescriptor() {
            return descriptor;
        }

        @Override
        public Attributes getAttributes() {
            return attributes;
        }

        private Status status() {
            return status;
        }
    }

    private static final MethodDescriptor.Marshaller<Object> STRING_MARSHALLER =
            new MethodDescriptor.Marshaller<>() {
                @Override
                public InputStream stream(Object value) {
                    return new ByteArrayInputStream(String.valueOf(value).getBytes(StandardCharsets.UTF_8));
                }

                @Override
                public Object parse(InputStream stream) {
                    try {
                        return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
                    } catch (java.io.IOException error) {
                        throw new IllegalStateException(error);
                    }
                }
            };
}
