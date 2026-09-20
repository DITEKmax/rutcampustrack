package ru.rutcampustrack.academic.grpc;

import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.Status;
import org.junit.jupiter.api.Test;
import ru.rutcampustrack.shared.security.InternalJwtClaims;
import ru.rutcampustrack.shared.security.InternalJwtException;
import ru.rutcampustrack.shared.security.InternalJwtValidator;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import org.mockito.ArgumentCaptor;

class StudentHomeworkGrpcIdentityInterceptorTest {
    private final InternalJwtValidator validator = mock(InternalJwtValidator.class);
    private final StudentHomeworkGrpcIdentityInterceptor interceptor =
            new StudentHomeworkGrpcIdentityInterceptor(validator);

    @Test
    void validTokenBindsClaimsOnlyForHomeworkMutation() {
        InternalJwtClaims claims = new InternalJwtClaims(100L, "STUDENT", 10L, false);
        when(validator.validate("signed")).thenReturn(claims);
        AtomicReference<InternalJwtClaims> seen = new AtomicReference<>();
        ServerCallHandler<Object, Object> next = (call, headers) -> {
            seen.set(StudentHomeworkGrpcIdentity.CLAIMS.get());
            return new ServerCall.Listener<>() { };
        };

        interceptor.interceptCall(call("SetHomeworkCompletion"), headers("signed"), next);

        assertThat(seen.get()).isEqualTo(claims);
    }

    @Test
    void invalidTokenClosesMutationWithUnauthenticated() {
        when(validator.validate("bad")).thenThrow(new InternalJwtException("bad token"));
        ServerCall<Object, Object> call = call("SetHomeworkCompletion");
        @SuppressWarnings("unchecked")
        ServerCall.Listener<Object> ignored = interceptor.interceptCall(
                call, headers("bad"), (nextCall, metadata) -> new ServerCall.Listener<>() { });

        ArgumentCaptor<Status> status = ArgumentCaptor.forClass(Status.class);
        verify(call).close(status.capture(), any());
        assertThat(status.getValue().getCode()).isEqualTo(Status.Code.UNAUTHENTICATED);
    }

    private static ServerCall<Object, Object> call(String method) {
        @SuppressWarnings("unchecked")
        ServerCall<Object, Object> call = mock(ServerCall.class);
        MethodDescriptor<Object, Object> descriptor = mock(MethodDescriptor.class);
        when(descriptor.getFullMethodName())
                .thenReturn("rutcampustrack.academic.AcademicGrpcService/" + method);
        when(call.getMethodDescriptor()).thenReturn(descriptor);
        return call;
    }

    private static Metadata headers(String token) {
        Metadata metadata = new Metadata();
        metadata.put(StudentHomeworkGrpcIdentityInterceptor.INTERNAL_TOKEN, token);
        return metadata;
    }
}
