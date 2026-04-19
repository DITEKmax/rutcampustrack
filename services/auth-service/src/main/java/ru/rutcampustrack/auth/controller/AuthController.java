package ru.rutcampustrack.auth.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import ru.rutcampustrack.auth.config.JwtProperties;
import ru.rutcampustrack.auth.dto.ChangePasswordRequest;
import ru.rutcampustrack.auth.dto.LoginRequest;
import ru.rutcampustrack.auth.dto.OtpCodeResponse;
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
@RequestMapping("/auth")
@Tag(name = "Authentication", description = "JWT authentication endpoints")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    /**
     * Жёсткая дата deprecation'а для /auth/refresh-body. Установлена через
     * M04/M05 — при removal обновить. Формат — RFC 7231 HTTP-date.
     */
    private static final String REFRESH_BODY_SUNSET = "Mon, 01 Jun 2026 00:00:00 GMT";

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

    @Operation(summary = "Login with credentials", description = "Authenticate with login and password, returns JWT token pair + refresh cookie")
    @ApiResponse(responseCode = "200", description = "Successfully authenticated")
    @ApiResponse(responseCode = "401", description = "Invalid credentials")
    @PostMapping("/login")
    public ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest request,
                                               HttpServletRequest httpRequest) {
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

    @Operation(summary = "Refresh access token",
               description = "Read refresh token from HttpOnly cookie 'rct_refresh', rotate it, return new access in body + new cookie.")
    @ApiResponse(responseCode = "200", description = "Tokens refreshed successfully")
    @ApiResponse(responseCode = "401", description = "Missing, invalid or expired refresh cookie")
    @PostMapping("/refresh")
    public ResponseEntity<TokenResponse> refresh(
            @CookieValue(name = AuthCookies.REFRESH_COOKIE_NAME, required = false) String refreshCookie) {
        if (refreshCookie == null || refreshCookie.isBlank()) {
            throw new TokenRefreshException("Missing refresh cookie");
        }
        return respondWithCookie(authService.refresh(new RefreshRequest(refreshCookie)));
    }

    @Operation(summary = "Logout", description = "Invalidate refresh token, ws-tickets, and clear refresh cookie")
    @ApiResponse(responseCode = "204", description = "Successfully logged out")
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @CookieValue(name = AuthCookies.REFRESH_COOKIE_NAME, required = false) String refreshCookie,
            @RequestBody(required = false) RefreshRequest body,
            Authentication authentication) {
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

    @Operation(summary = "Get RSA public key", description = "Returns RSA public key in PEM format for JWT verification")
    @ApiResponse(responseCode = "200", description = "Public key retrieved")
    @GetMapping("/public-key")
    public ResponseEntity<PublicKeyResponse> getPublicKey() {
        return ResponseEntity.ok(authService.getPublicKey());
    }

    @Operation(summary = "Request OTP code", description = "Generate OTP code for Telegram-based authentication and return it in response body for bot delivery")
    @ApiResponse(responseCode = "200", description = "OTP code generated and returned")
    @ApiResponse(responseCode = "429", description = "Rate limited — too many requests")
    @PostMapping("/otp/request")
    public ResponseEntity<OtpCodeResponse> requestOtp(@Valid @RequestBody OtpRequest request) {
        String code = otpService.requestOtp(request);
        return ResponseEntity.ok(new OtpCodeResponse(code));
    }

    @Operation(summary = "Verify OTP code", description = "Verify OTP code and receive JWT token pair + refresh cookie")
    @ApiResponse(responseCode = "200", description = "OTP verified, JWT pair returned")
    @ApiResponse(responseCode = "401", description = "Invalid or expired OTP code")
    @PostMapping("/otp/verify")
    public ResponseEntity<TokenResponse> verifyOtp(@Valid @RequestBody OtpVerifyRequest request) {
        return respondWithCookie(otpService.verifyOtp(request));
    }

    @Operation(summary = "Verify OTP code without telegram ID",
               description = "Verify OTP code by reverse lookup (code → telegramId). Used by web-panel "
                       + "where the user only enters the 6-digit code received in Telegram bot.")
    @ApiResponse(responseCode = "200", description = "OTP verified, JWT pair returned")
    @ApiResponse(responseCode = "401", description = "Invalid or expired OTP code")
    @PostMapping("/otp/verify-by-code")
    public ResponseEntity<TokenResponse> verifyOtpByCode(@Valid @RequestBody OtpVerifyByCodeRequest request) {
        return respondWithCookie(otpService.verifyOtpByCode(request));
    }

    @Operation(summary = "Authenticate via Telegram Mini App",
               description = "Validate Telegram initData (HMAC-SHA256) and return JWT token pair. "
                       + "Cookie also set for web-panel fallback; TMA clients read refreshToken from body.")
    @ApiResponse(responseCode = "200", description = "Successfully authenticated via TMA")
    @ApiResponse(responseCode = "401", description = "Invalid or tampered initData, or user not linked")
    @PostMapping("/tma")
    public ResponseEntity<TokenResponse> tmaAuth(@Valid @RequestBody TmaAuthRequest request) {
        return respondWithCookie(tmaService.authenticateWithInitData(request));
    }

    private ResponseEntity<TokenResponse> respondWithCookie(TokenResponse tokens) {
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, issueRefreshCookie(tokens.refreshToken()).toString())
                .body(tokens);
    }

    @Operation(summary = "Refresh tokens (body-based, deprecated)",
               description = "Exchange refresh token in request body for new token pair. "
                       + "DEPRECATED in M03b — use cookie-based POST /auth/refresh instead. "
                       + "Planned removal: M04/M05.")
    @ApiResponse(responseCode = "200", description = "Tokens refreshed successfully")
    @ApiResponse(responseCode = "401", description = "Invalid or expired refresh token")
    @PostMapping("/refresh-body")
    public ResponseEntity<TokenResponse> refreshBody(@Valid @RequestBody RefreshRequest request) {
        return ResponseEntity.ok()
                .header("Deprecation", "true")
                .header("Sunset", REFRESH_BODY_SUNSET)
                .body(authService.refresh(request));
    }

    @Operation(summary = "Change password", description = "Change password for authenticated user")
    @ApiResponse(responseCode = "200", description = "Password changed successfully")
    @ApiResponse(responseCode = "401", description = "Current password is incorrect")
    @PostMapping("/change-password")
    public ResponseEntity<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request,
                                               Authentication authentication) {
        Long userId = Long.parseLong(authentication.getName());
        authService.changePassword(userId, request);
        return ResponseEntity.ok().build();
    }
}
