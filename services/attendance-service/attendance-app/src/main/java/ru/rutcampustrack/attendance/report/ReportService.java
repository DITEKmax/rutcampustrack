package ru.rutcampustrack.attendance.report;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ru.rutcampustrack.academic.grpc.GroupMembersResponse;
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
import ru.rutcampustrack.attendance.grpc.AcademicGrpcClient;
import ru.rutcampustrack.attendance.grpc.ScheduleGrpcClient;
import ru.rutcampustrack.attendance.security.RequestContext;
import ru.rutcampustrack.attendance.semester.SemesterCacheService;
import ru.rutcampustrack.attendance.shared.port.AttendanceReadPort;
import ru.rutcampustrack.attendance.shared.port.AttendanceRecord;
import ru.rutcampustrack.schedule.grpc.LessonResponse;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
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
            AttendanceStatus.PRESENT, "б",
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
        authorizeHeadmanOrTeacher(lesson.getGroupId(), lesson.getSubjectId());

        GroupMembersResponse members = academicGrpcClient.getGroupMembers(lesson.getGroupId());

        List<AttendanceRecord> records = attendanceReadPort.findByLessonId(lessonId);
        Map<Long, AttendanceRecord> recordsByUserId = records.stream()
                .collect(Collectors.toMap(AttendanceRecord::userId, r -> r, (a, b) -> a));

        List<StudentAttendanceEntry> entries = members.getStudentsList().stream()
                .map(student -> {
                    Long uid = student.getUserId();
                    AttendanceRecord rec = recordsByUserId.get(uid);
                    AttendanceStatus status = (rec != null) ? rec.status() : AttendanceStatus.ABSENT;
                    String source = (rec != null && rec.source() != null)
                            ? rec.source().name().toLowerCase()
                            : null;
                    String excuseReason = (rec != null) ? rec.excuseReason() : null;
                    return new StudentAttendanceEntry(uid, student.getDisplayName(),
                            status.name().toLowerCase(), statusSymbol(status), source, excuseReason);
                })
                .toList();

        return new LessonAttendanceResponse(
                lessonId,
                lesson.getGroupId(),
                lesson.getSubjectId(),
                lesson.getDate(),
                entries
        );
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

        // D-10: Filter out CANCELLED — they don't count in statistics
        Map<Long, List<AttendanceRecord>> grouped = allRecords.stream()
                .filter(r -> r.status() != AttendanceStatus.CANCELLED)
                .collect(Collectors.groupingBy(AttendanceRecord::subjectId));

        // D-13: Resolve subject names via gRPC batch call
        List<Long> subjectIds = new ArrayList<>(grouped.keySet());
        Map<Long, String> subjectNames = academicGrpcClient.getSubjectsByIds(subjectIds);

        List<SubjectStats> subjectStatsList = grouped.entrySet().stream()
                .map(entry -> {
                    Long sid = entry.getKey();
                    List<AttendanceRecord> subjectRecords = entry.getValue();
                    String subjectName = subjectNames.getOrDefault(sid, "Unknown");

                    int total = subjectRecords.size();
                    int attended = (int) subjectRecords.stream()
                            .filter(r -> r.status() == AttendanceStatus.PRESENT
                                    || r.status() == AttendanceStatus.EXCUSED
                                    || r.status() == AttendanceStatus.FREE_ATTENDANCE)
                            .count();
                    int absent = (int) subjectRecords.stream()
                            .filter(r -> r.status() == AttendanceStatus.ABSENT)
                            .count();
                    int excused = (int) subjectRecords.stream()
                            .filter(r -> r.status() == AttendanceStatus.EXCUSED
                                    || r.status() == AttendanceStatus.FREE_ATTENDANCE)
                            .count();
                    double percentage = total == 0 ? 0.0 : (attended * 100.0) / total;

                    return new SubjectStats(sid, subjectName, total, attended, absent, excused, percentage);
                })
                .toList();

        int totalOverall = subjectStatsList.stream().mapToInt(SubjectStats::getTotal).sum();
        int attendedOverall = subjectStatsList.stream().mapToInt(SubjectStats::getAttended).sum();
        int absentOverall = subjectStatsList.stream().mapToInt(SubjectStats::getAbsent).sum();
        int excusedOverall = subjectStatsList.stream().mapToInt(SubjectStats::getExcused).sum();
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
        int total = counted.size();
        int attended = (int) counted.stream()
                .filter(r -> r.status() == AttendanceStatus.PRESENT
                        || r.status() == AttendanceStatus.EXCUSED
                        || r.status() == AttendanceStatus.FREE_ATTENDANCE)
                .count();
        int absent = (int) counted.stream()
                .filter(r -> r.status() == AttendanceStatus.ABSENT)
                .count();
        int excused = (int) counted.stream()
                .filter(r -> r.status() == AttendanceStatus.EXCUSED
                        || r.status() == AttendanceStatus.FREE_ATTENDANCE)
                .count();
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
        // Groups records by week number relative to the semester start (Mon-based weeks).
        Map<Integer, List<AttendanceRecord>> byWeek = new TreeMap<>();
        for (AttendanceRecord r : counted) {
            int weekOfSemester = weekNumberFrom(semesterStart, r.lessonDate());
            byWeek.computeIfAbsent(weekOfSemester, k -> new ArrayList<>()).add(r);
        }
        List<WeeklyStat> result = new ArrayList<>(byWeek.size());
        for (Map.Entry<Integer, List<AttendanceRecord>> e : byWeek.entrySet()) {
            int weekNum = e.getKey();
            List<AttendanceRecord> rows = e.getValue();
            int total = rows.size();
            int attended = (int) rows.stream()
                    .filter(r -> r.status() == AttendanceStatus.PRESENT
                            || r.status() == AttendanceStatus.EXCUSED
                            || r.status() == AttendanceStatus.FREE_ATTENDANCE)
                    .count();
            int absent = (int) rows.stream()
                    .filter(r -> r.status() == AttendanceStatus.ABSENT)
                    .count();
            int excused = (int) rows.stream()
                    .filter(r -> r.status() == AttendanceStatus.EXCUSED
                            || r.status() == AttendanceStatus.FREE_ATTENDANCE)
                    .count();
            int isoWeek = rows.get(0).lessonDate()
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
        Map<Long, String> names = academicGrpcClient.getSubjectsByIds(new ArrayList<>(agg.keySet()));
        return agg.entrySet().stream()
                .filter(e -> e.getValue()[1] > 0)
                .sorted(Comparator.comparingLong((Map.Entry<Long, long[]> e) -> e.getValue()[1]).reversed())
                .limit(Math.max(1, limit))
                .map(e -> new TopMissedSubject(
                        e.getKey(),
                        names.getOrDefault(e.getKey(), "Unknown"),
                        (int) e.getValue()[1],
                        (int) e.getValue()[0]))
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
        List<Long> ids = records.stream().map(AttendanceRecord::lessonId).distinct().toList();
        java.util.Set<Long> alive = scheduleGrpcClient.getLessonsByIds(ids).stream()
                .map(ru.rutcampustrack.schedule.grpc.LessonInfo::getLessonId)
                .collect(Collectors.toSet());
        return records.stream()
                .filter(r -> alive.contains(r.lessonId()))
                .toList();
    }

    private void authorizeHeadmanOrTeacher(Long groupId, Long subjectId) {
        UserRole role = requestContext.getRole();
        if (role == UserRole.STUDENT) {
            if (!requestContext.isHeadman()) {
                throw new AccessDeniedException("Only headmen can view group reports");
            }
            if (!requestContext.getGroupId().equals(groupId)) {
                throw new AccessDeniedException("Cannot view data for another group");
            }
        } else if (role == UserRole.TEACHER) {
            Long semId = semesterCacheService.getActiveSemesterId();
            if (semId == null) {
                throw new IllegalStateException("Active semester not available");
            }
            TeacherSubjectsResponse resp =
                    academicGrpcClient.getTeacherSubjects(requestContext.getUserId(), semId);
            boolean teaches = resp.getSubjectsList().stream()
                    .anyMatch(s -> s.getSubjectId() == subjectId.longValue()
                            && s.getGroupId() == groupId.longValue());
            if (!teaches) {
                throw new AccessDeniedException("Teacher does not teach this subject for this group");
            }
        } else {
            throw new AccessDeniedException("Access denied");
        }
    }
}
