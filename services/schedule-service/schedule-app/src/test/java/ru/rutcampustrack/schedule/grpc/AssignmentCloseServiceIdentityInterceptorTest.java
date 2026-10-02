package ru.rutcampustrack.schedule.grpc;

import io.grpc.Attributes;
import io.grpc.Grpc;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.ServerCall;
import io.grpc.ServerCall.Listener;
import io.grpc.Status;
import io.grpc.Context;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;
import ru.rutcampustrack.shared.security.grpc.DirectedServiceCredential;
import ru.rutcampustrack.shared.security.grpc.ServicePrincipal;

import javax.net.ssl.SSLSession;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AssignmentCloseServiceIdentityInterceptorTest {

    private static final String TOKEN = token((byte) 0x77);

    @Test
    void validAcademicCredentialBindsAcademicPrincipal() {
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
                    return new Listener<>() { };
                });

        assertThat(called.get()).isTrue();
        assertThat(seen).hasValue(ServicePrincipal.ACADEMIC_SERVICE);
    }

    @Test
    void validAcademicCredentialAlsoProtectsCommitMethod() {
        AssignmentCloseServiceIdentityInterceptor interceptor =
                new AssignmentCloseServiceIdentityInterceptor(TOKEN);
        AtomicReference<ServicePrincipal> seen = new AtomicReference<>();
        AtomicBoolean called = new AtomicBoolean();

        interceptor.interceptCall(
                call(AssignmentCloseServiceIdentityInterceptor.COMMIT_METHOD, tlsAttributes()),
                headers(TOKEN),
                (nextCall, metadata) -> {
                    called.set(true);
                    seen.set(ServicePrincipal.CONTEXT_KEY.get());
                    return new Listener<>() { };
                });

        assertThat(called.get()).isTrue();
        assertThat(seen).hasValue(ServicePrincipal.ACADEMIC_SERVICE);
    }

    @Test
    void homeworkRecoveryMethodsRequireDirectedAcademicIdentityAndTls() {
        var interceptor = new AssignmentCloseServiceIdentityInterceptor(TOKEN);
        for (String method : java.util.List.of("GetHomeworkBinding", "MoveHomeworkBinding", "ContinueHomeworkEdit",
                "AbortUnacceptedHomeworkEdit", "AcknowledgeHomeworkEdit")) {
            String full = ScheduleGrpcServiceGrpc.SERVICE_NAME + "/" + method;
            AtomicBoolean allowed = new AtomicBoolean();
            interceptor.interceptCall(call(full, tlsAttributes()), headers(TOKEN), (nextCall, metadata) -> {
                allowed.set(ServicePrincipal.CONTEXT_KEY.get() == ServicePrincipal.ACADEMIC_SERVICE);
                return new Listener<>() { };
            });
            assertThat(allowed.get()).as(method).isTrue();
            var missing = call(full, tlsAttributes());
            AtomicBoolean bypass = new AtomicBoolean();
            interceptor.interceptCall(missing, new Metadata(), (nextCall, metadata) -> {
                bypass.set(true); return new Listener<>() { };
            });
            assertThat(bypass.get()).as(method).isFalse(); verify(missing).close(any(Status.class), any(Metadata.class));
            var plain = call(full, Attributes.EMPTY);
            interceptor.interceptCall(plain, headers(TOKEN), (nextCall, metadata) -> {
                bypass.set(true); return new Listener<>() { };
            });
            assertThat(bypass.get()).as(method).isFalse(); verify(plain).close(any(Status.class), any(Metadata.class));
        }
    }

    @Test
    void missingConfigurationDeniesOnlyReservedMethod() {
        AssignmentCloseServiceIdentityInterceptor interceptor =
                new AssignmentCloseServiceIdentityInterceptor("");
        ServerCall<Object, Object> call = call(
                AssignmentCloseServiceIdentityInterceptor.PROTECTED_METHOD, tlsAttributes());
        AtomicBoolean called = new AtomicBoolean();

        interceptor.interceptCall(call, new Metadata(), (nextCall, metadata) -> {
            called.set(true);
            return new Listener<>() { };
        });

        verify(call).close(any(Status.class), any(Metadata.class));
        assertThat(called.get()).isFalse();
    }

    @Test
    void missingConfigurationAlsoDeniesCommitMethod() {
        AssignmentCloseServiceIdentityInterceptor interceptor =
                new AssignmentCloseServiceIdentityInterceptor("");
        ServerCall<Object, Object> call = call(
                AssignmentCloseServiceIdentityInterceptor.COMMIT_METHOD, tlsAttributes());
        AtomicBoolean called = new AtomicBoolean();

        interceptor.interceptCall(call, headers(TOKEN), (nextCall, metadata) -> {
            called.set(true);
            return new Listener<>() { };
        });

        verify(call).close(any(Status.class), any(Metadata.class));
        assertThat(called.get()).isFalse();
    }

    @Test
    void unrelatedMethodRetainsExistingAdmission() {
        AssignmentCloseServiceIdentityInterceptor interceptor =
                new AssignmentCloseServiceIdentityInterceptor("");
        AtomicBoolean called = new AtomicBoolean();
        interceptor.interceptCall(call("rutcampustrack.schedule.ScheduleGrpcService/ResolveLesson", Attributes.EMPTY),
                new Metadata(), (nextCall, metadata) -> {
                    called.set(true);
                    return new Listener<>() { };
                });
        assertThat(called.get()).isTrue();
    }

    @Test
    void explicitSpringRegistrationWorksWithoutStartingTheApplication() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().getPropertySources().addFirst(new MapPropertySource(
                    "test-service-identity",
                    java.util.Map.of("grpc.service-identity.academic-to-schedule-token", TOKEN)));
            context.register(AssignmentCloseServiceAuthConfiguration.class);
            context.refresh();

            assertThat(context.getBean(AssignmentCloseServiceIdentityInterceptor.class)).isNotNull();
        }
    }

    @Test
    void concurrentCallsKeepVerifiedContextBoundToEachCall() throws Exception {
        AssignmentCloseServiceIdentityInterceptor interceptor =
                new AssignmentCloseServiceIdentityInterceptor(TOKEN);
        java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newFixedThreadPool(2);
        java.util.concurrent.CountDownLatch entered = new java.util.concurrent.CountDownLatch(2);
        java.util.concurrent.CountDownLatch release = new java.util.concurrent.CountDownLatch(1);
        AtomicReference<ServicePrincipal> outerAfter = new AtomicReference<>();
        try {
            java.util.concurrent.Future<ServicePrincipal> first = executor.submit(
                    () -> invoke(interceptor, entered, release));
            java.util.concurrent.Future<ServicePrincipal> second = executor.submit(
                    () -> invoke(interceptor, entered, release));

            assertThat(entered.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            release.countDown();
            assertThat(first.get(5, java.util.concurrent.TimeUnit.SECONDS))
                    .isEqualTo(ServicePrincipal.ACADEMIC_SERVICE);
            assertThat(second.get(5, java.util.concurrent.TimeUnit.SECONDS))
                    .isEqualTo(ServicePrincipal.ACADEMIC_SERVICE);
            Context.current().withValue(ServicePrincipal.CONTEXT_KEY, new ServicePrincipal("outer-service"))
                    .run(() -> {
                        interceptor.interceptCall(
                                call(AssignmentCloseServiceIdentityInterceptor.PROTECTED_METHOD, tlsAttributes()),
                                headers(TOKEN),
                                (nextCall, metadata) -> new Listener<>() { });
                        outerAfter.set(ServicePrincipal.CONTEXT_KEY.get());
                    });
            assertThat(outerAfter).hasValue(new ServicePrincipal("outer-service"));
        } finally {
            release.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
        }
    }

    private static ServicePrincipal invoke(
            AssignmentCloseServiceIdentityInterceptor interceptor,
            java.util.concurrent.CountDownLatch entered,
            java.util.concurrent.CountDownLatch release) {
        AtomicReference<ServicePrincipal> seen = new AtomicReference<>();
        Context.current().withValue(ServicePrincipal.CONTEXT_KEY, new ServicePrincipal("outer-service"))
                .run(() -> interceptor.interceptCall(
                        call(AssignmentCloseServiceIdentityInterceptor.PROTECTED_METHOD, tlsAttributes()),
                        headers(TOKEN),
                        (nextCall, metadata) -> {
                            entered.countDown();
                            try {
                                if (!release.await(5, java.util.concurrent.TimeUnit.SECONDS)) {
                                    throw new IllegalStateException("concurrent identity test timed out");
                                }
                            } catch (InterruptedException error) {
                                Thread.currentThread().interrupt();
                                throw new IllegalStateException(error);
                            }
                            seen.set(ServicePrincipal.CONTEXT_KEY.get());
                            return new Listener<>() { };
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
