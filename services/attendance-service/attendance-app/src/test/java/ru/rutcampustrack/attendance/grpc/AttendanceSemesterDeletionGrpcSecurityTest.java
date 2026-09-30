package ru.rutcampustrack.attendance.grpc;

import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AttendanceSemesterDeletionGrpcSecurityTest {

    private AttendanceRequestBotGrpcSecretInterceptor interceptor;

    @BeforeEach
    void setUp() {
        interceptor = new AttendanceRequestBotGrpcSecretInterceptor();
        ReflectionTestUtils.setField(interceptor, "expectedSecret", "test-internal-secret");
    }

    @Test
    void semesterDeletionPreviewRejectsMissingAndWrongSecretBeforeService() {
        ServerCall<Object, Object> call = callFor(AttendanceRequestBotGrpcSecretInterceptor.SEMESTER_DELETION_SERVICE);
        ServerCallHandler<Object, Object> next = mock(ServerCallHandler.class);

        interceptor.interceptCall(call, new Metadata(), next);

        Metadata wrongSecret = new Metadata();
        wrongSecret.put(AttendanceRequestBotGrpcSecretInterceptor.SECRET_KEY, "wrong-secret");
        interceptor.interceptCall(call, wrongSecret, next);

        verify(call, org.mockito.Mockito.times(2)).close(
                argThat(status -> status.getCode() == io.grpc.Status.Code.UNAUTHENTICATED), any());
        verifyNoInteractions(next);
    }

    @Test
    void semesterDeletionPreviewAcceptsOnlyConfiguredSharedSecret() {
        ServerCall<Object, Object> call = callFor(AttendanceRequestBotGrpcSecretInterceptor.SEMESTER_DELETION_SERVICE);
        ServerCallHandler<Object, Object> next = mock(ServerCallHandler.class);
        ServerCall.Listener<Object> listener = new ServerCall.Listener<>() { };
        when(next.startCall(eq(call), any())).thenReturn(listener);
        Metadata headers = new Metadata();
        headers.put(AttendanceRequestBotGrpcSecretInterceptor.SECRET_KEY, "test-internal-secret");

        assertThat(interceptor.interceptCall(call, headers, next)).isSameAs(listener);

        verify(next).startCall(eq(call), eq(headers));
        verify(call, never()).close(any(), any());
    }

    @Test
    void unrelatedServiceKeepsExistingAuthenticationFlow() {
        ServerCall<Object, Object> call = callFor("rutcampustrack.schedule.ScheduleService");
        ServerCallHandler<Object, Object> next = mock(ServerCallHandler.class);
        ServerCall.Listener<Object> listener = new ServerCall.Listener<>() { };
        when(next.startCall(eq(call), any())).thenReturn(listener);

        assertThat(interceptor.interceptCall(call, new Metadata(), next)).isSameAs(listener);

        verify(next).startCall(eq(call), any());
        verify(call, never()).close(any(), any());
    }

    @SuppressWarnings("unchecked")
    private static ServerCall<Object, Object> callFor(String serviceName) {
        ServerCall<Object, Object> call = mock(ServerCall.class);
        MethodDescriptor<Object, Object> descriptor = mock(MethodDescriptor.class);
        when(descriptor.getServiceName()).thenReturn(serviceName);
        when(call.getMethodDescriptor()).thenReturn(descriptor);
        return call;
    }
}
