package ru.rutcampustrack.attendance.report.teacher;

import ru.rutcampustrack.attendance.report.teacher.TeacherAttendanceExportModel.Cell;
import ru.rutcampustrack.attendance.report.teacher.TeacherAttendanceExportModel.Column;

final class TeacherReportPresentation {
    static final String NO_STATS =
            "Нет данных о посещаемости за выбранный период. Отчёт сформирован по выбранным фильтрам.";

    private TeacherReportPresentation() {}

    static boolean noStats(TeacherStatsExportModel model) {
        return model.rows().isEmpty() || model.context().lessonsCount() == 0;
    }

    static String journalLessonHeading(Column column, String date, String separator) {
        String type = column.typeLabel().isBlank() ? column.lessonType() : column.typeLabel();
        String heading = type.isBlank() ? date : date + separator + type;
        return cancelled(column) ? heading + separator + "Отменено" : heading;
    }

    private static boolean cancelled(Column column) {
        return "CANCELLED".equalsIgnoreCase(column.state());
    }

    enum MarkFill {
        NEUTRAL(""),
        PRESENT("DBF8EF"),
        EXCUSED("FDF0D6"),
        ABSENT("FCE2E2");

        final String rgb;

        MarkFill(String rgb) {
            this.rgb = rgb;
        }

        static MarkFill forCell(Column column, Cell cell) {
            if (cancelled(column)
                    || cell == null
                    || !cell.recordPresent()
                    || cell.symbol().isBlank()
                    || cell.status() == null) {
                return NEUTRAL;
            }
            return switch (cell.status()) {
                case PRESENT -> PRESENT;
                case EXCUSED, FREE_ATTENDANCE -> EXCUSED;
                case ABSENT -> ABSENT;
                case CANCELLED -> NEUTRAL;
            };
        }
    }
}
