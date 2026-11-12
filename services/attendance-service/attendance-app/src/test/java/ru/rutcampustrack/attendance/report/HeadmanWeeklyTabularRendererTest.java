package ru.rutcampustrack.attendance.report;

import static org.assertj.core.api.Assertions.assertThat;
import static ru.rutcampustrack.attendance.contract.enums.AttendanceStatus.ABSENT;
import static ru.rutcampustrack.attendance.contract.enums.AttendanceStatus.EXCUSED;
import static ru.rutcampustrack.attendance.contract.enums.AttendanceStatus.FREE_ATTENDANCE;
import static ru.rutcampustrack.attendance.contract.enums.AttendanceStatus.PRESENT;
import static ru.rutcampustrack.attendance.report.teacher.TeacherAttendanceExportModel.ReportKind.HEADMAN_SUBJECT_JOURNAL;
import static ru.rutcampustrack.attendance.report.teacher.TeacherAttendanceExportModel.ReportKind.WEEKLY_ATTENDANCE;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.Test;
import ru.rutcampustrack.attendance.contract.enums.AttendanceStatus;
import ru.rutcampustrack.attendance.report.teacher.TeacherAttendanceExportModel;
import ru.rutcampustrack.attendance.report.teacher.TeacherAttendanceExportModel.Cell;
import ru.rutcampustrack.attendance.report.teacher.TeacherAttendanceExportModel.Column;
import ru.rutcampustrack.attendance.report.teacher.TeacherAttendanceExportModel.Context;
import ru.rutcampustrack.attendance.report.teacher.TeacherAttendanceExportModel.Row;

class HeadmanWeeklyTabularRendererTest {
    private static final LocalDate WEEK = LocalDate.of(2026, 4, 27);
    private final HeadmanWeeklyTabularRenderer renderer = new HeadmanWeeklyTabularRenderer();

    @Test
    void selectedWeeksRankByWeightedAttendanceAndKeepMissingWeeksSeparateFromAbsences()
            throws Exception {
        var first =
                model(
                        WEEK,
                        false,
                        List.of(column(1, WEEK, 2, "lecture", "CLOSED")),
                        List.of(
                                student(10, "Анна", Map.of(1L, mark(PRESENT))),
                                student(11, "Борис", Map.of(1L, mark(ABSENT))),
                                student(12, "Вера", Map.of())));
        LocalDate next = WEEK.plusWeeks(1);
        var second =
                model(
                        next,
                        false,
                        List.of(
                                column(2, next, 3, "practice", "CLOSED"),
                                column(3, next, 4, "lab", "CLOSED"),
                                column(4, next, 5, "lecture", "CLOSED")),
                        List.of(
                                student(
                                        10,
                                        "Анна <А>",
                                        Map.of(
                                                2L, mark(PRESENT),
                                                3L, mark(EXCUSED),
                                                4L, mark(ABSENT))),
                                student(
                                        13,
                                        "Глеб",
                                        Map.of(2L, mark(PRESENT), 3L, mark(FREE_ATTENDANCE)))));
        List<TeacherAttendanceExportModel> models = List.of(first, second);
        String html = html(models, true);
        var xlsx = unzip(renderer.renderXlsx(models, true));

        assertThat(html)
                .contains("Сводка посещаемости", "Посещаемость по неделям", "Анна &lt;А&gt;")
                .contains("2 / 4", "width:50%", "100%", "0%", "—", "colspan=\"3\"")
                .doesNotContain("09:15", "Проведено", "Типы занятий", "Анна <А>");
        assertThat(xlsx.get("xl/workbook.xml"))
                .contains("Журнал", "Сводка посещаемости", "Посещаемость по неделям");
        assertThat(sheetRows(xlsx, 2).subList(3, 8))
                .containsExactly(
                        List.of("№", "Студент", "Присутствовал", "Всего отметок", "Посещаемость"),
                        List.of("1", "Анна <А>", "2", "4", "0.5"),
                        List.of("2", "Глеб", "1", "2", "0.5"),
                        List.of("3", "Борис", "0", "1", "0"),
                        List.of("4", "Вера", "0", "0", "—"));
        assertThat(sheetRows(xlsx, 3))
                .contains(
                        List.of("Анна <А>", "1", "0.3333333333333333", "0.5"),
                        List.of("Глеб", "—", "0.5", "0.5"),
                        List.of("Борис", "0", "—", "0"),
                        List.of("Вера", "—", "—", "—"));
        assertThat(xlsx.get("xl/worksheets/sheet1.xml")).contains("ref=\"B13:D13\"");
        assertThat(xlsx.get("xl/worksheets/sheet2.xml"))
                .contains("type=\"dataBar\"", "<v>0.5</v>");
        assertThat(xlsx.get("xl/worksheets/sheet3.xml")).contains("type=\"colorScale\"");
    }

    @Test
    void subjectJournalUsesOnlyActualMarksForRowStatistics() throws Exception {
        var model =
                model(
                        WEEK,
                        true,
                        List.of(
                                column(1, WEEK, 1, "lecture", "CLOSED"),
                                column(2, WEEK, 2, "practice", "CLOSED"),
                                column(3, WEEK, 3, "lab", "CLOSED"),
                                column(4, WEEK, 4, "lab", "CANCELLED"),
                                column(5, WEEK, 5, "lecture", "PLANNED")),
                        List.of(
                                student(
                                        10,
                                        "Анна",
                                        Map.of(
                                                1L, mark(PRESENT),
                                                2L, mark(FREE_ATTENDANCE),
                                                3L, mark(ABSENT),
                                                4L, mark(ABSENT),
                                                5L, new Cell("н", ABSENT, false))),
                                student(11, "Борис", Map.of())));
        String html = html(List.of(model), false);
        var xlsx = unzip(renderer.renderXlsx(List.of(model), false));

        assertThat(html)
                .contains("<h2>Математика &amp; логика</h2>", "1 ЛК", "2 ПЗ", "3 ЛЗ")
                .contains("Типы занятий: Лекция", "Выбранные недели: 2026-04-27")
                .contains(
                        "Зачёркнутые пары имеют статус «Отменено».",
                        "class=\"lesson-head cancelled\">4 ЛЗ",
                        "text-decoration:line-through")
                .contains(
                        "class=\"present\">+</td>",
                        "class=\"excused\">у</td>",
                        "class=\"absent\">н</td>")
                .containsOnlyOnce("Математика &amp; логика")
                .doesNotContain("Сводка посещаемости", "Посещаемость по неделям");
        assertThat(sheetRows(xlsx, 1))
                .contains(
                        List.of("Типы занятий: Лекция"),
                        List.of("Выбранные недели: 2026-04-27"),
                        List.of("Зачёркнутые пары имеют статус «Отменено»."),
                        List.of("Анна", "+", "у", "н", "", "", "1", "0.3333333333333333", "1", "1", "3"),
                        List.of("Борис", "", "", "", "", "", "0", "—", "0", "0", "0"));
        assertThat(xlsx.get("xl/styles.xml"))
                .contains("textRotation=\"90\"", "FFDBF8EF", "FFFDF0D6", "FFFCE2E2", "FF607080", "<strike/>");
        assertThat(xlsx).doesNotContainKeys("xl/worksheets/sheet2.xml");
    }

    @Test
    void explicitOneWeekSelectionHasTrendsAndEmptyJournalsSayWhatIsMissing() throws Exception {
        var empty = model(WEEK, false, List.of(), List.of());
        assertThat(html(List.of(empty), true))
                .contains("Нет занятий за этот период", "Посещаемость по неделям");
        assertThat(html(List.of(empty), false)).doesNotContain("Посещаемость по неделям");
        assertThat(unzip(renderer.renderXlsx(List.of(empty), true)))
                .containsKey("xl/worksheets/sheet3.xml");
        assertThat(unzip(renderer.renderXlsx(List.of(empty), false)))
                .doesNotContainKey("xl/worksheets/sheet3.xml");

        var noMarks =
                model(
                        WEEK,
                        false,
                        List.of(column(1, WEEK, 1, "lecture", "PLANNED")),
                        List.of(student(10, "Анна", Map.of())));
        assertThat(html(List.of(noMarks), false))
                .contains("Нет отметок посещаемости", "Анна")
                .doesNotContain(">0%</td>");
        var xlsx = unzip(renderer.renderXlsx(List.of(noMarks), false));
        assertThat(sheetRows(xlsx, 2)).contains(List.of("1", "Анна", "0", "0", "—"));
    }

    private String html(List<TeacherAttendanceExportModel> models, boolean selectedWeeks) {
        return new String(renderer.renderHtml(models, selectedWeeks), StandardCharsets.UTF_8);
    }

    private static List<List<String>> sheetRows(Map<String, String> xlsx, int sheet)
            throws Exception {
        return ExportTableReader.sheetRows(xlsx.get("xl/worksheets/sheet" + sheet + ".xml"));
    }

    private static Map<String, String> unzip(byte[] bytes) throws Exception {
        Map<String, String> entries = new HashMap<>();
        try (var zip = new ZipInputStream(new ByteArrayInputStream(bytes), StandardCharsets.UTF_8)) {
            for (var entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
                entries.put(entry.getName(), new String(zip.readAllBytes(), StandardCharsets.UTF_8));
            }
        }
        return entries;
    }

    private static TeacherAttendanceExportModel model(
            LocalDate week, boolean subject, List<Column> columns, List<Row> rows) {
        return new TeacherAttendanceExportModel(
                new Context(
                        subject ? HEADMAN_SUBJECT_JOURNAL : WEEKLY_ATTENDANCE,
                        1,
                        "Весенний семестр",
                        1,
                        "УЧ-101",
                        subject ? 5L : null,
                        subject ? "Математика & логика" : "",
                        week,
                        week.plusDays(6),
                        subject ? List.of("Лекция") : List.of(),
                        Instant.parse("2026-05-06T12:00:00Z"),
                        subject ? List.of(week) : List.of()),
                columns,
                rows);
    }

    private static Column column(long id, LocalDate date, int number, String type, String state) {
        return new Column(id, date, LocalTime.of(9, 15), number, 5, "Математика & логика", type, "", state);
    }

    private static Row student(long id, String name, Map<Long, Cell> cells) {
        return new Row(id, name, cells, Optional.empty());
    }

    private static Cell mark(AttendanceStatus status) {
        return new Cell("", status, true);
    }
}
