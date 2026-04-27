package ru.rutcampustrack.auth.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import ru.rutcampustrack.auth.dto.ChangePasswordRequest;
import ru.rutcampustrack.auth.dto.LoginRequest;
import ru.rutcampustrack.auth.dto.OtpRequest;
import ru.rutcampustrack.auth.dto.OtpVerifyByCodeRequest;
import ru.rutcampustrack.auth.dto.OtpVerifyRequest;
import ru.rutcampustrack.auth.dto.PublicKeyResponse;
import ru.rutcampustrack.auth.dto.RefreshRequest;
import ru.rutcampustrack.auth.dto.TmaAuthRequest;
import ru.rutcampustrack.auth.dto.TokenResponse;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * M12 G3 — REST API contract для auth-service public endpoints.
 *
 * Все mappings (path/verb) — только в этом interface. Controller лишь
 * {@code implements AuthApi} и реализует бизнес-логику.
 */
@Tag(name = "Authentication", description = "JWT authentication endpoints")
@RequestMapping("/auth")
public interface AuthApi {

    /** Имя cookie для refresh-token (см. {@code AuthCookies.REFRESH_COOKIE_NAME}). */
    String REFRESH_COOKIE_NAME = "rct_refresh";

    @Operation(summary = "Login with credentials",
            description = "Authenticate with login and password, returns JWT token pair + refresh cookie")
    @ApiResponse(responseCode = "200", description = "Successfully authenticated")
    @ApiResponse(responseCode = "401", description = "Invalid credentials")
    @PostMapping("/login")
    ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest request,
                                        HttpServletRequest httpRequest);

    @Operation(summary = "Refresh access token",
            description = "Read refresh token from HttpOnly cookie 'rct_refresh', rotate it, "
                    + "return new access in body + new cookie.")
    @ApiResponse(responseCode = "200", description = "Tokens refreshed successfully")
    @ApiResponse(responseCode = "401", description = "Missing, invalid or expired refresh cookie")
    @PostMapping("/refresh")
    ResponseEntity<TokenResponse> refresh(
            @CookieValue(name = REFRESH_COOKIE_NAME, required = false) String refreshCookie);

    @Operation(summary = "Logout", description = "Invalidate refresh token, ws-tickets, and clear refresh cookie")
    @ApiResponse(responseCode = "204", description = "Successfully logged out")
    @PostMapping("/logout")
    ResponseEntity<Void> logout(
            @CookieValue(name = REFRESH_COOKIE_NAME, required = false) String refreshCookie,
            @RequestBody(required = false) RefreshRequest body,
            Authentication authentication);

    @Operation(summary = "Get RSA public key",
            description = "Returns RSA public key in PEM format for JWT verification")
    @ApiResponse(responseCode = "200", description = "Public key retrieved")
    @GetMapping("/public-key")
    ResponseEntity<PublicKeyResponse> getPublicKey();

    @Operation(summary = "Request OTP code",
            description = "Generate OTP code for Telegram-based authentication. "
                    + "M09 G2 (08 P0-2): код отправляется пользователю через "
                    + "notification-bot (RabbitMQ event otp.requested), НЕ возвращается в HTTP body.")
    @ApiResponse(responseCode = "204", description = "OTP code generated and dispatched to Telegram bot")
    @ApiResponse(responseCode = "401", description = "Unknown telegram_id or inactive account")
    @ApiResponse(responseCode = "429", description = "Rate limited — too many requests")
    @PostMapping("/otp/request")
    ResponseEntity<Void> requestOtp(@Valid @RequestBody OtpRequest request);

    @Operation(summary = "Verify OTP code",
            description = "Verify OTP code and receive JWT token pair + refresh cookie")
    @ApiResponse(responseCode = "200", description = "OTP verified, JWT pair returned")
    @ApiResponse(responseCode = "401", description = "Invalid or expired OTP code")
    @PostMapping("/otp/verify")
    ResponseEntity<TokenResponse> verifyOtp(@Valid @RequestBody OtpVerifyRequest request);

    @Operation(summary = "Verify OTP code without telegram ID",
            description = "Verify OTP code by reverse lookup (code → telegramId). Used by web-panel "
                    + "where the user only enters the 6-digit code received in Telegram bot. "
                    + "M16 G3: brute-force защита по IP — 20 mismatch'ей за 5 мин → 429.")
    @ApiResponse(responseCode = "200", description = "OTP verified, JWT pair returned")
    @ApiResponse(responseCode = "401", description = "Invalid or expired OTP code")
    @ApiResponse(responseCode = "429", description = "Too many verification attempts from this IP")
    @PostMapping("/otp/verify-by-code")
    ResponseEntity<TokenResponse> verifyOtpByCode(@Valid @RequestBody OtpVerifyByCodeRequest request,
                                                  HttpServletRequest httpRequest);

    @Operation(summary = "Authenticate via Telegram Mini App",
            description = "Validate Telegram initData (HMAC-SHA256) and return JWT token pair. "
                    + "Cookie also set for web-panel fallback; TMA clients read refreshToken from body.")
    @ApiResponse(responseCode = "200", description = "Successfully authenticated via TMA")
    @ApiResponse(responseCode = "401", description = "Invalid or tampered initData, or user not linked")
    @PostMapping("/tma")
    ResponseEntity<TokenResponse> tmaAuth(@Valid @RequestBody TmaAuthRequest request);

    @Operation(summary = "Change password", description = "Change password for authenticated user")
    @ApiResponse(responseCode = "204", description = "Password changed successfully")
    @ApiResponse(responseCode = "401", description = "Current password is incorrect")
    @PostMapping("/change-password")
    ResponseEntity<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request,
                                        Authentication authentication);
}
