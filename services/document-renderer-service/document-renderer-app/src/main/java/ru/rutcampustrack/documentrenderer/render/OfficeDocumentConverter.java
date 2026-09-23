package ru.rutcampustrack.documentrenderer.render;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

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

    /**
     * Converts every PDF page produced from the supplied DOCX into an ordered PNG archive.
     * The legacy {@link #convertToPng(byte[], int)} contract remains a single-page PNG.
     */
    public byte[] convertToPngPagesZip(byte[] docx, int requestedDpi) {
        Path tempDir = createTempDir();
        try {
            Path pdf = tempDir.resolve("input.pdf");
            Files.write(pdf, convertToPdf(docx));
            int dpi = requestedDpi > 0 ? requestedDpi : properties.getPngDpi();
            ProcessRunner.ProcessResult result = processRunner.run(List.of(
                    properties.getPdftoppmCommand(),
                    "-png",
                    "-r",
                    String.valueOf(dpi),
                    pdf.toString(),
                    tempDir.resolve("page").toString()), tempDir, properties.timeout());
            if (result.exitCode() != 0) {
                throw new DocumentConversionException(
                        "Poppler failed to convert PDF pages to PNG: " + result.output());
            }

            List<PngPage> pages = findPngPages(tempDir);
            if (pages.isEmpty()) {
                throw new DocumentConversionException("Poppler produced no PNG pages");
            }
            ensureCompletePageSequence(pages);
            return zipPages(pages);
        } catch (IOException ex) {
            throw new DocumentConversionException("Failed to create PNG pages archive", ex);
        } finally {
            deleteRecursively(tempDir);
        }
    }

    private static List<PngPage> findPngPages(Path tempDir) throws IOException {
        try (Stream<Path> paths = Files.list(tempDir)) {
            return paths
                    .filter(Files::isRegularFile)
                    .filter(path -> PNG_PAGE.matcher(path.getFileName().toString()).matches())
                    .map(path -> new PngPage(parsePageNumber(path), path))
                    .sorted(Comparator.comparingInt(PngPage::number))
                    .toList();
        }
    }

    private static int parsePageNumber(Path path) {
        Matcher matcher = PNG_PAGE.matcher(path.getFileName().toString());
        if (!matcher.matches()) {
            throw new DocumentConversionException("Poppler produced an unexpected PNG page filename");
        }
        try {
            int number = Integer.parseInt(matcher.group(1));
            if (number < 1) {
                throw new DocumentConversionException("Poppler produced an invalid PNG page number");
            }
            return number;
        } catch (NumberFormatException ex) {
            throw new DocumentConversionException("Poppler produced an invalid PNG page number", ex);
        }
    }

    private static void ensureCompletePageSequence(List<PngPage> pages) {
        for (int index = 0; index < pages.size(); index++) {
            if (pages.get(index).number() != index + 1) {
                throw new DocumentConversionException("Poppler produced an incomplete PNG page sequence");
            }
        }
    }

    private static byte[] zipPages(List<PngPage> pages) throws IOException {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream();
             ZipOutputStream zip = new ZipOutputStream(output)) {
            for (PngPage page : pages) {
                ZipEntry entry = new ZipEntry(String.format(Locale.ROOT, "page-%04d.png", page.number()));
                entry.setTime(0L);
                zip.putNextEntry(entry);
                Files.copy(page.path(), zip);
                zip.closeEntry();
            }
            zip.finish();
            return output.toByteArray();
        }
    }

    private static final Pattern PNG_PAGE = Pattern.compile("^page-(\\d+)\\.png$");

    private record PngPage(int number, Path path) {
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
