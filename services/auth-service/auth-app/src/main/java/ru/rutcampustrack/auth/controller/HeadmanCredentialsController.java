package ru.rutcampustrack.auth.controller;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.RestController;

import ru.rutcampustrack.auth.api.HeadmanCredentialsApi;
import ru.rutcampustrack.auth.dto.AdminPasswordResetLinkResponse;
import ru.rutcampustrack.auth.dto.HeadmanCredentialView;
import ru.rutcampustrack.auth.dto.HeadmanGeneratedPasswordRequest;
import ru.rutcampustrack.auth.security.SessionPrincipal;
import ru.rutcampustrack.auth.service.HeadmanCredentialsService;
import ru.rutcampustrack.auth.session.AuthSessionException;

@RestController
public final class HeadmanCredentialsController implements HeadmanCredentialsApi {
    private final HeadmanCredentialsService service;

    public HeadmanCredentialsController(HeadmanCredentialsService service) {
        this.service = service;
    }

    @Override
    public ResponseEntity<HeadmanCredentialView> read(
            long studentId, Authentication authentication) {
        return response(service.read(principal(authentication), studentId));
    }

    @Override
    public ResponseEntity<HeadmanCredentialView> generate(
            long studentId,
            HeadmanGeneratedPasswordRequest request,
            Authentication authentication) {
        return response(
                service.generate(
                        principal(authentication),
                        studentId,
                        request.expectedCredentialsVersion()));
    }

    @Override
    public ResponseEntity<AdminPasswordResetLinkResponse> issueLink(
            long studentId, Authentication authentication) {
        return response(service.issueLink(principal(authentication), studentId));
    }

    private static <T> ResponseEntity<T> response(T value) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(value);
    }

    private static SessionPrincipal principal(Authentication authentication) {
        if (authentication == null
                || !(authentication.getPrincipal() instanceof SessionPrincipal principal)) {
            throw new AuthSessionException(AuthSessionException.Code.INVALID_SESSION);
        }
        return principal;
    }
}
