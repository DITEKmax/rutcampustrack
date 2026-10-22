package ru.rutcampustrack.academic.botread;

import io.grpc.Status;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import ru.rutcampustrack.academic.grpc.HomeworksForWeekRequest;
import ru.rutcampustrack.academic.grpc.HomeworksForWeekResponse;
import ru.rutcampustrack.academic.identityoperation.AcademicIdentityReadFence;
import ru.rutcampustrack.botreads.grpc.DirectedBotIdentity;

import java.time.LocalDate;
import java.util.function.Function;

/** All eligibility, semester and homework composition uses one own-DB snapshot. */
@Service
public class AcademicBotHomeworkQuery {
    private final JdbcTemplate jdbc;
    private final AcademicIdentityReadFence fences;
    private final TransactionTemplate snapshot;

    public AcademicBotHomeworkQuery(
            JdbcTemplate jdbc,
            AcademicIdentityReadFence fences,
            PlatformTransactionManager transactions) {
        this.jdbc = jdbc;
        this.fences = fences;
        snapshot = new TransactionTemplate(transactions);
        snapshot.setReadOnly(true);
        snapshot.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        snapshot.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        snapshot.setTimeout(3);
    }

    public Result read(
            DirectedBotIdentity identity,
            LocalDate from,
            LocalDate to,
            Function<HomeworksForWeekRequest, HomeworksForWeekResponse> compose) {
        return snapshot.execute(
                ignored -> {
                    Scope scope = scope(identity);
                    var semesters =
                            jdbc.queryForList(
                                    "SELECT id FROM semesters WHERE is_active", Long.class);
                    if (semesters.size() != 1)
                        throw Status.FAILED_PRECONDITION
                                .withDescription("active semester required")
                                .asRuntimeException();
                    var request =
                            HomeworksForWeekRequest.newBuilder()
                                    .setGroupId(scope.groupId())
                                    .setStudentId(identity.getExpectedUserId())
                                    .setSemesterId(semesters.getFirst())
                                    .setDateFrom(from.toString())
                                    .setDateTo(to.toString())
                                    .build();
                    return new Result(scope, compose.apply(request));
                });
    }

    /** A fresh transaction detects a committed transfer/revoke or pending publication. */
    public void requireCurrent(DirectedBotIdentity identity, Scope previous) {
        snapshot.executeWithoutResult(
                ignored -> {
                    if (!previous.equals(scope(identity)))
                        throw Status.FAILED_PRECONDITION
                                .withDescription("current homework eligibility required")
                                .asRuntimeException();
                });
    }

    private Scope scope(DirectedBotIdentity identity) {
        AuthBotReadAuthorityClient.validate(identity);
        fences.requireUserReadable(identity.getExpectedUserId());
        var rows =
                jdbc.query(
                        """
                        SELECT p.academic_revision,p.applied_auth_roles_version,e.group_id,
                               g.academic_revision group_revision
                          FROM academic_profiles p
                          JOIN academic_role_eligibility e ON e.user_id=p.id
                          JOIN groups g ON g.id=e.group_id
                         WHERE p.id=? AND p.archived_at IS NULL AND e.role='STUDENT'
                           AND e.status='ACTIVE' AND g.is_active
                        """,
                        (rs, n) ->
                                new Scope(
                                        rs.getLong("academic_revision"),
                                        rs.getLong("applied_auth_roles_version"),
                                        rs.getLong("group_id"),
                                        rs.getLong("group_revision")),
                        identity.getExpectedUserId());
        if (rows.size() != 1)
            throw Status.PERMISSION_DENIED
                    .withDescription("current active student group required")
                    .asRuntimeException();
        var scope = rows.getFirst();
        if (scope.rolesVersion() != identity.getExpectedRolesVersion())
            throw Status.FAILED_PRECONDITION
                    .withDescription("current roles version required")
                    .asRuntimeException();
        if (scope.revision() <= 0 || scope.groupId() <= 0 || scope.groupRevision() <= 0)
            throw AuthBotReadAuthorityClient.unavailable();
        fences.requireGroupReadable(scope.groupId());
        return scope;
    }

    public record Scope(long revision, long rolesVersion, long groupId, long groupRevision) {}

    public record Result(Scope scope, HomeworksForWeekResponse response) {}
}
