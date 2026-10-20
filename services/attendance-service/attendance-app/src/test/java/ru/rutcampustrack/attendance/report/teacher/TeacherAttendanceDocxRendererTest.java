package ru.rutcampustrack.attendance.report.teacher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import ru.rutcampustrack.attendance.contract.enums.AttendanceStatus;
import ru.rutcampustrack.attendance.exception.ReportValidationException;
import ru.rutcampustrack.attendance.report.teacher.TeacherAttendanceExportModel.Cell;
import ru.rutcampustrack.attendance.report.teacher.TeacherAttendanceExportModel.Column;
import ru.rutcampustrack.attendance.report.teacher.TeacherAttendanceExportModel.Context;
import ru.rutcampustrack.attendance.report.teacher.TeacherAttendanceExportModel.Metrics;
import ru.rutcampustrack.attendance.report.teacher.TeacherAttendanceExportModel.ReportKind;
import ru.rutcampustrack.attendance.report.teacher.TeacherAttendanceExportModel.Row;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.xml.parsers.DocumentBuilderFactory;

class TeacherAttendanceDocxRendererTest {

    private static final String WORD_NS =
            "http://schemas.openxmlformats.org/wordprocessingml/2006/main";

    private final TeacherAttendanceDocxRenderer renderer = new TeacherAttendanceDocxRenderer();

    @Test
    void subjectJournalKeepsEveryLessonAndStudentAcrossLogicalSegments() throws Exception {
        TeacherAttendanceExportModel model =
                model(
                        ReportKind.SUBJECT_JOURNAL,
                        LocalDate.of(2026, 9, 1),
                        LocalDate.of(2026, 9, 30));

        byte[] docx = renderer.render(model);
        writeSampleIfRequested("subject-journal.docx", docx);
        String xml = TeacherAttendanceDocxRenderer.readDocumentXml(docx);
        var document = parse(xml);
        NodeList tables = document.getElementsByTagNameNS(WORD_NS, "tbl");

        assertThat(xml).doesNotContain("${", "50001", "50002");
        assertThat(visibleText(xml))
                .contains(
                        "Журнал посещаемости по предмету",
                        "Физика и астрономия",
                        "Запланировано",
                        "Отменено",
                        "25%");
        assertMatrix(model, document);
        var logicalRows = ru.rutcampustrack.attendance.report.ExportTableReader.wordRows(xml);
        assertThat(logicalRows)
                .anySatisfy(
                        row ->
                                assertThat(row)
                                        .containsExactly(
                                                model.rows().getFirst().displayName(),
                                                "2",
                                                "2",
                                                "1",
                                                "4",
                                                "8",
                                                "25%",
                                                "50%",
                                                "25%",
                                                "12,5%"));
        assertThat(logicalRows)
                .anySatisfy(
                        row ->
                                assertThat(row)
                                        .containsExactly(
                                                model.rows().get(1).displayName(),
                                                "0",
                                                "0",
                                                "0",
                                                "0",
                                                "0",
                                                "—",
                                                "—",
                                                "—",
                                                "—"));
    }

    @Test
    void eighteenWeekSemesterKeepsThirtyStudentsAllThirtySixLessonsAndTotals() throws Exception {
        TeacherAttendanceExportModel model = largeSemesterModel();

        byte[] docx = renderer.render(model);
        writeSampleIfRequested("subject-journal-large.docx", docx);
        String xml = TeacherAttendanceDocxRenderer.readDocumentXml(docx);
        var document = parse(xml);
        NodeList tables = document.getElementsByTagNameNS(WORD_NS, "tbl");
        String visibleText = visibleText(xml);

        assertThat(xml).doesNotContain("${", "91000", "91029");
        assertThat(visibleText)
                .contains(
                        "Математический анализ и прикладная статистика для инженерных направлений");
        assertMatrix(model, document);
        for (String student : largeStudentNames()) {
            assertThat(ru.rutcampustrack.attendance.report.ExportTableReader.wordRows(xml))
                    .anySatisfy(
                            row -> {
                                assertThat(row.getFirst()).isEqualTo(student);
                                assertThat(row.subList(1, row.size()))
                                        .containsExactly(
                                                "18", "9", "9", "27", "36", "50%", "75%", "25%",
                                                "25%");
                            });
        }
    }

    @Test
    void weeklyBatchKeepsCallerWeekBoundariesAndAddsASectionForEachModel() throws Exception {
        TeacherAttendanceExportModel first =
                model(
                        ReportKind.WEEKLY_ATTENDANCE,
                        LocalDate.of(2026, 9, 7),
                        LocalDate.of(2026, 9, 13));
        TeacherAttendanceExportModel second =
                model(
                        ReportKind.WEEKLY_ATTENDANCE,
                        LocalDate.of(2026, 9, 14),
                        LocalDate.of(2026, 9, 20));

        byte[] docx = renderer.render(List.of(first, second));
        writeSampleIfRequested("weekly-batch.docx", docx);
        String xml = TeacherAttendanceDocxRenderer.readDocumentXml(docx);
        String visibleText = visibleText(xml);

        assertThat(visibleText)
                .contains("Неделя: 07.09.2026–13.09.2026", "Неделя: 14.09.2026–20.09.2026");
        assertThat(visibleText.indexOf("Неделя: 07.09.2026–13.09.2026"))
                .isLessThan(visibleText.indexOf("Неделя: 14.09.2026–20.09.2026"));
        assertThat(visibleText).doesNotContain("${");
        assertThat(visibleText).doesNotContain("Итоги по студентам");
        parse(xml);
    }

    @Test
    void weeklyBatchRejectsSubjectJournalModels() {
        TeacherAttendanceExportModel subjectModel =
                model(
                        ReportKind.SUBJECT_JOURNAL,
                        LocalDate.of(2026, 9, 1),
                        LocalDate.of(2026, 9, 30));

        assertThatThrownBy(() -> renderer.render(List.of(subjectModel)))
                .isInstanceOf(ReportValidationException.class)
                .hasMessageContaining("WEEKLY_ATTENDANCE");
    }

    private static void assertMatrix(
            TeacherAttendanceExportModel model, org.w3c.dom.Document document) {
        var tables = document.getElementsByTagNameNS(WORD_NS, "tbl");
        java.util.Set<Long> observed = new java.util.HashSet<>();
        for (int table = 0; table < tables.getLength(); table++) {
            var matrixRows = rows((Element) tables.item(table));
            if (matrixRows.isEmpty()) continue;
            var headings =
                    cells(matrixRows.getFirst()).stream()
                            .map(TeacherAttendanceDocxRendererTest::cellText)
                            .toList();
            if (!headings.contains("№") || !headings.contains("Студент")) continue;
            for (var column : model.columns()) {
                String date =
                        column.date()
                                .format(java.time.format.DateTimeFormatter.ofPattern("dd.MM.uuuu"));
                int position = -1;
                for (int index = 0; index < headings.size(); index++) {
                    String heading = headings.get(index);
                    if (heading.contains(date)
                            && heading.contains(column.typeLabel())
                            && heading.contains("№" + column.lessonNumber())) position = index;
                }
                if (position < 0) continue;
                assertThat(observed.add(column.lessonId())).isTrue();
                for (var expected : model.rows()) {
                    var row =
                            matrixRows.stream()
                                    .skip(1)
                                    .map(TeacherAttendanceDocxRendererTest::cells)
                                    .filter(
                                            value ->
                                                    value.stream()
                                                            .anyMatch(
                                                                    cell ->
                                                                            cellText(cell)
                                                                                    .equals(
                                                                                            expected
                                                                                                    .displayName())))
                                    .findFirst()
                                    .orElseThrow();
                    var mark = expected.cellsByLessonId().get(column.lessonId());
                    String symbol =
                            mark == null ? "" : mark.symbol().isBlank() ? "·" : mark.symbol();
                    assertThat(cellText(row.get(position)))
                            .as(expected.displayName() + "/" + column.lessonId())
                            .isEqualTo(symbol);
                }
            }
        }
        assertThat(observed)
                .containsExactlyInAnyOrderElementsOf(
                        model.columns().stream().map(Column::lessonId).toList());
    }

    private static TeacherAttendanceExportModel model(
            ReportKind kind, LocalDate from, LocalDate to) {
        List<Column> columns = new ArrayList<>();
        for (int index = 0; index < 8; index++) {
            columns.add(
                    new Column(
                            700L + index,
                            from.plusDays(
                                    kind == ReportKind.WEEKLY_ATTENDANCE ? index % 6L : index * 2L),
                            LocalTime.of(9 + index % 4, 30),
                            index + 1,
                            81L,
                            "Физика и астрономия",
                            index % 2 == 0 ? "LAB" : "LECTURE",
                            index % 2 == 0 ? "Лабораторная работа" : "Лекция",
                            switch (index) {
                                case 2 -> "CANCELLED";
                                case 3, 7 -> "PLANNED";
                                case 4 -> "ACTIVE";
                                default -> "CLOSED";
                            }));
        }

        Map<Long, Cell> firstCells = new LinkedHashMap<>();
        firstCells.put(700L, new Cell("+", AttendanceStatus.PRESENT, true));
        firstCells.put(701L, new Cell("у", AttendanceStatus.EXCUSED, true));
        // Cancelled and future lessons have no attendance record and stay blank.
        firstCells.put(704L, new Cell("+", AttendanceStatus.PRESENT, true));
        firstCells.put(705L, new Cell("н", AttendanceStatus.ABSENT, true));
        firstCells.put(706L, new Cell("у", AttendanceStatus.EXCUSED, true));
        // Future lesson 707 deliberately has no key, so the report cannot imply absence.

        java.util.Optional<Metrics> rowMetrics =
                kind == ReportKind.WEEKLY_ATTENDANCE
                        ? java.util.Optional.empty()
                        : java.util.Optional.of(
                                new Metrics(
                                        2,
                                        2,
                                        1,
                                        4,
                                        8,
                                        new BigDecimal("25"),
                                        new BigDecimal("50"),
                                        new BigDecimal("25"),
                                        new BigDecimal("12.5")));
        Row first =
                new Row(
                        50001L,
                        "Иванова Елизавета Александровна с очень длинной фамилией",
                        firstCells,
                        rowMetrics);
        Row second =
                new Row(
                        50002L,
                        "Петров Артём",
                        Map.of(),
                        kind == ReportKind.WEEKLY_ATTENDANCE
                                ? java.util.Optional.empty()
                                : java.util.Optional.of(
                                        new Metrics(
                                                0,
                                                0,
                                                0,
                                                0,
                                                0,
                                                BigDecimal.ZERO,
                                                BigDecimal.ZERO,
                                                BigDecimal.ZERO,
                                                BigDecimal.ZERO)));

        Context context =
                new Context(
                        kind,
                        12L,
                        "Осенний семестр",
                        42L,
                        "ПИ-101",
                        kind == ReportKind.SUBJECT_JOURNAL ? 81L : null,
                        kind == ReportKind.SUBJECT_JOURNAL ? "Физика и астрономия" : "",
                        from,
                        to,
                        List.of("Лабораторная работа", "Лекция"),
                        Instant.parse("2026-09-23T10:11:12Z"));
        return new TeacherAttendanceExportModel(context, columns, List.of(first, second));
    }

    private static TeacherAttendanceExportModel largeSemesterModel() {
        LocalDate from = LocalDate.of(2026, 2, 2);
        LocalDate to = from.plusWeeks(18).minusDays(1);
        String subject = "Математический анализ и прикладная статистика для инженерных направлений";
        List<Column> columns = new ArrayList<>();
        for (int index = 0; index < 36; index++) {
            columns.add(
                    new Column(
                            800L + index,
                            largeSemesterDate(from, index),
                            LocalTime.of(9 + index % 6, 30),
                            index % 5 + 1,
                            801L,
                            subject,
                            index % 3 == 0 ? "LECTURE" : index % 3 == 1 ? "PRACTICE" : "LAB",
                            index % 3 == 0
                                    ? "Лекция"
                                    : index % 3 == 1
                                            ? "Практическое занятие"
                                            : "Лабораторная работа с индивидуальным оборудованием",
                            "CLOSED"));
        }

        List<Row> rows = new ArrayList<>();
        for (int studentIndex = 0; studentIndex < largeStudentNames().size(); studentIndex++) {
            Map<Long, Cell> cells = new LinkedHashMap<>();
            for (int lessonIndex = 0; lessonIndex < columns.size(); lessonIndex++) {
                int symbolIndex = (studentIndex + lessonIndex) % 4;
                Cell cell =
                        switch (symbolIndex) {
                            case 0 -> new Cell("", AttendanceStatus.PRESENT, true);
                            case 1 -> new Cell("+", AttendanceStatus.PRESENT, true);
                            case 2 -> new Cell("у", AttendanceStatus.EXCUSED, true);
                            default -> new Cell("н", AttendanceStatus.ABSENT, true);
                        };
                cells.put(columns.get(lessonIndex).lessonId(), cell);
            }
            rows.add(
                    new Row(
                            91_000L + studentIndex,
                            largeStudentNames().get(studentIndex),
                            cells,
                            java.util.Optional.of(
                                    new Metrics(
                                            18,
                                            9,
                                            9,
                                            27,
                                            36,
                                            new BigDecimal("50"),
                                            new BigDecimal("75"),
                                            new BigDecimal("25"),
                                            new BigDecimal("25")))));
        }

        Context context =
                new Context(
                        ReportKind.SUBJECT_JOURNAL,
                        12L,
                        "Осенне-весенний семестр 2025/2026",
                        43L,
                        "ПИ-301",
                        801L,
                        subject,
                        from,
                        to,
                        List.of(
                                "Лекция",
                                "Практическое занятие",
                                "Лабораторная работа с индивидуальным оборудованием"),
                        Instant.parse("2026-06-30T12:30:00Z"));
        return new TeacherAttendanceExportModel(context, columns, rows);
    }

    private static List<String> largeStudentNames() {
        return List.of(
                "Александрова Мария Сергеевна, обучающаяся по индивидуальному учебному плану",
                "Кузнецов-Петров Михаил Александрович",
                "Фёдорова Анастасия Михайловна",
                "Иванов Артём Дмитриевич",
                "Смирнова Софья Андреевна",
                "Попов Максим Сергеевич",
                "Васильева Мария Александровна",
                "Павлов Иван Михайлович",
                "Соколова Алиса Дмитриевна",
                "Михайлов Дмитрий Сергеевич",
                "Новикова Полина Андреевна",
                "Фёдоров Кирилл Максимович",
                "Морозова Елена Игоревна",
                "Волков Никита Александрович",
                "Алексеева Екатерина Павловна",
                "Лебедев Матвей Дмитриевич",
                "Семёнова Виктория Олеговна",
                "Егоров Даниил Сергеевич",
                "Павлова Анастасия Ильинична",
                "Козлов Александр Романович",
                "Степанова Арина Максимовна",
                "Николаев Тимофей Владимирович",
                "Орлова Дарья Евгеньевна",
                "Андреев Михаил Андреевич",
                "Макарова Полина Дмитриевна",
                "Захаров Роман Ильич",
                "Борисова Ксения Алексеевна",
                "Григорьев Лев Павлович",
                "Данилова Милана Константиновна",
                "Жукова Елизавета Александровна");
    }

    private static LocalDate largeSemesterDate(LocalDate from, int lessonIndex) {
        return from.plusWeeks(lessonIndex / 2).plusDays(lessonIndex % 2 * 3L);
    }

    private static String largeSemesterModelDate(int lessonIndex) {
        return largeSemesterDate(LocalDate.of(2026, 2, 2), lessonIndex)
                .format(java.time.format.DateTimeFormatter.ofPattern("dd.MM.uuuu"));
    }

    private static org.w3c.dom.Document parse(String xml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        return factory.newDocumentBuilder()
                .parse(
                        new ByteArrayInputStream(
                                xml.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }

    private static List<Element> rows(Element table) {
        return children(table, "tr");
    }

    private static List<Element> cells(Element row) {
        return children(row, "tc");
    }

    private static List<Element> children(Element parent, String localName) {
        List<Element> result = new ArrayList<>();
        NodeList children = parent.getChildNodes();
        for (int index = 0; index < children.getLength(); index++) {
            Node child = children.item(index);
            if (child instanceof Element element
                    && WORD_NS.equals(element.getNamespaceURI())
                    && localName.equals(element.getLocalName())) {
                result.add(element);
            }
        }
        return result;
    }

    private static String cellText(Element cell) {
        NodeList values = cell.getElementsByTagNameNS(WORD_NS, "t");
        StringBuilder text = new StringBuilder();
        for (int index = 0; index < values.getLength(); index++) {
            text.append(values.item(index).getTextContent());
        }
        return text.toString();
    }

    private static String visibleText(String xml) throws Exception {
        NodeList values = parse(xml).getElementsByTagNameNS(WORD_NS, "t");
        StringBuilder text = new StringBuilder();
        for (int index = 0; index < values.getLength(); index++) {
            text.append(values.item(index).getTextContent());
        }
        return text.toString();
    }

    private static int count(String text, String needle) {
        int count = 0;
        int offset = 0;
        while ((offset = text.indexOf(needle, offset)) >= 0) {
            count++;
            offset += needle.length();
        }
        return count;
    }

    private static int occurrences(String text, String value) {
        int count = 0;
        int offset = 0;
        while ((offset = text.indexOf(value, offset)) >= 0) {
            count++;
            offset += value.length();
        }
        return count;
    }

    private static void writeSampleIfRequested(String name, byte[] bytes) throws IOException {
        String outputDirectory = System.getenv("TEACHER_ATTENDANCE_SAMPLE_DIR");
        if (outputDirectory == null || outputDirectory.isBlank()) {
            return;
        }
        Path directory = Path.of(outputDirectory);
        Files.createDirectories(directory);
        Files.write(directory.resolve(name), bytes);
    }
}
