package ru.rutcampustrack.attendance.report;

import static ru.rutcampustrack.attendance.report.HeadmanTabularAttendance.DASH;
import static ru.rutcampustrack.attendance.report.HeadmanTabularAttendance.NO_LESSONS;
import static ru.rutcampustrack.attendance.report.HeadmanTabularAttendance.NO_MARKS;
import static ru.rutcampustrack.attendance.report.HeadmanTabularAttendance.NO_STUDENTS;
import static ru.rutcampustrack.attendance.report.HeadmanTabularAttendance.SUBJECT_STATS;
import static ru.rutcampustrack.attendance.report.HeadmanTabularAttendance.SUMMARY;
import static ru.rutcampustrack.attendance.report.HeadmanTabularAttendance.WEEKS;
import static ru.rutcampustrack.attendance.report.HeadmanTabularAttendance.cancelled;
import static ru.rutcampustrack.attendance.report.HeadmanTabularAttendance.numberAndType;
import static ru.rutcampustrack.attendance.report.HeadmanTabularAttendance.range;
import static ru.rutcampustrack.attendance.report.HeadmanTabularAttendance.shortType;

import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.stereotype.Component;
import ru.rutcampustrack.attendance.report.HeadmanTabularAttendance.Counts;
import ru.rutcampustrack.attendance.report.HeadmanTabularAttendance.Journal;
import ru.rutcampustrack.attendance.report.HeadmanTabularAttendance.JournalRow;
import ru.rutcampustrack.attendance.report.HeadmanTabularAttendance.Mark;
import ru.rutcampustrack.attendance.report.HeadmanTabularAttendance.Student;
import ru.rutcampustrack.attendance.report.teacher.TeacherAttendanceExportModel;
import ru.rutcampustrack.attendance.report.teacher.TeacherAttendanceExportModel.Column;

/** HTML and XLSX views over the server-assembled attendance matrices. */
@Component
final class HeadmanWeeklyTabularRenderer {
    static final String NAVY = "233D60";
    static final String PRESENT_FILL = "DBF8EF";
    static final String EXCUSED_FILL = "FDF0D6";
    static final String ABSENT_FILL = "FCE2E2";
    static final String CANCELLED_HEADER = "607080";

    private static final String HTML_START =
            """
            <!doctype html><html lang="ru"><head><meta charset="utf-8">
            <meta name="viewport" content="width=device-width, initial-scale=1">
            <title>Журнал посещаемости</title>
            <style>
            body{font:15px/1.45 Arial,sans-serif;color:#1c2939;margin:1.5rem}
            main{max-width:100%%;margin:auto}
            h1,h2{line-height:1.2}h2{margin-block:1.5rem .5rem}
            .meta,.empty{color:#4d5e70}
            .table-wrap{max-width:100%%;overflow-x:auto;margin-block:1rem 2rem}
            table{border-collapse:collapse}
            th,td{border:1px solid #d1d9e3;padding:.4rem .55rem;vertical-align:middle}
            thead th{background:#%s;color:white;text-align:center}
            tbody th{text-align:left;font-weight:600;background:#f2f5f9;min-width:10rem}
            td{text-align:center;min-width:1.4rem}
            .student{text-align:left}
            .journal .lesson-head{text-align:center;min-width:2.7rem;white-space:nowrap}
            .vertical{display:inline-block;writing-mode:vertical-rl;transform:rotate(180deg);
                      max-inline-size:18rem;white-space:normal;overflow-wrap:anywhere;text-align:left}
            .present{background:#%s}.excused{background:#%s}.absent{background:#%s}
            .cancelled{background:#%s}
            .cancelled,.cancelled .vertical{text-decoration:line-through}
            .rate{min-width:10rem;white-space:nowrap;text-align:left}
            .track{display:inline-block;width:6rem;height:.55rem;background:#e4eaf2;
                   border-radius:.3rem;vertical-align:middle;margin-inline-end:.45rem}
            .bar{display:block;height:100%%;background:#399a7c;border-radius:.3rem}
            .trends thead th{min-width:6.25rem}
            .total{font-weight:700}
            @media print{body{margin:.5rem}.table-wrap{overflow:visible}
                         table{font-size:10pt;print-color-adjust:exact}}
            </style></head><body><main><h1>Журнал посещаемости</h1>
            """
                    .formatted(NAVY, PRESENT_FILL, EXCUSED_FILL, ABSENT_FILL, CANCELLED_HEADER);

    byte[] renderHtml(List<TeacherAttendanceExportModel> models) {
        return renderHtml(models, models.size() > 1);
    }

    byte[] renderHtml(List<TeacherAttendanceExportModel> models, boolean selectedWeeks) {
        var report = HeadmanTabularAttendance.from(models);
        StringBuilder html = new StringBuilder(32_768);
        html.append(HTML_START);
        if (report.journals().isEmpty()) {
            html.append("<p class=\"empty\">").append(NO_LESSONS).append("</p>");
        } else {
            var context = report.journals().getFirst().context();
            html.append("<p class=\"meta\">Группа: <strong>")
                    .append(escapeHtml(context.groupLabel()))
                    .append("</strong> · Семестр: ")
                    .append(escapeHtml(context.semesterLabel()))
                    .append(" · Сформировано: ")
                    .append(escapeHtml(context.generatedAt().toString()))
                    .append("</p>");
        }
        report.journals().forEach(journal -> appendJournal(html, journal));
        if (report.weekly()) {
            appendSummary(html, report.students());
            if (selectedWeeks) {
                appendTrends(html, report);
            }
        }
        return html.append("</main></body></html>").toString().getBytes(StandardCharsets.UTF_8);
    }

    byte[] renderXlsx(List<TeacherAttendanceExportModel> models) {
        return renderXlsx(models, models.size() > 1);
    }

    byte[] renderXlsx(List<TeacherAttendanceExportModel> models, boolean selectedWeeks) {
        return HeadmanAttendanceXlsx.render(models, selectedWeeks);
    }

    private static void appendJournal(StringBuilder html, Journal journal) {
        html.append("<section><h2>").append(escapeHtml(journal.title())).append("</h2>");
        if (journal.subject()) {
            html.append("<p class=\"meta\">").append(range(journal.context())).append("</p>");
            for (String filter : journal.subjectFilters()) {
                html.append("<p class=\"meta\">").append(escapeHtml(filter)).append("</p>");
            }
        }
        String emptyMessage = journal.emptyMessage();
        if (!emptyMessage.isEmpty()) {
            html.append("<p class=\"empty\">").append(emptyMessage).append("</p>");
        }
        if (journal.columns().isEmpty() || journal.rows().isEmpty()) {
            html.append("</section>");
            return;
        }

        html.append("<div class=\"table-wrap\"><table class=\"journal\"><thead>");
        if (journal.subject()) {
            appendSubjectHeader(html, journal);
        } else {
            appendWeekHeader(html, journal);
        }
        html.append("</thead><tbody>");
        for (JournalRow row : journal.rows()) {
            html.append("<tr><th scope=\"row\">")
                    .append(escapeHtml(row.row().displayName()))
                    .append("</th>");
            for (Column column : journal.columns()) {
                Mark mark = row.mark(column);
                html.append("<td");
                if (mark != Mark.EMPTY) {
                    html.append(" class=\"").append(mark.style).append('"');
                }
                html.append('>').append(mark.symbol).append("</td>");
            }
            if (journal.subject()) {
                Counts counts = row.counts();
                appendCell(html, Integer.toString(counts.present()));
                appendCell(html, percent(counts));
                appendCell(html, Integer.toString(counts.excused()));
                appendCell(html, Integer.toString(counts.absent()));
                appendCell(html, Integer.toString(counts.denominator()));
            }
            html.append("</tr>");
        }
        html.append("</tbody></table></div></section>");
    }

    private static void appendSubjectHeader(StringBuilder html, Journal journal) {
        html.append("<tr><th scope=\"col\" class=\"student\" rowspan=\"2\">Студент</th>");
        for (Column column : journal.columns()) {
            html.append(lessonHeaderStart(cancelled(column)))
                    .append("<span class=\"vertical\">")
                    .append(column.date())
                    .append("</span></th>");
        }
        for (String header : SUBJECT_STATS) {
            html.append("<th scope=\"col\" rowspan=\"2\">").append(header).append("</th>");
        }
        html.append("</tr><tr>");
        for (Column column : journal.columns()) {
            appendLessonHeader(html, numberAndType(column), cancelled(column));
        }
        html.append("</tr>");
    }

    private static void appendWeekHeader(StringBuilder html, Journal journal) {
        html.append("<tr><th scope=\"col\" class=\"student\" rowspan=\"3\">Студент</th>");
        for (var span : journal.dates()) {
            html.append("<th class=\"lesson-head\" colspan=\"")
                    .append(span.end() - span.start())
                    .append("\">")
                    .append(span.date())
                    .append("</th>");
        }
        html.append("</tr><tr>");
        for (Column column : journal.columns()) {
            html.append("<th scope=\"col\" class=\"lesson-head\"><span class=\"vertical\">")
                    .append(escapeHtml(column.subjectLabel()))
                    .append("</span></th>");
        }
        html.append("</tr><tr>");
        for (Column column : journal.columns()) {
            appendLessonHeader(html, shortType(column), false);
        }
        html.append("</tr>");
    }

    private static String lessonHeaderStart(boolean cancelled) {
        return cancelled
                ? "<th scope=\"col\" class=\"lesson-head cancelled\">"
                : "<th scope=\"col\" class=\"lesson-head\">";
    }

    private static void appendLessonHeader(StringBuilder html, String text, boolean cancelled) {
        html.append(lessonHeaderStart(cancelled)).append(escapeHtml(text)).append("</th>");
    }

    private static void appendSummary(StringBuilder html, List<Student> students) {
        html.append("<section><h2>").append(SUMMARY).append("</h2>");
        if (students.isEmpty()) {
            html.append("<p class=\"empty\">").append(NO_STUDENTS).append("</p></section>");
            return;
        }
        if (students.stream().allMatch(student -> student.total().denominator() == 0)) {
            html.append("<p class=\"empty\">").append(NO_MARKS).append("</p>");
        }
        html.append(
                """
                <div class="table-wrap"><table><thead><tr><th scope="col">№</th>
                <th scope="col" class="student">Студент</th><th scope="col">Присутствовал / всего</th>
                <th scope="col">Посещаемость</th></tr></thead><tbody>
                """);
        int rank = 0;
        for (Student student : students) {
            Counts counts = student.total();
            html.append("<tr>");
            appendCell(html, Integer.toString(++rank));
            html.append("<th scope=\"row\">")
                    .append(escapeHtml(student.displayName()))
                    .append("</th>");
            appendCell(
                    html,
                    counts.denominator() == 0
                            ? DASH
                            : counts.present() + " / " + counts.denominator());
            html.append("<td class=\"rate\">");
            if (counts.denominator() > 0) {
                html.append("<span class=\"track\" aria-hidden=\"true\"><span class=\"bar\" style=\"width:")
                        .append(counts.fraction().movePointRight(2).toPlainString())
                        .append("%\"></span></span>");
            }
            html.append(percent(counts)).append("</td></tr>");
        }
        html.append("</tbody></table></div></section>");
    }

    private static void appendTrends(StringBuilder html, HeadmanTabularAttendance report) {
        html.append("<section><h2>").append(WEEKS).append("</h2>");
        if (report.students().isEmpty()) {
            html.append("<p class=\"empty\">").append(NO_STUDENTS).append("</p></section>");
            return;
        }
        html.append(
                "<div class=\"table-wrap\"><table class=\"trends\"><thead><tr><th scope=\"col\" class=\"student\">Студент</th>");
        for (Journal journal : report.journals()) {
            html.append("<th scope=\"col\">").append(range(journal.context())).append("</th>");
        }
        html.append("<th scope=\"col\">За весь период</th></tr></thead><tbody>");
        for (Student student : report.students()) {
            html.append("<tr><th scope=\"row\">")
                    .append(escapeHtml(student.displayName()))
                    .append("</th>");
            for (Counts counts : student.weeks()) {
                appendTrendCell(html, counts, false);
            }
            appendTrendCell(html, student.total(), true);
            html.append("</tr>");
        }
        html.append("</tbody></table></div></section>");
    }

    private static void appendTrendCell(StringBuilder html, Counts counts, boolean total) {
        html.append("<td");
        if (total) {
            html.append(" class=\"total\"");
        }
        if (counts.denominator() > 0) {
            html.append(" style=\"background:#").append(heatColor(counts)).append('"');
        }
        html.append('>').append(percent(counts)).append("</td>");
    }

    private static String heatColor(Counts counts) {
        double ratio = counts.fraction().doubleValue();
        String first = ratio <= 0.5 ? ABSENT_FILL : EXCUSED_FILL;
        String last = ratio <= 0.5 ? EXCUSED_FILL : PRESENT_FILL;
        double position = ratio <= 0.5 ? ratio * 2 : (ratio - 0.5) * 2;
        StringBuilder color = new StringBuilder(6);
        for (int channel = 0; channel < 6; channel += 2) {
            int from = Integer.parseInt(first.substring(channel, channel + 2), 16);
            int to = Integer.parseInt(last.substring(channel, channel + 2), 16);
            color.append("%02x".formatted(Math.round(from + (to - from) * position)));
        }
        return color.toString();
    }

    private static String percent(Counts counts) {
        return counts.denominator() == 0
                ? DASH
                : counts.fraction()
                                .movePointRight(2)
                                .setScale(1, RoundingMode.HALF_UP)
                                .stripTrailingZeros()
                                .toPlainString()
                                .replace('.', ',')
                        + "%";
    }

    private static void appendCell(StringBuilder html, String value) {
        html.append("<td>").append(escapeHtml(value)).append("</td>");
    }

    private static String escapeHtml(String value) {
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
