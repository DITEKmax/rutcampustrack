package ru.rutcampustrack.attendance.report;

import org.springframework.stereotype.Component;
import ru.rutcampustrack.attendance.contract.dto.report.HeadmanStatsResponse.Context;
import ru.rutcampustrack.attendance.contract.dto.report.HeadmanStatsResponse.Metric;
import ru.rutcampustrack.attendance.contract.dto.report.HeadmanStatsResponse.Metrics;
import ru.rutcampustrack.attendance.contract.dto.report.HeadmanStatsResponse.StudentRow;
import ru.rutcampustrack.attendance.exception.ReportExportUnavailableException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** DOCX layout for one complete, server-filtered PK-113 student-statistics block. */
@Component
public final class HeadmanStatsDocxRenderer {
    private static final int TABLE_WIDTH = 15_000;
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.uuuu");
    private static final DateTimeFormatter GENERATED_AT = DateTimeFormatter
            .ofPattern("dd.MM.uuuu HH:mm 'UTC'").withZone(ZoneOffset.UTC);

    public byte[] render(HeadmanStatsExportModel model) {
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
        entries.put("word/document.xml", documentXml(model));
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
            throw new ReportExportUnavailableException("Не удалось собрать DOCX статистики группы");
        }
    }

    private static String documentXml(HeadmanStatsExportModel model) {
        Context context = model.context();
        StringBuilder xml = new StringBuilder(24_000);
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>")
                .append("<w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\"><w:body>")
                .append(paragraph("Статистика посещаемости группы", true, 28))
                .append(paragraph("Группа: " + context.groupName(), false, 18))
                .append(paragraph("Активный семестр: " + context.semesterName() + " · "
                        + DATE.format(context.semesterFrom()) + " — " + DATE.format(context.semesterTo()), false, 18))
                .append(paragraph("Разрез: " + (context.subjectId() == null ? "вся группа" : context.subjectName()), false, 18))
                .append(paragraph("Типы занятий: " + (context.lessonTypes().isEmpty()
                        ? "все" : String.join(", ", context.lessonTypes())), false, 18))
                .append(paragraph("Завершённых занятий учтено: " + context.lessonsCount(), false, 18))
                .append(paragraph("Студентов после фильтра: " + model.filteredStudents(), false, 18))
                .append(paragraph("Фильтры: " + model.filters(), false, 18))
                .append(paragraph("Сортировка: " + model.sorts(), false, 18))
                .append(paragraph("Сформировано: " + GENERATED_AT.format(context.generatedAt()), false, 16))
                .append(paragraph("Итоги по выбранному набору студентов", true, 20))
                .append(summaryTable(model.summary()))
                .append(paragraph("Посещаемость по студентам", true, 20))
                .append(attendanceTable(model.rows()))
                .append(paragraph("Заявки и способ получения отметки «+»", true, 20))
                .append(requestsTable(model.rows()))
                .append("<w:sectPr><w:pgSz w:w=\"16838\" w:h=\"11906\" w:orient=\"landscape\"/>"
                        + "<w:pgMar w:top=\"600\" w:right=\"540\" w:bottom=\"600\" w:left=\"540\" w:header=\"300\" w:footer=\"300\" w:gutter=\"0\"/></w:sectPr>")
                .append("</w:body></w:document>");
        return xml.toString();
    }

    private static String summaryTable(Metrics summary) {
        StringBuilder xml = tableStart(new int[]{4_500, 3_500, 3_500, 3_500});
        xml.append(header(List.of("Показатель", "Количество", "Знаменатель", "Процент"),
                new int[]{4_500, 3_500, 3_500, 3_500}));
        addMetricSummary(xml, "+", summary.present());
        addMetricSummary(xml, "+ и у", summary.presentOrExcused());
        addMetricSummary(xml, "у", summary.excused());
        addMetricSummary(xml, "н", summary.absent());
        return xml.append("</w:tbl>").toString();
    }

    private static void addMetricSummary(StringBuilder xml, String label, Metric metric) {
        xml.append("<w:tr>").append(cell(label, 4_500, false))
                .append(cell(Integer.toString(metric.numerator()), 3_500, false))
                .append(cell(Integer.toString(metric.denominator()), 3_500, false))
                .append(cell(percentText(metric), 3_500, false)).append("</w:tr>");
    }

    private static String attendanceTable(List<StudentRow> rows) {
        int[] widths = {3_000, 3_000, 3_000, 3_000, 3_000};
        StringBuilder xml = tableStart(widths);
        xml.append(header(List.of("Студент", "+ · количество / знаменатель / %",
                "+ и у · количество / знаменатель / %", "у · количество / знаменатель / %",
                "н · количество / знаменатель / %"), widths));
        if (rows.isEmpty()) return emptyRow(xml, widths).append("</w:tbl>").toString();
        for (StudentRow row : rows) {
            xml.append("<w:tr><w:trPr><w:cantSplit/></w:trPr>").append(cell(row.displayName(), widths[0], false));
            for (Metric metric : List.of(row.metrics().present(), row.metrics().presentOrExcused(),
                    row.metrics().excused(), row.metrics().absent())) {
                xml.append(cell(metricText(metric), widths[1], false));
            }
            xml.append("</w:tr>");
        }
        return xml.append("</w:tbl>").toString();
    }

    private static String requestsTable(List<StudentRow> rows) {
        int[] widths = {3_000, 1_200, 1_200, 1_200, 1_200, 1_200, 1_200,
                1_200, 1_200, 1_200, 1_200};
        StringBuilder xml = tableStart(widths);
        xml.append(header(List.of("Студент", "н: подано", "н: одобрено", "н: отклонено",
                "у: подано", "у: одобрено", "у: отклонено", "+: гео", "+: заявка",
                "+: гео неуспех", "+: староста"), widths));
        if (rows.isEmpty()) return emptyRow(xml, widths).append("</w:tbl>").toString();
        for (StudentRow row : rows) {
            xml.append("<w:tr><w:trPr><w:cantSplit/></w:trPr>").append(cell(row.displayName(), widths[0], false));
            var late = row.lateCheckin();
            var excuse = row.excuse();
            var sources = row.sources();
            for (int value : new int[]{late.submitted(), late.approved(), late.rejected(),
                    excuse.submitted(), excuse.approved(), excuse.rejected(), sources.studentGeo(),
                    sources.manualRequest(), sources.autoAfterGeoFailure(), sources.headmanManual()}) {
                xml.append(cell(Integer.toString(value), 1_200, false));
            }
            xml.append("</w:tr>");
        }
        return xml.append("</w:tbl>").toString();
    }

    private static StringBuilder tableStart(int[] widths) {
        StringBuilder xml = new StringBuilder("<w:tbl><w:tblPr><w:tblW w:w=\"" + TABLE_WIDTH + "\" w:type=\"dxa\"/>"
                + "<w:tblLayout w:type=\"fixed\"/><w:tblBorders>"
                + "<w:top w:val=\"single\" w:sz=\"4\" w:color=\"AAB4C0\"/>"
                + "<w:left w:val=\"single\" w:sz=\"4\" w:color=\"AAB4C0\"/>"
                + "<w:bottom w:val=\"single\" w:sz=\"4\" w:color=\"AAB4C0\"/>"
                + "<w:right w:val=\"single\" w:sz=\"4\" w:color=\"AAB4C0\"/>"
                + "<w:insideH w:val=\"single\" w:sz=\"4\" w:color=\"D5DAE0\"/>"
                + "<w:insideV w:val=\"single\" w:sz=\"4\" w:color=\"D5DAE0\"/></w:tblBorders></w:tblPr><w:tblGrid>");
        for (int width : widths) xml.append("<w:gridCol w:w=\"").append(width).append("\"/>");
        return xml.append("</w:tblGrid>");
    }

    private static String header(List<String> labels, int[] widths) {
        StringBuilder xml = new StringBuilder("<w:tr><w:trPr><w:tblHeader/></w:trPr>");
        for (int index = 0; index < labels.size(); index++) xml.append(cell(labels.get(index), widths[index], true));
        return xml.append("</w:tr>").toString();
    }

    private static StringBuilder emptyRow(StringBuilder xml, int[] widths) {
        xml.append("<w:tr><w:tc><w:tcPr><w:tcW w:w=\"").append(widths[0]).append("\" w:type=\"dxa\"/></w:tcPr>")
                .append(paragraph("По заданным условиям данных нет.", false, 14)).append("</w:tc>");
        for (int index = 1; index < widths.length; index++) {
            xml.append("<w:tc><w:tcPr><w:tcW w:w=\"").append(widths[index]).append("\" w:type=\"dxa\"/></w:tcPr>")
                    .append(paragraph("", false, 14)).append("</w:tc>");
        }
        return xml.append("</w:tr>");
    }

    private static String cell(String text, int width, boolean header) {
        return "<w:tc><w:tcPr><w:tcW w:w=\"" + width + "\" w:type=\"dxa\"/>"
                + (header ? "<w:shd w:val=\"clear\" w:color=\"auto\" w:fill=\"DCE6F1\"/>" : "")
                + "<w:vAlign w:val=\"center\"/></w:tcPr>" + paragraph(text, header, header ? 13 : 14) + "</w:tc>";
    }

    private static String paragraph(String text, boolean bold, int fontSizeHalfPoints) {
        StringBuilder xml = new StringBuilder("<w:p><w:r><w:rPr>");
        if (bold) xml.append("<w:b/>");
        xml.append("<w:sz w:val=\"").append(fontSizeHalfPoints).append("\"/></w:rPr>");
        String normalized = (text == null ? "" : text).replace("\r\n", "\n").replace('\r', '\n');
        String[] lines = normalized.split("\n", -1);
        for (int index = 0; index < lines.length; index++) {
            if (index > 0) xml.append("<w:br/>");
            xml.append("<w:t xml:space=\"preserve\">").append(xmlText(lines[index])).append("</w:t>");
        }
        return xml.append("</w:r></w:p>").toString();
    }

    private static String metricText(Metric metric) {
        return metric.denominator() == 0 ? "— (0/0)"
                : metric.numerator() + " / " + metric.denominator() + " / " + percentText(metric);
    }

    private static String percentText(Metric metric) {
        return metric.denominator() == 0 ? "—" : String.format(java.util.Locale.ROOT, "%.1f%%", metric.percent());
    }

    private static String xmlText(String value) {
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
