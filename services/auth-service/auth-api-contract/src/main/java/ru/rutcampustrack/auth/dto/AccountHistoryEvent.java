package ru.rutcampustrack.auth.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import java.time.Instant;
import java.util.Objects;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(name = "AccountHistoryEvent")
public record AccountHistoryEvent(
        @NotBlank @Pattern(regexp = "^[1-9][0-9]*$") String id,
        @NotBlank @Pattern(regexp = "^[A-Z][A-Z0-9_]*$") String type,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant occurredAt,
        @Schema(nullable = true) String authMethod,
        @Schema(nullable = true) String clientLabel,
        @Schema(nullable = true) String locationLabel
) {
    public AccountHistoryEvent {
        requirePositiveDecimal(id, "id");
        requireUppercase(type, "type");
        Objects.requireNonNull(occurredAt, "occurredAt");
        if (authMethod != null) {
            requireUppercase(authMethod, "authMethod");
        }
    }

    private static void requirePositiveDecimal(String value, String name) {
        Objects.requireNonNull(value, name);
        if (!value.matches("[1-9][0-9]*")) {
            throw new IllegalArgumentException(name + " must be a positive decimal string");
        }
    }

    private static void requireUppercase(String value, String name) {
        Objects.requireNonNull(value, name);
        if (!value.matches("[A-Z][A-Z0-9_]*")) {
            throw new IllegalArgumentException(name + " must be an uppercase wire value");
        }
    }
}
