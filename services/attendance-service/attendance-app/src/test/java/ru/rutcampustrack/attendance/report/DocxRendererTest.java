package ru.rutcampustrack.attendance.report;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DocxRendererTest {

    private final DocxRenderer renderer = new DocxRenderer();

    @Test
    void renderSingleWeekReplacesEveryPlaceholder() throws IOException {
        byte[] docx = renderer.render(List.of(model(LocalDate.of(2026, 4, 27), "Math")));
        writeQaArtifact("headman-weekly-single.docx", docx);

        String documentXml = DocxRenderer.readDocumentXml(docx);

        assertThat(documentXml).doesNotContain("${");
        assertThat(documentXml)
                .contains("UVPV511")
                .contains("Math")
                .contains("LK")
                .contains("Student 01")
                .contains("+");
        assertThat(documentXml)
                .contains("<w:gridCol w:w=\"560\" />")
                .contains("<w:tcW w:w=\"560\" w:type=\"dxa\" />")
                .doesNotContain("<w:tcW w:w=\"340\" w:type=\"dxa\" />");
    }

    @Test
    void renderMultipleWeeksCreatesOneDocumentWithPageBreaks() throws IOException {
        byte[] docx = renderer.render(List.of(
                model(LocalDate.of(2026, 4, 27), "Math"),
                model(LocalDate.of(2026, 5, 11), "Physics")));
        writeQaArtifact("headman-weekly-multi.docx", docx);

        String documentXml = DocxRenderer.readDocumentXml(docx);

        assertThat(documentXml).doesNotContain("${");
        assertThat(documentXml).contains("w:type=\"page\"");
        assertThat(documentXml).contains("Math").contains("Physics");
    }

    private static HeadmanWeeklyReportModel model(LocalDate weekStart, String subjectName) {
        List<HeadmanWeeklyReportModel.Day> days = new ArrayList<>();
        days.add(new HeadmanWeeklyReportModel.Day(weekStart, List.of(
                new HeadmanWeeklyReportModel.LessonSlot(100L, 1, 5L, subjectName, "LK"))));
        for (int i = 1; i < HeadmanWeeklyReportModel.TEMPLATE_DAYS; i++) {
            days.add(new HeadmanWeeklyReportModel.Day(weekStart.plusDays(i), List.of()));
        }

        return new HeadmanWeeklyReportModel(
                10L,
                "UVPV511",
                "Group: UVPV511",
                1L,
                "Spring 2026",
                1,
                weekStart,
                weekStart.plusDays(6),
                days,
                List.of(new HeadmanWeeklyReportModel.Student(
                        1L,
                        "Student 01",
                        List.of(new HeadmanWeeklyReportModel.AttendanceMark(
                                100L,
                                weekStart,
                                1,
                        "+")))));
    }

    private static void writeQaArtifact(String fileName, byte[] docx) throws IOException {
        if (!"true".equalsIgnoreCase(System.getenv("M18_QA_ARTIFACTS"))) {
            return;
        }
        Path dir = Path.of("build", "m18-qa");
        Files.createDirectories(dir);
        Files.write(dir.resolve(fileName), docx);
    }
}
