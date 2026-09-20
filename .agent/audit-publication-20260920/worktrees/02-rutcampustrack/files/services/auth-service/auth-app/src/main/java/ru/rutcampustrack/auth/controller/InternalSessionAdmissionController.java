package ru.rutcampustrack.auth.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.http.CacheControl;
import org.springframework.web.bind.annotation.RestController;
import ru.rutcampustrack.auth.api.InternalSessionAdmissionApi;
import ru.rutcampustrack.auth.dto.AuthAdmissionRequest;
import ru.rutcampustrack.auth.dto.AuthAdmissionResponse;
import ru.rutcampustrack.auth.session.SessionAdmissionService;

import java.util.Objects;

/** Service-authenticated exchange of a session-bound access token. */
@RestController
public final class InternalSessionAdmissionController implements InternalSessionAdmissionApi {

    private final SessionAdmissionService sessionAdmissionService;

    public InternalSessionAdmissionController(SessionAdmissionService sessionAdmissionService) {
        this.sessionAdmissionService = Objects.requireNonNull(sessionAdmissionService, "sessionAdmissionService");
    }

    @Override
    public ResponseEntity<AuthAdmissionResponse> admit(AuthAdmissionRequest request) {
        Objects.requireNonNull(request, "request");
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(sessionAdmissionService.admit(request.accessToken()));
    }
}
