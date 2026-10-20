package ru.rutcampustrack.attendance.report;

import static org.assertj.core.api.Assertions.assertThat;

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

class HeadmanStatsRendererTest {
    @Test
    void largeSelectedStatisticsKeepsContextEveryRowAndSuppliedMetrics() throws Exception {
        Metrics supplied =
                new Metrics(
                        new Metric(7, 28, 26.3),
                        new Metric(9, 28, 32.1),
                        new Metric(2, 28, 7.1),
                        new Metric(19, 28, 67.9));
        Metrics noData =
                new Metrics(
                        new Metric(0, 0, 0),
                        new Metric(0, 0, 0),
                        new Metric(0, 0, 0),
                        new Metric(0, 0, 0));
        List<StudentRow> rows = new ArrayList<>();
        for (int index = 0; index < 120; index++) {
            String name =
                    (index == 0 ? "=SUM(A1:A2) " : "")
                            + String.format(
                                    "Студент-%03d <Синтетический> & Длинная-Фамилия Имя Отчество",
                                    index + 1);
            rows.add(
                    new StudentRow(
                            index + 1,
                            name,
                            index == 119 ? noData : supplied,
                            new TicketCounts(index, index / 2, index / 3),
                            new TicketCounts(3, 2, 1),
                            new Sources(4, 1, 1, 1)));
        }
        String filters =
                "ФИО: содержит «<Синтетический>»; Заявки на «н»: подано: от 0 (включительно)";
        String sorts = "1. ФИО: по возрастанию; 2. Процент «+»: по убыванию";
        Context context =
                new Context(
                        71,
                        "Синтетическая группа <120> & полный состав",
                        83L,
                        "Выбранный активный семестр 2026",
                        LocalDate.of(2026, 2, 2),
                        LocalDate.of(2026, 8, 16),
                        97L,
                        "Длинный выбранный предмет & отдельные типы",
                        List.of("lecture", "lab"),
                        28,
                        Instant.parse("2026-08-16T12:34:00Z"));
        HeadmanStatsExportModel model =
                new HeadmanStatsExportModel(context, supplied, rows.size(), rows, filters, sorts);
        byte[] docx = new HeadmanStatsDocxRenderer().render(model);
        HeadmanStatsTabularRenderer tabular = new HeadmanStatsTabularRenderer();
        byte[] htmlBytes = tabular.renderHtml(model);
        byte[] xlsx = tabular.renderXlsx(model);
        String word = entry(docx, "word/document.xml");
        String sheet = entry(xlsx, "xl/worksheets/sheet1.xml");
        String html = new String(htmlBytes, StandardCharsets.UTF_8);
        var wordRows = ExportTableReader.wordRows(word);
        var htmlRows = ExportTableReader.htmlRows(html);
        var sheetRows = ExportTableReader.sheetRows(sheet);
        var metricHeaders =
                sheetRows.stream()
                        .filter(cells -> cells.contains("+ процент"))
                        .findFirst()
                        .orElseThrow();
        for (StudentRow row : rows) {
            var metrics =
                    List.of(
                            row.metrics().present(),
                            row.metrics().presentOrExcused(),
                            row.metrics().excused(),
                            row.metrics().absent());
            List<String> textMetrics =
                    metrics.stream()
                            .map(
                                    metric ->
                                            metric.denominator() == 0
                                                    ? "— (0/0)"
                                                    : metric.numerator()
                                                            + " / "
                                                            + metric.denominator()
                                                            + " / "
                                                            + String.format(
                                                                    java.util.Locale.ROOT,
                                                                    "%.1f%%",
                                                                    metric.percent()))
                            .toList();
            for (var projected : List.of(wordRows, htmlRows)) {
                assertThat(projected)
                        .anySatisfy(
                                cells -> {
                                    assertThat(cells.getFirst()).isEqualTo(row.displayName());
                                    assertThat(cells.subList(1, cells.size()))
                                            .containsExactlyElementsOf(textMetrics);
                                });
                assertThat(projected)
                        .anySatisfy(
                                cells -> {
                                    assertThat(cells.getFirst()).isEqualTo(row.displayName());
                                    assertThat(cells.subList(1, cells.size()))
                                            .containsExactly(
                                                    Integer.toString(row.lateCheckin().submitted()),
                                                    Integer.toString(row.lateCheckin().approved()),
                                                    Integer.toString(row.lateCheckin().rejected()),
                                                    "3",
                                                    "2",
                                                    "1",
                                                    "4",
                                                    "1",
                                                    "1",
                                                    "1");
                                });
            }
            var values = ExportTableReader.sheetCells(sheet, row.displayName());
            assertThat(values.getFirst().getAttribute("t")).isEqualTo("inlineStr");
            List<String> labels = List.of("+", "+ и у", "у", "н");
            for (int index = 0; index < labels.size(); index++) {
                var metric = metrics.get(index);
                for (String suffix : List.of(" количество", " знаменатель", " процент")) {
                    var cell = values.get(metricHeaders.indexOf(labels.get(index) + suffix));
                    if (suffix.equals(" процент") && metric.denominator() == 0) {
                        assertThat(cell.getTextContent()).isEqualTo("—");
                    } else {
                        assertThat(cell.getAttribute("t")).isIn("n", "");
                        double expected =
                                suffix.equals(" количество")
                                        ? metric.numerator()
                                        : suffix.equals(" знаменатель")
                                                ? metric.denominator()
                                                : metric.percent() / 100.0;
                        assertThat(Double.parseDouble(cell.getTextContent()))
                                .isCloseTo(expected, org.assertj.core.data.Offset.offset(0.000001));
                    }
                }
            }
        }
        assertThat(wordRows)
                .anySatisfy(
                        cells -> {
                            assertThat(cells).contains(rows.getFirst().displayName());
                            assertThat(cells).anyMatch(value -> value.contains("26.3%"));
                        });
        assertThat(htmlRows)
                .anySatisfy(
                        cells -> {
                            assertThat(cells).contains(rows.getFirst().displayName());
                            assertThat(cells).anyMatch(value -> value.contains("26.3%"));
                        });
        assertThat(word).contains("— (0/0)", sorts).doesNotContain("${");
        assertThat(html).contains("— (0/0)", sorts).doesNotContain("<Синтетический>");
        var worksheet = ExportTableReader.xml(sheet);
        assertThat(worksheet.getElementsByTagNameNS("*", "f").getLength()).isZero();
        var numeric = worksheet.getElementsByTagNameNS("*", "c");
        boolean foundSuppliedPercent = false;
        for (int index = 0; index < numeric.getLength(); index++) {
            var cell = (org.w3c.dom.Element) numeric.item(index);
            if (cell.getTextContent().equals("0.263")) {
                assertThat(cell.getAttribute("t")).isIn("", "n");
                assertThat(Double.parseDouble(cell.getTextContent())).isEqualTo(0.263);
                foundSuppliedPercent = true;
            }
        }
        assertThat(foundSuppliedPercent).isTrue();
        Path output = Path.of("build", "headman-stats-renderer");
        Files.createDirectories(output);
        Files.write(output.resolve("large-selected-stats.docx"), docx);
        Files.write(output.resolve("large-selected-stats.html"), htmlBytes);
        Files.write(output.resolve("large-selected-stats.xlsx"), xlsx);
    }

    private static String entry(byte[] archive, String name) throws Exception {
        try (ZipInputStream zip =
                new ZipInputStream(new ByteArrayInputStream(archive), StandardCharsets.UTF_8)) {
            for (var item = zip.getNextEntry(); item != null; item = zip.getNextEntry()) {
                if (item.getName().equals(name))
                    return new String(zip.readAllBytes(), StandardCharsets.UTF_8);
            }
        }
        throw new IllegalStateException("Missing report entry: " + name);
    }
}
