package ru.rutcampustrack.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import java.util.Objects;

@Schema(name = "SelectActiveRoleRequest")
public record SelectActiveRoleRequest(
        @NotBlank @Pattern(regexp = "^[A-Z][A-Z0-9_]*$") String role,
        @NotBlank @Pattern(regexp = "^[1-9][0-9]*$") String expectedSessionVersion
) {
    public SelectActiveRoleRequest {
        requireUppercase(role, "role");
        requirePositiveDecimal(expectedSessionVersion, "expectedSessionVersion");
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
