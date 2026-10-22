package ru.rutcampustrack.academic.botread;

import io.grpc.Status;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import ru.rutcampustrack.academic.history.HistoricalMembershipService;
import ru.rutcampustrack.academic.identityoperation.AcademicIdentityReadFence;
import ru.rutcampustrack.botreads.grpc.BotAcademicReadAction;
import ru.rutcampustrack.botreads.grpc.BotAcademicScopeResponse;
import ru.rutcampustrack.botreads.grpc.BotScopeRosterMember;
import ru.rutcampustrack.botreads.grpc.BotScopeSubject;
import ru.rutcampustrack.botreads.grpc.BotTeacherAssignment;
import ru.rutcampustrack.botreads.grpc.DirectedBotIdentity;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;

/** Own Academic eligibility and dated domain data; no Auth table or remote call. */
@Service
public class AcademicOwnBotReadQuery {
    private static final ZoneId MOSCOW = ZoneId.of("Europe/Moscow");
    private static final int MAX_ROWS = 2048;
    private final JdbcTemplate jdbc;
    private final HistoricalMembershipService history;
    private final Clock clock;
    private final AcademicIdentityReadFence fences;

    public AcademicOwnBotReadQuery(
            JdbcTemplate jdbc,
            HistoricalMembershipService history,
            Clock clock,
            AcademicIdentityReadFence fences) {
        this.jdbc = jdbc;
        this.history = history;
        this.clock = clock;
        this.fences = fences;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public BotAcademicScopeResponse read(
            DirectedBotIdentity identity, BotAcademicReadAction action) {
        AuthBotReadAuthorityClient.validate(identity);
        String role =
                switch (action) {
                    case BOT_ACADEMIC_READ_ACTION_TEACHER_TODAY -> "TEACHER";
                    case BOT_ACADEMIC_READ_ACTION_HEADMAN_CURRENT -> "HEADMAN";
                    default ->
                            throw Status.INVALID_ARGUMENT
                                    .withDescription("fixed own-read action required")
                                    .asRuntimeException();
                };
        var authority = authority(identity, role);
        OffsetDateTime now = OffsetDateTime.now(clock.withZone(MOSCOW));
        LocalDate date = now.toLocalDate();
        var semesters =
                jdbc.query(
                        """
                        SELECT id,date_from,date_to FROM semesters
                         WHERE is_active AND NOT is_archived AND archive_transition='NONE'
                           AND NOT archive_release_pending ORDER BY id LIMIT 2
                        """,
                        (rs, n) ->
                                new Semester(
                                        rs.getLong("id"),
                                        rs.getObject("date_from", LocalDate.class),
                                        rs.getObject("date_to", LocalDate.class)));
        if (semesters.size() != 1) throw AuthBotReadAuthorityClient.unavailable();
        var semester = semesters.getFirst();
        if (date.isBefore(semester.from()) || date.isAfter(semester.to())) {
            throw Status.FAILED_PRECONDITION
                    .withDescription("current educational semester required")
                    .asRuntimeException();
        }
        var response =
                BotAcademicScopeResponse.newBuilder()
                        .setUserId(identity.getExpectedUserId())
                        .setTelegramBindingVersion(identity.getExpectedBindingVersion())
                        .setRolesVersion(identity.getExpectedRolesVersion())
                        .setAction(action)
                        .setEligibilityRevision(authority.revision())
                        .setServerNow(now.toString())
                        .setEducationalDate(date.toString())
                        .setSemesterId(semester.id())
                        .setSemesterDateFrom(semester.from().toString())
                        .setSemesterDateTo(semester.to().toString());
        if ("TEACHER".equals(role)) {
            var assignments = assignments(identity.getExpectedUserId(), semester, date);
            assignments.stream()
                    .map(BotTeacherAssignment::getGroupId)
                    .distinct()
                    .forEach(fences::requireGroupReadable);
            response.addAllAssignments(assignments);
        } else {
            long group = ownHeadmanGroup(identity.getExpectedUserId(), authority.groupId());
            fences.requireGroupReadable(group);
            String name =
                    jdbc.queryForObject(
                            "SELECT name FROM groups WHERE id=? AND is_active",
                            String.class,
                            group);
            if (name == null || name.isBlank()) throw AuthBotReadAuthorityClient.unavailable();
            // The established service validates coverage provenance, complete timelines,
            // unique intervals and orphan users; current role rows never replace history.
            var roster = history.readRoster(group, date, semester.id());
            if (roster.students().size() > MAX_ROWS) throw AuthBotReadAuthorityClient.unavailable();
            if (roster.students().stream()
                    .noneMatch(u -> u.getId() == identity.getExpectedUserId())) {
                throw denied();
            }
            response.setOwnGroupId(group).setOwnGroupName(name);
            roster.students()
                    .forEach(
                            user ->
                                    response.addRoster(
                                            BotScopeRosterMember.newBuilder()
                                                    .setStudentId(user.getId())
                                                    .setDisplayName(user.getDisplayName())));
            response.addAllSubjects(subjects(group, semester, date));
        }
        return response.build();
    }

    private Authority authority(DirectedBotIdentity identity, String role) {
        var rows =
                jdbc.query(
                        """
SELECT p.academic_revision,p.applied_auth_roles_version,e.group_id
  FROM academic_profiles p JOIN academic_role_eligibility e ON e.user_id=p.id
 WHERE p.id=? AND p.archived_at IS NULL AND e.role=? AND e.status='ACTIVE'
   AND NOT EXISTS (
     SELECT 1 FROM academic_identity_operation_targets t
     JOIN academic_identity_operations o ON o.operation_id=t.operation_id
      WHERE t.user_id=p.id AND t.fenced AND o.phase<>'SUCCEEDED')
""",
                        (rs, n) ->
                                new Authority(
                                        rs.getLong("academic_revision"),
                                        rs.getLong("applied_auth_roles_version"),
                                        rs.getObject("group_id", Long.class)),
                        identity.getExpectedUserId(),
                        role);
        if (rows.size() != 1) throw denied();
        var result = rows.getFirst();
        if (result.authRolesVersion() != identity.getExpectedRolesVersion()) {
            throw Status.FAILED_PRECONDITION
                    .withDescription("current eligibility revision required")
                    .asRuntimeException();
        }
        if (result.revision() <= 0) throw AuthBotReadAuthorityClient.unavailable();
        return result;
    }

    private long ownHeadmanGroup(long user, Long group) {
        if (group == null || group <= 0) throw denied();
        var ids =
                jdbc.queryForList(
                        """
                        SELECT e.user_id FROM academic_role_eligibility e
                         JOIN academic_profiles p ON p.id=e.user_id
                         JOIN groups g ON g.id=e.group_id
                         WHERE e.role='HEADMAN' AND e.status='ACTIVE' AND e.group_id=?
                           AND p.archived_at IS NULL AND g.is_active ORDER BY e.user_id LIMIT 2
                        """,
                        Long.class,
                        group);
        if (ids.size() != 1 || ids.getFirst() != user) throw denied();
        Boolean student =
                jdbc.queryForObject(
                        """
                        SELECT EXISTS(SELECT 1 FROM academic_role_eligibility
                         WHERE user_id=? AND role='STUDENT' AND status='ACTIVE' AND group_id=?)
                        """,
                        Boolean.class,
                        user,
                        group);
        if (!Boolean.TRUE.equals(student)) throw denied();
        return group;
    }

    private List<BotTeacherAssignment> assignments(
            long teacher, Semester semester, LocalDate date) {
        var rows =
                jdbc.query(
                        """
SELECT a.id,a.group_id,a.subject_id,a.semester_id,a.lesson_type::text,
       a.valid_from,coalesce(a.valid_until_exclusive,?::date+1) valid_until,
       g.name group_name,s.name subject_name
  FROM assignments a JOIN groups g ON g.id=a.group_id
  JOIN subjects s ON s.id=a.subject_id AND s.semester_id=a.semester_id
 WHERE a.teacher_id=? AND a.semester_id=? AND a.lifecycle_state='ACTIVE'
   AND g.is_active AND a.valid_from<=?
   AND coalesce(a.valid_until_exclusive,?::date+1)>?
   AND NOT EXISTS(SELECT 1 FROM assignment_replacement_operations o
     WHERE o.target_assignment_id=a.id AND o.operation_kind='ADOPT_UNASSIGNED'
       AND o.state IN ('PREPARED','APPLIED'))
 ORDER BY g.name,s.name,a.lesson_type,a.valid_from,a.id LIMIT ?
""",
                        (rs, n) ->
                                BotTeacherAssignment.newBuilder()
                                        .setAssignmentId(rs.getLong("id"))
                                        .setGroupId(rs.getLong("group_id"))
                                        .setSubjectId(rs.getLong("subject_id"))
                                        .setSemesterId(rs.getLong("semester_id"))
                                        .setLessonType(rs.getString("lesson_type"))
                                        .setValidFrom(
                                                rs.getObject("valid_from", LocalDate.class)
                                                        .toString())
                                        .setValidUntilExclusive(
                                                rs.getObject("valid_until", LocalDate.class)
                                                        .toString())
                                        .setGroupName(rs.getString("group_name"))
                                        .setSubjectName(rs.getString("subject_name"))
                                        .build(),
                        semester.to(),
                        teacher,
                        semester.id(),
                        date,
                        semester.to(),
                        date,
                        MAX_ROWS + 1);
        if (rows.size() > MAX_ROWS) throw AuthBotReadAuthorityClient.unavailable();
        return rows;
    }

    private List<BotScopeSubject> subjects(long group, Semester semester, LocalDate date) {
        var rows =
                jdbc.query(
                        """
SELECT s.id,s.name,array_agg(DISTINCT a.lesson_type::text ORDER BY a.lesson_type::text) types
  FROM assignments a JOIN subjects s ON s.id=a.subject_id AND s.semester_id=a.semester_id
 WHERE a.group_id=? AND a.semester_id=? AND a.lifecycle_state='ACTIVE'
   AND a.valid_from<=? AND coalesce(a.valid_until_exclusive,?::date+1)>?
   AND NOT EXISTS(SELECT 1 FROM assignment_replacement_operations o
     WHERE o.target_assignment_id=a.id AND o.operation_kind='ADOPT_UNASSIGNED'
       AND o.state IN ('PREPARED','APPLIED'))
 GROUP BY s.id,s.name ORDER BY s.id LIMIT ?
""",
                        (rs, n) ->
                                BotScopeSubject.newBuilder()
                                        .setSubjectId(rs.getLong("id"))
                                        .setSubjectName(rs.getString("name"))
                                        .addAllLessonTypes(
                                                List.of((String[]) rs.getArray("types").getArray()))
                                        .build(),
                        group,
                        semester.id(),
                        date,
                        semester.to(),
                        date,
                        MAX_ROWS + 1);
        if (rows.size() > MAX_ROWS) throw AuthBotReadAuthorityClient.unavailable();
        return rows;
    }

    private static io.grpc.StatusRuntimeException denied() {
        return Status.PERMISSION_DENIED
                .withDescription("current own Academic eligibility required")
                .asRuntimeException();
    }

    private record Authority(long revision, long authRolesVersion, Long groupId) {}

    private record Semester(long id, LocalDate from, LocalDate to) {}
}
