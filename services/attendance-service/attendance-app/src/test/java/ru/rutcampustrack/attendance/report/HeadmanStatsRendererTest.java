package ru.rutcampustrack.attendance.report;

import org.junit.jupiter.api.Test;
import ru.rutcampustrack.attendance.contract.dto.report.HeadmanStatsResponse.Context;
import ru.rutcampustrack.attendance.contract.dto.report.HeadmanStatsResponse.Metric;
import ru.rutcampustrack.attendance.contract.dto.report.HeadmanStatsResponse.Metrics;
import ru.rutcampustrack.attendance.contract.dto.report.HeadmanStatsResponse.Sources;
import ru.rutcampustrack.attendance.contract.dto.report.HeadmanStatsResponse.StudentRow;
import ru.rutcampustrack.attendance.contract.dto.report.HeadmanStatsResponse.TicketCounts;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;

class HeadmanStatsRendererTest {
    @Test
    void largeSelectedStatisticsKeepsContextEveryRowAndSuppliedMetrics() throws Exception {
        Metrics supplied = new Metrics(new Metric(7, 28, 26.3), new Metric(9, 28, 32.1),
                new Metric(2, 28, 7.1), new Metric(19, 28, 67.9));
        Metrics noData = new Metrics(new Metric(0, 0, 0), new Metric(0, 0, 0),
                new Metric(0, 0, 0), new Metric(0, 0, 0));
        List<StudentRow> rows = new ArrayList<>();
        for (int index = 0; index < 120; index++) {
            String name = (index == 0 ? "=SUM(A1:A2) " : "") + String.format("Студент-%03d <Синтетический> & Длинная-Фамилия Имя Отчество", index + 1);
            rows.add(new StudentRow(index + 1, name, index == 119 ? noData : supplied,
                    new TicketCounts(index, index / 2, index / 3), new TicketCounts(3, 2, 1),
                    new Sources(4, 1, 1, 1)));
        }
        String filters = "ФИО: содержит «<Синтетический>»; Заявки на «н»: подано: от 0 (включительно)";
        String sorts = "1. ФИО: по возрастанию; 2. Процент «+»: по убыванию";
        Context context = new Context(71, "Синтетическая группа <120> & полный состав", 83L,
                "Выбранный активный семестр 2026", LocalDate.of(2026, 2, 2), LocalDate.of(2026, 8, 16),
                97L, "Длинный выбранный предмет & отдельные типы", List.of("lecture", "lab"), 28,
                Instant.parse("2026-08-16T12:34:00Z"));
        HeadmanStatsExportModel model = new HeadmanStatsExportModel(context, supplied, rows.size(), rows, filters, sorts);
        byte[] docx = new HeadmanStatsDocxRenderer().render(model);
        HeadmanStatsTabularRenderer tabular = new HeadmanStatsTabularRenderer();
        byte[] htmlBytes = tabular.renderHtml(model);
        byte[] xlsx = tabular.renderXlsx(model);
        String word = entry(docx, "word/document.xml");
        String sheet = entry(xlsx, "xl/worksheets/sheet1.xml");
        String html = new String(htmlBytes, StandardCharsets.UTF_8);
        assertThat(word.split("<w:cantSplit/>", -1)).hasSize(rows.size() * 2 + 1);
        for (StudentRow row : rows) {
            String escaped = row.displayName().replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
            assertThat(word.split(java.util.regex.Pattern.quote(escaped), -1)).hasSize(3); // Both full tables.
            assertThat(html.split(java.util.regex.Pattern.quote(escaped), -1)).hasSize(3);
            assertThat(sheet.split(java.util.regex.Pattern.quote(escaped), -1)).hasSize(2);
        }
        assertThat(word).contains("Фильтры: ФИО: содержит «&lt;Синтетический&gt;»", sorts, "26.3%", "— (0/0)");
        assertThat(html).contains("<dt>Фильтры</dt>", "&lt;Синтетический&gt;", sorts, "26.3%", "— (0/0)")
                .doesNotContain("<Синтетический>");
        assertThat(sheet).contains("<dimension ref=\"A1:W139\"", "ySplit=\"19\"", "topLeftCell=\"A20\"", sorts,
                        "<c r=\"B20\" t=\"n\"><v>7</v>", "<v>0.263</v>", "t=\"inlineStr\"", "=SUM(A1:A2)")
                .doesNotContain("<f>");
        Path output = Path.of("build", "headman-stats-renderer");
        Files.createDirectories(output);
        Files.write(output.resolve("large-selected-stats.docx"), docx);
        Files.write(output.resolve("large-selected-stats.html"), htmlBytes);
        Files.write(output.resolve("large-selected-stats.xlsx"), xlsx);
    }

    private static String entry(byte[] archive, String name) throws Exception {
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(archive), StandardCharsets.UTF_8)) {
            for (var item = zip.getNextEntry(); item != null; item = zip.getNextEntry()) {
                if (item.getName().equals(name)) return new String(zip.readAllBytes(), StandardCharsets.UTF_8);
            }
        }
        throw new IllegalStateException("Missing report entry: " + name);
    }
}
