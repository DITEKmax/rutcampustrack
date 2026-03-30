package ru.rutcampustrack.academic.contract.dto.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record TransferStudentRequest(
        @NotNull(message = "ID новой группы обязателен")
        Long newGroupId,

        @NotBlank(message = "Причина перевода обязательна")
        String reason
) {}
