package ru.rutcampustrack.attendance.report.planned;

import ru.rutcampustrack.attendance.exception.ReportValidationException;
import ru.rutcampustrack.attendance.journal.JournalLessonPolicy;
import ru.rutcampustrack.attendance.report.ReportService;
import ru.rutcampustrack.attendance.report.teacher.TeacherAttendanceExportService;
import ru.rutcampustrack.schedule.grpc.LessonResponse;
import ru.rutcampustrack.teacher.grpc.*;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;

/** Reuses the actual Attendance roster projection; no teacher gRPC identity or outbound reader. */
public final class PlannedTeacherJournalReader
        implements TeacherAttendanceExportService.JournalReader {
    private final ReportService reports;
    private final AdmittedReportPlan plan;

    public PlannedTeacherJournalReader(ReportService reports, AdmittedReportPlan plan) {
        this.reports = reports;
        this.plan = plan;
    }

    @Override
    public TeacherJournalResponse read(long teacherId, List<Long> ids, boolean pastOnly) {
        if (teacherId != plan.getUserId()
                || ids == null
                || ids.isEmpty()
                || ids.size() > 100
                || ids.stream().anyMatch(id -> id == null || id <= 0)
                || new HashSet<>(ids).size() != ids.size()) throw invalid();
        List<LessonResponse> lessons =
                ids.stream()
                        .map(plan::getLessonById)
                        .sorted(
                                Comparator.comparing(LessonResponse::getDate)
                                        .thenComparingInt(LessonResponse::getLessonNumber)
                                        .thenComparingLong(LessonResponse::getId))
                        .toList();
        LessonResponse first = lessons.getFirst();
        Map<Long, TeacherJournalStudent.Builder> students = new HashMap<>();
        for (LessonResponse lesson : lessons) {
            if (lesson.getGroupId() != first.getGroupId()
                    || lesson.getSubjectId() != first.getSubjectId()
                    || lesson.getSemesterId() != first.getSemesterId()) throw invalid();
            if (pastOnly && !completed(lesson)) throw invalid();
            var roster = reports.getTeacherLessonAttendance(lesson.getId(), teacherId);
            for (var entry : roster.getEntries()) {
                var student =
                        students.computeIfAbsent(
                                entry.getUserId(),
                                id ->
                                        TeacherJournalStudent.newBuilder()
                                                .setStudentId(id)
                                                .setDisplayName(entry.getDisplayName()));
                boolean marked = entry.getStatus() != null;
                String status = marked ? entry.getStatus() : "";
                String symbol = entry.getSymbol() == null ? "" : entry.getSymbol();
                // Preserve the existing completed-history display; this never creates a mark.
                if (pastOnly && "closed".equalsIgnoreCase(lesson.getStatus()) && !marked) {
                    status = "absent";
                    symbol = "н";
                }
                student.addCells(
                        TeacherJournalCell.newBuilder()
                                .setLessonId(lesson.getId())
                                .setStatus(status)
                                .setSymbol(symbol)
                                .setRecordPresent(marked)
                                .setSource(entry.getSource() == null ? "" : entry.getSource()));
            }
        }
        return TeacherJournalResponse.newBuilder()
                .setServerNow(plan.clock().instant().toString())
                .addAllStudents(
                        students.values().stream()
                                .map(TeacherJournalStudent.Builder::build)
                                .sorted(
                                        Comparator.comparing(
                                                        TeacherJournalStudent::getDisplayName,
                                                        String.CASE_INSENSITIVE_ORDER)
                                                .thenComparingLong(
                                                        TeacherJournalStudent::getStudentId))
                                .toList())
                .build();
    }

    private boolean completed(LessonResponse lesson) {
        if ("closed".equalsIgnoreCase(lesson.getStatus())) return true;
        if (!"cancelled".equalsIgnoreCase(lesson.getStatus())) return false;
        return !LocalDate.parse(lesson.getDate())
                .atTime(LocalTime.parse(lesson.getEndTime()))
                .atZone(JournalLessonPolicy.MOSCOW)
                .toInstant()
                .isAfter(plan.clock().instant());
    }

    private static ReportValidationException invalid() {
        return new ReportValidationException("Некорректный набор пар журнала");
    }
}
