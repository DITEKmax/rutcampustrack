package ru.rutcampustrack.auth.dto;

import java.time.Instant;
import java.util.UUID;

/** Polling deliberately excludes account identity and session tokens. */
public record QrLoginStatusResponse(UUID challengeId, QrLoginState status,
        Instant expiresAt, long ttl, int pollAfterSeconds) {}
