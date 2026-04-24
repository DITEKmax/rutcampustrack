package ru.rutcampustrack.academic.contract.dto.semester;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Response DTO for semester overlap check endpoint.
 *
 * <p>Used by the admin panel's semester-dialog async validator (BUG-006-7,
 * Plan 58-05). Returns {@code overlaps=true} when the supplied date range
 * intersects an existing semester (excluding {@code excludeId} in edit mode).
 *
 * @param overlaps         whether the requested range overlaps any existing semester.
 * @param conflictingName  human-readable name of the first conflicting semester,
 *                         or {@code null} when no overlap was found.
 */
@Schema(description = "Результат проверки перекрытия диапазона дат с существующими семестрами")
public record OverlapCheckResponse(
        @Schema(description = "true — диапазон пересекает существующий семестр", example = "false")
        boolean overlaps,

        @Schema(description = "Название первого конфликтующего семестра или null",
                example = "Весенний 2026")
        String conflictingName
) {}
