package ru.rutcampustrack.attendance.report.teacher;

import org.springframework.stereotype.Component;
import ru.rutcampustrack.attendance.exception.ReportValidationException;
import ru.rutcampustrack.attendance.report.teacher.TeacherAttendanceExportModel.Column;
import ru.rutcampustrack.attendance.report.teacher.TeacherAttendanceExportModel.Context;
import ru.rutcampustrack.attendance.report.teacher.TeacherAttendanceExportModel.Metrics;
import ru.rutcampustrack.attendance.report.teacher.TeacherAttendanceExportModel.ReportKind;
import ru.rutcampustrack.attendance.report.teacher.TeacherAttendanceExportModel.Row;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/** Renders the teacher's supplied attendance selection without recalculating its scope or metrics. */
@Component
public final class TeacherAttendanceDocxRenderer {

    private static final String DOCUMENT_XML = "word/document.xml";
    private static final String WEEKLY_TEMPLATE = "report-templates/teacher-attendance-weekly.docx";
    private static final String SUBJECT_TEMPLATE = "report-templates/teacher-attendance-subject-journal.docx";
    private static final String PAGE_BREAK = "<w:p><w:r><w:br w:type=\"page\"/></w:r></w:p>";
    private static final int LESSONS_PER_SEGMENT = 6;
    private static final int MATRIX_WIDTH = 15_600;
    private static final int MATRIX_NUMBER_WIDTH = 480;
    private static final int MATRIX_NAME_WIDTH = 3_300;
    private static final int SUMMARY_NAME_WIDTH = 2_700;
    private static final Pattern PARAGRAPH = Pattern.compile("(?s)<w:p\\b[^>]*>.*?</w:p>");
    private static final Pattern PLACEHOLDER = Pattern.compile("\\$\\{[A-Z_]+}");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.uuuu");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter GENERATED_AT = DateTimeFormatter
            .ofPattern("dd.MM.uuuu HH:mm 'UTC'", Locale.ROOT)
            .withZone(ZoneOffset.UTC);

    /** Renders one weekly report or one subject journal according to its frozen model kind. */
    public byte[] render(TeacherAttendanceExportModel model) {
        Objects.requireNonNull(model, "model");
        ReportKind kind = model.context().kind();
        String template = switch (kind) {
            case WEEKLY_ATTENDANCE -> WEEKLY_TEMPLATE;
            case SUBJECT_JOURNAL -> SUBJECT_TEMPLATE;
        };
        TemplatePackage templatePackage = readTemplate(template);
        return writePackage(templatePackage.entries(), renderDocumentXml(templatePackage.documentXml(), model));
    }

    /** Renders already server-bounded weekly models in caller order, with a page break per week. */
    public byte[] render(List<TeacherAttendanceExportModel> weeklyModels) {
        if (weeklyModels == null || weeklyModels.isEmpty()) {
            throw new ReportValidationException("At least one weekly attendance model is required");
        }
        List<TeacherAttendanceExportModel> models = List.copyOf(weeklyModels);
        if (models.stream().anyMatch(model -> model.context().kind() != ReportKind.WEEKLY_ATTENDANCE)) {
            throw new ReportValidationException("A weekly DOCX batch can contain only WEEKLY_ATTENDANCE models");
        }

        TemplatePackage templatePackage = readTemplate(WEEKLY_TEMPLATE);
        List<String> documents = models.stream()
                .map(model -> renderDocumentXml(templatePackage.documentXml(), model))
                .toList();
        String documentXml = documents.size() == 1
                ? documents.get(0)
                : mergeDocumentXml(documents);
        return writePackage(templatePackage.entries(), documentXml);
    }

    private static String renderDocumentXml(String templateXml, TeacherAttendanceExportModel model) {
        Context context = model.context();
        Map<String, String> placeholders = new LinkedHashMap<>();
        placeholders.put("REPORT_TITLE", context.kind() == ReportKind.WEEKLY_ATTENDANCE
                ? "Посещаемость за неделю"
                : "Журнал посещаемости по предмету");
        placeholders.put("GROUP_LABEL", context.groupLabel());
        placeholders.put("WEEK_RANGE", context.periodFrom().format(DATE) + "–" + context.periodTo().format(DATE));
        placeholders.put("SUBJECT_LABEL", context.subjectLabel());
        placeholders.put("SEMESTER_LABEL", context.semesterLabel());
        placeholders.put("PERIOD_LABEL", context.periodFrom().format(DATE) + "–" + context.periodTo().format(DATE));
        placeholders.put("TYPE_LABELS", String.join(", ", context.typeLabels()));
        placeholders.put("GENERATED_AT", GENERATED_AT.format(context.generatedAt()));

        String documentXml = templateXml;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            documentXml = documentXml.replace("${" + entry.getKey() + "}", escapeXml(entry.getValue()));
        }
        documentXml = replacePlaceholderParagraph(documentXml, "MATRIX_TABLE", matrixTables(model));
        String summaryTable = context.kind() == ReportKind.WEEKLY_ATTENDANCE ? "" : summary(model);
        documentXml = replacePreviousTitleAndPlaceholderParagraph(
                documentXml, "Итоги по студентам", "SUMMARY_TABLE", summaryTable);
        Matcher unresolved = PLACEHOLDER.matcher(documentXml);
        if (unresolved.find()) {
            throw new ReportValidationException("DOCX template still contains placeholder " + unresolved.group());
        }
        return documentXml;
    }

    private static String matrixTables(TeacherAttendanceExportModel model) {
        StringBuilder xml = new StringBuilder();
        int columns = model.columns().size();
        if (columns == 0) {
            xml.append(paragraph("Занятий по выбранным типам за период нет.", false, false, 18, true));
            xml.append(matrixTable(model, 0, 0));
            return xml.toString();
        }
        for (int firstColumn = 0; firstColumn < columns; firstColumn += LESSONS_PER_SEGMENT) {
            if (firstColumn > 0) {
                xml.append(PAGE_BREAK);
            }
            int endColumn = Math.min(firstColumn + LESSONS_PER_SEGMENT, columns);
            xml.append(matrixTable(model, firstColumn, endColumn));
        }
        return xml.toString();
    }

    private static String matrixTable(TeacherAttendanceExportModel model, int firstColumn, int endColumn) {
        int lessonCount = endColumn - firstColumn;
        int[] widths = matrixWidths(lessonCount);
        StringBuilder xml = new StringBuilder();
        xml.append(tableStart(widths));
        xml.append(rowStart(true));
        xml.append(cell("№", widths[0], true, true, false, 16));
        xml.append(cell("Студент", widths[1], true, false, false, 16));
        for (int lessonIndex = firstColumn; lessonIndex < endColumn; lessonIndex++) {
            xml.append(cell(lessonHeader(model.columns().get(lessonIndex)), widths[lessonIndex - firstColumn + 2],
                    true, true, false, 15));
        }
        xml.append("</w:tr>");

        for (int rowIndex = 0; rowIndex < model.rows().size(); rowIndex++) {
            Row row = model.rows().get(rowIndex);
            boolean alternate = rowIndex % 2 == 1;
            xml.append(rowStart(false));
            xml.append(cell(Integer.toString(rowIndex + 1), widths[0], false, true, alternate, 17));
            xml.append(cell(displayName(row), widths[1], false, false, alternate, 17));
            for (int lessonIndex = firstColumn; lessonIndex < endColumn; lessonIndex++) {
                Column column = model.columns().get(lessonIndex);
                var mark = row.cellsByLessonId().get(column.lessonId());
                // A missing lesson key remains blank; absence is never inferred by the renderer.
                String symbol = mark == null ? "" : (mark.symbol().isBlank() ? "·" : mark.symbol());
                xml.append(cell(symbol, widths[lessonIndex - firstColumn + 2], false, true, alternate, 19));
            }
            xml.append("</w:tr>");
        }
        xml.append("</w:tbl>");
        return xml.toString();
    }

    private static String summary(TeacherAttendanceExportModel model) {
        StringBuilder xml = new StringBuilder(PAGE_BREAK);
        xml.append(paragraph("Итоги по студентам", true, false, 20, true));
        xml.append(paragraph(summaryContext(model), false, false, 18, true));

        String[] headings = {
                "Студент", "Присутствовал", "Уважительное\nотсутствие", "Отсутствовал",
                "Присутствовал\nили уважительно", "Всего\nзанятий", "% присутствия",
                "% присутствия\nили уважительно", "% уважительных\nотсутствий", "% отсутствий"
        };
        int[] widths = summaryWidths();
        xml.append(tableStart(widths)).append(rowStart(true));
        for (int index = 0; index < headings.length; index++) {
            xml.append(cell(headings[index], widths[index], true, index > 0, false, 13));
        }
        xml.append("</w:tr>");

        for (int rowIndex = 0; rowIndex < model.rows().size(); rowIndex++) {
            Row row = model.rows().get(rowIndex);
            Metrics metrics = row.metrics().orElseThrow(() ->
                    new ReportValidationException("Subject-journal row has no server-supplied metrics"));
            boolean alternate = rowIndex % 2 == 1;
            String[] values = {
                    displayName(row),
                    Integer.toString(metrics.presentCount()),
                    Integer.toString(metrics.excusedCount()),
                    Integer.toString(metrics.absentCount()),
                    Integer.toString(metrics.presentOrExcusedCount()),
                    Integer.toString(metrics.denominator()),
                    percent(metrics.percentPresent(), metrics.denominator()),
                    percent(metrics.percentPresentOrExcused(), metrics.denominator()),
                    percent(metrics.percentExcused(), metrics.denominator()),
                    percent(metrics.percentAbsent(), metrics.denominator())
            };
            xml.append(rowStart(false));
            for (int index = 0; index < values.length; index++) {
                xml.append(cell(values[index], widths[index], false, index > 0, alternate, 15));
            }
            xml.append("</w:tr>");
        }
        xml.append("</w:tbl>");
        return xml.toString();
    }

    private static String summaryContext(TeacherAttendanceExportModel model) {
        Context context = model.context();
        String scope = context.kind() == ReportKind.WEEKLY_ATTENDANCE
                ? "неделя " + context.periodFrom().format(DATE) + "–" + context.periodTo().format(DATE)
                : context.subjectLabel() + ", " + context.periodFrom().format(DATE) + "–" + context.periodTo().format(DATE);
        return "Сводка по группе «" + context.groupLabel() + "», " + scope
                + "; типы занятий: " + String.join(", ", context.typeLabels());
    }

    private static String lessonHeader(Column column) {
        List<String> lines = new ArrayList<>();
        lines.add(column.date().format(DATE));
        if (column.startTime() != null) {
            lines.add(column.startTime().format(TIME));
        }
        if (column.lessonNumber() > 0) {
            lines.add("№" + column.lessonNumber());
        }
        addIfPresent(lines, column.subjectLabel());
        addIfPresent(lines, column.typeLabel().isBlank() ? column.lessonType() : column.typeLabel());
        addIfPresent(lines, stateLabel(column.state()));
        return String.join("\n", lines);
    }

    private static String stateLabel(String state) {
        return switch (state.toUpperCase(Locale.ROOT)) {
            case "PLANNED" -> "Запланировано";
            case "ACTIVE" -> "Идёт";
            case "CLOSED" -> "Проведено";
            case "CANCELLED" -> "Отменено";
            default -> "";
        };
    }

    private static void addIfPresent(List<String> lines, String value) {
        if (value != null && !value.isBlank()) {
            lines.add(value);
        }
    }

    private static String displayName(Row row) {
        return row.displayName().isBlank() ? "Имя не указано" : row.displayName();
    }

    private static String percent(BigDecimal value, int denominator) {
        if (denominator == 0) {
            return "—";
        }
        return value.stripTrailingZeros().toPlainString().replace('.', ',') + "%";
    }

    private static int[] matrixWidths(int lessonCount) {
        if (lessonCount == 0) {
            return new int[]{MATRIX_NUMBER_WIDTH, MATRIX_WIDTH - MATRIX_NUMBER_WIDTH};
        }
        int[] widths = new int[lessonCount + 2];
        widths[0] = MATRIX_NUMBER_WIDTH;
        widths[1] = MATRIX_NAME_WIDTH;
        int lessonWidth = (MATRIX_WIDTH - MATRIX_NUMBER_WIDTH - MATRIX_NAME_WIDTH) / lessonCount;
        for (int index = 2; index < widths.length; index++) {
            widths[index] = lessonWidth;
        }
        widths[widths.length - 1] += MATRIX_WIDTH - sum(widths);
        return widths;
    }

    private static int[] summaryWidths() {
        int[] widths = new int[10];
        widths[0] = SUMMARY_NAME_WIDTH;
        int metricWidth = (MATRIX_WIDTH - SUMMARY_NAME_WIDTH) / (widths.length - 1);
        for (int index = 1; index < widths.length; index++) {
            widths[index] = metricWidth;
        }
        widths[widths.length - 1] += MATRIX_WIDTH - sum(widths);
        return widths;
    }

    private static int sum(int[] values) {
        int sum = 0;
        for (int value : values) {
            sum += value;
        }
        return sum;
    }

    private static String tableStart(int[] widths) {
        StringBuilder grid = new StringBuilder();
        for (int width : widths) {
            grid.append("<w:gridCol w:w=\"").append(width).append("\"/>");
        }
        int width = sum(widths);
        return "<w:tbl><w:tblPr><w:tblW w:w=\"" + width + "\" w:type=\"dxa\"/>"
                + "<w:tblLayout w:type=\"fixed\"/><w:tblBorders>"
                + border("top") + border("left") + border("bottom") + border("right")
                + border("insideH") + border("insideV")
                + "</w:tblBorders><w:tblCellMar><w:top w:w=\"70\" w:type=\"dxa\"/>"
                + "<w:left w:w=\"85\" w:type=\"dxa\"/><w:bottom w:w=\"70\" w:type=\"dxa\"/>"
                + "<w:right w:w=\"85\" w:type=\"dxa\"/></w:tblCellMar></w:tblPr><w:tblGrid>"
                + grid + "</w:tblGrid>";
    }

    private static String border(String edge) {
        return "<w:" + edge + " w:val=\"single\" w:sz=\"4\" w:space=\"0\" w:color=\"D9D9D9\"/>";
    }

    private static String rowStart(boolean header) {
        return "<w:tr><w:trPr>" + (header ? "<w:tblHeader w:val=\"true\"/>" : "")
                + "<w:cantSplit/></w:trPr>";
    }

    private static String cell(String value, int width, boolean header, boolean centered,
                               boolean alternate, int fontHalfPoints) {
        String fill = header ? "DDEBF7" : alternate ? "F4F8FB" : "FFFFFF";
        StringBuilder text = new StringBuilder();
        String[] lines = Objects.requireNonNullElse(value, "").split("\\n", -1);
        for (int index = 0; index < lines.length; index++) {
            if (index > 0) {
                text.append("<w:br/>");
            }
            text.append("<w:t xml:space=\"preserve\">").append(escapeXml(lines[index])).append("</w:t>");
        }
        return "<w:tc><w:tcPr><w:tcW w:w=\"" + width + "\" w:type=\"dxa\"/>"
                + "<w:shd w:val=\"clear\" w:color=\"auto\" w:fill=\"" + fill + "\"/>"
                + "<w:vAlign w:val=\"center\"/></w:tcPr><w:p><w:pPr>"
                + "<w:spacing w:before=\"0\" w:after=\"0\" w:line=\"220\" w:lineRule=\"auto\"/>"
                + (centered ? "<w:jc w:val=\"center\"/>" : "")
                + "</w:pPr><w:r><w:rPr><w:rFonts w:ascii=\"Arial\" w:hAnsi=\"Arial\" w:eastAsia=\"Arial\"/>"
                + (header ? "<w:b/>" : "") + "<w:sz w:val=\"" + fontHalfPoints + "\"/>"
                + "<w:szCs w:val=\"" + fontHalfPoints + "\"/></w:rPr>" + text
                + "</w:r></w:p></w:tc>";
    }

    private static String paragraph(String text, boolean bold, boolean centered, int fontHalfPoints,
                                    boolean keepWithNext) {
        return "<w:p><w:pPr><w:spacing w:before=\"140\" w:after=\"100\"/>"
                + (centered ? "<w:jc w:val=\"center\"/>" : "")
                + (keepWithNext ? "<w:keepNext/>" : "")
                + "</w:pPr><w:r><w:rPr><w:rFonts w:ascii=\"Arial\" w:hAnsi=\"Arial\" w:eastAsia=\"Arial\"/>"
                + (bold ? "<w:b/>" : "") + "<w:sz w:val=\"" + fontHalfPoints + "\"/>"
                + "<w:szCs w:val=\"" + fontHalfPoints + "\"/></w:rPr><w:t xml:space=\"preserve\">"
                + escapeXml(text) + "</w:t></w:r></w:p>";
    }

    private static String replacePlaceholderParagraph(String documentXml, String key, String replacement) {
        String placeholder = "${" + key + "}";
        Matcher matcher = PARAGRAPH.matcher(documentXml);
        StringBuilder output = new StringBuilder(documentXml.length() + replacement.length());
        int matches = 0;
        while (matcher.find()) {
            String paragraph = matcher.group();
            if (paragraph.contains(placeholder)) {
                matcher.appendReplacement(output, Matcher.quoteReplacement(replacement));
                matches++;
            }
        }
        matcher.appendTail(output);
        if (matches != 1) {
            throw new ReportValidationException("DOCX template must contain one ${" + key + "} paragraph");
        }
        return output.toString();
    }

    private static String replacePreviousTitleAndPlaceholderParagraph(String documentXml, String title,
                                                                      String key, String replacement) {
        String placeholder = "${" + key + "}";
        Matcher matcher = PARAGRAPH.matcher(documentXml);
        int titleStart = -1;
        int placeholderEnd = -1;
        String previousParagraph = "";
        while (matcher.find()) {
            String paragraph = matcher.group();
            if (paragraph.contains(placeholder)) {
                if (titleStart < 0 || !previousParagraph.contains(title)) {
                    throw new ReportValidationException(
                            "DOCX template must place the " + title + " heading before ${" + key + "}");
                }
                placeholderEnd = matcher.end();
                break;
            }
            titleStart = matcher.start();
            previousParagraph = paragraph;
        }
        if (placeholderEnd < 0) {
            throw new ReportValidationException("DOCX template must contain one ${" + key + "} paragraph");
        }
        return documentXml.substring(0, titleStart) + replacement + documentXml.substring(placeholderEnd);
    }

    private static String mergeDocumentXml(List<String> renderedDocuments) {
        String base = renderedDocuments.get(0);
        StringBuilder body = new StringBuilder();
        for (int index = 0; index < renderedDocuments.size(); index++) {
            if (index > 0) {
                body.append(PAGE_BREAK);
            }
            body.append(extractBodyContent(renderedDocuments.get(index)));
        }
        return replaceBodyContent(base, body.toString());
    }

    private static String extractBodyContent(String documentXml) {
        int bodyStart = documentXml.indexOf("<w:body");
        int bodyOpenEnd = documentXml.indexOf('>', bodyStart) + 1;
        int sectionStart = documentXml.lastIndexOf("<w:sectPr");
        int bodyEnd = documentXml.indexOf("</w:body>", bodyOpenEnd);
        if (bodyStart < 0 || bodyOpenEnd <= 0 || bodyEnd < 0) {
            throw new ReportValidationException("DOCX document.xml body is malformed");
        }
        int contentEnd = sectionStart > bodyOpenEnd ? sectionStart : bodyEnd;
        return documentXml.substring(bodyOpenEnd, contentEnd);
    }

    private static String replaceBodyContent(String documentXml, String bodyContent) {
        int bodyStart = documentXml.indexOf("<w:body");
        int bodyOpenEnd = documentXml.indexOf('>', bodyStart) + 1;
        int sectionStart = documentXml.lastIndexOf("<w:sectPr");
        int bodyEnd = documentXml.indexOf("</w:body>", bodyOpenEnd);
        if (bodyStart < 0 || bodyOpenEnd <= 0 || bodyEnd < 0) {
            throw new ReportValidationException("DOCX document.xml body is malformed");
        }
        int contentEnd = sectionStart > bodyOpenEnd ? sectionStart : bodyEnd;
        return documentXml.substring(0, bodyOpenEnd) + bodyContent + documentXml.substring(contentEnd);
    }

    private static TemplatePackage readTemplate(String templateResource) {
        InputStream resource = TeacherAttendanceDocxRenderer.class.getClassLoader()
                .getResourceAsStream(templateResource);
        if (resource == null) {
            throw new ReportValidationException("DOCX template not found: " + templateResource);
        }

        Map<String, byte[]> entries = new LinkedHashMap<>();
        String documentXml = null;
        try (InputStream input = resource;
             ZipInputStream zip = new ZipInputStream(input, StandardCharsets.UTF_8)) {
            ZipEntry entry = zip.getNextEntry();
            while (entry != null) {
                if (!entry.isDirectory()) {
                    byte[] bytes = zip.readAllBytes();
                    entries.put(entry.getName(), bytes);
                    if (DOCUMENT_XML.equals(entry.getName())) {
                        documentXml = new String(bytes, StandardCharsets.UTF_8);
                    }
                }
                entry = zip.getNextEntry();
            }
        } catch (IOException ex) {
            throw new ReportValidationException("Failed to read DOCX template: " + ex.getMessage());
        }
        if (documentXml == null) {
            throw new ReportValidationException("DOCX template has no " + DOCUMENT_XML);
        }
        return new TemplatePackage(entries, documentXml);
    }

    private static byte[] writePackage(Map<String, byte[]> entries, String documentXml) {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream();
             ZipOutputStream zip = new ZipOutputStream(output, StandardCharsets.UTF_8)) {
            for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey()));
                byte[] bytes = DOCUMENT_XML.equals(entry.getKey())
                        ? documentXml.getBytes(StandardCharsets.UTF_8)
                        : entry.getValue();
                zip.write(bytes);
                zip.closeEntry();
            }
            zip.finish();
            return output.toByteArray();
        } catch (IOException ex) {
            throw new ReportValidationException("Failed to write DOCX report: " + ex.getMessage());
        }
    }

    static String readDocumentXml(byte[] docx) {
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(docx), StandardCharsets.UTF_8)) {
            ZipEntry entry = zip.getNextEntry();
            while (entry != null) {
                if (DOCUMENT_XML.equals(entry.getName())) {
                    return new String(zip.readAllBytes(), StandardCharsets.UTF_8);
                }
                entry = zip.getNextEntry();
            }
        } catch (IOException ex) {
            throw new IllegalArgumentException("Failed to read generated DOCX", ex);
        }
        throw new IllegalArgumentException(DOCUMENT_XML + " not found in generated DOCX");
    }

    private static String escapeXml(String value) {
        String source = Objects.requireNonNullElse(value, "");
        StringBuilder result = new StringBuilder(source.length());
        source.codePoints().filter(TeacherAttendanceDocxRenderer::isXml10CodePoint).forEach(codePoint -> {
            switch (codePoint) {
                case '&' -> result.append("&amp;");
                case '<' -> result.append("&lt;");
                case '>' -> result.append("&gt;");
                case '\"' -> result.append("&quot;");
                case '\'' -> result.append("&apos;");
                default -> result.appendCodePoint(codePoint);
            }
        });
        return result.toString();
    }

    private static boolean isXml10CodePoint(int codePoint) {
        return codePoint == 0x9 || codePoint == 0xA || codePoint == 0xD
                || codePoint >= 0x20 && codePoint <= 0xD7FF
                || codePoint >= 0xE000 && codePoint <= 0xFFFD
                || codePoint >= 0x10000 && codePoint <= 0x10FFFF;
    }

    private record TemplatePackage(Map<String, byte[]> entries, String documentXml) {
    }
}
