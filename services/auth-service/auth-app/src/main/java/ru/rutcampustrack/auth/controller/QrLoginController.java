package ru.rutcampustrack.auth.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.RestController;
import ru.rutcampustrack.auth.api.QrLoginApi;
import ru.rutcampustrack.auth.dto.*;
import ru.rutcampustrack.auth.qr.QrLoginClientIp;
import ru.rutcampustrack.auth.security.AuthCookies;
import ru.rutcampustrack.auth.security.SessionPrincipal;
import ru.rutcampustrack.auth.service.QrLoginService;
import ru.rutcampustrack.auth.session.AuthSessionException;

@RestController
public final class QrLoginController implements QrLoginApi {
    private final QrLoginService service;
    private final QrLoginClientIp clientIp;
    public QrLoginController(QrLoginService service,QrLoginClientIp clientIp) { this.service=service;this.clientIp=clientIp; }
    @Override public ResponseEntity<QrLoginIssueResponse> issue(QrLoginIssueRequest request,HttpServletRequest http) {
        return response(service.issue(request,clientIp.resolve(http),http.getHeader("User-Agent")));
    }
    @Override public ResponseEntity<QrLoginStatusResponse> status(QrLoginProofRequest request,HttpServletRequest http) {
        return response(service.status(request,clientIp.resolve(http)));
    }
    @Override public ResponseEntity<TokenResponse> exchange(QrLoginProofRequest request) {
        TokenResponse tokens=service.exchange(request);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .header(HttpHeaders.SET_COOKIE,AuthCookies.issue(tokens.refreshToken(),service.refreshRemainingSeconds(tokens)).toString())
                .body(tokens);
    }
    @Override public ResponseEntity<QrLoginPreviewResponse> preview(QrLoginApprovalRequest request,Authentication authentication) {
        return response(service.preview(request,principal(authentication)));
    }
    @Override public ResponseEntity<QrLoginStatusResponse> decide(QrLoginDecisionRequest request,Authentication authentication) {
        return response(service.decide(request,principal(authentication)));
    }
    private static SessionPrincipal principal(Authentication authentication) {
        if (authentication==null || !(authentication.getPrincipal() instanceof SessionPrincipal principal))
            throw new AuthSessionException(AuthSessionException.Code.INVALID_SESSION);
        return principal;
    }
    private static <T> ResponseEntity<T> response(T body) { return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body); }
}
