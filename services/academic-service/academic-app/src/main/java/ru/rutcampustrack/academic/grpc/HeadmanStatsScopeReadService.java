package ru.rutcampustrack.academic.grpc;

import io.grpc.Status;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import ru.rutcampustrack.academic.assistant.AssistantPermissionAuthority;
import ru.rutcampustrack.academic.entity.Semester;
import ru.rutcampustrack.academic.identitycatalog.AuthIdentityDataClient;
import ru.rutcampustrack.academic.identityoperation.AcademicIdentityReadFence;
import ru.rutcampustrack.academic.repository.GroupRepository;
import ru.rutcampustrack.academic.repository.SemesterRepository;
import ru.rutcampustrack.academic.repository.StudentGroupHistoryRepository;
import ru.rutcampustrack.academic.security.AcademicLiveSessionClient;
import ru.rutcampustrack.academic.studentprojection.StudentProjectionScope;
import ru.rutcampustrack.academic.studentprojection.StudentProjectionScopeService;
import ru.rutcampustrack.authauthority.grpc.AuthorityAccountState;
import ru.rutcampustrack.authsecurity.grpc.LiveSessionAuthority;
import ru.rutcampustrack.shared.security.InternalJwtClaims;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;

/** Catalog metadata is returned only after current durable own-group authorization. */
@Service
public class HeadmanStatsScopeReadService {
    private final AssistantPermissionAuthority authority;
    private final GroupRepository groups;
    private final SemesterRepository semesters;
    private final JdbcTemplate jdbc;
    private final StudentGroupHistoryRepository history;
    private final StudentProjectionScopeService studentProjection;
    private final AcademicLiveSessionClient sessions;
    private final AuthIdentityDataClient identities;
    private final AcademicIdentityReadFence fences;
    private final TransactionTemplate reads;

    @Autowired
    public HeadmanStatsScopeReadService(
            AssistantPermissionAuthority authority,
            GroupRepository groups,
            SemesterRepository semesters,
            JdbcTemplate jdbc,
            StudentGroupHistoryRepository history,
            StudentProjectionScopeService studentProjection,
            AcademicLiveSessionClient sessions,
            AuthIdentityDataClient identities,
            AcademicIdentityReadFence fences,
            PlatformTransactionManager transactionManager) {
        this.studentProjection = studentProjection;
        this.authority = authority;
        this.groups = groups;
        this.semesters = semesters;
        this.jdbc = jdbc;
        this.history = history;
        this.sessions = sessions;
        this.identities = identities;
        this.fences = fences;
        this.reads = new TransactionTemplate(transactionManager);
        this.reads.setReadOnly(true);
        this.reads.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        this.reads.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public HeadmanStatsScopeResponse read(
            InternalJwtClaims actor, HeadmanStatsScopeRequest request) {
        long groupId = request.getGroupId();
        if (groupId <= 0
                || request.hasSemesterId() && request.getSemesterId() <= 0
                || request.hasStudentId() && request.getStudentId() <= 0) {
            throw Status.INVALID_ARGUMENT
                    .withDescription("group_id/semester_id must be positive")
                    .asRuntimeException();
        }
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw Status.UNAVAILABLE
                    .withDescription("scope admission requires no transaction")
                    .asRuntimeException();
        }
        var admitted = sessions.admitProjection(actor);
        var local = reads.execute(ignored -> readLocal(actor, request));
        verifyCurrent(actor, admitted, local);
        var response = local.response().toBuilder();
        if (request.hasStudentId() && local.response().hasSelectedSemester()) {
            response.setStudentRankScope(
                    toRankProto(
                            studentProjection.resolveHeadmanRank(
                                    local.response().getSelectedSemester().getId(),
                                    request.getStudentId(),
                                    groupId)));
        }
        var rechecked = reads.execute(ignored -> readLocal(actor, request));
        if (!local.equals(rechecked)) {
            throw Status.UNAVAILABLE
                    .withDescription("headman scope changed during read")
                    .asRuntimeException();
        }
        verifyCurrent(actor, sessions.admitProjection(actor), rechecked);
        return response.build();
    }

    private LocalRead readLocal(InternalJwtClaims actor, HeadmanStatsScopeRequest request) {
        long groupId = request.getGroupId();
        fences.requireUserReadable(actor.userId());
        fences.requireGroupReadable(groupId);
        if (!authority.allows(actor, groupId, "VIEW_STATS")) {
            throw Status.PERMISSION_DENIED
                    .withDescription("current own-group VIEW_STATS required")
                    .asRuntimeException();
        }
        var group =
                groups.findById(groupId)
                        .filter(value -> value.isActive())
                        .orElseThrow(
                                () ->
                                        Status.PERMISSION_DENIED
                                                .withDescription("current group unavailable")
                                                .asRuntimeException());
        var catalog =
                semesters.findAll().stream()
                        .filter(value -> value.getDeletionPhase() == null)
                        .sorted(
                                Comparator.comparing(Semester::getDateFrom)
                                        .reversed()
                                        .thenComparing(Semester::getId))
                        .toList();
        var active = catalog.stream().filter(Semester::isActive).toList();
        if (active.size() > 1)
            throw Status.UNAVAILABLE
                    .withDescription("ambiguous active semester")
                    .asRuntimeException();
        var response =
                HeadmanStatsScopeResponse.newBuilder()
                        .setGroupId(groupId)
                        .setGroupName(group.getName());
        if (!active.isEmpty()) response.setActiveSemesterId(active.getFirst().getId());
        for (Semester semester : catalog) {
            if (semester.getDateFrom() == null
                    || semester.getDateTo() == null
                    || semester.getDateTo().isBefore(semester.getDateFrom())) {
                throw Status.UNAVAILABLE
                        .withDescription("invalid semester catalog")
                        .asRuntimeException();
            }
            response.addSemesters(toProto(semester));
        }
        Semester selected =
                request.hasSemesterId()
                        ? catalog.stream()
                                .filter(value -> value.getId() == request.getSemesterId())
                                .findFirst()
                                .orElseThrow(
                                        () ->
                                                Status.NOT_FOUND
                                                        .withDescription(
                                                                "requested semester unavailable")
                                                        .asRuntimeException())
                        : active.isEmpty() ? null : active.getFirst();
        if (selected != null) response.setSelectedSemester(toProto(selected));
        if (request.hasStudentId()) {
            fences.requireUserReadable(request.getStudentId());
            boolean current =
                    Boolean.TRUE.equals(
                            jdbc.queryForObject(
                                    """
SELECT EXISTS (SELECT 1 FROM academic_role_eligibility g
    WHERE g.user_id = ? AND g.role = 'STUDENT' AND g.group_id = ?
      AND g.status <> 'ARCHIVED')
""",
                                    Boolean.class,
                                    request.getStudentId(),
                                    groupId));
            boolean historical = false;
            if (selected != null) {
                var rows = history.findByUserIdOrderByJoinedAtAscIdAsc(request.getStudentId());
                java.time.LocalDate previousEnd = null;
                boolean openSeen = false;
                for (var row : rows) {
                    if (row.getUserId() != request.getStudentId()
                            || row.getGroupId() == null
                            || row.getGroupId() <= 0
                            || row.getJoinedAt() == null
                            || openSeen
                            || row.getLeftAt() != null
                                    && row.getLeftAt().isBefore(row.getJoinedAt())
                            || previousEnd != null && previousEnd.isAfter(row.getJoinedAt())) {
                        throw Status.UNAVAILABLE
                                .withDescription("invalid student membership timeline")
                                .asRuntimeException();
                    }
                    if (row.getGroupId() == groupId
                            && !row.getJoinedAt().isAfter(selected.getDateTo())
                            && (row.getLeftAt() == null
                                    || row.getLeftAt().isAfter(row.getJoinedAt())
                                            && row.getLeftAt().isAfter(selected.getDateFrom())))
                        historical = true;
                    openSeen = row.getLeftAt() == null;
                    previousEnd = row.getLeftAt();
                }
            }
            if (selected != null ? !historical : !current) {
                throw Status.NOT_FOUND
                        .withDescription("student outside selected group scope")
                        .asRuntimeException();
            }
        }
        var members =
                jdbc.query(
                        """
SELECT p.id,p.applied_auth_roles_version,p.academic_revision,e.status,e.group_id
FROM academic_role_eligibility e JOIN academic_profiles p ON p.id=e.user_id
WHERE e.role='STUDENT' AND e.group_id=? AND e.status<>'ARCHIVED'
ORDER BY p.id
""",
                        (rs, n) ->
                                new LocalIdentity(
                                        rs.getLong("id"),
                                        rs.getLong("applied_auth_roles_version"),
                                        rs.getLong("academic_revision"),
                                        rs.getString("status"),
                                        rs.getLong("group_id")),
                        groupId);
        for (var member : members) fences.requireUserReadable(member.userId());
        response.setMembershipTotal(members.size());
        response.setMembershipActive(
                members.stream().filter(member -> "ACTIVE".equals(member.status())).count());
        var actorRows =
                jdbc.query(
                        "SELECT id,applied_auth_roles_version,academic_revision FROM"
                                + " academic_profiles WHERE id=?",
                        (rs, n) ->
                                new LocalIdentity(
                                        rs.getLong("id"),
                                        rs.getLong("applied_auth_roles_version"),
                                        rs.getLong("academic_revision"),
                                        actor.status(),
                                        groupId),
                        actor.userId());
        if (actorRows.size() != 1 || actorRows.getFirst().rolesVersion() != actor.rolesVersion()) {
            throw Status.UNAVAILABLE
                    .withDescription("actor domain revision is stale")
                    .asRuntimeException();
        }
        return new LocalRead(response.build(), List.copyOf(members), actorRows.getFirst());
    }

    private void verifyCurrent(
            InternalJwtClaims actor, LiveSessionAuthority session, LocalRead local) {
        var selected = new TreeSet<Long>();
        selected.add(actor.userId());
        local.members().forEach(member -> selected.add(member.userId()));
        var ids = List.copyOf(selected);
        var current =
                new java.util.LinkedHashMap<
                        Long, ru.rutcampustrack.authauthority.grpc.IdentityAuthoritySnapshot>();
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        for (int offset = 0; offset < ids.size(); offset += 200) {
            current.putAll(
                    identities.readServiceIdentity(
                            ids.subList(offset, Math.min(offset + 200, ids.size())), deadline));
        }
        var actorIdentity = current.get(actor.userId());
        if (actorIdentity == null
                || actorIdentity.getRolesVersion() != actor.rolesVersion()
                || actorIdentity.getAcademicRevision() != local.actor().academicRevision()
                || actorIdentity.getAccountState()
                        != AuthorityAccountState.AUTHORITY_ACCOUNT_STATE_ACTIVE
                || actorIdentity.getGrantsList().stream()
                        .noneMatch(
                                grant ->
                                        grant.getGrantId() == session.getActiveRoleGrantId()
                                                && grant.getRole().equalsIgnoreCase(actor.role())
                                                && grant.getStatus()
                                                        .equalsIgnoreCase(actor.status())
                                                && grant.hasGroupId()
                                                && Objects.equals(
                                                        grant.getGroupId(), actor.groupId()))) {
            throw Status.PERMISSION_DENIED
                    .withDescription("current own-group VIEW_STATS required")
                    .asRuntimeException();
        }
        for (var member : local.members()) {
            var identity = current.get(member.userId());
            if (identity == null
                    || identity.getRolesVersion() != member.rolesVersion()
                    || identity.getAcademicRevision() != member.academicRevision()
                    || identity.getAccountState()
                            != AuthorityAccountState.AUTHORITY_ACCOUNT_STATE_ACTIVE) {
                throw Status.UNAVAILABLE
                        .withDescription("cohort authority changed")
                        .asRuntimeException();
            }
            var students =
                    identity.getGrantsList().stream()
                            .filter(grant -> "STUDENT".equalsIgnoreCase(grant.getRole()))
                            .toList();
            if (students.size() != 1
                    || students.getFirst().getGrantId() <= 0
                    || !students.getFirst().getStatus().equalsIgnoreCase(member.status())
                    || !students.getFirst().hasGroupId()
                    || students.getFirst().getGroupId() != member.groupId()) {
                throw Status.UNAVAILABLE
                        .withDescription("cohort domain revision is stale")
                        .asRuntimeException();
            }
        }
    }

    private record LocalIdentity(
            long userId, long rolesVersion, long academicRevision, String status, long groupId) {}

    private record LocalRead(
            HeadmanStatsScopeResponse response, List<LocalIdentity> members, LocalIdentity actor) {}

    private static StudentProjectionScopeResponse toRankProto(StudentProjectionScope scope) {
        var result =
                StudentProjectionScopeResponse.newBuilder()
                        .setStudentId(scope.studentId())
                        .setSemesterId(scope.semesterId())
                        .setDateFrom(scope.dateFrom().toString())
                        .setDateTo(scope.dateTo().toString())
                        .setTerminalReadOnly(scope.terminalReadOnly())
                        .setServerDate(scope.serverDate().toString())
                        .setRankVisibility(
                                scope.rankVisibility()
                                                == StudentProjectionScope.RankVisibility.HIDDEN
                                        ? StudentProjectionRankVisibility
                                                .STUDENT_PROJECTION_RANK_VISIBILITY_HIDDEN
                                        : StudentProjectionRankVisibility
                                                .STUDENT_PROJECTION_RANK_VISIBILITY_VISIBLE)
                        .setRankEligible(scope.rankEligible())
                        .addAllActiveRosterUserIds(scope.activeRosterUserIds());
        if (scope.rankGroupId() != null) result.setRankGroupId(scope.rankGroupId());
        for (var subject : scope.subjects()) result.addSubjects(toSubjectProto(subject));
        for (var subject : scope.rankSubjects()) result.addRankSubjects(toSubjectProto(subject));
        for (var segment : scope.ownMembershipSegments()) {
            result.addOwnMembershipSegments(
                    StudentProjectionMembershipSegment.newBuilder()
                            .setGroupId(segment.groupId())
                            .setDateFrom(segment.dateFrom().toString())
                            .setDateUntilExclusive(segment.dateUntilExclusive().toString())
                            .addAllSubjectIds(segment.subjectIds()));
        }
        return result.build();
    }

    private static AcademicSubjectInfo toSubjectProto(StudentProjectionScope.Subject subject) {
        return AcademicSubjectInfo.newBuilder()
                .setSubjectId(subject.subjectId())
                .setGroupId(subject.groupId())
                .setSubjectName(subject.name())
                .setSubjectType(subject.type())
                .addAllLessonTypes(subject.lessonTypes())
                .build();
    }

    private static HeadmanStatsSemester toProto(Semester value) {
        return HeadmanStatsSemester.newBuilder()
                .setId(value.getId())
                .setName(value.getName())
                .setDateFrom(value.getDateFrom().toString())
                .setDateTo(value.getDateTo().toString())
                .setActive(value.isActive())
                .setArchived(value.isArchived())
                .build();
    }
}
