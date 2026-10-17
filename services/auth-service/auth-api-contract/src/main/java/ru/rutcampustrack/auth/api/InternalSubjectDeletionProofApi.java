package ru.rutcampustrack.auth.api;

import io.swagger.v3.oas.annotations.Hidden;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import ru.rutcampustrack.auth.dto.SubjectDeletionProofModels.*;

@Hidden
@RequestMapping("/internal/auth/subject-deletion")
public interface InternalSubjectDeletionProofApi {
    @PostMapping("/challenge")
    ResponseEntity<Challenge> challenge(@Valid @RequestBody ChallengeRequest request);

    @PostMapping("/verify")
    ResponseEntity<Verification> verify(@Valid @RequestBody VerifyRequest request);
}
