package ru.rutcampustrack.academic.identityoperation;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import ru.rutcampustrack.authoperations.grpc.*;

import java.time.LocalDate;
import java.util.List;

class AcademicIdentityMembershipTransitionsIT extends AbstractAcademicIdentityWriterIT {
    @ParameterizedTest
    @CsvSource({
        "ACTIVE,SUSPENDED,true,0,0",
        "SUSPENDED,ACTIVE,true,0,0",
        "SUSPENDED,EXPELLED,true,1,0",
        "EXPELLED,ACTIVE,false,0,1"
    })
    void keepsSuspendedMembershipAndOnlyReopensClosedTerminalHistory(
            String before, String after, boolean open, long closed, long inserted) {
        seed(before, false, open);
        var control = history(9);
        var prepared = IdentityOperationResult.newBuilder().addTargets(target(after)).build();
        var result =
                apply(
                        command("ROLE", null, null),
                        IdentityOperationKind.IDENTITY_OPERATION_KIND_ROLE,
                        prepared);
        assertThat(
                        jdbc.queryForList(
                                "SELECT id FROM student_group_history WHERE user_id=8 ORDER BY id",
                                Long.class))
                .hasSize(1 + (int) inserted)
                .contains(33L);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT joined_at FROM student_group_history WHERE id=33",
                                LocalDate.class))
                .isEqualTo(LocalDate.of(2026, 4, 1));
        assertThat(
                        jdbc.queryForObject(
                                "SELECT left_at FROM student_group_history WHERE id=33",
                                LocalDate.class))
                .isEqualTo(closed == 1 ? EFFECTIVE : open ? null : LocalDate.of(2026, 4, 10));
        assertThat(
                        jdbc.queryForObject(
                                "SELECT reason FROM student_group_history WHERE id=33",
                                String.class))
                .isEqualTo("original");
        if (inserted == 1) assertNewInterval("role-reactivation");
        assertThat(history(9)).isEqualTo(control);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT status FROM academic_role_eligibility WHERE user_id=8 AND"
                                    + " role='STUDENT'",
                                String.class))
                .isEqualTo(after);
        assertThat(result)
                .singleElement()
                .satisfies(
                        receipt -> {
                            assertThat(receipt.getCommittedAcademicRevision()).isEqualTo(3);
                            assertThat(receipt.getAppliedAuthRolesVersion()).isEqualTo(5);
                        });
    }

    @Test
    void assigningSameHeadmanKeepsExistingAssistants() {
        seed("ACTIVE", true, true);
        var before =
                jdbc.queryForObject(
                        "SELECT to_jsonb(a)::text FROM headman_assistants a WHERE id=22",
                        String.class);
        var history = history(8);
        var id = operation();
        var prepared =
                IdentityOperationResult.newBuilder()
                        .setOperationId(id.toString())
                        .addTargets(
                                target("ACTIVE")
                                        .addGrants(
                                                AdmissionGrant.newBuilder()
                                                        .setGrantId(11)
                                                        .setRole("HEADMAN")
                                                        .setStatus("ACTIVE")
                                                        .setGroupId(7)))
                        .build();
        apply(
                command("HEADMAN_REPLACE", 8L, 8L),
                IdentityOperationKind.IDENTITY_OPERATION_KIND_HEADMAN_REPLACE,
                prepared);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT to_jsonb(a)::text FROM headman_assistants a WHERE id=22",
                                String.class))
                .isEqualTo(before);
        assertThat(history(8)).isEqualTo(history);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT headman_assistants_revoked FROM"
                                    + " academic_identity_operations WHERE operation_id=?",
                                Integer.class,
                                id))
                .isZero();
    }

    @Test
    void profilePatchWithUnchangedArchivedStudentDoesNotStartReassignment() {
        seed("ARCHIVED", false, false);
        var history = history(8);
        var local =
                new AcademicIdentityLocalCommand(
                        new AcademicIdentityLocalCommand.ApiIntent("PROFILE", 8, "0".repeat(64)),
                        new AcademicIdentityLocalCommand.Profile(
                                8, "Петров", "Пётр", null, null, null, "EMP-123", null),
                        EFFECTIVE,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        "profile-update",
                        List.of(new AcademicIdentityLocalCommand.GroupRevision(7, 1)));
        apply(
                local,
                IdentityOperationKind.IDENTITY_OPERATION_KIND_ROLE,
                IdentityOperationResult.newBuilder()
                        .addTargets(
                                target("ARCHIVED")
                                        .addGrants(
                                                AdmissionGrant.newBuilder()
                                                        .setGrantId(13)
                                                        .setRole("TEACHER")
                                                        .setStatus("ACTIVE")))
                        .build());
        assertThat(history(8)).isEqualTo(history);
        assertThat(
                        jdbc.queryForMap(
                                "SELECT last_name,first_name,employee_number FROM academic_profiles"
                                    + " WHERE id=8"))
                .containsEntry("last_name", "Петров")
                .containsEntry("first_name", "Пётр")
                .containsEntry("employee_number", "EMP-123");
    }

    @Test
    void explicitArchivedStudentReassignmentStillOpensNewDatedMembership() {
        seed("ARCHIVED", false, false);
        var existing = history(8);
        var local =
                new AcademicIdentityLocalCommand(
                        new AcademicIdentityLocalCommand.ApiIntent("ROLE", 8, "0".repeat(64)),
                        null,
                        EFFECTIVE,
                        EFFECTIVE,
                        null,
                        null,
                        null,
                        null,
                        null,
                        3L,
                        "role-status",
                        List.of(new AcademicIdentityLocalCommand.GroupRevision(7, 1)));
        apply(
                local,
                IdentityOperationKind.IDENTITY_OPERATION_KIND_ROLE,
                IdentityOperationResult.newBuilder().addTargets(target("ACTIVE")).build());
        assertThat(history(8)).hasSize(2).containsAll(existing);
        assertNewInterval("role-reassignment");
    }

    private PreparedIdentityTarget.Builder target(String status) {
        return PreparedIdentityTarget.newBuilder()
                .setUserId(8)
                .setExpectedAcademicRevision(2)
                .setFinalRolesVersion(5)
                .addGrants(
                        AdmissionGrant.newBuilder()
                                .setGrantId(12)
                                .setRole("STUDENT")
                                .setStatus(status)
                                .setGroupId(7));
    }

    private void assertNewInterval(String reason) {
        var rows =
                jdbc.queryForList(
                        "SELECT group_id,joined_at,left_at,reason FROM student_group_history WHERE"
                            + " user_id=8 AND id<>33");
        assertThat(rows)
                .singleElement()
                .satisfies(
                        row -> {
                            assertThat(row.get("group_id")).isEqualTo(7L);
                            assertThat(((java.sql.Date) row.get("joined_at")).toLocalDate())
                                    .isEqualTo(EFFECTIVE);
                            assertThat(row.get("left_at")).isNull();
                            assertThat(row.get("reason")).isEqualTo(reason);
                        });
    }
}
