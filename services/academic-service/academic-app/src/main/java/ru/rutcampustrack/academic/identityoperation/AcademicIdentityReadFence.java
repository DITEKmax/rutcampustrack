package ru.rutcampustrack.academic.identityoperation;

import io.grpc.Status;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Protected readers cannot expose a group's locally committed, not-yet-published identity facts.
 */
@Component
public class AcademicIdentityReadFence {
    private final JdbcTemplate jdbc;

    public AcademicIdentityReadFence(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void requireGroupReadable(long groupId) {
        if (groupId <= 0) throw Status.INVALID_ARGUMENT.asRuntimeException();
        Boolean fenced =
                jdbc.queryForObject(
                        """
SELECT EXISTS(SELECT 1 FROM academic_identity_operation_groups g
  JOIN academic_identity_operations o ON o.operation_id=g.operation_id
 WHERE g.group_id=? AND g.fenced AND o.phase<>'SUCCEEDED')
OR EXISTS(SELECT 1 FROM academic_group_availability_guards WHERE group_id=?)
OR EXISTS(SELECT 1 FROM group_deletion_operations WHERE group_id=? AND phase NOT IN ('COMPLETED','CANCELLED'))
""",
                        Boolean.class,
                        groupId,
                        groupId,
                        groupId);
        if (!Boolean.FALSE.equals(fenced)) {
            throw Status.UNAVAILABLE
                    .withDescription("group identity operation is pending")
                    .asRuntimeException();
        }
    }

    public void requireUserReadable(long userId) {
        if (userId <= 0) throw Status.INVALID_ARGUMENT.asRuntimeException();
        requireUnfenced(
                jdbc.queryForObject(
                        "SELECT EXISTS(SELECT 1 FROM academic_identity_operation_targets t JOIN"
                                + " academic_identity_operations o ON o.operation_id=t.operation_id"
                                + " WHERE t.user_id=? AND t.fenced AND o.phase<>'SUCCEEDED') OR"
                                + " EXISTS(SELECT 1 FROM academic_group_availability_headmen WHERE"
                                + " user_id=?)",
                        Boolean.class,
                        userId,
                        userId));
    }

    /** The unscoped administrator registry includes every identity/group domain scope. */
    public void requireRegistryReadable() {
        requireUnfenced(
                jdbc.queryForObject(
                        "SELECT EXISTS(SELECT 1 FROM academic_identity_operation_targets t JOIN"
                            + " academic_identity_operations o ON o.operation_id=t.operation_id"
                            + " WHERE t.fenced AND o.phase<>'SUCCEEDED') OR EXISTS(SELECT 1 FROM"
                            + " academic_identity_operation_groups g JOIN"
                            + " academic_identity_operations o ON o.operation_id=g.operation_id"
                            + " WHERE g.fenced AND o.phase<>'SUCCEEDED') OR EXISTS(SELECT 1 FROM"
                            + " academic_group_availability_guards) OR EXISTS(SELECT 1 FROM"
                            + " academic_group_availability_headmen) OR EXISTS(SELECT 1 FROM"
                            + " group_deletion_operations WHERE phase NOT IN"
                            + " ('COMPLETED','CANCELLED'))",
                        Boolean.class));
    }

    private static void requireUnfenced(Boolean fenced) {
        if (!Boolean.FALSE.equals(fenced))
            throw Status.UNAVAILABLE
                    .withDescription("identity operation publication is pending")
                    .asRuntimeException();
    }
}
