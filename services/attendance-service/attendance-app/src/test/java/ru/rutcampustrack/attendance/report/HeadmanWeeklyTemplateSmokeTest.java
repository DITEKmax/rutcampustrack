package ru.rutcampustrack.attendance.report;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

class HeadmanWeeklyTemplateSmokeTest {
    @Test
    void actualRendererKeepsEveryDayAndLessonType() throws Exception {
        LocalDate start = LocalDate.of(2026, 4, 27);
        List<HeadmanWeeklyReportModel.Day> days = new ArrayList<>();
        List<String> expectedTypes = new ArrayList<>();
        for (int day = 0; day < 6; day++) {
            List<HeadmanWeeklyReportModel.LessonSlot> lessons = new ArrayList<>();
            for (int slot = 1; slot <= 5; slot++) {
                String type = "Тип " + day + "/" + slot;
                expectedTypes.add(type);
                lessons.add(
                        new HeadmanWeeklyReportModel.LessonSlot(
                                day * 5L + slot,
                                slot,
                                day * 5L + slot,
                                "Предмет " + day + "/" + slot,
                                type));
            }
            days.add(new HeadmanWeeklyReportModel.Day(start.plusDays(day), lessons));
        }
        var model =
                new HeadmanWeeklyReportModel(
                        10L,
                        "Группа",
                        "Группа <&>",
                        1L,
                        "Весна",
                        1,
                        start,
                        start.plusDays(6),
                        days,
                        List.of());
        byte[] document = new DocxRenderer().render(List.of(model));
        String xml = DocxRenderer.readDocumentXml(document);
        var rows = ExportTableReader.wordRows(xml);
        assertThat(xml).doesNotContain("${");
        for (int day = 0; day < 6; day++) {
            String date = start.plusDays(day).format(DateTimeFormatter.ofPattern("dd.MM.uuuu"));
            assertThat(rows).anySatisfy(cells -> assertThat(cells).contains(date));
        }
        for (String type : expectedTypes) {
            assertThat(rows).anySatisfy(cells -> assertThat(cells).contains(type));
        }
        assertThat(rows).anySatisfy(cells -> assertThat(cells).contains("Группа <&>"));
    }
}
