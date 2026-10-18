package ru.rutcampustrack.academic.integration;

import static org.assertj.core.api.Assertions.*;

import io.grpc.Status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import ru.rutcampustrack.academic.contract.dto.group.CreateGroupRequest;
import ru.rutcampustrack.academic.contract.dto.user.*;
import ru.rutcampustrack.academic.contract.enums.*;
import ru.rutcampustrack.academic.entity.*;
import ru.rutcampustrack.academic.group.GroupService;
import ru.rutcampustrack.academic.grpc.AttendanceUserImpactGrpcClient;
import ru.rutcampustrack.academic.history.HistoricalMembershipService;
import ru.rutcampustrack.academic.identityoperation.JdbcAcademicIdentityOperationStore;
import ru.rutcampustrack.academic.integration.AcademicIdentityIntegrationFixture.SelectedActor;
import ru.rutcampustrack.academic.repository.*;
import ru.rutcampustrack.academic.security.AcademicLiveSessionClient;
import ru.rutcampustrack.academic.user.*;
import ru.rutcampustrack.auth.event.PasswordChangedOutbox;
import ru.rutcampustrack.auth.session.jdbc.JdbcSessionAuthority;
import ru.rutcampustrack.auth.session.model.AuthRole;
import ru.rutcampustrack.auth.session.port.SessionStatePort;
import ru.rutcampustrack.authoperations.grpc.IdentityOperationPhase;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

/**
 * The two original history/archive scenarios, now through actual own-DB writers and Auth admission.
 */
class HistoricalMembershipArchiveIT extends AbstractAcademicIntegrationTest {
    private static final ZoneId MOSCOW = ZoneId.of("Europe/Moscow");
    @Autowired GroupService groupService;
    @Autowired UserService userService;
    @Autowired UserArchiveService archives;
    @Autowired UserRepository userRepository;
    @Autowired StudentGroupHistoryRepository historyRepository;
    @Autowired HistoricalMembershipService historicalMembershipService;
    @Autowired AcademicLiveSessionClient liveSessions;
    @Autowired JdbcAcademicIdentityOperationStore operations;
    @Autowired com.fasterxml.jackson.databind.ObjectMapper json;
    @MockitoBean AttendanceUserImpactGrpcClient attendanceImpact;
    private SelectedActor admin;
    private JdbcTemplate academic;
    private JdbcTemplate auth;
    private long testSemesterId;
    private final Map<UUID, Long> initialSessionVersions = new HashMap<>();
    private final Map<UUID, SelectedActor> selectedSessions = new HashMap<>();
    private int groupNumber;

    @BeforeEach
    void currentOwnedFixture() {
        academic = identityFixture.academicJdbc();
        auth = identityFixture.authJdbc();
        admin = identityFixture.select(identityFixture.canonical().adminId(), AuthRole.ADMIN);
        LocalDate today = LocalDate.now(MOSCOW);
        var writable =
                academic.queryForList(
                        "SELECT id FROM semesters WHERE date_from<=? AND date_to>=? AND NOT"
                                + " is_archived AND archive_transition='NONE' AND NOT"
                                + " archive_release_pending ORDER BY id LIMIT 1",
                        Long.class,
                        today,
                        today);
        academic.update("UPDATE semesters SET is_active=false WHERE is_active");
        testSemesterId =
                writable.isEmpty()
                        ? academic.queryForObject(
                                "INSERT INTO semesters(name,date_from,date_to,is_active,created_at)"
                                        + " VALUES(?,?,?,TRUE,NOW()) RETURNING id",
                                Long.class,
                                "History archive " + UUID.randomUUID(),
                                today.minusDays(7),
                                today.plusDays(7))
                        : writable.getFirst();
        academic.update("UPDATE semesters SET is_active=true WHERE id=?", testSemesterId);
        org.mockito.Mockito.when(attendanceImpact.preview(org.mockito.ArgumentMatchers.anyLong()))
                .thenAnswer(
                        call ->
                                ru.rutcampustrack.attendance.grpc.UserImpactSnapshot.newBuilder()
                                        .setUserId(call.<Long>getArgument(0))
                                        .setObservedAtEpochMs(System.currentTimeMillis())
                                        .setSnapshotDigest("22".repeat(32))
                                        .build());
    }

    @Test
    void serviceCreatedEnrollmentAndTransferAreDatedAndComplete() {
        Group source = createGroup();
        Group destination = createGroup();
        User student = createStudent(source.getId());

        long initialRolesVersion = rolesVersion(student.getId());
        assertThat(grantCount(student.getId(), "student")).isEqualTo(1);
        assertThat(grantStatus(student.getId(), "student")).isEqualTo("active");
        updateRoleGrant(
                student.getId(),
                "TEACHER",
                new RoleGrantUpdateRequest(
                        RoleGrantStatus.ACTIVE, null, "E-HISTORY-ARCHIVE", null));
        long withUnrelatedGrantRolesVersion = rolesVersion(student.getId());
        assertThat(withUnrelatedGrantRolesVersion).isGreaterThan(initialRolesVersion);
        assertThat(grantStatus(student.getId(), "teacher")).isEqualTo("active");

        patchUser(
                student.getId(),
                new PatchUserRequest(null, null, null, true, null, null, null, null));
        long assignedRolesVersion = rolesVersion(student.getId());
        assertThat(assignedRolesVersion).isGreaterThan(initialRolesVersion);
        assertThat(grantStatus(student.getId(), "headman")).isEqualTo("active");
        assertThat(grantGroupId(student.getId(), "headman")).isEqualTo(source.getId());

        assertThatThrownBy(
                        () ->
                                patchUser(
                                        student.getId(),
                                        new PatchUserRequest(
                                                null, null, null, false, null, null, null, null)))
                .isInstanceOf(ru.rutcampustrack.academic.exception.ConflictException.class);
        assertThat(rolesVersion(student.getId())).isEqualTo(assignedRolesVersion);
        assertThat(grantStatus(student.getId(), "headman")).isEqualTo("active");

        var unchangedState =
                academic.queryForMap(
                        """
SELECT (SELECT academic_revision FROM academic_profiles WHERE id=?) AS profile_revision,
 (SELECT academic_revision FROM groups WHERE id=?) AS group_revision,
 (SELECT count(*) FROM student_group_history WHERE user_id=?) AS history_count,
 (SELECT count(*) FROM academic_outbox) AS outbox_count
""",
                        student.getId(),
                        source.getId(),
                        student.getId());
        long catalogBeforeNoop =
                auth.queryForObject(
                        "SELECT version FROM auth_identity_catalog_clock WHERE singleton=1",
                        Long.class);
        UUID noopId = UUID.randomUUID();
        var sameHeadman = new PatchUserRequest(null, null, null, true, null, null, null, null);
        try (var ignored = identityFixture.request(admin, noopId)) {
            userService.patchUser(student.getId(), sameHeadman);
        }
        var noop = operations.find(noopId.toString());
        assertThat(noop.phase().name()).isEqualTo("SUCCEEDED");
        assertThat(noop.prepared().getPhase())
                .isEqualTo(IdentityOperationPhase.IDENTITY_OPERATION_PHASE_NOOP);
        assertThat(noop.receipts()).isEmpty();
        assertThat(noop.prepared().getTargets(0).getExpectedAcademicRevision())
                .isEqualTo(((Number) unchangedState.get("profile_revision")).longValue());
        try (var ignored = identityFixture.request(admin, noopId)) {
            userService.patchUser(student.getId(), sameHeadman);
        }
        assertThat(operations.find(noopId.toString()).prepared()).isEqualTo(noop.prepared());
        assertThat(
                        academic.queryForMap(
                                """
SELECT (SELECT academic_revision FROM academic_profiles WHERE id=?) AS profile_revision,
 (SELECT academic_revision FROM groups WHERE id=?) AS group_revision,
 (SELECT count(*) FROM student_group_history WHERE user_id=?) AS history_count,
 (SELECT count(*) FROM academic_outbox) AS outbox_count
""",
                                student.getId(),
                                source.getId(),
                                student.getId()))
                .isEqualTo(unchangedState);
        assertThat(
                        auth.queryForObject(
                                "SELECT version FROM auth_identity_catalog_clock WHERE singleton=1",
                                Long.class))
                .isEqualTo(catalogBeforeNoop);
        assertThat(
                        auth.queryForObject(
                                "SELECT count(*) FROM auth_headman_noop_receipts WHERE"
                                        + " operation_id=?",
                                Long.class,
                                noopId))
                .isOne();
        long reassignedRolesVersion = rolesVersion(student.getId());
        assertThat(reassignedRolesVersion).isEqualTo(assignedRolesVersion);

        LocalDate semesterStart = LocalDate.now(MOSCOW).minusDays(7);
        assertThat(
                        historicalMembershipService
                                .readRoster(source.getId(), semesterStart, testSemesterId)
                                .students())
                .extracting(User::getId)
                .containsExactly(student.getId());
        assertThat(historicalMembershipService.readMemberUserIds(source.getId(), semesterStart))
                .containsExactly(student.getId());

        String transferReason = "historical test transfer";
        transferStudent(
                student.getId(), new TransferStudentRequest(destination.getId(), transferReason));

        LocalDate transferDate = LocalDate.now(MOSCOW);
        assertThat(
                        historicalMembershipService
                                .readRoster(
                                        source.getId(), transferDate.minusDays(1), testSemesterId)
                                .students())
                .extracting(User::getId)
                .containsExactly(student.getId());
        assertThat(
                        historicalMembershipService.readMemberUserIds(
                                source.getId(), transferDate.minusDays(1)))
                .containsExactly(student.getId());
        assertThat(
                        historicalMembershipService
                                .readRoster(source.getId(), transferDate, testSemesterId)
                                .students())
                .isEmpty();
        assertThat(historicalMembershipService.readMemberUserIds(source.getId(), transferDate))
                .isEmpty();
        assertThat(
                        historicalMembershipService.readMemberUserIds(
                                destination.getId(), transferDate.minusDays(1)))
                .isEmpty();
        assertThat(
                        historicalMembershipService
                                .readRoster(destination.getId(), transferDate, testSemesterId)
                                .students())
                .extracting(User::getId)
                .containsExactly(student.getId());
        assertThat(historicalMembershipService.readMemberUserIds(destination.getId(), transferDate))
                .containsExactly(student.getId());

        long transferredRolesVersion = rolesVersion(student.getId());
        assertThat(transferredRolesVersion).isGreaterThan(reassignedRolesVersion);
        assertThat(grantStatus(student.getId(), "student")).isEqualTo("active");
        assertThat(grantGroupId(student.getId(), "student")).isEqualTo(destination.getId());
        assertThat(grantStatus(student.getId(), "headman")).isEqualTo("suspended");
        assertThat(grantGroupId(student.getId(), "headman")).isEqualTo(source.getId());
        assertThat(grantStatus(student.getId(), "teacher")).isEqualTo("active");

        protectedArchive(student.getId());
        LocalDate archiveDate = LocalDate.now(MOSCOW);
        long archivedRolesVersion = rolesVersion(student.getId());
        assertThat(archivedRolesVersion).isGreaterThan(transferredRolesVersion);
        assertThat(grantStatus(student.getId(), "student")).isEqualTo("archived");
        assertThat(grantStatus(student.getId(), "headman")).isEqualTo("archived");
        assertThat(grantStatus(student.getId(), "teacher")).isEqualTo("archived");
        assertThat(
                        userRepository
                                .findByIdIncludingArchived(student.getId())
                                .orElseThrow()
                                .getGroupId())
                .isNull();

        assertThat(
                        historicalMembershipService
                                .readRoster(
                                        source.getId(), transferDate.minusDays(1), testSemesterId)
                                .students())
                .extracting(User::getId)
                .containsExactly(student.getId());
        assertThat(
                        historicalMembershipService
                                .readRoster(destination.getId(), archiveDate, testSemesterId)
                                .students())
                .isEmpty();
        assertThat(historicalMembershipService.readMemberUserIds(destination.getId(), archiveDate))
                .isEmpty();
        assertThat(
                        historicalMembershipService
                                .readRoster(
                                        destination.getId(),
                                        archiveDate.plusDays(1),
                                        testSemesterId)
                                .students())
                .isEmpty();

        List<StudentGroupHistory> history =
                historyRepository.findByUserIdOrderByJoinedAtDesc(student.getId());
        assertThat(history).hasSize(2);
        assertThat(history).allSatisfy(row -> assertThat(row.getJoinedAt()).isNotNull());
        assertThat(history).extracting(StudentGroupHistory::getReason).containsOnly(transferReason);
        assertThat(
                        history.stream()
                                .filter(row -> row.getGroupId().equals(source.getId()))
                                .findFirst()
                                .orElseThrow()
                                .getLeftAt())
                .isEqualTo(transferDate);
        StudentGroupHistory destinationHistory =
                history.stream()
                        .filter(row -> row.getGroupId().equals(destination.getId()))
                        .findFirst()
                        .orElseThrow();
        assertThat(destinationHistory.getJoinedAt()).isEqualTo(transferDate);
        assertThat(destinationHistory.getLeftAt()).isEqualTo(archiveDate);
    }

    @Test
    void revokedSelectionsStayClearedAfterImmediateRestoreAndArchivePreservesGrantHistory() {
        Group group = createGroup();
        User student = createStudent(group.getId());
        updateRoleGrant(
                student.getId(),
                "TEACHER",
                new RoleGrantUpdateRequest(
                        RoleGrantStatus.ACTIVE, null, "E-HISTORY-ARCHIVE", null));
        patchUser(
                student.getId(),
                new PatchUserRequest(null, null, null, true, null, null, null, null));
        long studentGrant = grantId(student.getId(), "student");
        long headmanGrant = grantId(student.getId(), "headman");
        long teacherGrant = grantId(student.getId(), "teacher");
        UUID studentSession = createSession(student.getId(), studentGrant);
        UUID headmanSession = createSession(student.getId(), headmanGrant);
        UUID teacherSession = createSession(student.getId(), teacherGrant);

        updateRoleGrant(
                student.getId(),
                "STUDENT",
                new RoleGrantUpdateRequest(RoleGrantStatus.SUSPENDED, null, null, null));

        // Read the same real SIDs through Auth; no direct fixture session mutation.
        for (UUID session : List.of(studentSession, headmanSession, teacherSession)) {
            requireOldSessionDenied(session);
            refreshSelection(session);
            requireOldSessionDenied(session);
        }
        assertSelection(studentSession, null, 2);
        assertSelection(headmanSession, null, 2);
        assertSelection(teacherSession, teacherGrant, 1);
        assertThat(grantStatus(student.getId(), "teacher")).isEqualTo("active");
        updateRoleGrant(
                student.getId(),
                "STUDENT",
                new RoleGrantUpdateRequest(RoleGrantStatus.SUSPENDED, null, null, null));
        assertSelection(studentSession, null, 2);
        updateRoleGrant(
                student.getId(),
                "STUDENT",
                new RoleGrantUpdateRequest(RoleGrantStatus.ACTIVE, null, null, null));
        assertThat(grantId(student.getId(), "student")).isEqualTo(studentGrant);
        for (UUID session : List.of(studentSession, headmanSession, teacherSession))
            refreshSelection(session);
        assertSelection(studentSession, null, 2);
        assertSelection(headmanSession, null, 2);
        assertSelection(teacherSession, teacherGrant, 1);

        protectedArchive(student.getId());

        for (UUID session : List.of(studentSession, headmanSession, teacherSession)) {
            requireOldSessionDenied(session);
            assertThat(
                            auth.queryForObject(
                                    "SELECT revoked_at IS NOT NULL FROM auth_sessions WHERE sid=?",
                                    Boolean.class,
                                    session))
                    .isTrue();
        }
        assertSelection(headmanSession, null, 2);
        assertSelection(teacherSession, null, 2);
        assertSelection(studentSession, null, 2);
        assertThat(grantId(student.getId(), "teacher")).isEqualTo(teacherGrant);
        assertThat(grantStatus(student.getId(), "teacher")).isEqualTo("archived");
        assertThat(historyRepository.findByUserIdOrderByJoinedAtAscIdAsc(student.getId()))
                .hasSize(1)
                .allSatisfy(history -> assertThat(history.getLeftAt()).isNotNull());
    }

    private Group createGroup() {
        try (var ignored = identityFixture.request(admin)) {
            return groupService.createGroup(
                    new CreateGroupRequest((groupNumber++ == 0 ? "УИТ" : "УВП") + "-119"));
        }
    }

    private User createStudent(Long groupId) {
        long id =
                identityFixture
                        .seedUser(
                                new CreateUserRequest(
                                        "L5B",
                                        "Student",
                                        null,
                                        UserRole.STUDENT,
                                        groupId,
                                        null,
                                        900000000333L),
                                admin)
                        .userId();
        return userRepository.findByIdIncludingArchived(id).orElseThrow();
    }

    private User patchUser(Long user, PatchUserRequest request) {
        try (var ignored = identityFixture.request(admin)) {
            return userService.patchUser(user, request);
        }
    }

    private User transferStudent(Long user, TransferStudentRequest request) {
        try (var ignored = identityFixture.request(admin)) {
            return userService.transferStudent(user, request);
        }
    }

    private User updateRoleGrant(Long user, String role, RoleGrantUpdateRequest request) {
        try (var ignored = identityFixture.request(admin)) {
            return userService.updateRoleGrant(user, role, request);
        }
    }

    private void protectedArchive(long target) {
        UUID operation = UUID.randomUUID();
        try (var ignored = identityFixture.request(admin, operation)) {
            var preview = archives.preview(target);
            assertThat(preview.requiresPassword())
                    .isTrue(); // Both original scenarios retain an active TEACHER grant.
            archives.archive(
                    target,
                    new UserArchiveModels.ArchiveRequest(
                            operation,
                            preview.previewDigest(),
                            AcademicIdentityIntegrationFixture.FIRST_FULL_TEST_PASSWORD));
        }
    }

    private long rolesVersion(Long user) {
        return auth.queryForObject(
                "SELECT roles_version FROM auth_identities WHERE id=?", Long.class, user);
    }

    private long grantId(Long user, String role) {
        return auth.queryForObject(
                "SELECT id FROM user_role_grants WHERE user_id=? AND role=?",
                Long.class,
                user,
                role);
    }

    private int grantCount(Long user, String role) {
        return auth.queryForObject(
                "SELECT count(*) FROM user_role_grants WHERE user_id=? AND role=?",
                Integer.class,
                user,
                role);
    }

    private String grantStatus(Long user, String role) {
        return auth.queryForObject(
                "SELECT status FROM user_role_grants WHERE user_id=? AND role=?",
                String.class,
                user,
                role);
    }

    private Long grantGroupId(Long user, String role) {
        return auth.queryForObject(
                "SELECT group_id FROM user_role_grants WHERE user_id=? AND role=?",
                Long.class,
                user,
                role);
    }

    private UUID createSession(Long user, long grantId) {
        String role =
                auth.queryForObject(
                        "SELECT role FROM user_role_grants WHERE id=? AND user_id=?",
                        String.class,
                        grantId,
                        user);
        var selected =
                identityFixture.select(user, AuthRole.valueOf(role.toUpperCase(Locale.ROOT)));
        assertThat(
                        auth.queryForObject(
                                "SELECT active_role_grant_id FROM auth_sessions WHERE sid=?",
                                Long.class,
                                selected.sessionId()))
                .isEqualTo(grantId);
        initialSessionVersions.put(selected.sessionId(), selected.sessionVersion());
        selectedSessions.put(selected.sessionId(), selected);
        return selected.sessionId();
    }

    private void refreshSelection(UUID session) {
        var actor = selectedSessions.get(session);
        var authority =
                new JdbcSessionAuthority(
                        auth,
                        identityFixture.authTransactions().getTransactionManager(),
                        new PasswordChangedOutbox(auth, json));
        var snapshot =
                authority.snapshot(
                        new SessionStatePort.SnapshotCommand(
                                actor.userId(), session, Instant.now()));
        assertThat(snapshot.succeeded()).isTrue();
    }

    private void requireOldSessionDenied(UUID session) {
        assertThatThrownBy(() -> liveSessions.admit(selectedSessions.get(session).claims()))
                .satisfies(
                        error ->
                                assertThat(Status.fromThrowable(error).getCode())
                                        .isEqualTo(Status.Code.PERMISSION_DENIED));
    }

    private void assertSelection(UUID session, Long grant, long expectedRelativeVersion) {
        assertThat(
                        auth.queryForObject(
                                "SELECT active_role_grant_id FROM auth_sessions WHERE sid=?",
                                Long.class,
                                session))
                .isEqualTo(grant);
        // Real Auth selection establishes its own SV; retain the original exact one-step
        // invalidation oracle.
        assertThat(
                        auth.queryForObject(
                                "SELECT session_version FROM auth_sessions WHERE sid=?",
                                Long.class,
                                session))
                .isEqualTo(initialSessionVersions.get(session) + expectedRelativeVersion - 1);
    }
}
