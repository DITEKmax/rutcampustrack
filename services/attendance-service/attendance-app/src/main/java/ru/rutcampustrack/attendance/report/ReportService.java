package ru.rutcampustrack.attendance.report;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ru.rutcampustrack.academic.grpc.GroupMembersResponse;
import ru.rutcampustrack.academic.grpc.GroupResponse;
import ru.rutcampustrack.academic.grpc.StudentInfo;
import ru.rutcampustrack.academic.grpc.TeacherSubjectInfo;
import ru.rutcampustrack.academic.grpc.TeacherSubjectsResponse;
import ru.rutcampustrack.attendance.contract.dto.report.AttendanceRecordEntry;
import ru.rutcampustrack.attendance.contract.dto.report.JournalCell;
import ru.rutcampustrack.attendance.contract.dto.report.JournalResponse;
import ru.rutcampustrack.attendance.contract.dto.report.JournalStudentRow;
import ru.rutcampustrack.attendance.contract.dto.report.LessonAttendanceResponse;
import ru.rutcampustrack.attendance.contract.dto.report.OverallStats;
import ru.rutcampustrack.attendance.contract.dto.report.StatusBreakdown;
import ru.rutcampustrack.attendance.contract.dto.report.StudentAttendanceEntry;
import ru.rutcampustrack.attendance.contract.dto.report.StudentDashboardResponse;
import ru.rutcampustrack.attendance.contract.dto.report.StudentStatsResponse;
import ru.rutcampustrack.attendance.contract.dto.report.SubjectStats;
import ru.rutcampustrack.attendance.contract.dto.report.TopMissedSubject;
import ru.rutcampustrack.attendance.contract.dto.report.WeeklyStat;
import ru.rutcampustrack.attendance.contract.enums.AttendanceSource;
import ru.rutcampustrack.attendance.contract.enums.AttendanceStatus;
import ru.rutcampustrack.attendance.contract.enums.UserRole;
import ru.rutcampustrack.attendance.exception.AccessDeniedException;
import ru.rutcampustrack.attendance.exception.AcademicServiceUnavailableException;
import ru.rutcampustrack.attendance.exception.BadRequestException;
import ru.rutcampustrack.attendance.grpc.AcademicGrpcClient;
import ru.rutcampustrack.attendance.grpc.ScheduleGrpcClient;
import ru.rutcampustrack.attendance.journal.JournalLessonPolicy;
import ru.rutcampustrack.attendance.security.RequestContext;
import ru.rutcampustrack.attendance.semester.SemesterCacheService;
import ru.rutcampustrack.attendance.shared.port.AttendanceReadPort;
import ru.rutcampustrack.attendance.shared.port.AttendanceRecord;
import ru.rutcampustrack.attendance.shared.port.JournalAttachmentPort;
import ru.rutcampustrack.schedule.grpc.LessonResponse;
import ru.rutcampustrack.schedule.grpc.LessonInfo;
import ru.rutcampustrack.attendance.exception.ScheduleServiceUnavailableException;

import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Business logic for all 4 attendance report endpoints (RPRT-01..04).
 *
 * Domain isolation: this class lives in report/ and MUST NOT import from checkin/.
 * It accesses attendance data exclusively via AttendanceReadPort (shared/port/).
 *
 * Authorization:
 * - getLessonAttendance and getJournal: headman (own group) or teacher (teaches subject+group)
 * - getStudentStats and getStudentRecords: any authenticated user (own data only via RequestContext)
 */
@Service
@RequiredArgsConstructor
public class ReportService {

    /** Status symbols per D-08 specification. */
    private static final Map<AttendanceStatus, String> STATUS_SYMBOLS = Map.of(
            AttendanceStatus.PRESENT, "+",
            AttendanceStatus.ABSENT, "н",
            AttendanceStatus.EXCUSED, "у",
            AttendanceStatus.FREE_ATTENDANCE, "сп",
            AttendanceStatus.CANCELLED, "--"
    );

    private final AttendanceReadPort attendanceReadPort;
    private final AcademicGrpcClient academicGrpcClient;
    private final ScheduleGrpcClient scheduleGrpcClient;
    private final SemesterCacheService semesterCacheService;
    private final RequestContext requestContext;
    private final Clock clock;
    private final JournalAttachmentPort journalAttachmentPort;

    private String statusSymbol(AttendanceStatus s) {
        return STATUS_SYMBOLS.getOrDefault(s, "?");
    }

    // -------------------------------------------------------------------------
    // RPRT-01: Lesson attendance list
    // -------------------------------------------------------------------------

    /**
     * Returns attendance for all group members for a specific lesson.
     * Left-join: students not in MongoDB default to ABSENT.
     *
     * Authorization: headman (own group) or teacher teaching this subject+group.
     */
    public LessonAttendanceResponse getLessonAttendance(Long lessonId) {
        LessonResponse lesson = scheduleGrpcClient.getLessonById(lessonId);
        if (lesson == null) {
            throw new BadRequestException("Пара недоступна");
        }
        authorizeHeadmanOrTeacher(lesson);

        return buildLessonAttendance(lesson);
    }

    /**
     * Reads one concrete lesson for a teacher identity supplied by the
     * dedicated teacher gRPC boundary. The schedule snapshot remains the
     * historical lesson authority; this method reuses the existing exact
     * assignment and historical fallback gate below.
     */
    public LessonAttendanceResponse getTeacherLessonAttendance(Long lessonId, long teacherId) {
        LessonResponse lesson = scheduleGrpcClient.getLessonById(lessonId);
        if (lesson == null) {
            throw new BadRequestException("Пара недоступна");
        }
        authorizeTeacherLesson(lesson, teacherId);
        return buildLessonAttendance(lesson);
    }

    private LessonAttendanceResponse buildLessonAttendance(LessonResponse lesson) {
        Long lessonId = lesson.getId();

        JournalLessonPolicy.Timing timing = JournalLessonPolicy.requireTiming(lesson);
        GroupMembersResponse members = membersForLesson(lesson);
        String lessonStatus = timing.status();
        boolean editable = !"CANCELLED".equals(lessonStatus)
                && timing.hasStarted(clock.instant());
        String blockedReason = editBlockedReason(timing, clock.instant());

        List<AttendanceRecord> records = attendanceReadPort.findByLessonId(lessonId);
        Map<Long, AttendanceRecord> recordsByUserId = records.stream()
                .collect(Collectors.toMap(AttendanceRecord::userId, r -> r, (a, b) -> a));

        List<StudentAttendanceEntry> entries = members.getStudentsList().stream()
                .map(student -> {
                    Long uid = student.getUserId();
                    AttendanceRecord rec = recordsByUserId.get(uid);
                    AttendanceStatus status = rec == null ? null : rec.status();
                    String source = (rec != null && rec.source() != null)
                            ? rec.source().name().toLowerCase()
                            : null;
                    boolean excuse = rec != null && rec.status() == AttendanceStatus.EXCUSED;
                    String excuseReason = excuse ? rec.excuseReason() : null;
                    String excuseType = excuse ? rec.excuseType() : null;
                    String comment = excuse ? rec.excuseComment() : null;
                    boolean attachmentAvailable = excuse
                            && journalAttachmentPort != null
                            && journalAttachmentPort.isAvailable(lessonId, uid, rec.attachmentId());
                    String attachmentId = attachmentAvailable ? rec.attachmentId() : null;
                    String attachmentName = attachmentAvailable ? rec.attachmentName() : null;
                    String attachmentContentType = attachmentAvailable ? rec.attachmentContentType() : null;
                    Long attachmentSize = attachmentAvailable ? rec.attachmentSize() : null;
                    return new StudentAttendanceEntry(uid, student.getDisplayName(),
                            status == null ? null : status.name().toLowerCase(),
                            status == null ? null : statusSymbol(status), source, excuseReason,
                            excuseType, comment, attachmentId, attachmentName,
                            attachmentContentType, attachmentSize, editable, blockedReason);
                })
                .toList();

        return new LessonAttendanceResponse(
                lessonId,
                lesson.getGroupId(),
                lesson.getSubjectId(),
                lesson.getDate(),
                lesson.getSemesterId() > 0 ? lesson.getSemesterId() : null,
                lessonStatus,
                editable,
                entries
        );
    }

    private GroupMembersResponse membersForLesson(LessonResponse lesson) {
        if (lesson.getSemesterId() <= 0) {
            throw new AcademicServiceUnavailableException(
                    "Schedule returned a lesson without a positive semester");
        }
        LocalDate lessonDate = parseLessonDate(lesson.getDate());
        GroupMembersResponse response = academicGrpcClient.getGroupMembers(
                lesson.getGroupId(), lessonDate, lesson.getSemesterId());
        validateHistoricalRoster(response, lessonDate, lesson.getSemesterId());
        return response;
    }

    private static void validateHistoricalRoster(GroupMembersResponse members,
                                                 LocalDate lessonDate, long semesterId) {
        if (members == null || !members.hasAsOfDate() || !members.hasSemesterId()
                || !lessonDate.toString().equals(members.getAsOfDate())
                || members.getSemesterId() != semesterId) {
            throw new AcademicServiceUnavailableException(
                    "Academic returned a missing or mismatched historical roster echo");
        }
        java.util.Set<Long> ids = new java.util.HashSet<>();
        for (StudentInfo student : members.getStudentsList()) {
            if (student.getUserId() <= 0 || !ids.add(student.getUserId())) {
                throw new AcademicServiceUnavailableException(
                        "Academic returned duplicate or invalid historical student identity");
            }
        }
    }

    private static LocalDate parseLessonDate(String value) {
        try {
            return LocalDate.parse(value);
        } catch (RuntimeException ex) {
            throw new BadRequestException("Дата пары недоступна");
        }
    }

    private static String editBlockedReason(JournalLessonPolicy.Timing timing, java.time.Instant now) {
        if ("CANCELLED".equals(timing.status())) return "Пара отменена";
        if (!timing.hasStarted(now)) return "Пара ещё не началась";
        return null;
    }

    // -------------------------------------------------------------------------
    // RPRT-02: Journal grid
    // -------------------------------------------------------------------------

    /**
     * Returns the journal grid: columns = sorted unique dates, rows = students with cells.
     *
     * Authorization: headman (own group) or teacher teaching this subject+group.
     */
    public JournalResponse getJournal(Long groupId, Long subjectId, LocalDate dateFrom, LocalDate dateTo) {
        authorizeHeadmanOrTeacher(groupId, subjectId);

        GroupMembersResponse members = academicGrpcClient.getGroupMembers(groupId);

        List<AttendanceRecord> records =
                attendanceReadPort.findByGroupAndSubject(groupId, subjectId, dateFrom, dateTo);

        List<String> dates = records.stream()
                .map(r -> r.lessonDate().toString())
                .distinct()
                .sorted()
                .toList();

        List<JournalStudentRow> studentRows = members.getStudentsList().stream()
                .map(student -> {
                    Long uid = student.getUserId();
                    String displayName = student.getDisplayName();
                    List<JournalCell> cells = records.stream()
                            .filter(r -> r.userId().equals(uid))
                            .map(r -> new JournalCell(
                                    r.lessonId(),                       // Phase 55 D-01: required by headman cell-click marking
                                    r.lessonDate().toString(),
                                    r.lessonNumber(),
                                    r.status().name().toLowerCase(),
                                    statusSymbol(r.status()),
                                    r.excuseReason()
                            ))
                            .toList();
                    return new JournalStudentRow(uid, displayName, cells);
                })
                .toList();

        return new JournalResponse(groupId, subjectId, dates, studentRows);
    }

    // -------------------------------------------------------------------------
    // RPRT-03: Student attendance stats
    // -------------------------------------------------------------------------

    /**
     * Returns per-subject and overall attendance stats for the authenticated student.
     * CANCELLED lessons are excluded (D-10).
     * Subject names are resolved via gRPC GetSubjectsByIds (D-13).
     */
    public StudentStatsResponse getStudentStats() {
        Long userId = requestContext.getUserId();
        Long semesterId = semesterCacheService.getActiveSemesterId();
        if (semesterId == null) {
            throw new IllegalStateException("Active semester not available");
        }

        List<AttendanceRecord> allRecords = filterExistingLessons(
                attendanceReadPort.findByUserId(userId, semesterId));

        // M05 D9 / P2-10/5: single-pass aggregation. Один проход для per-subject
        // counters вместо groupingBy + 3× stream.filter.count на каждый subject.
        // D-10: CANCELLED исключены.
        Map<Long, int[]> bySubject = new java.util.HashMap<>();
        for (AttendanceRecord r : allRecords) {
            AttendanceStatus s = r.status();
            if (s == AttendanceStatus.CANCELLED) continue;
            int[] c = bySubject.computeIfAbsent(r.subjectId(), k -> new int[4]);
            // Индексы: [0]=total, [1]=attended, [2]=absent, [3]=excused
            c[0]++;
            if (s == AttendanceStatus.PRESENT
                    || s == AttendanceStatus.EXCUSED
                    || s == AttendanceStatus.FREE_ATTENDANCE) {
                c[1]++;
            }
            if (s == AttendanceStatus.ABSENT) {
                c[2]++;
            }
            if (s == AttendanceStatus.EXCUSED || s == AttendanceStatus.FREE_ATTENDANCE) {
                c[3]++;
            }
        }

        if (bySubject.isEmpty()) {
            OverallStats empty = new OverallStats(0, 0, 0, 0, 0.0);
            return new StudentStatsResponse(List.of(), empty);
        }

        // D-13: Resolve subject names and types via gRPC batch call
        List<Long> subjectIds = new ArrayList<>(bySubject.keySet());
        Map<Long, AcademicGrpcClient.SubjectDetails> subjects = academicGrpcClient.getSubjectDetailsByIds(subjectIds);

        List<SubjectStats> subjectStatsList = new ArrayList<>(bySubject.size());
        int totalOverall = 0, attendedOverall = 0, absentOverall = 0, excusedOverall = 0;
        for (Map.Entry<Long, int[]> e : bySubject.entrySet()) {
            int[] c = e.getValue();
            int total = c[0], attended = c[1], absent = c[2], excused = c[3];
            double pct = total == 0 ? 0.0 : (attended * 100.0) / total;
            AcademicGrpcClient.SubjectDetails subject = subjects.get(e.getKey());
            subjectStatsList.add(new SubjectStats(
                    e.getKey(),
                    subject == null ? "Unknown" : subject.name(),
                    subject == null ? "" : subject.type(),
                    total, attended, absent, excused, pct));
            totalOverall += total;
            attendedOverall += attended;
            absentOverall += absent;
            excusedOverall += excused;
        }

        double percentageOverall = totalOverall == 0 ? 0.0 : (attendedOverall * 100.0) / totalOverall;
        OverallStats overallStats = new OverallStats(
                totalOverall, attendedOverall, absentOverall, excusedOverall, percentageOverall);

        return new StudentStatsResponse(subjectStatsList, overallStats);
    }

    // -------------------------------------------------------------------------
    // v9.0: Student dashboard (overall + donut breakdown + weekly + top missed)
    // -------------------------------------------------------------------------

    /**
     * Single-call payload for the PWA/Mini-App home dashboard.
     * CANCELLED lessons are excluded from overall/weekly/topMissed (D-10),
     * but still reported in {@link StatusBreakdown} for transparency.
     * "Forgot to check-in" is a sub-slice of PRESENT with source=LATE_CHECKIN.
     */
    public StudentDashboardResponse getStudentDashboard(int topLimit) {
        Long userId = requestContext.getUserId();
        Long semesterId = semesterCacheService.getActiveSemesterId();
        if (semesterId == null) {
            throw new IllegalStateException("Active semester not available");
        }

        List<AttendanceRecord> records = filterExistingLessons(
                attendanceReadPort.findByUserId(userId, semesterId));

        StatusBreakdown breakdown = buildBreakdown(records);

        List<AttendanceRecord> counted = records.stream()
                .filter(r -> r.status() != AttendanceStatus.CANCELLED)
                .toList();

        OverallStats overall = buildOverall(counted);

        LocalDate semesterStart = resolveSemesterStart();
        List<WeeklyStat> weekly = buildWeekly(counted, semesterStart);

        List<TopMissedSubject> topMissed = buildTopMissed(counted, topLimit);

        return new StudentDashboardResponse(overall, breakdown, weekly, topMissed);
    }

    private StatusBreakdown buildBreakdown(List<AttendanceRecord> records) {
        int present = 0, absent = 0, excused = 0, freeAtt = 0, forgot = 0, cancelled = 0;
        for (AttendanceRecord r : records) {
            switch (r.status()) {
                case PRESENT -> {
                    present++;
                    if (r.source() == AttendanceSource.LATE_CHECKIN) forgot++;
                }
                case ABSENT -> absent++;
                case EXCUSED -> excused++;
                case FREE_ATTENDANCE -> freeAtt++;
                case CANCELLED -> cancelled++;
            }
        }
        return new StatusBreakdown(present, absent, excused, freeAtt, forgot, cancelled);
    }

    private OverallStats buildOverall(List<AttendanceRecord> counted) {
        // M05 D9 / P2-10/5: single-pass accumulators (O(N) vs O(3N)).
        int total = counted.size();
        int attended = 0, absent = 0, excused = 0;
        for (AttendanceRecord r : counted) {
            AttendanceStatus s = r.status();
            if (s == AttendanceStatus.PRESENT
                    || s == AttendanceStatus.EXCUSED
                    || s == AttendanceStatus.FREE_ATTENDANCE) {
                attended++;
            }
            if (s == AttendanceStatus.ABSENT) {
                absent++;
            }
            if (s == AttendanceStatus.EXCUSED || s == AttendanceStatus.FREE_ATTENDANCE) {
                excused++;
            }
        }
        double percentage = total == 0 ? 0.0 : (attended * 100.0) / total;
        return new OverallStats(total, attended, absent, excused, percentage);
    }

    private LocalDate resolveSemesterStart() {
        try {
            String from = academicGrpcClient.getActiveSemester().getDateFrom();
            return LocalDate.parse(from);
        } catch (Exception e) {
            // Fallback: first attendance date if semester info is unavailable.
            return LocalDate.now().withDayOfYear(1);
        }
    }

    private List<WeeklyStat> buildWeekly(List<AttendanceRecord> counted, LocalDate semesterStart) {
        // M05 D9 / P2-10/5: single-pass accumulators per week. O(N) вместо
        // O(N + K × 3N) где K — количество недель. Храним int[] counter'ы
        // и representative lessonDate для ISO-week resolve на выходе.
        TreeMap<Integer, int[]> byWeek = new TreeMap<>();
        Map<Integer, LocalDate> sampleDates = new java.util.HashMap<>();
        for (AttendanceRecord r : counted) {
            int weekOfSemester = weekNumberFrom(semesterStart, r.lessonDate());
            int[] c = byWeek.computeIfAbsent(weekOfSemester, k -> new int[4]);
            // Индексы: [0]=total, [1]=attended, [2]=absent, [3]=excused
            c[0]++;
            AttendanceStatus s = r.status();
            if (s == AttendanceStatus.PRESENT
                    || s == AttendanceStatus.EXCUSED
                    || s == AttendanceStatus.FREE_ATTENDANCE) {
                c[1]++;
            }
            if (s == AttendanceStatus.ABSENT) {
                c[2]++;
            }
            if (s == AttendanceStatus.EXCUSED || s == AttendanceStatus.FREE_ATTENDANCE) {
                c[3]++;
            }
            sampleDates.putIfAbsent(weekOfSemester, r.lessonDate());
        }
        List<WeeklyStat> result = new ArrayList<>(byWeek.size());
        for (Map.Entry<Integer, int[]> e : byWeek.entrySet()) {
            int weekNum = e.getKey();
            int[] c = e.getValue();
            int total = c[0], attended = c[1], absent = c[2], excused = c[3];
            int isoWeek = sampleDates.get(weekNum)
                    .get(java.time.temporal.WeekFields.ISO.weekOfWeekBasedYear());
            double pct = total == 0 ? 0.0 : (attended * 100.0) / total;
            result.add(new WeeklyStat(weekNum, isoWeek, "Н" + weekNum,
                    total, attended, absent, excused, pct));
        }
        return result;
    }

    private static int weekNumberFrom(LocalDate semesterStart, LocalDate date) {
        // Align both dates to the Monday of their week so Mon..Sun always lands
        // in the same bucket, regardless of which weekday the semester starts on.
        LocalDate startMon = semesterStart.with(java.time.DayOfWeek.MONDAY);
        LocalDate dateMon = date.with(java.time.DayOfWeek.MONDAY);
        long days = ChronoUnit.DAYS.between(startMon, dateMon);
        return (int) (days / 7L) + 1;
    }

    private List<TopMissedSubject> buildTopMissed(List<AttendanceRecord> counted, int limit) {
        Map<Long, long[]> agg = counted.stream()
                .collect(Collectors.toMap(
                        AttendanceRecord::subjectId,
                        r -> new long[]{1L, r.status() == AttendanceStatus.ABSENT ? 1L : 0L},
                        (a, b) -> new long[]{a[0] + b[0], a[1] + b[1]}));
        if (agg.isEmpty()) return List.of();
        Map<Long, AcademicGrpcClient.SubjectDetails> subjects =
                academicGrpcClient.getSubjectDetailsByIds(new ArrayList<>(agg.keySet()));
        return agg.entrySet().stream()
                .filter(e -> e.getValue()[1] > 0)
                .sorted(Comparator.comparingLong((Map.Entry<Long, long[]> e) -> e.getValue()[1]).reversed())
                .limit(Math.max(1, limit))
                .map(e -> {
                    AcademicGrpcClient.SubjectDetails subject = subjects.get(e.getKey());
                    return new TopMissedSubject(
                            e.getKey(),
                            subject == null ? "Unknown" : subject.name(),
                            subject == null ? "" : subject.type(),
                            (int) e.getValue()[1],
                            (int) e.getValue()[0]);
                })
                .toList();
    }

    // -------------------------------------------------------------------------
    // RPRT-04: Student attendance records
    // -------------------------------------------------------------------------

    /**
     * Returns attendance records for the authenticated student, optionally filtered by subjectId.
     */
    public List<AttendanceRecordEntry> getStudentRecords(Long subjectId) {
        Long userId = requestContext.getUserId();
        Long semesterId = semesterCacheService.getActiveSemesterId();

        List<AttendanceRecord> records = filterExistingLessons(
                attendanceReadPort.findByUserId(userId, semesterId));

        return records.stream()
                .filter(r -> subjectId == null || r.subjectId().equals(subjectId))
                .map(r -> new AttendanceRecordEntry(
                        r.lessonId(),
                        r.subjectId(),
                        r.lessonDate().toString(),
                        r.lessonNumber(),
                        r.status().name().toLowerCase(),
                        statusSymbol(r.status()),
                        r.source().name().toLowerCase(),
                        r.excuseReason()
                ))
                .toList();
    }

    /**
     * Builds the teacher's semester aggregate from concrete schedule lessons.
     * The BFF discovers the complete group-bounded lesson set, while this
     * service remains the final authority: every lesson is re-authorized against
     * the signed teacher identity and current active group authority.
     */
    public TeacherStatsResult getTeacherStats(TeacherStatsQuery query, long teacherId) {
        validateTeacherStatsQuery(query, teacherId);
        List<Long> requestedLessonIds = query.lessonIds() == null
                ? List.of()
                : query.lessonIds().stream().distinct().toList();
        if (requestedLessonIds.size() > 5_000
                || query.lessonIds() != null && (query.lessonIds().stream().anyMatch(id -> id == null || id <= 0)
                || requestedLessonIds.size() != query.lessonIds().size())) {
            throw new BadRequestException("lesson_ids must contain unique positive ids (at most 5000)");
        }

        TeacherSubjectsResponse teacherAuthority = academicGrpcClient.getCurrentTeacherSubjects(teacherId);
        if (teacherAuthority == null) {
            throw new AccessDeniedException("Teacher assignment authority is unavailable");
        }
        Set<Long> activeGroupIds = teacherAuthority.getSubjectsList().stream()
                .filter(info -> info != null && info.getGroupId() > 0)
                .map(TeacherSubjectInfo::getGroupId)
                .collect(Collectors.toSet());
        if (activeGroupIds.isEmpty()) {
            throw new AccessDeniedException("Teacher is not active in a group");
        }
        if (query.scope() == TeacherStatsScope.STUDENTS && !activeGroupIds.contains(query.groupId())) {
            throw new AccessDeniedException("Teacher is not active in the selected group");
        }

        List<LessonInfo> authoritativeLessons = requestedLessonIds.isEmpty()
                ? List.of()
                : scheduleGrpcClient.getLessonsByIds(requestedLessonIds);
        if (authoritativeLessons == null || authoritativeLessons.size() != requestedLessonIds.size()) {
            throw new ScheduleServiceUnavailableException("Schedule returned an incomplete lesson authority response");
        }
        Map<Long, LessonInfo> lessonById = new java.util.HashMap<>();
        for (LessonInfo lesson : authoritativeLessons) {
            if (lesson == null || lesson.getLessonId() <= 0
                    || lessonById.put(lesson.getLessonId(), lesson) != null) {
                throw new ScheduleServiceUnavailableException("Schedule returned malformed lesson authority");
            }
        }
        long semesterId = query.semesterId();
        if (!authoritativeLessons.isEmpty()) {
            long responseSemesterId = authoritativeLessons.get(0).getSemesterId();
            if (responseSemesterId <= 0 || responseSemesterId != query.semesterId()
                    || authoritativeLessons.stream().anyMatch(lesson -> lesson.getSemesterId() != responseSemesterId)) {
                throw new BadRequestException("Stats lessons must belong to one semester");
            }
            semesterId = responseSemesterId;
        }
        List<TeacherStatsSubjectOption> subjectOptions = buildSubjectOptions(
                authoritativeLessons, teacherAuthority);
        List<LessonInfo> lessons = new ArrayList<>(requestedLessonIds.size());
        LocalDate periodFrom = null;
        LocalDate periodTo = null;
        java.util.Set<String> requestedTypes = normalizedTypes(query.lessonTypes());
        for (Long lessonId : requestedLessonIds) {
            LessonInfo lesson = lessonById.get(lessonId);
            if (lesson == null) {
                throw new ScheduleServiceUnavailableException("Schedule returned no lesson authority response");
            }
            authorizeTeacherStatsLesson(lesson, teacherAuthority);
            LocalDate date = parseLessonDate(lesson.getDate());
            if (query.scope() == TeacherStatsScope.STUDENTS
                    && lesson.getGroupId() != query.groupId()) {
                throw new AccessDeniedException("Teacher stats lesson is outside the selected context");
            }
            // The request may carry the complete selected-group batch so the
            // response can expose every readable subject option. Only the
            // chosen subject contributes student rows.
            if (query.scope() == TeacherStatsScope.STUDENTS
                    && lesson.getSubjectId() != query.subjectId()) {
                continue;
            }
            if (!requestedTypes.isEmpty()
                    && !requestedTypes.contains(normalize(lesson.getLessonType()))) {
                continue;
            }
            if (!isCompleted(lesson) || isCancelled(lesson)) {
                continue;
            }
            lessons.add(lesson);
            periodFrom = periodFrom == null || date.isBefore(periodFrom) ? date : periodFrom;
            periodTo = periodTo == null || date.isAfter(periodTo) ? date : periodTo;
        }

        Map<Long, StudentStatsAccumulator> students = new java.util.LinkedHashMap<>();
        Map<Long, GroupStatsAccumulator> groups = new java.util.LinkedHashMap<>();
        if (query.scope() == TeacherStatsScope.GROUPS) {
            activeGroupIds.stream().sorted().forEach(groupId ->
                    groups.put(groupId, new GroupStatsAccumulator(groupId)));
        } else {
            groups.put(query.groupId(), new GroupStatsAccumulator(query.groupId()));
        }
        List<AttendanceRecord> attendanceRecords = lessons.isEmpty()
                ? List.of()
                : attendanceReadPort.findByLessonIds(
                        lessons.stream().map(LessonInfo::getLessonId).toList());
        Map<Long, List<AttendanceRecord>> recordsByLesson = attendanceRecords.stream()
                .filter(record -> record != null && record.lessonId() != null)
                .collect(Collectors.groupingBy(AttendanceRecord::lessonId));
        Map<String, GroupMembersResponse> rosters = new java.util.HashMap<>();
        for (LessonInfo lesson : lessons) {
            GroupStatsAccumulator group = groups.computeIfAbsent(lesson.getGroupId(),
                    ignored -> new GroupStatsAccumulator(lesson.getGroupId()));
            group.lessonsCount++;
            GroupMembersResponse roster = rosters.computeIfAbsent(
                    lesson.getGroupId() + ":" + lesson.getSemesterId() + ":" + lesson.getDate(),
                    ignored -> historicalMembersFor(lesson));
            Map<Long, AttendanceRecord> records = recordsByLesson.getOrDefault(lesson.getLessonId(), List.of()).stream()
                    .filter(record -> record != null && record.userId() != null)
                    .collect(Collectors.toMap(AttendanceRecord::userId, value -> value,
                            (first, ignored) -> first));
            for (StudentInfo member : roster.getStudentsList()) {
                if (member.getUserId() <= 0) {
                    throw new AcademicServiceUnavailableException("Academic returned an invalid student identity");
                }
                StudentStatsAccumulator student = students.computeIfAbsent(member.getUserId(),
                        ignored -> new StudentStatsAccumulator(member.getUserId(), member.getDisplayName()));
                AttendanceRecord record = records.get(member.getUserId());
                // Closed lessons normally have an AUTO_SCHEDULER ABSENT record.
                // Treat a missing historical mark as ABSENT so the denominator is
                // the passed lesson roster, never the number of stored documents.
                AttendanceStatus status = record == null || record.status() == null
                        ? AttendanceStatus.ABSENT : record.status();
                student.counter.add(status);
                group.counter.add(status);
            }
        }

        Map<Long, String> groupNames = new java.util.HashMap<>();
        for (Long groupId : groups.keySet()) {
            GroupResponse group = academicGrpcClient.getGroup(groupId);
            String groupName = group == null ? null : group.getName();
            if (group == null || group.getId() != groupId || groupName == null || groupName.isBlank()) {
                throw new AcademicServiceUnavailableException(
                        "Academic returned an incomplete group response for " + groupId);
            }
            groupNames.put(groupId, groupName);
        }

        List<TeacherStudentStats> studentRows = students.values().stream()
                .map(StudentStatsAccumulator::toResult)
                .filter(row -> matchesFilters(row, query.filters()))
                .sorted((left, right) -> compareStudents(left, right, query.sorts()))
                .toList();
        List<TeacherGroupStats> groupRows = groups.values().stream()
                .map(group -> group.toResult(groupNames.getOrDefault(group.groupId, "")))
                .filter(row -> matchesFilters(row, query.filters()))
                .sorted((left, right) -> compareGroups(left, right, query.sorts()))
                .toList();
        return new TeacherStatsResult(query.scope(), semesterId, periodFrom, periodTo, lessons.size(),
                studentRows, groupRows, subjectOptions, clock.instant());
    }

    private List<TeacherStatsSubjectOption> buildSubjectOptions(List<LessonInfo> lessons,
                                                                 TeacherSubjectsResponse response) {
        Set<Long> activeGroupIds = response.getSubjectsList().stream()
                .filter(info -> info != null && info.getGroupId() > 0)
                .map(TeacherSubjectInfo::getGroupId)
                .collect(Collectors.toSet());
        Map<TeacherStatsSubjectKey, Set<String>> lessonTypes = new java.util.HashMap<>();
        for (LessonInfo lesson : lessons) {
            if (lesson == null || !activeGroupIds.contains(lesson.getGroupId())
                    || lesson.getSubjectId() <= 0 || lesson.getLessonType() == null
                    || lesson.getLessonType().isBlank()) {
                continue;
            }
            lessonTypes.computeIfAbsent(new TeacherStatsSubjectKey(lesson.getGroupId(), lesson.getSubjectId()),
                    ignored -> new java.util.TreeSet<>()).add(normalize(lesson.getLessonType()));
        }
        if (lessonTypes.isEmpty()) return List.of();
        List<Long> subjectIds = lessonTypes.keySet().stream()
                .map(TeacherStatsSubjectKey::subjectId)
                .distinct()
                .toList();
        Map<Long, AcademicGrpcClient.SubjectDetails> details =
                academicGrpcClient.getSubjectDetailsByIds(subjectIds);
        List<TeacherStatsSubjectOption> result = new ArrayList<>();
        for (Map.Entry<TeacherStatsSubjectKey, Set<String>> entry : lessonTypes.entrySet()) {
            AcademicGrpcClient.SubjectDetails subject = details.get(entry.getKey().subjectId());
            if (subject == null || subject.name() == null || subject.name().isBlank()) {
                throw new AcademicServiceUnavailableException(
                        "Academic returned no subject for " + entry.getKey().subjectId());
            }
            result.add(new TeacherStatsSubjectOption(
                    entry.getKey().groupId(), entry.getKey().subjectId(), subject.name(),
                    List.copyOf(entry.getValue())));
        }
        result.sort(java.util.Comparator.comparingLong(TeacherStatsSubjectOption::groupId)
                .thenComparing(TeacherStatsSubjectOption::subjectName, String.CASE_INSENSITIVE_ORDER)
                .thenComparingLong(TeacherStatsSubjectOption::subjectId));
        return List.copyOf(result);
    }

    private void authorizeTeacherStatsLesson(LessonInfo lesson, TeacherSubjectsResponse response) {
        if (lesson == null || lesson.getLessonId() <= 0 || lesson.getGroupId() <= 0
                || lesson.getSubjectId() <= 0 || lesson.getAssignmentId() <= 0
                || lesson.getSemesterId() <= 0 || lesson.getTeacherId() <= 0
                || lesson.getLessonType() == null || lesson.getLessonType().isBlank()) {
            throw new AccessDeniedException("Teacher cannot read this lesson");
        }
        if (response == null || response.getSubjectsList().stream()
                .noneMatch(info -> info != null && info.getGroupId() == lesson.getGroupId())) {
            throw new AccessDeniedException("Teacher is not active in this group");
        }
    }

    private GroupMembersResponse historicalMembersFor(LessonInfo lesson) {
        if (lesson.getGroupId() <= 0 || lesson.getSemesterId() <= 0) {
            throw new AcademicServiceUnavailableException(
                    "Schedule returned a lesson without positive group/semester");
        }
        LocalDate lessonDate = parseLessonDate(lesson.getDate());
        GroupMembersResponse response = academicGrpcClient.getGroupMembers(
                lesson.getGroupId(), lessonDate, lesson.getSemesterId());
        validateHistoricalRoster(response, lessonDate, lesson.getSemesterId());
        return response;
    }

    private static void validateTeacherStatsQuery(TeacherStatsQuery query, long teacherId) {
        if (query == null || query.scope() == null || teacherId <= 0) {
            throw new BadRequestException("Invalid teacher stats query");
        }
        if (query.scope() == TeacherStatsScope.STUDENTS
                && (query.groupId() <= 0 || query.subjectId() <= 0)) {
            throw new BadRequestException("Student stats require group and subject");
        }
        if (query.scope() == TeacherStatsScope.GROUPS
                && (query.groupId() != 0 || query.subjectId() != 0)) {
            throw new BadRequestException("Group stats cannot select a group or subject");
        }
        if (query.semesterId() <= 0) {
            throw new BadRequestException("Stats require a positive semester");
        }
        validateFilters(query.filters(), query.scope());
        if (query.sorts() != null) {
            for (TeacherStatsSort sort : query.sorts()) {
                if (sort == null || !allowedColumn(sort.column())
                        || query.scope() == TeacherStatsScope.STUDENTS
                        && (sort.column().equals("groupName") || sort.column().equals("lessonsCount"))
                        || query.scope() == TeacherStatsScope.GROUPS
                        && sort.column().equals("displayName")) {
                    throw new BadRequestException("Unknown teacher stats sort column");
                }
            }
        }
    }

    private static void validateFilters(List<TeacherStatsFilter> filters, TeacherStatsScope scope) {
        if (filters == null) return;
        for (TeacherStatsFilter filter : filters) {
            if (filter == null || !allowedColumn(filter.column())) {
                throw new BadRequestException("Unknown teacher stats filter column");
            }
            if (scope == TeacherStatsScope.STUDENTS
                    && (filter.column().equals("groupName") || filter.column().equals("lessonsCount"))) {
                throw new BadRequestException("Filter column is not available for student stats");
            }
            if (scope == TeacherStatsScope.GROUPS && filter.column().equals("displayName")) {
                throw new BadRequestException("Filter column is not available for group stats");
            }
            if (filter.contains() != null && !filter.contains().isBlank()
                    && !(filter.column().equals("displayName") || filter.column().equals("groupName"))) {
                throw new BadRequestException("Text filter is not available for this column");
            }
            if (filter.column().equals("lessonsCount")
                    && (filter.minPercent() != null || filter.maxPercent() != null)) {
                throw new BadRequestException("Percentage filter is not available for lessons count");
            }
            if ((filter.column().equals("displayName") || filter.column().equals("groupName"))
                    && (filter.minPercent() != null || filter.maxPercent() != null)) {
                throw new BadRequestException("Percentage filter is not available for names");
            }
            if (!filter.column().equals("lessonsCount")
                    && (filter.minValue() != null || filter.maxValue() != null)) {
                throw new BadRequestException("Value filter is available only for lessons count");
            }
            if (filter.minValue() != null && filter.minValue() < 0
                    || filter.maxValue() != null && filter.maxValue() < 0) {
                throw new BadRequestException("Teacher stats value filter is out of range");
            }
            if (filter.minValue() != null && filter.maxValue() != null
                    && filter.minValue() > filter.maxValue()) {
                throw new BadRequestException("Teacher stats value filter is out of range");
            }
            if (filter.minPercent() != null && (filter.minPercent() < 0 || filter.minPercent() > 100)
                    || filter.maxPercent() != null && (filter.maxPercent() < 0 || filter.maxPercent() > 100)
                    || filter.minPercent() != null && filter.maxPercent() != null
                    && filter.minPercent() > filter.maxPercent()) {
                throw new BadRequestException("Teacher stats percentage filter is out of range");
            }
        }
    }

    private static boolean allowedColumn(String column) {
        return Set.of("displayName", "groupName", "present", "presentOrExcused", "excused", "absent", "lessonsCount")
                .contains(column);
    }

    private static java.util.Set<String> normalizedTypes(List<String> types) {
        if (types == null) return java.util.Set.of();
        return types.stream().filter(Objects::nonNull).map(ReportService::normalize)
                .filter(value -> !value.isBlank()).collect(Collectors.toUnmodifiableSet());
    }

    private static boolean isCompleted(LessonInfo lesson) {
        // Schedule transitions a lesson to CLOSED only after its concrete
        // Moscow end time (+ the configured grace period). A calendar-date
        // comparison would include today's unfinished lessons.
        return lesson != null && "closed".equalsIgnoreCase(lesson.getStatus());
    }

    private static boolean isCancelled(LessonInfo lesson) {
        return "cancelled".equalsIgnoreCase(lesson.getStatus());
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(java.util.Locale.ROOT);
    }

    private static boolean matchesFilters(TeacherStudentStats row, List<TeacherStatsFilter> filters) {
        if (filters == null) return true;
        for (TeacherStatsFilter filter : filters) {
            if (!matchesText(row.displayName(), filter) || !matchesMetric(row.metric(filter.column()), filter)) {
                return false;
            }
        }
        return true;
    }

    private static boolean matchesFilters(TeacherGroupStats row, List<TeacherStatsFilter> filters) {
        if (filters == null) return true;
        for (TeacherStatsFilter filter : filters) {
            if (!matchesText(row.groupName(), filter)
                    || !matchesMetric(row.metric(filter.column()), filter)
                    || !matchesValue(row.lessonsCount(), filter)) {
                return false;
            }
        }
        return true;
    }

    private static boolean matchesText(String value, TeacherStatsFilter filter) {
        if (filter.contains() == null || filter.contains().isBlank()) return true;
        if (!(filter.column().equals("displayName") || filter.column().equals("groupName"))) return true;
        return value.toLowerCase(java.util.Locale.ROOT)
                .contains(filter.contains().trim().toLowerCase(java.util.Locale.ROOT));
    }

    private static boolean matchesMetric(TeacherMetric metric, TeacherStatsFilter filter) {
        if (metric == null) return true;
        return (filter.minPercent() == null || metric.percent() >= filter.minPercent())
                && (filter.maxPercent() == null || metric.percent() <= filter.maxPercent());
    }

    private static boolean matchesValue(int value, TeacherStatsFilter filter) {
        if (!filter.column().equals("lessonsCount")) return true;
        return (filter.minValue() == null || value >= filter.minValue())
                && (filter.maxValue() == null || value <= filter.maxValue());
    }

    private static int compareStudents(TeacherStudentStats left, TeacherStudentStats right,
                                       List<TeacherStatsSort> sorts) {
        if (sorts != null) {
            for (TeacherStatsSort sort : sorts) {
                int result = compareColumn(left, right, sort.column());
                if (result != 0) return sort.descending() ? -result : result;
            }
        }
        int result = compareColumn(left, right, "present");
        return result != 0 ? result : Long.compare(left.studentId(), right.studentId());
    }

    private static int compareGroups(TeacherGroupStats left, TeacherGroupStats right,
                                     List<TeacherStatsSort> sorts) {
        if (sorts != null) {
            for (TeacherStatsSort sort : sorts) {
                int result = compareColumn(left, right, sort.column());
                if (result != 0) return sort.descending() ? -result : result;
            }
        }
        int result = compareColumn(left, right, "present");
        return result != 0 ? result : Long.compare(left.groupId(), right.groupId());
    }

    private static int compareColumn(TeacherStudentStats left, TeacherStudentStats right, String column) {
        return switch (column) {
            case "displayName" -> left.displayName().compareToIgnoreCase(right.displayName());
            case "present" -> Double.compare(left.present().percent(), right.present().percent());
            case "presentOrExcused" -> Double.compare(left.presentOrExcused().percent(), right.presentOrExcused().percent());
            case "excused" -> Double.compare(left.excused().percent(), right.excused().percent());
            case "absent" -> Double.compare(left.absent().percent(), right.absent().percent());
            default -> 0;
        };
    }

    private static int compareColumn(TeacherGroupStats left, TeacherGroupStats right, String column) {
        return switch (column) {
            case "groupName" -> left.groupName().compareToIgnoreCase(right.groupName());
            case "lessonsCount" -> Integer.compare(left.lessonsCount(), right.lessonsCount());
            case "present" -> Double.compare(left.present().percent(), right.present().percent());
            case "presentOrExcused" -> Double.compare(left.presentOrExcused().percent(), right.presentOrExcused().percent());
            case "excused" -> Double.compare(left.excused().percent(), right.excused().percent());
            case "absent" -> Double.compare(left.absent().percent(), right.absent().percent());
            default -> 0;
        };
    }

    public enum TeacherStatsScope {
        STUDENTS,
        GROUPS
    }

    public record TeacherStatsQuery(
            List<Long> lessonIds,
            TeacherStatsScope scope,
            long groupId,
            long subjectId,
            List<String> lessonTypes,
            List<TeacherStatsSort> sorts,
            List<TeacherStatsFilter> filters,
            long semesterId
    ) {
    }

    public record TeacherStatsSort(String column, boolean descending) {
    }

    public record TeacherStatsFilter(
                                    String column,
                                    String contains,
                                    Double minPercent,
            Double maxPercent,
            Integer minValue,
            Integer maxValue
    ) {
    }

    public record TeacherMetric(int numerator, int denominator, double percent) {
        private static TeacherMetric of(int numerator, int denominator) {
            return new TeacherMetric(numerator, denominator,
                    denominator == 0 ? 0.0 : numerator * 100.0 / denominator);
        }
    }

    public record TeacherStudentStats(
            long studentId,
            String displayName,
            TeacherMetric present,
            TeacherMetric presentOrExcused,
            TeacherMetric excused,
            TeacherMetric absent
    ) {
        private TeacherMetric metric(String column) {
            return switch (column) {
                case "present" -> present;
                case "presentOrExcused" -> presentOrExcused;
                case "excused" -> excused;
                case "absent" -> absent;
                default -> null;
            };
        }
    }

    public record TeacherGroupStats(
            long groupId,
            String groupName,
            int lessonsCount,
            TeacherMetric present,
            TeacherMetric presentOrExcused,
            TeacherMetric excused,
            TeacherMetric absent
    ) {
        private TeacherMetric metric(String column) {
            return switch (column) {
                case "present" -> present;
                case "presentOrExcused" -> presentOrExcused;
                case "excused" -> excused;
                case "absent" -> absent;
                default -> null;
            };
        }
    }

    public record TeacherStatsResult(
            TeacherStatsScope scope,
            long semesterId,
            LocalDate periodFrom,
            LocalDate periodTo,
            int lessonsCount,
            List<TeacherStudentStats> students,
            List<TeacherGroupStats> groups,
            List<TeacherStatsSubjectOption> subjectOptions,
            java.time.Instant serverNow
    ) {
    }

    public record TeacherStatsSubjectOption(
            long groupId,
            long subjectId,
            String subjectName,
            List<String> lessonTypes
    ) {
    }

    private record TeacherStatsSubjectKey(long groupId, long subjectId) {
    }

    private static final class StatsCounter {
        private int denominator;
        private int present;
        private int presentOrExcused;
        private int excused;
        private int absent;

        private void add(AttendanceStatus status) {
            if (status == AttendanceStatus.CANCELLED) return;
            denominator++;
            switch (status) {
                case PRESENT -> {
                    present++;
                    presentOrExcused++;
                }
                case EXCUSED, FREE_ATTENDANCE -> {
                    presentOrExcused++;
                    excused++;
                }
                case ABSENT -> absent++;
                case CANCELLED -> { /* handled above */ }
            }
        }

        private TeacherMetric presentMetric() {
            return TeacherMetric.of(present, denominator);
        }

        private TeacherMetric presentOrExcusedMetric() {
            return TeacherMetric.of(presentOrExcused, denominator);
        }

        private TeacherMetric excusedMetric() {
            return TeacherMetric.of(excused, denominator);
        }

        private TeacherMetric absentMetric() {
            return TeacherMetric.of(absent, denominator);
        }
    }

    private static final class StudentStatsAccumulator {
        private final long studentId;
        private final String displayName;
        private final StatsCounter counter = new StatsCounter();

        private StudentStatsAccumulator(long studentId, String displayName) {
            this.studentId = studentId;
            this.displayName = displayName == null ? "" : displayName;
        }

        private TeacherStudentStats toResult() {
            return new TeacherStudentStats(studentId, displayName, counter.presentMetric(),
                    counter.presentOrExcusedMetric(), counter.excusedMetric(), counter.absentMetric());
        }
    }

    private static final class GroupStatsAccumulator {
        private final long groupId;
        private final StatsCounter counter = new StatsCounter();
        private int lessonsCount;

        private GroupStatsAccumulator(long groupId) {
            this.groupId = groupId;
        }

        private TeacherGroupStats toResult(String groupName) {
            return new TeacherGroupStats(groupId, groupName == null ? "" : groupName, lessonsCount,
                    counter.presentMetric(), counter.presentOrExcusedMetric(),
                    counter.excusedMetric(), counter.absentMetric());
        }
    }

    // -------------------------------------------------------------------------
    // Authorization helper
    // -------------------------------------------------------------------------

    /**
     * Authorizes the current user to access group+subject reports.
     *
     * Rules (D-05):
     * - STUDENT: must be headman AND own group must match groupId
     * - TEACHER: must teach this subjectId for this groupId in the active semester
     * - ADMIN: access denied (admins use admin endpoints, not attendance reports)
     */
    /**
     * Drops attendance records whose lesson_id no longer exists in schedule-service.
     * Defense-in-depth: even if the {@code lesson.deleted} cascade event was missed
     * (broker downtime, consumer lag), stale docs won't surface in reports.
     * Empty input short-circuits with no gRPC call.
     */
    private List<AttendanceRecord> filterExistingLessons(List<AttendanceRecord> records) {
        if (records == null || records.isEmpty()) return List.of();
        List<Long> ids = records.stream()
                .map(AttendanceRecord::lessonId)
                .peek(id -> {
                    if (id == null || id <= 0) {
                        throw new ScheduleServiceUnavailableException(
                                "Attendance returned a malformed lesson identity");
                    }
                })
                .distinct()
                .toList();
        List<LessonInfo> lessons = scheduleGrpcClient.getLessonsByIds(ids);
        if (lessons == null) {
            throw new ScheduleServiceUnavailableException(
                    "Schedule returned no lesson authority response");
        }
        Map<Long, LessonInfo> authoritative = new java.util.HashMap<>();
        for (LessonInfo lesson : lessons) {
            if (lesson == null || lesson.getLessonId() <= 0 || lesson.getStatus().isBlank()
                    || authoritative.put(lesson.getLessonId(), lesson) != null) {
                throw new ScheduleServiceUnavailableException(
                        "Schedule returned a malformed or duplicate lesson authority");
            }
            String status = lesson.getStatus().toLowerCase(java.util.Locale.ROOT);
            if (!java.util.Set.of("planned", "active", "closed", "cancelled", "transferred")
                    .contains(status)) {
                throw new ScheduleServiceUnavailableException(
                        "Schedule returned an unknown lesson status");
            }
        }
        if (authoritative.size() != ids.size()) {
            throw new ScheduleServiceUnavailableException(
                    "Schedule returned an incomplete lesson authority response");
        }
        return records.stream()
                .filter(r -> {
                    String status = authoritative.get(r.lessonId()).getStatus()
                            .toLowerCase(java.util.Locale.ROOT);
                    return !status.equals("cancelled") && !status.equals("transferred");
                })
                .toList();
    }

    private void authorizeHeadmanOrTeacher(Long groupId, Long subjectId) {
        UserRole role = requestContext.getRole();
        if (role == UserRole.STUDENT) {
            if (!requestContext.getGroupId().equals(groupId)) {
                throw new AccessDeniedException("Cannot view data for another group");
            }
            if (!requestContext.isHeadman()
                    && !academicGrpcClient.hasAssistantPermission(groupId, "VIEW_STATS")) {
                throw new AccessDeniedException("Отсутствует право VIEW_STATS");
            }
        } else if (role == UserRole.TEACHER) {
            authorizeTeacherGroup(groupId, requestContext.getUserId());
        } else {
            throw new AccessDeniedException("Access denied");
        }
    }

    /**
     * Authorizes a concrete lesson read. The Schedule snapshot carries the
     * historical assignment identity; a rich Academic assignment projection is
     * matched when it is available so a current assignment cannot cross into a
     * different date/type/semester.
     */
    private void authorizeHeadmanOrTeacher(LessonResponse lesson) {
        Long groupId = lesson.getGroupId();
        Long subjectId = lesson.getSubjectId();
        UserRole role = requestContext.getRole();
        if (role == UserRole.STUDENT) {
            if (!Objects.equals(requestContext.getGroupId(), groupId)) {
                throw new AccessDeniedException("Cannot view data for another group");
            }
            if (!requestContext.isHeadman()
                    && !academicGrpcClient.hasAssistantPermission(groupId, "VIEW_STATS")
                    && !academicGrpcClient.hasAssistantPermission(groupId, "MARK_ATTENDANCE")) {
                throw new AccessDeniedException("Отсутствует право VIEW_STATS");
            }
            return;
        }
        if (role != UserRole.TEACHER) {
            throw new AccessDeniedException("Access denied");
        }

        authorizeTeacherLesson(lesson, requestContext.getUserId());
    }

    /**
     * Reusable concrete-lesson gate for the dedicated teacher read boundary.
     * Current active group authority grants read access to the whole group,
     * while the immutable Schedule snapshot supplies historical lesson data.
     */
    public void authorizeTeacherLesson(LessonResponse lesson, Long teacherId) {
        if (lesson == null || lesson.getId() <= 0 || lesson.getGroupId() <= 0
                || lesson.getSubjectId() <= 0 || lesson.getAssignmentId() <= 0
                || lesson.getSemesterId() <= 0 || lesson.getAssignedTeacherId() <= 0
                || lesson.getLessonType() == null || lesson.getLessonType().isBlank()
                || teacherId == null) {
            throw new AccessDeniedException("Teacher cannot read this lesson");
        }
        authorizeTeacherGroup(lesson.getGroupId(), teacherId);
    }

    private void authorizeTeacherGroup(long groupId, long teacherId) {
        if (groupId <= 0 || teacherId <= 0) {
            throw new AccessDeniedException("Teacher is not active in this group");
        }
        TeacherSubjectsResponse response = academicGrpcClient.getCurrentTeacherSubjects(teacherId);
        if (response == null || response.getSubjectsList().stream()
                .noneMatch(info -> info != null && info.getGroupId() == groupId)) {
            throw new AccessDeniedException("Teacher is not active in this group");
        }
    }

    /**
     * Own-lesson gate retained for excuse/ticket and attachment reads. Those
     * operations continue to require the immutable lesson assignment itself.
     */
    public void authorizeTeacherOwnLesson(LessonResponse lesson, Long teacherId) {
        JournalLessonPolicy.Timing timing = JournalLessonPolicy.requireTiming(lesson);
        long semesterId = lesson.getSemesterId();
        long assignmentId = lesson.getAssignmentId();
        long assignedTeacherId = lesson.getAssignedTeacherId();
        String lessonType = lesson.getLessonType();
        if (assignmentId <= 0 || assignedTeacherId <= 0 || teacherId == null
                || !Objects.equals(teacherId, assignedTeacherId)
                || lessonType == null || lessonType.isBlank()) {
            throw new AccessDeniedException("Teacher is not assigned to this lesson");
        }

        TeacherSubjectsResponse response = academicGrpcClient.getTeacherSubjects(teacherId, semesterId);
        if (response == null) {
            throw new AccessDeniedException("Teacher assignment authority is unavailable");
        }
        boolean exactAssignment = response != null && response.getSubjectsList().stream()
                .anyMatch(info -> matchesAssignment(info, lesson, timing.date()));
        if (exactAssignment) {
            return;
        }

        // The immutable Schedule snapshot is the authority for this concrete
        // historical pair. A current unrelated assignment must not revoke the
        // teacher's right to read the old pair. If Academic still exposes the
        // same assignment id, however, a mismatch is a fail-closed conflict.
        boolean conflictingSameAssignment = response.getSubjectsList().stream()
                .anyMatch(info -> info != null && info.getAssignmentId() == assignmentId);
        boolean malformedProjection = response.getSubjectsList().stream()
                .anyMatch(info -> info == null || info.getAssignmentId() <= 0);
        if (conflictingSameAssignment || malformedProjection) {
            throw new AccessDeniedException("Teacher is not assigned to this lesson");
        }
        return;
    }

    private static boolean matchesAssignment(TeacherSubjectInfo info,
                                             LessonResponse lesson,
                                             LocalDate lessonDate) {
        if (info == null || info.getAssignmentId() <= 0
                || info.getAssignmentId() != lesson.getAssignmentId()
                || info.getSemesterId() != lesson.getSemesterId()
                || info.getSubjectId() != lesson.getSubjectId()
                || info.getGroupId() != lesson.getGroupId()) {
            return false;
        }
        String actualType = lesson.getLessonType();
        String assignedType = info.getLessonType();
        if (actualType == null || actualType.isBlank() || assignedType == null
                || assignedType.isBlank() || !actualType.equalsIgnoreCase(assignedType)) {
            return false;
        }
        try {
            LocalDate validFrom = LocalDate.parse(info.getValidFrom());
            LocalDate validUntilExclusive = LocalDate.parse(info.getValidUntilExclusive());
            return !lessonDate.isBefore(validFrom) && lessonDate.isBefore(validUntilExclusive);
        } catch (RuntimeException ex) {
            return false;
        }
    }

    private static boolean matchesAssignment(TeacherSubjectInfo info,
                                             LessonInfo lesson,
                                             LocalDate lessonDate) {
        if (info == null || info.getAssignmentId() <= 0
                || info.getAssignmentId() != lesson.getAssignmentId()
                || info.getSemesterId() != lesson.getSemesterId()
                || info.getSubjectId() != lesson.getSubjectId()
                || info.getGroupId() != lesson.getGroupId()) {
            return false;
        }
        String actualType = lesson.getLessonType();
        String assignedType = info.getLessonType();
        if (actualType == null || actualType.isBlank() || assignedType == null
                || assignedType.isBlank() || !actualType.equalsIgnoreCase(assignedType)) {
            return false;
        }
        try {
            LocalDate validFrom = LocalDate.parse(info.getValidFrom());
            LocalDate validUntilExclusive = LocalDate.parse(info.getValidUntilExclusive());
            return !lessonDate.isBefore(validFrom) && lessonDate.isBefore(validUntilExclusive);
        } catch (RuntimeException ex) {
            return false;
        }
    }
}
