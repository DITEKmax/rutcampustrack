package ru.rutcampustrack.mobilebff.botread;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import io.grpc.*;
import io.grpc.netty.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import ru.rutcampustrack.authauthority.grpc.*;
import ru.rutcampustrack.botreads.grpc.*;
import ru.rutcampustrack.mobilebff.teacher.TeacherReadFacade;
import ru.rutcampustrack.shared.security.grpc.*;
import ru.rutcampustrack.shared.web.time.EducationalTime;

import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.KeyManagerFactory;

/** Real loopback TLS exercises the owned receiver and producer, beyond mocked response fixtures. */
class DirectedBotReadTlsTest {
    @Test
    void privateTeacherReadUsesActualTlsAndLiveProducerChecks(@TempDir Path directory)
            throws Exception {
        var material = material(directory);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[32]);
        var identity =
                DirectedBotIdentity.newBuilder()
                        .setTelegramSenderId(777)
                        .setPrivateChatId(777)
                        .setExpectedUserId(71)
                        .setExpectedBindingVersion(3)
                        .setExpectedRolesVersion(5)
                        .build();
        var capability = BotReadCapability.BOT_READ_CAPABILITY_TEACHER_TODAY_READ;
        var action = BotAcademicReadAction.BOT_ACADEMIC_READ_ACTION_TEACHER_TODAY;
        var dependencies = mock(BotReadDependencies.class);
        when(dependencies.check(identity, capability))
                .thenReturn(
                        CheckBotOwnAuthorityResponse.newBuilder()
                                .setUserId(71)
                                .setTelegramBindingVersion(3)
                                .setRolesVersion(5)
                                .setCapability(capability)
                                .build());
        when(dependencies.scope(identity, action))
                .thenReturn(
                        BotAcademicScopeResponse.newBuilder()
                                .setUserId(71)
                                .setTelegramBindingVersion(3)
                                .setRolesVersion(5)
                                .setAction(action)
                                .setEligibilityRevision(5)
                                .setSemesterId(9)
                                .setServerNow("2026-10-17T21:15:00Z")
                                .setEducationalDate("2026-10-18")
                                .setSemesterDateFrom("2026-09-01")
                                .setSemesterDateTo("2026-12-01")
                                .build());
        var time =
                new EducationalTime(
                        Clock.fixed(Instant.parse("2026-10-17T21:15:00Z"), ZoneOffset.UTC));
        var service =
                new DirectedBotReadGrpcService(
                        dependencies, new TeacherReadFacade(null, null, null, null, time), time);
        Server server =
                NettyServerBuilder.forAddress(new InetSocketAddress("127.0.0.1", 0))
                        .sslContext(material.server())
                        .addService(
                                ServerInterceptors.interceptForward(
                                        service, new DirectedBotIdentityInterceptor(token)))
                        .build()
                        .start();
        ManagedChannel channel =
                NettyChannelBuilder.forAddress("127.0.0.1", server.getPort())
                        .overrideAuthority("localhost")
                        .sslContext(
                                GrpcSslContexts.forClient()
                                        .trustManager(material.certificate().toFile())
                                        .build())
                        .build();
        try {
            var credential =
                    new DirectedServiceCredential(
                            new ServicePrincipal("notification-bot"),
                            new ServicePrincipal("mobile-bff"),
                            token);
            var stub =
                    DirectedBotReadServiceGrpc.newBlockingStub(channel)
                            .withDeadlineAfter(3, TimeUnit.SECONDS)
                            .withCallCredentials(
                                    ServiceTokenCallCredentials.forMethod(
                                            credential,
                                            "localhost",
                                            DirectedBotReadServiceGrpc.getGetOwnTeacherTodayMethod()
                                                    .getFullMethodName()));
            var response = stub.getOwnTeacherToday(identity);
            assertThat(response.getEducationalDate()).isEqualTo("2026-10-18");
            assertThat(response.getLessonsList()).isEmpty();
            when(dependencies.check(identity, capability))
                    .thenThrow(
                            Status.FAILED_PRECONDITION
                                    .withDescription("synthetic binding revoked")
                                    .asRuntimeException());
            assertThatThrownBy(() -> stub.getOwnTeacherToday(identity))
                    .isInstanceOf(StatusRuntimeException.class)
                    .satisfies(
                            error ->
                                    assertThat(Status.fromThrowable(error).getCode())
                                            .isEqualTo(Status.Code.FAILED_PRECONDITION));
            assertThatThrownBy(
                            () ->
                                    DirectedBotReadServiceGrpc.newBlockingStub(channel)
                                            .withDeadlineAfter(3, TimeUnit.SECONDS)
                                            .getOwnTeacherToday(identity))
                    .isInstanceOf(StatusRuntimeException.class)
                    .satisfies(
                            error ->
                                    assertThat(Status.fromThrowable(error).getCode())
                                            .isEqualTo(Status.Code.UNAUTHENTICATED));
        } finally {
            channel.shutdownNow();
            server.shutdownNow();
            assertThat(channel.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
            assertThat(server.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    private static Material material(Path directory) throws Exception {
        Path store = directory.resolve("synthetic-server.p12");
        Path certificate = directory.resolve("synthetic-server.crt");
        keytool(
                List.of(
                        "-genkeypair",
                        "-alias",
                        "server",
                        "-keyalg",
                        "RSA",
                        "-keysize",
                        "2048",
                        "-validity",
                        "1",
                        "-dname",
                        "CN=localhost",
                        "-ext",
                        "SAN=dns:localhost",
                        "-keystore",
                        store.toString(),
                        "-storetype",
                        "PKCS12",
                        "-storepass",
                        "test-only-password",
                        "-keypass",
                        "test-only-password",
                        "-noprompt"));
        keytool(
                List.of(
                        "-exportcert",
                        "-alias",
                        "server",
                        "-keystore",
                        store.toString(),
                        "-storepass",
                        "test-only-password",
                        "-rfc",
                        "-file",
                        certificate.toString()));
        KeyStore keys = KeyStore.getInstance("PKCS12");
        try (InputStream input = Files.newInputStream(store)) {
            keys.load(input, "test-only-password".toCharArray());
        }
        var managers = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        managers.init(keys, "test-only-password".toCharArray());
        return new Material(
                GrpcSslContexts.configure(
                                io.netty.handler.ssl.SslContextBuilder.forServer(managers))
                        .build(),
                certificate);
    }

    private static void keytool(List<String> arguments) throws Exception {
        var command = new ArrayList<String>();
        command.add(
                Path.of(
                                System.getProperty("java.home"),
                                "bin",
                                System.getProperty("os.name").toLowerCase().contains("win")
                                        ? "keytool.exe"
                                        : "keytool")
                        .toString());
        command.addAll(arguments);
        var process =
                new ProcessBuilder(command)
                        .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                        .redirectError(ProcessBuilder.Redirect.DISCARD)
                        .start();
        if (!process.waitFor(30, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            process.waitFor(5, TimeUnit.SECONDS);
            throw new AssertionError("synthetic TLS material generation timed out");
        }
        assertThat(process.exitValue()).isZero();
    }

    private record Material(io.netty.handler.ssl.SslContext server, Path certificate) {}
}
