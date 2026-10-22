package ru.rutcampustrack.academic.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.grpc.Status;
import io.grpc.stub.StreamObserver;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import ru.rutcampustrack.academic.contract.dto.group.CreateAdminGroupRequest;
import ru.rutcampustrack.academic.contract.dto.user.CreateUserRequest;
import ru.rutcampustrack.academic.contract.dto.user.RoleGrantUpdateRequest;
import ru.rutcampustrack.academic.contract.dto.user.TransferStudentRequest;
import ru.rutcampustrack.academic.contract.enums.RoleGrantStatus;
import ru.rutcampustrack.academic.contract.enums.UserRole;
import ru.rutcampustrack.academic.group.GroupService;
import ru.rutcampustrack.academic.grpc.AcademicGrpcServiceImpl;
import ru.rutcampustrack.academic.grpc.HeadmanJournalSemesterStateRequest;
import ru.rutcampustrack.academic.grpc.HeadmanStatsScopeReadService;
import ru.rutcampustrack.academic.grpc.HeadmanStatsScopeRequest;
import ru.rutcampustrack.academic.grpc.SemesterStateResponse;
import ru.rutcampustrack.academic.grpc.SemesterTransition;
import ru.rutcampustrack.academic.grpc.StudentHomeworkTestIdentity;
import ru.rutcampustrack.academic.identityoperation.AcademicUserIdentityOperations;
import ru.rutcampustrack.academic.integration.AcademicIdentityIntegrationFixture.SelectedActor;
import ru.rutcampustrack.auth.session.model.AuthRole;
import ru.rutcampustrack.shared.security.InternalJwtClaims;

import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Real split-DB grants and selected sessions; the shared fixture resets committed state per test.
 */
class HeadmanJournalSemesterStateIT extends AbstractAcademicIntegrationTest {
    @Autowired private JdbcTemplate jdbc;
    @Autowired private AcademicGrpcServiceImpl handler;
    @Autowired private HeadmanStatsScopeReadService statsScope;
    @Autowired private jakarta.persistence.EntityManager entityManager;
    @Autowired private GroupService groups;
    @Autowired private AcademicUserIdentityOperations identityOperations;
    private SelectedActor admin;
    private final java.util.Map<Long, InternalJwtClaims> selectedActors = new java.util.HashMap<>();
    private int nextGroup = 1;
    private long nextTelegram = 730000000001L;

    @BeforeEach
    void selectCurrentAdmin() {
        admin = identityFixture.select(identityFixture.canonical().adminId(), AuthRole.ADMIN);
    }

    @Test
    void journalCapabilitiesRemainDistinctFromStatsAndCurrentAuthorityGuardsArchivedState() {
        long group = group();
        long foreignGroup = group();
        long headman = student(group);
        long helper = student(group);
        assignHeadman(headman, group);
        LocalDate from =
                jdbc.queryForObject(
                        "SELECT COALESCE(max(date_to), CURRENT_DATE) + 366 FROM semesters",
                        LocalDate.class);
        long semester =
                jdbc.queryForObject(
                        "INSERT INTO semesters (name, date_from, date_to, is_active, is_archived,"
                                + " created_at) VALUES (?, ?, ?, false, true, NOW()) RETURNING id",
                        Long.class,
                        "Journal state " + UUID.randomUUID(),
                        from,
                        from.plusDays(20));
        var headmanActor = claims(headman, "HEADMAN", group);
        var helperActor = claims(helper, "STUDENT", group);
        var archived = read(headmanActor, group, semester);
        assertThat(archived.getId()).isEqualTo(semester);
        assertThat(archived.getArchived()).isTrue();
        assertThat(archived.getWriteBlocked()).isTrue();
        assertThat(archived.getTransition()).isEqualTo(SemesterTransition.NONE);
        assertDenied(() -> read(helperActor, group, semester), Status.Code.PERMISSION_DENIED);
        assertDenied(
                () -> read(claims(helper, "HEADMAN", group), group, semester),
                Status.Code.PERMISSION_DENIED);
        long assignment =
                jdbc.queryForObject(
                        "INSERT INTO headman_assistants (group_id, student_id, permissions,"
                            + " assigned_by, is_active, assigned_at) VALUES (?, ?,"
                            + " ARRAY['mark_attendance']::varchar(64)[], ?, true, NOW()) RETURNING"
                            + " id",
                        Long.class,
                        group,
                        helper,
                        headman);
        entityManager.clear();
        assertThat(read(helperActor, group, semester).getId()).isEqualTo(semester);
        // Journal admission must not borrow the VIEW_STATS-only statistics scope.
        assertDenied(
                () ->
                        statsScope.read(
                                helperActor,
                                HeadmanStatsScopeRequest.newBuilder()
                                        .setGroupId(group)
                                        .setSemesterId(semester)
                                        .build()),
                Status.Code.PERMISSION_DENIED);
        jdbc.update(
                "UPDATE headman_assistants SET permissions = ARRAY['view_stats']::varchar(64)[]"
                        + " WHERE id = ?",
                assignment);
        entityManager.clear();
        assertThat(read(helperActor, group, semester).getId()).isEqualTo(semester);
        for (String permission : new String[] {"manage_excuses", "view_journal"}) {
            jdbc.update(
                    "UPDATE headman_assistants SET permissions = ARRAY[?]::varchar(64)[] WHERE id ="
                            + " ?",
                    permission,
                    assignment);
            entityManager.clear();
            assertDenied(() -> read(helperActor, group, semester), Status.Code.PERMISSION_DENIED);
        }
        jdbc.update(
                "UPDATE headman_assistants SET permissions ="
                    + " ARRAY['mark_attendance']::varchar(64)[], is_active = false WHERE id = ?",
                assignment);
        entityManager.clear();
        assertDenied(() -> read(helperActor, group, semester), Status.Code.PERMISSION_DENIED);
        jdbc.update("UPDATE headman_assistants SET is_active = true WHERE id = ?", assignment);
        studentStatus(helper, RoleGrantStatus.SUSPENDED);
        entityManager.clear();
        assertDenied(() -> read(helperActor, group, semester), Status.Code.PERMISSION_DENIED);
        studentStatus(helper, RoleGrantStatus.ACTIVE);
        try (var ignored = identityFixture.request(admin)) {
            identityOperations.transfer(
                    helper, new TransferStudentRequest(foreignGroup, "Journal fixture transfer"));
        }
        // Keep the original adversarial active assignment in the old group after the real move.
        jdbc.update(
                "UPDATE headman_assistants SET is_active = true, revoked_at = NULL WHERE id = ?",
                assignment);
        entityManager.clear();
        assertDenied(() -> read(helperActor, group, semester), Status.Code.PERMISSION_DENIED);
        assertDenied(
                () -> read(headmanActor, foreignGroup, semester), Status.Code.PERMISSION_DENIED);
        assertDenied(() -> read(headmanActor, 0, semester), Status.Code.INVALID_ARGUMENT);
        assertDenied(() -> read(headmanActor, group, 0), Status.Code.INVALID_ARGUMENT);
        assertDenied(() -> read(headmanActor, group, Long.MAX_VALUE), Status.Code.NOT_FOUND);

        // The existing archive state IT drives lifecycle transitions through real commands.
        // This new risk is the signed journal actor's fresh permission to read that state.
        jdbc.update("UPDATE groups SET is_active = false WHERE id = ?", group);
        entityManager.clear();
        assertDenied(() -> read(headmanActor, group, semester), Status.Code.PERMISSION_DENIED);
        jdbc.update("UPDATE groups SET is_active = true WHERE id = ?", group);
        suspendHeadman(headman);
        entityManager.clear();
        assertDenied(() -> read(headmanActor, group, semester), Status.Code.PERMISSION_DENIED);
    }

    private SemesterStateResponse read(InternalJwtClaims actor, long group, long semester) {
        var response = new AtomicReference<SemesterStateResponse>();
        var failure = new AtomicReference<Throwable>();
        StudentHomeworkTestIdentity.run(
                actor,
                () ->
                        handler.getHeadmanJournalSemesterState(
                                HeadmanJournalSemesterStateRequest.newBuilder()
                                        .setGroupId(group)
                                        .setSemesterId(semester)
                                        .build(),
                                new StreamObserver<>() {
                                    @Override
                                    public void onNext(SemesterStateResponse value) {
                                        response.set(value);
                                    }

                                    @Override
                                    public void onError(Throwable error) {
                                        failure.set(error);
                                    }

                                    @Override
                                    public void onCompleted() {}
                                }));
        if (failure.get() != null) throw Status.fromThrowable(failure.get()).asRuntimeException();
        return response.get();
    }

    private long group() {
        return groups.createAdminGroup(new CreateAdminGroupRequest("ЖУР", "11" + nextGroup++, 4))
                .getId();
    }

    private long student(long group) {
        long user =
                identityFixture
                        .seedUser(
                                new CreateUserRequest(
                                        "Тестов",
                                        "Студент",
                                        null,
                                        UserRole.STUDENT,
                                        group,
                                        null,
                                        nextTelegram++),
                                admin)
                        .userId();
        selectedActors.put(user, identityFixture.select(user, AuthRole.STUDENT).claims());
        return user;
    }

    private void assignHeadman(long user, long group) {
        try (var ignored = identityFixture.request(admin)) {
            identityOperations.headman(group, user, null, false, null);
        }
        selectedActors.put(user, identityFixture.select(user, AuthRole.HEADMAN).claims());
    }

    private void studentStatus(long user, RoleGrantStatus status) {
        try (var ignored = identityFixture.request(admin)) {
            identityOperations.role(
                    user, "STUDENT", new RoleGrantUpdateRequest(status, null, null, null));
        }
    }

    private void suspendHeadman(long user) {
        // The real role protocol suspends HEADMAN with STUDENT; reactivation leaves HEADMAN
        // suspended.
        studentStatus(user, RoleGrantStatus.SUSPENDED);
        studentStatus(user, RoleGrantStatus.ACTIVE);
    }

    private InternalJwtClaims claims(long user, String role, long group) {
        var actor = selectedActors.get(user);
        if (actor.role().equals(role)) return actor;
        // Preserve the original false-HEADMAN denial using a real student's selected session tuple.
        return new InternalJwtClaims(
                actor.userId(),
                actor.sessionId(),
                actor.sessionVersion(),
                actor.rolesVersion(),
                role,
                actor.status(),
                group,
                "HEADMAN".equals(role),
                actor.readOnly());
    }

    private static void assertDenied(Runnable action, Status.Code expected) {
        assertThatThrownBy(action::run)
                .satisfies(
                        error ->
                                assertThat(Status.fromThrowable(error).getCode())
                                        .isEqualTo(expected));
    }
}
