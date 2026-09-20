package ru.rutcampustrack.attendance.grpc;

import com.google.rpc.Status;
import io.grpc.Context;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.protobuf.StatusProto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import ru.rutcampustrack.attendance.student.StudentCheckinException;
import ru.rutcampustrack.shared.security.InternalJwtClaims;
import ru.rutcampustrack.shared.security.InternalJwtException;
import ru.rutcampustrack.shared.security.InternalJwtValidator;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

class StudentGrpcBoundaryTest {

    private InternalJwtValidator validator;
    private StudentGrpcIdentityInterceptor interceptor;

    @BeforeEach
    void setUp() {
        validator = mock(InternalJwtValidator.class);
        interceptor = new StudentGrpcIdentityInterceptor(validator);
    }

    @Test
    void studentServiceWithoutTokenIsRejectedBeforeHandler() {
        when(validator.validate(isNull())).thenThrow(new InternalJwtException("Token is missing"));
        ServerCall<Object, Object> call = callFor(StudentGrpcIdentityInterceptor.STUDENT_SERVICE);
        ServerCallHandler<Object, Object> next = mock(ServerCallHandler.class);

        interceptor.interceptCall(call, new Metadata(), next);

        verify(call).close(argThat(status -> status.getCode() == io.grpc.Status.Code.UNAUTHENTICATED), any());
        verifyNoInteractions(next);
    }

    @Test
    void invalidStudentTokenIsRejectedWithoutLeakingValidatorReason() {
        when(validator.validate("bad-token"))
                .thenThrow(new InternalJwtException("signature diagnostic must stay internal"));
        ServerCall<Object, Object> call = callFor(StudentGrpcIdentityInterceptor.STUDENT_SERVICE);
        ServerCallHandler<Object, Object> next = mock(ServerCallHandler.class);
        Metadata headers = new Metadata();
        headers.put(StudentGrpcIdentityInterceptor.INTERNAL_TOKEN, "bad-token");

        interceptor.interceptCall(call, headers, next);

        verify(call).close(argThat(status -> status.getCode() == io.grpc.Status.Code.UNAUTHENTICATED
                && "Invalid internal identity".equals(status.getDescription())), any());
        verifyNoInteractions(next);
    }

    @Test
    void validStudentTokenPropagatesClaimsThroughGrpcContext() {
        InternalJwtClaims claims = new InternalJwtClaims(100L, "STUDENT", 10L, false);
        when(validator.validate("valid-token")).thenReturn(claims);
        ServerCall<Object, Object> call = callFor(StudentGrpcIdentityInterceptor.STUDENT_SERVICE);
        ServerCallHandler<Object, Object> next = mock(ServerCallHandler.class);
        ServerCall.Listener<Object> listener = new ServerCall.Listener<>() { };
        AtomicReference<InternalJwtClaims> observed = new AtomicReference<>();
        when(next.startCall(eq(call), any())).thenAnswer(invocation -> {
            observed.set(StudentGrpcIdentity.CLAIMS.get());
            return listener;
        });
        Metadata headers = new Metadata();
        headers.put(StudentGrpcIdentityInterceptor.INTERNAL_TOKEN, "valid-token");

        // Contexts.interceptCall deliberately wraps the listener so callbacks
        // continue under the authenticated Context.
        assertThat(interceptor.interceptCall(call, headers, next)).isNotNull();
        assertThat(observed).hasValue(claims);
        assertThat(StudentGrpcIdentity.CLAIMS.get()).isNull();
        verify(call, never()).close(any(), any());
    }

    @Test
    void unrelatedGrpcServiceKeepsExistingAuthFlow() {
        ServerCall<Object, Object> call = callFor("rutcampustrack.schedule.ScheduleService");
        ServerCallHandler<Object, Object> next = mock(ServerCallHandler.class);
        ServerCall.Listener<Object> listener = new ServerCall.Listener<>() { };
        when(next.startCall(eq(call), any())).thenReturn(listener);

        assertThat(interceptor.interceptCall(call, new Metadata(), next)).isSameAs(listener);
        verifyNoInteractions(validator);
    }

    @Test
    void cooldownErrorCarriesTypedRetryDetail() throws Exception {
        Instant retryAt = Instant.parse("2026-09-06T07:05:00Z");
        RuntimeException error = StudentCheckinGrpcErrors.toStatus(new StudentCheckinException(
                StudentCheckinException.Code.CHECKIN_COOLDOWN, "Повторите позже", retryAt));

        Status status = StatusProto.fromThrowable(error);

        assertThat(status).isNotNull();
        assertThat(status.getCode()).isEqualTo(io.grpc.Status.Code.RESOURCE_EXHAUSTED.value());
        assertThat(status.getDetailsCount()).isEqualTo(1);
        assertThat(status.getDetails(0).is(StudentCheckinErrorDetail.class)).isTrue();
        StudentCheckinErrorDetail detail = status.getDetails(0).unpack(StudentCheckinErrorDetail.class);
        assertThat(detail.getCode()).isEqualTo(
                StudentCheckinErrorCode.STUDENT_CHECKIN_ERROR_CODE_CHECKIN_COOLDOWN);
        assertThat(detail.getRetryAt()).isEqualTo(retryAt.toString());
    }

    @ParameterizedTest
    @EnumSource(value = StudentCheckinException.Code.class, names = "CHECKIN_COOLDOWN", mode = EnumSource.Mode.EXCLUDE)
    void everyDomainErrorCarriesItsTypedCode(StudentCheckinException.Code code) throws Exception {
        RuntimeException error = StudentCheckinGrpcErrors.toStatus(new StudentCheckinException(code, "domain error"));

        Status status = StatusProto.fromThrowable(error);
        StudentCheckinErrorDetail detail = status.getDetails(0).unpack(StudentCheckinErrorDetail.class);

        assertThat(detail.getCode().name()).isEqualTo("STUDENT_CHECKIN_ERROR_CODE_" + code.name());
        assertThat(detail.getRetryAt()).isEmpty();
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
