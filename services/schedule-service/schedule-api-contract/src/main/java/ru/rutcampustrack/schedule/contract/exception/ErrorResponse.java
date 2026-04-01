package ru.rutcampustrack.schedule.contract.exception;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

/**
 * RFC 7807 Problem Details response format.
 * Used by GlobalExceptionHandler in schedule-service.
 * Duplicated from academic-api-contract — no cross-service contract dependency.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Standard API error format")
public record ErrorResponse(

        @Schema(description = "HTTP status code", example = "403")
        int status,

        @Schema(description = "Error type URI", example = "https://api.rutcampustrack.ru/problems/access-denied")
        String type,

        @Schema(description = "Short error description", example = "Access denied")
        String title,

        @Schema(description = "Detailed description", example = "Required role: [ADMIN]")
        String detail,

        @Schema(description = "Request URI", example = "/api/schedule/items")
        String instance,

        @Schema(description = "Error timestamp")
        Instant timestamp,

        @Schema(description = "Field validation errors (only for 400)")
        List<FieldError> fieldErrors
) {

    public record FieldError(

            @Schema(description = "Field name", example = "dayOfWeek")
            String field,

            @Schema(description = "Rejected value", example = "7")
            Object rejectedValue,

            @Schema(description = "Error message", example = "must be between 0 and 5")
            String message
    ) {}
}
