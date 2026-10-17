package ru.rutcampustrack.academic.contract.api;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import ru.rutcampustrack.academic.contract.dto.subject.SubjectDeletionModels.*;

import java.util.UUID;

@RequestMapping("/academic")
public interface SubjectDeletionApi {
    @GetMapping("/subjects/{id}/deletion-preview")
    ResponseEntity<Preview> preview(@PathVariable long id);

    @PostMapping("/subjects/{id}/deletion-challenge")
    ResponseEntity<Challenge> challenge(
            @PathVariable long id, @Valid @RequestBody ChallengeRequest request);

    @PostMapping("/subjects/{id}/deletion")
    ResponseEntity<Operation> confirm(
            @PathVariable long id,
            @RequestHeader("Idempotency-Key") UUID key,
            @Valid @RequestBody ConfirmRequest request);

    @GetMapping("/subject-deletion-operations/{operationId}")
    ResponseEntity<Operation> status(@PathVariable UUID operationId);
}
