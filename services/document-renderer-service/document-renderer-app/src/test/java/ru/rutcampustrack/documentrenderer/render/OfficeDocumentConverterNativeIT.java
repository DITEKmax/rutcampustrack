package ru.rutcampustrack.documentrenderer.render;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.protobuf.ByteString;
import io.grpc.ManagedChannel;
import io.grpc.Metadata;
import io.grpc.Server;
import io.grpc.ServerInterceptors;
import io.grpc.Status;
import io.grpc.netty.NettyChannelBuilder;
import io.grpc.netty.NettyServerBuilder;
import io.grpc.stub.MetadataUtils;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;
import ru.rutcampustrack.documentrenderer.grpc.ConvertDocumentRequest;
import ru.rutcampustrack.documentrenderer.grpc.DocumentRendererGrpcServiceGrpc;
import ru.rutcampustrack.documentrenderer.grpc.DocumentRendererGrpcServiceImpl;
import ru.rutcampustrack.documentrenderer.grpc.GrpcAuthInterceptor;
import ru.rutcampustrack.documentrenderer.grpc.TargetFormat;

/** Requires real LibreOffice Writer and Poppler; absent tools are failures, never skips. */
class OfficeDocumentConverterNativeIT {

    @Test
    void realPdfAndPngPreserveBothPagesAndCleanConversionDirectories(@TempDir Path evidence)
            throws Exception {
        ObservingProcessRunner runner = new ObservingProcessRunner();
        OfficeDocumentConverter converter = new OfficeDocumentConverter(properties(), runner);
        byte[] docx = RendererDocxFixture.docx();

        byte[] pdf = convertPdfViaAuthenticatedRpc(converter, docx);
        retainEvidence("two-pages.docx", docx);
        retainEvidence("two-pages.pdf", pdf);
        Path pdfFile = evidence.resolve("two-pages.pdf");
        Files.write(pdfFile, pdf);
        ProcessRunner parser = new ProcessRunner();
        var text =
                parser.run(
                        List.of("pdftotext", "-layout", pdfFile.toString(), "-"),
                        evidence,
                        Duration.ofSeconds(10));
        assertThat(text.exitCode()).isZero();
        assertThat(text.output()).contains("FIRST PAGE", "SECOND PAGE", "Синтетический отчёт");
        assertThat(text.output().indexOf("FIRST PAGE"))
                .isLessThan(text.output().indexOf("SECOND PAGE"));
        var info =
                parser.run(
                        List.of("pdfinfo", pdfFile.toString()), evidence, Duration.ofSeconds(10));
        assertThat(info.exitCode()).isZero();
        assertThat(info.output()).containsPattern("Pages:\\s+2(?:\\s|$)");

        // Legacy PNG explicitly represents the first page; ZIP is the complete export contract.
        byte[] firstPage = converter.convertToPng(docx, 72);
        retainEvidence("first-page.png", firstPage);
        BufferedImage single = readPng(firstPage);
        assertColorPatch(single, true);
        List<String> names = new ArrayList<>();
        List<BufferedImage> images = new ArrayList<>();
        byte[] archive = converter.convertToPngPagesZip(docx, 72);
        retainEvidence("pages.zip", archive);
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(archive))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                names.add(entry.getName());
                byte[] page = zip.readAllBytes();
                retainEvidence(entry.getName(), page);
                images.add(readPng(page));
            }
        }
        assertThat(names).containsExactly("page-0001.png", "page-0002.png");
        assertThat(images).hasSize(2);
        assertColorPatch(images.get(0), true);
        assertColorPatch(images.get(1), false);
        assertThat(images.get(0).getWidth()).isEqualTo(single.getWidth());
        assertThat(images.get(0).getHeight()).isEqualTo(single.getHeight());
        assertThat(runner.directories)
                .isNotEmpty()
                .allSatisfy(path -> assertThat(path).doesNotExist());
    }

    private static void retainEvidence(String name, byte[] content) throws Exception {
        String configured = System.getenv("RCT_RENDERER_EVIDENCE_DIR");
        if (configured != null && !configured.isBlank()) {
            Path directory = Path.of(configured);
            Files.createDirectories(directory);
            Files.write(directory.resolve(name), content);
        }
    }

    @Test
    void malformedDocumentFailsWithoutLeavingConversionFiles() {
        ObservingProcessRunner runner = new ObservingProcessRunner();
        OfficeDocumentConverter converter = new OfficeDocumentConverter(properties(), runner);
        // A malformed ZIP container cannot be accepted as DOCX.
        assertThatThrownBy(() -> converter.convertToPdf(new byte[] {'P', 'K', 5, 6}))
                .isInstanceOf(DocumentConversionException.class);
        assertThat(runner.directories)
                .as("invalid input never starts an external converter")
                .isEmpty();
    }

    private static byte[] convertPdfViaAuthenticatedRpc(
            OfficeDocumentConverter converter, byte[] docx) throws Exception {
        GrpcAuthInterceptor auth = new GrpcAuthInterceptor();
        ReflectionTestUtils.setField(auth, "expectedSecret", "renderer-synthetic-admission");
        Server server =
                NettyServerBuilder.forPort(0)
                        .addService(
                                ServerInterceptors.intercept(
                                        new DocumentRendererGrpcServiceImpl(converter), auth))
                        .build()
                        .start();
        ManagedChannel channel =
                NettyChannelBuilder.forAddress("127.0.0.1", server.getPort())
                        .usePlaintext()
                        .build();
        try {
            var request =
                    ConvertDocumentRequest.newBuilder()
                            .setDocx(ByteString.copyFrom(docx))
                            .setTargetFormat(TargetFormat.PDF)
                            .build();
            var stub =
                    DocumentRendererGrpcServiceGrpc.newBlockingStub(channel)
                            .withDeadlineAfter(30, TimeUnit.SECONDS);
            assertThatThrownBy(() -> stub.convert(request))
                    .satisfies(
                            error ->
                                    assertThat(Status.fromThrowable(error).getCode())
                                            .isEqualTo(Status.Code.UNAUTHENTICATED));
            Metadata headers = new Metadata();
            headers.put(
                    Metadata.Key.of("x-grpc-secret", Metadata.ASCII_STRING_MARSHALLER),
                    "renderer-synthetic-admission");
            var response =
                    stub.withInterceptors(MetadataUtils.newAttachHeadersInterceptor(headers))
                            .convert(request);
            assertThat(response.getContentType()).isEqualTo("application/pdf");
            assertThat(response.getExtension()).isEqualTo("pdf");
            return response.getContent().toByteArray();
        } finally {
            channel.shutdownNow();
            server.shutdownNow();
            assertThat(channel.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
            assertThat(server.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    private static BufferedImage readPng(byte[] bytes) throws Exception {
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
        assertThat(image).as("decoded PNG").isNotNull();
        // A4 at 72dpi, with rounding tolerance; catches corrupt and wrong-resolution output.
        assertThat(image.getWidth()).isBetween(594, 597);
        assertThat(image.getHeight()).isBetween(841, 844);
        return image;
    }

    private static void assertColorPatch(BufferedImage image, boolean redPage) {
        int red = 0;
        int blue = 0;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int rgb = image.getRGB(x, y);
                int r = (rgb >> 16) & 255;
                int g = (rgb >> 8) & 255;
                int b = rgb & 255;
                if (r > 200 && g < 80 && b < 80) red++;
                if (b > 200 && r < 80 && g < 80) blue++;
            }
        }
        assertThat(redPage ? red : blue)
                .as("page-specific visible color patch")
                .isGreaterThan(1000);
        assertThat(redPage ? blue : red).as("other page must not replace this page").isZero();
    }

    private static DocumentRendererProperties properties() {
        DocumentRendererProperties properties = new DocumentRendererProperties();
        properties.setLibreofficeCommand(
                System.getenv().getOrDefault("LIBREOFFICE_COMMAND", "soffice"));
        properties.setPdftoppmCommand(System.getenv().getOrDefault("PDFTOPPM_COMMAND", "pdftoppm"));
        return properties;
    }

    private static final class ObservingProcessRunner extends ProcessRunner {
        private final List<Path> directories = new ArrayList<>();

        @Override
        public ProcessResult run(List<String> command, Path directory, Duration timeout) {
            directories.add(directory);
            return super.run(command, directory, timeout);
        }
    }
}
