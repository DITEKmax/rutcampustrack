package ru.rutcampustrack.auth.controller;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import ru.rutcampustrack.auth.api.InternalUserArchiveConfirmationApi;
import ru.rutcampustrack.auth.dto.ConfirmUserArchiveRequest;
import ru.rutcampustrack.auth.service.SemesterDeletionConfirmationService;

@RestController
public final class InternalUserArchiveConfirmationController implements InternalUserArchiveConfirmationApi {
    private final SemesterDeletionConfirmationService service;
    public InternalUserArchiveConfirmationController(SemesterDeletionConfirmationService service) {
        this.service = service;
    }
    @Override public ResponseEntity<Void> confirmUserArchive(ConfirmUserArchiveRequest request) {
        service.confirmUserArchive(request);
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }
}
