package ru.rutcampustrack.academic.contract.dto.group;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/** CAS-protected ADMIN headman assignment. */
@Schema(description = "Назначение старосты с проверкой ожидающего значения")
public record AssignHeadmanRequest(
        @Schema(description = "ID студента из состава группы", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull
        @Positive
        Long studentId,

        @Schema(description = "Текущий ID старосты; null означает ожидание отсутствия старосты")
        @Positive
        Long expectedHeadmanId
) {}
