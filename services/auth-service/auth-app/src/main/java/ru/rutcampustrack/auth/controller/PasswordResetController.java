package ru.rutcampustrack.auth.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import ru.rutcampustrack.auth.api.PasswordResetApi;
import ru.rutcampustrack.auth.dto.PasswordResetCompleteRequest;
import ru.rutcampustrack.auth.dto.PasswordResetRequest;
import ru.rutcampustrack.auth.dto.PasswordResetRequestResponse;
import ru.rutcampustrack.auth.dto.PasswordResetVerifyRequest;
import ru.rutcampustrack.auth.dto.PasswordResetVerifyResponse;
import ru.rutcampustrack.auth.service.PasswordResetService;

import java.util.Objects;

@RestController
public final class PasswordResetController implements PasswordResetApi {

    private final PasswordResetService passwordResetService;

    public PasswordResetController(PasswordResetService passwordResetService) {
        this.passwordResetService = Objects.requireNonNull(passwordResetService, "passwordResetService");
    }

    @Override
    public ResponseEntity<PasswordResetRequestResponse> request(PasswordResetRequest request) {
        return ResponseEntity.accepted()
                .body(passwordResetService.request(request));
    }

    @Override
    public ResponseEntity<PasswordResetVerifyResponse> verify(PasswordResetVerifyRequest request) {
        return ResponseEntity.ok()
                .body(passwordResetService.verify(request));
    }

    @Override
    public ResponseEntity<Void> complete(PasswordResetCompleteRequest request) {
        passwordResetService.complete(request.resetTicket(), request.newPassword());
        return ResponseEntity.noContent()
                .build();
    }
}
