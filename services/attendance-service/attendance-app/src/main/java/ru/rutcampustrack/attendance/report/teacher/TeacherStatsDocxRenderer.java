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
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** DOCX summary layouts for the two teacher statistics scopes. */
@Component
public final class TeacherStatsDocxRenderer {
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.uuuu");
    private static final DateTimeFormatter GENERATED_AT = DateTimeFormatter
            .ofPattern("dd.MM.uuuu HH:mm 'UTC'").withZone(ZoneOffset.UTC);
    private static final int TABLE_WIDTH = 15_000;

    public byte[] render(TeacherStatsExportModel model) {
        String document = documentXml(model);
        Map<String, String> entries = new LinkedHashMap<>();
        entries.put("[Content_Types].xml", "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">"
                + "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>"
                + "<Default Extension=\"xml\" ContentType=\"application/xml\"/>"
                + "<Override PartName=\"/word/document.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml\"/>"
                + "</Types>");
        entries.put("_rels/.rels", "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
                + "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"word/document.xml\"/>"
                + "</Relationships>");
        entries.put("word/document.xml", document);
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
            throw new ReportExportUnavailableException("Could not package teacher statistics DOCX");
        }
    }

    private static String documentXml(TeacherStatsExportModel model) {
        Context context = model.context();
        boolean groups = context.scope() == Scope.GROUPS;
        StringBuilder xml = new StringBuilder(16_384);
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>")
                .append("<w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\"><w:body>")
                .append(paragraph(groups ? "Статистика по моим группам" : "Статистика студентов группы", true, 30))
                .append(paragraph("Разрез: " + (groups ? "по группам" : "по студентам группы"), false, 18))
                .append(paragraph((groups ? "Группы: " : "Группа: ") + groupLabel(context), false, 18));
        if (!context.subjectLabel().isBlank()) {
            xml.append(paragraph("Предмет: " + context.subjectLabel(), false, 18));
        }
        xml.append(paragraph("Типы занятий: " + String.join(", ", context.typeLabels()), false, 18))
                .append(paragraph("Период семестра: " + dateRange(context.semesterFrom(), context.semesterTo()), false, 18))
                .append(paragraph("Период данных: " + dataRange(context), false, 18))
                .append(paragraph("Учтено пар: " + context.lessonsCount(), false, 18))
                .append(paragraph("Сформировано: " + GENERATED_AT.format(context.generatedAt()), false, 16))
                .append(table(model))
                .append("<w:sectPr><w:pgSz w:w=\"16838\" w:h=\"11906\" w:orient=\"landscape\"/>"
                        + "<w:pgMar w:top=\"720\" w:right=\"720\" w:bottom=\"720\" w:left=\"720\" w:header=\"360\" w:footer=\"360\" w:gutter=\"0\"/></w:sectPr>")
                .append("</w:body></w:document>");
        return xml.toString();
    }

    private static String table(TeacherStatsExportModel model) {
        boolean groups = model.context().scope() == Scope.GROUPS;
        StringBuilder xml = new StringBuilder(8_192);
        List<String> headers = groups
                ? List.of("Группа", "% «+»\nчислитель / знаменатель", "% «+ и у»\nчислитель / знаменатель",
                "% «у»\nчислитель / знаменатель", "% «н»\nчислитель / знаменатель", "Пар учтено")
                : List.of("Студент", "% «+»\nчислитель / знаменатель", "% «+ и у»\nчислитель / знаменатель",
                "% «у»\nчислитель / знаменатель", "% «н»\nчислитель / знаменатель");
        int[] widths = groups ? new int[]{3_500, 2_450, 2_450, 2_450, 2_450, 1_700}
                : new int[]{4_200, 2_700, 2_700, 2_700, 2_700};
        xml.append("<w:tbl><w:tblPr><w:tblW w:w=\"").append(TABLE_WIDTH).append("\" w:type=\"dxa\"/>")
                .append("<w:tblLayout w:type=\"fixed\"/><w:tblBorders>")
                .append("<w:top w:val=\"single\" w:sz=\"4\" w:color=\"AAB4C0\"/>")
                .append("<w:left w:val=\"single\" w:sz=\"4\" w:color=\"AAB4C0\"/>")
                .append("<w:bottom w:val=\"single\" w:sz=\"4\" w:color=\"AAB4C0\"/>")
                .append("<w:right w:val=\"single\" w:sz=\"4\" w:color=\"AAB4C0\"/>")
                .append("<w:insideH w:val=\"single\" w:sz=\"4\" w:color=\"D5DAE0\"/>")
                .append("<w:insideV w:val=\"single\" w:sz=\"4\" w:color=\"D5DAE0\"/>")
                .append("</w:tblBorders></w:tblPr><w:tblGrid>");
        for (int width : widths) xml.append("<w:gridCol w:w=\"").append(width).append("\"/>");
        xml.append("</w:tblGrid><w:tr><w:trPr><w:tblHeader/></w:trPr>");
        for (int index = 0; index < headers.size(); index++) {
            xml.append(cell(headers.get(index), widths[index], true));
        }
        xml.append("</w:tr>");
        if (model.rows().isEmpty()) {
            xml.append("<w:tr>").append(cell("По заданному фильтру данных нет.", widths[0], false));
            for (int index = 1; index < widths.length; index++) xml.append(cell("", widths[index], false));
            xml.append("</w:tr>");
        } else {
            for (Row row : model.rows()) {
                xml.append("<w:tr>").append(cell(row.label(), widths[0], false));
                List<Metric> metrics = row.metricsInDisplayOrder();
                for (int index = 0; index < metrics.size(); index++) {
                    xml.append(cell(metricText(metrics.get(index)), widths[index + 1], false));
                }
                if (groups) xml.append(cell(Integer.toString(row.lessonsCount()), widths[widths.length - 1], false));
                xml.append("</w:tr>");
            }
        }
        return xml.append("</w:tbl>").toString();
    }

    private static String cell(String text, int width, boolean header) {
        return "<w:tc><w:tcPr><w:tcW w:w=\"" + width + "\" w:type=\"dxa\"/>"
                + (header ? "<w:shd w:val=\"clear\" w:color=\"auto\" w:fill=\"DCE6F1\"/>" : "")
                + "<w:vAlign w:val=\"center\"/></w:tcPr>" + paragraph(text, header, 16) + "</w:tc>";
    }

    private static String paragraph(String text, boolean bold, int fontSizeHalfPoints) {
        StringBuilder xml = new StringBuilder("<w:p><w:r><w:rPr>");
        if (bold) xml.append("<w:b/>");
        xml.append("<w:sz w:val=\"").append(fontSizeHalfPoints).append("\"/></w:rPr>");
        String normalized = (text == null ? "" : text).replace("\r\n", "\n").replace('\r', '\n');
        String[] lines = normalized.split("\n", -1);
        for (int index = 0; index < lines.length; index++) {
            if (index > 0) xml.append("<w:br/>");
            xml.append("<w:t xml:space=\"preserve\">").append(escapeXml(lines[index])).append("</w:t>");
        }
        return xml.append("</w:r></w:p>").toString();
    }

    private static String metricText(Metric metric) {
        if (metric.denominator() == 0) return "— (0/0)";
        return metric.numerator() + " / " + metric.denominator() + "\n"
                + metric.percent().setScale(1, RoundingMode.HALF_UP).toPlainString() + "%";
    }

    private static String dateRange(java.time.LocalDate from, java.time.LocalDate to) {
        return DATE.format(from) + " — " + DATE.format(to);
    }

    private static String dataRange(Context context) {
        return context.dataFrom() == null ? "нет закрытых пар"
                : dateRange(context.dataFrom(), context.dataTo());
    }

    private static String groupLabel(Context context) {
        return context.groupLabels().isEmpty() ? "Мои группы" : String.join(", ", context.groupLabels());
    }

    private static String escapeXml(String value) {
        StringBuilder result = new StringBuilder(value.length());
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
        return result.toString();
    }
}
