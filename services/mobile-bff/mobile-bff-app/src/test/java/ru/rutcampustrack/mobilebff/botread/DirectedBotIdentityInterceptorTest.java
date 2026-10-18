package ru.rutcampustrack.mobilebff.botread;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import io.grpc.*;

import org.junit.jupiter.api.Test;

import ru.rutcampustrack.botreads.grpc.DirectedBotReadServiceGrpc;
import ru.rutcampustrack.shared.security.grpc.*;

import java.util.Base64;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.net.ssl.SSLSession;

class DirectedBotIdentityInterceptorTest {
    private static final String TOKEN =
            Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[32]);

    @Test
    void bothPersonalMethodsRequireExactDirectedTokenAndTlsWithoutUserJwtFallback() {
        var receiver = new DirectedBotIdentityInterceptor(TOKEN);
        for (var method :
                java.util.List.of(
                        DirectedBotReadServiceGrpc.getGetOwnTeacherTodayMethod(),
                        DirectedBotReadServiceGrpc.getGetOwnHeadmanCurrentAttendanceMethod())) {
            assertThat(invoke(receiver, method.getFullMethodName(), true, TOKEN, false)).isTrue();
            assertThat(invoke(receiver, method.getFullMethodName(), false, TOKEN, false)).isFalse();
            assertThat(invoke(receiver, method.getFullMethodName(), true, null, true)).isFalse();
            assertThat(invoke(receiver, method.getFullMethodName(), true, TOKEN, true)).isFalse();
            assertThat(
                            invoke(
                                    new DirectedBotIdentityInterceptor(""),
                                    method.getFullMethodName(),
                                    true,
                                    TOKEN,
                                    false))
                    .isFalse();
        }
    }

    private static boolean invoke(
            DirectedBotIdentityInterceptor receiver,
            String method,
            boolean tls,
            String token,
            boolean additionalToken) {
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
        var headers = new Metadata();
        if (token != null) headers.put(DirectedServiceCredential.TOKEN_METADATA_KEY, token);
        if (additionalToken) {
            headers.put(DirectedServiceCredential.TOKEN_METADATA_KEY, "invalid");
            headers.put(
                    Metadata.Key.of("x-internal-token", Metadata.ASCII_STRING_MARSHALLER),
                    "not-user-authority");
        }
        AtomicBoolean admitted = new AtomicBoolean();
        receiver.interceptCall(
                call,
                headers,
                (acceptedCall, acceptedHeaders) -> {
                    admitted.set(
                            DirectedBotIdentityInterceptor.BOT.equals(
                                    ServicePrincipal.CONTEXT_KEY.get()));
                    return new ServerCall.Listener<>() {};
                });
        return admitted.get();
    }
}
