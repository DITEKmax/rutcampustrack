package ru.rutcampustrack.academic.assistant;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import ru.rutcampustrack.academic.identityoperation.AcademicIdentityReadFence;
import ru.rutcampustrack.academic.security.AcademicLiveSessionClient;
import ru.rutcampustrack.shared.security.InternalJwtClaims;

import java.util.Locale;
import java.util.Set;

/** Live RPC authority; recipient selection remains a separate, session-independent read. */
@Service
public class AssistantPermissionReadService {
    private static final Set<String> PERMISSIONS =
            Set.of(
                    "MARK_ATTENDANCE",
                    "MANAGE_EXCUSES",
                    "MANAGE_HOMEWORK",
                    "CANCEL_LESSONS",
                    "VIEW_STATS");
    private final AcademicLiveSessionClient auth;
    private final JdbcTemplate jdbc;
    private final AcademicIdentityReadFence fences;
    private final TransactionTemplate reads;

    public AssistantPermissionReadService(
            AcademicLiveSessionClient auth,
            JdbcTemplate jdbc,
            AcademicIdentityReadFence fences,
            PlatformTransactionManager transactions) {
        this.auth = auth;
        this.jdbc = jdbc;
        this.fences = fences;
        reads = new TransactionTemplate(transactions);
        reads.setReadOnly(true);
        reads.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
    }

    public boolean allows(InternalJwtClaims actor, long groupId, String rawPermission) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) throw unavailable();
        String permission =
                rawPermission == null ? "" : rawPermission.trim().toUpperCase(Locale.ROOT);
        if (actor == null
                || actor.userId() <= 0
                || groupId <= 0
                || actor.groupId() == null
                || actor.groupId() != groupId
                || actor.readOnly()
                || !"ACTIVE".equalsIgnoreCase(actor.status())
                || !("HEADMAN".equalsIgnoreCase(actor.role())
                        || "STUDENT".equalsIgnoreCase(actor.role()))
                || !PERMISSIONS.contains(permission)) return false;
        if (!admit(actor)) return denyUnlessPending(actor, groupId);
        var before = snapshot(actor, groupId, permission);
        if (!admit(actor)) return denyUnlessPending(actor, groupId);
        var after = snapshot(actor, groupId, permission);
        if (!java.util.Objects.equals(before, after)) throw unavailable();
        return before != null && before.allowed();
    }

    private boolean denyUnlessPending(InternalJwtClaims actor, long groupId) {
        // Auth also denies fenced identities. A visible local publication barrier must
        // remain retryable rather than masquerading as a definitive permission revoke.
        reads.executeWithoutResult(
                status -> {
                    fences.requireUserReadable(actor.userId());
                    fences.requireGroupReadable(groupId);
                });
        return false;
    }

    private boolean admit(InternalJwtClaims actor) {
        try {
            auth.admit(actor);
            return true;
        } catch (StatusRuntimeException failure) {
            // Only Auth admission proves a current revoke; local fences remain UNAVAILABLE.
            return switch (failure.getStatus().getCode()) {
                case UNAUTHENTICATED, PERMISSION_DENIED, FAILED_PRECONDITION -> false;
                default -> throw unavailable();
            };
        }
    }

    private Snapshot snapshot(InternalJwtClaims actor, long groupId, String permission) {
        return reads.execute(
                status -> {
                    fences.requireUserReadable(actor.userId());
                    fences.requireGroupReadable(groupId);
                    var profiles =
                            jdbc.query(
                                    """
SELECT p.academic_revision,p.applied_auth_roles_version,p.archived_at IS NULL AS visible,
  g.academic_revision AS group_revision,
  EXISTS(SELECT 1 FROM academic_role_eligibility e WHERE e.user_id=p.id
    AND e.role='STUDENT' AND e.status='ACTIVE' AND e.group_id=g.id) AS student,
  EXISTS(SELECT 1 FROM academic_role_eligibility e WHERE e.user_id=p.id
    AND e.role='HEADMAN' AND e.status='ACTIVE' AND e.group_id=g.id) AS headman,
  EXISTS(SELECT 1 FROM headman_assistants h WHERE h.student_id=p.id
    AND h.group_id=g.id AND h.is_active
    AND EXISTS(SELECT 1 FROM unnest(h.permissions) permission
      WHERE lower(trim(permission))=?)) AS helper
FROM academic_profiles p JOIN groups g ON g.id=? WHERE p.id=?
""",
                                    (rs, n) -> {
                                        if (rs.getLong("applied_auth_roles_version")
                                                        != actor.rolesVersion()
                                                || rs.getLong("academic_revision") <= 0
                                                || rs.getLong("group_revision") <= 0)
                                            throw unavailable();
                                        boolean student = rs.getBoolean("student");
                                        boolean headman = rs.getBoolean("headman");
                                        boolean helper = rs.getBoolean("helper");
                                        boolean allowed =
                                                rs.getBoolean("visible")
                                                        && student
                                                        && ("HEADMAN".equalsIgnoreCase(actor.role())
                                                                ? headman
                                                                : helper);
                                        return new Snapshot(
                                                rs.getLong("academic_revision"),
                                                rs.getLong("group_revision"),
                                                rs.getBoolean("visible"),
                                                student,
                                                headman,
                                                helper,
                                                allowed);
                                    },
                                    permission.toLowerCase(Locale.ROOT),
                                    groupId,
                                    actor.userId());
                    if (profiles.size() > 1) throw unavailable();
                    return profiles.isEmpty() ? null : profiles.getFirst();
                });
    }

    private record Snapshot(
            long profileRevision,
            long groupRevision,
            boolean visible,
            boolean student,
            boolean headman,
            boolean helper,
            boolean allowed) {}

    private static StatusRuntimeException unavailable() {
        return Status.UNAVAILABLE
                .withDescription("assistant permission authority unavailable")
                .asRuntimeException();
    }
}
