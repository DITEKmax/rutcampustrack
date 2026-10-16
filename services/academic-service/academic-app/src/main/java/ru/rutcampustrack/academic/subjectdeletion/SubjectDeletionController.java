package ru.rutcampustrack.academic.subjectdeletion;

import org.springframework.http.*;
import org.springframework.web.bind.annotation.RestController;

import ru.rutcampustrack.academic.contract.api.SubjectDeletionApi;
import ru.rutcampustrack.academic.contract.dto.subject.SubjectDeletionModels.*;

import java.util.UUID;

@RestController
public class SubjectDeletionController implements SubjectDeletionApi {
    private final SubjectDeletionService service;

    public SubjectDeletionController(SubjectDeletionService service) {
        this.service = service;
    }

    @Override
    public ResponseEntity<Preview> preview(long id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.preview(id));
    }

    @Override
    public ResponseEntity<Challenge> challenge(long id, ChallengeRequest request) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(service.challenge(id, request));
    }

    @Override
    public ResponseEntity<Operation> confirm(long id, UUID key, ConfirmRequest request) {
        var result = service.confirm(id, key, request);
        return ResponseEntity.status(result.phase() == Phase.COMPLETED ? 200 : 202)
                .cacheControl(CacheControl.noStore())
                .body(result);
    }

    @Override
    public ResponseEntity<Operation> status(UUID id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.status(id));
    }
}
