package ru.rutcampustrack.auth.dto;

import java.time.Instant;
import java.util.UUID;

public record QrLoginPreviewResponse(UUID challengeId, QrLoginPurpose purpose,
        QrLoginState status, String browserLabel, Instant issuedAt, Instant expiresAt, String warning) {}
