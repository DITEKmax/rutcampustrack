package ru.rutcampustrack.auth.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import ru.rutcampustrack.auth.dto.AccountHistoryPage;
import ru.rutcampustrack.auth.dto.AuthSessionsPage;
import ru.rutcampustrack.auth.dto.ChangePasswordRequest;
import ru.rutcampustrack.auth.dto.CurrentSessionResponse;
import ru.rutcampustrack.auth.dto.SelectActiveRoleRequest;
import ru.rutcampustrack.auth.dto.SelectActiveRoleResponse;

@Tag(name = "Authentication session", description = "Session, role and account security contracts")
@RequestMapping("/auth")
public interface AuthSessionApi {

    String REFRESH_COOKIE_NAME = "rct_refresh";

    @Operation(summary = "Read the current session")
    @ApiResponse(responseCode = "200", description = "Current live session state")
    @GetMapping("/session")
    ResponseEntity<CurrentSessionResponse> currentSession(Authentication authentication);

    @Operation(summary = "Select the active role for the current session")
    @ApiResponse(responseCode = "200", description = "Role selected with a fresh session snapshot")
    @PutMapping("/session/active-role")
    ResponseEntity<SelectActiveRoleResponse> selectActiveRole(
            @Valid @RequestBody SelectActiveRoleRequest request,
            Authentication authentication);

    @Operation(summary = "List the current user's live sessions")
    @ApiResponse(responseCode = "200", description = "Own non-expired sessions")
    @GetMapping("/sessions")
    ResponseEntity<AuthSessionsPage> sessions(
            @RequestParam(name = "cursor", required = false) String cursor,
            @RequestParam(name = "limit", required = false) Integer limit,
            Authentication authentication);

    @Operation(summary = "Revoke the current session")
    @ApiResponse(responseCode = "204", description = "Current session revoked")
    @PostMapping("/logout")
    ResponseEntity<Void> logout(
            @CookieValue(name = REFRESH_COOKIE_NAME, required = false) String refreshCookie,
            Authentication authentication);

    @Operation(summary = "Revoke all sessions for the current user")
    @ApiResponse(responseCode = "204", description = "All sessions revoked")
    @PostMapping("/logout-all")
    ResponseEntity<Void> logoutAll(Authentication authentication);

    @Operation(summary = "Read the current user's account security history")
    @ApiResponse(responseCode = "200", description = "Own account history page")
    @GetMapping("/account-history")
    ResponseEntity<AccountHistoryPage> accountHistory(
            @RequestParam(name = "cursor", required = false) String cursor,
            @RequestParam(name = "limit", required = false) Integer limit,
            Authentication authentication);

    @Operation(summary = "Change the current user's password")
    @ApiResponse(responseCode = "204", description = "Password changed and sessions revoked")
    @PostMapping("/change-password")
    ResponseEntity<Void> changePassword(
            @Valid @RequestBody ChangePasswordRequest request,
            Authentication authentication);
}

