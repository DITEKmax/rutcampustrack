package ru.rutcampustrack.attendance.report;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import io.grpc.*;
import io.grpc.netty.*;
import io.grpc.stub.MetadataUtils;
import io.jsonwebtoken.Jwts;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import ru.rutcampustrack.attendance.grpc.*;
import ru.rutcampustrack.attendance.report.planned.*;
import ru.rutcampustrack.attendance.security.RequestContext;
import ru.rutcampustrack.reportrendering.grpc.*;
import ru.rutcampustrack.shared.security.*;
import ru.rutcampustrack.shared.security.grpc.*;

import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.file.*;
import java.security.KeyStore;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.KeyManagerFactory;

/** Actual private TLS receiver, signed full-plan authority and existing native renderer. */
class PlannedReportTlsTest {
    @Test
    void reportOnlyTlsCapabilityProducesActualRosterHtmlAndRejectsTampering(@TempDir Path directory)
            throws Exception {
        var material = material(directory);
        var keys = new InternalJwtTestFactory();
        var publicKeys = mock(PublicKeyProvider.class);
        when(publicKeys.getPublicKey()).thenReturn(keys.publicKey());
        var validator =
                new InternalJwtValidator(
                        publicKeys,
                        new InternalJwtProperties(null, 60, 0, false, null, null, null));
        var liveAcademic = mock(AcademicGrpcClient.class);
        var liveCaller = mock(RequestContext.class);
        var converter = mock(DocumentRendererGrpcClient.class);
        var composition =
                new HeadmanGroupCompositionService(
                        liveCaller,
                        liveAcademic,
                        converter,
                        new HeadmanGroupCompositionRenderer(),
                        java.time.Clock.systemUTC());
        var renderer = new PlannedReportRenderer(null, null, null, composition, null, converter);
        var source = ReportPlanFixtures.plan("HEADMAN_GROUP_COMPOSITION", "html");
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[32]);
        Server server =
                NettyServerBuilder.forAddress(new InetSocketAddress("127.0.0.1", 0))
                        .sslContext(material.server())
                        .maxInboundMessageSize(AdmittedReportPlan.MAX_BYTES + 1024)
                        .addService(
                                ServerInterceptors.interceptForward(
                                        new PlannedReportGrpcService(validator, renderer),
                                        new PlannedReportIdentityInterceptor(token)))
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
            Instant expiry = Instant.now().plusSeconds(45);
            var actor = ReportPlanFixtures.actor(false, true);
            String cap =
                    Jwts.builder()
                            .header()
                            .keyId(InternalJwtTestFactory.KEY_ID)
                            .and()
                            .subject(Long.toString(actor.userId()))
                            .issuer(InternalJwtTestFactory.ISSUER)
                            .audience()
                            .add(InternalJwtTestFactory.AUDIENCE)
                            .and()
                            .claim("token_use", "academic_report")
                            .claim("sid", actor.sessionId().toString())
                            .claim("sv", Long.toString(actor.sessionVersion()))
                            .claim("rv", Long.toString(actor.rolesVersion()))
                            .claim("role", actor.role())
                            .claim("status", actor.status())
                            .claim("group_id", Long.toString(actor.groupId()))
                            .claim("is_headman", actor.isHeadman())
                            .claim("readOnly", actor.readOnly())
                            .claim("report_kind", source.getReportKind())
                            .claim("report_hash", AdmittedReportPlan.digest(source))
                            .claim("report_job", source.getJobId())
                            .claim("report_lease", source.getLeaseId())
                            .claim("report_not_after", expiry.getEpochSecond())
                            .issuedAt(Date.from(Instant.now().minusSeconds(2)))
                            .expiration(Date.from(expiry))
                            .signWith(keys.keyPair().getPrivate(), Jwts.SIG.RS256)
                            .compact();
            Metadata metadata = new Metadata();
            metadata.put(
                    Metadata.Key.of("x-report-capability", Metadata.ASCII_STRING_MARSHALLER), cap);
            var directed =
                    new DirectedServiceCredential(
                            ServicePrincipal.ACADEMIC_SERVICE,
                            new ServicePrincipal("attendance-service"),
                            token);
            var stub =
                    ReportRenderingServiceGrpc.newBlockingStub(channel)
                            .withDeadlineAfter(5, TimeUnit.SECONDS)
                            .withCallCredentials(
                                    ServiceTokenCallCredentials.forMethod(
                                            directed,
                                            "localhost",
                                            ReportRenderingServiceGrpc.getRenderReportMethod()
                                                    .getFullMethodName()))
                            .withInterceptors(MetadataUtils.newAttachHeadersInterceptor(metadata));
            var result =
                    stub.renderReport(RenderReportRequest.newBuilder().setPlan(source).build());
            assertThat(result.getBody().toStringUtf8())
                    .contains("УИТ-311", "Студент 1", "Студент 2", "synthetic1", "Староста");
            assertThat(result.getFilename()).endsWith(".html");
            var tampered =
                    source.toBuilder()
                            .setGroupComposition(
                                    source.getGroupComposition().toBuilder()
                                            .setGroupName("foreign snapshot"))
                            .build();
            assertThatThrownBy(
                            () ->
                                    stub.renderReport(
                                            RenderReportRequest.newBuilder()
                                                    .setPlan(tampered)
                                                    .build()))
                    .isInstanceOf(StatusRuntimeException.class)
                    .satisfies(
                            e ->
                                    assertThat(Status.fromThrowable(e).getCode())
                                            .isEqualTo(Status.Code.UNAUTHENTICATED));
            assertThatThrownBy(
                            () ->
                                    ReportRenderingServiceGrpc.newBlockingStub(channel)
                                            .withDeadlineAfter(5, TimeUnit.SECONDS)
                                            .renderReport(
                                                    RenderReportRequest.newBuilder()
                                                            .setPlan(source)
                                                            .build()))
                    .isInstanceOf(StatusRuntimeException.class)
                    .satisfies(
                            e ->
                                    assertThat(Status.fromThrowable(e).getCode())
                                            .isEqualTo(Status.Code.UNAUTHENTICATED));
            verifyNoInteractions(liveAcademic, liveCaller, converter);
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
