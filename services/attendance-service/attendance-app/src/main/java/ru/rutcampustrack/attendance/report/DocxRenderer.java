package ru.rutcampustrack.attendance.report;

import org.springframework.stereotype.Component;

import ru.rutcampustrack.attendance.exception.ReportValidationException;
import ru.rutcampustrack.attendance.report.teacher.TeacherAttendanceExportModel;
import ru.rutcampustrack.attendance.report.teacher.TeacherAttendanceExportModel.Column;

import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

@Component
public class DocxRenderer {

    private static final String TEMPLATE_RESOURCE = "report-templates/headman-weekly.docx";
    private static final String DOCUMENT_XML = "word/document.xml";
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd.MM.uuuu");
    private static final Pattern PLACEHOLDER = Pattern.compile("\\$\\{([^}]+)}");
    private static final Pattern PARAGRAPH_IDENTIFIERS =
            Pattern.compile("\\s+w14:(?:paraId|textId)=\"[0-9A-Fa-f]{8}\"");
    private static final Pattern LESSON_PLACEHOLDER =
            Pattern.compile("\\$\\{(subject|type)_[a-z]{3}_[1-5]}");
    private static final List<String> DAYS = List.of("mon", "tue", "wed", "thu", "fri", "sat");
    private static final String PAGE_BREAK = "<w:p><w:r><w:br w:type=\"page\"/></w:r></w:p>";
    private static final String TEMPLATE_NUMBER_GRID_WIDTH = "<w:gridCol w:w=\"394\" />";
    private static final String FITTED_NUMBER_GRID_WIDTH = "<w:gridCol w:w=\"560\" />";
    private static final String TEMPLATE_NUMBER_CELL_WIDTH = "<w:tcW w:w=\"340\" w:type=\"dxa\" />";
    private static final String FITTED_NUMBER_CELL_WIDTH = "<w:tcW w:w=\"560\" w:type=\"dxa\" />";
    private static final String CELL_MARGIN_LEFT = "<w:left w:w=\"16\" w:type=\"dxa\" />";
    private static final String CELL_MARGIN_RIGHT = "<w:right w:w=\"16\" w:type=\"dxa\" />";
    private static final String TYPE_CELL_MARGINS =
            "<w:tcMar>" + CELL_MARGIN_LEFT + CELL_MARGIN_RIGHT + "</w:tcMar>";
    private static final String SUBJECT_CELL_MARGINS =
            "<w:tcMar><w:top w:w=\"16\" w:type=\"dxa\" />"
                    + CELL_MARGIN_LEFT
                    + "<w:bottom w:w=\"16\" w:type=\"dxa\" />"
                    + CELL_MARGIN_RIGHT
                    + "</w:tcMar>";
    private static final String SUBJECT_CELL_LAYOUT = "<w:noWrap /><w:textDirection";
    private static final String TEMPLATE_SUBJECT_ROW_HEIGHT = "<w:trHeight w:val=\"1986\" />";
    private static final String TALL_SUBJECT_ROW_HEIGHT = "<w:trHeight w:val=\"2346\" />";
    // At the two header heights, rotated lines have about 97 or 115 points of usable length.
    private static final int SUBJECT_LINE_HALF_POINTS = 194;
    private static final int TALL_SUBJECT_LINE_HALF_POINTS = 231;
    private static final int SUBJECT_SPACE_HALF_POINTS = 5;

    public byte[] render(List<HeadmanWeeklyReportModel> models) {
        return renderTemplate(models, DocxRenderer::valuesFor, false);
    }

    byte[] renderWeekly(List<TeacherAttendanceExportModel> models) {
        boolean tallSubjectRow =
                models != null
                        && models.stream()
                                .flatMap(model -> model.columns().stream())
                                .anyMatch(
                                        column ->
                                                !fitsSubjectCell(
                                                        column.subjectLabel(),
                                                        SUBJECT_LINE_HALF_POINTS));
        return renderTemplate(models, DocxRenderer::weeklyValuesFor, tallSubjectRow);
    }

    static boolean fitsWeeklyTemplate(TeacherAttendanceExportModel model) {
        if (model.rows().size() > HeadmanWeeklyReportModel.MAX_STUDENTS) {
            return false;
        }
        boolean[][] occupied =
                new boolean[DAYS.size()][HeadmanWeeklyReportModel.MAX_LESSONS_PER_DAY];
        for (Column column : model.columns()) {
            long day = ChronoUnit.DAYS.between(model.context().periodFrom(), column.date());
            int number = column.lessonNumber();
            if (day < 0
                    || day >= DAYS.size()
                    || number < 1
                    || number > HeadmanWeeklyReportModel.MAX_LESSONS_PER_DAY
                    || occupied[(int) day][number - 1]
                    || !fitsSubjectCell(column.subjectLabel(), TALL_SUBJECT_LINE_HALF_POINTS)) {
                return false;
            }
            occupied[(int) day][number - 1] = true;
        }
        return true;
    }

    private static boolean fitsSubjectCell(String subject, int maxLineWidth) {
        int lines = 1;
        int lineWidth = 0;
        for (String word : subject.split(" ", -1)) {
            if (word.isEmpty() || word.codePoints().anyMatch(Character::isWhitespace)) {
                return false;
            }
            int width = word.codePoints().map(DocxRenderer::estimatedGlyphHalfPoints).sum();
            if (width > maxLineWidth) {
                return false;
            }
            int joined = lineWidth + SUBJECT_SPACE_HALF_POINTS + width;
            if (lineWidth == 0 || joined <= maxLineWidth) {
                lineWidth = lineWidth == 0 ? width : joined;
            } else {
                lineWidth = width;
                if (++lines > 2) {
                    return false;
                }
            }
        }
        return true;
    }

    private static int estimatedGlyphHalfPoints(int codePoint) {
        // Conservative estimate for the template's 8-point font, without a runtime font lookup.
        if ("ЖШЩЫЮWфщ".indexOf(codePoint) >= 0 || codePoint > 'я' && codePoint != 'ё') {
            return 18;
        }
        if (Character.isUpperCase(codePoint) || "жмшыюmw".indexOf(codePoint) >= 0) {
            return 13;
        }
        return "гзкстухijlfrt".indexOf(codePoint) >= 0 ? 8 : 9;
    }

    private static <T> byte[] renderTemplate(
            List<T> models, Function<T, Map<String, String>> valuesForModel, boolean tallSubjectRow) {
        if (models == null || models.isEmpty()) {
            throw new ReportValidationException("At least one report model is required");
        }
        TemplatePackage template = readTemplate();
        String fittedXml =
                tallSubjectRow
                        ? template.documentXml()
                                .replace(TEMPLATE_SUBJECT_ROW_HEIGHT, TALL_SUBJECT_ROW_HEIGHT)
                        : template.documentXml();
        // Copied weekly bodies cannot share Word paragraph identifiers.
        String templateXml =
                models.size() == 1
                        ? fittedXml
                        : PARAGRAPH_IDENTIFIERS.matcher(fittedXml).replaceAll("");
        return writePackage(
                template.entries(),
                writer -> writeDocumentXml(writer, templateXml, models, valuesForModel));
    }

    private static String renderDocumentXml(String documentXml, Map<String, String> values) {
        return PLACEHOLDER
                .matcher(documentXml)
                .replaceAll(
                        match -> {
                            String value = values.get(match.group(1));
                            if (value == null) {
                                throw new ReportValidationException(
                                        "DOCX template still contains placeholder " + match.group());
                            }
                            return Matcher.quoteReplacement(escapeXml(value));
                        });
    }

    private static Map<String, String> valuesFor(HeadmanWeeklyReportModel model) {
        Map<String, String> values = blankValues(model.groupHeader(), model.weekStart());

        for (HeadmanWeeklyReportModel.Day day : model.days()) {
            int dayIndex = (int) ChronoUnit.DAYS.between(model.weekStart(), day.date());
            if (dayIndex < 0 || dayIndex >= DAYS.size()) {
                continue;
            }
            String dayCode = DAYS.get(dayIndex);
            for (HeadmanWeeklyReportModel.LessonSlot lesson : day.lessons()) {
                if (lesson.lessonNumber() < 1
                        || lesson.lessonNumber() > HeadmanWeeklyReportModel.MAX_LESSONS_PER_DAY) {
                    continue;
                }
                values.put(
                        "subject_" + dayCode + "_" + lesson.lessonNumber(),
                        nullToEmpty(lesson.subjectName()));
                values.put(
                        "type_" + dayCode + "_" + lesson.lessonNumber(),
                        nullToEmpty(lesson.lessonType()));
            }
        }

        List<HeadmanWeeklyReportModel.Student> students = model.students();
        for (int i = 0; i < students.size(); i++) {
            HeadmanWeeklyReportModel.Student student = students.get(i);
            String number = "%02d".formatted(i + 1);
            values.put("student_" + number + "_name", nullToEmpty(student.displayName()));
            for (HeadmanWeeklyReportModel.AttendanceMark mark : student.attendance()) {
                int dayIndex = (int) ChronoUnit.DAYS.between(model.weekStart(), mark.date());
                if (dayIndex < 0
                        || dayIndex >= DAYS.size()
                        || mark.lessonNumber() < 1
                        || mark.lessonNumber() > HeadmanWeeklyReportModel.MAX_LESSONS_PER_DAY) {
                    continue;
                }
                values.put(
                        "att_" + number + "_" + DAYS.get(dayIndex) + "_" + mark.lessonNumber(),
                        nullToEmpty(mark.symbol()));
            }
        }

        return values;
    }

    private static Map<String, String> weeklyValuesFor(TeacherAttendanceExportModel model) {
        var context = model.context();
        String groupHeader = "Группа: " + context.groupLabel() + ", " + context.semesterLabel();
        if (model.columns().isEmpty()) {
            groupHeader += " · Нет данных";
        }
        LocalDate weekStart = context.periodFrom();
        Map<String, String> values = blankValues(groupHeader, weekStart);
        Map<Long, String> slotByLesson = new LinkedHashMap<>();
        for (Column column : model.columns()) {
            String day = DAYS.get((int) ChronoUnit.DAYS.between(weekStart, column.date()));
            String slot = day + "_" + column.lessonNumber();
            slotByLesson.put(column.lessonId(), slot);
            values.put("subject_" + slot, column.subjectLabel());
            values.put("type_" + slot, HeadmanTabularAttendance.shortType(column));
        }
        for (int index = 0; index < model.rows().size(); index++) {
            var row = model.rows().get(index);
            String number = "%02d".formatted(index + 1);
            values.put("student_" + number + "_name", row.displayName());
            row.cellsByLessonId()
                    .forEach(
                            (lessonId, cell) ->
                                    values.put(
                                            "att_" + number + "_" + slotByLesson.get(lessonId),
                                            cell.symbol()));
        }
        return values;
    }

    private static Map<String, String> blankValues(String groupHeader, LocalDate weekStart) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("group_header", nullToEmpty(groupHeader));
        for (int dayIndex = 0; dayIndex < DAYS.size(); dayIndex++) {
            String day = DAYS.get(dayIndex);
            values.put("date_" + day, weekStart.plusDays(dayIndex).format(DATE_FORMAT));
            for (int lesson = 1; lesson <= HeadmanWeeklyReportModel.MAX_LESSONS_PER_DAY; lesson++) {
                values.put("subject_" + day + "_" + lesson, "");
                values.put("type_" + day + "_" + lesson, "");
            }
        }
        for (int studentIndex = 1;
                studentIndex <= HeadmanWeeklyReportModel.MAX_STUDENTS;
                studentIndex++) {
            String number = "%02d".formatted(studentIndex);
            values.put("student_" + number + "_name", "");
            for (String day : DAYS) {
                for (int lesson = 1;
                        lesson <= HeadmanWeeklyReportModel.MAX_LESSONS_PER_DAY;
                        lesson++) {
                    values.put("att_" + number + "_" + day + "_" + lesson, "");
                }
            }
        }
        return values;
    }

    private static <T> void writeDocumentXml(
            Writer writer,
            String templateXml,
            List<T> models,
            Function<T, Map<String, String>> valuesForModel)
            throws IOException {
        String first = renderDocumentXml(templateXml, valuesForModel.apply(models.get(0)));
        if (models.size() == 1) {
            writer.write(first);
            return;
        }
        BodyBounds firstBody = bodyBounds(first);
        writer.write(first, 0, firstBody.start());
        writer.write(first, firstBody.start(), firstBody.end() - firstBody.start());
        for (int index = 1; index < models.size(); index++) {
            writer.write(PAGE_BREAK);
            // Keep only the current week, rather than all rendered weeks and merged copies.
            String week = renderDocumentXml(templateXml, valuesForModel.apply(models.get(index)));
            BodyBounds body = bodyBounds(week);
            writer.write(week, body.start(), body.end() - body.start());
        }
        // The first document owns namespaces and the single final section definition.
        writer.write(first, firstBody.end(), first.length() - firstBody.end());
    }

    private static String fitLibreOfficeGeometry(String documentXml) {
        // Keep 10..35 in the number column on one line; otherwise LibreOffice
        // expands those rows and a one-week report spills onto a second page.
        String fittedXml =
                documentXml
                        .replace(TEMPLATE_NUMBER_GRID_WIDTH, FITTED_NUMBER_GRID_WIDTH)
                        .replace(TEMPLATE_NUMBER_CELL_WIDTH, FITTED_NUMBER_CELL_WIDTH);
        var placeholders = LESSON_PLACEHOLDER.matcher(fittedXml);
        StringBuilder result = new StringBuilder(fittedXml.length());
        int offset = 0;
        while (placeholders.find()) {
            int cellStart = fittedXml.lastIndexOf("<w:tc>", placeholders.start());
            boolean subject = "subject".equals(placeholders.group(1));
            String anchor = subject ? SUBJECT_CELL_LAYOUT : "<w:vAlign ";
            int layout = fittedXml.lastIndexOf(anchor, placeholders.start());
            if (layout <= cellStart) {
                throw new ReportValidationException("DOCX lesson cell has no expected layout");
            }
            result.append(fittedXml, offset, layout)
                    .append(subject ? SUBJECT_CELL_MARGINS : TYPE_CELL_MARGINS);
            // Subject labels can wrap into two rotated lines. Type labels stay on one line.
            offset = subject ? layout + "<w:noWrap />".length() : layout;
        }
        return result.append(fittedXml, offset, fittedXml.length()).toString();
    }

    private static BodyBounds bodyBounds(String documentXml) {
        int bodyStart = documentXml.indexOf("<w:body");
        if (bodyStart < 0) {
            throw new ReportValidationException("DOCX document.xml has no w:body");
        }
        int bodyOpenEnd = documentXml.indexOf('>', bodyStart) + 1;
        int sectStart = documentXml.lastIndexOf("<w:sectPr");
        int bodyEnd = documentXml.indexOf("</w:body>", bodyOpenEnd);
        if (bodyOpenEnd <= 0 || bodyEnd < 0) {
            throw new ReportValidationException("DOCX document.xml body is malformed");
        }
        int contentEnd = sectStart > bodyOpenEnd ? sectStart : bodyEnd;
        return new BodyBounds(bodyOpenEnd, contentEnd);
    }

    private record BodyBounds(int start, int end) {}

    private static TemplatePackage readTemplate() {
        InputStream resource =
                DocxRenderer.class.getClassLoader().getResourceAsStream(TEMPLATE_RESOURCE);
        if (resource == null) {
            throw new ReportValidationException("DOCX template not found: " + TEMPLATE_RESOURCE);
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
        return new TemplatePackage(entries, fitLibreOfficeGeometry(documentXml));
    }

    private static byte[] writePackage(Map<String, byte[]> entries, DocumentXmlWriter documentXml) {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream();
                ZipOutputStream zip = new ZipOutputStream(output, StandardCharsets.UTF_8)) {
            for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey()));
                if (DOCUMENT_XML.equals(entry.getKey())) {
                    Writer writer =
                            new BufferedWriter(new OutputStreamWriter(zip, StandardCharsets.UTF_8));
                    documentXml.write(writer);
                    // Closing the writer here would close the archive before its other entries.
                    writer.flush();
                } else {
                    zip.write(entry.getValue());
                }
                zip.closeEntry();
            }
            zip.finish();
            return output.toByteArray();
        } catch (IOException ex) {
            throw new ReportValidationException("Failed to write DOCX report: " + ex.getMessage());
        }
    }

    @FunctionalInterface
    private interface DocumentXmlWriter {
        void write(Writer writer) throws IOException;
    }

    static String readDocumentXml(byte[] docx) {
        try (ZipInputStream zip =
                new ZipInputStream(new ByteArrayInputStream(docx), StandardCharsets.UTF_8)) {
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

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static String escapeXml(String value) {
        StringBuilder xmlText = new StringBuilder(value.length());
        value.codePoints().filter(DocxRenderer::isXml10CodePoint).forEach(xmlText::appendCodePoint);
        return xmlText.toString()
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }

    private static boolean isXml10CodePoint(int codePoint) {
        return codePoint == 0x9
                || codePoint == 0xA
                || codePoint == 0xD
                || codePoint >= 0x20 && codePoint <= 0xD7FF
                || codePoint >= 0xE000 && codePoint <= 0xFFFD
                || codePoint >= 0x10000 && codePoint <= 0x10FFFF;
    }

    private record TemplatePackage(Map<String, byte[]> entries, String documentXml) {}
}
