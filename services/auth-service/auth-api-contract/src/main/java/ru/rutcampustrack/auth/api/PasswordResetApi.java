package ru.rutcampustrack.auth.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import ru.rutcampustrack.auth.dto.PasswordResetCompleteRequest;
import ru.rutcampustrack.auth.dto.PasswordResetRequest;
import ru.rutcampustrack.auth.dto.PasswordResetRequestResponse;
import ru.rutcampustrack.auth.dto.PasswordResetVerifyRequest;
import ru.rutcampustrack.auth.dto.PasswordResetVerifyResponse;

/** Public password-reset contract. None of these operations issues a login session. */
@Tag(name = "Password recovery", description = "Telegram OTP-based password reset")
@RequestMapping("/auth/password-reset")
public interface PasswordResetApi {

    @Operation(summary = "Request a password-reset OTP through Telegram")
    @ApiResponse(responseCode = "202", description = "Accepts exactly one of login or telegramId without disclosing account status")
    @ApiResponse(responseCode = "429", description = "Request rate limited")
    @PostMapping("/request")
    ResponseEntity<PasswordResetRequestResponse> request(@Valid @RequestBody PasswordResetRequest request);

    @Operation(summary = "Verify a password-reset OTP without a login session")
    @ApiResponse(responseCode = "200", description = "OTP accepted; returns only a reset ticket")
    @ApiResponse(responseCode = "400", description = "OTP is incorrect")
    @ApiResponse(responseCode = "410", description = "OTP has expired or was consumed")
    @ApiResponse(responseCode = "429", description = "Account attempt window is exhausted")
    @PostMapping("/verify")
    ResponseEntity<PasswordResetVerifyResponse> verify(@Valid @RequestBody PasswordResetVerifyRequest request);

    @Operation(summary = "Set a new password with a verified one-use reset ticket")
    @ApiResponse(responseCode = "204", description = "Password updated and all previous sessions revoked")
    @ApiResponse(responseCode = "400", description = "New password does not meet the password policy")
    @ApiResponse(responseCode = "410", description = "Reset ticket is invalid, expired, or already used")
    @PostMapping("/complete")
    ResponseEntity<Void> complete(@Valid @RequestBody PasswordResetCompleteRequest request);
}
