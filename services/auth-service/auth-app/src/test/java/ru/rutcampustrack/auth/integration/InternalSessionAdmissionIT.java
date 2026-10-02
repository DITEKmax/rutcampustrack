package ru.rutcampustrack.auth.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.testcontainers.containers.PostgreSQLContainer;
import ru.rutcampustrack.auth.config.InternalIssuerProperties;
import ru.rutcampustrack.auth.config.JwtProperties;
import ru.rutcampustrack.auth.controller.InternalSemesterDeletionConfirmationController;
import ru.rutcampustrack.auth.controller.InternalSessionAdmissionController;
import ru.rutcampustrack.auth.exception.GlobalExceptionHandler;
import ru.rutcampustrack.auth.exception.OtpRateLimitException;
import ru.rutcampustrack.auth.exception.SemesterDeletionConfirmationExceptionHandler;
import ru.rutcampustrack.auth.dto.ConfirmSemesterDeletionRequest;
import ru.rutcampustrack.auth.repository.UserRepository;
import ru.rutcampustrack.auth.security.InternalIssuerSecretFilter;
import ru.rutcampustrack.auth.service.AuthService;
import ru.rutcampustrack.auth.service.BcryptConcurrencyGuard;
import ru.rutcampustrack.auth.service.JwtService;
import ru.rutcampustrack.auth.service.LoginRateLimiter;
import ru.rutcampustrack.auth.service.SemesterDeletionConfirmationService;
import ru.rutcampustrack.auth.session.SessionAdmissionException;
import ru.rutcampustrack.auth.session.SessionAdmissionService;
import ru.rutcampustrack.auth.session.SessionLifecycleService;
import ru.rutcampustrack.auth.session.model.AuthMethod;
import ru.rutcampustrack.auth.session.model.AuthRole;
import ru.rutcampustrack.auth.session.model.RoleGrant;
import ru.rutcampustrack.auth.session.model.RoleStatus;
import ru.rutcampustrack.auth.session.model.SecurityEvent;
import ru.rutcampustrack.auth.session.model.SessionSnapshot;
import ru.rutcampustrack.auth.session.model.SessionState;
import ru.rutcampustrack.auth.session.port.SessionStatePort;
import ru.rutcampustrack.auth.entity.User;
import ru.rutcampustrack.shared.observability.BusinessMetrics;
import ru.rutcampustrack.auth.session.port.AuthSessionQueryPort;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import javax.sql.DataSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Source proof for admission against a fresh PostgreSQL 16/V24 authority.
 * Reuse is deliberately disabled; this test is not run during source stage.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class InternalSessionAdmissionIT {

    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16")
            .withDatabaseName("rct_student_auth_admission")
            .withUsername("rct_student_auth_admission")
            .withPassword("rct_student_auth_admission_pass")
            .withReuse(false);

    private JdbcTemplate jdbc;
    private PlatformTransactionManager transactionManager;
    private JdbcSessionAuthorityHolder authorityHolder;
    private JwtService signingService;
    private long userSequence;

    @BeforeAll
    void startDatabase() {
        POSTGRES.start();
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("filesystem:" + migrationDirectory().toAbsolutePath())
                .load()
                .migrate();
        DriverManagerDataSource source = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        source.setDriverClassName("org.postgresql.Driver");
        DataSource dataSource = source;
        jdbc = new JdbcTemplate(dataSource);
        transactionManager = new DataSourceTransactionManager(dataSource);
        authorityHolder = new JdbcSessionAuthorityHolder(
                new ru.rutcampustrack.auth.session.jdbc.JdbcSessionAuthority(jdbc, transactionManager,
                        new ru.rutcampustrack.auth.event.PasswordChangedOutbox(
                                jdbc, new ObjectMapper().findAndRegisterModules())));
        signingService = createJwtService();
    }

    @AfterAll
    void stopDatabase() {
        POSTGRES.stop();
    }

    @Test
    void liveAdmissionThenCommittedRevokeIsDenied() throws Exception {
        Instant now = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
        UserFixture user = seedUser(GrantSeed.active(AuthRole.STUDENT, 1L));
        CreatedSession session = createSession(user, now, AuthRole.STUDENT);
        SessionAdmissionService admission = admissionService(now);
        String access = accessToken(session.snapshot(), now);

        assertThat(admission.admit(access).userId()).isEqualTo(Long.toString(user.userId()));

        SessionStatePort.RevokeResult revoked = authorityHolder.authority().revokeCurrent(
                new SessionStatePort.RevokeCurrentCommand(
                        user.userId(), session.sessionId(), now.plusSeconds(1),
                        event(user.userId(), session.sessionId(), SecurityEvent.Type.CURRENT_LOGOUT, now.plusSeconds(1))
                )
        );
        assertThat(revoked.succeeded()).isTrue();
        assertThatThrownBy(() -> admission.admit(access))
                .isInstanceOfSatisfying(SessionAdmissionException.class, exception ->
                        assertThat(exception.code()).isEqualTo(SessionAdmissionException.Code.SESSION_REVOKED));
    }

    @Test
    void rolesVersionAndSessionVersionChangesProduceStaleDenial() throws Exception {
        Instant now = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
        UserFixture user = seedUser(
                GrantSeed.active(AuthRole.STUDENT, 1L),
                GrantSeed.active(AuthRole.TEACHER, null));
        CreatedSession session = createSession(user, now, AuthRole.STUDENT);
        SessionAdmissionService admission = admissionService(now);
        String access = accessToken(session.snapshot(), now);

        updateGrantStatus(user, AuthRole.STUDENT, RoleStatus.GRADUATED);
        assertThatThrownBy(() -> admission.admit(access))
                .isInstanceOfSatisfying(SessionAdmissionException.class, exception ->
                        assertThat(exception.code()).isEqualTo(SessionAdmissionException.Code.SESSION_STATE_STALE));

        long teacherGrant = grantId(user, AuthRole.TEACHER);
        SessionStatePort.RoleSelectionResult selected = authorityHolder.authority().selectRole(
                new SessionStatePort.SelectRoleCommand(
                        user.userId(), session.sessionId(), AuthRole.TEACHER,
                        session.snapshot().sessionVersion(), now.plusSeconds(2),
                        event(user.userId(), session.sessionId(), SecurityEvent.Type.ROLE_CHANGED, now.plusSeconds(2))
                )
        );
        assertThat(selected.succeeded()).isTrue();
        assertThat(selected.snapshot().activeRole().grantId()).isEqualTo(teacherGrant);
        assertThat(selected.snapshot().sessionVersion()).isGreaterThan(session.snapshot().sessionVersion());
        assertThatThrownBy(() -> admission.admit(access))
                .isInstanceOfSatisfying(SessionAdmissionException.class, exception ->
                        assertThat(exception.code()).isEqualTo(SessionAdmissionException.Code.SESSION_STATE_STALE));
    }

    @Test
    void suspendedActiveGrantIsClearedAndDenied() throws Exception {
        Instant now = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
        UserFixture user = seedUser(GrantSeed.active(AuthRole.STUDENT, 1L));
        CreatedSession session = createSession(user, now, AuthRole.STUDENT);
        SessionAdmissionService admission = admissionService(now);
        String access = accessToken(session.snapshot(), now);

        updateGrantStatus(user, AuthRole.STUDENT, RoleStatus.SUSPENDED);

        assertThatThrownBy(() -> admission.admit(access))
                .isInstanceOfSatisfying(SessionAdmissionException.class, exception ->
                assertThat(exception.code()).isEqualTo(SessionAdmissionException.Code.ROLE_NOT_SELECTABLE));
    }

    @Test
    void httpAdmissionUsesRealSecretGuardAndReturnsSignedInternalToken() throws Exception {
        Instant now = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
        UserFixture user = seedUser(GrantSeed.active(AuthRole.STUDENT, 1L));
        CreatedSession session = createSession(user, now, AuthRole.STUDENT);
        InternalIssuerProperties properties = issuerProperties();
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.getBeanFactory().registerSingleton("jwtService", signingService);
            context.getBeanFactory().registerSingleton("sessionStatePort", authorityHolder.authority());
            context.getBeanFactory().registerSingleton("internalIssuerProperties", properties);
            context.register(SessionAdmissionService.class, InternalSessionAdmissionController.class);
            context.refresh();

            SessionAdmissionService admission = context.getBean(SessionAdmissionService.class);
            InternalSessionAdmissionController controller =
                    context.getBean(InternalSessionAdmissionController.class);
            assertThat(context.getBean(JwtService.class)).isSameAs(signingService);
            assertThat(context.getBean(SessionStatePort.class)).isSameAs(authorityHolder.authority());
            assertThat(context.getBean(InternalIssuerProperties.class)).isSameAs(properties);
            assertThat(admission).isInstanceOf(SessionAdmissionService.class);
            assertThat(controller).isInstanceOf(InternalSessionAdmissionController.class);

            MockMvc mvc = mockMvc(controller, properties);
            String access = accessToken(session.snapshot(), now);

            MvcResult result = mvc.perform(post("/internal/auth/admit")
                            .header(InternalIssuerSecretFilter.HEADER, properties.getSecret())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"accessToken\":\"" + access + "\"}"))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Cache-Control", "no-store"))
                    .andReturn();
            JsonNode body = new ObjectMapper().readTree(result.getResponse().getContentAsString());
            String internal = body.get("internalToken").asText();
            Claims claims = Jwts.parser()
                    .verifyWith(publicKey())
                    .requireIssuer("rutcampustrack-auth")
                    .requireAudience(JwtService.INTERNAL_JWT_AUDIENCE)
                    .require(JwtService.TOKEN_USE_CLAIM, JwtService.TOKEN_USE_INTERNAL)
                    .build()
                    .parseSignedClaims(internal)
                    .getPayload();

            assertThat(claims.getSubject()).isEqualTo(Long.toString(user.userId()));
            assertThat(claims.get("sid", String.class)).isEqualTo(session.sessionId().toString());
            assertThat(claims.get("sv", String.class)).isEqualTo("1");
            assertThat(claims.get("rv", String.class)).isEqualTo(Long.toString(user.rolesVersion()));
            assertThat(body.get("expiresAt").asText()).isNotBlank();
            assertThat(body.get("role").asText()).isEqualTo("STUDENT");

            mvc.perform(post("/internal/issue-internal-jwt")
                            .header(InternalIssuerSecretFilter.HEADER, properties.getSecret())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isNotFound());
        }
    }

    @Test
    void httpSecretDenialStaysDistinctFromSessionDenialAndIsNotCached() throws Exception {
        Instant now = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
        UserFixture user = seedUser(GrantSeed.active(AuthRole.STUDENT, 1L));
        CreatedSession session = createSession(user, now, AuthRole.STUDENT);
        InternalIssuerProperties properties = issuerProperties();
        MockMvc mvc = mockMvc(admissionService(now), properties);

        mvc.perform(post("/internal/auth/admit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accessToken\":\"malformed\"}"))
                .andExpect(status().isUnauthorized());

        mvc.perform(post("/internal/auth/admit")
                        .header(InternalIssuerSecretFilter.HEADER, properties.getSecret())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accessToken\":\"malformed\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("Cache-Control", "no-store"));
    }

    @Test
    void httpTypedFailuresMapToProblemDetailsAndNoStore() throws Exception {
        Instant now = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS);

        UserFixture revokedUser = seedUser(GrantSeed.active(AuthRole.STUDENT, 1L));
        CreatedSession revokedSession = createSession(revokedUser, now, AuthRole.STUDENT);
        String revokedAccess = accessToken(revokedSession.snapshot(), now);
        assertThat(authorityHolder.authority().revokeCurrent(
                new SessionStatePort.RevokeCurrentCommand(
                        revokedUser.userId(), revokedSession.sessionId(), now.plusSeconds(1),
                        event(revokedUser.userId(), revokedSession.sessionId(),
                                SecurityEvent.Type.CURRENT_LOGOUT, now.plusSeconds(1)))
        ).succeeded()).isTrue();

        UserFixture ungrantedUser = seedUser(GrantSeed.active(AuthRole.STUDENT, 1L));
        CreatedSession ungrantedSession = createSession(ungrantedUser, now, AuthRole.STUDENT);
        String ungrantedAccess = accessToken(ungrantedSession.snapshot(), now);
        jdbc.update(
                "UPDATE auth_sessions SET active_role_grant_id = NULL, session_version = session_version + 1 "
                        + "WHERE sid = ? AND user_id = ?",
                ungrantedSession.sessionId(), ungrantedUser.userId());

        UserFixture suspendedUser = seedUser(GrantSeed.active(AuthRole.STUDENT, 1L));
        CreatedSession suspendedSession = createSession(suspendedUser, now, AuthRole.STUDENT);
        String suspendedAccess = accessToken(suspendedSession.snapshot(), now);
        updateGrantStatus(suspendedUser, AuthRole.STUDENT, RoleStatus.SUSPENDED);

        UserFixture staleUser = seedUser(GrantSeed.active(AuthRole.STUDENT, 1L));
        CreatedSession staleSession = createSession(staleUser, now, AuthRole.STUDENT);
        String staleAccess = accessToken(staleSession.snapshot(), now);
        updateGrantStatus(staleUser, AuthRole.STUDENT, RoleStatus.GRADUATED);

        UserFixture unavailableUser = seedUser(GrantSeed.active(AuthRole.STUDENT, 1L));
        CreatedSession unavailableSession = createSession(unavailableUser, now, AuthRole.STUDENT);
        String unavailableAccess = accessToken(unavailableSession.snapshot(), now);

        InternalIssuerProperties properties = issuerProperties();
        withProductionAdmission(authorityHolder.authority(), properties, mvc -> {
            assertProblem(mvc, "malformed", 401, "INVALID_SESSION", properties);
            assertProblem(mvc, revokedAccess, 401, "SESSION_REVOKED", properties);
            assertProblem(mvc, ungrantedAccess, 403, "ROLE_NOT_GRANTED", properties);
            assertProblem(mvc, suspendedAccess, 403, "ROLE_NOT_SELECTABLE", properties);
            assertProblem(mvc, staleAccess, 409, "SESSION_STATE_STALE", properties);
        });

        SessionStatePort unavailableAuthority = mock(SessionStatePort.class);
        when(unavailableAuthority.snapshot(any())).thenReturn(
                SessionStatePort.SnapshotResult.failure(
                        SessionStatePort.FailureCode.AUTHORITY_UNAVAILABLE));
        withProductionAdmission(unavailableAuthority, properties, mvc ->
                assertProblem(mvc, unavailableAccess, 503, "AUTHORITY_UNAVAILABLE", properties));
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"SEMESTER", "FLOOR", "BUILDING"})
    void deletionConfirmationRequiresLiveAdminAndCurrentPasswordWithoutCreatingSessions(String targetType) throws Exception {
        exercisePasswordConfirmation(targetType);
    }

    @Test
    void userArchiveConfirmationRequiresLiveAdminAndSharesCredentialAttemptBudget() throws Exception {
        exercisePasswordConfirmation("USER_ARCHIVE");
    }

    @Test
    void restoredVisibleAccountWithRetainedArchivedGrantsHasNoRoleUntilExplicitAssignment() throws Exception {
        Instant now=Instant.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
        UserFixture user=seedUser(GrantSeed.active(AuthRole.STUDENT,1L),GrantSeed.active(AuthRole.TEACHER,null));
        CreatedSession session=createSession(user,now,AuthRole.STUDENT);
        String previousAccess=accessToken(session.snapshot(),now);
        jdbc.update("UPDATE user_role_grants SET status='archived' WHERE user_id=?",user.userId());
        jdbc.update("UPDATE auth_sessions SET active_role_grant_id=NULL,session_version=session_version+1 WHERE sid=?",session.sessionId());
        jdbc.update("UPDATE users SET status='active',group_id=NULL,is_headman=FALSE WHERE id=?",user.userId());
        assertThatThrownBy(() -> admissionService(now).admit(previousAccess)).isInstanceOf(SessionAdmissionException.class);
        var snapshot=authorityHolder.authority().snapshot(new SessionStatePort.SnapshotCommand(user.userId(),session.sessionId(),now.plusSeconds(1)));
        assertThat(snapshot.succeeded()).isTrue();
        assertThat(snapshot.snapshot().activeRole()).isNull();
        for (AuthRole role:List.of(AuthRole.STUDENT,AuthRole.TEACHER)) {
            var selected=authorityHolder.authority().selectRole(new SessionStatePort.SelectRoleCommand(
                    user.userId(),session.sessionId(),role,snapshot.snapshot().sessionVersion(),now.plusSeconds(2),
                    event(user.userId(),session.sessionId(),SecurityEvent.Type.ROLE_CHANGED,now.plusSeconds(2))));
            assertThat(selected.succeeded()).isFalse();
            assertThat(selected.failureCode()).isEqualTo(SessionStatePort.FailureCode.ROLE_NOT_SELECTABLE);
        }
        updateGrantStatus(user,AuthRole.TEACHER,RoleStatus.ACTIVE);
        var assigned=authorityHolder.authority().selectRole(new SessionStatePort.SelectRoleCommand(
                user.userId(),session.sessionId(),AuthRole.TEACHER,snapshot.snapshot().sessionVersion(),now.plusSeconds(3),
                event(user.userId(),session.sessionId(),SecurityEvent.Type.ROLE_CHANGED,now.plusSeconds(3))));
        assertThat(assigned.succeeded()).isTrue();
        assertThat(assigned.snapshot().activeRole().role()).isEqualTo(AuthRole.TEACHER);
    }

    private void exercisePasswordConfirmation(String targetType) throws Exception {
        String route = switch (targetType) {
            case "SEMESTER" -> "/internal/auth/confirm-semester-deletion";
            case "USER_ARCHIVE" -> "/internal/auth/confirm-user-archive";
            default -> "/internal/auth/confirm-map-deletion";
        };
        Instant now = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
        InternalIssuerProperties properties = issuerProperties();
        UserRepository users = mock(UserRepository.class);
        LoginRateLimiter rateLimiter = mock(LoginRateLimiter.class);
        PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4);
        String currentPassword = "semester-confirm-current-password-sentinel";
        String currentHash = passwordEncoder.encode(currentPassword);

        UserFixture admin = seedUser(GrantSeed.active(AuthRole.ADMIN, null));
        CreatedSession adminSession = createSession(admin, now, AuthRole.ADMIN);
        User adminRecord = mock(User.class);
        when(adminRecord.getPasswordHash()).thenReturn(currentHash);
        when(users.findById(admin.userId())).thenReturn(Optional.of(adminRecord));

        AuthService authService = new AuthService(
                users, signingService, passwordEncoder, new JwtProperties("unused", 900, 604800),
                rateLimiter, new BcryptConcurrencyGuard(2, 0), mock(BusinessMetrics.class),
                mock(AuthSessionQueryPort.class),
                new SessionLifecycleService(authorityHolder.authority(), authorityHolder.authority()),
                Clock.fixed(now, ZoneOffset.UTC));
        SemesterDeletionConfirmationService confirmationService = new SemesterDeletionConfirmationService(
                signingService, authService, users, passwordEncoder, rateLimiter,
                new BcryptConcurrencyGuard(2, 0));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(
                        new InternalSemesterDeletionConfirmationController(confirmationService),
                        new ru.rutcampustrack.auth.controller.InternalMapDeletionConfirmationController(confirmationService),
                        new ru.rutcampustrack.auth.controller.InternalUserArchiveConfirmationController(confirmationService))
                // Register the global handler first so equal priorities cannot hide route-specific denials.
                .setControllerAdvice(new GlobalExceptionHandler(), new SemesterDeletionConfirmationExceptionHandler())
                .addFilters(new InternalIssuerSecretFilter(properties))
                .build();

        int sessionsBefore = sessionCount(admin.userId());
        String adminInternal = internalToken(adminSession.snapshot(), now);
        mvc.perform(post(route)
                        .header(InternalIssuerSecretFilter.HEADER, properties.getSecret())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(confirmationBody(adminInternal, currentPassword, 41L, targetType)))
                .andExpect(status().isNoContent())
                .andExpect(header().string("Cache-Control", "no-store"));
        assertThat(sessionCount(admin.userId())).isEqualTo(sessionsBefore);

        MvcResult wrongPassword = mvc.perform(post(route)
                        .header(InternalIssuerSecretFilter.HEADER, properties.getSecret())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(confirmationBody(adminInternal, "wrong-password-sentinel", 41L, targetType)))
                .andExpect(status().isForbidden())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andReturn();
        assertThat(wrongPassword.getResponse().getContentAsString())
                .doesNotContain("wrong-password-sentinel", currentPassword, adminInternal);
        verify(rateLimiter).recordFailure(
                "internal-semester-deletion", "__semester_delete_confirmation__:" + admin.userId());
        assertThat(sessionCount(admin.userId())).isEqualTo(sessionsBefore);

        UserFixture teacher = seedUser(GrantSeed.active(AuthRole.TEACHER, null));
        CreatedSession teacherSession = createSession(teacher, now, AuthRole.TEACHER);
        String teacherInternal = internalToken(teacherSession.snapshot(), now);
        mvc.perform(post(route)
                        .header(InternalIssuerSecretFilter.HEADER, properties.getSecret())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(confirmationBody(teacherInternal, currentPassword, 41L, targetType)))
                .andExpect(status().isForbidden())
                .andExpect(header().string("Cache-Control", "no-store"));
        verify(users, org.mockito.Mockito.never()).findById(teacher.userId());
        assertThat(sessionCount(teacher.userId())).isEqualTo(1);

        UserFixture revokedAdmin = seedUser(GrantSeed.active(AuthRole.ADMIN, null));
        CreatedSession revokedSession = createSession(revokedAdmin, now, AuthRole.ADMIN);
        String revokedInternal = internalToken(revokedSession.snapshot(), now);
        assertThat(authorityHolder.authority().revokeCurrent(new SessionStatePort.RevokeCurrentCommand(
                revokedAdmin.userId(), revokedSession.sessionId(), now.plusSeconds(1),
                event(revokedAdmin.userId(), revokedSession.sessionId(),
                        SecurityEvent.Type.CURRENT_LOGOUT, now.plusSeconds(1)))).succeeded()).isTrue();
        mvc.perform(post(route)
                        .header(InternalIssuerSecretFilter.HEADER, properties.getSecret())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(confirmationBody(revokedInternal, currentPassword, 41L, targetType)))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("Cache-Control", "no-store"));
        assertThat(sessionCount(revokedAdmin.userId())).isEqualTo(1);

        UserFixture staleAdmin = seedUser(GrantSeed.active(AuthRole.ADMIN, null));
        CreatedSession staleSession = createSession(staleAdmin, now, AuthRole.ADMIN);
        String staleInternal = internalToken(staleSession.snapshot(), now);
        updateGrantStatus(staleAdmin, AuthRole.ADMIN, RoleStatus.GRADUATED);
        mvc.perform(post(route)
                        .header(InternalIssuerSecretFilter.HEADER, properties.getSecret())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(confirmationBody(staleInternal, currentPassword, 41L, targetType)))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("Cache-Control", "no-store"));
        assertThat(sessionCount(staleAdmin.userId())).isEqualTo(1);

        mvc.perform(post(route)
                        .header(InternalIssuerSecretFilter.HEADER, properties.getSecret())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(confirmationBody("forged-internal-token", currentPassword, 41L, targetType)))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("Cache-Control", "no-store"));

        String accessToken = accessToken(adminSession.snapshot(), now);
        mvc.perform(post(route)
                        .header(InternalIssuerSecretFilter.HEADER, properties.getSecret())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(confirmationBody(accessToken, currentPassword, 41L, targetType)))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("Cache-Control", "no-store"));

        UserFixture hashlessAdmin = seedUser(GrantSeed.active(AuthRole.ADMIN, null));
        CreatedSession hashlessSession = createSession(hashlessAdmin, now, AuthRole.ADMIN);
        User hashlessRecord = mock(User.class);
        when(hashlessRecord.getPasswordHash()).thenReturn(null);
        when(users.findById(hashlessAdmin.userId())).thenReturn(Optional.of(hashlessRecord));
        mvc.perform(post(route)
                        .header(InternalIssuerSecretFilter.HEADER, properties.getSecret())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(confirmationBody(internalToken(hashlessSession.snapshot(), now),
                                currentPassword, 41L, targetType)))
                .andExpect(status().isForbidden())
                .andExpect(header().string("Cache-Control", "no-store"));
        assertThat(sessionCount(hashlessAdmin.userId())).isEqualTo(1);

        MvcResult invalidRequest = mvc.perform(post(route)
                        .header(InternalIssuerSecretFilter.HEADER, properties.getSecret())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"internalToken\":\"request-token-sentinel\","
                                + "\"password\":\"request-password-sentinel\",\"semesterId\":0,"
                                + "\"operationId\":\"not-a-uuid\",\"previewDigest\":\"digest-sentinel\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andReturn();
        assertThat(invalidRequest.getResponse().getContentAsString())
                .doesNotContain("request-token-sentinel", "request-password-sentinel", "digest-sentinel");

        mvc.perform(post(route)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(confirmationBody(adminInternal, currentPassword, 41L, targetType)))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("Cache-Control", "no-store"));

        mvc.perform(post(route)
                        .header(InternalIssuerSecretFilter.HEADER, "wrong-internal-issuer-secret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(confirmationBody(adminInternal, currentPassword, 41L, targetType)))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("Cache-Control", "no-store"));

        doThrow(new OtpRateLimitException("rate-limit-message-sentinel"))
                .when(rateLimiter).checkBlocked(
                        eq("internal-semester-deletion"),
                        eq("__semester_delete_confirmation__:" + admin.userId()));
        MvcResult limited = mvc.perform(post(route)
                        .header(InternalIssuerSecretFilter.HEADER, properties.getSecret())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(confirmationBody(adminInternal, currentPassword, 41L, targetType)))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andReturn();
        assertThat(limited.getResponse().getContentAsString()).doesNotContain("rate-limit-message-sentinel");
        assertThat(sessionCount(admin.userId())).isEqualTo(sessionsBefore);

        if (!"SEMESTER".equals(targetType) && !"USER_ARCHIVE".equals(targetType)) {
            String invalidBody = new ObjectMapper().writeValueAsString(
                    new ru.rutcampustrack.auth.dto.ConfirmMapDeletionRequest(
                            adminInternal, currentPassword,
                            ru.rutcampustrack.auth.dto.ConfirmMapDeletionRequest.TargetType.valueOf(targetType),
                            0, UUID.randomUUID(), "invalid-digest-sentinel"));
            MvcResult invalidMapPreview = mvc.perform(post(route)
                            .header(InternalIssuerSecretFilter.HEADER, properties.getSecret())
                            .contentType(MediaType.APPLICATION_JSON).content(invalidBody))
                    .andExpect(status().isBadRequest())
                    .andExpect(header().string("Cache-Control", "no-store")).andReturn();
            assertThat(invalidMapPreview.getResponse().getContentAsString())
                    .doesNotContain(adminInternal, currentPassword, "invalid-digest-sentinel");
        }

        org.mockito.Mockito.reset(rateLimiter);
        doThrow(new IllegalStateException("authority-failure-sentinel"))
                .when(users).findById(admin.userId());
        MvcResult unavailable = mvc.perform(post(route)
                        .header(InternalIssuerSecretFilter.HEADER, properties.getSecret())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(confirmationBody(adminInternal, currentPassword, 41L, targetType)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().string("Cache-Control", "no-store")).andReturn();
        assertThat(unavailable.getResponse().getContentAsString())
                .doesNotContain("authority-failure-sentinel", adminInternal, currentPassword);
        assertThat(sessionCount(admin.userId())).isEqualTo(sessionsBefore);
    }

    private SessionAdmissionService admissionService(Instant now) {
        InternalIssuerProperties properties = issuerProperties();
        properties.setTokenTtlSeconds(60);
        return new SessionAdmissionService(
                jwtService(), authorityHolder.authority(), properties, Clock.fixed(now, ZoneOffset.UTC));
    }

    private InternalIssuerProperties issuerProperties() {
        InternalIssuerProperties properties = new InternalIssuerProperties();
        properties.setSecret("test-internal-issuer-secret-32-bytes-or-more-for-test-env");
        properties.setTokenTtlSeconds(60);
        return properties;
    }

    private MockMvc mockMvc(SessionAdmissionService admission, InternalIssuerProperties properties) {
        return MockMvcBuilders.standaloneSetup(new InternalSessionAdmissionController(admission))
                .setControllerAdvice(new GlobalExceptionHandler())
                .addFilters(new InternalIssuerSecretFilter(properties))
                .build();
    }

    private MockMvc mockMvc(InternalSessionAdmissionController controller, InternalIssuerProperties properties) {
        return MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .addFilters(new InternalIssuerSecretFilter(properties))
                .build();
    }

    private void withProductionAdmission(
            SessionStatePort authority,
            InternalIssuerProperties properties,
            MockMvcScenario scenario
    ) throws Exception {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.getBeanFactory().registerSingleton("jwtService", signingService);
            context.getBeanFactory().registerSingleton("sessionStatePort", authority);
            context.getBeanFactory().registerSingleton("internalIssuerProperties", properties);
            context.register(SessionAdmissionService.class, InternalSessionAdmissionController.class);
            context.refresh();

            scenario.run(mockMvc(
                    context.getBean(InternalSessionAdmissionController.class), properties));
        }
    }

    private void assertProblem(
            MockMvc mvc,
            String accessToken,
            int expectedStatus,
            String expectedCode,
            InternalIssuerProperties properties
    ) throws Exception {
        MvcResult result = mvc.perform(post("/internal/auth/admit")
                        .header(InternalIssuerSecretFilter.HEADER, properties.getSecret())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accessToken\":\"" + accessToken + "\"}"))
                .andExpect(status().is(expectedStatus))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andReturn();
        JsonNode body = new ObjectMapper().readTree(result.getResponse().getContentAsString());
        assertThat(body.path("extras").path("code").asText()).isEqualTo(expectedCode);
    }

    private String accessToken(SessionSnapshot snapshot, Instant now) {
        return jwtService().generateSessionAccessToken(snapshot, now, now.plusSeconds(300));
    }

    private String internalToken(SessionSnapshot snapshot, Instant now) {
        return signingService.generateInternalToken(snapshot, now, now.plusSeconds(300));
    }

    private String confirmationBody(String token, String password, long targetId, String targetType) throws Exception {
        if ("USER_ARCHIVE".equals(targetType)) {
            return new ObjectMapper().writeValueAsString(new ru.rutcampustrack.auth.dto.ConfirmUserArchiveRequest(
                    token,password,ru.rutcampustrack.auth.dto.ConfirmUserArchiveRequest.Purpose.USER_ARCHIVE,
                    targetId,UUID.randomUUID(),"a".repeat(64)));
        }
        if (!"SEMESTER".equals(targetType)) {
            return new ObjectMapper().writeValueAsString(new ru.rutcampustrack.auth.dto.ConfirmMapDeletionRequest(
                    token, password, ru.rutcampustrack.auth.dto.ConfirmMapDeletionRequest.TargetType.valueOf(targetType),
                    targetId, UUID.randomUUID(), "a".repeat(64)));
        }
        return new ObjectMapper().writeValueAsString(new ConfirmSemesterDeletionRequest(
                token, password, targetId, UUID.randomUUID(), "preview-digest-test-value"));
    }

    private int sessionCount(long userId) {
        return jdbc.queryForObject("SELECT count(*) FROM auth_sessions WHERE user_id = ?", Integer.class, userId);
    }

    private JwtService jwtService() {
        return signingService;
    }

    private JwtService createJwtService() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            KeyPair pair = generator.generateKeyPair();
            JwtService service = new JwtService(
                    new JwtProperties("unused", 900, 604800), mock(StringRedisTemplate.class));
            setField(service, "privateKey", pair.getPrivate());
            setField(service, "publicKey", pair.getPublic());
            setField(service, "keyId", "it-kid");
            return service;
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private java.security.PublicKey publicKey() {
        try {
            var field = JwtService.class.getDeclaredField("publicKey");
            field.setAccessible(true);
            return (java.security.PublicKey) field.get(signingService);
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private CreatedSession createSession(UserFixture user, Instant now, AuthRole role) {
        UUID sessionId = UUID.randomUUID();
        UUID refreshJti = UUID.randomUUID();
        long activeGrantId = grantId(user, role);
        SessionState state = new SessionState(
                sessionId, user.userId(), activeGrantId, 1, refreshJti, null,
                now.plusSeconds(86_400), now, now, null, null,
                AuthMethod.OTP, "admission-it", "admission-it");
        SecurityEvent login = event(user.userId(), sessionId, SecurityEvent.Type.LOGIN, now);
        SessionStatePort.CreateSessionResult result = authorityHolder.authority().createSession(
                new SessionStatePort.CreateSessionCommand(
                        state, user.rolesVersion(), user.grants(), null, login));
        assertThat(result.succeeded()).isTrue();
        return new CreatedSession(sessionId, result.snapshot());
    }

    private UserFixture seedUser(GrantSeed... seeds) {
        String login = "admission-it-" + (++userSequence);
        long userId = jdbc.queryForObject(
                """
                        INSERT INTO users (
                            login, password_hash, last_name, first_name,
                            role, status, is_headman, group_id,
                            initial_password, password_changed, created_at, updated_at
                        ) VALUES (?, 'admission-hash', 'Admission', 'Test',
                                  CAST('student' AS user_role), CAST('active' AS account_status),
                                  FALSE, 1, 'initial', FALSE, ?, ?)
                        RETURNING id
                        """,
                Long.class, login, Timestamp.from(Instant.now()), Timestamp.from(Instant.now()));
        Instant created = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
        for (GrantSeed seed : seeds) {
            jdbc.update(
                    """
                            INSERT INTO user_role_grants (
                                user_id, role, status, group_id, created_at, updated_at
                            ) VALUES (?, ?, ?, ?, ?, ?)
                            """,
                    userId,
                    seed.role().name().toLowerCase(Locale.ROOT),
                    seed.status().name().toLowerCase(Locale.ROOT),
                    seed.groupId(),
                    Timestamp.from(created),
                    Timestamp.from(created));
        }
        long rolesVersion = jdbc.queryForObject(
                "SELECT roles_version FROM users WHERE id = ?", Long.class, userId);
        List<RoleGrant> grants = jdbc.query(
                """
                        SELECT id, user_id, role, status, group_id, created_at, updated_at
                        FROM user_role_grants WHERE user_id = ? ORDER BY id
                        """,
                InternalSessionAdmissionIT::mapGrant, userId);
        return new UserFixture(userId, rolesVersion, grants);
    }

    private void updateGrantStatus(UserFixture user, AuthRole role, RoleStatus status) {
        RoleGrant grant = grant(user, role);
        Instant updatedAt = grant.createdAt().plusSeconds(1);
        int updatedRows = jdbc.update(
                "UPDATE user_role_grants SET status = ?, updated_at = ? WHERE id = ? AND user_id = ?",
                status.name().toLowerCase(Locale.ROOT), Timestamp.from(updatedAt),
                grant.grantId(), user.userId());
        assertThat(updatedRows).isEqualTo(1);

        Boolean timestampsAreOrdered = jdbc.queryForObject(
                "SELECT updated_at >= created_at FROM user_role_grants "
                        + "WHERE id = ? AND user_id = ?",
                Boolean.class,
                grant.grantId(), user.userId());
        assertThat(timestampsAreOrdered)
                .as("grant %s for user %s must persist updated_at >= created_at",
                        grant.grantId(), user.userId())
                .isTrue();
    }

    private RoleGrant grant(UserFixture user, AuthRole role) {
        return user.grants().stream()
                .filter(candidate -> candidate.role() == role)
                .findFirst()
                .orElseThrow();
    }

    private long grantId(UserFixture user, AuthRole role) {
        return grant(user, role).grantId();
    }

    private SecurityEvent event(long userId, UUID sessionId, SecurityEvent.Type type, Instant when) {
        return event(userId, sessionId, type, when, AuthMethod.OTP);
    }

    private SecurityEvent event(
            long userId, UUID sessionId, SecurityEvent.Type type, Instant when, AuthMethod method) {
        return new SecurityEvent(userId, sessionId, type, when, method, "admission-it", "admission-it");
    }

    private static RoleGrant mapGrant(ResultSet rs, int rowNum) throws SQLException {
        long groupId = rs.getLong("group_id");
        boolean groupIdWasNull = rs.wasNull();
        return new RoleGrant(
                rs.getLong("id"),
                rs.getLong("user_id"),
                AuthRole.valueOf(rs.getString("role").toUpperCase(Locale.ROOT)),
                RoleStatus.valueOf(rs.getString("status").toUpperCase(Locale.ROOT)),
                groupIdWasNull ? null : groupId,
                rs.getObject("created_at", java.time.OffsetDateTime.class).toInstant(),
                rs.getObject("updated_at", java.time.OffsetDateTime.class).toInstant());
    }

    private static Path migrationDirectory() {
        Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        while (current != null) {
            Path candidate = current.resolve(
                    "services/academic-service/academic-app/src/main/resources/db/migration");
            if (Files.isRegularFile(candidate.resolve("V24__auth_session_authority.sql"))) {
                return candidate;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("academic migration directory is unavailable");
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        var field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private record JdbcSessionAuthorityHolder(
            ru.rutcampustrack.auth.session.jdbc.JdbcSessionAuthority authority) {
    }

    private record UserFixture(long userId, long rolesVersion, List<RoleGrant> grants) {
    }

    private record CreatedSession(UUID sessionId, SessionSnapshot snapshot) {
    }

    private record GrantSeed(AuthRole role, RoleStatus status, Long groupId) {
        private static GrantSeed active(AuthRole role, Long groupId) {
            return new GrantSeed(role, RoleStatus.ACTIVE, groupId);
        }
    }

    @FunctionalInterface
    private interface MockMvcScenario {
        void run(MockMvc mvc) throws Exception;
    }
}
