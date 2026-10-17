package ru.rutcampustrack.auth.export;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.*;
import java.util.*;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import ru.rutcampustrack.auth.dto.*;
import ru.rutcampustrack.auth.security.SessionPrincipal;
import ru.rutcampustrack.auth.service.StoredReportDownloadTicket;
import ru.rutcampustrack.auth.session.SessionAdmissionException;
import ru.rutcampustrack.auth.session.model.AuthRole;
import ru.rutcampustrack.shared.security.teacher.TeacherReportScope;
import ru.rutcampustrack.shared.security.teacher.TeacherReportScope.Tuple;

/** Fresh current assignment checks; session admission remains the caller's responsibility. */
public final class TeacherReportAuthorityJdbc {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper =
            new ObjectMapper()
                    .registerModule(new JavaTimeModule())
                    .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                    .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);

    public TeacherReportAuthorityJdbc(JdbcTemplate jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc);
    }

    public static boolean teacher(IssueReportDownloadTicketRequest report) {
        return report.kind() == ReportDownloadKind.TEACHER_JOURNAL
                || report.kind() == ReportDownloadKind.TEACHER_STATS;
    }

    public static boolean unscoped(IssueReportDownloadTicketRequest report) {
        if (report.kind() != ReportDownloadKind.TEACHER_STATS) return false;
        var params = report.teacherStats();
        return params != null
                && params.groupId() == null
                && params.subjectId() == null
                && ("groups".equalsIgnoreCase(params.scope())
                        || "group".equalsIgnoreCase(params.scope()));
    }

    public TeacherReportScope current(SessionPrincipal principal) {
        if (principal.selectedRole() != AuthRole.TEACHER) throw denied();
        try {
            LocalDate today = LocalDate.now(ZoneId.of("Europe/Moscow"));
            var semesters =
                    jdbc.queryForList("SELECT id FROM semesters WHERE is_active=TRUE", Long.class);
            if (semesters.size() != 1) throw unavailable();
            var tuples =
                    jdbc.query(
                            """
                SELECT DISTINCT a.group_id,a.subject_id FROM assignments a
                JOIN semesters s ON s.id=a.semester_id
                JOIN users u ON u.id=a.teacher_id
                JOIN groups g ON g.id=a.group_id JOIN subjects sub ON sub.id=a.subject_id
                WHERE a.teacher_id=? AND s.id=? AND s.is_active=TRUE
                  AND ? BETWEEN s.date_from AND s.date_to
                  AND u.status<>'archived'
                  AND EXISTS(SELECT 1 FROM user_role_grants rg WHERE rg.user_id=u.id
                    AND rg.role='teacher' AND rg.status='active')
                  AND a.lifecycle_state='ACTIVE' AND a.valid_from<=?
                  AND ?<COALESCE(a.valid_until_exclusive,s.date_to+1)
                """,
                            (rs, n) -> new Tuple(rs.getLong(1), rs.getLong(2)),
                            principal.userId(),
                            semesters.getFirst(),
                            today,
                            today,
                            today);
            if (tuples.isEmpty()) throw denied();
            return TeacherReportScope.of(tuples);
        } catch (DataAccessException | IllegalArgumentException failure) {
            throw unavailable();
        }
    }

    public void requireSelection(
            SessionPrincipal principal, IssueReportDownloadTicketRequest report) {
        if (!teacher(report)) return;
        TeacherReportScope current = current(principal);
        if (unscoped(report)) return;
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
                || group <= 0
                || subject <= 0
                || !current.tuples().contains(new Tuple(group, subject))) throw denied();
    }

    public TeacherReportScope capture(
            SessionPrincipal principal,
            IssueReportDownloadTicketRequest report,
            String producerHash) {
        if (!unscoped(report)) {
            requireSelection(principal, report);
            return null;
        }
        TeacherReportScope scope = current(principal);
        if (producerHash == null
                || !producerHash.matches("[0-9a-f]{64}")
                || !scope.sha256().equals(producerHash)) throw denied();
        return scope;
    }

    public void requireReady(StoredReportDownloadTicket identity) {
        if (!teacher(identity.report())) return;
        if (!unscoped(identity.report())) {
            requireSelection(identity.principal(), identity.report());
            return;
        }
        if (identity.teacherScope() == null
                || !current(identity.principal()).containsAll(identity.teacherScope()))
            throw denied();
    }

    public void requireReadyJob(
            SessionPrincipal principal, IssueReportDownloadTicketRequest report, UUID jobId) {
        if (!teacher(report)) return;
        if (jobId == null) {
            requireSelection(principal, report);
            return;
        }
        try {
            var identities =
                    jdbc.query(
                            """
                SELECT identity FROM auth_export_jobs WHERE job_id=? AND state='ready'
                  AND expires_at>NOW() AND user_id=? AND session_id=? AND binding_hash=?
                """,
                            (rs, n) -> rs.getString(1),
                            jobId,
                            principal.userId(),
                            principal.sessionId(),
                            report.bindingHash());
            if (identities.size() != 1) throw denied();
            StoredReportDownloadTicket identity;
            try {
                identity =
                        mapper.readValue(identities.getFirst(), StoredReportDownloadTicket.class);
            } catch (Exception malformed) {
                throw unavailable();
            }
            if (!identity.principal().equals(principal)
                    || !identity.report().bindingHash().equals(report.bindingHash())
                    || !Instant.now().isBefore(identity.expiresAt())) throw denied();
            requireReady(identity);
        } catch (DataAccessException failure) {
            throw unavailable();
        }
    }

    private static SessionAdmissionException denied() {
        return new SessionAdmissionException(
                SessionAdmissionException.Code.REPORT_PERMISSION_DENIED);
    }

    private static SessionAdmissionException unavailable() {
        return new SessionAdmissionException(SessionAdmissionException.Code.AUTHORITY_UNAVAILABLE);
    }
}
