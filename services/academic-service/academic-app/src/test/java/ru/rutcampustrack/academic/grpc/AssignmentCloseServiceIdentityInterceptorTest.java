package ru.rutcampustrack.academic.grpc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.grpc.Attributes;
import io.grpc.Context;
import io.grpc.Grpc;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.ServerCall;
import io.grpc.ServerCall.Listener;
import io.grpc.Status;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import javax.net.ssl.SSLSession;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;
import ru.rutcampustrack.shared.security.grpc.DirectedServiceCredential;
import ru.rutcampustrack.shared.security.grpc.ServicePrincipal;

class AssignmentCloseServiceIdentityInterceptorTest {

    private static final String TOKEN = token((byte) 0x66);

    @Test
    void validScheduleCredentialBindsSchedulePrincipal() {
        AssignmentCloseServiceIdentityInterceptor interceptor =
                new AssignmentCloseServiceIdentityInterceptor(TOKEN);
        AtomicReference<ServicePrincipal> seen = new AtomicReference<>();
        AtomicBoolean called = new AtomicBoolean();

        interceptor.interceptCall(
                call(AssignmentCloseServiceIdentityInterceptor.PROTECTED_METHOD, tlsAttributes()),
                headers(TOKEN),
                (nextCall, metadata) -> {
                    called.set(true);
                    seen.set(ServicePrincipal.CONTEXT_KEY.get());
                    return new Listener<>() {};
                });

        assertThat(called.get()).isTrue();
        assertThat(seen).hasValue(ServicePrincipal.SCHEDULE_SERVICE);
    }

    @Test
    void missingConfigurationDeniesOnlyReservedMethod() {
        AssignmentCloseServiceIdentityInterceptor interceptor =
                new AssignmentCloseServiceIdentityInterceptor("");
        ServerCall<Object, Object> call =
                call(AssignmentCloseServiceIdentityInterceptor.PROTECTED_METHOD, tlsAttributes());
        AtomicBoolean called = new AtomicBoolean();

        interceptor.interceptCall(
                call,
                new Metadata(),
                (nextCall, metadata) -> {
                    called.set(true);
                    return new Listener<>() {};
                });

        verify(call).close(any(Status.class), any(Metadata.class));
        assertThat(called.get()).isFalse();
    }

    @Test
    void unrelatedMethodRetainsExistingAdmission() {
        AssignmentCloseServiceIdentityInterceptor interceptor =
                new AssignmentCloseServiceIdentityInterceptor("");
        AtomicBoolean called = new AtomicBoolean();
        interceptor.interceptCall(
                call("rutcampustrack.academic.AcademicGrpcService/GetGroup", Attributes.EMPTY),
                new Metadata(),
                (nextCall, metadata) -> {
                    called.set(true);
                    return new Listener<>() {};
                });
        assertThat(called.get()).isTrue();
    }

    @Test
    void explicitSpringRegistrationWorksWithoutStartingTheApplication() {
        try (AnnotationConfigApplicationContext context =
                new AnnotationConfigApplicationContext()) {
            context.getEnvironment()
                    .getPropertySources()
                    .addFirst(
                            new MapPropertySource(
                                    "test-service-identity",
                                    java.util.Map.of(
                                            "grpc.service-identity.schedule-to-academic-token",
                                            TOKEN,
                                            "grpc.service-identity.attendance-to-academic-token",
                                            token((byte) 0x67))));
            context.register(AssignmentCloseServiceAuthConfiguration.class);
            context.refresh();

            assertThat(context.getBean(AssignmentCloseServiceIdentityInterceptor.class))
                    .isNotNull();
            assertPrincipal(
                    context.getBean(AssignmentCloseServiceIdentityInterceptor.class),
                    AssignmentCloseServiceIdentityInterceptor.SEMESTER_STATE_METHOD,
                    token((byte) 0x67),
                    new ServicePrincipal("attendance-service"));
        }
    }

    @Test
    void concurrentCallsKeepVerifiedContextBoundToEachCall() throws Exception {
        AssignmentCloseServiceIdentityInterceptor interceptor =
                new AssignmentCloseServiceIdentityInterceptor(TOKEN);
        java.util.concurrent.ExecutorService executor =
                java.util.concurrent.Executors.newFixedThreadPool(2);
        java.util.concurrent.CountDownLatch entered = new java.util.concurrent.CountDownLatch(2);
        java.util.concurrent.CountDownLatch release = new java.util.concurrent.CountDownLatch(1);
        AtomicReference<ServicePrincipal> outerAfter = new AtomicReference<>();
        try {
            java.util.concurrent.Future<ServicePrincipal> first =
                    executor.submit(() -> invoke(interceptor, entered, release));
            java.util.concurrent.Future<ServicePrincipal> second =
                    executor.submit(() -> invoke(interceptor, entered, release));

            assertThat(entered.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            release.countDown();
            assertThat(first.get(5, java.util.concurrent.TimeUnit.SECONDS))
                    .isEqualTo(ServicePrincipal.SCHEDULE_SERVICE);
            assertThat(second.get(5, java.util.concurrent.TimeUnit.SECONDS))
                    .isEqualTo(ServicePrincipal.SCHEDULE_SERVICE);
            Context.current()
                    .withValue(ServicePrincipal.CONTEXT_KEY, new ServicePrincipal("outer-service"))
                    .run(
                            () -> {
                                interceptor.interceptCall(
                                        call(
                                                AssignmentCloseServiceIdentityInterceptor
                                                        .PROTECTED_METHOD,
                                                tlsAttributes()),
                                        headers(TOKEN),
                                        (nextCall, metadata) -> new Listener<>() {});
                                outerAfter.set(ServicePrincipal.CONTEXT_KEY.get());
                            });
            assertThat(outerAfter).hasValue(new ServicePrincipal("outer-service"));
        } finally {
            release.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS))
                    .isTrue();
        }
    }

    @Test
    void semesterStateAcceptsSeparateAttendanceAndScheduleProofsWithoutBroadeningPreparedLookup() {
        String attendance = token((byte) 0x67);
        var interceptor = new AssignmentCloseServiceIdentityInterceptor(TOKEN, attendance);
        assertPrincipal(
                interceptor,
                AssignmentCloseServiceIdentityInterceptor.SEMESTER_STATE_METHOD,
                attendance,
                new ServicePrincipal("attendance-service"));
        assertPrincipal(
                interceptor,
                AssignmentCloseServiceIdentityInterceptor.SEMESTER_STATE_METHOD,
                TOKEN,
                ServicePrincipal.SCHEDULE_SERVICE);
        assertPrincipal(
                interceptor,
                AssignmentCloseServiceIdentityInterceptor.PROTECTED_METHOD,
                TOKEN,
                ServicePrincipal.SCHEDULE_SERVICE);
        assertDenied(
                interceptor,
                AssignmentCloseServiceIdentityInterceptor.PROTECTED_METHOD,
                tlsAttributes(),
                headers(attendance));
    }

    @Test
    void semesterStateRejectsMissingMalformedForeignDuplicateAndNonTlsCredentials() {
        String attendance = token((byte) 0x67);
        var interceptor = new AssignmentCloseServiceIdentityInterceptor(TOKEN, attendance);
        String method = AssignmentCloseServiceIdentityInterceptor.SEMESTER_STATE_METHOD;
        for (Metadata metadata :
                java.util.List.of(
                        new Metadata(), headers("malformed"), headers(token((byte) 0x68)))) {
            assertDenied(interceptor, method, tlsAttributes(), metadata);
        }
        Metadata duplicated = headers(attendance);
        duplicated.put(DirectedServiceCredential.TOKEN_METADATA_KEY, attendance);
        assertDenied(interceptor, method, tlsAttributes(), duplicated);
        Metadata mixed = headers(attendance);
        mixed.put(DirectedServiceCredential.TOKEN_METADATA_KEY, TOKEN);
        assertDenied(interceptor, method, tlsAttributes(), mixed);
        assertDenied(interceptor, method, Attributes.EMPTY, headers(attendance));
        assertDenied(interceptor, method, Attributes.EMPTY, headers(TOKEN));
        assertDenied(
                new AssignmentCloseServiceIdentityInterceptor(TOKEN),
                method,
                tlsAttributes(),
                headers(attendance));
        assertDenied(
                new AssignmentCloseServiceIdentityInterceptor(TOKEN, "malformed"),
                method,
                tlsAttributes(),
                headers(attendance));
        assertDenied(
                new AssignmentCloseServiceIdentityInterceptor("", attendance),
                method,
                tlsAttributes(),
                headers(TOKEN));
    }

    @Test
    void identicalValidDirectionTokensFailClosedWithoutLeakingConfiguration() {
        assertThatThrownBy(() -> new AssignmentCloseServiceIdentityInterceptor(TOKEN, TOKEN))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Directed service credentials must be distinct");
    }

    private static void assertPrincipal(
            AssignmentCloseServiceIdentityInterceptor interceptor,
            String method,
            String token,
            ServicePrincipal expected) {
        AtomicReference<ServicePrincipal> seen = new AtomicReference<>();
        interceptor.interceptCall(
                call(method, tlsAttributes()),
                headers(token),
                (nextCall, metadata) -> {
                    seen.set(ServicePrincipal.CONTEXT_KEY.get());
                    return new Listener<>() {};
                });
        assertThat(seen.get()).isEqualTo(expected);
        assertThat(ServicePrincipal.CONTEXT_KEY.get()).isNull();
    }

    private static void assertDenied(
            AssignmentCloseServiceIdentityInterceptor interceptor,
            String method,
            Attributes attributes,
            Metadata headers) {
        ServerCall<Object, Object> call = call(method, attributes);
        AtomicReference<Status.Code> denied = new AtomicReference<>();
        doAnswer(
                        invocation -> {
                            denied.set(((Status) invocation.getArgument(0)).getCode());
                            return null;
                        })
                .when(call)
                .close(any(Status.class), any(Metadata.class));
        AtomicBoolean reached = new AtomicBoolean();
        interceptor.interceptCall(
                call,
                headers,
                (nextCall, metadata) -> {
                    reached.set(true);
                    return new Listener<>() {};
                });
        assertThat(reached.get()).isFalse();
        assertThat(denied.get()).isEqualTo(Status.Code.UNAUTHENTICATED);
        assertThat(ServicePrincipal.CONTEXT_KEY.get()).isNull();
    }

    private static ServicePrincipal invoke(
            AssignmentCloseServiceIdentityInterceptor interceptor,
            java.util.concurrent.CountDownLatch entered,
            java.util.concurrent.CountDownLatch release) {
        AtomicReference<ServicePrincipal> seen = new AtomicReference<>();
        Context.current()
                .withValue(ServicePrincipal.CONTEXT_KEY, new ServicePrincipal("outer-service"))
                .run(
                        () ->
                                interceptor.interceptCall(
                                        call(
                                                AssignmentCloseServiceIdentityInterceptor
                                                        .PROTECTED_METHOD,
                                                tlsAttributes()),
                                        headers(TOKEN),
                                        (nextCall, metadata) -> {
                                            entered.countDown();
                                            try {
                                                if (!release.await(
                                                        5, java.util.concurrent.TimeUnit.SECONDS)) {
                                                    throw new IllegalStateException(
                                                            "concurrent identity test timed out");
                                                }
                                            } catch (InterruptedException error) {
                                                Thread.currentThread().interrupt();
                                                throw new IllegalStateException(error);
                                            }
                                            seen.set(ServicePrincipal.CONTEXT_KEY.get());
                                            return new Listener<>() {};
                                        }));
        return seen.get();
    }

    private static ServerCall<Object, Object> call(String method, Attributes attributes) {
        ServerCall<Object, Object> call = mock(ServerCall.class);
        MethodDescriptor<Object, Object> descriptor = mock(MethodDescriptor.class);
        when(descriptor.getFullMethodName()).thenReturn(method);
        when(call.getMethodDescriptor()).thenReturn(descriptor);
        when(call.getAttributes()).thenReturn(attributes);
        return call;
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
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
