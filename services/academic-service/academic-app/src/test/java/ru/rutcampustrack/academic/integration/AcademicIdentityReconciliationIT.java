package ru.rutcampustrack.academic.integration;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import io.grpc.Status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import ru.rutcampustrack.academic.admin.AdminAccessService;
import ru.rutcampustrack.academic.contract.dto.IdentityOperationHttpModels;
import ru.rutcampustrack.academic.contract.dto.group.CreateGroupRequest;
import ru.rutcampustrack.academic.contract.dto.user.AdminAccessInput;
import ru.rutcampustrack.academic.contract.dto.user.CreateUserRequest;
import ru.rutcampustrack.academic.contract.dto.user.TransferStudentRequest;
import ru.rutcampustrack.academic.contract.enums.UserRole;
import ru.rutcampustrack.academic.entity.User;
import ru.rutcampustrack.academic.group.GroupService;
import ru.rutcampustrack.academic.grpc.AcademicReadService;
import ru.rutcampustrack.academic.identityoperation.AcademicIdentityReconciliationService;
import ru.rutcampustrack.academic.identityoperation.AuthIdentityOperationClient;
import ru.rutcampustrack.academic.user.UserService;
import ru.rutcampustrack.auth.session.model.AuthRole;
import ru.rutcampustrack.authoperations.grpc.CommitIdentityOperationRequest;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/** Actual split DB/Auth admission, with a scheduling seam only before the real Auth commit. */
class AcademicIdentityReconciliationIT extends AbstractAcademicIntegrationTest {
    @Autowired private UserService users;
    @Autowired private GroupService groups;
    @Autowired private AdminAccessService admins;
    @Autowired private AcademicReadService reads;
    @Autowired private AcademicIdentityReconciliationService recovery;
    @MockitoSpyBean private AuthIdentityOperationClient operations;

    @Test
    void peerRosterStaysFencedAfterStagedTransferAndFreshRecoveryNeverRepeatsHistoryOrEvents() {
        var canonical = identityFixture.canonical();
        var full = identityFixture.select(canonical.adminId(), AuthRole.ADMIN);
        long limitedId =
                identityFixture
                        .seedUser(
                                new CreateUserRequest(
                                        "Recovery",
                                        "Actor",
                                        null,
                                        UserRole.ADMIN,
                                        null,
                                        null,
                                        null,
                                        new AdminAccessInput(
                                                "LIMITED", List.of("USERS_VIEW", "USERS_MANAGE"))),
                                full)
                        .userId();
        var limited = identityFixture.select(limitedId, AuthRole.ADMIN);
        long target =
                identityFixture
                        .seedUser(
                                new CreateUserRequest(
                                        "Recovery",
                                        "Student",
                                        null,
                                        UserRole.STUDENT,
                                        canonical.groupId(),
                                        null,
                                        900000000777L),
                                full)
                        .userId();
        long destination;
        try (var scope = identityFixture.request(full)) {
            destination = groups.createGroup(new CreateGroupRequest("УИТ-119")).getId();
        }
        var peer = identityFixture.select(canonical.studentId(), AuthRole.HEADMAN);
        try (var scope = identityFixture.request(peer)) {
            assertThat(reads.fetchGroupMembers(canonical.groupId()))
                    .extracting(User::getId)
                    .contains(target);
        }
        UUID failed = UUID.randomUUID();
        var revoked = new AtomicBoolean();
        doAnswer(
                        call -> {
                            CommitIdentityOperationRequest request = call.getArgument(0);
                            if (request.getOperationId().equals(failed.toString())
                                    && revoked.compareAndSet(false, true)) {
                                // Real public downscope, not a mocked denial or a forged
                                // grant/profile row.
                                try (var scope = identityFixture.request(full)) {
                                    admins.revoke(limitedId, 1L);
                                }
                            }
                            return call.callRealMethod();
                        })
                .when(operations)
                .commit(any());
        try (var scope = identityFixture.request(limited, failed)) {
            assertThatThrownBy(
                            () ->
                                    users.transferStudent(
                                            target,
                                            new TransferStudentRequest(
                                                    destination, "Recovery fixed intent")))
                    .satisfies(
                            error ->
                                    assertThat(Status.fromThrowable(error).getCode())
                                            .isEqualTo(Status.Code.FAILED_PRECONDITION));
        }
        var academic = identityFixture.academicJdbc();
        var auth = identityFixture.authJdbc();
        assertThat(
                        academic.queryForObject(
                                "SELECT phase FROM academic_identity_operations WHERE"
                                    + " operation_id=?",
                                String.class,
                                failed))
                .isEqualTo("FAILED_SAFE");
        assertThat(
                        auth.queryForObject(
                                "SELECT phase FROM auth_identity_operations WHERE operation_id=?",
                                String.class,
                                failed))
                .isEqualTo("FAILED_SAFE");
        assertThat(
                        academic.queryForObject(
                                "SELECT count(*) FROM academic_identity_operation_targets WHERE"
                                    + " operation_id=? AND committed_academic_revision IS NOT NULL",
                                Long.class,
                                failed))
                .isEqualTo(1);
        assertThat(
                        academic.queryForObject(
                                "SELECT group_id FROM academic_role_eligibility WHERE user_id=? AND"
                                    + " role='STUDENT'",
                                Long.class,
                                target))
                .isEqualTo(destination);
        String history =
                academic.queryForObject(
                        "SELECT jsonb_agg(to_jsonb(h) ORDER BY id)::text FROM student_group_history"
                            + " h WHERE user_id=?",
                        String.class,
                        target);
        long events = academic.queryForObject("SELECT count(*) FROM academic_outbox", Long.class);
        long changes =
                academic.queryForObject(
                        "SELECT count(*) FROM dashboard_scale_observations WHERE kind='CHANGE'",
                        Long.class);
        try (var scope = identityFixture.request(peer)) {
            assertThatThrownBy(() -> reads.fetchGroupMembers(canonical.groupId()))
                    .satisfies(
                            error ->
                                    assertThat(Status.fromThrowable(error).getCode())
                                            .isEqualTo(Status.Code.UNAVAILABLE));
        }
        UUID retry = UUID.randomUUID();
        try (var scope = identityFixture.request(full, retry)) {
            assertThat(recovery.reconcile(failed, null).status())
                    .isEqualTo(IdentityOperationHttpModels.Status.SUCCEEDED);
        }
        try (var scope = identityFixture.request(full, retry)) {
            assertThat(recovery.reconcile(failed, null).operationId()).isEqualTo(retry);
        }
        assertThat(
                        academic.queryForObject(
                                "SELECT jsonb_agg(to_jsonb(h) ORDER BY id)::text FROM"
                                    + " student_group_history h WHERE user_id=?",
                                String.class,
                                target))
                .isEqualTo(history);
        assertThat(academic.queryForObject("SELECT count(*) FROM academic_outbox", Long.class))
                .isEqualTo(events);
        assertThat(
                        academic.queryForObject(
                                "SELECT count(*) FROM dashboard_scale_observations WHERE"
                                    + " kind='CHANGE'",
                                Long.class))
                .isEqualTo(changes);
        assertThat(
                        auth.queryForObject(
                                "SELECT group_id FROM user_role_grants WHERE user_id=? AND"
                                    + " role='student' AND status='active'",
                                Long.class,
                                target))
                .isEqualTo(destination);
        assertThat(
                        academic.queryForObject(
                                "SELECT count(*) FROM academic_identity_operation_targets WHERE"
                                    + " user_id=? AND fenced",
                                Long.class,
                                target))
                .isZero();
        try (var scope = identityFixture.request(peer)) {
            assertThat(reads.fetchGroupMembers(canonical.groupId()))
                    .extracting(User::getId)
                    .doesNotContain(target);
        }
        try (var scope = identityFixture.request(full)) {
            assertThat(reads.fetchGroupMembers(destination))
                    .extracting(User::getId)
                    .contains(target);
        }
    }
}
