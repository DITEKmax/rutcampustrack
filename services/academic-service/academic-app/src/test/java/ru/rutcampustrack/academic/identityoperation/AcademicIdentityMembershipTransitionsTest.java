package ru.rutcampustrack.academic.identityoperation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import ru.rutcampustrack.academic.repository.SemesterRepository;
import ru.rutcampustrack.academic.repository.StudentGroupHistoryRepository;
import ru.rutcampustrack.authoperations.grpc.*;

import java.sql.ResultSet;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

class AcademicIdentityMembershipTransitionsTest {
    @ParameterizedTest
    @CsvSource({
        "ACTIVE,SUSPENDED,true,0,0",
        "SUSPENDED,ACTIVE,true,0,0",
        "SUSPENDED,EXPELLED,true,1,0",
        "EXPELLED,ACTIVE,false,0,1"
    })
    void keepsSuspendedMembershipAndOnlyReopensClosedTerminalHistory(
            String before, String after, boolean open, long closed, long inserted)
            throws Exception {
        var fixture = new Fixture(before, false, open);
        var target =
                PreparedIdentityTarget.newBuilder()
                        .setUserId(8)
                        .setExpectedAcademicRevision(2)
                        .setFinalRolesVersion(5)
                        .addGrants(
                                AdmissionGrant.newBuilder()
                                        .setGrantId(12)
                                        .setRole("STUDENT")
                                        .setStatus(after)
                                        .setGroupId(7));
        var result =
                fixture.writer.apply(
                        command("ROLE", null, null),
                        IdentityOperationKind.IDENTITY_OPERATION_KIND_ROLE,
                        IdentityOperationResult.newBuilder()
                                .setOperationId("00000000-0000-0000-0000-000000000001")
                                .addTargets(target)
                                .build(),
                        99);
        assertThat(
                        fixture.updates.stream()
                                .filter(s -> s.startsWith("UPDATE student_group_history")))
                .hasSize((int) closed);
        assertThat(fixture.inserts).hasSize((int) inserted);
        if (inserted == 1) {
            assertThat(fixture.inserts.getFirst()[2]).isEqualTo(LocalDate.of(2026, 4, 20));
            assertThat(fixture.inserts.getFirst()[3]).isEqualTo("role-reactivation");
        }
        assertThat(result)
                .singleElement()
                .satisfies(
                        receipt -> {
                            assertThat(receipt.getCommittedAcademicRevision()).isEqualTo(3);
                            assertThat(receipt.getAppliedAuthRolesVersion()).isEqualTo(5);
                        });
    }

    @Test
    void assigningSameHeadmanKeepsExistingAssistants() throws Exception {
        var fixture = new Fixture("ACTIVE", true, true);
        var target =
                PreparedIdentityTarget.newBuilder()
                        .setUserId(8)
                        .setExpectedAcademicRevision(2)
                        .setFinalRolesVersion(5)
                        .addGrants(
                                AdmissionGrant.newBuilder()
                                        .setGrantId(11)
                                        .setRole("HEADMAN")
                                        .setStatus("ACTIVE")
                                        .setGroupId(7))
                        .addGrants(
                                AdmissionGrant.newBuilder()
                                        .setGrantId(12)
                                        .setRole("STUDENT")
                                        .setStatus("ACTIVE")
                                        .setGroupId(7));
        fixture.writer.apply(
                command("HEADMAN_REPLACE", 8L, 8L),
                IdentityOperationKind.IDENTITY_OPERATION_KIND_HEADMAN_REPLACE,
                IdentityOperationResult.newBuilder()
                        .setOperationId("00000000-0000-0000-0000-000000000001")
                        .addTargets(target)
                        .build(),
                99);
        assertThat(
                        fixture.sql.stream()
                                .filter(
                                        s ->
                                                s.contains("FROM headman_assistants")
                                                        || s.startsWith(
                                                                "UPDATE headman_assistants")))
                .isEmpty();
        assertThat(fixture.headmanRevokedResults).containsExactly(0);
        assertThat(
                        fixture.updates.stream()
                                .filter(s -> s.startsWith("UPDATE student_group_history")))
                .isEmpty();
    }

    @Test
    void profilePatchWithUnchangedArchivedStudentDoesNotStartReassignment() throws Exception {
        var fixture = new Fixture("ARCHIVED", false, false);
        var target =
                PreparedIdentityTarget.newBuilder()
                        .setUserId(8)
                        .setExpectedAcademicRevision(2)
                        .setFinalRolesVersion(5)
                        .addGrants(
                                AdmissionGrant.newBuilder()
                                        .setGrantId(12)
                                        .setRole("STUDENT")
                                        .setStatus("ARCHIVED")
                                        .setGroupId(7))
                        .addGrants(
                                AdmissionGrant.newBuilder()
                                        .setGrantId(13)
                                        .setRole("TEACHER")
                                        .setStatus("ACTIVE"));
        var profile =
                new AcademicIdentityLocalCommand.Profile(
                        8, "Петров", "Пётр", null, null, null, "EMP-123", null);
        var local =
                new AcademicIdentityLocalCommand(
                        new AcademicIdentityLocalCommand.ApiIntent("PROFILE", 8, "0".repeat(64)),
                        profile,
                        LocalDate.of(2026, 4, 20),
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        "profile-update",
                        List.of(new AcademicIdentityLocalCommand.GroupRevision(7, 1)));
        fixture.writer.apply(
                local,
                IdentityOperationKind.IDENTITY_OPERATION_KIND_ROLE,
                IdentityOperationResult.newBuilder().addTargets(target).build(),
                99);
        assertThat(fixture.sql.stream().filter(sql -> sql.contains("student_group_history")))
                .isEmpty();
        assertThat(
                        fixture.updates.stream()
                                .filter(
                                        sql ->
                                                sql.startsWith(
                                                        "UPDATE academic_profiles SET last_name")))
                .hasSize(1);
    }

    @Test
    void explicitArchivedStudentReassignmentStillOpensNewDatedMembership() throws Exception {
        var fixture = new Fixture("ARCHIVED", false, false);
        var target =
                PreparedIdentityTarget.newBuilder()
                        .setUserId(8)
                        .setExpectedAcademicRevision(2)
                        .setFinalRolesVersion(5)
                        .addGrants(
                                AdmissionGrant.newBuilder()
                                        .setGrantId(12)
                                        .setRole("STUDENT")
                                        .setStatus("ACTIVE")
                                        .setGroupId(7));
        var local =
                new AcademicIdentityLocalCommand(
                        new AcademicIdentityLocalCommand.ApiIntent("ROLE", 8, "0".repeat(64)),
                        null,
                        LocalDate.of(2026, 4, 20),
                        LocalDate.of(2026, 4, 20),
                        null,
                        null,
                        null,
                        null,
                        null,
                        3L,
                        "role-status",
                        List.of(new AcademicIdentityLocalCommand.GroupRevision(7, 1)));
        fixture.writer.apply(
                local,
                IdentityOperationKind.IDENTITY_OPERATION_KIND_ROLE,
                IdentityOperationResult.newBuilder().addTargets(target).build(),
                99);
        assertThat(fixture.inserts)
                .singleElement()
                .satisfies(
                        args -> {
                            assertThat(args[2]).isEqualTo(LocalDate.of(2026, 4, 20));
                            assertThat(args[3]).isEqualTo("role-reassignment");
                        });
    }

    private static AcademicIdentityLocalCommand command(
            String action, Long expected, Long candidate) {
        return new AcademicIdentityLocalCommand(
                new AcademicIdentityLocalCommand.ApiIntent(action, 8, "0".repeat(64)),
                null,
                LocalDate.of(2026, 4, 20),
                null,
                null,
                null,
                candidate == null ? null : 7L,
                expected,
                candidate,
                null,
                null,
                List.of(new AcademicIdentityLocalCommand.GroupRevision(7, 1)));
    }

    private static final class Fixture {
        final List<String> sql = new ArrayList<>();
        final List<String> updates = new ArrayList<>();
        final List<Integer> headmanRevokedResults = new ArrayList<>();
        final List<Object[]> inserts = new ArrayList<>();
        final AcademicIdentityDomainWriter writer;

        Fixture(String before, boolean headman, boolean open) throws Exception {
            var student = mock(ResultSet.class);
            when(student.getString("status")).thenReturn(before);
            when(student.getObject("group_id", Long.class)).thenReturn(7L);
            JdbcTemplate jdbc =
                    mock(
                            JdbcTemplate.class,
                            invocation -> {
                                if (!(invocation.getArgument(0) instanceof String statement))
                                    return org.mockito.Answers.RETURNS_DEFAULTS.answer(invocation);
                                sql.add(statement);
                                return switch (invocation.getMethod().getName()) {
                                    case "queryForList" ->
                                            statement.contains("academic_profiles")
                                                    ? List.of(2L)
                                                    : statement.contains(
                                                                    "SELECT group_id FROM"
                                                                        + " student_group_history")
                                                            ? (open ? List.of(7L) : List.of())
                                                            : statement.contains(
                                                                            "student_group_history")
                                                                    ? (open
                                                                            ? List.of(33L)
                                                                            : List.of())
                                                                    : statement.contains(
                                                                                    "role='HEADMAN'")
                                                                            ? (headman
                                                                                    ? List.of(8L)
                                                                                    : List.of())
                                                                            : List.of(1L);
                                    case "query" -> {
                                        RowMapper<?> mapper = invocation.getArgument(1);
                                        yield statement.contains("role='STUDENT'")
                                                ? List.of(mapper.mapRow(student, 0))
                                                : List.of();
                                    }
                                    case "queryForObject" -> {
                                        if (statement.startsWith("INSERT INTO")) {
                                            Object[] args =
                                                    java.util.Arrays.copyOfRange(
                                                            invocation.getArguments(),
                                                            2,
                                                            invocation.getArguments().length);
                                            inserts.add(args);
                                            yield 44L;
                                        }
                                        if (statement.startsWith("SELECT employee_number"))
                                            yield "EMP-123";
                                        if (statement.startsWith("SELECT count(*)")) yield 0L;
                                        if (statement.contains("FROM student_group_history"))
                                            yield false;
                                        if (statement.contains("role='HEADMAN'")) yield headman;
                                        yield true;
                                    }
                                    case "update" -> {
                                        updates.add(statement);
                                        if (statement.startsWith(
                                                "UPDATE academic_identity_operations SET"
                                                        + " headman_assistants_revoked"))
                                            headmanRevokedResults.add(invocation.getArgument(1));
                                        yield 1;
                                    }
                                    default ->
                                            org.mockito.Answers.RETURNS_DEFAULTS.answer(invocation);
                                };
                            });
            writer =
                    new AcademicIdentityDomainWriter(
                            jdbc,
                            mock(SemesterRepository.class),
                            mock(ApplicationEventPublisher.class),
                            mock(StudentGroupHistoryRepository.class));
        }
    }
}
