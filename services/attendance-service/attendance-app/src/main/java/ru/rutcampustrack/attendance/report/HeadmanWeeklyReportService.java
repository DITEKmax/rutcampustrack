package ru.rutcampustrack.attendance.report;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ru.rutcampustrack.academic.grpc.GroupMembersResponse;
import ru.rutcampustrack.academic.grpc.GroupResponse;
import ru.rutcampustrack.academic.grpc.SemesterResponse;
import ru.rutcampustrack.academic.grpc.StudentInfo;
import ru.rutcampustrack.attendance.contract.dto.report.HeadmanWeeklyExportRequest;
import ru.rutcampustrack.attendance.contract.dto.report.HeadmanWeeklyWeekOption;
import ru.rutcampustrack.attendance.contract.dto.report.HeadmanWeeklyWeeksResponse;
import ru.rutcampustrack.attendance.contract.enums.AttendanceStatus;
import ru.rutcampustrack.attendance.exception.AccessDeniedException;
import ru.rutcampustrack.attendance.exception.AcademicServiceUnavailableException;
import ru.rutcampustrack.attendance.exception.ReportExportTooLargeException;
import ru.rutcampustrack.attendance.exception.ReportExportUnavailableException;
import ru.rutcampustrack.attendance.exception.ReportValidationException;
import ru.rutcampustrack.attendance.grpc.AcademicGrpcClient;
import ru.rutcampustrack.attendance.grpc.DocumentRendererGrpcClient;
import ru.rutcampustrack.attendance.grpc.ScheduleGrpcClient;
import ru.rutcampustrack.attendance.journal.JournalLessonPolicy;
import ru.rutcampustrack.attendance.report.teacher.TeacherAttendanceDocxRenderer;
import ru.rutcampustrack.attendance.report.teacher.TeacherAttendanceExportModel;
import ru.rutcampustrack.attendance.report.teacher.TeacherAttendanceExportModel.Cell;
import ru.rutcampustrack.attendance.report.teacher.TeacherAttendanceExportModel.Column;
import ru.rutcampustrack.attendance.report.teacher.TeacherAttendanceExportModel.Context;
import ru.rutcampustrack.attendance.report.teacher.TeacherAttendanceExportModel.ReportKind;
import ru.rutcampustrack.attendance.report.teacher.TeacherAttendanceExportModel.Row;
import ru.rutcampustrack.attendance.security.RequestContext;
import ru.rutcampustrack.attendance.shared.port.AttendanceReadPort;
import ru.rutcampustrack.attendance.shared.port.AttendanceRecord;
import ru.rutcampustrack.documentrenderer.grpc.TargetFormat;
import ru.rutcampustrack.schedule.grpc.LessonResponse;
import ru.rutcampustrack.schedule.grpc.LessonsResponse;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class HeadmanWeeklyReportService {

    private static final int MAX_SELECTED_WEEKS = 64;
    private static final int MAX_EXPORT_RESPONSE_BYTES = 20 * 1024 * 1024;
    private static final Map<String, String> TYPE_LABELS = Map.of(
            "lecture", "Лекция",
            "practice", "Практика",
            "lab", "Лабораторная",
            "laboratory", "Лабораторная");

    private final AcademicGrpcClient academicGrpcClient;
    private final ScheduleGrpcClient scheduleGrpcClient;
    private final AttendanceReadPort attendanceReadPort;
    private final TeacherAttendanceDocxRenderer docxRenderer;
    private final HeadmanWeeklyTabularRenderer tabularRenderer;
    private final DocumentRendererGrpcClient documentRendererGrpcClient;
    private final RequestContext requestContext;
    private final Clock clock;

    public HeadmanWeeklyWeeksResponse getActiveSemesterWeeks() {
        ensureHeadman();
        return buildWeeksResponse(academicGrpcClient.getActiveSemester(), LocalDate.now(clock));
    }

    public HeadmanWeeklyExportResult exportSingleWeek(LocalDate weekStart, String rawFormat) {
        HeadmanWeeklyReportFormat format = HeadmanWeeklyReportFormat.from(rawFormat);
        return export(buildReportModels(List.of(weekStart)), format);
    }

    public HeadmanWeeklyExportResult exportSelectedWeeks(HeadmanWeeklyExportRequest request) {
        if (request == null || request.weekStarts() == null || request.weekStarts().isEmpty()) {
            throw new ReportValidationException("Выбери хотя бы одну неделю");
        }
        if (request.weekStarts().size() > MAX_SELECTED_WEEKS) {
            throw new ReportValidationException("Можно выбрать не более 64 недель за один раз");
        }
        HeadmanWeeklyReportFormat format = HeadmanWeeklyReportFormat.from(request.format());
        return export(buildReportModels(request.weekStarts()), format);
    }

    private HeadmanWeeklyExportResult export(List<TeacherAttendanceExportModel> models,
                                             HeadmanWeeklyReportFormat format) {
        if (models.isEmpty()) {
            throw new ReportValidationException("Выбери хотя бы одну неделю");
        }
        List<LocalDate> weekStarts = models.stream().map(model -> model.context().periodFrom()).toList();
        String fileName = HeadmanWeeklyReportFiles.buildFileName(
                models.get(0).context().groupLabel(), weekStarts, format);
        byte[] content;
        switch (format) {
            case DOCX -> content = docxRenderer.render(models);
            case HTML -> content = tabularRenderer.renderHtml(models);
            case XLSX -> content = tabularRenderer.renderXlsx(models);
            case PDF, PNG -> {
                byte[] docx = docxRenderer.render(models);
                TargetFormat target = format == HeadmanWeeklyReportFormat.PDF
                        ? TargetFormat.PDF
                        : TargetFormat.PNG_PAGES_ZIP;
                try {
                        content = documentRendererGrpcClient.convertDocxForHeadmanWeeklyExport(docx, target);
                    } catch (StatusRuntimeException ex) {
                        if (ex.getStatus().getCode() == Status.Code.RESOURCE_EXHAUSTED) {
                            throw new ReportExportTooLargeException(
                                    "Экспорт превышает лимит передачи сервиса преобразования. Попробуй скачать меньше недель или выбери HTML/XLSX.");
                        }
                    throw ex;
                }
            }
            default -> throw new IllegalStateException("Unsupported weekly report format: " + format);
        }
        if (content == null || content.length == 0) {
            throw new ReportExportUnavailableException("Сервер формирования не вернул файл журнала");
        }
        if (content.length > MAX_EXPORT_RESPONSE_BYTES) {
            throw new ReportExportTooLargeException(
                    "Экспорт превышает общий лимит ответа 20 МиБ. Выбери меньше недель или формат HTML/XLSX.");
        }
        return new HeadmanWeeklyExportResult(fileName, format.contentType(), content);
    }

    List<TeacherAttendanceExportModel> buildReportModels(List<LocalDate> requestedWeekStarts) {
        ensureHeadman();
        SemesterResponse semester = requireActiveSemester(academicGrpcClient.getActiveSemester());
        LocalDate semesterStart = parseUpstreamDate(semester.getDateFrom(), "semester date_from");
        LocalDate semesterEnd = parseUpstreamDate(semester.getDateTo(), "semester date_to");
        if (semesterEnd.isBefore(semesterStart)) {
            throw new AcademicServiceUnavailableException("Academic returned an invalid active-semester date range");
        }
        List<LocalDate> weekStarts = normalizeWeekStarts(requestedWeekStarts, semester, LocalDate.now(clock));
        Long contextGroupId = requestContext.getGroupId();
        if (contextGroupId == null || contextGroupId <= 0) {
            throw new AccessDeniedException("Headman group is not available in request context");
        }
        long groupId = contextGroupId;
        GroupResponse group = academicGrpcClient.getGroup(groupId);
        if (group == null || group.getId() != groupId || group.getName().isBlank()) {
            throw new AcademicServiceUnavailableException("Academic returned incomplete group display metadata");
        }
        var generatedAt = clock.instant();
        return weekStarts.stream()
                .map(weekStart -> buildSingleWeekModel(groupId, group.getName(), semester,
                        semesterStart, semesterEnd, weekStart, generatedAt))
                .toList();
    }

    static HeadmanWeeklyWeeksResponse buildWeeksResponse(SemesterResponse semester, LocalDate today) {
        SemesterResponse active = requireActiveSemester(semester);
        LocalDate semesterStart = parseUpstreamDate(active.getDateFrom(), "semester date_from");
        LocalDate semesterEnd = parseUpstreamDate(active.getDateTo(), "semester date_to");
        if (semesterEnd.isBefore(semesterStart)) {
            throw new ReportValidationException("Active semester end date is before start date");
        }
        return new HeadmanWeeklyWeeksResponse(
                active.getId(), active.getName(), semesterStart, semesterEnd,
                activeSemesterWeeks(semesterStart, semesterEnd, today), HeadmanWeeklyReportFormat.catalogue());
    }

    static List<HeadmanWeeklyWeekOption> activeSemesterWeeks(LocalDate semesterStart,
                                                            LocalDate semesterEnd,
                                                            LocalDate today) {
        if (semesterStart == null || semesterEnd == null || semesterEnd.isBefore(semesterStart) || today == null) {
            throw new ReportValidationException("Invalid active-semester week range");
        }
        if (today.isBefore(semesterStart)) return List.of();
        LocalDate lastAvailableDate = today.isAfter(semesterEnd) ? semesterEnd : today;
        LocalDate firstMonday = mondayOf(semesterStart);
        LocalDate lastMonday = mondayOf(lastAvailableDate);
        ArrayList<HeadmanWeeklyWeekOption> result = new ArrayList<>();
        LocalDate weekStart = firstMonday;
        int weekOfSemester = 1;
        while (!weekStart.isAfter(lastMonday)) {
            LocalDate weekEnd = weekStart.plusDays(6);
            boolean current = !today.isBefore(weekStart) && !today.isAfter(weekEnd);
            int isoWeek = weekStart.get(WeekFields.ISO.weekOfWeekBasedYear());
            result.add(new HeadmanWeeklyWeekOption(
                    weekOfSemester,
                    isoWeek,
                    "Н" + weekOfSemester,
                    weekStart,
                    weekEnd,
                    current));
            weekStart = weekStart.plusWeeks(1);
            weekOfSemester++;
        }
        return List.copyOf(result);
    }

    private TeacherAttendanceExportModel buildSingleWeekModel(long groupId,
                                                               String groupName,
                                                               SemesterResponse semester,
                                                               LocalDate semesterStart,
                                                               LocalDate semesterEnd,
                                                               LocalDate weekStart,
                                                               java.time.Instant generatedAt) {
        LocalDate weekEnd = weekStart.plusDays(6);
        LessonsResponse response = scheduleGrpcClient.getLessonsByGroup(
                groupId, semester.getId(), weekStart.toString(), weekEnd.toString());
        List<WeeklyLesson> lessons = validateLessons(response, groupId, semester.getId(),
                semesterStart, semesterEnd, weekStart, weekEnd);
        Map<Long, AcademicGrpcClient.SubjectDetails> subjects = academicGrpcClient.getSubjectDetailsByIds(
                lessons.stream().map(lesson -> lesson.lesson().getSubjectId()).distinct().toList());
        List<Column> columns = lessons.stream().map(lesson -> {
            AcademicGrpcClient.SubjectDetails subject = subjects.get(lesson.lesson().getSubjectId());
            if (subject == null || subject.name() == null || subject.name().isBlank()) {
                throw new AcademicServiceUnavailableException("Academic returned missing subject display metadata");
            }
            String lessonType = normalize(lesson.lesson().getLessonType());
            return new Column(
                    lesson.lesson().getId(), lesson.date(), lesson.startTime(), lesson.lesson().getLessonNumber(),
                    lesson.lesson().getSubjectId(), subject.name(), lessonType, typeLabel(lessonType),
                    lesson.timing().status());
        }).toList();

        Map<LocalDate, GroupMembersResponse> rosterByDate = new HashMap<>();
        Map<Long, WeeklyStudent> rowsById = new LinkedHashMap<>();
        Map<Long, Set<Long>> membersByLesson = new HashMap<>();
        for (WeeklyLesson lesson : lessons) {
            GroupMembersResponse roster = rosterByDate.computeIfAbsent(lesson.date(), date -> {
                GroupMembersResponse value = academicGrpcClient.getGroupMembers(groupId, date, semester.getId());
                validateHistoricalRoster(value, date, semester.getId());
                return value;
            });
            Set<Long> memberIds = new HashSet<>();
            for (StudentInfo student : roster.getStudentsList()) {
                if (student.getUserId() <= 0 || student.getDisplayName().isBlank() || !memberIds.add(student.getUserId())) {
                    throw new AcademicServiceUnavailableException(
                            "Academic returned an invalid historical group member for the attendance journal");
                }
                WeeklyStudent row = rowsById.computeIfAbsent(
                        student.getUserId(), ignored -> new WeeklyStudent(student.getUserId(), student.getDisplayName(), lesson.date()));
                if (!lesson.date().isBefore(row.nameAsOf)) {
                    row.displayName = student.getDisplayName();
                    row.nameAsOf = lesson.date();
                }
            }
            membersByLesson.put(lesson.lesson().getId(), memberIds);
        }

        Map<AttendanceKey, AttendanceRecord> attendance = new HashMap<>();
        Set<Long> lessonIds = lessons.stream().map(value -> value.lesson().getId())
                .collect(java.util.stream.Collectors.toSet());
        Map<Long, WeeklyLesson> lessonsById = new HashMap<>();
        lessons.forEach(lesson -> lessonsById.put(lesson.lesson().getId(), lesson));
        for (AttendanceRecord record : attendanceReadPort.findByGroupAndDateRange(groupId, weekStart, weekEnd)) {
            if (record == null || record.lessonId() == null || record.userId() == null || record.groupId() == null
                    || record.subjectId() == null || record.lessonDate() == null || record.lessonNumber() == null) {
                throw new ReportExportUnavailableException("Attendance returned an incomplete journal record");
            }
            if (!lessonIds.contains(record.lessonId()) || !Objects.equals(record.groupId(), groupId)) continue;
            WeeklyLesson lesson = lessonsById.get(record.lessonId());
            if (lesson == null) {
                throw new ReportExportUnavailableException("Attendance returned an unknown lesson");
            }
            if (lesson.lesson().getSubjectId() != record.subjectId()
                    || lesson.lesson().getLessonNumber() != record.lessonNumber()
                    || !lesson.date().equals(record.lessonDate())) {
                throw new ReportExportUnavailableException("Attendance returned a mismatched journal record");
            }
            AttendanceKey key = new AttendanceKey(record.lessonId(), record.userId());
            if (attendance.putIfAbsent(key, record) != null) {
                throw new ReportExportUnavailableException("Attendance returned duplicate records for a journal cell");
            }
        }

        List<Row> rows = rowsById.values().stream()
                .sorted(Comparator.comparing((WeeklyStudent row) -> row.displayName, String.CASE_INSENSITIVE_ORDER)
                        .thenComparingLong(row -> row.studentId))
                .map(student -> {
                    Map<Long, Cell> cells = new LinkedHashMap<>();
                    for (WeeklyLesson lesson : lessons) {
                        long lessonId = lesson.lesson().getId();
                        if (!membersByLesson.getOrDefault(lessonId, Set.of()).contains(student.studentId)
                                || !lesson.timing().hasStarted(generatedAt)) continue;
                        AttendanceRecord mark = attendance.get(new AttendanceKey(lessonId, student.studentId));
                        if (mark == null || mark.status() == null || mark.status() == AttendanceStatus.CANCELLED) continue;
                        cells.put(lessonId, new Cell(symbolFor(mark.status()), mark.status(), true));
                    }
                    return new Row(student.studentId, student.displayName, cells, Optional.empty());
                })
                .toList();
        LinkedHashSet<String> typeLabels = new LinkedHashSet<>();
        columns.stream().map(Column::typeLabel).filter(value -> !value.isBlank()).forEach(typeLabels::add);
        return new TeacherAttendanceExportModel(
                new Context(ReportKind.WEEKLY_ATTENDANCE, semester.getId(), semester.getName(),
                        groupId, groupName, null, "", weekStart, weekEnd,
                        List.copyOf(typeLabels), generatedAt),
                columns, rows);
    }

    private List<WeeklyLesson> validateLessons(LessonsResponse response,
                                               long groupId,
                                               long semesterId,
                                               LocalDate semesterStart,
                                               LocalDate semesterEnd,
                                               LocalDate weekStart,
                                               LocalDate weekEnd) {
        if (response == null) {
            throw new AcademicServiceUnavailableException("Schedule returned no weekly lesson response");
        }
        List<WeeklyLesson> result = new ArrayList<>();
        Set<Long> lessonIds = new HashSet<>();
        for (LessonResponse lesson : response.getLessonsList()) {
            if (lesson.getId() <= 0 || lesson.getGroupId() != groupId || lesson.getSemesterId() != semesterId
                    || lesson.getSubjectId() <= 0 || lesson.getLessonNumber() <= 0
                    || !lessonIds.add(lesson.getId())) {
                throw new AcademicServiceUnavailableException("Schedule returned a mismatched or duplicate weekly lesson");
            }
            JournalLessonPolicy.Timing timing;
            try {
                timing = JournalLessonPolicy.requireTiming(lesson);
            } catch (RuntimeException invalid) {
                throw new AcademicServiceUnavailableException("Schedule returned incomplete weekly lesson timing");
            }
            if (timing.date().isBefore(weekStart) || timing.date().isAfter(weekEnd)
                    || timing.date().isBefore(semesterStart) || timing.date().isAfter(semesterEnd)) {
                throw new AcademicServiceUnavailableException("Schedule returned a lesson outside the selected report context");
            }
            if ("CANCELLED".equals(timing.status())) continue;
            result.add(new WeeklyLesson(lesson, timing, timing.date(), LocalTime.parse(lesson.getStartTime())));
        }
        result.sort(Comparator.comparing(WeeklyLesson::date)
                .thenComparing(WeeklyLesson::startTime)
                .thenComparingInt(value -> value.lesson().getLessonNumber())
                .thenComparingLong(value -> value.lesson().getId()));
        if (result.size() > 5_000) {
            throw new ReportExportTooLargeException("В выбранной неделе слишком много пар для передачи. Выбери другой диапазон.");
        }
        return List.copyOf(result);
    }

    private List<LocalDate> normalizeWeekStarts(List<LocalDate> requestedWeekStarts,
                                               SemesterResponse semester,
                                               LocalDate today) {
        if (requestedWeekStarts == null || requestedWeekStarts.isEmpty()
                || requestedWeekStarts.stream().anyMatch(Objects::isNull)) {
            throw new ReportValidationException("Выбери хотя бы одну неделю");
        }
        if (requestedWeekStarts.size() > MAX_SELECTED_WEEKS) {
            throw new ReportValidationException("Можно выбрать не более 64 недель за один раз");
        }
        Set<LocalDate> available = activeSemesterWeeks(
                parseUpstreamDate(semester.getDateFrom(), "semester date_from"),
                parseUpstreamDate(semester.getDateTo(), "semester date_to"), today).stream()
                .map(HeadmanWeeklyWeekOption::getWeekStart)
                .collect(java.util.stream.Collectors.toSet());
        List<LocalDate> normalized = requestedWeekStarts.stream()
                .distinct()
                .sorted(Comparator.naturalOrder())
                .toList();
        for (LocalDate weekStart : normalized) {
            if (!available.contains(weekStart)) {
                throw new ReportValidationException("Week is outside the available active-semester range: " + weekStart);
            }
        }
        return normalized;
    }

    private static SemesterResponse requireActiveSemester(SemesterResponse semester) {
        if (semester == null || semester.getId() <= 0 || semester.getName().isBlank()) {
            throw new AcademicServiceUnavailableException("Academic returned incomplete active-semester metadata");
        }
        return semester;
    }

    private static void validateHistoricalRoster(GroupMembersResponse roster, LocalDate asOfDate, long semesterId) {
        if (roster == null || !roster.hasAsOfDate() || !roster.hasSemesterId()
                || !asOfDate.toString().equals(roster.getAsOfDate()) || roster.getSemesterId() != semesterId) {
            throw new AcademicServiceUnavailableException("Academic returned a missing or mismatched historical roster echo");
        }
        Set<Long> studentIds = new HashSet<>();
        for (StudentInfo student : roster.getStudentsList()) {
            if (student.getUserId() <= 0 || !studentIds.add(student.getUserId())) {
                throw new AcademicServiceUnavailableException("Academic returned duplicate or invalid historical student identity");
            }
        }
    }

    private void ensureHeadman() {
        Long groupId = requestContext.getGroupId();
        if (groupId == null || groupId <= 0) {
            throw new AccessDeniedException("Headman group is not available in request context");
        }
        if (!academicGrpcClient.hasAssistantPermission(groupId, "VIEW_STATS")) {
            throw new AccessDeniedException("Отсутствует право VIEW_STATS");
        }
    }

    private static String symbolFor(AttendanceStatus status) {
        return switch (status) {
            case PRESENT -> "+";
            case ABSENT -> "н";
            case EXCUSED, FREE_ATTENDANCE -> "у";
            case CANCELLED -> "";
        };
    }

    private static String typeLabel(String lessonType) {
        String normalized = normalize(lessonType);
        return TYPE_LABELS.getOrDefault(normalized, normalized);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static LocalDate parseUpstreamDate(String value, String field) {
        try {
            return LocalDate.parse(value);
        } catch (RuntimeException ex) {
            throw new AcademicServiceUnavailableException("Academic returned an invalid " + field);
        }
    }

    private static LocalDate mondayOf(LocalDate date) {
        return date.with(DayOfWeek.MONDAY);
    }

    private static final class WeeklyStudent {
        private final long studentId;
        private String displayName;
        private LocalDate nameAsOf;

        private WeeklyStudent(long studentId, String displayName, LocalDate nameAsOf) {
            this.studentId = studentId;
            this.displayName = displayName;
            this.nameAsOf = nameAsOf;
        }
    }

    private record WeeklyLesson(
            LessonResponse lesson,
            JournalLessonPolicy.Timing timing,
            LocalDate date,
            LocalTime startTime
    ) {}

    private record AttendanceKey(long lessonId, long userId) {}
}
