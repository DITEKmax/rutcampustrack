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
import ru.rutcampustrack.attendance.contract.dto.report.StudentAttendanceEntry;
import ru.rutcampustrack.attendance.contract.dto.report.StudentStatsResponse;
import ru.rutcampustrack.attendance.contract.dto.report.SubjectStats;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
                    return new StudentAttendanceEntry(uid, student.getDisplayName(),
                            status.name().toLowerCase(), statusSymbol(status));
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
                                    r.lessonDate().toString(),
                                    r.lessonNumber(),
                                    r.status().name().toLowerCase(),
                                    statusSymbol(r.status())
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

        List<AttendanceRecord> allRecords = attendanceReadPort.findByUserId(userId, semesterId);

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
    // RPRT-04: Student attendance records
    // -------------------------------------------------------------------------

    /**
     * Returns attendance records for the authenticated student, optionally filtered by subjectId.
     */
    public List<AttendanceRecordEntry> getStudentRecords(Long subjectId) {
        Long userId = requestContext.getUserId();
        Long semesterId = semesterCacheService.getActiveSemesterId();

        List<AttendanceRecord> records = attendanceReadPort.findByUserId(userId, semesterId);

        return records.stream()
                .filter(r -> subjectId == null || r.subjectId().equals(subjectId))
                .map(r -> new AttendanceRecordEntry(
                        r.lessonId(),
                        r.subjectId(),
                        r.lessonDate().toString(),
                        r.lessonNumber(),
                        r.status().name().toLowerCase(),
                        statusSymbol(r.status()),
                        r.source().name().toLowerCase()
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
