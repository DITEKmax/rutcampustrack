package ru.rutcampustrack.attendance.report;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import ru.rutcampustrack.attendance.report.teacher.TeacherAttendanceExportModel;
import ru.rutcampustrack.attendance.report.teacher.TeacherAttendanceExportModel.Cell;
import ru.rutcampustrack.attendance.report.teacher.TeacherAttendanceExportModel.Column;
import ru.rutcampustrack.attendance.report.teacher.TeacherAttendanceExportModel.Context;
import ru.rutcampustrack.attendance.report.teacher.TeacherAttendanceExportModel.Row;

/** Presentation data shared by the HTML and Excel journals. */
record HeadmanTabularAttendance(List<Journal> journals, List<Student> students) {
    static final String NO_LESSONS = "Нет занятий за этот период.";
    static final String NO_STUDENTS = "Нет данных о составе группы.";
    static final String NO_MARKS = "Нет отметок посещаемости за этот период.";
    static final String DASH = "—";
    static final String SUMMARY = "Сводка посещаемости";
    static final String WEEKS = "Посещаемость по неделям";
    static final List<String> SUBJECT_STATS =
            List.of("Присутствовал", "% присутствия", "Уважительные", "Отсутствовал", "Всего отметок");

    static HeadmanTabularAttendance from(List<TeacherAttendanceExportModel> models) {
        var journals = new ArrayList<Journal>();
        Map<Long, String> names = new LinkedHashMap<>();
        for (TeacherAttendanceExportModel model : models) {
            List<Column> columns = model.columns();
            List<JournalRow> rows =
                    model.rows().stream()
                            .map(row -> new JournalRow(row, counts(columns, row)))
                            .toList();
            journals.add(new Journal(model.context(), columns, rows));
            if (model.context().subjectId() == null) {
                for (Row row : model.rows()) {
                    names.put(row.studentId(), row.displayName());
                }
            }
        }
        List<Map<Long, Counts>> weekCounts = journals.stream().map(Journal::countsById).toList();
        List<Student> students =
                names.entrySet().stream()
                        .map(
                                entry -> {
                                    List<Counts> weeks =
                                            weekCounts.stream()
                                                    .map(
                                                            week ->
                                                                    week.getOrDefault(
                                                                            entry.getKey(),
                                                                            Counts.NONE))
                                                    .toList();
                                    Counts total =
                                            weeks.stream().reduce(Counts.NONE, Counts::plus);
                                    return new Student(
                                            entry.getKey(), entry.getValue(), weeks, total);
                                })
                        .sorted(
                                Comparator.comparingDouble(
                                                (Student student) -> student.total().sortValue())
                                        .reversed()
                                        .thenComparing(
                                                Student::displayName,
                                                String.CASE_INSENSITIVE_ORDER)
                                        .thenComparingLong(Student::id))
                        .toList();
        return new HeadmanTabularAttendance(List.copyOf(journals), students);
    }

    boolean weekly() {
        return !journals.isEmpty() && !journals.getFirst().subject();
    }

    static String range(Context context) {
        return context.periodFrom() + " — " + context.periodTo();
    }

    static String shortType(Column column) {
        return switch (column.lessonType().toLowerCase(Locale.ROOT)) {
            case "lecture" -> "ЛК";
            case "practice" -> "ПЗ";
            case "lab", "laboratory" -> "ЛЗ";
            default -> column.typeLabel();
        };
    }

    static String numberAndType(Column column) {
        String type = shortType(column);
        return column.lessonNumber() > 0 ? (column.lessonNumber() + " " + type).strip() : type;
    }

    static boolean cancelled(Column column) {
        return "CANCELLED".equalsIgnoreCase(column.state());
    }

    private static Counts counts(List<Column> columns, Row row) {
        int present = 0;
        int excused = 0;
        int absent = 0;
        for (Column column : columns) {
            switch (Mark.forCell(column, row.cellsByLessonId().get(column.lessonId()))) {
                case PRESENT -> present++;
                case EXCUSED -> excused++;
                case ABSENT -> absent++;
                case EMPTY -> {}
            }
        }
        return new Counts(present, excused, absent);
    }

    record Journal(Context context, List<Column> columns, List<JournalRow> rows) {
        boolean subject() {
            return context.subjectId() != null;
        }

        String title() {
            return subject() ? context.subjectLabel() : "Неделя " + range(context);
        }

        List<String> subjectFilters() {
            var filters = new ArrayList<String>(3);
            if (!context.typeLabels().isEmpty()) {
                filters.add("Типы занятий: " + String.join(", ", context.typeLabels()));
            }
            if (!context.selectedWeekStarts().isEmpty()) {
                List<String> starts = context.selectedWeekStarts().stream().map(LocalDate::toString).toList();
                filters.add("Выбранные недели: " + String.join(", ", starts));
            }
            if (columns.stream().anyMatch(HeadmanTabularAttendance::cancelled)) {
                filters.add("Зачёркнутые пары имеют статус «Отменено».");
            }
            return filters;
        }

        String emptyMessage() {
            if (columns.isEmpty()) {
                return NO_LESSONS;
            }
            if (rows.isEmpty()) {
                return NO_STUDENTS;
            }
            return rows.stream().allMatch(row -> row.counts().denominator() == 0) ? NO_MARKS : "";
        }

        List<DateSpan> dates() {
            var dates = new ArrayList<DateSpan>();
            int start = 0;
            while (start < columns.size()) {
                LocalDate date = columns.get(start).date();
                int end = start + 1;
                while (end < columns.size() && columns.get(end).date().equals(date)) {
                    end++;
                }
                dates.add(new DateSpan(date, start, end));
                start = end;
            }
            return dates;
        }

        private Map<Long, Counts> countsById() {
            Map<Long, Counts> counts = new HashMap<>();
            for (JournalRow row : rows) {
                counts.put(row.row().studentId(), row.counts());
            }
            return counts;
        }
    }

    record DateSpan(LocalDate date, int start, int end) {}

    record JournalRow(Row row, Counts counts) {
        Mark mark(Column column) {
            return Mark.forCell(column, row.cellsByLessonId().get(column.lessonId()));
        }
    }

    record Student(long id, String displayName, List<Counts> weeks, Counts total) {}

    record Counts(int present, int excused, int absent) {
        private static final Counts NONE = new Counts(0, 0, 0);

        int denominator() {
            return present + excused + absent;
        }

        Counts plus(Counts other) {
            return new Counts(
                    present + other.present, excused + other.excused, absent + other.absent);
        }

        BigDecimal fraction() {
            return BigDecimal.valueOf(present)
                    .divide(BigDecimal.valueOf(denominator()), MathContext.DECIMAL64);
        }

        private double sortValue() {
            return denominator() == 0 ? -1 : (double) present / denominator();
        }
    }

    enum Mark {
        EMPTY("", ""),
        PRESENT("+", "present"),
        EXCUSED("у", "excused"),
        ABSENT("н", "absent");

        final String symbol;
        final String style;

        Mark(String symbol, String style) {
            this.symbol = symbol;
            this.style = style;
        }

        private static Mark forCell(Column column, Cell cell) {
            if (cell == null
                    || !cell.recordPresent()
                    || cell.status() == null
                    || cancelled(column)) {
                return EMPTY;
            }
            return switch (cell.status()) {
                case PRESENT -> PRESENT;
                case EXCUSED, FREE_ATTENDANCE -> EXCUSED;
                case ABSENT -> ABSENT;
                case CANCELLED -> EMPTY;
            };
        }
    }
}
