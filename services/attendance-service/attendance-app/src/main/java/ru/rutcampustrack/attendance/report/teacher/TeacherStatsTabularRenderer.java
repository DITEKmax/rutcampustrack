package ru.rutcampustrack.attendance.report.teacher;

import org.springframework.stereotype.Component;
import ru.rutcampustrack.attendance.exception.ReportExportUnavailableException;
import ru.rutcampustrack.attendance.report.teacher.TeacherStatsExportModel.Context;
import ru.rutcampustrack.attendance.report.teacher.TeacherStatsExportModel.Metric;
import ru.rutcampustrack.attendance.report.teacher.TeacherStatsExportModel.Row;
import ru.rutcampustrack.attendance.report.teacher.TeacherStatsExportModel.Scope;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Safe HTML and numeric-cell XLSX layouts for teacher 123 statistics. */
@Component
public final class TeacherStatsTabularRenderer {
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.uuuu");
    private static final List<String> METRIC_LABELS = List.of("«+»", "«+ и у»", "«у»", "«н»");

    public byte[] renderHtml(TeacherStatsExportModel model) {
        Context context = model.context();
        boolean groups = context.scope() == Scope.GROUPS;
        StringBuilder html = new StringBuilder(16_384);
        html.append("<!doctype html><html lang=\"ru\"><head><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">")
                .append("<title>").append(htmlText(title(context.scope()))).append("</title>")
                .append("<style>body{font-family:Arial,sans-serif;color:#202124;margin:1.5rem}h1{font-size:1.4rem}dl{display:grid;grid-template-columns:max-content 1fr;gap:.35rem 1rem}dt{font-weight:700}dd{margin:0}.table-wrap{overflow-x:auto;margin-top:1.25rem}table{border-collapse:collapse;min-width:54rem;width:100%}th,td{border:1px solid #c9ced3;padding:.55rem;text-align:left;vertical-align:top}thead{background:#eef1f3}tbody tr:nth-child(even){background:#f8f9fa}th:first-child,td:first-child{min-width:13rem}.metric{white-space:nowrap;text-align:center}</style></head><body>")
                .append("<h1>").append(htmlText(title(context.scope()))).append("</h1><dl>")
                .append(contextItem("Разрез", groups ? "По моим группам" : "По студентам группы"))
                .append(contextItem(groups ? "Группы" : "Группа", groupLabel(context)))
                .append(contextItem("Предмет", context.subjectLabel().isBlank() ? "—" : context.subjectLabel()))
                .append(contextItem("Типы занятий", String.join(", ", context.typeLabels())))
                .append(contextItem("Период семестра", dateRange(context)))
                .append(contextItem("Период данных", dataRange(context)))
                .append(contextItem("Учтено пар", Integer.toString(context.lessonsCount())))
                .append("</dl><div class=\"table-wrap\"><table><thead><tr><th>")
                .append(groups ? "Группа" : "Студент").append("</th>");
        for (String label : METRIC_LABELS) {
            html.append("<th class=\"metric\">").append(label)
                    .append("<br>числитель / знаменатель / %</th>");
        }
        if (groups) html.append("<th class=\"metric\">Пар учтено</th>");
        html.append("</tr></thead><tbody>");
        if (model.rows().isEmpty()) {
            html.append("<tr><td colspan=\"").append(groups ? 6 : 5)
                    .append("\">По заданному фильтру данных нет.</td></tr>");
        } else {
            for (Row row : model.rows()) {
                html.append("<tr><th scope=\"row\">").append(htmlText(row.label())).append("</th>");
                for (Metric metric : row.metricsInDisplayOrder()) {
                    html.append("<td class=\"metric\">").append(htmlText(metricText(metric))).append("</td>");
                }
                if (groups) html.append("<td class=\"metric\">").append(row.lessonsCount()).append("</td>");
                html.append("</tr>");
            }
        }
        html.append("</tbody></table></div></body></html>");
        return html.toString().getBytes(StandardCharsets.UTF_8);
    }

    public byte[] renderXlsx(TeacherStatsExportModel model) {
        Context context = model.context();
        boolean groups = context.scope() == Scope.GROUPS;
        int columns = 1 + 4 * 3 + (groups ? 1 : 0);
        int headerRow = 9;
        int firstDataRow = headerRow + 1;
        int lastRow = Math.max(firstDataRow, firstDataRow + model.rows().size() - 1);
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
                + "<Relationship Id=\"rId2\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles\" Target=\"styles.xml\"/></Relationships>");
        entries.put("xl/styles.xml", stylesXml());
        entries.put("xl/worksheets/sheet1.xml", sheetXml(model, headerRow, firstDataRow, lastRow, columns));
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
            throw new ReportExportUnavailableException("Could not package teacher statistics XLSX");
        }
    }

    private static String sheetXml(TeacherStatsExportModel model,
                                   int headerRow,
                                   int firstDataRow,
                                   int lastRow,
                                   int columns) {
        Context context = model.context();
        boolean groups = context.scope() == Scope.GROUPS;
        String lastColumn = columnName(columns);
        StringBuilder xml = new StringBuilder(16_384);
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>")
                .append("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><dimension ref=\"A1:")
                .append(lastColumn).append(lastRow).append("\"/><sheetViews><sheetView workbookViewId=\"0\"><pane xSplit=\"1\" ySplit=\"")
                .append(headerRow).append("\" topLeftCell=\"B").append(firstDataRow)
                .append("\" activePane=\"bottomRight\" state=\"frozen\"/></sheetView></sheetViews>")
                .append("<sheetFormatPr defaultRowHeight=\"18\"/><cols><col min=\"1\" max=\"1\" width=\"")
                .append(groups ? "30" : "36").append("\" customWidth=\"1\"/>");
        for (int column = 2; column <= 13; column++) {
            int width = (column - 2) % 3 == 2 ? 12 : 17;
            xml.append("<col min=\"").append(column).append("\" max=\"").append(column)
                    .append("\" width=\"").append(width).append("\" customWidth=\"1\"/>");
        }
        if (groups) xml.append("<col min=\"14\" max=\"14\" width=\"15\" customWidth=\"1\"/>");
        xml.append("</cols><sheetData>");
        appendContextRow(xml, 1, "Статистика посещаемости", title(context.scope()), 3);
        appendContextRow(xml, 2, "Разрез", groups ? "По моим группам" : "По студентам группы", 0);
        appendContextRow(xml, 3, groups ? "Группы" : "Группа", groupLabel(context), 0);
        appendContextRow(xml, 4, "Предмет", context.subjectLabel().isBlank() ? "—" : context.subjectLabel(), 0);
        appendContextRow(xml, 5, "Типы занятий", String.join(", ", context.typeLabels()), 0);
        appendContextRow(xml, 6, "Период семестра", dateRange(context), 0);
        appendContextRow(xml, 7, "Период данных", dataRange(context), 0);
        appendContextRow(xml, 8, "Учтено пар", Integer.toString(context.lessonsCount()), 0);
        List<String> headers = new ArrayList<>();
        headers.add(groups ? "Группа" : "Студент");
        for (String label : METRIC_LABELS) {
            headers.add(label + " · числитель");
            headers.add(label + " · знаменатель");
            headers.add(label + " · %");
        }
        if (groups) headers.add("Пар учтено");
        appendHeaderRow(xml, headerRow, headers);
        int rowNumber = firstDataRow;
        for (Row row : model.rows()) appendDataRow(xml, rowNumber++, row, groups);
        if (model.rows().isEmpty()) {
            xml.append("<row r=\"").append(firstDataRow).append("\">");
            inlineCell(xml, "A" + firstDataRow, "По заданному фильтру данных нет.", 0);
            xml.append("</row>");
        }
        xml.append("</sheetData><autoFilter ref=\"A").append(headerRow).append(":")
                .append(lastColumn).append(lastRow).append("\"/></worksheet>");
        return xml.toString();
    }

    private static void appendContextRow(StringBuilder xml, int row, String label, String value, int style) {
        xml.append("<row r=\"").append(row).append("\">");
        inlineCell(xml, "A" + row, label, 1);
        inlineCell(xml, "B" + row, value, style);
        xml.append("</row>");
    }

    private static void appendHeaderRow(StringBuilder xml, int row, List<String> headers) {
        xml.append("<row r=\"").append(row).append("\" ht=\"42\" customHeight=\"1\">");
        for (int index = 0; index < headers.size(); index++) {
            inlineCell(xml, columnName(index + 1) + row, headers.get(index), 1);
        }
        xml.append("</row>");
    }

    private static void appendDataRow(StringBuilder xml, int rowNumber, Row row, boolean groups) {
        xml.append("<row r=\"").append(rowNumber).append("\">");
        inlineCell(xml, "A" + rowNumber, row.label(), 0);
        int column = 2;
        for (Metric metric : row.metricsInDisplayOrder()) {
            numberCell(xml, columnName(column++) + rowNumber, metric.numerator(), 0);
            numberCell(xml, columnName(column++) + rowNumber, metric.denominator(), 0);
            if (metric.denominator() == 0) {
                inlineCell(xml, columnName(column++) + rowNumber, "—", 0);
            } else {
                String normalized = metric.percent().movePointLeft(2).stripTrailingZeros().toPlainString();
                numberCell(xml, columnName(column++) + rowNumber, normalized, 2);
            }
        }
        if (groups) numberCell(xml, columnName(column) + rowNumber, row.lessonsCount(), 0);
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

    private static String groupLabel(Context context) {
        return context.groupLabels().isEmpty() ? "Мои группы" : String.join(", ", context.groupLabels());
    }

    private static String title(Scope scope) {
        return scope == Scope.GROUPS ? "Статистика по моим группам" : "Статистика студентов группы";
    }

    private static String dateRange(Context context) {
        return DATE.format(context.semesterFrom()) + " — " + DATE.format(context.semesterTo());
    }

    private static String dataRange(Context context) {
        return context.dataFrom() == null ? "нет закрытых пар"
                : DATE.format(context.dataFrom()) + " — " + DATE.format(context.dataTo());
    }

    private static String metricText(Metric metric) {
        if (metric.denominator() == 0) return "— (0/0)";
        return metric.numerator() + " / " + metric.denominator() + " / "
                + metric.percent().setScale(1, RoundingMode.HALF_UP).toPlainString() + "%";
    }

    private static String htmlText(String value) {
        return xmlText(value).replace("&apos;", "&#39;");
    }

    /** Filters XML 1.0 forbidden controls before escaping, preserving the source name. */
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
