package ru.rutcampustrack.attendance.report;

import ru.rutcampustrack.attendance.contract.enums.AttendanceStatus;

import java.time.LocalDate;
import java.util.List;

public record HeadmanWeeklyReportModel(
        Long groupId,
        String groupCode,
        String groupHeader,
        Long semesterId,
        String semesterName,
        int weekOfSemester,
        LocalDate weekStart,
        LocalDate weekEnd,
        List<Day> days,
        List<Student> students
) {
    public static final int MAX_STUDENTS = 35;
    public static final int MAX_LESSONS_PER_DAY = 5;
    public static final int TEMPLATE_DAYS = 6;

    public HeadmanWeeklyReportModel {
        days = List.copyOf(days);
        students = List.copyOf(students);
    }

    static String symbolFor(AttendanceStatus status) {
        if (status == null) {
            return "";
        }
        return switch (status) {
            case PRESENT -> "+";
            case ABSENT -> "\u043d";
            case EXCUSED, FREE_ATTENDANCE -> "\u0443";
            case CANCELLED -> "";
        };
    }

    record Day(LocalDate date, List<LessonSlot> lessons) {
        Day {
            lessons = List.copyOf(lessons);
        }
    }

    record LessonSlot(
            Long lessonId,
            int lessonNumber,
            Long subjectId,
            String subjectName,
            String lessonType
    ) {}

    record Student(
            Long userId,
            String displayName,
            List<AttendanceMark> attendance
    ) {
        Student {
            attendance = List.copyOf(attendance);
        }
    }

    record AttendanceMark(
            Long lessonId,
            LocalDate date,
            int lessonNumber,
            String symbol
    ) {}
}
