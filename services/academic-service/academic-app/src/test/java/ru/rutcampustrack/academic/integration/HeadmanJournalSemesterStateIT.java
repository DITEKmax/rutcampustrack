package ru.rutcampustrack.academic.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import ru.rutcampustrack.academic.grpc.AcademicGrpcServiceImpl;
import ru.rutcampustrack.academic.grpc.HeadmanJournalSemesterStateRequest;
import ru.rutcampustrack.academic.grpc.HeadmanStatsScopeReadService;
import ru.rutcampustrack.academic.grpc.HeadmanStatsScopeRequest;
import ru.rutcampustrack.academic.grpc.SemesterStateResponse;
import ru.rutcampustrack.academic.grpc.SemesterTransition;
import ru.rutcampustrack.academic.grpc.StudentHomeworkTestIdentity;
import ru.rutcampustrack.shared.security.InternalJwtClaims;

/** Fresh SQL grants and uncached state; synthetic fixture writes roll back in the fresh IT DB. */
@Transactional
class HeadmanJournalSemesterStateIT extends AbstractAcademicIntegrationTest {
    @Autowired private JdbcTemplate jdbc;
    @Autowired private AcademicGrpcServiceImpl handler;
    @Autowired private HeadmanStatsScopeReadService statsScope;
    @Autowired private jakarta.persistence.EntityManager entityManager;

    @Test
    void journalCapabilitiesRemainDistinctFromStatsAndCurrentAuthorityGuardsArchivedState() {
        long group = group();
        long foreignGroup = group();
        long headman = student(group);
        long helper = student(group);
        jdbc.update(
                "INSERT INTO user_role_grants (user_id, role, status, group_id, created_at, updated_at) "
                        + "VALUES (?, 'headman', 'active', ?, NOW(), NOW())",
                headman,
                group);
        LocalDate from =
                jdbc.queryForObject(
                        "SELECT COALESCE(max(date_to), CURRENT_DATE) + 366 FROM semesters",
                        LocalDate.class);
        long semester =
                jdbc.queryForObject(
                        "INSERT INTO semesters (name, date_from, date_to, is_active, is_archived, created_at) "
                                + "VALUES (?, ?, ?, false, true, NOW()) RETURNING id",
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
                        "INSERT INTO headman_assistants "
                                + "(group_id, student_id, permissions, assigned_by, is_active, assigned_at) "
                                + "VALUES (?, ?, ARRAY['mark_attendance']::varchar(64)[], ?, true, NOW()) RETURNING id",
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
                "UPDATE headman_assistants SET permissions = ARRAY['view_stats']::varchar(64)[] WHERE id = ?",
                assignment);
        entityManager.clear();
        assertThat(read(helperActor, group, semester).getId()).isEqualTo(semester);
        for (String permission : new String[] {"manage_excuses", "view_journal"}) {
            jdbc.update(
                    "UPDATE headman_assistants SET permissions = ARRAY[?]::varchar(64)[] WHERE id = ?",
                    permission,
                    assignment);
            entityManager.clear();
            assertDenied(() -> read(helperActor, group, semester), Status.Code.PERMISSION_DENIED);
        }
        jdbc.update(
                "UPDATE headman_assistants SET permissions = ARRAY['mark_attendance']::varchar(64)[], is_active = false WHERE id = ?",
                assignment);
        entityManager.clear();
        assertDenied(() -> read(helperActor, group, semester), Status.Code.PERMISSION_DENIED);
        jdbc.update("UPDATE headman_assistants SET is_active = true WHERE id = ?", assignment);
        jdbc.update(
                "UPDATE user_role_grants SET status = 'suspended' WHERE user_id = ? AND role = 'student'",
                helper);
        entityManager.clear();
        assertDenied(() -> read(helperActor, group, semester), Status.Code.PERMISSION_DENIED);
        jdbc.update(
                "UPDATE user_role_grants SET status = 'active', group_id = ? WHERE user_id = ? AND role = 'student'",
                foreignGroup,
                helper);
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
        jdbc.update(
                "UPDATE user_role_grants SET status = 'suspended' WHERE user_id = ? AND role = 'headman'",
                headman);
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
        return jdbc.queryForObject(
                "INSERT INTO groups (name, current_course, training_duration_years, duration_status, is_active, created_at) "
                        + "VALUES (?, 1, 9, 'KNOWN', true, NOW()) RETURNING id",
                Long.class,
                "ТСТ-101-" + UUID.randomUUID().toString().substring(0, 8));
    }

    private long student(long group) {
        long user =
                jdbc.queryForObject(
                        "INSERT INTO users (login, password_hash, last_name, first_name, role, status, "
                                + "is_headman, group_id, password_changed, created_at, updated_at) "
                                + "VALUES (?, 'x', 'Тестов', 'Студент', 'student', 'active', false, ?, false, NOW(), NOW()) RETURNING id",
                        Long.class,
                        "journal_" + UUID.randomUUID().toString().substring(0, 12),
                        group);
        jdbc.update(
                "INSERT INTO user_role_grants (user_id, role, status, group_id, created_at, updated_at) "
                        + "VALUES (?, 'student', 'active', ?, NOW(), NOW())",
                user,
                group);
        return user;
    }

    private static InternalJwtClaims claims(long user, String role, long group) {
        return new InternalJwtClaims(
                user,
                UUID.randomUUID(),
                1,
                1,
                role,
                "ACTIVE",
                group,
                "HEADMAN".equals(role),
                false);
    }

    private static void assertDenied(Runnable action, Status.Code expected) {
        assertThatThrownBy(action::run)
                .satisfies(
                        error ->
                                assertThat(Status.fromThrowable(error).getCode())
                                        .isEqualTo(expected));
    }
}
