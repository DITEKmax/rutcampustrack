package ru.rutcampustrack.attendance.botread;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import io.grpc.*;

import net.devh.boot.grpc.client.interceptor.AnnotationGlobalClientInterceptorConfigurer;
import net.devh.boot.grpc.client.interceptor.GlobalClientInterceptorRegistry;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;

import ru.rutcampustrack.attendance.grpc.GrpcSecretClientInterceptor;
import ru.rutcampustrack.authauthority.grpc.AuthIdentityGrpcServiceGrpc;
import ru.rutcampustrack.botreads.grpc.AcademicOwnBotReadServiceGrpc;
import ru.rutcampustrack.schedule.grpc.ScheduleGrpcServiceGrpc;
import ru.rutcampustrack.shared.security.grpc.*;

import java.util.Base64;
import java.util.Map;

class AttendanceBotGlobalClientAdmissionTest {
    private static final Metadata.Key<String> SECRET =
            Metadata.Key.of("x-grpc-secret", Metadata.ASCII_STRING_MARSHALLER);

    @Test
    void frameworkGlobalDiscoveryEmitsNoLegacySecretForNewDirectedReadsAndPreservesLegacy() {
        try (var context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment()
                    .getPropertySources()
                    .addFirst(
                            new MapPropertySource(
                                    "synthetic",
                                    Map.of("grpc.auth.secret", "synthetic-legacy-secret")));
            context.register(GrpcSecretClientInterceptor.class);
            context.registerBean(
                    AnnotationGlobalClientInterceptorConfigurer.class,
                    () -> new AnnotationGlobalClientInterceptorConfigurer(context));
            context.registerBean(
                    GlobalClientInterceptorRegistry.class,
                    () -> new GlobalClientInterceptorRegistry(context));
            context.refresh();
            var registry = context.getBean(GlobalClientInterceptorRegistry.class);
            assertThat(registry.getClientInterceptors()).hasSize(1);
            assertNoSecret(
                    registry,
                    AuthIdentityGrpcServiceGrpc.getCheckAttendanceBotOwnAuthorityMethod(),
                    CallOptions.DEFAULT);
            assertNoSecret(
                    registry,
                    AcademicOwnBotReadServiceGrpc.getResolveAttendanceOwnBotReadScopeMethod(),
                    CallOptions.DEFAULT);
            var token = Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[32]);
            var credential =
                    new DirectedServiceCredential(
                            new ServicePrincipal("attendance-service"),
                            ServicePrincipal.SCHEDULE_SERVICE,
                            token);
            var options =
                    CallOptions.DEFAULT.withCallCredentials(
                            ServiceTokenCallCredentials.forMethod(
                                    credential,
                                    "schedule-service",
                                    ScheduleGrpcServiceGrpc.getGetLessonByIdMethod()
                                            .getFullMethodName()));
            assertNoSecret(registry, ScheduleGrpcServiceGrpc.getGetLessonByIdMethod(), options);
            assertThat(
                            headers(
                                            registry,
                                            ScheduleGrpcServiceGrpc.getGetLessonByIdMethod(),
                                            CallOptions.DEFAULT)
                                    .get(SECRET))
                    .isEqualTo("synthetic-legacy-secret");
            assertThat(
                            headers(
                                            registry,
                                            ScheduleGrpcServiceGrpc.getGetLessonsByGroupMethod(),
                                            options)
                                    .get(SECRET))
                    .isEqualTo("synthetic-legacy-secret");
        }
    }

    private static void assertNoSecret(
            GlobalClientInterceptorRegistry registry,
            MethodDescriptor<?, ?> method,
            CallOptions options) {
        assertThat(headers(registry, method, options).getAll(SECRET)).isNull();
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Metadata headers(
            GlobalClientInterceptorRegistry registry,
            MethodDescriptor method,
            CallOptions options) {
        var captured = new Metadata();
        ClientCall delegate = mock(ClientCall.class);
        doAnswer(
                        invocation -> {
                            captured.merge(invocation.getArgument(1));
                            return null;
                        })
                .when(delegate)
                .start(any(), any());
        Channel transport = mock(Channel.class);
        when(transport.newCall(any(), any())).thenReturn(delegate);
        Channel global =
                ClientInterceptors.interceptForward(transport, registry.getClientInterceptors());
        Metadata injected = new Metadata();
        injected.put(SECRET, "synthetic-preexisting-broad-secret");
        global.newCall(method, options).start(new ClientCall.Listener<>() {}, injected);
        return captured;
    }
}
