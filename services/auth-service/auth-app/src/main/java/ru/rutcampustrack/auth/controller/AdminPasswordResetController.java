package ru.rutcampustrack.auth.controller;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.RestController;
import ru.rutcampustrack.auth.api.AdminPasswordResetApi;
import ru.rutcampustrack.auth.dto.AdminPasswordResetLinkResponse;
import ru.rutcampustrack.auth.security.SessionPrincipal;
import ru.rutcampustrack.auth.service.AdminPasswordResetService;
import ru.rutcampustrack.auth.session.AuthSessionException;

@RestController
public final class AdminPasswordResetController implements AdminPasswordResetApi {
    private final AdminPasswordResetService service;

    public AdminPasswordResetController(AdminPasswordResetService service) {
        this.service = service;
    }

    @Override
    public ResponseEntity<AdminPasswordResetLinkResponse> issueLink(long userId, Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof SessionPrincipal principal)) {
            throw new AuthSessionException(AuthSessionException.Code.INVALID_SESSION);
        }
        return ResponseEntity.status(201).cacheControl(CacheControl.noStore())
                .body(service.issueLink(principal, userId));
    }
}
