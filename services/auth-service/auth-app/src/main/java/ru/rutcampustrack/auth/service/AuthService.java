package ru.rutcampustrack.auth.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jws;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import ru.rutcampustrack.auth.config.JwtProperties;
import ru.rutcampustrack.auth.dto.LoginRequest;
import ru.rutcampustrack.auth.dto.PublicKeyResponse;
import ru.rutcampustrack.auth.dto.RefreshRequest;
import ru.rutcampustrack.auth.dto.TokenResponse;
import ru.rutcampustrack.auth.entity.User;
import ru.rutcampustrack.auth.exception.InvalidCredentialsException;
import ru.rutcampustrack.auth.exception.PasswordResetException;
import ru.rutcampustrack.auth.session.AuthSessionException;
import ru.rutcampustrack.auth.security.SessionPrincipal;
import ru.rutcampustrack.auth.session.PasswordPolicy;
import ru.rutcampustrack.auth.session.SessionLifecycleService;
import ru.rutcampustrack.auth.session.model.AuthMethod;
import ru.rutcampustrack.auth.session.model.AuthRole;
import ru.rutcampustrack.auth.session.port.CredentialSessionTransactionPort;
import ru.rutcampustrack.auth.session.port.SessionStatePort;
import ru.rutcampustrack.auth.session.port.AuthSessionQueryPort;
import ru.rutcampustrack.auth.session.model.SessionSnapshot;
import ru.rutcampustrack.shared.observability.BusinessMetrics;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.UUID;

/**
 * Public authentication service.  Password/OTP/TMA proof validation ends at
 * one session issuance seam; role and session truth is owned by PostgreSQL.
 */
@Service
public class AuthService {

    private final UserRepositoryFacade userRepositoryFacade;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;
    private final JwtProperties jwtProperties;
    private final LoginRateLimiter loginRateLimiter;
    private final BcryptConcurrencyGuard bcryptGuard;
    private final BusinessMetrics businessMetrics;
    private final AuthSessionQueryPort sessionQueryPort;
    private final SessionLifecycleService sessionLifecycle;
    private final Clock clock;
    private final PasswordPolicy passwordPolicy = new PasswordPolicy();

    /**
     * The small facade keeps the existing repository type out of the session
     * issuance seam while still using UserRepository for credential lookup.
     */
    @Autowired
    public AuthService(
            ru.rutcampustrack.auth.repository.UserRepository userRepository,
            JwtService jwtService,
            PasswordEncoder passwordEncoder,
            JwtProperties jwtProperties,
            LoginRateLimiter loginRateLimiter,
            BcryptConcurrencyGuard bcryptGuard,
            BusinessMetrics businessMetrics,
            AuthSessionQueryPort sessionQueryPort,
            SessionLifecycleService sessionLifecycle
    ) {
        this(userRepository, jwtService, passwordEncoder, jwtProperties, loginRateLimiter,
                bcryptGuard, businessMetrics, sessionQueryPort, sessionLifecycle, Clock.systemUTC());
    }

    public AuthService(
            ru.rutcampustrack.auth.repository.UserRepository userRepository,
            JwtService jwtService,
            PasswordEncoder passwordEncoder,
            JwtProperties jwtProperties,
            LoginRateLimiter loginRateLimiter,
            BcryptConcurrencyGuard bcryptGuard,
            BusinessMetrics businessMetrics,
            AuthSessionQueryPort sessionQueryPort,
            SessionLifecycleService sessionLifecycle,
            Clock clock
    ) {
        this.userRepositoryFacade = new UserRepositoryFacade(Objects.requireNonNull(userRepository, "userRepository"));
        this.jwtService = Objects.requireNonNull(jwtService, "jwtService");
        this.passwordEncoder = Objects.requireNonNull(passwordEncoder, "passwordEncoder");
        this.jwtProperties = Objects.requireNonNull(jwtProperties, "jwtProperties");
        this.loginRateLimiter = Objects.requireNonNull(loginRateLimiter, "loginRateLimiter");
        this.bcryptGuard = Objects.requireNonNull(bcryptGuard, "bcryptGuard");
        this.businessMetrics = Objects.requireNonNull(businessMetrics, "businessMetrics");
        this.sessionQueryPort = Objects.requireNonNull(sessionQueryPort, "sessionQueryPort");
        this.sessionLifecycle = Objects.requireNonNull(sessionLifecycle, "sessionLifecycle");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public TokenResponse login(LoginRequest request, String ipAddress) {
        Objects.requireNonNull(request, "request");
        String clientIp = ipAddress == null || ipAddress.isBlank() ? "unknown" : ipAddress;
        loginRateLimiter.checkBlocked(clientIp, request.login());

        User user;
        try {
            user = userRepositoryFacade.findByLogin(request.login())
                    .orElseThrow(() -> new InvalidCredentialsException());
        } catch (InvalidCredentialsException exception) {
            loginRateLimiter.recordFailure(clientIp, request.login());
            throw exception;
        } catch (RuntimeException exception) {
            throw new AuthSessionException(AuthSessionException.Code.AUTHORITY_UNAVAILABLE);
        }

        String observedHash = user.getPasswordHash();
        if (observedHash == null || observedHash.isBlank()) {
            loginRateLimiter.recordFailure(clientIp, request.login());
            throw new InvalidCredentialsException();
        }

        boolean passwordMatches = bcryptGuard.execute(() ->
                passwordEncoder.matches(request.password(), observedHash));
        if (!passwordMatches) {
            loginRateLimiter.recordFailure(clientIp, request.login());
            throw new InvalidCredentialsException();
        }

        loginRateLimiter.clearFailures(clientIp, request.login());
        return issueSession(user, AuthMethod.PASSWORD, observedHash, null, null);
    }

    /** Shared issuance seam used by all public proof types. */
    public TokenResponse issueSession(User user, AuthMethod authMethod, String expectedCredentialHash) {
        return issueSession(user, authMethod, expectedCredentialHash, null, null);
    }

    /**
     * Creates the DB session before signing either a selected access token or a
     * neutral bootstrap token. Labels are trusted observations and may remain
     * null when the public proof endpoint has no such metadata.
     */
    public TokenResponse issueSession(
            User user,
            AuthMethod authMethod,
            String expectedCredentialHash,
            String clientLabel,
            String locationLabel
    ) {
        Instant createdAt = clock.instant();
        UUID sessionId = UUID.randomUUID();
        UUID refreshJti = UUID.randomUUID();
        SessionSnapshot snapshot = createSession(user, authMethod, expectedCredentialHash,
                clientLabel, locationLabel, sessionId, refreshJti, createdAt);
        return issueTokenPair(snapshot, refreshJti, createdAt);
    }

    /** Trusted QR transaction seam: the caller stores its receipt in the same REQUIRED transaction. */
    public SessionSnapshot createQrSession(long userId, UUID sessionId, UUID refreshJti,
                                            Instant issuedAt, String browserLabel) {
        User user = userRepositoryFacade.findById(userId)
                .orElseThrow(() -> new AuthSessionException(AuthSessionException.Code.INVALID_SESSION));
        return createSession(user, AuthMethod.QR, null, browserLabel, null, sessionId, refreshJti, issuedAt);
    }

    /** Signs only an already committed, re-admitted QR receipt; no session or refresh rotation is performed. */
    public TokenResponse signQrSession(SessionSnapshot snapshot, UUID originalRefreshJti, Instant originalIssuedAt,
                                       Instant originalAccessExpiry, String signingFingerprint) {
        if (snapshot.authMethod() != AuthMethod.QR || !qrSigningFingerprint().equals(signingFingerprint)
                || !clock.instant().isBefore(originalAccessExpiry)) {
            throw new AuthSessionException(AuthSessionException.Code.INVALID_SESSION);
        }
        return issueTokenPair(snapshot, originalRefreshJti, originalIssuedAt, originalAccessExpiry);
    }

    public String qrSigningFingerprint() {
        return java.util.HexFormat.of().formatHex(ru.rutcampustrack.auth.qr.QrLoginCrypto.hash(
                jwtService.getPublicKeyPem() + ":" + jwtService.getSigningKeyId()));
    }

    public Instant qrAccessExpiry(SessionSnapshot snapshot, Instant issuedAt) {
        return accessExpiry(snapshot, issuedAt.truncatedTo(ChronoUnit.SECONDS));
    }

    private SessionSnapshot createSession(User user, AuthMethod authMethod, String expectedCredentialHash,
            String clientLabel, String locationLabel, UUID sessionId, UUID refreshJti, Instant createdAt) {
        Objects.requireNonNull(user, "user");
        Objects.requireNonNull(authMethod, "authMethod");
        long userId = requirePositiveUserId(user);
        AuthSessionQueryPort.LoginAuthority authority;
        try {
            authority = sessionQueryPort.findLoginAuthority(userId)
                    .orElseThrow(() -> new AuthSessionException(AuthSessionException.Code.INVALID_SESSION));
        } catch (AuthSessionException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new AuthSessionException(AuthSessionException.Code.AUTHORITY_UNAVAILABLE);
        }

        Instant refreshExpiresAt;
        try {
            refreshExpiresAt = createdAt.plusSeconds(jwtProperties.refreshTokenExpiration());
        } catch (RuntimeException exception) {
            throw new AuthSessionException(AuthSessionException.Code.AUTHORITY_UNAVAILABLE);
        }

        SessionStatePort.CreateSessionResult result;
        try {
            result = sessionLifecycle.createSession(new SessionLifecycleService.CreateSessionRequest(
                    userId,
                    sessionId,
                    refreshJti,
                    createdAt,
                    refreshExpiresAt,
                    authMethod,
                    authority.rolesVersion(),
                    authority.grants(),
                    authMethod == AuthMethod.PASSWORD ? expectedCredentialHash : null,
                    clientLabel,
                    locationLabel
            ));
        } catch (RuntimeException exception) {
            throw new AuthSessionException(AuthSessionException.Code.AUTHORITY_UNAVAILABLE);
        }
        if (result == null) {
            throw new AuthSessionException(AuthSessionException.Code.AUTHORITY_UNAVAILABLE);
        }
        if (!result.succeeded()) {
            throw map(result.failureCode());
        }

        SessionSnapshot snapshot = result.snapshot();
        if (snapshot == null) {
            throw new AuthSessionException(AuthSessionException.Code.AUTHORITY_UNAVAILABLE);
        }
        businessMetrics.loginCounter(snapshot.activeRole() == null
                ? "bootstrap"
                : snapshot.activeRole().role().name().toLowerCase(java.util.Locale.ROOT)).increment();
        return snapshot;
    }

    /** Cookie-only public refresh entry point. */
    public TokenResponse refresh(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new AuthSessionException(AuthSessionException.Code.REFRESH_REJECTED);
        }

        Jws<Claims> parsed;
        try {
            parsed = jwtService.parseSessionRefreshToken(refreshToken);
        } catch (RuntimeException exception) {
            throw new AuthSessionException(AuthSessionException.Code.REFRESH_REJECTED);
        }

        long userId;
        UUID sessionId;
        UUID presentedJti;
        try {
            userId = parsePositiveDecimal(parsed.getPayload().getSubject());
            sessionId = UUID.fromString(requiredStringClaim(parsed.getPayload(), "sid"));
            presentedJti = UUID.fromString(requiredStringClaim(parsed.getPayload(), "jti"));
        } catch (RuntimeException exception) {
            throw new AuthSessionException(AuthSessionException.Code.REFRESH_REJECTED);
        }

        Instant now = clock.instant();
        UUID replacementJti = UUID.randomUUID();
        SessionStatePort.RefreshResult result;
        try {
            result = sessionLifecycle.refresh(new SessionLifecycleService.RefreshRequest(
                    userId, sessionId, presentedJti, replacementJti, now));
        } catch (RuntimeException exception) {
            throw new AuthSessionException(AuthSessionException.Code.AUTHORITY_UNAVAILABLE);
        }
        if (result == null) {
            throw new AuthSessionException(AuthSessionException.Code.AUTHORITY_UNAVAILABLE);
        }
        if (!result.succeeded()) {
            throw map(result.failureCode());
        }
        SessionSnapshot snapshot = result.snapshot();
        if (snapshot == null) {
            throw new AuthSessionException(AuthSessionException.Code.AUTHORITY_UNAVAILABLE);
        }
        return issueTokenPair(snapshot, replacementJti, now);
    }

    public TokenResponse refresh(RefreshRequest request) {
        Objects.requireNonNull(request, "request");
        return refresh(request.refreshToken());
    }

    /** Remaining absolute lifetime for the rotated cookie; never extends it. */
    public long refreshRemainingSeconds(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new AuthSessionException(AuthSessionException.Code.REFRESH_REJECTED);
        }
        try {
            Instant expiration = jwtService.parseSessionRefreshToken(refreshToken)
                    .getPayload().getExpiration().toInstant();
            long remaining = expiration.getEpochSecond() - clock.instant().getEpochSecond();
            if (remaining <= 0) {
                throw new AuthSessionException(AuthSessionException.Code.REFRESH_REJECTED);
            }
            return Math.max(1L, remaining);
        } catch (AuthSessionException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new AuthSessionException(AuthSessionException.Code.REFRESH_REJECTED);
        }
    }

    public PublicKeyResponse getPublicKey() {
        return new PublicKeyResponse(jwtService.getPublicKeyPem(), "RS256");
    }

    /** Re-reads live session authority before every protected public effect. */
    public SessionSnapshot admit(SessionPrincipal principal) {
        Objects.requireNonNull(principal, "principal");
        SessionStatePort.SnapshotResult result;
        try {
            result = sessionLifecycle.snapshot(new SessionLifecycleService.SnapshotRequest(
                    principal.userId(), principal.sessionId(), clock.instant()));
        } catch (RuntimeException exception) {
            throw exception instanceof AuthSessionException typed
                    ? typed
                    : new AuthSessionException(AuthSessionException.Code.AUTHORITY_UNAVAILABLE, exception);
        }
        if (result == null) {
            throw new AuthSessionException(AuthSessionException.Code.AUTHORITY_UNAVAILABLE);
        }
        if (!result.succeeded()) {
            throw map(result.failureCode());
        }
        SessionSnapshot snapshot = result.snapshot();
        if (snapshot == null || snapshot.userId() != principal.userId()
                || !snapshot.sessionId().equals(principal.sessionId())) {
            throw new AuthSessionException(AuthSessionException.Code.INVALID_SESSION);
        }
        if (!snapshot.isLiveAt(clock.instant())) {
            throw new AuthSessionException(AuthSessionException.Code.SESSION_REVOKED);
        }
        if (snapshot.sessionVersion() != principal.sessionVersion()
                || snapshot.rolesVersion() != principal.rolesVersion()) {
            throw new AuthSessionException(AuthSessionException.Code.SESSION_STATE_STALE);
        }
        if (principal.isBootstrap()) {
            if (snapshot.activeRole() != null) {
                throw new AuthSessionException(AuthSessionException.Code.SESSION_STATE_STALE);
            }
            return snapshot;
        }
        if (snapshot.activeRole() == null || !sameSelectedIdentity(principal, snapshot)) {
            throw new AuthSessionException(AuthSessionException.Code.SESSION_STATE_STALE);
        }
        return snapshot;
    }

    public SessionStatePort.RoleSelectionResult selectRole(
            SessionPrincipal principal,
            ru.rutcampustrack.auth.dto.SelectActiveRoleRequest request,
            String clientLabel,
            String locationLabel
    ) {
        Objects.requireNonNull(request, "request");
        SessionSnapshot current = admit(principal);
        AuthRole role;
        long expectedVersion;
        try {
            role = AuthRole.valueOf(request.role());
            expectedVersion = parsePositiveDecimal(request.expectedSessionVersion());
        } catch (RuntimeException exception) {
            throw new AuthSessionException(AuthSessionException.Code.ROLE_NOT_SELECTABLE);
        }
        SessionStatePort.RoleSelectionResult result;
        try {
            result = sessionLifecycle.selectRole(new SessionLifecycleService.SelectRoleRequest(
                    principal.userId(), principal.sessionId(), role, expectedVersion, clock.instant(),
                    current.authMethod(), clientLabel, locationLabel));
        } catch (RuntimeException exception) {
            throw exception instanceof AuthSessionException typed
                    ? typed
                    : new AuthSessionException(AuthSessionException.Code.AUTHORITY_UNAVAILABLE, exception);
        }
        if (result == null) {
            throw new AuthSessionException(AuthSessionException.Code.AUTHORITY_UNAVAILABLE);
        }
        if (!result.succeeded()) {
            throw map(result.failureCode());
        }
        return result;
    }

    public SessionStatePort.RevokeResult logout(SessionPrincipal principal) {
        SessionSnapshot current = admit(principal);
        SessionStatePort.RevokeResult result;
        try {
            result = sessionLifecycle.revokeCurrent(new SessionLifecycleService.RevokeRequest(
                    principal.userId(), principal.sessionId(), clock.instant(),
                    current.authMethod(), current.clientLabel(), current.locationLabel()));
        } catch (RuntimeException exception) {
            throw exception instanceof AuthSessionException typed
                    ? typed
                    : new AuthSessionException(AuthSessionException.Code.AUTHORITY_UNAVAILABLE, exception);
        }
        if (result == null) {
            throw new AuthSessionException(AuthSessionException.Code.AUTHORITY_UNAVAILABLE);
        }
        if (!result.succeeded()) {
            throw map(result.failureCode());
        }
        return result;
    }

    /** Cookie-only current-session logout, attributed from owned DB metadata. */
    public SessionStatePort.RevokeResult logoutRefreshCookie(
            String refreshToken, SessionPrincipal expectedPrincipal) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return null;
        }
        Claims claims;
        try {
            claims = jwtService.parseSessionRefreshToken(refreshToken).getPayload();
        } catch (ExpiredJwtException exception) {
            // Logout cleanup is idempotent even when the cookie is already expired.
            return null;
        } catch (RuntimeException exception) {
            throw new AuthSessionException(AuthSessionException.Code.REFRESH_REJECTED);
        }
        long userId;
        UUID sessionId;
        try {
            userId = parsePositiveDecimal(claims.getSubject());
            sessionId = UUID.fromString(requiredStringClaim(claims, "sid"));
        } catch (RuntimeException exception) {
            throw new AuthSessionException(AuthSessionException.Code.REFRESH_REJECTED);
        }
        if (expectedPrincipal != null
                && (expectedPrincipal.userId() != userId
                || !expectedPrincipal.sessionId().equals(sessionId))) {
            throw new AuthSessionException(AuthSessionException.Code.INVALID_SESSION);
        }
        AuthSessionQueryPort.SessionMetadata metadata;
        try {
            metadata = sessionQueryPort.findSessionMetadata(userId, sessionId).orElse(null);
        } catch (DataAccessException exception) {
            throw new AuthSessionException(AuthSessionException.Code.AUTHORITY_UNAVAILABLE, exception);
        }
        if (metadata == null || !metadata.isLiveAt(clock.instant())) {
            return null;
        }
        AuthMethod authMethod;
        try {
            authMethod = AuthMethod.valueOf(metadata.authMethod());
        } catch (RuntimeException exception) {
            throw new AuthSessionException(AuthSessionException.Code.AUTHORITY_UNAVAILABLE);
        }
        SessionStatePort.RevokeResult result;
        try {
            result = sessionLifecycle.revokeCurrent(new SessionLifecycleService.RevokeRequest(
                    userId, sessionId, clock.instant(), authMethod,
                    metadata.clientLabel(), metadata.locationLabel()));
        } catch (RuntimeException exception) {
            throw exception instanceof AuthSessionException typed
                    ? typed
                    : new AuthSessionException(AuthSessionException.Code.AUTHORITY_UNAVAILABLE, exception);
        }
        if (result == null) {
            throw new AuthSessionException(AuthSessionException.Code.AUTHORITY_UNAVAILABLE);
        }
        if (!result.succeeded() && result.failureCode() != SessionStatePort.FailureCode.SESSION_REVOKED) {
            throw map(result.failureCode());
        }
        return result;
    }

    public SessionStatePort.RevokeAllResult logoutAll(SessionPrincipal principal) {
        SessionSnapshot current = admit(principal);
        SessionStatePort.RevokeAllResult result;
        try {
            result = sessionLifecycle.revokeAll(new SessionLifecycleService.RevokeAllRequest(
                    principal.userId(), principal.sessionId(), clock.instant(),
                    current.authMethod(), current.clientLabel(), current.locationLabel()));
        } catch (RuntimeException exception) {
            throw exception instanceof AuthSessionException typed
                    ? typed
                    : new AuthSessionException(AuthSessionException.Code.AUTHORITY_UNAVAILABLE, exception);
        }
        if (result == null) {
            throw new AuthSessionException(AuthSessionException.Code.AUTHORITY_UNAVAILABLE);
        }
        if (!result.succeeded()) {
            throw map(result.failureCode());
        }
        return result;
    }

    public TokenResponse selectedAccess(SessionStatePort.RoleSelectionResult result) {
        Objects.requireNonNull(result, "result");
        if (!result.succeeded() || result.snapshot() == null) {
            throw new AuthSessionException(AuthSessionException.Code.AUTHORITY_UNAVAILABLE);
        }
        SessionSnapshot snapshot = result.snapshot();
        Instant issuedAt = clock.instant().truncatedTo(ChronoUnit.SECONDS);
        Instant expiry = issuedAt.plusSeconds(jwtProperties.accessTokenExpiration());
        Instant refreshExpiry = snapshot.refreshExpiresAt().truncatedTo(ChronoUnit.SECONDS);
        if (expiry.isAfter(refreshExpiry)) {
            expiry = refreshExpiry;
        }
        if (!issuedAt.isBefore(expiry)) {
            throw new AuthSessionException(AuthSessionException.Code.SESSION_REVOKED);
        }
        String access = jwtService.generateSessionAccessToken(snapshot, issuedAt, expiry);
        return new TokenResponse(access, null,
                Math.max(1L, expiry.getEpochSecond() - issuedAt.getEpochSecond()));
    }

    public CredentialSessionTransactionPort.ChangePasswordResult changePassword(
            SessionPrincipal principal,
            ru.rutcampustrack.auth.dto.ChangePasswordRequest request
    ) {
        if (principal.isBootstrap()) {
            throw new AuthSessionException(AuthSessionException.Code.BOOTSTRAP_SCOPE_DENIED);
        }
        SessionSnapshot current = admit(principal);
        PasswordPolicy.Validation validation = passwordPolicy.validate(request.newPassword());
        if (!validation.valid()) {
            throw new AuthSessionException(AuthSessionException.Code.PASSWORD_POLICY_VIOLATION);
        }
        User user;
        try {
            user = userRepositoryFacade.findById(principal.userId())
                    .orElseThrow(() -> new AuthSessionException(AuthSessionException.Code.INVALID_SESSION));
        } catch (DataAccessException exception) {
            throw new AuthSessionException(AuthSessionException.Code.AUTHORITY_UNAVAILABLE, exception);
        }
        String currentHash = user.getPasswordHash();
        if (currentHash == null || currentHash.isBlank()) {
            throw new AuthSessionException(AuthSessionException.Code.CURRENT_PASSWORD_INVALID);
        }
        String replacementHash = bcryptGuard.execute(() -> {
            if (!passwordEncoder.matches(request.currentPassword(), currentHash)) {
                return null;
            }
            return passwordEncoder.encode(request.newPassword().toString());
        });
        if (replacementHash == null) {
            throw new AuthSessionException(AuthSessionException.Code.CURRENT_PASSWORD_INVALID);
        }
        CredentialSessionTransactionPort.ChangePasswordResult result;
        try {
            result = sessionLifecycle.changePassword(new SessionLifecycleService.ChangePasswordRequest(
                    principal.userId(), principal.sessionId(),
                    new CredentialSessionTransactionPort.CredentialHash(currentHash),
                    new CredentialSessionTransactionPort.CredentialHash(replacementHash),
                    request.newPassword(), clock.instant(), current.authMethod(),
                    current.clientLabel(), current.locationLabel()));
        } catch (RuntimeException exception) {
            throw exception instanceof AuthSessionException typed
                    ? typed
                    : new AuthSessionException(AuthSessionException.Code.AUTHORITY_UNAVAILABLE, exception);
        }
        if (result == null) {
            throw new AuthSessionException(AuthSessionException.Code.AUTHORITY_UNAVAILABLE);
        }
        if (!result.succeeded()) {
            throw mapCredentialFailure(result.failureCode());
        }
        return result;
    }

    public long completePasswordReset(
            CredentialSessionTransactionPort.CredentialHash ticketHash,
            String newPassword
    ) {
        Objects.requireNonNull(ticketHash, "ticketHash");
        PasswordPolicy.Validation validation = passwordPolicy.validate(newPassword);
        if (!validation.valid()) {
            throw new AuthSessionException(AuthSessionException.Code.PASSWORD_POLICY_VIOLATION);
        }
        String replacementHash = bcryptGuard.execute(() -> passwordEncoder.encode(newPassword));
        CredentialSessionTransactionPort.PasswordResetResult result;
        try {
            result = sessionLifecycle.completePasswordReset(
                    new SessionLifecycleService.PasswordResetRequest(
                            ticketHash,
                            new CredentialSessionTransactionPort.CredentialHash(replacementHash),
                            newPassword,
                            clock.instant()));
        } catch (RuntimeException exception) {
            throw exception instanceof AuthSessionException typed
                    ? typed
                    : new AuthSessionException(AuthSessionException.Code.AUTHORITY_UNAVAILABLE, exception);
        }
        if (result == null) {
            throw new AuthSessionException(AuthSessionException.Code.AUTHORITY_UNAVAILABLE);
        }
        if (result.succeeded()) {
            return result.userId();
        }
        switch (result.failureCode()) {
            case RESET_TICKET_INVALID -> throw new PasswordResetException(
                    PasswordResetException.Code.RESET_TICKET_INVALID, null, null);
            case PASSWORD_POLICY_VIOLATION -> throw new AuthSessionException(
                    AuthSessionException.Code.PASSWORD_POLICY_VIOLATION);
            case AUTHORITY_UNAVAILABLE -> throw new AuthSessionException(
                    AuthSessionException.Code.AUTHORITY_UNAVAILABLE);
        }
        throw new AuthSessionException(AuthSessionException.Code.AUTHORITY_UNAVAILABLE);
    }

    private TokenResponse issueTokenPair(SessionSnapshot snapshot, UUID refreshJti, Instant now) {
        return issueTokenPair(snapshot, refreshJti, now, null);
    }

    private Instant accessExpiry(SessionSnapshot snapshot, Instant issuedAt) {
        try {
            Instant deadline = issuedAt.plusSeconds(jwtProperties.accessTokenExpiration());
            Instant refreshExpiry = snapshot.refreshExpiresAt().truncatedTo(ChronoUnit.SECONDS);
            return deadline.isBefore(refreshExpiry) ? deadline : refreshExpiry;
        } catch (RuntimeException exception) {
            throw new AuthSessionException(AuthSessionException.Code.AUTHORITY_UNAVAILABLE);
        }
    }

    private TokenResponse issueTokenPair(SessionSnapshot snapshot, UUID refreshJti, Instant now, Instant originalAccessExpiry) {
        Instant issuedAt = now.truncatedTo(ChronoUnit.SECONDS);
        Instant absoluteRefreshExpiry = snapshot.refreshExpiresAt().truncatedTo(ChronoUnit.SECONDS);
        Instant accessExpiry = originalAccessExpiry == null ? accessExpiry(snapshot, issuedAt) : originalAccessExpiry;
        if (!issuedAt.isBefore(accessExpiry) || !issuedAt.isBefore(absoluteRefreshExpiry) || accessExpiry.isAfter(absoluteRefreshExpiry)) {
            throw new AuthSessionException(AuthSessionException.Code.SESSION_REVOKED);
        }

        String refresh = jwtService.generateSessionRefreshToken(
                snapshot, refreshJti, issuedAt, absoluteRefreshExpiry);
        String access = snapshot.activeRole() == null
                ? jwtService.generateBootstrapToken(snapshot, issuedAt, accessExpiry)
                : jwtService.generateSessionAccessToken(snapshot, issuedAt, accessExpiry);
        long expiresIn = Math.max(1L, accessExpiry.getEpochSecond() - issuedAt.getEpochSecond());
        return new TokenResponse(access, refresh, expiresIn);
    }

    private static String requiredStringClaim(Claims claims, String name) {
        String value = claims.get(name, String.class);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("missing claim");
        }
        return value;
    }

    private static long parsePositiveDecimal(String value) {
        if (value == null || !value.matches("[1-9][0-9]*")) {
            throw new IllegalArgumentException("invalid user id");
        }
        long parsed = Long.parseLong(value);
        if (parsed <= 0) {
            throw new IllegalArgumentException("invalid user id");
        }
        return parsed;
    }

    private static long requirePositiveUserId(User user) {
        if (user.getId() == null || user.getId() <= 0) {
            throw new AuthSessionException(AuthSessionException.Code.INVALID_SESSION);
        }
        return user.getId();
    }

    static AuthSessionException map(SessionStatePort.FailureCode code) {
        if (code == null) {
            return new AuthSessionException(AuthSessionException.Code.AUTHORITY_UNAVAILABLE);
        }
        return switch (code) {
            case INVALID_SESSION -> new AuthSessionException(AuthSessionException.Code.INVALID_SESSION);
            case SESSION_REVOKED -> new AuthSessionException(AuthSessionException.Code.SESSION_REVOKED);
            case SESSION_STATE_STALE -> new AuthSessionException(AuthSessionException.Code.SESSION_STATE_STALE);
            case SESSION_VERSION_CONFLICT -> new AuthSessionException(AuthSessionException.Code.SESSION_VERSION_CONFLICT);
            case ROLE_NOT_GRANTED -> new AuthSessionException(AuthSessionException.Code.ROLE_NOT_GRANTED);
            case ROLE_NOT_SELECTABLE -> new AuthSessionException(AuthSessionException.Code.ROLE_NOT_SELECTABLE);
            case ROLE_READ_ONLY -> new AuthSessionException(AuthSessionException.Code.ROLE_READ_ONLY);
            case REFRESH_ALREADY_ROTATED -> new AuthSessionException(AuthSessionException.Code.REFRESH_ALREADY_ROTATED);
            case REFRESH_REJECTED -> new AuthSessionException(AuthSessionException.Code.REFRESH_REJECTED);
            default -> new AuthSessionException(AuthSessionException.Code.AUTHORITY_UNAVAILABLE);
        };
    }

    private static AuthSessionException mapCredentialFailure(
            CredentialSessionTransactionPort.FailureCode code) {
        if (code == null) {
            return new AuthSessionException(AuthSessionException.Code.AUTHORITY_UNAVAILABLE);
        }
        return switch (code) {
            case CURRENT_PASSWORD_INVALID -> new AuthSessionException(AuthSessionException.Code.CURRENT_PASSWORD_INVALID);
            case PASSWORD_POLICY_VIOLATION -> new AuthSessionException(AuthSessionException.Code.PASSWORD_POLICY_VIOLATION);
            case INVALID_SESSION -> new AuthSessionException(AuthSessionException.Code.INVALID_SESSION);
            case SESSION_REVOKED -> new AuthSessionException(AuthSessionException.Code.SESSION_REVOKED);
            case AUTHORITY_UNAVAILABLE -> new AuthSessionException(AuthSessionException.Code.AUTHORITY_UNAVAILABLE);
        };
    }

    private static boolean sameSelectedIdentity(SessionPrincipal principal, SessionSnapshot snapshot) {
        var role = snapshot.activeRole();
        return role != null
                && principal.selectedRole() == role.role()
                && principal.selectedStatus() == role.status()
                && Objects.equals(principal.groupId(), role.groupId())
                && principal.headman() == (role.role() == AuthRole.HEADMAN)
                && principal.readOnly() == role.isReadOnly();
    }

    /** Credential-only access to the existing JPA repository. */
    private static final class UserRepositoryFacade {
        private final ru.rutcampustrack.auth.repository.UserRepository delegate;

        private UserRepositoryFacade(ru.rutcampustrack.auth.repository.UserRepository delegate) {
            this.delegate = delegate;
        }

        private java.util.Optional<User> findByLogin(String login) {
            return delegate.findByLogin(login);
        }

        private java.util.Optional<User> findById(long userId) {
            return delegate.findById(userId);
        }
    }
}
