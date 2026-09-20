package ru.rutcampustrack.shared.security.grpc;

import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientInterceptors;
import io.grpc.ManagedChannel;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.Server;
import io.grpc.ServerServiceDefinition;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.ClientCalls;
import io.grpc.stub.MetadataUtils;
import io.grpc.stub.ServerCalls;
import io.grpc.netty.GrpcSslContexts;
import io.grpc.netty.NettyChannelBuilder;
import io.grpc.netty.NettyServerBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.TrustManagerFactory;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Transport gate for service identity. In-process tests cannot prove this
 * requirement, so the positive case uses a local Netty TLS server and client.
 */
class ServiceIdentityTlsTransportTest {

    private static final String METHOD =
            ServiceIdentityServerInterceptor.SCHEDULE_INSTALL_ASSIGNMENT_CLOSE_CAP;
    private static final String REVERSE_METHOD =
            ServiceIdentityServerInterceptor.ACADEMIC_GET_PREPARED_ASSIGNMENT_CLOSE_OPERATION;
    private static final String AUTHORITY = "localhost";
    private static final String TOKEN = token((byte) 0x55);
    private static final String REVERSE_TOKEN = token((byte) 0x56);

    @Test
    void localTlsBindsPrincipalAndReachesTestHandler(@TempDir Path tempDir) throws Exception {
        TlsMaterial material = createTlsMaterial(tempDir);
        AtomicInteger handlerCalls = new AtomicInteger();
        AtomicReference<ServicePrincipal> principal = new AtomicReference<>();
        ServiceIdentityServerInterceptor interceptor = interceptor();
        Server server = tlsServer(material, interceptor, handlerCalls, principal, METHOD).start();
        ManagedChannel channel = null;
        try {
            channel = tlsChannel(material, server.getPort());
            String response = ClientCalls.blockingUnaryCall(
                    channel,
                    descriptor(METHOD),
                    secureCallOptions(callCredentials()),
                    "request");

            assertThat(response).isEqualTo("accepted");
            assertThat(handlerCalls).hasValue(1);
            assertThat(principal).hasValue(ServicePrincipal.ACADEMIC_SERVICE);
        } finally {
            stopResources(channel, server);
        }
    }

    @Test
    void localTlsBindsReverseDirectionPrincipalAndReachesTestHandler(@TempDir Path tempDir) throws Exception {
        TlsMaterial material = createTlsMaterial(tempDir);
        AtomicInteger handlerCalls = new AtomicInteger();
        AtomicReference<ServicePrincipal> principal = new AtomicReference<>();
        ServiceIdentityServerInterceptor interceptor = reverseInterceptor();
        Server server = tlsServer(material, interceptor, handlerCalls, principal, REVERSE_METHOD).start();
        ManagedChannel channel = null;
        try {
            channel = tlsChannel(material, server.getPort());
            String response = ClientCalls.blockingUnaryCall(
                    channel,
                    descriptor(REVERSE_METHOD),
                    secureCallOptions(reverseCallCredentials()),
                    "request");

            assertThat(response).isEqualTo("accepted");
            assertThat(handlerCalls).hasValue(1);
            assertThat(principal).hasValue(ServicePrincipal.SCHEDULE_SERVICE);
        } finally {
            stopResources(channel, server);
        }
    }

    @Test
    void plaintextServerRejectsInjectedTokenBeforeHandler() throws Exception {
        AtomicInteger handlerCalls = new AtomicInteger();
        AtomicReference<ServicePrincipal> principal = new AtomicReference<>();
        Server server = plainServer(interceptor(), handlerCalls, principal).start();
        ManagedChannel channel = null;
        try {
            channel = NettyChannelBuilder.forAddress("localhost", server.getPort())
                    .usePlaintext()
                    .build();
            Metadata injected = new Metadata();
            injected.put(DirectedServiceCredential.TOKEN_METADATA_KEY, TOKEN);
            Channel attached = ClientInterceptors.intercept(
                    channel, MetadataUtils.newAttachHeadersInterceptor(injected));
            assertThatThrownBy(() -> ClientCalls.blockingUnaryCall(
                    attached, descriptor(METHOD), deadlineOptions(), "request"))
                    .isInstanceOf(StatusRuntimeException.class)
                    .satisfies(error -> assertThat(((StatusRuntimeException) error).getStatus().getCode())
                            .isEqualTo(Status.Code.UNAUTHENTICATED));
            assertThat(handlerCalls).hasValue(0);
            assertThat(principal).hasValue(null);
        } finally {
            stopResources(channel, server);
        }
    }

    @Test
    void plaintextClientCallCredentialsFailBeforeSendingToken() throws Exception {
        AtomicInteger handlerCalls = new AtomicInteger();
        AtomicReference<ServicePrincipal> principal = new AtomicReference<>();
        Server server = plainServer(interceptor(), handlerCalls, principal).start();
        ManagedChannel channel = null;
        try {
            final ManagedChannel callChannel = NettyChannelBuilder.forAddress("localhost", server.getPort())
                    .usePlaintext()
                    .overrideAuthority(AUTHORITY)
                    .build();
            channel = callChannel;
            assertThatThrownBy(() -> ClientCalls.blockingUnaryCall(
                    callChannel,
                    descriptor(METHOD),
                    secureCallOptions(callCredentials()),
                    "request"))
                    .isInstanceOf(StatusRuntimeException.class)
                    .satisfies(error -> assertThat(((StatusRuntimeException) error).getStatus().getCode())
                            .isEqualTo(Status.Code.UNAUTHENTICATED));
            assertThat(handlerCalls).hasValue(0);
        } finally {
            stopResources(channel, server);
        }
    }

    @Test
    void untrustedPeerCannotReachHandler(@TempDir Path tempDir) throws Exception {
        TlsMaterial material = createTlsMaterial(tempDir);
        AtomicInteger handlerCalls = new AtomicInteger();
        AtomicReference<ServicePrincipal> principal = new AtomicReference<>();
        Server server = tlsServer(material, interceptor(), handlerCalls, principal, METHOD).start();
        ManagedChannel channel = null;
        try {
            channel = NettyChannelBuilder.forAddress("localhost", server.getPort())
                    .sslContext(GrpcSslContexts.forClient().build())
                    .overrideAuthority(AUTHORITY)
                    .build();
            Metadata injected = new Metadata();
            injected.put(DirectedServiceCredential.TOKEN_METADATA_KEY, TOKEN);
            Channel attached = ClientInterceptors.intercept(
                    channel, MetadataUtils.newAttachHeadersInterceptor(injected));
            assertThatThrownBy(() -> ClientCalls.blockingUnaryCall(
                    attached, descriptor(METHOD), deadlineOptions(), "request"))
                    .isInstanceOf(StatusRuntimeException.class);
            assertThat(handlerCalls).hasValue(0);
            assertThat(principal).hasValue(null);
        } finally {
            stopResources(channel, server);
        }
    }

    @Test
    void wrongHostPeerCannotReachHandler(@TempDir Path tempDir) throws Exception {
        TlsMaterial material = createTlsMaterial(tempDir);
        AtomicInteger handlerCalls = new AtomicInteger();
        AtomicReference<ServicePrincipal> principal = new AtomicReference<>();
        Server server = tlsServer(material, interceptor(), handlerCalls, principal, METHOD).start();
        ManagedChannel channel = null;
        try {
            channel = NettyChannelBuilder.forAddress("localhost", server.getPort())
                    .sslContext(material.clientContext())
                    .overrideAuthority("wrong-host")
                    .build();
            Metadata injected = new Metadata();
            injected.put(DirectedServiceCredential.TOKEN_METADATA_KEY, TOKEN);
            Channel attached = ClientInterceptors.intercept(
                    channel, MetadataUtils.newAttachHeadersInterceptor(injected));
            assertThatThrownBy(() -> ClientCalls.blockingUnaryCall(
                    attached, descriptor(METHOD), deadlineOptions(), "request"))
                    .isInstanceOf(StatusRuntimeException.class);
            assertThat(handlerCalls).hasValue(0);
            assertThat(principal).hasValue(null);
        } finally {
            stopResources(channel, server);
        }
    }

    private static ServiceIdentityServerInterceptor interceptor() {
        return new ServiceIdentityServerInterceptor(
                ServicePrincipal.SCHEDULE_SERVICE,
                java.util.Map.of(METHOD, ServicePrincipal.ACADEMIC_SERVICE),
                List.of(new DirectedServiceCredential(
                        ServicePrincipal.ACADEMIC_SERVICE, ServicePrincipal.SCHEDULE_SERVICE, TOKEN)));
    }

    private static ServiceIdentityServerInterceptor reverseInterceptor() {
        return new ServiceIdentityServerInterceptor(
                ServicePrincipal.ACADEMIC_SERVICE,
                java.util.Map.of(REVERSE_METHOD, ServicePrincipal.SCHEDULE_SERVICE),
                List.of(new DirectedServiceCredential(
                        ServicePrincipal.SCHEDULE_SERVICE, ServicePrincipal.ACADEMIC_SERVICE, REVERSE_TOKEN)));
    }

    private static ServiceTokenCallCredentials callCredentials() {
        return ServiceTokenCallCredentials.forMethod(
                new DirectedServiceCredential(
                        ServicePrincipal.ACADEMIC_SERVICE, ServicePrincipal.SCHEDULE_SERVICE, TOKEN),
                AUTHORITY,
                METHOD);
    }

    private static ServiceTokenCallCredentials reverseCallCredentials() {
        return ServiceTokenCallCredentials.forMethod(
                new DirectedServiceCredential(
                        ServicePrincipal.SCHEDULE_SERVICE, ServicePrincipal.ACADEMIC_SERVICE, REVERSE_TOKEN),
                AUTHORITY,
                REVERSE_METHOD);
    }

    private static CallOptions secureCallOptions(ServiceTokenCallCredentials credentials) {
        return deadlineOptions().withCallCredentials(credentials);
    }

    private static CallOptions deadlineOptions() {
        return CallOptions.DEFAULT.withDeadlineAfter(5, TimeUnit.SECONDS);
    }

    private static Server tlsServer(
            TlsMaterial material,
            ServiceIdentityServerInterceptor interceptor,
            AtomicInteger handlerCalls,
            AtomicReference<ServicePrincipal> principal,
            String method) throws Exception {
        return NettyServerBuilder.forAddress(loopbackEphemeral())
                .sslContext(material.serverContext())
                .intercept(interceptor)
                .addService(testService(handlerCalls, principal, method))
                .build();
    }

    private static Server plainServer(
            ServiceIdentityServerInterceptor interceptor,
            AtomicInteger handlerCalls,
            AtomicReference<ServicePrincipal> principal) {
        return NettyServerBuilder.forAddress(loopbackEphemeral())
                .intercept(interceptor)
                .addService(testService(handlerCalls, principal, METHOD))
                .build();
    }

    private static ServerServiceDefinition testService(
            AtomicInteger handlerCalls,
            AtomicReference<ServicePrincipal> principal,
            String method) {
        return ServerServiceDefinition.builder(MethodDescriptor.extractFullServiceName(method))
                .addMethod(descriptor(method), ServerCalls.asyncUnaryCall((request, observer) -> {
                    handlerCalls.incrementAndGet();
                    principal.set(ServicePrincipal.CONTEXT_KEY.get());
                    observer.onNext("accepted");
                    observer.onCompleted();
                }))
                .build();
    }

    private static ManagedChannel tlsChannel(TlsMaterial material, int port) throws Exception {
        return NettyChannelBuilder.forAddress("localhost", port)
                .sslContext(material.clientContext())
                .overrideAuthority(AUTHORITY)
                .build();
    }

    private static InetSocketAddress loopbackEphemeral() {
        return new InetSocketAddress(InetAddress.getLoopbackAddress(), 0);
    }

    private static MethodDescriptor<String, String> descriptor(String method) {
        return MethodDescriptor.create(
                MethodDescriptor.MethodType.UNARY, method, STRING_MARSHALLER, STRING_MARSHALLER);
    }

    private static String token(byte fill) {
        byte[] bytes = new byte[DirectedServiceCredential.TOKEN_BYTES];
        java.util.Arrays.fill(bytes, fill);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static TlsMaterial createTlsMaterial(Path directory) throws Exception {
        Path keyStore = directory.resolve("server.p12");
        Path certificate = directory.resolve("server.cer");
        Path trustStore = directory.resolve("trust.p12");
        String password = "test-only-password";
        runKeytool(List.of(
                "-genkeypair", "-alias", "server", "-keyalg", "RSA", "-keysize", "2048",
                "-validity", "1", "-dname", "CN=localhost", "-ext", "SAN=dns:localhost",
                "-keystore", keyStore.toString(), "-storetype", "PKCS12", "-storepass", password,
                "-keypass", password, "-noprompt"));
        runKeytool(List.of(
                "-exportcert", "-alias", "server", "-keystore", keyStore.toString(),
                "-storepass", password, "-rfc", "-file", certificate.toString()));
        runKeytool(List.of(
                "-importcert", "-alias", "server", "-file", certificate.toString(),
                "-keystore", trustStore.toString(), "-storetype", "PKCS12", "-storepass", password,
                "-noprompt"));
        return new TlsMaterial(keyStore, trustStore, password);
    }

    private static void runKeytool(List<String> arguments) throws Exception {
        Path executable = Path.of(System.getProperty("java.home"), "bin",
                System.getProperty("os.name", "").toLowerCase().contains("win")
                        ? "keytool.exe" : "keytool");
        List<String> command = new ArrayList<>();
        command.add(executable.toString());
        command.addAll(arguments);
        Process process = new ProcessBuilder(command)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start();
        boolean finished = process.waitFor(30, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            process.waitFor(5, TimeUnit.SECONDS);
            throw new AssertionError("test-only key material generation timed out");
        }
        int exitCode = process.exitValue();
        if (exitCode != 0) {
            throw new AssertionError("test-only key material generation failed with exit " + exitCode);
        }
    }

    private static void stopChannel(ManagedChannel channel) throws InterruptedException {
        channel.shutdownNow();
        assertThat(channel.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
    }

    private static void stopServer(Server server) throws InterruptedException {
        server.shutdownNow();
        assertThat(server.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
    }

    private static void stopResources(ManagedChannel channel, Server server) throws InterruptedException {
        try {
            if (channel != null) {
                stopChannel(channel);
            }
        } finally {
            stopServer(server);
        }
    }

    private record TlsMaterial(Path keyStore, Path trustStore, String password) {
        io.netty.handler.ssl.SslContext serverContext() throws Exception {
            KeyStore store = KeyStore.getInstance("PKCS12");
            try (InputStream input = Files.newInputStream(keyStore)) {
                store.load(input, password.toCharArray());
            }
            KeyManagerFactory keyManagers = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            keyManagers.init(store, password.toCharArray());
            return GrpcSslContexts.configure(
                    io.netty.handler.ssl.SslContextBuilder.forServer(keyManagers)).build();
        }

        io.netty.handler.ssl.SslContext clientContext() throws Exception {
            KeyStore store = KeyStore.getInstance("PKCS12");
            try (InputStream input = Files.newInputStream(trustStore)) {
                store.load(input, password.toCharArray());
            }
            TrustManagerFactory trustManagers = TrustManagerFactory.getInstance(
                    TrustManagerFactory.getDefaultAlgorithm());
            trustManagers.init(store);
            return GrpcSslContexts.configure(
                    GrpcSslContexts.forClient().trustManager(trustManagers))
                    .endpointIdentificationAlgorithm("HTTPS")
                    .build();
        }
    }

    private static final MethodDescriptor.Marshaller<String> STRING_MARSHALLER =
            new MethodDescriptor.Marshaller<>() {
                @Override
                public InputStream stream(String value) {
                    return new ByteArrayInputStream(value.getBytes(StandardCharsets.UTF_8));
                }

                @Override
                public String parse(InputStream stream) {
                    try {
                        return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
                    } catch (IOException error) {
                        throw new IllegalStateException(error);
                    }
                }
            };
}
