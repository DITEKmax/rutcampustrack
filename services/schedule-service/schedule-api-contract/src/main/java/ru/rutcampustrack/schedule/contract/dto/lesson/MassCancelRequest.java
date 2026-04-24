package ru.rutcampustrack.schedule.contract.dto.lesson;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * Request DTO for mass-cancelling lessons for a group over a date range.
 * No Lombok — contract modules use plain Java records.
 */
@Schema(description = "Запрос на массовую отмену пар группы в диапазоне дат")
public record MassCancelRequest(

        @Schema(description = "ID группы, для которой отменяются пары",
                example = "10",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull
        Long groupId,

        @Schema(description = "Начало диапазона дат (включительно)",
                example = "2026-04-24",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull
        LocalDate dateFrom,

        @Schema(description = "Конец диапазона дат (включительно)",
                example = "2026-04-30",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull
        LocalDate dateTo,

        @Schema(description = "Причина массовой отмены пар",
                example = "Карантин в корпусе",
                maxLength = 512,
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank @Size(max = 512)
        String reason
) {}
