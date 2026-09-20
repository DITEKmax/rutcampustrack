package ru.rutcampustrack.auth.service;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.security.crypto.password.PasswordEncoder;
import ru.rutcampustrack.auth.config.JwtProperties;
import ru.rutcampustrack.auth.dto.ChangePasswordRequest;
import ru.rutcampustrack.auth.repository.UserRepository;
import ru.rutcampustrack.auth.security.SessionPrincipal;
import ru.rutcampustrack.auth.session.AuthSessionException;
import ru.rutcampustrack.auth.session.SessionLifecycleService;
import ru.rutcampustrack.auth.session.model.AuthMethod;
import ru.rutcampustrack.auth.session.model.AuthRole;
import ru.rutcampustrack.auth.session.model.RoleGrant;
import ru.rutcampustrack.auth.session.model.RoleStatus;
import ru.rutcampustrack.auth.session.model.SessionSnapshot;
import ru.rutcampustrack.auth.session.port.AuthSessionQueryPort;
import ru.rutcampustrack.auth.session.port.CredentialSessionTransactionPort;
import ru.rutcampustrack.auth.session.port.SessionStatePort;
import ru.rutcampustrack.shared.observability.BusinessMetrics;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthServiceRepositoryFailureTest {

    private static final long USER_ID = 42L;
    private static final UUID SESSION_ID =
            UUID.fromString("55555555-5555-4555-8555-555555555555");
    private static final Instant NOW = Instant.parse("2026-09-10T10:00:00Z");

    @Test
    void changePassword_repositoryFailureIsTypedAuthorityUnavailableBeforeMutation() {
        UserRepository userRepository = mock(UserRepository.class);
        JwtService jwtService = mock(JwtService.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        JwtProperties jwtProperties = mock(JwtProperties.class);
        LoginRateLimiter loginRateLimiter = mock(LoginRateLimiter.class);
        BcryptConcurrencyGuard bcryptGuard = mock(BcryptConcurrencyGuard.class);
        BusinessMetrics businessMetrics = mock(BusinessMetrics.class);
        AuthSessionQueryPort queryPort = mock(AuthSessionQueryPort.class);
        SessionStatePort statePort = mock(SessionStatePort.class);
        CredentialSessionTransactionPort credentialPort = mock(CredentialSessionTransactionPort.class);
        SessionLifecycleService lifecycle = new SessionLifecycleService(statePort, credentialPort);
        SessionPrincipal principal = new SessionPrincipal(
                USER_ID, SESSION_ID, 2L, 3L, AuthRole.STUDENT, RoleStatus.ACTIVE,
                10L, false, false);

        when(statePort.snapshot(any(SessionStatePort.SnapshotCommand.class)))
                .thenReturn(SessionStatePort.SnapshotResult.success(activeSnapshot()));
        when(userRepository.findById(USER_ID))
                .thenThrow(new DataAccessResourceFailureException("database unavailable"));
        AuthService authService = new AuthService(
                userRepository, jwtService, passwordEncoder, jwtProperties, loginRateLimiter,
                bcryptGuard, businessMetrics, queryPort, lifecycle,
                Clock.fixed(NOW, ZoneOffset.UTC));

        assertThatThrownBy(() -> authService.changePassword(
                principal, new ChangePasswordRequest("old-password", "NewPassword1!")))
                .isInstanceOfSatisfying(AuthSessionException.class,
                        exception -> assertThat(exception.code())
                                .isEqualTo(AuthSessionException.Code.AUTHORITY_UNAVAILABLE));

        verify(credentialPort, never()).changePassword(any());
    }

    private static SessionSnapshot activeSnapshot() {
        RoleGrant grant = new RoleGrant(
                7L, USER_ID, AuthRole.STUDENT, RoleStatus.ACTIVE, 10L,
                NOW, NOW);
        return new SessionSnapshot(
                SESSION_ID, USER_ID, 2L, 3L, grant, List.of(grant),
                NOW.plusSeconds(3600), NOW, NOW, null, null,
                AuthMethod.PASSWORD, "web", "campus");
    }
}
