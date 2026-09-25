package ru.rutcampustrack.attendance.report;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import lombok.RequiredArgsConstructor;
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
import ru.rutcampustrack.documentrenderer.grpc.TargetFormat;
import ru.rutcampustrack.schedule.grpc.LessonResponse;
import ru.rutcampustrack.schedule.grpc.LessonsResponse;

import java.math.BigDecimal;
import java.time.Clock;
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
                data.context(), data.summary(), data.rows().size(), data.rows());
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

    private StatsData calculate(long groupId, Long subjectId, List<String> requestedTypes,
                                List<HeadmanStatsSort> requestedSorts, List<HeadmanStatsFilter> filters) {
        GroupResponse group = requireGroup(groupId);
        SemesterResponse semester = activeSemesterOrNull();
        Instant generatedAt = clock.instant();
        if (semester == null) {
            if (subjectId != null || !requestedTypes.isEmpty()) {
                throw new BadRequestException("Нет активного семестра с доступными предметами для статистики");
            }
            return emptyData(groupId, group.getName(), null, subjectId, requestedTypes,
                    generatedAt, List.of(), EmptyState.NO_ACTIVE_SEMESTER);
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

        GroupMembersResponse currentRoster = academicGrpcClient.getGroupMembers(groupId);
        Map<Long, StudentAccumulator> students = new LinkedHashMap<>();
        Set<Long> currentStudentIds = validateRoster(currentRoster);
        if (selectedLessons.isEmpty()) {
            EmptyState empty = currentStudentIds.isEmpty() ? EmptyState.NO_MEMBERS
                    : subjectId == null ? EmptyState.NO_COMPLETED_LESSONS : EmptyState.FILTERED_EMPTY;
            Context context = context(groupId, group.getName(), semester, semesterFrom, semesterTo,
                    subjectId, selectedSubject, selectedTypes, 0, generatedAt);
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
        Map<LocalDate, GroupMembersResponse> historicalRosters = new HashMap<>();
        for (StatsLesson lesson : selectedLessons) {
            GroupMembersResponse roster = historicalRosters.computeIfAbsent(lesson.date(), date -> {
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
        return new StatsData(context, summary, visibleRows.size(), visibleRows, subjectOptions, emptyState);
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
        if (!requestContext.isHeadman() && !academicGrpcClient.hasAssistantPermission(groupId, "VIEW_STATS")) {
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
