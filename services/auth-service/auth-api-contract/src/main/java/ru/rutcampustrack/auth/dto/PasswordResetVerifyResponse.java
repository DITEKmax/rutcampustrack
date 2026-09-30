package ru.rutcampustrack.auth.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record PasswordResetVerifyResponse(
        @JsonProperty("resetTicket") String resetTicket,
        @JsonProperty("expiresInSeconds") int expiresInSeconds,
        @JsonProperty("attemptsRemaining") int attemptsRemaining
) {
    @Override
    public String toString() {
        return "PasswordResetVerifyResponse[resetTicket=<redacted>, expiresInSeconds="
                + expiresInSeconds + ", attemptsRemaining=" + attemptsRemaining + ']';
    }
}
