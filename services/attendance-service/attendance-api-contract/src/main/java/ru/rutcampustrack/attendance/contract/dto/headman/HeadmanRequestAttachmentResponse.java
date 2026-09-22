package ru.rutcampustrack.attendance.contract.dto.headman;

import java.time.Instant;

public record HeadmanRequestAttachmentResponse(
        String id,
        String name,
        String contentType,
        long size,
        String sha256,
        String state,
        Instant uploadedAt,
        Instant expiresAt,
        String downloadUrl
) {
}
