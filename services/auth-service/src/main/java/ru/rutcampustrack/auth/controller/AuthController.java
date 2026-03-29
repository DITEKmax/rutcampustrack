package ru.rutcampustrack.auth.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import ru.rutcampustrack.auth.dto.ChangePasswordRequest;
import ru.rutcampustrack.auth.dto.LoginRequest;
import ru.rutcampustrack.auth.dto.OtpRequest;
import ru.rutcampustrack.auth.dto.OtpVerifyRequest;
import ru.rutcampustrack.auth.dto.PublicKeyResponse;
import ru.rutcampustrack.auth.dto.RefreshRequest;
import ru.rutcampustrack.auth.dto.TokenResponse;
import ru.rutcampustrack.auth.service.AuthService;
import ru.rutcampustrack.auth.service.OtpService;

@RestController
@RequestMapping("/auth")
@Tag(name = "Authentication", description = "JWT authentication endpoints")
public class AuthController {

    private final AuthService authService;
    private final OtpService otpService;

    public AuthController(AuthService authService, OtpService otpService) {
        this.authService = authService;
        this.otpService = otpService;
    }

    @Operation(summary = "Login with credentials", description = "Authenticate with login and password, returns JWT token pair")
    @ApiResponse(responseCode = "200", description = "Successfully authenticated")
    @ApiResponse(responseCode = "401", description = "Invalid credentials")
    @PostMapping("/login")
    public ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @Operation(summary = "Refresh access token", description = "Exchange refresh token for new token pair (rotation)")
    @ApiResponse(responseCode = "200", description = "Tokens refreshed successfully")
    @ApiResponse(responseCode = "401", description = "Invalid or expired refresh token")
    @PostMapping("/refresh")
    public ResponseEntity<TokenResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        return ResponseEntity.ok(authService.refresh(request));
    }

    @Operation(summary = "Logout", description = "Invalidate refresh token")
    @ApiResponse(responseCode = "204", description = "Successfully logged out")
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@Valid @RequestBody RefreshRequest request) {
        authService.logout(request.refreshToken());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Get RSA public key", description = "Returns RSA public key in PEM format for JWT verification")
    @ApiResponse(responseCode = "200", description = "Public key retrieved")
    @GetMapping("/public-key")
    public ResponseEntity<PublicKeyResponse> getPublicKey() {
        return ResponseEntity.ok(authService.getPublicKey());
    }

    @Operation(summary = "Request OTP code", description = "Generate OTP code for Telegram-based authentication")
    @ApiResponse(responseCode = "200", description = "OTP code generated and stored")
    @ApiResponse(responseCode = "429", description = "Rate limited — too many requests")
    @PostMapping("/otp/request")
    public ResponseEntity<Void> requestOtp(@Valid @RequestBody OtpRequest request) {
        otpService.requestOtp(request);
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "Verify OTP code", description = "Verify OTP code and receive JWT token pair")
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
}
