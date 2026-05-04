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
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
@RequiredArgsConstructor
public class HeadmanWeeklyReportService {

    private static final Locale RU_LOCALE = Locale.forLanguageTag("ru");
    private static final int MAX_SUBJECT_DISPLAY_CHARS = 28;
    private static final Set<String> SUBJECT_ABBREVIATION_STOP_WORDS = Set.of(
            "и", "в", "во", "на", "по", "для", "с", "со", "из", "от", "о", "об", "при"
    );
    private static final List<TextReplacement> SUBJECT_NAME_REPLACEMENTS = List.of(
            new TextReplacement("Системы искусственного интеллекта", "Сист. ИИ"),
            new TextReplacement("искусственного интеллекта", "ИИ"),
            new TextReplacement("машинное обучение", "МО"),
            new TextReplacement("Сервис-ориентированное", "Сервис-ориент."),
            new TextReplacement("программирование", "прогр."),
            new TextReplacement("технологии", "техн."),
            new TextReplacement("Технологии", "Техн."),
            new TextReplacement("протоколы", "прот."),
            new TextReplacement("Администрирование", "Админ."),
            new TextReplacement("Разработка", "Разраб."),
            new TextReplacement("приложений", "прил."),
            new TextReplacement("Информационные", "Инф.")
    );

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

        Map<Long, AcademicGrpcClient.SubjectDetails> subjects = academicGrpcClient.getSubjectDetailsByIds(lessons.stream()
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

        Map<Long, Integer> reportSlotByLessonId = new HashMap<>();
        List<HeadmanWeeklyReportModel.Day> days = new ArrayList<>(HeadmanWeeklyReportModel.TEMPLATE_DAYS);
        for (int day = 0; day < HeadmanWeeklyReportModel.TEMPLATE_DAYS; day++) {
            LocalDate date = weekStart.plusDays(day);
            List<LessonResponse> dayLessons = lessons.stream()
                    .filter(lesson -> LocalDate.parse(lesson.getDate()).equals(date))
                    .toList();
            List<HeadmanWeeklyReportModel.LessonSlot> slots = new ArrayList<>(dayLessons.size());
            for (int i = 0; i < dayLessons.size(); i++) {
                LessonResponse lesson = dayLessons.get(i);
                int reportSlot = i + 1;
                reportSlotByLessonId.put(lesson.getId(), reportSlot);
                AcademicGrpcClient.SubjectDetails subject = subjects.get(lesson.getSubjectId());
                slots.add(new HeadmanWeeklyReportModel.LessonSlot(
                        lesson.getId(),
                        reportSlot,
                        lesson.getSubjectId(),
                        compactSubjectName(subject == null ? "Unknown" : subject.name()),
                        lessonTypeAbbreviation(subject == null ? "" : subject.type())));
            }
            days.add(new HeadmanWeeklyReportModel.Day(date, slots));
        }

        List<HeadmanWeeklyReportModel.Student> reportStudents = students.stream()
                .map(student -> new HeadmanWeeklyReportModel.Student(
                        student.getUserId(),
                        compactStudentDisplayName(student.getDisplayName()),
                        lessons.stream()
                                .map(lesson -> attendanceMark(student, lesson, attendance, reportSlotByLessonId))
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
            Map<AttendanceKey, AttendanceRecord> attendance,
            Map<Long, Integer> reportSlotByLessonId) {
        AttendanceRecord record = attendance.get(new AttendanceKey(lesson.getId(), student.getUserId()));
        return new HeadmanWeeklyReportModel.AttendanceMark(
                lesson.getId(),
                LocalDate.parse(lesson.getDate()),
                reportSlotByLessonId.getOrDefault(lesson.getId(), lesson.getLessonNumber()),
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

    static String compactStudentDisplayName(String displayName) {
        String normalized = normalizeSpaces(displayName);
        if (normalized.isEmpty()) {
            return "";
        }
        String[] parts = normalized.split(" ");
        if (parts.length < 2) {
            return normalized;
        }

        StringBuilder initials = new StringBuilder();
        for (int i = 1; i < parts.length && initials.length() < 4; i++) {
            String part = parts[i].replaceAll("[^\\p{L}]", "");
            if (!part.isEmpty()) {
                initials.append(part.substring(0, 1).toUpperCase(RU_LOCALE)).append('.');
            }
        }
        return initials.isEmpty() ? normalized : parts[0] + " " + initials;
    }

    static String compactSubjectName(String subjectName) {
        String compact = normalizeSpaces(subjectName);
        for (TextReplacement replacement : SUBJECT_NAME_REPLACEMENTS) {
            compact = compact.replace(replacement.from(), replacement.to());
        }
        if (compact.length() <= MAX_SUBJECT_DISPLAY_CHARS) {
            return compact;
        }

        String abbreviated = abbreviateSubjectByInitials(compact);
        if (!abbreviated.isBlank()) {
            return abbreviated.length() <= MAX_SUBJECT_DISPLAY_CHARS
                    ? abbreviated
                    : truncateWithEllipsis(abbreviated);
        }
        return truncateWithEllipsis(compact);
    }

    static String lessonTypeAbbreviation(String lessonType) {
        return switch (normalizeSpaces(lessonType).toLowerCase(Locale.ROOT)) {
            case "lecture" -> "\u041b\u041a";
            case "practice" -> "\u041f\u0417";
            case "lab" -> "\u041b\u0417";
            default -> "";
        };
    }

    private static String abbreviateSubjectByInitials(String value) {
        String[] words = value.split(" ");
        if (words.length < 2) {
            return "";
        }

        StringBuilder result = new StringBuilder(words[0]);
        boolean hasInitials = false;
        for (int i = 1; i < words.length; i++) {
            String word = words[i].replaceAll("^[^\\p{L}\\p{N}]+|[^\\p{L}\\p{N}]+$", "");
            if (word.isEmpty()
                    || SUBJECT_ABBREVIATION_STOP_WORDS.contains(word.toLowerCase(RU_LOCALE))) {
                continue;
            }
            if (!hasInitials) {
                result.append(' ');
                hasInitials = true;
            }
            result.append(word.substring(0, 1).toUpperCase(RU_LOCALE))
                    .append('.');
        }
        return result.toString();
    }

    private static String truncateWithEllipsis(String value) {
        if (value.length() <= MAX_SUBJECT_DISPLAY_CHARS) {
            return value;
        }
        return value.substring(0, MAX_SUBJECT_DISPLAY_CHARS - 3).stripTrailing() + "...";
    }

    private static String normalizeSpaces(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", " ");
    }

    private record AttendanceKey(Long lessonId, Long userId) {}

    private record TextReplacement(String from, String to) {}
}
