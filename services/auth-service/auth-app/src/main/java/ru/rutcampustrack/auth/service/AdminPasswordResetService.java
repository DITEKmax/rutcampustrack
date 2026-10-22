package ru.rutcampustrack.auth.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import ru.rutcampustrack.auth.dto.AdminPasswordResetLinkResponse;
import ru.rutcampustrack.auth.repository.UserRepository;
import ru.rutcampustrack.auth.security.SessionPrincipal;
import ru.rutcampustrack.auth.session.AuthSessionException;
import ru.rutcampustrack.auth.session.SessionLifecycleService;
import ru.rutcampustrack.auth.session.model.AuthRole;
import ru.rutcampustrack.auth.session.model.RoleStatus;
import ru.rutcampustrack.auth.session.model.SessionSnapshot;
import ru.rutcampustrack.auth.session.port.CredentialSessionTransactionPort;

import java.time.Instant;

/** An admin-assisted route; public Telegram requests retain their decoy behavior. */
@Service
public class AdminPasswordResetService {
    @org.springframework.beans.factory.annotation.Autowired
    private ru.rutcampustrack.auth.security.AuthAdminAccess adminAccess;

    private final AuthService authService;
    private final UserRepository users;
    private final SessionLifecycleService sessions;
    private final String recoveryUrl;

    public AdminPasswordResetService(
            AuthService authService,
            UserRepository users,
            SessionLifecycleService sessions,
            @Value("${auth.password-reset-url:}") String recoveryUrl) {
        this.authService = authService;
        this.users = users;
        this.sessions = sessions;
        this.recoveryUrl = recoveryUrl;
    }

    @org.springframework.transaction.annotation.Transactional
    public AdminPasswordResetLinkResponse issueLink(SessionPrincipal principal, long userId) {
        adminAccess.authorize(principal, userId, "USER_CREDENTIALS_MANAGE");
        SessionSnapshot current = authService.admit(principal);
        if (current.activeRole() == null
                || current.activeRole().role() != AuthRole.ADMIN
                || current.activeRole().status() != RoleStatus.ACTIVE
                || current.activeRole().isReadOnly()) {
            throw new AuthSessionException(AuthSessionException.Code.ROLE_NOT_GRANTED);
        }
        PasswordResetLinkMaterial material =
                PasswordResetLinkMaterial.create(recoveryUrl, Instant.now());
        try {
            if (userId <= 0 || !users.existsById(userId)) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Пользователь не найден");
            }
        } catch (DataAccessException exception) {
            throw new AuthSessionException(
                    AuthSessionException.Code.AUTHORITY_UNAVAILABLE, exception);
        }
        try {
            var ticketHash =
                    new CredentialSessionTransactionPort.CredentialHash(material.ticketHash());
            // authorize holds the target identity lock and requires FULL for active ADMINs.
            // A ticket issued for an ordinary target must remain ordinary after later promotion.
            boolean issued =
                    users.hasActiveAdminGrant(userId)
                            ? sessions.issueAdministrativePasswordResetTicket(
                                    userId, ticketHash, material.response().expiresAt())
                            : sessions.issuePasswordResetTicket(
                                    userId, ticketHash, material.response().expiresAt());
            if (!issued) {
                throw new AuthSessionException(AuthSessionException.Code.AUTHORITY_UNAVAILABLE);
            }
        } catch (RuntimeException exception) {
            throw exception instanceof AuthSessionException typed
                    ? typed
                    : new AuthSessionException(
                            AuthSessionException.Code.AUTHORITY_UNAVAILABLE, exception);
        }
        return material.response();
    }
}
