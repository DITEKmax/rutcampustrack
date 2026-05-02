package ru.rutcampustrack.attendance.report;

import org.springframework.stereotype.Component;
import ru.rutcampustrack.attendance.exception.ReportValidationException;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

@Component
public class DocxRenderer {

    private static final String TEMPLATE_RESOURCE = "report-templates/headman-weekly.docx";
    private static final String DOCUMENT_XML = "word/document.xml";
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd.MM.uuuu");
    private static final Pattern PLACEHOLDER = Pattern.compile("\\$\\{[^}]+}");
    private static final List<String> DAYS = List.of("mon", "tue", "wed", "thu", "fri", "sat");
    private static final String PAGE_BREAK = "<w:p><w:r><w:br w:type=\"page\"/></w:r></w:p>";
    private static final String TEMPLATE_NUMBER_GRID_WIDTH = "<w:gridCol w:w=\"394\" />";
    private static final String FITTED_NUMBER_GRID_WIDTH = "<w:gridCol w:w=\"560\" />";
    private static final String TEMPLATE_NUMBER_CELL_WIDTH = "<w:tcW w:w=\"340\" w:type=\"dxa\" />";
    private static final String FITTED_NUMBER_CELL_WIDTH = "<w:tcW w:w=\"560\" w:type=\"dxa\" />";

    public byte[] render(List<HeadmanWeeklyReportModel> models) {
        if (models == null || models.isEmpty()) {
            throw new ReportValidationException("At least one report model is required");
        }
        TemplatePackage template = readTemplate();
        List<String> renderedDocuments = models.stream()
                .map(model -> renderDocumentXml(template.documentXml(), model))
                .toList();

        String documentXml = renderedDocuments.size() == 1
                ? renderedDocuments.get(0)
                : mergeDocumentXml(renderedDocuments);
        return writePackage(template.entries(), fitLibreOfficeGeometry(documentXml));
    }

    private static String renderDocumentXml(String documentXml, HeadmanWeeklyReportModel model) {
        String renderedXml = documentXml;
        for (Map.Entry<String, String> entry : valuesFor(model).entrySet()) {
            renderedXml = renderedXml.replace("${" + entry.getKey() + "}", escapeXml(entry.getValue()));
        }
        var matcher = PLACEHOLDER.matcher(renderedXml);
        if (matcher.find()) {
            throw new ReportValidationException("DOCX template still contains placeholder " + matcher.group());
        }
        return renderedXml;
    }

    private static Map<String, String> valuesFor(HeadmanWeeklyReportModel model) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("group_header", nullToEmpty(model.groupHeader()));

        for (int dayIndex = 0; dayIndex < DAYS.size(); dayIndex++) {
            String dayCode = DAYS.get(dayIndex);
            LocalDate date = model.weekStart().plusDays(dayIndex);
            values.put("date_" + dayCode, date.format(DATE_FORMAT));
            for (int lesson = 1; lesson <= HeadmanWeeklyReportModel.MAX_LESSONS_PER_DAY; lesson++) {
                values.put("subject_" + dayCode + "_" + lesson, "");
                values.put("type_" + dayCode + "_" + lesson, "");
            }
        }

        for (HeadmanWeeklyReportModel.Day day : model.days()) {
            int dayIndex = (int) java.time.temporal.ChronoUnit.DAYS.between(model.weekStart(), day.date());
            if (dayIndex < 0 || dayIndex >= DAYS.size()) {
                continue;
            }
            String dayCode = DAYS.get(dayIndex);
            for (HeadmanWeeklyReportModel.LessonSlot lesson : day.lessons()) {
                if (lesson.lessonNumber() < 1
                        || lesson.lessonNumber() > HeadmanWeeklyReportModel.MAX_LESSONS_PER_DAY) {
                    continue;
                }
                values.put("subject_" + dayCode + "_" + lesson.lessonNumber(), nullToEmpty(lesson.subjectName()));
                values.put("type_" + dayCode + "_" + lesson.lessonNumber(), nullToEmpty(lesson.lessonType()));
            }
        }

        for (int studentIndex = 1; studentIndex <= HeadmanWeeklyReportModel.MAX_STUDENTS; studentIndex++) {
            String number = "%02d".formatted(studentIndex);
            values.put("student_" + number + "_name", "");
            for (String day : DAYS) {
                for (int lesson = 1; lesson <= HeadmanWeeklyReportModel.MAX_LESSONS_PER_DAY; lesson++) {
                    values.put("att_" + number + "_" + day + "_" + lesson, "");
                }
            }
        }

        List<HeadmanWeeklyReportModel.Student> students = model.students();
        for (int i = 0; i < students.size(); i++) {
            HeadmanWeeklyReportModel.Student student = students.get(i);
            String number = "%02d".formatted(i + 1);
            values.put("student_" + number + "_name", nullToEmpty(student.displayName()));
            for (HeadmanWeeklyReportModel.AttendanceMark mark : student.attendance()) {
                int dayIndex = (int) java.time.temporal.ChronoUnit.DAYS.between(model.weekStart(), mark.date());
                if (dayIndex < 0
                        || dayIndex >= DAYS.size()
                        || mark.lessonNumber() < 1
                        || mark.lessonNumber() > HeadmanWeeklyReportModel.MAX_LESSONS_PER_DAY) {
                    continue;
                }
                values.put("att_" + number + "_" + DAYS.get(dayIndex) + "_" + mark.lessonNumber(),
                        nullToEmpty(mark.symbol()));
            }
        }

        return values;
    }

    private static String mergeDocumentXml(List<String> renderedDocuments) {
        String base = renderedDocuments.get(0);
        StringBuilder body = new StringBuilder();
        for (int i = 0; i < renderedDocuments.size(); i++) {
            if (i > 0) {
                body.append(PAGE_BREAK);
            }
            body.append(extractBodyContent(renderedDocuments.get(i)));
        }
        return replaceBodyContent(base, body.toString());
    }

    private static String fitLibreOfficeGeometry(String documentXml) {
        return documentXml
                // Keep 10..35 in the number column on one line; otherwise LibreOffice
                // expands those rows and a one-week report spills onto a second page.
                .replace(TEMPLATE_NUMBER_GRID_WIDTH, FITTED_NUMBER_GRID_WIDTH)
                .replace(TEMPLATE_NUMBER_CELL_WIDTH, FITTED_NUMBER_CELL_WIDTH);
    }

    private static String extractBodyContent(String documentXml) {
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
        return documentXml.substring(bodyOpenEnd, contentEnd);
    }

    private static String replaceBodyContent(String documentXml, String bodyContent) {
        int bodyStart = documentXml.indexOf("<w:body");
        int bodyOpenEnd = documentXml.indexOf('>', bodyStart) + 1;
        int sectStart = documentXml.lastIndexOf("<w:sectPr");
        int bodyEnd = documentXml.indexOf("</w:body>", bodyOpenEnd);
        if (bodyStart < 0 || bodyOpenEnd <= 0 || bodyEnd < 0) {
            throw new ReportValidationException("DOCX document.xml body is malformed");
        }
        int contentEnd = sectStart > bodyOpenEnd ? sectStart : bodyEnd;
        return documentXml.substring(0, bodyOpenEnd)
                + bodyContent
                + documentXml.substring(contentEnd);
    }

    private static TemplatePackage readTemplate() {
        InputStream resource = DocxRenderer.class.getClassLoader().getResourceAsStream(TEMPLATE_RESOURCE);
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

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static String escapeXml(String value) {
        return Objects.requireNonNullElse(value, "")
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }

    private record TemplatePackage(Map<String, byte[]> entries, String documentXml) {}
}
