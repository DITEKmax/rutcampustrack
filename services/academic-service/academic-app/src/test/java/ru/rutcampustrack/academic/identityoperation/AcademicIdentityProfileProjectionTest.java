package ru.rutcampustrack.academic.identityoperation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;

import ru.rutcampustrack.academic.contract.dto.user.UpdateUserRequest;
import ru.rutcampustrack.academic.contract.enums.UserRole;
import ru.rutcampustrack.academic.identitycatalog.AuthIdentityDataClient;
import ru.rutcampustrack.academic.security.RequestContext;
import ru.rutcampustrack.academic.user.AuthUserArchiveClient;
import ru.rutcampustrack.academic.user.UserArchiveRepository;
import ru.rutcampustrack.authauthority.grpc.*;

import java.sql.Types;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import javax.sql.rowset.RowSetMetaDataImpl;
import javax.sql.rowset.RowSetProvider;

class AcademicIdentityProfileProjectionTest {
    @Test
    void existingProfileCommandReadsOnlySelectedColumnsAndPreservesScalarStatus() throws Exception {
        JdbcTemplate jdbc =
                mock(
                        JdbcTemplate.class,
                        invocation -> {
                            String sql = invocation.getArgument(0);
                            if (!invocation.getMethod().getName().equals("query")
                                    || !sql.contains("FROM academic_profiles"))
                                return org.mockito.Answers.RETURNS_DEFAULTS.answer(invocation);
                            // A real JDBC rowset resolves names against the actual SELECT
                            // projection.
                            // Its getString("directory_status") throws SQLException if SQL omitted
                            // that field.
                            String projection =
                                    sql.substring(
                                            sql.indexOf("SELECT") + 6,
                                            sql.indexOf(" FROM academic_profiles"));
                            String[] columns = projection.trim().split(",");
                            var metadata = new RowSetMetaDataImpl();
                            metadata.setColumnCount(columns.length);
                            Map<String, Object> values =
                                    Map.of(
                                            "directory_role",
                                            "TEACHER",
                                            "directory_status",
                                            "SUSPENDED",
                                            "last_name",
                                            "Иванов",
                                            "first_name",
                                            "Иван",
                                            "employee_number",
                                            "EMP-123",
                                            "academic_revision",
                                            2L,
                                            "applied_auth_roles_version",
                                            3L);
                            for (int i = 0; i < columns.length; i++) {
                                String column = columns[i].trim();
                                metadata.setColumnName(i + 1, column);
                                metadata.setColumnLabel(i + 1, column);
                                metadata.setColumnType(
                                        i + 1,
                                        column.endsWith("revision") || column.endsWith("version")
                                                ? Types.BIGINT
                                                : Types.VARCHAR);
                            }
                            var rows = RowSetProvider.newFactory().createCachedRowSet();
                            rows.setMetaData(metadata);
                            rows.moveToInsertRow();
                            for (int i = 0; i < columns.length; i++) {
                                Object value = values.get(columns[i].trim());
                                if (value == null) rows.updateNull(i + 1);
                                else rows.updateObject(i + 1, value);
                            }
                            rows.insertRow();
                            rows.moveToCurrentRow();
                            rows.beforeFirst();
                            assertThat(rows.next()).isTrue();
                            RowMapper<?> mapper = invocation.getArgument(1);
                            return List.of(mapper.mapRow(rows, 0));
                        });
        var identity =
                IdentityAuthoritySnapshot.newBuilder()
                        .setUserId(8)
                        .setAcademicRevision(2)
                        .setRolesVersion(3)
                        .setCredentialsVersion(1)
                        .setTelegramBindingVersion(1)
                        .setAccountState(AuthorityAccountState.AUTHORITY_ACCOUNT_STATE_ACTIVE)
                        .addGrants(
                                AuthorityRoleGrant.newBuilder()
                                        .setGrantId(10)
                                        .setRole("TEACHER")
                                        .setStatus("ACTIVE"))
                        .build();
        var auth = mock(AuthIdentityDataClient.class);
        when(auth.readServiceIdentity(eq(List.of(8L)), anyLong())).thenReturn(Map.of(8L, identity));
        var http = mock(AcademicIdentityHttpOperations.class);
        var captured = new AtomicReference<AcademicIdentityCoordinator.AcceptedCommand>();
        when(http.execute(any(AcademicIdentityLocalCommand.ApiIntent.class), any()))
                .thenAnswer(
                        invocation -> {
                            Function<UUID, AcademicIdentityCoordinator.AcceptedCommand> build =
                                    invocation.getArgument(1);
                            captured.set(
                                    build.apply(
                                            UUID.fromString(
                                                    "00000000-0000-0000-0000-000000000001")));
                            return null;
                        });
        var mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        var service =
                new AcademicUserIdentityOperations(
                        jdbc,
                        http,
                        mapper,
                        new LocalTransactions(),
                        auth,
                        mock(AcademicIdentityReadFence.class),
                        new RequestContext(),
                        mock(UserArchiveRepository.class),
                        mock(AuthUserArchiveClient.class));
        service.replaceProfile(
                8,
                new UpdateUserRequest(
                        "Петров", "Пётр", null, UserRole.TEACHER, null, "EMP-123", null));
        var local =
                mapper.readValue(
                        captured.get().localPayload().toByteArray(),
                        AcademicIdentityLocalCommand.class);
        assertThat(local.profile().directoryStatus()).isEqualTo("SUSPENDED");
        assertThat(local.profile().lastName()).isEqualTo("Петров");
        assertThat(captured.get().authCommand().getTargets(0).getExpectedAcademicRevision())
                .isEqualTo(2);
        assertThat(captured.get().authCommand().getTargets(0).getDesiredGrants(0).getStatus())
                .isEqualTo("ACTIVE");
    }

    private static final class LocalTransactions extends AbstractPlatformTransactionManager {
        @Override
        protected Object doGetTransaction() {
            return new Object();
        }

        @Override
        protected void doBegin(Object transaction, TransactionDefinition definition) {}

        @Override
        protected void doCommit(DefaultTransactionStatus status) {}

        @Override
        protected void doRollback(DefaultTransactionStatus status) {}
    }
}
