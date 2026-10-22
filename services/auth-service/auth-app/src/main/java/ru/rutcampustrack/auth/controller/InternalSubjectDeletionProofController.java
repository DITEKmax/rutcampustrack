package ru.rutcampustrack.auth.controller;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import ru.rutcampustrack.auth.api.InternalSubjectDeletionProofApi;
import ru.rutcampustrack.auth.dto.SubjectDeletionProofModels.*;
import ru.rutcampustrack.auth.service.SubjectDeletionProofService;

@RestController
public class InternalSubjectDeletionProofController implements InternalSubjectDeletionProofApi {
    private final SubjectDeletionProofService proofs;

    public InternalSubjectDeletionProofController(SubjectDeletionProofService proofs) {
        this.proofs = proofs;
    }

    @Override
    public ResponseEntity<Challenge> challenge(ChallengeRequest request) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(proofs.challenge(request));
    }

    @Override
    public ResponseEntity<Verification> verify(VerifyRequest request) {
        var result = proofs.verify(request);
        return ResponseEntity.status(result.accepted() ? 200 : 409)
                .cacheControl(CacheControl.noStore())
                .body(result);
    }
}
