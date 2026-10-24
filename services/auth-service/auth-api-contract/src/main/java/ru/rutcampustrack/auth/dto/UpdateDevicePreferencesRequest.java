package ru.rutcampustrack.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateDevicePreferencesRequest(
        @NotNull
                @Pattern(regexp = "light|dark|system")
                @Schema(
                        requiredMode = Schema.RequiredMode.REQUIRED,
                        allowableValues = {"light", "dark", "system"})
                String theme,
        @NotNull
                @Size(max = 19)
                @Pattern(regexp = "0|[1-9][0-9]*")
                @Schema(
                        requiredMode = Schema.RequiredMode.REQUIRED,
                        pattern = "^(0|[1-9][0-9]*)$",
                        example = "0")
                String expectedRevision) {}
