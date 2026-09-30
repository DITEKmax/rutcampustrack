package ru.rutcampustrack.auth.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import ru.rutcampustrack.auth.config.OtpProperties;
import ru.rutcampustrack.auth.dto.AdminPasswordResetLinkResponse;
import ru.rutcampustrack.auth.repository.UserRepository;
import ru.rutcampustrack.auth.security.SessionPrincipal;
import ru.rutcampustrack.auth.session.AuthSessionException;
import ru.rutcampustrack.auth.session.SessionLifecycleService;
import ru.rutcampustrack.auth.session.model.AuthRole;
import ru.rutcampustrack.auth.session.model.RoleStatus;
import ru.rutcampustrack.auth.session.model.SessionSnapshot;
import ru.rutcampustrack.auth.session.port.CredentialSessionTransactionPort;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;

/** An admin-assisted route; public Telegram requests retain their decoy behavior. */
@Service
public final class AdminPasswordResetService {
    private final AuthService authService;
    private final UserRepository users;
    private final SessionLifecycleService sessions;
    private final OtpProperties otp;
    private final String recoveryUrl;
    private final SecureRandom random = new SecureRandom();

    public AdminPasswordResetService(AuthService authService, UserRepository users,
                                    SessionLifecycleService sessions, OtpProperties otp,
                                    @Value("${auth.password-reset-url:}") String recoveryUrl) {
        this.authService = authService;
        this.users = users;
        this.sessions = sessions;
        this.otp = otp;
        this.recoveryUrl = recoveryUrl;
    }

    public AdminPasswordResetLinkResponse issueLink(SessionPrincipal principal, long userId) {
        SessionSnapshot current = authService.admit(principal);
        if (current.activeRole() == null || current.activeRole().role() != AuthRole.ADMIN
                || current.activeRole().status() != RoleStatus.ACTIVE || current.activeRole().isReadOnly()) {
            throw new AuthSessionException(AuthSessionException.Code.ROLE_NOT_GRANTED);
        }
        String base = trustedRecoveryUrl();
        try {
            if (userId <= 0 || !users.existsById(userId)) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Пользователь не найден");
            }
        } catch (DataAccessException exception) {
            throw new AuthSessionException(AuthSessionException.Code.AUTHORITY_UNAVAILABLE, exception);
        }
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String ticket = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        int ttl = otp.ttlSeconds();
        if (ttl <= 0) throw new AuthSessionException(AuthSessionException.Code.AUTHORITY_UNAVAILABLE);
        Instant expiresAt = Instant.now().plusSeconds(ttl);
        try {
            if (!sessions.issuePasswordResetTicket(userId,
                    new CredentialSessionTransactionPort.CredentialHash(hash(ticket)), expiresAt)) {
                throw new AuthSessionException(AuthSessionException.Code.AUTHORITY_UNAVAILABLE);
            }
        } catch (RuntimeException exception) {
            throw exception instanceof AuthSessionException typed ? typed
                    : new AuthSessionException(AuthSessionException.Code.AUTHORITY_UNAVAILABLE, exception);
        }
        return new AdminPasswordResetLinkResponse(base + "#resetTicket=" + ticket
                + "&expiresAt=" + expiresAt, expiresAt, ttl);
    }

    private String trustedRecoveryUrl() {
        try {
            URI uri = URI.create(recoveryUrl);
            String host = uri.getHost();
            boolean loopback = "localhost".equalsIgnoreCase(host) || "127.0.0.1".equals(host)
                    || "[::1]".equals(host);
            if (host == null || uri.getRawUserInfo() != null || uri.getRawQuery() != null
                    || uri.getRawFragment() != null || uri.getPath() == null
                    || !(uri.getPath().endsWith("/password-reset") || uri.getPath().endsWith("/password-reset/"))
                    || !("https".equalsIgnoreCase(uri.getScheme())
                         || (loopback && "http".equalsIgnoreCase(uri.getScheme())))) {
                throw new IllegalArgumentException("invalid configured recovery URL");
            }
            return uri.toASCIIString();
        } catch (RuntimeException exception) {
            // Configuration failures never echo the configured value or incoming request headers.
            throw new AuthSessionException(AuthSessionException.Code.AUTHORITY_UNAVAILABLE);
        }
    }

    private static String hash(String ticket) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(ticket.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }
}
