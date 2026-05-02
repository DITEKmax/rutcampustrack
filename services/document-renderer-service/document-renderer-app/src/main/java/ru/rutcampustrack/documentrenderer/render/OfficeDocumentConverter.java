package ru.rutcampustrack.documentrenderer.render;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

@Service
@EnableConfigurationProperties(DocumentRendererProperties.class)
public class OfficeDocumentConverter {

    private final DocumentRendererProperties properties;
    private final ProcessRunner processRunner;

    public OfficeDocumentConverter(DocumentRendererProperties properties, ProcessRunner processRunner) {
        this.properties = properties;
        this.processRunner = processRunner;
    }

    public byte[] convertToPdf(byte[] docx) {
        Path tempDir = createTempDir();
        try {
            Path input = tempDir.resolve("input.docx");
            Path output = tempDir.resolve("input.pdf");
            Files.write(input, docx);
            ProcessRunner.ProcessResult result = processRunner.run(List.of(
                    properties.getLibreofficeCommand(),
                    "--headless",
                    "--nologo",
                    "--nofirststartwizard",
                    "--convert-to",
                    "pdf",
                    "--outdir",
                    tempDir.toString(),
                    input.toString()), tempDir, properties.timeout());
            if (result.exitCode() != 0 || !Files.exists(output)) {
                throw new DocumentConversionException("LibreOffice failed to convert DOCX to PDF: " + result.output());
            }
            return Files.readAllBytes(output);
        } catch (IOException ex) {
            throw new DocumentConversionException("Failed to convert DOCX to PDF", ex);
        } finally {
            deleteRecursively(tempDir);
        }
    }

    public byte[] convertToPng(byte[] docx, int requestedDpi) {
        Path tempDir = createTempDir();
        try {
            Path pdf = tempDir.resolve("input.pdf");
            Files.write(pdf, convertToPdf(docx));
            int dpi = requestedDpi > 0 ? requestedDpi : properties.getPngDpi();
            ProcessRunner.ProcessResult result = processRunner.run(List.of(
                    properties.getPdftoppmCommand(),
                    "-png",
                    "-singlefile",
                    "-r",
                    String.valueOf(dpi),
                    pdf.toString(),
                    tempDir.resolve("page").toString()), tempDir, properties.timeout());
            Path png = tempDir.resolve("page.png");
            if (result.exitCode() != 0 || !Files.exists(png)) {
                throw new DocumentConversionException("Poppler failed to convert PDF to PNG: " + result.output());
            }
            return Files.readAllBytes(png);
        } catch (IOException ex) {
            throw new DocumentConversionException("Failed to convert DOCX to PNG", ex);
        } finally {
            deleteRecursively(tempDir);
        }
    }

    private static Path createTempDir() {
        try {
            return Files.createTempDirectory("rct-renderer-" + UUID.randomUUID());
        } catch (IOException ex) {
            throw new DocumentConversionException("Failed to create renderer temp directory", ex);
        }
    }

    private static void deleteRecursively(Path root) {
        if (root == null || !Files.exists(root)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(root)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                    // Best-effort cleanup for temp files.
                }
            });
        } catch (IOException ignored) {
            // Best-effort cleanup for temp files.
        }
    }
}
