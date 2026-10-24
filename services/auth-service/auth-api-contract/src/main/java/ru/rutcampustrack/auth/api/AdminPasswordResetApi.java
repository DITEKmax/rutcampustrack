package ru.rutcampustrack.auth.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import ru.rutcampustrack.auth.dto.AdminCredentialView;
import ru.rutcampustrack.auth.dto.AdminGeneratedPasswordRequest;
import ru.rutcampustrack.auth.dto.AdminPasswordResetLinkResponse;
import ru.rutcampustrack.shared.web.api.exception.ErrorResponse;

@Tag(name = "Administrator password recovery")
@RequestMapping("/auth/admin/users")
public interface AdminPasswordResetApi {
    @Operation(summary = "Issue a one-use recovery link under a current active ADMIN session")
    @PostMapping("/{userId}/password-reset-link")
    ResponseEntity<AdminPasswordResetLinkResponse> issueLink(
            @PathVariable("userId") long userId, Authentication authentication);

    @Operation(
            summary =
                    "Read the current permitted credential and CAS version; audit direct disclosure")
    @ApiResponse(
            responseCode = "503",
            description = "AUTHORITY_UNAVAILABLE or ADMIN_AUTHORITY_UNAVAILABLE",
            content =
                    @Content(
                            mediaType = "application/problem+json",
                            schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(
            responseCode = "200",
            description = "Current permitted credential and CAS version",
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = AdminCredentialView.class)))
    @GetMapping("/{userId}/initial-password")
    ResponseEntity<AdminCredentialView> readCredential(
            @PathVariable("userId") long userId, Authentication authentication);

    @Operation(
            summary =
                    "Generate a version-checked password and revoke old credentials; read back after unknown outcome")
    @ApiResponse(
            responseCode = "409",
            description = "CREDENTIAL_VERSION_CONFLICT or CREDENTIAL_TARGET_UNAVAILABLE",
            content =
                    @Content(
                            mediaType = "application/problem+json",
                            schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(
            responseCode = "503",
            description =
                    "AUTHORITY_UNAVAILABLE or ADMIN_AUTHORITY_UNAVAILABLE; do not blindly regenerate",
            content =
                    @Content(
                            mediaType = "application/problem+json",
                            schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(
            responseCode = "200",
            description = "Committed generated password and new credential version",
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = AdminCredentialView.class)))
    @PostMapping("/{userId}/generated-password")
    ResponseEntity<AdminCredentialView> generatePassword(
            @PathVariable("userId") long userId,
            @Valid @RequestBody AdminGeneratedPasswordRequest request,
            Authentication authentication);
}
