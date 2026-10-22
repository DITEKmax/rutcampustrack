package ru.rutcampustrack.academic.notification;

import io.grpc.Status;

import jakarta.persistence.EntityManager;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import ru.rutcampustrack.academic.assignment.TeacherAssignmentReadAuthority;
import ru.rutcampustrack.academic.assistant.AssistantPermissionAuthority;
import ru.rutcampustrack.academic.contract.enums.SubjectType;
import ru.rutcampustrack.academic.grpc.*;
import ru.rutcampustrack.academic.history.HistoricalMembershipService;
import ru.rutcampustrack.academic.identityoperation.AcademicIdentityReadFence;
import ru.rutcampustrack.academic.repository.*;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.*;

/**
 * Closed business catalogue; local eligibility and fresh Auth recipient data are composed
 * explicitly.
 */
@Service
public class NotificationAudienceResolver {
    private static final ZoneId MOSCOW = ZoneId.of("Europe/Moscow");
    private final HistoricalMembershipService membership;
    private final UserRepository users;
    private final GroupRepository groups;
    private final SubjectRepository subjects;
    private final HeadmanAssistantRepository assistants;
    private final AssistantPermissionAuthority helperAuthority;
    private final TeacherAssignmentReadAuthority teacherAuthority;
    private final JdbcTemplate jdbc;
    private final AcademicIdentityReadFence fences;
    private final NotificationAudienceAuthority authority;
    private final TransactionTemplate snapshot;
    private final EntityManager entities;

    public NotificationAudienceResolver(
            HistoricalMembershipService membership,
            UserRepository users,
            GroupRepository groups,
            SubjectRepository subjects,
            HeadmanAssistantRepository assistants,
            AssistantPermissionAuthority helperAuthority,
            TeacherAssignmentReadAuthority teacherAuthority,
            JdbcTemplate jdbc,
            AcademicIdentityReadFence fences,
            NotificationAudienceAuthority authority,
            PlatformTransactionManager transactions,
            EntityManager entities) {
        this.membership = membership;
        this.users = users;
        this.groups = groups;
        this.subjects = subjects;
        this.assistants = assistants;
        this.helperAuthority = helperAuthority;
        this.teacherAuthority = teacherAuthority;
        this.jdbc = jdbc;
        this.fences = fences;
        this.authority = authority;
        this.entities = entities;
        this.snapshot = new TransactionTemplate(transactions);
        snapshot.setReadOnly(true);
        snapshot.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
    }

    public NotificationAudienceResponse resolve(NotificationAudienceRequest request) {
        // Reject malformed closed-envelope input before any remote dependency is needed.
        policy(request.getEventType());
        try {
            if (!UUID.fromString(request.getRequestId()).toString().equals(request.getRequestId()))
                throw new IllegalArgumentException();
            OffsetDateTime.parse(request.getOccurredAt());
        } catch (RuntimeException malformed) {
            throw invalid("request_id and occurred_at must be canonical UUID/ISO timestamp");
        }
        return authority.resolve(() -> snapshot.execute(ignored -> collect(request)));
    }

    private NotificationAudienceAuthority.LocalSnapshot collect(
            NotificationAudienceRequest request) {
        // Both captures must query their own RR snapshot, including when an HTTP open-EM exists.
        entities.clear();
        boolean decided =
                Set.of("excuse.decided", "late_checkin.decided").contains(request.getEventType());
        if (decided
                        && (!request.hasRequestOutcome()
                                || !Set.of("approved", "rejected", "cancelled")
                                        .contains(request.getRequestOutcome()))
                || !decided && request.hasRequestOutcome())
            throw invalid("request_outcome must match decided event");
        Policy policy =
                decided && "cancelled".equals(request.getRequestOutcome())
                        ? new Policy(
                                true,
                                false,
                                true,
                                "MANAGE_EXCUSES",
                                false,
                                true,
                                false,
                                null,
                                false)
                        : policy(request.getEventType());
        LocalDate occurredDay;
        try {
            if (!UUID.fromString(request.getRequestId()).toString().equals(request.getRequestId()))
                throw new IllegalArgumentException();
            occurredDay =
                    OffsetDateTime.parse(request.getOccurredAt())
                            .atZoneSameInstant(MOSCOW)
                            .toLocalDate();
        } catch (RuntimeException malformed) {
            throw invalid("request_id and occurred_at must be canonical UUID/ISO timestamp");
        }
        if (request.hasGroupId() && request.getGroupId() <= 0
                || request.hasAffectedUserId() && request.getAffectedUserId() <= 0)
            throw invalid("context IDs must be positive");
        if (policy.group() && !request.hasGroupId()) throw invalid("group_id required");
        if (policy.personal() && !request.hasAffectedUserId())
            throw invalid("affected_user_id required");
        if (policy.personal()
                && users.findByIdIncludingArchived(request.getAffectedUserId()).isEmpty())
            throw invalid("unknown affected user");
        // A known managed primary group still has an authoritative legacy Student/HEADMAN fact.
        // Missing teacher provenance produces no TEACHER recipients; supplied malformed tuples
        // fail.
        if (!policy.teacher() && request.getLessonScopesCount() > 0)
            throw invalid("lesson scope not permitted for event");
        if (!policy.personal() && request.hasAffectedUserId())
            throw invalid("personal context not permitted for event");
        if (request.getLessonScopesCount() > 32) throw invalid("too many lesson scopes");
        boolean activeGroup =
                !request.hasGroupId()
                        || groups.findById(request.getGroupId())
                                .orElseThrow(() -> invalid("unknown group"))
                                .isActive();

        var watchedGroups = new TreeSet<Long>();
        if (request.hasGroupId()) watchedGroups.add(request.getGroupId());
        request.getLessonScopesList().forEach(scope -> watchedGroups.add(scope.getGroupId()));
        watchedGroups.forEach(fences::requireGroupReadable);
        if (policy.adminPermission() != null && !policy.personalAdmin())
            fences.requireRegistryReadable();
        Map<Long, SortedSet<String>> recipients = new TreeMap<>();
        long affected = request.hasAffectedUserId() ? request.getAffectedUserId() : -1;
        if (policy.students()) {
            for (long user : membership.readMemberUserIds(request.getGroupId(), occurredDay))
                addIfActive(recipients, user, "STUDENT");
        }
        if (policy.headman() && activeGroup) {
            var current = users.findActiveHeadmanIdsByGrantGroupId(request.getGroupId());
            if (current.size() > 1)
                throw Status.FAILED_PRECONDITION
                        .withDescription("headman authority inconsistent")
                        .asRuntimeException();
            for (long user : current)
                if (user != affected
                        && Objects.equals(
                                request.getGroupId(),
                                users.findActiveStudentGrantGroupId(user).orElse(null)))
                    addIfActive(recipients, user, "HEADMAN");
        }
        if (policy.helperPermission() != null && activeGroup) {
            for (var assistant : assistants.findByGroupIdAndIsActiveTrue(request.getGroupId())) {
                long user = assistant.getStudentId();
                if (user != affected
                        && helperAuthority.allowsRecipient(
                                user, request.getGroupId(), policy.helperPermission()))
                    addIfActive(recipients, user, "STUDENT");
            }
        }
        if (policy.personal()) {
            if (policy.personalAdmin()) {
                addIfActive(recipients, affected, "ADMIN");
            } else addIfActive(recipients, affected, "STUDENT");
        }
        if (policy.teacher())
            for (LessonNotificationScope scope : request.getLessonScopesList()) {
                SubjectType type;
                LocalDate day;
                try {
                    type = SubjectType.valueOf(scope.getLessonType().toUpperCase(Locale.ROOT));
                    day = LocalDate.parse(scope.getLessonDate());
                } catch (RuntimeException malformed) {
                    throw invalid("exact lesson type/date required");
                }
                if (scope.getGroupId() <= 0
                        || scope.getSubjectId() <= 0
                        || scope.getSemesterId() <= 0
                        || scope.hasAssignmentId() && scope.getAssignmentId() <= 0)
                    throw invalid("exact lesson IDs required");
                if (!policy.multipleGroups() && scope.getGroupId() != request.getGroupId())
                    throw invalid("lesson group differs from event");
                groups.findById(scope.getGroupId())
                        .orElseThrow(() -> invalid("unknown lesson group"));
                var subject =
                        subjects.findById(scope.getSubjectId())
                                .orElseThrow(() -> invalid("unknown lesson subject"));
                if (!Objects.equals(subject.getGroupId(), scope.getGroupId()))
                    throw invalid("lesson subject belongs to another group");
                for (long teacher :
                        teacherAuthority.findTeacherIds(
                                scope.getGroupId(),
                                scope.getSubjectId(),
                                scope.getSemesterId(),
                                type,
                                day,
                                scope.hasAssignmentId() ? scope.getAssignmentId() : null))
                    addIfActive(recipients, teacher, "TEACHER");
            }
        if (policy.adminPermission() != null && !policy.personalAdmin()) {
            // This caught-up index enumerates candidates only. Auth decides their current
            // recipient grants/profile after the local transaction has ended.
            for (long user :
                    jdbc.queryForList(
                            "SELECT user_id FROM academic_identity_read_model_grants"
                                    + " WHERE role='ADMIN' AND status='ACTIVE' ORDER BY user_id",
                            Long.class)) addIfActive(recipients, user, "ADMIN");
        }
        var profiles = new TreeMap<Long, NotificationAudienceAuthority.ProfileStamp>();
        for (long user : recipients.keySet()) {
            fences.requireUserReadable(user);
            var stamp =
                    jdbc.queryForObject(
                            "SELECT academic_revision,applied_auth_roles_version FROM"
                                    + " academic_profiles WHERE id=?",
                            (rs, row) ->
                                    new NotificationAudienceAuthority.ProfileStamp(
                                            rs.getLong("academic_revision"),
                                            rs.getLong("applied_auth_roles_version"),
                                            Map.of()),
                            user);
            if (stamp == null) throw NotificationAudienceAuthority.unavailable();
            var roleGroups = new TreeMap<String, Long>();
            jdbc.query(
                    "SELECT role,group_id FROM academic_role_eligibility WHERE user_id=?"
                            + " AND status='ACTIVE'",
                    rs -> {
                        long group = rs.getLong("group_id");
                        Long selectedGroup = rs.wasNull() ? 0L : group;
                        roleGroups.put(rs.getString("role"), selectedGroup);
                    },
                    user);
            profiles.put(
                    user,
                    new NotificationAudienceAuthority.ProfileStamp(
                            stamp.academicRevision(), stamp.authRolesVersion(), roleGroups));
            watchedGroups.addAll(
                    jdbc.queryForList(
                            "SELECT group_id FROM academic_role_eligibility WHERE user_id=?"
                                    + " AND status='ACTIVE' AND group_id IS NOT NULL",
                            Long.class,
                            user));
        }
        var revisions = new TreeMap<Long, Long>();
        for (long group : watchedGroups) {
            fences.requireGroupReadable(group);
            Long revision =
                    jdbc.queryForObject(
                            "SELECT academic_revision FROM groups WHERE id=?", Long.class, group);
            if (revision == null || revision <= 0)
                throw NotificationAudienceAuthority.unavailable();
            revisions.put(group, revision);
        }
        return new NotificationAudienceAuthority.LocalSnapshot(
                request.getRequestId(),
                request.getEventType(),
                recipients,
                profiles,
                revisions,
                policy.adminPermission());
    }

    private void addIfActive(Map<Long, SortedSet<String>> recipients, long user, String role) {
        if (user <= 0) return;
        fences.requireUserReadable(user);
        Boolean eligible =
                jdbc.queryForObject(
                        "SELECT EXISTS(SELECT 1 FROM academic_profiles p WHERE p.id=? AND"
                                + " p.archived_at IS NULL AND p.directory_status<>'ARCHIVED' AND"
                                + " (?='ADMIN' OR EXISTS(SELECT 1 FROM academic_role_eligibility e"
                                + " WHERE e.user_id=p.id AND e.role=? AND e.status='ACTIVE')))",
                        Boolean.class,
                        user,
                        role,
                        role);
        if (Boolean.TRUE.equals(eligible)) add(recipients, user, role);
    }

    private static void add(Map<Long, SortedSet<String>> recipients, long user, String role) {
        recipients.computeIfAbsent(user, ignored -> new TreeSet<>()).add(role);
    }

    private static RuntimeException invalid(String message) {
        return Status.INVALID_ARGUMENT.withDescription(message).asRuntimeException();
    }

    private static Policy policy(String type) {
        return switch (type) {
            case "lesson.started", "lesson.reminder", "lesson.blocked" ->
                    new Policy(true, true, true, null, false, false, false, null, false);
            case "lesson.cancelled",
                    "lesson.one_off.cancelled",
                    "lesson.one_off.created",
                    "homework.published",
                    "homework.updated",
                    "homework.withdrawn" ->
                    new Policy(true, true, true, null, true, false, false, null, false);
            case "lesson.transferred", "lesson.updated", "lesson.restored" ->
                    new Policy(true, true, true, null, true, false, false, null, true);
            case "lesson.closed" ->
                    new Policy(
                            true, false, true, "MARK_ATTENDANCE", false, false, false, null, false);
            case "excuse.requested", "late_checkin.requested" ->
                    new Policy(
                            true, false, true, "MANAGE_EXCUSES", false, true, false, null, false);
            case "excuse.decided",
                    "late_checkin.decided",
                    "attendance.marked",
                    "homework.weekly_digest",
                    "homework.due_reminder",
                    "group.membership.changed",
                    "headman.access.changed",
                    "assistant.access.changed" ->
                    new Policy(false, false, false, null, false, true, false, null, false);
            case "group.renamed", "group.archived" ->
                    new Policy(true, true, true, null, false, false, false, "GROUPS_MANAGE", false);
            case "group.promotion.completed" ->
                    new Policy(
                            false, false, false, null, false, false, false, "GROUPS_MANAGE", false);
            case "semester.activated" ->
                    new Policy(
                            false,
                            false,
                            false,
                            null,
                            false,
                            false,
                            false,
                            "SEMESTERS_MANAGE",
                            false);
            case "semester.operation.completed" ->
                    new Policy(
                            false,
                            false,
                            false,
                            null,
                            false,
                            true,
                            true,
                            "SEMESTERS_MANAGE",
                            false);
            case "admin.access.changed" ->
                    new Policy(false, false, false, null, false, true, true, null, false);
            default -> throw invalid("unknown notification event type");
        };
    }

    private record Policy(
            boolean group,
            boolean students,
            boolean headman,
            String helperPermission,
            boolean teacher,
            boolean personal,
            boolean personalAdmin,
            String adminPermission,
            boolean multipleGroups) {}
}
