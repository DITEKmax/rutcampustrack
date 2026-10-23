package ru.rutcampustrack.auth.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

/** Browser-scoped preference; deliberately contains no account or device identifier. */
public record DevicePreferencesResponse(
        @JsonInclude(JsonInclude.Include.ALWAYS)
                @Schema(
                        nullable = true,
                        allowableValues = {"light", "dark", "system"})
                String theme,
        @Schema(
                        requiredMode = Schema.RequiredMode.REQUIRED,
                        pattern = "^(0|[1-9][0-9]*)$",
                        example = "0")
                String revision) {}
