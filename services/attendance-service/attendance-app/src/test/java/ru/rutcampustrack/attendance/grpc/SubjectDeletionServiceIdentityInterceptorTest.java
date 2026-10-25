package ru.rutcampustrack.attendance.grpc;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import io.grpc.*;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;

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
                (c, h) -> {
                    called.set(true);
                    principal.set(ServicePrincipal.CONTEXT_KEY.get());
                    return new ServerCall.Listener<>() {};
                });
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
        var wrong = headers(Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[31]));
        var duplicate = headers(TOKEN);
        duplicate.put(DirectedServiceCredential.TOKEN_METADATA_KEY, TOKEN);
        for (var metadata : List.of(new Metadata(), wrong, duplicate)) {
            var c = call(method, true);
            var bypass = new AtomicBoolean();
            interceptor.interceptCall(
                    c,
                    metadata,
                    (n, h) -> {
                        bypass.set(true);
                        return new ServerCall.Listener<>() {};
                    });
            assertThat(bypass.get()).isFalse();
            verify(c)
                    .close(
                            argThat(s -> s.getCode() == Status.Code.UNAUTHENTICATED),
                            any(Metadata.class));
        }
        var plain = call(method, false);
        var bypass = new AtomicBoolean();
        interceptor.interceptCall(
                plain,
                headers(TOKEN),
                (n, h) -> {
                    bypass.set(true);
                    return new ServerCall.Listener<>() {};
                });
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
                (n, h) -> {
                    called.set(true);
                    return new ServerCall.Listener<>() {};
                });
        assertThat(called.get()).isFalse();
        verify(reserved).close(any(Status.class), any(Metadata.class));
        interceptor.interceptCall(
                call("existing.Unrelated/Read", false),
                new Metadata(),
                (n, h) -> {
                    called.set(true);
                    return new ServerCall.Listener<>() {};
                });
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
                                            "grpc.service-identity.academic-to-attendance-token",
                                            TOKEN)));
            context.register(SubjectDeletionServiceIdentityInterceptor.class);
            context.refresh();
            assertThat(context.getBean(SubjectDeletionServiceIdentityInterceptor.class))
                    .isNotNull();
        }
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
