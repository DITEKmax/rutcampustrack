package ru.rutcampustrack.attendance.report.teacher;

import io.grpc.Status;
import ru.rutcampustrack.academic.grpc.GroupResponse;
import ru.rutcampustrack.attendance.contract.enums.AttendanceStatus;
import ru.rutcampustrack.attendance.exception.AcademicServiceUnavailableException;
import ru.rutcampustrack.attendance.exception.BadRequestException;
import ru.rutcampustrack.attendance.exception.ReportExportUnavailableException;
import ru.rutcampustrack.attendance.grpc.AcademicGrpcClient;
import ru.rutcampustrack.attendance.grpc.DocumentRendererGrpcClient;
import ru.rutcampustrack.attendance.grpc.ScheduleGrpcClient;
import ru.rutcampustrack.attendance.grpc.TeacherAcademicGrpcClient;
import ru.rutcampustrack.attendance.journal.JournalLessonPolicy;
import ru.rutcampustrack.attendance.report.ReportService;
import ru.rutcampustrack.documentrenderer.grpc.TargetFormat;
import ru.rutcampustrack.schedule.grpc.LessonResponse;
import ru.rutcampustrack.teacher.grpc.TeacherAttendanceExportRequest;
import ru.rutcampustrack.teacher.grpc.TeacherAttendanceExportResponse;
import ru.rutcampustrack.teacher.grpc.TeacherAttendanceReportKind;
import ru.rutcampustrack.teacher.grpc.TeacherAssignmentsResponse;
import ru.rutcampustrack.teacher.grpc.TeacherJournalCell;
import ru.rutcampustrack.teacher.grpc.TeacherJournalResponse;
import ru.rutcampustrack.teacher.grpc.TeacherJournalStudent;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Builds one complete, server-authorized teacher subject journal for export. */
public final class TeacherAttendanceExportService {
    private static final DateTimeFormatter SAFE_DATE = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final int JOURNAL_BATCH_SIZE = 100;
    private static final int MAX_EXPORT_LESSONS = 5_000;
    private static final int MAX_EXPORT_RESPONSE_BYTES = 20 * 1024 * 1024;
    private static final int EXCEL_MAX_COLUMNS = 16_384;
    private static final String DOCX_MIME =
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
    private static final String XLSX_MIME =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    private static final String HTML_MIME = "text/html; charset=UTF-8";
    private static final Map<String, String> TYPE_LABELS = Map.of(
            "lecture", "Лекция",
            "practice", "Практика",
            "laboratory", "Лабораторная",
            "lab", "Лабораторная");

    private final ReportService reportService;
    private final ScheduleGrpcClient schedule;
    private final AcademicGrpcClient academic;
    private final TeacherAcademicGrpcClient teacherAcademic;
    private final DocumentRendererGrpcClient renderer;
    private final BiFunction<Long, List<Long>, TeacherJournalResponse> journalReader;
    private final TeacherAttendanceDocxRenderer docxRenderer = new TeacherAttendanceDocxRenderer();

    public TeacherAttendanceExportService(ReportService reportService,
                                          ScheduleGrpcClient schedule,
                                          AcademicGrpcClient academic,
                                          TeacherAcademicGrpcClient teacherAcademic,
                                          DocumentRendererGrpcClient renderer,
                                          BiFunction<Long, List<Long>, TeacherJournalResponse> journalReader) {
        this.reportService = reportService;
        this.schedule = schedule;
        this.academic = academic;
        this.teacherAcademic = teacherAcademic;
        this.renderer = renderer;
        this.journalReader = journalReader;
    }

    public TeacherAttendanceExportResponse export(TeacherAttendanceExportRequest request, long teacherId) {
        ExportFormat format = ExportFormat.from(request.getFormat());
        if (request.getReportKind() != TeacherAttendanceReportKind
                .TEACHER_ATTENDANCE_REPORT_KIND_SUBJECT_JOURNAL) {
            throw new BadRequestException("Only the subject journal export is available in this consumer");
        }
        long semesterId = positive(request.getSemesterId(), "semester_id");
        long groupId = positive(request.getGroupId(), "group_id");
        long subjectId = positive(request.getSubjectId(), "subject_id");
        List<String> types = normalizeTypes(request.getLessonTypesList());

        // This call establishes current active-group authority even when the
        // historical semester has no scheduled lessons for the selection.
        ReportService.TeacherStatsResult emptyScope = reportService.getTeacherStats(
                statsQuery(List.of(), semesterId, groupId, subjectId, types), teacherId);
        TeacherAssignmentsResponse semester = teacherAcademic.fullSemesterAssignments(semesterId);
        LocalDate semesterFrom = parseDate(semester.getSemesterDateFrom(), "semester_date_from");
        LocalDate semesterTo = parseDate(semester.getSemesterDateTo(), "semester_date_to");
        if (semesterTo.isBefore(semesterFrom)) {
            throw new AcademicServiceUnavailableException("Academic returned an invalid semester date range");
        }
        DateRange period = requestedPeriod(request.getDateFrom(), request.getDateTo(), semesterFrom, semesterTo);

        GroupResponse group = academic.getGroup(groupId);
        if (group == null || group.getId() != groupId || group.getName().isBlank()) {
            throw new AcademicServiceUnavailableException("Academic returned incomplete group metadata");
        }
        AcademicGrpcClient.SubjectDetails subject = academic.getSubjectDetailsByIds(List.of(subjectId))
                .get(subjectId);
        if (subject == null || subject.name() == null || subject.name().isBlank()) {
            throw new AcademicServiceUnavailableException("Academic returned incomplete subject metadata");
        }

        List<LessonResponse> lessons = new ArrayList<>();
        for (LessonResponse lesson : schedule.getLessonsByGroup(
                groupId, semesterId, period.from().toString(), period.to().toString()).getLessonsList()) {
            if (lesson.getGroupId() != groupId || lesson.getSemesterId() != semesterId
                    || lesson.getSubjectId() != subjectId
                    || types.stream().noneMatch(type -> type.equalsIgnoreCase(lesson.getLessonType()))) {
                continue;
            }
            if (lesson.getId() <= 0) {
                throw new AcademicServiceUnavailableException("Schedule returned an invalid lesson identity");
            }
            LocalDate lessonDate = parseDate(lesson.getDate(), "lesson.date");
            if (!lessonDate.isBefore(period.from()) && !lessonDate.isAfter(period.to())) lessons.add(lesson);
        }
        lessons.sort(Comparator.comparing((LessonResponse value) -> parseDate(value.getDate(), "lesson.date"))
                .thenComparing(value -> parseTime(value.getStartTime()),
                        Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparingInt(LessonResponse::getLessonNumber)
                .thenComparingLong(LessonResponse::getId));
        if (lessons.stream().map(LessonResponse::getId).distinct().count() != lessons.size()) {
            throw new AcademicServiceUnavailableException("Schedule returned duplicate lesson identities");
        }
        if (lessons.size() > MAX_EXPORT_LESSONS) {
            throw Status.RESOURCE_EXHAUSTED
                    .withDescription("Teacher journal exceeds the supported export size")
                    .asRuntimeException();
        }

        List<Long> lessonIds = lessons.stream().map(LessonResponse::getId).toList();
        ReportService.TeacherStatsResult stats = lessonIds.isEmpty() ? emptyScope
                : reportService.getTeacherStats(statsQuery(lessonIds, semesterId, groupId, subjectId, types), teacherId);
        TeacherAttendanceExportModel model = buildModel(lessons, types, group, subject, subjectId,
                semesterId, period.from(), period.to(), stats, teacherId);
        byte[] content = format.render(model, docxRenderer, renderer);
        if (content == null || content.length == 0) {
            throw new ReportExportUnavailableException("Teacher attendance export returned no content");
        }
        if (content.length > MAX_EXPORT_RESPONSE_BYTES) {
            throw Status.RESOURCE_EXHAUSTED
                    .withDescription("Teacher attendance export exceeds the 20 MiB response limit")
                    .asRuntimeException();
        }
        String extension = format == ExportFormat.PNG ? "zip" : format.code();
        String filename = "teacher-journal-g" + groupId + "-s" + subjectId + "-"
                + SAFE_DATE.format(period.from()) + "-" + SAFE_DATE.format(period.to()) + "." + extension;
        return TeacherAttendanceExportResponse.newBuilder()
                .setContent(com.google.protobuf.ByteString.copyFrom(content))
                .setFileName(filename)
                .setContentType(format.contentType())
                .build();
    }

    private TeacherAttendanceExportModel buildModel(List<LessonResponse> lessons,
                                                    List<String> types,
                                                    GroupResponse group,
                                                    AcademicGrpcClient.SubjectDetails subject,
                                                    long subjectId,
                                                    long semesterId,
                                                    LocalDate semesterFrom,
                                                    LocalDate periodTo,
                                                    ReportService.TeacherStatsResult stats,
                                                    long teacherId) {
        List<TeacherAttendanceExportModel.Column> columns = lessons.stream()
                .map(lesson -> new TeacherAttendanceExportModel.Column(
                        lesson.getId(), parseDate(lesson.getDate(), "lesson.date"),
                        parseTime(lesson.getStartTime()), lesson.getLessonNumber(),
                        lesson.getSubjectId(), subject.name(), lesson.getLessonType(),
                        typeLabel(lesson.getLessonType()), normalizedState(lesson.getStatus())))
                .toList();
        Set<Long> expectedLessonIds = lessons.stream().map(LessonResponse::getId)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        Instant generatedAt = stats.serverNow();
        if (generatedAt == null) {
            throw new ReportExportUnavailableException("Attendance returned no export snapshot time");
        }
        Set<Long> journalableLessonIds = lessons.stream()
                .filter(lesson -> JournalLessonPolicy.requireTiming(lesson).hasStarted(generatedAt))
                .map(LessonResponse::getId)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        Map<Long, MutableRow> rows = new LinkedHashMap<>();
        for (String type : types) {
            List<Long> ids = lessons.stream()
                    .filter(lesson -> type.equalsIgnoreCase(lesson.getLessonType()))
                    .map(LessonResponse::getId)
                    .toList();
            for (int offset = 0; offset < ids.size(); offset += JOURNAL_BATCH_SIZE) {
                List<Long> batch = ids.subList(offset, Math.min(ids.size(), offset + JOURNAL_BATCH_SIZE));
                TeacherJournalResponse journal = journalReader.apply(teacherId, batch);
                for (TeacherJournalStudent student : journal.getStudentsList()) {
                    MutableRow row = rows.computeIfAbsent(student.getStudentId(),
                            ignored -> new MutableRow(student.getStudentId(), student.getDisplayName()));
                    for (TeacherJournalCell cell : student.getCellsList()) {
                        if (!expectedLessonIds.contains(cell.getLessonId())) {
                            throw new ReportExportUnavailableException("Journal returned an unexpected lesson cell");
                        }
                        if (!journalableLessonIds.contains(cell.getLessonId())) continue;
                        if (row.cells.containsKey(cell.getLessonId())) {
                            throw new ReportExportUnavailableException("Journal returned a duplicate lesson cell");
                        }
                        String symbol = value(cell.getSymbol());
                        if (!cell.getRecordPresent() && symbol.isBlank()) symbol = "·";
                        row.cells.put(cell.getLessonId(), new TeacherAttendanceExportModel.Cell(
                                symbol, parseStatus(cell.getStatus()), cell.getRecordPresent()));
                    }
                }
            }
        }

        Map<Long, TeacherAttendanceExportModel.Metrics> metricsByStudent = new HashMap<>();
        for (ReportService.TeacherStudentStats student : stats.students()) {
            TeacherAttendanceExportModel.Metrics metrics = metrics(student);
            metricsByStudent.put(student.studentId(), metrics);
            rows.computeIfAbsent(student.studentId(), ignored -> new MutableRow(student.studentId(), student.displayName()));
            if (metrics.denominator() > 0 && rows.get(student.studentId()).cells.isEmpty()) {
                throw new ReportExportUnavailableException("Attendance metrics have no matching journal cells");
            }
        }

        List<TeacherAttendanceExportModel.Row> resultRows = rows.values().stream()
                .sorted(Comparator.comparing((MutableRow row) -> row.displayName,
                                Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))
                        .thenComparingLong(row -> row.studentId))
                .map(row -> {
                    Map<Long, TeacherAttendanceExportModel.Cell> orderedCells = new LinkedHashMap<>();
                    for (TeacherAttendanceExportModel.Column column : columns) {
                        TeacherAttendanceExportModel.Cell cell = row.cells.get(column.lessonId());
                        if (cell != null) orderedCells.put(column.lessonId(), cell);
                    }
                    return new TeacherAttendanceExportModel.Row(row.studentId, row.displayName, orderedCells,
                            java.util.Optional.of(metricsByStudent.getOrDefault(row.studentId, emptyMetrics())));
                })
                .toList();
        List<String> typeLabels = types.stream().map(TeacherAttendanceExportService::typeLabel).toList();
        return new TeacherAttendanceExportModel(
                new TeacherAttendanceExportModel.Context(
                        TeacherAttendanceExportModel.ReportKind.SUBJECT_JOURNAL,
                        semesterId, "Семестр " + semesterId, group.getId(), group.getName(),
                        subjectId, subject.name(),
                        semesterFrom, periodTo, typeLabels, generatedAt),
                columns,
                resultRows);
    }

    private static ReportService.TeacherStatsQuery statsQuery(List<Long> lessonIds,
                                                               long semesterId,
                                                               long groupId,
                                                               long subjectId,
                                                               List<String> types) {
        return new ReportService.TeacherStatsQuery(lessonIds, ReportService.TeacherStatsScope.STUDENTS,
                groupId, subjectId, types, List.of(), List.of(), semesterId);
    }

    private static TeacherAttendanceExportModel.Metrics metrics(ReportService.TeacherStudentStats student) {
        ReportService.TeacherMetric present = student.present();
        ReportService.TeacherMetric presentOrExcused = student.presentOrExcused();
        ReportService.TeacherMetric excused = student.excused();
        ReportService.TeacherMetric absent = student.absent();
        if (present.denominator() != presentOrExcused.denominator()
                || present.denominator() != excused.denominator()
                || present.denominator() != absent.denominator()) {
            throw new ReportExportUnavailableException("Attendance metrics use inconsistent denominators");
        }
        return new TeacherAttendanceExportModel.Metrics(
                present.numerator(), excused.numerator(), absent.numerator(),
                presentOrExcused.numerator(), present.denominator(),
                BigDecimal.valueOf(present.percent()), BigDecimal.valueOf(presentOrExcused.percent()),
                BigDecimal.valueOf(excused.percent()), BigDecimal.valueOf(absent.percent()));
    }

    private static TeacherAttendanceExportModel.Metrics emptyMetrics() {
        BigDecimal zero = BigDecimal.ZERO;
        return new TeacherAttendanceExportModel.Metrics(0, 0, 0, 0, 0, zero, zero, zero, zero);
    }

    private static List<String> normalizeTypes(List<String> values) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        if (values != null) {
            for (String value : values) {
                if (value == null || value.isBlank() || value.length() > 64) {
                    throw new BadRequestException("lesson_types must contain 1..3 non-empty values");
                }
                result.add(value.trim().toLowerCase(Locale.ROOT));
            }
        }
        if (result.isEmpty() || result.size() > 3 || values.size() != result.size()) {
            throw new BadRequestException("lesson_types must contain 1..3 unique values");
        }
        return List.copyOf(result);
    }

    private static long positive(long value, String field) {
        if (value <= 0) throw new BadRequestException(field + " must be positive");
        return value;
    }

    private static LocalDate parseDate(String value, String field) {
        try {
            return LocalDate.parse(value);
        } catch (RuntimeException error) {
            throw new AcademicServiceUnavailableException("Academic or Schedule returned invalid " + field);
        }
    }

    private static DateRange requestedPeriod(String fromValue, String toValue,
                                             LocalDate semesterFrom, LocalDate semesterTo) {
        boolean hasFrom = fromValue != null && !fromValue.isBlank();
        boolean hasTo = toValue != null && !toValue.isBlank();
        if (!hasFrom && !hasTo) return new DateRange(semesterFrom, semesterTo);
        if (hasFrom != hasTo) throw new BadRequestException("date_from and date_to must be provided together");
        LocalDate from = requestedDate(fromValue, "date_from");
        LocalDate to = requestedDate(toValue, "date_to");
        if (to.isBefore(from) || from.isBefore(semesterFrom) || to.isAfter(semesterTo)) {
            throw new BadRequestException("The selected period must be inside the semester");
        }
        return new DateRange(from, to);
    }

    private static LocalDate requestedDate(String value, String field) {
        try {
            return LocalDate.parse(value);
        } catch (RuntimeException error) {
            throw new BadRequestException(field + " must be an ISO-8601 date");
        }
    }

    private record DateRange(LocalDate from, LocalDate to) {
    }

    private static LocalTime parseTime(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return LocalTime.parse(value);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static AttendanceStatus parseStatus(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return AttendanceStatus.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException error) {
            throw new ReportExportUnavailableException("Attendance returned an unknown journal status");
        }
    }

    private static String normalizedState(String value) {
        if (value == null || value.isBlank()) return "UNKNOWN";
        return switch (value.trim().toUpperCase(Locale.ROOT)) {
            case "PLANNED", "ACTIVE", "CLOSED", "CANCELLED" -> value.trim().toUpperCase(Locale.ROOT);
            default -> "UNKNOWN";
        };
    }

    private static String stateLabel(String value) {
        if (value == null) return "";
        return switch (value.trim().toUpperCase(Locale.ROOT)) {
            case "PLANNED" -> "Запланировано";
            case "ACTIVE" -> "Идёт";
            case "CLOSED" -> "Завершено";
            case "CANCELLED" -> "Отменено";
            default -> "";
        };
    }

    private static String typeLabel(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        return TYPE_LABELS.getOrDefault(normalized, value == null ? "" : value.trim());
    }

    private static String value(String value) {
        return value == null ? "" : value;
    }

    private static String safeCellValue(TeacherAttendanceExportModel.Cell cell) {
        if (cell == null) return "";
        if (!cell.symbol().isBlank()) return cell.symbol();
        return "·";
    }

    private static String metricValue(int numerator, int denominator, BigDecimal percent) {
        return denominator == 0 ? "—" : numerator + "/" + denominator + " ("
                + percent.stripTrailingZeros().toPlainString() + "%)";
    }

    private static byte[] html(TeacherAttendanceExportModel model) {
        StringBuilder out = new StringBuilder(16_384);
        out.append("<!doctype html><html lang=\"ru\"><head><meta charset=\"UTF-8\"><meta name=\"viewport\" content=\"width=device-width,initial-scale=1\"><title>")
                .append(escapeHtml(model.context().groupLabel())).append(" — журнал</title>")
                .append("<style>body{font:14px/1.45 system-ui,sans-serif;color:#202124;margin:24px}h1{font-size:22px;margin:0 0 12px}p{margin:6px 0}.table-wrap{max-width:100%;overflow:auto;margin-top:18px;border:1px solid #d6dbe1;border-radius:8px}table{border-collapse:collapse;min-width:100%;width:max-content}th,td{border:1px solid #d6dbe1;padding:7px 9px;text-align:center;vertical-align:middle}thead th{position:sticky;top:0;background:#f2f5f8;z-index:2;font-weight:600;white-space:normal;min-width:84px;max-width:150px;overflow-wrap:anywhere}thead th:first-child{left:0;z-index:4;min-width:220px}tbody th{position:sticky;left:0;background:#fff;text-align:left;z-index:1;min-width:220px}tbody td{white-space:nowrap}tbody tr:nth-child(even){background:#f8fafc}footer{margin-top:16px;color:#5f6368;font-size:12px}</style></head><body>")
                .append("<h1>Журнал посещаемости</h1><p>")
                .append(escapeHtml(model.context().groupLabel())).append(" · ")
                .append(escapeHtml(model.context().subjectLabel())).append("</p><p>")
                .append(model.context().periodFrom()).append(" — ").append(model.context().periodTo())
                .append(" · ").append(escapeHtml(String.join(", ", model.context().typeLabels())))
                .append("</p><div class=\"table-wrap\" role=\"region\" aria-label=\"Полный журнал, прокручивайте по горизонтали\" tabindex=\"0\"><table><thead><tr><th scope=\"col\">Студент</th>");
        for (TeacherAttendanceExportModel.Column column : model.columns()) {
            out.append("<th scope=\"col\">")
                    .append(column.date()).append(" · ")
                    .append(column.startTime() == null ? "" : column.startTime() + " · ")
                    .append(escapeHtml(column.typeLabel())).append(" · №")
                    .append(column.lessonNumber());
            String stateLabel = stateLabel(column.state());
            if (!stateLabel.isBlank()) out.append(" · ").append(escapeHtml(stateLabel));
            out.append("</th>");
        }
        out.append("<th scope=\"col\">Присутствовал</th><th scope=\"col\">Присутствовал или уважительно</th>")
                .append("<th scope=\"col\">Уважительно</th><th scope=\"col\">Отсутствовал</th></tr></thead><tbody>");
        for (TeacherAttendanceExportModel.Row row : model.rows()) {
            out.append("<tr><th scope=\"row\">").append(escapeHtml(row.displayName())).append("</th>");
            for (TeacherAttendanceExportModel.Column column : model.columns()) {
                out.append("<td>").append(escapeHtml(safeCellValue(row.cellsByLessonId().get(column.lessonId())))).append("</td>");
            }
            TeacherAttendanceExportModel.Metrics metrics = row.metrics().orElseThrow();
            out.append("<td>").append(metricValue(metrics.presentCount(), metrics.denominator(), metrics.percentPresent()))
                    .append("</td><td>").append(metricValue(metrics.presentOrExcusedCount(), metrics.denominator(), metrics.percentPresentOrExcused()))
                    .append("</td><td>").append(metricValue(metrics.excusedCount(), metrics.denominator(), metrics.percentExcused()))
                    .append("</td><td>").append(metricValue(metrics.absentCount(), metrics.denominator(), metrics.percentAbsent()))
                    .append("</td></tr>");
        }
        out.append("</tbody></table></div><footer>Сформировано ").append(model.context().generatedAt()).append("</footer></body></html>");
        return out.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] xlsx(TeacherAttendanceExportModel model) {
        if (model.columns().size() + 10 > EXCEL_MAX_COLUMNS) {
            throw Status.RESOURCE_EXHAUSTED
                    .withDescription("Teacher journal exceeds the Excel column limit")
                    .asRuntimeException();
        }
        StringBuilder sheet = new StringBuilder(16_384);
        sheet.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>")
                .append("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><sheetViews><sheetView workbookViewId=\"0\"><pane xSplit=\"1\" ySplit=\"6\" topLeftCell=\"B7\" activePane=\"bottomRight\" state=\"frozen\"/><selection pane=\"topRight\" activeCell=\"B1\" sqref=\"B1\"/><selection pane=\"bottomLeft\" activeCell=\"A7\" sqref=\"A7\"/><selection pane=\"bottomRight\" activeCell=\"B7\" sqref=\"B7\"/></sheetView></sheetViews><cols>")
                .append("<col min=\"1\" max=\"1\" width=\"30\" customWidth=\"1\"/>");
        for (int index = 0; index < model.columns().size(); index++) {
            int columnIndex = index + 2;
            sheet.append("<col min=\"").append(columnIndex).append("\" max=\"")
                    .append(columnIndex).append("\" width=\"14\" customWidth=\"1\"/>");
        }
        for (int index = 0; index < 9; index++) {
            int columnIndex = model.columns().size() + index + 2;
            sheet.append("<col min=\"").append(columnIndex).append("\" max=\"")
                    .append(columnIndex).append("\" width=\"18\" customWidth=\"1\"/>");
        }
        sheet.append("</cols><sheetData>");
        appendSheetRow(sheet, 1, List.of("Журнал посещаемости", model.context().groupLabel()));
        appendSheetRow(sheet, 2, List.of("Предмет", model.context().subjectLabel()));
        appendSheetRow(sheet, 3, List.of("Период", model.context().periodFrom() + " — " + model.context().periodTo()));
        appendSheetRow(sheet, 4, List.of("Типы занятий", String.join(", ", model.context().typeLabels())));
        appendSheetRow(sheet, 6, headers(model), true);
        int rowIndex = 7;
        for (TeacherAttendanceExportModel.Row row : model.rows()) {
            List<SheetValue> values = new ArrayList<>(model.columns().size() + 10);
            values.add(SheetValue.text(row.displayName()));
            for (TeacherAttendanceExportModel.Column column : model.columns()) {
                values.add(SheetValue.text(safeCellValue(row.cellsByLessonId().get(column.lessonId()))));
            }
            TeacherAttendanceExportModel.Metrics metrics = row.metrics().orElseThrow();
            values.add(SheetValue.number(metrics.denominator()));
            addMetricValues(values, metrics.presentCount(), metrics.percentPresent(), metrics.denominator());
            addMetricValues(values, metrics.presentOrExcusedCount(), metrics.percentPresentOrExcused(), metrics.denominator());
            addMetricValues(values, metrics.excusedCount(), metrics.percentExcused(), metrics.denominator());
            addMetricValues(values, metrics.absentCount(), metrics.percentAbsent(), metrics.denominator());
            appendSheetValuesRow(sheet, rowIndex++, values, false);
        }
        sheet.append("</sheetData></worksheet>");

        Map<String, String> entries = new LinkedHashMap<>();
        entries.put("[Content_Types].xml", "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">"
                + "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>"
                + "<Default Extension=\"xml\" ContentType=\"application/xml\"/>"
                + "<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>"
                + "<Override PartName=\"/xl/worksheets/sheet1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>"
                + "<Override PartName=\"/xl/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml\"/>"
                + "</Types>");
        entries.put("_rels/.rels", "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
                + "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/>"
                + "</Relationships>");
        entries.put("xl/workbook.xml", "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\"><sheets>"
                + "<sheet name=\"Журнал\" sheetId=\"1\" r:id=\"rId1\"/></sheets></workbook>");
        entries.put("xl/_rels/workbook.xml.rels", "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
                + "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet1.xml\"/>"
                + "<Relationship Id=\"rId2\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles\" Target=\"styles.xml\"/>"
                + "</Relationships>");
        entries.put("xl/worksheets/sheet1.xml", sheet.toString());
        entries.put("xl/styles.xml", "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<styleSheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">"
                + "<fonts count=\"1\"><font><sz val=\"11\"/><name val=\"Calibri\"/></font></fonts>"
                + "<fills count=\"2\"><fill><patternFill patternType=\"none\"/></fill><fill><patternFill patternType=\"gray125\"/></fill></fills>"
                + "<borders count=\"1\"><border><left/><right/><top/><bottom/><diagonal/></border></borders>"
                + "<cellStyleXfs count=\"1\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\"/></cellStyleXfs>"
                + "<cellXfs count=\"3\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\"/>"
                + "<xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyAlignment=\"1\"><alignment horizontal=\"center\" vertical=\"center\" wrapText=\"1\"/></xf>"
                + "<xf numFmtId=\"10\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\"/></cellXfs>"
                + "<cellStyles count=\"1\"><cellStyle name=\"Normal\" xfId=\"0\" builtinId=\"0\"/></cellStyles></styleSheet>");
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
             ZipOutputStream zip = new ZipOutputStream(bytes, StandardCharsets.UTF_8)) {
            for (Map.Entry<String, String> entry : entries.entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey()));
                zip.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
            zip.finish();
            return bytes.toByteArray();
        } catch (IOException error) {
            throw new ReportExportUnavailableException("Could not package teacher XLSX");
        }
    }

    private static List<String> headers(TeacherAttendanceExportModel model) {
        List<String> result = new ArrayList<>(model.columns().size() + 10);
        result.add("Студент");
        for (TeacherAttendanceExportModel.Column column : model.columns()) {
            result.add(column.date() + "\n" + typeLabel(column.lessonType()) + "\n№" + column.lessonNumber()
                    + (column.startTime() == null ? "" : "\n" + column.startTime())
                    + (stateLabel(column.state()).isBlank() ? "" : "\n" + stateLabel(column.state())));
        }
        result.addAll(List.of("Знаменатель", "Присутствовал — количество", "Присутствовал — %",
                "Присутствовал или уважительно — количество", "Присутствовал или уважительно — %",
                "Уважительно — количество", "Уважительно — %", "Отсутствовал — количество", "Отсутствовал — %"));
        return result;
    }

    private static void appendSheetRow(StringBuilder sheet, int rowIndex, List<String> values) {
        appendSheetValuesRow(sheet, rowIndex, values.stream().map(SheetValue::text).toList(), false);
    }

    private static void appendSheetRow(StringBuilder sheet, int rowIndex, List<String> values, boolean wrapped) {
        appendSheetValuesRow(sheet, rowIndex, values.stream().map(SheetValue::text).toList(), wrapped);
    }

    private static void appendSheetValuesRow(StringBuilder sheet,
                                             int rowIndex,
                                             List<SheetValue> values,
                                             boolean wrapped) {
        sheet.append("<row r=\"").append(rowIndex).append("\"");
        if (wrapped) sheet.append(" ht=\"68\" customHeight=\"1\"");
        sheet.append(">");
        for (int index = 0; index < values.size(); index++) {
            String reference = cellReference(index + 1, rowIndex);
            SheetValue value = values.get(index);
            sheet.append("<c r=\"").append(reference).append("\"");
            if (wrapped) sheet.append(" s=\"1\"");
            else if (value.style() != 0) sheet.append(" s=\"").append(value.style()).append("\"");
            if (value.number() != null) {
                sheet.append(" t=\"n\"><v>").append(value.number()).append("</v></c>");
            } else {
                sheet.append(" t=\"inlineStr\"><is><t xml:space=\"preserve\">")
                        .append(escapeXml(value.text())).append("</t></is></c>");
            }
        }
        sheet.append("</row>");
    }

    private static void addMetricValues(List<SheetValue> values, int count, BigDecimal percent, int denominator) {
        values.add(SheetValue.number(count));
        values.add(denominator == 0 ? SheetValue.text("") : SheetValue.percentage(percent));
    }

    private static String cellReference(int columnNumber, int rowNumber) {
        StringBuilder column = new StringBuilder();
        int current = columnNumber;
        while (current > 0) {
            int remainder = (current - 1) % 26;
            column.insert(0, (char) ('A' + remainder));
            current = (current - 1) / 26;
        }
        return column.append(rowNumber).toString();
    }

    private static String escapeHtml(String value) {
        return value == null ? "" : value.replace("&", "&amp;").replace("<", "&lt;")
                .replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;");
    }

    private static String escapeXml(String value) {
        StringBuilder result = new StringBuilder(value == null ? 0 : value.length());
        if (value != null) {
            value.codePoints().filter(codePoint -> codePoint == 0x9 || codePoint == 0xA || codePoint == 0xD
                            || codePoint >= 0x20 && codePoint <= 0xD7FF
                            || codePoint >= 0xE000 && codePoint <= 0xFFFD
                            || codePoint >= 0x10000 && codePoint <= 0x10FFFF)
                    .forEach(codePoint -> {
                        switch (codePoint) {
                            case '&' -> result.append("&amp;");
                            case '<' -> result.append("&lt;");
                            case '>' -> result.append("&gt;");
                            case '\"' -> result.append("&quot;");
                            case '\'' -> result.append("&apos;");
                            default -> result.appendCodePoint(codePoint);
                        }
                    });
        }
        return result.toString();
    }

    private static String normalizeFormat(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static final class MutableRow {
        private final long studentId;
        private final String displayName;
        private final Map<Long, TeacherAttendanceExportModel.Cell> cells = new HashMap<>();

        private MutableRow(long studentId, String displayName) {
            this.studentId = studentId;
            this.displayName = displayName == null ? "" : displayName;
        }
    }

    private record SheetValue(String text, String number, int style) {
        private static SheetValue text(String value) {
            return new SheetValue(value == null ? "" : value, null, 0);
        }

        private static SheetValue number(int value) {
            return new SheetValue("", Integer.toString(value), 0);
        }

        private static SheetValue percentage(BigDecimal value) {
            return new SheetValue("", value.movePointLeft(2).stripTrailingZeros().toPlainString(), 2);
        }
    }

    private enum ExportFormat {
        DOCX("docx", DOCX_MIME),
        PDF("pdf", "application/pdf"),
        PNG("png", "application/zip"),
        HTML("html", HTML_MIME),
        XLSX("xlsx", XLSX_MIME);

        private final String code;
        private final String contentType;

        ExportFormat(String code, String contentType) {
            this.code = code;
            this.contentType = contentType;
        }

        private static ExportFormat from(String value) {
            String normalized = normalizeFormat(value);
            for (ExportFormat format : values()) if (format.code.equals(normalized)) return format;
            throw new BadRequestException("Unknown teacher attendance export format");
        }

        private String code() { return code; }
        private String contentType() { return contentType; }

        private byte[] render(TeacherAttendanceExportModel model,
                              TeacherAttendanceDocxRenderer docxRenderer,
                              DocumentRendererGrpcClient renderer) {
            return switch (this) {
                case DOCX -> docxRenderer.render(model);
                case PDF -> renderer.convertDocxForTeacherExport(docxRenderer.render(model), TargetFormat.PDF);
                case PNG -> renderer.convertDocxForTeacherExport(
                        docxRenderer.render(model), TargetFormat.PNG_PAGES_ZIP);
                case HTML -> html(model);
                case XLSX -> xlsx(model);
            };
        }
    }
}
