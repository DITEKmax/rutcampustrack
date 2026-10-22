package ru.rutcampustrack.academic.exportjobs;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import ru.rutcampustrack.academic.assistant.AssistantPermissionAuthority;
import ru.rutcampustrack.academic.identityoperation.AcademicIdentityReadFence;
import ru.rutcampustrack.auth.dto.*;
import ru.rutcampustrack.shared.security.InternalJwtClaims;
import ru.rutcampustrack.shared.security.teacher.TeacherReportScope;

import java.time.*;
import java.util.*;

/** Current domain policy, using Academic data only. Auth separately admits every security tuple. */
@Component
public class LocalReportAuthority {
    private final JdbcTemplate jdbc;
    private final AssistantPermissionAuthority assistants;
    private final AcademicIdentityReadFence fences;

    public LocalReportAuthority(
            JdbcTemplate jdbc,
            AssistantPermissionAuthority assistants,
            AcademicIdentityReadFence fences) {
        this.jdbc = jdbc;
        this.assistants = assistants;
        this.fences = fences;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public StoredReportIdentity plan(
            InternalJwtClaims actor, IssueReportDownloadTicketRequest report, Instant expires) {
        long revision = revision(actor);
        Long semester = semester(report);
        var identity = new StoredReportIdentity(actor, report, expires, revision, semester, null);
        requireWithinSnapshot(identity, false);
        return identity;
    }

    @Transactional(
            readOnly = true,
            isolation = Isolation.REPEATABLE_READ,
            propagation = Propagation.REQUIRES_NEW)
    public void require(StoredReportIdentity identity, boolean ready) {
        requireWithinSnapshot(identity, ready);
    }

    void requireWithinSnapshot(StoredReportIdentity identity, boolean ready) {
        var actor = identity.actor();
        var report = identity.report();
        fence(() -> fences.requireUserReadable(actor.userId()));
        if (!Instant.now().isBefore(identity.expiresAt())
                || revision(actor) != identity.academicRevision()) throw denied();
        switch (report.kind()) {
            case TEACHER_JOURNAL, TEACHER_STATS -> {
                requireEligibility(actor, "TEACHER", null);
                var current = teacherScope(actor.userId());
                if (unscoped(report)) {
                    if (ready && !current.containsAll(identity.teacherScope())) throw denied();
                } else {
                    Long group =
                            report.kind() == ReportDownloadKind.TEACHER_JOURNAL
                                    ? report.teacherJournal().groupId()
                                    : report.teacherStats().groupId();
                    Long subject =
                            report.kind() == ReportDownloadKind.TEACHER_JOURNAL
                                    ? report.teacherJournal().subjectId()
                                    : report.teacherStats().subjectId();
                    if (group == null
                            || subject == null
                            || !current.tuples()
                                    .contains(new TeacherReportScope.Tuple(group, subject)))
                        throw denied();
                }
            }
            case HEADMAN_GROUP_COMPOSITION -> {
                requireActiveGroup(actor.groupId());
                requireEligibility(actor, "HEADMAN", actor.groupId());
                if (!"HEADMAN".equals(actor.role())) throw denied();
            }
            case HEADMAN_WEEKLY_CURRENT,
                    HEADMAN_WEEKLY_SELECTED,
                    HEADMAN_STATS,
                    HEADMAN_STATS_TREND,
                    HEADMAN_SUBJECT_JOURNAL -> {
                requireActiveGroup(actor.groupId());
                if (!Set.of("STUDENT", "HEADMAN").contains(actor.role())
                        || actor.groupId() == null
                        || !assistants.allows(actor, actor.groupId(), "VIEW_STATS")) throw denied();
                requireEligibility(actor, actor.role(), actor.groupId());
            }
            case ADMIN_USERS, ADMIN_GROUPS, ADMIN_GROUP_ATTENDANCE -> {
                if (!"ADMIN".equals(actor.role())) throw denied();
                fence(fences::requireRegistryReadable);
            }
        }
        // An omitted active semester is part of the admitted selector, including its absence.
        if (implicitSemester(report)
                && !Objects.equals(optionalActiveSemester(), identity.semesterId())) throw denied();
        if (identity.semesterId() != null) {
            var range = semesterRange(identity.semesterId());
            List<LocalDate> weeks =
                    switch (report.kind()) {
                        case HEADMAN_WEEKLY_CURRENT ->
                                List.of(report.headmanWeeklyCurrent().weekStart());
                        case HEADMAN_WEEKLY_SELECTED -> report.headmanWeeklySelected().weekStarts();
                        case HEADMAN_SUBJECT_JOURNAL -> report.headmanSubjectJournal().weekStarts();
                        default -> null;
                    };
            if (weeks != null
                    && (weeks.isEmpty()
                                    && report.kind() != ReportDownloadKind.HEADMAN_SUBJECT_JOURNAL
                            || weeks.size() > 64
                            || weeks.stream().distinct().count() != weeks.size()
                            || weeks.stream()
                                    .anyMatch(
                                            day ->
                                                    day == null
                                                            || day.getDayOfWeek()
                                                                    != DayOfWeek.MONDAY
                                                            || day.isBefore(
                                                                    range.from()
                                                                            .with(
                                                                                    java.time
                                                                                            .temporal
                                                                                            .TemporalAdjusters
                                                                                            .previousOrSame(
                                                                                                    DayOfWeek
                                                                                                            .MONDAY)))
                                                            || day.isAfter(range.to()))))
                throw ExportJobRepository.error(HttpStatus.BAD_REQUEST);
            if (report.kind() == ReportDownloadKind.TEACHER_JOURNAL) {
                var p = report.teacherJournal();
                if (!p.isDateRangeConsistent()
                        || p.dateFrom() != null
                                && (p.dateFrom().isBefore(range.from())
                                        || p.dateTo().isAfter(range.to())))
                    throw ExportJobRepository.error(HttpStatus.BAD_REQUEST);
            }
        }
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public StoredReportIdentity capture(StoredReportIdentity identity, String producerHash) {
        if (!unscoped(identity.report())) {
            requireWithinSnapshot(identity, false);
            return identity;
        }
        var scope = teacherScope(identity.actor().userId());
        if (producerHash == null || !scope.sha256().equals(producerHash)) throw denied();
        return identity.withTeacherScope(scope);
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public TeacherReportScope teacherScope(long teacherId) {
        fence(() -> fences.requireUserReadable(teacherId));
        Long activeCount =
                jdbc.queryForObject(
                        "SELECT count(*) FROM semesters WHERE is_active=TRUE", Long.class);
        if (activeCount == null || activeCount != 1)
            throw ExportJobRepository.error(HttpStatus.SERVICE_UNAVAILABLE);
        LocalDate today = LocalDate.now(ZoneId.of("Europe/Moscow"));
        var tuples =
                jdbc.query(
                        """
SELECT DISTINCT a.group_id,a.subject_id FROM assignments a
JOIN semesters s ON s.id=a.semester_id
JOIN academic_profiles p ON p.id=a.teacher_id
JOIN academic_role_eligibility e ON e.user_id=p.id AND e.role='TEACHER' AND e.status='ACTIVE'
WHERE a.teacher_id=? AND s.is_active=TRUE AND ? BETWEEN s.date_from AND s.date_to
  AND p.archived_at IS NULL AND a.lifecycle_state='ACTIVE' AND a.valid_from<=?
  AND ?<COALESCE(a.valid_until_exclusive,s.date_to+1)
""",
                        (rs, n) -> new TeacherReportScope.Tuple(rs.getLong(1), rs.getLong(2)),
                        teacherId,
                        today,
                        today,
                        today);
        if (tuples.isEmpty()) throw denied();
        for (Long group :
                tuples.stream().map(TeacherReportScope.Tuple::groupId).distinct().toList())
            requireGroupReadable(group);
        return TeacherReportScope.of(tuples);
    }

    public SemesterRange semesterRange(long id) {
        var rows =
                jdbc.query(
                        "SELECT date_from,date_to FROM semesters WHERE id=? AND deletion_phase IS"
                                + " NULL",
                        (rs, n) ->
                                new SemesterRange(
                                        rs.getObject(1, LocalDate.class),
                                        rs.getObject(2, LocalDate.class)),
                        id);
        if (rows.size() != 1
                || rows.getFirst().from() == null
                || rows.getFirst().to() == null
                || rows.getFirst().to().isBefore(rows.getFirst().from())) throw denied();
        return rows.getFirst();
    }

    private long revision(InternalJwtClaims actor) {
        var rows =
                jdbc.query(
                        "SELECT academic_revision FROM academic_profiles WHERE id=? AND archived_at"
                                + " IS NULL AND applied_auth_roles_version=?",
                        (rs, n) -> rs.getLong(1),
                        actor.userId(),
                        actor.rolesVersion());
        if (rows.size() != 1) throw denied();
        return rows.getFirst();
    }

    private void requireActiveGroup(Long groupId) {
        if (groupId != null) requireGroupReadable(groupId);
        if (groupId == null
                || !Boolean.TRUE.equals(
                        jdbc.queryForObject(
                                "SELECT EXISTS (SELECT 1 FROM groups WHERE id=? AND"
                                        + " is_active=TRUE)",
                                Boolean.class,
                                groupId))) throw denied();
    }

    private void requireEligibility(InternalJwtClaims actor, String role, Long group) {
        if (!role.equals(actor.role()) || actor.readOnly() || !"ACTIVE".equals(actor.status()))
            throw denied();
        Boolean allowed =
                jdbc.queryForObject(
                        "SELECT EXISTS (SELECT 1 FROM academic_role_eligibility WHERE user_id=? AND"
                            + " role=? AND status='ACTIVE' AND group_id IS NOT DISTINCT FROM ?)",
                        Boolean.class,
                        actor.userId(),
                        role,
                        group);
        if (!Boolean.TRUE.equals(allowed)) throw denied();
    }

    void requireGroupReadable(long groupId) {
        fence(() -> fences.requireGroupReadable(groupId));
    }

    private static void fence(Runnable guard) {
        try {
            guard.run();
        } catch (io.grpc.StatusRuntimeException pending) {
            throw ExportJobRepository.error(HttpStatus.SERVICE_UNAVAILABLE);
        }
    }

    private Long semester(IssueReportDownloadTicketRequest report) {
        return switch (report.kind()) {
            case TEACHER_JOURNAL -> report.teacherJournal().semesterId();
            case TEACHER_STATS -> report.teacherStats().semesterId();
            case ADMIN_GROUP_ATTENDANCE -> Long.valueOf(report.adminGroupAttendance().semesterId());
            case HEADMAN_STATS ->
                    report.headmanStats().semesterId() == null
                            ? optionalActiveSemester()
                            : report.headmanStats().semesterId();
            case HEADMAN_STATS_TREND ->
                    report.headmanStatsTrend().semesterId() == null
                            ? optionalActiveSemester()
                            : report.headmanStatsTrend().semesterId();
            case HEADMAN_WEEKLY_CURRENT, HEADMAN_WEEKLY_SELECTED, HEADMAN_SUBJECT_JOURNAL ->
                    activeSemester();
            default -> null;
        };
    }

    private Long activeSemester() {
        Long id = optionalActiveSemester();
        if (id == null) throw denied();
        return id;
    }

    private Long optionalActiveSemester() {
        var ids =
                jdbc.queryForList(
                        "SELECT id FROM semesters WHERE is_active=TRUE AND deletion_phase IS NULL",
                        Long.class);
        if (ids.size() > 1) throw denied();
        return ids.isEmpty() ? null : ids.getFirst();
    }

    private static boolean implicitSemester(IssueReportDownloadTicketRequest report) {
        return switch (report.kind()) {
            case HEADMAN_WEEKLY_CURRENT, HEADMAN_WEEKLY_SELECTED, HEADMAN_SUBJECT_JOURNAL -> true;
            case HEADMAN_STATS -> report.headmanStats().semesterId() == null;
            case HEADMAN_STATS_TREND -> report.headmanStatsTrend().semesterId() == null;
            default -> false;
        };
    }

    static boolean unscoped(IssueReportDownloadTicketRequest report) {
        return report.kind() == ReportDownloadKind.TEACHER_STATS
                && report.teacherStats().groupId() == null
                && report.teacherStats().subjectId() == null
                && Set.of("group", "groups")
                        .contains(report.teacherStats().scope().toLowerCase(Locale.ROOT));
    }

    private static org.springframework.web.server.ResponseStatusException denied() {
        return ExportJobRepository.error(HttpStatus.FORBIDDEN);
    }

    public record SemesterRange(LocalDate from, LocalDate to) {}
}
