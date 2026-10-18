package ru.rutcampustrack.academic.user;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.grpc.Metadata;
import io.grpc.Status;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import ru.rutcampustrack.academic.contract.dto.user.CreateUserRequest;
import ru.rutcampustrack.academic.contract.enums.UserRole;
import ru.rutcampustrack.academic.exception.ConflictException;
import ru.rutcampustrack.academic.identitycatalog.IdentityCatalogSynchronizer;
import ru.rutcampustrack.academic.identityoperation.*;
import ru.rutcampustrack.auth.authority.AuthIdentityOperationStore;
import ru.rutcampustrack.auth.authority.AuthInternalActorReader;
import ru.rutcampustrack.auth.security.SessionPrincipal;
import ru.rutcampustrack.auth.service.BcryptConcurrencyGuard;
import ru.rutcampustrack.auth.session.model.AuthRole;
import ru.rutcampustrack.auth.session.model.RoleStatus;
import ru.rutcampustrack.authauthority.grpc.*;
import ru.rutcampustrack.authoperations.grpc.*;

import java.sql.ResultSet;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

/** Field-specific duplicate outcomes at the actual Academic/Auth operation seams. */
class UserServiceConflictTest {
    private final UserIdentityCommandFixture fixture = new UserIdentityCommandFixture();

    private CreateUserRequest studentRequest() {
        return new CreateUserRequest(
                "Иванов", "Иван", "Иванович", UserRole.STUDENT, 1L, null, 123456789L);
    }

    @Test
    void existingTelegramIdThrowsConflictWithFieldTelegramId() {
        var rejected =
                new RejectionBoundary(IdentityOperationPhase.IDENTITY_OPERATION_PHASE_REJECTED);
        rejected.install();
        assertTelegramConflict(() -> fixture.service.createUser(studentRequest()));
        // Replay reads the saved closed subtype; it does not infer it from a fresh catalog row.
        when(rejected.store.acceptedForActor(eq(fixture.id.toString()), any()))
                .thenAnswer(call -> fixture.accepted);
        assertTelegramConflict(() -> fixture.service.createUser(studentRequest()));
        verify(rejected.auth, times(1)).prepare(any());
        verify(rejected.store, never()).apply(any());
        verify(fixture.users, never()).save(any());

        // A lost/unknown rejection acknowledgement cannot become a field conflict.
        var unknown = new RejectionBoundary(null);
        unknown.install();
        assertThatThrownBy(() -> fixture.service.createUser(studentRequest()))
                .isInstanceOf(AcademicIdentityPendingException.class);
        verify(unknown.store, never()).rejected(any(), any());

        // A concurrent positive reservation wins over the requested negative tombstone.
        var positive =
                new RejectionBoundary(
                        IdentityOperationPhase.IDENTITY_OPERATION_PHASE_PREPARED_FENCED);
        positive.install();
        assertThatThrownBy(() -> fixture.service.createUser(studentRequest()))
                .isInstanceOf(AcademicIdentityPendingException.class);
        verify(positive.store, never()).rejected(any(), any());
    }

    @Test
    void existingEmployeeNumberThrowsConflictWithFieldEmployeeNumber() {
        when(fixture.jdbc.queryForObject(
                        contains("employee_number=?"), eq(Boolean.class), eq("EMP-001"), eq(0L)))
                .thenReturn(true);
        var request =
                new CreateUserRequest(
                        "Учителев", "Учитель", null, UserRole.TEACHER, null, "EMP-001", null);
        assertThatThrownBy(() -> fixture.service.createUser(request))
                .isInstanceOfSatisfying(
                        ConflictException.class,
                        ex -> assertThat(ex.getField()).isEqualTo("employeeNumber"));
        fixture.notAccepted();
        verify(fixture.users, never()).save(any());
    }

    @Test
    void noConflictWhenNoExistingValues() {
        fixture.service.createUser(studentRequest());
        var produced = actualAuthCredentialInsert(fixture.accepted.authCommand());
        String hash = (String) produced[2];
        String initial = (String) produced[3];
        assertThat(initial != null && initial.length() == 12)
                .as("administratively visible initial credential is retained by Auth")
                .isTrue();
        assertThat(new BCryptPasswordEncoder().matches(initial, hash))
                .as("actual Auth INSERT hash verifies the issued credential")
                .isTrue();
        assertThat(hash.startsWith("$2a$10$"))
                .as("Auth-compatible BCrypt cost and version")
                .isTrue();
        when(fixture.auth.initialCredentials(eq(List.of(42L)), anyLong()))
                .thenReturn(
                        ReadInitialCredentialsResponse.newBuilder()
                                .addCredentials(
                                        InitialCredentialSnapshot.newBuilder()
                                                .setUserId(42)
                                                .setInitialPassword(initial)
                                                .setPasswordChanged(false))
                                .build());
        fixture.service.createUser(studentRequest());
        verify(fixture.auth, times(2)).initialCredentials(eq(List.of(42L)), anyLong());
        var assembler =
                (UserAssembler)
                        org.springframework.test.util.ReflectionTestUtils.getField(
                                fixture.service, "userAssembler");
        verify(assembler).toCreatedModel(any(), eq(initial));
        var user = fixture.users.findByIdIncludingArchived(42L).orElseThrow();
        assertThat(user.isPasswordChanged()).isFalse();
        assertThat(user.getInitialPassword()).isNull();
        assertThat(user.getPasswordHash()).isNull();
        verify(fixture.users, never()).save(any());
    }

    private static void assertTelegramConflict(Runnable call) {
        assertThatThrownBy(call::run)
                .isInstanceOfSatisfying(
                        ConflictException.class,
                        ex -> assertThat(ex.getField()).isEqualTo("telegramId"));
    }

    /** Stops at the real Auth INSERT: no DB, server or new authority implementation. */
    private static Object[] actualAuthCredentialInsert(PrepareIdentityOperationRequest command) {
        var captured = new AtomicReference<Object[]>();
        var stop = new IllegalStateException("synthetic capture after Auth INSERT arguments");
        JdbcTemplate jdbc =
                mock(
                        JdbcTemplate.class,
                        call -> {
                            String method = call.getMethod().getName();
                            if (call.getArguments().length == 0)
                                return RETURNS_DEFAULTS.answer(call);
                            String sql = call.getArgument(0);
                            if (method.equals("queryForList"))
                                return sql.contains("FROM auth_identities WHERE id=? FOR UPDATE")
                                        ? List.of(99L)
                                        : List.of();
                            if (method.equals("queryForObject")) {
                                if (sql.contains("nextval")) return 42L;
                                return sql.contains("JOIN auth_sessions");
                            }
                            if (method.equals("query")) {
                                if (!sql.contains("SELECT p.version")
                                        && !sql.contains(
                                                "SELECT roles_version,credentials_version"))
                                    return List.of();
                                var row =
                                        mock(
                                                ResultSet.class,
                                                read -> {
                                                    Object column =
                                                            read.getArguments().length == 0
                                                                    ? null
                                                                    : read.getArgument(0);
                                                    return switch (read.getMethod().getName()) {
                                                        case "getLong" -> 1L;
                                                        case "getBoolean" -> true;
                                                        case "getString" ->
                                                                "mode".equals(column)
                                                                        ? "FULL"
                                                                        : "ACTIVE";
                                                        case "getArray", "getObject" -> null;
                                                        default -> RETURNS_DEFAULTS.answer(read);
                                                    };
                                                });
                                RowMapper<?> rowMapper = call.getArgument(1);
                                return List.of(rowMapper.mapRow(row, 0));
                            }
                            if (method.equals("update")) {
                                if (sql.contains("INSERT INTO auth_identities(")) {
                                    captured.set(
                                            Arrays.copyOfRange(
                                                    call.getArguments(),
                                                    1,
                                                    call.getArguments().length));
                                    assertThat(sql).contains("password_hash,initial_password");
                                    // This INSERT relies on the Auth schema's
                                    // password_changed=false default.
                                    assertThat(sql).doesNotContain("password_changed");
                                    throw stop;
                                }
                                return 1;
                            }
                            return RETURNS_DEFAULTS.answer(call);
                        });
        var actors = mock(AuthInternalActorReader.class);
        when(actors.read())
                .thenReturn(
                        new SessionPrincipal(
                                99,
                                UUID.randomUUID(),
                                1,
                                1,
                                AuthRole.ADMIN,
                                RoleStatus.ACTIVE,
                                null,
                                false,
                                false));
        var auth =
                new AuthIdentityOperationStore(
                        jdbc,
                        new ObjectMapper().findAndRegisterModules(),
                        new UserIdentityCommandFixture.LocalTransactions(),
                        actors,
                        new BCryptPasswordEncoder(),
                        new BcryptConcurrencyGuard(1, 0));
        assertThatThrownBy(() -> auth.prepare(command)).isSameAs(stop);
        assertThat(captured.get()).isNotNull();
        return captured.get();
    }

    private final class RejectionBoundary {
        final JdbcAcademicIdentityOperationStore store =
                mock(JdbcAcademicIdentityOperationStore.class);
        final AcademicIdentityCoordinator.Auth auth = mock(AcademicIdentityCoordinator.Auth.class);
        AcademicIdentityCoordinator.Operation current;
        final IdentityOperationPhase reply;

        RejectionBoundary(IdentityOperationPhase reply) {
            this.reply = reply;
        }

        void install() {
            var request = new MockHttpServletRequest();
            request.addHeader("Idempotency-Key", fixture.id.toString());
            var coordinator =
                    new AcademicIdentityCoordinator(
                            store, auth, mock(IdentityCatalogSynchronizer.class));
            var http =
                    new AcademicIdentityHttpOperations(
                            request, new MockHttpServletResponse(), store, coordinator);
            doAnswer(
                            call -> {
                                Function<UUID, AcademicIdentityCoordinator.AcceptedCommand> build =
                                        call.getArgument(1);
                                return http.execute(
                                        call.getArgument(0),
                                        id -> {
                                            fixture.accepted = build.apply(id);
                                            return fixture.accepted;
                                        });
                            })
                    .when(fixture.http)
                    .execute(any(AcademicIdentityLocalCommand.ApiIntent.class), any());
            when(store.accept(any()))
                    .thenAnswer(
                            call -> {
                                var command =
                                        (AcademicIdentityCoordinator.AcceptedCommand)
                                                call.getArgument(0);
                                if (current == null)
                                    current =
                                            new AcademicIdentityCoordinator.Operation(
                                                    fixture.id.toString(),
                                                    AcademicIdentityCoordinator.Phase.ACCEPTED,
                                                    command.authCommand(),
                                                    null,
                                                    List.of());
                                return current;
                            });
            var trailers = new Metadata();
            trailers.put(
                    Metadata.Key.of("x-rct-error-code", Metadata.ASCII_STRING_MARSHALLER),
                    "TELEGRAM_ID_CONFLICT");
            when(auth.prepare(any())).thenThrow(Status.ALREADY_EXISTS.asRuntimeException(trailers));
            when(store.prepareRejection(
                            any(),
                            eq(
                                    IdentityOperationRejectionReason
                                            .IDENTITY_OPERATION_REJECTION_REASON_TARGET_CONFLICT),
                            eq("TELEGRAM_ID_CONFLICT")))
                    .thenAnswer(
                            call ->
                                    RejectUnpreparedIdentityOperationRequest.newBuilder()
                                            .setCanonicalPrepare(current.command())
                                            .setReason(
                                                    IdentityOperationRejectionReason
                                                            .IDENTITY_OPERATION_REJECTION_REASON_TARGET_CONFLICT)
                                            .build());
            when(auth.reject(any()))
                    .thenAnswer(
                            call -> {
                                if (reply == null) throw Status.UNAVAILABLE.asRuntimeException();
                                return IdentityOperationResult.newBuilder()
                                        .setOperationId(current.id())
                                        .setCommandHash(current.command().getCommandHash())
                                        .setKind(current.command().getKind())
                                        .setPhase(reply)
                                        .setOperationRevision(1)
                                        .setErrorCode(
                                                reply
                                                                == IdentityOperationPhase
                                                                        .IDENTITY_OPERATION_PHASE_REJECTED
                                                        ? "PREPARE_REJECTED_TARGET_CONFLICT"
                                                        : "")
                                        .addAllTargets(
                                                reply
                                                                == IdentityOperationPhase
                                                                        .IDENTITY_OPERATION_PHASE_REJECTED
                                                        ? List.of()
                                                        : List.of(
                                                                PreparedIdentityTarget.newBuilder()
                                                                        .setUserId(42)
                                                                        .setFinalRolesVersion(1)
                                                                        .setCredentialsVersion(1)
                                                                        .setTelegramBindingVersion(
                                                                                1)
                                                                        .addGrants(
                                                                                AdmissionGrant
                                                                                        .newBuilder()
                                                                                        .setGrantId(
                                                                                                7)
                                                                                        .setRole(
                                                                                                "STUDENT")
                                                                                        .setStatus(
                                                                                                "ACTIVE")
                                                                                        .setGroupId(
                                                                                                1))
                                                                        .build()))
                                        .build();
                            });
            when(store.rejected(any(), any()))
                    .thenAnswer(
                            call -> {
                                current =
                                        new AcademicIdentityCoordinator.Operation(
                                                current.id(),
                                                AcademicIdentityCoordinator.Phase.REJECTED,
                                                current.command(),
                                                call.getArgument(1),
                                                List.of(),
                                                "TELEGRAM_ID_CONFLICT");
                                return current;
                            });
            when(store.prepared(any(), any()))
                    .thenAnswer(
                            call -> {
                                current =
                                        new AcademicIdentityCoordinator.Operation(
                                                current.id(),
                                                AcademicIdentityCoordinator.Phase.AUTH_PREPARED,
                                                current.command(),
                                                call.getArgument(1),
                                                List.of(),
                                                "TELEGRAM_ID_CONFLICT");
                                return current;
                            });
            when(store.apply(any())).thenThrow(Status.UNAVAILABLE.asRuntimeException());
            when(store.findForActor(anyString())).thenAnswer(call -> current);
        }
    }
}
