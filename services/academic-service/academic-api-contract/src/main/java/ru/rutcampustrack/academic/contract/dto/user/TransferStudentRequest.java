package ru.rutcampustrack.academic.contract.dto.user;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Request DTO for transferring a student to a different group.
 * The transfer history is recorded in the student_transfers table.
 */
@Schema(description = "Запрос на перевод студента в другую группу")
public record TransferStudentRequest(

        @Schema(description = "ID новой группы", example = "43",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "ID новой группы обязателен")
        Long newGroupId,

        @Schema(description = "Причина перевода (для audit log)",
                example = "Перевод по заявлению студента",
                requiredMode = Schema.RequiredMode.REQUIRED, maxLength = 1000)
        @NotBlank(message = "Причина перевода обязательна")
        @Size(max = 1000)
        String reason
) {}
