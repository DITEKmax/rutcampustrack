package ru.rutcampustrack.auth.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import ru.rutcampustrack.auth.dto.AdminPasswordResetLinkResponse;

@Tag(name = "Administrator password recovery")
@RequestMapping("/auth/admin/users")
public interface AdminPasswordResetApi {
    @Operation(summary = "Issue a one-use recovery link under a current active ADMIN session")
    @PostMapping("/{userId}/password-reset-link")
    ResponseEntity<AdminPasswordResetLinkResponse> issueLink(
            @PathVariable("userId") long userId, Authentication authentication);
}
