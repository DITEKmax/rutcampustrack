package ru.rutcampustrack.auth.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import ru.rutcampustrack.auth.dto.ConfirmSemesterDeletionRequest;
import ru.rutcampustrack.auth.dto.ConfirmMapDeletionRequest;
import ru.rutcampustrack.auth.entity.User;
import ru.rutcampustrack.auth.exception.OtpRateLimitException;
import ru.rutcampustrack.auth.repository.UserRepository;
import ru.rutcampustrack.auth.security.SessionPrincipal;
import ru.rutcampustrack.auth.session.AuthSessionException;
import ru.rutcampustrack.auth.session.model.AuthRole;
import ru.rutcampustrack.auth.session.model.RoleStatus;
import ru.rutcampustrack.auth.session.model.SessionSnapshot;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Rechecks one current ADMIN session and verifies its current account password. */
@Service
public final class SemesterDeletionConfirmationService {

    private static final String RATE_LIMIT_IP = "internal-semester-deletion";
    // Longer than the public login column's 32-character limit, so this bucket cannot collide with login.
    private static final String RATE_LIMIT_ACTION = "__semester_delete_confirmation__:";

    private final JwtService jwtService;
    private final AuthService authService;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final LoginRateLimiter loginRateLimiter;
    private final BcryptConcurrencyGuard bcryptGuard;

    public SemesterDeletionConfirmationService(
            JwtService jwtService,
            AuthService authService,
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            LoginRateLimiter loginRateLimiter,
            BcryptConcurrencyGuard bcryptGuard
    ) {
        this.jwtService = Objects.requireNonNull(jwtService, "jwtService");
        this.authService = Objects.requireNonNull(authService, "authService");
        this.userRepository = Objects.requireNonNull(userRepository, "userRepository");
        this.passwordEncoder = Objects.requireNonNull(passwordEncoder, "passwordEncoder");
        this.loginRateLimiter = Objects.requireNonNull(loginRateLimiter, "loginRateLimiter");
        this.bcryptGuard = Objects.requireNonNull(bcryptGuard, "bcryptGuard");
    }

    /**
     * Confirms only the password proof. The caller retains operation/preview binding;
     * this method creates no session, ticket, or durable credential material.
     */
    public void confirm(ConfirmSemesterDeletionRequest request) {
        Objects.requireNonNull(request, "request");
        confirmPassword(request.internalToken(), request.password());
    }

    public void confirmMapDeletion(ConfirmMapDeletionRequest request) {
        Objects.requireNonNull(request, "request");
        // Share the credential attempt budget with semester deletion so another
        // destructive-operation route cannot bypass a blocked ADMIN account.
        confirmPassword(request.internalToken(), request.password());
    }

    public void confirmUserArchive(ru.rutcampustrack.auth.dto.ConfirmUserArchiveRequest request) {
        Objects.requireNonNull(request, "request");
        confirmPassword(request.internalToken(), request.password());
    }

    private void confirmPassword(String internalToken, String password) {
        SessionPrincipal principal = parsePrincipal(internalToken);
        SessionSnapshot current = authService.admit(principal);
        if (current.activeRole() == null || current.activeRole().role() != AuthRole.ADMIN) {
            throw new AuthSessionException(AuthSessionException.Code.ROLE_NOT_GRANTED);
        }

        String rateKey = RATE_LIMIT_ACTION + current.userId();
        checkRateLimit(rateKey);

        User user = loadCurrentUser(current.userId());
        String currentPasswordHash = user.getPasswordHash();
        if (currentPasswordHash == null || currentPasswordHash.isBlank()
                || !matchesPassword(password, currentPasswordHash)) {
            recordFailure(rateKey);
            throw new AuthSessionException(AuthSessionException.Code.CURRENT_PASSWORD_INVALID);
        }

        clearFailures(rateKey);
    }

    private SessionPrincipal parsePrincipal(String token) {
        Jws<Claims> parsed;
        try {
            parsed = jwtService.parseInternalToken(token);
        } catch (IllegalStateException exception) {
            throw new AuthSessionException(AuthSessionException.Code.AUTHORITY_UNAVAILABLE);
        } catch (RuntimeException exception) {
            throw new AuthSessionException(AuthSessionException.Code.INVALID_SESSION);
        }

        try {
            Claims claims = parsed.getPayload();
            String groupClaim = claims.get("group_id", String.class);
            Long groupId = groupClaim == null ? null : Long.parseLong(groupClaim);
            return new SessionPrincipal(
                    Long.parseLong(claims.getSubject()),
                    UUID.fromString(claims.get("sid", String.class)),
                    Long.parseLong(claims.get("sv", String.class)),
                    Long.parseLong(claims.get("rv", String.class)),
                    AuthRole.valueOf(claims.get("role", String.class)),
                    RoleStatus.valueOf(claims.get("status", String.class)),
                    groupId,
                    claims.get("is_headman", Boolean.class),
                    claims.get("readOnly", Boolean.class)
            );
        } catch (RuntimeException exception) {
            throw new AuthSessionException(AuthSessionException.Code.INVALID_SESSION);
        }
    }

    private User loadCurrentUser(long userId) {
        try {
            Optional<User> found = userRepository.findById(userId);
            if (found == null || found.isEmpty()) {
                throw new AuthSessionException(AuthSessionException.Code.AUTHORITY_UNAVAILABLE);
            }
            return found.get();
        } catch (AuthSessionException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new AuthSessionException(AuthSessionException.Code.AUTHORITY_UNAVAILABLE);
        }
    }

    private boolean matchesPassword(String password, String encodedHash) {
        try {
            return bcryptGuard.execute(() -> passwordEncoder.matches(password, encodedHash));
        } catch (OtpRateLimitException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new AuthSessionException(AuthSessionException.Code.AUTHORITY_UNAVAILABLE);
        }
    }

    private void checkRateLimit(String rateKey) {
        try {
            loginRateLimiter.checkBlocked(RATE_LIMIT_IP, rateKey);
        } catch (OtpRateLimitException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new AuthSessionException(AuthSessionException.Code.AUTHORITY_UNAVAILABLE);
        }
    }

    private void recordFailure(String rateKey) {
        try {
            loginRateLimiter.recordFailure(RATE_LIMIT_IP, rateKey);
        } catch (RuntimeException exception) {
            throw new AuthSessionException(AuthSessionException.Code.AUTHORITY_UNAVAILABLE);
        }
    }

    private void clearFailures(String rateKey) {
        try {
            loginRateLimiter.clearFailures(RATE_LIMIT_IP, rateKey);
        } catch (RuntimeException exception) {
            throw new AuthSessionException(AuthSessionException.Code.AUTHORITY_UNAVAILABLE);
        }
    }
}
