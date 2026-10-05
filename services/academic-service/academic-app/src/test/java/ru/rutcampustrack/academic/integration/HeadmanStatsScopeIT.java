package ru.rutcampustrack.academic.integration;

import io.grpc.Status;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import ru.rutcampustrack.academic.grpc.HeadmanStatsScopeReadService;
import ru.rutcampustrack.academic.grpc.HeadmanStatsScopeRequest;
import ru.rutcampustrack.shared.security.InternalJwtClaims;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Actual SQL authority/counts and archive reads; all synthetic fixture writes roll back. */
@Transactional(isolation = Isolation.REPEATABLE_READ)
class HeadmanStatsScopeIT extends AbstractAcademicIntegrationTest {
    @Autowired private JdbcTemplate jdbc;
    @Autowired private HeadmanStatsScopeReadService service;
    @Autowired private jakarta.persistence.EntityManager entityManager;

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
        jdbc.update("INSERT INTO user_role_grants (user_id, role, status, group_id, created_at, updated_at) "
                + "VALUES (?, 'headman', 'active', ?, NOW(), NOW())", headman, groupId);
        LocalDate from = jdbc.queryForObject("SELECT COALESCE(max(date_to), CURRENT_DATE) + 366 FROM semesters", LocalDate.class);
        long semesterId = jdbc.queryForObject("INSERT INTO semesters (name, date_from, date_to, is_active, "
                + "is_archived, created_at) VALUES (?, ?, ?, false, true, NOW()) RETURNING id", Long.class,
                "Headman scope " + UUID.randomUUID(), from, from.plusDays(20));
        var request = HeadmanStatsScopeRequest.newBuilder().setGroupId(groupId).setSemesterId(semesterId).build();
        var actor = claims(headman, "HEADMAN", groupId);
        var result = service.read(actor, request);
        assertThat(result.getSelectedSemester().getId()).isEqualTo(semesterId);
        assertThat(result.getSelectedSemester().getArchived()).isTrue();
        assertThat(result.getSelectedSemester().getDateFrom()).isEqualTo(from.toString());
        assertThat(result.getMembershipActive()).isEqualTo(2);
        assertThat(result.getMembershipTotal()).isEqualTo(3);
        assertThat(result.getSemestersList()).anyMatch(value -> value.getId() == semesterId && value.getArchived());
        // A current grant cannot invent membership before a late joiner entered this group.
        assertDenied(() -> service.read(actor, request.toBuilder().setStudentId(assistant).build()), Status.Code.NOT_FOUND);
        jdbc.update("INSERT INTO student_group_history (user_id, group_id, joined_at, left_at, created_at) "
                + "VALUES (?, ?, ?, NULL, NOW())", assistant, groupId, from.plusDays(21));
        assertDenied(() -> service.read(actor, request.toBuilder().setStudentId(assistant).build()), Status.Code.NOT_FOUND);
        jdbc.update("INSERT INTO student_group_history (user_id, group_id, joined_at, left_at, created_at) "
                + "VALUES (?, ?, ?, ?, NOW())", historicalMember, groupId, from, from.plusDays(5));
        assertThat(service.read(actor, request.toBuilder().setStudentId(historicalMember).build())
                .getSelectedSemester().getId()).isEqualTo(semesterId); // no schedule lessons are required for scope
        assertDenied(() -> service.read(actor, request.toBuilder().setStudentId(foreignStudent).build()), Status.Code.NOT_FOUND);
        jdbc.update("UPDATE student_group_history SET left_at = ? WHERE user_id = ?", from, historicalMember);
        entityManager.clear();
        assertDenied(() -> service.read(actor, request.toBuilder().setStudentId(historicalMember).build()), Status.Code.NOT_FOUND);
        assertDenied(() -> service.read(actor, request.toBuilder().setGroupId(otherGroup).build()), Status.Code.PERMISSION_DENIED);
        assertDenied(() -> service.read(actor, request.toBuilder().setSemesterId(Long.MAX_VALUE).build()), Status.Code.NOT_FOUND);
        assertDenied(() -> service.read(actor, request.toBuilder().setSemesterId(0).build()), Status.Code.INVALID_ARGUMENT);
        jdbc.update("UPDATE user_role_grants SET status = 'suspended' WHERE user_id = ? AND role = 'headman'", headman);
        assertDenied(() -> service.read(actor, request), Status.Code.PERMISSION_DENIED);
        long assignment = jdbc.queryForObject("INSERT INTO headman_assistants "
                + "(group_id, student_id, permissions, assigned_by, is_active, assigned_at) "
                + "VALUES (?, ?, ARRAY['view_stats']::varchar(64)[], ?, true, NOW()) RETURNING id", Long.class,
                groupId, assistant, headman);
        var assistantActor = claims(assistant, "STUDENT", groupId);
        assertThat(service.read(assistantActor, request).getMembershipTotal()).isEqualTo(3);
        jdbc.update("UPDATE headman_assistants SET permissions = ARRAY['mark_attendance']::varchar(64)[] WHERE id = ?", assignment);
        // Each actual RPC gets a fresh persistence context; emulate it after fixture JDBC changes.
        entityManager.clear();
        assertDenied(() -> service.read(assistantActor, request), Status.Code.PERMISSION_DENIED);
        jdbc.update("UPDATE user_role_grants SET group_id = ? WHERE user_id = ? AND role = 'student'", otherGroup, assistant);
        entityManager.clear();
        assertDenied(() -> service.read(assistantActor, request), Status.Code.PERMISSION_DENIED);
    }

    private long group() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        return jdbc.queryForObject("INSERT INTO groups (name, current_course, "
                + "training_duration_years, duration_status, is_active, created_at) "
                + "VALUES (?, 1, 9, 'KNOWN', true, NOW()) RETURNING id", Long.class, "ТСТ-101-" + suffix);
    }

    private long student(long groupId, String status) {
        long id = jdbc.queryForObject("INSERT INTO users (login, password_hash, last_name, first_name, role, status, "
                + "is_headman, group_id, password_changed, created_at, updated_at) "
                + "VALUES (?, 'x', 'Тестов', 'Студент', 'student', 'active', false, ?, false, NOW(), NOW()) RETURNING id",
                Long.class, "stats_" + UUID.randomUUID().toString().substring(0, 12), groupId);
        jdbc.update("INSERT INTO user_role_grants (user_id, role, status, group_id, created_at, updated_at) "
                + "VALUES (?, 'student', ?, ?, NOW(), NOW())", id, status, groupId);
        return id;
    }

    private static InternalJwtClaims claims(long userId, String role, long groupId) {
        return new InternalJwtClaims(userId, UUID.randomUUID(), 1, 1, role, "ACTIVE", groupId, "HEADMAN".equals(role), false);
    }

    private static void assertDenied(Runnable request, Status.Code expected) {
        assertThatThrownBy(request::run).satisfies(error -> assertThat(Status.fromThrowable(error).getCode()).isEqualTo(expected));
    }
}
