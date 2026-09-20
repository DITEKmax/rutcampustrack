package ru.rutcampustrack.auth.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import ru.rutcampustrack.auth.config.JwtProperties;
import ru.rutcampustrack.auth.dto.ChangePasswordRequest;
import ru.rutcampustrack.auth.dto.CurrentSessionResponse;
import ru.rutcampustrack.auth.dto.LoginRequest;
import ru.rutcampustrack.auth.dto.PublicKeyResponse;
import ru.rutcampustrack.auth.dto.TokenResponse;
import ru.rutcampustrack.auth.exception.GlobalExceptionHandler;
import ru.rutcampustrack.auth.security.SessionPrincipal;
import ru.rutcampustrack.auth.service.AuthService;
import ru.rutcampustrack.auth.service.OtpService;
import ru.rutcampustrack.auth.service.TmaService;
import ru.rutcampustrack.auth.service.WsTicketService;
import ru.rutcampustrack.auth.session.AuthSessionException;
import ru.rutcampustrack.auth.session.model.AuthMethod;
import ru.rutcampustrack.auth.session.model.AuthRole;
import ru.rutcampustrack.auth.session.model.RoleGrant;
import ru.rutcampustrack.auth.session.model.RoleStatus;
import ru.rutcampustrack.auth.session.model.SessionRevokeReason;
import ru.rutcampustrack.auth.session.model.SessionSnapshot;
import ru.rutcampustrack.auth.session.port.CredentialSessionTransactionPort;
import ru.rutcampustrack.auth.session.port.AuthSessionQueryPort;
import ru.rutcampustrack.auth.session.port.SessionStatePort;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthSessionControllerTest {

    private static final long USER_ID = 42L;
    private static final UUID SESSION_ID = UUID.fromString("55555555-5555-4555-8555-555555555555");
    private static final Instant CREATED_AT = Instant.parse("2026-09-10T10:00:00Z");

    private AuthService authService;
    private AuthSessionQueryPort queryPort;
    private WsTicketService wsTicketService;
    private AuthSessionController controller;
    private Authentication authentication;
    private SessionPrincipal principal;
    private SessionSnapshot snapshot;

    @BeforeEach
    void setUp() {
        authService = mock(AuthService.class);
        queryPort = mock(AuthSessionQueryPort.class);
        wsTicketService = mock(WsTicketService.class);
        controller = new AuthSessionController(authService, queryPort, wsTicketService);

        principal = new SessionPrincipal(
                USER_ID, SESSION_ID, 2L, 3L, AuthRole.STUDENT, RoleStatus.ACTIVE,
                10L, false, false);
        RoleGrant grant = new RoleGrant(
                7L, USER_ID, AuthRole.STUDENT, RoleStatus.ACTIVE, 10L,
                CREATED_AT, CREATED_AT);
        snapshot = new SessionSnapshot(
                SESSION_ID, USER_ID, 2L, 3L, grant, List.of(grant),
                CREATED_AT.plusSeconds(3600), CREATED_AT, CREATED_AT,
                null, null, AuthMethod.PASSWORD, "web", "campus");
        authentication = new UsernamePasswordAuthenticationToken(principal, null);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void currentSessionUsesAuthoritativeIdentityAndNoStore() {
        when(authService.admit(principal)).thenReturn(snapshot);
        when(queryPort.findUserIdentity(USER_ID)).thenReturn(Optional.of(
                new AuthSessionQueryPort.UserIdentity(USER_ID, "Иван Иванов", Map.of(10L, "ИВТ-211"))));

        ResponseEntity<CurrentSessionResponse> response = controller.currentSession(authentication);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getCacheControl()).isEqualTo("no-store");
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().userId()).isEqualTo("42");
        assertThat(response.getBody().displayName()).isEqualTo("Иван Иванов");
        assertThat(response.getBody().groupLabel()).isEqualTo("ИВТ-211");
        assertThat(response.getBody().activeRole()).isEqualTo("STUDENT");
        assertThat(response.getBody().roles()).singleElement()
                .satisfies(role -> {
                    assertThat(role.grantId()).isEqualTo("7");
                    assertThat(role.contextLabel()).isEqualTo("ИВТ-211");
                });
    }

    @Test
    void logoutSupportsCookieOnlyAndBearerOnlyWithDurableServiceDelegation() {
        when(authService.logoutRefreshCookie("refresh-cookie", null))
                .thenReturn(SessionStatePort.RevokeResult.success(revokedSnapshot(), false));

        ResponseEntity<Void> cookieOnly = controller.logout("refresh-cookie", null);
        assertThat(cookieOnly.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(cookieOnly.getHeaders().getFirst(HttpHeaders.SET_COOKIE))
                .contains("rct_refresh=").contains("Max-Age=0");
        assertThat(cookieOnly.getHeaders().getCacheControl()).isEqualTo("no-store");
        verify(authService).logoutRefreshCookie("refresh-cookie", null);
        verify(wsTicketService).invalidateAllFor(USER_ID);

        ResponseEntity<Void> bearerOnly = controller.logout(null, authentication);
        assertThat(bearerOnly.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(bearerOnly.getHeaders().getCacheControl()).isEqualTo("no-store");
        verify(authService).logout(principal);
        verify(wsTicketService, times(2)).invalidateAllFor(USER_ID);
    }

    @Test
    void successfulLogoutAllAndPasswordChangeInvalidateTicketsAfterDurableService() {
        when(authService.logoutAll(principal)).thenReturn(SessionStatePort.RevokeAllResult.success(2));
        ChangePasswordRequest request = new ChangePasswordRequest("old", "NewPassword1!");
        when(authService.changePassword(principal, request))
                .thenReturn(CredentialSessionTransactionPort.ChangePasswordResult.success(1));

        controller.logoutAll(authentication);
        controller.changePassword(request, authentication);

        var order = inOrder(authService, wsTicketService);
        order.verify(authService).logoutAll(principal);
        order.verify(wsTicketService).invalidateAllFor(USER_ID);
        order.verify(authService).changePassword(principal, request);
        order.verify(wsTicketService).invalidateAllFor(USER_ID);
        verify(wsTicketService, times(2)).invalidateAllFor(USER_ID);
    }

    @Test
    void committedChangesKeep204AndClearCookieWhenRedisCleanupFails() throws Exception {
        when(authService.logout(principal))
                .thenReturn(SessionStatePort.RevokeResult.success(revokedSnapshot(), false));
        when(authService.logoutAll(principal)).thenReturn(SessionStatePort.RevokeAllResult.success(2));
        ChangePasswordRequest request = new ChangePasswordRequest("old", "NewPassword1!");
        when(authService.changePassword(principal, request))
                .thenReturn(CredentialSessionTransactionPort.ChangePasswordResult.success(1));
        doThrow(new RedisConnectionFailureException("redis unavailable"))
                .when(wsTicketService).invalidateAllFor(USER_ID);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        mvc.perform(post("/auth/logout").principal(authentication))
                .andExpect(status().isNoContent())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(header().string(HttpHeaders.SET_COOKIE,
                        org.hamcrest.Matchers.containsString("Max-Age=0")));
        mvc.perform(post("/auth/logout-all").principal(authentication))
                .andExpect(status().isNoContent())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(header().string(HttpHeaders.SET_COOKIE,
                        org.hamcrest.Matchers.containsString("Max-Age=0")));
        mvc.perform(post("/auth/change-password")
                        .principal(authentication)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"old\",\"newPassword\":\"NewPassword1!\"}"))
                .andExpect(status().isNoContent())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(header().string(HttpHeaders.SET_COOKIE,
                        org.hamcrest.Matchers.containsString("Max-Age=0")));

        verify(authService).logout(principal);
        verify(authService).logoutAll(principal);
        verify(authService).changePassword(principal, request);
        verify(wsTicketService, times(3)).invalidateAllFor(USER_ID);
    }

    @Test
    void foreignAuthenticatedPrincipalCannotRevokeAnotherSessionCookie() throws Exception {
        SessionPrincipal foreignPrincipal = new SessionPrincipal(
                43L, UUID.fromString("66666666-6666-4666-8666-666666666666"),
                1L, 1L, AuthRole.STUDENT, RoleStatus.ACTIVE, 10L, false, false);
        Authentication foreignAuthentication =
                new UsernamePasswordAuthenticationToken(foreignPrincipal, null);
        doThrow(new AuthSessionException(AuthSessionException.Code.INVALID_SESSION))
                .when(authService).logoutRefreshCookie("other-session-cookie", foreignPrincipal);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        mvc.perform(post("/auth/logout")
                        .cookie(new Cookie("rct_refresh", "other-session-cookie"))
                        .principal(foreignAuthentication))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(jsonPath("$.extras.code").value("INVALID_SESSION"));
    }

    @Test
    void cookieLogoutAuthorityFailureReturns503WithoutClearingCookie() throws Exception {
        doThrow(new AuthSessionException(AuthSessionException.Code.AUTHORITY_UNAVAILABLE))
                .when(authService).logoutRefreshCookie("refresh-cookie", null);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        mvc.perform(post("/auth/logout")
                        .cookie(new Cookie("rct_refresh", "refresh-cookie")))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE))
                .andExpect(jsonPath("$.extras.code").value("AUTHORITY_UNAVAILABLE"));

        verify(authService).logoutRefreshCookie("refresh-cookie", null);
        verifyNoInteractions(wsTicketService);
    }

    @Test
    void bearerLogoutAuthorityFailureDoesNotInvalidateTicketsOrClearCookie() throws Exception {
        doThrow(new AuthSessionException(AuthSessionException.Code.AUTHORITY_UNAVAILABLE))
                .when(authService).logout(principal);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        mvc.perform(post("/auth/logout").principal(authentication))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE))
                .andExpect(jsonPath("$.extras.code").value("AUTHORITY_UNAVAILABLE"));

        verify(authService).logout(principal);
        verify(wsTicketService, never()).invalidateAllFor(anyLong());
    }

    @Test
    void currentPasswordInvalidIsAnHttp400ProblemWithoutCache() throws Exception {
        when(authService.changePassword(any(SessionPrincipal.class), any(ChangePasswordRequest.class)))
                .thenThrow(new AuthSessionException(AuthSessionException.Code.CURRENT_PASSWORD_INVALID));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        SecurityContextHolder.getContext().setAuthentication(authentication);

                mvc.perform(post("/auth/change-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .principal(authentication)
                        .content("{\"currentPassword\":\"old\",\"newPassword\":\"NewPassword1!\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.extras.code").value("CURRENT_PASSWORD_INVALID"));
    }

    @Test
    void publicAuthResponsesAreNoStoreAndTokenResponseKeepsTmaRefreshField() throws Exception {
        AuthController authController = new AuthController(
                authService, mock(OtpService.class), mock(TmaService.class), jwtProperties());
        TokenResponse tokens = new TokenResponse("access-secret", "refresh-secret", 900L);
        when(authService.login(any(LoginRequest.class), any(String.class))).thenReturn(tokens);

        ResponseEntity<TokenResponse> login = authController.login(
                new LoginRequest("student", "password"), new MockHttpServletRequest());

        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(login.getHeaders().getCacheControl()).isEqualTo("no-store");
        assertThat(login.getHeaders().getFirst(HttpHeaders.SET_COOKIE))
                .contains("rct_refresh=refresh-secret");
        JsonNode serialized = new ObjectMapper().readTree(new ObjectMapper().writeValueAsString(tokens));
        assertThat(serialized.path("refreshToken").asText()).isEqualTo("refresh-secret");
        assertThat(tokens.toString()).doesNotContain("access-secret", "refresh-secret");

        when(authService.getPublicKey()).thenReturn(new PublicKeyResponse("pem", "RS256"));
        assertThat(authController.getPublicKey().getHeaders().getCacheControl()).isEqualTo("no-store");

        ChangePasswordRequest password = new ChangePasswordRequest("current-secret", "new-secret");
        assertThat(password.toString())
                .doesNotContain("current-secret", "new-secret")
                .contains("<redacted>");
    }

    private static JwtProperties jwtProperties() {
        JwtProperties properties = mock(JwtProperties.class);
        when(properties.refreshTokenExpiration()).thenReturn(3600L);
        return properties;
    }

    private SessionSnapshot revokedSnapshot() {
        return new SessionSnapshot(
                SESSION_ID, USER_ID, 2L, 3L, snapshot.activeRole(), snapshot.roles(),
                snapshot.refreshExpiresAt(), snapshot.createdAt(), snapshot.lastSeenAt(),
                CREATED_AT.plusSeconds(1), SessionRevokeReason.CURRENT_LOGOUT,
                snapshot.authMethod(), snapshot.clientLabel(), snapshot.locationLabel());
    }
}
