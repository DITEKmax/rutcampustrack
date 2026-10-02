package ru.rutcampustrack.attendance.report;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import ru.rutcampustrack.academic.grpc.GroupMembersResponse;
import ru.rutcampustrack.academic.grpc.GroupResponse;
import ru.rutcampustrack.academic.grpc.SemesterResponse;
import ru.rutcampustrack.academic.grpc.StudentInfo;
import ru.rutcampustrack.attendance.contract.dto.report.HeadmanStatsExportRequest;
import ru.rutcampustrack.attendance.contract.dto.report.HeadmanStatsFilter;
import ru.rutcampustrack.attendance.contract.dto.report.HeadmanStatsQueryRequest;
import ru.rutcampustrack.attendance.contract.dto.report.HeadmanStatsResponse;
import ru.rutcampustrack.attendance.contract.dto.report.HeadmanStatsResponse.ColumnDescriptor;
import ru.rutcampustrack.attendance.contract.dto.report.HeadmanStatsResponse.Context;
import ru.rutcampustrack.attendance.contract.dto.report.HeadmanStatsResponse.EmptyState;
import ru.rutcampustrack.attendance.contract.dto.report.HeadmanStatsResponse.FilterKind;
import ru.rutcampustrack.attendance.contract.dto.report.HeadmanStatsResponse.FormatOption;
import ru.rutcampustrack.attendance.contract.dto.report.HeadmanStatsResponse.LessonTypeOption;
import ru.rutcampustrack.attendance.contract.dto.report.HeadmanStatsResponse.Metric;
import ru.rutcampustrack.attendance.contract.dto.report.HeadmanStatsResponse.Metrics;
import ru.rutcampustrack.attendance.contract.dto.report.HeadmanStatsResponse.StudentRow;
import ru.rutcampustrack.attendance.contract.dto.report.HeadmanStatsResponse.SubjectOption;
import ru.rutcampustrack.attendance.contract.dto.report.HeadmanStatsResponse.TicketCounts;
import ru.rutcampustrack.attendance.contract.dto.report.HeadmanStatsResponse.Sources;
import ru.rutcampustrack.attendance.contract.dto.report.HeadmanStatsSort;
import ru.rutcampustrack.attendance.contract.dto.report.HeadmanStatsStudentDetailResponse;
import ru.rutcampustrack.attendance.contract.dto.report.HeadmanStatsStudentDetailResponse.ExcuseTicketEntry;
import ru.rutcampustrack.attendance.contract.dto.report.HeadmanStatsStudentDetailResponse.LateCheckinTicket;
import ru.rutcampustrack.attendance.contract.dto.report.HeadmanStatsStudentDetailResponse.SubjectMetrics;
import ru.rutcampustrack.attendance.contract.dto.report.HeadmanStatsStudentDetailResponse.TicketLesson;
import ru.rutcampustrack.attendance.contract.dto.report.HeadmanStatsStudentDetailResponse.TicketPage;
import ru.rutcampustrack.attendance.contract.dto.report.HeadmanStatsStudentDetailResponse.WeekMetrics;
import ru.rutcampustrack.attendance.contract.dto.report.HeadmanStatsTrendQueryRequest;
import ru.rutcampustrack.attendance.contract.dto.report.HeadmanStatsTrendResponse;
import ru.rutcampustrack.attendance.contract.dto.report.HeadmanStatsTrendResponse.Point;
import ru.rutcampustrack.attendance.contract.dto.report.HeadmanStatsTrendResponse.TrendMetric;
import ru.rutcampustrack.attendance.contract.enums.AttendanceSource;
import ru.rutcampustrack.attendance.contract.enums.AttendanceStatus;
import ru.rutcampustrack.attendance.contract.enums.ExcuseTicketStatus;
import ru.rutcampustrack.attendance.contract.enums.LateCheckinRequestOrigin;
import ru.rutcampustrack.attendance.contract.enums.LateCheckinRequestStatus;
import ru.rutcampustrack.attendance.contract.exception.ResourceNotFoundException;
import ru.rutcampustrack.attendance.exception.AccessDeniedException;
import ru.rutcampustrack.attendance.exception.AcademicServiceUnavailableException;
import ru.rutcampustrack.attendance.exception.BadRequestException;
import ru.rutcampustrack.attendance.exception.ReportExportTooLargeException;
import ru.rutcampustrack.attendance.exception.ReportExportUnavailableException;
import ru.rutcampustrack.attendance.exception.ScheduleServiceUnavailableException;
import ru.rutcampustrack.attendance.excuse.ExcuseRepository;
import ru.rutcampustrack.attendance.excuse.entity.ExcuseTicket;
import ru.rutcampustrack.attendance.grpc.AcademicGrpcClient;
import ru.rutcampustrack.attendance.grpc.DocumentRendererGrpcClient;
import ru.rutcampustrack.attendance.grpc.ScheduleGrpcClient;
import ru.rutcampustrack.attendance.journal.JournalLessonPolicy;
import ru.rutcampustrack.attendance.latecheckin.LateCheckinRepository;
import ru.rutcampustrack.attendance.latecheckin.entity.LateCheckinRequest;
import ru.rutcampustrack.attendance.security.RequestContext;
import ru.rutcampustrack.attendance.shared.port.AttendanceReadPort;
import ru.rutcampustrack.attendance.shared.port.AttendanceRecord;
import ru.rutcampustrack.attendance.studentrequest.entity.StudentLessonSnapshotDocument;
import ru.rutcampustrack.documentrenderer.grpc.TargetFormat;
import ru.rutcampustrack.schedule.grpc.LessonResponse;
import ru.rutcampustrack.schedule.grpc.LessonsResponse;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
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
import java.util.Set;
import java.util.TreeMap;

/** Server-owned semester stats for the authenticated headman's current group. */
@Service
@RequiredArgsConstructor
public class HeadmanStatsService {
    private static final int MAX_SEMESTER_LESSONS = 5_000;
    private static final int MAX_PAGE_SIZE = 100;
    private static final int MAX_TICKET_PAGE_SIZE = 100;
    private static final int MAX_EXPORT_STUDENTS = 5_000;
    private static final int MAX_EXPORT_RESPONSE_BYTES = 20 * 1024 * 1024;
    private static final int MAX_RENDERER_INPUT_BYTES = 4 * 1024 * 1024 - 1024;
    private static final List<ColumnDescriptor> COLUMNS = List.of(
            column("displayName", "ФИО", FilterKind.TEXT),
            column("presentCount", "Количество «+»", FilterKind.RANGE),
            column("presentPercent", "Процент «+»", FilterKind.RANGE),
            column("presentOrExcusedCount", "Количество «+ и у»", FilterKind.RANGE),
            column("presentOrExcusedPercent", "Процент «+ и у»", FilterKind.RANGE),
            column("excusedCount", "Количество «у»", FilterKind.RANGE),
            column("excusedPercent", "Процент «у»", FilterKind.RANGE),
            column("absentCount", "Количество «н»", FilterKind.RANGE),
            column("absentPercent", "Процент «н»", FilterKind.RANGE),
            column("lateSubmitted", "Заявки на «н»: подано", FilterKind.RANGE),
            column("lateApproved", "Заявки на «н»: одобрено", FilterKind.RANGE),
            column("lateRejected", "Заявки на «н»: отклонено", FilterKind.RANGE),
            column("excuseSubmitted", "Заявки на «у»: подано", FilterKind.RANGE),
            column("excuseApproved", "Заявки на «у»: одобрено", FilterKind.RANGE),
            column("excuseRejected", "Заявки на «у»: отклонено", FilterKind.RANGE),
            column("sourceStudentGeo", "«+»: геолокация", FilterKind.RANGE),
            column("sourceManualRequest", "«+»: ручная заявка старосте", FilterKind.RANGE),
            column("sourceAutoGeoFailure", "«+»: заявка после неудачного гео", FilterKind.RANGE),
            column("sourceHeadmanManual", "«+»: отметил староста", FilterKind.RANGE)
    );
    private static final Map<String, ColumnDescriptor> COLUMNS_BY_FIELD = COLUMNS.stream()
            .collect(java.util.stream.Collectors.toUnmodifiableMap(ColumnDescriptor::field, column -> column));

    private final AcademicGrpcClient academicGrpcClient;
    private final ScheduleGrpcClient scheduleGrpcClient;
    private final AttendanceReadPort attendanceReadPort;
    private final ExcuseRepository excuseRepository;
    private final LateCheckinRepository lateCheckinRepository;
    private final HeadmanStatsDocxRenderer docxRenderer;
    private final HeadmanStatsTabularRenderer tabularRenderer;
    private final DocumentRendererGrpcClient documentRendererGrpcClient;
    private final RequestContext requestContext;
    private final Clock clock;

    public HeadmanStatsResponse query(HeadmanStatsQueryRequest request) {
        long groupId = ensureHeadman();
        int page = requirePage(request);
        int size = requirePageSize(request);
        List<HeadmanStatsSort> sorts = validateSorts(request.sorts());
        List<HeadmanStatsFilter> filters = validateFilters(request.filters());
        List<String> requestedTypes = validateTypes(request.lessonTypes(), request.subjectId());
        StatsData data = calculate(groupId, request.subjectId(), requestedTypes, sorts, filters);
        int total = data.rows().size();
        int from = (int) Math.min((long) page * size, total);
        int to = Math.min(from + size, total);
        List<StudentRow> pageRows = data.rows().subList(from, to);
        boolean previous = page > 0 && total > 0;
        boolean next = (long) (page + 1) * size < total;
        return new HeadmanStatsResponse(data.context(), data.summary(), total, pageRows, data.subjects(),
                COLUMNS, HeadmanStatsFormat.catalogue(), page, size,
                total == 0 ? 0 : (int) Math.min(Integer.MAX_VALUE, ((long) total + size - 1) / size),
                total, previous, next, data.emptyState());
    }

    public HeadmanStatsExportResult export(HeadmanStatsExportRequest request) {
        long groupId = ensureHeadman();
        if (request == null) throw new BadRequestException("Не заданы параметры выгрузки статистики");
        HeadmanStatsFormat format = HeadmanStatsFormat.from(request.format());
        List<HeadmanStatsSort> sorts = validateSorts(request.sorts());
        List<HeadmanStatsFilter> filters = validateFilters(request.filters());
        List<String> requestedTypes = validateTypes(request.lessonTypes(), request.subjectId());
        StatsData data = calculate(groupId, request.subjectId(), requestedTypes, sorts, filters);
        if (data.context().semesterId() == null) {
            throw new BadRequestException("В активном семестре пока нет статистики для выгрузки");
        }
        if (data.rows().size() > MAX_EXPORT_STUDENTS) {
            throw new ReportExportTooLargeException("Выгрузка включает больше 5000 студентов. Уточни фильтры таблицы.");
        }
        HeadmanStatsExportModel model = new HeadmanStatsExportModel(
                data.context(), data.summary(), data.rows().size(), data.rows(),
                describeExportFilters(filters), describeExportSorts(sorts));
        byte[] content;
        switch (format) {
            case DOCX -> content = docxRenderer.render(model);
            case HTML -> content = tabularRenderer.renderHtml(model);
            case XLSX -> content = tabularRenderer.renderXlsx(model);
            case PDF, PNG -> {
                byte[] docx = docxRenderer.render(model);
                if (docx.length > MAX_RENDERER_INPUT_BYTES) {
                    throw new ReportExportTooLargeException(
                            "DOCX превышает лимит сервиса преобразования. Уточни фильтры таблицы или выбери HTML/XLSX.");
                }
                TargetFormat target = format == HeadmanStatsFormat.PDF
                        ? TargetFormat.PDF : TargetFormat.PNG_PAGES_ZIP;
                try {
                    content = documentRendererGrpcClient.convertDocxForHeadmanWeeklyExport(docx, target);
                } catch (StatusRuntimeException error) {
                    if (error.getStatus().getCode() == Status.Code.RESOURCE_EXHAUSTED) {
                        throw new ReportExportTooLargeException(
                                "DOCX превышает лимит сервиса преобразования. Уточни фильтры таблицы.");
                    }
                    throw error;
                }
            }
            default -> throw new IllegalStateException("Unsupported headman statistics format: " + format);
        }
        if (content == null || content.length == 0) {
            throw new ReportExportUnavailableException("Сервис формирования не вернул файл статистики");
        }
        if (content.length > MAX_EXPORT_RESPONSE_BYTES) {
            throw new ReportExportTooLargeException("Выгрузка превышает лимит ответа 20 МиБ. Уточни фильтры таблицы.");
        }
        String fileName = HeadmanStatsReportFiles.fileName(data.context().groupName(),
                data.context().semesterName(), data.context().subjectName(), format);
        return new HeadmanStatsExportResult(fileName, format.contentType(), content);
    }

    public HeadmanStatsTrendResponse trend(HeadmanStatsTrendQueryRequest request) {
        long groupId = ensureHeadman();
        if (request == null || request.mode() == null) {
            throw new BadRequestException("Укажи режим динамики статистики");
        }
        Long subjectId = request.subjectId();
        List<String> types = request.lessonTypes() == null ? List.of() : request.lessonTypes();
        switch (request.mode()) {
            case SEMESTER -> {
                if (request.weekStart() != null || subjectId != null || !types.isEmpty()) {
                    throw new BadRequestException("Для динамики за семестр не нужны фильтры недели или предмета");
                }
            }
            case WEEK -> {
                if (request.weekStart() == null || subjectId != null || !types.isEmpty()) {
                    throw new BadRequestException("Для динамики по дням укажи только начало недели");
                }
                if (request.weekStart().getDayOfWeek() != DayOfWeek.MONDAY) {
                    throw new BadRequestException("Неделя должна начинаться в понедельник");
                }
            }
            case SUBJECT -> {
                if (request.weekStart() != null || subjectId == null || subjectId <= 0) {
                    throw new BadRequestException("Для динамики по предмету выбери предмет");
                }
                types = validateTypes(types, subjectId);
            }
        }

        CalculationCapture capture = new CalculationCapture();
        calculate(groupId, subjectId, types, List.of(), List.of(), false, false, capture);
        if (capture.context.semesterId() == null) {
            return new HeadmanStatsTrendResponse(capture.context, request.mode(), List.of(),
                    HeadmanStatsTrendResponse.EmptyState.NO_ACTIVE_SEMESTER,
                    HeadmanStatsTrendFormat.catalogue());
        }
        if (request.mode() == HeadmanStatsTrendQueryRequest.Mode.WEEK) {
            LocalDate weekEnd = request.weekStart().plusDays(6);
            if (weekEnd.isBefore(capture.context.semesterFrom())
                    || request.weekStart().isAfter(capture.context.semesterTo())) {
                throw new BadRequestException("Выбранная неделя находится за границами семестра");
            }
        }
        List<TrendBucket> buckets = trendBuckets(capture.context, request.mode(), request.weekStart());
        Map<LocalDate, ReportService.StatsCounter> counters = new HashMap<>();
        for (TrendBucket bucket : buckets) counters.put(bucket.key(), new ReportService.StatsCounter());
        for (StatsLesson lesson : capture.selectedLessons) {
            LocalDate key = trendBucketKey(lesson.date(), request.mode());
            ReportService.StatsCounter counter = counters.get(key);
            if (counter == null) continue;
            for (long studentId : capture.membersByLesson.getOrDefault((long) lesson.lesson().getId(), Set.of())) {
                AttendanceRecord record = capture.attendanceByCell.get(
                        new AttendanceKey(lesson.lesson().getId(), studentId));
                counter.add(record == null || record.status() == null ? AttendanceStatus.ABSENT : record.status());
            }
        }
        boolean hasEligiblePairs = counters.values().stream()
                .anyMatch(counter -> counter.presentMetric().denominator() > 0);
        List<Point> points = buckets.stream().map(bucket -> {
            ReportService.StatsCounter counter = counters.get(bucket.key());
            return new Point(bucket.key().toString(), bucket.label(), bucket.from(), bucket.to(),
                    trendMetric(counter.presentMetric()), trendMetric(counter.presentOrExcusedMetric()));
        }).toList();
        return new HeadmanStatsTrendResponse(capture.context, request.mode(), points,
                hasEligiblePairs ? HeadmanStatsTrendResponse.EmptyState.NONE
                        : capture.completedSemesterLessons == 0
                        ? HeadmanStatsTrendResponse.EmptyState.NO_COMPLETED_LESSONS
                        : HeadmanStatsTrendResponse.EmptyState.NO_MATCHING_LESSONS,
                HeadmanStatsTrendFormat.catalogue());
    }

    public HeadmanStatsStudentDetailResponse studentDetail(Long studentId, int latePage, int excusePage, int size) {
        long groupId = ensureHeadman();
        if (studentId == null || studentId <= 0) {
            throw new BadRequestException("Укажи корректный ID студента");
        }
        requireTicketPage(latePage, size);
        requireTicketPage(excusePage, size);
        CalculationCapture capture = new CalculationCapture();
        calculate(groupId, null, List.of(), List.of(), List.of(), false, true, capture);
        boolean historicalMember = capture.historyMembersByLesson.values().stream()
                .anyMatch(memberIds -> memberIds.contains(studentId));
        if (!capture.currentStudentIds.contains(studentId) && !historicalMember) {
            throw new ResourceNotFoundException("Student", "id", studentId);
        }

        StudentAccumulator accumulator = capture.students.get(studentId);
        String displayName = accumulator == null
                ? capture.currentStudentNames.getOrDefault(studentId, capture.historicalStudentNames.get(studentId))
                : accumulator.displayName;
        if (displayName == null || displayName.isBlank()) displayName = "Студент #" + studentId;
        Metrics metrics = accumulator == null ? emptyMetrics() : metrics(accumulator.counter);
        boolean hasSemester = capture.context.semesterId() != null;
        boolean hasPersonalLessons = capture.selectedLessons.stream()
                .anyMatch(lesson -> capture.membersByLesson.getOrDefault((long) lesson.lesson().getId(), Set.of())
                        .contains(studentId));
        HeadmanStatsStudentDetailResponse.EmptyState emptyState = !hasSemester
                ? HeadmanStatsStudentDetailResponse.EmptyState.NO_ACTIVE_SEMESTER
                : !hasPersonalLessons ? HeadmanStatsStudentDetailResponse.EmptyState.NO_COMPLETED_LESSONS
                : HeadmanStatsStudentDetailResponse.EmptyState.NONE;

        List<SubjectMetrics> subjects = hasPersonalLessons ? studentSubjectMetrics(capture, studentId) : List.of();
        List<WeekMetrics> weeks = hasPersonalLessons ? studentWeekMetrics(capture, studentId) : List.of();
        List<Long> historyLessonIds = capture.historyMembersByLesson.entrySet().stream()
                .filter(entry -> entry.getValue().contains(studentId))
                .map(Map.Entry::getKey)
                .sorted()
                .toList();
        TicketPage<LateCheckinTicket> lateTickets = lateCheckinPage(
                groupId, capture.context.semesterId(), studentId, historyLessonIds, latePage, size, capture);
        TicketPage<ExcuseTicketEntry> excuseTickets = excusePage(
                groupId, capture.context.semesterId(), studentId, historyLessonIds, excusePage, size, capture);
        return new HeadmanStatsStudentDetailResponse(capture.context,
                new HeadmanStatsStudentDetailResponse.Student(studentId, displayName), metrics,
                subjects, weeks, lateTickets, excuseTickets, emptyState);
    }

    private StatsData calculate(long groupId, Long subjectId, List<String> requestedTypes,
                                List<HeadmanStatsSort> requestedSorts, List<HeadmanStatsFilter> filters) {
        return calculate(groupId, subjectId, requestedTypes, requestedSorts, filters, true, false, null);
    }

    private StatsData calculate(long groupId, Long subjectId, List<String> requestedTypes,
                                List<HeadmanStatsSort> requestedSorts, List<HeadmanStatsFilter> filters,
                                boolean includeTicketAggregates, boolean includeHistoryMembership,
                                CalculationCapture capture) {
        GroupResponse group = requireGroup(groupId);
        GroupMembersResponse currentRoster = academicGrpcClient.getGroupMembers(groupId);
        Set<Long> currentStudentIds = validateRoster(currentRoster);
        Map<Long, String> currentStudentNames = rosterNames(currentRoster);
        SemesterResponse semester = activeSemesterOrNull();
        Instant generatedAt = clock.instant();
        if (semester == null) {
            if (capture == null && (subjectId != null || !requestedTypes.isEmpty())) {
                throw new BadRequestException("Нет активного семестра с доступными предметами для статистики");
            }
            StatsData empty = emptyData(groupId, group.getName(), null, subjectId, requestedTypes,
                    generatedAt, List.of(), EmptyState.NO_ACTIVE_SEMESTER);
            if (capture != null) capture.capture(empty.context(), List.of(), List.of(), List.of(), Map.of(),
                    Map.of(), Map.of(), Map.of(), Map.of(), currentStudentIds, currentStudentNames, 0);
            return empty;
        }
        LocalDate semesterFrom = parseSemesterDate(semester.getDateFrom(), "start");
        LocalDate semesterTo = parseSemesterDate(semester.getDateTo(), "end");
        if (semesterTo.isBefore(semesterFrom)) {
            throw new AcademicServiceUnavailableException("Academic returned an invalid active-semester range");
        }

        LessonsResponse lessonResponse = scheduleGrpcClient.getLessonsByGroup(groupId, semester.getId(),
                semesterFrom.toString(), semesterTo.toString());
        List<StatsLesson> semesterLessons = validateSemesterLessons(lessonResponse, groupId,
                semester.getId(), semesterFrom, semesterTo);
        int completedSemesterLessons = (int) semesterLessons.stream().filter(StatsLesson::completed).count();
        List<SubjectOption> subjectOptions = buildSubjectOptions(semesterLessons);
        SubjectOption selectedSubject = validateSelection(subjectId, requestedTypes, subjectOptions);
        List<String> selectedTypes = requestedTypes.isEmpty() ? List.of()
                : selectedSubject.lessonTypes().stream().map(LessonTypeOption::code)
                .filter(requestedTypes::contains).toList();
        List<StatsLesson> selectedLessons = semesterLessons.stream()
                .filter(StatsLesson::completed)
                .filter(lesson -> subjectId == null || lesson.lesson().getSubjectId() == subjectId)
                .filter(lesson -> requestedTypes.isEmpty() || requestedTypes.contains(normalize(lesson.lessonType())))
                .toList();

        boolean captureHistory = capture != null && includeHistoryMembership;
        Map<LocalDate, GroupMembersResponse> historicalRosters = captureHistory
                ? loadHistoricalRosters(groupId, semester.getId(), semesterLessons, currentRoster)
                : new HashMap<>();
        Map<Long, Set<Long>> historyMembersByLesson = !captureHistory ? Map.of()
                : membershipsByLesson(semesterLessons, historicalRosters);
        Map<Long, String> historicalStudentNames = !captureHistory ? Map.of()
                : historicalStudentNames(historicalRosters);

        Map<Long, StudentAccumulator> students = new LinkedHashMap<>();
        if (selectedLessons.isEmpty()) {
            EmptyState empty = currentStudentIds.isEmpty() ? EmptyState.NO_MEMBERS
                    : subjectId == null ? EmptyState.NO_COMPLETED_LESSONS : EmptyState.FILTERED_EMPTY;
            Context context = context(groupId, group.getName(), semester, semesterFrom, semesterTo,
                    subjectId, selectedSubject, selectedTypes, 0, generatedAt);
            if (capture != null) capture.capture(context, subjectOptions, semesterLessons, List.of(), Map.of(),
                    historyMembersByLesson, Map.of(), Map.of(), historicalStudentNames, currentStudentIds, currentStudentNames,
                    completedSemesterLessons);
            return new StatsData(context, emptyMetrics(), 0, List.of(), subjectOptions, empty);
        }

        Map<Long, StatsLesson> lessonsById = new HashMap<>();
        for (StatsLesson lesson : selectedLessons) lessonsById.put(lesson.lesson().getId(), lesson);
        List<Long> lessonIds = selectedLessons.stream().map(value -> (long) value.lesson().getId()).toList();
        List<AttendanceRecord> records = attendanceReadPort.findByGroupAndDateRange(
                groupId, semesterFrom, semesterTo);
        Map<AttendanceKey, AttendanceRecord> attendanceByCell = validateAttendanceRecords(
                records, groupId, lessonsById);
        Map<Long, Set<Long>> membersByLesson = new HashMap<>();
        for (StatsLesson lesson : selectedLessons) {
            GroupMembersResponse roster = captureHistory ? historicalRosters.get(lesson.date())
                    : historicalRosters.computeIfAbsent(lesson.date(), date -> {
                        GroupMembersResponse loaded = academicGrpcClient.getGroupMembers(groupId, date, semester.getId());
                        validateHistoricalRoster(loaded, date, semester.getId());
                        return loaded;
                    });
            Set<Long> lessonMembers = new LinkedHashSet<>();
            for (StudentInfo member : roster.getStudentsList()) {
                long studentId = member.getUserId();
                lessonMembers.add(studentId);
                StudentAccumulator accumulator = students.computeIfAbsent(studentId,
                        ignored -> new StudentAccumulator(studentId, safeName(member.getDisplayName(), studentId)));
                accumulator.updateName(safeName(member.getDisplayName(), studentId), lesson.date());
                AttendanceRecord record = attendanceByCell.get(new AttendanceKey(lesson.lesson().getId(), studentId));
                AttendanceStatus status = record == null || record.status() == null
                        ? AttendanceStatus.ABSENT : record.status();
                accumulator.counter.add(status);
                if (status == AttendanceStatus.PRESENT && record != null) {
                    accumulator.sources.addDirect(record.source());
                }
            }
            membersByLesson.put((long) lesson.lesson().getId(), Set.copyOf(lessonMembers));
        }
        for (StudentInfo member : currentRoster.getStudentsList()) {
            long studentId = member.getUserId();
            students.computeIfAbsent(studentId,
                    ignored -> new StudentAccumulator(studentId, safeName(member.getDisplayName(), studentId)))
                    .updateName(safeName(member.getDisplayName(), studentId), LocalDate.MAX);
        }

        if (includeTicketAggregates) {
            Map<AttendanceKey, LateCheckinRequest> approvedLateCheckins = loadLateCheckins(
                    groupId, semester.getId(), lessonIds, membersByLesson, students);
            loadExcuseTickets(groupId, lessonIds, semester.getId(), membersByLesson, students);
            for (Map.Entry<AttendanceKey, LateCheckinRequest> entry : approvedLateCheckins.entrySet()) {
                AttendanceRecord record = attendanceByCell.get(entry.getKey());
                if (record == null || record.status() != AttendanceStatus.PRESENT
                        || record.source() != AttendanceSource.LATE_CHECKIN) continue;
                StudentAccumulator student = students.get(entry.getKey().studentId());
                if (student != null) student.sources.addLateOrigin(entry.getValue().getOrigin());
            }
        }

        List<StudentRow> visibleRows = students.values().stream()
                .map(StudentAccumulator::toRow)
                .filter(row -> matchesFilters(row, filters))
                .sorted((left, right) -> compareRows(left, right, requestedSorts))
                .toList();
        ReportService.StatsCounter summaryCounter = new ReportService.StatsCounter();
        visibleRows.forEach(row -> {
            StudentAccumulator student = students.get(row.studentId());
            if (student != null) summaryCounter.merge(student.counter);
        });
        Metrics summary = metrics(summaryCounter);
        EmptyState emptyState = visibleRows.isEmpty() ? EmptyState.FILTERED_EMPTY : EmptyState.NONE;
        if (visibleRows.isEmpty() && students.isEmpty()) emptyState = EmptyState.NO_MEMBERS;
        Context context = context(groupId, group.getName(), semester, semesterFrom, semesterTo,
                subjectId, selectedSubject, selectedTypes, selectedLessons.size(), generatedAt);
        if (capture != null) capture.capture(context, subjectOptions, semesterLessons, selectedLessons,
                attendanceByCell, historyMembersByLesson, membersByLesson, students, historicalStudentNames,
                currentStudentIds, currentStudentNames,
                completedSemesterLessons);
        return new StatsData(context, summary, visibleRows.size(), visibleRows, subjectOptions, emptyState);
    }

    private static Map<Long, String> rosterNames(GroupMembersResponse roster) {
        Map<Long, String> names = new LinkedHashMap<>();
        for (StudentInfo student : roster.getStudentsList()) {
            names.put(student.getUserId(), safeName(student.getDisplayName(), student.getUserId()));
        }
        return Map.copyOf(names);
    }

    private Map<LocalDate, GroupMembersResponse> loadHistoricalRosters(
            long groupId, long semesterId, List<StatsLesson> lessons, GroupMembersResponse currentRoster) {
        Map<LocalDate, GroupMembersResponse> rostersByDate = new HashMap<>();
        LocalDate today = LocalDate.now(clock);
        for (StatsLesson lesson : lessons) {
            rostersByDate.computeIfAbsent(lesson.date(), date -> {
                if (date.isAfter(today)) return currentRoster;
                GroupMembersResponse historical = academicGrpcClient.getGroupMembers(groupId, date, semesterId);
                validateHistoricalRoster(historical, date, semesterId);
                return historical;
            });
        }
        return Map.copyOf(rostersByDate);
    }

    private static Map<Long, Set<Long>> membershipsByLesson(
            List<StatsLesson> lessons, Map<LocalDate, GroupMembersResponse> rostersByDate) {
        Map<Long, Set<Long>> memberships = new HashMap<>();
        for (StatsLesson lesson : lessons) {
            GroupMembersResponse roster = rostersByDate.get(lesson.date());
            if (roster == null) {
                throw new AcademicServiceUnavailableException("Academic returned no semester-date group roster");
            }
            memberships.put((long) lesson.lesson().getId(), validateRoster(roster));
        }
        return Map.copyOf(memberships);
    }

    private static Map<Long, String> historicalStudentNames(Map<LocalDate, GroupMembersResponse> rostersByDate) {
        Map<Long, String> names = new HashMap<>();
        Map<Long, LocalDate> latestDates = new HashMap<>();
        rostersByDate.forEach((date, roster) -> {
            for (StudentInfo student : roster.getStudentsList()) {
                LocalDate latestDate = latestDates.get(student.getUserId());
                if (latestDate == null || date.isAfter(latestDate)) {
                    latestDates.put(student.getUserId(), date);
                    names.put(student.getUserId(), safeName(student.getDisplayName(), student.getUserId()));
                }
            }
        });
        return Map.copyOf(names);
    }

    private static List<TrendBucket> trendBuckets(Context context, HeadmanStatsTrendQueryRequest.Mode mode,
                                                   LocalDate weekStart) {
        LocalDate semesterFrom = context.semesterFrom();
        LocalDate semesterTo = context.semesterTo();
        if (semesterFrom == null || semesterTo == null) return List.of();
        if (mode == HeadmanStatsTrendQueryRequest.Mode.WEEK) {
            LocalDate from = weekStart.isBefore(semesterFrom) ? semesterFrom : weekStart;
            LocalDate requestedTo = weekStart.plusDays(6);
            LocalDate to = requestedTo.isAfter(semesterTo) ? semesterTo : requestedTo;
            List<TrendBucket> days = new ArrayList<>();
            for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
                days.add(new TrendBucket(date, date, date, date.toString()));
            }
            return List.copyOf(days);
        }
        List<TrendBucket> weeks = new ArrayList<>();
        LocalDate monday = semesterFrom.with(DayOfWeek.MONDAY);
        while (!monday.isAfter(semesterTo)) {
            LocalDate from = monday.isBefore(semesterFrom) ? semesterFrom : monday;
            LocalDate weekEnd = monday.plusDays(6);
            LocalDate to = weekEnd.isAfter(semesterTo) ? semesterTo : weekEnd;
            weeks.add(new TrendBucket(monday, from, to,
                    from.equals(to) ? from.toString() : from + " – " + to));
            monday = monday.plusDays(7);
        }
        return List.copyOf(weeks);
    }

    private static LocalDate trendBucketKey(LocalDate date, HeadmanStatsTrendQueryRequest.Mode mode) {
        return mode == HeadmanStatsTrendQueryRequest.Mode.WEEK ? date : date.with(DayOfWeek.MONDAY);
    }

    private static TrendMetric trendMetric(ReportService.TeacherMetric metric) {
        if (metric.denominator() == 0) return new TrendMetric(0, 0, null);
        return new TrendMetric(metric.numerator(), metric.denominator(), metric.percent());
    }

    private static List<SubjectMetrics> studentSubjectMetrics(CalculationCapture capture, long studentId) {
        Map<Long, ReportService.StatsCounter> bySubject = new HashMap<>();
        Map<SubjectTypeKey, ReportService.StatsCounter> byType = new HashMap<>();
        for (StatsLesson lesson : capture.selectedLessons) {
            long lessonId = lesson.lesson().getId();
            if (!capture.membersByLesson.getOrDefault(lessonId, Set.of()).contains(studentId)) continue;
            AttendanceRecord record = capture.attendanceByCell.get(new AttendanceKey(lessonId, studentId));
            AttendanceStatus status = record == null || record.status() == null
                    ? AttendanceStatus.ABSENT : record.status();
            bySubject.computeIfAbsent((long) lesson.lesson().getSubjectId(), ignored -> new ReportService.StatsCounter())
                    .add(status);
            byType.computeIfAbsent(new SubjectTypeKey(lesson.lesson().getSubjectId(), normalize(lesson.lessonType())),
                    ignored -> new ReportService.StatsCounter()).add(status);
        }
        List<SubjectMetrics> result = new ArrayList<>();
        for (SubjectOption subject : capture.subjects) {
            ReportService.StatsCounter subjectCounter = bySubject.get(subject.id());
            if (subjectCounter == null || subjectCounter.presentMetric().denominator() == 0) continue;
            List<HeadmanStatsStudentDetailResponse.LessonTypeMetrics> types = new ArrayList<>();
            for (LessonTypeOption type : subject.lessonTypes()) {
                ReportService.StatsCounter typeCounter = byType.get(new SubjectTypeKey(subject.id(), type.code()));
                if (typeCounter == null || typeCounter.presentMetric().denominator() == 0) continue;
                types.add(new HeadmanStatsStudentDetailResponse.LessonTypeMetrics(
                        type.code(), type.label(), metrics(typeCounter)));
            }
            result.add(new SubjectMetrics(subject.id(), subject.label(), metrics(subjectCounter), types));
        }
        return List.copyOf(result);
    }

    private static List<WeekMetrics> studentWeekMetrics(CalculationCapture capture, long studentId) {
        List<TrendBucket> buckets = trendBuckets(capture.context,
                HeadmanStatsTrendQueryRequest.Mode.SEMESTER, null);
        Map<LocalDate, ReportService.StatsCounter> counters = new HashMap<>();
        for (TrendBucket bucket : buckets) counters.put(bucket.key(), new ReportService.StatsCounter());
        for (StatsLesson lesson : capture.selectedLessons) {
            long lessonId = lesson.lesson().getId();
            if (!capture.membersByLesson.getOrDefault(lessonId, Set.of()).contains(studentId)) continue;
            LocalDate key = lesson.date().with(DayOfWeek.MONDAY);
            ReportService.StatsCounter counter = counters.get(key);
            if (counter == null) continue;
            AttendanceRecord record = capture.attendanceByCell.get(new AttendanceKey(lessonId, studentId));
            counter.add(record == null || record.status() == null ? AttendanceStatus.ABSENT : record.status());
        }
        return buckets.stream().map(bucket -> {
            ReportService.StatsCounter counter = counters.get(bucket.key());
            return new WeekMetrics(bucket.key(), bucket.from(), bucket.to(),
                    trendMetric(counter.presentMetric()), trendMetric(counter.presentOrExcusedMetric()));
        }).toList();
    }

    private TicketPage<LateCheckinTicket> lateCheckinPage(long groupId, Long semesterId, long studentId,
                                                           List<Long> eligibleLessonIds, int page, int size,
                                                           CalculationCapture capture) {
        Sort order = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));
        PageRequest request = PageRequest.of(page, size, order);
        Page<LateCheckinRequest> tickets = semesterId == null || eligibleLessonIds.isEmpty()
                ? Page.empty(request)
                 : lateCheckinRepository.findByGroupIdAndSemesterIdAndStudentIdAndLessonIdInAndStatusIn(
                        groupId, semesterId, studentId, eligibleLessonIds,
                        List.of(LateCheckinRequestStatus.PENDING, LateCheckinRequestStatus.APPROVED,
                                LateCheckinRequestStatus.REJECTED, LateCheckinRequestStatus.CANCELLED), request);
        if (tickets == null) throw new ReportExportUnavailableException("Attendance returned no late-checkin history page");
        Map<Long, StatsLesson> lessonsById = lessonsById(capture.semesterLessons);
        List<LateCheckinTicket> items = tickets.getContent().stream()
                .map(ticket -> toLateCheckinTicket(ticket, groupId, semesterId, studentId,
                        eligibleLessonIds, lessonsById, capture.subjects))
                .toList();
        return new TicketPage<>(tickets.getNumber(), tickets.getSize(), tickets.getTotalElements(),
                tickets.getTotalPages(), tickets.hasPrevious(), tickets.hasNext(), items);
    }

    private TicketPage<ExcuseTicketEntry> excusePage(long groupId, Long semesterId, long studentId,
                                                       List<Long> eligibleLessonIds, int page, int size,
                                                       CalculationCapture capture) {
        Sort order = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));
        PageRequest request = PageRequest.of(page, size, order);
        Page<ExcuseTicket> tickets = semesterId == null || eligibleLessonIds.isEmpty()
                ? Page.empty(request)
                 : excuseRepository.findByGroupIdAndSemesterIdAndStudentIdAndLessonIdsInAndStatusIn(
                        groupId, semesterId, studentId, eligibleLessonIds,
                        List.of(ExcuseTicketStatus.SUBMITTED, ExcuseTicketStatus.APPROVED,
                                ExcuseTicketStatus.REJECTED, ExcuseTicketStatus.CANCELLED), request);
        if (tickets == null) throw new ReportExportUnavailableException("Attendance returned no excuse history page");
        Map<Long, StatsLesson> lessonsById = lessonsById(capture.semesterLessons);
        Set<Long> eligible = Set.copyOf(eligibleLessonIds);
        List<ExcuseTicketEntry> items = tickets.getContent().stream()
                .map(ticket -> toExcuseTicket(ticket, groupId, semesterId, studentId, eligible,
                        lessonsById, capture.subjects))
                .toList();
        return new TicketPage<>(tickets.getNumber(), tickets.getSize(), tickets.getTotalElements(),
                tickets.getTotalPages(), tickets.hasPrevious(), tickets.hasNext(), items);
    }

    private static LateCheckinTicket toLateCheckinTicket(LateCheckinRequest ticket, long groupId, Long semesterId,
                                                         long studentId, List<Long> eligibleLessonIds,
                                                         Map<Long, StatsLesson> lessonsById,
                                                         List<SubjectOption> subjects) {
        if (ticket == null || ticket.getId() == null || ticket.getId().isBlank() || ticket.getLessonId() == null
                || ticket.getGroupId() == null || ticket.getGroupId() != groupId
                || ticket.getSemesterId() == null || !Objects.equals(ticket.getSemesterId(), semesterId)
                || ticket.getStudentId() == null || ticket.getStudentId() != studentId
                || ticket.getStatus() == null || ticket.getOrigin() == null
                || !eligibleLessonIds.contains(ticket.getLessonId())) {
            throw new ReportExportUnavailableException("Attendance returned a mismatched late-checkin history item");
        }
        StatsLesson lesson = lessonsById.get(ticket.getLessonId());
        if (lesson == null) throw new ReportExportUnavailableException("Late-checkin history references an unknown lesson");
        long subjectId = ticket.getSubjectId() == null ? lesson.lesson().getSubjectId() : ticket.getSubjectId();
        String subjectName = ticket.getSubjectName() == null || ticket.getSubjectName().isBlank()
                ? subjectName(subjectId, subjects) : ticket.getSubjectName();
        String lessonType = ticket.getSubjectType() == null || ticket.getSubjectType().isBlank()
                ? lesson.lessonType() : ticket.getSubjectType();
        LocalDate lessonDate = ticket.getLessonDate() == null ? lesson.date() : ticket.getLessonDate();
        Integer lessonNumber = ticket.getLessonNumber() == null
                ? lesson.lesson().getLessonNumber() : ticket.getLessonNumber();
        return new LateCheckinTicket(ticket.getId(), lessonDate, subjectId, subjectName,
                lessonType, lessonNumber, ticket.getCreatedAt(), ticket.getDecisionAt(),
                ticket.getStatus(), ticket.getOrigin());
    }

    private static ExcuseTicketEntry toExcuseTicket(ExcuseTicket ticket, long groupId, Long semesterId,
                                                     long studentId, Set<Long> eligibleLessonIds,
                                                     Map<Long, StatsLesson> lessonsById,
                                                     List<SubjectOption> subjects) {
        if (ticket == null || ticket.getId() == null || ticket.getId().isBlank()
                || ticket.getGroupId() == null || ticket.getGroupId() != groupId
                || ticket.getSemesterId() == null || !Objects.equals(ticket.getSemesterId(), semesterId)
                || ticket.getStudentId() == null || ticket.getStudentId() != studentId
                || ticket.getStatus() == null || ticket.getLessonIds() == null) {
            throw new ReportExportUnavailableException("Attendance returned a mismatched excuse history item");
        }
        Map<Long, StudentLessonSnapshotDocument> snapshots = ticket.getLessonSnapshots() == null
                ? Map.of()
                : ticket.getLessonSnapshots().stream()
                .filter(Objects::nonNull)
                .filter(snapshot -> snapshot.getLessonId() != null)
                .collect(java.util.stream.Collectors.toMap(StudentLessonSnapshotDocument::getLessonId,
                        snapshot -> snapshot, (first, ignored) -> first));
        List<TicketLesson> lessons = ticket.getLessonIds().stream().filter(Objects::nonNull)
                .filter(eligibleLessonIds::contains).distinct().map(lessonId -> {
                    StatsLesson lesson = lessonsById.get(lessonId);
                    StudentLessonSnapshotDocument snapshot = snapshots.get(lessonId);
                    if (lesson == null && (snapshot == null || snapshot.getSubjectId() == null
                            || snapshot.getDate() == null)) {
                        throw new ReportExportUnavailableException("Excuse history references an unknown lesson");
                    }
                    Long snapshotSubjectId = snapshot == null ? null : snapshot.getSubjectId();
                    long subjectId = snapshotSubjectId == null && lesson != null
                            ? lesson.lesson().getSubjectId() : snapshotSubjectId;
                    LocalDate lessonDate = snapshot == null || snapshot.getDate() == null
                            ? lesson.date() : snapshot.getDate();
                    String subjectName = snapshot == null || snapshot.getSubjectName() == null
                            || snapshot.getSubjectName().isBlank()
                            ? subjectName(subjectId, subjects) : snapshot.getSubjectName();
                    String lessonType = snapshot == null || snapshot.getSubjectType() == null
                            || snapshot.getSubjectType().isBlank()
                            ? lesson == null ? "" : lesson.lessonType() : snapshot.getSubjectType();
                    Integer lessonNumber = snapshot == null || snapshot.getLessonNumber() == null
                            ? lesson == null ? null : lesson.lesson().getLessonNumber() : snapshot.getLessonNumber();
                    return new TicketLesson(lessonId, lessonDate, subjectName, lessonType, lessonNumber);
                }).sorted(Comparator.comparing(TicketLesson::lessonDate).thenComparingLong(TicketLesson::lessonId))
                .toList();
        if (lessons.isEmpty()) {
            throw new ReportExportUnavailableException("Excuse history page has no eligible group lesson");
        }
        return new ExcuseTicketEntry(ticket.getId(), ticket.getCreatedAt(), ticket.getDecisionAt(),
                ticket.getStatus(), lessons);
    }

    private static String subjectName(long subjectId, List<SubjectOption> subjects) {
        return subjects.stream().filter(subject -> subject.id() == subjectId)
                .map(SubjectOption::label).findFirst().orElse("");
    }

    private static Map<Long, StatsLesson> lessonsById(List<StatsLesson> lessons) {
        Map<Long, StatsLesson> result = new HashMap<>();
        for (StatsLesson lesson : lessons) result.put((long) lesson.lesson().getId(), lesson);
        return result;
    }

    private static void requireTicketPage(int page, int size) {
        if (page < 0 || size < 1 || size > MAX_TICKET_PAGE_SIZE) {
            throw new BadRequestException("Страница истории должна быть неотрицательной, размер — от 1 до 100");
        }
    }

    private void loadExcuseTickets(long groupId, List<Long> lessonIds, long semesterId,
                                   Map<Long, Set<Long>> membersByLesson,
                                   Map<Long, StudentAccumulator> students) {
        if (lessonIds.isEmpty()) return;
        List<ExcuseTicket> tickets = excuseRepository.findByLessonIdsInAndStatusIn(lessonIds,
                List.of(ExcuseTicketStatus.SUBMITTED, ExcuseTicketStatus.APPROVED, ExcuseTicketStatus.REJECTED));
        if (tickets == null) throw new ReportExportUnavailableException("Attendance returned no excuse-ticket response");
        Set<String> ticketIds = new HashSet<>();
        Set<Long> lessonIdSet = Set.copyOf(lessonIds);
        for (ExcuseTicket ticket : tickets) {
            if (ticket == null || ticket.getId() == null || ticket.getId().isBlank()) {
                throw new ReportExportUnavailableException("Attendance returned an invalid excuse-ticket identity");
            }
            if (!ticketIds.add(ticket.getId())) {
                throw new ReportExportUnavailableException("Attendance returned duplicate excuse tickets");
            }
            if (ticket.getGroupId() == null || ticket.getSemesterId() == null || ticket.getStudentId() == null
                    || ticket.getGroupId() != groupId || ticket.getSemesterId() != semesterId) continue;
            if (ticket.getLessonIds() == null || ticket.getLessonIds().stream().filter(Objects::nonNull)
                    .noneMatch(lessonId -> lessonIdSet.contains(lessonId)
                            && membersByLesson.getOrDefault(lessonId, Set.of()).contains(ticket.getStudentId()))) continue;
            StudentAccumulator student = students.get(ticket.getStudentId());
            if (student == null) continue;
            if (ticket.getStatus() == null) continue;
            switch (ticket.getStatus()) {
                case SUBMITTED -> student.excuseTickets.submitted++;
                case APPROVED -> {
                    student.excuseTickets.submitted++;
                    student.excuseTickets.approved++;
                }
                case REJECTED -> {
                    student.excuseTickets.submitted++;
                    student.excuseTickets.rejected++;
                }
                case DRAFT, CANCELLED -> { }
            }
        }
    }

    private Map<AttendanceKey, LateCheckinRequest> loadLateCheckins(
            long groupId, long semesterId, List<Long> lessonIds,
            Map<Long, Set<Long>> membersByLesson, Map<Long, StudentAccumulator> students) {
        if (lessonIds.isEmpty()) return Map.of();
        List<LateCheckinRequest> requests = lateCheckinRepository.findByGroupIdAndSemesterIdAndStatusIn(
                groupId, semesterId, List.of(LateCheckinRequestStatus.PENDING,
                        LateCheckinRequestStatus.APPROVED, LateCheckinRequestStatus.REJECTED));
        if (requests == null) throw new ReportExportUnavailableException("Attendance returned no late-checkin response");
        Set<Long> lessonIdSet = Set.copyOf(lessonIds);
        Set<String> requestIds = new HashSet<>();
        Map<AttendanceKey, LateCheckinRequest> latestApproved = new HashMap<>();
        for (LateCheckinRequest request : requests) {
            if (request == null || request.getId() == null || request.getId().isBlank()) {
                throw new ReportExportUnavailableException("Attendance returned an invalid late-checkin identity");
            }
            if (!requestIds.add(request.getId())) {
                throw new ReportExportUnavailableException("Attendance returned duplicate late-checkin requests");
            }
            if (request.getGroupId() == null || request.getSemesterId() == null
                    || request.getStudentId() == null || request.getLessonId() == null
                    || request.getGroupId() != groupId || request.getSemesterId() != semesterId
                    || !lessonIdSet.contains(request.getLessonId())
                    || !membersByLesson.getOrDefault(request.getLessonId(), Set.of()).contains(request.getStudentId())) continue;
            if (request.getStatus() == null) {
                throw new ReportExportUnavailableException("Attendance returned a late-checkin request without status");
            }
            StudentAccumulator student = students.get(request.getStudentId());
            if (student != null) {
                switch (request.getStatus()) {
                    case PENDING -> student.lateTickets.submitted++;
                    case APPROVED -> {
                        student.lateTickets.submitted++;
                        student.lateTickets.approved++;
                    }
                    case REJECTED -> {
                        student.lateTickets.submitted++;
                        student.lateTickets.rejected++;
                    }
                    case CANCELLED -> { }
                }
            }
            if (request.getStatus() == LateCheckinRequestStatus.APPROVED) {
                AttendanceKey key = new AttendanceKey(request.getLessonId(), request.getStudentId());
                LateCheckinRequest previous = latestApproved.get(key);
                if (previous == null || newer(request, previous)) latestApproved.put(key, request);
            }
        }
        return Map.copyOf(latestApproved);
    }

    private List<StatsLesson> validateSemesterLessons(LessonsResponse response, long groupId, long semesterId,
                                                       LocalDate from, LocalDate to) {
        if (response == null) throw new ScheduleServiceUnavailableException("Schedule returned no semester lesson response");
        if (response.getLessonsCount() > MAX_SEMESTER_LESSONS) {
            throw new ReportExportTooLargeException("В активном семестре больше 5000 занятий для статистического расчёта");
        }
        List<StatsLesson> all = new ArrayList<>();
        Set<Long> ids = new HashSet<>();
        for (LessonResponse lesson : response.getLessonsList()) {
            if (lesson.getId() <= 0 || lesson.getGroupId() != groupId || lesson.getSemesterId() != semesterId
                    || lesson.getSubjectId() <= 0 || lesson.getLessonNumber() <= 0 || !ids.add(lesson.getId())
                    || lesson.getLessonType() == null || lesson.getLessonType().isBlank()) {
                throw new ScheduleServiceUnavailableException("Schedule returned malformed headman statistics lessons");
            }
            String rawStatus = normalizedLessonStatus(lesson.getStatus());
            if (!Set.of("planned", "active", "closed", "cancelled", "transferred", "deleted").contains(rawStatus)) {
                throw new ScheduleServiceUnavailableException("Schedule returned an unknown semester lesson status");
            }
            LocalDate date;
            if (rawStatus.equals("cancelled") || rawStatus.equals("transferred") || rawStatus.equals("deleted")) {
                date = parseLessonDate(lesson.getDate());
            } else {
                try {
                    date = JournalLessonPolicy.requireTiming(lesson).date();
                } catch (RuntimeException invalid) {
                    throw new ScheduleServiceUnavailableException("Schedule returned incomplete headman statistics lesson timing");
                }
            }
            if (date.isBefore(from) || date.isAfter(to)) {
                throw new ScheduleServiceUnavailableException("Schedule returned a lesson outside the active semester");
            }
            boolean excluded = rawStatus.equals("cancelled") || rawStatus.equals("transferred") || rawStatus.equals("deleted");
            all.add(new StatsLesson(lesson, date, normalize(lesson.getLessonType()), !excluded,
                    rawStatus.equals("closed")));
        }
        all.sort(Comparator.comparing(StatsLesson::date)
                .thenComparingInt(value -> value.lesson().getLessonNumber())
                .thenComparingLong(value -> value.lesson().getId()));
        return List.copyOf(all);
    }

    private List<SubjectOption> buildSubjectOptions(List<StatsLesson> lessons) {
        Map<Long, Set<String>> typesBySubject = new TreeMap<>();
        for (StatsLesson lesson : lessons) {
            if (lesson.inOptions()) typesBySubject.computeIfAbsent((long) lesson.lesson().getSubjectId(),
                    ignored -> new java.util.TreeSet<>()).add(lesson.lessonType());
        }
        if (typesBySubject.isEmpty()) return List.of();
        Map<Long, AcademicGrpcClient.SubjectDetails> subjects = academicGrpcClient.getSubjectDetailsByIds(
                List.copyOf(typesBySubject.keySet()));
        List<SubjectOption> options = new ArrayList<>();
        for (Map.Entry<Long, Set<String>> entry : typesBySubject.entrySet()) {
            AcademicGrpcClient.SubjectDetails subject = subjects.get(entry.getKey());
            if (subject == null || subject.name() == null || subject.name().isBlank()) {
                throw new AcademicServiceUnavailableException("Academic returned no active-semester subject details");
            }
            List<LessonTypeOption> types = entry.getValue().stream()
                    .map(type -> new LessonTypeOption(type, typeLabel(type))).toList();
            options.add(new SubjectOption(entry.getKey(), subject.name(), types));
        }
        options.sort(Comparator.comparing(SubjectOption::label, String.CASE_INSENSITIVE_ORDER)
                .thenComparingLong(SubjectOption::id));
        return List.copyOf(options);
    }

    private static SubjectOption validateSelection(Long subjectId, List<String> requestedTypes,
                                                  List<SubjectOption> options) {
        if (subjectId == null) {
            if (!requestedTypes.isEmpty()) throw new BadRequestException("Сначала выбери предмет");
            return null;
        }
        SubjectOption selected = options.stream().filter(option -> option.id() == subjectId).findFirst()
                .orElseThrow(() -> new BadRequestException("Предмет не относится к активной группе и семестру"));
        Set<String> available = selected.lessonTypes().stream().map(LessonTypeOption::code)
                .collect(java.util.stream.Collectors.toSet());
        if (!available.containsAll(requestedTypes)) {
            throw new BadRequestException("Тип занятия не относится к выбранному предмету и семестру");
        }
        return selected;
    }

    private Map<AttendanceKey, AttendanceRecord> validateAttendanceRecords(
            List<AttendanceRecord> records, long groupId, Map<Long, StatsLesson> lessonsById) {
        if (records == null) throw new ReportExportUnavailableException("Attendance returned no statistics records");
        Map<AttendanceKey, AttendanceRecord> result = new HashMap<>();
        for (AttendanceRecord record : records) {
            if (record == null || record.lessonId() == null || record.userId() == null) {
                throw new ReportExportUnavailableException("Attendance returned an incomplete statistics record");
            }
            StatsLesson lesson = lessonsById.get(record.lessonId());
            if (lesson == null) continue;
            if (!Objects.equals(record.groupId(), groupId) || record.subjectId() == null
                    || record.lessonNumber() == null || record.lessonDate() == null
                    || record.subjectId() != lesson.lesson().getSubjectId()
                    || record.lessonNumber() != lesson.lesson().getLessonNumber()
                    || !record.lessonDate().equals(lesson.date())) {
                throw new ReportExportUnavailableException("Attendance returned a mismatched statistics record");
            }
            AttendanceKey key = new AttendanceKey(record.lessonId(), record.userId());
            if (result.putIfAbsent(key, record) != null) {
                throw new ReportExportUnavailableException("Attendance returned duplicate statistics records");
            }
        }
        return Map.copyOf(result);
    }

    private static void validateHistoricalRoster(GroupMembersResponse response, LocalDate date, long semesterId) {
        if (response == null || !response.hasAsOfDate() || !response.hasSemesterId()
                || !date.toString().equals(response.getAsOfDate()) || response.getSemesterId() != semesterId) {
            throw new AcademicServiceUnavailableException("Academic returned a mismatched historical group roster");
        }
        validateRoster(response);
    }

    private static Set<Long> validateRoster(GroupMembersResponse response) {
        if (response == null) throw new AcademicServiceUnavailableException("Academic returned no group roster");
        Set<Long> ids = new LinkedHashSet<>();
        for (StudentInfo student : response.getStudentsList()) {
            if (student.getUserId() <= 0 || !ids.add(student.getUserId())) {
                throw new AcademicServiceUnavailableException("Academic returned duplicate or invalid group members");
            }
        }
        return Set.copyOf(ids);
    }

    private GroupResponse requireGroup(long groupId) {
        GroupResponse group = academicGrpcClient.getGroup(groupId);
        if (group == null || group.getId() != groupId || group.getName() == null || group.getName().isBlank()) {
            throw new AcademicServiceUnavailableException("Academic returned incomplete headman group metadata");
        }
        return group;
    }

    private SemesterResponse activeSemesterOrNull() {
        try {
            SemesterResponse semester = academicGrpcClient.getActiveSemester();
            if (semester == null || semester.getId() <= 0) return null;
            if (semester.getName() == null || semester.getName().isBlank()) {
                throw new AcademicServiceUnavailableException("Academic returned incomplete active-semester metadata");
            }
            return semester;
        } catch (ResourceNotFoundException missing) {
            return null;
        }
    }

    private long ensureHeadman() {
        Long groupId = requestContext.getGroupId();
        if (groupId == null || groupId <= 0) {
            throw new AccessDeniedException("Headman group is not available in request context");
        }
        if (!academicGrpcClient.hasAssistantPermission(groupId, "VIEW_STATS")) {
            throw new AccessDeniedException("Отсутствует право VIEW_STATS");
        }
        return groupId;
    }

    private StatsData emptyData(long groupId, String groupName, SemesterResponse semester,
                                Long subjectId, List<String> types, Instant generatedAt,
                                List<SubjectOption> subjects, EmptyState emptyState) {
        Context context = new Context(groupId, groupName, semester == null ? null : semester.getId(),
                semester == null ? "" : semester.getName(), null, null,
                subjectId, "", types, 0, generatedAt);
        return new StatsData(context, emptyMetrics(), 0, List.of(), subjects, emptyState);
    }

    private static Context context(long groupId, String groupName, SemesterResponse semester,
                                   LocalDate from, LocalDate to, Long subjectId, SubjectOption subject,
                                   List<String> types, int lessonsCount, Instant generatedAt) {
        return new Context(groupId, groupName, semester.getId(), semester.getName(), from, to,
                subjectId, subject == null ? "" : subject.label(), types, lessonsCount, generatedAt);
    }

    private static Metrics metrics(ReportService.StatsCounter counter) {
        return new Metrics(metric(counter.presentMetric()), metric(counter.presentOrExcusedMetric()),
                metric(counter.excusedMetric()), metric(counter.absentMetric()));
    }

    private static Metrics emptyMetrics() {
        return new Metrics(new Metric(0, 0, 0), new Metric(0, 0, 0),
                new Metric(0, 0, 0), new Metric(0, 0, 0));
    }

    private static Metric metric(ReportService.TeacherMetric metric) {
        return new Metric(metric.numerator(), metric.denominator(), metric.percent());
    }

    private static List<String> validateTypes(List<String> rawTypes, Long subjectId) {
        if (rawTypes == null || rawTypes.isEmpty()) return List.of();
        if (subjectId == null) throw new BadRequestException("Типы занятий нельзя выбрать без предмета");
        List<String> normalized = rawTypes.stream().map(HeadmanStatsService::normalize)
                .filter(value -> !value.isBlank()).toList();
        if (normalized.size() != rawTypes.size() || new HashSet<>(normalized).size() != normalized.size()) {
            throw new BadRequestException("Список типов занятий содержит пустое или повторное значение");
        }
        return List.copyOf(normalized);
    }

    private static String describeExportFilters(List<HeadmanStatsFilter> filters) {
        if (filters.isEmpty()) return "Нет";
        return filters.stream().map(filter -> {
            String label = COLUMNS_BY_FIELD.get(filter.field()).label();
            if (filter.contains() != null) return label + ": содержит «" + filter.contains().trim() + "»";
            String bounds = filter.minimum() == null ? "" : "от " + filter.minimum().toPlainString();
            if (filter.maximum() != null) {
                bounds += (bounds.isEmpty() ? "" : " ") + "до " + filter.maximum().toPlainString();
            }
            return label + ": " + (bounds.isEmpty() ? "без ограничения" : bounds + " (включительно)");
        }).collect(java.util.stream.Collectors.joining("; "));
    }

    private static String describeExportSorts(List<HeadmanStatsSort> sorts) {
        if (sorts.isEmpty()) return COLUMNS_BY_FIELD.get("presentPercent").label()
                + ": по убыванию (по умолчанию)";
        List<String> labels = new ArrayList<>();
        for (int index = 0; index < sorts.size(); index++) {
            HeadmanStatsSort sort = sorts.get(index);
            labels.add((index + 1) + ". " + COLUMNS_BY_FIELD.get(sort.field()).label()
                    + (sort.descending() ? ": по убыванию" : ": по возрастанию"));
        }
        return String.join("; ", labels);
    }

    private static List<HeadmanStatsSort> validateSorts(List<HeadmanStatsSort> sorts) {
        if (sorts == null || sorts.isEmpty()) return List.of();
        Set<String> fields = new HashSet<>();
        for (HeadmanStatsSort sort : sorts) {
            if (sort == null || !COLUMNS_BY_FIELD.containsKey(sort.field()) || !fields.add(sort.field())) {
                throw new BadRequestException("Сортировка содержит неизвестное или повторное поле");
            }
        }
        return List.copyOf(sorts);
    }

    private static List<HeadmanStatsFilter> validateFilters(List<HeadmanStatsFilter> filters) {
        if (filters == null || filters.isEmpty()) return List.of();
        Set<String> fields = new HashSet<>();
        for (HeadmanStatsFilter filter : filters) {
            if (filter == null || !COLUMNS_BY_FIELD.containsKey(filter.field()) || !fields.add(filter.field())) {
                throw new BadRequestException("Фильтр содержит неизвестное или повторное поле");
            }
            boolean text = COLUMNS_BY_FIELD.get(filter.field()).filterKind() == FilterKind.TEXT;
            if (text) {
                if (filter.contains() == null || filter.minimum() != null || filter.maximum() != null) {
                    throw new BadRequestException("Для фильтра ФИО укажи только текст");
                }
            } else {
                if (filter.contains() != null || filter.minimum() != null && filter.minimum().signum() < 0
                        || filter.maximum() != null && filter.maximum().signum() < 0
                        || filter.minimum() != null && filter.maximum() != null
                        && filter.minimum().compareTo(filter.maximum()) > 0) {
                    throw new BadRequestException("Укажи корректный числовой диапазон");
                }
                if (filter.field().endsWith("Percent")
                        && (filter.minimum() != null && filter.minimum().compareTo(BigDecimal.valueOf(100)) > 0
                        || filter.maximum() != null && filter.maximum().compareTo(BigDecimal.valueOf(100)) > 0)) {
                    throw new BadRequestException("Процент должен быть в диапазоне от 0 до 100");
                }
            }
        }
        return List.copyOf(filters);
    }

    private static int requirePage(HeadmanStatsQueryRequest request) {
        if (request == null || request.page() == null || request.page() < 0) {
            throw new BadRequestException("Номер страницы должен быть неотрицательным");
        }
        return request.page();
    }

    private static int requirePageSize(HeadmanStatsQueryRequest request) {
        if (request == null || request.size() == null || request.size() < 1 || request.size() > MAX_PAGE_SIZE) {
            throw new BadRequestException("Размер страницы должен быть от 1 до 100 строк");
        }
        return request.size();
    }

    private static LocalDate parseSemesterDate(String value, String field) {
        try {
            return LocalDate.parse(value);
        } catch (RuntimeException invalid) {
            throw new AcademicServiceUnavailableException("Academic returned an invalid semester " + field + " date");
        }
    }

    private static LocalDate parseLessonDate(String value) {
        try {
            return LocalDate.parse(value);
        } catch (RuntimeException invalid) {
            throw new ScheduleServiceUnavailableException("Schedule returned an invalid semester lesson date");
        }
    }

    private static boolean matchesFilters(StudentRow row, List<HeadmanStatsFilter> filters) {
        for (HeadmanStatsFilter filter : filters) {
            if (COLUMNS_BY_FIELD.get(filter.field()).filterKind() == FilterKind.TEXT) {
                if (!row.displayName().toLowerCase(Locale.ROOT)
                        .contains(filter.contains().trim().toLowerCase(Locale.ROOT))) return false;
                continue;
            }
            BigDecimal value = valueFor(row, filter.field());
            if (filter.minimum() != null && value.compareTo(filter.minimum()) < 0
                    || filter.maximum() != null && value.compareTo(filter.maximum()) > 0) return false;
        }
        return true;
    }

    private static BigDecimal valueFor(StudentRow row, String field) {
        return switch (field) {
            case "presentCount" -> BigDecimal.valueOf(row.metrics().present().numerator());
            case "presentPercent" -> BigDecimal.valueOf(row.metrics().present().percent());
            case "presentOrExcusedCount" -> BigDecimal.valueOf(row.metrics().presentOrExcused().numerator());
            case "presentOrExcusedPercent" -> BigDecimal.valueOf(row.metrics().presentOrExcused().percent());
            case "excusedCount" -> BigDecimal.valueOf(row.metrics().excused().numerator());
            case "excusedPercent" -> BigDecimal.valueOf(row.metrics().excused().percent());
            case "absentCount" -> BigDecimal.valueOf(row.metrics().absent().numerator());
            case "absentPercent" -> BigDecimal.valueOf(row.metrics().absent().percent());
            case "lateSubmitted" -> BigDecimal.valueOf(row.lateCheckin().submitted());
            case "lateApproved" -> BigDecimal.valueOf(row.lateCheckin().approved());
            case "lateRejected" -> BigDecimal.valueOf(row.lateCheckin().rejected());
            case "excuseSubmitted" -> BigDecimal.valueOf(row.excuse().submitted());
            case "excuseApproved" -> BigDecimal.valueOf(row.excuse().approved());
            case "excuseRejected" -> BigDecimal.valueOf(row.excuse().rejected());
            case "sourceStudentGeo" -> BigDecimal.valueOf(row.sources().studentGeo());
            case "sourceManualRequest" -> BigDecimal.valueOf(row.sources().manualRequest());
            case "sourceAutoGeoFailure" -> BigDecimal.valueOf(row.sources().autoAfterGeoFailure());
            case "sourceHeadmanManual" -> BigDecimal.valueOf(row.sources().headmanManual());
            default -> throw new BadRequestException("Неизвестный числовой столбец");
        };
    }

    private static int compareRows(StudentRow left, StudentRow right, List<HeadmanStatsSort> sorts) {
        List<HeadmanStatsSort> effective = sorts.isEmpty()
                ? List.of(new HeadmanStatsSort("presentPercent", true)) : sorts;
        for (HeadmanStatsSort sort : effective) {
            int result = COLUMNS_BY_FIELD.get(sort.field()).filterKind() == FilterKind.TEXT
                    ? left.displayName().compareToIgnoreCase(right.displayName())
                    : valueFor(left, sort.field()).compareTo(valueFor(right, sort.field()));
            if (result != 0) return sort.descending() ? -result : result;
        }
        int byName = left.displayName().compareToIgnoreCase(right.displayName());
        return byName != 0 ? byName : Long.compare(left.studentId(), right.studentId());
    }

    private static ColumnDescriptor column(String field, String label, FilterKind kind) {
        return new ColumnDescriptor(field, label, kind);
    }

    private static boolean newer(LateCheckinRequest candidate, LateCheckinRequest previous) {
        if (candidate.getUpdatedAt() != null && previous.getUpdatedAt() != null) {
            int byUpdated = candidate.getUpdatedAt().compareTo(previous.getUpdatedAt());
            if (byUpdated != 0) return byUpdated > 0;
        } else if (candidate.getCreatedAt() != null && previous.getCreatedAt() != null) {
            int byCreated = candidate.getCreatedAt().compareTo(previous.getCreatedAt());
            if (byCreated != 0) return byCreated > 0;
        }
        return candidate.getId().compareTo(previous.getId()) > 0;
    }

    private static String safeName(String value, long studentId) {
        return value == null || value.isBlank() ? "Студент #" + studentId : value;
    }

    private static String typeLabel(String type) {
        return switch (normalize(type)) {
            case "lecture" -> "Лекция";
            case "practice" -> "Практика";
            case "lab", "laboratory" -> "Лабораторная";
            default -> type;
        };
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static String normalizedLessonStatus(String value) {
        String status = normalize(value);
        // The shared journal policy already accepts legacy STARTED as ACTIVE.
        return status.equals("started") ? "active" : status;
    }

    private record StatsData(Context context, Metrics summary, int totalRows, List<StudentRow> rows,
                             List<SubjectOption> subjects, EmptyState emptyState) {
    }

    private record StatsLesson(LessonResponse lesson, LocalDate date, String lessonType,
                               boolean inOptions, boolean completed) {
    }

    private record AttendanceKey(long lessonId, long studentId) {
    }

    private record TrendBucket(LocalDate key, LocalDate from, LocalDate to, String label) {
    }

    private record SubjectTypeKey(long subjectId, String lessonType) {
    }

    /** Captures the central calculation once for trend or personal detail without ticket-wide scans. */
    private static final class CalculationCapture {
        private Context context;
        private List<SubjectOption> subjects = List.of();
        private List<StatsLesson> semesterLessons = List.of();
        private List<StatsLesson> selectedLessons = List.of();
        private Map<AttendanceKey, AttendanceRecord> attendanceByCell = Map.of();
        private Map<Long, Set<Long>> historyMembersByLesson = Map.of();
        private Map<Long, Set<Long>> membersByLesson = Map.of();
        private Map<Long, StudentAccumulator> students = Map.of();
        private Map<Long, String> historicalStudentNames = Map.of();
        private Set<Long> currentStudentIds = Set.of();
        private Map<Long, String> currentStudentNames = Map.of();
        private int completedSemesterLessons;

        private void capture(Context context, List<SubjectOption> subjects, List<StatsLesson> semesterLessons,
                             List<StatsLesson> selectedLessons,
                             Map<AttendanceKey, AttendanceRecord> attendanceByCell,
                             Map<Long, Set<Long>> historyMembersByLesson,
                             Map<Long, Set<Long>> membersByLesson,
                             Map<Long, StudentAccumulator> students,
                             Map<Long, String> historicalStudentNames,
                             Set<Long> currentStudentIds, Map<Long, String> currentStudentNames,
                             int completedSemesterLessons) {
            this.context = Objects.requireNonNull(context, "context");
            this.subjects = List.copyOf(subjects);
            this.semesterLessons = List.copyOf(semesterLessons);
            this.selectedLessons = List.copyOf(selectedLessons);
            this.attendanceByCell = Map.copyOf(attendanceByCell);
            this.historyMembersByLesson = immutableMemberships(historyMembersByLesson);
            this.membersByLesson = immutableMemberships(membersByLesson);
            this.students = Map.copyOf(students);
            this.historicalStudentNames = Map.copyOf(historicalStudentNames);
            this.currentStudentIds = Set.copyOf(currentStudentIds);
            this.currentStudentNames = Map.copyOf(currentStudentNames);
            this.completedSemesterLessons = completedSemesterLessons;
        }

        private static Map<Long, Set<Long>> immutableMemberships(Map<Long, Set<Long>> memberships) {
            Map<Long, Set<Long>> copy = new HashMap<>();
            memberships.forEach((lessonId, studentIds) -> copy.put(lessonId, Set.copyOf(studentIds)));
            return Map.copyOf(copy);
        }
    }

    private static final class StudentAccumulator {
        private final long studentId;
        private final ReportService.StatsCounter counter = new ReportService.StatsCounter();
        private final TicketCounter lateTickets = new TicketCounter();
        private final TicketCounter excuseTickets = new TicketCounter();
        private final SourceCounter sources = new SourceCounter();
        private String displayName;
        private LocalDate nameDate = LocalDate.MIN;

        private StudentAccumulator(long studentId, String displayName) {
            this.studentId = studentId;
            this.displayName = displayName;
        }

        private void updateName(String candidate, LocalDate date) {
            if (date.isAfter(nameDate)) {
                displayName = candidate;
                nameDate = date;
            }
        }

        private StudentRow toRow() {
            return new StudentRow(studentId, displayName, metrics(counter), lateTickets.toCounts(),
                    excuseTickets.toCounts(), sources.toSources());
        }
    }

    private static final class TicketCounter {
        private int submitted;
        private int approved;
        private int rejected;

        private TicketCounts toCounts() {
            return new TicketCounts(submitted, approved, rejected);
        }
    }

    private static final class SourceCounter {
        private int studentGeo;
        private int manualRequest;
        private int autoAfterGeoFailure;
        private int headmanManual;

        private void addDirect(AttendanceSource source) {
            if (source == AttendanceSource.STUDENT_GEO) studentGeo++;
            else if (source == AttendanceSource.HEADMAN) headmanManual++;
        }

        private void addLateOrigin(LateCheckinRequestOrigin origin) {
            if (origin == LateCheckinRequestOrigin.MANUAL) manualRequest++;
            else if (origin == LateCheckinRequestOrigin.AUTO_GEO_FAILURE) autoAfterGeoFailure++;
        }

        private Sources toSources() {
            return new Sources(studentGeo, manualRequest, autoAfterGeoFailure, headmanManual);
        }
    }
}
