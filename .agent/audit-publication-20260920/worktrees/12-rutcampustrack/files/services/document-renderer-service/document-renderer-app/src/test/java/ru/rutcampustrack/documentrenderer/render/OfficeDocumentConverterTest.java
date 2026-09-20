package ru.rutcampustrack.documentrenderer.render;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OfficeDocumentConverterTest {

    private static final byte[] DOCX = {1, 2, 3};
    private static final byte[] PDF = {4, 5, 6};
    private static final byte[] PNG = {7, 8, 9};

    @Test
    void convertToPdfReturnsRendererOutputAndCleansTaskDirectory() {
        RecordingProcessRunner runner = new RecordingProcessRunner();
        OfficeDocumentConverter converter = new OfficeDocumentConverter(properties(), runner);

        assertThat(converter.convertToPdf(DOCX)).containsExactly(PDF);

        assertThat(runner.invocations()).singleElement().satisfies(invocation -> {
            assertThat(invocation.command()).containsExactly(
                    "soffice", "--headless", "--nologo", "--nofirststartwizard",
                    "--convert-to", "pdf", "--outdir", invocation.workingDirectory().toString(),
                    invocation.workingDirectory().resolve("input.docx").toString());
            assertThat(invocation.workingDirectory()).doesNotExist();
        });
    }

    @Test
    void convertToPngUsesDeclaredDpiAndCleansBothTaskDirectories() {
        RecordingProcessRunner runner = new RecordingProcessRunner();
        OfficeDocumentConverter converter = new OfficeDocumentConverter(properties(), runner);

        assertThat(converter.convertToPng(DOCX, 300)).containsExactly(PNG);

        assertThat(runner.invocations()).hasSize(2);
        assertThat(runner.invocations().get(1).command())
                .containsSubsequence("pdftoppm", "-png", "-singlefile", "-r", "300");
        assertThat(runner.invocations())
                .extracting(Invocation::workingDirectory)
                .allSatisfy(path -> assertThat(path).doesNotExist());
    }

    @Test
    void convertToPngUsesConfiguredDefaultDpiWhenRequestIsNonPositive() {
        RecordingProcessRunner runner = new RecordingProcessRunner();
        OfficeDocumentConverter converter = new OfficeDocumentConverter(properties(), runner);

        assertThat(converter.convertToPng(DOCX, 0)).containsExactly(PNG);

        assertThat(runner.invocations().get(1).command())
                .containsSubsequence("pdftoppm", "-r", "144");
        assertThat(runner.invocations())
                .extracting(Invocation::workingDirectory)
                .allSatisfy(path -> assertThat(path).doesNotExist());
    }

    @Test
    void nonZeroProcessExitFailsAndCleansTaskDirectory() {
        RecordingProcessRunner runner = new RecordingProcessRunner();
        runner.mode = Mode.NON_ZERO_EXIT;
        OfficeDocumentConverter converter = new OfficeDocumentConverter(properties(), runner);

        assertThatThrownBy(() -> converter.convertToPdf(DOCX))
                .isInstanceOf(DocumentConversionException.class)
                .hasMessageContaining("LibreOffice failed");
        assertThat(runner.invocations())
                .extracting(Invocation::workingDirectory)
                .allSatisfy(path -> assertThat(path).doesNotExist());
    }

    @Test
    void missingPngOutputFailsAndCleansNestedTaskDirectories() {
        RecordingProcessRunner runner = new RecordingProcessRunner();
        runner.mode = Mode.MISSING_PNG_OUTPUT;
        OfficeDocumentConverter converter = new OfficeDocumentConverter(properties(), runner);

        assertThatThrownBy(() -> converter.convertToPng(DOCX, 200))
                .isInstanceOf(DocumentConversionException.class)
                .hasMessageContaining("Poppler failed");
        assertThat(runner.invocations())
                .extracting(Invocation::workingDirectory)
                .allSatisfy(path -> assertThat(path).doesNotExist());
    }

    @Test
    void processRunnerTimeoutIsPropagatedAndCleansTaskDirectory() {
        RecordingProcessRunner runner = new RecordingProcessRunner();
        DocumentConversionException timeout = new DocumentConversionException("renderer timeout");
        runner.failure = timeout;
        OfficeDocumentConverter converter = new OfficeDocumentConverter(properties(), runner);

        assertThatThrownBy(() -> converter.convertToPdf(DOCX)).isSameAs(timeout);
        assertThat(runner.invocations())
                .extracting(Invocation::workingDirectory)
                .allSatisfy(path -> assertThat(path).doesNotExist());
    }

    @Test
    void processRunnerErrorIsPropagatedThroughPngConversionAndCleansTaskDirectory() {
        RecordingProcessRunner runner = new RecordingProcessRunner();
        DocumentConversionException failure = new DocumentConversionException("renderer process error");
        runner.failure = failure;
        OfficeDocumentConverter converter = new OfficeDocumentConverter(properties(), runner);

        assertThatThrownBy(() -> converter.convertToPng(DOCX, 200)).isSameAs(failure);
        assertThat(runner.invocations())
                .extracting(Invocation::workingDirectory)
                .allSatisfy(path -> assertThat(path).doesNotExist());
    }

    private static DocumentRendererProperties properties() {
        DocumentRendererProperties properties = new DocumentRendererProperties();
        properties.setLibreofficeCommand("soffice");
        properties.setPdftoppmCommand("pdftoppm");
        properties.setPngDpi(144);
        properties.setTimeoutSeconds(5);
        return properties;
    }

    private enum Mode {
        SUCCESS,
        NON_ZERO_EXIT,
        MISSING_PNG_OUTPUT
    }

    private record Invocation(List<String> command, Path workingDirectory) {
    }

    private static final class RecordingProcessRunner extends ProcessRunner {
        private final List<Invocation> invocations = new ArrayList<>();
        private Mode mode = Mode.SUCCESS;
        private DocumentConversionException failure;

        @Override
        public ProcessResult run(List<String> command, Path workingDirectory, Duration timeout) {
            invocations.add(new Invocation(List.copyOf(command), workingDirectory));
            if (failure != null) {
                throw failure;
            }
            if (mode == Mode.NON_ZERO_EXIT) {
                return new ProcessResult(1, "synthetic process failure");
            }
            try {
                if (command.contains("soffice")) {
                    Files.write(workingDirectory.resolve("input.pdf"), PDF);
                } else if (mode != Mode.MISSING_PNG_OUTPUT) {
                    Files.write(workingDirectory.resolve("page.png"), PNG);
                }
            } catch (IOException error) {
                throw new AssertionError("fake renderer could not write its output", error);
            }
            return new ProcessResult(0, "synthetic success");
        }

        private List<Invocation> invocations() {
            return List.copyOf(invocations);
        }
    }
}
