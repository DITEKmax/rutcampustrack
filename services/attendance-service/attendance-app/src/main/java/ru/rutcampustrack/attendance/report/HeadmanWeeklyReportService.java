package ru.rutcampustrack.attendance.report;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ru.rutcampustrack.academic.grpc.GroupResponse;
import ru.rutcampustrack.academic.grpc.SemesterResponse;
import ru.rutcampustrack.academic.grpc.StudentInfo;
import ru.rutcampustrack.attendance.contract.api.ReportApi;
import ru.rutcampustrack.attendance.contract.dto.report.HeadmanWeeklyExportRequest;
import ru.rutcampustrack.attendance.contract.dto.report.HeadmanWeeklyWeekOption;
import ru.rutcampustrack.attendance.contract.dto.report.HeadmanWeeklyWeeksResponse;
import ru.rutcampustrack.attendance.exception.AccessDeniedException;
import ru.rutcampustrack.attendance.exception.ReportExportUnavailableException;
import ru.rutcampustrack.attendance.exception.ReportValidationException;
import ru.rutcampustrack.attendance.grpc.AcademicGrpcClient;
import ru.rutcampustrack.attendance.grpc.DocumentRendererGrpcClient;
import ru.rutcampustrack.attendance.grpc.ScheduleGrpcClient;
import ru.rutcampustrack.attendance.security.RequestContext;
import ru.rutcampustrack.attendance.shared.port.AttendanceReadPort;
import ru.rutcampustrack.attendance.shared.port.AttendanceRecord;
import ru.rutcampustrack.documentrenderer.grpc.TargetFormat;
import ru.rutcampustrack.schedule.grpc.LessonResponse;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
@RequiredArgsConstructor
public class HeadmanWeeklyReportService {

    private final AcademicGrpcClient academicGrpcClient;
    private final ScheduleGrpcClient scheduleGrpcClient;
    private final AttendanceReadPort attendanceReadPort;
    private final DocxRenderer docxRenderer;
    private final DocumentRendererGrpcClient documentRendererGrpcClient;
    private final RequestContext requestContext;
    private final Clock clock;

    public HeadmanWeeklyWeeksResponse getActiveSemesterWeeks() {
        ensureHeadman();
        SemesterResponse semester = academicGrpcClient.getActiveSemester();
        return buildWeeksResponse(semester, LocalDate.now(clock));
    }

    public HeadmanWeeklyExportResult exportSingleWeek(LocalDate weekStart, String rawFormat) {
        ensureHeadman();
        HeadmanWeeklyReportFormat format = HeadmanWeeklyReportFormat.from(rawFormat);
        List<HeadmanWeeklyReportModel> models = buildReportModels(List.of(weekStart));
        return export(models, format);
    }

    public HeadmanWeeklyExportResult exportSelectedWeeks(HeadmanWeeklyExportRequest request) {
        ensureHeadman();
        if (request == null || request.weekStarts() == null || request.weekStarts().isEmpty()) {
            throw new ReportValidationException("At least one week must be selected");
        }
        HeadmanWeeklyReportFormat format = HeadmanWeeklyReportFormat.from(request.format());
        List<LocalDate> weekStarts = request.weekStarts().stream()
                .distinct()
                .sorted(Comparator.naturalOrder())
                .toList();
        List<HeadmanWeeklyReportModel> models = buildReportModels(weekStarts);
        return export(models, format);
    }

    private HeadmanWeeklyExportResult export(List<HeadmanWeeklyReportModel> models,
                                             HeadmanWeeklyReportFormat format) {
        List<LocalDate> weekStarts = models.stream()
                .map(HeadmanWeeklyReportModel::weekStart)
                .toList();
        String fileName = HeadmanWeeklyReportFiles.buildFileName(models.get(0).groupCode(), weekStarts, format);
        return switch (format) {
            case DOCX -> new HeadmanWeeklyExportResult(fileName, format.contentType(), docxRenderer.render(models));
            case PDF -> new HeadmanWeeklyExportResult(
                    fileName,
                    format.contentType(),
                    documentRendererGrpcClient.convertDocx(docxRenderer.render(models), TargetFormat.PDF));
            case PNG -> exportPng(models, fileName);
        };
    }

    private HeadmanWeeklyExportResult exportPng(List<HeadmanWeeklyReportModel> models, String fileName) {
        if (models.size() == 1) {
            byte[] png = documentRendererGrpcClient.convertDocx(
                    docxRenderer.render(models),
                    TargetFormat.PNG);
            return new HeadmanWeeklyExportResult(fileName, HeadmanWeeklyReportFormat.PNG.contentType(), png);
        }
        return new HeadmanWeeklyExportResult(fileName, ReportApi.ZIP_MEDIA_TYPE, pngZip(models));
    }

    private byte[] pngZip(List<HeadmanWeeklyReportModel> models) {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream();
             ZipOutputStream zip = new ZipOutputStream(output)) {
            for (HeadmanWeeklyReportModel model : models) {
                zip.putNextEntry(new ZipEntry(HeadmanWeeklyReportFiles.pngEntryName(model)));
                byte[] png = documentRendererGrpcClient.convertDocx(
                        docxRenderer.render(List.of(model)),
                        TargetFormat.PNG);
                zip.write(png);
                zip.closeEntry();
            }
            zip.finish();
            return output.toByteArray();
        } catch (IOException ex) {
            throw new ReportExportUnavailableException("Failed to build PNG archive: " + ex.getMessage());
        }
    }

    List<HeadmanWeeklyReportModel> buildReportModels(List<LocalDate> requestedWeekStarts) {
        ensureHeadman();
        SemesterResponse semester = academicGrpcClient.getActiveSemester();
        List<LocalDate> weekStarts = normalizeWeekStarts(requestedWeekStarts, semester);

        Long groupId = requestContext.getGroupId();
        GroupResponse group = academicGrpcClient.getGroup(groupId);
        String groupCode = group.getName().isBlank() ? ("group-" + groupId) : group.getName();

        List<StudentInfo> students = academicGrpcClient.getGroupMembers(groupId).getStudentsList().stream()
                .sorted(Comparator
                        .comparing(StudentInfo::getDisplayName, String.CASE_INSENSITIVE_ORDER)
                        .thenComparingLong(StudentInfo::getUserId))
                .toList();
        if (students.size() > HeadmanWeeklyReportModel.MAX_STUDENTS) {
            throw new ReportValidationException("Headman weekly template supports up to 35 students");
        }

        Map<Integer, HeadmanWeeklyWeekOption> weeksByStart = activeSemesterWeeks(
                LocalDate.parse(semester.getDateFrom()),
                LocalDate.parse(semester.getDateTo()),
                LocalDate.now(clock)).stream()
                .collect(Collectors.toMap(
                        HeadmanWeeklyWeekOption::getWeekOfSemester,
                        Function.identity()));

        return weekStarts.stream()
                .map(weekStart -> buildSingleWeekModel(
                        groupId,
                        groupCode,
                        semester,
                        students,
                        weekStart,
                        weeksByStart))
                .toList();
    }

    static HeadmanWeeklyWeeksResponse buildWeeksResponse(SemesterResponse semester, LocalDate today) {
        LocalDate semesterStart = LocalDate.parse(semester.getDateFrom());
        LocalDate semesterEnd = LocalDate.parse(semester.getDateTo());
        if (semesterEnd.isBefore(semesterStart)) {
            throw new ReportValidationException("Active semester end date is before start date");
        }

        return new HeadmanWeeklyWeeksResponse(
                semester.getId(),
                semester.getName(),
                semesterStart,
                semesterEnd,
                activeSemesterWeeks(semesterStart, semesterEnd, today));
    }

    static List<HeadmanWeeklyWeekOption> activeSemesterWeeks(LocalDate semesterStart,
                                                            LocalDate semesterEnd,
                                                            LocalDate today) {
        LocalDate firstMonday = mondayOf(semesterStart);
        LocalDate lastMonday = mondayOf(semesterEnd);

        ArrayList<HeadmanWeeklyWeekOption> result = new ArrayList<>();
        LocalDate weekStart = firstMonday;
        int weekOfSemester = 1;
        while (!weekStart.isAfter(lastMonday)) {
            LocalDate weekEnd = weekStart.plusDays(6);
            boolean current = today != null && !today.isBefore(weekStart) && !today.isAfter(weekEnd);
            int isoWeek = weekStart.get(WeekFields.ISO.weekOfWeekBasedYear());
            result.add(new HeadmanWeeklyWeekOption(
                    weekOfSemester,
                    isoWeek,
                    "\u041d" + weekOfSemester,
                    weekStart,
                    weekEnd,
                    current));
            weekStart = weekStart.plusWeeks(1);
            weekOfSemester++;
        }
        return List.copyOf(result);
    }

    private HeadmanWeeklyReportModel buildSingleWeekModel(Long groupId,
                                                          String groupCode,
                                                          SemesterResponse semester,
                                                          List<StudentInfo> students,
                                                          LocalDate weekStart,
                                                          Map<Integer, HeadmanWeeklyWeekOption> weeksByNumber) {
        LocalDate weekEnd = weekStart.plusDays(6);
        List<LessonResponse> lessons = scheduleGrpcClient.getLessonsByGroup(
                        groupId,
                        semester.getId(),
                        weekStart.toString(),
                        weekEnd.toString())
                .getLessonsList()
                .stream()
                .filter(lesson -> !"cancelled".equalsIgnoreCase(lesson.getStatus()))
                .sorted(Comparator
                        .comparing(LessonResponse::getDate)
                        .thenComparingInt(LessonResponse::getLessonNumber)
                        .thenComparingLong(LessonResponse::getId))
                .toList();

        validateTemplateLessonLimits(weekStart, lessons);

        Map<Long, String> subjectNames = academicGrpcClient.getSubjectsByIds(lessons.stream()
                .map(LessonResponse::getSubjectId)
                .distinct()
                .toList());

        Map<AttendanceKey, AttendanceRecord> attendance = attendanceReadPort
                .findByGroupAndDateRange(groupId, weekStart, weekEnd)
                .stream()
                .collect(Collectors.toMap(
                        record -> new AttendanceKey(record.lessonId(), record.userId()),
                        Function.identity(),
                        (first, ignored) -> first));

        List<HeadmanWeeklyReportModel.Day> days = new ArrayList<>(HeadmanWeeklyReportModel.TEMPLATE_DAYS);
        for (int day = 0; day < HeadmanWeeklyReportModel.TEMPLATE_DAYS; day++) {
            LocalDate date = weekStart.plusDays(day);
            List<HeadmanWeeklyReportModel.LessonSlot> slots = lessons.stream()
                    .filter(lesson -> LocalDate.parse(lesson.getDate()).equals(date))
                    .map(lesson -> new HeadmanWeeklyReportModel.LessonSlot(
                            lesson.getId(),
                            lesson.getLessonNumber(),
                            lesson.getSubjectId(),
                            subjectNames.getOrDefault(lesson.getSubjectId(), "Unknown"),
                            ""))
                    .toList();
            days.add(new HeadmanWeeklyReportModel.Day(date, slots));
        }

        List<HeadmanWeeklyReportModel.Student> reportStudents = students.stream()
                .map(student -> new HeadmanWeeklyReportModel.Student(
                        student.getUserId(),
                        student.getDisplayName(),
                        lessons.stream()
                                .map(lesson -> attendanceMark(student, lesson, attendance))
                                .toList()))
                .toList();

        int weekOfSemester = weekNumberFor(weekStart, weeksByNumber);
        return new HeadmanWeeklyReportModel(
                groupId,
                groupCode,
                "\u0413\u0440\u0443\u043f\u043f\u0430: " + groupCode + ", " + semester.getName(),
                semester.getId(),
                semester.getName(),
                weekOfSemester,
                weekStart,
                weekEnd,
                days,
                reportStudents);
    }

    private HeadmanWeeklyReportModel.AttendanceMark attendanceMark(
            StudentInfo student,
            LessonResponse lesson,
            Map<AttendanceKey, AttendanceRecord> attendance) {
        AttendanceRecord record = attendance.get(new AttendanceKey(lesson.getId(), student.getUserId()));
        return new HeadmanWeeklyReportModel.AttendanceMark(
                lesson.getId(),
                LocalDate.parse(lesson.getDate()),
                lesson.getLessonNumber(),
                record == null ? "" : HeadmanWeeklyReportModel.symbolFor(record.status()));
    }

    private List<LocalDate> normalizeWeekStarts(List<LocalDate> requestedWeekStarts, SemesterResponse semester) {
        if (requestedWeekStarts == null || requestedWeekStarts.isEmpty()) {
            throw new ReportValidationException("At least one week must be selected");
        }
        List<HeadmanWeeklyWeekOption> activeWeeks = buildWeeksResponse(semester, LocalDate.now(clock)).getWeeks();
        java.util.Set<LocalDate> allowed = activeWeeks.stream()
                .map(HeadmanWeeklyWeekOption::getWeekStart)
                .collect(Collectors.toSet());
        List<LocalDate> normalized = requestedWeekStarts.stream()
                .distinct()
                .sorted(Comparator.naturalOrder())
                .toList();
        for (LocalDate weekStart : normalized) {
            if (weekStart == null || !allowed.contains(weekStart)) {
                throw new ReportValidationException("Week is outside the active semester: " + weekStart);
            }
        }
        return normalized;
    }

    private static void validateTemplateLessonLimits(LocalDate weekStart, List<LessonResponse> lessons) {
        Map<LocalDate, Long> lessonsByDay = lessons.stream()
                .collect(Collectors.groupingBy(
                        lesson -> LocalDate.parse(lesson.getDate()),
                        Collectors.counting()));
        for (Map.Entry<LocalDate, Long> entry : lessonsByDay.entrySet()) {
            long dayIndex = java.time.temporal.ChronoUnit.DAYS.between(weekStart, entry.getKey());
            if (dayIndex >= HeadmanWeeklyReportModel.TEMPLATE_DAYS) {
                throw new ReportValidationException("Headman weekly template supports Monday-Saturday lessons only");
            }
            if (entry.getValue() > HeadmanWeeklyReportModel.MAX_LESSONS_PER_DAY) {
                throw new ReportValidationException("Headman weekly template supports up to 5 lessons per day");
            }
        }
    }

    private static int weekNumberFor(LocalDate weekStart, Map<Integer, HeadmanWeeklyWeekOption> weeksByNumber) {
        return weeksByNumber.entrySet().stream()
                .filter(entry -> entry.getValue().getWeekStart().equals(weekStart))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElseThrow(() -> new ReportValidationException("Week is outside the active semester: " + weekStart));
    }

    private void ensureHeadman() {
        if (!requestContext.isHeadman()) {
            throw new AccessDeniedException("Only a group headman can export weekly reports");
        }
        if (requestContext.getGroupId() == null) {
            throw new AccessDeniedException("Headman group is not available in request context");
        }
    }

    private static LocalDate mondayOf(LocalDate date) {
        return date.with(DayOfWeek.MONDAY);
    }

    private record AttendanceKey(Long lessonId, Long userId) {}
}
