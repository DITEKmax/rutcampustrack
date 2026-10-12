package ru.rutcampustrack.documentrenderer.render;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class OfficeDocumentInputValidationTest {

    @ParameterizedTest
    @MethodSource("invalidPackages")
    void rejectsNonDocxPackageBeforeStartingNativeProcess(byte[] input) {
        ProcessRunner mustNotRun =
                new ProcessRunner() {
                    @Override
                    public ProcessResult run(
                            List<String> command, Path directory, Duration timeout) {
                        throw new AssertionError("Invalid DOCX must never start a native process");
                    }
                };

        assertThatThrownBy(
                        () ->
                                new OfficeDocumentConverter(
                                                new DocumentRendererProperties(), mustNotRun)
                                        .convertToPdf(input))
                .isInstanceOf(InvalidDocumentException.class)
                .hasMessage("Input must be a valid DOCX document");
    }

    private static Stream<byte[]> invalidPackages() throws Exception {
        return Stream.of(
                "plain text is not a DOCX".getBytes(StandardCharsets.UTF_8),
                changedPart("word/document.xml", null),
                changedPart("_rels/.rels", null),
                changedPart("word/document.xml", "<invalid"),
                changedPart(
                        "word/document.xml",
                        """
                        <!DOCTYPE document [<!ENTITY xxe SYSTEM "file:///renderer-fixture-do-not-read">]>
                        <document xmlns="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
                        &xxe;</document>
                        """),
                changedPart("word/document.xml", "<document xmlns=\"wrong-namespace\"/>"),
                changedPart(
                        "[Content_Types].xml",
                        """
                        <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
                        <Override PartName="/word/document.xml" ContentType="application/vnd.ms-word.document.macroEnabled.main+xml"/>
                        </Types>
                        """),
                changedPart(
                        "_rels/.rels",
                        """
                        <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                        <Relationship Id="rId1" Target="word/document.xml" TargetMode="External"
                        Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument"/>
                        </Relationships>
                        """));
    }

    private static byte[] changedPart(String changed, String replacement) throws Exception {
        try (ZipInputStream input =
                        new ZipInputStream(new ByteArrayInputStream(RendererDocxFixture.docx()));
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                ZipOutputStream output = new ZipOutputStream(bytes)) {
            ZipEntry entry;
            while ((entry = input.getNextEntry()) != null) {
                byte[] content = input.readAllBytes();
                if (entry.getName().equals(changed)) {
                    if (replacement == null) continue;
                    content = replacement.getBytes(StandardCharsets.UTF_8);
                }
                output.putNextEntry(new ZipEntry(entry.getName()));
                output.write(content);
                output.closeEntry();
            }
            output.finish();
            return bytes.toByteArray();
        }
    }
}
