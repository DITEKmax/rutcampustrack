package ru.rutcampustrack.academic.identityoperation;

import io.grpc.Status;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import ru.rutcampustrack.academic.event.AcademicPersonalNotificationEvent;
import ru.rutcampustrack.academic.event.DashboardInvalidatedEvent;
import ru.rutcampustrack.academic.event.GroupUpdatedEvent;
import ru.rutcampustrack.academic.event.StudentInitialMembershipRecordedEvent;
import ru.rutcampustrack.academic.repository.SemesterRepository;
import ru.rutcampustrack.academic.repository.StudentGroupHistoryRepository;
import ru.rutcampustrack.authoperations.grpc.AcademicIdentityCommitReceipt;
import ru.rutcampustrack.authoperations.grpc.AdmissionGrant;
import ru.rutcampustrack.authoperations.grpc.IdentityOperationKind;
import ru.rutcampustrack.authoperations.grpc.IdentityOperationResult;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Concrete local APPLY. It cannot contact Auth or commit independently of its operation receipt.
 */
@Component
public class AcademicIdentityDomainWriter {
    private final JdbcTemplate jdbc;
    private final SemesterRepository semesters;
    private final ApplicationEventPublisher events;
    private final StudentGroupHistoryRepository histories;

    public AcademicIdentityDomainWriter(
            JdbcTemplate jdbc,
            SemesterRepository semesters,
            ApplicationEventPublisher events,
            StudentGroupHistoryRepository histories) {
        this.jdbc = jdbc;
        this.semesters = semesters;
        this.events = events;
        this.histories = histories;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public List<AcademicIdentityCommitReceipt> apply(
            AcademicIdentityLocalCommand command,
            IdentityOperationKind kind,
            IdentityOperationResult prepared,
            long acceptedActorId) {
        if (acceptedActorId <= 0) throw conflict();
        if (command.profile() != null
                && kind != IdentityOperationKind.IDENTITY_OPERATION_KIND_CREATE
                && prepared.getTargetsList().stream()
                        .noneMatch(t -> t.getUserId() == command.profile().userId()))
            throw conflict();
        for (var group : command.groups()) {
            var revisions =
                    jdbc.queryForList(
                            "SELECT academic_revision FROM groups WHERE id=? FOR" + " UPDATE",
                            Long.class,
                            group.groupId());
            if (revisions.size() != 1 || revisions.getFirst() != group.expectedRevision())
                throw conflict();
        }
        var initialMemberships = new java.util.ArrayList<InitialMembership>();
        Map<Long, Student> previous = new HashMap<>();
        Map<Long, Boolean> wasHeadman = new HashMap<>();
        for (var target : prepared.getTargetsList()) {
            if (kind == IdentityOperationKind.IDENTITY_OPERATION_KIND_CREATE) {
                if (prepared.getTargetsCount() != 1
                        || target.getExpectedAcademicRevision() != 0
                        || command.profile() == null
                        || command.profile().userId() != 0) throw conflict();
                var p = command.profile();
                var baseRoles =
                        target.getGrantsList().stream()
                                .filter(
                                        g ->
                                                List.of("STUDENT", "TEACHER", "ADMIN")
                                                        .contains(g.getRole()))
                                .toList();
                if (baseRoles.size() != 1) throw conflict();
                jdbc.update(
                        """
INSERT INTO academic_profiles(id,directory_role,directory_status,last_name,first_name,middle_name,email,phone,
  employee_number,avatar_id,academic_revision,applied_auth_roles_version,created_at,updated_at)
VALUES(?,?,?,?,?,?,?,?,?,?,1,?,now(),now())
""",
                        target.getUserId(),
                        baseRoles.getFirst().getRole(),
                        p.directoryStatus(),
                        p.lastName(),
                        p.firstName(),
                        p.middleName(),
                        p.email(),
                        p.phone(),
                        p.employeeNumber(),
                        p.avatarId(),
                        target.getFinalRolesVersion());
            } else {
                var revisions =
                        jdbc.queryForList(
                                "SELECT academic_revision FROM academic_profiles WHERE id=? FOR"
                                        + " UPDATE",
                                Long.class,
                                target.getUserId());
                if (revisions.size() != 1
                        || revisions.getFirst() != target.getExpectedAcademicRevision())
                    throw conflict();
            }
            var students =
                    jdbc.query(
                            "SELECT status,group_id FROM academic_role_eligibility WHERE user_id=?"
                                    + " AND role='STUDENT'",
                            (rs, n) ->
                                    new Student(
                                            rs.getString("status"),
                                            rs.getObject("group_id", Long.class)),
                            target.getUserId());
            previous.put(target.getUserId(), students.isEmpty() ? null : students.getFirst());
            wasHeadman.put(
                    target.getUserId(),
                    Boolean.TRUE.equals(
                            jdbc.queryForObject(
                                    "SELECT EXISTS(SELECT 1 FROM academic_role_eligibility WHERE"
                                            + " user_id=? AND role='HEADMAN' AND status='ACTIVE')",
                                    Boolean.class,
                                    target.getUserId())));
        }
        for (var target : prepared.getTargetsList()) {
            for (var grant : target.getGrantsList()) {
                if (grant.getRole().equals("HEADMAN") && grant.getStatus().equals("ACTIVE")) {
                    if (!grant.hasGroupId()
                            || target.getGrantsList().stream()
                                    .noneMatch(
                                            student ->
                                                    student.getRole().equals("STUDENT")
                                                            && student.getStatus().equals("ACTIVE")
                                                            && student.hasGroupId()
                                                            && student.getGroupId()
                                                                    == grant.getGroupId()))
                        throw conflict();
                }
            }
        }
        if (kind == IdentityOperationKind.IDENTITY_OPERATION_KIND_HEADMAN_REPLACE) {
            int revoked = validateHeadman(command, prepared, acceptedActorId);
            if (jdbc.update(
                            "UPDATE academic_identity_operations SET headman_assistants_revoked=?"
                                    + " WHERE operation_id=? AND phase='AUTH_PREPARED' AND"
                                    + " headman_assistants_revoked IS NULL",
                            revoked,
                            java.util.UUID.fromString(prepared.getOperationId()))
                    != 1) throw conflict();
        }
        // Suspend both affected headman facts before installing the single desired winner.
        for (var target : prepared.getTargetsList()) {
            jdbc.update(
                    "UPDATE academic_role_eligibility SET status='SUSPENDED',updated_at=now() WHERE"
                            + " user_id=? AND role='HEADMAN'",
                    target.getUserId());
        }
        for (var target : prepared.getTargetsList()) {
            Student old = previous.get(target.getUserId());
            AdmissionGrant desired =
                    target.getGrantsList().stream()
                            .filter(g -> g.getRole().equals("STUDENT"))
                            .findFirst()
                            .orElse(null);
            if (kind == IdentityOperationKind.IDENTITY_OPERATION_KIND_TRANSFER) {
                if (prepared.getTargetsCount() != 1
                        || old == null
                        || !old.status().equals("ACTIVE")
                        || !Objects.equals(old.group(), command.sourceGroupId())
                        || desired == null
                        || !desired.getStatus().equals("ACTIVE")
                        || !desired.hasGroupId()
                        || !Objects.equals(desired.getGroupId(), command.destinationGroupId())
                        || Objects.equals(command.sourceGroupId(), command.destinationGroupId()))
                    throw conflict();
                requireGroupFence(command, command.sourceGroupId());
                requireGroupFence(command, command.destinationGroupId());
                closeMembership(
                        target.getUserId(),
                        command.sourceGroupId(),
                        command.effectiveDate(),
                        command.reason(),
                        true);
                openMembership(
                        target.getUserId(),
                        command.destinationGroupId(),
                        command.effectiveDate(),
                        command.reason(),
                        false,
                        null);
                revokeUserAssistants(command, target.getUserId(), acceptedActorId);
                if (wasHeadman.get(target.getUserId()))
                    revokeGroupAssistants(command.sourceGroupId(), acceptedActorId);
                events.publishEvent(
                        AcademicPersonalNotificationEvent.membership(
                                target.getUserId(),
                                command.sourceGroupId(),
                                command.destinationGroupId(),
                                command.effectiveDate(),
                                acceptedActorId));
            } else if (kind == IdentityOperationKind.IDENTITY_OPERATION_KIND_CREATE
                    && desired != null
                    && desired.getStatus().equals("ACTIVE")) {
                requireGroupFence(command, desired.getGroupId());
                long history =
                        openMembership(
                                target.getUserId(),
                                desired.getGroupId(),
                                command.initialMembershipDate(),
                                "initial-enrollment",
                                true,
                                command.enrollmentSemesterId());
                initialMemberships.add(
                        new InitialMembership(history, command.enrollmentSemesterId()));
            } else if (kind == IdentityOperationKind.IDENTITY_OPERATION_KIND_ROLE
                    && desired != null
                    && !"ARCHIVED".equals(desired.getStatus())) {
                if (!desired.hasGroupId()) throw conflict();
                var open = openMembershipGroups(target.getUserId());
                if (open.size() > 1
                        || !open.isEmpty()
                                && !Objects.equals(open.getFirst(), desired.getGroupId()))
                    throw conflict();
                if (old == null || "ARCHIVED".equals(old.status())) {
                    requireGroupFence(command, desired.getGroupId());
                    boolean initial = old == null;
                    long history =
                            openMembership(
                                    target.getUserId(),
                                    desired.getGroupId(),
                                    command.initialMembershipDate(),
                                    initial ? "initial-enrollment" : "role-reassignment",
                                    initial,
                                    initial ? command.enrollmentSemesterId() : null);
                    if (initial)
                        initialMemberships.add(
                                new InitialMembership(history, command.enrollmentSemesterId()));
                    open = List.of(desired.getGroupId());
                } else if ("ACTIVE".equals(desired.getStatus()) && open.isEmpty()) {
                    requireGroupFence(command, desired.getGroupId());
                    openMembership(
                            target.getUserId(),
                            desired.getGroupId(),
                            command.effectiveDate(),
                            "ARCHIVED".equals(old.status())
                                    ? "role-reassignment"
                                    : "role-reactivation",
                            false,
                            null);
                    open = List.of(desired.getGroupId());
                }
                if (List.of("EXPELLED", "GRADUATED").contains(desired.getStatus())
                        && !open.isEmpty()) {
                    requireGroupFence(command, open.getFirst());
                    closeMembership(
                            target.getUserId(),
                            open.getFirst(),
                            command.effectiveDate(),
                            null,
                            false);
                }
                // Suspension keeps the established dated membership interval. Only terminal
                // status closes it; reactivation reuses it when it is still open.
                if (wasHeadman.get(target.getUserId()) && !"ACTIVE".equals(desired.getStatus()))
                    revokeGroupAssistants(old.group(), acceptedActorId);
            } else if (kind == IdentityOperationKind.IDENTITY_OPERATION_KIND_ARCHIVE) {
                var open = openMembershipGroups(target.getUserId());
                if (open.size() > 1) throw conflict();
                if (!open.isEmpty()) {
                    requireGroupFence(command, open.getFirst());
                    closeMembership(
                            target.getUserId(),
                            open.getFirst(),
                            command.effectiveDate(),
                            null,
                            false);
                }
                if (wasHeadman.get(target.getUserId()))
                    revokeGroupAssistants(old.group(), acceptedActorId);
                jdbc.update(
                        "UPDATE academic_profiles SET archived_at=now(),directory_status='ARCHIVED'"
                                + " WHERE id=?",
                        target.getUserId());
                revokeUserAssistants(command, target.getUserId(), acceptedActorId);
            } else if (kind == IdentityOperationKind.IDENTITY_OPERATION_KIND_RESTORE) {
                jdbc.update(
                        "UPDATE academic_profiles SET archived_at=NULL,directory_status='ACTIVE'"
                                + " WHERE id=?",
                        target.getUserId());
                revokeUserAssistants(command, target.getUserId(), acceptedActorId);
            }
            if (command.profile() != null
                    && kind != IdentityOperationKind.IDENTITY_OPERATION_KIND_CREATE
                    && command.profile().userId() == target.getUserId()) {
                var p = command.profile();
                jdbc.update(
                        "UPDATE academic_profiles SET"
                            + " last_name=?,first_name=?,middle_name=?,email=?,phone=?,employee_number=?,directory_status=?"
                            + " WHERE id=?",
                        p.lastName(),
                        p.firstName(),
                        p.middleName(),
                        p.email(),
                        p.phone(),
                        p.employeeNumber(),
                        p.directoryStatus(),
                        target.getUserId());
            }
            for (var grant : target.getGrantsList()) {
                if (!List.of("STUDENT", "TEACHER", "HEADMAN").contains(grant.getRole())) continue;
                if (grant.getStatus().equals("ACTIVE") && grant.getRole().equals("TEACHER")) {
                    String employee =
                            jdbc.queryForObject(
                                    "SELECT employee_number FROM academic_profiles WHERE id=?",
                                    String.class,
                                    target.getUserId());
                    if (employee == null || employee.isBlank()) throw conflict();
                }
                jdbc.update(
                        """
INSERT INTO academic_role_eligibility(user_id,role,status,group_id,updated_at) VALUES(?,?,?,?,now())
ON CONFLICT(user_id,role) DO UPDATE SET status=excluded.status,group_id=excluded.group_id,updated_at=excluded.updated_at
""",
                        target.getUserId(),
                        grant.getRole(),
                        grant.getStatus(),
                        grant.hasGroupId() ? grant.getGroupId() : null);
            }
            if (kind != IdentityOperationKind.IDENTITY_OPERATION_KIND_CREATE) {
                if (jdbc.update(
                                "UPDATE academic_profiles SET"
                                    + " academic_revision=academic_revision+1,applied_auth_roles_version=?,updated_at=now()"
                                    + " WHERE id=? AND academic_revision=?",
                                target.getFinalRolesVersion(),
                                target.getUserId(),
                                target.getExpectedAcademicRevision())
                        != 1) throw conflict();
            }
            var afterHeadman =
                    target.getGrantsList().stream()
                            .filter(
                                    g ->
                                            g.getRole().equals("HEADMAN")
                                                    && g.getStatus().equals("ACTIVE"))
                            .findFirst();
            Student previousStudent = previous.get(target.getUserId());
            if (wasHeadman.get(target.getUserId()) && previousStudent == null) throw conflict();
            Long beforeGroup = wasHeadman.get(target.getUserId()) ? previousStudent.group() : null;
            Long afterGroup = afterHeadman.isPresent() ? afterHeadman.get().getGroupId() : null;
            if (!Objects.equals(beforeGroup, afterGroup)) {
                if (beforeGroup != null)
                    events.publishEvent(
                            AcademicPersonalNotificationEvent.headman(
                                    target.getUserId(), beforeGroup, true, false, acceptedActorId));
                if (afterGroup != null)
                    events.publishEvent(
                            AcademicPersonalNotificationEvent.headman(
                                    target.getUserId(), afterGroup, false, true, acceptedActorId));
            }
        }
        if (prepared.getTargetsList().stream()
                .flatMap(t -> t.getGrantsList().stream())
                .anyMatch(g -> List.of("STUDENT", "TEACHER").contains(g.getRole())))
            events.publishEvent(new DashboardInvalidatedEvent(this));
        for (var group : command.groups()) {
            if (jdbc.update(
                            "UPDATE groups SET academic_revision=academic_revision+1 WHERE id=? AND"
                                    + " academic_revision=?",
                            group.groupId(),
                            group.expectedRevision())
                    != 1) throw conflict();
            events.publishEvent(new GroupUpdatedEvent(this, group.groupId()));
        }
        for (var initial : initialMemberships) {
            var row =
                    histories
                            .findById(initial.historyId())
                            .orElseThrow(AcademicIdentityDomainWriter::conflict);
            var semester =
                    semesters
                            .findById(initial.semesterId())
                            .orElseThrow(AcademicIdentityDomainWriter::conflict);
            events.publishEvent(new StudentInitialMembershipRecordedEvent(row, semester));
        }
        return prepared.getTargetsList().stream()
                .map(
                        t ->
                                AcademicIdentityCommitReceipt.newBuilder()
                                        .setUserId(t.getUserId())
                                        .setPreviousAcademicRevision(
                                                t.getExpectedAcademicRevision())
                                        .setCommittedAcademicRevision(
                                                t.getExpectedAcademicRevision() + 1)
                                        .setAppliedAuthRolesVersion(t.getFinalRolesVersion())
                                        .build())
                .toList();
    }

    private int validateHeadman(
            AcademicIdentityLocalCommand command, IdentityOperationResult prepared, long actor) {
        requireGroupFence(command, command.headmanGroupId());
        if (!Boolean.TRUE.equals(
                jdbc.queryForObject(
                        "SELECT is_active FROM groups WHERE id=?",
                        Boolean.class,
                        command.headmanGroupId()))) throw conflict();
        var headmen =
                jdbc.queryForList(
                        "SELECT user_id FROM academic_role_eligibility WHERE group_id=? AND"
                                + " role='HEADMAN' AND status='ACTIVE' ORDER BY user_id",
                        Long.class,
                        command.headmanGroupId());
        if (headmen.size() > 1
                || !Objects.equals(
                        headmen.isEmpty() ? null : headmen.getFirst(), command.expectedHeadmanId()))
            throw conflict();
        var candidate =
                prepared.getTargetsList().stream()
                        .filter(t -> Objects.equals(t.getUserId(), command.candidateUserId()))
                        .findFirst()
                        .orElseThrow(AcademicIdentityDomainWriter::conflict);
        boolean student =
                candidate.getGrantsList().stream()
                        .anyMatch(
                                g ->
                                        g.getRole().equals("STUDENT")
                                                && g.getStatus().equals("ACTIVE")
                                                && g.hasGroupId()
                                                && Objects.equals(
                                                        g.getGroupId(), command.headmanGroupId()));
        boolean headman =
                candidate.getGrantsList().stream()
                        .anyMatch(
                                g ->
                                        g.getRole().equals("HEADMAN")
                                                && g.getStatus().equals("ACTIVE")
                                                && g.hasGroupId()
                                                && Objects.equals(
                                                        g.getGroupId(), command.headmanGroupId()));
        if (!student || !headman) throw conflict();
        if (!Boolean.TRUE.equals(
                jdbc.queryForObject(
                        "SELECT EXISTS(SELECT 1 FROM academic_role_eligibility e JOIN"
                                + " academic_profiles p ON p.id=e.user_id WHERE e.user_id=? AND"
                                + " e.role='STUDENT' AND e.status='ACTIVE' AND e.group_id=? AND"
                                + " p.archived_at IS NULL)",
                        Boolean.class,
                        command.candidateUserId(),
                        command.headmanGroupId()))) throw conflict();
        return !Objects.equals(command.expectedHeadmanId(), command.candidateUserId())
                ? revokeGroupAssistants(command.headmanGroupId(), actor)
                : 0;
    }

    private void closeMembership(
            long user, Long group, LocalDate date, String reason, boolean replaceReason) {
        if (date == null || group == null) throw conflict();
        var rows =
                jdbc.queryForList(
                        "SELECT id FROM student_group_history WHERE user_id=? AND left_at IS NULL"
                                + " ORDER BY id FOR UPDATE",
                        Long.class,
                        user);
        if (rows.size() != 1
                || jdbc.update(
                                "UPDATE student_group_history SET left_at=?,reason=CASE WHEN ? THEN"
                                        + " ? ELSE reason END WHERE id=? AND group_id=? AND"
                                        + " joined_at<=?",
                                date,
                                replaceReason,
                                reason,
                                rows.getFirst(),
                                group,
                                date)
                        != 1) throw conflict();
    }

    private long openMembership(
            long user,
            Long group,
            LocalDate date,
            String reason,
            boolean initial,
            Long semesterId) {
        if (group == null
                || date == null
                || !Boolean.TRUE.equals(
                        jdbc.queryForObject(
                                "SELECT EXISTS(SELECT 1 FROM group_history_coverage c JOIN groups g"
                                    + " ON g.id=c.group_id WHERE c.group_id=? AND g.is_active AND"
                                    + " c.coverage_from<=? AND c.writer_version='managed_v1')",
                                Boolean.class,
                                group,
                                date))) throw conflict();
        var open =
                jdbc.queryForObject(
                        "SELECT count(*) FROM student_group_history WHERE user_id=? AND left_at IS"
                                + " NULL",
                        Long.class,
                        user);
        if (open == null || open != 0) throw conflict();
        if (Boolean.TRUE.equals(
                jdbc.queryForObject(
                        "SELECT EXISTS(SELECT 1 FROM student_group_history WHERE user_id=? AND"
                                + " (joined_at>? OR left_at>?))",
                        Boolean.class,
                        user,
                        date,
                        date))) throw conflict();
        Long id =
                jdbc.queryForObject(
                        "INSERT INTO"
                            + " student_group_history(user_id,group_id,joined_at,reason,created_at)"
                            + " VALUES(?,?,?,?,now()) RETURNING id",
                        Long.class,
                        user,
                        group,
                        date,
                        reason);
        if (initial) {
            if (semesterId == null) throw conflict();
            var semester =
                    semesters
                            .findById(semesterId)
                            .orElseThrow(AcademicIdentityDomainWriter::conflict);
            if (!semester.isActive() || !date.equals(semester.getDateFrom())) throw conflict();
        }
        if (id == null || id <= 0) throw conflict();
        return id;
    }

    private List<Long> openMembershipGroups(long user) {
        return jdbc.queryForList(
                "SELECT group_id FROM student_group_history WHERE user_id=? AND left_at IS NULL"
                        + " ORDER BY id FOR UPDATE",
                Long.class,
                user);
    }

    private record InitialMembership(long historyId, long semesterId) {}

    private static void requireGroupFence(AcademicIdentityLocalCommand command, Long group) {
        if (group == null || command.groups().stream().noneMatch(g -> g.groupId() == group))
            throw conflict();
    }

    private int revokeGroupAssistants(Long group, long actor) {
        var rows =
                jdbc.query(
                        "SELECT id,student_id,permissions FROM headman_assistants WHERE group_id=?"
                                + " AND is_active ORDER BY id FOR UPDATE",
                        (rs, n) ->
                                new Assistant(
                                        rs.getLong("id"),
                                        rs.getLong("student_id"),
                                        group,
                                        (String[]) rs.getArray("permissions").getArray()),
                        group);
        revoke(rows, actor);
        return rows.size();
    }

    private void revokeUserAssistants(AcademicIdentityLocalCommand command, long user, long actor) {
        var rows =
                jdbc.query(
                        "SELECT id,student_id,group_id,permissions FROM headman_assistants WHERE"
                                + " student_id=? AND is_active ORDER BY group_id,id FOR UPDATE",
                        (rs, n) ->
                                new Assistant(
                                        rs.getLong("id"),
                                        rs.getLong("student_id"),
                                        rs.getLong("group_id"),
                                        (String[]) rs.getArray("permissions").getArray()),
                        user);
        for (var row : rows) requireGroupFence(command, row.group());
        revoke(rows, actor);
    }

    private void revoke(List<Assistant> rows, long actor) {
        for (var row : rows) {
            if (jdbc.update(
                            "UPDATE headman_assistants SET is_active=false,revoked_at=now() WHERE"
                                    + " id=? AND is_active",
                            row.id())
                    != 1) throw conflict();
            events.publishEvent(
                    AcademicPersonalNotificationEvent.assistant(
                            row.user(),
                            row.group(),
                            true,
                            false,
                            row.permissions(),
                            new String[0],
                            actor));
        }
    }

    private static io.grpc.StatusRuntimeException conflict() {
        return Status.ABORTED
                .withDescription("Academic identity state changed")
                .asRuntimeException();
    }

    private record Student(String status, Long group) {}

    private record Assistant(long id, long user, long group, String[] permissions) {}
}
