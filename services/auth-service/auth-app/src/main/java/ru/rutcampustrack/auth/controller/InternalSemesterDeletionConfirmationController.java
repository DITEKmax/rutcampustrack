package ru.rutcampustrack.auth.controller;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import ru.rutcampustrack.auth.api.InternalSemesterDeletionConfirmationApi;
import ru.rutcampustrack.auth.dto.ConfirmSemesterDeletionRequest;
import ru.rutcampustrack.auth.service.SemesterDeletionConfirmationService;

import java.util.Objects;

@RestController
public final class InternalSemesterDeletionConfirmationController
        implements InternalSemesterDeletionConfirmationApi {

    private final SemesterDeletionConfirmationService confirmationService;

    public InternalSemesterDeletionConfirmationController(
            SemesterDeletionConfirmationService confirmationService) {
        this.confirmationService = Objects.requireNonNull(confirmationService, "confirmationService");
    }

    @Override
    public ResponseEntity<Void> confirmSemesterDeletion(ConfirmSemesterDeletionRequest request) {
        confirmationService.confirm(request);
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }
}
