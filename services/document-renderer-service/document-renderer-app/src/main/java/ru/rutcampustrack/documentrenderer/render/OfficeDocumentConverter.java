package ru.rutcampustrack.documentrenderer.render;

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
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;

@Service
@EnableConfigurationProperties(DocumentRendererProperties.class)
public class OfficeDocumentConverter {

    private final DocumentRendererProperties properties;
    private final ProcessRunner processRunner;

    public OfficeDocumentConverter(
            DocumentRendererProperties properties, ProcessRunner processRunner) {
        this.properties = properties;
        this.processRunner = processRunner;
    }

    public byte[] convertToPdf(byte[] docx) {
        Path tempDir = createTempDir();
        try {
            Path input = tempDir.resolve("input.docx");
            Path output = tempDir.resolve("input.pdf");
            Files.write(input, docx);
            validateDocx(input);
            ProcessRunner.ProcessResult result =
                    processRunner.run(
                            List.of(
                                    properties.getLibreofficeCommand(),
                                    "--headless",
                                    "--nologo",
                                    "--nofirststartwizard",
                                    "--convert-to",
                                    "pdf",
                                    "--outdir",
                                    tempDir.toString(),
                                    input.toString()),
                            tempDir,
                            properties.timeout());
            if (result.exitCode() != 0 || !Files.exists(output)) {
                throw new DocumentConversionException(
                        "LibreOffice failed to convert DOCX to PDF: " + result.output());
            }
            return Files.readAllBytes(output);
        } catch (IOException ex) {
            throw new DocumentConversionException("Failed to convert DOCX to PDF", ex);
        } finally {
            deleteRecursively(tempDir);
        }
    }

    private static void validateDocx(Path input) {
        // LibreOffice auto-detects plain text even when the filename ends with .docx.
        // Check the real package/XML identity so malformed input never starts that process.
        XMLInputFactory xml = XMLInputFactory.newFactory();
        xml.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        xml.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        try (ZipFile zip = new ZipFile(input.toFile())) {
            validatePart(
                    zip,
                    xml,
                    "[Content_Types].xml",
                    "Types",
                    "http://schemas.openxmlformats.org/package/2006/content-types");
            validatePart(
                    zip,
                    xml,
                    "_rels/.rels",
                    "Relationships",
                    "http://schemas.openxmlformats.org/package/2006/relationships");
            validatePart(
                    zip,
                    xml,
                    "word/document.xml",
                    "document",
                    "http://schemas.openxmlformats.org/wordprocessingml/2006/main");
        } catch (IOException | XMLStreamException ex) {
            throw new InvalidDocumentException(ex);
        }
    }

    private static void validatePart(
            ZipFile zip, XMLInputFactory xml, String name, String root, String namespace)
            throws IOException, XMLStreamException {
        ZipEntry part = zip.getEntry(name);
        if (part == null || part.isDirectory()) {
            throw new InvalidDocumentException();
        }
        try (var bytes = zip.getInputStream(part)) {
            XMLStreamReader reader = xml.createXMLStreamReader(bytes);
            try {
                boolean rootSeen = false;
                boolean documentIdentity = "word/document.xml".equals(name);
                while (reader.hasNext()) {
                    if (Thread.currentThread().isInterrupted()) {
                        throw new DocumentConversionException("Document conversion interrupted");
                    }
                    int event = reader.next();
                    if (event == XMLStreamConstants.DTD
                            || event == XMLStreamConstants.ENTITY_REFERENCE) {
                        throw new InvalidDocumentException();
                    }
                    if (event == XMLStreamConstants.START_ELEMENT && !rootSeen) {
                        if (!root.equals(reader.getLocalName())
                                || !namespace.equals(reader.getNamespaceURI())) {
                            throw new InvalidDocumentException();
                        }
                        rootSeen = true;
                    }
                    if (event == XMLStreamConstants.START_ELEMENT
                            && namespace.equals(reader.getNamespaceURI())) {
                        if ("[Content_Types].xml".equals(name)
                                && "Override".equals(reader.getLocalName())
                                && "/word/document.xml"
                                        .equals(reader.getAttributeValue(null, "PartName"))
                                && "application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"
                                        .equals(reader.getAttributeValue(null, "ContentType"))) {
                            documentIdentity = true;
                        }
                        if ("_rels/.rels".equals(name)
                                && "Relationship".equals(reader.getLocalName())
                                && "http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument"
                                        .equals(reader.getAttributeValue(null, "Type"))
                                && "word/document.xml"
                                        .equals(reader.getAttributeValue(null, "Target"))
                                && !"External"
                                        .equalsIgnoreCase(
                                                reader.getAttributeValue(null, "TargetMode"))) {
                            documentIdentity = true;
                        }
                    }
                }
                if (!rootSeen || !documentIdentity) {
                    throw new InvalidDocumentException();
                }
            } finally {
                reader.close();
            }
        }
    }

    public byte[] convertToPng(byte[] docx, int requestedDpi) {
        Path tempDir = createTempDir();
        try {
            Path pdf = tempDir.resolve("input.pdf");
            Files.write(pdf, convertToPdf(docx));
            int dpi = requestedDpi > 0 ? requestedDpi : properties.getPngDpi();
            ProcessRunner.ProcessResult result =
                    processRunner.run(
                            List.of(
                                    properties.getPdftoppmCommand(),
                                    "-png",
                                    "-singlefile",
                                    "-r",
                                    String.valueOf(dpi),
                                    pdf.toString(),
                                    tempDir.resolve("page").toString()),
                            tempDir,
                            properties.timeout());
            Path png = tempDir.resolve("page.png");
            if (result.exitCode() != 0 || !Files.exists(png)) {
                throw new DocumentConversionException(
                        "Poppler failed to convert PDF to PNG: " + result.output());
            }
            return Files.readAllBytes(png);
        } catch (IOException ex) {
            throw new DocumentConversionException("Failed to convert DOCX to PNG", ex);
        } finally {
            deleteRecursively(tempDir);
        }
    }

    /**
     * Converts every PDF page produced from the supplied DOCX into an ordered PNG archive. The
     * legacy {@link #convertToPng(byte[], int)} contract remains a single-page PNG.
     */
    public byte[] convertToPngPagesZip(byte[] docx, int requestedDpi) {
        Path tempDir = createTempDir();
        try {
            Path pdf = tempDir.resolve("input.pdf");
            Files.write(pdf, convertToPdf(docx));
            int dpi = requestedDpi > 0 ? requestedDpi : properties.getPngDpi();
            ProcessRunner.ProcessResult result =
                    processRunner.run(
                            List.of(
                                    properties.getPdftoppmCommand(),
                                    "-png",
                                    "-r",
                                    String.valueOf(dpi),
                                    pdf.toString(),
                                    tempDir.resolve("page").toString()),
                            tempDir,
                            properties.timeout());
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
            return paths.filter(Files::isRegularFile)
                    .filter(path -> PNG_PAGE.matcher(path.getFileName().toString()).matches())
                    .map(path -> new PngPage(parsePageNumber(path), path))
                    .sorted(Comparator.comparingInt(PngPage::number))
                    .toList();
        }
    }

    private static int parsePageNumber(Path path) {
        Matcher matcher = PNG_PAGE.matcher(path.getFileName().toString());
        if (!matcher.matches()) {
            throw new DocumentConversionException(
                    "Poppler produced an unexpected PNG page filename");
        }
        try {
            int number = Integer.parseInt(matcher.group(1));
            if (number < 1) {
                throw new DocumentConversionException(
                        "Poppler produced an invalid PNG page number");
            }
            return number;
        } catch (NumberFormatException ex) {
            throw new DocumentConversionException(
                    "Poppler produced an invalid PNG page number", ex);
        }
    }

    private static void ensureCompletePageSequence(List<PngPage> pages) {
        for (int index = 0; index < pages.size(); index++) {
            if (pages.get(index).number() != index + 1) {
                throw new DocumentConversionException(
                        "Poppler produced an incomplete PNG page sequence");
            }
        }
    }

    private static byte[] zipPages(List<PngPage> pages) throws IOException {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream();
                ZipOutputStream zip = new ZipOutputStream(output)) {
            for (PngPage page : pages) {
                ZipEntry entry =
                        new ZipEntry(String.format(Locale.ROOT, "page-%04d.png", page.number()));
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

    private record PngPage(int number, Path path) {}

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
            paths.sorted(Comparator.reverseOrder())
                    .forEach(
                            path -> {
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
