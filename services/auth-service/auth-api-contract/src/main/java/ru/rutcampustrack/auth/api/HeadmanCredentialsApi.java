package ru.rutcampustrack.auth.api;

import io.swagger.v3.oas.annotations.Operation;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import ru.rutcampustrack.auth.dto.AdminPasswordResetLinkResponse;
import ru.rutcampustrack.auth.dto.HeadmanCredentialView;
import ru.rutcampustrack.auth.dto.HeadmanGeneratedPasswordRequest;

@RequestMapping("/auth/headman/students")
public interface HeadmanCredentialsApi {
    @Operation(
            operationId = "readHeadmanMemberCredential",
            summary = "Read a current own-group generated credential")
    @GetMapping("/{studentId}/initial-password")
    ResponseEntity<HeadmanCredentialView> read(
            @PathVariable("studentId") long studentId, Authentication authentication);

    @Operation(
            operationId = "generateHeadmanMemberPassword",
            summary = "Generate a version-checked own-group password")
    @PostMapping("/{studentId}/generated-password")
    ResponseEntity<HeadmanCredentialView> generate(
            @PathVariable("studentId") long studentId,
            @Valid @RequestBody HeadmanGeneratedPasswordRequest request,
            Authentication authentication);

    @Operation(
            operationId = "issueHeadmanMemberResetLink",
            summary = "Issue an own-group one-use recovery link")
    @PostMapping("/{studentId}/password-reset-link")
    ResponseEntity<AdminPasswordResetLinkResponse> issueLink(
            @PathVariable("studentId") long studentId, Authentication authentication);
}
