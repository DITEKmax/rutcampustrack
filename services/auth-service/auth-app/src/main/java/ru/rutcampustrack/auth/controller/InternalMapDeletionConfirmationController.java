package ru.rutcampustrack.auth.controller;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import ru.rutcampustrack.auth.api.InternalMapDeletionConfirmationApi;
import ru.rutcampustrack.auth.dto.ConfirmMapDeletionRequest;
import ru.rutcampustrack.auth.service.SemesterDeletionConfirmationService;

import java.util.Objects;

@RestController
public final class InternalMapDeletionConfirmationController implements InternalMapDeletionConfirmationApi {
    private final SemesterDeletionConfirmationService confirmationService;

    public InternalMapDeletionConfirmationController(SemesterDeletionConfirmationService confirmationService) {
        this.confirmationService = Objects.requireNonNull(confirmationService, "confirmationService");
    }

    @Override
    public ResponseEntity<Void> confirmMapDeletion(ConfirmMapDeletionRequest request) {
        confirmationService.confirmMapDeletion(request);
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }
}
