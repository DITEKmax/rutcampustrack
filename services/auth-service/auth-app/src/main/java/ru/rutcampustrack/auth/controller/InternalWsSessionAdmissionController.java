package ru.rutcampustrack.auth.controller;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import ru.rutcampustrack.auth.api.InternalWsSessionAdmissionApi;
import ru.rutcampustrack.auth.dto.WsSessionAdmissionRequest;
import ru.rutcampustrack.auth.security.SessionPrincipal;
import ru.rutcampustrack.auth.service.AuthService;
import ru.rutcampustrack.auth.session.AuthSessionException;
import ru.rutcampustrack.auth.session.model.AuthRole;
import ru.rutcampustrack.auth.session.model.RoleStatus;

import java.util.Objects;
import java.util.UUID;

/** Rechecks one bound WebSocket identity using the same authority as public session operations. */
@RestController
public final class InternalWsSessionAdmissionController implements InternalWsSessionAdmissionApi {

    private final AuthService authService;

    public InternalWsSessionAdmissionController(AuthService authService) {
        this.authService = Objects.requireNonNull(authService, "authService");
    }

    @Override
    public ResponseEntity<Void> admitWsSession(WsSessionAdmissionRequest request) {
        SessionPrincipal principal;
        try {
            principal = new SessionPrincipal(request.userId(), UUID.fromString(request.sessionId()),
                    request.sessionVersion(), request.rolesVersion(), AuthRole.valueOf(request.role()),
                    RoleStatus.valueOf(request.status()), request.groupId(), request.isHeadman(),
                    request.readOnly());
        } catch (RuntimeException exception) {
            return denied();
        }
        try {
            authService.admit(principal);
            return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
        } catch (AuthSessionException exception) {
            if (exception.code() == AuthSessionException.Code.AUTHORITY_UNAVAILABLE) {
                throw exception;
            }
            return denied();
        }
    }

    private static ResponseEntity<Void> denied() {
        return ResponseEntity.status(401).cacheControl(CacheControl.noStore()).build();
    }
}
