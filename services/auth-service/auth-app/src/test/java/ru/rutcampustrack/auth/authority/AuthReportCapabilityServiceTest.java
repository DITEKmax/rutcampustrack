package ru.rutcampustrack.auth.authority;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.grpc.StatusRuntimeException;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import ru.rutcampustrack.auth.config.InternalIssuerProperties;
import ru.rutcampustrack.auth.security.SessionPrincipal;
import ru.rutcampustrack.auth.service.*;
import ru.rutcampustrack.auth.session.AuthSessionException;
import ru.rutcampustrack.auth.session.model.*;
import ru.rutcampustrack.authsecurity.grpc.*;
import ru.rutcampustrack.shared.security.admin.AdminAccessProfile;

import java.time.*;
import java.util.*;

class AuthReportCapabilityServiceTest {
    private static final Instant NOW =
            Instant.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
    private static final UUID SID = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID JOB = UUID.fromString("22222222-2222-4222-8222-222222222222");
    private static final UUID LEASE = UUID.fromString("33333333-3333-4333-8333-333333333333");
    private final AuthService sessions = mock(AuthService.class);
    private JwtService jwt = mock(JwtService.class);
    @org.junit.jupiter.api.io.TempDir java.nio.file.Path keyDirectory;
    private final AuthAdminJdbcAuthority admins = mock(AuthAdminJdbcAuthority.class);
    private final MemoryStore store = new MemoryStore();

    @Test
    void opaqueTicketContainsNoBearerOrSelectorAndKeepsSixtySecondLifetime() {
        var service = service(NOW);
        var issued =
                service.issue(
                        issue("TEACHER_JOURNAL", false, NOW.plusSeconds(600), AuthRole.TEACHER));
        assertThat(issued.getTicket()).matches("[A-Za-z0-9_-]{43}");
        assertThat(Instant.parse(issued.getExpiresAt())).isEqualTo(NOW.plusSeconds(60));
        assertThat(store.ttl).isEqualTo(Duration.ofSeconds(60));
        assertThat(store.encoded)
                .doesNotContain(
                        issued.getTicket(),
                        "internal_token",
                        "password",
                        "teacher_journal",
                        "selector");
        assertThat(store.encoded).contains(JOB.toString());
        var redeemed = service.redeem(redeem(issued));
        assertThat(redeemed.getOwnerResourceId()).isEqualTo(JOB.toString());
        assertThat(redeemed.getAuthority().getInternalToken()).isEmpty();
        verifyNoInteractions(jwt);
    }

    @Test
    void capabilityExpiresWithOwnerAndCannotBeRedeemedAfterExpiry() {
        var issued =
                service(NOW)
                        .issue(
                                issue(
                                        "TEACHER_JOURNAL",
                                        false,
                                        NOW.plusSeconds(15),
                                        AuthRole.TEACHER));
        assertThat(Instant.parse(issued.getExpiresAt())).isEqualTo(NOW.plusSeconds(15));
        assertThat(store.ttl).isEqualTo(Duration.ofSeconds(15));
        assertThatThrownBy(() -> service(NOW.plusSeconds(15)).redeem(redeem(issued)))
                .isInstanceOf(StatusRuntimeException.class);
    }

    @Test
    void everyRedemptionReadmitsStoredSessionAndCannotUseStaleSidSvOrRv() {
        var service = service(NOW);
        var issued =
                service.issue(
                        issue("TEACHER_JOURNAL", false, NOW.plusSeconds(600), AuthRole.TEACHER));
        service.redeem(redeem(issued));
        doThrow(new AuthSessionException(AuthSessionException.Code.SESSION_STATE_STALE))
                .when(sessions)
                .admit(any());
        assertThatThrownBy(() -> service.redeem(redeem(issued)))
                .isInstanceOf(AuthSessionException.class);
        verify(sessions, times(4)).admit(any()); // pre/post issue, each redemption
    }

    @Test
    void stickyFullArtifactCannotSurviveAdministratorDownscope() {
        var service = service(NOW);
        when(admins.current(any()))
                .thenReturn(new AdminAccessProfile(7, true, "FULL", List.of("USERS_VIEW"), 1));
        var issued =
                service.issue(issue("ADMIN_USERS", true, NOW.plusSeconds(600), AuthRole.ADMIN));
        when(admins.current(any()))
                .thenReturn(new AdminAccessProfile(7, true, "LIMITED", List.of("USERS_VIEW"), 2));
        assertThatThrownBy(() -> service.redeem(redeem(issued)))
                .isInstanceOf(StatusRuntimeException.class);
    }

    @Test
    void renderAuthorityIsBoundToPlanDigestJobAndLeaseWithoutChangingTicketBinding()
            throws Exception {
        var generator = java.security.KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        var pair = generator.generateKeyPair();
        jwt =
                ru.rutcampustrack.auth.support.TestJwtKeys.load(
                        pair,
                        keyDirectory,
                        mock(org.springframework.data.redis.core.StringRedisTemplate.class));
        var service = service(NOW);
        var identity = identity(AuthRole.TEACHER);
        String digest = "b".repeat(64);
        var reply =
                service.admit(
                        authority("TEACHER_JOURNAL", false, NOW.plusSeconds(600), identity)
                                .toBuilder()
                                .setSelectorHash(digest)
                                .setOwnerResourceId(JOB.toString())
                                .setLeaseId(LEASE.toString())
                                .build());
        var claims =
                io.jsonwebtoken.Jwts.parser()
                        .verifyWith(pair.getPublic())
                        .clock(() -> Date.from(NOW))
                        .build()
                        .parseSignedClaims(reply.getInternalToken())
                        .getPayload();
        assertThat(claims.get("token_use", String.class)).isEqualTo("academic_report");
        assertThat(claims.get("report_kind", String.class)).isEqualTo("TEACHER_JOURNAL");
        assertThat(claims.get("report_hash", String.class)).isEqualTo(digest);
        assertThat(claims.get("report_job", String.class)).isEqualTo(JOB.toString());
        assertThat(claims.get("report_lease", String.class)).isEqualTo(LEASE.toString());
        assertThat(claims.get("report_not_after", Number.class).longValue())
                .isEqualTo(NOW.plusSeconds(30).getEpochSecond());
        assertThat(claims.get("sid", String.class)).isEqualTo(SID.toString());
        assertThat(claims.get("sv", String.class)).isEqualTo("2");
        assertThat(claims.get("rv", String.class)).isEqualTo("3");
        assertThat(claims.getExpiration().toInstant()).isEqualTo(NOW.plusSeconds(30));
        var provider = mock(ru.rutcampustrack.shared.security.PublicKeyProvider.class);
        when(provider.getPublicKey()).thenReturn(pair.getPublic());
        var validator =
                new ru.rutcampustrack.shared.security.InternalJwtValidator(
                        provider,
                        new ru.rutcampustrack.shared.security.InternalJwtProperties(
                                null, 60, 0, false, null, null, null));
        assertThat(
                        validator
                                .validateAcademicReport(
                                        reply.getInternalToken(),
                                        "TEACHER_JOURNAL",
                                        digest,
                                        JOB.toString(),
                                        LEASE.toString())
                                .sessionId())
                .isEqualTo(SID);
        assertThatThrownBy(() -> validator.validate(reply.getInternalToken()))
                .isInstanceOf(ru.rutcampustrack.shared.security.InternalJwtException.class);
        assertThatThrownBy(
                        () ->
                                service.admit(
                                        authority(
                                                        "TEACHER_JOURNAL",
                                                        false,
                                                        NOW.plusSeconds(600),
                                                        identity)
                                                .toBuilder()
                                                .setOwnerResourceId(JOB.toString())
                                                .build()))
                .isInstanceOf(StatusRuntimeException.class);
    }

    @Test
    void delegatedStudentStatsRemainPossibleButCompositionRequiresHeadman() {
        var service = service(NOW);
        assertThatCode(
                        () ->
                                service.admit(
                                        authority(
                                                "HEADMAN_STATS",
                                                false,
                                                NOW.plusSeconds(60),
                                                identity(AuthRole.STUDENT))))
                .doesNotThrowAnyException();
        assertThatThrownBy(
                        () ->
                                service.admit(
                                        authority(
                                                "HEADMAN_GROUP_COMPOSITION",
                                                false,
                                                NOW.plusSeconds(60),
                                                identity(AuthRole.STUDENT))))
                .isInstanceOf(StatusRuntimeException.class);
    }

    @Test
    void signedReceiptCannotOutliveTheCurrentSession() {
        var service = service(NOW);
        var grant =
                new RoleGrant(
                        8,
                        7,
                        AuthRole.TEACHER,
                        RoleStatus.ACTIVE,
                        null,
                        NOW.minusSeconds(60),
                        NOW.minusSeconds(60));
        doReturn(
                        new SessionSnapshot(
                                SID,
                                7,
                                2,
                                3,
                                grant,
                                List.of(grant),
                                NOW.plusSeconds(15),
                                NOW.minusSeconds(60),
                                NOW,
                                null,
                                null,
                                AuthMethod.PASSWORD,
                                "",
                                ""))
                .when(sessions)
                .admit(any());
        var issued =
                service.issue(
                        issue("TEACHER_JOURNAL", false, NOW.plusSeconds(600), AuthRole.TEACHER));
        assertThat(Instant.parse(issued.getExpiresAt())).isEqualTo(NOW.plusSeconds(15));
        assertThat(store.ttl).isEqualTo(Duration.ofSeconds(15));
    }

    @Test
    void grpcErrorsNeverEchoCredentialsOrDependencyExceptionDetails() {
        var failing = mock(AuthReportCapabilityService.class);
        when(failing.admit(any()))
                .thenThrow(
                        new IllegalStateException(
                                "password=synthetic-secret; SQL selector payload"));
        var server =
                new AuthSecurityCapabilityGrpcService(
                        failing,
                        mock(AuthPasswordStepUpService.class),
                        mock(SubjectDeletionProofService.class));
        var failure = new java.util.concurrent.atomic.AtomicReference<Throwable>();
        var observer =
                new io.grpc.stub.StreamObserver<AdmittedReportAuthority>() {
                    public void onNext(AdmittedReportAuthority value) {
                        fail("No authority on failure");
                    }

                    public void onError(Throwable value) {
                        failure.set(value);
                    }

                    public void onCompleted() {
                        fail("No completed response on failure");
                    }
                };
        io.grpc.Context.current()
                .withValue(
                        ru.rutcampustrack.shared.security.grpc.ServicePrincipal.CONTEXT_KEY,
                        ru.rutcampustrack.shared.security.grpc.ServicePrincipal.ACADEMIC_SERVICE)
                .run(
                        () ->
                                server.admitStoredSession(
                                        authority(
                                                "TEACHER_JOURNAL",
                                                false,
                                                NOW.plusSeconds(60),
                                                identity(AuthRole.TEACHER)),
                                        observer));
        assertThat(failure.get()).isInstanceOf(StatusRuntimeException.class);
        assertThat(failure.get().getMessage())
                .doesNotContain("synthetic-secret", "SQL", "selector");
    }

    @Test
    void admittedSessionExposesExactSelectedGrantAndRefreshExpirySeparatelyFromTokenExpiry() {
        var service = service(NOW);
        when(jwt.generateInternalToken(
                        any(SessionSnapshot.class), any(Instant.class), any(Instant.class)))
                .thenReturn("synthetic-transient-token");
        var result = service.admitSession(identity(AuthRole.TEACHER));
        assertThat(result.hasActiveRoleGrantId()).isTrue();
        assertThat(result.getActiveRoleGrantId()).isEqualTo(8);
        assertThat(Instant.parse(result.getRefreshExpiresAt())).isEqualTo(NOW.plusSeconds(3600));
        assertThat(Instant.parse(result.getExpiresAt())).isEqualTo(NOW.plusSeconds(300));
        assertThat(result.hasRevokedAt()).isFalse();
    }

    private AuthReportCapabilityService service(Instant now) {
        var manager = mock(PlatformTransactionManager.class);
        when(manager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        doAnswer(
                        invocation -> {
                            SessionPrincipal p = invocation.getArgument(0);
                            var grant =
                                    new RoleGrant(
                                            8,
                                            7,
                                            p.selectedRole(),
                                            RoleStatus.ACTIVE,
                                            p.groupId(),
                                            NOW.minusSeconds(60),
                                            NOW.minusSeconds(60));
                            return new SessionSnapshot(
                                    SID,
                                    7,
                                    2,
                                    3,
                                    grant,
                                    List.of(grant),
                                    NOW.plusSeconds(3600),
                                    NOW.minusSeconds(60),
                                    NOW,
                                    null,
                                    null,
                                    AuthMethod.PASSWORD,
                                    "",
                                    "");
                        })
                .when(sessions)
                .admit(any());
        var properties = new InternalIssuerProperties();
        properties.setTokenTtlSeconds(300);
        return new AuthReportCapabilityService(
                sessions,
                jwt,
                properties,
                store,
                new ObjectMapper(),
                admins,
                manager,
                Clock.fixed(now, ZoneOffset.UTC));
    }

    private static IssueReportCapabilityRequest issue(
            String kind, boolean full, Instant expires, AuthRole role) {
        return IssueReportCapabilityRequest.newBuilder()
                .setOwnerResourceId(JOB.toString())
                .setAuthority(authority(kind, full, expires, identity(role)))
                .build();
    }

    private static AdmitStoredSessionRequest authority(
            String kind, boolean full, Instant expires, StoredSessionIdentity identity) {
        return AdmitStoredSessionRequest.newBuilder()
                .setIdentity(identity)
                .setReportKind(kind)
                .setSelectorHash("a".repeat(64))
                .setNotAfter(expires.toString())
                .setRequiresFull(full)
                .build();
    }

    private static StoredSessionIdentity identity(AuthRole role) {
        var value =
                StoredSessionIdentity.newBuilder()
                        .setUserId(7)
                        .setSessionId(SID.toString())
                        .setSessionVersion(2)
                        .setRolesVersion(3)
                        .setRole(role.name())
                        .setStatus("ACTIVE")
                        .setIsHeadman(role == AuthRole.HEADMAN);
        if (role == AuthRole.STUDENT || role == AuthRole.HEADMAN) value.setGroupId(5);
        return value.build();
    }

    private static RedeemReportCapabilityRequest redeem(IssuedReportCapability issued) {
        return RedeemReportCapabilityRequest.newBuilder().setTicket(issued.getTicket()).build();
    }

    private static final class MemoryStore implements ReportDownloadTicketStore {
        private final Map<String, String> values = new HashMap<>();
        private final Map<String, Integer> redemptions = new HashMap<>();
        private int issues;
        private Duration ttl;
        private String encoded;

        public boolean putIfAbsent(String digest, String payload, Duration ttl) {
            this.ttl = ttl;
            this.encoded = payload;
            return values.putIfAbsent(digest, payload) == null;
        }

        public Optional<String> find(String digest) {
            return Optional.ofNullable(values.get(digest));
        }

        public boolean allowIssue(String digest, int maximum, Duration window) {
            assertThat(maximum).isEqualTo(10);
            assertThat(window).isEqualTo(Duration.ofSeconds(60));
            return issues++ < maximum;
        }

        public boolean allowRedemption(String digest, int maximum, Duration window) {
            assertThat(maximum).isEqualTo(20);
            assertThat(window).isEqualTo(Duration.ofSeconds(60));
            return redemptions.merge(digest, 1, Integer::sum) <= maximum;
        }
    }
}
