package ru.rutcampustrack.academic.identityoperation;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.protobuf.ByteString;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import ru.rutcampustrack.academic.security.RequestContext;
import ru.rutcampustrack.academic.user.UserArchiveRepository;
import ru.rutcampustrack.authoperations.grpc.*;
import ru.rutcampustrack.shared.security.InternalJwtClaims;

import java.security.MessageDigest;
import java.util.List;
import java.util.UUID;

/** Own local terminal CAS only; it does not substitute for Auth's real two-owner NOOP proof. */
@Testcontainers
class AcademicIdentityAdminNoopIT {
    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    private static JdbcTemplate jdbc;
    private static TransactionTemplate tx;
    private static final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private final AcademicIdentityDomainWriter writer = mock(AcademicIdentityDomainWriter.class);
    private final AcademicIdentityCacheInvalidator caches =
            mock(AcademicIdentityCacheInvalidator.class);
    private JdbcAcademicIdentityOperationStore store;
    private AcademicIdentityCoordinator.AcceptedCommand command;
    private IdentityOperationResult noop;

    @BeforeAll
    static void schema() {
        var source =
                new DriverManagerDataSource(
                        postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        Flyway.configure()
                .dataSource(source)
                .locations("classpath:db/academic-migration")
                .load()
                .migrate();
        jdbc = new JdbcTemplate(source);
        tx = new TransactionTemplate(new DataSourceTransactionManager(source));
        jdbc.update(
                "INSERT INTO"
                    + " academic_profiles(id,directory_role,directory_status,last_name,first_name,employee_number,academic_revision,applied_auth_roles_version,created_at,updated_at)"
                    + " VALUES(8,'TEACHER','ACTIVE','Петров','Пётр','EMP-8',2,4,now(),now())");
    }

    @BeforeEach
    void setup() throws Exception {
        var context = new RequestContext();
        context.setClaims(
                new InternalJwtClaims(
                        99, UUID.randomUUID(), 1, 4, "ADMIN", "ACTIVE", null, false, false));
        store =
                new JdbcAcademicIdentityOperationStore(
                        jdbc, mapper, context, writer, caches, mock(UserArchiveRepository.class));
        var auth =
                PrepareIdentityOperationRequest.newBuilder()
                        .setOperationId(UUID.randomUUID().toString())
                        .setKind(IdentityOperationKind.IDENTITY_OPERATION_KIND_ADMIN_ACCESS)
                        .addTargets(
                                IdentityTargetCommand.newBuilder()
                                        .setUserId(8)
                                        .setExpectedRolesVersion(4)
                                        .setExpectedCredentialsVersion(1)
                                        .setExpectedTelegramBindingVersion(1)
                                        .setExpectedAcademicRevision(2)
                                        .setDesiredAccountState(
                                                IdentityAccountState.IDENTITY_ACCOUNT_STATE_ACTIVE)
                                        .setDesiredAdminProfile(
                                                AdmissionAdminProfile.getDefaultInstance())
                                        .addDesiredGrants(
                                                AdmissionGrant.newBuilder()
                                                        .setGrantId(12)
                                                        .setRole("TEACHER")
                                                        .setStatus("ACTIVE")));
        auth.setCommandHash(hash(auth.build().toByteArray()));
        byte[] local =
                mapper.writeValueAsBytes(
                        new AcademicIdentityLocalCommand(
                                new AcademicIdentityLocalCommand.ApiIntent(
                                        "ADMIN_ACCESS", 8, "0".repeat(64)),
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                "admin-access",
                                List.of()));
        command =
                new AcademicIdentityCoordinator.AcceptedCommand(
                        auth.build(), hash(local), ByteString.copyFrom(local));
        noop =
                IdentityOperationResult.newBuilder()
                        .setOperationId(auth.getOperationId())
                        .setCommandHash(auth.getCommandHash())
                        .setKind(auth.getKind())
                        .setPhase(IdentityOperationPhase.IDENTITY_OPERATION_PHASE_NOOP)
                        .setOperationRevision(1)
                        .addTargets(
                                PreparedIdentityTarget.newBuilder()
                                        .setUserId(8)
                                        .setLogin("teacher-1234")
                                        .setFinalRolesVersion(4)
                                        .setCredentialsVersion(1)
                                        .setTelegramBindingVersion(1)
                                        .setExpectedAcademicRevision(2)
                                        .addAllGrants(auth.getTargets(0).getDesiredGrantsList()))
                        .build();
    }

    @Test
    void acceptedNoopIsDurableWithoutApplyingOrChangingLocalProfile() {
        var accepted = tx.execute(status -> store.accept(command));
        var terminal = tx.execute(status -> store.nooped(accepted, noop));
        assertThat(terminal.phase()).isEqualTo(AcademicIdentityCoordinator.Phase.SUCCEEDED);
        assertThat(terminal.prepared()).isEqualTo(noop);
        assertThat(terminal.receipts()).isEmpty();
        assertThat(tx.execute(status -> store.nooped(accepted, noop)).prepared()).isEqualTo(noop);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT academic_revision FROM academic_profiles WHERE id=8",
                                Long.class))
                .isEqualTo(2);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT applied_auth_roles_version FROM academic_profiles WHERE"
                                    + " id=8",
                                Long.class))
                .isEqualTo(4);
        verifyNoInteractions(writer, caches);
    }

    @Test
    void staleNoopCannotOverwriteAlreadyCommittedPositiveWork() {
        var accepted = tx.execute(status -> store.accept(command));
        var positive =
                noop.toBuilder()
                        .setPhase(IdentityOperationPhase.IDENTITY_OPERATION_PHASE_PREPARED_FENCED)
                        .setTargets(0, noop.getTargets(0).toBuilder().setFinalRolesVersion(5))
                        .build();
        tx.executeWithoutResult(
                status -> {
                    jdbc.update(
                            "UPDATE academic_identity_operations SET"
                                + " phase='LOCAL_COMMITTED',auth_operation_revision=1,auth_prepared_payload=?"
                                + " WHERE operation_id=?",
                            positive.toByteArray(),
                            UUID.fromString(command.authCommand().getOperationId()));
                    jdbc.update(
                            "UPDATE academic_identity_operation_targets SET"
                                + " fenced=true,committed_academic_revision=3,applied_auth_roles_version=5"
                                + " WHERE operation_id=?",
                            UUID.fromString(command.authCommand().getOperationId()));
                });
        assertThatThrownBy(() -> tx.execute(status -> store.nooped(accepted, noop)))
                .isInstanceOfSatisfying(
                        StatusRuntimeException.class,
                        e -> assertThat(e.getStatus().getCode()).isEqualTo(Status.Code.ABORTED));
        var unchanged = store.find(accepted.id());
        assertThat(unchanged.phase()).isEqualTo(AcademicIdentityCoordinator.Phase.LOCAL_COMMITTED);
        assertThat(unchanged.prepared()).isEqualTo(positive);
        assertThat(unchanged.receipts())
                .singleElement()
                .satisfies(
                        receipt -> assertThat(receipt.getAppliedAuthRolesVersion()).isEqualTo(5));
        verifyNoInteractions(writer, caches);
    }

    private static ByteString hash(byte[] value) throws Exception {
        return ByteString.copyFrom(MessageDigest.getInstance("SHA-256").digest(value));
    }
}
