package ru.rutcampustrack.auth.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.RestController;
import ru.rutcampustrack.auth.api.AuthApi;
import ru.rutcampustrack.auth.config.JwtProperties;
import ru.rutcampustrack.auth.dto.ChangePasswordRequest;
import ru.rutcampustrack.auth.dto.LoginRequest;
import ru.rutcampustrack.auth.dto.OtpRequest;
import ru.rutcampustrack.auth.dto.OtpVerifyByCodeRequest;
import ru.rutcampustrack.auth.dto.OtpVerifyRequest;
import ru.rutcampustrack.auth.dto.PublicKeyResponse;
import ru.rutcampustrack.auth.dto.RefreshRequest;
import ru.rutcampustrack.auth.dto.TokenResponse;
import ru.rutcampustrack.auth.dto.TmaAuthRequest;
import ru.rutcampustrack.auth.exception.TokenRefreshException;
import ru.rutcampustrack.auth.security.AuthCookies;
import ru.rutcampustrack.auth.service.AuthService;
import ru.rutcampustrack.auth.service.OtpService;
import ru.rutcampustrack.auth.service.TmaService;
import ru.rutcampustrack.auth.service.WsTicketService;

@RestController
public class AuthController implements AuthApi {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final AuthService authService;
    private final OtpService otpService;
    private final TmaService tmaService;
    private final JwtProperties jwtProperties;
    private final WsTicketService wsTicketService;

    public AuthController(AuthService authService,
                          OtpService otpService,
                          TmaService tmaService,
                          JwtProperties jwtProperties,
                          WsTicketService wsTicketService) {
        this.authService = authService;
        this.otpService = otpService;
        this.tmaService = tmaService;
        this.jwtProperties = jwtProperties;
        this.wsTicketService = wsTicketService;
    }

    @Override
    public ResponseEntity<TokenResponse> login(LoginRequest request, HttpServletRequest httpRequest) {
        return respondWithCookie(authService.login(request, resolveClientIp(httpRequest)));
    }

    private ResponseCookie issueRefreshCookie(String refreshToken) {
        return AuthCookies.issue(refreshToken, jwtProperties.refreshTokenExpiration());
    }

    /**
     * M03a Группа 11: client IP для composite login rate-limit key.
     * X-Forwarded-For (первый IP) имеет приоритет над RemoteAddr — auth-service
     * всегда за Gateway/nginx, RemoteAddr = прокси.
     */
    private static String resolveClientIp(HttpServletRequest req) {
        String xff = req.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            int comma = xff.indexOf(',');
            return (comma > 0 ? xff.substring(0, comma) : xff).trim();
        }
        String remote = req.getRemoteAddr();
        return remote == null ? "unknown" : remote;
    }

    @Override
    public ResponseEntity<TokenResponse> refresh(String refreshCookie) {
        if (refreshCookie == null || refreshCookie.isBlank()) {
            throw new TokenRefreshException("Missing refresh cookie");
        }
        return respondWithCookie(authService.refresh(new RefreshRequest(refreshCookie)));
    }

    @Override
    public ResponseEntity<Void> logout(String refreshCookie, RefreshRequest body, Authentication authentication) {
        // M03b Группа 8: инвалидируем активные ws-ticket'ы пользователя до revoke refresh —
        // если access-JWT доступен (Bearer прошёл JwtAuthenticationFilter). Если чистый
        // cookie-logout без Bearer — пропускаем, ticket'ы истекут сами через 30s.
        // DECISIONS 2026-04-20: event `user.logged-out` отложен в M04 — структурный лог
        // здесь покрывает audit до появления event-infra.
        long userId = -1;
        int revokedTickets = 0;
        if (authentication != null && authentication.isAuthenticated()) {
            try {
                userId = Long.parseLong(authentication.getName());
                revokedTickets = wsTicketService.invalidateAllFor(userId);
            } catch (NumberFormatException ignored) {
                // anonymous / malformed principal — пропускаем
            }
        }
        log.info("auth.logout userId={} revoked_tickets={} cookie_logout={}",
                userId, revokedTickets, refreshCookie != null && !refreshCookie.isBlank());

        // Best-effort: revoke cookie token if present, иначе — body (legacy TMA).
        String token = refreshCookie != null && !refreshCookie.isBlank()
                ? refreshCookie
                : (body != null ? body.refreshToken() : null);
        if (token != null && !token.isBlank()) {
            authService.logout(token);
        }
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, AuthCookies.clear().toString())
                .build();
    }

    @Override
    public ResponseEntity<PublicKeyResponse> getPublicKey() {
        return ResponseEntity.ok(authService.getPublicKey());
    }

    @Override
    public ResponseEntity<Void> requestOtp(OtpRequest request) {
        otpService.requestOtp(request);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<TokenResponse> verifyOtp(OtpVerifyRequest request) {
        return respondWithCookie(otpService.verifyOtp(request));
    }

    @Override
    public ResponseEntity<TokenResponse> verifyOtpByCode(OtpVerifyByCodeRequest request) {
        return respondWithCookie(otpService.verifyOtpByCode(request));
    }

    @Override
    public ResponseEntity<TokenResponse> tmaAuth(TmaAuthRequest request) {
        return respondWithCookie(tmaService.authenticateWithInitData(request));
    }

    private ResponseEntity<TokenResponse> respondWithCookie(TokenResponse tokens) {
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, issueRefreshCookie(tokens.refreshToken()).toString())
                .body(tokens);
    }

    @Override
    public ResponseEntity<Void> changePassword(ChangePasswordRequest request, Authentication authentication) {
        Long userId = Long.parseLong(authentication.getName());
        authService.changePassword(userId, request);
        return ResponseEntity.noContent().build();
    }
}
