package ru.rutcampustrack.auth.session;

import org.junit.jupiter.api.Test;
import ru.rutcampustrack.auth.session.model.AuthRole;
import ru.rutcampustrack.auth.session.model.RoleGrant;
import ru.rutcampustrack.auth.session.model.RoleStatus;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ActiveRolePolicyTest {

    private static final Instant CREATED = Instant.parse("2026-09-08T08:00:00Z");

    private final ActiveRolePolicy policy = new ActiveRolePolicy();

    @Test
    void studentWinsDefaultEvenWhenTerminalAndRemainsReadOnly() {
        RoleGrant student = grant(1, AuthRole.STUDENT, RoleStatus.GRADUATED, 11L);
        RoleGrant teacher = grant(2, AuthRole.TEACHER, RoleStatus.ACTIVE, null);

        ActiveRolePolicy.Evaluation result = policy.evaluate(7, List.of(teacher, student));

        assertThat(result.succeeded()).isTrue();
        assertThat(result.defaultGrant()).isEqualTo(student);
        assertThat(result.defaultGrant().isReadOnly()).isTrue();
        assertThat(result.defaultGrant().isSelectable()).isFalse();
    }

    @Test
    void teacherIsFallbackAndAdminHeadmanOnlyStayNeutral() {
        RoleGrant teacher = grant(3, AuthRole.TEACHER, RoleStatus.ACTIVE, null);
        ActiveRolePolicy.Evaluation teacherResult = policy.evaluate(7, List.of(teacher));
        assertThat(teacherResult.defaultGrant()).isEqualTo(teacher);

        RoleGrant admin = grant(4, AuthRole.ADMIN, RoleStatus.ACTIVE, null);
        RoleGrant headman = grant(5, AuthRole.HEADMAN, RoleStatus.ACTIVE, 12L);
        ActiveRolePolicy.Evaluation neutral = policy.evaluate(7, List.of(admin, headman));
        assertThat(neutral.defaultGrant()).isNull();
    }

    @Test
    void suspendedGrantIsActualButCannotBeSelected() {
        RoleGrant suspended = grant(6, AuthRole.STUDENT, RoleStatus.SUSPENDED, 13L);

        ActiveRolePolicy.Selection result = policy.select(7, AuthRole.STUDENT, List.of(suspended));

        assertThat(result.code()).isEqualTo(ActiveRolePolicy.Code.ROLE_NOT_SELECTABLE);
        assertThat(result.grant()).isNull();
    }

    @Test
    void foreignAndDuplicateGrantsAreRejected() {
        RoleGrant foreign = grant(7, AuthRole.STUDENT, RoleStatus.ACTIVE, 14L, 8);
        ActiveRolePolicy.Evaluation foreignResult = policy.evaluate(7, List.of(foreign));
        assertThat(foreignResult.code()).isEqualTo(ActiveRolePolicy.Code.FOREIGN_GRANT);
        ActiveRolePolicy.Selection foreignSelection = policy.select(
                7, AuthRole.STUDENT, List.of(foreign));
        assertThat(foreignSelection.code()).isEqualTo(ActiveRolePolicy.Code.FOREIGN_GRANT);

        RoleGrant first = grant(8, AuthRole.STUDENT, RoleStatus.ACTIVE, 14L);
        RoleGrant duplicateRole = grant(9, AuthRole.STUDENT, RoleStatus.GRADUATED, 14L);
        ActiveRolePolicy.Evaluation duplicateResult = policy.evaluate(7, List.of(first, duplicateRole));
        assertThat(duplicateResult.code()).isEqualTo(ActiveRolePolicy.Code.DUPLICATE_GRANT);
    }

    private static RoleGrant grant(long id, AuthRole role, RoleStatus status, Long groupId) {
        return grant(id, role, status, groupId, 7);
    }

    private static RoleGrant grant(long id, AuthRole role, RoleStatus status, Long groupId, long userId) {
        return new RoleGrant(id, userId, role, status, groupId, CREATED, CREATED);
    }
}
