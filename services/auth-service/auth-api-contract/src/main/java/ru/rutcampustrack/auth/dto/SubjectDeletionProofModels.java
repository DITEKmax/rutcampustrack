package ru.rutcampustrack.auth.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.time.Instant;
import java.util.UUID;

/** Internal, purpose-isolated proof; no secret participates in diagnostic rendering. */
public final class SubjectDeletionProofModels {
    private SubjectDeletionProofModels() {}

    public record Binding(
            @Positive long subjectId,
            @Positive long semesterId,
            @Positive long groupId,
            @NotNull UUID operationId,
            @NotBlank @Pattern(regexp = "[0-9a-f]{64}") String previewDigest) {}

    public record ChallengeRequest(
            @NotBlank String internalToken,
            @NotBlank @Size(max = 256) String password,
            @NotNull @Valid Binding binding) {
        @Override
        public String toString() {
            return "SubjectDeletionChallengeRequest[redacted]";
        }
    }

    public record VerifyRequest(
            @NotBlank String internalToken,
            @NotNull @Valid Binding binding,
            @NotBlank @Pattern(regexp = "[A-Za-z0-9_-]{32}") String challengeId,
            @NotBlank @Pattern(regexp = "[0-9]{6}") String code) {
        @Override
        public String toString() {
            return "SubjectDeletionVerifyRequest[redacted]";
        }
    }

    public record Challenge(String challengeId, Instant expiresAt, int attemptsRemaining) {
        @Override
        public String toString() {
            return "SubjectDeletionChallenge[redacted]";
        }
    }

    public record Verification(boolean accepted, UUID receiptId) {
        @Override
        public String toString() {
            return "SubjectDeletionVerification[redacted]";
        }
    }
}
