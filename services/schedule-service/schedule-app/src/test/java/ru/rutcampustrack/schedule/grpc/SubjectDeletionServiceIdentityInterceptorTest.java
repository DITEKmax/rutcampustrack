package ru.rutcampustrack.schedule.grpc;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import io.grpc.*;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.test.util.ReflectionTestUtils;

import ru.rutcampustrack.shared.security.grpc.*;
import ru.rutcampustrack.subjectdeletion.grpc.SubjectDeletionParticipantGrpc;

import java.util.*;
import java.util.concurrent.atomic.*;

import javax.net.ssl.SSLSession;

class SubjectDeletionServiceIdentityInterceptorTest {
    private static final String TOKEN =
            Base64.getUrlEncoder()
                    .withoutPadding()
                    .encodeToString(new byte[DirectedServiceCredential.TOKEN_BYTES]);
    private static final String METHOD =
            SubjectDeletionParticipantGrpc.getApplyMethod().getFullMethodName();

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(
            strings = {
                "rutcampustrack.subjectdeletion.SubjectDeletionParticipant/Apply",
                "rutcampustrack.groupdeletion.GroupDeletionParticipant/Apply"
            })
    void exactMutationOverTlsBindsOnlyAcademicPrincipal(String method) {
        var interceptor = new SubjectDeletionServiceIdentityInterceptor(TOKEN);
        var called = new AtomicBoolean();
        var principal = new AtomicReference<ServicePrincipal>();
        interceptor.interceptCall(
                call(method, true),
                headers(TOKEN),
                throughLegacy(
                        (c, h) -> {
                            called.set(true);
                            principal.set(ServicePrincipal.CONTEXT_KEY.get());
                            return new ServerCall.Listener<>() {};
                        }));
        assertThat(called.get()).isTrue();
        assertThat(principal.get()).isEqualTo(ServicePrincipal.ACADEMIC_SERVICE);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(
            strings = {
                "rutcampustrack.subjectdeletion.SubjectDeletionParticipant/Apply",
                "rutcampustrack.groupdeletion.GroupDeletionParticipant/Apply"
            })
    void missingWrongDuplicateOrPlaintextIdentityCannotReachMutation(String method) {
        var interceptor = new SubjectDeletionServiceIdentityInterceptor(TOKEN);
        var other = new byte[32];
        Arrays.fill(other, (byte) 1);
        var wrong = headers(Base64.getUrlEncoder().withoutPadding().encodeToString(other));
        var duplicate = headers(TOKEN);
        duplicate.put(DirectedServiceCredential.TOKEN_METADATA_KEY, TOKEN);
        for (var metadata : List.of(new Metadata(), wrong, duplicate)) {
            metadata.put(legacyKey(), "legacy-secret");
            var c = call(method, true);
            var bypass = new AtomicBoolean();
            interceptor.interceptCall(
                    c,
                    metadata,
                    throughLegacy(
                            (n, h) -> {
                                bypass.set(true);
                                return new ServerCall.Listener<>() {};
                            }));
            assertThat(bypass.get()).isFalse();
            verify(c)
                    .close(
                            argThat(s -> s.getCode() == Status.Code.UNAUTHENTICATED),
                            any(Metadata.class));
        }
        var plain = call(method, false);
        var plainHeaders = headers(TOKEN);
        plainHeaders.put(legacyKey(), "legacy-secret");
        var bypass = new AtomicBoolean();
        interceptor.interceptCall(
                plain,
                plainHeaders,
                throughLegacy(
                        (n, h) -> {
                            bypass.set(true);
                            return new ServerCall.Listener<>() {};
                        }));
        assertThat(bypass.get()).isFalse();
        verify(plain)
                .close(
                        argThat(s -> s.getCode() == Status.Code.UNAUTHENTICATED),
                        any(Metadata.class));
    }

    @Test
    void absentConfigurationFailsClosedForMutationButDoesNotChangeOtherMethods() {
        var interceptor = new SubjectDeletionServiceIdentityInterceptor("");
        var reserved = call(METHOD, true);
        var called = new AtomicBoolean();
        interceptor.interceptCall(
                reserved,
                headers(TOKEN),
                throughLegacy(
                        (n, h) -> {
                            called.set(true);
                            return new ServerCall.Listener<>() {};
                        }));
        assertThat(called.get()).isFalse();
        verify(reserved).close(any(Status.class), any(Metadata.class));
        var unrelated = call("existing.Unrelated/Read", false);
        interceptor.interceptCall(
                unrelated,
                new Metadata(),
                throughLegacy(
                        (n, h) -> {
                            called.set(true);
                            return new ServerCall.Listener<>() {};
                        }));
        assertThat(called.get()).isFalse();
        verify(unrelated)
                .close(
                        argThat(s -> s.getCode() == Status.Code.UNAUTHENTICATED),
                        any(Metadata.class));
        var legacyHeaders = new Metadata();
        legacyHeaders.put(legacyKey(), "legacy-secret");
        interceptor.interceptCall(
                call("existing.Unrelated/Read", false),
                legacyHeaders,
                throughLegacy(
                        (n, h) -> {
                            called.set(true);
                            return new ServerCall.Listener<>() {};
                        }));
        assertThat(called.get()).isTrue();
    }

    @Test
    void actualSpringAnnotationRegistersTheExactConfiguredInterceptor() {
        try (var context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment()
                    .getPropertySources()
                    .addFirst(
                            new MapPropertySource(
                                    "subject-test",
                                    Map.of(
                                            "grpc.service-identity.academic-to-schedule-token",
                                            TOKEN)));
            context.register(SubjectDeletionServiceIdentityInterceptor.class);
            context.refresh();
            assertThat(context.getBean(SubjectDeletionServiceIdentityInterceptor.class))
                    .isNotNull();
        }
    }

    private static Metadata.Key<String> legacyKey() {
        return Metadata.Key.of("x-grpc-secret", Metadata.ASCII_STRING_MARSHALLER);
    }

    private static ServerCallHandler<Object, Object> throughLegacy(
            ServerCallHandler<Object, Object> next) {
        var legacy = new GrpcAuthInterceptor();
        ReflectionTestUtils.setField(legacy, "expectedSecret", "legacy-secret");
        return (call, headers) -> legacy.interceptCall(call, headers, next);
    }

    private static Metadata headers(String token) {
        var h = new Metadata();
        h.put(DirectedServiceCredential.TOKEN_METADATA_KEY, token);
        return h;
    }

    private static ServerCall<Object, Object> call(String method, boolean tls) {
        ServerCall<Object, Object> call = mock(ServerCall.class);
        MethodDescriptor<Object, Object> descriptor = mock(MethodDescriptor.class);
        when(descriptor.getFullMethodName()).thenReturn(method);
        when(call.getMethodDescriptor()).thenReturn(descriptor);
        when(call.getAttributes())
                .thenReturn(
                        tls
                                ? Attributes.newBuilder()
                                        .set(
                                                Grpc.TRANSPORT_ATTR_SSL_SESSION,
                                                mock(SSLSession.class))
                                        .build()
                                : Attributes.EMPTY);
        return call;
    }
}
