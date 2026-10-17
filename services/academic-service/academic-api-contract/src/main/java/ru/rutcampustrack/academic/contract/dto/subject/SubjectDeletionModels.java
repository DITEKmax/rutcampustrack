package ru.rutcampustrack.academic.contract.dto.subject;

import jakarta.validation.constraints.*;

import java.time.Instant;
import java.util.UUID;

public final class SubjectDeletionModels {
    private SubjectDeletionModels() {}

    public enum Phase {
        PREPARING,
        SEALED,
        COMMITTING,
        COMPLETED,
        RELEASING,
        CANCELLED
    }

    public record Preview(
            String subjectId,
            String semesterId,
            String groupId,
            String subjectName,
            String previewDigest,
            long academicRows,
            long scheduleRows,
            long attendanceRows,
            long mixedTickets) {}

    public record ChallengeRequest(
            @NotNull UUID operationId,
            @NotBlank @Pattern(regexp = "[0-9a-f]{64}") String previewDigest,
            @NotBlank @Size(max = 256) String password) {
        @Override
        public String toString() {
            return "SubjectDeletionChallengeRequest[redacted]";
        }
    }

    public record Challenge(String challengeId, Instant expiresAt, int attemptsRemaining) {
        @Override
        public String toString() {
            return "SubjectDeletionChallenge[redacted]";
        }
    }

    public record ConfirmRequest(
            @NotNull UUID operationId,
            @NotBlank @Pattern(regexp = "[0-9a-f]{64}") String previewDigest,
            @NotBlank @Pattern(regexp = "[A-Za-z0-9_-]{32}") String challengeId,
            @NotBlank @Pattern(regexp = "[0-9]{6}") String code) {
        @Override
        public String toString() {
            return "SubjectDeletionConfirmRequest[redacted]";
        }
    }

    public record Operation(
            UUID operationId,
            String subjectId,
            String semesterId,
            Phase phase,
            boolean retryable,
            String reason) {}
}
