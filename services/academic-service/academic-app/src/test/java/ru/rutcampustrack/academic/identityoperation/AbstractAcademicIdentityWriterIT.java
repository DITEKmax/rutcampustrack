package ru.rutcampustrack.academic.identityoperation;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import ru.rutcampustrack.academic.integration.AbstractAcademicIntegrationTest;
import ru.rutcampustrack.authoperations.grpc.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Local APPLY component fixture on the existing complete migrated owner database. */
abstract class AbstractAcademicIdentityWriterIT extends AbstractAcademicIntegrationTest {
    static final LocalDate EFFECTIVE = LocalDate.of(2026, 4, 20);
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactions;
    @Autowired AcademicIdentityDomainWriter writer;

    void profile(long id, String role, String status) {
        jdbc.update(
                "INSERT INTO"
                    + " academic_profiles(id,directory_role,directory_status,last_name,first_name,employee_number,academic_revision,applied_auth_roles_version,created_at,updated_at)"
                    + " VALUES(?,?,?,'Initial','Synthetic',?,2,3,NOW(),NOW())",
                id,
                role,
                status,
                "EMP-LOCAL-" + id);
    }

    void seed(String status, boolean headman, boolean open) {
        jdbc.update(
                "INSERT INTO groups(id,name,is_active,academic_revision)"
                        + " VALUES(7,'ЛОК-111',true,1)");
        jdbc.update(
                "INSERT INTO group_history_coverage(group_id,coverage_from,writer_version)"
                        + " VALUES(7,DATE '2026-01-01','managed_v1')");
        profile(8, "STUDENT", status);
        profile(9, "STUDENT", "ACTIVE");
        profile(99, "ADMIN", "ACTIVE");
        jdbc.update(
                "INSERT INTO academic_role_eligibility(user_id,role,status,group_id,updated_at)"
                        + " VALUES(8,'STUDENT',?,7,NOW()),(9,'STUDENT','ACTIVE',7,NOW())",
                status);
        if (headman)
            jdbc.update(
                    "INSERT INTO academic_role_eligibility(user_id,role,status,group_id,updated_at)"
                            + " VALUES(8,'HEADMAN','ACTIVE',7,NOW())");
        jdbc.update(
                "INSERT INTO student_group_history(id,user_id,group_id,joined_at,left_at,reason)"
                        + " VALUES(33,8,7,DATE '2026-04-01',?,'original'),(34,9,7,DATE"
                        + " '2026-04-01',NULL,'control')",
                open ? null : LocalDate.of(2026, 4, 10));
        jdbc.update(
                "INSERT INTO"
                    + " headman_assistants(id,group_id,student_id,permissions,assigned_by,is_active)"
                    + " VALUES(22,7,9,ARRAY['HOMEWORK'],8,true)");
    }

    UUID operation() {
        var operation = UUID.randomUUID();
        jdbc.update(
                """
INSERT INTO academic_identity_operations(operation_id,command_hash,local_command_hash,command_payload,local_payload,
    actor_id,actor_session_id,actor_session_version,actor_roles_version,kind,phase,auth_operation_revision,created_at,updated_at)
VALUES(?,?,?,decode('01','hex'),decode('01','hex'),99,?,1,1,'HEADMAN_REPLACE','AUTH_PREPARED',1,NOW(),NOW())
""",
                operation,
                new byte[32],
                new byte[32],
                UUID.randomUUID());
        return operation;
    }

    List<AcademicIdentityCommitReceipt> apply(
            AcademicIdentityLocalCommand local,
            IdentityOperationKind kind,
            IdentityOperationResult prepared) {
        var committed =
                new TransactionTemplate(transactions)
                        .execute(tx -> writer.apply(local, kind, prepared, 99));
        for (var receipt : committed) {
            org.assertj.core.api.Assertions.assertThat(
                            jdbc.queryForObject(
                                    "SELECT academic_revision FROM academic_profiles WHERE id=?",
                                    Long.class,
                                    receipt.getUserId()))
                    .isEqualTo(receipt.getCommittedAcademicRevision());
            org.assertj.core.api.Assertions.assertThat(
                            jdbc.queryForObject(
                                    "SELECT applied_auth_roles_version FROM academic_profiles WHERE"
                                        + " id=?",
                                    Long.class,
                                    receipt.getUserId()))
                    .isEqualTo(receipt.getAppliedAuthRolesVersion());
        }
        return committed;
    }

    List<String> history(long user) {
        return jdbc.queryForList(
                "SELECT to_jsonb(h)::text FROM student_group_history h WHERE user_id=? ORDER BY id",
                String.class,
                user);
    }

    AcademicIdentityLocalCommand command(String action, Long expected, Long candidate) {
        return new AcademicIdentityLocalCommand(
                new AcademicIdentityLocalCommand.ApiIntent(action, 8, "0".repeat(64)),
                null,
                EFFECTIVE,
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
}
