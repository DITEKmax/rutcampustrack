package ru.rutcampustrack.academic.user;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;

import ru.rutcampustrack.academic.contract.dto.user.*;
import ru.rutcampustrack.academic.contract.enums.UserRole;
import ru.rutcampustrack.academic.entity.User;
import ru.rutcampustrack.academic.exception.BadRequestException;
import ru.rutcampustrack.academic.identitycatalog.AuthIdentityDataClient;
import ru.rutcampustrack.academic.identityoperation.*;
import ru.rutcampustrack.academic.repository.*;
import ru.rutcampustrack.academic.security.RequestContext;
import ru.rutcampustrack.authauthority.grpc.*;
import ru.rutcampustrack.authoperations.grpc.*;

import java.sql.ResultSet;
import java.time.LocalDate;
import java.util.*;
import java.util.function.Function;

/** Existing role-specific input outcomes through the real Academic command producer. */
class UserServiceTelegramRequiredTest {
    private final UserIdentityCommandFixture fixture = new UserIdentityCommandFixture();

    private CreateUserRequest req(UserRole role, Long telegramId, String employeeNumber) {
        return new CreateUserRequest(
                "Иванов",
                "Иван",
                "Иванович",
                role,
                role == UserRole.STUDENT ? 1L : null,
                employeeNumber,
                telegramId);
    }

    @Test
    void studentWithoutTelegramIdIsRejected() {
        assertThatThrownBy(() -> fixture.service.createUser(req(UserRole.STUDENT, null, null)))
                .isInstanceOfSatisfying(
                        BadRequestException.class,
                        ex -> {
                            assertThat(ex.getField()).isEqualTo("telegramId");
                            assertThat(ex.getMessage())
                                    .isEqualTo("Telegram ID обязателен для студента");
                        });
        verify(fixture.users, never()).save(any());
        verify(fixture.users, never()).nextStudentLoginSeq();
        fixture.notAccepted();
    }

    @Test
    void studentWithZeroTelegramIdIsRejected() {
        rejected(req(UserRole.STUDENT, 0L, null), "telegramId");
    }

    @Test
    void studentWithoutGroupIsRejectedBeforePersistence() {
        rejected(
                new CreateUserRequest(
                        "Иванов", "Иван", null, UserRole.STUDENT, null, null, 123456789L),
                "groupId");
    }

    @Test
    void studentCannotCarryTeacherEmployeeNumber() {
        rejected(req(UserRole.STUDENT, 123456789L, "EMP-777"), "employeeNumber");
    }

    @Test
    void teacherCannotCarryStudentGroup() {
        rejected(
                new CreateUserRequest(
                        "Иванов", "Иван", null, UserRole.TEACHER, 1L, "EMP-777", null),
                "groupId");
    }

    @Test
    void adminCannotCarryTeacherEmployeeNumber() {
        rejected(
                new CreateUserRequest(
                        "Иванов",
                        "Иван",
                        null,
                        UserRole.ADMIN,
                        null,
                        "EMP-777",
                        null,
                        new AdminAccessInput("FULL", List.of())),
                "employeeNumber");
    }

    @Test
    void teacherWithoutTelegramIdIsAccepted() {
        accepted(req(UserRole.TEACHER, null, "EMP-777"));
        assertThat(fixture.accepted.authCommand().getCreateIdentity().getTelegramId()).isZero();
        assertThat(fixture.local().profile().employeeNumber()).isEqualTo("EMP-777");
    }

    @Test
    void adminWithoutTelegramIdIsAccepted() {
        accepted(
                new CreateUserRequest(
                        "Иванов",
                        "Иван",
                        "Иванович",
                        UserRole.ADMIN,
                        null,
                        null,
                        null,
                        new AdminAccessInput("FULL", List.of())));
        assertThat(fixture.accepted.authCommand().getCreateIdentity().getTelegramId()).isZero();
        assertThat(fixture.accepted.authCommand().getTargets(0).getDesiredAdminProfile().getMode())
                .isEqualTo("FULL");
    }

    @Test
    void studentWithTelegramIdIsAccepted() {
        accepted(req(UserRole.STUDENT, 123456789L, null));
        assertThat(fixture.accepted.authCommand().getCreateIdentity().getTelegramId())
                .isEqualTo(123456789L);
        assertThat(fixture.accepted.authCommand().getTargets(0).getDesiredGrants(0).getGroupId())
                .isEqualTo(1);
        assertThat(fixture.local().enrollmentSemesterId()).isEqualTo(1);
    }

    private void rejected(CreateUserRequest request, String field) {
        assertThatThrownBy(() -> fixture.service.createUser(request))
                .isInstanceOfSatisfying(
                        BadRequestException.class,
                        ex -> assertThat(ex.getField()).isEqualTo(field));
        verify(fixture.users, never()).save(any());
        fixture.notAccepted();
    }

    private void accepted(CreateUserRequest request) {
        assertThatCode(() -> fixture.service.createUser(request)).doesNotThrowAnyException();
        assertThat(fixture.accepted).isNotNull();
        assertThat(fixture.accepted.authCommand().getTargets(0).getDesiredGrants(0).getRole())
                .isEqualTo(request.role().name());
        verify(fixture.users, never()).save(any()); // Academic never writes the identity view.
    }
}

/** Local SQL/remote boundaries are mocked; input validation and command construction are real. */
final class UserIdentityCommandFixture {
    final UserRepository users = mock(UserRepository.class);
    final UserRoleGrantWriter oldWriter = mock(UserRoleGrantWriter.class);
    final AuthIdentityDataClient auth = mock(AuthIdentityDataClient.class);
    final AcademicIdentityHttpOperations http = mock(AcademicIdentityHttpOperations.class);
    final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    final Map<String, Object> profile = new HashMap<>();
    final JdbcTemplate jdbc;
    final UserService service;
    final AcademicUserIdentityOperations operations;
    AcademicIdentityCoordinator.AcceptedCommand accepted;
    final UUID id = UUID.fromString("00000000-0000-0000-0000-000000000042");

    UserIdentityCommandFixture() {
        profile.put("directory_role", "STUDENT");
        profile.put("directory_status", "ACTIVE");
        profile.put("last_name", "Петров");
        profile.put("first_name", "Пётр");
        profile.put("employee_number", "EMP-001");
        profile.put("academic_revision", 2L);
        profile.put("applied_auth_roles_version", 3L);
        jdbc =
                mock(
                        JdbcTemplate.class,
                        call -> {
                            if (call.getMethod().getName().equals("query")) {
                                String sql = call.getArgument(0);
                                var values =
                                        sql.contains("FROM academic_profiles")
                                                ? profile
                                                : sql.contains("FROM semesters")
                                                        ? Map.<String, Object>of(
                                                                "id",
                                                                1L,
                                                                "date_from",
                                                                LocalDate.of(2026, 9, 1),
                                                                "date_to",
                                                                LocalDate.of(2027, 1, 31))
                                                        : Map.<String, Object>of(
                                                                "academic_revision",
                                                                1L,
                                                                "coverage_from",
                                                                LocalDate.of(2026, 9, 1),
                                                                "writer_version",
                                                                "managed_v1");
                                var row =
                                        mock(
                                                ResultSet.class,
                                                read -> {
                                                    if (read.getArguments().length == 0)
                                                        return RETURNS_DEFAULTS.answer(read);
                                                    Object value = values.get(read.getArgument(0));
                                                    return switch (read.getMethod().getName()) {
                                                        case "getString" ->
                                                                value == null
                                                                        ? null
                                                                        : value.toString();
                                                        case "getLong" ->
                                                                value == null
                                                                        ? 0L
                                                                        : ((Number) value)
                                                                                .longValue();
                                                        case "getObject" -> value;
                                                        default -> RETURNS_DEFAULTS.answer(read);
                                                    };
                                                });
                                RowMapper<?> rowMapper = call.getArgument(1);
                                return List.of(rowMapper.mapRow(row, 0));
                            }
                            if (call.getMethod().getName().equals("queryForList")
                                    && ((String) call.getArgument(0))
                                            .contains("FROM groups WHERE id=?")) return List.of(1L);
                            return RETURNS_DEFAULTS.answer(call);
                        });
        when(http.execute(any(AcademicIdentityLocalCommand.ApiIntent.class), any()))
                .thenAnswer(
                        call -> {
                            Function<UUID, AcademicIdentityCoordinator.AcceptedCommand> build =
                                    call.getArgument(1);
                            accepted = build.apply(id);
                            return completed(accepted);
                        });
        identity(
                List.of(
                        AuthorityRoleGrant.newBuilder()
                                .setGrantId(11)
                                .setRole("TEACHER")
                                .setStatus("ACTIVE")
                                .build(),
                        AuthorityRoleGrant.newBuilder()
                                .setGrantId(12)
                                .setRole("STUDENT")
                                .setStatus("ACTIVE")
                                .setGroupId(1)
                                .build()));
        operations =
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
        service =
                new UserService(
                        users,
                        mock(HeadmanAssistantRepository.class),
                        mock(StudentGroupHistoryRepository.class),
                        new RequestContext(),
                        mock(UserAssembler.class),
                        null,
                        mock(org.springframework.context.ApplicationEventPublisher.class),
                        mock(GroupRepository.class),
                        mock(SemesterRepository.class),
                        mock(GroupHistoryCoverageRepository.class),
                        oldWriter,
                        mock(UserRoleGrantReader.class),
                        mock(ru.rutcampustrack.academic.group.GroupHeadmanAssignmentService.class),
                        mock(org.springframework.security.crypto.password.PasswordEncoder.class));
        org.springframework.test.util.ReflectionTestUtils.setField(
                service, "identityOperations", operations);
        org.springframework.test.util.ReflectionTestUtils.setField(service, "identityData", auth);
        var user = new User();
        user.setId(42L);
        when(users.findByIdIncludingArchived(42L)).thenReturn(Optional.of(user));
        when(auth.initialCredentials(eq(List.of(42L)), anyLong()))
                .thenReturn(
                        ReadInitialCredentialsResponse.newBuilder()
                                .addCredentials(
                                        InitialCredentialSnapshot.newBuilder().setUserId(42))
                                .build());
    }

    void identity(List<AuthorityRoleGrant> grants) {
        var identity =
                IdentityAuthoritySnapshot.newBuilder()
                        .setUserId(42)
                        .setAccountState(AuthorityAccountState.AUTHORITY_ACCOUNT_STATE_ACTIVE)
                        .setAcademicRevision(2)
                        .setRolesVersion(3)
                        .setCredentialsVersion(1)
                        .setTelegramBindingVersion(1)
                        .setTelegramId(123456789L)
                        .addAllGrants(grants)
                        .build();
        when(auth.readServiceIdentity(eq(List.of(42L)), anyLong()))
                .thenReturn(Map.of(42L, identity));
    }

    AcademicIdentityLocalCommand local() {
        try {
            return mapper.readValue(
                    accepted.localPayload().toByteArray(), AcademicIdentityLocalCommand.class);
        } catch (java.io.IOException failure) {
            throw new AssertionError(failure);
        }
    }

    void notAccepted() {
        assertThat(accepted).isNull();
        verify(auth, never()).initialCredentials(any(), anyLong());
    }

    AcademicIdentityCoordinator.Operation completed(
            AcademicIdentityCoordinator.AcceptedCommand command) {
        return new AcademicIdentityCoordinator.Operation(
                id.toString(),
                AcademicIdentityCoordinator.Phase.SUCCEEDED,
                command.authCommand(),
                IdentityOperationResult.newBuilder()
                        .addTargets(PreparedIdentityTarget.newBuilder().setUserId(42))
                        .build(),
                List.of());
    }

    static final class LocalTransactions extends AbstractPlatformTransactionManager {
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
