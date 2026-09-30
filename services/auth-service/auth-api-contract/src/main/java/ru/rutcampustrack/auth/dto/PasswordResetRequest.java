package ru.rutcampustrack.auth.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** Accepts either a web login or the Telegram account identifier used by the bot. */
public record PasswordResetRequest(
        @Positive Long telegramId,
        @Size(max = 128) @Pattern(regexp = "(?s).*\\S.*") String login
) {
    @AssertTrue(message = "Exactly one recovery identifier is required")
    @JsonIgnore
    public boolean isExactlyOneIdentifier() {
        return (telegramId != null) ^ (login != null && !login.isBlank());
    }

    @Override
    public String toString() {
        return "PasswordResetRequest[identifier=<redacted>]";
    }
}
