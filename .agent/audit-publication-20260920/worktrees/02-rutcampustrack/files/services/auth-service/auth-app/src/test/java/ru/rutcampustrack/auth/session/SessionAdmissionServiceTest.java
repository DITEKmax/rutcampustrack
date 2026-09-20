package ru.rutcampustrack.auth.session;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ru.rutcampustrack.auth.config.InternalIssuerProperties;
import ru.rutcampustrack.auth.dto.AuthAdmissionResponse;
import ru.rutcampustrack.auth.service.JwtService;
import ru.rutcampustrack.auth.session.model.AuthMethod;
import ru.rutcampustrack.auth.session.model.AuthRole;
import ru.rutcampustrack.auth.session.model.RoleGrant;
import ru.rutcampustrack.auth.session.model.RoleStatus;
import ru.rutcampustrack.auth.session.model.SessionSnapshot;
import ru.rutcampustrack.auth.session.port.SessionStatePort;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class SessionAdmissionServiceTest {

    private static final long USER_ID = 42L;
    private static final UUID SESSION_ID = UUID.fromString("55555555-5555-4555-8555-555555555555");
    private static final Instant NOW = Instant.parse("2026-09-10T10:00:00.500Z");
    private static final Instant ACCESS_EXPIRATION = Instant.parse("2026-09-10T10:05:00Z");

    private JwtService jwtService;
    private SessionStatePort sessionStatePort;
    private InternalIssuerProperties issuerProperties;
    private SessionAdmissionService service;
    private Claims claims;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        jwtService = mock(JwtService.class);
        sessionStatePort = mock(SessionStatePort.class);
        issuerProperties = new InternalIssuerProperties();
        issuerProperties.setSecret("test-internal-issuer-secret-32-bytes-or-more-for-test-env");
        issuerProperties.setTokenTtlSeconds(60);
        service = new SessionAdmissionService(
                jwtService,
                sessionStatePort,
                issuerProperties,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );

        Jws<Claims> parsed = mock(Jws.class);
        claims = mock(Claims.class);
        when(parsed.getPayload()).thenReturn(claims);
        when(jwtService.parseSessionAccessToken("access")).thenReturn(parsed);
        when(claims.getSubject()).thenReturn(Long.toString(USER_ID));
        when(claims.get("sid")).thenReturn(SESSION_ID.toString());
        when(claims.get("sv")).thenReturn("2");
        when(claims.get("rv")).thenReturn("3");
        when(claims.get("role")).thenReturn("STUDENT");
        when(claims.get("status")).thenReturn("ACTIVE");
        when(claims.get("group_id")).thenReturn("10");
        when(claims.get("is_headman")).thenReturn(false);
        when(claims.get("readOnly")).thenReturn(false);
        when(claims.getExpiration()).thenReturn(java.util.Date.from(ACCESS_EXPIRATION));
        when(jwtService.generateInternalToken(any(), any(), any())).thenReturn("internal");
    }

    @Test
    void admitsLiveSnapshotAndBuildsResponseFromTheSameGrant() {
        SessionSnapshot snapshot = snapshot(RoleStatus.ACTIVE, AuthRole.STUDENT, 10L);
        when(sessionStatePort.snapshot(any())).thenReturn(SessionStatePort.SnapshotResult.success(snapshot));

        AuthAdmissionResponse response = service.admit("access");

        assertThat(response.internalToken()).isEqualTo("internal");
        assertThat(response.expiresAt()).isEqualTo(Instant.parse("2026-09-10T10:01:00Z"));
        assertThat(response.sessionId()).isEqualTo(SESSION_ID.toString());
        assertThat(response.userId()).isEqualTo("42");
        assertThat(response.sessionVersion()).isEqualTo("2");
        assertThat(response.rolesVersion()).isEqualTo("3");
        assertThat(response.role()).isEqualTo("STUDENT");
        assertThat(response.status()).isEqualTo("ACTIVE");
        assertThat(response.groupId()).isEqualTo("10");
        assertThat(response.isHeadman()).isFalse();
        assertThat(response.readOnly()).isFalse();
        verify(sessionStatePort).snapshot(any());
        verify(jwtService).generateInternalToken(
                snapshot, Instant.parse("2026-09-10T10:00:00Z"),
                Instant.parse("2026-09-10T10:01:00Z"));
    }

    @Test
    void delayedSnapshotUsesFreshClockAndRetainsOriginalSnapshotRequestTime() {
        MutableClock delayedClock = new MutableClock(NOW);
        service = new SessionAdmissionService(
                jwtService, sessionStatePort, issuerProperties, delayedClock);
        SessionSnapshot snapshot = snapshot(RoleStatus.ACTIVE, AuthRole.STUDENT, 10L);
        when(sessionStatePort.snapshot(any())).thenAnswer(invocation -> {
            delayedClock.advanceTo(Instant.parse("2026-09-10T10:00:05.250Z"));
            return SessionStatePort.SnapshotResult.success(snapshot);
        });

        AuthAdmissionResponse response = service.admit("access");

        assertThat(response.expiresAt()).isEqualTo(Instant.parse("2026-09-10T10:01:05Z"));
        verify(sessionStatePort).snapshot(
                new SessionStatePort.SnapshotCommand(USER_ID, SESSION_ID, NOW));
        verify(jwtService).generateInternalToken(
                snapshot,
                Instant.parse("2026-09-10T10:00:05Z"),
                Instant.parse("2026-09-10T10:01:05Z"));
    }

    @Test
    void originalAccessExpiryAfterSnapshotIsRejectedBeforeSigning() {
        MutableClock delayedClock = new MutableClock(NOW);
        service = new SessionAdmissionService(
                jwtService, sessionStatePort, issuerProperties, delayedClock);
        SessionSnapshot snapshot = snapshot(RoleStatus.ACTIVE, AuthRole.STUDENT, 10L);
        when(sessionStatePort.snapshot(any())).thenAnswer(invocation -> {
            delayedClock.advanceTo(Instant.parse("2026-09-10T10:05:00.001Z"));
            return SessionStatePort.SnapshotResult.success(snapshot);
        });

        assertThatThrownBy(() -> service.admit("access"))
                .isInstanceOfSatisfying(SessionAdmissionException.class, exception ->
                        assertThat(exception.code())
                                .isEqualTo(SessionAdmissionException.Code.INVALID_SESSION));
        verify(sessionStatePort).snapshot(
                new SessionStatePort.SnapshotCommand(USER_ID, SESSION_ID, NOW));
        verify(jwtService, org.mockito.Mockito.never())
                .generateInternalToken(any(), any(), any());
    }

    @Test
    void sessionExpiryDuringSnapshotIsRejectedBeforeSigning() {
        MutableClock delayedClock = new MutableClock(NOW);
        service = new SessionAdmissionService(
                jwtService, sessionStatePort, issuerProperties, delayedClock);
        SessionSnapshot snapshot = snapshot(
                RoleStatus.ACTIVE,
                AuthRole.STUDENT,
                10L,
                Instant.parse("2026-09-10T10:00:03Z"));
        when(sessionStatePort.snapshot(any())).thenAnswer(invocation -> {
            delayedClock.advanceTo(Instant.parse("2026-09-10T10:00:05.250Z"));
            return SessionStatePort.SnapshotResult.success(snapshot);
        });

        assertThatThrownBy(() -> service.admit("access"))
                .isInstanceOfSatisfying(SessionAdmissionException.class, exception ->
                        assertThat(exception.code())
                                .isEqualTo(SessionAdmissionException.Code.SESSION_REVOKED));
        verify(sessionStatePort).snapshot(
                new SessionStatePort.SnapshotCommand(USER_ID, SESSION_ID, NOW));
        verify(jwtService, org.mockito.Mockito.never())
                .generateInternalToken(any(), any(), any());
    }

    @Test
    void signingDelayPastInternalExpiryIsRejectedBeforeResponse() {
        MutableClock delayedClock = new MutableClock(NOW);
        service = new SessionAdmissionService(
                jwtService, sessionStatePort, issuerProperties, delayedClock);
        SessionSnapshot snapshot = snapshot(RoleStatus.ACTIVE, AuthRole.STUDENT, 10L);
        when(sessionStatePort.snapshot(any())).thenReturn(SessionStatePort.SnapshotResult.success(snapshot));
        when(jwtService.generateInternalToken(any(), any(), any())).thenAnswer(invocation -> {
            delayedClock.advanceTo(Instant.parse("2026-09-10T10:01:00.001Z"));
            return "internal";
        });

        assertThatThrownBy(() -> service.admit("access"))
                .isInstanceOfSatisfying(SessionAdmissionException.class, exception ->
                        assertThat(exception.code())
                                .isEqualTo(SessionAdmissionException.Code.INVALID_SESSION));
        verify(sessionStatePort).snapshot(
                new SessionStatePort.SnapshotCommand(USER_ID, SESSION_ID, NOW));
        verify(jwtService).generateInternalToken(
                snapshot,
                Instant.parse("2026-09-10T10:00:00Z"),
                Instant.parse("2026-09-10T10:01:00Z"));
    }

    @Test
    void signingDelayPastSessionExpiryIsRejectedBeforeResponse() {
        MutableClock delayedClock = new MutableClock(NOW);
        service = new SessionAdmissionService(
                jwtService, sessionStatePort, issuerProperties, delayedClock);
        SessionSnapshot snapshot = snapshot(
                RoleStatus.ACTIVE,
                AuthRole.STUDENT,
                10L,
                Instant.parse("2026-09-10T10:00:30Z"));
        when(sessionStatePort.snapshot(any())).thenReturn(SessionStatePort.SnapshotResult.success(snapshot));
        when(jwtService.generateInternalToken(any(), any(), any())).thenAnswer(invocation -> {
            delayedClock.advanceTo(Instant.parse("2026-09-10T10:00:30.001Z"));
            return "internal";
        });

        assertThatThrownBy(() -> service.admit("access"))
                .isInstanceOfSatisfying(SessionAdmissionException.class, exception ->
                        assertThat(exception.code())
                                .isEqualTo(SessionAdmissionException.Code.SESSION_REVOKED));
        verify(sessionStatePort).snapshot(
                new SessionStatePort.SnapshotCommand(USER_ID, SESSION_ID, NOW));
        verify(jwtService).generateInternalToken(
                snapshot,
                Instant.parse("2026-09-10T10:00:00Z"),
                Instant.parse("2026-09-10T10:01:00Z"));
    }

    @Test
    void terminalGrantIsAdmittedReadOnly() {
        when(claims.get("status")).thenReturn("EXPELLED");
        when(claims.get("group_id")).thenReturn(null);
        when(claims.get("readOnly")).thenReturn(true);
        SessionSnapshot snapshot = snapshot(RoleStatus.EXPELLED, AuthRole.STUDENT, null);
        when(sessionStatePort.snapshot(any())).thenReturn(SessionStatePort.SnapshotResult.success(snapshot));

        AuthAdmissionResponse response = service.admit("access");

        assertThat(response.status()).isEqualTo("EXPELLED");
        assertThat(response.groupId()).isNull();
        assertThat(response.readOnly()).isTrue();
    }

    @Test
    void authoritativeSuspendedGrantIsRoleNotSelectable() {
        Instant created = Instant.parse("2026-09-10T08:00:00Z");
        RoleGrant suspended = new RoleGrant(
                1L, USER_ID, AuthRole.STUDENT, RoleStatus.SUSPENDED, 10L, created, created);
        SessionSnapshot snapshot = new SessionSnapshot(
                SESSION_ID, USER_ID, 3L, 3L, null, List.of(suspended),
                Instant.parse("2026-09-10T11:00:00Z"), created, created,
                null, null, AuthMethod.OTP, "test", "test");
        when(sessionStatePort.snapshot(any())).thenReturn(SessionStatePort.SnapshotResult.success(snapshot));

        assertThatThrownBy(() -> service.admit("access"))
                .isInstanceOf(SessionAdmissionException.class)
                .extracting(exception -> ((SessionAdmissionException) exception).code())
                .isEqualTo(SessionAdmissionException.Code.ROLE_NOT_SELECTABLE);
        verify(jwtService, org.mockito.Mockito.never()).generateInternalToken(any(), any(), any());
    }

    @Test
    void malformedAccessIsInvalidAndDoesNotReadAuthority() {
        when(jwtService.parseSessionAccessToken("bad"))
                .thenThrow(new IllegalArgumentException("bad token"));

        assertThatThrownBy(() -> service.admit("bad"))
                .isInstanceOf(SessionAdmissionException.class)
                .extracting(exception -> ((SessionAdmissionException) exception).code())
                .isEqualTo(SessionAdmissionException.Code.INVALID_SESSION);
        verifyNoInteractions(sessionStatePort);
    }

    @Test
    void authorityFailuresRemainTyped() {
        for (SessionStatePort.FailureCode failureCode : SessionStatePort.FailureCode.values()) {
            when(sessionStatePort.snapshot(any()))
                    .thenReturn(SessionStatePort.SnapshotResult.failure(failureCode));

            assertThatThrownBy(() -> service.admit("access"))
                    .isInstanceOfSatisfying(SessionAdmissionException.class, exception ->
                            assertThat(exception.code()).isEqualTo(expectedCode(failureCode)));
        }
    }

    @Test
    void sameVersionIdentityMismatchesReachTheAuthoritativeGrantCheck() {
        reset(claims);
        Map<String, Object> identityClaims = new HashMap<>();
        identityClaims.put("sid", SESSION_ID.toString());
        identityClaims.put("sv", "2");
        identityClaims.put("rv", "3");
        identityClaims.put("role", "STUDENT");
        identityClaims.put("status", "ACTIVE");
        identityClaims.put("group_id", "10");
        identityClaims.put("is_headman", false);
        identityClaims.put("readOnly", false);
        when(claims.getSubject()).thenReturn(Long.toString(USER_ID));
        when(claims.get(anyString())).thenAnswer(invocation ->
                identityClaims.get(invocation.getArgument(0, String.class)));
        when(claims.getExpiration()).thenReturn(java.util.Date.from(ACCESS_EXPIRATION));

        SessionSnapshot snapshot = snapshot(RoleStatus.ACTIVE, AuthRole.STUDENT, 10L);
        when(sessionStatePort.snapshot(any())).thenReturn(SessionStatePort.SnapshotResult.success(snapshot));
        List<IdentityMismatch> mismatches = List.of(
                new IdentityMismatch("role", "TEACHER"),
                new IdentityMismatch("status", "EXPELLED"),
                new IdentityMismatch("group_id", "11"),
                new IdentityMismatch("is_headman", true),
                new IdentityMismatch("readOnly", true)
        );

        for (IdentityMismatch mismatch : mismatches) {
            Object expected = identityClaims.put(mismatch.claim(), mismatch.value());
            assertThatThrownBy(() -> service.admit("access"))
                    .isInstanceOfSatisfying(SessionAdmissionException.class, exception ->
                            assertThat(exception.code())
                                    .isEqualTo(SessionAdmissionException.Code.SESSION_STATE_STALE));
            identityClaims.put(mismatch.claim(), expected);
        }

        verify(sessionStatePort, times(mismatches.size())).snapshot(any());
        verify(jwtService, org.mockito.Mockito.never())
                .generateInternalToken(any(), any(), any());
    }

    @Test
    void staleVersionsAndChangedGrantIdentityAreConflicts() {
        SessionSnapshot staleVersion = new SessionSnapshot(
                SESSION_ID, USER_ID, 3L, 3L,
                new RoleGrant(1L, USER_ID, AuthRole.STUDENT, RoleStatus.ACTIVE, 10L,
                        Instant.parse("2026-09-10T08:00:00Z"), Instant.parse("2026-09-10T08:00:00Z")),
                List.of(new RoleGrant(1L, USER_ID, AuthRole.STUDENT, RoleStatus.ACTIVE, 10L,
                        Instant.parse("2026-09-10T08:00:00Z"), Instant.parse("2026-09-10T08:00:00Z"))),
                Instant.parse("2026-09-10T11:00:00Z"),
                Instant.parse("2026-09-10T08:00:00Z"),
                Instant.parse("2026-09-10T08:00:00Z"),
                null, null, AuthMethod.OTP, "test", "test");
        when(sessionStatePort.snapshot(any())).thenReturn(SessionStatePort.SnapshotResult.success(staleVersion));

        assertThatThrownBy(() -> service.admit("access"))
                .isInstanceOf(SessionAdmissionException.class)
                .extracting(exception -> ((SessionAdmissionException) exception).code())
                .isEqualTo(SessionAdmissionException.Code.SESSION_STATE_STALE);

        when(sessionStatePort.snapshot(any())).thenReturn(
                SessionStatePort.SnapshotResult.failure(SessionStatePort.FailureCode.ROLE_NOT_SELECTABLE));
        assertThatThrownBy(() -> service.admit("access"))
                .isInstanceOf(SessionAdmissionException.class)
                .extracting(exception -> ((SessionAdmissionException) exception).code())
                .isEqualTo(SessionAdmissionException.Code.ROLE_NOT_SELECTABLE);
    }

    @Test
    void noAdmissionCacheCallsAuthorityAndSignsOnEveryRequest() {
        SessionSnapshot snapshot = snapshot(RoleStatus.ACTIVE, AuthRole.STUDENT, 10L);
        when(sessionStatePort.snapshot(any())).thenReturn(SessionStatePort.SnapshotResult.success(snapshot));

        service.admit("access");
        service.admit("access");

        verify(sessionStatePort, times(2)).snapshot(any());
        verify(jwtService, times(2)).generateInternalToken(any(), any(), any());
    }

    private SessionSnapshot snapshot(RoleStatus status, AuthRole role, Long groupId) {
        return snapshot(status, role, groupId, Instant.parse("2026-09-10T11:00:00Z"));
    }

    private SessionSnapshot snapshot(
            RoleStatus status,
            AuthRole role,
            Long groupId,
            Instant refreshExpiresAt
    ) {
        Instant created = Instant.parse("2026-09-10T08:00:00Z");
        RoleGrant grant = new RoleGrant(1L, USER_ID, role, status, groupId, created, created);
        return new SessionSnapshot(
                SESSION_ID, USER_ID, 2L, 3L, grant, List.of(grant),
                refreshExpiresAt, created, created,
                null, null, AuthMethod.OTP, "test", "test");
    }

    private static SessionAdmissionException.Code expectedCode(SessionStatePort.FailureCode failureCode) {
        return switch (failureCode) {
            case INVALID_SESSION -> SessionAdmissionException.Code.INVALID_SESSION;
            case SESSION_REVOKED -> SessionAdmissionException.Code.SESSION_REVOKED;
            case ROLE_NOT_GRANTED -> SessionAdmissionException.Code.ROLE_NOT_GRANTED;
            case ROLE_NOT_SELECTABLE -> SessionAdmissionException.Code.ROLE_NOT_SELECTABLE;
            case SESSION_STATE_STALE -> SessionAdmissionException.Code.SESSION_STATE_STALE;
            case AUTHORITY_UNAVAILABLE -> SessionAdmissionException.Code.AUTHORITY_UNAVAILABLE;
            default -> SessionAdmissionException.Code.AUTHORITY_UNAVAILABLE;
        };
    }

    private record IdentityMismatch(String claim, Object value) {
    }

    private static final class MutableClock extends Clock {
        private Instant current;

        private MutableClock(Instant current) {
            this.current = current;
        }

        private void advanceTo(Instant next) {
            this.current = next;
        }

        @Override
        public Instant instant() {
            return current;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return Clock.fixed(current, zone);
        }
    }
}
