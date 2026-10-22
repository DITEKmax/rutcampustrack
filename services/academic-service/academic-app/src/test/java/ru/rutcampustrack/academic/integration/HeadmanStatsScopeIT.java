package ru.rutcampustrack.academic.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.grpc.Status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import ru.rutcampustrack.academic.contract.dto.group.CreateAdminGroupRequest;
import ru.rutcampustrack.academic.contract.dto.user.CreateUserRequest;
import ru.rutcampustrack.academic.contract.dto.user.RoleGrantUpdateRequest;
import ru.rutcampustrack.academic.contract.dto.user.TransferStudentRequest;
import ru.rutcampustrack.academic.contract.dto.user.UserArchiveModels.ArchiveRequest;
import ru.rutcampustrack.academic.contract.enums.RoleGrantStatus;
import ru.rutcampustrack.academic.contract.enums.UserRole;
import ru.rutcampustrack.academic.group.GroupService;
import ru.rutcampustrack.academic.grpc.AttendanceUserImpactGrpcClient;
import ru.rutcampustrack.academic.grpc.HeadmanStatsScopeReadService;
import ru.rutcampustrack.academic.grpc.HeadmanStatsScopeRequest;
import ru.rutcampustrack.academic.identityoperation.AcademicUserIdentityOperations;
import ru.rutcampustrack.academic.integration.AcademicIdentityIntegrationFixture.SelectedActor;
import ru.rutcampustrack.academic.user.UserArchiveService;
import ru.rutcampustrack.attendance.grpc.UserImpactSnapshot;
import ru.rutcampustrack.auth.session.model.AuthRole;
import ru.rutcampustrack.shared.security.InternalJwtClaims;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Actual split-DB authority/counts and archive reads; the shared fixture resets committed state.
 * Attendance impact transport alone is doubled for the owned archived cohort member.
 */
class HeadmanStatsScopeIT extends AbstractAcademicIntegrationTest {
    @Autowired private JdbcTemplate jdbc;
    @Autowired private HeadmanStatsScopeReadService service;
    @Autowired private jakarta.persistence.EntityManager entityManager;
    @Autowired private GroupService groups;
    @Autowired private AcademicUserIdentityOperations identityOperations;
    @Autowired private UserArchiveService archives;
    @MockitoBean private AttendanceUserImpactGrpcClient attendanceImpact;
    private SelectedActor admin;
    private int nextGroup = 1;
    private long nextTelegram = 740000000001L;

    @BeforeEach
    void selectCurrentAdmin() {
        admin = identityFixture.select(identityFixture.canonical().adminId(), AuthRole.ADMIN);
    }

    @Test
    void selectedArchivedReadCountsCurrentGrantsAndRejectsForeignOrRevokedActor() {
        long groupId = group();
        long otherGroup = group();
        long headman = student(groupId, "active");
        long assistant = student(groupId, "active");
        student(groupId, "suspended");
        student(groupId, "archived");
        long historicalMember = student(otherGroup, "active");
        long foreignStudent = student(otherGroup, "active");
        assignHeadman(headman, groupId);
        LocalDate from =
                jdbc.queryForObject(
                        "SELECT COALESCE(max(date_to), CURRENT_DATE) + 366 FROM semesters",
                        LocalDate.class);
        long semesterId =
                jdbc.queryForObject(
                        "INSERT INTO semesters (name, date_from, date_to, is_active, is_archived,"
                                + " created_at) VALUES (?, ?, ?, false, true, NOW()) RETURNING id",
                        Long.class,
                        "Headman scope " + UUID.randomUUID(),
                        from,
                        from.plusDays(20));
        var request =
                HeadmanStatsScopeRequest.newBuilder()
                        .setGroupId(groupId)
                        .setSemesterId(semesterId)
                        .build();
        var actor = claims(headman, "HEADMAN", groupId);
        var result = service.read(actor, request);
        assertThat(result.getSelectedSemester().getId()).isEqualTo(semesterId);
        assertThat(result.getSelectedSemester().getArchived()).isTrue();
        assertThat(result.getSelectedSemester().getDateFrom()).isEqualTo(from.toString());
        assertThat(result.getMembershipActive()).isEqualTo(2);
        assertThat(result.getMembershipTotal()).isEqualTo(3);
        assertThat(result.getSemestersList())
                .anyMatch(value -> value.getId() == semesterId && value.getArchived());
        // A current grant cannot invent membership before a late joiner entered this group.
        assertDenied(
                () -> service.read(actor, request.toBuilder().setStudentId(assistant).build()),
                Status.Code.NOT_FOUND);
        jdbc.update(
                "INSERT INTO student_group_history (user_id, group_id, joined_at, left_at,"
                        + " created_at) VALUES (?, ?, ?, NULL, NOW())",
                assistant,
                groupId,
                from.plusDays(21));
        assertDenied(
                () -> service.read(actor, request.toBuilder().setStudentId(assistant).build()),
                Status.Code.NOT_FOUND);
        jdbc.update(
                "INSERT INTO student_group_history (user_id, group_id, joined_at, left_at,"
                        + " created_at) VALUES (?, ?, ?, ?, NOW())",
                historicalMember,
                groupId,
                from,
                from.plusDays(5));
        assertThat(
                        service.read(
                                        actor,
                                        request.toBuilder().setStudentId(historicalMember).build())
                                .getSelectedSemester()
                                .getId())
                .isEqualTo(semesterId); // no schedule lessons are required for scope
        assertDenied(
                () -> service.read(actor, request.toBuilder().setStudentId(foreignStudent).build()),
                Status.Code.NOT_FOUND);
        jdbc.update(
                "UPDATE student_group_history SET left_at = ? WHERE user_id = ?",
                from,
                historicalMember);
        entityManager.clear();
        assertDenied(
                () ->
                        service.read(
                                actor, request.toBuilder().setStudentId(historicalMember).build()),
                Status.Code.NOT_FOUND);
        assertDenied(
                () -> service.read(actor, request.toBuilder().setGroupId(otherGroup).build()),
                Status.Code.PERMISSION_DENIED);
        assertDenied(
                () ->
                        service.read(
                                actor, request.toBuilder().setSemesterId(Long.MAX_VALUE).build()),
                Status.Code.NOT_FOUND);
        assertDenied(
                () -> service.read(actor, request.toBuilder().setSemesterId(0).build()),
                Status.Code.INVALID_ARGUMENT);
        suspendHeadman(headman);
        assertDenied(() -> service.read(actor, request), Status.Code.PERMISSION_DENIED);
        long assignment =
                jdbc.queryForObject(
                        "INSERT INTO headman_assistants (group_id, student_id, permissions,"
                            + " assigned_by, is_active, assigned_at) VALUES (?, ?,"
                            + " ARRAY['view_stats']::varchar(64)[], ?, true, NOW()) RETURNING id",
                        Long.class,
                        groupId,
                        assistant,
                        headman);
        var assistantActor = claims(assistant, "STUDENT", groupId);
        assertThat(service.read(assistantActor, request).getMembershipTotal()).isEqualTo(3);
        jdbc.update(
                "UPDATE headman_assistants SET permissions ="
                        + " ARRAY['mark_attendance']::varchar(64)[] WHERE id = ?",
                assignment);
        // Each actual RPC gets a fresh persistence context; emulate it after fixture JDBC changes.
        entityManager.clear();
        assertDenied(() -> service.read(assistantActor, request), Status.Code.PERMISSION_DENIED);
        transferStudent(assistant, otherGroup);
        // Keep the original adversarial old-group assignment active after the real transfer.
        jdbc.update(
                "UPDATE headman_assistants SET is_active = true, revoked_at = NULL WHERE id = ?",
                assignment);
        entityManager.clear();
        assertDenied(() -> service.read(assistantActor, request), Status.Code.PERMISSION_DENIED);
        // Also retain the wrong-group denial with a freshly admitted post-transfer session.
        var movedActor = claims(assistant, "STUDENT", otherGroup);
        assertDenied(() -> service.read(movedActor, request), Status.Code.PERMISSION_DENIED);
    }

    @Test
    void admittedTargetRankUsesCurrentStudentCohortAndRevocationStillDeniesTheRead() {
        long groupId = group(), foreignGroup = group();
        long headman = student(groupId, "active"),
                target = student(groupId, "active"),
                peer = student(groupId, "active");
        long suspended = student(groupId, "suspended"), foreign = student(foreignGroup, "active");
        assignHeadman(headman, groupId);
        LocalDate from =
                jdbc.queryForObject(
                        "SELECT COALESCE(max(date_to), CURRENT_DATE) + 366 FROM semesters",
                        LocalDate.class);
        long semester =
                jdbc.queryForObject(
                        "INSERT INTO semesters"
                            + " (name,date_from,date_to,is_active,is_archived,created_at) VALUES"
                            + " (?, ?, ?, false, true, NOW()) RETURNING id",
                        Long.class,
                        "Target rank " + UUID.randomUUID(),
                        from,
                        from.plusDays(20));
        for (long user : new long[] {target, peer, suspended, foreign}) {
            jdbc.update(
                    "INSERT INTO student_group_history"
                            + " (user_id,group_id,joined_at,left_at,created_at) VALUES (?, ?, DATE"
                            + " '2000-01-01', NULL, NOW())",
                    user,
                    user == foreign ? foreignGroup : groupId);
        }
        var actor = claims(headman, "HEADMAN", groupId);
        var request =
                HeadmanStatsScopeRequest.newBuilder()
                        .setGroupId(groupId)
                        .setSemesterId(semester)
                        .setStudentId(target)
                        .build();
        var rank = service.read(actor, request).getStudentRankScope();
        assertThat(rank.getStudentId()).isEqualTo(target);
        assertThat(rank.getSemesterId()).isEqualTo(semester);
        assertThat(rank.getRankGroupId()).isEqualTo(groupId);
        assertThat(rank.getRankEligible()).isTrue();
        assertThat(rank.getActiveRosterUserIdsList()).containsExactly(target, peer);
        assertThat(rank.getOwnMembershipSegmentsList())
                .allMatch(segment -> segment.getGroupId() == groupId);
        var suspendedRank =
                service.read(actor, request.toBuilder().setStudentId(suspended).build())
                        .getStudentRankScope();
        assertThat(suspendedRank.getRankEligible()).isFalse();
        assertThat(suspendedRank.getActiveRosterUserIdsList()).isEmpty();
        assertDenied(
                () -> service.read(actor, request.toBuilder().setStudentId(foreign).build()),
                Status.Code.NOT_FOUND);
        suspendHeadman(headman);
        entityManager.clear();
        assertDenied(() -> service.read(actor, request), Status.Code.PERMISSION_DENIED);
    }

    private long group() {
        return groups.createAdminGroup(new CreateAdminGroupRequest("СТС", "11" + nextGroup++, 4))
                .getId();
    }

    private long student(long groupId, String status) {
        long id =
                identityFixture
                        .seedUser(
                                new CreateUserRequest(
                                        "Тестов",
                                        "Студент",
                                        null,
                                        UserRole.STUDENT,
                                        groupId,
                                        null,
                                        nextTelegram++),
                                admin)
                        .userId();
        if ("archived".equals(status)) {
            org.mockito.Mockito.when(attendanceImpact.preview(id))
                    .thenReturn(
                            UserImpactSnapshot.newBuilder()
                                    .setUserId(id)
                                    .setObservedAtEpochMs(System.currentTimeMillis())
                                    .setSnapshotDigest("0".repeat(64))
                                    .build());
            UUID operation = UUID.randomUUID();
            try (var ignored = identityFixture.request(admin, operation)) {
                var preview = archives.preview(id);
                archives.archive(id, new ArchiveRequest(operation, preview.previewDigest(), null));
            }
        } else if (!"active".equals(status)) {
            studentStatus(id, RoleGrantStatus.valueOf(status.toUpperCase(java.util.Locale.ROOT)));
        }
        // CREATE establishes membership. These cases deliberately build absence/late/dated
        // Academic history below, without changing the actual Auth grant or its projection.
        jdbc.update("DELETE FROM student_group_history WHERE user_id = ?", id);
        return id;
    }

    private void assignHeadman(long user, long group) {
        try (var ignored = identityFixture.request(admin)) {
            identityOperations.headman(group, user, null, false, null);
        }
    }

    private void studentStatus(long user, RoleGrantStatus status) {
        try (var ignored = identityFixture.request(admin)) {
            identityOperations.role(
                    user, "STUDENT", new RoleGrantUpdateRequest(status, null, null, null));
        }
    }

    private void suspendHeadman(long user) {
        // Restore the original STUDENT-active/HEADMAN-suspended snapshot through real commands.
        studentStatus(user, RoleGrantStatus.SUSPENDED);
        studentStatus(user, RoleGrantStatus.ACTIVE);
    }

    private void transferStudent(long user, long group) {
        // The owned late-join row has served its assertions; a real current transfer must not
        // close a membership whose fixture start is still in the future.
        jdbc.update(
                "UPDATE student_group_history SET joined_at = LEAST(joined_at, ?) WHERE user_id = ?"
                        + " AND left_at IS NULL",
                LocalDate.now(java.time.ZoneId.of("Europe/Moscow")),
                user);
        try (var ignored = identityFixture.request(admin)) {
            identityOperations.transfer(
                    user, new TransferStudentRequest(group, "Stats fixture transfer"));
        }
    }

    private InternalJwtClaims claims(long userId, String role, long groupId) {
        return identityFixture.select(userId, AuthRole.valueOf(role)).claims();
    }

    private static void assertDenied(Runnable request, Status.Code expected) {
        assertThatThrownBy(request::run)
                .satisfies(
                        error ->
                                assertThat(Status.fromThrowable(error).getCode())
                                        .isEqualTo(expected));
    }
}
