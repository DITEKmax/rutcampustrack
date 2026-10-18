package ru.rutcampustrack.academic.botread;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.grpc.Attributes;
import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.Grpc;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.ServerCall;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;

import ru.rutcampustrack.academic.grpc.GrpcAuthInterceptor;
import ru.rutcampustrack.academic.grpc.GrpcSecretClientInterceptor;
import ru.rutcampustrack.shared.security.grpc.DirectedServiceCredential;
import ru.rutcampustrack.shared.security.grpc.ServicePrincipal;
import ru.rutcampustrack.shared.security.grpc.ServiceTokenCallCredentials;

import java.util.Base64;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.net.ssl.SSLSession;

class AcademicBotReadTransportTest {
    private static final String METHOD =
            "rutcampustrack.botreads.AcademicOwnBotReadService/ResolveOwnBotReadScope";

    @ParameterizedTest
    @ValueSource(
            strings = {
                "rutcampustrack.authauthority.AuthIdentityGrpcService/CheckAcademicBotOwnAuthority",
                "rutcampustrack.authsecurity.AuthSecurityCapabilityService/AdmitSessionIdentity"
            })
    @SuppressWarnings("unchecked")
    void directedAuthCallStripsLegacySharedSecretMetadata(String fullMethod) {
        var method = (MethodDescriptor<Object, Object>) mock(MethodDescriptor.class);
        when(method.getFullMethodName()).thenReturn(fullMethod);
        var credentials =
                ServiceTokenCallCredentials.forMethod(
                        new DirectedServiceCredential(
                                ServicePrincipal.ACADEMIC_SERVICE,
                                new ServicePrincipal("auth-service"),
                                token(3)),
                        "auth-service",
                        fullMethod);
        var options = CallOptions.DEFAULT.withCallCredentials(credentials);
        var channel = mock(Channel.class);
        var transport = (ClientCall<Object, Object>) mock(ClientCall.class);
        when(channel.newCall(method, options)).thenReturn(transport);
        var interceptor = new GrpcSecretClientInterceptor();
        ReflectionTestUtils.setField(interceptor, "secret", "legacy-test-secret");
        var metadata = new Metadata();
        var key = Metadata.Key.of("x-grpc-secret", Metadata.ASCII_STRING_MARSHALLER);
        metadata.put(key, "caller-provided-legacy-secret");
        interceptor
                .interceptCall(method, options, channel)
                .start(new ClientCall.Listener<>() {}, metadata);
        assertThat(metadata.getAll(key)).isNull();
    }

    @Test
    @SuppressWarnings("unchecked")
    void ordinaryLegacyClientRetainsItsConfiguredSecret() {
        var method = (MethodDescriptor<Object, Object>) mock(MethodDescriptor.class);
        var channel = mock(Channel.class);
        var transport = (ClientCall<Object, Object>) mock(ClientCall.class);
        when(channel.newCall(method, CallOptions.DEFAULT)).thenReturn(transport);
        var interceptor = new GrpcSecretClientInterceptor();
        ReflectionTestUtils.setField(interceptor, "secret", "legacy-test-secret");
        var metadata = new Metadata();
        interceptor
                .interceptCall(method, CallOptions.DEFAULT, channel)
                .start(new ClientCall.Listener<>() {}, metadata);
        assertThat(metadata.get(Metadata.Key.of("x-grpc-secret", Metadata.ASCII_STRING_MARSHALLER)))
                .isEqualTo("legacy-test-secret");
    }

    @Test
    void exactVerifiedMethodUsesTlsServiceIdentityWithoutLegacySharedSecret() {
        var allowed = new AtomicBoolean();
        var receiver = new AcademicBotReadServiceIdentityInterceptor(token(1), token(2));
        var call = call(METHOD, true);
        var legacy = legacy();
        receiver.interceptCall(
                call,
                headers(token(1)),
                (c, h) ->
                        legacy.interceptCall(
                                c,
                                h,
                                (accepted, metadata) -> {
                                    allowed.set(true);
                                    return new ServerCall.Listener<>() {};
                                }));
        assertThat(allowed.get()).isTrue();
    }

    @Test
    void genericSecretCannotAdmitAttendanceCredentialToBffOnlyMethod() {
        var allowed = new AtomicBoolean();
        var headers = headers(token(2));
        headers.put(
                Metadata.Key.of("x-grpc-secret", Metadata.ASCII_STRING_MARSHALLER),
                "legacy-test-secret");
        var legacy = legacy();
        new AcademicBotReadServiceIdentityInterceptor(token(1), token(2))
                .interceptCall(
                        call(METHOD, true),
                        headers,
                        (c, h) ->
                                legacy.interceptCall(
                                        c,
                                        h,
                                        (accepted, metadata) -> {
                                            allowed.set(true);
                                            return new ServerCall.Listener<>() {};
                                        }));
        assertThat(allowed.get()).isFalse();
    }

    @Test
    void plaintextCannotUseAnOtherwiseCorrectDirectedToken() {
        var allowed = new AtomicBoolean();
        new AcademicBotReadServiceIdentityInterceptor(token(1), token(2))
                .interceptCall(
                        call(METHOD, false),
                        headers(token(1)),
                        (c, h) -> {
                            allowed.set(true);
                            return new ServerCall.Listener<>() {};
                        });
        assertThat(allowed.get()).isFalse();
    }

    @Test
    void existingMethodsRetainTheirLegacySecretRequirement() {
        var allowed = new AtomicBoolean();
        var legacy = legacy();
        new AcademicBotReadServiceIdentityInterceptor(token(1), token(2))
                .interceptCall(
                        call("rutcampustrack.academic.AcademicGrpcService/GetGroupMembers", true),
                        headers(token(1)),
                        (c, h) ->
                                legacy.interceptCall(
                                        c,
                                        h,
                                        (accepted, metadata) -> {
                                            allowed.set(true);
                                            return new ServerCall.Listener<>() {};
                                        }));
        assertThat(allowed.get()).isFalse();
    }

    private static GrpcAuthInterceptor legacy() {
        var result = new GrpcAuthInterceptor();
        ReflectionTestUtils.setField(result, "expectedSecret", "legacy-test-secret");
        return result;
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

    private static Metadata headers(String token) {
        var result = new Metadata();
        result.put(DirectedServiceCredential.TOKEN_METADATA_KEY, token);
        return result;
    }

    private static String token(int marker) {
        var bytes = new byte[32];
        bytes[0] = (byte) marker;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
