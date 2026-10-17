package ru.rutcampustrack.academic.botcredentials;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.grpc.Attributes;
import io.grpc.Context;
import io.grpc.Grpc;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.ServerCall;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.net.ssl.SSLSession;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import ru.rutcampustrack.shared.security.grpc.DirectedServiceCredential;
import ru.rutcampustrack.shared.security.grpc.ServicePrincipal;

class BotCredentialServiceIdentityInterceptorTest {

    @ParameterizedTest
    @ValueSource(strings = {"GetOwnBotInitialCredentials", "ManageOwnBotCredentialMessages"})
    void onlyVerifiedTlsBotCanEnterDomain(String method) {
        var called = new AtomicBoolean();
        new BotCredentialServiceIdentityInterceptor(token())
                .interceptCall(
                        call(method, true),
                        headers(token()),
                        (call, headers) -> {
                            BotCredentialServiceIdentityInterceptor.requireBot();
                            called.set(true);
                            return new ServerCall.Listener<>() {};
                        });
        assertThat(called.get()).isTrue();
        assertThat(ServicePrincipal.CONTEXT_KEY.get()).isNull();
    }

    @ParameterizedTest
    @ValueSource(
            strings = {"plaintext", "missing", "duplicate", "malformed", "wrong", "unconfigured"})
    void sharedGrpcSecretCannotSubstituteDedicatedTlsIdentity(String defect) {
        var call = call("GetOwnBotInitialCredentials", !defect.equals("plaintext"));
        var headers =
                headers(
                        defect.equals("missing")
                                ? null
                                : defect.equals("malformed")
                                        ? "invalid"
                                        : defect.equals("wrong") ? otherToken() : token());
        headers.put(
                Metadata.Key.of("x-grpc-secret", Metadata.ASCII_STRING_MARSHALLER),
                "synthetic-shared");
        if (defect.equals("duplicate"))
            headers.put(DirectedServiceCredential.TOKEN_METADATA_KEY, token());
        var called = new AtomicBoolean();
        new BotCredentialServiceIdentityInterceptor(defect.equals("unconfigured") ? "" : token())
                .interceptCall(
                        call,
                        headers,
                        (next, metadata) -> {
                            called.set(true);
                            return new ServerCall.Listener<>() {};
                        });
        assertThat(called.get()).isFalse();
        verify(call).close(any(Status.class), any(Metadata.class));
    }

    @Test
    void directDomainInvocationRequiresVerifiedBotPrincipal() {
        assertThatThrownBy(BotCredentialServiceIdentityInterceptor::requireBot)
                .isInstanceOf(StatusRuntimeException.class);
        Context.current()
                .withValue(ServicePrincipal.CONTEXT_KEY, ServicePrincipal.SCHEDULE_SERVICE)
                .run(
                        () ->
                                assertThatThrownBy(
                                                BotCredentialServiceIdentityInterceptor::requireBot)
                                        .isInstanceOf(StatusRuntimeException.class));
    }

    @Test
    void ordinaryProfileRetainsExistingAdmission() {
        var called = new AtomicBoolean();
        new BotCredentialServiceIdentityInterceptor("")
                .interceptCall(
                        call("GetUserByTelegramId", false),
                        new Metadata(),
                        (call, headers) -> {
                            called.set(true);
                            return new ServerCall.Listener<>() {};
                        });
        assertThat(called.get()).isTrue();
    }

    private static ServerCall<Object, Object> call(String method, boolean tls) {
        ServerCall<Object, Object> call = mock(ServerCall.class);
        MethodDescriptor<Object, Object> descriptor = mock(MethodDescriptor.class);
        when(descriptor.getFullMethodName())
                .thenReturn("rutcampustrack.academic.AcademicGrpcService/" + method);
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

    private static Metadata headers(String token) {
        var headers = new Metadata();
        if (token != null) headers.put(DirectedServiceCredential.TOKEN_METADATA_KEY, token);
        return headers;
    }

    private static String token() {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[32]);
    }

    private static String otherToken() {
        byte[] bytes = new byte[32];
        bytes[0] = 1;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
