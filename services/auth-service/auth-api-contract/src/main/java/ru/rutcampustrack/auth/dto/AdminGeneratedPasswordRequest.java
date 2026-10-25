package ru.rutcampustrack.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record AdminGeneratedPasswordRequest(
        @NotNull
                @Pattern(regexp = "[1-9][0-9]{0,18}")
                @Schema(requiredMode = Schema.RequiredMode.REQUIRED, pattern = "^[1-9][0-9]{0,18}$")
                String expectedCredentialsVersion) {}
