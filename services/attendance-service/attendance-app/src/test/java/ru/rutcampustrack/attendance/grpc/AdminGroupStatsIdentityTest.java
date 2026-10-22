package ru.rutcampustrack.attendance.grpc;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import io.grpc.*;

import org.junit.jupiter.api.Test;

import ru.rutcampustrack.shared.security.InternalJwtClaims;
import ru.rutcampustrack.shared.security.InternalJwtValidator;
import ru.rutcampustrack.shared.security.grpc.DirectedServiceCredential;
import ru.rutcampustrack.shared.security.grpc.ServicePrincipal;

import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.net.ssl.SSLSession;

/** Exact new method admission, not a substitute for signed-token/TLS transport integration. */
class AdminGroupStatsIdentityTest {
    private static final String SERVICE_TOKEN =
            Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[32]);
    private static final Metadata.Key<String> USER_TOKEN =
            Metadata.Key.of("x-internal-token", Metadata.ASCII_STRING_MARSHALLER);

    @Test
    void exactMethodRequiresTlsDirectedCredentialAndActiveAdminUserTogether() {
        var validator = mock(InternalJwtValidator.class);
        when(validator.validate("synthetic-user-token")).thenReturn(claims("ADMIN", false));
        var interceptor = new AdminGroupStatsGrpcIdentityInterceptor(validator, SERVICE_TOKEN);
        for (boolean tls : new boolean[] {false, true}) {
            for (boolean credential : new boolean[] {false, true}) {
                var call = call(tls);
                Metadata headers = headers(credential);
                AtomicBoolean admitted = new AtomicBoolean();
                interceptor.interceptCall(
                        call,
                        headers,
                        (next, meta) -> {
                            assertThat(ServicePrincipal.CONTEXT_KEY.get())
                                    .isEqualTo(ServicePrincipal.ACADEMIC_SERVICE);
                            assertThat(
                                            AdminGroupStatsGrpcIdentity.requireAdminToken()
                                                    .equals("synthetic-user-token"))
                                    .isTrue();
                            admitted.set(true);
                            return new ServerCall.Listener<>() {};
                        });
                assertThat(admitted.get()).isEqualTo(tls && credential);
                if (!(tls && credential))
                    verify(call).close(any(Status.class), any(Metadata.class));
            }
        }
        assertThat(ServicePrincipal.CONTEXT_KEY.get()).isNull();
        assertThat(AdminGroupStatsGrpcIdentity.CLAIMS.get()).isNull();
    }

    @Test
    void absentConfigurationAndDuplicateCredentialCannotAdmitProtectedMethod() {
        var validator = mock(InternalJwtValidator.class);
        when(validator.validate(any())).thenReturn(claims("ADMIN", false));
        AtomicBoolean admitted = new AtomicBoolean();
        new AdminGroupStatsGrpcIdentityInterceptor(validator, "")
                .interceptCall(
                        call(true),
                        headers(true),
                        (next, meta) -> {
                            admitted.set(true);
                            return new ServerCall.Listener<>() {};
                        });
        Metadata duplicate = headers(true);
        duplicate.put(DirectedServiceCredential.TOKEN_METADATA_KEY, SERVICE_TOKEN);
        new AdminGroupStatsGrpcIdentityInterceptor(validator, SERVICE_TOKEN)
                .interceptCall(
                        call(true),
                        duplicate,
                        (next, meta) -> {
                            admitted.set(true);
                            return new ServerCall.Listener<>() {};
                        });
        assertThat(admitted.get()).isFalse();
        Metadata duplicateUser = headers(true);
        duplicateUser.put(USER_TOKEN, "synthetic-user-token");
        var duplicateCall = call(true);
        new AdminGroupStatsGrpcIdentityInterceptor(validator, SERVICE_TOKEN)
                .interceptCall(
                        duplicateCall,
                        duplicateUser,
                        (next, meta) -> {
                            admitted.set(true);
                            return new ServerCall.Listener<>() {};
                        });
        assertThat(admitted.get()).isFalse();
        verify(duplicateCall).close(any(Status.class), any(Metadata.class));
        for (var claims : java.util.List.of(claims("TEACHER", false), claims("ADMIN", true))) {
            Context.current()
                    .withValue(ServicePrincipal.CONTEXT_KEY, ServicePrincipal.ACADEMIC_SERVICE)
                    .withValue(AdminGroupStatsGrpcIdentity.CLAIMS, claims)
                    .withValue(AdminGroupStatsGrpcIdentity.TOKEN, "synthetic-user-token")
                    .run(
                            () ->
                                    assertThatThrownBy(
                                                    AdminGroupStatsGrpcIdentity::requireAdminToken)
                                            .isInstanceOf(StatusRuntimeException.class)
                                            .satisfies(
                                                    error ->
                                                            assertThat(
                                                                            ((StatusRuntimeException)
                                                                                            error)
                                                                                    .getStatus()
                                                                                    .getCode())
                                                                    .isEqualTo(
                                                                            Status.Code
                                                                                    .PERMISSION_DENIED)));
        }
    }

    private static InternalJwtClaims claims(String role, boolean readOnly) {
        return new InternalJwtClaims(
                1, UUID.randomUUID(), 1, 1, role, "ACTIVE", null, false, readOnly);
    }

    private static Metadata headers(boolean service) {
        Metadata result = new Metadata();
        result.put(USER_TOKEN, "synthetic-user-token");
        if (service) result.put(DirectedServiceCredential.TOKEN_METADATA_KEY, SERVICE_TOKEN);
        return result;
    }

    @SuppressWarnings("unchecked")
    private static ServerCall<Object, Object> call(boolean tls) {
        ServerCall<Object, Object> call = mock(ServerCall.class);
        MethodDescriptor<Object, Object> method =
                MethodDescriptor.newBuilder()
                        .setType(MethodDescriptor.MethodType.UNARY)
                        .setFullMethodName(AdminGroupStatsGrpcIdentity.METHOD)
                        .setRequestMarshaller(mock(MethodDescriptor.Marshaller.class))
                        .setResponseMarshaller(mock(MethodDescriptor.Marshaller.class))
                        .build();
        when(call.getMethodDescriptor()).thenReturn(method);
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
