package ru.rutcampustrack.auth.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import ru.rutcampustrack.auth.config.JwtProperties;
import ru.rutcampustrack.auth.dto.AccessTokenResponse;
import ru.rutcampustrack.auth.dto.ChangePasswordRequest;
import ru.rutcampustrack.auth.dto.LoginRequest;
import ru.rutcampustrack.auth.dto.OtpCodeResponse;
import ru.rutcampustrack.auth.dto.OtpRequest;
import ru.rutcampustrack.auth.dto.OtpVerifyRequest;
import ru.rutcampustrack.auth.dto.PublicKeyResponse;
import ru.rutcampustrack.auth.dto.TokenPair;
import ru.rutcampustrack.auth.dto.TokenResponse;
import ru.rutcampustrack.auth.service.AuthService;
import ru.rutcampustrack.auth.service.OtpService;

import java.time.Duration;

@RestController
@RequestMapping("/auth")
@Tag(name = "Authentication", description = "JWT authentication endpoints")
public class AuthController {

    private final AuthService authService;
    private final OtpService otpService;
    private final JwtProperties jwtProperties;

    public AuthController(AuthService authService, OtpService otpService, JwtProperties jwtProperties) {
        this.authService = authService;
        this.otpService = otpService;
        this.jwtProperties = jwtProperties;
    }

    @Operation(summary = "Login with credentials", description = "Authenticate with login and password, returns access token in body and refresh token as httpOnly cookie")
    @ApiResponse(responseCode = "200", description = "Successfully authenticated")
    @ApiResponse(responseCode = "401", description = "Invalid credentials")
    @PostMapping("/login")
    public ResponseEntity<AccessTokenResponse> login(@Valid @RequestBody LoginRequest request,
                                                     HttpServletResponse response) {
        TokenPair tokens = authService.login(request);
        addRefreshCookie(response, tokens.refreshToken());
        return ResponseEntity.ok(new AccessTokenResponse(tokens.accessToken(), tokens.expiresIn()));
    }

    @Operation(summary = "Refresh access token", description = "Exchange refresh token cookie for new access token and rotated refresh cookie")
    @ApiResponse(responseCode = "200", description = "Tokens refreshed successfully")
    @ApiResponse(responseCode = "401", description = "Invalid or expired refresh token")
    @PostMapping("/refresh")
    public ResponseEntity<AccessTokenResponse> refresh(
            @CookieValue(name = "refresh_token") String refreshToken,
            HttpServletResponse response) {
        TokenPair tokens = authService.refresh(refreshToken);
        addRefreshCookie(response, tokens.refreshToken());
        return ResponseEntity.ok(new AccessTokenResponse(tokens.accessToken(), tokens.expiresIn()));
    }

    @Operation(summary = "Logout", description = "Invalidate refresh token and clear cookie")
    @ApiResponse(responseCode = "204", description = "Successfully logged out")
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @CookieValue(name = "refresh_token", required = false) String refreshToken,
            HttpServletResponse response) {
        if (refreshToken != null) {
            authService.logout(refreshToken);
        }
        clearRefreshCookie(response);
        return ResponseEntity.noContent().build();
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

    @Operation(summary = "Verify OTP code", description = "Verify OTP code and receive JWT token pair (refresh token in body for bot clients)")
    @ApiResponse(responseCode = "200", description = "OTP verified, JWT pair returned")
    @ApiResponse(responseCode = "401", description = "Invalid or expired OTP code")
    @PostMapping("/otp/verify")
    public ResponseEntity<TokenResponse> verifyOtp(@Valid @RequestBody OtpVerifyRequest request) {
        return ResponseEntity.ok(otpService.verifyOtp(request));
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

    private void addRefreshCookie(HttpServletResponse response, String refreshToken) {
        ResponseCookie cookie = ResponseCookie.from("refresh_token", refreshToken)
                .httpOnly(true)
                .secure(true)
                .sameSite("Strict")
                .path("/api/auth")
                .maxAge(Duration.ofSeconds(jwtProperties.refreshTokenExpiration()))
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private void clearRefreshCookie(HttpServletResponse response) {
        ResponseCookie cookie = ResponseCookie.from("refresh_token", "")
                .httpOnly(true)
                .secure(true)
                .sameSite("Strict")
                .path("/api/auth")
                .maxAge(0)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }
}
