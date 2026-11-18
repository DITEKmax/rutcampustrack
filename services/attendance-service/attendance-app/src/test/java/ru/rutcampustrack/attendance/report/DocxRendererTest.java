package ru.rutcampustrack.attendance.report;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.xml.sax.Attributes;
import org.xml.sax.helpers.DefaultHandler;

import ru.rutcampustrack.attendance.contract.enums.AttendanceStatus;
import ru.rutcampustrack.attendance.report.teacher.TeacherAttendanceExportModel;
import ru.rutcampustrack.attendance.report.teacher.TeacherAttendanceExportModel.Cell;
import ru.rutcampustrack.attendance.report.teacher.TeacherAttendanceExportModel.Column;
import ru.rutcampustrack.attendance.report.teacher.TeacherAttendanceExportModel.Context;
import ru.rutcampustrack.attendance.report.teacher.TeacherAttendanceExportModel.ReportKind;
import ru.rutcampustrack.attendance.report.teacher.TeacherAttendanceExportModel.Row;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import javax.xml.parsers.SAXParserFactory;

class DocxRendererTest {

    private final DocxRenderer renderer = new DocxRenderer();

    @Test
    void weeklySlotsCompactSparseAndDuplicateNumbersAndKeepEveryLessonIdMark() throws Exception {
        LocalDate start = LocalDate.of(2026, 4, 27);
        List<Column> columns =
                List.of(
                        weeklyColumn(15, start, "12:00", 7),
                        weeklyColumn(14, start, "11:00", 3),
                        weeklyColumn(13, start, "11:00", 3),
                        weeklyColumn(12, start, "11:00", 2),
                        weeklyColumn(31, start.plusDays(2), "08:00", 7),
                        weeklyColumn(11, start, "09:00", 6));
        Map<Long, Cell> first = new LinkedHashMap<>();
        Map<Long, Cell> second = new LinkedHashMap<>();
        for (long id : List.of(11L, 12L, 13L, 14L, 15L, 31L)) {
            first.put(id, new Cell("A" + id, AttendanceStatus.PRESENT, true));
            second.put(id, new Cell("B" + id, AttendanceStatus.ABSENT, true));
        }
        var model =
                new TeacherAttendanceExportModel(
                        weeklyContext(start),
                        columns,
                        List.of(
                                new Row(1, "Student A", first, Optional.empty()),
                                new Row(2, "Student B", second, Optional.empty())));

        assertThat(DocxRenderer.fitsWeeklyTemplate(model)).isTrue();
        var rows =
                ExportTableReader.wordRows(
                        DocxRenderer.readDocumentXml(renderer.renderWeekly(List.of(model))));

        assertThat(rows).hasSize(40);
        assertThat(rows.get(2).subList(2, 7))
                .containsExactly("Lesson 11", "Lesson 12", "Lesson 13", "Lesson 14", "Lesson 15");
        assertThat(rows.get(2).subList(7, 12)).containsOnly("");
        assertThat(rows.get(2).get(12)).isEqualTo("Lesson 31");
        assertThat(rows.get(4).subList(2, 7))
                .containsExactly("A11", "A12", "A13", "A14", "A15");
        assertThat(rows.get(5).subList(2, 7))
                .containsExactly("B11", "B12", "B13", "B14", "B15");
        assertThat(rows.get(4).get(12)).isEqualTo("A31");
        assertThat(rows.get(5).get(12)).isEqualTo("B31");
        assertThat(model.columns()).containsExactlyElementsOf(columns);
    }

    private static Column weeklyColumn(long id, LocalDate date, String time, int number) {
        return new Column(
                id, date, LocalTime.parse(time), number, 5, "Lesson " + id,
                "lecture", "Лекция", "CLOSED");
    }

    private static Context weeklyContext(LocalDate start) {
        return new Context(
                ReportKind.WEEKLY_ATTENDANCE, 1, "Spring", 10, "Group", null, "",
                start, start.plusDays(6), List.of("Лекция"), Instant.EPOCH);
    }

    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {
                "Math|1986",
                "Проектирование цифровых платформ и сервисов|2346",
                "Системы искусственного интеллекта и машинное обучение|2706"
            })
    void weeklySubjectHeaderUsesSmallestSufficientHeight(String subject, int height) throws Exception {
        LocalDate start = LocalDate.of(2026, 4, 27);
        var model =
                new TeacherAttendanceExportModel(
                        weeklyContext(start),
                        List.of(
                                new Column(
                                        11, start, LocalTime.of(9, 0), 7, 5, subject,
                                        "lecture", "Лекция", "CLOSED")),
                        List.of());

        assertThat(DocxRenderer.fitsWeeklyTemplate(model)).isTrue();
        String xml = DocxRenderer.readDocumentXml(renderer.renderWeekly(List.of(model)));
        assertThat(xml).contains("<w:trHeight w:val=\"" + height + "\" />");
        assertThat(ExportTableReader.wordRows(xml).get(2).get(2)).isEqualTo(subject);
    }

    @Test
    void renderSingleWeekReplacesEveryPlaceholder() throws Exception {
        byte[] docx =
                renderer.render(List.of(model(LocalDate.of(2026, 4, 27), "Math", "\u041b\u041a")));
        writeQaArtifact("headman-weekly-single.docx", docx);

        String documentXml = DocxRenderer.readDocumentXml(docx);

        assertThat(documentXml).doesNotContain("${").contains("w14:paraId=");
        assertThat(documentXml.split("<w:tcMar>", -1)).hasSize(61);
        assertThat(documentXml).doesNotContain("<w:noWrap /><w:textDirection");
        assertThat(documentXml)
                .contains("UVPV511")
                .contains("Math")
                .contains("\u041b\u041a")
                .contains("Student 01")
                .contains("+");
        DocumentContents contents = readDocumentContents(docx);
        assertThat(contents.tables).isEqualTo(1);
        assertThat(contents.pageBreaks).isZero();
        assertThat(contents.sections).isEqualTo(1);
    }

    @Test
    void renderMultipleWeeksCreatesOneDocumentWithPageBreaks() throws Exception {
        byte[] docx =
                renderer.render(
                        List.of(
                                model(LocalDate.of(2026, 4, 27), "Math", "\u041b\u041a"),
                                model(LocalDate.of(2026, 5, 11), "Physics", "\u041f\u0417")));
        writeQaArtifact("headman-weekly-multi.docx", docx);

        String documentXml = DocxRenderer.readDocumentXml(docx);

        assertThat(documentXml).doesNotContain("${", "w14:paraId=", "w14:textId=");
        assertThat(documentXml).contains("w:type=\"page\"");
        assertThat(documentXml).contains("Math").contains("Physics");
        DocumentContents contents = readDocumentContents(docx);
        assertThat(contents.tables).isEqualTo(2);
        assertThat(contents.pageBreaks).isEqualTo(1);
        assertThat(contents.sections).isEqualTo(1);
    }

    @Test
    void renderFullSemesterPreservesEveryWeekRowAndTemplateResource() throws Exception {
        List<HeadmanWeeklyReportModel> models = new ArrayList<>();
        for (int week = 1; week <= 18; week++) {
            models.add(fullWeek(week));
        }

        byte[] docx = renderer.render(models);
        DocumentContents contents = readDocumentContents(docx);

        assertThat(contents.tables).isEqualTo(18);
        assertThat(contents.pageBreaks).isEqualTo(17);
        assertThat(contents.sections).isEqualTo(1);
        assertThat(contents.texts).noneMatch(text -> text.contains("${"));
        assertThat(contents.texts.stream().filter("+"::equals).count()).isEqualTo(18L * 35 * 6 * 5);
        int previousLastStudent = -1;
        for (int week = 1; week <= 18; week++) {
            int firstStudent = contents.texts.indexOf(studentName(week, 1));
            int lastStudent = contents.texts.indexOf(studentName(week, 35));
            assertThat(firstStudent).isGreaterThan(previousLastStudent);
            assertThat(lastStudent).isGreaterThan(firstStudent);
            previousLastStudent = lastStudent;
            for (int student = 1; student <= 35; student++) {
                String name = studentName(week, student);
                assertThat(contents.texts.stream().filter(name::equals).count()).isEqualTo(1);
            }
            for (int day = 1; day <= 6; day++) {
                for (int lesson = 1; lesson <= 5; lesson++) {
                    String subject = subjectName(week, day, lesson);
                    assertThat(contents.texts.stream().filter(subject::equals).count())
                            .isEqualTo(1);
                }
            }
        }
        assertThat(contents.texts).contains("27.04.2026", "29.08.2026", "W18S35 <&>");

        byte[] template;
        try (var input =
                DocxRenderer.class
                        .getClassLoader()
                        .getResourceAsStream("report-templates/headman-weekly.docx")) {
            assertThat(input).isNotNull();
            template = input.readAllBytes();
        }
        PackageContents expected = readPackageContents(template);
        PackageContents actual = readPackageContents(docx);
        assertThat(actual.entries()).containsExactlyElementsOf(expected.entries());
        assertThat(actual.resources().keySet())
                .containsExactlyElementsOf(expected.resources().keySet());
        expected.resources()
                .forEach(
                        (name, bytes) ->
                                assertThat(actual.resources().get(name)).containsExactly(bytes));
    }

    private static HeadmanWeeklyReportModel fullWeek(int week) {
        LocalDate start = LocalDate.of(2026, 4, 27).plusWeeks(week - 1);
        List<HeadmanWeeklyReportModel.Day> days = new ArrayList<>();
        List<HeadmanWeeklyReportModel.AttendanceMark> marks = new ArrayList<>();
        for (int day = 1; day <= 6; day++) {
            List<HeadmanWeeklyReportModel.LessonSlot> lessons = new ArrayList<>();
            for (int lesson = 1; lesson <= 5; lesson++) {
                long id = day * 10L + lesson;
                lessons.add(
                        new HeadmanWeeklyReportModel.LessonSlot(
                                id, lesson, id, subjectName(week, day, lesson), "LK"));
                marks.add(
                        new HeadmanWeeklyReportModel.AttendanceMark(
                                id, start.plusDays(day - 1), lesson, "+"));
            }
            days.add(new HeadmanWeeklyReportModel.Day(start.plusDays(day - 1), lessons));
        }
        List<HeadmanWeeklyReportModel.Student> students = new ArrayList<>();
        for (int student = 1; student <= 35; student++) {
            students.add(
                    new HeadmanWeeklyReportModel.Student(
                            (long) student, studentName(week, student), List.copyOf(marks)));
        }
        return new HeadmanWeeklyReportModel(
                10L,
                "UVPV511",
                "Group: UVPV511",
                1L,
                "Spring 2026",
                week,
                start,
                start.plusDays(6),
                days,
                students);
    }

    private static String studentName(int week, int student) {
        return "W%02dS%02d".formatted(week, student) + (week == 18 && student == 35 ? " <&>" : "");
    }

    private static String subjectName(int week, int day, int lesson) {
        return "W%02dD%dL%d".formatted(week, day, lesson);
    }

    private static DocumentContents readDocumentContents(byte[] docx) throws Exception {
        SAXParserFactory factory = SAXParserFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        try (ZipInputStream zip =
                new ZipInputStream(new ByteArrayInputStream(docx), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if ("word/document.xml".equals(entry.getName())) {
                    DocumentContents contents = new DocumentContents();
                    factory.newSAXParser().parse(zip, contents);
                    return contents;
                }
            }
        }
        throw new IllegalArgumentException("Missing document.xml");
    }

    private static PackageContents readPackageContents(byte[] docx) throws IOException {
        List<String> entries = new ArrayList<>();
        Map<String, byte[]> resources = new LinkedHashMap<>();
        try (ZipInputStream zip =
                new ZipInputStream(new ByteArrayInputStream(docx), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                entries.add(entry.getName());
                if (!"word/document.xml".equals(entry.getName())) {
                    resources.put(entry.getName(), zip.readAllBytes());
                }
                zip.closeEntry();
            }
        }
        return new PackageContents(entries, resources);
    }

    private record PackageContents(List<String> entries, Map<String, byte[]> resources) {}

    private static final class DocumentContents extends DefaultHandler {
        private static final String WORD_NAMESPACE =
                "http://schemas.openxmlformats.org/wordprocessingml/2006/main";
        final List<String> texts = new ArrayList<>();
        private StringBuilder text;
        int tables;
        int pageBreaks;
        int sections;

        @Override
        public void startElement(
                String uri, String localName, String qName, Attributes attributes) {
            if (!WORD_NAMESPACE.equals(uri)) {
                return;
            }
            switch (localName) {
                case "tbl" -> tables++;
                case "sectPr" -> sections++;
                case "br" -> {
                    if ("page".equals(attributes.getValue(WORD_NAMESPACE, "type"))) {
                        pageBreaks++;
                    }
                }
                case "t" -> text = new StringBuilder();
                default -> {}
            }
        }

        @Override
        public void characters(char[] chars, int start, int length) {
            if (text != null) {
                text.append(chars, start, length);
            }
        }

        @Override
        public void endElement(String uri, String localName, String qName) {
            if (WORD_NAMESPACE.equals(uri) && "t".equals(localName)) {
                texts.add(text.toString());
                text = null;
            }
        }
    }

    private static HeadmanWeeklyReportModel model(
            LocalDate weekStart, String subjectName, String lessonType) {
        List<HeadmanWeeklyReportModel.Day> days = new ArrayList<>();
        days.add(
                new HeadmanWeeklyReportModel.Day(
                        weekStart,
                        List.of(
                                new HeadmanWeeklyReportModel.LessonSlot(
                                        100L, 1, 5L, subjectName, lessonType))));
        for (int i = 1; i < HeadmanWeeklyReportModel.TEMPLATE_DAYS; i++) {
            days.add(new HeadmanWeeklyReportModel.Day(weekStart.plusDays(i), List.of()));
        }

        return new HeadmanWeeklyReportModel(
                10L,
                "UVPV511",
                "Group: UVPV511",
                1L,
                "Spring 2026",
                1,
                weekStart,
                weekStart.plusDays(6),
                days,
                List.of(
                        new HeadmanWeeklyReportModel.Student(
                                1L,
                                "Student 01",
                                List.of(
                                        new HeadmanWeeklyReportModel.AttendanceMark(
                                                100L, weekStart, 1, "+")))));
    }

    private static void writeQaArtifact(String fileName, byte[] docx) throws IOException {
        if (!"true".equalsIgnoreCase(System.getenv("M18_QA_ARTIFACTS"))) {
            return;
        }
        Path dir = Path.of("build", "m18-qa");
        Files.createDirectories(dir);
        Files.write(dir.resolve(fileName), docx);
    }
}
