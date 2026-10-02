package ru.rutcampustrack.academic.contract.dto.user;

import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.UUID;

public final class UserArchiveModels {
    private UserArchiveModels() { }
    public record Preview(long userId, long linkedGroupCount, long attendanceMarksCount,
            long activeHeadmanGroupCount, long soleTeacherAssignmentCount, boolean requiresPassword,
            Instant academicObservedAt, Instant attendanceObservedAt, String attendanceSnapshotDigest,
            String previewDigest, Instant expiresAt) { }
    public record ArchiveRequest(@NotNull UUID operationId,
            @NotBlank @Pattern(regexp = "[0-9a-f]{64}") String previewDigest,
            String password) {
        @Override public String toString() { return "ArchiveRequest[redacted]"; }
    }
    public record RestoreRequest(@NotNull UUID operationId) { }
}
