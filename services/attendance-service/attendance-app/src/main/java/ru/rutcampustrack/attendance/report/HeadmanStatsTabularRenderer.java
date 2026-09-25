package ru.rutcampustrack.attendance.report;

import org.springframework.stereotype.Component;
import ru.rutcampustrack.attendance.contract.dto.report.HeadmanStatsResponse.Context;
import ru.rutcampustrack.attendance.contract.dto.report.HeadmanStatsResponse.Metric;
import ru.rutcampustrack.attendance.contract.dto.report.HeadmanStatsResponse.Metrics;
import ru.rutcampustrack.attendance.contract.dto.report.HeadmanStatsResponse.StudentRow;
import ru.rutcampustrack.attendance.exception.ReportExportUnavailableException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Escaped HTML and numeric-cell XLSX layouts for the PK-113 statistics block. */
@Component
public final class HeadmanStatsTabularRenderer {
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.uuuu");
    private static final DateTimeFormatter GENERATED_AT = DateTimeFormatter
            .ofPattern("dd.MM.uuuu HH:mm 'UTC'").withZone(ZoneOffset.UTC);
    private static final List<String> METRIC_LABELS = List.of("+", "+ и у", "у", "н");

    public byte[] renderHtml(HeadmanStatsExportModel model) {
        Context context = model.context();
        StringBuilder html = new StringBuilder(24_000);
        html.append("<!doctype html><html lang=\"ru\"><head><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">")
                .append("<title>Статистика группы</title><style>body{font-family:Arial,sans-serif;color:#202124;margin:1.5rem}h1,h2{margin-block:1rem .5rem}dl{display:grid;grid-template-columns:max-content 1fr;gap:.35rem 1rem}dt{font-weight:700}dd{margin:0}.table-wrap{overflow-x:auto;margin-top:1rem;margin-bottom:1.5rem}table{border-collapse:collapse;min-width:54rem;width:100%}th,td{border:1px solid #c9ced3;padding:.45rem;text-align:left;vertical-align:top}thead{background:#eef1f3}tbody tr:nth-child(even){background:#f8f9fa}.metric{text-align:center;white-space:nowrap}</style></head><body>")
                .append("<h1>Статистика посещаемости группы</h1><dl>")
                .append(contextItem("Группа", context.groupName()))
                .append(contextItem("Активный семестр", semester(context)))
                .append(contextItem("Разрез", context.subjectId() == null ? "Вся группа" : context.subjectName()))
                .append(contextItem("Типы занятий", context.lessonTypes().isEmpty()
                        ? "Все" : String.join(", ", context.lessonTypes())))
                .append(contextItem("Завершённых занятий учтено", Integer.toString(context.lessonsCount())))
                .append(contextItem("Студентов после фильтра", Integer.toString(model.filteredStudents())))
                .append(contextItem("Сформировано", GENERATED_AT.format(context.generatedAt())))
                .append("</dl><h2>Итоги по выбранному набору студентов</h2>")
                .append(summaryHtml(model.summary()))
                .append("<h2>Посещаемость по студентам</h2>").append(attendanceHtml(model.rows()))
                .append("<h2>Заявки и способ получения отметки «+»</h2>").append(requestsHtml(model.rows()))
                .append("</body></html>");
        return html.toString().getBytes(StandardCharsets.UTF_8);
    }

    public byte[] renderXlsx(HeadmanStatsExportModel model) {
        int headerRow = 17;
        int firstDataRow = headerRow + 1;
        int lastRow = Math.max(headerRow, firstDataRow + model.rows().size() - 1);
        Map<String, String> entries = new LinkedHashMap<>();
        entries.put("[Content_Types].xml", "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">"
                + "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>"
                + "<Default Extension=\"xml\" ContentType=\"application/xml\"/>"
                + "<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>"
                + "<Override PartName=\"/xl/worksheets/sheet1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>"
                + "<Override PartName=\"/xl/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml\"/>"
                + "</Types>");
        entries.put("_rels/.rels", "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
                + "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/>"
                + "</Relationships>");
        entries.put("xl/workbook.xml", "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\"><sheets>"
                + "<sheet name=\"Статистика\" sheetId=\"1\" r:id=\"rId1\"/></sheets></workbook>");
        entries.put("xl/_rels/workbook.xml.rels", "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
                + "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet1.xml\"/>"
                + "<Relationship Id=\"rId2\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles\" Target=\"styles.xml\"/>"
                + "</Relationships>");
        entries.put("xl/styles.xml", stylesXml());
        entries.put("xl/worksheets/sheet1.xml", sheetXml(model, headerRow, firstDataRow, lastRow));
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
             ZipOutputStream zip = new ZipOutputStream(bytes, StandardCharsets.UTF_8)) {
            for (Map.Entry<String, String> entry : entries.entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey()));
                zip.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
            zip.finish();
            return bytes.toByteArray();
        } catch (IOException error) {
            throw new ReportExportUnavailableException("Не удалось собрать XLSX статистики группы");
        }
    }

    private static String summaryHtml(Metrics summary) {
        StringBuilder html = new StringBuilder("<div class=\"table-wrap\"><table><thead><tr><th>Показатель</th><th>Количество</th><th>Знаменатель</th><th>Процент</th></tr></thead><tbody>");
        for (int index = 0; index < METRIC_LABELS.size(); index++) {
            Metric metric = metricAt(summary, index);
            html.append("<tr><th scope=\"row\">").append(htmlText(METRIC_LABELS.get(index)))
                    .append("</th><td>").append(metric.numerator()).append("</td><td>")
                    .append(metric.denominator()).append("</td><td>").append(htmlText(percentText(metric)))
                    .append("</td></tr>");
        }
        return html.append("</tbody></table></div>").toString();
    }

    private static String attendanceHtml(List<StudentRow> rows) {
        StringBuilder html = new StringBuilder("<div class=\"table-wrap\"><table><thead><tr><th>Студент</th>");
        for (String label : METRIC_LABELS) html.append("<th class=\"metric\">").append(htmlText(label))
                .append("<br>количество / знаменатель / %</th>");
        html.append("</tr></thead><tbody>");
        if (rows.isEmpty()) return html.append("<tr><td colspan=\"5\">По заданным условиям данных нет.</td></tr></tbody></table></div>").toString();
        for (StudentRow row : rows) {
            html.append("<tr><th scope=\"row\">").append(htmlText(row.displayName())).append("</th>");
            for (Metric metric : metrics(row)) html.append("<td class=\"metric\">").append(htmlText(metricText(metric))).append("</td>");
            html.append("</tr>");
        }
        return html.append("</tbody></table></div>").toString();
    }

    private static String requestsHtml(List<StudentRow> rows) {
        StringBuilder html = new StringBuilder("<div class=\"table-wrap\"><table><thead><tr><th>Студент</th><th>н: подано</th><th>н: одобрено</th><th>н: отклонено</th><th>у: подано</th><th>у: одобрено</th><th>у: отклонено</th><th>+: гео</th><th>+: заявка</th><th>+: гео неуспех</th><th>+: староста</th></tr></thead><tbody>");
        if (rows.isEmpty()) return html.append("<tr><td colspan=\"11\">По заданным условиям данных нет.</td></tr></tbody></table></div>").toString();
        for (StudentRow row : rows) {
            var late = row.lateCheckin();
            var excuse = row.excuse();
            var sources = row.sources();
            html.append("<tr><th scope=\"row\">").append(htmlText(row.displayName())).append("</th>");
            for (int value : new int[]{late.submitted(), late.approved(), late.rejected(),
                    excuse.submitted(), excuse.approved(), excuse.rejected(), sources.studentGeo(),
                    sources.manualRequest(), sources.autoAfterGeoFailure(), sources.headmanManual()}) {
                html.append("<td class=\"metric\">").append(value).append("</td>");
            }
            html.append("</tr>");
        }
        return html.append("</tbody></table></div>").toString();
    }

    private static String sheetXml(HeadmanStatsExportModel model, int headerRow, int firstDataRow, int lastRow) {
        StringBuilder xml = new StringBuilder(32_000);
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>")
                .append("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><dimension ref=\"A1:W")
                .append(lastRow).append("\"/><sheetViews><sheetView workbookViewId=\"0\"><pane ySplit=\"")
                .append(headerRow).append("\" topLeftCell=\"A").append(firstDataRow)
                .append("\" activePane=\"bottomLeft\" state=\"frozen\"/></sheetView></sheetViews><cols>")
                .append("<col min=\"1\" max=\"1\" width=\"32\" customWidth=\"1\"/>")
                .append("<col min=\"2\" max=\"23\" width=\"15\" customWidth=\"1\"/></cols><sheetData>");
        appendInlineRow(xml, 1, List.of("Статистика посещаемости группы"), true);
        Context context = model.context();
        appendContextRow(xml, 2, "Группа", context.groupName());
        appendContextRow(xml, 3, "Активный семестр", semester(context));
        appendContextRow(xml, 4, "Разрез", context.subjectId() == null ? "Вся группа" : context.subjectName());
        appendContextRow(xml, 5, "Типы занятий", context.lessonTypes().isEmpty() ? "Все" : String.join(", ", context.lessonTypes()));
        appendContextRow(xml, 6, "Завершённых занятий учтено", Integer.toString(context.lessonsCount()));
        appendContextRow(xml, 7, "Студентов после фильтра", Integer.toString(model.filteredStudents()));
        appendContextRow(xml, 8, "Сформировано", GENERATED_AT.format(context.generatedAt()));
        appendInlineRow(xml, 10, List.of("Итоги по выбранному набору студентов"), true);
        appendInlineRow(xml, 11, List.of("Показатель", "Количество", "Знаменатель", "Процент"), true);
        for (int index = 0; index < METRIC_LABELS.size(); index++) {
            Metric metric = metricAt(model.summary(), index);
            int row = 12 + index;
            xml.append("<row r=\"").append(row).append("\">");
            inlineCell(xml, "A" + row, METRIC_LABELS.get(index), 0);
            numberCell(xml, "B" + row, metric.numerator(), 0);
            numberCell(xml, "C" + row, metric.denominator(), 0);
            if (metric.denominator() == 0) inlineCell(xml, "D" + row, "—", 0);
            else numberCell(xml, "D" + row, Double.toString(metric.percent() / 100.0), 2);
            xml.append("</row>");
        }
        appendInlineRow(xml, 16, List.of("Данные по студентам"), true);
        appendInlineRow(xml, headerRow, headers(), true);
        if (model.rows().isEmpty()) {
            appendInlineRow(xml, firstDataRow, List.of("По заданным условиям данных нет."), false);
        } else {
            for (int index = 0; index < model.rows().size(); index++) {
                appendDataRow(xml, firstDataRow + index, model.rows().get(index));
            }
        }
        return xml.append("</sheetData></worksheet>").toString();
    }

    private static void appendDataRow(StringBuilder xml, int rowNumber, StudentRow row) {
        xml.append("<row r=\"").append(rowNumber).append("\">");
        inlineCell(xml, "A" + rowNumber, row.displayName(), 0);
        int column = 2;
        for (Metric metric : metrics(row)) {
            numberCell(xml, columnName(column++) + rowNumber, metric.numerator(), 0);
            numberCell(xml, columnName(column++) + rowNumber, metric.denominator(), 0);
            if (metric.denominator() == 0) inlineCell(xml, columnName(column++) + rowNumber, "—", 0);
            else numberCell(xml, columnName(column++) + rowNumber, Double.toString(metric.percent() / 100.0), 2);
        }
        var late = row.lateCheckin();
        var excuse = row.excuse();
        var sources = row.sources();
        for (int value : new int[]{late.submitted(), late.approved(), late.rejected(), excuse.submitted(),
                excuse.approved(), excuse.rejected(), sources.studentGeo(), sources.manualRequest(),
                sources.autoAfterGeoFailure(), sources.headmanManual()}) {
            numberCell(xml, columnName(column++) + rowNumber, value, 0);
        }
        xml.append("</row>");
    }

    private static List<String> headers() {
        List<String> labels = new ArrayList<>(List.of("Студент"));
        for (String metric : METRIC_LABELS) {
            labels.add(metric + " количество");
            labels.add(metric + " знаменатель");
            labels.add(metric + " процент");
        }
        labels.addAll(List.of("н подано", "н одобрено", "н отклонено", "у подано", "у одобрено", "у отклонено",
                "+ гео", "+ заявка", "+ после гео", "+ староста"));
        return List.copyOf(labels);
    }

    private static void appendContextRow(StringBuilder xml, int row, String label, String value) {
        xml.append("<row r=\"").append(row).append("\">");
        inlineCell(xml, "A" + row, label, 1);
        inlineCell(xml, "B" + row, value, 0);
        xml.append("</row>");
    }

    private static void appendInlineRow(StringBuilder xml, int row, List<String> values, boolean header) {
        xml.append("<row r=\"").append(row).append("\">");
        for (int index = 0; index < values.size(); index++) {
            inlineCell(xml, columnName(index + 1) + row, values.get(index), header ? 1 : 0);
        }
        xml.append("</row>");
    }

    private static void numberCell(StringBuilder xml, String reference, int value, int style) {
        numberCell(xml, reference, Integer.toString(value), style);
    }

    private static void numberCell(StringBuilder xml, String reference, String value, int style) {
        xml.append("<c r=\"").append(reference).append("\" t=\"n\"");
        if (style != 0) xml.append(" s=\"").append(style).append("\"");
        xml.append("><v>").append(value).append("</v></c>");
    }

    private static void inlineCell(StringBuilder xml, String reference, String value, int style) {
        xml.append("<c r=\"").append(reference).append("\" t=\"inlineStr\"");
        if (style != 0) xml.append(" s=\"").append(style).append("\"");
        xml.append("><is><t xml:space=\"preserve\">").append(xmlText(value)).append("</t></is></c>");
    }

    private static String stylesXml() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<styleSheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">"
                + "<numFmts count=\"1\"><numFmt numFmtId=\"164\" formatCode=\"0.0%\"/></numFmts>"
                + "<fonts count=\"2\"><font><sz val=\"11\"/><name val=\"Calibri\"/></font><font><b/><sz val=\"11\"/><name val=\"Calibri\"/></font></fonts>"
                + "<fills count=\"3\"><fill><patternFill patternType=\"none\"/></fill><fill><patternFill patternType=\"gray125\"/></fill><fill><patternFill patternType=\"solid\"><fgColor rgb=\"FFDCE6F1\"/><bgColor indexed=\"64\"/></patternFill></fill></fills>"
                + "<borders count=\"1\"><border><left/><right/><top/><bottom/><diagonal/></border></borders>"
                + "<cellStyleXfs count=\"1\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\"/></cellStyleXfs>"
                + "<cellXfs count=\"4\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\"/>"
                + "<xf numFmtId=\"0\" fontId=\"1\" fillId=\"2\" borderId=\"0\" xfId=\"0\" applyAlignment=\"1\"><alignment vertical=\"center\" wrapText=\"1\"/></xf>"
                + "<xf numFmtId=\"164\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyNumberFormat=\"1\"/>"
                + "<xf numFmtId=\"0\" fontId=\"1\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyAlignment=\"1\"><alignment vertical=\"center\" wrapText=\"1\"/></xf></cellXfs>"
                + "<cellStyles count=\"1\"><cellStyle name=\"Normal\" xfId=\"0\" builtinId=\"0\"/></cellStyles></styleSheet>";
    }

    private static String contextItem(String label, String value) {
        return "<dt>" + htmlText(label) + "</dt><dd>" + htmlText(value) + "</dd>";
    }

    private static String semester(Context context) {
        if (context.semesterId() == null || context.semesterFrom() == null) return "нет активного семестра";
        return context.semesterName() + " · " + DATE.format(context.semesterFrom()) + " — " + DATE.format(context.semesterTo());
    }

    private static List<Metric> metrics(StudentRow row) {
        return List.of(row.metrics().present(), row.metrics().presentOrExcused(),
                row.metrics().excused(), row.metrics().absent());
    }

    private static Metric metricAt(Metrics metrics, int index) {
        return switch (index) {
            case 0 -> metrics.present();
            case 1 -> metrics.presentOrExcused();
            case 2 -> metrics.excused();
            case 3 -> metrics.absent();
            default -> throw new IllegalArgumentException("Unknown metric index");
        };
    }

    private static String metricText(Metric metric) {
        return metric.denominator() == 0 ? "— (0/0)"
                : metric.numerator() + " / " + metric.denominator() + " / " + percentText(metric);
    }

    private static String percentText(Metric metric) {
        return metric.denominator() == 0 ? "—"
                : java.math.BigDecimal.valueOf(metric.percent()).setScale(1, RoundingMode.HALF_UP).toPlainString() + "%";
    }

    private static String htmlText(String value) {
        return xmlText(value).replace("&apos;", "&#39;");
    }

    /** Filters XML 1.0 forbidden controls before escaping and leaves valid text intact. */
    private static String xmlText(String value) {
        StringBuilder result = new StringBuilder(value == null ? 0 : value.length());
        if (value != null) {
            value.codePoints().filter(codePoint -> codePoint == 0x9 || codePoint == 0xA || codePoint == 0xD
                            || codePoint >= 0x20 && codePoint <= 0xD7FF
                            || codePoint >= 0xE000 && codePoint <= 0xFFFD
                            || codePoint >= 0x10000 && codePoint <= 0x10FFFF)
                    .forEach(codePoint -> {
                        switch (codePoint) {
                            case '&' -> result.append("&amp;");
                            case '<' -> result.append("&lt;");
                            case '>' -> result.append("&gt;");
                            case '"' -> result.append("&quot;");
                            case '\'' -> result.append("&apos;");
                            default -> result.appendCodePoint(codePoint);
                        }
                    });
        }
        return result.toString();
    }

    private static String columnName(int number) {
        StringBuilder result = new StringBuilder();
        for (int value = number; value > 0; value = (value - 1) / 26) {
            result.insert(0, (char) ('A' + (value - 1) % 26));
        }
        return result.toString();
    }
}
