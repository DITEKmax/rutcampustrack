package ru.rutcampustrack.auth.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record PasswordResetRequestResponse(
        @JsonProperty("challengeId") String challengeId,
        @JsonProperty("ttlSeconds") int ttlSeconds
) {}
