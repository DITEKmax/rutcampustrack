package ru.rutcampustrack.auth.authority;

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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import ru.rutcampustrack.shared.security.grpc.DirectedServiceCredential;
import ru.rutcampustrack.shared.security.grpc.ServicePrincipal;

import java.util.Base64;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.net.ssl.SSLSession;

class AuthBotKeyboardBindingsAuthorizationTest {
    private static final String METHOD =
            "rutcampustrack.authauthority.AuthIdentityGrpcService/ListBotKeyboardBindings";

    @Test
    void onlyTlsBotCanDiscoverKeyboardRecipients() {
        var entered = new AtomicBoolean();
        interceptor()
                .interceptCall(
                        call(METHOD, true),
                        headers(token(0)),
                        (c, h) -> {
                            AuthDirectedIdentityInterceptor.require(
                                    AuthDirectedIdentityInterceptor.BOT);
                            entered.set(true);
                            return new ServerCall.Listener<>() {};
                        });
        assertThat(entered).isTrue();
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "plaintext",
                "missing",
                "bff",
                "academic",
                "attendance",
                "duplicate",
                "shared"
            })
    void otherCallersAndSharedSecretCannotReadBindingCatalogue(String fault) {
        var call = call(METHOD, !fault.equals("plaintext"));
        String credential =
                switch (fault) {
                    case "missing", "shared" -> null;
                    case "bff" -> token(1);
                    case "academic" -> token(2);
                    case "attendance" -> token(3);
                    default -> token(0);
                };
        var headers = headers(credential);
        if (fault.equals("duplicate"))
            headers.put(DirectedServiceCredential.TOKEN_METADATA_KEY, token(0));
        if (fault.equals("shared"))
            headers.put(
                    Metadata.Key.of("x-grpc-secret", Metadata.ASCII_STRING_MARSHALLER),
                    "synthetic");
        var entered = new AtomicBoolean();
        interceptor()
                .interceptCall(
                        call,
                        headers,
                        (c, h) -> {
                            entered.set(true);
                            return new ServerCall.Listener<>() {};
                        });
        assertThat(entered).isFalse();
        verify(call).close(any(Status.class), any(Metadata.class));
    }

    @Test
    void botCatalogueAdmissionDoesNotGrantAcademicCatalogOrDirectInvocation() {
        var call =
                call(
                        "rutcampustrack.authauthority.AuthDataAuthorityService/ReadIdentityCatalogChanges",
                        true);
        var entered = new AtomicBoolean();
        interceptor()
                .interceptCall(
                        call,
                        headers(token(0)),
                        (c, h) -> {
                            entered.set(true);
                            return new ServerCall.Listener<>() {};
                        });
        assertThat(entered).isFalse();
        var service =
                new AuthBotIdentityGrpcService(
                        mock(AuthBotAuthorityQuery.class),
                        mock(AcademicIdentityPresentationClient.class));
        var observer =
                new io.grpc.stub.StreamObserver<
                        ru.rutcampustrack.authauthority.grpc.ListBotKeyboardBindingsResponse>() {
                    public void onNext(
                            ru.rutcampustrack.authauthority.grpc.ListBotKeyboardBindingsResponse
                                    value) {
                        throw new AssertionError("unauthorized result");
                    }

                    public void onError(Throwable error) {
                        assertThat(Status.fromThrowable(error).getCode())
                                .isEqualTo(Status.Code.UNAUTHENTICATED);
                    }

                    public void onCompleted() {
                        throw new AssertionError("unauthorized completion");
                    }
                };
        service.listBotKeyboardBindings(
                ru.rutcampustrack.authauthority.grpc.ListBotKeyboardBindingsRequest
                        .getDefaultInstance(),
                observer);
        assertThatThrownBy(
                        () ->
                                AuthDirectedIdentityInterceptor.require(
                                        AuthDirectedIdentityInterceptor.BOT))
                .isInstanceOf(StatusRuntimeException.class);
        Context.current()
                .withValue(ServicePrincipal.CONTEXT_KEY, AuthDirectedIdentityInterceptor.ACADEMIC)
                .run(
                        () ->
                                assertThatThrownBy(
                                                () ->
                                                        AuthDirectedIdentityInterceptor.require(
                                                                AuthDirectedIdentityInterceptor
                                                                        .BOT))
                                        .isInstanceOf(StatusRuntimeException.class));
    }

    private static AuthDirectedIdentityInterceptor interceptor() {
        return new AuthDirectedIdentityInterceptor(token(0), token(1), token(2), token(3));
    }

    private static String token(int first) {
        byte[] bytes = new byte[32];
        bytes[0] = (byte) first;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static Metadata headers(String token) {
        var headers = new Metadata();
        if (token != null) headers.put(DirectedServiceCredential.TOKEN_METADATA_KEY, token);
        return headers;
    }

    @SuppressWarnings("unchecked")
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
